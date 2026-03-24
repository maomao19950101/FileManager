package com.filemanager.core.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_configs")
data class SyncConfig(
    @PrimaryKey
    val id: String,
    val name: String,
    val localPath: String,
    val remotePath: String,
    val syncMode: SyncMode = SyncMode.TWO_WAY,
    val autoSync: Boolean = false,
    val syncIntervalMinutes: Int = 30,
    val lastSyncTime: Long? = null,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

enum class SyncMode {
    TWO_WAY,      // 双向同步
    LOCAL_TO_REMOTE,  // 仅上传
    REMOTE_TO_LOCAL   // 仅下载
}
