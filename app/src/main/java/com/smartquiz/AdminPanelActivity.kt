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
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.AggregateQuerySnapshot
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import com.smartquiz.databinding.ActivityAdminPanelBinding
import java.text.SimpleDateFormat
import java.util.*

/**
 * Admin Dashboard — the entry point for administrators.
 */
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
    // NOTE: We use `getCount()` (the Java method) instead of the
    // `.count` property because AggregateQuerySnapshot implements
    // Iterable, which confuses the Kotlin property resolver.
    // ==================================================================
    private fun loadStatistics() {
        binding.progressBar.visibility = View.VISIBLE

        val uid = auth.currentUser?.uid
        if (uid == null) {
            binding.progressBar.visibility = View.GONE
            return
        }

        // ---------- Total Users ----------
        db.collection("users").count().get(AggregateSource.SERVER)
            .addOnSuccessListener { snap ->
                binding.tvStatUsers.text = snap.getCount().toString()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "users count failed", e)
                binding.tvStatUsers.text = "—"
            }

        // ---------- Quizzes: public + own ----------
        val publicTask = db.collection("quizzes")
            .whereEqualTo("visibility", "public")
            .count()
            .get(AggregateSource.SERVER)

        val ownTask = db.collection("quizzes")
            .whereEqualTo("creatorId", uid)
            .count()
            .get(AggregateSource.SERVER)

        Tasks.whenAllSuccess<AggregateQuerySnapshot>(publicTask, ownTask)
            .addOnSuccessListener { results ->
                val publicCount = results.getOrNull(0)?.getCount() ?: 0L
                val ownCount = results.getOrNull(1)?.getCount() ?: 0L
                val total = publicCount + ownCount

                binding.tvStatQuizzes.text = total.toString()
                binding.tvStatActiveQuizzes.text = publicCount.toString()
                binding.progressBar.visibility = View.GONE
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "quiz stats failed", e)
                binding.tvStatQuizzes.text = "—"
                binding.tvStatActiveQuizzes.text = "—"
                binding.progressBar.visibility = View.GONE
            }

        // ---------- Cheat alerts: own quizzes only ----------
        db.collectionGroup("cheat_logs")
            .whereEqualTo("creatorId", uid)
            .count()
            .get(AggregateSource.SERVER)
            .addOnSuccessListener { snap ->
                binding.tvStatCheatAlerts.text = snap.getCount().toString()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "cheat_logs count failed", e)
                binding.tvStatCheatAlerts.text = "—"
            }
    }

    // ==================================================================
    // RECENT QUIZZES
    // ==================================================================
    private fun loadRecentQuizzes() {
        val uid = auth.currentUser?.uid ?: return

        val publicTask = db.collection("quizzes")
            .whereEqualTo("visibility", "public")
            .limit(20)
            .get()

        val ownTask = db.collection("quizzes")
            .whereEqualTo("creatorId", uid)
            .limit(20)
            .get()

        Tasks.whenAllSuccess<com.google.firebase.firestore.QuerySnapshot>(publicTask, ownTask)
            .addOnSuccessListener { results ->
                val seen = mutableSetOf<String>()
                val merged = mutableListOf<Quiz>()

                results.getOrNull(0)?.documents?.forEach { doc ->
                    if (seen.add(doc.id)) {
                        try {
                            doc.toObject(Quiz::class.java)?.let { q ->
                                q.quizId = doc.id
                                merged.add(q)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "parse failed ${doc.id}", e)
                        }
                    }
                }
                results.getOrNull(1)?.documents?.forEach { doc ->
                    if (seen.add(doc.id)) {
                        try {
                            doc.toObject(Quiz::class.java)?.let { q ->
                                q.quizId = doc.id
                                merged.add(q)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "parse failed ${doc.id}", e)
                        }
                    }
                }

                val top5 = merged
                    .sortedByDescending { it.createdAt }
                    .take(5)

                renderRecentQuizzes(top5)
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
            val createdAt = if (quiz.createdAt > 0)
                dateFormat.format(Date(quiz.createdAt)) else "—"

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