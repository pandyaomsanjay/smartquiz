package com.smartquiz

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.smartquiz.databinding.ActivityChangePasswordBinding

class ChangePasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChangePasswordBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChangePasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        binding.btnUpdatePassword.setOnClickListener {
            val currentPass = binding.etCurrentPassword.text.toString().trim()
            val newPass = binding.etNewPassword.text.toString().trim()
            val confirmPass = binding.etConfirmPassword.text.toString().trim()

            if (currentPass.isEmpty()) {
                Toast.makeText(this, "Enter current password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (newPass.length < 6) {
                Toast.makeText(this, "New password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (newPass != confirmPass) {
                Toast.makeText(this, "New passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            changePassword(currentPass, newPass)
        }
    }

    private fun changePassword(currentPass: String, newPass: String) {
        val user = auth.currentUser ?: return
        val email = user.email ?: return

        binding.progressBar.visibility = View.VISIBLE
        binding.btnUpdatePassword.isEnabled = false

        val credential = EmailAuthProvider.getCredential(email, currentPass)
        user.reauthenticate(credential)
            .addOnCompleteListener { reauthTask ->
                if (reauthTask.isSuccessful) {
                    user.updatePassword(newPass)
                        .addOnCompleteListener { updateTask ->
                            binding.progressBar.visibility = View.GONE
                            binding.btnUpdatePassword.isEnabled = true

                            if (updateTask.isSuccessful) {
                                Toast.makeText(this, "Password updated successfully!", Toast.LENGTH_SHORT).show()
                                finish()
                            } else {
                                Toast.makeText(this, AuthHelper.getErrorMessage(updateTask.exception), Toast.LENGTH_LONG).show()
                            }
                        }
                } else {
                    binding.progressBar.visibility = View.GONE
                    binding.btnUpdatePassword.isEnabled = true
                    Toast.makeText(this, AuthHelper.getErrorMessage(reauthTask.exception), Toast.LENGTH_LONG).show()
                }
            }
    }
}