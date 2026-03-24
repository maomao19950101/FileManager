package com.filemanager.core.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.filemanager.core.database.Converters

@Entity(tableName = "transfer_tasks")
@TypeConverters(Converters::class)
data class TransferTask(
    @PrimaryKey
    val id: String,
    val sourcePath: String,
    val targetPath: String,
    val isUpload: Boolean,
    val priority: Int = 5,
    val status: TransferStatus = TransferStatus.PENDING,
    val progress: Int = 0,
    val bytesTransferred: Long = 0,
    val totalBytes: Long = 0,
    val retryCount: Int = 0,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

enum class TransferStatus {
    PENDING,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}
