package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BotConfigDao {
    @Query("SELECT * FROM bot_configs ORDER BY id DESC")
    fun getAllConfigs(): Flow<List<BotConfig>>

    @Query("SELECT * FROM bot_configs WHERE id = :id")
    suspend fun getConfigById(id: Int): BotConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: BotConfig): Long

    @Update
    suspend fun updateConfig(config: BotConfig)

    @Delete
    suspend fun deleteConfig(config: BotConfig)
}
