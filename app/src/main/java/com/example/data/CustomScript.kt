package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_scripts")
data class CustomScript(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val content: String, // Stringified script commands
    val timestamp: Long = System.currentTimeMillis()
)
