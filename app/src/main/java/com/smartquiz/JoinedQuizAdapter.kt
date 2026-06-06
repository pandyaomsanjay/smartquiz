package com.smartquiz.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.smartquiz.databinding.ItemJoinedQuizBinding
import com.smartquiz.models.JoinedQuiz
import java.text.SimpleDateFormat
import java.util.*

class JoinedQuizAdapter(
    private var quizzes: List<JoinedQuiz>,
    private val onItemClick: (JoinedQuiz) -> Unit
) : RecyclerView.Adapter<JoinedQuizAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemJoinedQuizBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemJoinedQuizBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val quiz = quizzes[position]
        holder.binding.tvQuizTitle.text = quiz.quizTitle
        holder.binding.tvQuizCode.text = "Code: ${quiz.quizCode}"
        holder.binding.tvCreator.text = "By: ${quiz.creatorName}"
        holder.binding.tvCategory.text = "Category: ${quiz.category}"
        val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        holder.binding.tvJoinDate.text = "Joined: ${dateFormat.format(Date(quiz.joinTime))}"

        // Set status chip color and text
        when (quiz.status) {
            "Completed" -> {
                holder.binding.chipStatus.text = "✅ Completed"
                holder.binding.chipStatus.chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                    holder.itemView.context.getColor(android.R.color.holo_green_light)
                )
            }
            "In Progress" -> {
                holder.binding.chipStatus.text = "⏳ In Progress"
                holder.binding.chipStatus.chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                    holder.itemView.context.getColor(android.R.color.holo_orange_light)
                )
            }
            "Expired" -> {
                holder.binding.chipStatus.text = "❌ Expired"
                holder.binding.chipStatus.chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                    holder.itemView.context.getColor(android.R.color.holo_red_light)
                )
            }
            else -> {
                holder.binding.chipStatus.text = quiz.status
            }
        }

        holder.itemView.setOnClickListener { onItemClick(quiz) }
    }

    override fun getItemCount() = quizzes.size

    fun updateList(newList: List<JoinedQuiz>) {
        quizzes = newList
        notifyDataSetChanged()
    }
}