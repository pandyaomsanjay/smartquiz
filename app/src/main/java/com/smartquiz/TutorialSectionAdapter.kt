package com.smartquiz

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.smartquiz.databinding.ItemTutorialSectionBinding

class TutorialSectionAdapter(
    private val sections: List<TutorialSection>
) : RecyclerView.Adapter<TutorialSectionAdapter.SectionViewHolder>() {

    private val expandedStates = mutableMapOf<Int, Boolean>().apply {
        sections.indices.forEach { this[it] = false } // all collapsed by default
    }

    class SectionViewHolder(val binding: ItemTutorialSectionBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SectionViewHolder {
        val binding = ItemTutorialSectionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SectionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SectionViewHolder, position: Int) {
        val section = sections[position]
        val isExpanded = expandedStates[position] ?: false

        holder.binding.ivIcon.setImageResource(section.iconRes)
        holder.binding.tvTitle.text = section.title
        holder.binding.tvDescription.text = section.description
        holder.binding.tvExpandIndicator.text = if (isExpanded) "▲" else "▼"

        // Bullet points
        val bulletContainer = holder.binding.llBulletPoints
        bulletContainer.removeAllViews()
        if (isExpanded) {
            bulletContainer.visibility = View.VISIBLE
            section.bulletPoints.forEach { point ->
                val tv = TextView(holder.itemView.context).apply {
                    text = "• $point"
                    textSize = 14f
                    setTextColor(holder.itemView.context.getColor(R.color.text_secondary))
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    setPadding(0, 4, 0, 4)
                }
                bulletContainer.addView(tv)
            }
        } else {
            bulletContainer.visibility = View.GONE
        }

        // Toggle expansion on click
        holder.itemView.setOnClickListener {
            expandedStates[position] = !isExpanded
            notifyItemChanged(position)
        }
    }

    override fun getItemCount(): Int = sections.size
}