package com.smartquiz

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.smartquiz.databinding.ActivityAdminAnnouncementsBinding

class AdminAnnouncementsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminAnnouncementsBinding
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminAnnouncementsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        db = FirebaseFirestore.getInstance()

        binding.btnSend.setOnClickListener {
            val title = binding.etTitle.text.toString().trim()
            val message = binding.etMessage.text.toString().trim()
            if (title.isEmpty() || message.isEmpty()) {
                Toast.makeText(this, "Please fill both fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            sendAnnouncement(title, message)
        }
    }

    private fun sendAnnouncement(title: String, message: String) {
        val announcement = hashMapOf(
            "title" to title,
            "message" to message,
            "timestamp" to System.currentTimeMillis()
        )
        db.collection("announcements").add(announcement)
            .addOnSuccessListener {
                Toast.makeText(this, "Announcement sent", Toast.LENGTH_SHORT).show()
                finish()
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