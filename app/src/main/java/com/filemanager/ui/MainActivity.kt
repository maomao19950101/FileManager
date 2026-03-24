package com.filemanager.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.filemanager.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val MANAGE_STORAGE_REQUEST_CODE = 1002
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        checkAndRequestPermissions()
        setupUI()
    }
    
    private fun setupUI() {
        binding.apply {
            // 文件管理
            cardFileManager.setOnClickListener {
                startActivity(Intent(this@MainActivity, FileBrowserActivity::class.java))
            }
            
            // 文件传输
            cardTransfer.setOnClickListener {
                startActivity(Intent(this@MainActivity, TransferActivity::class.java))
            }
            
            // 备份恢复
            cardBackup.setOnClickListener {
                startActivity(Intent(this@MainActivity, BackupActivity::class.java))
            }
            
            // 自动同步
            cardSync.setOnClickListener {
                startActivity(Intent(this@MainActivity, SyncActivity::class.java))
            }
            
            // 设置
            btnSettings.setOnClickListener {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }
        }
    }
    
    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 需要特殊权限
            if (!Environment.isExternalStorageManager()) {
                showManageStorageDialog()
                return
            }
        } else {
            // Android 10及以下
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }
    
    private fun showManageStorageDialog() {
        AlertDialog.Builder(this)
            .setTitle("需要文件管理权限")
            .setMessage("本应用需要访问所有文件的权限才能正常管理文件。请在设置中开启。")
            .setPositiveButton("去设置") { _, _ ->
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivityForResult(intent, MANAGE_STORAGE_REQUEST_CODE)
            }
            .setNegativeButton("取消", null)
            .setCancelable(false)
            .show()
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // 权限已授予
            } else {
                AlertDialog.Builder(this)
                    .setTitle("权限被拒绝")
                    .setMessage("没有存储权限，应用无法正常工作。")
                    .setPositiveButton("重新授权") { _, _ -> checkAndRequestPermissions() }
                    .setNegativeButton("退出") { _, _ -> finish() }
                    .setCancelable(false)
                    .show()
            }
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == MANAGE_STORAGE_REQUEST_CODE) {
            checkAndRequestPermissions()
        }
    }
}