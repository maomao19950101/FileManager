# FileManager - Android文件管理器

一个功能完整的Android文件管理应用，支持文件浏览、跨设备传输、备份恢复和自动同步。

## ✨ 功能特性

### 📁 文件管理
- 浏览本地文件系统
- 复制、移动、删除、重命名文件
- 支持多选操作
- 文件详情查看

### 🔄 跨设备传输
- **断点续传** - 大文件传输中断后可恢复
- **任务队列** - 优先级队列管理多个传输任务
- **失败重试** - 最多3次重试，指数退避策略
- **分块传输** - 1MB分块，优化大文件稳定性

### 💾 备份恢复
- 完整/增量备份
- ZIP压缩
- AES加密支持
- 自动清理旧备份

### 🔄 自动同步
- 双向/单向同步模式
- 文件差异扫描 (MD5校验)
- 冲突检测与处理
- 定时同步 (WorkManager)

## 🛠 技术栈

| 组件 | 技术 |
|------|------|
| 构建工具 | Gradle 8.2.2 |
| Android SDK | 33 (Target & Compile) |
| 最低版本 | 24 (Android 7.0) |
| 架构 | MVVM + Repository |
| 异步 | Kotlin Coroutines + Flow |
| 数据库 | Room |
| 后台任务 | WorkManager |

## 📁 项目结构

```
app/src/main/java/com/filemanager/
├── core/
│   ├── FileManagerApp.kt          # Application入口
│   ├── BootReceiver.kt            # 开机启动
│   ├── transfer/
│   │   └── TransferManager.kt     # 传输管理器
│   ├── backup/
│   │   └── BackupManager.kt       # 备份管理器
│   ├── sync/
│   │   ├── SyncManager.kt         # 同步管理器
│   │   └── SyncWorker.kt          # 后台同步Worker
│   ├── database/                  # Room数据库
│   └── model/                     # 数据模型
├── ui/                            # 界面Activity
└── utils/                         # 工具类
```

## 🚀 快速开始

### 1. 克隆仓库
```bash
git clone <你的远程仓库地址>
cd FileManager
```

### 2. 用Android Studio打开
- File → Open → 选择项目文件夹
- 等待Gradle同步完成

### 3. 运行
- 连接Android设备或启动模拟器
- 点击 Run ▶️

## 🔧 配置远程仓库

如果你还没有配置远程仓库：

```bash
# 添加远程仓库（替换为你的实际地址）
git remote add origin https://github.com/username/FileManager.git
# 或 SSH方式
git remote add origin git@github.com:username/FileManager.git

# 推送代码
git push -u origin main
```

## 📝 开发计划

- [ ] 添加更多文件类型的预览支持
- [ ] 实现WebDAV/FTP远程同步协议
- [ ] 添加文件搜索功能
- [ ] 支持云存储（Google Drive, Dropbox等）

## 📄 许可证

MIT License
