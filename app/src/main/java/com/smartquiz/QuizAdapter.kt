package com.smartquiz

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.smartquiz.databinding.ItemQuizBinding

class QuizAdapter(
    private var quizList: List<Quiz>,
    private val onQuizClick: (Quiz) -> Unit
) : RecyclerView.Adapter<QuizAdapter.QuizViewHolder>() {

    class QuizViewHolder(val binding: ItemQuizBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuizViewHolder {
        val binding = ItemQuizBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return QuizViewHolder(binding)
    }

    override fun onBindViewHolder(holder: QuizViewHolder, position: Int) {
        val quiz = quizList[position]
        holder.binding.tvQuizTitle.text = quiz.title
        holder.binding.tvQuizDescription.text = quiz.description   // fixed ID
        holder.itemView.setOnClickListener { onQuizClick(quiz) }
    }

    override fun getItemCount() = quizList.size

    fun updateList(newList: List<Quiz>) {
        quizList = newList
        notifyDataSetChanged()
    }
}