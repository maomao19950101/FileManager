package com.filemanager.core.database

import androidx.room.*
import com.filemanager.core.model.TransferStatus
import com.filemanager.core.model.TransferTask
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferTaskDao {
    
    @Query("SELECT * FROM transfer_tasks ORDER BY createdAt DESC")
    fun getAllTasks(): Flow<List<TransferTask>>
    
    @Query("SELECT * FROM transfer_tasks WHERE status = :status ORDER BY priority DESC, createdAt ASC")
    fun getTasksByStatus(status: TransferStatus): Flow<List<TransferTask>>
    
    @Query("SELECT * FROM transfer_tasks WHERE status IN ('PENDING', 'RUNNING') ORDER BY priority DESC")
    suspend fun getPendingTasks(): List<TransferTask>
    
    @Query("SELECT * FROM transfer_tasks WHERE id = :taskId")
    suspend fun getTaskById(taskId: String): TransferTask?
    
    @Query("SELECT * FROM transfer_tasks ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentTasks(limit: Int): List<TransferTask>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TransferTask)
    
    @Update
    suspend fun update(task: TransferTask)
    
    @Delete
    suspend fun delete(task: TransferTask)
    
    @Query("UPDATE transfer_tasks SET status = :status, updatedAt = :timestamp WHERE id = :taskId")
    suspend fun updateStatus(taskId: String, status: TransferStatus, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE transfer_tasks SET progress = :progress, updatedAt = :timestamp WHERE id = :taskId")
    suspend fun updateProgress(taskId: String, progress: Int, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE transfer_tasks SET bytesTransferred = :bytes, updatedAt = :timestamp WHERE id = :taskId")
    suspend fun updateBytesTransferred(taskId: String, bytes: Long, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE transfer_tasks SET totalBytes = :bytes, updatedAt = :timestamp WHERE id = :taskId")
    suspend fun updateTotalBytes(taskId: String, bytes: Long, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE transfer_tasks SET retryCount = :count, updatedAt = :timestamp WHERE id = :taskId")
    suspend fun updateRetryCount(taskId: String, count: Int, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE transfer_tasks SET errorMessage = :message, updatedAt = :timestamp WHERE id = :taskId")
    suspend fun updateErrorMessage(taskId: String, message: String?, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE transfer_tasks SET completedAt = :timestamp WHERE id = :taskId")
    suspend fun updateCompletedAt(taskId: String, timestamp: Long)
    
    @Query("DELETE FROM transfer_tasks WHERE status IN ('COMPLETED', 'FAILED', 'CANCELLED') AND createdAt < :timestamp")
    suspend fun deleteOldCompletedTasks(timestamp: Long)
    
    @Query("SELECT COUNT(*) FROM transfer_tasks WHERE status = 'RUNNING'")
    suspend fun getRunningTaskCount(): Int
}