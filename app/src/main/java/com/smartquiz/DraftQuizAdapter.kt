package com.smartquiz

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.smartquiz.databinding.ItemDraftQuizBinding
import java.text.SimpleDateFormat
import java.util.*

class DraftQuizAdapter(
    private val drafts: List<Quiz>,
    private val onAction: (Action, Quiz) -> Unit
) : RecyclerView.Adapter<DraftQuizAdapter.ViewHolder>() {

    enum class Action { EDIT, DELETE, DOWNLOAD_PDF }

    class ViewHolder(val binding: ItemDraftQuizBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDraftQuizBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val quiz = drafts[position]
        holder.binding.tvTitle.text = quiz.title
        holder.binding.tvQuestions.text = "${quiz.totalQuestions} Questions"
        val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        holder.binding.tvCreated.text = "Created: ${dateFormat.format(Date(quiz.createdAt))}"
        holder.binding.tvUpdated.text = "Updated: ${dateFormat.format(Date(quiz.updatedAt))}"
        holder.binding.tvStatus.text = "DRAFT"

        holder.binding.btnEdit.setOnClickListener { onAction(Action.EDIT, quiz) }
        holder.binding.btnDelete.setOnClickListener { onAction(Action.DELETE, quiz) }
        holder.binding.btnDownloadPdf.setOnClickListener { onAction(Action.DOWNLOAD_PDF, quiz) }
    }

    override fun getItemCount() = drafts.size
}