package com.smartquiz

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.smartquiz.databinding.ActivityCreatorDashboardBinding
import com.smartquiz.Quiz

class CreatorDashboardActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCreatorDashboardBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private val quizList = mutableListOf<Quiz>()
    private lateinit var quizAdapter: QuizAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreatorDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        quizAdapter = QuizAdapter(quizList) { quiz ->
            // Open detailed stats activity for this quiz
            val intent = Intent(this, QuizStatsActivity::class.java)
            intent.putExtra("quizId", quiz.quizId)
            intent.putExtra("quizTitle", quiz.title)
            startActivity(intent)
        }
        binding.rvMyQuizzes.layoutManager = LinearLayoutManager(this)
        binding.rvMyQuizzes.adapter = quizAdapter
        loadMyQuizzes()
    }

    private fun loadMyQuizzes() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("quizzes").whereEqualTo("creatorId", uid).get()
            .addOnSuccessListener { docs ->
                quizList.clear()
                for (doc in docs) {
                    val quiz = doc.toObject(Quiz::class.java)
                    quiz.quizId = doc.id
                    quizList.add(quiz)
                }
                quizAdapter.notifyDataSetChanged()
                if (quizList.isEmpty()) Toast.makeText(this, "No quizzes created yet", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load quizzes: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}