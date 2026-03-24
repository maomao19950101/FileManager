package com.filemanager.ui

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.filemanager.core.FileManagerApp
import com.filemanager.core.model.TransferStatus
import com.filemanager.databinding.ActivityTransferBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TransferActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityTransferBinding
    private lateinit var adapter: TransferTaskAdapter
    private val transferManager by lazy { (application as FileManagerApp).transferManager }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransferBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupRecyclerView()
        setupFab()
        observeData()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "文件传输"
    }
    
    private fun setupRecyclerView() {
        adapter = TransferTaskAdapter(
            onPauseClick = { transferManager.pauseTask(it) },
            onResumeClick = { transferManager.resumeTask(it) },
            onCancelClick = { transferManager.cancelTask(it) },
            onRetryClick = { 
                lifecycleScope.launch { transferManager.retryFailedTask(it) }
            }
        )
        
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }
    
    private fun setupFab() {
        binding.fabAdd.setOnClickListener {
            // 显示添加传输任务对话框
            showAddTransferDialog()
        }
    }
    
    private fun observeData() {
        lifecycleScope.launch {
            transferManager.transferState.collectLatest { state ->
                updateStatusUI(state)
            }
        }
        
        // 观察任务列表
        // TODO: 实现任务列表观察
    }
    
    private fun updateStatusUI(state: com.filemanager.core.transfer.TransferManager.TransferState) {
        val statusText = when (state) {
            is com.filemanager.core.transfer.TransferManager.TransferState.Idle -> "就绪"
            is com.filemanager.core.transfer.TransferManager.TransferState.Running -> 
                "进行中: ${state.activeCount}个任务"
            is com.filemanager.core.transfer.TransferManager.TransferState.Paused -> 
                "已暂停: ${state.pausedCount}个任务"
            is com.filemanager.core.transfer.TransferManager.TransferState.Error -> 
                "错误: ${state.message}"
        }
        binding.tvStatus.text = statusText
    }
    
    private fun showAddTransferDialog() {
        // 实现添加传输任务对话框
    }
}