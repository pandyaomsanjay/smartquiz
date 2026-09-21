package com.smartquiz

import java.io.Serializable

data class Quiz(
    var quizId: String = "",
    val title: String = "",
    val description: String = "",
    val creatorId: String = "",
    val createdAt: Long = 0,
    val isPublic: Boolean = true,
    val visibility: String = "private",
    val quizCode: String = "",
    val totalQuestions: Int = 0,
    val timerSeconds: Int = 60,
    val deadline: Long = 0,                 // due date/time (ms)
    val startTime: Long = 0,                // NEW – start date/time (ms)
    val allowMultipleAttempts: Boolean = false,
    val category: String = "General",
    val negativeMarking: Boolean = false,
    val negativeMarkingValue: Float = 0.25f,
    val hasImageQuestions: Boolean = false,
    val hasAudioQuestions: Boolean = false,
    val hasVideoQuestions: Boolean = false,
    val timerType: String = "NONE",
    val totalTimeSeconds: Long = 0,
    val timePerQuestionSeconds: Long = 0,
    val randomizationMode: String = "FIXED_ORDER",
    val showScoreAfterSubmission: Boolean = true,
    val status: String = "DRAFT",           // DRAFT / PUBLISHED
    val updatedAt: Long = 0L,

    // ---------- NEW: lifecycle & archive ----------
    val archived: Boolean = false,          // true once archive/delete ran
    val archivedAt: Long = 0L,              // server timestamp when archived
    val participantCount: Int = 0           // cached count for listing
) : Serializable {

    /**
     * Returns the live lifecycle status.
     *  UPCOMING   – startTime is in the future
     *  LIVE       – startTime <= now < deadline
     *  COMPLETED  – deadline passed but the quiz was marked completed by creator (optional)
     *  EXPIRED    – deadline passed
     *  DELETED    – archived == true
     *
     * Uses `serverTimeMs` (from Firestore ServerValue or current time) so the
     * calculation is not dependent on the local clock.
     */
    fun computeStatus(serverTimeMs: Long = System.currentTimeMillis()): QuizLifecycleStatus {
        if (archived) return QuizLifecycleStatus.DELETED
        val now = serverTimeMs
        return when {
            startTime > 0 && now < startTime -> QuizLifecycleStatus.UPCOMING
            deadline > 0 && now >= deadline -> QuizLifecycleStatus.EXPIRED
            else -> QuizLifecycleStatus.LIVE
        }
    }
}