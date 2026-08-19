// LeaderboardQuizItem.kt
data class LeaderboardQuizItem(
    val quizId: String,
    val title: String,
    val quizCode: String = "",
    val userScore: Int,
    val totalScore: Int,
    val userRank: Int,
    val totalParticipants: Int,
    val status: String  // "Completed", "In Progress", etc.
)