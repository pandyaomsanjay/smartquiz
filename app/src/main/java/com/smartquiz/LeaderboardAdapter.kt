package com.smartquiz

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.smartquiz.databinding.ItemLeaderboardBinding

class LeaderboardAdapter(
    private var entries: List<LeaderboardEntry>,
    private val showScores: Boolean = true
) : RecyclerView.Adapter<LeaderboardAdapter.ViewHolder>() {

    private var highlightedUserId: String? = null

    class ViewHolder(val binding: ItemLeaderboardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLeaderboardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]

        if (showScores) {
            holder.binding.tvRank.text = "#${entry.rank}"
            holder.binding.tvScore.text = "${entry.totalScore} pts"
            holder.binding.tvRank.visibility = View.VISIBLE
            holder.binding.tvScore.visibility = View.VISIBLE
        } else {
            holder.binding.tvRank.visibility = View.GONE
            holder.binding.tvScore.visibility = View.GONE
        }

        holder.binding.tvName.text = entry.name

        val isCurrentUser = entry.userId == highlightedUserId
        holder.binding.root.setBackgroundColor(
            if (isCurrentUser) Color.parseColor("#E0E7FF") else Color.TRANSPARENT
        )
        holder.binding.tvYouLabel.visibility = if (isCurrentUser) View.VISIBLE else View.GONE
    }

    override fun getItemCount() = entries.size

    fun updateList(newList: List<LeaderboardEntry>) {
        entries = newList
        notifyDataSetChanged()
    }

    fun setHighlightedUser(userId: String?) {
        highlightedUserId = userId
    }
}