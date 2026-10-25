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

        if (quizId.isEmpty()) {
            Log.e(TAG, "Cannot log cheat event: quizId is empty")
            return
        }

        // ============================================================
        // Resolve the user's real name and email from Firestore.
        // `auth.currentUser.displayName` is null for email/password
        // users, so we cannot rely on it. Firestore users/{uid} is the
        // single source of truth for the profile name.
        // ============================================================
        db.collection("users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                val resolvedName = userDoc.getString("name")
                    ?.takeIf { it.isNotBlank() }
                    ?: userName.takeIf { it.isNotBlank() }
                    ?: auth.currentUser?.displayName
                    ?: "Unknown"

                val resolvedEmail = userDoc.getString("email")
                    ?.takeIf { it.isNotBlank() }
                    ?: email.takeIf { it.isNotBlank() }
                    ?: auth.currentUser?.email
                    ?: ""

                writeLog(
                    context = context,
                    uid = uid,
                    resolvedName = resolvedName,
                    resolvedEmail = resolvedEmail,
                    eventType = eventType,
                    quizId = quizId,
                    quizTitle = quizTitle,
                    creatorId = creatorId
                )
            }
            .addOnFailureListener { e ->
                // Fallback to whatever we have (avoids losing the log
                // entirely if Firestore is temporarily unreachable).
                Log.e(TAG, "Failed to resolve user name from Firestore", e)
                val fallbackName = userName.takeIf { it.isNotBlank() }
                    ?: auth.currentUser?.displayName
                    ?: "Unknown"
                val fallbackEmail = email.takeIf { it.isNotBlank() }
                    ?: auth.currentUser?.email
                    ?: ""

                writeLog(
                    context = context,
                    uid = uid,
                    resolvedName = fallbackName,
                    resolvedEmail = fallbackEmail,
                    eventType = eventType,
                    quizId = quizId,
                    quizTitle = quizTitle,
                    creatorId = creatorId
                )
            }
    }

    /** Writes the actual cheat log document to Firestore. */
    private fun writeLog(
        context: Context,
        uid: String,
        resolvedName: String,
        resolvedEmail: String,
        eventType: String,
        quizId: String,
        quizTitle: String,
        creatorId: String
    ) {
        // Determine if suspicious based on violation count
        val (suspicious, count) = checkSuspicious(context)

        val now = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        val log = CheatLog(
            userId = uid,
            userName = resolvedName,
            email = resolvedEmail,
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

        db.collection("quizzes").document(quizId).collection("cheat_logs")
            .add(log)
            .addOnSuccessListener {
                Log.d(TAG, "Cheat log saved: $eventType for quiz $quizId (user=$resolvedName)")
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