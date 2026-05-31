package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bot_configs")
data class BotConfig(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val serverAddress: String,
    val serverPort: Int = 25565,
    val username: String,
    val edition: String = "JAVA", // "JAVA" or "BEDROCK"
    val version: String = "1.20.4",
    val scriptsEnabled: Boolean = false,
    val selectedScriptId: Int? = null,
    val antiAfkEnabled: Boolean = true,
    val antiAfkType: String = "CIRCLE", // "CIRCLE", "JUMP", "RANDOM_WALK", "AUTO_CHAT"
    val autoReconnect: Boolean = true,
    val avatarType: String = "STEVE", // "STEVE", "ALEX", "CREEPER", "ENDERMAN", "ROBOT", "WIZARD"
    val themeColorHex: String = "#6750A4", // Default M3 primary Indigo/Purple
    val customStatus: String = "READY", // Custom action statuses
    val triggerResponses: String = "hi:::Hello! I am online and managing chunks.;;;help:::I am a custom MineBot client. Type messages to chat with me.;;;status:::Host environment: Android Sandbox. Battery: Nominal. CPU: 1.25%."
)
