package com.smartquiz

import java.io.Serializable

data class Question(
    var questionId: String = "",
    val text: String = "",
    val options: List<String> = listOf(),
    val questionType: String = "radio", // "radio", "checkbox", "descriptive", "scenario"
    // For radio: single correct index
    var correctAnswerIndex: Int = 0,
    // For checkbox: list of correct indices
    var correctAnswerIndices: List<Int> = emptyList(),
    // For descriptive: correct answer text
    var correctAnswerText: String = "",
    val points: Int = 1,
    val imageUrl: String = "",
    val audioUrl: String = "",
    val videoUrl: String = "",

    // ---------- Scenario support ----------
    // For "scenario" type: the scenario/case/passage text.
    val scenarioText: String = "",
    // For "scenario" type: the sub-questions contained in this scenario.
    // Each sub-question is a normal Question with type
    // "radio", "checkbox", or "descriptive".
    // Sub-questions are NOT counted separately at the quiz level;
    // the quiz-level count is the sum of all sub-questions.
    val subQuestions: List<Question> = emptyList()
) : Serializable {

    /** Total marks for this entry. A scenario sums its sub-questions. */
    fun totalPoints(): Int {
        return if (questionType == "scenario") {
            subQuestions.sumOf { it.points }
        } else {
            points
        }
    }

    /** Counts the questions this entry contributes to the quiz total. */
    fun questionCount(): Int {
        return if (questionType == "scenario") subQuestions.size else 1
    }

    /** True if this is a scenario containing one or more sub-questions. */
    val isScenario: Boolean get() = questionType == "scenario"
}