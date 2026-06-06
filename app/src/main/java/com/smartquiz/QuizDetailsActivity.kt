package com.smartquiz

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.smartquiz.databinding.ActivityQuizDetailsBinding
import com.smartquiz.models.JoinedQuiz
import java.text.SimpleDateFormat
import java.util.*

class QuizDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQuizDetailsBinding
    private lateinit var joinedQuiz: JoinedQuiz
    private val db = FirebaseFirestore.getInstance()
    private var attemptStatus = ""
    private var submitTime: Long? = null
    private var score: Int? = null
    private var totalScore: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuizDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        joinedQuiz = intent.getSerializableExtra("joinedQuiz") as JoinedQuiz

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_back)
        supportActionBar?.title = "Quiz Details"

        loadQuizAndAttemptDetails()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        return true
    }

    private fun loadQuizAndAttemptDetails() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        db.collection("quizzes").document(joinedQuiz.quizId).get()
            .addOnSuccessListener { quizDoc ->
                val title = quizDoc.getString("title") ?: joinedQuiz.quizTitle
                val description = quizDoc.getString("description") ?: "No description"
                val visibility = quizDoc.getString("visibility") ?: "private"
                binding.tvTitle.text = title
                binding.tvDescription.text = description
                binding.tvVisibility.text = if (visibility == "public") "🌍 Public Quiz" else "🔒 Private Quiz"
            }

        db.collection("quizzes").document(joinedQuiz.quizId)
            .collection("attempts").document(userId)
            .get()
            .addOnSuccessListener { attemptDoc ->
                if (attemptDoc.exists()) {
                    attemptStatus = attemptDoc.getString("status") ?: "In Progress"
                    submitTime = attemptDoc.getLong("submitTime")
                    score = attemptDoc.getLong("score")?.toInt()
                    totalScore = attemptDoc.getLong("totalScore")?.toInt()
                } else {
                    attemptStatus = "In Progress"
                }
                updateUI()
            }
            .addOnFailureListener {
                attemptStatus = "In Progress"
                updateUI()
            }

        binding.tvCreator.text = "Created by: ${joinedQuiz.creatorName}"
        val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        binding.tvJoinDate.text = "Joined: ${dateFormat.format(Date(joinedQuiz.joinTime))}"
    }

    private fun updateUI() {
        when (attemptStatus) {
            "Completed" -> {
                binding.tvStatus.text = "✅ Completed"
                binding.tvStatus.setBackgroundColor(ContextCompat.getColor(this, R.color.success_light))
                binding.tvSubmitInfo.visibility = android.view.View.VISIBLE
                val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                val submitDate = if (submitTime != null) dateFormat.format(Date(submitTime!!)) else "Unknown"
                binding.tvSubmitInfo.text = "Submitted: $submitDate\nScore: ${score ?: 0}/${totalScore ?: 0}"
                binding.btnReattempt.visibility = android.view.View.GONE
                binding.tvCompletionMessage.visibility = android.view.View.VISIBLE
                binding.tvCompletionMessage.text = "This quiz has already been completed and cannot be attempted again."
            }
            "In Progress" -> {
                binding.tvStatus.text = "⏳ In Progress"
                binding.tvStatus.setBackgroundColor(ContextCompat.getColor(this, R.color.warning_light))
                binding.tvSubmitInfo.visibility = android.view.View.GONE
                binding.tvCompletionMessage.visibility = android.view.View.GONE
                binding.btnReattempt.visibility = android.view.View.VISIBLE
                binding.btnReattempt.text = "Continue Quiz"
            }
            "Expired" -> {
                binding.tvStatus.text = "❌ Expired"
                binding.tvStatus.setBackgroundColor(ContextCompat.getColor(this, R.color.error_light))
                binding.btnReattempt.visibility = android.view.View.GONE
                binding.tvSubmitInfo.visibility = android.view.View.GONE
                binding.tvCompletionMessage.visibility = android.view.View.VISIBLE
                binding.tvCompletionMessage.text = "This quiz has expired."
            }
        }

        binding.btnReattempt.setOnClickListener {
            if (attemptStatus == "In Progress") {
                val intent = Intent(this, QuizInstructionsActivity::class.java)
                intent.putExtra("quizId", joinedQuiz.quizId)
                intent.putExtra("quizTitle", joinedQuiz.quizTitle)
                startActivity(intent)
                finish()
            }
        }
    }
}