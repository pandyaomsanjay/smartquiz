package com.smartquiz

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.smartquiz.databinding.ActivityLeaderboardBinding

class LeaderboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLeaderboardBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private val quizItems = mutableListOf<LeaderboardQuizItem>()
    private lateinit var adapter: LeaderboardQuizAdapter
    private var currentFilter = "All"
    private val TAG = "LeaderboardActivity"

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

        // 1. Fetch quizzes created by the user
        val createdTask = db.collection("quizzes")
            .whereEqualTo("creatorId", userId)
            .get()

        // 2. Fetch quizzes joined by the user
        val joinedTask = db.collection("users").document(userId)
            .collection("joinedQuizzes")
            .get()

        Tasks.whenAllComplete(createdTask, joinedTask)
            .addOnSuccessListener { tasks ->
                val createdDocs = if (createdTask.isSuccessful) createdTask.result?.documents ?: emptyList() else emptyList()
                val joinedDocs = if (joinedTask.isSuccessful) joinedTask.result?.documents ?: emptyList() else emptyList()

                if (!createdTask.isSuccessful) Log.e(TAG, "Failed to load created quizzes", createdTask.exception)
                if (!joinedTask.isSuccessful) Log.e(TAG, "Failed to load joined quizzes", joinedTask.exception)

                // Use a Set to automatically remove duplicates
                val uniqueQuizIds = mutableSetOf<String>()

                createdDocs.forEach { uniqueQuizIds.add(it.id) }
                joinedDocs.forEach { doc ->
                    val qid = doc.getString("quizId") ?: doc.id
                    uniqueQuizIds.add(qid)
                }

                Log.d(TAG, "Total unique quizzes found: ${uniqueQuizIds.size}")

                if (uniqueQuizIds.isEmpty()) {
                    showEmpty()
                    return@addOnSuccessListener
                }

                fetchQuizDetails(uniqueQuizIds.toList())
            }
    }

    private fun fetchQuizDetails(quizIds: List<String>) {
        val tasks = quizIds.map { quizId ->
            val quizRef = db.collection("quizzes").document(quizId)
            val quizDocTask = quizRef.get()
            val attemptsTask = quizRef.collection("attempts").get()

            // Use whenAllComplete instead of whenAllSuccess so one failure doesn't break the whole list
            Tasks.whenAllComplete(quizDocTask, attemptsTask)
                .continueWith { _ ->
                    if (!quizDocTask.isSuccessful || !quizDocTask.result.exists()) {
                        Log.e(TAG, "Failed to load quiz doc for $quizId", quizDocTask.exception)
                        return@continueWith null
                    }

                    val quizDoc = quizDocTask.result
                    val title = quizDoc.getString("title") ?: "Unknown Quiz"
                    val quizCode = quizDoc.getString("quizCode") ?: ""
                    val showScore = quizDoc.getBoolean("showScoreAfterSubmission") ?: true

                    // If attempts fail to load (e.g. permission denied), just show 0 attempts for this quiz
                    val attempts = if (attemptsTask.isSuccessful) attemptsTask.result?.documents ?: emptyList() else emptyList()

                    val userAttempt = attempts.find { it.id == auth.currentUser?.uid }
                    val status = userAttempt?.getString("status") ?: "In Progress"
                    val userScore = if (showScore) (userAttempt?.getDouble("score")?.toInt() ?: 0) else 0
                    val totalScore = if (showScore) (userAttempt?.getDouble("totalScore")?.toInt() ?: 0) else 0

                    var userRank = 0
                    val totalParticipants = attempts.size

                    if (showScore && attempts.isNotEmpty()) {
                        val sorted = attempts.sortedWith(
                            compareByDescending<DocumentSnapshot> { it.getDouble("score") }
                                .thenBy { it.getLong("submitTime") }
                        )

                        var rank = 1
                        var prevScore: Double? = null
                        var position = 1
                        for (doc in sorted) {
                            val score = doc.getDouble("score") ?: 0.0
                            if (prevScore != null && score != prevScore) rank = position
                            if (doc.id == auth.currentUser?.uid) {
                                userRank = rank
                                break
                            }
                            prevScore = score
                            position++
                        }
                    }

                    LeaderboardQuizItem(
                        quizId = quizId,
                        title = title,
                        quizCode = quizCode,
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
                quizItems.addAll(filtered.sortedByDescending { if (it.showScore) it.userRank else 0 })

                binding.progressBar.visibility = View.GONE
                if (quizItems.isEmpty()) {
                    showEmpty()
                } else {
                    binding.rvMyQuizzes.visibility = View.VISIBLE
                    adapter.notifyDataSetChanged()
                }
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

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}