package com.smartquiz

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuizCreationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_back)
        supportActionBar?.title = "Create Quiz"

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        binding.etDeadline.setOnClickListener { showDateTimePicker() }
        binding.btnAddQuestion.setOnClickListener { showAddQuestionDialog(null) }
        binding.btnSaveQuiz.setOnClickListener { showSaveConfirmation() }

        // Timer type selection – show/hide appropriate input
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
        // Default: No Timer selected, so hide both
        binding.inputLayoutTotalTime.visibility = View.GONE
        binding.inputLayoutPerQuestionTime.visibility = View.GONE

        // Setup RecyclerView for preview
        adapter = QuestionPreviewAdapter(
            questions = questionsList,
            onEditClick = { question -> showAddQuestionDialog(question) },
            onDeleteClick = { question -> deleteQuestion(question) }
        )
        binding.rvQuestionPreview.layoutManager = LinearLayoutManager(this)
        binding.rvQuestionPreview.adapter = adapter

        updateQuestionsCount()
        binding.radioPrivate.isChecked = true
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        return true
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

    private fun showAddQuestionDialog(existingQuestion: Question?) {
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
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        AlertDialog.Builder(this)
            .setTitle(if (existingQuestion != null) "Edit Question" else "Add Question")
            .setView(dialogView)
            .setPositiveButton(if (existingQuestion != null) "Update" else "Add") { _, _ ->
                val text = etQuestionText.text.toString().trim()
                val type = when (spinnerType.selectedItemPosition) {
                    0 -> "radio"
                    1 -> "checkbox"
                    else -> "descriptive"
                }
                val points = etPoints.text.toString().toIntOrNull() ?: 1
                val imageUrl = etImageUrl.text.toString().trim()
                val audioUrl = etAudioUrl.text.toString().trim()
                val videoUrl = etVideoUrl.text.toString().trim()

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
                                questionId = System.currentTimeMillis().toString(),
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
                                questionId = System.currentTimeMillis().toString(),
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
                                questionId = System.currentTimeMillis().toString(),
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
            .show()
    }

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
        Toast.makeText(this, if (existingQuestion != null) "Question updated" else "Question added", Toast.LENGTH_SHORT).show()
    }

    private fun deleteQuestion(question: Question) {
        AlertDialog.Builder(this)
            .setTitle("Delete Question")
            .setMessage("Are you sure you want to delete this question?")
            .setPositiveButton("Delete") { _, _ ->
                questionsList.removeAll { it.questionId == question.questionId }
                updateQuestionsCount()
                adapter.updateList(questionsList)
                Toast.makeText(this, "Question deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateQuestionsCount() {
        binding.tvQuestionsCount.text = "Questions added: ${questionsList.size}"
        binding.rvQuestionPreview.visibility = if (questionsList.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showSaveConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Save Quiz")
            .setMessage("Are you sure you want to save this quiz?")
            .setPositiveButton("Yes") { _, _ ->
                saveQuiz()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun saveQuiz() {
        val title = binding.etQuizTitle.text.toString().trim()
        val description = binding.etQuizDescription.text.toString().trim()
        val visibility = if (binding.radioPublic.isChecked) "public" else "private"
        val negativeMarking = binding.switchNegativeMarking?.isChecked ?: false
        val negativeMarkingValue = binding.etNegativeValue?.text.toString().toFloatOrNull() ?: 0.25f

        // ---- Timer type and values ----
        val timerType = when (binding.radioGroupTimerType.checkedRadioButtonId) {
            R.id.radioWholeQuizTimer -> "WHOLE_QUIZ"
            R.id.radioPerQuestionTimer -> "PER_QUESTION"
            else -> "NONE"
        }

        val totalTimeSeconds: Long
        val perQuestionSeconds: Long
        when (timerType) {
            "WHOLE_QUIZ" -> {
                val input = binding.etTotalTime.text.toString()
                totalTimeSeconds = parseDurationToSeconds(input) ?: 0
                if (totalTimeSeconds <= 0) {
                    Toast.makeText(this, "Please enter a valid total time (e.g., 00:30:00)", Toast.LENGTH_SHORT).show()
                    return
                }
                perQuestionSeconds = 0
            }
            "PER_QUESTION" -> {
                val input = binding.etPerQuestionTime.text.toString()
                perQuestionSeconds = parseDurationToSeconds(input) ?: 0
                if (perQuestionSeconds <= 0) {
                    Toast.makeText(this, "Please enter a valid time per question (e.g., 00:00:30)", Toast.LENGTH_SHORT).show()
                    return
                }
                totalTimeSeconds = 0
            }
            else -> {
                totalTimeSeconds = 0
                perQuestionSeconds = 0
            }
        }

        if (title.isEmpty()) {
            Toast.makeText(this, "Quiz title required", Toast.LENGTH_SHORT).show()
            return
        }
        if (questionsList.isEmpty()) {
            Toast.makeText(this, "Add at least one question", Toast.LENGTH_SHORT).show()
            return
        }

        val quizCode = if (visibility == "private") Random.nextInt(100000, 999999).toString() else ""

        val quiz = Quiz(
            title = title,
            description = description,
            quizCode = quizCode,
            creatorId = auth.currentUser?.uid ?: "",
            visibility = visibility,
            createdAt = System.currentTimeMillis(),
            totalQuestions = questionsList.size,
            timerSeconds = 0, // legacy – not used
            deadline = deadlineTimestamp,
            negativeMarking = negativeMarking,
            negativeMarkingValue = negativeMarkingValue,
            hasImageQuestions = questionsList.any { it.imageUrl.isNotEmpty() },
            hasAudioQuestions = questionsList.any { it.audioUrl.isNotEmpty() },
            hasVideoQuestions = questionsList.any { it.videoUrl.isNotEmpty() },
            // NEW TIMER FIELDS
            timerType = timerType,
            totalTimeSeconds = totalTimeSeconds,
            timePerQuestionSeconds = perQuestionSeconds
        )

        db.collection("quizzes").add(quiz)
            .addOnSuccessListener { docRef ->
                val batch = db.batch()
                for (q in questionsList) {
                    val publicRef = docRef.collection("questions").document()
                    val publicData = mapOf(
                        "text" to q.text,
                        "options" to q.options,
                        "questionType" to q.questionType,
                        "points" to q.points,
                        "imageUrl" to q.imageUrl,
                        "audioUrl" to q.audioUrl,
                        "videoUrl" to q.videoUrl
                    )
                    batch.set(publicRef, publicData)

                    val privateRef = docRef.collection("questions_private").document(publicRef.id)
                    val privateData = when (q.questionType) {
                        "radio" -> mapOf("correctAnswerIndex" to q.correctAnswerIndex)
                        "checkbox" -> mapOf("correctAnswerIndices" to q.correctAnswerIndices)
                        "descriptive" -> mapOf("correctAnswerText" to q.correctAnswerText)
                        else -> emptyMap<String, Any>()
                    }
                    batch.set(privateRef, privateData)
                }
                batch.commit().addOnSuccessListener {
                    val message = if (visibility == "private") "Quiz saved! Code: $quizCode" else "Public quiz saved!"
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    val intent = Intent(this, QuizStatsActivity::class.java)
                    intent.putExtra("quizId", docRef.id)
                    intent.putExtra("quizTitle", title)
                    startActivity(intent)
                    finish()
                }.addOnFailureListener { e ->
                    Toast.makeText(this, "Error saving questions: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error saving quiz: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}