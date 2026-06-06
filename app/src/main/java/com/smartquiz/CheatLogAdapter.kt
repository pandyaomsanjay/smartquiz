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
        holder.binding.tvUserId.text = "User: ${log.userId}"
        holder.binding.tvQuizId.text = "Quiz: ${log.quizId}"
        holder.binding.tvReason.text = "Reason: ${log.reason}"
        holder.binding.tvDevice.text = "Device: ${log.deviceInfo}"
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))
        holder.binding.tvTimestamp.text = date
    }

    override fun getItemCount() = logs.size
}