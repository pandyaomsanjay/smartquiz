package com.smartquiz

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.smartquiz.databinding.ActivityResultBinding

class ResultActivity : AppCompatActivity() {
    private lateinit var binding: ActivityResultBinding
    private var quizId = ""
    private var quizTitle = ""
    private var score = 0
    private var total = 0
    private var timeTakenSeconds = 0L
    private var submissionReason = "NORMAL"
    private var showScore = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        quizId = intent.getStringExtra("quizId") ?: ""
        quizTitle = intent.getStringExtra("quizTitle") ?: "Quiz"
        score = intent.getIntExtra("score", 0)
        total = intent.getIntExtra("total", 0)
        timeTakenSeconds = intent.getLongExtra("timeTaken", 0L)
        submissionReason = intent.getStringExtra("submissionReason") ?: "NORMAL"
        showScore = intent.getBooleanExtra("showScore", true)

        binding.tvQuizTitle.text = quizTitle

        if (showScore) {
            binding.tvScore.text = "Score: $score / $total"
            val percentage = if (total > 0) (score * 100 / total) else 0
            binding.tvPercentage.text = "Percentage: $percentage%"
            binding.tvScore.visibility = View.VISIBLE
            binding.tvPercentage.visibility = View.VISIBLE
            binding.tvScoreHidden.visibility = View.GONE
        } else {
            binding.tvScore.visibility = View.GONE
            binding.tvPercentage.visibility = View.GONE
            binding.tvScoreHidden.visibility = View.VISIBLE
            binding.tvScoreHidden.text = "Your score is hidden by the quiz creator."
        }

        val timeFormatted = formatDuration(timeTakenSeconds)
        binding.tvTimeTaken.text = "Time Taken: $timeFormatted"

        val (statusText, reasonText) = getStatusAndReason()
        binding.tvStatus.text = statusText
        if (reasonText.isNotEmpty()) {
            binding.tvReason.visibility = View.VISIBLE
            binding.tvReason.text = "Reason: $reasonText"
        } else {
            binding.tvReason.visibility = View.GONE
        }

        updateJoinedQuizIfNeeded()

        binding.btnHome.setOnClickListener {
            startActivity(Intent(this, HomeDashboardActivity::class.java))
            finish()
        }

        binding.btnRateQuiz.setOnClickListener {
            val intent = Intent(this, FeedbackActivity::class.java)
            intent.putExtra("quizId", quizId)
            startActivity(intent)
        }
    }

    private fun getStatusAndReason(): Pair<String, String> {
        return when (submissionReason) {
            "NORMAL" -> "✅ Completed" to ""
            "TIMER_EXPIRED" -> "⏱️ Time Expired" to "Quiz was auto-submitted because time ran out."
            "THREE_CHEAT_WARNINGS", "CHEAT_LIMIT_REACHED" -> "⚠️ Automatically Submitted" to "Three Suspicious Activity Warnings"
            else -> "📋 Submitted" to ""
        }
    }

    private fun updateJoinedQuizIfNeeded() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val joinedRef = db.collection("users").document(userId)
            .collection("joinedQuizzes").document(quizId)

        joinedRef.get().addOnSuccessListener { doc ->
            if (doc.exists() && doc.getString("status") != "Completed") {
                joinedRef.update(
                    mapOf(
                        "status" to "Completed",
                        "submitTime" to System.currentTimeMillis(),
                        "score" to score
                    )
                ).addOnFailureListener {
                    Toast.makeText(this, "Failed to update quiz status", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}