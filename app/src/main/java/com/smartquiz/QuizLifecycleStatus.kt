package com.smartquiz

enum class QuizLifecycleStatus(val label: String, val colorRes: Int) {
    UPCOMING("UPCOMING", R.color.warning),
    LIVE("LIVE", R.color.success),
    COMPLETED("COMPLETED", R.color.primary),
    EXPIRED("EXPIRED", R.color.error),
    DELETED("DELETED", R.color.text_secondary);

    val isJoinable: Boolean
        get() = this == LIVE
}