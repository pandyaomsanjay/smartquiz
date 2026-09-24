package com.smartquiz

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/**
 * Centralised normalization + comparison for Descriptive answers.
 *
 * This is the single source of truth for "are these two descriptive
 * answers equivalent?". All evaluation sites (QuizAttemptActivity,
 * QuizStatsActivity, CreatorAnalyticsActivity, ...) MUST call into
 * here — no duplicate comparison logic anywhere else.
 *
 * ---------------------------------------------------------------------
 * Behavior (documented intentionally):
 *
 *   1. null / blank answers are never considered correct.
 *   2. Leading / trailing whitespace is ignored.
 *   3. Repeated whitespace (spaces, tabs, newlines) collapses to one space.
 *   4. Unicode NFKC normalization (so different Unicode forms of the
 *      same character compare equal).
 *   5. Case differences are ignored (Locale.ROOT lowercase).
 *   6. Numeric equivalence — when BOTH sides parse as a number
 *      (after stripping commas, spaces, underscores), they compare
 *      by value:  "10.50" == "10.5",  "1,000" == "1000",  "1 000" == "1000".
 *   7. Alphanumeric formatting equivalence — when both sides contain a
 *      digit and are purely [a-z0-9] after removing spaces, the
 *      space-stripped forms must match:
 *          "abc 123"  == "abc123"   →  true
 *          "123 456"  == "123456"   →  true
 *      This rule does NOT trigger for pure-letter text, so
 *          "New York" != "NewYork"
 *      remains untouched.
 *   8. Everything else must match character-for-character after
 *      normalization.  "Java" != "JavaScript",  "TCP" != "UDP",
 *      "123" != "124" → all stay NOT equal.
 *
 * The ORIGINAL strings are never mutated anywhere — the matcher only
 * returns a boolean. UI, PDFs and Firestore keep the student's exact
 * input and the creator's exact correct answer.
 */
object DescriptiveAnswerMatcher {

    private const val EPSILON = 1e-9

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /** Single correct answer comparison. */
    fun areEquivalent(studentAnswer: String?, correctAnswer: String?): Boolean {
        // Rule 1: empty handling
        if (correctAnswer.isNullOrBlank()) return false
        if (studentAnswer.isNullOrBlank()) return false

        val nStudent = normalize(studentAnswer)
        val nCorrect = normalize(correctAnswer)
        if (nStudent.isEmpty() || nCorrect.isEmpty()) return false

        // Rule 2: exact match after basic normalization
        if (nStudent == nCorrect) return true

        // Rule 3: numeric equivalence
        val sNum = toNumber(studentAnswer)
        val cNum = toNumber(correctAnswer)
        if (sNum != null && cNum != null && abs(sNum - cNum) < EPSILON) return true

        // Rule 4: alphanumeric-with-formatting equivalence
        val sNoSpace = nStudent.replace(" ", "")
        val cNoSpace = nCorrect.replace(" ", "")
        if (sNoSpace.isNotEmpty() && sNoSpace == cNoSpace) {
            if (containsDigit(sNoSpace) && isAlphaNumeric(sNoSpace)) return true
        }

        return false
    }

    /**
     * Multiple accepted answers. If the caller only has one correct
     * answer, pass `listOf(correctAnswer)`.
     */
    fun matchesAny(studentAnswer: String?, acceptedAnswers: List<String>): Boolean {
        if (studentAnswer.isNullOrBlank() || acceptedAnswers.isEmpty()) return false
        return acceptedAnswers.any { areEquivalent(studentAnswer, it) }
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Basic normalization pipeline:
     *   trim → NFKC → collapse whitespace → lowercase (Locale.ROOT).
     * Never applied to the stored / displayed strings — only used
     * internally for comparison.
     */
    fun normalize(raw: String?): String {
        if (raw.isNullOrEmpty()) return ""
        var s = raw.trim()
        if (s.isEmpty()) return ""
        s = Normalizer.normalize(s, Normalizer.Form.NFKC)
        s = s.replace(Regex("\\s+"), " ")
        s = s.lowercase(Locale.ROOT)
        return s
    }

    /** True if the raw answer is a pure number (with optional formatting). */
    private fun isNumericLike(raw: String): Boolean {
        val cleaned = stripNumericFormatting(raw)
        if (cleaned.isEmpty()) return false
        return cleaned.matches(Regex("[-+]?\\d+(\\.\\d+)?"))
    }

    private fun toNumber(raw: String): Double? {
        if (!isNumericLike(raw)) return null
        return stripNumericFormatting(raw).toDoubleOrNull()
    }

    private fun stripNumericFormatting(raw: String): String {
        return raw
            .replace(",", "")
            .replace(" ", "")
            .replace("\u00A0", "")   // non-breaking space
            .replace("_", "")
            .replace("'", "")        // thousands separator used in some locales
            .trim()
    }

    private fun containsDigit(s: String): Boolean = s.any { it.isDigit() }

    private fun isAlphaNumeric(s: String): Boolean = s.matches(Regex("[a-z0-9]+"))
}