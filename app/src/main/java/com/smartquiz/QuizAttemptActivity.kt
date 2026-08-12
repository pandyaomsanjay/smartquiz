package com.smartquiz

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.smartquiz.databinding.ActivityQuizAttemptBinding
import com.smartquiz.databinding.DialogQuestionGridBinding
import com.smartquiz.models.JoinedQuiz
import android.view.ViewGroup
import kotlin.random.Random

class QuizAttemptActivity : AppCompatActivity() {
    private lateinit var binding: ActivityQuizAttemptBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var quiz: Quiz
    private var questions = mutableListOf<Question>()
    private var shuffledQuestions = mutableListOf<Question>()
    private var currentIndex = 0
    private var score = 0.0
    private var quizId = ""
    private var quizTitle = ""
    private var creatorId = ""
    private var isSubmitted = false
    private val userAnswers = mutableMapOf<String, Any>()
    private var startTime = 0L
    private val sharedPrefs by lazy { getSharedPreferences("quiz_answers", MODE_PRIVATE) }
    private val cheatPrefs by lazy { getSharedPreferences("cheat_warnings", MODE_PRIVATE) }
    private var mediaPlayer: MediaPlayer? = null
    private val TAG = "QuizAttempt"

    // ---------- RANDOMIZATION ----------
    private val questionOrder = mutableListOf<String>()
    private val optionOrderMap = mutableMapOf<String, List<Int>>()
    private var randomizationLoaded = false

    // ---------- STATE ----------
    private val questionStateMap = mutableMapOf<String, QuestionState>()
    private val questionStatesList = mutableListOf<QuestionState>()
    private var gridDialog: AlertDialog? = null
    private lateinit var gridAdapter: QuestionGridAdapter

    // Auto-save debounce
    private val saveHandler = Handler(Looper.getMainLooper())
    private var saveRunnable: Runnable? = null
    private val SAVE_DEBOUNCE_MS = 500L

    // Cheat logging flags
    private var isInForeground = true
    private var userLeftViaSystem = false
    private var isDialogShowing = false

    // Timer state
    private var timerType: String = "NONE"
    private var totalTimeSeconds: Long = 0
    private var timePerQuestionSeconds: Long = 0
    private var quizEndTime: Long = 0L

    // TimerManager
    private lateinit var timerManager: TimerManager
    private var isQuizExpired = false
    private var isQuizSubmitted = false
    private var wholeQuizRemainingSeconds: Long = -1

    // Cheat detection
    private var violationCount = 0
    private var lastViolationTime = 0L
    private val DEBOUNCE_MS = 500L

    // Restored per‑question timer values from Firestore
    private var perQuestionRemainingMap = mutableMapOf<String, Long>()

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            isInForeground = false
            if (!userLeftViaSystem) {
                handleCheatEvent("APP_BACKGROUND")
            }
            userLeftViaSystem = false
            if (::timerManager.isInitialized) {
                timerManager.pauseTimer()
            }
            saveCurrentState()
            saveTimerStateToFirestore()
        }

        override fun onStart(owner: LifecycleOwner) {
            isInForeground = true
            if (::timerManager.isInitialized && !isQuizExpired && !isQuizSubmitted) {
                timerManager.resumeTimer()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuizAttemptBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)

        setSupportActionBar(binding.toolbar)
        quizId = intent.getStringExtra("quizId") ?: ""
        quizTitle = intent.getStringExtra("quizTitle") ?: "Quiz"
        creatorId = intent.getStringExtra("creatorId") ?: ""
        supportActionBar?.title = quizTitle

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        violationCount = getViolationCount()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleCheatEvent("BACK_BUTTON")
                if (!isQuizSubmitted) safeFinish()
            }
        })

        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)

        if (savedInstanceState != null) {
            timerType = savedInstanceState.getString("timerType", "NONE") ?: "NONE"
            quizEndTime = savedInstanceState.getLong("quizEndTime", 0L)
            currentIndex = savedInstanceState.getInt("currentIndex", 0)
            isQuizExpired = savedInstanceState.getBoolean("isQuizExpired", false)
            isQuizSubmitted = savedInstanceState.getBoolean("isQuizSubmitted", false)
            lastViolationTime = savedInstanceState.getLong("lastViolationTime", 0L)
            wholeQuizRemainingSeconds = savedInstanceState.getLong("wholeQuizRemaining", -1)
            restoreFullState(savedInstanceState)
        }

        loadQuizAndQuestions()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("timerType", timerType)
        outState.putLong("quizEndTime", quizEndTime)
        outState.putInt("currentIndex", currentIndex)
        outState.putBoolean("isQuizExpired", isQuizExpired)
        outState.putBoolean("isQuizSubmitted", isQuizSubmitted)
        outState.putLong("lastViolationTime", lastViolationTime)
        if (::timerManager.isInitialized && timerManager.isWholeQuizActive()) {
            outState.putLong("wholeQuizRemaining", timerManager.getWholeQuizRemaining())
        } else {
            outState.putLong("wholeQuizRemaining", -1)
        }
        saveFullStateToBundle(outState)
    }

    override fun onResume() {
        super.onResume()
        if (isQuizSubmitted) {
            disableAnswerControls()
            return
        }
        if (isQuizExpired) {
            disableAnswerControls()
            return
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        userLeftViaSystem = true
        handleCheatEvent("HOME_OR_RECENTS")
        saveCurrentState()
        saveTimerStateToFirestore()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!isDialogShowing && !hasFocus && isInForeground && !isQuizSubmitted) {
            handleCheatEvent("FOCUS_LOST")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        timerManager.cancel()
        mediaPlayer?.release()
        saveHandler.removeCallbacksAndMessages(null)
    }

    private fun safeFinish() {
        if (!isFinishing && !isDestroyed) finish()
    }

    // ---------- CHEAT HANDLING ----------
    private fun handleCheatEvent(eventType: String) {
        if (isQuizSubmitted) return
        val now = System.currentTimeMillis()
        if (now - lastViolationTime < DEBOUNCE_MS) {
            Log.d(TAG, "Debounced duplicate cheat event: $eventType")
            return
        }
        lastViolationTime = now
        violationCount = incrementViolationCount()
        logCheatEvent(eventType, violationCount)
        saveViolationCountToFirestore()
        when (violationCount) {
            1 -> showWarning1()
            2 -> showWarning2()
            3 -> {
                showViolationWarning("Maximum suspicious activity limit reached. Your quiz has been automatically submitted.")
                timerManager.cancel()
                submitQuizWithReason("THREE_CHEAT_WARNINGS")
            }
        }
    }

    private fun showWarning1() {
        AlertDialog.Builder(this)
            .setTitle("WARNING 1/3")
            .setMessage("Suspicious activity detected.\nPlease remain on the quiz screen.\nFurther violations may automatically submit your quiz.")
            .setPositiveButton("Continue Attempt") { _, _ -> }
            .setCancelable(false)
            .show()
    }

    private fun showWarning2() {
        AlertDialog.Builder(this)
            .setTitle("WARNING 2/3")
            .setMessage("This is your final warning.\nOne more suspicious activity will automatically submit your quiz.")
            .setPositiveButton("Continue Attempt") { _, _ -> }
            .setCancelable(false)
            .show()
    }

    private fun showViolationWarning(message: String) {
        runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    }

    private fun logCheatEvent(eventType: String, violationNumber: Int) {
        val userId = auth.currentUser?.uid ?: return
        val userName = auth.currentUser?.displayName ?: ""
        val email = auth.currentUser?.email ?: ""
        val currentCreatorId = if (creatorId.isNotEmpty()) creatorId
        else if (::quiz.isInitialized) quiz.creatorId
        else ""

        CheatLogger.logCheatEvent(
            context = applicationContext,
            eventType = eventType,
            quizId = quizId,
            quizTitle = quizTitle,
            creatorId = currentCreatorId,
            userId = userId,
            userName = userName,
            email = email
        )
    }

    // ---------- PERSISTENCE HELPERS ----------
    private fun getViolationCount(): Int {
        val userId = auth.currentUser?.uid ?: return 0
        val key = "${quizId}_$userId"
        return cheatPrefs.getInt(key, 0)
    }

    private fun incrementViolationCount(): Int {
        val userId = auth.currentUser?.uid ?: return 0
        val key = "${quizId}_$userId"
        val current = cheatPrefs.getInt(key, 0)
        val newCount = current + 1
        cheatPrefs.edit().putInt(key, newCount).apply()
        return newCount
    }

    private fun clearViolationCount() {
        val userId = auth.currentUser?.uid ?: return
        val key = "${quizId}_$userId"
        cheatPrefs.edit().remove(key).apply()
    }

    private fun saveViolationCountToFirestore() {
        val userId = auth.currentUser?.uid ?: return
        val attemptRef = db.collection("quizzes").document(quizId)
            .collection("attempts").document(userId)
        attemptRef.update("violationCount", violationCount)
            .addOnFailureListener { e -> Log.e(TAG, "Failed to save violation count: ${e.message}") }
    }

    // ---------- HELPER: enable/disable controls ----------
    private fun disableAnswerControls() {
        setChildrenEnabled(binding.radioGroupOptions, false)
    }

    private fun enableAnswerControls() {
        setChildrenEnabled(binding.radioGroupOptions, true)
    }

    private fun setChildrenEnabled(viewGroup: ViewGroup, enabled: Boolean) {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            when (child) {
                is ViewGroup -> setChildrenEnabled(child, enabled)
                is RadioButton -> child.isEnabled = enabled
                is CheckBox -> child.isEnabled = enabled
                is EditText -> {
                    child.isEnabled = enabled
                    if (!enabled) {
                        child.isFocusable = false
                        child.isFocusableInTouchMode = false
                    } else {
                        child.isFocusable = true
                        child.isFocusableInTouchMode = true
                    }
                }
                else -> child.isEnabled = enabled
            }
        }
    }

    // ---------- STATE PERSISTENCE ----------
    private fun saveFullStateToBundle(bundle: Bundle) {
        bundle.putSerializable("savedAnswers", HashMap(userAnswers))
        val stateList = questionStateMap.values.toList()
        bundle.putSerializable("questionStates", ArrayList(stateList))
    }

    private fun restoreFullState(savedInstanceState: Bundle) {
        val savedAnswers = savedInstanceState.getSerializable("savedAnswers") as? HashMap<String, Any>
        if (savedAnswers != null) {
            userAnswers.clear()
            userAnswers.putAll(savedAnswers)
        }
        val savedStates = savedInstanceState.getSerializable("questionStates") as? List<QuestionState>
        if (savedStates != null) {
            questionStateMap.clear()
            savedStates.forEach { questionStateMap[it.questionId] = it }
            questionStatesList.clear()
            questionStatesList.addAll(savedStates)
        }
    }

    // ---------- AUTO-SAVE ----------
    private fun scheduleAutoSave() {
        saveRunnable?.let { saveHandler.removeCallbacks(it) }
        saveRunnable = Runnable {
            saveCurrentState()
            saveTimerStateToFirestore()
        }
        saveHandler.postDelayed(saveRunnable!!, SAVE_DEBOUNCE_MS)
    }

    private fun saveCurrentState() {
        val userId = auth.currentUser?.uid ?: return
        val prefs = getSharedPreferences("quiz_state_$quizId", MODE_PRIVATE)
        val editor = prefs.edit()
        questionStateMap.forEach { (qId, state) ->
            editor.putBoolean("answered_$qId", state.isAnswered)
            editor.putBoolean("review_$qId", state.isMarkedForReview)
            editor.putBoolean("bookmark_$qId", state.isBookmarked)
            editor.putBoolean("locked_$qId", state.isLocked)
        }
        editor.putInt("currentIndex", currentIndex)
        editor.apply()
        syncWithFirestore()
    }

    private fun syncWithFirestore() {
        val userId = auth.currentUser?.uid ?: return
        val attemptRef = db.collection("quizzes").document(quizId)
            .collection("attempts").document(userId)
        val updates = mutableMapOf<String, Any>()
        updates["answers"] = userAnswers
        updates["currentIndex"] = currentIndex
        val statesMap = questionStateMap.mapValues { (_, state) ->
            mapOf(
                "isAnswered" to state.isAnswered,
                "isMarkedForReview" to state.isMarkedForReview,
                "isBookmarked" to state.isBookmarked,
                "isLocked" to state.isLocked
            )
        }
        updates["questionStates"] = statesMap
        attemptRef.update(updates)
            .addOnSuccessListener { Log.d(TAG, "Sync successful") }
            .addOnFailureListener { e -> Log.e(TAG, "Sync failed: ${e.message}") }
    }

    private fun saveTimerStateToFirestore() {
        val userId = auth.currentUser?.uid ?: return
        val attemptRef = db.collection("quizzes").document(quizId)
            .collection("attempts").document(userId)
        val updates = mutableMapOf<String, Any>()
        if (::timerManager.isInitialized) {
            if (timerType == "WHOLE_QUIZ") {
                updates["wholeQuizRemaining"] = timerManager.getWholeQuizRemaining()
            } else if (timerType == "PER_QUESTION") {
                val perQuestionRemaining = mutableMapOf<String, Long>()
                for (q in shuffledQuestions) {
                    val remaining = timerManager.getRemainingForQuestion(q.questionId)
                    if (remaining > 0) {
                        perQuestionRemaining[q.questionId] = remaining
                    }
                }
                updates["perQuestionRemaining"] = perQuestionRemaining
            }
        }
        if (updates.isNotEmpty()) {
            attemptRef.set(updates, SetOptions.merge())
                .addOnSuccessListener { Log.d(TAG, "Timer state saved") }
                .addOnFailureListener { e -> Log.e(TAG, "Failed to save timer state: ${e.message}") }
        }
    }

    // ---------- LOAD QUIZ AND QUESTIONS ----------
    private fun loadQuizAndQuestions() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "Please log in", Toast.LENGTH_SHORT).show()
            safeFinish()
            return
        }

        // Check if there's an existing attempt first
        val attemptRef = db.collection("quizzes").document(quizId)
            .collection("attempts").document(userId)
        attemptRef.get()
            .addOnSuccessListener { attemptDoc ->
                if (attemptDoc.exists()) {
                    val status = attemptDoc.getString("status")
                    // If completed or expired, navigate to result
                    if (status == "Completed" || status == "TIME_EXPIRED" || status == "CHEATING_AUTO_SUBMITTED") {
                        val score = attemptDoc.getLong("score")?.toInt() ?: 0
                        val totalScore = attemptDoc.getLong("totalScore")?.toInt() ?: 0
                        val reason = attemptDoc.getString("submissionReason") ?: status
                        val intent = Intent(this, ResultActivity::class.java)
                        intent.putExtra("quizId", quizId)
                        intent.putExtra("quizTitle", quizTitle)
                        intent.putExtra("score", score)
                        intent.putExtra("total", totalScore)
                        intent.putExtra("submissionReason", reason)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                        finish()
                        return@addOnSuccessListener
                    }
                }
                // If no completed attempt, proceed to load quiz
                fetchQuizAndQuestions()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error checking attempt status: ${e.message}")
                fetchQuizAndQuestions()
            }
    }

    private fun fetchQuizAndQuestions() {
        db.collection("quizzes").document(quizId).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Toast.makeText(this, "Quiz not found", Toast.LENGTH_SHORT).show()
                    safeFinish()
                    return@addOnSuccessListener
                }
                quiz = doc.toObject(Quiz::class.java)!!
                quiz.quizId = doc.id

                if (quiz.deadline > 0 && System.currentTimeMillis() > quiz.deadline) {
                    Toast.makeText(this, "Quiz expired", Toast.LENGTH_SHORT).show()
                    safeFinish()
                    return@addOnSuccessListener
                }

                timerType = quiz.timerType.ifEmpty { "NONE" }
                totalTimeSeconds = quiz.totalTimeSeconds
                timePerQuestionSeconds = quiz.timePerQuestionSeconds

                loadQuestions()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load quiz", Toast.LENGTH_SHORT).show()
                safeFinish()
            }
    }

    // ---------- LOAD QUESTIONS (only public data) ----------
    private fun loadQuestions() {
        val questionsCollection = db.collection("quizzes").document(quizId).collection("questions")
        questionsCollection.get()
            .addOnSuccessListener { docs ->
                val loadedQuestions = docs.map { doc ->
                    doc.toObject(Question::class.java).apply { questionId = doc.id }
                }
                if (loadedQuestions.isNotEmpty()) {
                    questions.clear()
                    questions.addAll(loadedQuestions)
                    // Restore attempt from Firestore before starting
                    restoreAttemptFromFirestore()
                    startQuiz()
                } else {
                    Toast.makeText(this, "No questions could be loaded", Toast.LENGTH_SHORT).show()
                    safeFinish()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load questions: ${e.message}", Toast.LENGTH_SHORT).show()
                safeFinish()
            }
    }

    // ---------- RESTORE FROM FIRESTORE ----------
    private fun restoreAttemptFromFirestore() {
        val userId = auth.currentUser?.uid ?: return
        val attemptRef = db.collection("quizzes").document(quizId)
            .collection("attempts").document(userId)
        attemptRef.get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val savedAnswers = doc.get("answers") as? Map<String, Any> ?: emptyMap()
                    userAnswers.clear()
                    userAnswers.putAll(savedAnswers)

                    currentIndex = doc.getLong("currentIndex")?.toInt() ?: 0

                    @Suppress("UNCHECKED_CAST")
                    val savedStates = doc.get("questionStates") as? Map<String, Map<String, Any>> ?: emptyMap()
                    questionStateMap.clear()
                    questionStatesList.clear()
                    savedStates.forEach { (qId, stateMap) ->
                        val state = QuestionState(
                            questionId = qId,
                            isAnswered = stateMap["isAnswered"] as? Boolean ?: false,
                            isMarkedForReview = stateMap["isMarkedForReview"] as? Boolean ?: false,
                            isBookmarked = stateMap["isBookmarked"] as? Boolean ?: false,
                            isLocked = stateMap["isLocked"] as? Boolean ?: false
                        )
                        questionStateMap[qId] = state
                        questionStatesList.add(state)
                    }

                    violationCount = doc.getLong("violationCount")?.toInt() ?: 0
                    wholeQuizRemainingSeconds = doc.getLong("wholeQuizRemaining") ?: -1

                    @Suppress("UNCHECKED_CAST")
                    val perQuestionRemaining = doc.get("perQuestionRemaining") as? Map<String, Long> ?: emptyMap()
                    perQuestionRemainingMap = perQuestionRemaining.toMutableMap()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to restore from Firestore: ${e.message}")
            }
    }

    // ---------- RANDOMIZATION HELPERS ----------
    private fun loadOrGenerateRandomization() {
        val userId = auth.currentUser?.uid ?: return
        val attemptRef = db.collection("quizzes").document(quizId)
            .collection("attempts").document(userId)

        attemptRef.get().addOnSuccessListener { doc ->
            if (doc.exists() && doc.contains("questionOrder")) {
                @Suppress("UNCHECKED_CAST")
                val savedOrder = doc.get("questionOrder") as? List<String> ?: emptyList()
                if (savedOrder.isNotEmpty()) {
                    questionOrder.clear()
                    questionOrder.addAll(savedOrder)
                    @Suppress("UNCHECKED_CAST")
                    val savedOptionOrder = doc.get("optionOrder") as? Map<String, List<Int>> ?: emptyMap()
                    optionOrderMap.clear()
                    optionOrderMap.putAll(savedOptionOrder)
                    randomizationLoaded = true
                    buildShuffledQuestions()
                    return@addOnSuccessListener
                }
            }
            generateAndSaveRandomization()
        }.addOnFailureListener {
            generateAndSaveRandomization()
        }
    }

    private fun generateAndSaveRandomization() {
        val originalQuestionIds = questions.map { it.questionId }
        when (quiz.randomizationMode) {
            "RANDOM_QUESTION_ORDER", "RANDOM_QUESTION_AND_OPTION_ORDER" -> {
                val seed = System.currentTimeMillis() + auth.currentUser?.uid.hashCode()
                val random = Random(seed)
                questionOrder.clear()
                questionOrder.addAll(originalQuestionIds.shuffled(random))
            }
            else -> {
                questionOrder.clear()
                questionOrder.addAll(originalQuestionIds)
            }
        }

        optionOrderMap.clear()
        if (quiz.randomizationMode == "RANDOM_QUESTION_AND_OPTION_ORDER") {
            val seed = System.currentTimeMillis() + auth.currentUser?.uid.hashCode() + 1
            val random = Random(seed)
            for (q in questions) {
                val originalIndices = q.options.indices.toList()
                optionOrderMap[q.questionId] = originalIndices.shuffled(random)
            }
        } else {
            for (q in questions) {
                optionOrderMap[q.questionId] = q.options.indices.toList()
            }
        }

        saveRandomizationToFirestore()
        randomizationLoaded = true
        buildShuffledQuestions()
    }

    private fun saveRandomizationToFirestore() {
        val userId = auth.currentUser?.uid ?: return
        val attemptRef = db.collection("quizzes").document(quizId)
            .collection("attempts").document(userId)
        val updates = mutableMapOf<String, Any>()
        updates["questionOrder"] = questionOrder
        updates["optionOrder"] = optionOrderMap
        attemptRef.set(updates, SetOptions.merge())
            .addOnSuccessListener { Log.d(TAG, "Randomization saved") }
            .addOnFailureListener { e -> Log.e(TAG, "Failed to save randomization: ${e.message}") }
    }

    private fun buildShuffledQuestions() {
        shuffledQuestions.clear()
        for (qId in questionOrder) {
            val q = questions.find { it.questionId == qId }
            if (q != null) {
                val shuffledOptions = if (quiz.randomizationMode == "RANDOM_QUESTION_AND_OPTION_ORDER") {
                    val order = optionOrderMap[qId] ?: q.options.indices.toList()
                    order.map { q.options[it] }
                } else {
                    q.options
                }
                val shuffledQ = q.copy(options = shuffledOptions)
                shuffledQuestions.add(shuffledQ)
            }
        }
        initializeQuestionStates()
        displayQuestion()
        updateProgress()
    }

    // ---------- START QUIZ ----------
    private fun startQuiz() {
        createAttemptDocument()
        restoreSavedAnswers()
        loadOrGenerateRandomization()
        setupTimerAndNavigation()
    }

    private fun createAttemptDocument() {
        val userId = auth.currentUser?.uid ?: return
        val user = auth.currentUser
        val userName = user?.displayName ?: user?.email?.substringBefore("@") ?: "User"
        val email = user?.email ?: ""

        val attemptRef = db.collection("quizzes").document(quizId)
            .collection("attempts").document(userId)

        val initialData = mapOf(
            "userId" to userId,
            "userName" to userName,
            "email" to email,
            "quizId" to quizId,
            "status" to "In Progress",
            "joinTime" to System.currentTimeMillis(),
            "answers" to emptyMap<String, Any>(),
            "score" to 0,
            "totalScore" to questions.sumOf { it.points },
            "violationCount" to 0,
            "currentIndex" to 0
        )

        attemptRef.set(initialData, SetOptions.merge())
            .addOnSuccessListener { Log.d(TAG, "Attempt document created with user info") }
            .addOnFailureListener { e -> Log.e(TAG, "Failed to create attempt: ${e.message}") }
    }

    private fun setupTimerAndNavigation() {
        startTime = System.currentTimeMillis()

        val timerMode = when (timerType) {
            "WHOLE_QUIZ" -> TimerManager.TimerMode.WHOLE_QUIZ
            "PER_QUESTION" -> TimerManager.TimerMode.PER_QUESTION
            else -> TimerManager.TimerMode.NONE
        }

        timerManager = TimerManager(
            mode = timerMode,
            onTick = { seconds ->
                runOnUiThread { updateTimerUI(seconds) }
            },
            onFinish = {
                runOnUiThread {
                    when (timerMode) {
                        TimerManager.TimerMode.WHOLE_QUIZ -> onWholeQuizExpired()
                        TimerManager.TimerMode.PER_QUESTION -> onQuestionTimerExpired()
                        else -> { /* no‑op */ }
                    }
                }
            }
        )

        if (timerMode == TimerManager.TimerMode.WHOLE_QUIZ) {
            if (wholeQuizRemainingSeconds > 0) {
                timerManager.restoreWholeQuiz(wholeQuizRemainingSeconds)
                timerManager.resumeTimer()
            } else if (totalTimeSeconds > 0) {
                timerManager.startWholeQuiz(totalTimeSeconds)
            }
        }

        when (timerType) {
            "WHOLE_QUIZ" -> {
                binding.tvTimerLabel.visibility = View.VISIBLE
                binding.tvTimer.visibility = View.VISIBLE
            }
            "PER_QUESTION" -> {
                binding.tvTimerLabel.visibility = View.VISIBLE
                binding.tvTimer.visibility = View.VISIBLE
            }
            else -> {
                binding.tvTimerLabel.visibility = View.GONE
                binding.tvTimer.visibility = View.GONE
            }
        }

        setupNavigation()
    }

    private fun initializeQuestionStates() {
        if (questionStateMap.isEmpty()) {
            questionStateMap.clear()
            questionStatesList.clear()
            for (q in shuffledQuestions) {
                val state = QuestionState(
                    questionId = q.questionId,
                    isAnswered = userAnswers.containsKey(q.questionId),
                    isBookmarked = loadBookmarkState(q.questionId),
                    isMarkedForReview = loadReviewState(q.questionId),
                    isLocked = false
                )
                questionStateMap[q.questionId] = state
                questionStatesList.add(state)
            }
        } else {
            questionStatesList.clear()
            questionStatesList.addAll(questionStateMap.values)
        }
    }

    private fun loadBookmarkState(questionId: String): Boolean {
        val prefs = getSharedPreferences("quiz_state_$quizId", MODE_PRIVATE)
        return prefs.getBoolean("bookmark_$questionId", false)
    }

    private fun loadReviewState(questionId: String): Boolean {
        val prefs = getSharedPreferences("quiz_state_$quizId", MODE_PRIVATE)
        return prefs.getBoolean("review_$questionId", false)
    }

    private fun setupNavigation() {
        binding.btnPrevious.setOnClickListener {
            if (currentIndex > 0 && !isQuizSubmitted && !isQuizExpired) {
                saveCurrentAnswer()
                if (timerType == "PER_QUESTION") timerManager.pauseTimer()
                currentIndex--
                displayQuestion()
            }
        }

        binding.btnBookmark.setOnClickListener {
            toggleBookmark(shuffledQuestions[currentIndex].questionId)
        }

        binding.btnMarkForReview.setOnClickListener {
            toggleMarkForReview()
        }

        binding.btnGrid.setOnClickListener {
            showQuestionGrid()
        }
    }

    private fun toggleMarkForReview() {
        val q = shuffledQuestions[currentIndex]
        val state = questionStateMap[q.questionId] ?: return
        state.isMarkedForReview = !state.isMarkedForReview
        saveQuestionState(state)
        updateQuestionGridState()
        updateProgress()
        binding.btnMarkForReview.text = if (state.isMarkedForReview) "Unmark Review" else "Mark for Review"
        Toast.makeText(this, if (state.isMarkedForReview) "Marked for Review" else "Review mark removed", Toast.LENGTH_SHORT).show()
        scheduleAutoSave()
    }

    private fun saveQuestionState(state: QuestionState) {
        val prefs = getSharedPreferences("quiz_state_$quizId", MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("answered_${state.questionId}", state.isAnswered)
            putBoolean("review_${state.questionId}", state.isMarkedForReview)
            putBoolean("bookmark_${state.questionId}", state.isBookmarked)
            putBoolean("locked_${state.questionId}", state.isLocked)
            apply()
        }
        questionStateMap[state.questionId] = state
        val index = questionStatesList.indexOfFirst { it.questionId == state.questionId }
        if (index != -1) {
            questionStatesList[index] = state
        }
    }

    private fun updateQuestionGridState() {
        gridDialog?.let {
            gridAdapter?.notifyDataSetChanged()
        }
    }

    // ---------- DISPLAY QUESTION ----------
    private fun displayQuestion() {
        val q = shuffledQuestions[currentIndex]
        binding.tvQuestion.text = q.text

        // Media handling
        if (q.imageUrl.isNotEmpty()) {
            binding.ivQuestionImage.visibility = View.VISIBLE
            Glide.with(this).load(q.imageUrl).into(binding.ivQuestionImage)
        } else {
            binding.ivQuestionImage.visibility = View.GONE
        }

        if (q.videoUrl.isNotEmpty()) {
            binding.vvQuestionVideo.visibility = View.VISIBLE
            binding.vvQuestionVideo.setVideoURI(Uri.parse(q.videoUrl))
            binding.vvQuestionVideo.setOnPreparedListener { mp -> mp.start() }
        } else {
            binding.vvQuestionVideo.visibility = View.GONE
        }

        if (q.audioUrl.isNotEmpty()) {
            binding.llAudioControls.visibility = View.VISIBLE
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(q.audioUrl)
                prepareAsync()
            }
            binding.btnPlayAudio.setOnClickListener { mediaPlayer?.start() }
        } else {
            binding.llAudioControls.visibility = View.GONE
        }

        val container = binding.radioGroupOptions
        container.removeAllViews()

        val originalQuestion = questions.find { it.questionId == q.questionId } ?: q
        val optionOrder = optionOrderMap[q.questionId] ?: originalQuestion.options.indices.toList()
        val shuffledOptions = optionOrder.map { originalQuestion.options[it] }

        when (q.questionType) {
            "radio" -> {
                val radioGroup = RadioGroup(this)
                radioGroup.orientation = RadioGroup.VERTICAL
                shuffledOptions.forEachIndexed { displayIdx, optionText ->
                    val rb = RadioButton(this)
                    rb.text = optionText
                    val originalIdx = optionOrder[displayIdx]
                    rb.tag = originalIdx
                    radioGroup.addView(rb)
                }
                container.addView(radioGroup)
                val saved = userAnswers[q.questionId]
                if (saved is Int) {
                    for (i in 0 until radioGroup.childCount) {
                        val child = radioGroup.getChildAt(i)
                        if (child is RadioButton && child.tag == saved) {
                            child.isChecked = true
                            break
                        }
                    }
                }
                radioGroup.setOnCheckedChangeListener { _, checkedId ->
                    if (checkedId != -1) {
                        val rb = radioGroup.findViewById<RadioButton>(checkedId)
                        val originalIdx = rb.tag as Int
                        onAnswerSelected(q.questionId, originalIdx)
                    }
                }
            }
            "checkbox" -> {
                val linearLayout = LinearLayout(this)
                linearLayout.orientation = LinearLayout.VERTICAL
                shuffledOptions.forEachIndexed { displayIdx, optionText ->
                    val cb = CheckBox(this)
                    cb.text = optionText
                    val originalIdx = optionOrder[displayIdx]
                    cb.tag = originalIdx
                    linearLayout.addView(cb)
                }
                container.addView(linearLayout)
                val saved = userAnswers[q.questionId]
                if (saved is List<*>) {
                    val selectedIndices = saved.filterIsInstance<Int>()
                    for (i in 0 until linearLayout.childCount) {
                        val child = linearLayout.getChildAt(i)
                        if (child is CheckBox && selectedIndices.contains(child.tag)) {
                            child.isChecked = true
                        }
                    }
                }
                for (i in 0 until linearLayout.childCount) {
                    val child = linearLayout.getChildAt(i)
                    if (child is CheckBox) {
                        child.setOnCheckedChangeListener { _, _ ->
                            val checkedIndices = (0 until linearLayout.childCount)
                                .mapNotNull { idx ->
                                    val view = linearLayout.getChildAt(idx)
                                    if (view is CheckBox && view.isChecked) view.tag as? Int else null
                                }
                            onAnswerSelected(q.questionId, checkedIndices)
                        }
                    }
                }
            }
            "descriptive" -> {
                val editText = EditText(this)
                editText.hint = "Type your answer here"
                editText.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                container.addView(editText)
                val saved = userAnswers[q.questionId]
                if (saved is String) {
                    editText.setText(saved)
                }
                editText.addTextChangedListener(object : android.text.TextWatcher {
                    override fun afterTextChanged(s: android.text.Editable?) {
                        onAnswerSelected(q.questionId, s.toString())
                    }
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                })
            }
        }

        binding.tvProgress.text = "${currentIndex + 1}/${shuffledQuestions.size}"
        binding.tvMarks.text = "${q.points} marks"

        val state = questionStateMap[q.questionId]
        binding.btnBookmark.text = if (state?.isBookmarked == true) "Unbookmark" else "Bookmark"
        binding.btnMarkForReview.text = if (state?.isMarkedForReview == true) "Unmark Review" else "Mark for Review"

        val isTimedOut = state?.isLocked == true
        val shouldDisable = isQuizSubmitted || isQuizExpired || isTimedOut
        if (shouldDisable) {
            disableAnswerControls()
        } else {
            enableAnswerControls()
        }

        if (timerType == "PER_QUESTION" && !isQuizSubmitted && !isQuizExpired && !isTimedOut) {
            val savedRemaining = perQuestionRemainingMap[q.questionId]
            if (savedRemaining != null && savedRemaining > 0) {
                timerManager.startQuestionTimer(q.questionId, savedRemaining)
            } else {
                timerManager.startQuestionTimer(q.questionId, timePerQuestionSeconds)
            }
        }

        val isLastQuestion = (currentIndex == shuffledQuestions.size - 1)
        if (isLastQuestion) {
            binding.btnNextOrSubmit.text = "Submit Quiz"
            binding.btnNextOrSubmit.setOnClickListener {
                if (isQuizSubmitted || isQuizExpired) {
                    Toast.makeText(this, "Quiz already expired or submitted.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                saveCurrentAnswer()
                showSubmitConfirmation()
            }
        } else {
            binding.btnNextOrSubmit.text = "Next"
            binding.btnNextOrSubmit.setOnClickListener {
                if (isQuizSubmitted || isQuizExpired) {
                    Toast.makeText(this, "Quiz already expired or submitted.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                saveCurrentAnswer()
                if (timerType == "PER_QUESTION") timerManager.pauseTimer()
                currentIndex++
                displayQuestion()
            }
        }

        updateProgress()
    }

    private fun getCheckedIndices(linearLayout: LinearLayout): List<Int> {
        val indices = mutableListOf<Int>()
        for (i in 0 until linearLayout.childCount) {
            val child = linearLayout.getChildAt(i)
            if (child is CheckBox && child.isChecked) {
                indices.add(child.tag as Int)
            }
        }
        return indices
    }

    private fun onAnswerSelected(questionId: String, answer: Any) {
        if (isQuizSubmitted || isQuizExpired) return
        userAnswers[questionId] = answer
        val state = questionStateMap[questionId] ?: return
        state.isAnswered = true
        saveQuestionState(state)
        scheduleAutoSave()
        updateProgress()
        updateQuestionGridState()
    }

    // ---------- SAVE CURRENT ANSWER ----------
    private fun saveCurrentAnswer() {
        val q = shuffledQuestions[currentIndex]
        val container = binding.radioGroupOptions
        when (q.questionType) {
            "radio" -> {
                val radioGroup = container.getChildAt(0) as? RadioGroup
                val selectedId = radioGroup?.checkedRadioButtonId
                if (selectedId != null && selectedId != -1) {
                    val rb = radioGroup.findViewById<RadioButton>(selectedId)
                    val selectedIndex = rb.tag as Int
                    userAnswers[q.questionId] = selectedIndex
                } else {
                    userAnswers.remove(q.questionId)
                }
            }
            "checkbox" -> {
                val linearLayout = container.getChildAt(0) as? LinearLayout
                val selectedIndices = getCheckedIndices(linearLayout ?: return)
                if (selectedIndices.isNotEmpty()) {
                    userAnswers[q.questionId] = selectedIndices
                } else {
                    userAnswers.remove(q.questionId)
                }
            }
            "descriptive" -> {
                val editText = container.getChildAt(0) as? EditText
                val text = editText?.text.toString().trim()
                if (text.isNotEmpty()) {
                    userAnswers[q.questionId] = text
                } else {
                    userAnswers.remove(q.questionId)
                }
            }
        }
        val state = questionStateMap[q.questionId]
        state?.isAnswered = userAnswers.containsKey(q.questionId)
        state?.let { saveQuestionState(it) }
        scheduleAutoSave()
        updateProgress()
    }

    // ---------- RESTORE SAVED ANSWERS ----------
    private fun restoreSavedAnswers() {
        for (q in shuffledQuestions) {
            when (q.questionType) {
                "radio" -> {
                    val saved = sharedPrefs.getInt("${quizId}_${q.questionId}", -1)
                    if (saved != -1) userAnswers[q.questionId] = saved
                }
                "checkbox" -> {
                    val savedJson = sharedPrefs.getString("${quizId}_${q.questionId}", null)
                    if (savedJson != null) {
                        val indices = savedJson.split(",").mapNotNull { it.toIntOrNull() }
                        if (indices.isNotEmpty()) userAnswers[q.questionId] = indices
                    }
                }
                "descriptive" -> {
                    val savedText = sharedPrefs.getString("${quizId}_${q.questionId}", null)
                    if (savedText != null) userAnswers[q.questionId] = savedText
                }
            }
        }
    }

    // ---------- BOOKMARK ----------
    private fun toggleBookmark(questionId: String) {
        val uid = auth.currentUser?.uid ?: return
        val state = questionStateMap[questionId] ?: return
        state.isBookmarked = !state.isBookmarked
        saveQuestionState(state)
        binding.btnBookmark.text = if (state.isBookmarked) "Unbookmark" else "Bookmark"
        val bookmarkRef = db.collection("bookmarks").document("${uid}_$questionId")
        if (state.isBookmarked) {
            bookmarkRef.set(mapOf(
                "userId" to uid,
                "questionId" to questionId,
                "quizId" to quizId,
                "createdAt" to System.currentTimeMillis()
            )).addOnSuccessListener {
                Toast.makeText(this, "Bookmarked", Toast.LENGTH_SHORT).show()
            }
        } else {
            bookmarkRef.delete().addOnSuccessListener {
                Toast.makeText(this, "Bookmark removed", Toast.LENGTH_SHORT).show()
            }
        }
        updateQuestionGridState()
        updateProgress()
        scheduleAutoSave()
    }

    // ---------- QUESTION GRID ----------
    private fun showQuestionGrid() {
        if (isQuizSubmitted || isQuizExpired) {
            Toast.makeText(this, "Quiz already submitted or expired", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogBinding = DialogQuestionGridBinding.inflate(layoutInflater)
        val gridRecycler = dialogBinding.rvQuestionGrid
        gridRecycler.layoutManager = GridLayoutManager(this, 4)

        gridAdapter = QuestionGridAdapter(currentIndex) { position ->
            if (!isQuizSubmitted && !isQuizExpired) {
                saveCurrentAnswer()
                if (timerType == "PER_QUESTION") timerManager.pauseTimer()
                currentIndex = position
                displayQuestion()
                gridDialog?.dismiss()
            } else {
                Toast.makeText(this, "Quiz already submitted or expired", Toast.LENGTH_SHORT).show()
            }
        }
        gridAdapter.submitList(questionStatesList)
        gridRecycler.adapter = gridAdapter

        isDialogShowing = true

        gridDialog = AlertDialog.Builder(this)
            .setTitle("Question Grid")
            .setView(dialogBinding.root)
            .setPositiveButton("Close") { _, _ ->
                isDialogShowing = false
                gridDialog = null
            }
            .setOnDismissListener {
                isDialogShowing = false
                gridDialog = null
            }
            .show()
    }

    // ---------- PROGRESS INDICATOR ----------
    private fun updateProgress() {
        val total = shuffledQuestions.size
        val answered = questionStateMap.values.count { it.isAnswered }
        val bookmarked = questionStateMap.values.count { it.isBookmarked }
        val reviewed = questionStateMap.values.count { it.isMarkedForReview }
        val locked = questionStateMap.values.count { it.isLocked }
        val percentage = if (total > 0) (answered * 100 / total) else 0

        binding.tvProgressText.text = "$answered/$total"
        binding.progressOverall.progress = percentage
    }

    // ---------- TIMER UI UPDATE ----------
    private fun updateTimerUI(seconds: Long) {
        runOnUiThread {
            binding.tvTimerLabel.visibility = View.VISIBLE
            binding.tvTimer.visibility = View.VISIBLE
            val label = when (timerType) {
                "WHOLE_QUIZ" -> "Quiz Time Remaining"
                "PER_QUESTION" -> "Question Time Remaining"
                else -> ""
            }
            binding.tvTimerLabel.text = label
            binding.tvTimer.text = formatDuration(seconds)
        }
    }

    // ---------- WHOLE QUIZ EXPIRY ----------
    private fun onWholeQuizExpired() {
        if (isSubmitted || isQuizSubmitted) return
        submitQuizWithReason("TIMER_EXPIRED")
    }

    // ---------- QUESTION TIMER EXPIRY ----------
    private fun onQuestionTimerExpired() {
        if (isSubmitted || isQuizSubmitted) return
        val question = shuffledQuestions[currentIndex]
        val state = questionStateMap[question.questionId]
        state?.isLocked = true
        state?.let { saveQuestionState(it) }
        disableAnswerControls()
        Toast.makeText(this, "Time expired for this question", Toast.LENGTH_SHORT).show()

        if (currentIndex < shuffledQuestions.size - 1) {
            saveCurrentAnswer()
            timerManager.pauseTimer()
            currentIndex++
            displayQuestion()
        } else {
            Toast.makeText(this, "Time's up for the last question. Submitting quiz.", Toast.LENGTH_SHORT).show()
            submitQuizWithReason("TIMER_EXPIRED")
        }
        updateProgress()
        updateQuestionGridState()
    }

    // ---------- SUBMIT CONFIRMATION ----------
    private fun showSubmitConfirmation() {
        if (isQuizSubmitted || isQuizExpired) {
            Toast.makeText(this, "Quiz already expired or submitted.", Toast.LENGTH_SHORT).show()
            return
        }
        isDialogShowing = true
        AlertDialog.Builder(this)
            .setTitle("Submit Quiz")
            .setMessage("Are you sure you want to submit the quiz? You cannot change your answers after submission.")
            .setPositiveButton("Yes, Submit") { _, _ ->
                isDialogShowing = false
                submitQuizWithReason("NORMAL")
            }
            .setNegativeButton("Cancel") { _, _ ->
                isDialogShowing = false
            }
            .setOnDismissListener {
                isDialogShowing = false
            }
            .show()
    }

    // ---------- SUBMIT QUIZ (with correct answers fetched at submission) ----------
    private fun submitQuizWithReason(reason: String = "NORMAL") {
        if (isSubmitted) return
        isSubmitted = true

        disableAnswerControls()
        binding.btnPrevious.isEnabled = false
        binding.btnNextOrSubmit.isEnabled = false
        binding.btnBookmark.isEnabled = false
        binding.btnMarkForReview.isEnabled = false
        binding.btnGrid.isEnabled = false
        timerManager.cancel()
        mediaPlayer?.release()

        saveCurrentAnswer()
        saveCurrentState()
        saveTimerStateToFirestore()

        val totalPossible = questions.sumOf { it.points }
        val userId = auth.currentUser?.uid ?: run {
            Toast.makeText(this, "Please log in", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            safeFinish()
            return
        }

        // Fetch correct answers from questions_private (batch)
        fetchCorrectAnswersAndSubmit(userId, totalPossible, reason)
    }

    private fun fetchCorrectAnswersAndSubmit(userId: String, totalPossible: Int, reason: String) {
        val privateTasks = questions.map { q ->
            db.collection("quizzes").document(quizId)
                .collection("questions_private").document(q.questionId)
                .get()
        }

        Tasks.whenAllSuccess<DocumentSnapshot>(privateTasks)
            .addOnSuccessListener { snapshots ->
                snapshots.forEachIndexed { index, doc ->
                    if (doc.exists()) {
                        val q = questions[index]
                        when (q.questionType) {
                            "radio" -> q.correctAnswerIndex = doc.getLong("correctAnswerIndex")?.toInt() ?: 0
                            "checkbox" -> {
                                val rawList = doc.get("correctAnswerIndices") as? List<*>
                                q.correctAnswerIndices = rawList?.mapNotNull {
                                    when (it) {
                                        is Int -> it
                                        is Long -> it.toInt()
                                        else -> null
                                    }
                                } ?: emptyList()
                            }
                            "descriptive" -> q.correctAnswerText = doc.getString("correctAnswerText") ?: ""
                        }
                    }
                }
                // Now compute final score
                computeFinalScoreAndSubmit(userId, totalPossible, reason)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to fetch correct answers: ${e.message}")
                // Continue without correct answers (score will be 0)
                computeFinalScoreAndSubmit(userId, totalPossible, reason)
            }
    }

    private fun computeFinalScoreAndSubmit(userId: String, totalPossible: Int, reason: String) {
        var finalScore = 0.0
        for ((qId, answer) in userAnswers) {
            val q = questions.find { it.questionId == qId } ?: continue
            val isCorrect = when (q.questionType) {
                "radio" -> {
                    val selected = answer as? Int
                    selected != null && selected == q.correctAnswerIndex
                }
                "checkbox" -> {
                    val selected = answer as? List<*>
                    if (selected != null) {
                        val selectedSet = selected.mapNotNull {
                            when (it) {
                                is Int -> it
                                is Long -> it.toInt()
                                else -> null
                            }
                        }.toSet()
                        val correctSet = q.correctAnswerIndices.toSet()
                        selectedSet == correctSet
                    } else false
                }
                "descriptive" -> {
                    val userText = answer as? String
                    userText != null && userText.trim().equals(q.correctAnswerText.trim(), ignoreCase = true)
                }
                else -> false
            }
            if (isCorrect) {
                finalScore += q.points
            }
        }
        finalScore = finalScore.coerceAtLeast(0.0)
        score = finalScore

        val endTime = System.currentTimeMillis()
        val timeSpentSeconds = (endTime - startTime) / 1000
        val durationFormatted = formatDuration(timeSpentSeconds)

        val user = auth.currentUser!!
        val attemptData = mutableMapOf<String, Any>()
        attemptData["userId"] = userId
        attemptData["userName"] = user.displayName ?: user.email?.substringBefore("@") ?: "User"
        attemptData["email"] = user.email ?: ""
        attemptData["joinTime"] = startTime
        attemptData["submitTime"] = endTime
        attemptData["duration"] = timeSpentSeconds
        attemptData["durationFormatted"] = durationFormatted
        attemptData["answers"] = userAnswers
        attemptData["score"] = score
        attemptData["totalScore"] = totalPossible
        attemptData["status"] = "Completed"
        attemptData["submissionReason"] = reason
        attemptData["violationCount"] = violationCount
        attemptData["questionStates"] = questionStateMap.values.map { it.copy() }
        attemptData["questionOrder"] = questionOrder
        attemptData["optionOrder"] = optionOrderMap
        attemptData["currentIndex"] = currentIndex

        val attemptRef = db.collection("quizzes").document(quizId)
            .collection("attempts").document(userId)

        attemptRef.set(attemptData, SetOptions.merge())
            .addOnSuccessListener {
                isQuizExpired = true
                isQuizSubmitted = true
                for (q in questions) {
                    sharedPrefs.edit().remove("${quizId}_${q.questionId}").apply()
                }
                clearViolationCount()

                val resultData = mutableMapOf<String, Any>()
                resultData["userId"] = userId
                resultData["quizId"] = quizId
                resultData["score"] = score
                resultData["totalScore"] = totalPossible
                resultData["submittedAt"] = endTime
                resultData["submissionReason"] = reason
                db.collection("results").add(resultData)

                updateJoinedQuiz(userId, score.toInt(), totalPossible, endTime)
                CheatLogger.clearViolations(applicationContext)

                val intent = Intent(this, ResultActivity::class.java)
                intent.putExtra("quizId", quizId)
                intent.putExtra("quizTitle", quizTitle)
                intent.putExtra("score", score.toInt())
                intent.putExtra("total", totalPossible)
                intent.putExtra("timeTaken", timeSpentSeconds)
                intent.putExtra("submissionReason", reason)
                intent.putExtra("showScore", quiz.showScoreAfterSubmission)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                safeFinish()
            }
            .addOnFailureListener { e ->
                isSubmitted = false
                isQuizExpired = false
                isQuizSubmitted = false
                enableAnswerControls()
                binding.btnPrevious.isEnabled = true
                binding.btnNextOrSubmit.isEnabled = true
                binding.btnBookmark.isEnabled = true
                binding.btnMarkForReview.isEnabled = true
                binding.btnGrid.isEnabled = true
                Toast.makeText(this, "Failed to save attempt: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e(TAG, "Submit error: ${e.message}")
            }
    }

    private fun updateJoinedQuiz(userId: String, score: Int, totalScore: Int, submitTime: Long) {
        val joinedQuizRef = db.collection("users").document(userId)
            .collection("joinedQuizzes").document(quizId)
        joinedQuizRef.get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                val updates = mapOf(
                    "status" to "Completed",
                    "submitTime" to submitTime,
                    "score" to score
                )
                joinedQuizRef.update(updates)
            } else {
                val joinedQuiz = JoinedQuiz(
                    quizId = quizId,
                    quizTitle = quizTitle,
                    quizCode = quiz.quizCode,
                    creatorId = if (creatorId.isNotEmpty()) creatorId else quiz.creatorId,
                    creatorName = "",
                    joinTime = startTime,
                    submitTime = submitTime,
                    status = "Completed",
                    score = score.toDouble(),
                    category = quiz.category,
                    allowMultipleAttempts = quiz.allowMultipleAttempts
                )
                joinedQuizRef.set(joinedQuiz)
            }
        }
    }
}