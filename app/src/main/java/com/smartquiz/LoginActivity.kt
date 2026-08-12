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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.smartquiz.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    private val googleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Toast.makeText(this, "Google sign in failed: ${e.message}", Toast.LENGTH_SHORT).show()
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
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        // Check if user is banned
                        val userId = auth.currentUser?.uid
                        if (userId != null) {
                            FirebaseFirestore.getInstance().collection("users").document(userId).get()
                                .addOnSuccessListener { doc ->
                                    val isBanned = doc.getBoolean("isBanned") ?: false
                                    if (isBanned) {
                                        Toast.makeText(this, "Your account has been banned.", Toast.LENGTH_LONG).show()
                                        auth.signOut()
                                    } else {
                                        startActivity(Intent(this, HomeDashboardActivity::class.java))
                                        safeFinish()
                                    }
                                }
                                .addOnFailureListener {
                                    // If we can't check ban, allow login
                                    startActivity(Intent(this, HomeDashboardActivity::class.java))
                                    safeFinish()
                                }
                        } else {
                            startActivity(Intent(this, HomeDashboardActivity::class.java))
                            safeFinish()
                        }
                    } else {
                        val errorMessage = when (task.exception) {
                            is FirebaseAuthInvalidUserException -> "No account found with this email."
                            is FirebaseAuthInvalidCredentialsException -> "Incorrect password."
                            else -> "Login failed: ${task.exception?.message}"
                        }
                        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                    }
                }
        }

        binding.btnGoogleSignIn.setOnClickListener {
            googleSignInClient.signOut().addOnCompleteListener {
                val signInIntent = googleSignInClient.signInIntent
                googleLauncher.launch(signInIntent)
            }
        }

        binding.tvForgotPassword.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Enter your email address first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            auth.sendPasswordResetEmail(email)
                .addOnSuccessListener {
                    Toast.makeText(this, "Password reset email sent", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }

        binding.tvSignup.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }
    }

    private fun safeFinish() {
        if (!isFinishing && !isDestroyed) {
            Handler(Looper.getMainLooper()).post {
                if (!isFinishing && !isDestroyed) {
                    finish()
                }
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val isNewUser = task.result.additionalUserInfo?.isNewUser ?: false

                    // 🔒 NEW: Reject new Google users on the login page
                    if (isNewUser) {
                        Toast.makeText(
                            this,
                            "No account found. Please sign up first using email/password.",
                            Toast.LENGTH_LONG
                        ).show()
                        auth.signOut()
                        return@addOnCompleteListener
                    }

                    // Existing Google user – normal login
                    val userId = user?.uid
                    if (userId != null) {
                        FirebaseFirestore.getInstance().collection("users").document(userId).get()
                            .addOnSuccessListener { doc ->
                                val isBanned = doc.getBoolean("isBanned") ?: false
                                if (isBanned) {
                                    Toast.makeText(this, "Your account has been banned.", Toast.LENGTH_LONG).show()
                                    auth.signOut()
                                } else {
                                    startActivity(Intent(this, HomeDashboardActivity::class.java))
                                    safeFinish()
                                }
                            }
                            .addOnFailureListener {
                                startActivity(Intent(this, HomeDashboardActivity::class.java))
                                safeFinish()
                            }
                    } else {
                        startActivity(Intent(this, HomeDashboardActivity::class.java))
                        safeFinish()
                    }
                } else {
                    Toast.makeText(this, "Google sign-in failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }
}