package com.smartquiz

import java.io.Serializable

data class Quiz(
    var quizId: String = "",
    val title: String = "",
    val description: String = "",
    val creatorId: String = "",
    val createdAt: Long = 0,
    val isPublic: Boolean = true,          // legacy, will be replaced by visibility
    val visibility: String = "private",    // "public" or "private"
    val quizCode: String = "",             // only for private quizzes
    val totalQuestions: Int = 0,
    val timerSeconds: Int = 60,
    val deadline: Long = 0,
    val allowMultipleAttempts: Boolean = false,
    val category: String = "General",
    val negativeMarking: Boolean = false,
    val negativeMarkingValue: Float = 0.25f,
    val hasImageQuestions: Boolean = false,
    val hasAudioQuestions: Boolean = false,
    val hasVideoQuestions: Boolean = false
) : Serializable