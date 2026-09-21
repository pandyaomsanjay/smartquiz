package com.smartquiz

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.smartquiz.databinding.ItemPublicQuizCardBinding

class PublicQuizAdapter(
    private var items: List<PublicQuizItem>,
    private val onJoinClick: (PublicQuizItem) -> Unit
) : RecyclerView.Adapter<PublicQuizAdapter.VH>() {

    /**
     * @param participantCount  Real number of users who have joined the quiz.
     * @param userJoinStatus    null  = user has not joined
     *                          "In Progress" = joined but not completed
     *                          "Completed"   = joined and completed
     *                          "Expired"     = joined but quiz expired
     */
    data class PublicQuizItem(
        val quiz: Quiz,
        val creatorName: String,
        val status: QuizLifecycleStatus,
        val participantCount: Int = 0,
        val userJoinStatus: String? = null
    )

    class VH(val b: ItemPublicQuizCardBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemPublicQuizCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val q = item.quiz
        val b = holder.b

        b.tvTitle.text = q.title
        b.tvDescription.text = q.description.ifBlank { "No description" }
        b.tvCreator.text = "By ${item.creatorName}"
        b.tvQuestions.text = "${q.totalQuestions} Qs"
        b.tvMarks.text = "${q.totalQuestions} Marks"
        b.tvTimer.text = when (q.timerType) {
            "WHOLE_QUIZ" -> formatDuration(q.totalTimeSeconds)
            "PER_QUESTION" -> formatDuration(q.timePerQuestionSeconds)
            else -> "No timer"
        }
        b.tvStartTime.text = "Start: ${QuizTimeUtils.formatDateTimeShort(q.startTime)}"
        b.tvDueTime.text = "Due: ${QuizTimeUtils.formatDateTimeShort(q.deadline)}"

        // ---------- Participant count ----------
        val count = item.participantCount
        b.tvParticipants.text = if (count == 1) "1 participant" else "$count participants"

        // ---------- Status chip ----------
        b.tvStatus.text = item.status.label
        b.tvStatus.setBackgroundColor(
            ContextCompat.getColor(holder.itemView.context, item.status.colorRes)
        )

        // ---------- Button label + enabled state ----------
        val (label, enabled) = resolveButtonState(item)
        b.btnJoin.text = label
        b.btnJoin.isEnabled = enabled
        b.btnJoin.alpha = if (enabled) 1f else 0.6f

        // ---------- Click handlers ----------
        b.btnJoin.setOnClickListener {
            if (enabled) onJoinClick(item)
        }
        holder.itemView.setOnClickListener {
            if (enabled) onJoinClick(item)
        }
    }

    /**
     * Decides the button label and enabled state based on:
     *   1. Quiz lifecycle (UPCOMING / LIVE / EXPIRED)
     *   2. The current user's join status on this quiz.
     */
    private fun resolveButtonState(item: PublicQuizItem): Pair<String, Boolean> {
        // Lifecycle takes priority over join state for terminal states
        when (item.status) {
            QuizLifecycleStatus.UPCOMING -> return "Starts Soon" to false
            QuizLifecycleStatus.EXPIRED,
            QuizLifecycleStatus.DELETED -> return "Expired" to false
            QuizLifecycleStatus.COMPLETED -> return "Completed" to false
            QuizLifecycleStatus.LIVE -> { /* fall through to join-state checks */ }
        }

        // User-specific state (LIVE quiz)
        return when (item.userJoinStatus) {
            "Completed" -> "View Result" to true
            "In Progress" -> "Continue" to true
            "Expired" -> "Expired" to false
            else -> "Join / Start" to true   // Not joined yet
        }
    }

    override fun getItemCount() = items.size

    fun updateList(newList: List<PublicQuizItem>) {
        items = newList
        notifyDataSetChanged()
    }
}