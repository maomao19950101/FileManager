package com.filemanager.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.filemanager.core.FileManagerApp
import com.filemanager.core.model.BackupConfig
import com.filemanager.core.model.BackupType
import com.filemanager.databinding.ActivityBackupBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BackupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBackupBinding
    private val backupManager by lazy { (application as FileManagerApp).backupManager }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBackupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupUI()
        observeData()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "备份与恢复"
    }

    private fun setupUI() {
        binding.fabAddBackup.setOnClickListener {
            showCreateBackupDialog()
        }

        binding.btnBackupNow.setOnClickListener {
            // 执行备份
            Toast.makeText(this, "开始备份...", Toast.LENGTH_SHORT).show()
        }

        binding.btnRestore.setOnClickListener {
            // 恢复备份
            showRestoreDialog()
        }
    }

    private fun observeData() {
        lifecycleScope.launch {
            backupManager.backupState.collectLatest { state ->
                when (state) {
                    is com.filemanager.core.backup.BackupManager.BackupState.Running -> {
                        binding.progressBar.progress = state.progress
                    }
                    is com.filemanager.core.backup.BackupManager.BackupState.Restoring -> {
                        binding.progressBar.progress = state.progress
                    }
                    else -> {}
                }
            }
        }
    }

    private fun showCreateBackupDialog() {
        // 显示创建备份配置对话框
        AlertDialog.Builder(this)
            .setTitle("创建备份任务")
            .setMessage("请输入备份名称和选择要备份的文件夹")
            .setPositiveButton("确定") { _, _ ->
                createBackupConfig()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun createBackupConfig() {
        lifecycleScope.launch {
            try {
                val configId = backupManager.createBackupConfig(
                    name = "备份_${System.currentTimeMillis()}",
                    sourcePaths = listOf("/sdcard/Documents"),
                    backupLocation = "/sdcard/Backups",
                    backupType = BackupType.FULL,
                    compressionEnabled = true,
                    encryptionEnabled = false
                )
                Toast.makeText(this@BackupActivity, "备份配置已创建", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@BackupActivity, "创建失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showRestoreDialog() {
        AlertDialog.Builder(this)
            .setTitle("恢复备份")
            .setMessage("请选择要恢复的备份文件")
            .setPositiveButton("确定", null)
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
