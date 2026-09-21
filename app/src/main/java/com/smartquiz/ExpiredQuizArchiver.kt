package com.smartquiz

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions

/**
 * Handles archiving of expired public quizzes.
 *
 * This is a client‑side sweep that runs:
 *  - Once on every app launch (QuizApplication.onCreate)
 *  - Periodically via WorkManager (best‑effort)
 *
 * It does NOT delete data:
 *  - Quiz doc is kept (archived = true, status = "ARCHIVED")
 *  - results, cheat_logs, attempts subcollections are untouched
 *  - A summary is copied to `archived_public_quizzes` for analytics
 */
object ExpiredQuizArchiver {

    private const val TAG = "QuizArchiver"
    private const val ARCHIVE_DELAY_MS = 24L * 60 * 60 * 1000

    /**
     * Sweeps all public quizzes whose deadline is older than 24 h.
     * Safe to call repeatedly — uses `archived == false` filter.
     */
    fun sweep(context: Context, onDone: ((Int) -> Unit)? = null) {
        val db = FirebaseFirestore.getInstance()
        val cutoff = System.currentTimeMillis() - ARCHIVE_DELAY_MS

        db.collection("quizzes")
            .whereEqualTo("visibility", "public")
            .whereEqualTo("archived", false)
            .whereGreaterThan("deadline", 0)
            .whereLessThanOrEqualTo("deadline", cutoff)
            .get()
            .addOnSuccessListener { docs ->
                if (docs.isEmpty) {
                    Log.d(TAG, "No expired quizzes to archive.")
                    onDone?.invoke(0)
                    return@addOnSuccessListener
                }

                val batch = db.batch()
                docs.forEach { doc ->
                    val data = doc.data

                    // 1. Copy summary to archival collection (long‑term analytics)
                    val summaryRef = db.collection("archived_public_quizzes").document(doc.id)
                    batch.set(summaryRef, mapOf(
                        "quizId" to doc.id,
                        "title" to (data["title"] ?: ""),
                        "description" to (data["description"] ?: ""),
                        "creatorId" to (data["creatorId"] ?: ""),
                        "startTime" to (data["startTime"] ?: 0L),
                        "deadline" to (data["deadline"] ?: 0L),
                        "totalQuestions" to (data["totalQuestions"] ?: 0),
                        "archivedAt" to System.currentTimeMillis(),
                        "originalStatus" to (data["status"] ?: "PUBLISHED")
                    ), SetOptions.merge())

                    // 2. Mark quiz as archived — DO NOT delete.
                    batch.update(doc.reference, mapOf(
                        "archived" to true,
                        "status" to "ARCHIVED",
                        "archivedAt" to System.currentTimeMillis()
                    ))
                }

                batch.commit()
                    .addOnSuccessListener {
                        Log.d(TAG, "Archived ${docs.size()} quizzes.")
                        onDone?.invoke(docs.size())
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Archive batch failed: ${e.message}")
                        onDone?.invoke(0)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Sweep query failed: ${e.message}")
                onDone?.invoke(0)
            }
    }
}