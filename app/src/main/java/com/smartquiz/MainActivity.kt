package com.smartquiz

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.smartquiz.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private var isNavigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_SmartQuiz_Splash)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // Splash Animations
        val fadeIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
        val scaleUp = AnimationUtils.loadAnimation(this, R.anim.scale_up)
        binding.logoImageView.startAnimation(fadeIn)
        binding.appNameTextView.startAnimation(scaleUp)

        // Check Authentication Session
        checkSession()
    }

    private fun checkSession() {
        val currentUser = auth.currentUser

        Handler(Looper.getMainLooper()).postDelayed({
            if (isNavigated || isFinishing) return@postDelayed
            isNavigated = true

            if (currentUser == null) {
                // Not logged in -> Route to Login
                navigateTo(LoginActivity::class.java)
            } else {
                // Logged in (Admin or User) -> Route to Normal Dashboard
                // HomeDashboardActivity will show the Admin button if the user is an admin.
                navigateTo(HomeDashboardActivity::class.java)
            }
        }, 1500) // 1.5 second splash delay
    }

    private fun navigateTo(target: Class<*>) {
        val intent = Intent(this, target)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}