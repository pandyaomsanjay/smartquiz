package com.smartquiz

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.smartquiz.databinding.ItemJoinedQuizLeaderboardBinding

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

        if (item.showScore) {
            holder.binding.tvScore.text = "Score: ${item.userScore}/${item.totalScore}"
            val rankText = if (item.userRank > 0) "#${item.userRank} of ${item.totalParticipants}" else "Not Attempted"
            holder.binding.tvRank.text = "Rank: $rankText"
            holder.binding.tvScore.visibility = View.VISIBLE
            holder.binding.tvRank.visibility = View.VISIBLE
            holder.binding.tvStatus.visibility = View.GONE
        } else {
            holder.binding.tvScore.visibility = View.GONE
            holder.binding.tvRank.visibility = View.GONE
            holder.binding.tvStatus.visibility = View.VISIBLE
            holder.binding.tvStatus.text = when (item.status) {
                "Completed" -> "✅ Completed"
                "In Progress" -> "⏳ In Progress"
                else -> item.status
            }
        }

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