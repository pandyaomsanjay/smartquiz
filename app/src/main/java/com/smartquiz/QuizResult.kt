package com.smartquiz

data class QuizResult(
    val userId: String = "",
    val quizId: String = "",
    val score: Int = 0,
    val totalScore: Int = 0,
    val submittedAt: Long = 0
)