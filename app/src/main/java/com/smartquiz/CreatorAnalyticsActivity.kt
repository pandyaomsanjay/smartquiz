package com.smartquiz

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.smartquiz.databinding.ActivityCreatorAnalyticsBinding
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class CreatorAnalyticsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreatorAnalyticsBinding
    private lateinit var db: FirebaseFirestore
    private var quizId: String = ""
    private var quizTitle: String = ""

    // Data
    private val attempts = mutableListOf<AttemptData>()
    private val filteredAttempts = mutableListOf<AttemptData>()
    private val leaderboardEntries = mutableListOf<LeaderboardEntry>()
    private lateinit var leaderboardAdapter: LeaderboardAdapter

    // Date filter
    private var startDate: Long = 0L
    private var endDate: Long = System.currentTimeMillis()
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    // Most incorrect question tracking
    private val questionIncorrectCount = mutableMapOf<String, Int>()

    data class AttemptData(
        val userId: String,
        val userName: String,
        val email: String,
        val score: Double,
        val totalScore: Int,
        val duration: Long,
        val submissionReason: String,
        val submittedAt: Long,
        val answers: Map<String, Any>? = emptyMap()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreatorAnalyticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        quizId = intent.getStringExtra("quizId") ?: ""
        quizTitle = intent.getStringExtra("quizTitle") ?: "Analytics"

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = quizTitle
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        db = FirebaseFirestore.getInstance()

        setupDateFilter()
        setupLeaderboard()
        loadAttempts()
    }

    private fun setupDateFilter() {
        // Populate quick filter options
        val filterOptions = arrayOf("All Time", "Today", "Last 7 Days", "Last 30 Days", "Custom")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, filterOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerDateFilter.adapter = adapter

        binding.spinnerDateFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val now = System.currentTimeMillis()
                startDate = when (position) {
                    1 -> { // Today
                        val cal = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        cal.timeInMillis
                    }
                    2 -> now - 7 * 24 * 60 * 60 * 1000L
                    3 -> now - 30 * 24 * 60 * 60 * 1000L
                    else -> 0L // All Time or Custom
                }
                endDate = now
                applyFilter()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Custom date pickers (simplified - just set start/end via buttons)
        binding.btnPickStartDate.setOnClickListener {
            showDatePicker { timestamp ->
                startDate = timestamp
                applyFilter()
            }
        }

        binding.btnPickEndDate.setOnClickListener {
            showDatePicker { timestamp ->
                endDate = timestamp
                applyFilter()
            }
        }
    }

    private fun showDatePicker(onDateSelected: (Long) -> Unit) {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        android.app.DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDay ->
                val cal = Calendar.getInstance()
                cal.set(selectedYear, selectedMonth, selectedDay, 0, 0, 0)
                cal.set(Calendar.MILLISECOND, 0)
                onDateSelected(cal.timeInMillis)
            },
            year, month, day
        ).show()
    }

    private fun setupLeaderboard() {
        leaderboardAdapter = LeaderboardAdapter(leaderboardEntries)
        binding.rvLeaderboard.layoutManager = LinearLayoutManager(this)
        binding.rvLeaderboard.adapter = leaderboardAdapter
    }

    private fun loadAttempts() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE

        db.collection("quizzes").document(quizId)
            .collection("attempts")
            .orderBy("submitTime", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { docs ->
                attempts.clear()
                questionIncorrectCount.clear()
                for (doc in docs) {
                    val data = doc.data
                    val userId = doc.id
                    val userName = data["userName"] as? String ?: "Unknown"
                    val email = data["email"] as? String ?: ""
                    val score = when (val s = data["score"]) {
                        is Double -> s
                        is Long -> s.toDouble()
                        is Int -> s.toDouble()
                        else -> 0.0
                    }
                    val totalScore = when (val ts = data["totalScore"]) {
                        is Int -> ts
                        is Long -> ts.toInt()
                        is Double -> ts.toInt()
                        else -> 0
                    }
                    val duration = data["duration"] as? Long ?: 0
                    val submissionReason = data["submissionReason"] as? String ?: "NORMAL"
                    val submittedAt = data["submitTime"] as? Long ?: 0
                    val answers = data["answers"] as? Map<String, Any> ?: emptyMap()

                    val attempt = AttemptData(
                        userId = userId,
                        userName = userName,
                        email = email,
                        score = score,
                        totalScore = totalScore,
                        duration = duration,
                        submissionReason = submissionReason,
                        submittedAt = submittedAt,
                        answers = answers
                    )
                    attempts.add(attempt)

                    // Track incorrect answers for "Most Incorrect Question"
                    // We need to know correct answers; we'll fetch questions later.
                }
                // Need to fetch questions to compute incorrect counts
                fetchQuestionsAndComputeStats()
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Failed to load attempts: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun fetchQuestionsAndComputeStats() {
        db.collection("quizzes").document(quizId)
            .collection("questions")
            .get()
            .addOnSuccessListener { questionDocs ->
                val questionMap = mutableMapOf<String, Question>()
                for (doc in questionDocs) {
                    val q = doc.toObject(Question::class.java)
                    q.questionId = doc.id
                    questionMap[q.questionId] = q
                }
                // Compute incorrect counts per question
                for (attempt in attempts) {
                    val answers = attempt.answers ?: continue
                    for ((qId, answer) in answers) {
                        val q = questionMap[qId] ?: continue
                        val isCorrect = when (q.questionType) {
                            "radio" -> {
                                val selected = answer as? Int
                                selected != null && selected == q.correctAnswerIndex
                            }
                            "checkbox" -> {
                                val selected = answer as? List<*>
                                if (selected != null) {
                                    val selectedSet = selected.mapNotNull {
                                        when (it) {
                                            is Int -> it
                                            is Long -> it.toInt()
                                            else -> null
                                        }
                                    }.toSet()
                                    selectedSet == q.correctAnswerIndices.toSet()
                                } else false
                            }
                            "descriptive" -> {
                                val userText = answer as? String
                                userText != null && userText.trim().equals(q.correctAnswerText.trim(), ignoreCase = true)
                            }
                            else -> false
                        }
                        if (!isCorrect) {
                            questionIncorrectCount[qId] = questionIncorrectCount.getOrDefault(qId, 0) + 1
                        }
                    }
                }
                applyFilter()
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Failed to load questions: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun applyFilter() {
        filteredAttempts.clear()
        val start = startDate
        val end = endDate
        for (attempt in attempts) {
            if (start > 0 && attempt.submittedAt < start) continue
            if (attempt.submittedAt > end) continue
            filteredAttempts.add(attempt)
        }
        updateUI()
    }

    private fun updateUI() {
        binding.progressBar.visibility = View.GONE

        if (filteredAttempts.isEmpty()) {
            binding.statsContainer.visibility = View.GONE
            binding.rvLeaderboard.visibility = View.GONE
            binding.tvEmpty.visibility = View.VISIBLE
            return
        }

        binding.statsContainer.visibility = View.VISIBLE
        binding.rvLeaderboard.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE

        // Compute stats
        val totalParticipants = filteredAttempts.size
        val completed = filteredAttempts.count { it.submissionReason == "NORMAL" }
        val completionRate = if (totalParticipants > 0) (completed * 100.0 / totalParticipants) else 0.0
        val scores = filteredAttempts.map { it.score }
        val highest = scores.maxOrNull() ?: 0.0
        val lowest = scores.minOrNull() ?: 0.0
        val autoSubmit = filteredAttempts.count { it.submissionReason == "THREE_CHEAT_WARNINGS" || it.submissionReason == "CHEAT_LIMIT_REACHED" }
        val timeExpired = filteredAttempts.count { it.submissionReason == "TIMER_EXPIRED" }

        // Most incorrect question
        var mostIncorrectQuestionId = ""
        var maxIncorrect = 0
        for ((qId, count) in questionIncorrectCount) {
            if (count > maxIncorrect) {
                maxIncorrect = count
                mostIncorrectQuestionId = qId
            }
        }
        val mostIncorrectText = if (mostIncorrectQuestionId.isNotEmpty()) {
            // Fetch question text from cached questions? We don't have them in this scope.
            // We can show "Question ID" or re-fetch. For simplicity, we'll show "Question ${mostIncorrectQuestionId.take(8)}..."
            "Question (ID: ${mostIncorrectQuestionId.take(8)}...) - $maxIncorrect incorrect attempts"
        } else {
            "No incorrect answers"
        }

        // Update UI
        binding.tvTotalParticipants.text = "Total Participants: $totalParticipants"
        binding.tvCompletionRate.text = "Completion Rate: ${String.format("%.1f", completionRate)}%"
        binding.tvHighestScore.text = "Highest Score: ${highest.roundToInt()}"
        binding.tvLowestScore.text = "Lowest Score: ${lowest.roundToInt()}"
        binding.tvAutoSubmit.text = "Auto-Submitted (Cheating): $autoSubmit"
        binding.tvTimeExpired.text = "Time-Expired: $timeExpired"
        binding.tvMostIncorrect.text = "Most Incorrect: $mostIncorrectText"

        // Leaderboard - sort by score descending
        val sorted = filteredAttempts.sortedByDescending { it.score }
        leaderboardEntries.clear()
        sorted.forEachIndexed { index, attempt ->
            leaderboardEntries.add(
                LeaderboardEntry(
                    userId = attempt.userId,
                    name = attempt.userName,
                    totalScore = attempt.score.toInt()
                )
            )
        }
        leaderboardAdapter.notifyDataSetChanged()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}