package com.smartquiz

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.smartquiz.databinding.ActivityLeaderboardBinding
import com.smartquiz.models.JoinedQuiz
import LeaderboardQuizItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import android.text.TextWatcher
import android.content.Intent
import android.view.View
import android.text.Editable
import android.widget.Toast
import com.google.android.gms.tasks.Tasks


// LeaderboardActivity.kt
class LeaderboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLeaderboardBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private val quizItems = mutableListOf<LeaderboardQuizItem>()
    private lateinit var adapter: LeaderboardQuizAdapter
    private var currentFilter = "All"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLeaderboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_back)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        adapter = LeaderboardQuizAdapter(quizItems) { quizId, title ->
            val intent = Intent(this, QuizLeaderboardActivity::class.java)
            intent.putExtra("quizId", quizId)
            intent.putExtra("quizTitle", title)
            startActivity(intent)
        }
        binding.rvMyQuizzes.layoutManager = LinearLayoutManager(this)
        binding.rvMyQuizzes.adapter = adapter

        // Search
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { filterQuizzes(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Filter chips
        binding.chipGroupFilter.setOnCheckedChangeListener { _, checkedId ->
            currentFilter = when (checkedId) {
                R.id.chipCompleted -> "Completed"
                R.id.chipInProgress -> "In Progress"
                else -> "All"
            }
            loadData()
        }

        binding.btnGoJoin.setOnClickListener {
            startActivity(Intent(this, JoinQuizActivity::class.java))
        }

        loadData()
    }

    private fun loadData() {
        val userId = auth.currentUser?.uid ?: return
        binding.progressBar.visibility = View.VISIBLE
        binding.layoutEmpty.visibility = View.GONE
        binding.rvMyQuizzes.visibility = View.GONE

        // Get joined quizzes
        db.collection("users").document(userId)
            .collection("joinedQuizzes")
            .get()
            .addOnSuccessListener { joinedSnap ->
                val joinedList = joinedSnap.mapNotNull { it.toObject(JoinedQuiz::class.java) }
                    .filter { it.status != "Expired" } // optionally filter
                if (joinedList.isEmpty()) {
                    showEmpty()
                    return@addOnSuccessListener
                }
                // For each joined quiz, fetch attempts to compute rank and participants
                val quizIds = joinedList.map { it.quizId }
                fetchQuizDetails(joinedList)
            }
            .addOnFailureListener {
                showError()
            }
    }

    private fun fetchQuizDetails(joinedList: List<JoinedQuiz>) {
        val tasks = joinedList.map { joined ->
            val quizRef = db.collection("quizzes").document(joined.quizId)
            // Get all attempts for this quiz
            quizRef.collection("attempts").get()
                .continueWith { task ->
                    val attempts = task.result?.documents ?: emptyList()
                    // Compute rank for current user
                    val userAttempt = attempts.find { it.id == auth.currentUser?.uid }
                    val userScore = userAttempt?.getLong("score")?.toInt() ?: 0
                    val totalScore = userAttempt?.getLong("totalScore")?.toInt() ?: 0
                    val status = userAttempt?.getString("status") ?: "In Progress"

                    // Sort attempts by score descending, then submitTime ascending for tie-break
                    val sorted = attempts.sortedWith(compareByDescending<DocumentSnapshot> { it.getLong("score") }
                        .thenBy { it.getLong("submitTime") })

                    // Compute rank with ties (competition rank)
                    var rank = 1
                    var prevScore: Long? = null
                    var position = 1
                    for (doc in sorted) {
                        val score = doc.getLong("score") ?: 0
                        if (prevScore != null && score != prevScore) {
                            rank = position
                        }
                        if (doc.id == auth.currentUser?.uid) {
                            // found user's rank
                            return@continueWith LeaderboardQuizItem(
                                quizId = joined.quizId,
                                title = joined.quizTitle,
                                quizCode = joined.quizCode,
                                userScore = userScore,
                                totalScore = totalScore,
                                userRank = rank,
                                totalParticipants = sorted.size,
                                status = status
                            )
                        }
                        prevScore = score
                        position++
                    }
                    // If user not found (shouldn't happen), return default
                    LeaderboardQuizItem(
                        quizId = joined.quizId,
                        title = joined.quizTitle,
                        quizCode = joined.quizCode,
                        userScore = userScore,
                        totalScore = totalScore,
                        userRank = 0,
                        totalParticipants = sorted.size,
                        status = status
                    )
                }
        }

        Tasks.whenAllComplete(tasks)
            .addOnCompleteListener {
                val items = tasks.mapNotNull { if (it.isSuccessful) it.result else null }
                // Apply filter
                val filtered = if (currentFilter == "All") items
                else items.filter { it.status == currentFilter }
                quizItems.clear()
                quizItems.addAll(filtered.sortedByDescending { it.userRank })
                binding.progressBar.visibility = View.GONE
                if (quizItems.isEmpty()) {
                    showEmpty()
                } else {
                    binding.rvMyQuizzes.visibility = View.VISIBLE
                    adapter.notifyDataSetChanged()
                }
            }
            .addOnFailureListener {
                showError()
            }
    }

    private fun filterQuizzes(query: String) {
        val lower = query.lowercase()
        val filtered = quizItems.filter {
            it.title.lowercase().contains(lower) || it.quizCode.lowercase().contains(lower)
        }
        adapter.updateList(filtered)
    }

    private fun showEmpty() {
        binding.progressBar.visibility = View.GONE
        binding.layoutEmpty.visibility = View.VISIBLE
        binding.rvMyQuizzes.visibility = View.GONE
    }

    private fun showError() {
        binding.progressBar.visibility = View.GONE
        Toast.makeText(this, "Failed to load leaderboard", Toast.LENGTH_SHORT).show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}