package com.smartquiz

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.smartquiz.databinding.ActivitySetPasswordBinding

class SetPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetPasswordBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        val user = auth.currentUser

        if (user == null || user.email.isNullOrEmpty()) {
            finish()
            return
        }

        binding.btnSavePassword.setOnClickListener {
            val newPass = binding.etNewPassword.text.toString().trim()
            val confirmPass = binding.etConfirmPassword.text.toString().trim()

            if (newPass.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (newPass != confirmPass) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            linkPassword(user.email!!, newPass)
        }
    }

    private fun linkPassword(email: String, password: String) {
        val user = auth.currentUser ?: return
        val credential = EmailAuthProvider.getCredential(email, password)

        binding.progressBar.visibility = View.VISIBLE
        binding.btnSavePassword.isEnabled = false

        user.linkWithCredential(credential)
            .addOnCompleteListener { task ->
                binding.progressBar.visibility = View.GONE
                binding.btnSavePassword.isEnabled = true

                if (task.isSuccessful) {
                    Toast.makeText(this, "Password linked successfully!", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, HomeDashboardActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    val error = task.exception
                    if (error is FirebaseAuthUserCollisionException) {
                        Toast.makeText(
                            this,
                            "This email is already associated with another account.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(this, AuthHelper.getErrorMessage(error), Toast.LENGTH_LONG).show()
                    }
                }
            }
    }
}