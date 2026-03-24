package com.filemanager.core

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.filemanager.core.database.AppDatabase
import com.filemanager.core.transfer.TransferManager
import com.filemanager.core.sync.SyncManager
import com.filemanager.core.backup.BackupManager

class FileManagerApp : Application() {
    
    companion object {
        lateinit var instance: FileManagerApp
            private set
    }
    
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    val transferManager: TransferManager by lazy { TransferManager.getInstance(this) }
    val syncManager: SyncManager by lazy { SyncManager.getInstance(this) }
    val backupManager: BackupManager by lazy { BackupManager.getInstance(this) }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
    }
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                NotificationChannel(
                    "transfer_channel",
                    "文件传输",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "显示文件传输进度"
                },
                NotificationChannel(
                    "sync_channel",
                    "自动同步",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "显示自动同步状态"
                },
                NotificationChannel(
                    "backup_channel",
                    "备份恢复",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "显示备份和恢复进度"
                }
            )
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannels(channels)
        }
    }
}
