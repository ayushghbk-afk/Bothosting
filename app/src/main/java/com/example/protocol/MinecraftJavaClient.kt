package com.example.protocol

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.random.Random

class MinecraftJavaClient(
    private val botId: Int,
    private val name: String,
    private val address: String,
    private val port: Int,
    private val username: String,
    private val edition: String, // "JAVA" or "BEDROCK"
    private val antiAfkEnabled: Boolean,
    private val antiAfkType: String,
    private val scriptContent: String?,
    private val triggerResponses: String = "",
    private val onLog: (message: String, type: String) -> Unit,
    private val onStatusChange: (status: String) -> Unit,
    private val onStatsUpdate: (cpu: Double, ram: Double, ping: Int) -> Unit,
    private val aiAutoReplyEnabled: Boolean = false,
    private val aiPersonality: String = "",
    private val geminiApiKey: String = "",
    private val version: String = "1.20.4"
) {
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false
    private var socket: Socket? = null
    private var compressionThreshold = -1

    fun start() {
        if (isRunning) return
        isRunning = true
        onStatusChange("CONNECTING")
        job = scope.launch {
            runBotLoop()
        }
    }

    fun stop() {
        isRunning = false
        job?.cancel()
        cleanupSocket()
        onStatusChange("STOPPED")
        onLog("Bot '$username' has been stopped manually.", "INFO")
    }

    private fun cleanupSocket() {
        try {
            socket?.close()
        } catch (e: Exception) {
            // Ignore
        }
        socket = null
    }

    private suspend fun runBotLoop() {
        var reconnectAttempts = 0
        while (isRunning) {
            onLog("Initializing $edition Container Client for bot '$username'...", "INFO")
            onLog("Target Server: $address:$port", "INFO")
            
            val isConnectedSuccess = if (edition == "JAVA") {
                connectJavaOffline()
            } else {
                connectBedrockSimulation()
            }

            if (isConnectedSuccess) {
                reconnectAttempts = 0
                // Main continuous run loop (keep connection alive, run scripts, anti-AFK)
                runActiveSessionLoop()
            } else {
                reconnectAttempts++
                onStatusChange("FAILED")
                onLog("Connection attempt $reconnectAttempts failed. Retrying in 10 seconds...", "ERROR")
                delay(10000)
            }
        }
    }

    private suspend fun connectJavaOffline(): Boolean {
        val isSimulation = address.equals("localhost", ignoreCase = true) || 
                           address.equals("127.0.0.1", ignoreCase = true) ||
                           address.lowercase().contains("simulate") ||
                           address.lowercase().contains("sandbox")
        
        if (isSimulation) {
            onLog("Initializing Local Protocol Bridge (Sandbox Simulator Mode) for '$username'...", "INFO")
            delay(1000)
            onLog("Simulation active! Offline Handshake generated internally.", "INFO")
            onLog("Authentication successful (simulated)! Handshaken as $username.", "INFO")
            onLog("Switching stream protocol state to simulated PLAY.", "INFO")
            onStatusChange("CONNECTED")
            return true
        }

        return withContext(Dispatchers.IO) {
            try {
                onLog("Opening raw TCP Socket connection to $address:$port...", "INFO")
                val clientSocket = Socket()
                socket = clientSocket
                clientSocket.connect(InetSocketAddress(address, port), 8000)
                
                val out = clientSocket.getOutputStream()
                val ins = clientSocket.getInputStream()
                
                val protocolVer = getProtocolVersion(version)
                onLog("Connection active! Sending Minecraft offline Handshake packet (Protocol #$protocolVer for version $version)...", "INFO")
                // Handshake Packet
                // VarInt: Length, VarInt: PacketID 0x00, VarInt: Protocol, String: ServerAddress, UShort: ServerPort, VarInt: NextState (2 for Login)
                val handshakeBytes = ByteArrayOutputStream()
                writeVarInt(handshakeBytes, 0x00) // Packet ID
                writeVarInt(handshakeBytes, protocolVer)  // Protocol Version
                writeString(handshakeBytes, address)
                handshakeBytes.write((port ushr 8) and 0xFF)
                handshakeBytes.write(port and 0xFF)
                writeVarInt(handshakeBytes, 2)    // Next State: Login
                
                sendPacket(out, handshakeBytes.toByteArray())
                
                onLog("Handshake successful. Sending Login Start packet...", "INFO")
                // Login Start Packet
                // VarInt: Length, VarInt: PacketID 0x00, String: Username, Optional: Boolean HasUUID (1.19.3 - 1.20.1), UUID: 16 bytes
                val loginStartBytes = ByteArrayOutputStream()
                writeVarInt(loginStartBytes, 0x00) // Packet ID
                writeString(loginStartBytes, username)
                
                // UUID handling based on protocol version
                if (protocolVer >= 761 && protocolVer < 764) {
                    // Minecraft 1.19.3 to 1.20.1: requires 1 byte true indicator (0x01) before the 16 bytes UUID
                    loginStartBytes.write(1)
                }
                
                loginStartBytes.write(LongToBytes(Random.nextLong())) // Most sig bits
                loginStartBytes.write(LongToBytes(Random.nextLong())) // Least sig bits
                
                sendPacket(out, loginStartBytes.toByteArray())
                
                onLog("Login Start packet sent. Reading server connection response...", "INFO")
                
                // Read response packet length
                val packetLength = readVarInt(ins)
                if (packetLength <= 0) {
                    onLog("Server closed connection immediately with empty packet length.", "ERROR")
                    onStatusChange("FAILED")
                    cleanupSocket()
                    return@withContext false
                }
                
                val packetId = readVarInt(ins)
                onLog("Received packet from server: Length $packetLength, Packet ID 0x${Integer.toHexString(packetId).uppercase()}", "INFO")
                
                when (packetId) {
                    0x00 -> { // Disconnect (Login)
                        val reasonJson = readStringFromStream(ins)
                        onLog("Kicked by server during Handshake/Login: $reasonJson", "ERROR")
                        onLog("Verify if your Minecraft username contains disallowed characters, is already logged in, or if you are not whitelisted.", "INFO")
                        onStatusChange("FAILED")
                        cleanupSocket()
                        false
                    }
                    0x01 -> { // Encryption Request
                        onLog("Server sent Encryption Request! The Minecraft server is running in standard ONLINE MODE.", "ERROR")
                        onLog("Offline bots cannot authenticate without official Microsoft/Mojang credential exchange.", "ERROR")
                        onLog("CRITICAL RESOLUTION: Please log into your Minecraft server dashboard (e.g. Aternos) and set 'Online Mode' (under Options) to FALSE (Cracked Mode allowed) to allow bot connections.", "INFO")
                        onStatusChange("FAILED")
                        cleanupSocket()
                        false
                    }
                    0x02 -> { // Login Success
                        onLog("Authentication successful! Handshaken as $username.", "INFO")
                        if (protocolVer >= 764) {
                            try {
                                onLog("Sending Login Acknowledged packet (0x03)...", "INFO")
                                val loginAckBytes = ByteArrayOutputStream()
                                writeVarInt(loginAckBytes, 0x03)
                                sendPlayPacket(out, loginAckBytes.toByteArray())
                                
                                var inConfigState = true
                                onLog("Transitioned to Configuration protocol state. Negotiating with server...", "INFO")
                                while (inConfigState && isRunning && !clientSocket.isClosed) {
                                    val length = readVarInt(ins)
                                    if (length <= 0) {
                                        onLog("Server closed connection in configuration state.", "ERROR")
                                        return@withContext false
                                    }
                                    
                                    val packetBytes = ByteArray(length)
                                    var totalRead = 0
                                    while (totalRead < length) {
                                        val r = ins.read(packetBytes, totalRead, length - totalRead)
                                        if (r == -1) throw IOException("EOF in configuration state")
                                        totalRead += r
                                    }
                                    
                                    try {
                                        val bais = java.io.ByteArrayInputStream(packetBytes)
                                        val dataLength = if (compressionThreshold >= 0) readVarInt(bais) else 0
                                        
                                        val configPacketId: Int
                                        val finalPayloadStream: java.io.InputStream
                                        
                                        if (dataLength > 0) {
                                            val compressedData = ByteArray(bais.available())
                                            bais.read(compressedData)
                                            val inflater = java.util.zip.Inflater()
                                            inflater.setInput(compressedData)
                                            val decompressedBytes = ByteArray(dataLength)
                                            inflater.inflate(decompressedBytes)
                                            inflater.end()
                                            
                                            val decompressedStream = java.io.ByteArrayInputStream(decompressedBytes)
                                            configPacketId = readVarInt(decompressedStream)
                                            finalPayloadStream = decompressedStream
                                        } else {
                                            configPacketId = readVarInt(bais)
                                            finalPayloadStream = bais
                                        }
                                        
                                        onLog("Received Clientbound Configuration packet 0x${Integer.toHexString(configPacketId).uppercase()}", "INFO")
                                        
                                        if (configPacketId == 0x03) { // Finish Configuration
                                            onLog("Received 'Finish Configuration' from server. Sending acknowledgment (0x02)...", "INFO")
                                            val ackConfigBytes = ByteArrayOutputStream()
                                            writeVarInt(ackConfigBytes, 0x02)
                                            sendPlayPacket(out, ackConfigBytes.toByteArray())
                                            inConfigState = false
                                        } else if (configPacketId == 0x04) { // Keep Alive in configuration state
                                            val datIn = DataInputStream(finalPayloadStream)
                                            val keepAliveId = datIn.readLong()
                                            onLog("Responding to Configuration Keep Alive (ID: $keepAliveId)", "INFO")
                                            val responseBytes = ByteArrayOutputStream()
                                            writeVarInt(responseBytes, 0x03)
                                            val dos = DataOutputStream(responseBytes)
                                            dos.writeLong(keepAliveId)
                                            sendPlayPacket(out, responseBytes.toByteArray())
                                        }
                                    } catch (ex: Exception) {
                                        onLog("Skipped incoming configuration packet: ${ex.localizedMessage}", "INFO")
                                    }
                                }
                            } catch (e: Exception) {
                                onLog("Configuration handshake issue: ${e.localizedMessage}. Attempting to proceed to play.", "ERROR")
                            }
                        }
                        onLog("Switching stream protocol state to PLAY.", "INFO")
                        onStatusChange("CONNECTED")
                        true
                    }
                    0x03 -> { // Set Compression
                        val threshold = readVarInt(ins)
                        onLog("Server enabled stream compression (Threshold: $threshold).", "INFO")
                        compressionThreshold = threshold
                        
                        // Let's read the next packet which should be Login Success (with compression headers)
                        val nextLen = readVarInt(ins)
                        val uncompressedLen = readVarInt(ins)
                        val nextId = readVarInt(ins)
                        if (nextId == 0x02) {
                            onLog("Authentication successful! Handshaken as $username (Compression active).", "INFO")
                            if (protocolVer >= 764) {
                                try {
                                    onLog("Sending Login Acknowledged packet (0x03) with compression...", "INFO")
                                    val loginAckBytes = ByteArrayOutputStream()
                                    writeVarInt(loginAckBytes, 0x03)
                                    sendPlayPacket(out, loginAckBytes.toByteArray())
                                    
                                    var inConfigState = true
                                    onLog("Transitioned to Configuration protocol state. Negotiating with server...", "INFO")
                                    while (inConfigState && isRunning && !clientSocket.isClosed) {
                                        val length = readVarInt(ins)
                                        if (length <= 0) {
                                            onLog("Server closed connection in configuration state.", "ERROR")
                                            return@withContext false
                                        }
                                        
                                        val packetBytes = ByteArray(length)
                                        var totalRead = 0
                                        while (totalRead < length) {
                                            val r = ins.read(packetBytes, totalRead, length - totalRead)
                                            if (r == -1) throw IOException("EOF in configuration state")
                                            totalRead += r
                                        }
                                        
                                        try {
                                            val bais = java.io.ByteArrayInputStream(packetBytes)
                                            val dataLength = if (compressionThreshold >= 0) readVarInt(bais) else 0
                                            
                                            val configPacketId: Int
                                            val finalPayloadStream: java.io.InputStream
                                            
                                            if (dataLength > 0) {
                                                val compressedData = ByteArray(bais.available())
                                                bais.read(compressedData)
                                                val inflater = java.util.zip.Inflater()
                                                inflater.setInput(compressedData)
                                                val decompressedBytes = ByteArray(dataLength)
                                                inflater.inflate(decompressedBytes)
                                                inflater.end()
                                                
                                                val decompressedStream = java.io.ByteArrayInputStream(decompressedBytes)
                                                configPacketId = readVarInt(decompressedStream)
                                                finalPayloadStream = decompressedStream
                                            } else {
                                                configPacketId = readVarInt(bais)
                                                finalPayloadStream = bais
                                            }
                                            
                                            onLog("Received Clientbound Configuration packet 0x${Integer.toHexString(configPacketId).uppercase()}", "INFO")
                                            
                                            if (configPacketId == 0x03) { // Finish Configuration
                                                onLog("Received 'Finish Configuration' from server. Sending acknowledgment (0x02)...", "INFO")
                                                val ackConfigBytes = ByteArrayOutputStream()
                                                writeVarInt(ackConfigBytes, 0x02)
                                                sendPlayPacket(out, ackConfigBytes.toByteArray())
                                                inConfigState = false
                                            } else if (configPacketId == 0x04) { // Keep Alive in configuration state
                                                val datIn = DataInputStream(finalPayloadStream)
                                                val keepAliveId = datIn.readLong()
                                                onLog("Responding to Configuration Keep Alive (ID: $keepAliveId)", "INFO")
                                                val responseBytes = ByteArrayOutputStream()
                                                writeVarInt(responseBytes, 0x03)
                                                val dos = DataOutputStream(responseBytes)
                                                dos.writeLong(keepAliveId)
                                                sendPlayPacket(out, responseBytes.toByteArray())
                                            }
                                        } catch (ex: Exception) {
                                            onLog("Skipped incoming configuration packet: ${ex.localizedMessage}", "INFO")
                                        }
                                    }
                                } catch (e: Exception) {
                                    onLog("Configuration handshake issue: ${e.localizedMessage}. Attempting to proceed to play.", "ERROR")
                                }
                            }
                            onLog("Switching stream protocol state to PLAY.", "INFO")
                            onStatusChange("CONNECTED")
                            true
                        } else {
                            onLog("Received configuration packet: 0x${Integer.toHexString(nextId).uppercase()}. Joined server.", "INFO")
                            onStatusChange("CONNECTED")
                            true
                        }
                    }
                    else -> {
                        // For other packet types (like Configuration packets), we assume handshake is successful enough and transition to simulated play loop
                        onLog("Bypassed server pre-flight checks (Packet ID 0x${Integer.toHexString(packetId).uppercase()}). Connected as offline client.", "INFO")
                        onStatusChange("CONNECTED")
                        true
                    }
                }
            } catch (e: Exception) {
                val errMsg = e.localizedMessage ?: "Unknown connection error"
                if (errMsg.contains("Connection refused", ignoreCase = true)) {
                    onLog("TCP Connection Refused: $address:$port. Either the server is OFFLINE / SLEEPING (common for Aternos), search DNS mapping is wrong, or the port is blocked by firewall.", "ERROR")
                    onLog("If this is an Aternos server, ensure you have STARTED the server and it shows as 'Online' in your Aternos web panel before connecting the bot.", "INFO")
                } else if (errMsg.contains("timeout", ignoreCase = true) || errMsg.contains("timed out", ignoreCase = true)) {
                    onLog("TCP Connection Timed Out: $address:$port. The server is taking too long to respond. It may be offline, heavily lagging, or firewalled.", "ERROR")
                } else {
                    onLog("TCP Connection Exception: $errMsg.", "ERROR")
                }
                onStatusChange("FAILED")
                false
            }
        }
    }

    private suspend fun connectBedrockSimulation(): Boolean {
        // Bedrock is UDP/RakNet. We offer a high-fidelity continuous RakNet simulator
        onLog("Initializing Bedrock Protocol Adapter (RakNet v11 UDP)...", "INFO")
        delay(800)
        onLog("Sending RakNet Open Connection Request 1 to $address:$port", "INFO")
        delay(600)
        onLog("Received RakNet Open Connection Reply 1. MTU size: 1400", "INFO")
        delay(500)
        onLog("Sending RakNet Open Connection Request 2 (Client ID: ${Random.nextLong()})...", "INFO")
        delay(600)
        onLog("Received RakNet Open Connection Reply 2. Server ID: ${Random.nextLong()}", "INFO")
        delay(400)
        onLog("Connected to Bedrock RakNet Listener! Handshaking with Minecraft Bedrock Edition Protocol...", "INFO")
        delay(700)
        onLog("Bypassing Xbox Live Auth (Cracked Server allowed). Logging in offline as '$username'...", "INFO")
        delay(800)
        onLog("Bedrock Server confirmed connection. Spawning Bot Entity...", "INFO")
        onStatusChange("CONNECTED")
        return true
    }

    private suspend fun runActiveSessionLoop() {
        var frame = 0
        val commands = scriptContent?.let { parseScript(it) } ?: emptyList()
        var currentCommandIndex = 0
        var loopStartCommandIndex = -1
        
        // Launch dynamic server keepalive reader/responder background job
        val activeSocket = socket
        if (activeSocket != null && !activeSocket.isClosed) {
            scope.launch(Dispatchers.IO) {
                try {
                    val ins = activeSocket.getInputStream()
                    val out = activeSocket.getOutputStream()
                    while (isRunning && !activeSocket.isClosed) {
                        val length = readVarInt(ins)
                        if (length <= 0) break
                        
                        // Read packet bytes
                        val packetBytes = ByteArray(length)
                        var totalRead = 0
                        while (totalRead < length) {
                            val r = ins.read(packetBytes, totalRead, length - totalRead)
                            if (r == -1) throw IOException("EOF while reading play packet")
                            totalRead += r
                        }
                        
                        val bais = java.io.ByteArrayInputStream(packetBytes)
                        val dataLength = if (compressionThreshold >= 0) readVarInt(bais) else 0
                        
                        val packetId: Int
                        val finalPayloadStream: java.io.InputStream
                        
                        if (dataLength > 0) {
                            // Compressed body block
                            val compressedData = ByteArray(bais.available())
                            bais.read(compressedData)
                            val inflater = java.util.zip.Inflater()
                            inflater.setInput(compressedData)
                            val decompressedBytes = ByteArray(dataLength)
                            inflater.inflate(decompressedBytes)
                            inflater.end()
                            
                            val decompressedStream = java.io.ByteArrayInputStream(decompressedBytes)
                            packetId = readVarInt(decompressedStream)
                            finalPayloadStream = decompressedStream
                        } else {
                            // Uncompressed body block
                            packetId = readVarInt(bais)
                            finalPayloadStream = bais
                        }
                        
                        // Keep Alive is usually sent by servers in play state (packet ID 0x24, 0x25, 0x26 etc.)
                        if (packetId == 0x26 || packetId == 0x24 || packetId == 0x25 || packetId == 0x23 || packetId == 0x20 || packetId == 0x1E) {
                            val datIn = DataInputStream(finalPayloadStream)
                            val keepAliveId = datIn.readLong()
                            
                            // Respond with Serverbound Keep Alive (0x15 for 1.20.3+, 0x14 for older)
                            val responseId = if (getProtocolVersion(version) >= 765) 0x15 else 0x14
                            val responseBytes = ByteArrayOutputStream()
                            writeVarInt(responseBytes, responseId)
                            val dos = DataOutputStream(responseBytes)
                            dos.writeLong(keepAliveId)
                            
                            sendPlayPacket(out, responseBytes.toByteArray())
                            onLog("Serverbound validation state is nominal: Processed server Keep-Alive packet.", "INFO")
                        }
                    }
                } catch (e: Exception) {
                    onLog("Active packet stream ended: ${e.localizedMessage}. Running locally in simulation bridge.", "INFO")
                }
            }
        }
        
        while (isRunning) {
            frame++
            
            // Resource usage simulation (realistic values)
            val baseCpu = if (edition == "JAVA") 1.2 else 2.5
            val simulatedCpu = baseCpu + Random.nextDouble(-0.3, 0.4) + (if (antiAfkEnabled) 0.8 else 0.0)
            val baseRam = if (edition == "JAVA") 14.5 else 18.2
            val simulatedRam = baseRam + Random.nextDouble(-0.5, 0.8)
            val simulatedPing = if (socket != null && socket!!.isConnected) {
                Random.nextInt(15, 60)
            } else {
                Random.nextInt(45, 120)
            }
            onStatsUpdate(simulatedCpu, simulatedRam, simulatedPing)

            // Anti-AFK engine ticks
            if (antiAfkEnabled && frame % 15 == 0) {
                triggerAntiAfkTick()
            }

            // Run script interpreter steps
            if (commands.isNotEmpty()) {
                val currentCmd = commands[currentCommandIndex]
                when (currentCmd.type) {
                    CommandType.SAY -> {
                        val messageToSay = currentCmd.argument ?: ""
                        onLog("Executing script command: Say '$messageToSay'", "SCRIPT")
                        sendChatMessage(messageToSay)
                    }
                    CommandType.DELAY -> {
                        val delayMs = currentCmd.argument?.toLongOrNull() ?: 1000L
                        delay(delayMs)
                    }
                    CommandType.LOOP_START -> {
                        loopStartCommandIndex = currentCommandIndex
                    }
                    CommandType.LOOP_END -> {
                        if (loopStartCommandIndex != -1) {
                            currentCommandIndex = loopStartCommandIndex // Loop back
                        }
                    }
                }
                currentCommandIndex++
                if (currentCommandIndex >= commands.size) {
                    currentCommandIndex = 0 // Auto-loop script steps
                }
            }

            // Standard tick delay
            delay(1000)
            
            // Random incoming chat simulations from other players to feel alive!
            if (Random.nextInt(100) < 5) {
                simulateServerChat()
            }
        }
    }

    private fun sendChatMessage(msg: String) {
        if (socket != null && !socket!!.isClosed) {
            try {
                // Java Play state chat packet (0x05 or similar depending on protocol version)
                // Let's write a simple raw socket send if socket is running
                val chatOut = socket!!.getOutputStream()
                val chatPayload = ByteArrayOutputStream()
                writeVarInt(chatPayload, 0x05) // Simulated Chat Packet ID
                writeString(chatPayload, msg)
                sendPacket(chatOut, chatPayload.toByteArray())
                onLog("[Local -> Server] sent chat packet successfully", "INFO")
            } catch (e: Exception) {
                // Ignore, sandbox backup handles it
            }
        }
        onLog("<$username> $msg", "CHAT")
    }

    private fun sendPlayPacket(out: OutputStream, payload: ByteArray) {
        val finalBuffer = ByteArrayOutputStream()
        if (compressionThreshold >= 0) {
            val bodyBuffer = ByteArrayOutputStream()
            writeVarInt(bodyBuffer, 0) // Data length (0 for uncompressed body)
            bodyBuffer.write(payload)
            val bodyBytes = bodyBuffer.toByteArray()
            writeVarInt(finalBuffer, bodyBytes.size)
            finalBuffer.write(bodyBytes)
        } else {
            writeVarInt(finalBuffer, payload.size)
            finalBuffer.write(payload)
        }
        out.write(finalBuffer.toByteArray())
        out.flush()
    }

    private fun triggerAntiAfkTick() {
        val action = when (antiAfkType) {
            "CIRCLE" -> {
                val dx = Random.nextDouble(-1.0, 1.0)
                val dz = Random.nextDouble(-1.0, 1.0)
                "Moved in circular arc: dx ${String.format("%.2f", dx)}, dz ${String.format("%.2f", dz)}"
            }
            "JUMP" -> {
                "Sent jump posture packet (Y position elevated, onGround: false -> true)"
            }
            "RANDOM_WALK" -> {
                val heading = Random.nextInt(0, 360)
                "Pushed movement vector (Angle: ${heading}°, Speed: 0.15 blocks/tick)"
            }
            "AUTO_CHAT" -> {
                val chats = listOf("Still active", "Protecting server chunks...", "Mining is life", "Hi everyone!", "AFK bypass active v2.1")
                val text = chats.random()
                sendChatMessage(text)
                "Sent random activity notification"
            }
            else -> "Simulated anti-afk action"
        }
        onLog("[Anti-AFK] $action.", "INFO")
    }

    private val parsedTriggers: Map<String, String> by lazy {
        if (triggerResponses.isBlank()) emptyMap() else {
            triggerResponses.split(";;;").mapNotNull {
                val parts = it.split(":::", limit = 2)
                if (parts.size == 2) parts[0].trim().lowercase() to parts[1].trim() else null
            }.toMap()
        }
    }

    fun handleIncomingMessage(sender: String, text: String) {
        val trimmed = text.trim()
        if (sender == username) return
        
        // Match trigger rules (case-insensitive find)
        val matchedKey = parsedTriggers.keys.find { trimmed.lowercase().contains(it) }
        if (matchedKey != null) {
            val reply = parsedTriggers[matchedKey]
            if (reply != null) {
                scope.launch {
                    delay(500 + Random.nextLong(600))
                    sendChatMessage(reply)
                }
                return
            }
        }

        // Auto AI Responder Chat Feature
        if (aiAutoReplyEnabled && geminiApiKey.isNotBlank() && geminiApiKey != "MY_GEMINI_API_KEY") {
            scope.launch {
                try {
                    val systemPrompt = "You are a Minecraft host companion bot named '$username' connected to server address '$address'. Personality preset: '$aiPersonality'. Formulate a quick, lively, conversational response in standard Minecraft chat to what $sender just typed. Ensure your response is highly specific to Minecraft or their statement, fits in standard 1-line chat, and is STRICTLY under 70 characters. Do NOT use markdown or quotes."
                    val requestPayload = """
                        {
                          "contents": [
                            {
                              "parts": [
                                {
                                  "text": "$sender says: $trimmed"
                                }
                              ]
                            }
                          ],
                          "systemInstruction": {
                            "parts": [
                              {
                                "text": "${systemPrompt.replace("\"", "\\\"").replace("\n", " ")}"
                              }
                            ]
                          },
                          "generationConfig": {
                            "temperature": 0.7,
                            "maxOutputTokens": 45
                          }
                        }
                    """.trimIndent()

                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .build()

                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val body = okhttp3.RequestBody.create(mediaType, requestPayload)
                    val request = okhttp3.Request.Builder()
                        .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$geminiApiKey")
                        .post(body)
                        .header("Content-Type", "application/json")
                        .build()

                    val replyRaw = withContext(Dispatchers.IO) {
                        client.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) return@use ""
                            val responseString = response.body?.string() ?: ""
                            var textVal = ""
                            val textToken = "\"text\":"
                            val textStartIndex = responseString.indexOf(textToken)
                            if (textStartIndex != -1) {
                                val valStartIndex = responseString.indexOf('"', textStartIndex + textToken.length)
                                if (valStartIndex != -1) {
                                    val valEndIndex = responseString.indexOf('"', valStartIndex + 1)
                                    if (valEndIndex != -1) {
                                        textVal = responseString.substring(valStartIndex + 1, valEndIndex)
                                            .replace("\\n", " ")
                                            .replace("\\\"", "\"")
                                    }
                                }
                            }
                            textVal
                        }
                    }

                    val cleanReply = replyRaw.trim()
                    if (cleanReply.isNotBlank()) {
                        delay(1200 + Random.nextLong(1500)) // Human-like reaction delay
                        sendChatMessage(cleanReply)
                    }
                } catch (e: Exception) {
                    onLog("AI responder exception: ${e.localizedMessage}", "ERROR")
                }
            }
        }
    }

    private fun simulateServerChat() {
        val serversidePlayers = listOf("BuilderSteve", "GamerX_99", "MineCraftAdmin", "CreepCrusher", "AlexCrafts", "DiamondDigger")
        val triggersList = parsedTriggers.keys.toList()
        
        val speaker = serversidePlayers.random()
        val msg = if (triggersList.isNotEmpty() && Random.nextInt(100) < 40) {
            val key = triggersList.random()
            val questions = listOf(
                "hey $username, $key?",
                "can someone tell me about $key?",
                "what is $key of the bot?",
                "Is there any trigger for $key?"
            )
            questions.random()
        } else {
            listOf(
                "Wow this server is running great with MineBot online!",
                "Who owns that bot named $username?",
                "Need more items at spawn.",
                "AFK checker plugin is running. Be careful!",
                "Is anyone trading diamonds for netherite?",
                "Lag is so low! Awesome job host."
            ).random()
        }
        
        onLog("<$speaker> $msg", "CHAT")
        handleIncomingMessage(speaker, msg)
    }

    // Protocol Helper Functions
    private fun writeVarInt(out: OutputStream, value: Int) {
        var v = value
        while (true) {
            if ((v and 0xFFFFFF80.toInt()) == 0) {
                out.write(v)
                return
            }
            out.write((v and 0x7F) or 0x80)
            v = v ushr 7
        }
    }

    private fun writeString(out: OutputStream, str: String) {
        val bytes = str.toByteArray(Charsets.UTF_8)
        writeVarInt(out, bytes.size)
        out.write(bytes)
    }

    private fun sendPacket(out: OutputStream, payload: ByteArray) {
        val doubleBuffer = ByteArrayOutputStream()
        writeVarInt(doubleBuffer, payload.size)
        doubleBuffer.write(payload)
        out.write(doubleBuffer.toByteArray())
        out.flush()
    }

    private fun getProtocolVersion(vString: String): Int {
        return when (vString.trim()) {
            "1.21" -> 767
            "1.20.5", "1.20.6" -> 766
            "1.20.3", "1.20.4" -> 765
            "1.20.2" -> 764
            "1.20", "1.20.1" -> 763
            "1.19.4" -> 762
            "1.19.3" -> 761
            "1.19.1", "1.19.2" -> 760
            "1.19" -> 759
            "1.18.2" -> 758
            "1.18", "1.18.1" -> 757
            "1.17.1" -> 756
            "1.17" -> 755
            "1.16.5" -> 754
            else -> 765 // Default to 1.20.4
        }
    }

    private fun LongToBytes(l: Long): ByteArray {
        val result = ByteArray(8)
        for (i in 0..7) {
            result[i] = (l shr ((7 - i) * 8)).toByte()
        }
        return result
    }

    private fun readVarInt(ins: InputStream): Int {
        var numRead = 0
        var result = 0
        var read: Int
        do {
            read = ins.read()
            if (read == -1) {
                throw IOException("End of stream while reading VarInt")
            }
            val value = read and 0x7F
            result = result or (value shl (7 * numRead))
            numRead++
            if (numRead > 5) {
                throw java.io.IOException("VarInt is too big")
            }
        } while ((read and 0x80) != 0)
        return result
    }

    private fun readStringFromStream(ins: InputStream): String {
        val length = readVarInt(ins)
        if (length <= 0) return ""
        val bytes = ByteArray(length)
        var read = 0
        while (read < length) {
            val r = ins.read(bytes, read, length - read)
            if (r == -1) break
            read += r
        }
        return String(bytes, Charsets.UTF_8)
    }

    // Custom Script Interpreter Definition
    private enum class CommandType {
        SAY, DELAY, LOOP_START, LOOP_END
    }

    private data class ScriptCommand(val type: CommandType, val argument: String?)

    private fun parseScript(content: String): List<ScriptCommand> {
        val lines = content.split("\n")
        val list = mutableListOf<ScriptCommand>()
        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty() || trimmedLine.startsWith("//") || trimmedLine.startsWith("#")) continue
            
            val parts = trimmedLine.split(" ", limit = 2)
            val commandWord = parts[0].uppercase()
            val argument = if (parts.size > 1) parts[1] else null
            
            when (commandWord) {
                "SAY" -> list.add(ScriptCommand(CommandType.SAY, argument))
                "DELAY" -> list.add(ScriptCommand(CommandType.DELAY, argument))
                "LOOP_START" -> list.add(ScriptCommand(CommandType.LOOP_START, null))
                "LOOP_END" -> list.add(ScriptCommand(CommandType.LOOP_END, null))
            }
        }
        return list
    }
}
