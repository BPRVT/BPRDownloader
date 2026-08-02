package com.bprvt.bprdownloader.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bprvt.bprdownloader.data.HistoryEntry
import com.bprvt.bprdownloader.databinding.ItemHistoryBinding
import com.bprvt.bprdownloader.util.Format

class HistoryAdapter(
    private val onClick: (HistoryEntry) -> Unit,
    private val onLongClick: (HistoryEntry) -> Unit
) : ListAdapter<HistoryEntry, HistoryAdapter.Holder>(DIFF) {

    class Holder(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = getItem(position)
        with(holder.binding) {
            title.text = entry.label
            subtitle.text = entry.url
            star.visibility = if (entry.favorite) View.VISIBLE else View.INVISIBLE

            val parts = mutableListOf(Format.relativeTime(entry.lastUsed))
            if (entry.sizeBytes > 0) parts.add(Format.bytes(entry.sizeBytes))
            if (entry.useCount > 1) parts.add("×${entry.useCount}")
            meta.text = parts.joinToString("\n")

            root.setOnClickListener { onClick(entry) }
            root.setOnLongClickListener {
                onLongClick(entry)
                true
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<HistoryEntry>() {
            override fun areItemsTheSame(a: HistoryEntry, b: HistoryEntry) = a.id == b.id
            override fun areContentsTheSame(a: HistoryEntry, b: HistoryEntry) = a == b
        }
    }
}
