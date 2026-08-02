package com.bprvt.bprdownloader.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bprvt.bprdownloader.databinding.ItemFileBinding
import com.bprvt.bprdownloader.util.Format
import java.io.File

class FilesAdapter(
    private val onClick: (File) -> Unit
) : ListAdapter<File, FilesAdapter.Holder>(DIFF) {

    class Holder(val binding: ItemFileBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val file = getItem(position)
        with(holder.binding) {
            name.text = file.name
            detail.text = "${Format.bytes(file.length())}  ·  ${Format.relativeTime(file.lastModified())}"
            root.setOnClickListener { onClick(file) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<File>() {
            override fun areItemsTheSame(a: File, b: File) = a.absolutePath == b.absolutePath
            override fun areContentsTheSame(a: File, b: File) =
                a.absolutePath == b.absolutePath && a.length() == b.length()
        }
    }
}
