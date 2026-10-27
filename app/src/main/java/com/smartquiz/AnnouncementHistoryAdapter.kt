package com.smartquiz

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.smartquiz.databinding.ItemAnnouncementHistoryBinding
import java.text.SimpleDateFormat
import java.util.*

data class AnnouncementHistoryItem(
    val id: String,
    val title: String,
    val message: String,
    val timestamp: Long
)

class AnnouncementHistoryAdapter(
    private var items: List<AnnouncementHistoryItem>,
    private val onClick: (AnnouncementHistoryItem) -> Unit
) : RecyclerView.Adapter<AnnouncementHistoryAdapter.VH>() {

    class VH(val binding: ItemAnnouncementHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemAnnouncementHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val b = holder.binding

        b.tvTitle.text = item.title
        b.tvMessage.text = item.message

        val formatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        b.tvDate.text = if (item.timestamp > 0) formatter.format(Date(item.timestamp)) else "Unknown Date"

        b.root.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = items.size

    fun updateList(newList: List<AnnouncementHistoryItem>) {
        items = newList
        notifyDataSetChanged()
    }
}