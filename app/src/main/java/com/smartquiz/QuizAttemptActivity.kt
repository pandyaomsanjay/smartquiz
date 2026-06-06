package com.smartquiz

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.smartquiz.databinding.ActivityQuizAttemptBinding
import com.smartquiz.models.JoinedQuiz

class QuizAttemptActivity : AppCompatActivity() {
    private lateinit var binding: ActivityQuizAttemptBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var quiz: Quiz
    private var questions = mutableListOf<Question>()
    private var shuffledQuestions = mutableListOf<Question>()
    private var currentIndex = 0
    private var score = 0
    private var quizId = ""
    private var quizTitle = ""
    private var timer: CountDownTimer? = null
    private var timeLeft = 0
    private var isSubmitted = false
    private val userAnswers = mutableMapOf<String, Int>()
    private var startTime = 0L
    private val sharedPrefs by lazy { getSharedPreferences("quiz_answers", MODE_PRIVATE) }
    private var mediaPlayer: MediaPlayer? = null
    private var cheatAttempts = 0
    private val TAG = "QuizAttempt"

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
        supportActionBar?.title = quizTitle

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        loadQuizAndQuestions()
    }

    override fun onPause() {
        super.onPause()
        if (!isSubmitted) {
            cheatAttempts++
            logCheatAttempt("App switched or minimised")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        mediaPlayer?.release()
    }

    private fun logCheatAttempt(reason: String) {
        val userId = auth.currentUser?.uid ?: return
        val cheatLog = hashMapOf(
            "userId" to userId,
            "quizId" to quizId,
            "reason" to reason,
            "timestamp" to System.currentTimeMillis(),
            "deviceInfo" to "${Build.MANUFACTURER} ${Build.MODEL}"
        )
        db.collection("cheat_logs").add(cheatLog)
    }

    private fun loadQuizAndQuestions() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "Please log in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Check if the user already has a completed attempt for this quiz
        db.collection("quizzes").document(quizId).collection("attempts").document(userId)
            .get()
            .addOnSuccessListener { attemptDoc ->
                val isCompleted = attemptDoc.exists() && attemptDoc.getString("status") == "Completed"
                if (isCompleted) {
                    // If quiz allows multiple attempts, we can let them try again
                    // First, get the quiz to check allowMultipleAttempts flag
                    db.collection("quizzes").document(quizId).get()
                        .addOnSuccessListener { quizDoc ->
                            val quizAllowMultiple = quizDoc.getBoolean("allowMultipleAttempts") ?: false
                            if (!quizAllowMultiple) {
                                Toast.makeText(this, "You have already completed this quiz. Multiple attempts are not allowed.", Toast.LENGTH_LONG).show()
                                finish()
                            } else {
                                // Allowed to attempt again – proceed to load quiz
                                fetchQuizAndQuestions()
                            }
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Error checking quiz settings", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                } else {
                    // No completed attempt – proceed normally
                    fetchQuizAndQuestions()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error checking attempt: ${e.message}")
                // Fallback: assume not completed and proceed
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
                loadQuestions()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load quiz", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun loadQuestions() {
        db.collection("quizzes").document(quizId).collection("questions").get()
            .addOnSuccessListener { docs ->
                if (docs.isEmpty()) {
                    Toast.makeText(this, "No questions found", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }

                val total = docs.size()
                var loaded = 0
                var hasError = false

                for (doc in docs) {
                    val q = doc.toObject(Question::class.java)
                    q.questionId = doc.id

                    db.collection("quizzes").document(quizId)
                        .collection("questions_private").document(doc.id)
                        .get()
                        .addOnSuccessListener { privateDoc ->
                            if (privateDoc.exists()) {
                                q.correctAnswerIndex = privateDoc.getLong("correctAnswerIndex")?.toInt() ?: 0
                            } else {
                                Log.e(TAG, "Missing private doc for ${doc.id}")
                                hasError = true
                            }
                            questions.add(q)
                            loaded++
                            if (loaded == total) {
                                if (questions.isNotEmpty()) {
                                    startQuiz()
                                    if (hasError) {
                                        Toast.makeText(this, "Some questions may have issues. Proceeding anyway.", Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    Toast.makeText(this, "No valid questions found", Toast.LENGTH_SHORT).show()
                                    finish()
                                }
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Failed to fetch private doc for ${doc.id}: ${e.message}")
                            hasError = true
                            questions.add(q)
                            loaded++
                            if (loaded == total) {
                                if (questions.isNotEmpty()) {
                                    startQuiz()
                                    Toast.makeText(this, "Some question answers couldn't be loaded. Please contact support.", Toast.LENGTH_LONG).show()
                                } else {
                                    finish()
                                }
                            }
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
        timeLeft = quiz.timerSeconds
        updateTimerUI()
        timer = object : CountDownTimer((timeLeft * 1000L), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeft = (millisUntilFinished / 1000).toInt()
                updateTimerUI()
            }
            override fun onFinish() {
                if (!isSubmitted) {
                    Toast.makeText(this@QuizAttemptActivity, "Time's up!", Toast.LENGTH_SHORT).show()
                    submitQuiz()
                }
            }
        }.start()

        displayQuestion()

        binding.btnPrevious.setOnClickListener {
            if (currentIndex > 0) {
                saveCurrentAnswer()
                currentIndex--
                displayQuestion()
            }
        }

        binding.btnBookmark.setOnClickListener {
            toggleBookmark(shuffledQuestions[currentIndex].questionId)
        }
    }

    private fun restoreSavedAnswers() {
        for (q in shuffledQuestions) {
            val saved = sharedPrefs.getInt("${quizId}_${q.questionId}", -1)
            if (saved != -1) userAnswers[q.questionId] = saved
        }
    }

    private fun showSubmitConfirmation() {
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

        // Options
        binding.radioGroupOptions.removeAllViews()
        q.options.forEachIndexed { idx, option ->
            val rb = RadioButton(this)
            rb.text = option
            rb.tag = idx
            binding.radioGroupOptions.addView(rb)

            val savedAnswer = userAnswers[q.questionId]
            if (savedAnswer != null && savedAnswer == idx) {
                rb.isChecked = true
            }
        }

        binding.tvProgress.text = "${currentIndex + 1}/${shuffledQuestions.size}"
        checkBookmarkStatus(q.questionId)

        val isLastQuestion = (currentIndex == shuffledQuestions.size - 1)
        if (isLastQuestion) {
            binding.btnNextOrSubmit.text = "Submit Quiz"
            binding.btnNextOrSubmit.setOnClickListener {
                saveCurrentAnswer()
                showSubmitConfirmation()
            }
        } else {
            binding.btnNextOrSubmit.text = "Next"
            binding.btnNextOrSubmit.setOnClickListener {
                saveCurrentAnswer()
                currentIndex++
                displayQuestion()
            }
        }
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
        val selectedRadio = binding.radioGroupOptions.findViewById<RadioButton>(binding.radioGroupOptions.checkedRadioButtonId)
        if (selectedRadio != null) {
            val selectedIndex = selectedRadio.tag as Int
            val questionId = shuffledQuestions[currentIndex].questionId
            userAnswers[questionId] = selectedIndex
            sharedPrefs.edit().putInt("${quizId}_$questionId", selectedIndex).apply()
        }
    }

    private fun submitQuiz() {
        if (isSubmitted) return
        isSubmitted = true
        timer?.cancel()
        mediaPlayer?.release()

        val totalPossible = questions.sumOf { it.points }
        val userId = auth.currentUser?.uid ?: run {
            Toast.makeText(this, "Please log in", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // Calculate final score with negative marking
        var finalScore = 0
        for ((qId, selectedIdx) in userAnswers) {
            val q = questions.find { it.questionId == qId }
            if (q != null) {
                if (selectedIdx == q.correctAnswerIndex) {
                    finalScore += q.points
                } else if (quiz.negativeMarking) {
                    finalScore -= (quiz.negativeMarkingValue * q.points).toInt()
                }
            }
        }
        finalScore = finalScore.coerceAtLeast(0)
        score = finalScore

        val endTime = System.currentTimeMillis()
        val timeSpentSeconds = (endTime - startTime) / 1000
        val minutes = timeSpentSeconds / 60
        val seconds = timeSpentSeconds % 60
        val durationFormatted = String.format("%02d:%02d", minutes, seconds)

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
        attemptData["cheatAttempts"] = cheatAttempts

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

                updateJoinedQuiz(userId, score, totalPossible, endTime)

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
                    creatorName = "",
                    joinTime = startTime,
                    submitTime = submitTime,
                    status = "Completed",
                    score = score,
                    category = quiz.category,
                    allowMultipleAttempts = quiz.allowMultipleAttempts
                )
                joinedQuizRef.set(joinedQuiz)
            }
        }
    }

    private fun updateTimerUI() {
        val minutes = timeLeft / 60
        val seconds = timeLeft % 60
        binding.tvTimer.text = String.format("%02d:%02d", minutes, seconds)
    }
}