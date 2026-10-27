package com.smartquiz

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.firestore.FirebaseFirestore
import com.smartquiz.databinding.ActivityAdminAnnouncementsBinding
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class AdminAnnouncementsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminAnnouncementsBinding
    private lateinit var db: FirebaseFirestore

    // ⚠️ REPLACE THIS WITH YOUR ACTUAL FIREBASE PROJECT ID
    private val PROJECT_ID = "smartquiz-51b9d"

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

        // 1. Save to Firestore
        db.collection("announcements").add(announcement)
            .addOnSuccessListener {
                Toast.makeText(this, "Announcement saved! Sending push...", Toast.LENGTH_SHORT).show()
                fetchTokensAndSendPush(title, message)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun fetchTokensAndSendPush(title: String, message: String) {
        db.collection("users").get().addOnSuccessListener { documents ->
            val tokens = mutableListOf<String>()
            for (doc in documents) {
                val token = doc.getString("fcmToken")
                val isBanned = doc.getBoolean("isBanned") ?: false
                if (!token.isNullOrEmpty() && !isBanned) {
                    tokens.add(token)
                }
            }

            if (tokens.isEmpty()) {
                Toast.makeText(this, "No users have notification tokens.", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }

            // Run on background thread
            Thread {
                try {
                    // 2. Get Access Token using the JSON file
                    val stream = assets.open("service-account.json")
                    val credentials = GoogleCredentials.fromStream(stream)
                        .createScoped(listOf("https://www.googleapis.com/auth/firebase.messaging"))

                    credentials.refreshIfExpired()
                    val accessToken = credentials.accessToken.tokenValue

                    // 3. Send to each token via HTTP v1 API
                    for (token in tokens) {
                        sendFcmV1Request(accessToken, token, title, message)
                    }

                    runOnUiThread {
                        Toast.makeText(this, "Push notifications sent!", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                } catch (e: Exception) {
                    Log.e("FCM_SENDER", "Error sending push", e)
                    runOnUiThread {
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
        }
    }

    private fun sendFcmV1Request(accessToken: String, deviceToken: String, title: String, message: String) {
        val url = URL("https://fcm.googleapis.com/v1/projects/$PROJECT_ID/messages:send")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Authorization", "Bearer $accessToken")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true

        val body = JSONObject().apply {
            put("message", JSONObject().apply {
                put("token", deviceToken)
                put("notification", JSONObject().apply {
                    put("title", title)
                    put("body", message)
                })
            })
        }

        val outputStream = OutputStreamWriter(conn.outputStream)
        outputStream.write(body.toString())
        outputStream.flush()
        outputStream.close()

        val responseCode = conn.responseCode
        Log.d("FCM_SENDER", "FCM Response: $responseCode")
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}