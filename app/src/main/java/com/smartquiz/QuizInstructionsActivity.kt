package com.smartquiz

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.smartquiz.databinding.ActivityQuizInstructionsBinding

class QuizInstructionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQuizInstructionsBinding
    private var quizId = ""
    private var quizTitle = ""
    private var creatorId = ""
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuizInstructionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarInstructions)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_back)

        quizId = intent.getStringExtra("quizId") ?: ""
        quizTitle = intent.getStringExtra("quizTitle") ?: "Quiz"
        creatorId = intent.getStringExtra("creatorId") ?: ""

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        binding.progressBar.visibility = View.VISIBLE
        binding.btnStartQuiz.isEnabled = false
        binding.btnStartQuiz.text = "Loading..."

        loadQuizDetails()
    }

    private fun loadQuizDetails() {
        if (quizId.isEmpty()) {
            Toast.makeText(this, "Invalid quiz ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val quizRef = db.collection("quizzes").document(quizId)
        quizRef.get()
            .addOnSuccessListener { quizDoc ->
                if (!quizDoc.exists()) {
                    Toast.makeText(this, "Quiz not found", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }

                val quiz = quizDoc.toObject(Quiz::class.java) ?: run {
                    Toast.makeText(this, "Failed to parse quiz data", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }

                db.collection("users").document(quiz.creatorId).get()
                    .addOnSuccessListener { userDoc ->
                        val creatorName = userDoc.getString("name") ?: "Unknown Creator"
                        displayQuizInfo(quiz, creatorName)
                        checkUserAttempt(quiz)
                    }
                    .addOnFailureListener {
                        displayQuizInfo(quiz, "Unknown Creator")
                        checkUserAttempt(quiz)
                    }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Failed to load quiz: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun displayQuizInfo(quiz: Quiz, creatorName: String) {
        binding.tvQuizTitle.text = quiz.title
        binding.tvCreator.text = "Created by: $creatorName"
        binding.tvDescription.text = quiz.description
        binding.tvQuestionCount.text = "Questions: ${quiz.totalQuestions}"
        binding.tvTimerMode.text = "Timer: ${getTimerModeDisplay(quiz)}"
        binding.tvDeadline.text =
            if (quiz.deadline > 0) "Deadline: ${formatDate(quiz.deadline)}" else "Deadline: No deadline"

        // Instructions
        val instructions = """
            • Read each question carefully.
            • You cannot go back after submitting.
            • Timer will run; quiz auto-submits when time ends.
            • Do not switch apps or take screenshots.
            • Each question may have different point values.
            • Click the button below to begin or continue.
        """.trimIndent()
        binding.tvInstructions.text = instructions

        binding.progressBar.visibility = View.GONE
    }

    private fun getTimerModeDisplay(quiz: Quiz): String {
        return when (quiz.timerType) {
            "WHOLE_QUIZ" -> "Whole Quiz (${formatDuration(quiz.totalTimeSeconds)})"
            "PER_QUESTION" -> "Per Question (${formatDuration(quiz.timePerQuestionSeconds)} each)"
            else -> "No Timer"
        }
    }

    private fun formatDate(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }

    private fun checkUserAttempt(quiz: Quiz) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            setStatus("Not Started", R.color.primary)
            binding.btnStartQuiz.isEnabled = false
            binding.btnStartQuiz.text = "Login Required"
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show()
            return
        }

        // ---------- NEW: lifecycle status ----------
        val serverTime = QuizTimeUtils.getServerTimeMs()
        val lifecycle = quiz.computeStatus(serverTime)

        binding.tvStatus.text = lifecycle.label
        binding.tvStatus.setBackgroundColor(
            ContextCompat.getColor(this, lifecycle.colorRes)
        )

        when (lifecycle) {
            QuizLifecycleStatus.UPCOMING -> {
                binding.btnStartQuiz.isEnabled = false
                binding.btnStartQuiz.text = "Not Started Yet"
                Toast.makeText(
                    this,
                    "This quiz starts at ${QuizTimeUtils.formatDateTime(quiz.startTime)}",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
            QuizLifecycleStatus.EXPIRED, QuizLifecycleStatus.DELETED -> {
                binding.btnStartQuiz.isEnabled = false
                binding.btnStartQuiz.text = "Expired"
                Toast.makeText(this, "This quiz has expired", Toast.LENGTH_SHORT).show()
                return
            }
            QuizLifecycleStatus.COMPLETED -> {
                binding.btnStartQuiz.isEnabled = false
                binding.btnStartQuiz.text = "Completed"
                Toast.makeText(this, "This quiz has been completed", Toast.LENGTH_SHORT).show()
                return
            }
            QuizLifecycleStatus.LIVE -> { /* continue below */ }
        }

        val now = System.currentTimeMillis()
        val isExpired = quiz.deadline > 0 && now > quiz.deadline

        db.collection("quizzes").document(quizId)
            .collection("attempts").document(userId)
            .get()
            .addOnSuccessListener { attemptDoc ->
                if (attemptDoc.exists()) {
                    val status = attemptDoc.getString("status") ?: "In Progress"
                    when (status) {
                        "Completed" -> {
                            setStatus("Completed", R.color.accent)
                            binding.btnStartQuiz.isEnabled = false
                            binding.btnStartQuiz.text = "Completed"
                            Toast.makeText(this, "You have already completed this quiz", Toast.LENGTH_SHORT).show()
                        }
                        "In Progress" -> {
                            if (isExpired) {
                                setStatus("Expired", R.color.error)
                                binding.btnStartQuiz.isEnabled = false
                                binding.btnStartQuiz.text = "Expired"
                                Toast.makeText(this, "This quiz has expired", Toast.LENGTH_SHORT).show()
                            } else {
                                setStatus("Active", R.color.success)
                                binding.btnStartQuiz.isEnabled = true
                                binding.btnStartQuiz.text = "Continue Attempt"
                                binding.btnStartQuiz.setOnClickListener {
                                    startQuizActivity()
                                }
                            }
                        }
                        else -> handleNotStarted(isExpired)
                    }
                } else {
                    handleNotStarted(isExpired)
                }
            }
            .addOnFailureListener {
                handleNotStarted(isExpired)
            }
    }

    private fun handleNotStarted(isExpired: Boolean) {
        if (isExpired) {
            setStatus("Expired", R.color.error)
            binding.btnStartQuiz.isEnabled = false
            binding.btnStartQuiz.text = "Expired"
            Toast.makeText(this, "This quiz has expired", Toast.LENGTH_SHORT).show()
        } else {
            setStatus("Not Started", R.color.primary)
            binding.btnStartQuiz.isEnabled = true
            binding.btnStartQuiz.text = "Start Quiz"
            binding.btnStartQuiz.setOnClickListener {
                startQuizActivity()
            }
        }
    }

    private fun setStatus(text: String, colorResId: Int) {
        binding.tvStatus.text = text
        binding.tvStatus.setBackgroundColor(ContextCompat.getColor(this, colorResId))
    }

    private fun startQuizActivity() {
        val intent = Intent(this, QuizAttemptActivity::class.java)
        intent.putExtra("quizId", quizId)
        intent.putExtra("quizTitle", quizTitle)
        intent.putExtra("creatorId", creatorId)
        startActivity(intent)
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}