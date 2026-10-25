package com.smartquiz

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.smartquiz.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    private val googleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Toast.makeText(this, "Google sign in cancelled or failed.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .requestProfile()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Email / Password Login
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.btnLogin.isEnabled = false

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    binding.btnLogin.isEnabled = true
                    if (task.isSuccessful) {
                        checkUserStatusAndRoute()
                    } else {
                        // Use AuthHelper for clean error messages
                        Toast.makeText(this, AuthHelper.getErrorMessage(task.exception), Toast.LENGTH_LONG).show()
                    }
                }
        }

        // Google Sign-In Button
        binding.btnGoogleSignIn.setOnClickListener {
            googleSignInClient.signOut().addOnCompleteListener {
                googleLauncher.launch(googleSignInClient.signInIntent)
            }
        }

        // Forgot Password
        binding.tvForgotPassword.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Enter your email address first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "If an account exists, a reset link has been sent.", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, AuthHelper.getErrorMessage(task.exception), Toast.LENGTH_LONG).show()
                    }
                }
        }

        binding.tvSignup.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }
    }

    private fun checkUserStatusAndRoute() {
        val userId = auth.currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                if (doc.getBoolean("isBanned") == true) {
                    Toast.makeText(this, "Your account has been banned.", Toast.LENGTH_LONG).show()
                    auth.signOut()
                } else {
                    navigateToTarget(doc.getString("role")?.lowercase() ?: "user")
                }
            }
            .addOnFailureListener { navigateToTarget("user") }
    }

    private fun navigateToTarget(role: String) {
        val target = if (role == "admin" || role == "super_admin") AdminPanelActivity::class.java else HomeDashboardActivity::class.java
        val intent = Intent(this, target)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        safeFinish()
    }

    private fun safeFinish() {
        if (!isFinishing && !isDestroyed) {
            Handler(Looper.getMainLooper()).post { if (!isFinishing && !isDestroyed) finish() }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Toast.makeText(this, AuthHelper.getErrorMessage(task.exception), Toast.LENGTH_SHORT).show()
                    return@addOnCompleteListener
                }

                val user = auth.currentUser ?: return@addOnCompleteListener
                val uid = user.uid

                FirebaseFirestore.getInstance().collection("users").document(uid).get()
                    .addOnSuccessListener { doc ->
                        if (doc.getBoolean("isBanned") == true) {
                            Toast.makeText(this, "Your account has been banned.", Toast.LENGTH_LONG).show()
                            auth.signOut()
                            return@addOnSuccessListener
                        }

                        // =========================================================
                        // FIX: PREVENT AUTO-REGISTRATION FROM LOGIN PAGE
                        // =========================================================
                        if (!doc.exists()) {
                            // Sign out immediately so they aren't left in a half-authenticated state
                            auth.signOut()
                            googleSignInClient.signOut()

                            // Show clear dialog requiring explicit registration
                            MaterialAlertDialogBuilder(this)
                                .setTitle("Account Not Found")
                                .setMessage("No SmartQuiz account found for this Google account. Please create an account first.")
                                .setPositiveButton("Create Account") { _, _ ->
                                    startActivity(Intent(this, SignupActivity::class.java))
                                }
                                .setNegativeButton("Cancel", null)
                                .setCancelable(false)
                                .show()
                        } else {
                            // User exists -> Check password provider
                            if (!AuthHelper.hasPasswordProvider(user)) {
                                startActivity(Intent(this, SetPasswordActivity::class.java))
                                safeFinish()
                            } else {
                                navigateToTarget(doc.getString("role")?.lowercase() ?: "user")
                            }
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Network error. Please try again.", Toast.LENGTH_SHORT).show()
                        auth.signOut()
                    }
            }
    }
}