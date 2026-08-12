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
    private var authListener: FirebaseAuth.AuthStateListener? = null
    private var isNavigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply splash theme to avoid white flash
        setTheme(R.style.Theme_SmartQuiz_Splash)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // Load animations
        val fadeIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
        val scaleUp = AnimationUtils.loadAnimation(this, R.anim.scale_up)
        binding.logoImageView.startAnimation(fadeIn)
        binding.appNameTextView.startAnimation(scaleUp)

        // Set up auth state listener
        authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            runOnUiThread {
                if (!isNavigated) {
                    isNavigated = true
                    if (user != null) {
                        startActivity(Intent(this, HomeDashboardActivity::class.java))
                    } else {
                        startActivity(Intent(this, LoginActivity::class.java))
                    }
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    finish()
                }
            }
        }

        // Add the listener
        auth.addAuthStateListener(authListener!!)

        // Fallback: if the listener doesn't fire (e.g., due to a race condition),
        // navigate after a timeout to ensure the app doesn't hang.
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isNavigated) {
                isNavigated = true
                val user = auth.currentUser
                if (user != null) {
                    startActivity(Intent(this, HomeDashboardActivity::class.java))
                } else {
                    startActivity(Intent(this, LoginActivity::class.java))
                }
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish()
            }
        }, 3000) // 3 seconds timeout; splash animation is 2 seconds, extra for token refresh
    }

    override fun onDestroy() {
        super.onDestroy()
        // Remove the listener to avoid memory leaks
        authListener?.let { auth.removeAuthStateListener(it) }
    }
}