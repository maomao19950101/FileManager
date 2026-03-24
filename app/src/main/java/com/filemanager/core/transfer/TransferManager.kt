package com.filemanager.core.transfer

import android.content.Context
import android.util.Log
import com.filemanager.core.database.AppDatabase
import com.filemanager.core.model.TransferTask
import com.filemanager.core.model.TransferStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.PriorityBlockingQueue
import kotlin.math.min

class TransferManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "TransferManager"
        private const val CHUNK_SIZE = 1024 * 1024 // 1MB chunks
        private const val MAX_RETRY_COUNT = 3
        private const val RETRY_DELAY_MS = 5000L
        
        @Volatile
        private var instance: TransferManager? = null
        
        fun getInstance(context: Context): TransferManager {
            return instance ?: synchronized(this) {
                instance ?: TransferManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database = AppDatabase.getDatabase(context)
    private val taskDao = database.transferTaskDao()
    
    // 优先级任务队列
    private val taskQueue = PriorityBlockingQueue<TransferTask>(100) { t1, t2 ->
        t2.priority.compareTo(t1.priority)
    }
    
    // 活跃任务管理
    private val activeTasks = ConcurrentHashMap<String, Job>()
    private val pausedTasks = ConcurrentHashMap<String, TransferTask>()
    
    // 状态监听
    private val _transferState = MutableStateFlow<TransferState>(TransferState.Idle)
    val transferState: StateFlow<TransferState> = _transferState
    
    private val _overallProgress = MutableStateFlow(0)
    val overallProgress: StateFlow<Int> = _overallProgress
    
    init {
        // 恢复未完成的任务
        scope.launch {
            restorePendingTasks()
        }
    }
    
    sealed class TransferState {
        object Idle : TransferState()
        data class Running(val activeCount: Int, val queueCount: Int) : TransferState()
        data class Paused(val pausedCount: Int) : TransferState()
        data class Error(val message: String) : TransferState()
    }
    
    // ========== 任务管理 ==========
    
    suspend fun addTask(
        sourcePath: String,
        targetPath: String,
        isUpload: Boolean,
        priority: Int = 5
    ): String {
        val task = TransferTask(
            id = generateTaskId(),
            sourcePath = sourcePath,
            targetPath = targetPath,
            isUpload = isUpload,
            priority = priority,
            status = TransferStatus.PENDING,
            createdAt = System.currentTimeMillis()
        )
        
        taskDao.insert(task)
        taskQueue.offer(task)
        processQueue()
        
        return task.id
    }
    
    suspend fun addBatchTasks(tasks: List<Triple<String, String, Boolean>>): List<String> {
        val taskIds = mutableListOf<String>()
        
        tasks.forEach { (source, target, isUpload) ->
            val id = addTask(source, target, isUpload)
            taskIds.add(id)
        }
        
        return taskIds
    }
    
    fun pauseTask(taskId: String) {
        activeTasks[taskId]?.cancel()
        activeTasks.remove(taskId)
        
        scope.launch {
            taskDao.updateStatus(taskId, TransferStatus.PAUSED)
            updateState()
        }
    }
    
    fun resumeTask(taskId: String) {
        scope.launch {
            val task = taskDao.getTaskById(taskId) ?: return@launch
            taskQueue.offer(task)
            taskDao.updateStatus(taskId, TransferStatus.PENDING)
            processQueue()
        }
    }
    
    fun cancelTask(taskId: String) {
        activeTasks[taskId]?.cancel()
        activeTasks.remove(taskId)
        
        scope.launch {
            taskDao.updateStatus(taskId, TransferStatus.CANCELLED)
            taskDao.updateProgress(taskId, 0)
            updateState()
        }
    }
    
    suspend fun retryFailedTask(taskId: String) {
        val task = taskDao.getTaskById(taskId) ?: return
        if (task.status == TransferStatus.FAILED || task.status == TransferStatus.CANCELLED) {
            taskQueue.offer(task.copy(
                status = TransferStatus.PENDING,
                retryCount = 0,
                errorMessage = null
            ))
            taskDao.updateStatus(taskId, TransferStatus.PENDING)
            processQueue()
        }
    }
    
    // ========== 断点续传核心 ==========
    
    private fun processQueue() {
        scope.launch {
            while (taskQueue.isNotEmpty() && activeTasks.size < 3) { // 最大并发数
                val task = taskQueue.poll() ?: break
                if (activeTasks.containsKey(task.id)) continue
                
                val job = launch { executeTransfer(task) }
                activeTasks[task.id] = job
            }
            updateState()
        }
    }
    
    private suspend fun executeTransfer(task: TransferTask) {
        var retryCount = 0
        
        while (retryCount < MAX_RETRY_COUNT) {
            try {
                taskDao.updateStatus(task.id, TransferStatus.RUNNING)
                
                if (task.isUpload) {
                    uploadFileWithResume(task)
                } else {
                    downloadFileWithResume(task)
                }
                
                // 成功完成
                taskDao.updateStatus(task.id, TransferStatus.COMPLETED)
                taskDao.updateCompletedAt(task.id, System.currentTimeMillis())
                break
                
            } catch (e: Exception) {
                Log.e(TAG, "Transfer failed for task ${task.id}", e)
                retryCount++
                
                if (retryCount < MAX_RETRY_COUNT) {
                    // 指数退避重试
                    val delay = RETRY_DELAY_MS * (1 shl (retryCount - 1))
                    taskDao.updateRetryCount(task.id, retryCount)
                    delay(delay)
                } else {
                    // 最终失败
                    taskDao.updateStatus(task.id, TransferStatus.FAILED)
                    taskDao.updateErrorMessage(task.id, e.message ?: "Unknown error")
                }
            }
        }
        
        activeTasks.remove(task.id)
        processQueue()
        updateOverallProgress()
    }
    
    private suspend fun downloadFileWithResume(task: TransferTask) {
        val file = File(task.targetPath)
        val tempFile = File(task.targetPath + ".tmp")
        
        // 检查断点
        val existingSize = if (tempFile.exists()) tempFile.length() else 0L
        taskDao.updateBytesTransferred(task.id, existingSize)
        
        withContext(Dispatchers.IO) {
            val connection = URL(task.sourcePath).openConnection() as HttpURLConnection
            
            try {
                connection.apply {
                    requestMethod = "GET"
                    setRequestProperty("Range", "bytes=$existingSize-")
                    connectTimeout = 30000
                    readTimeout = 30000
                }
                
                val totalSize = connection.contentLengthLong + existingSize
                taskDao.updateTotalBytes(task.id, totalSize)
                
                RandomAccessFile(tempFile, "rw").use { raf ->
                    raf.seek(existingSize)
                    
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(CHUNK_SIZE)
                        var bytesRead: Int
                        var totalBytes = existingSize
                        var lastUpdate = System.currentTimeMillis()
                        
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            // 检查取消
                            if (!isActive) {
                                throw CancellationException("Transfer cancelled")
                            }
                            
                            // 检查暂停
                            if (taskDao.getTaskById(task.id)?.status == TransferStatus.PAUSED) {
                                throw TransferPausedException()
                            }
                            
                            raf.write(buffer, 0, bytesRead)
                            totalBytes += bytesRead
                            
                            // 批量更新进度（每500ms）
                            val now = System.currentTimeMillis()
                            if (now - lastUpdate > 500) {
                                val progress = ((totalBytes * 100) / totalSize).toInt()
                                taskDao.updateProgress(task.id, progress)
                                taskDao.updateBytesTransferred(task.id, totalBytes)
                                lastUpdate = now
                            }
                        }
                    }
                }
                
                // 下载完成，重命名
                tempFile.renameTo(file)
                
            } finally {
                connection.disconnect()
            }
        }
    }
    
    private suspend fun uploadFileWithResume(task: TransferTask) {
        val file = File(task.sourcePath)
        if (!file.exists()) throw IllegalArgumentException("Source file not found")
        
        val totalSize = file.length()
        taskDao.updateTotalBytes(task.id, totalSize)
        
        // 查询服务器已接收的字节数
        val uploadedSize = queryServerProgress(task.targetPath)
        taskDao.updateBytesTransferred(task.id, uploadedSize)
        
        withContext(Dispatchers.IO) {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(uploadedSize)
                
                val connection = URL(task.targetPath).openConnection() as HttpURLConnection
                
                try {
                    connection.apply {
                        requestMethod = "PUT"
                        doOutput = true
                        setRequestProperty("Content-Range", "bytes $uploadedSize-${totalSize - 1}/$totalSize")
                        setRequestProperty("X-File-Name", file.name)
                        connectTimeout = 30000
                        readTimeout = 30000
                    }
                    
                    connection.outputStream.use { output ->
                        val buffer = ByteArray(CHUNK_SIZE)
                        var bytesRead: Int
                        var totalBytes = uploadedSize
                        var lastUpdate = System.currentTimeMillis()
                        
                        while (raf.read(buffer).also { bytesRead = it } != -1) {
                            if (!isActive) throw CancellationException("Transfer cancelled")
                            
                            output.write(buffer, 0, bytesRead)
                            totalBytes += bytesRead
                            
                            val now = System.currentTimeMillis()
                            if (now - lastUpdate > 500) {
                                val progress = ((totalBytes * 100) / totalSize).toInt()
                                taskDao.updateProgress(task.id, progress)
                                taskDao.updateBytesTransferred(task.id, totalBytes)
                                lastUpdate = now
                            }
                        }
                    }
                    
                    if (connection.responseCode !in 200..299) {
                        throw RuntimeException("Upload failed: ${connection.responseCode}")
                    }
                    
                } finally {
                    connection.disconnect()
                }
            }
        }
    }
    
    private suspend fun queryServerProgress(url: String): Long {
        // 查询服务器已接收的字节数
        return withContext(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "HEAD"
                connection.getHeaderField("X-Received-Bytes")?.toLongOrNull() ?: 0L
            } catch (e: Exception) {
                0L
            }
        }
    }
    
    // ========== 辅助方法 ==========
    
    private suspend fun restorePendingTasks() {
        val pendingTasks = taskDao.getPendingTasks()
        pendingTasks.forEach { taskQueue.offer(it) }
        processQueue()
    }
    
    private fun updateState() {
        val state = when {
            activeTasks.isNotEmpty() -> TransferState.Running(
                activeTasks.size,
                taskQueue.size
            )
            pausedTasks.isNotEmpty() -> TransferState.Paused(pausedTasks.size)
            else -> TransferState.Idle
        }
        _transferState.value = state
    }
    
    private suspend fun updateOverallProgress() {
        val tasks = taskDao.getRecentTasks(50)
        if (tasks.isEmpty()) {
            _overallProgress.value = 0
            return
        }
        
        val totalProgress = tasks.sumOf { it.progress }
        _overallProgress.value = totalProgress / tasks.size
    }
    
    private fun generateTaskId(): String {
        return "task_${System.currentTimeMillis()}_${(0..9999).random()}"
    }
    
    fun shutdown() {
        activeTasks.values.forEach { it.cancel() }
        scope.cancel()
    }
    
    class TransferPausedException : Exception("Transfer paused by user")
}
