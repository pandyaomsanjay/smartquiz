package com.smartquiz

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.smartquiz.databinding.ActivityResultBinding

class ResultActivity : AppCompatActivity() {
    private lateinit var binding: ActivityResultBinding
    private var quizId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val score = intent.getIntExtra("score", 0)
        val total = intent.getIntExtra("total", 0)
        quizId = intent.getStringExtra("quizId") ?: ""

        binding.tvScore.text = "You scored $score out of $total"
        val percentage = if (total > 0) (score * 100 / total) else 0
        binding.tvPercentage.text = "Percentage: $percentage%"

        // Update joined quiz status if not already updated (fallback)
        updateJoinedQuizIfNeeded(score, total)

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

    private fun updateJoinedQuizIfNeeded(score: Int, totalScore: Int) {
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
}