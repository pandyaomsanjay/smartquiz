package com.smartquiz

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.smartquiz.databinding.ActivityAdminAnnouncementsBinding

class AdminAnnouncementsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminAnnouncementsBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private val TAG = "AdminAnnouncements"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminAnnouncementsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "You must be logged in", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // --- HEAVY LOGGING FOR DEBUGGING ---
        Log.d(TAG, "======================================")
        Log.d(TAG, "CURRENT USER UID: ${currentUser.uid}")
        Log.d(TAG, "CURRENT USER EMAIL: ${currentUser.email}")

        db.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val role = doc.getString("role")
                    Log.d(TAG, "FIRESTORE DB ROLE: '$role' (Make sure this is exactly 'admin')")
                } else {
                    Log.e(TAG, "USER DOCUMENT MISSING IN FIRESTORE!")
                }
            }
        Log.d(TAG, "======================================")

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
        binding.btnSend.isEnabled = false
        binding.btnSend.text = "Sending..."
        binding.progressBar.visibility = View.VISIBLE

        val announcement = hashMapOf(
            "title" to title,
            "message" to message,
            "timestamp" to System.currentTimeMillis(),
            "senderId" to (auth.currentUser?.uid ?: "")
        )

        db.collection("announcements").add(announcement)
            .addOnSuccessListener { docRef ->
                binding.btnSend.isEnabled = true
                binding.btnSend.text = "Send Announcement"
                binding.progressBar.visibility = View.GONE

                Toast.makeText(this, "Announcement sent successfully!", Toast.LENGTH_SHORT).show()
                binding.etTitle.setText("")
                binding.etMessage.setText("")
                finish()
            }
            .addOnFailureListener { e ->
                binding.btnSend.isEnabled = true
                binding.btnSend.text = "Send Announcement"
                binding.progressBar.visibility = View.GONE

                Log.e(TAG, "FAILED TO SEND ANNOUNCEMENT", e)

                if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    Toast.makeText(this, "PERMISSION DENIED. Check Logcat for details.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}