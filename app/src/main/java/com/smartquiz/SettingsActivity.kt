package com.smartquiz

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.smartquiz.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // 1. Setup App Version
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            binding.tvAppVersion.text = "SmartQuiz v${pInfo.versionName}"
        } catch (e: Exception) {
            binding.tvAppVersion.text = "SmartQuiz"
        }

        // 2. Load User Data
        loadUserProfile()

        // 3. Setup Navigation Click Listeners
        binding.rowTutorial.setOnClickListener {
            startActivity(Intent(this, TutorialActivity::class.java))
        }

        binding.rowAccountInfo.setOnClickListener {
            startActivity(Intent(this, UserProfileActivity::class.java))
        }

        // 4. Setup Logout with Confirmation
        binding.btnLogout.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout of SmartQuiz?")
                .setPositiveButton("Logout") { _, _ ->
                    auth.signOut()
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun loadUserProfile() {
        val user = auth.currentUser
        if (user == null) {
            finish()
            return
        }

        // Fetch from Firestore for the most up-to-date profile info
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                val name = doc.getString("name") ?: "User"
                val email = doc.getString("email") ?: user.email ?: ""
                val avatarUrl = doc.getString("avatarUrl")

                binding.tvName.text = name
                binding.tvEmail.text = email

                if (!avatarUrl.isNullOrEmpty()) {
                    Glide.with(this).load(avatarUrl).into(binding.ivAvatar)
                }
            }
            .addOnFailureListener {
                binding.tvName.text = user.displayName ?: "User"
                binding.tvEmail.text = user.email ?: ""
            }

        // ============================================================
        // Provider Detection for Password Management
        // ============================================================
        val hasPassword = AuthHelper.hasPasswordProvider(user)

        if (hasPassword) {
            binding.tvPasswordTitle.text = "Change Password"
            binding.tvPasswordSubtitle.text = "Update your existing password"
            binding.rowPassword.setOnClickListener {
                startActivity(Intent(this, ChangePasswordActivity::class.java))
            }
        } else {
            binding.tvPasswordTitle.text = "Set Password"
            binding.tvPasswordSubtitle.text = "Add a password for email login"
            binding.rowPassword.setOnClickListener {
                startActivity(Intent(this, SetPasswordActivity::class.java))
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}