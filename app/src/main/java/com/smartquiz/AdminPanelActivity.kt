package com.smartquiz

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.smartquiz.databinding.ActivityAdminPanelBinding
import java.text.SimpleDateFormat
import java.util.*

class AdminPanelActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminPanelBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private val TAG = "AdminPanel"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminPanelBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Verify admin role before doing anything else
        db.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { doc ->
                val role = doc.getString("role")?.lowercase() ?: "user"
                if (role != "admin" && role != "super_admin") {
                    Toast.makeText(this, "Access Denied – Admin Only", Toast.LENGTH_LONG).show()
                    finish()
                    return@addOnSuccessListener
                }

                bindAdminIdentity(currentUser.uid)
                loadStatistics()
                loadRecentQuizzes()
                wireQuickActions()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to verify admin", e)
                Toast.makeText(this, "Error verifying permissions: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    // ==================================================================
    // ADMIN IDENTITY
    // ==================================================================
    private fun bindAdminIdentity(uid: String) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val name = doc.getString("name") ?: "Admin"
                val email = doc.getString("email") ?: ""
                val avatar = doc.getString("avatarUrl")

                binding.tvAdminName.text = name
                binding.tvAdminEmail.text = email

                if (!avatar.isNullOrEmpty()) {
                    Glide.with(this).load(avatar).into(binding.ivAdminAvatar)
                }
            }
            .addOnFailureListener { e -> Log.e(TAG, "Failed to load admin profile", e) }
    }

    // ==================================================================
    // OVERVIEW STATISTICS
    // ==================================================================
    private fun loadStatistics() {
        binding.progressBar.visibility = View.VISIBLE

        // 1. Total Users
        db.collection("users").count().get(AggregateSource.SERVER)
            .addOnSuccessListener { snap ->
                binding.tvStatUsers.text = snap.count.toString()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "users count failed", e)
                binding.tvStatUsers.text = "—"
            }

        // 2. Total Quizzes
        db.collection("quizzes").count().get(AggregateSource.SERVER)
            .addOnSuccessListener { snap ->
                binding.tvStatQuizzes.text = snap.count.toString()
                binding.progressBar.visibility = View.GONE
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "quizzes count failed", e)
                binding.tvStatQuizzes.text = "—"
                binding.progressBar.visibility = View.GONE
            }

        // 3. Public Quizzes
        db.collection("quizzes").whereEqualTo("visibility", "public").count().get(AggregateSource.SERVER)
            .addOnSuccessListener { snap ->
                binding.tvStatActiveQuizzes.text = snap.count.toString()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "public quizzes count failed", e)
                binding.tvStatActiveQuizzes.text = "—"
            }

        // 4. Total Announcements Published (Replaced Cheat Alerts)
        db.collection("announcements").count().get(AggregateSource.SERVER)
            .addOnSuccessListener { snap ->
                binding.tvStatAnnouncements.text = snap.count.toString()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "announcements count failed", e)
                binding.tvStatAnnouncements.text = "—"
            }
    }

    // ==================================================================
    // RECENT QUIZZES (Fetches latest from all users)
    // ==================================================================
    private fun loadRecentQuizzes() {
        db.collection("quizzes")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(5)
            .get()
            .addOnSuccessListener { docs ->
                val recentQuizzes = mutableListOf<Quiz>()
                docs.forEach { doc ->
                    try {
                        doc.toObject(Quiz::class.java)?.let { q ->
                            q.quizId = doc.id
                            recentQuizzes.add(q)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "parse failed ${doc.id}", e)
                    }
                }
                renderRecentQuizzes(recentQuizzes)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "recent quizzes failed", e)
                renderRecentQuizzes(emptyList())
            }
    }

    private fun renderRecentQuizzes(quizzes: List<Quiz>) {
        val container = binding.llRecentActivity
        container.removeAllViews()

        if (quizzes.isEmpty()) {
            val tv = TextView(this).apply {
                text = "No quizzes created yet"
                textSize = 13f
                setTextColor(getColor(R.color.text_secondary))
                setPadding(0, 12, 0, 12)
            }
            container.addView(tv)
            return
        }

        val dateFormat = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())

        quizzes.forEach { quiz ->
            val title = quiz.title.ifBlank { "(untitled)" }
            val visibility = quiz.visibility.replaceFirstChar { it.uppercase() }
            val createdAt = if (quiz.createdAt > 0) dateFormat.format(Date(quiz.createdAt)) else "—"

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 12, 0, 12)
            }

            val titleView = TextView(this).apply {
                text = title
                textSize = 15f
                setTextColor(getColor(R.color.text_primary))
                setTypeface(null, android.graphics.Typeface.BOLD)
            }

            val metaView = TextView(this).apply {
                text = "$visibility • $createdAt"
                textSize = 12f
                setTextColor(getColor(R.color.text_secondary))
                setPadding(0, 4, 0, 0)
            }

            row.addView(titleView)
            row.addView(metaView)
            container.addView(row)
        }
    }

    // ==================================================================
    // QUICK ACTIONS
    // ==================================================================
    private fun wireQuickActions() {
        binding.cardManageQuizzes.setOnClickListener {
            startActivity(Intent(this, AdminQuizzesActivity::class.java))
        }
        binding.cardManageUsers.setOnClickListener {
            startActivity(Intent(this, AdminUsersActivity::class.java))
        }
        binding.cardCheatLogs.setOnClickListener {
            startActivity(Intent(this, AdminCheatLogsActivity::class.java))
        }
        binding.cardAnnouncements.setOnClickListener {
            startActivity(Intent(this, AdminAnnouncementsActivity::class.java))
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}