package com.smartquiz

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.smartquiz.databinding.ItemQuizCardBinding

class QuizAdapter(
    private var quizList: List<Quiz>,
    private val onQuizClick: (Quiz) -> Unit,
    private val onDeleteClick: (Quiz) -> Unit
) : RecyclerView.Adapter<QuizAdapter.QuizViewHolder>() {

    class QuizViewHolder(val binding: ItemQuizCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuizViewHolder {
        val binding = ItemQuizCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return QuizViewHolder(binding)
    }

    override fun onBindViewHolder(holder: QuizViewHolder, position: Int) {
        val quiz = quizList[position]
        holder.binding.tvQuizTitle.text = quiz.title
        val questions = quiz.totalQuestions
        // Format timer as HH:MM:SS
        val duration = formatDuration(quiz.timerSeconds.toLong())
        holder.binding.tvQuizMetadata.text = "$questions Questions • $duration"

        // Delete click
        holder.binding.ivDelete.setOnClickListener {
            onDeleteClick(quiz)
        }

        // Item click -> open stats
        holder.itemView.setOnClickListener { onQuizClick(quiz) }
    }

    override fun getItemCount() = quizList.size

    fun updateList(newList: List<Quiz>) {
        quizList = newList
        notifyDataSetChanged()
    }
}