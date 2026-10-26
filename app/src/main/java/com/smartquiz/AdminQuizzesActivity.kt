package com.smartquiz

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.smartquiz.databinding.ActivityAdminQuizzesBinding
import com.smartquiz.databinding.ItemAdminQuizBinding
import java.text.SimpleDateFormat
import java.util.*

class AdminQuizzesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminQuizzesBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private val allQuizzes = mutableListOf<Quiz>()
    private val filtered = mutableListOf<Quiz>()
    private lateinit var adapter: QuizzesAdapter

    private var currentFilter = "all"
    private val TAG = "AdminQuizzes"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminQuizzesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        adapter = QuizzesAdapter(filtered) { quiz -> showQuizActions(quiz) }
        binding.rvQuizzes.layoutManager = LinearLayoutManager(this)
        binding.rvQuizzes.adapter = adapter

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { applyFilterAndSearch() }
        })

        binding.chipGroupFilter.setOnCheckedChangeListener { _, checkedId ->
            currentFilter = when (checkedId) {
                R.id.chipPublic -> "public"
                R.id.chipPrivate -> "private"
                R.id.chipPublished -> "published"
                else -> "all"
            }
            applyFilterAndSearch()
        }

        loadQuizzes()
    }

    private fun loadQuizzes() {
        val currentUid = auth.currentUser?.uid
        if (currentUid == null) {
            Toast.makeText(this, "Please log in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE
        allQuizzes.clear()

        // Fetch ALL quizzes from the database (Matches the Dashboard count of 109)
        db.collection("quizzes").get()
            .addOnSuccessListener { docs ->
                binding.progressBar.visibility = View.GONE

                docs.forEach { doc ->
                    try {
                        val q = doc.toObject(Quiz::class.java)?.apply { quizId = doc.id }
                        if (q != null) allQuizzes.add(q)
                    } catch (e: Exception) {
                        Log.e(TAG, "parse fail on ${doc.id}", e)
                    }
                }

                // Sort newest first
                allQuizzes.sortByDescending { it.createdAt }
                applyFilterAndSearch()
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.tvEmpty.visibility = View.VISIBLE
                Log.e(TAG, "loadQuizzes failed", e)
                Toast.makeText(this, "Failed to load quizzes: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun applyFilterAndSearch() {
        val query = binding.etSearch.text.toString().trim().lowercase()
        filtered.clear()

        for (q in allQuizzes) {
            // A quiz is truly published if its status is PUBLISHED OR if it has a join code
            val isActuallyPublished = q.status.equals("PUBLISHED", ignoreCase = true) || q.quizCode.isNotEmpty()

            val matchesFilter = when (currentFilter) {
                "public" -> q.visibility == "public"
                "private" -> q.visibility == "private"
                "published" -> isActuallyPublished
                else -> true // "All" shows everything (Drafts, Published, Archived)
            }
            if (!matchesFilter) continue

            val matchesSearch = query.isEmpty() ||
                    q.title.lowercase().contains(query) ||
                    q.quizCode.lowercase().contains(query)
            if (matchesSearch) filtered.add(q)
        }

        adapter.notifyDataSetChanged()
        binding.tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showQuizActions(quiz: Quiz) {
        val actions = arrayOf("View Quiz Stats", "Delete Quiz", "Cancel")
        MaterialAlertDialogBuilder(this)
            .setTitle(quiz.title)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> {
                        val intent = Intent(this, QuizStatsActivity::class.java)
                        intent.putExtra("quizId", quiz.quizId)
                        intent.putExtra("quizTitle", quiz.title)
                        startActivity(intent)
                    }
                    1 -> confirmDelete(quiz)
                    else -> { }
                }
            }
            .show()
    }

    private fun confirmDelete(quiz: Quiz) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Quiz?")
            .setMessage("This will permanently remove \"${quiz.title}\" and all its related data.")
            .setPositiveButton("Delete") { _, _ -> deleteQuiz(quiz) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteQuiz(quiz: Quiz) {
        val quizId = quiz.quizId
        if (quizId.isEmpty()) return

        Toast.makeText(this, "Deleting quiz…", Toast.LENGTH_SHORT).show()

        deleteCollection("quizzes/$quizId/questions")
        deleteCollection("quizzes/$quizId/questions_private")
        deleteCollection("quizzes/$quizId/attempts")
        deleteCollection("quizzes/$quizId/cheat_logs")
        deleteResultsForQuiz(quizId)

        db.collection("quizzes").document(quizId).delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Quiz deleted", Toast.LENGTH_SHORT).show()
                loadQuizzes()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Delete failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteCollection(path: String) {
        db.collection(path).limit(100).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) return@addOnSuccessListener
                val batch = db.batch()
                snapshot.documents.forEach { batch.delete(it.reference) }
                batch.commit().addOnSuccessListener { deleteCollection(path) }
            }
    }

    private fun deleteResultsForQuiz(quizId: String) {
        db.collection("results").whereEqualTo("quizId", quizId).limit(100).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) return@addOnSuccessListener
                val batch = db.batch()
                snapshot.documents.forEach { batch.delete(it.reference) }
                batch.commit().addOnSuccessListener { deleteResultsForQuiz(quizId) }
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    inner class QuizzesAdapter(
        private val items: List<Quiz>,
        private val onClick: (Quiz) -> Unit
    ) : RecyclerView.Adapter<QuizzesAdapter.VH>() {

        inner class VH(val b: ItemAdminQuizBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemAdminQuizBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val quiz = items[position]
            val b = holder.b

            b.tvTitle.text = quiz.title.ifBlank { "(untitled)" }

            val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            val created = if (quiz.createdAt > 0) dateFormat.format(Date(quiz.createdAt)) else "—"
            b.tvMeta.text = "${quiz.totalQuestions} questions  •  ${quiz.visibility.replaceFirstChar { it.uppercase() }}  •  $created"
            b.tvOwner.text = if (quiz.creatorId.isNotBlank()) "Owner: ${quiz.creatorId.take(12)}…" else "Owner: unknown"

            // FIXED: If it has a quizCode, it is fully published and joinable
            val isActuallyPublished = quiz.status.equals("PUBLISHED", ignoreCase = true) || quiz.quizCode.isNotEmpty()

            when {
                isActuallyPublished -> {
                    b.chipStatus.text = "Published"
                    b.chipStatus.setChipBackgroundColorResource(R.color.success)
                    b.chipStatus.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.white))
                }
                quiz.status.equals("ARCHIVED", ignoreCase = true) -> {
                    b.chipStatus.text = "Archived"
                    b.chipStatus.setChipBackgroundColorResource(R.color.warning)
                    b.chipStatus.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.white))
                }
                else -> {
                    b.chipStatus.text = "Draft"
                    b.chipStatus.setChipBackgroundColorResource(R.color.surface_variant)
                    b.chipStatus.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.text_secondary))
                }
            }

            b.root.setOnClickListener { onClick(quiz) }
        }

        override fun getItemCount() = items.size
    }
}