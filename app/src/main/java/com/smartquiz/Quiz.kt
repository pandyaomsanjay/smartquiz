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
    val timerSeconds: Int = 60,            // legacy – keep for compatibility
    val deadline: Long = 0,
    val startTime: Long = 0,
    val allowMultipleAttempts: Boolean = false,
    val category: String = "General",
    val negativeMarking: Boolean = false,
    val negativeMarkingValue: Float = 0.25f,
    val hasImageQuestions: Boolean = false,
    val hasAudioQuestions: Boolean = false,
    val hasVideoQuestions: Boolean = false,
    // TIMER FIELDS
    val timerType: String = "NONE",        // "NONE", "WHOLE_QUIZ", "PER_QUESTION"
    val totalTimeSeconds: Long = 0,
    val timePerQuestionSeconds: Long = 0,
    // Randomization mode
    val randomizationMode: String = "FIXED_ORDER",  // "FIXED_ORDER", "RANDOM_QUESTION_ORDER", "RANDOM_QUESTION_AND_OPTION_ORDER"
    // Score visibility
    val showScoreAfterSubmission: Boolean = true,
    // Draft system
    val status: String = "DRAFT",          // "DRAFT", "PUBLISHED", "EXPIRED", "ARCHIVED"
    val updatedAt: Long = 0L,
    // Archive / lifecycle
    val archived: Boolean = false,
    val archivedAt: Long = 0L,
    val participantCount: Int = 0,

    // ---------- NEW: creator-configured question count ----------
    // The number the creator declares on the creation screen. The actual
    // sum of normal questions + scenario sub-questions must match this
    // value before the quiz can be published. 0 means "not enforced".
    val configuredQuestionCount: Int = 0
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