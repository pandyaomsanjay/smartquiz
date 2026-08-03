package com.smartquiz

import android.app.Application
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

class QuizApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()
        FirebaseFirestore.getInstance().firestoreSettings = settings
    }
}

// ========== DURATION UTILITIES ==========
/**
 * Formats a duration (in seconds) as HH:MM:SS.
 * Example: 65 -> "00:01:05"
 */
fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d:%02d", hours, minutes, seconds)
}

/**
 * Parses a time string in HH:MM:SS, MM:SS, or plain seconds into total seconds.
 * Returns null for invalid input.
 */
fun parseDurationToSeconds(timeString: String): Long? {
    val trimmed = timeString.trim()
    if (trimmed.isEmpty()) return null
    val parts = trimmed.split(":")
    return when (parts.size) {
        3 -> {
            val h = parts[0].toIntOrNull()
            val m = parts[1].toIntOrNull()
            val s = parts[2].toIntOrNull()
            if (h != null && m != null && s != null) h * 3600L + m * 60L + s else null
        }
        2 -> {
            val m = parts[0].toIntOrNull()
            val s = parts[1].toIntOrNull()
            if (m != null && s != null) m * 60L + s else null
        }
        1 -> parts[0].toLongOrNull()
        else -> null
    }
}