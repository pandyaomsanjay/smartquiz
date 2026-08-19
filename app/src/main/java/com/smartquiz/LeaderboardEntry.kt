package com.smartquiz

// LeaderboardEntry.kt
data class LeaderboardEntry(
    val userId: String = "",
    val name: String = "",
    val totalScore: Int = 0,
    var rank: Int = 0  // computed
)