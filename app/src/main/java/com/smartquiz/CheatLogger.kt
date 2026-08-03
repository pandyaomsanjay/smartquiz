package com.smartquiz

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

object CheatLogger {
    private const val TAG = "CheatLogger"
    private const val PREF_NAME = "cheat_prefs"
    private const val KEY_EVENTS = "events"
    private const val WINDOW_MS = 5 * 60 * 1000L // 5 minutes
    private const val SUSPICIOUS_THRESHOLD = 3

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun logCheatEvent(
        context: Context,
        eventType: String,
        quizId: String = "",
        quizTitle: String = "",
        creatorId: String = "",
        userId: String? = null,
        userName: String = "",
        email: String = ""
    ) {
        val uid = userId ?: auth.currentUser?.uid ?: return
        val name = userName.ifEmpty { auth.currentUser?.displayName ?: "Unknown" }
        val mail = email.ifEmpty { auth.currentUser?.email ?: "" }

        if (quizId.isEmpty()) {
            Log.e(TAG, "Cannot log cheat event: quizId is empty")
            return
        }

        // Determine if suspicious based on violation count
        val (suspicious, count) = checkSuspicious(context)

        val now = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        val log = CheatLog(
            userId = uid,
            userName = name,
            email = mail,
            quizId = quizId,
            creatorId = creatorId,
            quizTitle = quizTitle,
            eventType = eventType,
            screen = "QuizAttemptActivity",
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            timestamp = now,
            date = dateFormat.format(Date(now)),
            time = timeFormat.format(Date(now)),
            internetStatus = "Online",
            suspicious = suspicious,
            violationCount = count
        )

        // ✅ Save to subcollection of the specific quiz
        db.collection("quizzes").document(quizId).collection("cheat_logs")
            .add(log)
            .addOnSuccessListener {
                Log.d(TAG, "Cheat log saved: $eventType for quiz $quizId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save cheat log: ${e.message}")
            }

        updateViolationCount(context)
    }

    private fun checkSuspicious(context: Context): Pair<Boolean, Int> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val eventsJson = prefs.getString(KEY_EVENTS, "[]") ?: "[]"
        val events = mutableListOf<Long>()
        try {
            val trimmed = eventsJson.trim('[', ']')
            if (trimmed.isNotEmpty()) {
                events.addAll(trimmed.split(',').map { it.trim().toLong() })
            }
        } catch (e: Exception) { /* ignore */ }

        val now = System.currentTimeMillis()
        val recent = events.filter { now - it <= WINDOW_MS }
        val count = recent.size
        return Pair(count >= SUSPICIOUS_THRESHOLD, count)
    }

    private fun updateViolationCount(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val eventsJson = prefs.getString(KEY_EVENTS, "[]") ?: "[]"
        val events = mutableListOf<Long>()
        try {
            val trimmed = eventsJson.trim('[', ']')
            if (trimmed.isNotEmpty()) {
                events.addAll(trimmed.split(',').map { it.trim().toLong() })
            }
        } catch (e: Exception) { /* ignore */ }

        val now = System.currentTimeMillis()
        val validEvents = events.filter { now - it <= WINDOW_MS }
        val newEvents = validEvents + now
        val limited = newEvents.takeLast(20)
        val json = limited.joinToString(prefix = "[", postfix = "]")
        prefs.edit().putString(KEY_EVENTS, json).apply()
    }

    fun clearViolations(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_EVENTS, "[]")
            .apply()
    }
}