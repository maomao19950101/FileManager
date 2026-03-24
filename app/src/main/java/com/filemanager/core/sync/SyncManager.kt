package com.filemanager.core.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import com.filemanager.core.database.AppDatabase
import com.filemanager.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class SyncManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "SyncManager"
        private const val CHUNK_SIZE = 8192
        
        @Volatile
        private var instance: SyncManager? = null
        
        fun getInstance(context: Context): SyncManager {
            return instance ?: synchronized(this) {
                instance ?: SyncManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database = AppDatabase.getDatabase(context)
    private val configDao = database.syncConfigDao()
    
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState
    
    sealed class SyncState {
        object Idle : SyncState()
        data class Scanning(val configId: String) : SyncState()
        data class Syncing(val configId: String, val progress: Int, val currentFile: String) : SyncState()
        data class Completed(val configId: String, val uploaded: Int, val downloaded: Int, val conflicts: Int) : SyncState()
        data class Error(val configId: String, val message: String) : SyncState()
    }
    
    // ========== 同步配置管理 ==========
    
    suspend fun createSyncConfig(
        name: String,
        localPath: String,
        remotePath: String,
        syncMode: SyncMode = SyncMode.TWO_WAY,
        autoSync: Boolean = false,
        syncIntervalMinutes: Int = 30
    ): String {
        val config = SyncConfig(
            id = generateConfigId(),
            name = name,
            localPath = localPath,
            remotePath = remotePath,
            syncMode = syncMode,
            autoSync = autoSync,
            syncIntervalMinutes = syncIntervalMinutes
        )
        
        configDao.insert(config)
        
        if (autoSync) {
            scheduleAutoSync(config.id, syncIntervalMinutes)
        }
        
        return config.id
    }
    
    fun getAllConfigs(): Flow<List<SyncConfig>> = configDao.getAllConfigs()
    
    suspend fun updateConfig(config: SyncConfig) {
        configDao.update(config)
        
        if (config.autoSync) {
            scheduleAutoSync(config.id, config.syncIntervalMinutes)
        } else {
            cancelAutoSync(config.id)
        }
    }
    
    suspend fun deleteConfig(configId: String) {
        cancelAutoSync(configId)
        configDao.getConfigById(configId)?.let { configDao.delete(it) }
    }
    
    // ========== 执行同步 ==========
    
    suspend fun startSync(configId: String) {
        val config = configDao.getConfigById(configId)
            ?: throw IllegalArgumentException("Sync config not found")
        
        scope.launch {
            try {
                _syncState.value = SyncState.Scanning(configId)
                
                // 扫描文件差异
                val diff = scanFileDifferences(config)
                
                // 执行同步
                performSync(config, diff)
                
                // 更新最后同步时间
                configDao.updateLastSyncTime(configId, System.currentTimeMillis())
                
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed for config $configId", e)
                _syncState.value = SyncState.Error(configId, e.message ?: "Unknown error")
            }
        }
    }
    
    private suspend fun scanFileDifferences(config: SyncConfig): SyncDifference {
        return withContext(Dispatchers.IO) {
            val localFiles = scanLocalFiles(File(config.localPath))
            val remoteFiles = scanRemoteFiles(config.remotePath)
            
            val toUpload = mutableListOf<FileInfo>()
            val toDownload = mutableListOf<FileInfo>()
            val conflicts = mutableListOf<ConflictInfo>()
            
            when (config.syncMode) {
                SyncMode.LOCAL_TO_REMOTE -> {
                    // 仅上传：本地有新文件或更新的文件
                    localFiles.forEach { (path, localInfo) ->
                        val remoteInfo = remoteFiles[path]
                        if (remoteInfo == null || localInfo.lastModified > remoteInfo.lastModified) {
                            toUpload.add(localInfo)
                        }
                    }
                }
                
                SyncMode.REMOTE_TO_LOCAL -> {
                    // 仅下载：远程有新文件或更新的文件
                    remoteFiles.forEach { (path, remoteInfo) ->
                        val localInfo = localFiles[path]
                        if (localInfo == null || remoteInfo.lastModified > localInfo.lastModified) {
                            toDownload.add(remoteInfo)
                        }
                    }
                }
                
                SyncMode.TWO_WAY -> {
                    // 双向同步
                    val allPaths = (localFiles.keys + remoteFiles.keys).toSortedSet()
                    
                    allPaths.forEach { path ->
                        val local = localFiles[path]
                        val remote = remoteFiles[path]
                        
                        when {
                            local == null -> toDownload.add(remote!!)
                            remote == null -> toUpload.add(local)
                            local.lastModified > remote.lastModified -> toUpload.add(local)
                            remote.lastModified > local.lastModified -> toDownload.add(remote)
                            local.hash != remote.hash -> conflicts.add(ConflictInfo(path, local, remote))
                        }
                    }
                }
            }
            
            SyncDifference(toUpload, toDownload, conflicts)
        }
    }
    
    private fun scanLocalFiles(dir: File): Map<String, FileInfo> {
        val files = mutableMapOf<String, FileInfo>()
        
        if (!dir.exists() || !dir.isDirectory) return files
        
        dir.walkTopDown().forEach { file ->
            if (file.isFile) {
                val relativePath = file.relativeTo(dir).path
                files[relativePath] = FileInfo(
                    path = file.absolutePath,
                    relativePath = relativePath,
                    size = file.length(),
                    lastModified = file.lastModified(),
                    hash = calculateFileHash(file)
                )
            }
        }
        
        return files
    }
    
    private fun scanRemoteFiles(remotePath: String): Map<String, FileInfo> {
        // TODO: 实现远程文件扫描（通过FTP/SFTP/WebDAV等）
        // 这里使用模拟实现
        return emptyMap()
    }
    
    private fun calculateFileHash(file: File): String {
        val md = MessageDigest.getInstance("MD5")
        file.inputStream().use { fis ->
            val buffer = ByteArray(CHUNK_SIZE)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                md.update(buffer, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
    
    private suspend fun performSync(config: SyncConfig, diff: SyncDifference) {
        val totalOperations = diff.toUpload.size + diff.toDownload.size
        var completed = 0
        
        // 上传文件
        diff.toUpload.forEach { fileInfo ->
            if (!scope.isActive) throw CancellationException()
            
            _syncState.value = SyncState.Syncing(
                config.id,
                (completed * 100 / totalOperations),
                fileInfo.relativePath
            )
            
            uploadFile(config, fileInfo)
            completed++
            
            yield()
        }
        
        // 下载文件
        diff.toDownload.forEach { fileInfo ->
            if (!scope.isActive) throw CancellationException()
            
            _syncState.value = SyncState.Syncing(
                config.id,
                (completed * 100 / totalOperations),
                fileInfo.relativePath
            )
            
            downloadFile(config, fileInfo)
            completed++
            
            yield()
        }
        
        _syncState.value = SyncState.Completed(
            config.id,
            diff.toUpload.size,
            diff.toDownload.size,
            diff.conflicts.size
        )
    }
    
    private suspend fun uploadFile(config: SyncConfig, fileInfo: FileInfo) {
        // TODO: 实现实际上传逻辑
        withContext(Dispatchers.IO) {
            // 模拟上传延迟
            delay(100)
        }
    }
    
    private suspend fun downloadFile(config: SyncConfig, fileInfo: FileInfo) {
        // TODO: 实现实际下载逻辑
        withContext(Dispatchers.IO) {
            // 模拟下载延迟
            delay(100)
        }
    }
    
    // ========== 自动同步 ==========
    
    private fun scheduleAutoSync(configId: String, intervalMinutes: Int) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        
        val syncWorkRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            intervalMinutes.toLong(),
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setInputData(workDataOf("config_id" to configId))
            .addTag("sync_$configId")
            .build()
        
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "sync_$configId",
            ExistingPeriodicWorkPolicy.UPDATE,
            syncWorkRequest
        )
    }
    
    private fun cancelAutoSync(configId: String) {
        WorkManager.getInstance(context).cancelUniqueWork("sync_$configId")
    }
    
    fun shutdown() {
        scope.cancel()
    }
    
    // ========== 数据类 ==========
    
    private data class FileInfo(
        val path: String,
        val relativePath: String,
        val size: Long,
        val lastModified: Long,
        val hash: String
    )
    
    private data class ConflictInfo(
        val path: String,
        val local: FileInfo,
        val remote: FileInfo
    )
    
    private data class SyncDifference(
        val toUpload: List<FileInfo>,
        val toDownload: List<FileInfo>,
        val conflicts: List<ConflictInfo>
    )
    
    private fun generateConfigId(): String = "sync_cfg_${System.currentTimeMillis()}"
}