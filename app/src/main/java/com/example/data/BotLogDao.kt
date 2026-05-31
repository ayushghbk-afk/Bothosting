package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BotLogDao {
    @Query("SELECT * FROM bot_logs WHERE botId = :botId ORDER BY timestamp DESC LIMIT 200")
    fun getLogsForBotFlow(botId: Int): Flow<List<BotLog>>

    @Query("SELECT * FROM bot_logs WHERE botId = :botId ORDER BY timestamp DESC LIMIT 200")
    suspend fun getLogsForBot(botId: Int): List<BotLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: BotLog)

    @Query("DELETE FROM bot_logs WHERE botId = :botId")
    suspend fun deleteLogsForBot(botId: Int)

    @Query("DELETE FROM bot_logs")
    suspend fun clearAllLogs()
}
