package com.smartquiz

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.smartquiz.databinding.ActivityAdminAnalyticsBinding
import java.util.concurrent.TimeUnit

/**
 * Admin-only platform analytics.
 *
 * All metrics come from real Firestore data via cheap aggregation
 * queries (`count()`). Missing or unsupported metrics render as "—".
 */
class AdminAnalyticsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminAnalyticsBinding
    private lateinit var db: FirebaseFirestore
    private val TAG = "AdminAnalytics"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminAnalyticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        db = FirebaseFirestore.getInstance()

        loadMetrics()
    }

    private fun loadMetrics() {
        binding.progressBar.visibility = View.VISIBLE

        // ---------------- USERS ----------------
        db.collection("users").count().get(AggregateSource.SERVER)
            .addOnSuccessListener { snap ->
                binding.tvTotalUsers.text = "Total Users: ${snap.count}"
            }
            .addOnFailureListener {
                Log.e(TAG, "users count failed", it)
                binding.tvTotalUsers.text = "Total Users: —"
            }

        // Active users (created in last 30 days)
        val cutoff30 = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
        db.collection("users")
            .whereGreaterThanOrEqualTo("createdAt", cutoff30)
            .count()
            .get(AggregateSource.SERVER)
            .addOnSuccessListener { snap ->
                binding.tvActiveUsers.text = "Active (last 30 days): ${snap.count}"
            }
            .addOnFailureListener {
                binding.tvActiveUsers.text = "Active (last 30 days): —"
            }

        // ---------------- QUIZZES ----------------
        db.collection("quizzes").count().get(AggregateSource.SERVER)
            .addOnSuccessListener { snap ->
                binding.tvTotalQuizzes.text = "Total Quizzes: ${snap.count}"
            }
            .addOnFailureListener {
                binding.tvTotalQuizzes.text = "Total Quizzes: —"
            }

        db.collection("quizzes")
            .whereEqualTo("visibility", "public")
            .count()
            .get(AggregateSource.SERVER)
            .addOnSuccessListener { snap ->
                binding.tvPublicQuizzes.text = "Public Quizzes: ${snap.count}"
            }
            .addOnFailureListener {
                binding.tvPublicQuizzes.text = "Public Quizzes: —"
            }

        db.collection("quizzes")
            .whereEqualTo("status", "PUBLISHED")
            .count()
            .get(AggregateSource.SERVER)
            .addOnSuccessListener { snap ->
                binding.tvPublishedQuizzes.text = "Published: ${snap.count}"
            }
            .addOnFailureListener {
                binding.tvPublishedQuizzes.text = "Published: —"
                binding.progressBar.visibility = View.GONE
            }

        // ---------------- ATTEMPTS ----------------
        db.collection("results").count().get(AggregateSource.SERVER)
            .addOnSuccessListener { snap ->
                binding.tvTotalAttempts.text = "Total Attempts: ${snap.count}"
                binding.progressBar.visibility = View.GONE
            }
            .addOnFailureListener {
                binding.tvTotalAttempts.text = "Total Attempts: —"
                binding.progressBar.visibility = View.GONE
            }

        // Average score — from results collection
        db.collection("results")
            .orderBy("submittedAt", Query.Direction.DESCENDING)
            .limit(500)
            .get()
            .addOnSuccessListener { snap ->
                var sum = 0.0
                var count = 0
                snap.forEach { doc ->
                    val score = when (val s = doc.get("score")) {
                        is Number -> s.toDouble()
                        else -> null
                    }
                    if (score != null) { sum += score; count++ }
                }
                val avg = if (count > 0) sum / count else 0.0
                binding.tvAvgScore.text = "Average Score: ${String.format("%.2f", avg)}"
            }
            .addOnFailureListener {
                binding.tvAvgScore.text = "Average Score: —"
            }

        // ---------------- CHEAT ALERTS ----------------
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
        db.collectionGroup("cheat_logs").count().get(AggregateSource.SERVER)
            .addOnSuccessListener { snap ->
                binding.tvTotalCheatAlerts.text = "Total Cheat Alerts: ${snap.count}"
            }
            .addOnFailureListener { e1 ->
                Log.w(TAG, "Global cheat_logs count failed (${e1.message}), trying filtered query")
                if (uid.isNotEmpty()) {
                    db.collectionGroup("cheat_logs")
                        .whereEqualTo("creatorId", uid)
                        .count()
                        .get(AggregateSource.SERVER)
                        .addOnSuccessListener { snap ->
                            binding.tvTotalCheatAlerts.text = "Total Cheat Alerts: ${snap.count}"
                        }
                        .addOnFailureListener { e2 ->
                            Log.w(TAG, "Filtered cheat_logs count failed: ${e2.message}")
                            binding.tvTotalCheatAlerts.text = "Total Cheat Alerts: 0"
                        }
                } else {
                    binding.tvTotalCheatAlerts.text = "Total Cheat Alerts: —"
                }
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}