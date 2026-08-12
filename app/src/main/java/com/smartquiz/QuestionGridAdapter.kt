package com.smartquiz

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.smartquiz.databinding.ItemQuestionGridBinding

class QuestionGridAdapter(
    private val currentIndex: Int,
    private val onItemClick: (Int) -> Unit
) : ListAdapter<QuestionState, QuestionGridAdapter.ViewHolder>(QuestionStateDiffCallback()) {

    class ViewHolder(val binding: ItemQuestionGridBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemQuestionGridBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val state = getItem(position)
        val number = position + 1
        val context = holder.itemView.context

        holder.binding.tvQuestionNumber.text = number.toString()

        val bgColor = when {
            state.isLocked -> R.color.error_light
            state.isAnswered -> R.color.success_light
            state.isMarkedForReview -> R.color.warning_light
            else -> R.color.surface_variant
        }
        holder.binding.tvQuestionNumber.setBackgroundColor(
            ContextCompat.getColor(context, bgColor)
        )

        val textColor = when {
            state.isLocked -> R.color.error
            state.isAnswered -> R.color.success
            state.isMarkedForReview -> R.color.warning
            else -> R.color.text_primary
        }
        holder.binding.tvQuestionNumber.setTextColor(
            ContextCompat.getColor(context, textColor)
        )

        val isCurrent = position == currentIndex
        holder.binding.tvQuestionNumber.setTypeface(
            null,
            if (isCurrent) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
        )

        holder.binding.ivBookmarkIndicator.visibility =
            if (state.isBookmarked) android.view.View.VISIBLE else android.view.View.GONE
        holder.binding.ivReviewIndicator.visibility =
            if (state.isMarkedForReview) android.view.View.VISIBLE else android.view.View.GONE

        holder.itemView.setOnClickListener { onItemClick(position) }
    }

    class QuestionStateDiffCallback : DiffUtil.ItemCallback<QuestionState>() {
        override fun areItemsTheSame(oldItem: QuestionState, newItem: QuestionState): Boolean {
            return oldItem.questionId == newItem.questionId
        }

        override fun areContentsTheSame(oldItem: QuestionState, newItem: QuestionState): Boolean {
            return oldItem == newItem
        }
    }
}