package com.smartquiz

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.android.gms.tasks.Tasks
import java.text.SimpleDateFormat
import java.util.*

object QuizTimeUtils {

    /** Milliseconds in 24 hours */
    const val ARCHIVE_DELAY_MS = 24L * 60 * 60 * 1000

    /**
     * Fetches the Firestore server time and returns it as ms.
     * Falls back to device time if the call fails.
     */
    fun getServerTimeMs(): Long {
        return try {
            val snap = Tasks.await(
                FirebaseFirestore.getInstance()
                    .document("system/serverTime")   // this doc must exist in Firestore
                    .get()
            )
            snap.getLong("now") ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    fun formatDateTime(ms: Long): String {
        if (ms <= 0) return "—"
        val fmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        return fmt.format(Date(ms))
    }

    fun formatDateTimeShort(ms: Long): String {
        if (ms <= 0) return "—"
        val fmt = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        return fmt.format(Date(ms))
    }

    /** True when the quiz is 24 h past its due time and should be archived. */
    fun shouldArchive(quiz: Quiz, serverTimeMs: Long): Boolean {
        if (quiz.archived) return false
        if (quiz.deadline <= 0) return false
        return serverTimeMs >= quiz.deadline + ARCHIVE_DELAY_MS
    }
}