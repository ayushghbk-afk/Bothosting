package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import com.example.data.BotConfig
import com.example.data.BotLog
import com.example.protocol.MinecraftJavaClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MineBotService : Service() {

    private val job = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + job)
    private lateinit var database: AppDatabase

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val botId = intent?.getIntExtra(EXTRA_BOT_ID, -1) ?: -1

        if (action == ACTION_START && botId != -1) {
            // 1. Guarantee immediate startForeground call to satisfy OS contract
            showForegroundNotification("MineBot Host Active", "Initializing bot sandbox...")

            // 2. Launch asynchronously
            serviceScope.launch {
                val config = database.botConfigDao().getConfigById(botId)
                if (config != null) {
                    val scriptContent = if (config.scriptsEnabled && config.selectedScriptId != null) {
                        database.customScriptDao().getScriptById(config.selectedScriptId)?.content
                    } else {
                        null
                    }
                    launchBotInstance(config, scriptContent)
                } else {
                    // Config was not found, update foreground state just in case
                    updateForegroundState()
                }
            }
        } else if (action == ACTION_STOP && botId != -1) {
            stopBotInstance(botId)
        } else {
            // General status check
            updateForegroundState()
        }

        return START_NOT_STICKY
    }

    private fun launchBotInstance(config: BotConfig, scriptContent: String?) {
        if (activeClients.containsKey(config.id)) {
            // Already running
            return
        }

        // Create log helper
        val logHelper = { message: String, type: String ->
            serviceScope.launch {
                database.botLogDao().insertLog(
                    BotLog(
                        botId = config.id,
                        message = message,
                        type = type
                    )
                )
            }
            Unit
        }

        val client = MinecraftJavaClient(
            botId = config.id,
            name = config.name,
            address = config.serverAddress,
            port = config.serverPort,
            username = config.username,
            edition = config.edition,
            antiAfkEnabled = config.antiAfkEnabled,
            antiAfkType = config.antiAfkType,
            scriptContent = scriptContent,
            triggerResponses = config.triggerResponses,
            onLog = logHelper,
            onStatusChange = { newStatus ->
                // Update UI state maps
                _botStatuses.value = _botStatuses.value.toMutableMap().apply {
                    put(config.id, newStatus)
                }
            },
            onStatsUpdate = { cpu, ram, ping ->
                _botStats.value = _botStats.value.toMutableMap().apply {
                    put(config.id, BotStats(cpu, ram, ping))
                }
            },
            aiAutoReplyEnabled = config.aiAutoReplyEnabled,
            aiPersonality = config.aiPersonality,
            geminiApiKey = com.example.BuildConfig.GEMINI_API_KEY,
            version = config.version
        )

        activeClients[config.id] = client
        client.start()

        _runningBotIds.value = _runningBotIds.value.toMutableSet().apply { add(config.id) }
        logHelper("Bot container successfully registered with Background Host Service.", "INFO")
        
        updateForegroundState()
    }

    private fun stopBotInstance(botId: Int) {
        val client = activeClients.remove(botId)
        client?.stop()
        _runningBotIds.value = _runningBotIds.value.toMutableSet().apply { remove(botId) }
        _botStatuses.value = _botStatuses.value.toMutableMap().apply { remove(botId) }
        _botStats.value = _botStats.value.toMutableMap().apply { remove(botId) }
        
        updateForegroundState()
    }

    private fun showForegroundNotification(title: String, text: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(title, text),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, buildNotification(title, text))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateForegroundState() {
        val runningCount = _runningBotIds.value.size
        if (runningCount > 0) {
            val title = "MineBot Host Active"
            val text = "$runningCount Minecraft bot(s) hosting online 24/7."
            showForegroundNotification(title, text)
        } else {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            stopSelf()
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "MineBot Host Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
        // Stop all clients
        activeClients.values.forEach { it.stop() }
        activeClients.clear()
        _runningBotIds.value = emptySet()
        _botStatuses.value = emptyMap()
        _botStats.value = emptyMap()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    data class BotStats(val cpu: Double, val ram: Double, val ping: Int)

    companion object {
        const val CHANNEL_ID = "MineBotServiceChannel"
        const val NOTIFICATION_ID = 8089

        const val ACTION_START = "com.example.service.START_BOT"
        const val ACTION_STOP = "com.example.service.STOP_BOT"
        const val EXTRA_BOT_ID = "com.example.service.EXTRA_BOT_ID"

        private val activeClients = mutableMapOf<Int, MinecraftJavaClient>()

        // Stateful observers for the UI ViewModels
        private val _runningBotIds = MutableStateFlow<Set<Int>>(emptySet())
        val runningBotIds: StateFlow<Set<Int>> = _runningBotIds.asStateFlow()

        private val _botStatuses = MutableStateFlow<Map<Int, String>>(emptyMap())
        val botStatuses: StateFlow<Map<Int, String>> = _botStatuses.asStateFlow()

        private val _botStats = MutableStateFlow<Map<Int, BotStats>>(emptyMap())
        val botStats: StateFlow<Map<Int, BotStats>> = _botStats.asStateFlow()

        fun startBot(context: Context, botId: Int) {
            val intent = Intent(context, MineBotService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_BOT_ID, botId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopBot(context: Context, botId: Int) {
            val intent = Intent(context, MineBotService::class.java).apply {
                action = ACTION_STOP
                putExtra(EXTRA_BOT_ID, botId)
            }
            context.startService(intent)
        }

        fun handleManualMessage(botId: Int, message: String) {
            activeClients[botId]?.handleIncomingMessage("You", message)
        }
    }
}
