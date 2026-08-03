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
    val allowMultipleAttempts: Boolean = false,
    val category: String = "General",
    val negativeMarking: Boolean = false,
    val negativeMarkingValue: Float = 0.25f,
    val hasImageQuestions: Boolean = false,
    val hasAudioQuestions: Boolean = false,
    val hasVideoQuestions: Boolean = false,
    // NEW TIMER FIELDS
    val timerType: String = "NONE",        // "NONE", "WHOLE_QUIZ", "PER_QUESTION"
    val totalTimeSeconds: Long = 0,        // for WHOLE_QUIZ
    val timePerQuestionSeconds: Long = 0   // for PER_QUESTION
) : Serializable