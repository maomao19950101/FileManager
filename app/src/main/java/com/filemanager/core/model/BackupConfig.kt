package com.filemanager.core.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "backup_configs")
data class BackupConfig(
    @PrimaryKey
    val id: String,
    val name: String,
    val sourcePaths: List<String>,  // 备份的源路径列表
    val backupLocation: String,     // 备份目标位置
    val backupType: BackupType = BackupType.FULL,
    val compressionEnabled: Boolean = true,
    val encryptionEnabled: Boolean = false,
    val encryptionKey: String? = null,
    val scheduleEnabled: Boolean = false,
    val scheduleCron: String? = null,  // Cron表达式
    val maxBackups: Int = 10,          // 保留的最大备份数
    val lastBackupTime: Long? = null,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "backup_records")
data class BackupRecord(
    @PrimaryKey
    val id: String,
    val configId: String,
    val backupPath: String,
    val size: Long,
    val fileCount: Int,
    val status: BackupStatus = BackupStatus.IN_PROGRESS,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

enum class BackupType {
    FULL,      // 完整备份
    INCREMENTAL  // 增量备份
}

enum class BackupStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    RESTORING
}
