package com.filemanager.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object FileUtils {
    
    fun getExternalStoragePath(): String {
        return Environment.getExternalStorageDirectory().absolutePath
    }
    
    fun listFiles(path: String): List<File> {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) {
            return emptyList()
        }
        
        return dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: emptyList()
    }
    
    fun canNavigateTo(path: String): Boolean {
        return File(path).canRead()
    }
    
    fun getFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
            else -> String.format("%.2f GB", size / (1024.0 * 1024 * 1024))
        }
    }
    
    fun getFileDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
    
    fun getFileExtension(fileName: String): String {
        return fileName.substringAfterLast(".", "")
    }
    
    fun getMimeType(file: File): String {
        val extension = getFileExtension(file.name)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
    }
    
    fun openFile(context: Context, file: File) {
        try {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            } else {
                Uri.fromFile(file)
            }
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, getMimeType(file))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // 没有应用可以打开此文件
        }
    }
    
    fun copyFile(source: File, dest: File): Boolean {
        return try {
            dest.parentFile?.mkdirs()
            source.copyTo(dest, overwrite = true)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    fun moveFile(source: File, dest: File): Boolean {
        return try {
            dest.parentFile?.mkdirs()
            source.renameTo(dest)
        } catch (e: Exception) {
            false
        }
    }
    
    fun deleteFile(file: File): Boolean {
        return try {
            if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
        } catch (e: Exception) {
            false
        }
    }
}