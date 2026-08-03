package com.smartquiz

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class QuestionPreviewAdapter(
    private var questions: List<Question>,
    private val onEditClick: (Question) -> Unit,
    private val onDeleteClick: (Question) -> Unit
) : RecyclerView.Adapter<QuestionPreviewAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvQuestionText: TextView = itemView.findViewById(R.id.tvQuestionText)
        val tvQuestionType: TextView = itemView.findViewById(R.id.tvQuestionType)
        val llOptions: LinearLayout = itemView.findViewById(R.id.llOptions)
        val tvPoints: TextView = itemView.findViewById(R.id.tvPoints)
        val btnEdit: ImageButton = itemView.findViewById(R.id.btnEdit)
        val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_question_preview, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val q = questions[position]

        holder.tvQuestionText.text = q.text

        val typeDisplay = when (q.questionType) {
            "radio" -> "Radio (Single Choice)"
            "checkbox" -> "Checkbox (Multiple Choice)"
            "descriptive" -> "Descriptive (Fill in the blanks)"
            else -> "Unknown"
        }
        holder.tvQuestionType.text = typeDisplay

        val optionsContainer = holder.llOptions
        optionsContainer.removeAllViews()

        when (q.questionType) {
            "radio", "checkbox" -> {
                for ((idx, option) in q.options.withIndex()) {
                    val tv = TextView(holder.itemView.context)
                    val isCorrect = when (q.questionType) {
                        "radio" -> idx == q.correctAnswerIndex
                        "checkbox" -> q.correctAnswerIndices.contains(idx)
                        else -> false
                    }
                    val prefix = if (isCorrect) "✓ " else "  "
                    tv.text = String.format("%s%s. %s", prefix, ('A' + idx), option)
                    tv.textSize = 14f
                    tv.setTextColor(
                        if (isCorrect)
                            holder.itemView.context.getColor(R.color.success)
                        else
                            holder.itemView.context.getColor(R.color.text_secondary)
                    )
                    optionsContainer.addView(tv)
                }
            }
            "descriptive" -> {
                val tv = TextView(holder.itemView.context)
                tv.text = "Correct Answer: ${q.correctAnswerText}"
                tv.textSize = 14f
                tv.setTextColor(holder.itemView.context.getColor(R.color.success))
                optionsContainer.addView(tv)
            }
        }

        holder.tvPoints.text = holder.itemView.context.getString(R.string.points_format, q.points)

        holder.btnEdit.setOnClickListener { onEditClick(q) }
        holder.btnDelete.setOnClickListener { onDeleteClick(q) }
    }

    override fun getItemCount() = questions.size

    fun updateList(newList: List<Question>) {
        questions = newList
        notifyDataSetChanged()
    }
}