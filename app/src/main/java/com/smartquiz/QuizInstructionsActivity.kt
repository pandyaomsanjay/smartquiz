package com.smartquiz

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.smartquiz.databinding.ActivityQuizInstructionsBinding

class QuizInstructionsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityQuizInstructionsBinding
    private var quizId = ""
    private var quizTitle = ""
    private var creatorId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuizInstructionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        quizId = intent.getStringExtra("quizId") ?: ""
        quizTitle = intent.getStringExtra("quizTitle") ?: "Quiz"
        creatorId = intent.getStringExtra("creatorId") ?: ""

        binding.tvQuizTitle.text = quizTitle
        binding.tvInstructions.text = """
            • Read each question carefully.
            • You cannot go back after submitting.
            • Timer will run; quiz auto-submits when time ends.
            • Do not switch apps or take screenshots.
            • Each question may have different point values.
            • Click "Start Quiz" to begin.
        """.trimIndent()

        binding.btnStartQuiz.setOnClickListener {
            val intent = Intent(this, QuizAttemptActivity::class.java)
            intent.putExtra("quizId", quizId)
            intent.putExtra("quizTitle", quizTitle)
            intent.putExtra("creatorId", creatorId)
            startActivity(intent)
            finish()
        }
    }
}