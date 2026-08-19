package com.smartquiz

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.smartquiz.databinding.ActivityQuizLeaderboardBinding

// QuizLeaderboardActivity.kt
class QuizLeaderboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQuizLeaderboardBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var quizId = ""
    private var quizTitle = ""
    private val participants = mutableListOf<LeaderboardEntry>()
    private lateinit var adapter: LeaderboardAdapter // reuse existing LeaderboardAdapter (update to support highlighting)
    private var currentUserId: String? = null
    private var currentUserRank = -1

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

        adapter = LeaderboardAdapter(participants) // we'll extend to highlight current user
        binding.rvLeaderboard.layoutManager = LinearLayoutManager(this)
        binding.rvLeaderboard.adapter = adapter

        loadLeaderboard()

        binding.btnViewMyRank.setOnClickListener {
            if (currentUserRank != -1) {
                binding.rvLeaderboard.smoothScrollToPosition(currentUserRank - 1)
            }
        }
    }

    private fun loadLeaderboard() {
        binding.progressBar.visibility = View.VISIBLE
        val quizRef = db.collection("quizzes").document(quizId)

        // Fetch all attempts
        quizRef.collection("attempts")
            .get()
            .addOnSuccessListener { attemptsSnap ->
                val attempts = attemptsSnap.documents
                if (attempts.isEmpty()) {
                    binding.progressBar.visibility = View.GONE
                    binding.tvEmpty.visibility = View.VISIBLE
                    return@addOnSuccessListener
                }

                // Compute statistics
                val completed = attempts.filter { it.getString("status") == "Completed" }
                val total = attempts.size
                val completionRate = if (total > 0) (completed.size * 100.0 / total) else 0.0
                val scores = attempts.mapNotNull { it.getLong("score")?.toInt() }
                val highest = scores.maxOrNull() ?: 0
                val lowest = scores.minOrNull() ?: 0

                binding.tvTotalParticipants.text = "Total Participants: $total"
                binding.tvCompletionRate.text = "Completion Rate: ${String.format("%.1f", completionRate)}%"
                binding.tvHighestScore.text = "Highest Score: $highest"
                binding.tvLowestScore.text = "Lowest Score: $lowest"

                // Build participant list with user names (resolve from user documents)
                val userIds = attempts.map { it.id }.distinct()
                val userTasks = userIds.map { userId ->
                    db.collection("users").document(userId).get()
                }

                Tasks.whenAllSuccess<DocumentSnapshot>(userTasks)
                    .addOnSuccessListener { userDocs ->
                        val userMap = userDocs.associate { it.id to (it.getString("name") ?: "Unknown") }

                        // Map attempts to LeaderboardEntry (score, name, userId)
                        val entries = attempts.mapNotNull { doc ->
                            val userId = doc.id
                            val score = doc.getLong("score")?.toInt() ?: 0
                            val name = userMap[userId] ?: "Unknown"
                            LeaderboardEntry(userId = userId, name = name, totalScore = score)
                        }

                        // Sort with tie-breaking: descending score, then submitTime ascending (earlier better)
                        val sorted = entries.sortedWith(compareByDescending<LeaderboardEntry> { it.totalScore }
                            .thenBy { entry ->
                                // fetch submitTime for tie-break
                                val attemptDoc = attempts.find { it.id == entry.userId } // better to map
                                // We'll store submitTime in the entry or fetch from doc
                                0L // placeholder; we'll do proper tie-break below
                            })

                        // Rebuild with proper rank and tie handling
                        val ranked = mutableListOf<LeaderboardEntry>()
                        var rank = 1
                        var prevScore: Int? = null
                        var position = 1
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

                        // Find current user's rank
                        currentUserRank = participants.indexOfFirst { it.userId == currentUserId } + 1
                        if (currentUserRank > 0) {
                            binding.btnViewMyRank.visibility = View.VISIBLE
                        }

                        // Highlight current user in adapter (we'll pass a set of highlighted userIds)
                        adapter.setHighlightedUser(currentUserId)
                        adapter.notifyDataSetChanged()

                        binding.progressBar.visibility = View.GONE
                        binding.tvEmpty.visibility = View.GONE
                    }
                    .addOnFailureListener {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this, "Failed to load user names", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Failed to load leaderboard", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}