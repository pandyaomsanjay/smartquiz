package com.smartquiz

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.smartquiz.databinding.ActivityQuizCreationBinding
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

class QuizCreationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQuizCreationBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private val questionsList = mutableListOf<Question>()
    private lateinit var adapter: QuestionPreviewAdapter
    private val calendar = Calendar.getInstance()
    private var deadlineTimestamp = 0L

    // Start time
    private var startTimeTimestamp = 0L
    private val startCalendar = Calendar.getInstance()

    // Draft mode flags
    private var isEditingDraft = false
    private var draftQuizId: String? = null
    private var isDraftMode = false
    private var originalCreatedAt: Long = 0L

    // ---------- Scenario editor state ----------
    private val pendingSubQuestions = mutableListOf<Question>()

    private val titleCheckHandler = Handler(Looper.getMainLooper())
    private var titleCheckRunnable: Runnable? = null

    // ------------------------------------------------------------------------
    // LIFECYCLE
    // ------------------------------------------------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuizCreationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_back)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        draftQuizId = intent.getStringExtra("quizId")
        isDraftMode = intent.getBooleanExtra("isDraft", false)
        isEditingDraft = draftQuizId != null && isDraftMode

        if (isEditingDraft) {
            supportActionBar?.title = "Edit Draft"
            loadDraftData(draftQuizId!!)
        } else {
            supportActionBar?.title = "Create Quiz"
            binding.radioPrivate.isChecked = true
            binding.radioFixedOrder.isChecked = true
            binding.switchShowScore.isChecked = true
            binding.switchNegativeMarking.isChecked = false
            binding.etNegativeValue.setText("0.25")
            binding.etTotalTime.setText("00:30:00")
            binding.etPerQuestionTime.setText("00:01:00")
        }

        // Date/time pickers
        binding.etDeadline.setOnClickListener { showDateTimePicker() }
        binding.etStartTime.setOnClickListener { showStartDateTimePicker() }

        binding.btnAddQuestion.setOnClickListener { showAddQuestionDialog(null) }
        binding.btnSaveDraft.setOnClickListener { saveQuizAsDraft() }
        binding.btnSaveQuiz.setOnClickListener { showSaveConfirmation() }

        // NEW: live update of "Questions added: X / Y required" while typing.
        binding.etConfiguredQuestionCount.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                updateQuestionsCount()
            }
        })

        // NEW: clamp to a positive integer on focus lost.
        binding.etConfiguredQuestionCount.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val txt = binding.etConfiguredQuestionCount.text.toString().trim()
                if (txt.isEmpty() || (txt.toIntOrNull() ?: 0) <= 0) {
                    binding.etConfiguredQuestionCount.setText("")
                }
            }
        }

        binding.radioGroupTimerType.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioNoTimer -> {
                    binding.inputLayoutTotalTime.visibility = View.GONE
                    binding.inputLayoutPerQuestionTime.visibility = View.GONE
                }
                R.id.radioWholeQuizTimer -> {
                    binding.inputLayoutTotalTime.visibility = View.VISIBLE
                    binding.inputLayoutPerQuestionTime.visibility = View.GONE
                }
                R.id.radioPerQuestionTimer -> {
                    binding.inputLayoutTotalTime.visibility = View.GONE
                    binding.inputLayoutPerQuestionTime.visibility = View.VISIBLE
                }
            }
        }
        binding.inputLayoutTotalTime.visibility = View.GONE
        binding.inputLayoutPerQuestionTime.visibility = View.GONE

        adapter = QuestionPreviewAdapter(
            questions = questionsList,
            onEditClick = { question -> showAddQuestionDialog(question) },
            onDeleteClick = { question -> deleteQuestion(question) }
        )
        binding.rvQuestionPreview.layoutManager = LinearLayoutManager(this)
        binding.rvQuestionPreview.adapter = adapter
        updateQuestionsCount()

        binding.etQuizTitle.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) checkTitleDuplicate()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        return true
    }

    private fun safeFinish() {
        if (!isFinishing && !isDestroyed) {
            Handler(Looper.getMainLooper()).post {
                if (!isFinishing && !isDestroyed) finish()
            }
        }
    }

    // ------------------------------------------------------------------------
    // LOAD DRAFT DATA
    // ------------------------------------------------------------------------
    private fun loadDraftData(quizId: String) {
        db.collection("quizzes").document(quizId).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Toast.makeText(this, "Draft not found", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }
                val quiz = doc.toObject(Quiz::class.java) ?: run {
                    Toast.makeText(this, "Failed to parse draft", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }

                originalCreatedAt = quiz.createdAt

                binding.etQuizTitle.setText(quiz.title)
                binding.etQuizDescription.setText(quiz.description)

                // NEW: restore configured question count.
                if (quiz.configuredQuestionCount > 0) {
                    binding.etConfiguredQuestionCount.setText(
                        quiz.configuredQuestionCount.toString()
                    )
                }

                if (quiz.visibility == "public") binding.radioPublic.isChecked = true
                else binding.radioPrivate.isChecked = true

                startTimeTimestamp = quiz.startTime
                if (quiz.startTime > 0) {
                    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    binding.etStartTime.setText(fmt.format(Date(quiz.startTime)))
                }

                deadlineTimestamp = quiz.deadline
                if (quiz.deadline > 0) {
                    val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    binding.etDeadline.setText(format.format(Date(quiz.deadline)))
                }

                binding.switchNegativeMarking.isChecked = quiz.negativeMarking
                binding.etNegativeValue.setText(quiz.negativeMarkingValue.toString())
                binding.switchShowScore.isChecked = quiz.showScoreAfterSubmission

                when (quiz.timerType) {
                    "WHOLE_QUIZ" -> {
                        binding.radioWholeQuizTimer.isChecked = true
                        binding.inputLayoutTotalTime.visibility = View.VISIBLE
                        binding.etTotalTime.setText(formatDuration(quiz.totalTimeSeconds))
                    }
                    "PER_QUESTION" -> {
                        binding.radioPerQuestionTimer.isChecked = true
                        binding.inputLayoutPerQuestionTime.visibility = View.VISIBLE
                        binding.etPerQuestionTime.setText(formatDuration(quiz.timePerQuestionSeconds))
                    }
                    else -> binding.radioNoTimer.isChecked = true
                }

                when (quiz.randomizationMode) {
                    "RANDOM_QUESTION_ORDER" -> binding.radioRandomQuestionOrder.isChecked = true
                    "RANDOM_QUESTION_AND_OPTION_ORDER" -> binding.radioRandomQuestionAndOptionOrder.isChecked = true
                    else -> binding.radioFixedOrder.isChecked = true
                }

                loadQuestionsFromFirestore(quizId)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load draft: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun loadQuestionsFromFirestore(quizId: String) {
        db.collection("quizzes").document(quizId).collection("questions")
            .get()
            .addOnSuccessListener { questionDocs ->
                val loadedQuestions = mutableListOf<Question>()
                val tasks = questionDocs.map { qDoc ->
                    var q = qDoc.toObject(Question::class.java).apply { questionId = qDoc.id }

                    // Reconstruct scenario sub-questions
                    if (q.questionType == "scenario") {
                        val rawSubs = qDoc.get("subQuestions") as? List<*>
                        val subs = rawSubs?.mapNotNull { raw ->
                            @Suppress("UNCHECKED_CAST")
                            val m = raw as? Map<String, Any> ?: return@mapNotNull null
                            Question(
                                questionId = m["questionId"] as? String ?: "",
                                text = m["text"] as? String ?: "",
                                options = (m["options"] as? List<*>)?.mapNotNull { it as? String }
                                    ?: emptyList(),
                                questionType = m["questionType"] as? String ?: "radio",
                                points = (m["points"] as? Long)?.toInt() ?: 1,
                                imageUrl = m["imageUrl"] as? String ?: "",
                                audioUrl = m["audioUrl"] as? String ?: "",
                                videoUrl = m["videoUrl"] as? String ?: ""
                            )
                        } ?: emptyList()
                        q = q.copy(subQuestions = subs)
                    }

                    // Fetch private answers
                    db.collection("quizzes").document(quizId)
                        .collection("questions_private").document(qDoc.id)
                        .get()
                        .continueWith { task ->
                            if (task.isSuccessful && task.result.exists()) {
                                val data = task.result

                                if (q.questionType == "scenario") {
                                    @Suppress("UNCHECKED_CAST")
                                    val answersMap = data.get("subAnswers")
                                            as? Map<String, Map<String, Any>>
                                    val merged = q.subQuestions.map { sub ->
                                        val a = answersMap?.get(sub.questionId)
                                        when (sub.questionType) {
                                            "radio" -> sub.copy(
                                                correctAnswerIndex = (a?.get("correctAnswerIndex")
                                                        as? Long)?.toInt() ?: 0
                                            )
                                            "checkbox" -> sub.copy(
                                                correctAnswerIndices = (a?.get("correctAnswerIndices")
                                                        as? List<*>)?.mapNotNull {
                                                    (it as? Long)?.toInt()
                                                } ?: emptyList()
                                            )
                                            else -> sub.copy(
                                                correctAnswerText =
                                                    a?.get("correctAnswerText") as? String ?: ""
                                            )
                                        }
                                    }
                                    q = q.copy(subQuestions = merged)
                                } else {
                                    when (q.questionType) {
                                        "radio" -> q.correctAnswerIndex =
                                            data.getLong("correctAnswerIndex")?.toInt() ?: 0
                                        "checkbox" -> {
                                            val rawList = data.get("correctAnswerIndices") as? List<*>
                                            q.correctAnswerIndices = rawList?.mapNotNull {
                                                when (it) {
                                                    is Int -> it
                                                    is Long -> it.toInt()
                                                    else -> null
                                                }
                                            } ?: emptyList()
                                        }
                                        "descriptive" -> q.correctAnswerText =
                                            data.getString("correctAnswerText") ?: ""
                                    }
                                }
                            }
                            q
                        }
                }
                com.google.android.gms.tasks.Tasks.whenAllComplete(tasks)
                    .addOnCompleteListener {
                        tasks.forEach { task ->
                            if (task.isSuccessful) task.result?.let { loadedQuestions.add(it) }
                        }
                        questionsList.clear()
                        questionsList.addAll(loadedQuestions)
                        updateQuestionsCount()
                        adapter.updateList(questionsList)
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load questions: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ------------------------------------------------------------------------
    // SAVE AS DRAFT
    // ------------------------------------------------------------------------
    private fun saveQuizAsDraft() {
        val quiz = buildQuiz(status = "DRAFT", generateCode = false)
        if (quiz == null) {
            Toast.makeText(this, "Failed to build quiz data", Toast.LENGTH_SHORT).show()
            return
        }
        val userId = auth.currentUser?.uid ?: run {
            Toast.makeText(this, "Please log in", Toast.LENGTH_SHORT).show()
            return
        }

        if (isEditingDraft && draftQuizId != null) {
            val updateMap = buildUpdateMap(quiz, userId, status = "DRAFT")

            db.collection("quizzes").document(draftQuizId!!)
                .set(updateMap, SetOptions.merge())
                .addOnSuccessListener {
                    saveQuestionsToFirestore(draftQuizId!!) {
                        Toast.makeText(this, "Draft updated successfully", Toast.LENGTH_SHORT).show()
                        goToDraftQuizzes()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to update draft: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            return
        }

        val newQuiz = quiz.copy(
            creatorId = userId,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        db.collection("quizzes").add(newQuiz)
            .addOnSuccessListener { docRef ->
                draftQuizId = docRef.id
                isEditingDraft = true
                originalCreatedAt = newQuiz.createdAt
                saveQuestionsToFirestore(docRef.id) {
                    Toast.makeText(this, "Quiz saved as draft successfully", Toast.LENGTH_SHORT).show()
                    goToDraftQuizzes()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save draft: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ------------------------------------------------------------------------
    // PUBLISH QUIZ
    // ------------------------------------------------------------------------
    private fun saveQuiz() {
        if (!validateQuiz()) return

        val quiz = buildQuiz(status = "PUBLISHED", generateCode = true)
        if (quiz == null) {
            Toast.makeText(this, "Failed to build quiz data", Toast.LENGTH_SHORT).show()
            return
        }
        val userId = auth.currentUser?.uid ?: run {
            Toast.makeText(this, "Please log in", Toast.LENGTH_SHORT).show()
            return
        }

        val updateMap = buildUpdateMap(quiz, userId, status = "PUBLISHED")

        val docRef = if (isEditingDraft && draftQuizId != null) {
            db.collection("quizzes").document(draftQuizId!!)
        } else {
            db.collection("quizzes").document()
        }

        docRef.set(updateMap, SetOptions.merge())
            .addOnSuccessListener {
                val quizId = if (isEditingDraft && draftQuizId != null) draftQuizId!! else docRef.id
                saveQuestionsToFirestore(quizId) {
                    val message = if (quiz.visibility == "private")
                        "Quiz saved! Code: ${quiz.quizCode}"
                    else
                        "Public quiz saved!"
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    val intent = Intent(this, QuizStatsActivity::class.java)
                    intent.putExtra("quizId", quizId)
                    intent.putExtra("quizTitle", quiz.title)
                    startActivity(intent)
                    safeFinish()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error publishing quiz: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun buildUpdateMap(quiz: Quiz, userId: String, status: String): MutableMap<String, Any> {
        val map = mutableMapOf<String, Any>(
            "title" to quiz.title,
            "description" to quiz.description,
            "visibility" to quiz.visibility,
            "startTime" to quiz.startTime,
            "deadline" to quiz.deadline,
            "negativeMarking" to quiz.negativeMarking,
            "negativeMarkingValue" to quiz.negativeMarkingValue,
            "hasImageQuestions" to quiz.hasImageQuestions,
            "hasAudioQuestions" to quiz.hasAudioQuestions,
            "hasVideoQuestions" to quiz.hasVideoQuestions,
            "timerType" to quiz.timerType,
            "totalTimeSeconds" to quiz.totalTimeSeconds,
            "timePerQuestionSeconds" to quiz.timePerQuestionSeconds,
            "randomizationMode" to quiz.randomizationMode,
            "showScoreAfterSubmission" to quiz.showScoreAfterSubmission,
            "status" to status,
            "updatedAt" to System.currentTimeMillis(),
            "totalQuestions" to questionsList.sumOf { it.questionCount() },
            "configuredQuestionCount" to (binding.etConfiguredQuestionCount.text.toString().toIntOrNull() ?: 0),
            "creatorId" to userId,
            "archived" to false
        )
        if (quiz.quizCode.isNotEmpty()) {
            map["quizCode"] = quiz.quizCode
        }
        return map
    }

    private fun buildQuiz(status: String, generateCode: Boolean): Quiz? {
        val title = binding.etQuizTitle.text.toString().trim()
        if (status != "DRAFT" && title.isEmpty()) {
            Toast.makeText(this, "Quiz title is required", Toast.LENGTH_SHORT).show()
            return null
        }
        val description = binding.etQuizDescription.text.toString().trim()
        val visibility = if (binding.radioPublic.isChecked) "public" else "private"
        val negativeMarking = binding.switchNegativeMarking?.isChecked ?: false
        val negativeMarkingValue = binding.etNegativeValue?.text.toString().toFloatOrNull() ?: 0.25f

        val timerType = when (binding.radioGroupTimerType.checkedRadioButtonId) {
            R.id.radioWholeQuizTimer -> "WHOLE_QUIZ"
            R.id.radioPerQuestionTimer -> "PER_QUESTION"
            else -> "NONE"
        }
        val totalTimeSeconds: Long
        val perQuestionSeconds: Long
        when (timerType) {
            "WHOLE_QUIZ" -> {
                totalTimeSeconds = parseDurationToSeconds(binding.etTotalTime.text.toString()) ?: 0
                perQuestionSeconds = 0
            }
            "PER_QUESTION" -> {
                perQuestionSeconds = parseDurationToSeconds(binding.etPerQuestionTime.text.toString()) ?: 0
                totalTimeSeconds = 0
            }
            else -> {
                totalTimeSeconds = 0
                perQuestionSeconds = 0
            }
        }

        val randomizationMode = when (binding.radioGroupRandomization.checkedRadioButtonId) {
            R.id.radioRandomQuestionOrder -> "RANDOM_QUESTION_ORDER"
            R.id.radioRandomQuestionAndOptionOrder -> "RANDOM_QUESTION_AND_OPTION_ORDER"
            else -> "FIXED_ORDER"
        }

        val showScoreAfterSubmission = binding.switchShowScore.isChecked
        val quizCode = if (generateCode && status != "DRAFT" && visibility == "private") {
            generateUniqueQuizCode()
        } else {
            ""
        }

        val configuredCount = binding.etConfiguredQuestionCount.text.toString().toIntOrNull() ?: 0
        val actualCount = questionsList.sumOf { it.questionCount() }

        return Quiz(
            title = title,
            description = description,
            quizCode = quizCode,
            creatorId = "",
            visibility = visibility,
            createdAt = 0L,
            totalQuestions = actualCount,
            timerSeconds = 0,
            startTime = startTimeTimestamp,
            deadline = deadlineTimestamp,
            negativeMarking = negativeMarking,
            negativeMarkingValue = negativeMarkingValue,
            hasImageQuestions = questionsList.any {
                if (it.isScenario) it.subQuestions.any { s -> s.imageUrl.isNotEmpty() }
                else it.imageUrl.isNotEmpty()
            },
            hasAudioQuestions = questionsList.any {
                if (it.isScenario) it.subQuestions.any { s -> s.audioUrl.isNotEmpty() }
                else it.audioUrl.isNotEmpty()
            },
            hasVideoQuestions = questionsList.any {
                if (it.isScenario) it.subQuestions.any { s -> s.videoUrl.isNotEmpty() }
                else it.videoUrl.isNotEmpty()
            },
            timerType = timerType,
            totalTimeSeconds = totalTimeSeconds,
            timePerQuestionSeconds = perQuestionSeconds,
            randomizationMode = randomizationMode,
            showScoreAfterSubmission = showScoreAfterSubmission,
            status = status,
            updatedAt = System.currentTimeMillis(),
            archived = false,
            configuredQuestionCount = configuredCount
        )
    }

    // ------------------------------------------------------------------------
    // SAVE QUESTIONS
    // ------------------------------------------------------------------------
    private fun saveQuestionsToFirestore(quizId: String, onComplete: () -> Unit) {
        db.collection("quizzes").document(quizId).collection("questions")
            .get()
            .addOnSuccessListener { querySnapshot ->
                val batch = db.batch()
                for (doc in querySnapshot.documents) {
                    batch.delete(doc.reference)
                    val privateRef = db.collection("quizzes").document(quizId)
                        .collection("questions_private").document(doc.id)
                    batch.delete(privateRef)
                }
                batch.commit().addOnSuccessListener {
                    writeQuestions(quizId, onComplete)
                }.addOnFailureListener { e ->
                    Toast.makeText(this, "Error clearing old questions: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error fetching old questions: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun writeQuestions(quizId: String, onComplete: () -> Unit) {
        val batch = db.batch()
        for (q in questionsList) {
            val publicRef = db.collection("quizzes").document(quizId)
                .collection("questions").document()
            val privateRef = db.collection("quizzes").document(quizId)
                .collection("questions_private").document(publicRef.id)

            if (q.isScenario) {
                // ---------- Public scenario doc (no correct answers) ----------
                val publicSubs = q.subQuestions.map { sub ->
                    mapOf(
                        "questionId" to sub.questionId,
                        "text" to sub.text,
                        "options" to sub.options,
                        "questionType" to sub.questionType,
                        "points" to sub.points,
                        "imageUrl" to sub.imageUrl,
                        "audioUrl" to sub.audioUrl,
                        "videoUrl" to sub.videoUrl
                    )
                }
                batch.set(publicRef, mapOf(
                    "questionType" to "scenario",
                    "scenarioText" to q.scenarioText,
                    "subQuestions" to publicSubs,
                    "points" to q.totalPoints()
                ))

                // ---------- Private sub-answers keyed by sub-questionId ----------
                val privateAnswers = q.subQuestions.associate { sub ->
                    sub.questionId to when (sub.questionType) {
                        "radio" -> mapOf("correctAnswerIndex" to sub.correctAnswerIndex)
                        "checkbox" -> mapOf("correctAnswerIndices" to sub.correctAnswerIndices)
                        else -> mapOf("correctAnswerText" to sub.correctAnswerText)
                    }
                }
                batch.set(privateRef, mapOf("subAnswers" to privateAnswers))
            } else {
                // ---------- Normal question ----------
                batch.set(publicRef, mapOf(
                    "text" to q.text,
                    "options" to q.options,
                    "questionType" to q.questionType,
                    "points" to q.points,
                    "imageUrl" to q.imageUrl,
                    "audioUrl" to q.audioUrl,
                    "videoUrl" to q.videoUrl
                ))
                val privateData = when (q.questionType) {
                    "radio" -> mapOf("correctAnswerIndex" to q.correctAnswerIndex)
                    "checkbox" -> mapOf("correctAnswerIndices" to q.correctAnswerIndices)
                    "descriptive" -> mapOf("correctAnswerText" to q.correctAnswerText)
                    else -> emptyMap<String, Any>()
                }
                batch.set(privateRef, privateData)
            }
        }
        batch.commit().addOnSuccessListener { onComplete() }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error saving questions: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun generateUniqueQuizCode(): String {
        return Random.nextInt(100000, 999999).toString()
    }

    // ------------------------------------------------------------------------
    // BACK HANDLING
    // ------------------------------------------------------------------------
    override fun onBackPressed() {
        if (hasUnsavedChanges()) {
            AlertDialog.Builder(this)
                .setTitle("Save your quiz as a draft?")
                .setMessage("You have unsaved changes. Would you like to save them as a draft?")
                .setPositiveButton("Save as Draft") { _, _ -> saveQuizAsDraft() }
                .setNegativeButton("Discard") { _, _ -> super.onBackPressed() }
                .setNeutralButton("Cancel", null)
                .show()
        } else {
            super.onBackPressed()
        }
    }

    private fun hasUnsavedChanges(): Boolean {
        return binding.etQuizTitle.text.toString().isNotBlank() || questionsList.isNotEmpty()
    }

    private fun goToDraftQuizzes() {
        startActivity(Intent(this, DraftQuizzesActivity::class.java))
        safeFinish()
    }

    // ------------------------------------------------------------------------
    // DATE/TIME PICKERS
    // ------------------------------------------------------------------------
    private fun showStartDateTimePicker() {
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                startCalendar.set(year, month, dayOfMonth)
                TimePickerDialog(
                    this,
                    { _, hourOfDay, minute ->
                        startCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                        startCalendar.set(Calendar.MINUTE, minute)
                        startCalendar.set(Calendar.SECOND, 0)
                        startTimeTimestamp = startCalendar.timeInMillis
                        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        binding.etStartTime.setText(fmt.format(startCalendar.time))
                    },
                    startCalendar.get(Calendar.HOUR_OF_DAY),
                    startCalendar.get(Calendar.MINUTE),
                    true
                ).show()
            },
            startCalendar.get(Calendar.YEAR),
            startCalendar.get(Calendar.MONTH),
            startCalendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showDateTimePicker() {
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                TimePickerDialog(
                    this,
                    { _, hourOfDay, minute ->
                        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                        calendar.set(Calendar.MINUTE, minute)
                        deadlineTimestamp = calendar.timeInMillis
                        val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        binding.etDeadline.setText(format.format(calendar.time))
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    // ------------------------------------------------------------------------
    // TITLE DUPLICATE CHECK
    // ------------------------------------------------------------------------
    private fun normalizeTitle(title: String): String {
        return title.trim()
            .replace(Regex("\\s+"), " ")
            .lowercase(Locale.getDefault())
    }

    private fun checkTitleDuplicate() {
        val title = binding.etQuizTitle.text.toString().trim()
        if (title.isEmpty()) {
            binding.tvTitleFeedback.visibility = View.GONE
            return
        }

        val normalized = normalizeTitle(title)
        val creatorId = auth.currentUser?.uid ?: return

        titleCheckRunnable?.let { titleCheckHandler.removeCallbacks(it) }

        titleCheckRunnable = Runnable {
            db.collection("quizzes")
                .whereEqualTo("creatorId", creatorId)
                .get()
                .addOnSuccessListener { docs ->
                    val duplicate = docs.any { doc ->
                        val existingTitle = doc.getString("title") ?: ""
                        normalizeTitle(existingTitle) == normalized
                    }
                    if (duplicate) {
                        binding.tvTitleFeedback.text = "⚠ Quiz title already exists"
                        binding.tvTitleFeedback.setTextColor(getColor(R.color.error))
                        binding.tvTitleFeedback.visibility = View.VISIBLE
                    } else {
                        binding.tvTitleFeedback.text = "✓ Title available"
                        binding.tvTitleFeedback.setTextColor(getColor(R.color.success))
                        binding.tvTitleFeedback.visibility = View.VISIBLE
                    }
                }
                .addOnFailureListener {
                    binding.tvTitleFeedback.visibility = View.GONE
                }
        }
        titleCheckHandler.postDelayed(titleCheckRunnable!!, 500)
    }

    private fun isTitleDuplicate(title: String): Boolean {
        val normalized = normalizeTitle(title)
        val creatorId = auth.currentUser?.uid ?: return false
        return try {
            val docs = com.google.android.gms.tasks.Tasks.await(
                db.collection("quizzes")
                    .whereEqualTo("creatorId", creatorId)
                    .get()
            )
            docs.any { doc ->
                val existingTitle = doc.getString("title") ?: ""
                normalizeTitle(existingTitle) == normalized
            }
        } catch (e: Exception) {
            false
        }
    }

    // ------------------------------------------------------------------------
    // ADD / EDIT QUESTION DIALOG — routes SCENARIO to the scenario editor
    // ------------------------------------------------------------------------
    private fun showAddQuestionDialog(existingQuestion: Question?) {
        // If editing an existing scenario, jump straight to the scenario editor.
        if (existingQuestion?.isScenario == true) {
            showAddScenarioDialog(existingQuestion)
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_add_question, null)
        val etQuestionText = dialogView.findViewById<EditText>(R.id.etQuestionText)
        val etOption1 = dialogView.findViewById<EditText>(R.id.etOption1)
        val etOption2 = dialogView.findViewById<EditText>(R.id.etOption2)
        val etOption3 = dialogView.findViewById<EditText>(R.id.etOption3)
        val etOption4 = dialogView.findViewById<EditText>(R.id.etOption4)
        val spinnerType = dialogView.findViewById<Spinner>(R.id.spinnerQuestionType)
        val spinnerCorrect = dialogView.findViewById<Spinner>(R.id.spinnerCorrect)
        val llOptions = dialogView.findViewById<LinearLayout>(R.id.llOptionsContainer)
        val llDescriptive = dialogView.findViewById<LinearLayout>(R.id.llDescriptiveContainer)
        val llCorrectCheckbox = dialogView.findViewById<LinearLayout>(R.id.llCorrectCheckbox)
        val tvCorrectRadio = dialogView.findViewById<TextView>(R.id.tvCorrectRadio)
        val cbCorrect1 = dialogView.findViewById<CheckBox>(R.id.cbCorrect1)
        val cbCorrect2 = dialogView.findViewById<CheckBox>(R.id.cbCorrect2)
        val cbCorrect3 = dialogView.findViewById<CheckBox>(R.id.cbCorrect3)
        val cbCorrect4 = dialogView.findViewById<CheckBox>(R.id.cbCorrect4)
        val etCorrectAnswerText = dialogView.findViewById<EditText>(R.id.etCorrectAnswerText)
        val etPoints = dialogView.findViewById<EditText>(R.id.etPoints)
        val etImageUrl = dialogView.findViewById<EditText>(R.id.etImageUrl)
        val etAudioUrl = dialogView.findViewById<EditText>(R.id.etAudioUrl)
        val etVideoUrl = dialogView.findViewById<EditText>(R.id.etVideoUrl)

        if (existingQuestion != null) {
            etQuestionText.setText(existingQuestion.text)
            val typeIndex = when (existingQuestion.questionType) {
                "radio" -> 0
                "checkbox" -> 1
                "descriptive" -> 2
                else -> 0
            }
            spinnerType.setSelection(typeIndex)
            if (existingQuestion.options.isNotEmpty()) {
                etOption1.setText(existingQuestion.options.getOrNull(0) ?: "")
                etOption2.setText(existingQuestion.options.getOrNull(1) ?: "")
                etOption3.setText(existingQuestion.options.getOrNull(2) ?: "")
                etOption4.setText(existingQuestion.options.getOrNull(3) ?: "")
            }
            when (existingQuestion.questionType) {
                "radio" -> spinnerCorrect.setSelection(existingQuestion.correctAnswerIndex)
                "checkbox" -> {
                    for (idx in existingQuestion.correctAnswerIndices) {
                        when (idx) {
                            0 -> cbCorrect1.isChecked = true
                            1 -> cbCorrect2.isChecked = true
                            2 -> cbCorrect3.isChecked = true
                            3 -> cbCorrect4.isChecked = true
                        }
                    }
                }
                "descriptive" -> etCorrectAnswerText.setText(existingQuestion.correctAnswerText)
            }
            etPoints.setText(existingQuestion.points.toString())
            etImageUrl.setText(existingQuestion.imageUrl)
            etAudioUrl.setText(existingQuestion.audioUrl)
            etVideoUrl.setText(existingQuestion.videoUrl)

            when (existingQuestion.questionType) {
                "radio" -> {
                    llOptions.visibility = View.VISIBLE
                    llDescriptive.visibility = View.GONE
                    tvCorrectRadio.visibility = View.VISIBLE
                    spinnerCorrect.visibility = View.VISIBLE
                    llCorrectCheckbox.visibility = View.GONE
                }
                "checkbox" -> {
                    llOptions.visibility = View.VISIBLE
                    llDescriptive.visibility = View.GONE
                    tvCorrectRadio.visibility = View.GONE
                    spinnerCorrect.visibility = View.GONE
                    llCorrectCheckbox.visibility = View.VISIBLE
                }
                "descriptive" -> {
                    llOptions.visibility = View.GONE
                    llDescriptive.visibility = View.VISIBLE
                    tvCorrectRadio.visibility = View.GONE
                    spinnerCorrect.visibility = View.GONE
                    llCorrectCheckbox.visibility = View.GONE
                }
            }
        }

        // The spinner contains Radio / Checkbox / Descriptive / Scenario.
        spinnerType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                when (position) {
                    0 -> {
                        llOptions.visibility = View.VISIBLE
                        llDescriptive.visibility = View.GONE
                        tvCorrectRadio.visibility = View.VISIBLE
                        spinnerCorrect.visibility = View.VISIBLE
                        llCorrectCheckbox.visibility = View.GONE
                    }
                    1 -> {
                        llOptions.visibility = View.VISIBLE
                        llDescriptive.visibility = View.GONE
                        tvCorrectRadio.visibility = View.GONE
                        spinnerCorrect.visibility = View.GONE
                        llCorrectCheckbox.visibility = View.VISIBLE
                    }
                    2 -> {
                        llOptions.visibility = View.GONE
                        llDescriptive.visibility = View.VISIBLE
                        tvCorrectRadio.visibility = View.GONE
                        spinnerCorrect.visibility = View.GONE
                        llCorrectCheckbox.visibility = View.GONE
                    }
                    3 -> {
                        // Scenario — hide all sub-fields; editor opens on submit.
                        llOptions.visibility = View.GONE
                        llDescriptive.visibility = View.GONE
                        tvCorrectRadio.visibility = View.GONE
                        spinnerCorrect.visibility = View.GONE
                        llCorrectCheckbox.visibility = View.GONE
                        Toast.makeText(
                            this@QuizCreationActivity,
                            "Press Add/Update to open the Scenario editor",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existingQuestion != null) "Edit Question" else "Add Question")
            .setView(dialogView)
            .setPositiveButton(if (existingQuestion != null) "Update" else "Add") { _, _ ->
                // Route to scenario editor if the user chose Scenario.
                if (spinnerType.selectedItemPosition == 3) {
                    dialogView.post { showAddScenarioDialog(null) }
                    return@setPositiveButton
                }

                val text = etQuestionText.text.toString().trim()
                val type = when (spinnerType.selectedItemPosition) {
                    0 -> "radio"
                    1 -> "checkbox"
                    else -> "descriptive"
                }
                val points = etPoints.text.toString().toIntOrNull() ?: 0
                val imageUrl = etImageUrl.text.toString().trim()
                val audioUrl = etAudioUrl.text.toString().trim()
                val videoUrl = etVideoUrl.text.toString().trim()

                if (points <= 0) {
                    Toast.makeText(this, "Points must be greater than 0", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                when (type) {
                    "radio" -> {
                        val options = listOf(
                            etOption1.text.toString().trim(),
                            etOption2.text.toString().trim(),
                            etOption3.text.toString().trim(),
                            etOption4.text.toString().trim()
                        )
                        val correctIndex = spinnerCorrect.selectedItemPosition
                        if (text.isNotBlank() && options.all { it.isNotBlank() }) {
                            val newQuestion = Question(
                                questionId = existingQuestion?.questionId
                                    ?: System.currentTimeMillis().toString(),
                                text = text,
                                options = options,
                                questionType = type,
                                correctAnswerIndex = correctIndex,
                                points = points,
                                imageUrl = imageUrl,
                                audioUrl = audioUrl,
                                videoUrl = videoUrl
                            )
                            addOrUpdateQuestion(newQuestion, existingQuestion)
                        } else {
                            Toast.makeText(this, "Fill all required fields", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "checkbox" -> {
                        val options = listOf(
                            etOption1.text.toString().trim(),
                            etOption2.text.toString().trim(),
                            etOption3.text.toString().trim(),
                            etOption4.text.toString().trim()
                        )
                        val correctIndices = mutableListOf<Int>()
                        if (cbCorrect1.isChecked) correctIndices.add(0)
                        if (cbCorrect2.isChecked) correctIndices.add(1)
                        if (cbCorrect3.isChecked) correctIndices.add(2)
                        if (cbCorrect4.isChecked) correctIndices.add(3)
                        if (text.isNotBlank() && options.all { it.isNotBlank() } && correctIndices.isNotEmpty()) {
                            val newQuestion = Question(
                                questionId = existingQuestion?.questionId
                                    ?: System.currentTimeMillis().toString(),
                                text = text,
                                options = options,
                                questionType = type,
                                correctAnswerIndices = correctIndices,
                                points = points,
                                imageUrl = imageUrl,
                                audioUrl = audioUrl,
                                videoUrl = videoUrl
                            )
                            addOrUpdateQuestion(newQuestion, existingQuestion)
                        } else {
                            Toast.makeText(this, "Fill all fields and select at least one correct", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "descriptive" -> {
                        val correctText = etCorrectAnswerText.text.toString().trim()
                        if (text.isNotBlank() && correctText.isNotBlank()) {
                            val newQuestion = Question(
                                questionId = existingQuestion?.questionId
                                    ?: System.currentTimeMillis().toString(),
                                text = text,
                                options = emptyList(),
                                questionType = type,
                                correctAnswerText = correctText,
                                points = points,
                                imageUrl = imageUrl,
                                audioUrl = audioUrl,
                                videoUrl = videoUrl
                            )
                            addOrUpdateQuestion(newQuestion, existingQuestion)
                        } else {
                            Toast.makeText(this, "Fill question text and correct answer", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
    }

    // ------------------------------------------------------------------------
    // SCENARIO EDITOR
    // ------------------------------------------------------------------------
    private fun showAddScenarioDialog(existing: Question?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_scenario, null)
        val etScenarioText = dialogView.findViewById<EditText>(R.id.etScenarioText)
        val llSub = dialogView.findViewById<LinearLayout>(R.id.llSubQuestions)
        val btnAddSub = dialogView.findViewById<Button>(R.id.btnAddSubQuestion)
        val tvCount = dialogView.findViewById<TextView>(R.id.tvScenarioCount)

        pendingSubQuestions.clear()
        if (existing?.isScenario == true) {
            etScenarioText.setText(existing.scenarioText)
            pendingSubQuestions.addAll(existing.subQuestions)
        }

        fun refreshList() {
            llSub.removeAllViews()
            pendingSubQuestions.forEachIndexed { index, sub ->
                val item = layoutInflater.inflate(
                    R.layout.item_scenario_sub_question, llSub, false
                )
                item.findViewById<TextView>(R.id.tvSubIndex).text = "Question ${index + 1}"
                item.findViewById<TextView>(R.id.tvSubType).text = when (sub.questionType) {
                    "radio" -> "Radio (Single Choice)"
                    "checkbox" -> "Checkbox (Multiple Choice)"
                    else -> "Descriptive"
                }
                item.findViewById<TextView>(R.id.tvSubText).text = sub.text
                item.findViewById<ImageButton>(R.id.btnEditSub).setOnClickListener {
                    showSubQuestionDialog(index) { refreshList() }
                }
                item.findViewById<ImageButton>(R.id.btnDeleteSub).setOnClickListener {
                    pendingSubQuestions.removeAt(index)
                    refreshList()
                    tvCount.text = "Questions added: ${pendingSubQuestions.size}"
                }
                llSub.addView(item)
            }
            tvCount.text = "Questions added: ${pendingSubQuestions.size}"
        }

        refreshList()

        btnAddSub.setOnClickListener {
            showSubQuestionDialog(null) { refreshList() }
        }

        AlertDialog.Builder(this)
            .setTitle(if (existing != null) "Edit Scenario" else "Add Scenario")
            .setView(dialogView)
            .setPositiveButton(if (existing != null) "Update" else "Add") { _, _ ->
                val text = etScenarioText.text.toString().trim()
                if (text.isBlank()) {
                    Toast.makeText(this, "Scenario text is required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (pendingSubQuestions.isEmpty()) {
                    Toast.makeText(
                        this,
                        "Add at least one question to the scenario",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }
                val scenario = Question(
                    questionId = existing?.questionId ?: System.currentTimeMillis().toString(),
                    text = "",
                    options = emptyList(),
                    questionType = "scenario",
                    points = 0,
                    scenarioText = text,
                    subQuestions = pendingSubQuestions.toList()
                )
                addOrUpdateQuestion(scenario, existing)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Modal to add/edit a single sub-question inside the scenario editor.
     */
    private fun showSubQuestionDialog(editIndex: Int?, onDone: () -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_question, null)
        val etQuestionText = dialogView.findViewById<EditText>(R.id.etQuestionText)
        val etOption1 = dialogView.findViewById<EditText>(R.id.etOption1)
        val etOption2 = dialogView.findViewById<EditText>(R.id.etOption2)
        val etOption3 = dialogView.findViewById<EditText>(R.id.etOption3)
        val etOption4 = dialogView.findViewById<EditText>(R.id.etOption4)
        val spinnerType = dialogView.findViewById<Spinner>(R.id.spinnerQuestionType)
        val spinnerCorrect = dialogView.findViewById<Spinner>(R.id.spinnerCorrect)
        val llOptions = dialogView.findViewById<LinearLayout>(R.id.llOptionsContainer)
        val llDescriptive = dialogView.findViewById<LinearLayout>(R.id.llDescriptiveContainer)
        val llCorrectCheckbox = dialogView.findViewById<LinearLayout>(R.id.llCorrectCheckbox)
        val tvCorrectRadio = dialogView.findViewById<TextView>(R.id.tvCorrectRadio)
        val cbCorrect1 = dialogView.findViewById<CheckBox>(R.id.cbCorrect1)
        val cbCorrect2 = dialogView.findViewById<CheckBox>(R.id.cbCorrect2)
        val cbCorrect3 = dialogView.findViewById<CheckBox>(R.id.cbCorrect3)
        val cbCorrect4 = dialogView.findViewById<CheckBox>(R.id.cbCorrect4)
        val etCorrectAnswerText = dialogView.findViewById<EditText>(R.id.etCorrectAnswerText)
        val etPoints = dialogView.findViewById<EditText>(R.id.etPoints)
        val etImageUrl = dialogView.findViewById<EditText>(R.id.etImageUrl)
        val etAudioUrl = dialogView.findViewById<EditText>(R.id.etAudioUrl)
        val etVideoUrl = dialogView.findViewById<EditText>(R.id.etVideoUrl)

        // Restrict spinner to Radio / Checkbox / Descriptive only.
        val subTypes = resources.getStringArray(R.array.sub_question_types)
        val subAdapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, subTypes
        )
        subAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerType.adapter = subAdapter

        val existing = editIndex?.let { pendingSubQuestions.getOrNull(it) }
        if (existing != null) {
            etQuestionText.setText(existing.text)
            spinnerType.setSelection(
                when (existing.questionType) {
                    "radio" -> 0
                    "checkbox" -> 1
                    else -> 2
                }
            )
            existing.options.getOrNull(0)?.let { etOption1.setText(it) }
            existing.options.getOrNull(1)?.let { etOption2.setText(it) }
            existing.options.getOrNull(2)?.let { etOption3.setText(it) }
            existing.options.getOrNull(3)?.let { etOption4.setText(it) }
            when (existing.questionType) {
                "radio" -> spinnerCorrect.setSelection(existing.correctAnswerIndex)
                "checkbox" -> existing.correctAnswerIndices.forEach {
                    when (it) {
                        0 -> cbCorrect1.isChecked = true
                        1 -> cbCorrect2.isChecked = true
                        2 -> cbCorrect3.isChecked = true
                        3 -> cbCorrect4.isChecked = true
                    }
                }
                "descriptive" -> etCorrectAnswerText.setText(existing.correctAnswerText)
            }
            etPoints.setText(existing.points.toString())
            etImageUrl.setText(existing.imageUrl)
            etAudioUrl.setText(existing.audioUrl)
            etVideoUrl.setText(existing.videoUrl)
        }

        spinnerType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, position: Int, id: Long) {
                when (position) {
                    0 -> {
                        llOptions.visibility = View.VISIBLE
                        llDescriptive.visibility = View.GONE
                        tvCorrectRadio.visibility = View.VISIBLE
                        spinnerCorrect.visibility = View.VISIBLE
                        llCorrectCheckbox.visibility = View.GONE
                    }
                    1 -> {
                        llOptions.visibility = View.VISIBLE
                        llDescriptive.visibility = View.GONE
                        tvCorrectRadio.visibility = View.GONE
                        spinnerCorrect.visibility = View.GONE
                        llCorrectCheckbox.visibility = View.VISIBLE
                    }
                    2 -> {
                        llOptions.visibility = View.GONE
                        llDescriptive.visibility = View.VISIBLE
                        tvCorrectRadio.visibility = View.GONE
                        spinnerCorrect.visibility = View.GONE
                        llCorrectCheckbox.visibility = View.GONE
                    }
                }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        AlertDialog.Builder(this)
            .setTitle(if (existing != null) "Edit Sub-Question" else "Add Sub-Question")
            .setView(dialogView)
            .setPositiveButton(if (existing != null) "Update" else "Add") { _, _ ->
                val text = etQuestionText.text.toString().trim()
                val type = when (spinnerType.selectedItemPosition) {
                    0 -> "radio"
                    1 -> "checkbox"
                    else -> "descriptive"
                }
                val points = etPoints.text.toString().toIntOrNull() ?: 0
                if (text.isBlank() || points <= 0) {
                    Toast.makeText(this, "Fill question text and points", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                when (type) {
                    "radio" -> {
                        val opts = listOf(
                            etOption1.text.toString().trim(),
                            etOption2.text.toString().trim(),
                            etOption3.text.toString().trim(),
                            etOption4.text.toString().trim()
                        )
                        if (opts.any { it.isBlank() }) {
                            Toast.makeText(this, "Fill all options", Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }
                        val q = Question(
                            questionId = existing?.questionId
                                ?: "sub_${System.currentTimeMillis()}_${(0..9999).random()}",
                            text = text,
                            options = opts,
                            questionType = "radio",
                            correctAnswerIndex = spinnerCorrect.selectedItemPosition,
                            points = points,
                            imageUrl = etImageUrl.text.toString().trim(),
                            audioUrl = etAudioUrl.text.toString().trim(),
                            videoUrl = etVideoUrl.text.toString().trim()
                        )
                        if (editIndex == null) pendingSubQuestions.add(q)
                        else pendingSubQuestions[editIndex] = q
                        onDone()
                    }
                    "checkbox" -> {
                        val opts = listOf(
                            etOption1.text.toString().trim(),
                            etOption2.text.toString().trim(),
                            etOption3.text.toString().trim(),
                            etOption4.text.toString().trim()
                        )
                        val correct = mutableListOf<Int>()
                        if (cbCorrect1.isChecked) correct.add(0)
                        if (cbCorrect2.isChecked) correct.add(1)
                        if (cbCorrect3.isChecked) correct.add(2)
                        if (cbCorrect4.isChecked) correct.add(3)
                        if (opts.any { it.isBlank() } || correct.isEmpty()) {
                            Toast.makeText(
                                this, "Fill options and pick at least one correct",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@setPositiveButton
                        }
                        val q = Question(
                            questionId = existing?.questionId
                                ?: "sub_${System.currentTimeMillis()}_${(0..9999).random()}",
                            text = text,
                            options = opts,
                            questionType = "checkbox",
                            correctAnswerIndices = correct,
                            points = points,
                            imageUrl = etImageUrl.text.toString().trim(),
                            audioUrl = etAudioUrl.text.toString().trim(),
                            videoUrl = etVideoUrl.text.toString().trim()
                        )
                        if (editIndex == null) pendingSubQuestions.add(q)
                        else pendingSubQuestions[editIndex] = q
                        onDone()
                    }
                    else -> {
                        val correctText = etCorrectAnswerText.text.toString().trim()
                        if (correctText.isBlank()) {
                            Toast.makeText(this, "Enter correct answer", Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }
                        val q = Question(
                            questionId = existing?.questionId
                                ?: "sub_${System.currentTimeMillis()}_${(0..9999).random()}",
                            text = text,
                            options = emptyList(),
                            questionType = "descriptive",
                            correctAnswerText = correctText,
                            points = points,
                            imageUrl = etImageUrl.text.toString().trim(),
                            audioUrl = etAudioUrl.text.toString().trim(),
                            videoUrl = etVideoUrl.text.toString().trim()
                        )
                        if (editIndex == null) pendingSubQuestions.add(q)
                        else pendingSubQuestions[editIndex] = q
                        onDone()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ------------------------------------------------------------------------
    // ADD / UPDATE / DELETE
    // ------------------------------------------------------------------------
    private fun addOrUpdateQuestion(newQuestion: Question, existingQuestion: Question?) {
        if (existingQuestion != null) {
            val index = questionsList.indexOfFirst { it.questionId == existingQuestion.questionId }
            if (index != -1) {
                questionsList[index] = newQuestion
            }
        } else {
            questionsList.add(newQuestion)
        }
        updateQuestionsCount()
        adapter.updateList(questionsList)
        Toast.makeText(
            this,
            if (existingQuestion != null) "Question updated" else "Question added",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun deleteQuestion(question: Question) {
        AlertDialog.Builder(this)
            .setTitle("Delete Question")
            .setMessage(
                if (question.isScenario)
                    "Are you sure you want to delete this scenario and all its sub-questions?"
                else
                    "Are you sure you want to delete this question?"
            )
            .setPositiveButton("Delete") { _, _ ->
                questionsList.removeAll { it.questionId == question.questionId }
                updateQuestionsCount()
                adapter.updateList(questionsList)
                Toast.makeText(this, "Question deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Updates the "Questions added: N" label.
     * Counts actual child questions (scenario sub-questions + normal questions).
     * The scenario container itself is not counted.
     * Also compares against the configured total and appends a hint.
     */
    private fun updateQuestionsCount() {
        val actual = questionsList.sumOf { it.questionCount() }
        val configured = binding.etConfiguredQuestionCount.text.toString().toIntOrNull() ?: 0

        val label = if (configured > 0) {
            "Questions added: $actual / $configured required"
        } else {
            "Questions added: $actual"
        }
        binding.tvQuestionsCount.text = label
        binding.rvQuestionPreview.visibility =
            if (questionsList.isEmpty()) View.GONE else View.VISIBLE
    }

    // ------------------------------------------------------------------------
    // SAVE CONFIRMATION
    // ------------------------------------------------------------------------
    private fun showSaveConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Save Quiz")
            .setMessage("Are you sure you want to save this quiz?")
            .setPositiveButton("Yes") { _, _ -> saveQuiz() }
            .setNegativeButton("No", null)
            .show()
    }

    // ------------------------------------------------------------------------
    // VALIDATION
    // ------------------------------------------------------------------------
    private fun validateQuiz(): Boolean {
        val title = binding.etQuizTitle.text.toString().trim()
        if (title.isEmpty()) {
            binding.etQuizTitle.error = "Quiz title is required"
            binding.etQuizTitle.requestFocus()
            Toast.makeText(this, "Please enter a quiz title", Toast.LENGTH_SHORT).show()
            return false
        }

        if (isTitleDuplicate(title)) {
            Toast.makeText(this, "Quiz title already exists. Please choose a different title.", Toast.LENGTH_LONG).show()
            binding.etQuizTitle.requestFocus()
            return false
        }

        val description = binding.etQuizDescription.text.toString().trim()
        if (description.isEmpty()) {
            binding.etQuizDescription.error = "Description is required"
            binding.etQuizDescription.requestFocus()
            Toast.makeText(this, "Please enter a description", Toast.LENGTH_SHORT).show()
            return false
        }

        // ---------- NEW: configured question count validation ----------
        val configuredCount = binding.etConfiguredQuestionCount.text.toString().toIntOrNull() ?: 0
        if (configuredCount <= 0) {
            binding.etConfiguredQuestionCount.error = "Enter the total number of questions"
            binding.etConfiguredQuestionCount.requestFocus()
            Toast.makeText(
                this,
                "Please enter the total number of questions for this quiz.",
                Toast.LENGTH_SHORT
            ).show()
            return false
        }

        if (questionsList.isEmpty()) {
            Toast.makeText(this, "Add at least one question", Toast.LENGTH_SHORT).show()
            return false
        }

        // ---------- Validate every question (normal + scenario sub-questions) ----------
        for (entry in questionsList) {

            if (entry.isScenario) {
                if (entry.scenarioText.isBlank()) {
                    Toast.makeText(this, "A scenario is missing its text", Toast.LENGTH_SHORT).show()
                    return false
                }
                if (entry.subQuestions.isEmpty()) {
                    Toast.makeText(this, "Each scenario needs at least one question", Toast.LENGTH_SHORT).show()
                    return false
                }
                for (sub in entry.subQuestions) {
                    if (sub.text.isBlank()) {
                        Toast.makeText(this, "A scenario sub-question is empty", Toast.LENGTH_SHORT).show()
                        return false
                    }
                    when (sub.questionType) {
                        "radio" -> if (sub.correctAnswerIndex !in sub.options.indices) {
                            Toast.makeText(this, "Invalid correct option in a sub-question", Toast.LENGTH_SHORT).show()
                            return false
                        }
                        "checkbox" -> if (sub.correctAnswerIndices.isEmpty()) {
                            Toast.makeText(this, "Sub-question missing correct options", Toast.LENGTH_SHORT).show()
                            return false
                        }
                        "descriptive" -> if (sub.correctAnswerText.isBlank()) {
                            Toast.makeText(this, "Sub-question missing correct answer", Toast.LENGTH_SHORT).show()
                            return false
                        }
                    }
                    if (sub.points <= 0) {
                        Toast.makeText(this, "Every sub-question needs points > 0", Toast.LENGTH_SHORT).show()
                        return false
                    }
                }
                continue
            }

            // Normal questions
            if (entry.text.isBlank()) {
                Toast.makeText(this, "One or more questions have empty text", Toast.LENGTH_SHORT).show()
                return false
            }
            when (entry.questionType) {
                "radio" -> if (entry.correctAnswerIndex < 0 || entry.correctAnswerIndex >= entry.options.size) {
                    Toast.makeText(this, "One or more questions have invalid correct answer", Toast.LENGTH_SHORT).show()
                    return false
                }
                "checkbox" -> {
                    if (entry.correctAnswerIndices.isEmpty()) {
                        Toast.makeText(this, "One or more checkbox questions have no correct option selected", Toast.LENGTH_SHORT).show()
                        return false
                    }
                    for (idx in entry.correctAnswerIndices) {
                        if (idx < 0 || idx >= entry.options.size) {
                            Toast.makeText(this, "One or more questions have invalid correct answer", Toast.LENGTH_SHORT).show()
                            return false
                        }
                    }
                }
                "descriptive" -> if (entry.correctAnswerText.isBlank()) {
                    Toast.makeText(this, "One or more descriptive questions have no correct answer text", Toast.LENGTH_SHORT).show()
                    return false
                }
            }
            if (entry.points <= 0) {
                Toast.makeText(this, "Each question must have points > 0", Toast.LENGTH_SHORT).show()
                return false
            }
        }

        // ---------- NEW: configured vs. actual total ----------
        val actualCount = questionsList.sumOf { it.questionCount() }
        if (actualCount != configuredCount) {
            val message = if (actualCount < configuredCount) {
                val missing = configuredCount - actualCount
                "Please add $missing more question${if (missing == 1) "" else "s"}. " +
                        "Total required: $configuredCount, current: $actualCount."
            } else {
                val extra = actualCount - configuredCount
                "Question limit exceeded. " +
                        "Total required: $configuredCount, current: $actualCount. " +
                        "Please remove $extra question${if (extra == 1) "" else "s"}."
            }
            AlertDialog.Builder(this)
                .setTitle("Question Count Mismatch")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show()
            return false
        }

        // ---------- Timer validation (unchanged) ----------
        val timerType = when (binding.radioGroupTimerType.checkedRadioButtonId) {
            R.id.radioWholeQuizTimer -> "WHOLE_QUIZ"
            R.id.radioPerQuestionTimer -> "PER_QUESTION"
            else -> "NONE"
        }
        when (timerType) {
            "WHOLE_QUIZ" -> {
                val input = binding.etTotalTime.text.toString()
                val totalSeconds = parseDurationToSeconds(input)
                if (totalSeconds == null || totalSeconds <= 0) {
                    binding.etTotalTime.error = "Please enter a valid time (e.g., 00:30:00)"
                    binding.etTotalTime.requestFocus()
                    Toast.makeText(this, "Please enter a valid total time", Toast.LENGTH_SHORT).show()
                    return false
                }
            }
            "PER_QUESTION" -> {
                val input = binding.etPerQuestionTime.text.toString()
                val perSeconds = parseDurationToSeconds(input)
                if (perSeconds == null || perSeconds <= 0) {
                    binding.etPerQuestionTime.error = "Please enter a valid time (e.g., 00:01:00)"
                    binding.etPerQuestionTime.requestFocus()
                    Toast.makeText(this, "Please enter a valid time per question", Toast.LENGTH_SHORT).show()
                    return false
                }
            }
            else -> { /* No timer, ok */ }
        }

        val negativeMarking = binding.switchNegativeMarking?.isChecked ?: false
        if (negativeMarking) {
            val negativeValue = binding.etNegativeValue?.text.toString().toFloatOrNull()
            if (negativeValue == null || negativeValue <= 0f || negativeValue > 1f) {
                binding.etNegativeValue?.error = "Must be between 0 and 1"
                binding.etNegativeValue?.requestFocus()
                Toast.makeText(this, "Negative marking value must be between 0 and 1", Toast.LENGTH_SHORT).show()
                return false
            }
        }

        if (startTimeTimestamp > 0 && startTimeTimestamp >= deadlineTimestamp && deadlineTimestamp > 0) {
            Toast.makeText(this, "Start time must be before due time", Toast.LENGTH_SHORT).show()
            binding.etStartTime.error = "Start must be before due"
            return false
        }

        if (deadlineTimestamp > 0 && deadlineTimestamp <= System.currentTimeMillis()) {
            binding.etDeadline.error = "Deadline must be in the future"
            binding.etDeadline.requestFocus()
            Toast.makeText(this, "Deadline must be in the future", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    // ------------------------------------------------------------------------
    // HELPERS
    // ------------------------------------------------------------------------
    private fun parseDurationToSeconds(input: String): Long? {
        val parts = input.split(":")
        return if (parts.size == 3) {
            try {
                val h = parts[0].toLong()
                val m = parts[1].toLong()
                val s = parts[2].toLong()
                h * 3600 + m * 60 + s
            } catch (e: NumberFormatException) {
                null
            }
        } else {
            null
        }
    }

    private fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return String.format("%02d:%02d:%02d", h, m, s)
    }
}