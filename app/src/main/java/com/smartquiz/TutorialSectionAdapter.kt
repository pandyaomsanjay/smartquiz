package com.smartquiz

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.smartquiz.databinding.ItemTutorialSectionBinding

class TutorialSectionAdapter :
    ListAdapter<TutorialSection, TutorialSectionAdapter.VH>(DIFF) {

    /** Titles currently expanded. Keyed by title, not position, so it survives filtering. */
    private val expandedTitles = mutableSetOf<String>()

    class VH(val binding: ItemTutorialSectionBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<TutorialSection>() {
            override fun areItemsTheSame(a: TutorialSection, b: TutorialSection) = a.title == b.title
            override fun areContentsTheSame(a: TutorialSection, b: TutorialSection) = a == b
        }

        private fun Context.dpToPx(dp: Int): Int =
            (dp * resources.displayMetrics.density).toInt()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemTutorialSectionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val section = getItem(position)
        val b = holder.binding
        val ctx = b.root.context
        val isExpanded = expandedTitles.contains(section.title)

        b.ivIcon.setImageResource(section.iconRes)
        b.tvTitle.text = section.title
        b.tvCategory.text = section.category
        b.tvCategory.visibility = View.VISIBLE
        b.tvDescription.text = section.description

        // Rotation matches expanded state
        b.tvExpandIndicator.rotation = if (isExpanded) 180f else 0f
        b.tvExpandIndicator.contentDescription =
            ctx.getString(if (isExpanded) R.string.tutorial_collapse
            else R.string.tutorial_expand)

        // Rebuild bullet views only on bind; hidden container prevents re-layout cost
        val bulletContainer = b.llBulletPoints
        bulletContainer.removeAllViews()
        if (isExpanded && section.bulletPoints.isNotEmpty()) {
            bulletContainer.visibility = View.VISIBLE
            for (point in section.bulletPoints) {
                val tv = TextView(ctx).apply {
                    text = "• $point"
                    textSize = 14f
                    setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
                    setLineSpacing(0f, 1.15f)
                    setPadding(0, ctx.dpToPx(4), 0, ctx.dpToPx(4))
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
                bulletContainer.addView(tv)
            }
        } else {
            bulletContainer.visibility = View.GONE
        }

        b.root.setOnClickListener {
            val wasExpanded = expandedTitles.contains(section.title)
            if (wasExpanded) expandedTitles.remove(section.title)
            else expandedTitles.add(section.title)
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) notifyItemChanged(pos)
        }
    }

    /** Used by the toolbar "expand all / collapse all" (optional hook; not wired). */
    fun expandAll(sections: List<TutorialSection>) {
        expandedTitles.clear()
        sections.forEach { expandedTitles.add(it.title) }
        notifyDataSetChanged()
    }

    fun collapseAll() {
        expandedTitles.clear()
        notifyDataSetChanged()
    }
}