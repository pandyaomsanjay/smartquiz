package com.smartquiz

data class User(
    var uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "user",          // "user", "creator", or "admin"
    val avatarUrl: String = "",
    val totalPoints: Int = 0,
    val streak: Int = 0,
    val lastQuizDate: Long = 0,
    val badges: List<String> = listOf(),
    val isBanned: Boolean = false,
    val banReason: String = ""          // optional, for admin blocking
)