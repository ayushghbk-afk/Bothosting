package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.service.MineBotService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType

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
                } else if (currentConfigs.none { it.serverAddress.equals("Ragebaitrebels.aternos.me", ignoreCase = true) }) {
                    repository.insertConfig(
                        BotConfig(
                            name = "Ragebait Rebels Java",
                            serverAddress = "Ragebaitrebels.aternos.me",
                            serverPort = 56690,
                            username = "RageRebel",
                            edition = "JAVA",
                            version = "1.20.4",
                            scriptsEnabled = false,
                            selectedScriptId = null,
                            antiAfkEnabled = true,
                            antiAfkType = "CIRCLE",
                            autoReconnect = true
                        )
                    )
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
        val ragebaitRebels = BotConfig(
            name = "Ragebait Rebels Java",
            serverAddress = "Ragebaitrebels.aternos.me",
            serverPort = 56690,
            username = "RageRebel",
            edition = "JAVA",
            version = "1.20.4",
            scriptsEnabled = false,
            selectedScriptId = null,
            antiAfkEnabled = true,
            antiAfkType = "CIRCLE",
            autoReconnect = true
        )
        repository.insertConfig(defaultJava)
        repository.insertConfig(defaultBedrock)
        repository.insertConfig(ragebaitRebels)
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

    data class GithubRepoFile(val name: String, val downloadUrl: String, val size: Long)

    fun importScriptFromGithub(url: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val urlObj = java.net.URL(url)
                    val conn = urlObj.openConnection() as java.net.HttpURLConnection
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; MineBot Companion)")
                    if (conn.responseCode != 200) {
                        throw Exception("HTTP returned error code ${conn.responseCode}: ${conn.responseMessage}")
                    }
                    conn.inputStream.bufferedReader().readText()
                }
                onSuccess(text)
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Unknown network timeout or address error")
            }
        }
    }

    fun parseGithubContentsJson(jsonStr: String): List<GithubRepoFile> {
        val list = mutableListOf<GithubRepoFile>()
        try {
            val array = org.json.JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val type = obj.optString("type")
                if (type == "file") {
                    val name = obj.optString("name")
                    val downloadUrl = obj.optString("download_url")
                    val size = obj.optLong("size", 0L)
                    if (name.isNotEmpty() && downloadUrl.isNotEmpty()) {
                        list.add(GithubRepoFile(name, downloadUrl, size))
                    }
                }
            }
        } catch (e: Exception) {
            // RegEx fallback
            val regex = "\"name\"\\s*:\\s*\"([^\"]+)\"[^}]*\"download_url\"\\s*:\\s*\"([^\"]+)\"".toRegex()
            regex.findAll(jsonStr).forEach { match ->
                val name = match.groupValues[1]
                val downloadUrl = match.groupValues[2]
                list.add(GithubRepoFile(name, downloadUrl, 0L))
            }
        }
        return list
    }

    fun fetchGithubRepoContents(urlInput: String, onSuccess: (List<GithubRepoFile>) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val clean = urlInput.trim()
                    .removePrefix("http://")
                    .removePrefix("https://")
                    .removePrefix("www.")
                    .removePrefix("github.com/")
                    .removeSuffix(".git")
                
                val parts = clean.split("/")
                if (parts.size < 2) {
                    throw Exception("Invalid GitHub repository path: Enter as 'owner/repo' or paste whole URL")
                }
                val owner = parts[0]
                val repo = parts[1]

                val fileList = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val apiUrl = "https://api.github.com/repos/$owner/$repo/contents"
                    val urlObj = java.net.URL(apiUrl)
                    val conn = urlObj.openConnection() as java.net.HttpURLConnection
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; MineBot Companion)")
                    conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                    
                    if (conn.responseCode != 200) {
                        throw Exception("GitHub API error code ${conn.responseCode}: ${conn.responseMessage}")
                    }
                    
                    val text = conn.inputStream.bufferedReader().readText()
                    parseGithubContentsJson(text)
                }
                onSuccess(fileList)
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Unknown GitHub repository access error")
            }
        }
    }

    data class ExtractedBotInfo(val host: String?, val port: Int?, val username: String?)

    fun extractBotInfoFromScript(content: String): ExtractedBotInfo {
        // Match expressions like: host: 'ragebaitrebels.aternos.me' or host = "ragebaitrebels.aternos.me"
        val hostRegex = """host\s*[:=]\s*['"]([^'"]+)['"]""".toRegex()
        val portRegex = """port\s*[:=]\s*(\d+)""".toRegex()
        val usernameRegex = """username\s*[:=]\s*['"]([^'"]+)['"]""".toRegex()

        val host = hostRegex.find(content)?.groupValues?.get(1)
        val port = portRegex.find(content)?.groupValues?.get(1)?.toIntOrNull()
        val username = usernameRegex.find(content)?.groupValues?.get(1)

        return ExtractedBotInfo(host, port, username)
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

    fun generateScriptFromPrompt(prompt: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val apiKey = com.example.BuildConfig.GEMINI_API_KEY
                if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
                    throw Exception("Please configure your GEMINI_API_KEY in the Secrets panel in AI Studio with a valid Google Gemini API Key.")
                }

                val systemPrompt = "You are an expert Minecraft bot command scripter. Convert the user's prompt into a clean series of bot script commands. Use ONLY the following valid commands:\n- SAY <message/command> (e.g. SAY /login master123, SAY walking in a circle...)\n- DELAY <millis> (e.g. DELAY 5000)\n- LOOP_START\n- LOOP_END\n\nEnsure there is NO markdown, NO code block backticks (e.g. do NOT wrap in ```), NO explanations, NO introductory or concluding text. Output ONLY the raw commands, each on a new line."

                val escapedPrompt = prompt
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "")

                val requestJson = """
                    {
                      "contents": [
                        {
                          "parts": [
                            {
                              "text": "User intent: $escapedPrompt"
                            }
                          ]
                        }
                      ],
                      "systemInstruction": {
                        "parts": [
                          {
                            "text": "${systemPrompt.replace("\n", "\\n")}"
                          }
                        ]
                      },
                      "generationConfig": {
                        "temperature": 0.2
                      }
                    }
                """.trimIndent()

                val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .build()

                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val body = okhttp3.RequestBody.create(mediaType, requestJson)
                    val request = okhttp3.Request.Builder()
                        .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey")
                        .post(body)
                        .header("Content-Type", "application/json")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw Exception("Gemini API returned error code ${response.code}")
                        }
                        val responseString = response.body?.string() ?: throw Exception("Empty response from AI engine")
                        
                        var textVal = ""
                        val textToken = "\"text\":"
                        var currentSearchIndex = 0
                        while (true) {
                            val textStartIndex = responseString.indexOf(textToken, currentSearchIndex)
                            if (textStartIndex == -1) break
                            val valStartIndex = responseString.indexOf('"', textStartIndex + textToken.length)
                            if (valStartIndex != -1) {
                                val valEndIndex = responseString.indexOf('"', valStartIndex + 1)
                                if (valEndIndex != -1) {
                                    val piece = responseString.substring(valStartIndex + 1, valEndIndex)
                                    textVal += piece
                                        .replace("\\n", "\n")
                                        .replace("\\\"", "\"")
                                        .replace("\\t", "\t")
                                    currentSearchIndex = valEndIndex + 1
                                } else {
                                    break
                                }
                            } else {
                                break
                            }
                        }

                        if (textVal.isBlank()) {
                            throw Exception("AI generated an empty response or invalid JSON formatting.")
                        }

                        var cleanedText = textVal.trim()
                        if (cleanedText.startsWith("```")) {
                            cleanedText = cleanedText.substringAfter("\n")
                            if (cleanedText.endsWith("```")) {
                                cleanedText = cleanedText.substringBeforeLast("```")
                            }
                        }
                        cleanedText.trim()
                    }
                }
                onSuccess(result)
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Unknown AI communication error occurred.")
            }
        }
    }

    fun chatWithAi(botId: Int, prompt: String) {
        viewModelScope.launch {
            repository.insertLog(
                BotLog(
                    botId = botId,
                    message = "User prompt: $prompt",
                    type = "CHAT"
                )
            )
            try {
                val apiKey = com.example.BuildConfig.GEMINI_API_KEY
                if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
                    repository.insertLog(
                        BotLog(
                            botId = botId,
                            message = "System error: GEMINI_API_KEY not configured. Register it in the Secrets panel in AI Studio.",
                            type = "ERROR"
                        )
                    )
                    return@launch
                }

                val systemPrompt = "You are MineBot AI Assistant, a helpful Minecraft chat/commands advisor built as a companion chatbot into the MineBot application. Help the player understand commands, customize scripts, troubleshoot connection blocks, or general survival rules. Keep responses concise, simple, highly structured, and under 3 short paragraphs."
                val requestPayload = """
                    {
                      "contents": [
                        {
                          "parts": [
                            {
                              "text": "User says: $prompt"
                            }
                          ]
                        }
                      ],
                      "systemInstruction": {
                        "parts": [
                          {
                            "text": "${systemPrompt.replace("\n", "\\n")}"
                          }
                        ]
                      },
                      "generationConfig": {
                        "temperature": 0.7,
                        "maxOutputTokens": 300
                      }
                    }
                """.trimIndent()

                val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val body = okhttp3.RequestBody.create(mediaType, requestPayload)
                    val request = okhttp3.Request.Builder()
                        .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey")
                        .post(body)
                        .header("Content-Type", "application/json")
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw Exception("HTTP Code ${response.code}")
                        val json = response.body?.string() ?: ""
                        var textVal = ""
                        val textToken = "\"text\":"
                        var idx = 0
                        while (true) {
                            val start = json.indexOf(textToken, idx)
                            if (start == -1) break
                            val quoteStart = json.indexOf('"', start + textToken.length)
                            if (quoteStart != -1) {
                                val quoteEnd = json.indexOf('"', quoteStart + 1)
                                if (quoteEnd != -1) {
                                    textVal += json.substring(quoteStart + 1, quoteEnd)
                                        .replace("\\n", "\n")
                                        .replace("\\\"", "\"")
                                        .replace("\\t", "    ")
                                    idx = quoteEnd + 1
                                } else break
                            } else break
                        }
                        textVal
                    }
                }

                if (result.isNotBlank()) {
                    repository.insertLog(
                        BotLog(
                            botId = botId,
                            message = result,
                            type = "AI"
                        )
                    )
                } else {
                    repository.insertLog(
                        BotLog(
                            botId = botId,
                            message = "System error: Received empty response from AI model companion.",
                            type = "ERROR"
                        )
                    )
                }
            } catch (e: Exception) {
                repository.insertLog(
                    BotLog(
                        botId = botId,
                        message = "AI connection error: ${e.localizedMessage ?: "Unknown fault"}",
                        type = "ERROR"
                    )
                )
            }
        }
    }
}
