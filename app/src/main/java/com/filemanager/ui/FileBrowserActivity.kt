package com.filemanager.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.filemanager.databinding.ActivityFileBrowserBinding
import com.filemanager.utils.FileUtils
import java.io.File

class FileBrowserActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityFileBrowserBinding
    private lateinit var adapter: FileAdapter
    private var currentPath: String = FileUtils.getExternalStoragePath()
    private val selectedFiles = mutableSetOf<File>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFileBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupRecyclerView()
        loadFiles(currentPath)
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "文件管理"
    }
    
    private fun setupRecyclerView() {
        adapter = FileAdapter { file, isLongClick ->
            if (isLongClick) {
                toggleSelection(file)
            } else {
                if (file.isDirectory) {
                    currentPath = file.absolutePath
                    loadFiles(currentPath)
                } else {
                    FileUtils.openFile(this, file)
                }
            }
        }
        
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }
    
    private fun loadFiles(path: String) {
        val files = FileUtils.listFiles(path)
        adapter.submitList(files)
        binding.tvPath.text = path
    }
    
    private fun toggleSelection(file: File) {
        if (selectedFiles.contains(file)) {
            selectedFiles.remove(file)
        } else {
            selectedFiles.add(file)
        }
        adapter.setSelectedFiles(selectedFiles)
        updateActionMode()
    }
    
    private fun updateActionMode() {
        if (selectedFiles.isNotEmpty()) {
            supportActionBar?.title = "已选择 ${selectedFiles.size} 项"
        } else {
            supportActionBar?.title = "文件管理"
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_file_browser, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            R.id.action_refresh -> {
                loadFiles(currentPath)
                true
            }
            R.id.action_select_all -> {
                adapter.selectAll()
                true
            }
            R.id.action_new_folder -> {
                showNewFolderDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun showNewFolderDialog() {
        // 实现新建文件夹对话框
        Toast.makeText(this, "新建文件夹", Toast.LENGTH_SHORT).show()
    }
    
    override fun onBackPressed() {
        val parent = File(currentPath).parentFile
        if (parent != null && FileUtils.canNavigateTo(parent.absolutePath)) {
            currentPath = parent.absolutePath
            loadFiles(currentPath)
        } else {
            super.onBackPressed()
        }
    }
}