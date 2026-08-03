package com.smartquiz

import java.io.Serializable

data class Question(
    var questionId: String = "",
    val text: String = "",
    val options: List<String> = listOf(),
    val questionType: String = "radio", // "radio", "checkbox", "descriptive"
    // For radio: single correct index
    var correctAnswerIndex: Int = 0,
    // For checkbox: list of correct indices
    var correctAnswerIndices: List<Int> = emptyList(),
    // For descriptive: correct answer text
    var correctAnswerText: String = "",
    val points: Int = 1,
    val imageUrl: String = "",
    val audioUrl: String = "",
    val videoUrl: String = ""
) : Serializable