package com.smartquiz

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.smartquiz.databinding.ActivityAdminPanelBinding

class AdminPanelActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAdminPanelBinding
    private lateinit var db: FirebaseFirestore
    private val users = mutableListOf<User>()
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminPanelBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        db = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        // 1. Ensure user is logged in
        if (currentUser == null) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val userId = currentUser.uid

        // 2. Verify admin role
        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                val role = doc.getString("role")?.lowercase() ?: "user"
                if (role != "admin") {
                    Toast.makeText(this, "Access Denied – Admin Only", Toast.LENGTH_LONG).show()
                    finish()
                    return@addOnSuccessListener
                }

                // 3. Admin-only initialisation
                adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
                binding.listUsers.adapter = adapter

                loadUsers()

                binding.btnAssignCreator.setOnClickListener {
                    val position = binding.listUsers.checkedItemPosition
                    if (position != -1) {
                        val user = users[position]
                        db.collection("users").document(user.uid).update("role", "creator")
                            .addOnSuccessListener {
                                Toast.makeText(this, "Role updated to creator", Toast.LENGTH_SHORT).show()
                                loadUsers()
                            }
                    } else {
                        Toast.makeText(this, "Select a user first", Toast.LENGTH_SHORT).show()
                    }
                }

                binding.btnBanUser.setOnClickListener {
                    val position = binding.listUsers.checkedItemPosition
                    if (position != -1) {
                        val user = users[position]
                        db.collection("users").document(user.uid).update("isBanned", true)
                            .addOnSuccessListener {
                                Toast.makeText(this, "User banned", Toast.LENGTH_SHORT).show()
                                loadUsers()
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "Failed: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        Toast.makeText(this, "Select a user first", Toast.LENGTH_SHORT).show()
                    }
                }

                binding.btnBlockUser.setOnClickListener {
                    val position = binding.listUsers.checkedItemPosition
                    if (position != -1) {
                        val user = users[position]
                        val reasonEditText = EditText(this)
                        AlertDialog.Builder(this)
                            .setTitle("Block User")
                            .setMessage("Reason for blocking ${user.name}?")
                            .setView(reasonEditText)
                            .setPositiveButton("Block") { _, _ ->
                                val reason = reasonEditText.text.toString().trim()
                                if (reason.isEmpty()) {
                                    Toast.makeText(this, "Please enter a reason", Toast.LENGTH_SHORT).show()
                                    return@setPositiveButton
                                }
                                db.collection("users").document(user.uid).update("isBanned", true, "banReason", reason)
                                    .addOnSuccessListener {
                                        Toast.makeText(this, "User blocked with reason: $reason", Toast.LENGTH_SHORT).show()
                                        loadUsers()
                                    }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    } else {
                        Toast.makeText(this, "Select a user first", Toast.LENGTH_SHORT).show()
                    }
                }

                binding.btnManageQuizzes.setOnClickListener {
                    startActivity(Intent(this, AdminQuizzesActivity::class.java))
                }

                binding.btnAnalytics.setOnClickListener {
                    startActivity(Intent(this, AdminAnalyticsActivity::class.java))
                }

                binding.btnAnnouncements.setOnClickListener {
                    startActivity(Intent(this, AdminAnnouncementsActivity::class.java))
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error verifying permissions: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun loadUsers() {
        db.collection("users").get()
            .addOnSuccessListener { docs ->
                users.clear()
                val list = mutableListOf<String>()
                for (doc in docs) {
                    val user = doc.toObject(User::class.java)
                    user.uid = doc.id
                    users.add(user)
                    val banned = if (user.isBanned) " [BANNED]" else ""
                    list.add("${user.name} (${user.email}) - Role: ${user.role}$banned")
                }
                adapter.clear()
                adapter.addAll(list)
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load users", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}