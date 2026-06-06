package com.smartquiz

data class QuizAttempt(
    val attemptId: String = "",
    val userId: String = "",
    val quizId: String = "",
    val entryTimestamp: Long = 0,
    val exitTimestamp: Long = 0,
    val timeSpentSeconds: Int = 0,
    val score: Int = 0,
    val totalScore: Int = 0,
    val answers: Map<String, Int> = emptyMap(),
    val deviceInfo: String = "",
    val ipAddress: String = ""
)