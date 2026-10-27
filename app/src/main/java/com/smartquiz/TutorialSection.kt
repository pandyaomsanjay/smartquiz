package com.smartquiz

import androidx.annotation.DrawableRes

/**
 * A single expandable section in the offline tutorial.
 *
 * [category] was added in v1.6.0 for the tutorial redesign. It defaults to
 * [CATEGORY_GETTING_STARTED] so any section constructed without an explicit
 * category still compiles and shows up under "All".
 */
data class TutorialSection(
    val title: String,
    val description: String,
    @DrawableRes val iconRes: Int,
    val bulletPoints: List<String> = emptyList(),
    val category: String = CATEGORY_GETTING_STARTED
) {
    companion object {
        const val CATEGORY_GETTING_STARTED   = "Getting Started"
        const val CATEGORY_TAKING_QUIZZES    = "Taking Quizzes"
        const val CATEGORY_RESULTS           = "Results"
        const val CATEGORY_CREATING_QUIZZES  = "Creating Quizzes"
        const val CATEGORY_TROUBLESHOOTING   = "Troubleshooting"

        /** The pseudo-category used by the "All" filter chip. */
        const val CATEGORY_ALL = "All"

        /** Category chips displayed in [TutorialActivity], in display order. */
        val ALL_CATEGORIES: List<String> = listOf(
            CATEGORY_GETTING_STARTED,
            CATEGORY_TAKING_QUIZZES,
            CATEGORY_RESULTS,
            CATEGORY_CREATING_QUIZZES,
            CATEGORY_TROUBLESHOOTING
        )
    }
}