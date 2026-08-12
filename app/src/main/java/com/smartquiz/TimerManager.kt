package com.smartquiz

import android.os.CountDownTimer

/**
 * Centralised timer manager for quiz attempts.
 * Supports three modes: NONE, WHOLE_QUIZ, PER_QUESTION.
 *
 * Usage:
 * - Create instance with mode and callbacks.
 * - For WHOLE_QUIZ: call startWholeQuiz(totalSeconds).
 * - For PER_QUESTION: call startQuestionTimer(questionId, perQuestionSeconds).
 * - On pause/resume: call pauseTimer() / resumeTimer().
 * - On destroy: call cancel().
 */
class TimerManager(
    private val mode: TimerMode,
    private val onTick: (remainingSeconds: Long) -> Unit,
    private val onFinish: () -> Unit
) {

    enum class TimerMode {
        NONE, WHOLE_QUIZ, PER_QUESTION
    }

    private var timer: CountDownTimer? = null
    private var remainingMillis: Long = 0L
    private var currentQuestionId: String? = null
    private val questionRemainingMap = mutableMapOf<String, Long>()

    // For whole quiz restore
    private var isWholeQuizRunning = false

    /**
     * Start the whole‑quiz timer.
     * @param totalSeconds total duration in seconds
     */
    fun startWholeQuiz(totalSeconds: Long) {
        if (mode != TimerMode.WHOLE_QUIZ) return
        isWholeQuizRunning = true
        startTimer(totalSeconds)
    }

    /**
     * Start the timer for a specific question.
     * Uses any saved remaining time for that question, or the default per‑question duration.
     */
    fun startQuestionTimer(questionId: String, defaultSeconds: Long) {
        if (mode != TimerMode.PER_QUESTION) return
        currentQuestionId = questionId
        val saved = questionRemainingMap[questionId] ?: defaultSeconds
        if (saved <= 0) {
            onFinish()
            return
        }
        startTimer(saved)
    }

    /**
     * Pause the current timer, saving its remaining time.
     */
    fun pauseTimer() {
        timer?.cancel()
        if (mode == TimerMode.WHOLE_QUIZ) {
            // remainingMillis is already updated by onTick
        } else if (mode == TimerMode.PER_QUESTION && currentQuestionId != null) {
            questionRemainingMap[currentQuestionId!!] = remainingMillis / 1000
        }
    }

    /**
     * Resume the paused timer.
     */
    fun resumeTimer() {
        if (remainingMillis <= 0) {
            onFinish()
            return
        }
        startTimer(remainingMillis / 1000)
    }

    /**
     * Stop and clean up the timer.
     */
    fun cancel() {
        timer?.cancel()
        timer = null
    }

    /**
     * Reset the timer for a new question (for PER_QUESTION mode).
     * Pauses current timer and clears the current question reference.
     * The new question's timer will be started with its own saved time.
     */
    fun resetForNewQuestion(newQuestionId: String) {
        if (mode != TimerMode.PER_QUESTION) return
        pauseTimer()
        currentQuestionId = newQuestionId
        // The next startQuestionTimer will use the saved time for this new question
    }

    /**
     * Restore the whole‑quiz timer state after rotation.
     * @param remainingSeconds remaining seconds before the timer was paused
     */
    fun restoreWholeQuiz(remainingSeconds: Long) {
        if (mode != TimerMode.WHOLE_QUIZ) return
        remainingMillis = remainingSeconds * 1000
        isWholeQuizRunning = true
        // Do not start automatically; caller must call resumeTimer() after UI restore
    }

    /**
     * Get the remaining time (in seconds) for a specific question (PER_QUESTION mode).
     */
    fun getRemainingForQuestion(questionId: String): Long {
        return questionRemainingMap[questionId] ?: 0
    }

    /**
     * For whole‑quiz mode, get the current remaining seconds.
     */
    fun getWholeQuizRemaining(): Long {
        return if (mode == TimerMode.WHOLE_QUIZ) remainingMillis / 1000 else 0
    }

    /**
     * Check if the whole‑quiz timer is currently active (running or paused).
     */
    fun isWholeQuizActive(): Boolean = isWholeQuizRunning

    private fun startTimer(seconds: Long) {
        cancel()
        remainingMillis = seconds * 1000
        timer = object : CountDownTimer(remainingMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                remainingMillis = millisUntilFinished
                this@TimerManager.onTick(millisUntilFinished / 1000)
            }

            override fun onFinish() {
                remainingMillis = 0
                this@TimerManager.onFinish()
            }
        }.start()
    }
}