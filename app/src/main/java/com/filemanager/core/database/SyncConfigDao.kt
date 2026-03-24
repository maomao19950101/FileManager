package com.filemanager.core.database

import androidx.room.*
import com.filemanager.core.model.SyncConfig
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncConfigDao {
    
    @Query("SELECT * FROM sync_configs ORDER BY createdAt DESC")
    fun getAllConfigs(): Flow<List<SyncConfig>>
    
    @Query("SELECT * FROM sync_configs WHERE isEnabled = 1 AND autoSync = 1")
    suspend fun getAutoSyncConfigs(): List<SyncConfig>
    
    @Query("SELECT * FROM sync_configs WHERE id = :id")
    suspend fun getConfigById(id: String): SyncConfig?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: SyncConfig)
    
    @Update
    suspend fun update(config: SyncConfig)
    
    @Delete
    suspend fun delete(config: SyncConfig)
    
    @Query("UPDATE sync_configs SET lastSyncTime = :timestamp WHERE id = :id")
    suspend fun updateLastSyncTime(id: String, timestamp: Long)
}