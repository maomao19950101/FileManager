package com.filemanager.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.filemanager.core.FileManagerApp
import com.filemanager.core.model.SyncMode
import com.filemanager.databinding.ActivitySyncBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SyncActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySyncBinding
    private val syncManager by lazy { (application as FileManagerApp).syncManager }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySyncBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupUI()
        observeData()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "自动同步"
    }

    private fun setupUI() {
        binding.fabAddSync.setOnClickListener {
            showCreateSyncDialog()
        }

        binding.btnSyncNow.setOnClickListener {
            // 立即同步
            Toast.makeText(this, "开始同步...", Toast.LENGTH_SHORT).show()
        }

        binding.switchAutoSync.setOnCheckedChangeListener { _, isChecked ->
            // 切换自动同步
            Toast.makeText(this, "自动同步: ${if (isChecked) "开启" else "关闭"}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeData() {
        lifecycleScope.launch {
            syncManager.syncState.collectLatest { state ->
                when (state) {
                    is com.filemanager.core.sync.SyncManager.SyncState.Scanning -> {
                        binding.tvStatus.text = "正在扫描..."
                    }
                    is com.filemanager.core.sync.SyncManager.SyncState.Syncing -> {
                        binding.tvStatus.text = "正在同步: ${state.currentFile}"
                        binding.progressBar.progress = state.progress
                    }
                    is com.filemanager.core.sync.SyncManager.SyncState.Completed -> {
                        binding.tvStatus.text = "同步完成"
                        Toast.makeText(this@SyncActivity,
                            "上传: ${state.uploaded}, 下载: ${state.downloaded}",
                            Toast.LENGTH_SHORT).show()
                    }
                    is com.filemanager.core.sync.SyncManager.SyncState.Error -> {
                        binding.tvStatus.text = "同步失败: ${state.message}"
                    }
                    else -> {}
                }
            }
        }
    }

    private fun showCreateSyncDialog() {
        AlertDialog.Builder(this)
            .setTitle("创建同步任务")
            .setMessage("配置本地和远程文件夹同步")
            .setPositiveButton("确定") { _, _ ->
                createSyncConfig()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun createSyncConfig() {
        lifecycleScope.launch {
            try {
                val configId = syncManager.createSyncConfig(
                    name = "同步_${System.currentTimeMillis()}",
                    localPath = "/sdcard/Sync",
                    remotePath = "/remote/sync",
                    syncMode = SyncMode.TWO_WAY,
                    autoSync = binding.switchAutoSync.isChecked,
                    syncIntervalMinutes = 30
                )
                Toast.makeText(this@SyncActivity, "同步配置已创建", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@SyncActivity, "创建失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
