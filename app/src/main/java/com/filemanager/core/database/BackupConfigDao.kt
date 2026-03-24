package com.filemanager.core.database

import androidx.room.*
import com.filemanager.core.model.BackupConfig
import com.filemanager.core.model.BackupRecord
import com.filemanager.core.model.BackupStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface BackupConfigDao {
    
    @Query("SELECT * FROM backup_configs ORDER BY createdAt DESC")
    fun getAllConfigs(): Flow<List<BackupConfig>>
    
    @Query("SELECT * FROM backup_configs WHERE id = :id")
    suspend fun getConfigById(id: String): BackupConfig?
    
    @Query("SELECT * FROM backup_configs WHERE scheduleEnabled = 1 AND isEnabled = 1")
    suspend fun getScheduledConfigs(): List<BackupConfig>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: BackupConfig)
    
    @Update
    suspend fun update(config: BackupConfig)
    
    @Delete
    suspend fun delete(config: BackupConfig)
    
    @Query("UPDATE backup_configs SET lastBackupTime = :timestamp WHERE id = :id")
    suspend fun updateLastBackupTime(id: String, timestamp: Long)
}

@Dao
interface BackupRecordDao {
    
    @Query("SELECT * FROM backup_records WHERE configId = :configId ORDER BY createdAt DESC")
    fun getRecordsByConfig(configId: String): Flow<List<BackupRecord>>
    
    @Query("SELECT * FROM backup_records WHERE id = :id")
    suspend fun getRecordById(id: String): BackupRecord?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: BackupRecord)
    
    @Update
    suspend fun update(record: BackupRecord)
    
    @Delete
    suspend fun delete(record: BackupRecord)
    
    @Query("SELECT COUNT(*) FROM backup_records WHERE configId = :configId AND status = 'COMPLETED'")
    suspend fun getCompletedCount(configId: String): Int
    
    @Query("DELETE FROM backup_records WHERE configId = :configId AND id NOT IN (SELECT id FROM backup_records WHERE configId = :configId AND status = 'COMPLETED' ORDER BY createdAt DESC LIMIT :keepCount)")
    suspend fun deleteOldRecords(configId: String, keepCount: Int)
}