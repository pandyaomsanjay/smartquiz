package com.smartquiz

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.smartquiz.databinding.ItemImportPreviewBinding

class ImportPreviewAdapter(
    private var items: List<Row>,
    private val onEdit: (Int) -> Unit,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<ImportPreviewAdapter.VH>() {

    /**
     * A single row in the preview list.
     *  - [Valid] rows carry a parsed Question and its position in the pending list.
     *  - [Invalid] rows carry the error message + raw content.
     *  - [Duplicate] rows are valid but flagged as duplicates of an earlier row.
     */
    sealed class Row {
        abstract val displayIndex: Int   // 1-based number for the UI

        data class Valid(
            override val displayIndex: Int,
            val pendingIndex: Int,
            val question: Question
        ) : Row()

        data class Duplicate(
            override val displayIndex: Int,
            val pendingIndex: Int,
            val question: Question
        ) : Row()

        data class Invalid(
            override val displayIndex: Int,
            val error: String,
            val raw: String
        ) : Row()
    }

    class VH(val b: ItemImportPreviewBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemImportPreviewBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = items[position]
        val ctx = holder.itemView.context

        holder.b.tvIndex.text = "Q${row.displayIndex}"

        when (row) {
            is Row.Valid -> {
                val q = row.question
                holder.b.tvType.text = q.questionType.uppercase()
                holder.b.tvQuestionText.text = q.text

                val optionsText = if (q.options.isNotEmpty()) {
                    q.options.mapIndexed { i, o -> "${('A' + i)}. $o" }.joinToString("\n")
                } else ""

                val correctText = when (q.questionType) {
                    "radio" -> "Correct: ${('A' + q.correctAnswerIndex)}"
                    "checkbox" ->
                        "Correct: " + q.correctAnswerIndices.map { ('A' + it) }.joinToString(", ")
                    "descriptive" -> "Correct: ${q.correctAnswerText}"
                    else -> ""
                }

                holder.b.tvDetails.text = buildString {
                    if (optionsText.isNotBlank()) append(optionsText).append("\n")
                    append(correctText).append("\n")
                    append("Marks: ${q.points}")
                }

                holder.b.tvStatusBadge.text = "Valid"
                holder.b.tvStatusBadge.setBackgroundColor(
                    ContextCompat.getColor(ctx, R.color.success)
                )

                holder.b.llActions.visibility = View.VISIBLE
                holder.b.btnEdit.setOnClickListener { onEdit(row.pendingIndex) }
                holder.b.btnDelete.setOnClickListener { onDelete(row.pendingIndex) }
            }

            is Row.Duplicate -> {
                val q = row.question
                holder.b.tvType.text = q.questionType.uppercase()
                holder.b.tvQuestionText.text = q.text
                holder.b.tvDetails.text = "⚠ Duplicate of an earlier question"

                holder.b.tvStatusBadge.text = "Duplicate"
                holder.b.tvStatusBadge.setBackgroundColor(
                    ContextCompat.getColor(ctx, R.color.warning)
                )

                holder.b.llActions.visibility = View.VISIBLE
                holder.b.btnEdit.setOnClickListener { onEdit(row.pendingIndex) }
                holder.b.btnDelete.setOnClickListener { onDelete(row.pendingIndex) }
            }

            is Row.Invalid -> {
                holder.b.tvType.text = "—"
                holder.b.tvQuestionText.text = "Row ${row.displayIndex} (invalid)"
                holder.b.tvDetails.text = "❌ ${row.error}\n${row.raw.take(120)}"

                holder.b.tvStatusBadge.text = "Invalid"
                holder.b.tvStatusBadge.setBackgroundColor(
                    ContextCompat.getColor(ctx, R.color.error)
                )

                holder.b.llActions.visibility = View.GONE
            }
        }
    }

    override fun getItemCount() = items.size

    fun update(newItems: List<Row>) {
        items = newItems
        notifyDataSetChanged()
    }
}