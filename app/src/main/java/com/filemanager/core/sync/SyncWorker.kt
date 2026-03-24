package com.filemanager.core.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        val configId = inputData.getString("config_id") ?: return Result.failure()
        
        return try {
            val syncManager = SyncManager.getInstance(applicationContext)
            syncManager.startSync(configId)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}