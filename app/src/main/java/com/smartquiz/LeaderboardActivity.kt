package com.smartquiz

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.smartquiz.databinding.ActivityLeaderboardBinding
import com.smartquiz.models.JoinedQuiz

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

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { filterQuizzes(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

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

        db.collection("users").document(userId)
            .collection("joinedQuizzes")
            .get()
            .addOnSuccessListener { joinedSnap ->
                val joinedList = joinedSnap.mapNotNull { it.toObject(JoinedQuiz::class.java) }
                    .filter { it.status != "Expired" }
                if (joinedList.isEmpty()) {
                    showEmpty()
                    return@addOnSuccessListener
                }
                fetchQuizDetails(joinedList)
            }
            .addOnFailureListener {
                showError()
            }
    }

    private fun fetchQuizDetails(joinedList: List<JoinedQuiz>) {
        val tasks = joinedList.map { joined ->
            val quizRef = db.collection("quizzes").document(joined.quizId)

            val quizDocTask = quizRef.get()
            val attemptsTask = quizRef.collection("attempts").get()

            Tasks.whenAllSuccess<Any>(quizDocTask, attemptsTask)
                .continueWith { _ ->
                    val quizDoc = quizDocTask.result
                    val attempts = attemptsTask.result?.documents ?: emptyList()
                    val showScore = quizDoc?.getBoolean("showScoreAfterSubmission") ?: true

                    val userAttempt = attempts.find { it.id == auth.currentUser?.uid }
                    val status = userAttempt?.getString("status") ?: "In Progress"
                    val userScore = if (showScore) (userAttempt?.getDouble("score")?.toInt() ?: 0) else 0
                    val totalScore = if (showScore) (userAttempt?.getDouble("totalScore")?.toInt() ?: 0) else 0

                    var userRank = 0
                    val totalParticipants = attempts.size

                    if (showScore) {
                        val sorted = attempts.sortedWith(compareByDescending<DocumentSnapshot> { it.getDouble("score") }
                            .thenBy { it.getLong("submitTime") })
                        var rank = 1
                        var prevScore: Double? = null
                        var position = 1
                        for (doc in sorted) {
                            val score = doc.getDouble("score") ?: 0.0
                            if (prevScore != null && score != prevScore) {
                                rank = position
                            }
                            if (doc.id == auth.currentUser?.uid) {
                                userRank = rank
                                break
                            }
                            prevScore = score
                            position++
                        }
                    }

                    LeaderboardQuizItem(
                        quizId = joined.quizId,
                        title = joined.quizTitle,
                        quizCode = joined.quizCode,
                        userScore = userScore,
                        totalScore = totalScore,
                        userRank = userRank,
                        totalParticipants = totalParticipants,
                        status = status,
                        showScore = showScore
                    )
                }
        }

        Tasks.whenAllComplete(tasks)
            .addOnCompleteListener {
                val items = tasks.mapNotNull { if (it.isSuccessful) it.result else null }
                val filtered = if (currentFilter == "All") items else items.filter { it.status == currentFilter }
                quizItems.clear()
                // Sort by rank if visible, else by title
                quizItems.addAll(filtered.sortedByDescending { if (it.showScore) it.userRank else 0 })
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