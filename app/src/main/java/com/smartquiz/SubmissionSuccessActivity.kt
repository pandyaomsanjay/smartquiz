package com.smartquiz

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.smartquiz.databinding.ActivitySubmissionSuccessBinding
import java.text.SimpleDateFormat
import java.util.*

class SubmissionSuccessActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySubmissionSuccessBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySubmissionSuccessBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val score = intent.getDoubleExtra("score", 0.0)       // Changed to Double
        val total = intent.getIntExtra("total", 0)
        val submitTime = intent.getLongExtra("submitTime", System.currentTimeMillis())

        // Display score with one decimal if fractional, otherwise as integer
        val scoreDisplay = if (score == score.toLong().toDouble()) {
            score.toInt().toString()
        } else {
            String.format("%.1f", score)
        }
        binding.tvScore.text = "Your Score: $scoreDisplay / $total"

        val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault())
        binding.tvSubmissionTime.text = "Submitted on: ${dateFormat.format(Date(submitTime))}"

        binding.btnHome.setOnClickListener {
            startActivity(Intent(this, HomeDashboardActivity::class.java))
            finish()
        }
    }
}