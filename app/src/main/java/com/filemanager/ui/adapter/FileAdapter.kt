package com.filemanager.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.filemanager.R
import com.filemanager.utils.FileUtils
import java.io.File

class FileAdapter(
    private val onItemClick: (File, Boolean) -> Unit
) : ListAdapter<File, FileAdapter.FileViewHolder>(FileDiffCallback()) {

    private val selectedFiles = mutableSetOf<File>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = getItem(position)
        holder.bind(file, selectedFiles.contains(file))
    }

    fun toggleSelection(file: File) {
        if (selectedFiles.contains(file)) {
            selectedFiles.remove(file)
        } else {
            selectedFiles.add(file)
        }
        notifyDataSetChanged()
    }

    fun selectAll() {
        currentList.forEach { selectedFiles.add(it) }
        notifyDataSetChanged()
    }

    fun clearSelection() {
        selectedFiles.clear()
        notifyDataSetChanged()
    }

    fun getSelectedFiles(): Set<File> = selectedFiles.toSet()

    fun setSelectedFiles(files: Set<File>) {
        selectedFiles.clear()
        selectedFiles.addAll(files)
        notifyDataSetChanged()
    }

    inner class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivIcon: ImageView = itemView.findViewById(R.id.ivIcon)
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvInfo: TextView = itemView.findViewById(R.id.tvInfo)

        fun bind(file: File, isSelected: Boolean) {
            tvName.text = file.name

            if (file.isDirectory) {
                ivIcon.setImageResource(R.drawable.ic_folder)
                val count = file.listFiles()?.size ?: 0
                tvInfo.text = "$count 项"
            } else {
                ivIcon.setImageResource(getFileIcon(file))
                tvInfo.text = "${FileUtils.getFileSize(file.length())} · ${FileUtils.getFileDate(file.lastModified())}"
            }

            itemView.isSelected = isSelected
            itemView.setBackgroundColor(
                if (isSelected) itemView.context.getColor(R.color.selected_item)
                else itemView.context.getColor(android.R.color.transparent)
            )

            itemView.setOnClickListener {
                onItemClick(file, false)
            }

            itemView.setOnLongClickListener {
                onItemClick(file, true)
                true
            }
        }

        private fun getFileIcon(file: File): Int {
            return when (FileUtils.getFileExtension(file.name).lowercase()) {
                "jpg", "jpeg", "png", "gif", "bmp", "webp" -> R.drawable.ic_image
                "mp4", "avi", "mkv", "mov", "flv" -> R.drawable.ic_video
                "mp3", "wav", "flac", "aac", "ogg" -> R.drawable.ic_audio
                "pdf" -> R.drawable.ic_pdf
                "doc", "docx" -> R.drawable.ic_document
                "xls", "xlsx" -> R.drawable.ic_spreadsheet
                "ppt", "pptx" -> R.drawable.ic_presentation
                "txt", "log", "md" -> R.drawable.ic_text
                "zip", "rar", "7z", "tar", "gz" -> R.drawable.ic_archive
                "apk" -> R.drawable.ic_apk
                else -> R.drawable.ic_file
            }
        }
    }

    class FileDiffCallback : DiffUtil.ItemCallback<File>() {
        override fun areItemsTheSame(oldItem: File, newItem: File): Boolean {
            return oldItem.absolutePath == newItem.absolutePath
        }

        override fun areContentsTheSame(oldItem: File, newItem: File): Boolean {
            return oldItem.length() == newItem.length() &&
                    oldItem.lastModified() == newItem.lastModified()
        }
    }
}
