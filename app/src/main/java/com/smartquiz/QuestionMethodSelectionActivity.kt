package com.smartquiz

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.smartquiz.databinding.ActivityQuestionMethodSelectionBinding

class QuestionMethodSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQuestionMethodSelectionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuestionMethodSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // NEW: Setup Toolbar and Back Button
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.cardManual.setOnClickListener {
            startActivity(Intent(this, QuizCreationActivity::class.java))
            finish()
        }

        binding.cardBulk.setOnClickListener {
            startActivity(Intent(this, BulkImportActivity::class.java))
            finish()
        }
    }

    // NEW: Handle Back Button click
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}