package com.filemanager.core.backup

import android.content.Context
import android.util.Log
import com.filemanager.core.database.AppDatabase
import com.filemanager.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.*
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min

class BackupManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "BackupManager"
        private const val BUFFER_SIZE = 8192
        private const val MAX_RETRY_COUNT = 3
        
        @Volatile
        private var instance: BackupManager? = null
        
        fun getInstance(context: Context): BackupManager {
            return instance ?: synchronized(this) {
                instance ?: BackupManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database = AppDatabase.getDatabase(context)
    private val configDao = database.backupConfigDao()
    private val recordDao = database.backupRecordDao()
    
    private val _backupState = MutableStateFlow<BackupState>(BackupState.Idle)
    val backupState: StateFlow<BackupState> = _backupState
    
    sealed class BackupState {
        object Idle : BackupState()
        data class Running(val configId: String, val progress: Int) : BackupState()
        data class Restoring(val recordId: String, val progress: Int) : BackupState()
        data class Error(val message: String) : BackupState()
    }
    
    // ========== 备份配置管理 ==========
    
    suspend fun createBackupConfig(
        name: String,
        sourcePaths: List<String>,
        backupLocation: String,
        backupType: BackupType = BackupType.FULL,
        compressionEnabled: Boolean = true,
        encryptionEnabled: Boolean = false,
        encryptionKey: String? = null,
        scheduleEnabled: Boolean = false,
        scheduleCron: String? = null,
        maxBackups: Int = 10
    ): String {
        val config = BackupConfig(
            id = generateConfigId(),
            name = name,
            sourcePaths = sourcePaths,
            backupLocation = backupLocation,
            backupType = backupType,
            compressionEnabled = compressionEnabled,
            encryptionEnabled = encryptionEnabled,
            encryptionKey = encryptionKey?.takeIf { encryptionEnabled },
            scheduleEnabled = scheduleEnabled,
            scheduleCron = scheduleCron,
            maxBackups = maxBackups
        )
        
        configDao.insert(config)
        return config.id
    }
    
    fun getAllConfigs(): Flow<List<BackupConfig>> = configDao.getAllConfigs()
    
    suspend fun updateConfig(config: BackupConfig) = configDao.update(config)
    
    suspend fun deleteConfig(configId: String) {
        configDao.getConfigById(configId)?.let { configDao.delete(it) }
    }
    
    // ========== 执行备份 ==========
    
    suspend fun startBackup(configId: String): String {
        val config = configDao.getConfigById(configId) 
            ?: throw IllegalArgumentException("Config not found")
        
        val recordId = generateRecordId()
        val timestamp = System.currentTimeMillis()
        val backupFileName = "${config.name}_${timestamp}.zip"
        val backupPath = File(config.backupLocation, backupFileName).absolutePath
        
        val record = BackupRecord(
            id = recordId,
            configId = configId,
            backupPath = backupPath,
            size = 0,
            fileCount = 0,
            status = BackupStatus.IN_PROGRESS,
            createdAt = timestamp
        )
        
        recordDao.insert(record)
        
        scope.launch {
            try {
                executeBackup(config, record)
            } catch (e: Exception) {
                Log.e(TAG, "Backup failed", e)
                recordDao.update(record.copy(
                    status = BackupStatus.FAILED,
                    errorMessage = e.message
                ))
                _backupState.value = BackupState.Error(e.message ?: "Unknown error")
            }
        }
        
        return recordId
    }
    
    private suspend fun executeBackup(config: BackupConfig, record: BackupRecord) {
        var retryCount = 0
        var success = false
        
        while (retryCount < MAX_RETRY_COUNT && !success) {
            try {
                val result = performBackupOperation(config, record)
                
                recordDao.update(record.copy(
                    size = result.totalSize,
                    fileCount = result.fileCount,
                    status = BackupStatus.COMPLETED,
                    completedAt = System.currentTimeMillis()
                ))
                
                configDao.updateLastBackupTime(config.id, System.currentTimeMillis())
                
                // 清理旧备份
                cleanupOldBackups(config.id, config.maxBackups)
                
                success = true
                _backupState.value = BackupState.Idle
                
            } catch (e: Exception) {
                retryCount++
                Log.w(TAG, "Backup attempt $retryCount failed", e)
                
                if (retryCount < MAX_RETRY_COUNT) {
                    delay(2000L * retryCount)
                } else {
                    throw e
                }
            }
        }
    }
    
    private data class BackupResult(val totalSize: Long, val fileCount: Int)
    
    private suspend fun performBackupOperation(config: BackupConfig, record: BackupRecord): BackupResult {
        val backupFile = File(record.backupPath)
        backupFile.parentFile?.mkdirs()
        
        var totalSize = 0L
        var fileCount = 0
        val allFiles = mutableListOf<File>()
        
        // 收集所有文件
        config.sourcePaths.forEach { path ->
            val file = File(path)
            if (file.exists()) {
                collectFiles(file, allFiles)
            }
        }
        
        val totalFiles = allFiles.size
        
        FileOutputStream(backupFile).use { fos ->
            val cos = if (config.encryptionEnabled && config.encryptionKey != null) {
                CipherOutputStream(fos, createEncryptCipher(config.encryptionKey))
            } else null
            
            val zos = ZipOutputStream(BufferedOutputStream(cos ?: fos))
            
            try {
                allFiles.forEachIndexed { index, file ->
                    if (!scope.isActive) throw CancellationException()
                    
                    addFileToZip(file, zos, config)
                    totalSize += file.length()
                    fileCount++
                    
                    val progress = ((index + 1) * 100 / totalFiles)
                    _backupState.value = BackupState.Running(config.id, progress)
                    
                    yield() // 协程协作
                }
            } finally {
                zos.close()
                cos?.close()
            }
        }
        
        return BackupResult(totalSize, fileCount)
    }
    
    private fun collectFiles(file: File, list: MutableList<File>) {
        if (file.isFile) {
            list.add(file)
        } else if (file.isDirectory) {
            file.listFiles()?.forEach { collectFiles(it, list) }
        }
    }
    
    private fun addFileToZip(file: File, zos: ZipOutputStream, config: BackupConfig) {
        val entry = ZipEntry(file.name)
        entry.time = file.lastModified()
        
        if (config.compressionEnabled) {
            entry.method = ZipEntry.DEFLATED
        } else {
            entry.method = ZipEntry.STORED
            entry.size = file.length()
            entry.compressedSize = file.length()
            entry.crc = calculateCRC(file)
        }
        
        zos.putNextEntry(entry)
        FileInputStream(file).use { fis ->
            fis.copyTo(zos, BUFFER_SIZE)
        }
        zos.closeEntry()
    }
    
    private fun calculateCRC(file: File): Long {
        val crc = java.util.zip.CRC32()
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(BUFFER_SIZE)
            var len: Int
            while (fis.read(buffer).also { len = it } != -1) {
                crc.update(buffer, 0, len)
            }
        }
        return crc.value
    }
    
    private fun createEncryptCipher(key: String): Cipher {
        val cipher = Cipher.getInstance("AES")
        val keySpec = SecretKeySpec(key.padEnd(16).take(16).toByteArray(), "AES")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec)
        return cipher
    }
    
    private suspend fun cleanupOldBackups(configId: String, maxBackups: Int) {
        recordDao.deleteOldRecords(configId, maxBackups)
    }
    
    // ========== 恢复功能 ==========
    
    suspend fun startRestore(recordId: String, targetPath: String) {
        val record = recordDao.getRecordById(recordId)
            ?: throw IllegalArgumentException("Backup record not found")
        
        scope.launch {
            try {
                recordDao.update(record.copy(status = BackupStatus.RESTORING))
                performRestore(record, targetPath)
                recordDao.update(record.copy(status = BackupStatus.COMPLETED))
                _backupState.value = BackupState.Idle
            } catch (e: Exception) {
                Log.e(TAG, "Restore failed", e)
                recordDao.update(record.copy(
                    status = BackupStatus.FAILED,
                    errorMessage = e.message
                ))
                _backupState.value = BackupState.Error(e.message ?: "Unknown error")
            }
        }
    }
    
    private suspend fun performRestore(record: BackupRecord, targetPath: String) {
        val backupFile = File(record.backupPath)
        if (!backupFile.exists()) throw FileNotFoundException("Backup file not found")
        
        val targetDir = File(targetPath)
        targetDir.mkdirs()
        
        FileInputStream(backupFile).use { fis ->
            val cis = if (record.backupPath.endsWith(".enc")) {
                // 需要解密
                CipherInputStream(fis, createDecryptCipher(/* key */))
            } else null
            
            val zis = java.util.zip.ZipInputStream(BufferedInputStream(cis ?: fis))
            
            try {
                var entry: java.util.zip.ZipEntry?
                var processedCount = 0
                
                while (zis.nextEntry.also { entry = it } != null) {
                    if (!scope.isActive) throw CancellationException()
                    
                    val outFile = File(targetDir, entry!!.name)
                    outFile.parentFile?.mkdirs()
                    
                    FileOutputStream(outFile).use { fos ->
                        zis.copyTo(fos, BUFFER_SIZE)
                    }
                    
                    outFile.setLastModified(entry!!.time)
                    processedCount++
                    
                    val progress = min((processedCount * 100 / record.fileCount), 100)
                    _backupState.value = BackupState.Restoring(record.id, progress)
                    
                    yield()
                }
            } finally {
                zis.close()
            }
        }
    }
    
    private fun createDecryptCipher(key: String): Cipher {
        val cipher = Cipher.getInstance("AES")
        val keySpec = SecretKeySpec(key.padEnd(16).take(16).toByteArray(), "AES")
        cipher.init(Cipher.DECRYPT_MODE, keySpec)
        return cipher
    }
    
    fun getBackupRecords(configId: String): Flow<List<BackupRecord>> = 
        recordDao.getRecordsByConfig(configId)
    
    private fun generateConfigId(): String = "backup_cfg_${System.currentTimeMillis()}"
    private fun generateRecordId(): String = "backup_rec_${System.currentTimeMillis()}"
    
    fun shutdown() {
        scope.cancel()
    }
}