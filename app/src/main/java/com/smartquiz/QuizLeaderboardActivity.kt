package com.smartquiz

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.smartquiz.databinding.ActivityQuizLeaderboardBinding

class QuizLeaderboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQuizLeaderboardBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var quizId = ""
    private var quizTitle = ""
    private var showScores = true
    private val participants = mutableListOf<LeaderboardEntry>()
    private lateinit var adapter: LeaderboardAdapter
    private var currentUserId: String? = null
    private var currentUserRank = -1
    private val TAG = "QuizLeaderboard"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuizLeaderboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        quizId = intent.getStringExtra("quizId") ?: ""
        quizTitle = intent.getStringExtra("quizTitle") ?: "Quiz"
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = quizTitle
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_back)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        currentUserId = auth.currentUser?.uid

        // Initialize adapter with empty list
        adapter = LeaderboardAdapter(participants, showScores = true)
        binding.rvLeaderboard.layoutManager = LinearLayoutManager(this)
        binding.rvLeaderboard.adapter = adapter

        // Fetch quiz visibility setting
        db.collection("quizzes").document(quizId).get()
            .addOnSuccessListener { doc ->
                showScores = doc.getBoolean("showScoreAfterSubmission") ?: true
                binding.tvQuizTitle.text = quizTitle
                loadLeaderboard()
            }
            .addOnFailureListener {
                showScores = true
                binding.tvQuizTitle.text = quizTitle
                loadLeaderboard()
            }

        binding.btnViewMyRank.setOnClickListener {
            if (currentUserRank != -1) {
                binding.rvLeaderboard.smoothScrollToPosition(currentUserRank - 1)
            }
        }
    }

    private fun loadLeaderboard() {
        binding.progressBar.visibility = View.VISIBLE
        // Keep RecyclerView visible but with empty adapter; we'll update later
        binding.rvLeaderboard.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE

        val quizRef = db.collection("quizzes").document(quizId)

        quizRef.collection("attempts")
            .get()
            .addOnSuccessListener { attemptsSnap ->
                val attempts = attemptsSnap.documents
                if (attempts.isEmpty()) {
                    binding.progressBar.visibility = View.GONE
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.rvLeaderboard.visibility = View.GONE
                    participants.clear()
                    adapter.updateList(participants)
                    return@addOnSuccessListener
                }

                Log.d(TAG, "Attempts count: ${attempts.size}")

                if (!showScores) {
                    populateSimpleLeaderboard(attempts)
                    return@addOnSuccessListener
                }

                // Scores visible
                val completed = attempts.filter { it.getString("status") == "Completed" }
                val total = attempts.size
                val completionRate = if (total > 0) (completed.size * 100.0 / total) else 0.0
                val scores = attempts.mapNotNull { it.getDouble("score")?.toInt() }
                val highest = scores.maxOrNull() ?: 0
                val lowest = scores.minOrNull() ?: 0

                binding.tvTotalParticipants.text = "Total Participants: $total"
                binding.tvCompletionRate.text = "Completion Rate: ${String.format("%.1f", completionRate)}%"
                binding.tvHighestScore.text = "Highest Score: $highest"
                binding.tvLowestScore.text = "Lowest Score: $lowest"
                binding.statsCard.visibility = View.VISIBLE

                val userIds = attempts.map { it.id }.distinct()
                val userTasks = userIds.map { userId ->
                    db.collection("users").document(userId).get()
                }

                Tasks.whenAllSuccess<DocumentSnapshot>(userTasks)
                    .addOnSuccessListener { userDocs ->
                        val userMap = userDocs.associate { it.id to (it.getString("name") ?: "Unknown") }
                        buildRankedLeaderboard(attempts, userMap)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to load user names: ${e.message}")
                        val fallbackMap = userIds.associateWith { it.take(8) }
                        buildRankedLeaderboard(attempts, fallbackMap)
                        Toast.makeText(this, "Could not load names, showing user IDs", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.tvEmpty.visibility = View.VISIBLE
                binding.rvLeaderboard.visibility = View.GONE
                Toast.makeText(this, "Failed to load leaderboard: ${e.message}", Toast.LENGTH_SHORT).show()
                binding.tvEmpty.text = "Unable to load leaderboard data."
            }
    }

    private fun buildRankedLeaderboard(attempts: List<DocumentSnapshot>, userMap: Map<String, String>) {
        val entries = attempts.mapNotNull { doc ->
            val userId = doc.id
            val score = doc.getDouble("score")?.toInt() ?: 0
            val name = userMap[userId] ?: "Unknown"
            LeaderboardEntry(userId = userId, name = name, totalScore = score)
        }

        Log.d(TAG, "Entries size: ${entries.size}")

        if (entries.isEmpty()) {
            binding.progressBar.visibility = View.GONE
            binding.tvEmpty.visibility = View.VISIBLE
            binding.rvLeaderboard.visibility = View.GONE
            participants.clear()
            adapter.updateList(participants)
            return
        }

        val submitTimeMap = attempts.associate { it.id to (it.getLong("submitTime") ?: 0L) }
        val sorted = entries.sortedWith(compareByDescending<LeaderboardEntry> { it.totalScore }
            .thenBy { submitTimeMap[it.userId] ?: 0L })

        var rank = 1
        var prevScore: Int? = null
        var position = 1
        val ranked = mutableListOf<LeaderboardEntry>()
        for (entry in sorted) {
            if (prevScore != null && entry.totalScore != prevScore) {
                rank = position
            }
            ranked.add(entry.copy(rank = rank))
            prevScore = entry.totalScore
            position++
        }

        participants.clear()
        participants.addAll(ranked)

        currentUserRank = participants.indexOfFirst { it.userId == currentUserId } + 1
        binding.btnViewMyRank.visibility = if (currentUserRank > 0) View.VISIBLE else View.GONE

        adapter.updateList(participants)
        adapter.setHighlightedUser(currentUserId)
        binding.rvLeaderboard.visibility = View.VISIBLE
        binding.progressBar.visibility = View.GONE
        binding.tvEmpty.visibility = View.GONE
    }

    private fun populateSimpleLeaderboard(attempts: List<DocumentSnapshot>) {
        val userIds = attempts.map { it.id }.distinct()
        val userTasks = userIds.map { userId ->
            db.collection("users").document(userId).get()
        }

        Tasks.whenAllSuccess<DocumentSnapshot>(userTasks)
            .addOnSuccessListener { userDocs ->
                val userMap = userDocs.associate { it.id to (it.getString("name") ?: "Unknown") }
                participants.clear()
                for (doc in attempts) {
                    val userId = doc.id
                    val name = userMap[userId] ?: "Unknown"
                    participants.add(LeaderboardEntry(userId = userId, name = name, totalScore = 0, rank = 0))
                }
                binding.statsCard.visibility = View.GONE
                adapter.updateList(participants)
                adapter.setHighlightedUser(currentUserId)
                binding.rvLeaderboard.visibility = View.VISIBLE
                binding.progressBar.visibility = View.GONE
                binding.tvEmpty.visibility = View.GONE
                binding.btnViewMyRank.visibility = View.GONE
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to load names for simple leaderboard: ${e.message}")
                participants.clear()
                for (doc in attempts) {
                    val userId = doc.id
                    participants.add(LeaderboardEntry(userId = userId, name = userId.take(8), totalScore = 0, rank = 0))
                }
                binding.statsCard.visibility = View.GONE
                adapter.updateList(participants)
                adapter.setHighlightedUser(currentUserId)
                binding.rvLeaderboard.visibility = View.VISIBLE
                binding.progressBar.visibility = View.GONE
                binding.tvEmpty.visibility = View.GONE
                binding.btnViewMyRank.visibility = View.GONE
                Toast.makeText(this, "Could not load names, showing user IDs", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}