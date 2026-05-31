package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.service.MineBotService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class BotViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = BotRepository(database)

    // Data-persistent streams
    val bots: StateFlow<List<BotConfig>> = repository.allConfigs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scripts: StateFlow<List<CustomScript>> = repository.allScripts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Foreground service state streams bridged cleanly
    val runningBotIds: StateFlow<Set<Int>> = MineBotService.runningBotIds
    val botStatuses: StateFlow<Map<Int, String>> = MineBotService.botStatuses
    val botStats: StateFlow<Map<Int, MineBotService.BotStats>> = MineBotService.botStats

    init {
        // Pre-populate some cool default scripts so the user sees excellent templates right away!
        viewModelScope.launch {
            repository.allScripts.first().let { currentScripts ->
                if (currentScripts.isEmpty()) {
                    createDefaultScripts()
                }
            }
            repository.allConfigs.first().let { currentConfigs ->
                if (currentConfigs.isEmpty()) {
                    createDefaultBots()
                }
            }
        }
    }

    private suspend fun createDefaultScripts() {
        val loginScript = CustomScript(
            name = "Auto Register & Login",
            content = """// Automatically registers and logs in on cracked servers
DELAY 1500
SAY /register strongpassword123 strongpassword123
DELAY 1200
SAY /login strongpassword123
DELAY 2000
SAY /spawn
DELAY 1000
SAY Bot hosted successfully by MineBot Android Client!"""
        )

        val circularWalkScript = CustomScript(
            name = "AFK anti-kick routine",
            content = """// Periodically triggers a circular sequence and reports health
LOOP_START
DELAY 5000
SAY /home afk
DELAY 2000
SAY Checking area safety... status: NOMINAL.
LOOP_END"""
        )

        val spammerScript = CustomScript(
            name = "Spam Advertisement Helper",
            content = """// Safely broadcast periodic messages to promote services
LOOP_START
SAY [Ad] Selling raw minerals & diamond armor at spawn shop /warp spawn_market
DELAY 20000
SAY [Ad] Join our active clan /clan list for cooperative survival!
DELAY 20000
LOOP_END"""
        )

        repository.insertScript(loginScript)
        repository.insertScript(circularWalkScript)
        repository.insertScript(spammerScript)
    }

    private suspend fun createDefaultBots() {
        val defaultJava = BotConfig(
            name = "LobbyDweller",
            serverAddress = "play.hypixel.net",
            serverPort = 25565,
            username = "GamerSteve_99",
            edition = "JAVA",
            version = "1.20.4",
            scriptsEnabled = true,
            selectedScriptId = 1,
            antiAfkEnabled = true,
            antiAfkType = "CIRCLE",
            autoReconnect = true
        )
        val defaultBedrock = BotConfig(
            name = "BedrockMiner",
            serverAddress = "pe.mineplex.com",
            serverPort = 19132,
            username = "AlexRocky",
            edition = "BEDROCK",
            version = "1.20.8",
            scriptsEnabled = true,
            selectedScriptId = 2,
            antiAfkEnabled = true,
            antiAfkType = "JUMP",
            autoReconnect = false
        )
        repository.insertConfig(defaultJava)
        repository.insertConfig(defaultBedrock)
    }

    // Bot CRUD Actions
    fun saveBot(config: BotConfig) {
        viewModelScope.launch {
            if (config.id == 0) {
                repository.insertConfig(config)
            } else {
                repository.updateConfig(config)
            }
        }
    }

    fun deleteBot(config: BotConfig) {
        viewModelScope.launch {
            stopBot(config.id)
            repository.deleteConfig(config)
        }
    }

    // Bot Connection Control
    fun startBot(botId: Int) {
        viewModelScope.launch {
            // Write a log starting up
            repository.insertLog(
                BotLog(
                    botId = botId,
                    message = "Client instruction: Start requested in UI.",
                    type = "INFO"
                )
            )
            MineBotService.startBot(getApplication(), botId)
        }
    }

    fun stopBot(botId: Int) {
        viewModelScope.launch {
            repository.insertLog(
                BotLog(
                    botId = botId,
                    message = "Client instruction: Stop requested in UI.",
                    type = "INFO"
                )
            )
            MineBotService.stopBot(getApplication(), botId)
        }
    }

    // Log Querying
    fun getLogsForBot(botId: Int): Flow<List<BotLog>> {
        return repository.getLogsForBotFlow(botId)
    }

    fun clearLogs(botId: Int) {
        viewModelScope.launch {
            repository.clearLogsForBot(botId)
        }
    }

    // Script Actions
    fun saveScript(script: CustomScript) {
        viewModelScope.launch {
            repository.insertScript(script)
        }
    }

    fun deleteScript(script: CustomScript) {
        viewModelScope.launch {
            repository.deleteScript(script)
        }
    }

    fun importScriptFromGithub(url: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    java.net.URL(url).readText(Charsets.UTF_8)
                }
                onSuccess(text)
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Unknown network timeout or address error")
            }
        }
    }

    fun sendManualCommand(botId: Int, message: String) {
        viewModelScope.launch {
            repository.insertLog(
                BotLog(
                    botId = botId,
                    message = "<You> $message",
                    type = "CHAT"
                )
            )
            // Dispatch message to active background trigger listeners if online
            MineBotService.handleManualMessage(botId, message)
        }
    }
}
