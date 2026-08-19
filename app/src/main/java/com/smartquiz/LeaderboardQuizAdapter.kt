package com.smartquiz

import LeaderboardQuizItem
import androidx.recyclerview.widget.RecyclerView
import com.smartquiz.databinding.ItemJoinedQuizLeaderboardBinding
import android.view.ViewGroup
import android.view.LayoutInflater

// LeaderboardQuizAdapter.kt
class LeaderboardQuizAdapter(
    private var items: List<LeaderboardQuizItem>,
    private val onViewLeaderboard: (quizId: String, title: String) -> Unit
) : RecyclerView.Adapter<LeaderboardQuizAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemJoinedQuizLeaderboardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemJoinedQuizLeaderboardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.tvQuizTitle.text = item.title
        holder.binding.tvQuizCode.text = "Code: ${item.quizCode}"
        holder.binding.tvScore.text = "Score: ${item.userScore}/${item.totalScore}"
        val rankText = if (item.userRank > 0) "#${item.userRank} of ${item.totalParticipants}" else "Not Attempted"
        holder.binding.tvRank.text = "Rank: $rankText"
        holder.binding.btnViewLeaderboard.setOnClickListener {
            onViewLeaderboard(item.quizId, item.title)
        }
    }

    override fun getItemCount() = items.size

    fun updateList(newList: List<LeaderboardQuizItem>) {
        items = newList
        notifyDataSetChanged()
    }
}