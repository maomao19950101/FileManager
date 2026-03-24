package com.filemanager.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed, restoring services...")
            
            val app = context.applicationContext as? FileManagerApp ?: return
            
            CoroutineScope(Dispatchers.IO).launch {
                // 恢复传输任务
                app.transferManager
                
                // 恢复自动同步
                val syncConfigs = app.database.syncConfigDao().getAutoSyncConfigs()
                syncConfigs.forEach { config ->
                    Log.d(TAG, "Restoring auto sync: ${config.name}")
                }
            }
        }
    }
}