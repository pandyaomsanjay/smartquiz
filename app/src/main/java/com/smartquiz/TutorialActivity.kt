package com.smartquiz

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.smartquiz.databinding.ActivityTutorialBinding

class TutorialActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTutorialBinding
    private lateinit var adapter: TutorialSectionAdapter

    /** Source of truth — never mutated after init. */
    private val allSections: List<TutorialSection> by lazy { createTutorialSections() }

    private var currentQuery: String = ""
    private var currentCategory: String = TutorialSection.CATEGORY_ALL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTutorialBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.tutorial_toolbar_title)

        adapter = TutorialSectionAdapter()
        binding.rvTutorial.layoutManager = LinearLayoutManager(this)
        binding.rvTutorial.adapter = adapter

        setupSearch()
        setupCategories()

        // Initial render: everything, no query
        applyFilters()
    }

    // ==================================================================
    // SEARCH
    // ==================================================================
    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentQuery = s?.toString()?.trim()?.lowercase().orEmpty()
                applyFilters()
            }
        })

        // Empty state button: clear BOTH search and category so the user always gets results back
        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.setText("")
            binding.chipGroupCategories.check(R.id.chipAll)
        }
    }

    // ==================================================================
    // CATEGORY CHIPS
    // ==================================================================
    @Suppress("DEPRECATION")
    private fun setupCategories() {
        binding.chipGroupCategories.setOnCheckedChangeListener { _, checkedId ->
            currentCategory = when (checkedId) {
                R.id.chipGettingStarted  -> TutorialSection.CATEGORY_GETTING_STARTED
                R.id.chipTakingQuizzes   -> TutorialSection.CATEGORY_TAKING_QUIZZES
                R.id.chipResults         -> TutorialSection.CATEGORY_RESULTS
                R.id.chipCreating        -> TutorialSection.CATEGORY_CREATING_QUIZZES
                R.id.chipTroubleshooting -> TutorialSection.CATEGORY_TROUBLESHOOTING
                else                     -> TutorialSection.CATEGORY_ALL
            }
            applyFilters()
        }
    }

    // ==================================================================
    // FILTER PIPELINE
    // ==================================================================
    private fun applyFilters() {
        val filtered = if (currentQuery.isEmpty() && currentCategory == TutorialSection.CATEGORY_ALL) {
            allSections
        } else {
            allSections.filter { section ->
                matchesCategory(section) && matchesQuery(section)
            }
        }

        adapter.submitList(filtered)

        val empty = filtered.isEmpty()
        binding.rvTutorial.visibility = if (empty) View.GONE else View.VISIBLE
        binding.layoutEmpty.visibility = if (empty) View.VISIBLE else View.GONE
    }

    private fun matchesCategory(section: TutorialSection): Boolean =
        currentCategory == TutorialSection.CATEGORY_ALL || section.category == currentCategory

    private fun matchesQuery(section: TutorialSection): Boolean {
        if (currentQuery.isEmpty()) return true
        if (section.title.lowercase().contains(currentQuery)) return true
        if (section.description.lowercase().contains(currentQuery)) return true
        return section.bulletPoints.any { it.lowercase().contains(currentQuery) }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    // ==================================================================
    // ALL 42 SECTIONS — content preserved verbatim from original, only
    // the `category =` argument has been added.
    // ==================================================================
    private fun createTutorialSections(): List<TutorialSection> = listOf(

        // ---------------- Getting Started ----------------
        TutorialSection(
            title = "Getting Started",
            description = "Open Smart Quiz Hub, sign in or register, and complete your profile setup.",
            iconRes = R.drawable.ic_home,
            category = TutorialSection.CATEGORY_GETTING_STARTED,
            bulletPoints = listOf(
                "Sign in with your existing account or create a new one.",
                "Set your display name in Profile – it will appear on your quiz participation records.",
                "Explore available quizzes from the Home screen.",
                "Your session is remembered – you stay signed in until you log out."
            )
        ),
        TutorialSection(
            title = "Login, Session & Logout",
            description = "You stay signed in until you explicitly log out.",
            iconRes = R.drawable.ic_profile,
            category = TutorialSection.CATEGORY_GETTING_STARTED,
            bulletPoints = listOf(
                "Closing the app, restarting your phone, or swiping the app away does NOT log you out.",
                "Reopening the app takes you straight to your Home screen.",
                "To log out, tap Logout on the Home screen.",
                "A confirmation dialog will ask: \"Are you sure you want to logout?\"",
                "Tap Logout to end the session, or Cancel to stay signed in.",
                "After logout, you cannot return to Home by pressing the Back button."
            )
        ),
        TutorialSection(
            title = "Setting Up Your Profile",
            description = "Your display name is important for quiz participation and results.",
            iconRes = R.drawable.ic_profile,
            category = TutorialSection.CATEGORY_GETTING_STARTED,
            bulletPoints = listOf(
                "Tap Profile from the Home screen.",
                "Enter your name and optionally upload an avatar.",
                "Save changes – your name will be used in quiz results and statistics."
            )
        ),

        // ---------------- Taking Quizzes ----------------
        TutorialSection(
            title = "Home Screen Overview",
            description = "Your dashboard for performance, quizzes, and quick actions.",
            iconRes = R.drawable.ic_dashboard,
            category = TutorialSection.CATEGORY_TAKING_QUIZZES,
            bulletPoints = listOf(
                "Your Profile card shows your name and daily streak.",
                "Your Performance card shows Quizzes Joined count and Global Standings.",
                "The 'Quizzes Joined' count updates automatically whenever you join a new quiz.",
                "Core Workspace gives you quick access to Create Quiz, Join Quiz, Leaderboard, My Quizzes, Profile, Settings, and Logout.",
                "The Public Quizzes section lists all live public quizzes you can join."
            )
        ),
        TutorialSection(
            title = "Public Quizzes",
            description = "Browse and join public quizzes from the Home screen.",
            iconRes = R.drawable.ic_quiz,
            category = TutorialSection.CATEGORY_TAKING_QUIZZES,
            bulletPoints = listOf(
                "Each public quiz shows: title, description, creator, number of questions, marks, timer, start time, due time, participant count, and status.",
                "Quiz status can be: UPCOMING, LIVE, COMPLETED, or EXPIRED.",
                "Only LIVE quizzes can be joined.",
                "Upcoming quizzes show 'Starts Soon'.",
                "Expired quizzes show 'Expired' and cannot be joined.",
                "Quizzes are automatically archived 24 hours after their due time.",
                "Your historical 'Quizzes Joined' count is not affected by quiz expiry."
            )
        ),
        TutorialSection(
            title = "Finding a Quiz",
            description = "Discover quizzes you can join.",
            iconRes = R.drawable.ic_search,
            category = TutorialSection.CATEGORY_TAKING_QUIZZES,
            bulletPoints = listOf(
                "Public quizzes are listed on the Home screen.",
                "Use the search bar to filter by title, category, or creator.",
                "Private quizzes can be joined using a 6-digit code."
            )
        ),
        TutorialSection(
            title = "Joining a Quiz",
            description = "Enter the quiz code or scan the QR code to join.",
            iconRes = R.drawable.ic_qr_code,
            category = TutorialSection.CATEGORY_TAKING_QUIZZES,
            bulletPoints = listOf(
                "Open Join Quiz from the Home screen.",
                "Enter the 6-digit code or scan the QR code.",
                "Review the quiz information and instructions.",
                "Tap Start Quiz to begin your attempt.",
                "The participant count increases when you join for the first time.",
                "Re-opening a quiz you already joined does NOT increase the count."
            )
        ),
        TutorialSection(
            title = "Quiz Information",
            description = "Before you start, review all details carefully.",
            iconRes = R.drawable.ic_info,
            category = TutorialSection.CATEGORY_TAKING_QUIZZES,
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
            category = TutorialSection.CATEGORY_TAKING_QUIZZES,
            bulletPoints = listOf(
                "Current question and answer options.",
                "Question counter and progress bar.",
                "Timer (if enabled).",
                "Previous, Next, Bookmark, Mark for Review, and Grid buttons.",
                "Submit button at the end.",
                "If the question belongs to a scenario, the scenario context is shown above the question."
            )
        ),
        TutorialSection(
            title = "Scenario Questions",
            description = "Some quizzes contain scenario-based questions with a shared context.",
            iconRes = R.drawable.ic_quiz,
            category = TutorialSection.CATEGORY_TAKING_QUIZZES,
            bulletPoints = listOf(
                "A scenario is a case study or passage followed by multiple related questions.",
                "The scenario itself is NOT a separate question – only the questions inside it count.",
                "Example: Scenario has 3 questions, so it contributes 3 to the total.",
                "The scenario text is shown above each related question during the attempt.",
                "You can answer scenario questions just like normal questions.",
                "Question types inside a scenario can be Radio, Checkbox, or Descriptive."
            )
        ),
        TutorialSection(
            title = "Question Types",
            description = "Smart Quiz Hub supports four question types.",
            iconRes = R.drawable.ic_edit,
            category = TutorialSection.CATEGORY_TAKING_QUIZZES,
            bulletPoints = listOf(
                "Radio (Single Choice): pick exactly one correct option.",
                "Checkbox (Multiple Choice): pick one or more correct options.",
                "Descriptive (Text Answer): type your answer in a text field.",
                "Scenario: a case study with multiple sub-questions of the above types."
            )
        ),
        TutorialSection(
            title = "Descriptive Answers",
            description = "Type your answer in the multi-line input field.",
            iconRes = R.drawable.ic_edit,
            category = TutorialSection.CATEGORY_TAKING_QUIZZES,
            bulletPoints = listOf(
                "Tap the answer field and type your response.",
                "The field supports multiple lines and long answers.",
                "The app automatically ignores harmless formatting differences:",
                "   • Leading/trailing spaces are ignored.",
                "   • Multiple spaces are treated as one.",
                "   • Capital and small letters are treated the same.",
                "   • '123456' is treated as the same answer as '123 456'.",
                "   • '1000' is treated as the same answer as '1,000'.",
                "   • '10.5' is treated as the same answer as '10.50'.",
                "Incorrect answers remain incorrect – 'Java' is NOT the same as 'JavaScript'."
            )
        ),
        TutorialSection(
            title = "Answering Questions",
            description = "Select or type your answer and move on.",
            iconRes = R.drawable.ic_edit,
            category = TutorialSection.CATEGORY_TAKING_QUIZZES,
            bulletPoints = listOf(
                "Read the question carefully.",
                "Tap your chosen answer (Radio / Checkbox).",
                "Type your answer in the text field (Descriptive).",
                "Answers are automatically saved.",
                "Use Next to move forward or Previous to go back."
            )
        ),
        TutorialSection(
            title = "Question Navigation",
            description = "Move between questions easily.",
            iconRes = R.drawable.ic_navigation,
            category = TutorialSection.CATEGORY_TAKING_QUIZZES,
            bulletPoints = listOf(
                "Next: goes to the next question.",
                "Previous: returns to the previous question.",
                "Question Grid: jump directly to any question.",
                "Navigation preserves your answers and states.",
                "Scenario sub-questions are counted in the progress just like normal questions."
            )
        ),
        TutorialSection(
            title = "Question Status Indicators",
            description = "Each question shows its current state.",
            iconRes = R.drawable.ic_status,
            category = TutorialSection.CATEGORY_TAKING_QUIZZES,
            bulletPoints = listOf(
                "Answered – you have selected/typed an answer.",
                "Unanswered – no answer provided yet.",
                "Marked for Review – you want to revisit this question.",
                "Bookmarked – saved for later reference.",
                "Locked – cannot be edited (timer expired or quiz submitted)."
            )
        ),
        TutorialSection(
            title = "Bookmarking Questions",
            description = "Save questions you want to revisit later.",
            iconRes = R.drawable.ic_bookmark,
            category = TutorialSection.CATEGORY_TAKING_QUIZZES,
            bulletPoints = listOf(
                "Tap the Bookmark button on a question to bookmark it.",
                "Tap again to remove the bookmark.",
                "Bookmarks help you quickly identify important questions.",
                "Works for both normal and scenario sub-questions."
            )
        ),
        TutorialSection(
            title = "Mark for Review",
            description = "Flag questions you are unsure about.",
            iconRes = R.drawable.ic_review,
            category = TutorialSection.CATEGORY_TAKING_QUIZZES,
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
            category = TutorialSection.CATEGORY_TAKING_QUIZZES,
            bulletPoints = listOf(
                "Shows answered count, total questions, and percentage.",
                "Updates instantly as you answer questions.",
                "Helps you see how much is remaining.",
                "Scenario containers are NOT counted – only the questions inside them."
            )
        ),
        TutorialSection(
            title = "Quiz Timer",
            description = "Timers help manage your time during the quiz.",
            iconRes = R.drawable.ic_timer,
            category = TutorialSection.CATEGORY_TAKING_QUIZZES,
            bulletPoints = listOf(
                "No Timer – no countdown; submit manually.",
                "Whole Quiz Timer – single timer for the entire quiz.",
                "Per-Question Timer – each question has its own timer.",
                "Timer shows remaining time in HH:MM:SS format.",
                "Scenario sub-questions share the same timer configuration as normal questions."
            )
        ),
        TutorialSection(
            title = "Timer Expiration",
            description = "What happens when time runs out.",
            iconRes = R.drawable.ic_warning,
            category = TutorialSection.CATEGORY_TAKING_QUIZZES,
            bulletPoints = listOf(
                "Whole quiz expires → auto-submits the quiz.",
                "Individual question expires → that question becomes locked.",
                "Locked questions cannot be edited.",
                "Manage your time to avoid unnecessary auto-submissions."
            )
        ),
        TutorialSection(
            title = "Auto-Save & Recovery",
            description = "Your progress is preserved automatically.",
            iconRes = R.drawable.ic_save,
            category = TutorialSection.CATEGORY_TAKING_QUIZZES,
            bulletPoints = listOf(
                "Answers are saved as you select/type them.",
                "Navigation state, bookmarks, and review marks are preserved.",
                "If the app is closed or recreated, your attempt can be resumed.",
                "Descriptive answers are stored exactly as you typed them.",
                "Works with an internet connection; offline support is available."
            )
        ),
        TutorialSection(
            title = "Internet Interruption",
            description = "What happens if your connection drops.",
            iconRes = R.drawable.ic_wifi,
            category = TutorialSection.CATEGORY_TAKING_QUIZZES,
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
            category = TutorialSection.CATEGORY_TAKING_QUIZZES,
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
            category = TutorialSection.CATEGORY_TAKING_QUIZZES,
            bulletPoints = listOf(
                "Warning 1/3 – first reminder to stay on the quiz screen.",
                "Warning 2/3 – final warning.",
                "Warning 3/3 – the quiz may be automatically submitted.",
                "Stay on the quiz screen until you finish.",
                "These warnings apply equally to scenario sub-questions."
            )
        ),
        TutorialSection(
            title = "Negative Marking",
            description = "Some quizzes may deduct marks for wrong answers.",
            iconRes = R.drawable.ic_negative_marking,
            category = TutorialSection.CATEGORY_TAKING_QUIZZES,
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
            category = TutorialSection.CATEGORY_TAKING_QUIZZES,
            bulletPoints = listOf(
                "Statuses: Available, Not Started, In Progress, Completed, Expired, Closed.",
                "You cannot start or continue an expired/closed quiz.",
                "Expired public quizzes are archived 24 hours after their due time.",
                "Your historical results and Quizzes Joined count are preserved."
            )
        ),

        // ---------------- Results ----------------
        TutorialSection(
            title = "Submitting a Quiz",
            description = "Final review before submission.",
            iconRes = R.drawable.ic_submit,
            category = TutorialSection.CATEGORY_RESULTS,
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
            category = TutorialSection.CATEGORY_RESULTS,
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
            category = TutorialSection.CATEGORY_RESULTS,
            bulletPoints = listOf(
                "Quiz name, score, total marks, and percentage.",
                "Time taken and submission status.",
                "Status: Completed, Time Expired, or Automatically Submitted.",
                "If auto-submitted due to warnings, the reason is shown.",
                "Descriptive answers use smart matching – harmless formatting differences do not count against you."
            )
        ),
        TutorialSection(
            title = "Quiz History",
            description = "Review your past attempts.",
            iconRes = R.drawable.ic_history,
            category = TutorialSection.CATEGORY_RESULTS,
            bulletPoints = listOf(
                "Access from the Home screen or your profile.",
                "Shows quiz name, score, percentage, duration, and status.",
                "Completed attempts cannot be reopened for editing.",
                "Your Quizzes Joined count includes both public and private quizzes."
            )
        ),
        TutorialSection(
            title = "User Bookmarks",
            description = "Access your bookmarked questions or quizzes.",
            iconRes = R.drawable.ic_bookmark,
            category = TutorialSection.CATEGORY_RESULTS,
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
            category = TutorialSection.CATEGORY_RESULTS,
            bulletPoints = listOf(
                "Total attempts, average score, highest/lowest scores.",
                "Average percentage and completion information.",
                "Attempt duration and trends.",
                "This is your personal data, not shared with others."
            )
        ),
        TutorialSection(
            title = "Leaderboard",
            description = "See how you rank against other participants.",
            iconRes = R.drawable.ic_leaderboard,
            category = TutorialSection.CATEGORY_RESULTS,
            bulletPoints = listOf(
                "Tap Leaderboard from the Home screen.",
                "See your rank, score, and total participants per quiz.",
                "If the creator hid scores, only participation is shown.",
                "Tap 'View Leaderboard' on any quiz to see the full ranking."
            )
        ),

        // ---------------- Creating Quizzes ----------------
        TutorialSection(
            title = "Creating a Quiz",
            description = "Any user can create and manage their own quizzes.",
            iconRes = R.drawable.ic_add,
            category = TutorialSection.CATEGORY_CREATING_QUIZZES,
            bulletPoints = listOf(
                "Tap Create Quiz from the Home screen.",
                "Enter the title, description, and Total Questions (required).",
                "Set visibility (Public or Private).",
                "Choose the timer type (None, Whole Quiz, or Per Question).",
                "Configure randomization and negative marking (optional).",
                "Set start time and deadline (optional).",
                "Add questions using '+ Add Question'."
            )
        ),
        TutorialSection(
            title = "Total Questions Configuration",
            description = "Set the exact number of questions for your quiz.",
            iconRes = R.drawable.ic_edit,
            category = TutorialSection.CATEGORY_CREATING_QUIZZES,
            bulletPoints = listOf(
                "Enter the required total number of questions.",
                "Scenario sub-questions count toward this total.",
                "The scenario container itself is NOT counted.",
                "Example: Scenario (3 questions) + Normal (2 questions) = 5 total.",
                "The 'Questions added: X / Y required' label updates live.",
                "You cannot publish until the actual count matches the configured total."
            )
        ),
        TutorialSection(
            title = "Creating Scenario Questions",
            description = "Group related questions under a shared scenario.",
            iconRes = R.drawable.ic_quiz,
            category = TutorialSection.CATEGORY_CREATING_QUIZZES,
            bulletPoints = listOf(
                "In Add Question, select 'Scenario (Case + Sub-questions)' as the type.",
                "Enter the scenario text or case study in the dialog.",
                "Tap '+ Add Question' to add sub-questions.",
                "Each sub-question can be Radio, Checkbox, or Descriptive.",
                "You can edit, remove, and reorder sub-questions.",
                "The scenario contributes its sub-question count to the quiz total.",
                "Multiple scenarios can be added to a single quiz.",
                "Scenarios and normal questions can be mixed in any order."
            )
        ),
        TutorialSection(
            title = "Saving as Draft vs Publishing",
            description = "Two ways to save your quiz.",
            iconRes = R.drawable.ic_save,
            category = TutorialSection.CATEGORY_CREATING_QUIZZES,
            bulletPoints = listOf(
                "Save as Draft – keeps the quiz private while you work on it.",
                "Drafts can be edited later from My Quizzes → Draft Quizzes.",
                "Save Quiz (Publish) – validates all fields and publishes the quiz.",
                "Publishing requires the actual question count to match the configured total.",
                "Scenarios work in drafts exactly like in published quizzes."
            )
        ),
        TutorialSection(
            title = "Managing Drafts",
            description = "Edit or delete your drafts before publishing.",
            iconRes = R.drawable.ic_edit,
            category = TutorialSection.CATEGORY_CREATING_QUIZZES,
            bulletPoints = listOf(
                "Open My Quizzes → Draft Quizzes.",
                "Tap Edit to reopen the draft in the creation screen.",
                "Tap Delete to permanently remove the draft and its questions.",
                "Tap Download PDF to export a draft question paper.",
                "Publishing a draft runs the same validation as a new quiz."
            )
        ),
        TutorialSection(
            title = "Creator Analytics",
            description = "Track how participants are performing.",
            iconRes = R.drawable.ic_analytics,
            category = TutorialSection.CATEGORY_CREATING_QUIZZES,
            bulletPoints = listOf(
                "Open My Quizzes and tap a quiz to see its statistics.",
                "See participant count, completion rate, highest/lowest scores.",
                "View the most incorrect question.",
                "Filter by date range (Today, Last 7 Days, Last 30 Days, Custom).",
                "Export data as CSV or PDF."
            )
        ),
        TutorialSection(
            title = "Downloading Question Papers",
            description = "Export a printable PDF of your quiz.",
            iconRes = R.drawable.ic_download,
            category = TutorialSection.CATEGORY_CREATING_QUIZZES,
            bulletPoints = listOf(
                "Tap Download Question Paper in the quiz statistics screen.",
                "Choose 'Question Paper Only' or 'Question Paper with Answers'.",
                "Scenario text is shown as a header; sub-questions are numbered normally.",
                "The PDF can be shared via any installed app."
            )
        ),

        // ---------------- Troubleshooting ----------------
        TutorialSection(
            title = "Troubleshooting",
            description = "Common issues and their solutions.",
            iconRes = R.drawable.ic_help,
            category = TutorialSection.CATEGORY_TROUBLESHOOTING,
            bulletPoints = listOf(
                "Quiz code not working: check code, availability, and internet.",
                "Quiz not loading: retry, check connection, reopen.",
                "Answer not appearing: wait for sync or check connection.",
                "Unexpected submission: check result screen for reason.",
                "Cannot edit a question: it may be locked (timer expired or submitted).",
                "Descriptive answer marked wrong: check for genuinely different content, not just spacing."
            )
        ),
        TutorialSection(
            title = "Important User Tips",
            description = "Best practices for a smooth quiz experience.",
            iconRes = R.drawable.ic_tips,
            category = TutorialSection.CATEGORY_TROUBLESHOOTING,
            bulletPoints = listOf(
                "Read all instructions before starting.",
                "Check the timer and deadline.",
                "Keep a stable internet connection.",
                "Stay on the quiz screen.",
                "Review unanswered and marked questions.",
                "Use Mark for Review and Bookmark wisely.",
                "Do not wait until the last second to submit.",
                "Verify answers before final submission.",
                "For descriptive answers, focus on the actual content – formatting differences are handled automatically."
            )
        )
    )
}