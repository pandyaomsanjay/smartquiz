package com.smartquiz

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.smartquiz.databinding.ActivityCreatorDashboardBinding

class CreatorDashboardActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCreatorDashboardBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private val allQuizzes = mutableListOf<Quiz>()          // Source of truth
    private val displayedQuizzes = mutableListOf<Quiz>()    // Filtered list for adapter
    private lateinit var quizAdapter: QuizAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreatorDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_back)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        quizAdapter = QuizAdapter(
            displayedQuizzes,
            onQuizClick = { quiz ->
                val intent = Intent(this, QuizStatsActivity::class.java)
                intent.putExtra("quizId", quiz.quizId)
                intent.putExtra("quizTitle", quiz.title)
                startActivity(intent)
            },
            onDeleteClick = { quiz ->
                showDeleteConfirmation(quiz)
            }
        )
        binding.rvMyQuizzes.layoutManager = LinearLayoutManager(this)
        binding.rvMyQuizzes.adapter = quizAdapter

        binding.btnDraftQuizzes.setOnClickListener {
            startActivity(Intent(this, DraftQuizzesActivity::class.java))
        }

        // Search functionality – title only
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterQuizzes(s.toString())
            }
        })

        loadMyQuizzes()
    }

    private fun loadMyQuizzes() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("quizzes").whereEqualTo("creatorId", uid).get()
            .addOnSuccessListener { docs ->
                allQuizzes.clear()
                for (doc in docs) {
                    val quiz = doc.toObject(Quiz::class.java)
                    quiz.quizId = doc.id
                    allQuizzes.add(quiz)
                }
                filterQuizzes(binding.etSearch.text.toString())
                if (allQuizzes.isEmpty()) {
                    Toast.makeText(this, "No quizzes created yet", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load quizzes: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun filterQuizzes(query: String) {
        displayedQuizzes.clear()
        if (query.isEmpty()) {
            displayedQuizzes.addAll(allQuizzes)
        } else {
            val lowerQuery = query.lowercase()
            for (quiz in allQuizzes) {
                // Search by title only
                if (quiz.title.lowercase().contains(lowerQuery)) {
                    displayedQuizzes.add(quiz)
                }
            }
        }
        quizAdapter.updateList(displayedQuizzes)
        if (displayedQuizzes.isEmpty() && query.isNotEmpty()) {
            Toast.makeText(this, "No matching quizzes found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDeleteConfirmation(quiz: Quiz) {
        AlertDialog.Builder(this)
            .setTitle("Delete Quiz")
            .setMessage("Are you sure you want to delete \"${quiz.title}\" and all its data (questions, attempts, results)? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteQuiz(quiz)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteQuiz(quiz: Quiz) {
        val quizId = quiz.quizId
        if (quizId.isEmpty()) {
            Toast.makeText(this, "Invalid quiz ID", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Deleting quiz...", Toast.LENGTH_SHORT).show()

        // Delete subcollections
        deleteCollection("quizzes/$quizId/questions")
        deleteCollection("quizzes/$quizId/questions_private")
        deleteCollection("quizzes/$quizId/attempts")
        deleteCollection("quizzes/$quizId/cheat_logs")

        // Delete results
        deleteResultsForQuiz(quizId)

        // Finally delete the quiz document
        db.collection("quizzes").document(quizId).delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Quiz deleted successfully", Toast.LENGTH_SHORT).show()
                loadMyQuizzes()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error deleting quiz: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteCollection(path: String) {
        val collectionRef = db.collection(path)
        collectionRef.limit(100).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) return@addOnSuccessListener
                val batch = db.batch()
                for (doc in snapshot.documents) {
                    batch.delete(doc.reference)
                }
                batch.commit().addOnSuccessListener {
                    deleteCollection(path) // recurse
                }.addOnFailureListener { e ->
                    e.printStackTrace()
                }
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
            }
    }

    private fun deleteResultsForQuiz(quizId: String) {
        db.collection("results")
            .whereEqualTo("quizId", quizId)
            .limit(100)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) return@addOnSuccessListener
                val batch = db.batch()
                for (doc in snapshot.documents) {
                    batch.delete(doc.reference)
                }
                batch.commit().addOnSuccessListener {
                    deleteResultsForQuiz(quizId) // recurse
                }.addOnFailureListener { e ->
                    e.printStackTrace()
                }
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}