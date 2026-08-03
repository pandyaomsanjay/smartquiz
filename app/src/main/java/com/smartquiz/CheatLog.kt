package com.smartquiz

data class CheatLog(
    val userId: String = "",
    val userName: String = "",
    val email: String = "",
    val quizId: String = "",
    val creatorId: String = "",
    val quizTitle: String = "",
    val eventType: String = "",          // BACK_BUTTON, HOME_OR_RECENTS, APP_BACKGROUND, FOCUS_LOST
    val screen: String = "",
    val deviceModel: String = "",
    val androidVersion: String = "",
    val timestamp: Long = 0,
    val date: String = "",
    val time: String = "",
    val internetStatus: String = "Online",
    val suspicious: Boolean = false,
    val violationCount: Int = 0
)