package com.smartquiz

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
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
import com.bumptech.glide.Glide
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.smartquiz.databinding.ActivityQuizAttemptBinding
import com.smartquiz.models.JoinedQuiz
import android.view.ViewGroup

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
    private var mediaPlayer: MediaPlayer? = null
    private val TAG = "QuizAttempt"

    // Cheat logging flags
    private var isInForeground = true
    private var userLeftViaSystem = false

    // Timer state
    private var timerType: String = "NONE"
    private var totalTimeSeconds: Long = 0
    private var timePerQuestionSeconds: Long = 0
    private var quizEndTime: Long = 0L

    // Whole Quiz Timer
    private var wholeQuizTimer: CountDownTimer? = null
    private var isQuizExpired = false   // NEW: global lock for whole‑quiz timer

    // Question Timer
    private var questionTimer: CountDownTimer? = null
    private var currentQuestionId: String = ""
    private var currentRemainingSeconds: Long = 0
    private val questionRemainingTimes = mutableMapOf<String, Long>()   // key = questionId
    private val questionTimedOut = mutableSetOf<String>()              // questions that hit 0 (expired)

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

        // --- Cheat logging: Back button ---
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                logCheatEvent("BACK_BUTTON")
                finish()
            }
        })

        // --- Cheat logging: App background/foreground ---
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                isInForeground = false
                if (!userLeftViaSystem) {
                    logCheatEvent("APP_BACKGROUND")
                }
                userLeftViaSystem = false
            }

            override fun onStart(owner: LifecycleOwner) {
                isInForeground = true
            }
        })

        // Restore state if any
        if (savedInstanceState != null) {
            timerType = savedInstanceState.getString("timerType", "NONE") ?: "NONE"
            quizEndTime = savedInstanceState.getLong("quizEndTime", 0L)
            currentIndex = savedInstanceState.getInt("currentIndex", 0)
            isQuizExpired = savedInstanceState.getBoolean("isQuizExpired", false)
            // Restore per-question times
            val keys = savedInstanceState.getStringArrayList("questionKeys") ?: arrayListOf()
            val values = savedInstanceState.getLongArray("questionValues") ?: longArrayOf()
            questionRemainingTimes.clear()
            for (i in keys.indices) {
                questionRemainingTimes[keys[i]] = values[i]
            }
            // Restore timed-out set
            val timedOutList = savedInstanceState.getStringArrayList("timedOutQuestions") ?: arrayListOf()
            questionTimedOut.clear()
            questionTimedOut.addAll(timedOutList)
        }

        loadQuizAndQuestions()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("timerType", timerType)
        outState.putLong("quizEndTime", quizEndTime)
        outState.putInt("currentIndex", currentIndex)
        outState.putBoolean("isQuizExpired", isQuizExpired)
        // Save per-question times
        val keys = questionRemainingTimes.keys.toList()
        val values = questionRemainingTimes.values.toLongArray()
        outState.putStringArrayList("questionKeys", ArrayList(keys))
        outState.putLongArray("questionValues", values)
        outState.putStringArrayList("timedOutQuestions", ArrayList(questionTimedOut))
    }

    override fun onResume() {
        super.onResume()
        // If whole quiz already expired, do nothing else
        if (isQuizExpired) {
            disableAnswerControls()
            return
        }
        // Restore whole quiz timer if not expired
        if (timerType == "WHOLE_QUIZ" && quizEndTime > 0) {
            val now = System.currentTimeMillis()
            if (now >= quizEndTime) {
                // Timer expired while in background
                onWholeQuizExpired()
            } else {
                // Recreate timer with remaining time
                wholeQuizTimer?.cancel()
                val remaining = (quizEndTime - now) / 1000
                if (remaining > 0) {
                    wholeQuizTimer = object : CountDownTimer(remaining * 1000, 1000) {
                        override fun onTick(millisUntilFinished: Long) {
                            val rem = (millisUntilFinished / 1000).toLong()
                            updateTimerUI(rem, "Quiz Time Remaining")
                        }
                        override fun onFinish() {
                            onWholeQuizExpired()
                        }
                    }.start()
                }
            }
        }
        // For question timer, we don't auto-resume; the timer will be started when the question is displayed.
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        userLeftViaSystem = true
        logCheatEvent("HOME_OR_RECENTS")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus && isInForeground) {
            logCheatEvent("FOCUS_LOST")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        wholeQuizTimer?.cancel()
        questionTimer?.cancel()
        mediaPlayer?.release()
    }

    // ---------- HELPER: disable / enable answer controls ----------
    private fun disableAnswerControls() {
        // Disable all child views inside radioGroupOptions container
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
                    // For EditText, also set focusable to false when disabled to prevent cursor
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

    // ---------- END HELPER ----------

    private fun logCheatEvent(eventType: String) {
        val userId = auth.currentUser?.uid ?: return
        val userName = auth.currentUser?.displayName ?: ""
        val email = auth.currentUser?.email ?: ""
        val currentCreatorId = if (creatorId.isNotEmpty()) creatorId
        else if (::quiz.isInitialized) quiz.creatorId
        else ""
        Log.d(TAG, "Cheat event detected: $eventType, creatorId: $currentCreatorId")
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

    private fun loadQuizAndQuestions() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "Please log in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        db.collection("quizzes").document(quizId).collection("attempts").document(userId)
            .get()
            .addOnSuccessListener { attemptDoc ->
                val isCompleted = attemptDoc.exists() && attemptDoc.getString("status") == "Completed"
                if (isCompleted) {
                    db.collection("quizzes").document(quizId).get()
                        .addOnSuccessListener { quizDoc ->
                            val allowMultiple = quizDoc.getBoolean("allowMultipleAttempts") ?: false
                            if (!allowMultiple) {
                                Toast.makeText(this, "You have already completed this quiz. Multiple attempts are not allowed.", Toast.LENGTH_LONG).show()
                                finish()
                            } else {
                                fetchQuizAndQuestions()
                            }
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Error checking quiz settings", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                } else {
                    fetchQuizAndQuestions()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error checking attempt: ${e.message}")
                fetchQuizAndQuestions()
            }
    }

    private fun fetchQuizAndQuestions() {
        db.collection("quizzes").document(quizId).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Toast.makeText(this, "Quiz not found", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }
                quiz = doc.toObject(Quiz::class.java)!!
                quiz.quizId = doc.id

                if (quiz.deadline > 0 && System.currentTimeMillis() > quiz.deadline) {
                    Toast.makeText(this, "Quiz expired", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }

                // Store timer config
                timerType = quiz.timerType.ifEmpty { "NONE" }
                totalTimeSeconds = quiz.totalTimeSeconds
                timePerQuestionSeconds = quiz.timePerQuestionSeconds

                loadQuestions()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load quiz", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun loadQuestions() {
        val questionsCollection = db.collection("quizzes").document(quizId).collection("questions")
        questionsCollection.get()
            .addOnSuccessListener { docs ->
                val tasks = mutableListOf<Task<Question>>()
                for (doc in docs) {
                    val q = doc.toObject(Question::class.java).apply { questionId = doc.id }
                    val task = db.collection("quizzes").document(quizId)
                        .collection("questions_private").document(doc.id)
                        .get()
                        .addOnSuccessListener { privateDoc ->
                            if (privateDoc.exists()) {
                                when (q.questionType) {
                                    "radio" -> {
                                        q.correctAnswerIndex = privateDoc.getLong("correctAnswerIndex")?.toInt() ?: 0
                                    }
                                    "checkbox" -> {
                                        val rawList = privateDoc.get("correctAnswerIndices") as? List<*>
                                        q.correctAnswerIndices = rawList?.mapNotNull {
                                            when (it) {
                                                is Int -> it
                                                is Long -> it.toInt()
                                                else -> null
                                            }
                                        } ?: emptyList()
                                    }
                                    "descriptive" -> {
                                        q.correctAnswerText = privateDoc.getString("correctAnswerText") ?: ""
                                    }
                                }
                                Log.d(TAG, "Private data loaded for ${q.questionId}")
                            } else {
                                Log.w(TAG, "Private document missing for ${q.questionId}")
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Failed to fetch private data for ${q.questionId}: ${e.message}")
                        }
                        .continueWith { q }
                    tasks.add(task)
                }
                Tasks.whenAllComplete(tasks)
                    .addOnCompleteListener { allComplete ->
                        val loadedQuestions = mutableListOf<Question>()
                        for (task in tasks) {
                            if (task.isSuccessful) {
                                task.result?.let { loadedQuestions.add(it) }
                            }
                        }
                        if (loadedQuestions.isNotEmpty()) {
                            questions.clear()
                            questions.addAll(loadedQuestions)
                            startQuiz()
                        } else {
                            Toast.makeText(this, "No questions could be loaded", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load questions: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun startQuiz() {
        shuffledQuestions = questions.shuffled().toMutableList()
        restoreSavedAnswers()
        startTime = System.currentTimeMillis()

        // Initialize per-question remaining times for those not yet visited
        // (only for PER_QUESTION timer)
        if (timerType == "PER_QUESTION") {
            for (q in shuffledQuestions) {
                if (!questionRemainingTimes.containsKey(q.questionId)) {
                    questionRemainingTimes[q.questionId] = timePerQuestionSeconds
                }
            }
        }

        // Start Whole Quiz Timer if applicable
        when (timerType) {
            "WHOLE_QUIZ" -> startWholeQuizTimer()
            "PER_QUESTION" -> {
                // Timer will be started in displayQuestion()
                binding.tvTimerLabel.visibility = View.VISIBLE
                binding.tvTimer.visibility = View.VISIBLE
            }
            else -> {
                // No timer
                binding.tvTimerLabel.visibility = View.GONE
                binding.tvTimer.visibility = View.GONE
            }
        }

        displayQuestion()

        binding.btnPrevious.setOnClickListener {
            if (currentIndex > 0) {
                // Prevent navigation if whole quiz expired
                if (isQuizExpired) return@setOnClickListener
                saveCurrentAnswer()
                saveCurrentQuestionTimer()   // save timer state before leaving
                currentIndex--
                displayQuestion()
            }
        }

        binding.btnBookmark.setOnClickListener {
            toggleBookmark(shuffledQuestions[currentIndex].questionId)
        }
    }

    // ---------- WHOLE QUIZ TIMER ----------
    private fun startWholeQuizTimer() {
        if (totalTimeSeconds <= 0) return
        quizEndTime = System.currentTimeMillis() + totalTimeSeconds * 1000
        wholeQuizTimer?.cancel()
        wholeQuizTimer = object : CountDownTimer(totalTimeSeconds * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val remaining = (millisUntilFinished / 1000).toLong()
                updateTimerUI(remaining, "Quiz Time Remaining")
            }
            override fun onFinish() {
                onWholeQuizExpired()
            }
        }.start()
    }

    private fun onWholeQuizExpired() {
        if (isSubmitted) return
        isQuizExpired = true
        updateTimerUI(0, "Quiz Time Remaining")
        disableAnswerControls()
        Toast.makeText(this, "Time's up! Submitting quiz.", Toast.LENGTH_SHORT).show()
        submitQuiz()
    }

    // ---------- PER-QUESTION TIMER ----------
    private fun startQuestionTimer() {
        // Cancel any existing timer
        questionTimer?.cancel()

        val question = shuffledQuestions[currentIndex]
        currentQuestionId = question.questionId

        // If this question already expired, do not start timer
        if (questionTimedOut.contains(currentQuestionId)) {
            currentRemainingSeconds = 0
            updateTimerUI(0, "Question Time Remaining")
            disableAnswerControls()
            return
        }

        // Get remaining time from map (already initialized)
        var remaining = questionRemainingTimes[currentQuestionId] ?: timePerQuestionSeconds
        if (remaining <= 0) {
            // This should not happen if not in timedOut set, but handle gracefully
            remaining = 0
            questionTimedOut.add(currentQuestionId)
            updateTimerUI(0, "Question Time Remaining")
            disableAnswerControls()
            return
        }

        currentRemainingSeconds = remaining
        updateTimerUI(remaining, "Question Time Remaining")
        enableAnswerControls()   // ensure controls are enabled for this question (if not expired)

        questionTimer = object : CountDownTimer(remaining * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000).toLong()
                currentRemainingSeconds = seconds
                questionRemainingTimes[currentQuestionId] = seconds
                updateTimerUI(seconds, "Question Time Remaining")
            }

            override fun onFinish() {
                currentRemainingSeconds = 0
                questionRemainingTimes[currentQuestionId] = 0
                questionTimedOut.add(currentQuestionId)
                updateTimerUI(0, "Question Time Remaining")
                // Disable controls for this question
                disableAnswerControls()

                // Auto‑move to next question or submit
                if (!isSubmitted && !isQuizExpired) {
                    if (currentIndex < shuffledQuestions.size - 1) {
                        // Save current answer (maybe unanswered) and move
                        saveCurrentAnswer()
                        saveCurrentQuestionTimer()
                        currentIndex++
                        displayQuestion()
                    } else {
                        // Last question – submit
                        Toast.makeText(this@QuizAttemptActivity, "Time's up for last question. Submitting quiz.", Toast.LENGTH_SHORT).show()
                        submitQuiz()
                    }
                }
            }
        }.start()
    }

    private fun saveCurrentQuestionTimer() {
        // Cancel timer and save current remaining time
        questionTimer?.cancel()
        if (currentQuestionId.isNotEmpty()) {
            questionRemainingTimes[currentQuestionId] = currentRemainingSeconds
            if (currentRemainingSeconds <= 0) {
                questionTimedOut.add(currentQuestionId)
            }
        }
        questionTimer = null
    }

    // ---------- END TIMER HELPERS ----------

    private fun updateTimerUI(seconds: Long, label: String) {
        runOnUiThread {
            binding.tvTimerLabel.visibility = View.VISIBLE
            binding.tvTimer.visibility = View.VISIBLE
            binding.tvTimerLabel.text = label
            binding.tvTimer.text = formatDuration(seconds)
        }
    }

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

    private fun showSubmitConfirmation() {
        // Do not allow submission if quiz expired
        if (isQuizExpired) {
            Toast.makeText(this, "Quiz has already expired.", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Submit Quiz")
            .setMessage("Are you sure you want to submit the quiz? You cannot change your answers after submission.")
            .setPositiveButton("Yes, Submit") { _, _ ->
                submitQuiz()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun displayQuestion() {
        val q = shuffledQuestions[currentIndex]
        binding.tvQuestion.text = q.text

        // Media handling (unchanged)
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

        // Build answer UI
        val container = binding.radioGroupOptions
        container.removeAllViews()

        when (q.questionType) {
            "radio" -> {
                val radioGroup = RadioGroup(this)
                radioGroup.orientation = RadioGroup.VERTICAL
                q.options.forEachIndexed { idx, option ->
                    val rb = RadioButton(this)
                    rb.text = option
                    rb.tag = idx
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
            }
            "checkbox" -> {
                val linearLayout = LinearLayout(this)
                linearLayout.orientation = LinearLayout.VERTICAL
                q.options.forEachIndexed { idx, option ->
                    val cb = CheckBox(this)
                    cb.text = option
                    cb.tag = idx
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
            }
        }

        binding.tvProgress.text = "${currentIndex + 1}/${shuffledQuestions.size}"
        binding.tvMarks.text = "${q.points} marks"

        checkBookmarkStatus(q.questionId)

        // --- Determine if we should enable/disable controls ---
        val shouldDisable = isQuizExpired || questionTimedOut.contains(q.questionId)
        if (shouldDisable) {
            disableAnswerControls()
        } else {
            enableAnswerControls()
        }

        // Handle Next/Submit button
        val isLastQuestion = (currentIndex == shuffledQuestions.size - 1)
        if (isLastQuestion) {
            binding.btnNextOrSubmit.text = "Submit Quiz"
            binding.btnNextOrSubmit.setOnClickListener {
                if (isQuizExpired) {
                    Toast.makeText(this, "Quiz already expired.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                saveCurrentAnswer()
                saveCurrentQuestionTimer()
                showSubmitConfirmation()
            }
        } else {
            binding.btnNextOrSubmit.text = "Next"
            binding.btnNextOrSubmit.setOnClickListener {
                if (isQuizExpired) {
                    Toast.makeText(this, "Quiz already expired.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                saveCurrentAnswer()
                saveCurrentQuestionTimer()   // save timer state before moving
                currentIndex++
                displayQuestion()
                // Timer will start in displayQuestion
            }
        }

        // Start PER-QUESTION timer after displaying the question (if not expired and not whole quiz)
        if (timerType == "PER_QUESTION" && !isQuizExpired) {
            startQuestionTimer()
        }
        // For WHOLE_QUIZ, timer already running; nothing else.
    }

    private fun checkBookmarkStatus(questionId: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("bookmarks").document("${uid}_$questionId").get()
            .addOnSuccessListener { doc ->
                binding.btnBookmark.setText(if (doc.exists()) "Unbookmark" else "Bookmark")
            }
    }

    private fun toggleBookmark(questionId: String) {
        val uid = auth.currentUser?.uid ?: return
        val bookmarkRef = db.collection("bookmarks").document("${uid}_$questionId")
        bookmarkRef.get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                bookmarkRef.delete()
                Toast.makeText(this, "Bookmark removed", Toast.LENGTH_SHORT).show()
                binding.btnBookmark.text = "Bookmark"
            } else {
                bookmarkRef.set(mapOf(
                    "userId" to uid,
                    "questionId" to questionId,
                    "quizId" to quizId,
                    "quizTitle" to quizTitle
                ))
                Toast.makeText(this, "Question bookmarked", Toast.LENGTH_SHORT).show()
                binding.btnBookmark.text = "Unbookmark"
            }
        }
    }

    private fun saveCurrentAnswer() {
        // Safety check: if quiz expired or current question expired, do NOT save any changes.
        if (isQuizExpired) {
            return
        }
        val q = shuffledQuestions[currentIndex]
        if (questionTimedOut.contains(q.questionId)) {
            return   // question already expired, prevent changes
        }

        val container = binding.radioGroupOptions
        when (q.questionType) {
            "radio" -> {
                val radioGroup = container.getChildAt(0) as? RadioGroup
                val selectedId = radioGroup?.checkedRadioButtonId
                if (selectedId != null && selectedId != -1) {
                    val rb = radioGroup.findViewById<RadioButton>(selectedId)
                    val selectedIndex = rb.tag as Int
                    userAnswers[q.questionId] = selectedIndex
                    sharedPrefs.edit().putInt("${quizId}_${q.questionId}", selectedIndex).apply()
                } else {
                    userAnswers.remove(q.questionId)
                    sharedPrefs.edit().remove("${quizId}_${q.questionId}").apply()
                }
            }
            "checkbox" -> {
                val linearLayout = container.getChildAt(0) as? LinearLayout
                val selectedIndices = mutableListOf<Int>()
                for (i in 0 until (linearLayout?.childCount ?: 0)) {
                    val child = linearLayout?.getChildAt(i)
                    if (child is CheckBox && child.isChecked) {
                        selectedIndices.add(child.tag as Int)
                    }
                }
                if (selectedIndices.isNotEmpty()) {
                    userAnswers[q.questionId] = selectedIndices
                    val json = selectedIndices.joinToString(",")
                    sharedPrefs.edit().putString("${quizId}_${q.questionId}", json).apply()
                } else {
                    userAnswers.remove(q.questionId)
                    sharedPrefs.edit().remove("${quizId}_${q.questionId}").apply()
                }
            }
            "descriptive" -> {
                val editText = container.getChildAt(0) as? EditText
                val text = editText?.text.toString().trim()
                if (text.isNotEmpty()) {
                    userAnswers[q.questionId] = text
                    sharedPrefs.edit().putString("${quizId}_${q.questionId}", text).apply()
                } else {
                    userAnswers.remove(q.questionId)
                    sharedPrefs.edit().remove("${quizId}_${q.questionId}").apply()
                }
            }
        }
    }

    private fun submitQuiz() {
        if (isSubmitted) return
        isSubmitted = true
        wholeQuizTimer?.cancel()
        questionTimer?.cancel()
        mediaPlayer?.release()

        // Disable all controls before scoring to prevent any last‑minute changes
        disableAnswerControls()

        val totalPossible = questions.sumOf { it.points }
        val userId = auth.currentUser?.uid ?: run {
            Toast.makeText(this, "Please log in", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

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

        db.collection("quizzes").document(quizId).collection("attempts").document(userId)
            .set(attemptData)
            .addOnSuccessListener {
                for (q in questions) {
                    sharedPrefs.edit().remove("${quizId}_${q.questionId}").apply()
                }

                val resultData = mutableMapOf<String, Any>()
                resultData["userId"] = userId
                resultData["quizId"] = quizId
                resultData["score"] = score
                resultData["totalScore"] = totalPossible
                resultData["submittedAt"] = endTime
                db.collection("results").add(resultData)

                updateJoinedQuiz(userId, score.toInt(), totalPossible, endTime)

                CheatLogger.clearViolations(applicationContext)

                val intent = Intent(this, SubmissionSuccessActivity::class.java)
                intent.putExtra("score", score)
                intent.putExtra("total", totalPossible)
                intent.putExtra("submitTime", endTime)
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save attempt: ${e.message}", Toast.LENGTH_SHORT).show()
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