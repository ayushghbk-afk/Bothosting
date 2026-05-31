package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomScriptDao {
    @Query("SELECT * FROM custom_scripts ORDER BY id DESC")
    fun getAllScripts(): Flow<List<CustomScript>>

    @Query("SELECT * FROM custom_scripts WHERE id = :id")
    suspend fun getScriptById(id: Int): CustomScript?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScript(script: CustomScript): Long

    @Delete
    suspend fun deleteScript(script: CustomScript)
}
