package com.smartquiz

import java.io.Serializable

/**
 * Tracks the state of a single question during an attempt.
 */
data class QuestionState(
    val questionId: String = "",
    var isAnswered: Boolean = false,
    var isMarkedForReview: Boolean = false,
    var isBookmarked: Boolean = false,
    var isLocked: Boolean = false,          // timer expired or quiz submitted
    var answer: Any? = null,               // can be Int, List<Int>, or String
    var lastSavedAnswer: Any? = null       // for conflict resolution
) : Serializable