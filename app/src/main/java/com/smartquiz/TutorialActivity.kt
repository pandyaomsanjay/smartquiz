package com.smartquiz

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.smartquiz.databinding.ActivityTutorialBinding

class TutorialActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTutorialBinding
    private lateinit var adapter: TutorialSectionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTutorialBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "How to Use Smart Quiz Hub"

        val sections = createTutorialSections()
        adapter = TutorialSectionAdapter(sections)

        binding.rvTutorial.layoutManager = LinearLayoutManager(this)
        binding.rvTutorial.adapter = adapter
    }

    private fun createTutorialSections(): List<TutorialSection> {
        return listOf(
            TutorialSection(
                title = "Getting Started",
                description = "Open Smart Quiz Hub, sign in or register, and complete your profile setup.",
                iconRes = R.drawable.ic_home,
                bulletPoints = listOf(
                    "Sign in with your existing account or create a new one.",
                    "Set your display name in Profile – it will appear on your quiz participation records.",
                    "Explore available quizzes from the home screen."
                )
            ),
            TutorialSection(
                title = "Setting Up Your Profile",
                description = "Your display name is important for quiz participation and results.",
                iconRes = R.drawable.ic_profile,
                bulletPoints = listOf(
                    "Tap Profile from the home screen.",
                    "Enter your name and optionally upload an avatar.",
                    "Save changes – your name will be used in quiz results and statistics."
                )
            ),
            TutorialSection(
                title = "Finding a Quiz",
                description = "Discover quizzes you can join.",
                iconRes = R.drawable.ic_search,
                bulletPoints = listOf(
                    "Public quizzes are listed on the home screen.",
                    "Use the search bar to filter by title, category, or creator.",
                    "Private quizzes can be joined using a 6‑digit code."
                )
            ),
            TutorialSection(
                title = "Joining a Quiz",
                description = "Enter the quiz code or scan the QR code to join.",
                iconRes = R.drawable.ic_qr_code,
                bulletPoints = listOf(
                    "Open Join Quiz from the home screen.",
                    "Enter the 6‑digit code or scan the QR code.",
                    "Review the quiz information and instructions.",
                    "Tap Start Quiz to begin your attempt."
                )
            ),
            TutorialSection(
                title = "Quiz Information",
                description = "Before you start, review all details carefully.",
                iconRes = R.drawable.ic_info,
                bulletPoints = listOf(
                    "Quiz title, creator name, and description.",
                    "Number of questions and total marks.",
                    "Timer mode (No Timer, Whole Quiz, or Per Question).",
                    "Deadline and negative marking rules (if any).",
                    "Instructions and rules for the attempt."
                )
            ),
            TutorialSection(
                title = "Understanding the Quiz Screen",
                description = "The main quiz interface gives you all the tools you need.",
                iconRes = R.drawable.ic_quiz,
                bulletPoints = listOf(
                    "Current question and answer options.",
                    "Question counter and progress bar.",
                    "Timer (if enabled).",
                    "Previous, Next, Bookmark, Mark for Review, and Grid buttons.",
                    "Submit button at the end."
                )
            ),
            TutorialSection(
                title = "Answering Questions",
                description = "Select your answer and move on.",
                iconRes = R.drawable.ic_edit,
                bulletPoints = listOf(
                    "Read the question carefully.",
                    "Tap your chosen answer.",
                    "Answers are automatically saved.",
                    "Use Next to move forward or Previous to go back."
                )
            ),
            TutorialSection(
                title = "Question Navigation",
                description = "Move between questions easily.",
                iconRes = R.drawable.ic_navigation,
                bulletPoints = listOf(
                    "Next: goes to the next question.",
                    "Previous: returns to the previous question.",
                    "Question Grid: jump directly to any question.",
                    "Navigation preserves your answers and states."
                )
            ),
            TutorialSection(
                title = "Question Status Indicators",
                description = "Each question shows its current state.",
                iconRes = R.drawable.ic_status,
                bulletPoints = listOf(
                    "Answered – you have selected an answer.",
                    "Unanswered – no answer selected yet.",
                    "Marked for Review – you want to revisit this question.",
                    "Bookmarked – saved for later reference.",
                    "Locked – cannot be edited (timer expired or quiz submitted)."
                )
            ),
            TutorialSection(
                title = "Bookmarking Questions",
                description = "Save questions you want to revisit later.",
                iconRes = R.drawable.ic_bookmark,
                bulletPoints = listOf(
                    "Tap the Bookmark button on a question to bookmark it.",
                    "Tap again to remove the bookmark.",
                    "Bookmarks help you quickly identify important questions."
                )
            ),
            TutorialSection(
                title = "Mark for Review",
                description = "Flag questions you are unsure about.",
                iconRes = R.drawable.ic_review,
                bulletPoints = listOf(
                    "Tap Mark for Review on a question.",
                    "The question will be highlighted in the grid.",
                    "You can unmark it later if you change your mind."
                )
            ),
            TutorialSection(
                title = "Progress Indicator",
                description = "Track your progress through the quiz.",
                iconRes = R.drawable.ic_dashboard,
                bulletPoints = listOf(
                    "Shows answered count, total questions, and percentage.",
                    "Updates instantly as you answer questions.",
                    "Helps you see how much is remaining."
                )
            ),
            TutorialSection(
                title = "Quiz Timer",
                description = "Timers help manage your time during the quiz.",
                iconRes = R.drawable.ic_timer,
                bulletPoints = listOf(
                    "No Timer – no countdown; submit manually.",
                    "Whole Quiz Timer – single timer for the entire quiz.",
                    "Per‑Question Timer – each question has its own timer.",
                    "Timer shows remaining time in HH:MM:SS format."
                )
            ),
            TutorialSection(
                title = "Timer Expiration",
                description = "What happens when time runs out.",
                iconRes = R.drawable.ic_warning,
                bulletPoints = listOf(
                    "Whole quiz expires → auto‑submits the quiz.",
                    "Individual question expires → that question becomes locked.",
                    "Locked questions cannot be edited.",
                    "Manage your time to avoid unnecessary auto‑submissions."
                )
            ),
            TutorialSection(
                title = "Auto‑Save & Recovery",
                description = "Your progress is preserved automatically.",
                iconRes = R.drawable.ic_save,
                bulletPoints = listOf(
                    "Answers are saved as you select them.",
                    "Navigation state, bookmarks, and review marks are preserved.",
                    "If the app is closed or recreated, your attempt can be resumed.",
                    "Works with an internet connection; offline support is available."
                )
            ),
            TutorialSection(
                title = "Internet Interruption",
                description = "What happens if your connection drops.",
                iconRes = R.drawable.ic_wifi,
                bulletPoints = listOf(
                    "The app continues using locally saved data.",
                    "When connection returns, changes are synced automatically.",
                    "Status: Offline → Syncing → Synced.",
                    "Do not intentionally disconnect your internet during an attempt."
                )
            ),
            TutorialSection(
                title = "Activity Recreation / App Reopen",
                description = "Your attempt state is preserved when reopening the app.",
                iconRes = R.drawable.ic_refresh,
                bulletPoints = listOf(
                    "Current question, answers, timer state, bookmarks, and review marks are restored.",
                    "The attempt continues from where you left off.",
                    "No data is lost if the app is killed and reopened."
                )
            ),
            TutorialSection(
                title = "Suspicious Activity Warnings",
                description = "The app may warn you if you leave the quiz screen.",
                iconRes = R.drawable.ic_warning,
                bulletPoints = listOf(
                    "Warning 1/3 – first reminder to stay on the quiz screen.",
                    "Warning 2/3 – final warning.",
                    "Warning 3/3 – the quiz may be automatically submitted.",
                    "Stay on the quiz screen until you finish."
                )
            ),
            TutorialSection(
                title = "Submitting a Quiz",
                description = "Final review before submission.",
                iconRes = R.drawable.ic_submit,
                bulletPoints = listOf(
                    "Review all answered, unanswered, and marked questions.",
                    "Check remaining time.",
                    "Tap Submit Quiz and confirm.",
                    "Unanswered questions will be submitted as they are."
                )
            ),
            TutorialSection(
                title = "Automatic Submission",
                description = "The quiz may submit without your action.",
                iconRes = R.drawable.ic_auto_submit,
                bulletPoints = listOf(
                    "When the whole quiz timer expires.",
                    "When suspicious activity reaches the limit.",
                    "After submission, you cannot edit answers."
                )
            ),
            TutorialSection(
                title = "Quiz Results",
                description = "See your performance after submission.",
                iconRes = R.drawable.ic_score,
                bulletPoints = listOf(
                    "Quiz name, score, total marks, and percentage.",
                    "Time taken and submission status.",
                    "Status: Completed, Time Expired, or Automatically Submitted.",
                    "If auto‑submitted due to warnings, the reason is shown."
                )
            ),
            TutorialSection(
                title = "Quiz History",
                description = "Review your past attempts.",
                iconRes = R.drawable.ic_history,
                bulletPoints = listOf(
                    "Access from the home screen or your profile.",
                    "Shows quiz name, score, percentage, duration, and status.",
                    "Completed attempts cannot be reopened for editing."
                )
            ),
            TutorialSection(
                title = "User Bookmarks",
                description = "Access your bookmarked questions or quizzes.",
                iconRes = R.drawable.ic_bookmark,
                bulletPoints = listOf(
                    "Bookmarks are saved for your account.",
                    "You can view and remove bookmarks.",
                    "Bookmarks are private to you."
                )
            ),
            TutorialSection(
                title = "User Quiz Statistics",
                description = "View your own performance metrics.",
                iconRes = R.drawable.ic_analytics,
                bulletPoints = listOf(
                    "Total attempts, average score, highest/lowest scores.",
                    "Average percentage and completion information.",
                    "Attempt duration and trends.",
                    "This is your personal data, not shared with others."
                )
            ),
            TutorialSection(
                title = "Negative Marking",
                description = "Some quizzes may deduct marks for wrong answers.",
                iconRes = R.drawable.ic_negative_marking,
                bulletPoints = listOf(
                    "Check the Quiz Information screen before starting.",
                    "The deduction value (e.g., 0.25 marks) is shown.",
                    "Read instructions carefully to avoid surprises."
                )
            ),
            TutorialSection(
                title = "Deadline and Quiz Status",
                description = "Quizzes may have availability windows.",
                iconRes = R.drawable.ic_calendar,
                bulletPoints = listOf(
                    "Statuses: Available, Not Started, In Progress, Completed, Expired, Closed.",
                    "You cannot start or continue an expired/closed quiz.",
                    "Check the deadline before joining."
                )
            ),
            TutorialSection(
                title = "Troubleshooting",
                description = "Common issues and their solutions.",
                iconRes = R.drawable.ic_help,
                bulletPoints = listOf(
                    "Quiz code not working: check code, availability, and internet.",
                    "Quiz not loading: retry, check connection, reopen.",
                    "Answer not appearing: wait for sync or check connection.",
                    "Unexpected submission: check result screen for reason.",
                    "Cannot edit a question: it may be locked (timer expired or submitted)."
                )
            ),
            TutorialSection(
                title = "Important User Tips",
                description = "Best practices for a smooth quiz experience.",
                iconRes = R.drawable.ic_tips,
                bulletPoints = listOf(
                    "Read all instructions before starting.",
                    "Check the timer and deadline.",
                    "Keep a stable internet connection.",
                    "Stay on the quiz screen.",
                    "Review unanswered and marked questions.",
                    "Use Mark for Review and Bookmark wisely.",
                    "Do not wait until the last second to submit.",
                    "Verify answers before final submission."
                )
            )
        )
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}