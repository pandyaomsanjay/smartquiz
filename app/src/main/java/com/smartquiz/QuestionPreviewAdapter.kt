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
        val context = holder.itemView.context

        when (q.questionType) {

            // ============================================================
            // SCENARIO
            // ============================================================
            "scenario" -> {
                holder.tvQuestionText.text =
                    q.scenarioText.ifBlank { "(empty scenario)" }
                holder.tvQuestionType.text =
                    "Scenario (${q.subQuestions.size} question${if (q.subQuestions.size == 1) "" else "s"})"

                val container = holder.llOptions
                container.removeAllViews()

                q.subQuestions.forEachIndexed { index, sub ->
                    // Divider before each sub-question (except the first)
                    if (index > 0) {
                        val divider = View(context)
                        divider.layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 1
                        )
                        divider.setBackgroundColor(
                            context.getColor(R.color.border_light)
                        )
                        container.addView(divider)
                    }

                    // Sub-question header: "Q1 [Radio]"
                    val typeLabel = when (sub.questionType) {
                        "radio" -> "Radio"
                        "checkbox" -> "Checkbox"
                        else -> "Descriptive"
                    }
                    val header = TextView(context)
                    header.text = "Q${index + 1} [$typeLabel]  •  ${sub.points} pts"
                    header.textSize = 12f
                    header.setTextColor(context.getColor(R.color.accent))
                    header.setPadding(0, 8, 0, 2)
                    container.addView(header)

                    // Sub-question text
                    val textView = TextView(context)
                    textView.text = sub.text.ifBlank { "(empty)" }
                    textView.textSize = 14f
                    textView.setTextColor(context.getColor(R.color.text_primary))
                    textView.setPadding(0, 2, 0, 4)
                    container.addView(textView)

                    // Render correct answers for the sub-question
                    when (sub.questionType) {
                        "radio", "checkbox" -> {
                            for ((idx, option) in sub.options.withIndex()) {
                                val optionView = TextView(context)
                                val isCorrect = if (sub.questionType == "radio") {
                                    idx == sub.correctAnswerIndex
                                } else {
                                    sub.correctAnswerIndices.contains(idx)
                                }
                                val prefix = if (isCorrect) "✓ " else "    "
                                optionView.text = "$prefix${('A' + idx)}. $option"
                                optionView.textSize = 13f
                                optionView.setTextColor(
                                    if (isCorrect)
                                        context.getColor(R.color.success)
                                    else
                                        context.getColor(R.color.text_secondary)
                                )
                                optionView.setPadding(12, 1, 0, 1)
                                container.addView(optionView)
                            }
                        }
                        "descriptive" -> {
                            val answerView = TextView(context)
                            answerView.text = "Correct Answer: ${sub.correctAnswerText}"
                            answerView.textSize = 13f
                            answerView.setTextColor(context.getColor(R.color.success))
                            answerView.setPadding(12, 2, 0, 2)
                            container.addView(answerView)
                        }
                    }
                }

                holder.tvPoints.text = "Total points: ${q.totalPoints()}"
            }

            // ============================================================
            // RADIO / CHECKBOX
            // ============================================================
            "radio", "checkbox" -> {
                holder.tvQuestionText.text = q.text
                holder.tvQuestionType.text =
                    if (q.questionType == "radio")
                        "Radio (Single Choice)"
                    else
                        "Checkbox (Multiple Choice)"

                val container = holder.llOptions
                container.removeAllViews()
                for ((idx, option) in q.options.withIndex()) {
                    val tv = TextView(context)
                    val isCorrect = if (q.questionType == "radio") {
                        idx == q.correctAnswerIndex
                    } else {
                        q.correctAnswerIndices.contains(idx)
                    }
                    val prefix = if (isCorrect) "✓ " else "  "
                    tv.text = String.format("%s%s. %s", prefix, ('A' + idx), option)
                    tv.textSize = 14f
                    tv.setTextColor(
                        if (isCorrect)
                            context.getColor(R.color.success)
                        else
                            context.getColor(R.color.text_secondary)
                    )
                    container.addView(tv)
                }

                holder.tvPoints.text = "Points: ${q.points}"
            }

            // ============================================================
            // DESCRIPTIVE
            // ============================================================
            "descriptive" -> {
                holder.tvQuestionText.text = q.text
                holder.tvQuestionType.text = "Descriptive (Fill in the blanks)"

                val container = holder.llOptions
                container.removeAllViews()
                val tv = TextView(context)
                tv.text = "Correct Answer: ${q.correctAnswerText}"
                tv.textSize = 14f
                tv.setTextColor(context.getColor(R.color.success))
                container.addView(tv)

                holder.tvPoints.text = "Points: ${q.points}"
            }
        }

        holder.btnEdit.setOnClickListener { onEditClick(q) }
        holder.btnDelete.setOnClickListener { onDeleteClick(q) }
    }

    override fun getItemCount() = questions.size

    fun updateList(newList: List<Question>) {
        questions = newList
        notifyDataSetChanged()
    }
}