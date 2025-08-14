package com.astralstream.player.data.database.dao

import androidx.room.*
import com.astralstream.player.data.database.entities.SettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    
    @Query("SELECT * FROM settings WHERE key = :key")
    suspend fun getSetting(key: String): SettingsEntity?
    
    @Query("SELECT * FROM settings WHERE key = :key")
    fun getSettingFlow(key: String): Flow<SettingsEntity?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSetting(setting: SettingsEntity)
    
    @Query("DELETE FROM settings WHERE key = :key")
    suspend fun deleteSetting(key: String)
    
    @Query("DELETE FROM settings")
    suspend fun clearAllSettings()
    
    @Query("SELECT * FROM settings")
    fun getAllSettings(): Flow<List<SettingsEntity>>
}