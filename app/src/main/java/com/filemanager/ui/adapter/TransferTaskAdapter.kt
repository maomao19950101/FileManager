package com.filemanager.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.filemanager.R
import com.filemanager.core.model.TransferStatus
import com.filemanager.core.model.TransferTask
import com.filemanager.utils.FileUtils

class TransferTaskAdapter(
    private val onPauseClick: (String) -> Unit,
    private val onResumeClick: (String) -> Unit,
    private val onCancelClick: (String) -> Unit,
    private val onRetryClick: (String) -> Unit
) : ListAdapter<TransferTask, TransferTaskAdapter.TaskViewHolder>(TaskDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transfer_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvFileName: TextView = itemView.findViewById(R.id.tvFileName)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val tvProgress: TextView = itemView.findViewById(R.id.tvProgress)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)
        private val btnAction: ImageButton = itemView.findViewById(R.id.btnAction)
        private val btnCancel: ImageButton = itemView.findViewById(R.id.btnCancel)

        fun bind(task: TransferTask) {
            val fileName = task.sourcePath.substringAfterLast("/")
            tvFileName.text = fileName

            val statusText = when (task.status) {
                TransferStatus.PENDING -> "等待中"
                TransferStatus.RUNNING -> "传输中"
                TransferStatus.PAUSED -> "已暂停"
                TransferStatus.COMPLETED -> "已完成"
                TransferStatus.FAILED -> "失败"
                TransferStatus.CANCELLED -> "已取消"
            }
            tvStatus.text = statusText

            val progressText = if (task.totalBytes > 0) {
                "${FileUtils.getFileSize(task.bytesTransferred)} / ${FileUtils.getFileSize(task.totalBytes)}"
            } else {
                FileUtils.getFileSize(task.bytesTransferred)
            }
            tvProgress.text = progressText

            progressBar.progress = task.progress
            progressBar.isIndeterminate = task.status == TransferStatus.PENDING

            // 设置操作按钮
            when (task.status) {
                TransferStatus.RUNNING -> {
                    btnAction.setImageResource(R.drawable.ic_pause)
                    btnAction.setOnClickListener { onPauseClick(task.id) }
                    btnAction.visibility = View.VISIBLE
                    btnCancel.visibility = View.VISIBLE
                    btnCancel.setOnClickListener { onCancelClick(task.id) }
                }
                TransferStatus.PAUSED, TransferStatus.PENDING -> {
                    btnAction.setImageResource(R.drawable.ic_play)
                    btnAction.setOnClickListener { onResumeClick(task.id) }
                    btnAction.visibility = View.VISIBLE
                    btnCancel.visibility = View.VISIBLE
                    btnCancel.setOnClickListener { onCancelClick(task.id) }
                }
                TransferStatus.FAILED -> {
                    btnAction.setImageResource(R.drawable.ic_retry)
                    btnAction.setOnClickListener { onRetryClick(task.id) }
                    btnAction.visibility = View.VISIBLE
                    btnCancel.visibility = View.GONE
                }
                TransferStatus.COMPLETED, TransferStatus.CANCELLED -> {
                    btnAction.visibility = View.GONE
                    btnCancel.visibility = View.GONE
                }
            }
        }
    }

    class TaskDiffCallback : DiffUtil.ItemCallback<TransferTask>() {
        override fun areItemsTheSame(oldItem: TransferTask, newItem: TransferTask): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TransferTask, newItem: TransferTask): Boolean {
            return oldItem == newItem
        }
    }
}
