package com.smartquiz

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.smartquiz.databinding.ActivityAdminAnalyticsBinding

class AdminAnalyticsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminAnalyticsBinding
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminAnalyticsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        db = FirebaseFirestore.getInstance()

        loadAnalytics()
    }

    private fun loadAnalytics() {
        // Total users
        db.collection("users").get().addOnSuccessListener { users ->
            binding.tvTotalUsers.text = "Total Users: ${users.size()}"
        }
        // Total quizzes
        db.collection("quizzes").get().addOnSuccessListener { quizzes ->
            binding.tvTotalQuizzes.text = "Total Quizzes: ${quizzes.size()}"
        }
        // Total quiz attempts
        db.collection("quiz_attempts").get().addOnSuccessListener { attempts ->
            binding.tvTotalAttempts.text = "Total Attempts: ${attempts.size()}"
        }
        // Average score
        db.collection("results").get().addOnSuccessListener { results ->
            val totalScore = results.sumOf { it.getLong("score") ?: 0 }
            val avg = if (results.isEmpty()) 0.0 else totalScore.toDouble() / results.size()
            binding.tvAvgScore.text = "Average Score: ${String.format("%.2f", avg)}"
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}