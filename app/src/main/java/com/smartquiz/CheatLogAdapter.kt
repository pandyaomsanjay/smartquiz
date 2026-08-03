package com.smartquiz

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.smartquiz.databinding.ItemCheatLogBinding
import java.text.SimpleDateFormat
import java.util.*

class CheatLogAdapter(private val logs: List<CheatLog>) :
    RecyclerView.Adapter<CheatLogAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemCheatLogBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCheatLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val log = logs[position]

        // User info
        holder.binding.tvUserId.text = "User: ${log.userName} (${log.email})"

        // Quiz info
        holder.binding.tvQuizId.text = "Quiz: ${log.quizTitle}"

        // Event type with suspicious flag and violation count
        val eventDisplay = log.eventType.replace("_", " ").capitalize()
        val suspiciousText = if (log.suspicious) " ⚠️ SUSPICIOUS" else ""
        val violationText = if (log.violationCount > 0) " (${log.violationCount} violations)" else ""
        holder.binding.tvReason.text = "Event: $eventDisplay$suspiciousText$violationText"

        // Device info
        holder.binding.tvDevice.text = "Device: ${log.deviceModel} (${log.androidVersion})"

        // Timestamp
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        holder.binding.tvTimestamp.text = dateFormat.format(Date(log.timestamp))
    }

    override fun getItemCount() = logs.size
}