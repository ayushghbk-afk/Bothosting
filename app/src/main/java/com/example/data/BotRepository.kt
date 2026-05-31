package com.example.data

import kotlinx.coroutines.flow.Flow

class BotRepository(private val database: AppDatabase) {

    private val botConfigDao = database.botConfigDao()
    private val botLogDao = database.botLogDao()
    private val customScriptDao = database.customScriptDao()

    val allConfigs: Flow<List<BotConfig>> = botConfigDao.getAllConfigs()
    val allScripts: Flow<List<CustomScript>> = customScriptDao.getAllScripts()

    suspend fun getConfigById(id: Int): BotConfig? {
        return botConfigDao.getConfigById(id)
    }

    suspend fun insertConfig(config: BotConfig): Int {
        return botConfigDao.insertConfig(config).toInt()
    }

    suspend fun updateConfig(config: BotConfig) {
        botConfigDao.updateConfig(config)
    }

    suspend fun deleteConfig(config: BotConfig) {
        botConfigDao.deleteConfig(config)
    }

    fun getLogsForBotFlow(botId: Int): Flow<List<BotLog>> {
        return botLogDao.getLogsForBotFlow(botId)
    }

    suspend fun insertLog(log: BotLog) {
        botLogDao.insertLog(log)
    }

    suspend fun clearLogsForBot(botId: Int) {
        botLogDao.deleteLogsForBot(botId)
    }

    suspend fun getScriptById(id: Int): CustomScript? {
        return customScriptDao.getScriptById(id)
    }

    suspend fun insertScript(script: CustomScript): Int {
        return customScriptDao.insertScript(script).toInt()
    }

    suspend fun deleteScript(script: CustomScript) {
        customScriptDao.deleteScript(script)
    }
}
