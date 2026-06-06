package com.smartquiz

data class CheatLog(
    val userId: String = "",
    val quizId: String = "",
    val reason: String = "",
    val timestamp: Long = 0,
    val deviceInfo: String = ""
)