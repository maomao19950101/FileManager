package com.filemanager.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.filemanager.core.model.*

@Database(
    entities = [
        TransferTask::class,
        SyncConfig::class,
        BackupConfig::class,
        BackupRecord::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun transferTaskDao(): TransferTaskDao
    abstract fun syncConfigDao(): SyncConfigDao
    abstract fun backupConfigDao(): BackupConfigDao
    abstract fun backupRecordDao(): BackupRecordDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "file_manager_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}