package com.smartquiz

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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

        binding.etTimer.setText("60")
        binding.etDeadline.setOnClickListener { showDateTimePicker() }
        binding.btnAddQuestion.setOnClickListener { showAddQuestionDialog() }
        binding.btnSaveQuiz.setOnClickListener { saveQuiz() }
        updateQuestionsCount()

        // Default: private quiz
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

    private fun showAddQuestionDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_question, null)
        val etQuestionText = dialogView.findViewById<EditText>(R.id.etQuestionText)
        val etOption1 = dialogView.findViewById<EditText>(R.id.etOption1)
        val etOption2 = dialogView.findViewById<EditText>(R.id.etOption2)
        val etOption3 = dialogView.findViewById<EditText>(R.id.etOption3)
        val etOption4 = dialogView.findViewById<EditText>(R.id.etOption4)
        val spinnerCorrect = dialogView.findViewById<Spinner>(R.id.spinnerCorrect)
        val etPoints = dialogView.findViewById<EditText>(R.id.etPoints)
        val etImageUrl = dialogView.findViewById<EditText>(R.id.etImageUrl)
        val etAudioUrl = dialogView.findViewById<EditText>(R.id.etAudioUrl)
        val etVideoUrl = dialogView.findViewById<EditText>(R.id.etVideoUrl)

        AlertDialog.Builder(this)
            .setTitle("Add Question")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val text = etQuestionText.text.toString().trim()
                val options = listOf(
                    etOption1.text.toString().trim(),
                    etOption2.text.toString().trim(),
                    etOption3.text.toString().trim(),
                    etOption4.text.toString().trim()
                )
                val correctIndex = spinnerCorrect.selectedItemPosition
                val points = etPoints.text.toString().toIntOrNull() ?: 1
                val imageUrl = etImageUrl.text.toString().trim()
                val audioUrl = etAudioUrl.text.toString().trim()
                val videoUrl = etVideoUrl.text.toString().trim()

                if (text.isNotBlank() && options.all { it.isNotBlank() }) {
                    questionsList.add(
                        Question(
                            questionId = System.currentTimeMillis().toString(),
                            text = text,
                            options = options,
                            correctAnswerIndex = correctIndex,
                            points = points,
                            imageUrl = imageUrl,
                            audioUrl = audioUrl,
                            videoUrl = videoUrl
                        )
                    )
                    Toast.makeText(this, "Question added", Toast.LENGTH_SHORT).show()
                    updateQuestionsCount()
                } else {
                    Toast.makeText(this, "Fill all required fields", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateQuestionsCount() {
        binding.tvQuestionsCount.text = "Questions added: ${questionsList.size}"
    }

    private fun saveQuiz() {
        val title = binding.etQuizTitle.text.toString().trim()
        val description = binding.etQuizDescription.text.toString().trim()
        val visibility = if (binding.radioPublic.isChecked) "public" else "private"
        val timerSeconds = binding.etTimer.text.toString().toIntOrNull() ?: 60
        val negativeMarking = binding.switchNegativeMarking?.isChecked ?: false
        val negativeMarkingValue = binding.etNegativeValue?.text.toString().toFloatOrNull() ?: 0.25f


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
            timerSeconds = timerSeconds,
            deadline = deadlineTimestamp,
            negativeMarking = negativeMarking,
            negativeMarkingValue = negativeMarkingValue,
            hasImageQuestions = questionsList.any { it.imageUrl.isNotEmpty() },
            hasAudioQuestions = questionsList.any { it.audioUrl.isNotEmpty() },
            hasVideoQuestions = questionsList.any { it.videoUrl.isNotEmpty() }
        )

        db.collection("quizzes").add(quiz)
            .addOnSuccessListener { docRef ->
                val batch = db.batch()
                for (q in questionsList) {
                    val publicRef = docRef.collection("questions").document()
                    val publicData = mapOf(
                        "text" to q.text,
                        "options" to q.options,
                        "points" to q.points,
                        "imageUrl" to q.imageUrl,
                        "audioUrl" to q.audioUrl,
                        "videoUrl" to q.videoUrl
                    )
                    batch.set(publicRef, publicData)

                    val privateRef = docRef.collection("questions_private").document(publicRef.id)
                    val privateData = mapOf("correctAnswerIndex" to q.correctAnswerIndex)
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