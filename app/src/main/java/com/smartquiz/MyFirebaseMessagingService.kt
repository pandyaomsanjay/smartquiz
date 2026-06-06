package com.smartquiz

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        remoteMessage.notification?.let {
            Log.d("FCM", "Message: ${it.body}")
            showNotification(
                it.title ?: "Quiz Update",
                it.body ?: ""
            )
        }
    }


    override fun onNewToken(token: String) {
        Log.d("FCM", "Refreshed token: $token")
    }

    private fun showNotification(title: String, body: String) {
        // Implement notification here
    }
}