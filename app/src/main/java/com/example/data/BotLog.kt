package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bot_logs")
data class BotLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val botId: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val message: String,
    val type: String // "INFO", "CHAT", "ERROR", "SCRIPT"
)
