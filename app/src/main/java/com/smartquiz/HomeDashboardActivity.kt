package com.smartquiz

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.smartquiz.databinding.ActivityHomeDashboardBinding

class HomeDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeDashboardBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    // Public quiz section
    private lateinit var publicAdapter: PublicQuizAdapter
    private var serverTimeMs: Long = 0L

    // Live listener for "Quizzes Joined"
    private var joinedQuizzesListener: ListenerRegistration? = null

    private var isFirstResume = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        loadUserData()

        // ---------- Quick action cards ----------
        binding.chipCreateQuiz.setOnClickListener {
            startActivity(Intent(this, QuizCreationActivity::class.java))
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
        binding.chipJoinQuiz.setOnClickListener {
            startActivity(Intent(this, JoinQuizActivity::class.java))
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
        binding.chipLeaderboard.setOnClickListener {
            startActivity(Intent(this, LeaderboardActivity::class.java))
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
        binding.chipCreatorDashboard.setOnClickListener {
            startActivity(Intent(this, CreatorDashboardActivity::class.java))
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
        binding.chipProfile.setOnClickListener {
            startActivity(Intent(this, UserProfileActivity::class.java))
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
        binding.chipSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        // ---------- Logout with confirmation ----------
        binding.chipLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout") { _, _ ->
                    auth.signOut()

                    joinedQuizzesListener?.remove()
                    joinedQuizzesListener = null

                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // ---------- Public quiz section ----------
        setupPublicQuizSection()
        // Actual load happens in onResume()
    }

    /**
     * Reload public quizzes every time Home comes back into the foreground.
     * This keeps participant counts and the user's join status fresh after
     * the user joins/attempts a quiz and returns.
     */
    override fun onResume() {
        super.onResume()
        if (isFirstResume) {
            isFirstResume = false
        }
        loadPublicQuizzes()
    }

    override fun onDestroy() {
        super.onDestroy()
        joinedQuizzesListener?.remove()
        joinedQuizzesListener = null
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        return true
    }

    // ==================================================================
    // PUBLIC QUIZ SECTION
    // ==================================================================

    private fun setupPublicQuizSection() {
        publicAdapter = PublicQuizAdapter(emptyList()) { item ->
            val quiz = item.quiz
            when {
                // User already completed → Instructions screen shows "Completed"
                item.userJoinStatus == "Completed" -> {
                    val intent = Intent(this, QuizInstructionsActivity::class.java)
                    intent.putExtra("quizId", quiz.quizId)
                    intent.putExtra("quizTitle", quiz.title)
                    intent.putExtra("creatorId", quiz.creatorId)
                    startActivity(intent)
                    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                }
                // User is in-progress → resume attempt
                item.userJoinStatus == "In Progress" -> {
                    val intent = Intent(this, QuizAttemptActivity::class.java)
                    intent.putExtra("quizId", quiz.quizId)
                    intent.putExtra("quizTitle", quiz.title)
                    intent.putExtra("creatorId", quiz.creatorId)
                    startActivity(intent)
                    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                }
                // Not joined yet → go to instructions
                item.status.isJoinable -> {
                    val intent = Intent(this, QuizInstructionsActivity::class.java)
                    intent.putExtra("quizId", quiz.quizId)
                    intent.putExtra("quizTitle", quiz.title)
                    intent.putExtra("creatorId", quiz.creatorId)
                    startActivity(intent)
                    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                }
                else -> {
                    Toast.makeText(
                        this,
                        "This quiz is ${item.status.label.lowercase()}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        binding.rvPublicQuizzes.layoutManager = LinearLayoutManager(this)
        binding.rvPublicQuizzes.adapter = publicAdapter
    }

    /**
     * Loads public quizzes.
     *
     * NOTE: We intentionally do NOT use `.orderBy("deadline")` here because
     * combining 3 equality filters + 1 orderBy requires a Firestore composite
     * index. To avoid the FAILED_PRECONDITION error, we sort on the client.
     */
    private fun loadPublicQuizzes() {
        binding.progressPublic.visibility = View.VISIBLE
        binding.tvEmptyPublicQuizzes.visibility = View.GONE

        serverTimeMs = QuizTimeUtils.getServerTimeMs()
        val currentUserId = auth.currentUser?.uid

        db.collection("quizzes")
            .whereEqualTo("visibility", "public")
            .whereEqualTo("status", "PUBLISHED")
            .whereEqualTo("archived", false)
            .limit(50)
            .get()
            .addOnSuccessListener { docs ->
                if (isFinishing) return@addOnSuccessListener

                // Sort by deadline (descending) on the client — no index needed.
                val quizzes = docs.mapNotNull { doc ->
                    doc.toObject(Quiz::class.java)?.apply { quizId = doc.id }
                }.sortedByDescending { it.deadline }
                    .take(30)

                if (quizzes.isEmpty()) {
                    publicAdapter.updateList(emptyList())
                    binding.progressPublic.visibility = View.GONE
                    binding.tvEmptyPublicQuizzes.visibility = View.VISIBLE
                    return@addOnSuccessListener
                }

                // 1) Fetch creator names in parallel
                val creatorIds = quizzes.map { it.creatorId }.distinct()
                val creatorTasks = creatorIds.map { db.collection("users").document(it).get() }

                // 2) Fetch current user's joined quizzes (for button state)
                val joinedTask = if (currentUserId != null) {
                    db.collection("users").document(currentUserId)
                        .collection("joinedQuizzes")
                        .get()
                } else null

                val allTasks = mutableListOf<com.google.android.gms.tasks.Task<*>>()
                allTasks.addAll(creatorTasks)
                joinedTask?.let { allTasks.add(it) }

                Tasks.whenAllComplete(allTasks)
                    .addOnSuccessListener {
                        if (isFinishing) return@addOnSuccessListener

                        // ---- Creator name map ----
                        val nameMap = mutableMapOf<String, String>()
                        creatorTasks.forEach { t ->
                            if (t.isSuccessful) {
                                val d = t.result
                                nameMap[d.id] = d.getString("name") ?: "Unknown"
                            }
                        }

                        // ---- User's joined map (quizId -> status) ----
                        val joinedMap = mutableMapOf<String, String>()
                        if (joinedTask != null && joinedTask.isSuccessful) {
                            joinedTask.result.forEach { doc ->
                                val qid = doc.getString("quizId") ?: doc.id
                                val st = doc.getString("status") ?: "In Progress"
                                joinedMap[qid] = st
                            }
                        }

                        // ---- Build adapter items ----
                        val items = quizzes.map { q ->
                            PublicQuizAdapter.PublicQuizItem(
                                quiz = q,
                                creatorName = nameMap[q.creatorId] ?: "Unknown",
                                status = q.computeStatus(serverTimeMs),
                                participantCount = q.participantCount,
                                userJoinStatus = joinedMap[q.quizId]
                            )
                        }
                        publicAdapter.updateList(items)
                        binding.progressPublic.visibility = View.GONE
                        binding.tvEmptyPublicQuizzes.visibility =
                            if (items.isEmpty()) View.VISIBLE else View.GONE
                    }
                    .addOnFailureListener {
                        if (isFinishing) return@addOnFailureListener
                        // Fallback: show quizzes without creator name/join status
                        val items = quizzes.map { q ->
                            PublicQuizAdapter.PublicQuizItem(
                                quiz = q,
                                creatorName = "Unknown",
                                status = q.computeStatus(serverTimeMs),
                                participantCount = q.participantCount,
                                userJoinStatus = null
                            )
                        }
                        publicAdapter.updateList(items)
                        binding.progressPublic.visibility = View.GONE
                    }
            }
            .addOnFailureListener { e ->
                if (isFinishing) return@addOnFailureListener
                Log.e("HomeDashboard", "loadPublicQuizzes failed", e)

                binding.progressPublic.visibility = View.GONE
                binding.tvEmptyPublicQuizzes.visibility = View.VISIBLE

                // Friendly message for missing-index errors
                val msg = if (e.message?.contains("FAILED_PRECONDITION") == true) {
                    "Public quizzes are temporarily unavailable. Please try again."
                } else {
                    "Failed to load public quizzes."
                }
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
    }

    // ==================================================================
    // USER DATA
    // ==================================================================

    private fun loadUserData() {
        val userId = auth.currentUser?.uid ?: run {
            binding.tvGreeting.text = "Hello, Guest!"
            binding.tvStreak.text = "🔥 0 day streak"
            binding.tvRank.text = "#-"
            binding.tvTotalQuizzes.text = "—"
            binding.adminCardRow.visibility = View.GONE
            return
        }

        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                if (isFinishing) return@addOnSuccessListener
                val name = doc.getString("name") ?: "User"
                binding.tvGreeting.text = "Hello, $name!"
                val avatarUrl = doc.getString("avatarUrl")
                if (!avatarUrl.isNullOrEmpty()) {
                    Glide.with(this).load(avatarUrl).into(binding.ivProfile)
                }
                val streak = doc.getLong("streak")?.toInt() ?: 0
                binding.tvStreak.text = "🔥 $streak day streak"
                binding.tvRank.text = "#1"

                // Only "admin" / "super_admin" see the Admin card.
                val role = doc.getString("role")?.lowercase() ?: "user"
                if (role == "admin" || role == "super_admin") {
                    binding.adminCardRow.visibility = View.VISIBLE
                    binding.chipAdminPanel.setOnClickListener {
                        startActivity(Intent(this, AdminPanelActivity::class.java))
                        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                    }
                } else {
                    binding.adminCardRow.visibility = View.GONE
                }

                observeJoinedQuizCount(userId)
            }
            .addOnFailureListener {
                if (!isFinishing) {
                    binding.tvGreeting.text = "Hello, User!"
                    observeJoinedQuizCount(userId)
                }
            }
    }

    // ==================================================================
    // "Quizzes Joined" — real-time count
    // ==================================================================
    private fun observeJoinedQuizCount(userId: String) {
        binding.tvTotalQuizzes.text = "—"
        joinedQuizzesListener?.remove()

        joinedQuizzesListener = db.collection("users")
            .document(userId)
            .collection("joinedQuizzes")
            .addSnapshotListener { snapshot, error ->
                if (isFinishing || isDestroyed) return@addSnapshotListener
                if (error != null) {
                    Log.e("HomeDashboard", "Joined count failed", error)
                    binding.tvTotalQuizzes.text = "—"
                    return@addSnapshotListener
                }
                binding.tvTotalQuizzes.text = (snapshot?.size() ?: 0).toString()
            }
    }
}