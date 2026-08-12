package com.smartquiz

import androidx.annotation.DrawableRes

data class TutorialSection(
    val title: String,
    val description: String,
    @DrawableRes val iconRes: Int,
    val bulletPoints: List<String> = emptyList()
)