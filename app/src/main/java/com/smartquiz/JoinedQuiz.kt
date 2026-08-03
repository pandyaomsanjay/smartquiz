package com.smartquiz.models

import java.io.Serializable

data class JoinedQuiz(
    val quizId: String = "",
    val quizTitle: String = "",
    val quizCode: String = "",
    val creatorId: String = "",
    val creatorName: String = "",
    val joinTime: Long = 0,
    val submitTime: Long? = null,
    var status: String = "In Progress",
    val score: Double? = null,        // Changed to Double
    val category: String = "",
    val allowMultipleAttempts: Boolean = false
) : Serializable