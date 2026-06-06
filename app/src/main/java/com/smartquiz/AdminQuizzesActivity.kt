package com.smartquiz

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.smartquiz.databinding.ActivityAdminQuizzesBinding

class AdminQuizzesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminQuizzesBinding
    private lateinit var db: FirebaseFirestore
    private val quizList = mutableListOf<Quiz>()
    private lateinit var adapter: QuizAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminQuizzesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        db = FirebaseFirestore.getInstance()

        adapter = QuizAdapter(quizList) { quiz ->
            AlertDialog.Builder(this)
                .setTitle("Delete Quiz")
                .setMessage("Are you sure you want to delete \"${quiz.title}\"? All questions and results will be lost.")
                .setPositiveButton("Delete") { _, _ ->
                    deleteQuiz(quiz)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        binding.rvQuizzes.layoutManager = LinearLayoutManager(this)
        binding.rvQuizzes.adapter = adapter

        loadAllQuizzes()
    }

    private fun loadAllQuizzes() {
        db.collection("quizzes").get()
            .addOnSuccessListener { docs ->
                quizList.clear()
                for (doc in docs) {
                    val quiz = doc.toObject(Quiz::class.java)
                    quiz.quizId = doc.id
                    quizList.add(quiz)
                }
                adapter.notifyDataSetChanged()
            }
    }

    private fun deleteQuiz(quiz: Quiz) {
        db.collection("quizzes").document(quiz.quizId).delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Quiz deleted", Toast.LENGTH_SHORT).show()
                loadAllQuizzes()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}