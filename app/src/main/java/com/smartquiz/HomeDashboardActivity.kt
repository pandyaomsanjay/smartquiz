package com.smartquiz

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.smartquiz.databinding.ActivityHomeDashboardBinding

class HomeDashboardActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHomeDashboardBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var quizList: MutableList<Quiz>
    private lateinit var adapter: QuizAdapter
    private var currentFilter = "public"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        quizList = mutableListOf()

        loadUserData()

        adapter = QuizAdapter(quizList) { quiz ->
            val intent = Intent(this, QuizAttemptActivity::class.java)
            intent.putExtra("quizId", quiz.quizId)
            intent.putExtra("quizTitle", quiz.title)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
        binding.rvQuizzes.layoutManager = LinearLayoutManager(this)
        binding.rvQuizzes.adapter = adapter

        binding.progressBar.visibility = View.VISIBLE
        loadQuizzes()

        // Chip listeners for filter
        binding.chipPublic.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) currentFilter = "public"
            loadQuizzes()
        }
        binding.chipPrivate.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) currentFilter = "private"
            loadQuizzes()
        }
        binding.chipMyQuizzes.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) currentFilter = "my"
            loadQuizzes()
        }
        binding.chipJoined.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) currentFilter = "joined"
            loadQuizzes()
        }

        // Search filter
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterQuizzes(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Quick action cards
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
        binding.chipLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        return true
    }

    private fun loadUserData() {
        val userId = auth.currentUser?.uid ?: return
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

                // Total quizzes joined
                db.collection("results").whereEqualTo("userId", userId).get()
                    .addOnSuccessListener { results ->
                        if (!isFinishing) binding.tvTotalQuizzes.text = results.size().toString()
                    }

                // Rank placeholder - you can implement real ranking later
                binding.tvRank.text = "#1"

                // 👇 ADMIN PANEL VISIBILITY
                val role = doc.getString("role") ?: "user"
                if (role.equals("admin", ignoreCase = true)) {
                    binding.adminCardRow.visibility = View.VISIBLE
                    binding.chipAdminPanel.setOnClickListener {
                        startActivity(Intent(this, AdminPanelActivity::class.java))
                        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                    }
                } else {
                    binding.adminCardRow.visibility = View.GONE
                }
            }
            .addOnFailureListener {
                if (!isFinishing) binding.tvGreeting.text = "Hello, User!"
            }
    }

    private fun loadQuizzes() {
        when (currentFilter) {
            "public" -> loadPublicQuizzes()
            "private" -> loadPrivateQuizzes()
            "my" -> loadMyQuizzes()
            "joined" -> loadJoinedQuizzes()
        }
    }

    private fun loadPublicQuizzes() {
        db.collection("quizzes")
            .whereEqualTo("visibility", "public")
            .get()
            .addOnSuccessListener { documents ->
                if (isFinishing) return@addOnSuccessListener
                quizList.clear()
                for (doc in documents) {
                    val quiz = doc.toObject(Quiz::class.java)
                    if (quiz != null) {
                        quiz.quizId = doc.id
                        quizList.add(quiz)
                    }
                }
                updateAdapter()
            }
            .addOnFailureListener { showError() }
    }

    private fun loadPrivateQuizzes() {
        // Private quizzes are not listed publicly
        quizList.clear()
        updateAdapter()
        Toast.makeText(this, "Private quizzes can only be joined via code", Toast.LENGTH_SHORT).show()
    }

    private fun loadMyQuizzes() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("quizzes")
            .whereEqualTo("creatorId", userId)
            .get()
            .addOnSuccessListener { documents ->
                quizList.clear()
                for (doc in documents) {
                    val quiz = doc.toObject(Quiz::class.java)
                    if (quiz != null) {
                        quiz.quizId = doc.id
                        quizList.add(quiz)
                    }
                }
                updateAdapter()
            }
            .addOnFailureListener { showError() }
    }

    private fun loadJoinedQuizzes() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId)
            .collection("joinedQuizzes")
            .get()
            .addOnSuccessListener { joinedDocs ->
                val quizIds = joinedDocs.mapNotNull { it.getString("quizId") }
                if (quizIds.isEmpty()) {
                    quizList.clear()
                    updateAdapter()
                    return@addOnSuccessListener
                }
                db.collection("quizzes")
                    .whereIn("quizId", quizIds)
                    .get()
                    .addOnSuccessListener { quizDocs ->
                        quizList.clear()
                        for (doc in quizDocs) {
                            val quiz = doc.toObject(Quiz::class.java)
                            if (quiz != null) {
                                quiz.quizId = doc.id
                                quizList.add(quiz)
                            }
                        }
                        updateAdapter()
                    }
                    .addOnFailureListener { showError() }
            }
            .addOnFailureListener { showError() }
    }

    private fun filterQuizzes(query: String) {
        if (query.isEmpty()) {
            adapter.updateList(quizList)
            return
        }
        val filtered = quizList.filter {
            it.title.contains(query, ignoreCase = true) ||
                    it.category.contains(query, ignoreCase = true) ||
                    it.description.contains(query, ignoreCase = true)
        }
        adapter.updateList(filtered)
    }

    private fun updateAdapter() {
        adapter.notifyDataSetChanged()
        binding.progressBar.visibility = View.GONE
        binding.tvEmptyQuizzes.visibility = if (quizList.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showError() {
        if (!isFinishing) {
            binding.progressBar.visibility = View.GONE
            Toast.makeText(this, "Failed to load quizzes", Toast.LENGTH_SHORT).show()
        }
    }
}
