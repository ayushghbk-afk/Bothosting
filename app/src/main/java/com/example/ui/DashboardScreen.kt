package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.BotConfig
import com.example.data.BotLog
import com.example.data.CustomScript
import com.example.service.MineBotService
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

enum class BotTab {
    DASHBOARD,
    BOT_EDITOR,
    SCRIPT_EDITOR,
    CONSOLE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: BotViewModel,
    modifier: Modifier = Modifier
) {
    var currentTab by remember { mutableStateOf(BotTab.DASHBOARD) }
    var selectedBotIdForConsole by remember { mutableStateOf<Int?>(null) }
    var botToEdit by remember { mutableStateOf<BotConfig?>(null) }
    
    val bots by viewModel.bots.collectAsStateWithLifecycle()
    val scripts by viewModel.scripts.collectAsStateWithLifecycle()
    val runningBotIds by viewModel.runningBotIds.collectAsStateWithLifecycle()
    val statuses by viewModel.botStatuses.collectAsStateWithLifecycle()
    val stats by viewModel.botStats.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()

    // Determine console target bot
    LaunchedEffect(bots) {
        if (selectedBotIdForConsole == null && bots.isNotEmpty()) {
            selectedBotIdForConsole = bots.first().id
        }
    }

    val recentLogState = remember(selectedBotIdForConsole) {
        if (selectedBotIdForConsole != null) {
            viewModel.getLogsForBot(selectedBotIdForConsole!!)
        } else {
            flowOf(emptyList())
        }
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val latestLog = recentLogState.value.firstOrNull()
    val recentLogMessage = if (latestLog != null) {
        "[${latestLog.type}] ${latestLog.message}"
    } else {
        null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "CraftBot",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                letterSpacing = (-0.5).sp
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(Color(0xFF22C55E))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Node: US-East-1 (Active)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )
                            }
                        }
                        // Avatar profile container
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFFDDE2F9))
                                .border(1.dp, Color(0xFFC3C6CF), RoundedCornerShape(50)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Profile",
                                tint = Color(0xFF1B1B1F),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = BentoNavBarBg,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == BotTab.DASHBOARD,
                    onClick = { currentTab = BotTab.DASHBOARD },
                    label = { Text("Home", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF1B1B1F),
                        selectedTextColor = Color(0xFF1B1B1F),
                        indicatorColor = Color(0xFFDDE2F9),
                        unselectedIconColor = Color(0xFF44474E),
                        unselectedTextColor = Color(0xFF44474E)
                    )
                )
                NavigationBarItem(
                    selected = currentTab == BotTab.BOT_EDITOR,
                    onClick = { 
                        botToEdit = null
                        currentTab = BotTab.BOT_EDITOR 
                    },
                    label = { Text("Config", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Bot Editor") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF1B1B1F),
                        selectedTextColor = Color(0xFF1B1B1F),
                        indicatorColor = Color(0xFFDDE2F9),
                        unselectedIconColor = Color(0xFF44474E),
                        unselectedTextColor = Color(0xFF44474E)
                    )
                )
                NavigationBarItem(
                    selected = currentTab == BotTab.SCRIPT_EDITOR,
                    onClick = { currentTab = BotTab.SCRIPT_EDITOR },
                    label = { Text("Scripts", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                    icon = { Icon(Icons.Default.Build, contentDescription = "Script Editor") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF1B1B1F),
                        selectedTextColor = Color(0xFF1B1B1F),
                        indicatorColor = Color(0xFFDDE2F9),
                        unselectedIconColor = Color(0xFF44474E),
                        unselectedTextColor = Color(0xFF44474E)
                    )
                )
                NavigationBarItem(
                    selected = currentTab == BotTab.CONSOLE,
                    onClick = { currentTab = BotTab.CONSOLE },
                    label = { Text("Console", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                    icon = { Icon(Icons.Default.Info, contentDescription = "Live Console") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF1B1B1F),
                        selectedTextColor = Color(0xFF1B1B1F),
                        indicatorColor = Color(0xFFDDE2F9),
                        unselectedIconColor = Color(0xFF44474E),
                        unselectedTextColor = Color(0xFF44474E)
                    )
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (currentTab) {
                BotTab.DASHBOARD -> DashboardTab(
                    bots = bots,
                    runningBotIds = runningBotIds,
                    statuses = statuses,
                    stats = stats,
                    scriptsCount = scripts.size,
                    recentLogMessage = recentLogMessage,
                    onEditBot = { config ->
                        botToEdit = config
                        currentTab = BotTab.BOT_EDITOR
                    },
                    onDeleteBot = { config ->
                        viewModel.deleteBot(config)
                    },
                    onToggleBot = { config ->
                        val isRunning = runningBotIds.contains(config.id)
                        if (isRunning) {
                            viewModel.stopBot(config.id)
                        } else {
                            viewModel.startBot(config.id)
                        }
                    },
                    onViewConsole = { config ->
                        selectedBotIdForConsole = config.id
                        currentTab = BotTab.CONSOLE
                    },
                    onConsoleTabClick = {
                        currentTab = BotTab.CONSOLE
                    },
                    onAddBotClick = {
                        botToEdit = null
                        currentTab = BotTab.BOT_EDITOR
                    },
                    onCreateMockBot = {
                        viewModel.saveBot(
                            BotConfig(
                                name = "TestBot",
                                serverAddress = "localhost",
                                serverPort = 25565,
                                username = "Custom_Bot_" + (100..999).random(),
                                edition = "JAVA",
                                antiAfkEnabled = true,
                                antiAfkType = "JUMP"
                            )
                        )
                    }
                )
                BotTab.BOT_EDITOR -> ConfigEditorTab(
                    botConfig = botToEdit,
                    scripts = scripts,
                    onSave = { updated ->
                        viewModel.saveBot(updated)
                        currentTab = BotTab.DASHBOARD
                    },
                    onCancel = {
                        currentTab = BotTab.DASHBOARD
                    }
                )
                BotTab.SCRIPT_EDITOR -> ScriptEditorTab(
                    scripts = scripts,
                    onSaveScript = { name, content ->
                        viewModel.saveScript(CustomScript(name = name, content = content))
                    },
                    onDeleteScript = { script ->
                        viewModel.deleteScript(script)
                    },
                    onImportScript = { url, onSuccess, onError ->
                        viewModel.importScriptFromGithub(url, onSuccess, onError)
                    },
                    onFetchRepo = { urlInput, onSuccess, onError ->
                        viewModel.fetchGithubRepoContents(urlInput, onSuccess, onError)
                    },
                    onAutoCreateBot = { botName, host, port, username ->
                        viewModel.saveBot(
                            BotConfig(
                                name = botName,
                                serverAddress = host,
                                serverPort = port,
                                username = username,
                                edition = "JAVA",
                                version = "1.20.4",
                                scriptsEnabled = true,
                                antiAfkEnabled = true,
                                antiAfkType = "CIRCLE"
                            )
                        )
                    },
                    onGenerateAiScript = { prompt, onSuccess, onError ->
                        viewModel.generateScriptFromPrompt(prompt, onSuccess, onError)
                    }
                )
                BotTab.CONSOLE -> ConsoleTab(
                    bots = bots,
                    selectedBotId = selectedBotIdForConsole,
                    onBotSelected = { selectedBotIdForConsole = it },
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
fun DashboardTab(
    bots: List<BotConfig>,
    runningBotIds: Set<Int>,
    statuses: Map<Int, String>,
    stats: Map<Int, MineBotService.BotStats>,
    scriptsCount: Int,
    recentLogMessage: String?,
    onEditBot: (BotConfig) -> Unit,
    onDeleteBot: (BotConfig) -> Unit,
    onToggleBot: (BotConfig) -> Unit,
    onViewConsole: (BotConfig) -> Unit,
    onConsoleTabClick: () -> Unit,
    onAddBotClick: () -> Unit,
    onCreateMockBot: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            BentoGridDashboard(
                activeCount = runningBotIds.size,
                totalCount = bots.size,
                scriptsCount = scriptsCount,
                stats = stats,
                onAddBotClick = onAddBotClick,
                recentLogMessage = recentLogMessage,
                onConsoleClick = onConsoleTabClick
            )
        }

        item {
            val context = LocalContext.current
            var isBackgroundOptimized by remember { mutableStateOf(!isBatteryOptimizationsIgnored(context)) }
            
            LaunchedEffect(Unit) {
                while (true) {
                    isBackgroundOptimized = !isBatteryOptimizationsIgnored(context)
                    delay(3000)
                }
            }

            if (isBackgroundOptimized) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFEF7FF),
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFFE8DEF8)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFE8DEF8)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Battery Warning",
                                tint = Color(0xFF21005D),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "24/7 Hosting Optimization",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF1D192B)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "To keep Minecraft client sockets alive 24/7 continuously without background termination under Android 15/16, exclude this app from Doze mode constraints.",
                                fontSize = 11.sp,
                                color = Color(0xFF49454F),
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    requestIgnoreBatteryOptimizations(context)
                                    isBackgroundOptimized = !isBatteryOptimizationsIgnored(context)
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF6750A4),
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("WHITELIST BACKGROUND", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFE6F4EA),
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF34A853).copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Battery Optimized",
                            tint = Color(0xFF137333),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "24/7 Background Hosting: ACTIVE",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF137333)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "System battery limitations bypassed. Background sockets run continuous.",
                                fontSize = 10.sp,
                                color = Color(0xFF137333).copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ACTIVE BOTS LOGISTICS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color(0xFF44474E),
                    letterSpacing = 1.sp
                )
                if (bots.isEmpty()) {
                    TextButton(onClick = onCreateMockBot) {
                        Text("Add Sample Bot", color = Color(0xFF1B1B1F), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (bots.isEmpty()) {
            item {
                EmptyStateCard()
            }
        } else {
            items(bots, key = { it.id }) { bot ->
                val status = statuses[bot.id] ?: "STOPPED"
                val botStat = stats[bot.id]
                BotLogisticsCard(
                    bot = bot,
                    status = status,
                    botStat = botStat,
                    isRunning = runningBotIds.contains(bot.id),
                    onToggle = { onToggleBot(bot) },
                    onEdit = { onEditBot(bot) },
                    onDelete = { onDeleteBot(bot) },
                    onViewConsole = { onViewConsole(bot) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun BentoGridDashboard(
    activeCount: Int,
    totalCount: Int,
    scriptsCount: Int,
    stats: Map<Int, MineBotService.BotStats>,
    onAddBotClick: () -> Unit,
    recentLogMessage: String?,
    onConsoleClick: () -> Unit
) {
    val totalCpu = stats.values.sumOf { it.cpu }
    val totalRam = stats.values.sumOf { it.ram }
    val ramFormatted = if (totalRam > 0) "${String.format("%.1f", totalRam / 1024.0)}GB" else "0.0GB"
    val cpuFormatted = "${String.format("%.1f", totalCpu)}%"

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Main Stat Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = BentoMainStatCardBg
            ),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1B1B1F))
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Bot Host",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(Color.White.copy(alpha = 0.5f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "PREMIUM SLOT",
                            color = Color(0xFF001D35),
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = String.format("%02d", activeCount),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B1B1F)
                        )
                        Text(
                            text = " / $totalCount",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF44474E),
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                    Text(
                        text = "Active Bots Online",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF44474E)
                    )
                }
            }
        }

        // CPU & RAM Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // CPU Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                    .padding(1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF0F0F7))
                            .padding(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "CPU",
                            tint = Color(0xFF1B1B1F),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "CPU",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF44474E)
                        )
                        Text(
                            text = cpuFormatted,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B1B1F)
                        )
                    }
                }
            }

            // RAM Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                    .padding(1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF0F0F7))
                            .padding(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "RAM",
                            tint = Color(0xFF1B1B1F),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "RAM",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF44474E)
                        )
                        Text(
                            text = ramFormatted,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B1B1F)
                        )
                    }
                }
            }
        }

        // Scripts & Quick Action Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Scripts Card
            Card(
                colors = CardDefaults.cardColors(containerColor = BentoScriptCardBg),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(90.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "SCRIPTS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1D192B).copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$scriptsCount Active",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1D192B)
                    )
                }
            }

            // Quick Action Card (New Bot)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF212121)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(90.dp)
                    .clickable { onAddBotClick() }
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create new bot",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "New Bot",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }

        // Short Preview Terminal
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111115)),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onConsoleClick() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.Green)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "REAL-TIME LOGS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4ADE80),
                            letterSpacing = 1.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        text = "tap to expand",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.4f),
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (recentLogMessage != null) {
                            Text(
                                text = recentLogMessage,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Text(
                                text = ">_ socket listener is ready\nWaiting for bot connection events...",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BotLogisticsCard(
    bot: BotConfig,
    status: String,
    botStat: MineBotService.BotStats?,
    isRunning: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onViewConsole: () -> Unit
) {
    val statusColor = when (status) {
        "CONNECTED" -> TerminalEmeraldGreen
        "CONNECTING" -> TerminalGoldYellow
        "STOPPED" -> Color(0xFF44474E)
        "FAILED" -> TerminalRubyRed
        else -> TerminalCyanAccent
    }

    val botColor = try {
        Color(android.graphics.Color.parseColor(bot.themeColorHex))
    } catch (e: Exception) {
        Color(0xFF6750A4)
    }

    val avatarIcon = when (bot.avatarType.uppercase()) {
        "STEVE" -> Icons.Default.AccountBox
        "ALEX" -> Icons.Default.Face
        "CREEPER" -> Icons.Default.Warning
        "ENDERMAN" -> Icons.Default.Lock
        "ROBOT" -> Icons.Default.Build
        "WIZARD" -> Icons.Default.Star
        else -> Icons.Default.AccountCircle
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isRunning) statusColor.copy(alpha = 0.5f) else BentoBorder,
                RoundedCornerShape(24.dp)
            )
            .testTag("bot_card_${bot.id}")
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Render custom status line first as a bento banner style if available
            if (bot.customStatus.isNotBlank()) {
                Row(modifier = Modifier.padding(bottom = 10.dp)) {
                    Box(
                        modifier = Modifier
                            .background(botColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .border(1.dp, botColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "SIMULATION ROLE: ${bot.customStatus.uppercase()}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = botColor,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            // Title & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Visual Avatar Circle Icon Block
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(botColor.copy(alpha = 0.15f))
                            .border(1.dp, botColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = avatarIcon,
                            contentDescription = bot.avatarType,
                            tint = botColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = bot.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1B1B1F),
                                fontFamily = FontFamily.Default
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (bot.edition == "JAVA") Color(0xFFE8DEF8)
                                        else Color(0xFFDDE2F9),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = bot.edition,
                                    color = if (bot.edition == "JAVA") Color(0xFF1D192B) else Color(0xFF001D35),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = "${bot.serverAddress}:${bot.serverPort}",
                            fontSize = 11.sp,
                            color = Color(0xFF44474E),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Connect Button
                Button(
                    onClick = onToggle,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) Color(0xFFBA1A1A) else Color(0xFF212121),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isRunning) "KILL" else "HOST",
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            // Real-time Status Badge & Indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "STATUS: $status",
                            fontSize = 11.sp,
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                
                if (bot.antiAfkEnabled) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "• AFK REACTION (${bot.antiAfkType})",
                        fontSize = 11.sp,
                        color = Color(0xFF44474E),
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Real-time logistics values (if running)
            if (isRunning && botStat != null) {
                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "CPU: ${String.format("%.1f", botStat.cpu)}%",
                        fontSize = 11.sp,
                        color = Color(0xFF1B1B1F),
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "MEM: ${String.format("%.1f", botStat.ram)}MB",
                        fontSize = 11.sp,
                        color = Color(0xFF44474E),
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "PING: ${botStat.ping}ms",
                        fontSize = 11.sp,
                        color = TerminalEmeraldGreen,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f), modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))

            // Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onViewConsole) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Console Logs",
                        tint = Color(0xFF1B1B1F)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Configuration",
                        tint = Color(0xFF1B1B1F)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Bot",
                        tint = TerminalRubyRed
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyStateCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Build,
                contentDescription = "No bots",
                tint = Color(0xFF1B1B1F),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "NO BOTS PROVISIONED",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B1B1F),
                fontFamily = FontFamily.Default
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Click the '+' button above to configure your first Minecraft host bot. Supports offline cracked modes, custom automated script loops, and real-time terminal outputs.",
                fontSize = 11.sp,
                color = Color(0xFF44474E),
                fontFamily = FontFamily.Default,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigEditorTab(
    botConfig: BotConfig?,
    scripts: List<CustomScript>,
    onSave: (BotConfig) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf(botConfig?.name ?: "MinerBot") }
    var address by remember { mutableStateOf(botConfig?.serverAddress ?: "") }
    var port by remember { mutableStateOf(botConfig?.serverPort?.toString() ?: "25565") }
    var username by remember { mutableStateOf(botConfig?.username ?: "MineBotClient") }
    var edition by remember { mutableStateOf(botConfig?.edition ?: "JAVA") }
    var antiAfkEnabled by remember { mutableStateOf(botConfig?.antiAfkEnabled ?: true) }
    var antiAfkType by remember { mutableStateOf(botConfig?.antiAfkType ?: "CIRCLE") }
    var scriptsEnabled by remember { mutableStateOf(botConfig?.scriptsEnabled ?: false) }
    var selectedScriptId by remember { mutableStateOf<Int?>(botConfig?.selectedScriptId) }

    var avatarType by remember { mutableStateOf(botConfig?.avatarType ?: "STEVE") }
    var themeColorHex by remember { mutableStateOf(botConfig?.themeColorHex ?: "#6750A4") }
    var customStatus by remember { mutableStateOf(botConfig?.customStatus ?: "READY") }
    var aiAutoReplyEnabled by remember { mutableStateOf(botConfig?.aiAutoReplyEnabled ?: false) }
    var aiPersonality by remember { mutableStateOf(botConfig?.aiPersonality ?: "Friendly builder bot who loves mining diamonds") }

    var triggersMap by remember(botConfig) {
        val raw = botConfig?.triggerResponses ?: "hi:::Hello! I am online and managing chunks.;;;help:::I am a custom MineBot client. Type messages to chat with me.;;;status:::Host environment: Android Sandbox. Battery: Nominal. CPU: 1.25%."
        val map = if (raw.isBlank()) mutableMapOf() else {
            raw.split(";;;").mapNotNull {
                val parts = it.split(":::", limit = 2)
                if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
            }.toMap().toMutableMap()
        }
        mutableStateOf(map)
    }

    LaunchedEffect(scripts) {
        if (selectedScriptId == null && scripts.isNotEmpty()) {
            selectedScriptId = scripts.first().id
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = if (botConfig == null) "PROVISION NEW HOST BOT" else "MODIFY BOT CONFIGURATION",
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B1B1F),
                fontSize = 16.sp,
                fontFamily = FontFamily.Default,
                letterSpacing = 0.5.sp
            )
            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.4f), modifier = Modifier.padding(vertical = 8.dp))
        }

        item {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("App Bot Name", fontFamily = FontFamily.Default) },
                colors = textFieldColors(),
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_bot_name")
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Server IP / Domain", fontFamily = FontFamily.Default) },
                    colors = textFieldColors(),
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .weight(1.5f)
                        .testTag("input_bot_address")
                )
                TextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Port", fontFamily = FontFamily.Default) },
                    colors = textFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .weight(0.8f)
                        .testTag("input_bot_port")
                )
            }
        }

        item {
            TextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Minecraft Username (for login)", fontFamily = FontFamily.Default) },
                colors = textFieldColors(),
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_bot_username")
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        "CUSTOM BOT VISUALS & STATUS ROLE",
                        color = Color(0xFF1B1B1F),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Default,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Avatar presets selection
                    Text(
                        "Choose Character Avatar Profile:",
                        color = Color(0xFF44474E),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val avatars = listOf("STEVE", "ALEX", "CREEPER", "ENDERMAN", "ROBOT", "WIZARD")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        avatars.take(3).forEach { av ->
                            Button(
                                onClick = { avatarType = av },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (avatarType == av) Color(0xFFE8DEF8) else Color(0xFFF3F4F9),
                                    contentColor = Color(0xFF111115)
                                ),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Text(av, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        avatars.drop(3).forEach { av ->
                            Button(
                                onClick = { avatarType = av },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (avatarType == av) Color(0xFFE8DEF8) else Color(0xFFF3F4F9),
                                    contentColor = Color(0xFF111115)
                                ),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Text(av, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Accent Colors Grid Row
                    Text(
                        "Select Custom Visual Theme Color:",
                        color = Color(0xFF44474E),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val colorSwatches = listOf(
                        "#6750A4" to Color(0xFF6750A4), // M3 Indigo
                        "#00E676" to Color(0xFF00E676), // Lime Accent
                        "#00E5FF" to Color(0xFF00E5FF), // Cyan
                        "#FF9100" to Color(0xFFFF9100), // Amber
                        "#FF1744" to Color(0xFFFF1744), // Crimson
                        "#D500F9" to Color(0xFFD500F9), // Purple
                        "#37474F" to Color(0xFF37474F)  // Dark Slate
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        colorSwatches.forEach { (hex, colorVal) ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(colorVal)
                                    .border(
                                        width = if (themeColorHex == hex) 3.dp else 0.dp,
                                        color = if (themeColorHex == hex) Color.White else Color.Transparent,
                                        shape = RoundedCornerShape(50)
                                    )
                                    .clickable { themeColorHex = hex }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TextField(
                        value = customStatus,
                        onValueChange = { customStatus = it },
                        label = { Text("Simulation Role Status (e.g. MINING, PATROLLING)", fontFamily = FontFamily.Default) },
                        colors = textFieldColors(),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Default, fontSize = 12.sp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item {
            var newTrigger by remember { mutableStateOf("") }
            var newReply by remember { mutableStateOf("") }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        "CUSTOM BOT CHAT AUTORESPONDERS",
                        color = Color(0xFF1B1B1F),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Default,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Create automatic trigger rules. When players mention a trigger keyword in the server chat, the bot immediately auto-replies.",
                        fontSize = 10.sp,
                        color = Color(0xFF44474E),
                        lineHeight = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    TextField(
                        value = newTrigger,
                        onValueChange = { newTrigger = it },
                        label = { Text("If message contains keyword...", fontSize = 11.sp, fontFamily = FontFamily.Default) },
                        placeholder = { Text("e.g. hello, coordinate, rule", fontSize = 11.sp) },
                        colors = textFieldColors(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = newReply,
                        onValueChange = { newReply = it },
                        label = { Text("Auto-reply with text...", fontSize = 11.sp, fontFamily = FontFamily.Default) },
                        placeholder = { Text("Hello! How can I assist you on this server?", fontSize = 11.sp) },
                        colors = textFieldColors(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            if (newTrigger.isNotBlank() && newReply.isNotBlank()) {
                                val updated = triggersMap.toMutableMap()
                                updated[newTrigger.trim().lowercase()] = newReply.trim()
                                triggersMap = updated
                                newTrigger = ""
                                newReply = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF212121),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Add Filter Rule", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    if (triggersMap.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "ACTIVE INTERACTIVE RULES CHATBOARD:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF44474E)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        triggersMap.forEach { (trigger, reply) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .border(1.dp, Color.LightGray.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Keyword: \"$trigger\"",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color(0xFF1B1B1F)
                                    )
                                    Text(
                                        text = "Reply: \"$reply\"",
                                        fontSize = 11.sp,
                                        color = Color(0xFF44474E),
                                        lineHeight = 14.sp
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val updated = triggersMap.toMutableMap()
                                        updated.remove(trigger)
                                        triggersMap = updated
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete filter",
                                        tint = TerminalRubyRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "MINECRAFT PLATFORM EDITION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF44474E),
                fontFamily = FontFamily.Default,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { 
                        edition = "JAVA" 
                        if (port == "19132") port = "25565"
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (edition == "JAVA") Color(0xFFE8DEF8) else Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (edition == "JAVA") Color(0xFFC3C6CF) else Color.LightGray.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "JAVA (PC/Cracked)",
                        color = if (edition == "JAVA") Color(0xFF1D192B) else Color(0xFF1B1B1F),
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }

                Button(
                    onClick = { 
                        edition = "BEDROCK" 
                        if (port == "25565") port = "19132"
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (edition == "BEDROCK") Color(0xFFDDE2F9) else Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (edition == "BEDROCK") Color(0xFFC3C6CF) else Color.LightGray.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "BEDROCK (Pocket/UDP)",
                        color = if (edition == "BEDROCK") Color(0xFF001D35) else Color(0xFF1B1B1F),
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BentoBorder, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "ANTI-AFK MOVEMENT TRIGGER",
                                color = Color(0xFF1B1B1F),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Default
                            )
                            Text(
                                "Bypasses automatic spawn kicks",
                                color = Color(0xFF44474E),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Default
                            )
                        }
                        Switch(
                            checked = antiAfkEnabled,
                            onCheckedChange = { antiAfkEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF212121),
                                uncheckedThumbColor = Color(0xFF44474E),
                                uncheckedTrackColor = Color(0xFFF3F4F9)
                            )
                        )
                    }

                    if (antiAfkEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Select AFK Movement Subsystem Type:",
                            fontSize = 11.sp,
                            color = Color(0xFF44474E),
                            fontFamily = FontFamily.Default,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val moveTypes = listOf(
                            "CIRCLE" to "Circular Crawl",
                            "JUMP" to "Frequent Jumps",
                            "RANDOM_WALK" to "Random Wander",
                            "AUTO_CHAT" to "Activity Chat"
                        )

                        moveTypes.forEach { (type, desc) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { antiAfkType = type }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = antiAfkType == type,
                                    onClick = { antiAfkType = type },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = Color(0xFF1B1B1F),
                                        unselectedColor = Color(0xFFC3C6CF)
                                    )
                                )
                                Text(
                                    text = desc,
                                    color = if (antiAfkType == type) Color(0xFF1B1B1F) else Color(0xFF44474E),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Default,
                                    fontWeight = if (antiAfkType == type) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BentoBorder, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "DSL AUTOMATED SCRIPTS",
                                color = Color(0xFF1B1B1F),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Default
                            )
                            Text(
                                "Run custom loop macros sequentially",
                                color = Color(0xFF44474E),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Default
                            )
                        }
                        Switch(
                            checked = scriptsEnabled,
                            onCheckedChange = { scriptsEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF212121),
                                uncheckedThumbColor = Color(0xFF44474E),
                                uncheckedTrackColor = Color(0xFFF3F4F9)
                            )
                        )
                    }

                    if (scriptsEnabled && scripts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Select Assigned Execution Script Template:",
                            fontSize = 11.sp,
                            color = Color(0xFF44474E),
                            fontFamily = FontFamily.Default,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        scripts.forEach { s ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedScriptId = s.id }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedScriptId == s.id,
                                    onClick = { selectedScriptId = s.id },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = Color(0xFF1B1B1F),
                                        unselectedColor = Color(0xFFC3C6CF)
                                    )
                                )
                                Column(modifier = Modifier.padding(start = 8.dp)) {
                                    Text(
                                        text = s.name,
                                        color = if (selectedScriptId == s.id) Color(0xFF1B1B1F) else Color(0xFF44474E),
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Default,
                                        fontWeight = if (selectedScriptId == s.id) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BentoBorder, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "INTELLIGENT AI AUTO-RESPONDER",
                                color = Color(0xFF6750A4),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Default
                            )
                            Text(
                                "Uses Gemini to chat dynamically with other server players",
                                color = Color(0xFF44474E),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Default
                            )
                        }
                        Switch(
                            checked = aiAutoReplyEnabled,
                            onCheckedChange = { aiAutoReplyEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF6750A4),
                                uncheckedThumbColor = Color(0xFF44474E),
                                uncheckedTrackColor = Color(0xFFF3F4F9)
                            )
                        )
                    }

                    if (aiAutoReplyEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        TextField(
                            value = aiPersonality,
                            onValueChange = { aiPersonality = it },
                            label = { Text("Personality preset (e.g. Sarcastic builder bot)", fontSize = 11.sp) },
                            colors = textFieldColors(),
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Default, fontSize = 12.sp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF44474E)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, BentoBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("CANCEL", fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        val parsedPort = port.toIntOrNull() ?: 25565
                        val serializedTriggers = triggersMap.entries.joinToString(";;;") { "${it.key}:::${it.value}" }
                        onSave(
                            BotConfig(
                                id = botConfig?.id ?: 0,
                                name = name,
                                serverAddress = address,
                                serverPort = parsedPort,
                                username = username,
                                edition = edition,
                                antiAfkEnabled = antiAfkEnabled,
                                antiAfkType = antiAfkType,
                                scriptsEnabled = scriptsEnabled,
                                selectedScriptId = selectedScriptId,
                                avatarType = avatarType,
                                themeColorHex = themeColorHex,
                                customStatus = customStatus,
                                triggerResponses = serializedTriggers,
                                aiAutoReplyEnabled = aiAutoReplyEnabled,
                                aiPersonality = aiPersonality
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF212121),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("submit_bot_button")
                ) {
                    Text("SAVE BOT", fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ScriptEditorTab(
    scripts: List<CustomScript>,
    onSaveScript: (name: String, content: String) -> Unit,
    onDeleteScript: (CustomScript) -> Unit,
    onImportScript: (url: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) -> Unit,
    onFetchRepo: (urlInput: String, onSuccess: (List<BotViewModel.GithubRepoFile>) -> Unit, onError: (String) -> Unit) -> Unit,
    onAutoCreateBot: (name: String, host: String, port: Int, username: String) -> Unit,
    onGenerateAiScript: (prompt: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    var githubInput by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    var importSuccessMessage by remember { mutableStateOf<String?>(null) }

    // Repository elements state list
    var repoFiles by remember { mutableStateOf<List<BotViewModel.GithubRepoFile>>(emptyList()) }
    var selectedRepoFile by remember { mutableStateOf<String?>(null) }

    // Connection auto extraction
    var detectedBotInfo by remember { mutableStateOf<BotViewModel.ExtractedBotInfo?>(null) }
    var detectedFileName by remember { mutableStateOf<String?>(null) }
    var botCreationStatus by remember { mutableStateOf<String?>(null) }

    // AI Generator States
    var aiPrompt by remember { mutableStateOf("") }
    var isGeneratingAi by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf<String?>(null) }
    var generatedAiCode by remember { mutableStateOf<String?>(null) }

    fun isRepositoryUrl(input: String): Boolean {
        val trimmed = input.trim().lowercase(java.util.Locale.getDefault())
        if (trimmed.endsWith(".git")) return true
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            val path = trimmed.substringAfter("github.com/").trim('/')
            val parts = path.split("/")
            return parts.size == 2 || (parts.size == 3 && parts[2] == "tree")
        }
        val parts = trimmed.split("/")
        return parts.size == 2
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "DSL SCRIPTER PARSING ENGINE",
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1B1B1F),
                fontSize = 16.sp,
                fontFamily = FontFamily.Default,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "Use simple, powerful scripts to automate tasks on your private server natively. Commands are parsed sequentially.",
                fontSize = 11.sp,
                color = Color(0xFF44474E),
                fontFamily = FontFamily.Default,
                lineHeight = 16.sp
            )
            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.4f), modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "GitHub",
                            tint = Color(0xFF6750A4),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "IMPORT FROM GITHUB.COM",
                            color = Color(0xFF1B1B1F),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Default
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Paste a raw script link or a repository URL/path (e.g. 'ayushghbk-afk/Bot.ju') to search and download script code natively.",
                        fontSize = 11.sp,
                        color = Color(0xFF44474E),
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    TextField(
                        value = githubInput,
                        onValueChange = { githubInput = it },
                        placeholder = { Text("e.g. ayushghbk-afk/Bot.ju", color = Color(0xFF44474E).copy(alpha = 0.5f), fontSize = 11.sp) },
                        colors = textFieldColors(),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("github_import_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color(0xFF6750A4),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }

                        Button(
                            onClick = {
                                if (githubInput.isNotBlank()) {
                                    val cleanedUrl = githubInput.trim()
                                    isImporting = true
                                    importError = null
                                    importSuccessMessage = null
                                    repoFiles = emptyList()
                                    detectedBotInfo = null
                                    botCreationStatus = null

                                    val isRepo = isRepositoryUrl(cleanedUrl)
                                    if (isRepo) {
                                        onFetchRepo(cleanedUrl, { files ->
                                            isImporting = false
                                            repoFiles = files
                                            if (files.isEmpty()) {
                                                importError = "No files found in the specified GitHub repository."
                                            } else {
                                                importSuccessMessage = "Successfully fetched ${files.size} file(s) in repository! Choose a script below."
                                            }
                                        }, { errorMsg ->
                                            isImporting = false
                                            importError = errorMsg
                                        })
                                    } else {
                                        val finalUrl = if (cleanedUrl.startsWith("http://") || cleanedUrl.startsWith("https://")) {
                                            cleanedUrl
                                                .replace("github.com/", "raw.githubusercontent.com/")
                                                .replace("/blob/", "/")
                                        } else {
                                            "https://raw.githubusercontent.com/$cleanedUrl"
                                        }
                                        onImportScript(finalUrl, { downloadedText ->
                                            isImporting = false
                                            content = downloadedText
                                            val fileName = finalUrl.substringAfterLast("/").substringBefore(".")
                                            name = "GitHub: " + fileName.substringBefore("?").replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
                                            importSuccessMessage = "Successfully imported script code!"
                                            githubInput = ""

                                            // Parse credentials
                                            val hostRegex = """host\s*[:=]\s*['"]([^'"]+)['"]""".toRegex()
                                            val portRegex = """port\s*[:=]\s*(\d+)""".toRegex()
                                            val usernameRegex = """username\s*[:=]\s*['"]([^'"]+)['"]""".toRegex()

                                            val fileHost = hostRegex.find(downloadedText)?.groupValues?.get(1)
                                            val filePort = portRegex.find(downloadedText)?.groupValues?.get(1)?.toIntOrNull()
                                            val fileUsername = usernameRegex.find(downloadedText)?.groupValues?.get(1)

                                            if (fileHost != null || filePort != null || fileUsername != null) {
                                                detectedBotInfo = BotViewModel.ExtractedBotInfo(fileHost, filePort, fileUsername)
                                                detectedFileName = fileName
                                            }
                                        }, { errorMsg ->
                                            isImporting = false
                                            importError = errorMsg
                                        })
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF6750A4),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            enabled = githubInput.isNotBlank() && !isImporting,
                            modifier = Modifier.testTag("github_import_button")
                        ) {
                            Text("PULL & LOAD CODE", fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }

                    AnimatedVisibility(visible = importError != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFFDAD9))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "Error: ${importError ?: ""}",
                                color = Color(0xFFBA1A1A),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    AnimatedVisibility(visible = importSuccessMessage != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFE8F5E9))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = importSuccessMessage ?: "",
                                color = Color(0xFF2E7D32),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Default
                            )
                        }
                    }

                    // Display list of repo files when found
                    if (repoFiles.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "CHOOSE SCRIPT FILE TO IMPORT:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6750A4)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        repoFiles.forEach { file ->
                            val isSelected = selectedRepoFile == file.name
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) Color(0xFFE8DEF8) else Color(0xFFF3F4F9))
                                    .clickable {
                                        selectedRepoFile = file.name
                                        isImporting = true
                                        importError = null
                                        importSuccessMessage = null
                                        detectedBotInfo = null
                                        onImportScript(file.downloadUrl, { downloadedText ->
                                            isImporting = false
                                            content = downloadedText
                                            val fileNameCleaned = file.name.substringBefore(".")
                                            name = "GitHub: " + fileNameCleaned.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
                                            importSuccessMessage = "Successfully imported script code from ${file.name}!"

                                            // Parse credentials
                                            val hostRegex = """host\s*[:=]\s*['"]([^'"]+)['"]""".toRegex()
                                            val portRegex = """port\s*[:=]\s*(\d+)""".toRegex()
                                            val usernameRegex = """username\s*[:=]\s*['"]([^'"]+)['"]""".toRegex()

                                            val fileHost = hostRegex.find(downloadedText)?.groupValues?.get(1)
                                            val filePort = portRegex.find(downloadedText)?.groupValues?.get(1)?.toIntOrNull()
                                            val fileUsername = usernameRegex.find(downloadedText)?.groupValues?.get(1)

                                            if (fileHost != null || filePort != null || fileUsername != null) {
                                                detectedBotInfo = BotViewModel.ExtractedBotInfo(fileHost, filePort, fileUsername)
                                                detectedFileName = file.name
                                            }
                                        }, { errorMsg ->
                                            isImporting = false
                                            importError = errorMsg
                                        })
                                    }
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) Color(0xFF6750A4) else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (file.name.endsWith(".js")) Icons.Default.Build 
                                                  else if (file.name.endsWith(".json")) Icons.Default.Settings 
                                                  else Icons.Default.List,
                                    contentDescription = "File",
                                    tint = if (isSelected) Color(0xFF6750A4) else Color(0xFF44474E),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = file.name,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1B1B1F)
                                    )
                                    if (file.size > 0) {
                                        Text(
                                            text = "${(file.size / 1024.0).toString().take(4)} KB",
                                            fontSize = 9.sp,
                                            color = Color(0xFF44474E)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Display extracted Bot credentials
                    if (detectedBotInfo != null) {
                        val info = detectedBotInfo!!
                        val hasHost = !info.host.isNullOrBlank()
                        val hasPort = info.port != null
                        val hasUser = !info.username.isNullOrBlank()

                        if (hasHost || hasPort || hasUser) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Color(0xFFFFB300), RoundedCornerShape(16.dp))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = "Extracted Settings Info",
                                            tint = Color(0xFFFF8F00),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "EXTRACTED BOT CONNECTION CREDENTIALS",
                                            color = Color(0xFFFF8F00),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "We discovered virtual connection credentials inside '${detectedFileName ?: "script"}':",
                                        fontSize = 11.sp,
                                        color = Color(0xFF5D4037)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    if (hasHost) {
                                        Text(
                                            text = "📍 Bot Server Address: ${info.host}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = Color(0xFF3E2723)
                                        )
                                    }
                                    if (hasPort) {
                                        Text(
                                            text = "🔌 Connection Port: ${info.port}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = Color(0xFF3E2723)
                                        )
                                    }
                                    if (hasUser) {
                                        Text(
                                            text = "👤 Bot Username: ${info.username}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = Color(0xFF3E2723)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Button(
                                        onClick = {
                                            val hostVal = info.host ?: "localhost"
                                            val portVal = info.port ?: 25565
                                            val userVal = info.username ?: "FlyBot"
                                            onAutoCreateBot(
                                                userVal,
                                                hostVal,
                                                portVal,
                                                userVal
                                            )
                                            botCreationStatus = "Successfully registered and preconfigured '${userVal}' bot profile in the database!"
                                            detectedBotInfo = null
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFFF8F00),
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("AUTO-INITIALIZE PLAY BOT PROFILE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    if (botCreationStatus != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFE8F5E9))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = botCreationStatus ?: "",
                                color = Color(0xFF2E7D32),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Default
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8DEF8)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFC3C6CF).copy(alpha = 0.5f), RoundedCornerShape(24.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        "SCRIPT PARSING DOCUMENTATION",
                        color = Color(0xFF1D192B),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Default,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "• SAY <message> : Broadcast server command or chat.\n" +
                        "• DELAY <millis> : Wait for server response/cooldown.\n" +
                        "• LOOP_START / LOOP_END : Continuously repeat loops.",
                        fontSize = 11.sp,
                        color = Color(0xFF1D192B).copy(alpha = 0.85f),
                        lineHeight = 16.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EDF7)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, Color(0xFFD0BCFF), RoundedCornerShape(24.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "AI Icon",
                            tint = Color(0xFF6750A4),
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "INTELLIGENT AI SCRIPT COPILOT",
                            color = Color(0xFF21005D),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Default,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Powered by Gemini 3.5 Flash server-side. Type what your companion bot should perform on the server, and the AI will auto-generate native commands.",
                        fontSize = 11.sp,
                        color = Color(0xFF49454F),
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "QUICK AI DIRECTIVES:",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6750A4)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "Walk in Circle" to "LOOP_START\nSAY /move forward\nDELAY 1000\nSAY /turn right\nDELAY 1000\nLOOP_END",
                            "Ad Broadcaster" to "LOOP_START\nSAY Join my awesome guild at /warp team!\nDELAY 600000\nLOOP_END",
                            "Auto Survival AFK" to "LOOP_START\nSAY [AFK] Maintaining live companion session...\nDELAY 3000\nSAY /eat potato\nDELAY 5000\nLOOP_END"
                        ).forEach { (label, mockCode) ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White)
                                    .clickable {
                                        aiPrompt = "Create a script that: $label"
                                        generatedAiCode = mockCode
                                        aiError = null
                                    }
                                    .border(1.dp, Color(0xFFE8DEF8), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(label, fontSize = 10.sp, color = Color(0xFF6750A4), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextField(
                        value = aiPrompt,
                        onValueChange = { aiPrompt = it },
                        placeholder = { Text("Describe actions (e.g., login, type spawn, wait 5 seconds and loop spamming advertisements)", color = Color(0xFF49454F).copy(alpha = 0.5f), fontSize = 11.sp) },
                        colors = textFieldColors(),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Default, fontSize = 11.sp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_prompt_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isGeneratingAi) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color(0xFF6750A4),
                                    strokeWidth = 2.dp
                                )
                                Text("Thinking...", fontSize = 11.sp, color = Color(0xFF6750A4))
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }

                        Button(
                            onClick = {
                                if (aiPrompt.isNotBlank()) {
                                    isGeneratingAi = true
                                    aiError = null
                                    generatedAiCode = null
                                    onGenerateAiScript(aiPrompt, { result ->
                                        isGeneratingAi = false
                                        generatedAiCode = result
                                    }, { errorMsg ->
                                        isGeneratingAi = false
                                        aiError = errorMsg
                                    })
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF6750A4),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            enabled = aiPrompt.isNotBlank() && !isGeneratingAi,
                            modifier = Modifier.testTag("ai_generate_button")
                        ) {
                            Text("ASK AI ENGINE", fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }

                    AnimatedVisibility(visible = aiError != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFFDAD9))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "AI Error: ${aiError ?: ""}",
                                color = Color(0xFFBA1A1A),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    AnimatedVisibility(visible = generatedAiCode != null) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            Text(
                                "GENERATED BOT SCRIPTER COMMANDS:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF21005D)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF111115), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = generatedAiCode ?: "",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    content = generatedAiCode ?: ""
                                    name = if (aiPrompt.length > 5 && aiPrompt.contains(" ")) {
                                        aiPrompt.substringAfter("that: ").substringAfter(" ").substringBefore(" ").replaceFirstChar { it.uppercase() } + " Script"
                                    } else {
                                        "AI Custom Script"
                                    }
                                    generatedAiCode = null
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF21005D),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("APPLY GENERATED CODE TO CUSTOM EDITOR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        "WRITE A CUSTOM SCRIPT",
                        color = Color(0xFF1B1B1F),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Default
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    TextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Script Title", fontFamily = FontFamily.Default) },
                        colors = textFieldColors(),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium),
                        modifier = Modifier.fillMaxWidth().testTag("input_script_name")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    TextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("Code Content (SAY / DELAY)", fontFamily = FontFamily.Default) },
                        colors = textFieldColors(),
                        placeholder = { Text("DELAY 2000\nSAY /login master123\nSAY /spawn", color = Color(0xFF44474E).copy(alpha = 0.5f), fontFamily = FontFamily.Monospace) },
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .testTag("input_script_content")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (name.isNotEmpty() && content.isNotEmpty()) {
                                onSaveScript(name, content)
                                name = ""
                                content = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF212121),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.align(Alignment.End).testTag("save_script_btn")
                    ) {
                        Text("COMPILE & SAVE", fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Text(
                text = "PERSISTED SCRIPT TEMPLATES",
                fontWeight = FontWeight.Bold,
                color = Color(0xFF44474E),
                fontSize = 12.sp,
                fontFamily = FontFamily.Default,
                letterSpacing = 0.5.sp
            )
        }

        if (scripts.isEmpty()) {
            item {
                Text(
                    "No templates created.",
                    fontSize = 12.sp,
                    color = Color(0xFF44474E),
                    fontFamily = FontFamily.Default
                )
            }
        } else {
            items(scripts) { s ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BentoBorder, RoundedCornerShape(24.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                s.name,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1B1B1F),
                                fontFamily = FontFamily.Default,
                                fontSize = 14.sp
                            )
                            IconButton(onClick = { onDeleteScript(s) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Script",
                                    tint = TerminalRubyRed
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF111115), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                s.content,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color.White,
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConsoleTab(
    bots: List<BotConfig>,
    selectedBotId: Int?,
    onBotSelected: (Int) -> Unit,
    viewModel: BotViewModel
) {
    val activeBotLogs = remember(selectedBotId) {
        if (selectedBotId != null) {
            viewModel.getLogsForBot(selectedBotId)
        } else {
            flowOf(emptyList())
        }
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val state = activeBotLogs.value

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "REAL-TIME LOG CONSOLE",
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1B1B1F),
            fontSize = 16.sp,
            fontFamily = FontFamily.Default,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Target Bot Switcher Tabs
        if (bots.isNotEmpty()) {
            ScrollableTabRow(
                selectedTabIndex = bots.indexOfFirst { it.id == selectedBotId }.coerceAtLeast(0),
                containerColor = Color.White,
                contentColor = Color(0xFF1B1B1F),
                edgePadding = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BentoBorder, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
            ) {
                bots.forEach { b ->
                    Tab(
                        selected = selectedBotId == b.id,
                        onClick = { onBotSelected(b.id) },
                        text = {
                            Text(
                                b.name,
                                fontFamily = FontFamily.Default,
                                fontWeight = if (selectedBotId == b.id) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedBotId == b.id) Color(0xFF1B1B1F) else Color(0xFF44474E),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Terminal Output Screen
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111115)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .border(1.dp, Color(0xFF1E1E24), RoundedCornerShape(24.dp))
        ) {
            if (selectedBotId == null || bots.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No bot logs online. Start a bot to inspect logging pipeline.",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            } else if (state.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        ">_ Initializing socket stdout listeners...\nWaiting for activity sequence.",
                        color = Color(0xFF4ADE80),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    reverseLayout = true // Terminal style: latest log at the bottom
                ) {
                    items(state) { log ->
                        ConsoleLogLine(log)
                    }
                }
            }
        }

        if (selectedBotId != null && bots.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            var directCommandText by remember { mutableStateOf("") }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = directCommandText,
                    onValueChange = { directCommandText = it },
                    placeholder = { Text("Send terminal SAY command / msg", fontFamily = FontFamily.Default, fontSize = 11.sp) },
                    colors = textFieldColors(),
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("terminal_command_input")
                )

                Button(
                    onClick = {
                        if (directCommandText.isNotEmpty() && selectedBotId != null) {
                            val textToSend = directCommandText
                            directCommandText = ""
                            viewModel.sendManualCommand(selectedBotId, textToSend)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF212121),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("send_command_button")
                ) {
                    Text("SEND", fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }

                Button(
                    onClick = {
                        if (directCommandText.isNotEmpty() && selectedBotId != null) {
                            val textToSend = directCommandText
                            directCommandText = ""
                            viewModel.chatWithAi(selectedBotId, textToSend)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6750A4),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("ask_ai_button")
                ) {
                    Text("ASK AI", fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }

                Button(
                    onClick = { viewModel.clearLogs(selectedBotId) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFEAEC),
                        contentColor = Color(0xFFBA1A1A)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFBA1A1A).copy(alpha = 0.2f))
                ) {
                    Text("CLEAR", fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun ConsoleLogLine(log: BotLog) {
    val timestampFormatted = remember(log.timestamp) {
        val date = java.util.Date(log.timestamp)
        val format = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        format.format(date)
    }

    val typeColor = when (log.type) {
        "ERROR" -> TerminalRubyRed
        "CHAT" -> TerminalCyanAccent
        "SCRIPT" -> TerminalGoldYellow
        "AI" -> Color(0xFFD0BCFF)
        else -> Color(0xFF4ADE80)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        // Timestamp
        Text(
            text = "[$timestampFormatted] ",
            color = Color.Gray,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        // Log Type Tag
        Text(
            text = "[${log.type}] ",
            color = typeColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        // Log Content
        Text(
            text = log.message,
            color = if (log.type == "AI") Color(0xFFE6E1E5) else Color.White,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun textFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color(0xFFFFFFFF),
    unfocusedContainerColor = Color(0xFFFFFFFF),
    disabledContainerColor = Color(0xFFF3F4F9),
    focusedIndicatorColor = Color(0xFF1B1B1F),
    unfocusedIndicatorColor = Color(0xFFC3C6CF),
    focusedLabelColor = Color(0xFF1B1B1F),
    unfocusedLabelColor = Color(0xFF44474E),
    focusedTextColor = Color(0xFF1B1B1F),
    unfocusedTextColor = Color(0xFF1B1B1F)
)

fun <T> flowOf(value: T): kotlinx.coroutines.flow.Flow<T> = kotlinx.coroutines.flow.flow { emit(value) }

fun isBatteryOptimizationsIgnored(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
        pm.isIgnoringBatteryOptimizations(context.packageName)
    } else {
        true
    }
}

fun requestIgnoreBatteryOptimizations(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }
}
