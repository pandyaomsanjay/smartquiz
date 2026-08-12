package com.smartquiz

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.smartquiz.databinding.ItemParticipantBinding

class ParticipantStatsAdapter(
    private var items: List<QuizStatsActivity.ParticipantStats>,
    private val onItemClick: (QuizStatsActivity.ParticipantStats) -> Unit
) : RecyclerView.Adapter<ParticipantStatsAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemParticipantBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemParticipantBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        // Serial number (position + 1)
        holder.binding.tvSrNo.text = (position + 1).toString()
        holder.binding.tvName.text = item.name
        holder.binding.tvEmail.text = item.email
        holder.binding.tvScore.text = "${item.score}/${item.totalScore} (${item.percentage}%)"
        holder.binding.tvTimeSpent.text = item.formattedDuration
        holder.binding.tvEntryTime.text = item.formattedStart
        holder.binding.tvStatus.text = item.statusDisplay

        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount() = items.size

    fun submitList(newList: List<QuizStatsActivity.ParticipantStats>) {
        items = newList
        notifyDataSetChanged()
    }
}