package com.smartquiz

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.smartquiz.databinding.ActivityFeedbackBinding

class FeedbackActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFeedbackBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFeedbackBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val quizId = intent.getStringExtra("quizId") ?: ""
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        binding.btnSubmit.setOnClickListener {
            val rating = binding.ratingBar.rating
            val comment = binding.etComment.text.toString()
            val feedback = hashMapOf(
                "quizId" to quizId,
                "userId" to userId,
                "rating" to rating,
                "comment" to comment,
                "createdAt" to System.currentTimeMillis()
            )
            FirebaseFirestore.getInstance().collection("feedback").add(feedback)
                .addOnSuccessListener {
                    Toast.makeText(this, "Thanks for your feedback!", Toast.LENGTH_SHORT).show()
                    finish()
                }
        }
    }
}
