package com.smartquiz

import java.io.Serializable

data class Question(
    var questionId: String = "",
    val text: String = "",
    val options: List<String> = listOf(),
    var correctAnswerIndex: Int = 0,
    val points: Int = 1,
    val imageUrl: String = "",      // optional image
    val audioUrl: String = "",      // optional audio
    val videoUrl: String = ""       // optional video
) : Serializable
