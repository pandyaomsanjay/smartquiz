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
import com.google.android.gms.tasks.Tasks
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.smartquiz.databinding.ActivityAdminQuizzesBinding
import com.smartquiz.databinding.ItemAdminQuizBinding
import java.text.SimpleDateFormat
import java.util.*

/**
 * Admin — Quiz Management.
 *
 * Loads public quizzes + the admin's own quizzes via two
 * rule-compatible queries and merges them client-side.
 */
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

        // Search
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { applyFilterAndSearch() }
        })

        // Filter chips
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

    // ==================================================================
    // LOAD
    // ==================================================================
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

        // Rule-compatible split queries
        val publicTask = db.collection("quizzes")
            .whereEqualTo("visibility", "public")
            .get()

        val ownTask = db.collection("quizzes")
            .whereEqualTo("creatorId", currentUid)
            .get()

        Tasks.whenAllSuccess<QuerySnapshot>(publicTask, ownTask)
            .addOnSuccessListener { results ->
                binding.progressBar.visibility = View.GONE
                val seen = mutableSetOf<String>()

                // Public quizzes
                results.getOrNull(0)?.documents?.forEach { doc ->
                    if (seen.add(doc.id)) {
                        try {
                            val q = doc.toObject(Quiz::class.java)?.apply { quizId = doc.id }
                            if (q != null) allQuizzes.add(q)
                        } catch (e: Exception) {
                            Log.e(TAG, "parse failed ${doc.id}", e)
                        }
                    }
                }

                // Own quizzes (avoid duplicates)
                results.getOrNull(1)?.documents?.forEach { doc ->
                    if (seen.add(doc.id)) {
                        try {
                            val q = doc.toObject(Quiz::class.java)?.apply { quizId = doc.id }
                            if (q != null) allQuizzes.add(q)
                        } catch (e: Exception) {
                            Log.e(TAG, "parse failed ${doc.id}", e)
                        }
                    }
                }

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

    // ==================================================================
    // FILTER + SEARCH
    // ==================================================================
    private fun applyFilterAndSearch() {
        val query = binding.etSearch.text.toString().trim().lowercase()
        filtered.clear()

        for (q in allQuizzes) {
            val matchesFilter = when (currentFilter) {
                "public" -> q.visibility == "public"
                "private" -> q.visibility == "private"
                "published" -> q.status.equals("PUBLISHED", ignoreCase = true)
                else -> true
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

    // ==================================================================
    // QUIZ ACTIONS
    // ==================================================================
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
                    else -> { /* cancel */ }
                }
            }
            .show()
    }

    private fun confirmDelete(quiz: Quiz) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Quiz?")
            .setMessage(
                "This will permanently remove \"${quiz.title}\" and all its questions, " +
                        "attempts, cheat logs, and related results. This cannot be undone."
            )
            .setPositiveButton("Delete") { _, _ -> deleteQuiz(quiz) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteQuiz(quiz: Quiz) {
        val quizId = quiz.quizId
        if (quizId.isEmpty()) {
            Toast.makeText(this, "Invalid quiz ID", Toast.LENGTH_SHORT).show()
            return
        }

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
            .addOnFailureListener { e -> Log.e(TAG, "deleteCollection $path failed", e) }
    }

    private fun deleteResultsForQuiz(quizId: String) {
        db.collection("results").whereEqualTo("quizId", quizId).limit(100).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) return@addOnSuccessListener
                val batch = db.batch()
                snapshot.documents.forEach { batch.delete(it.reference) }
                batch.commit().addOnSuccessListener { deleteResultsForQuiz(quizId) }
            }
            .addOnFailureListener { e -> Log.e(TAG, "deleteResults failed", e) }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    // ==================================================================
    // ADAPTER
    // ==================================================================
    inner class QuizzesAdapter(
        private val items: List<Quiz>,
        private val onClick: (Quiz) -> Unit
    ) : RecyclerView.Adapter<QuizzesAdapter.VH>() {

        inner class VH(val b: ItemAdminQuizBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemAdminQuizBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return VH(b)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val quiz = items[position]
            val b = holder.b

            b.tvTitle.text = quiz.title.ifBlank { "(untitled)" }

            val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            val created = if (quiz.createdAt > 0)
                dateFormat.format(Date(quiz.createdAt)) else "—"

            b.tvMeta.text = "${quiz.totalQuestions} questions  •  " +
                    "${quiz.visibility.replaceFirstChar { it.uppercase() }}  •  $created"

            b.tvOwner.text = if (quiz.creatorId.isNotBlank())
                "Owner: ${quiz.creatorId.take(12)}…" else "Owner: unknown"

            when (quiz.status.uppercase()) {
                "PUBLISHED" -> {
                    b.chipStatus.text = "Published"
                    b.chipStatus.setChipBackgroundColorResource(R.color.success)
                    b.chipStatus.setTextColor(
                        ContextCompat.getColor(holder.itemView.context, android.R.color.white)
                    )
                }
                "DRAFT" -> {
                    b.chipStatus.text = "Draft"
                    b.chipStatus.setChipBackgroundColorResource(R.color.surface_variant)
                    b.chipStatus.setTextColor(
                        ContextCompat.getColor(holder.itemView.context, R.color.text_secondary)
                    )
                }
                "ARCHIVED" -> {
                    b.chipStatus.text = "Archived"
                    b.chipStatus.setChipBackgroundColorResource(R.color.warning)
                    b.chipStatus.setTextColor(
                        ContextCompat.getColor(holder.itemView.context, android.R.color.white)
                    )
                }
                else -> {
                    b.chipStatus.text = quiz.status.ifBlank { "—" }
                    b.chipStatus.setChipBackgroundColorResource(R.color.surface_variant)
                    b.chipStatus.setTextColor(
                        ContextCompat.getColor(holder.itemView.context, R.color.text_secondary)
                    )
                }
            }

            b.root.setOnClickListener { onClick(quiz) }
        }

        override fun getItemCount() = items.size
    }
}