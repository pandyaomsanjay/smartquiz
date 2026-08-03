package com.smartquiz

import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.smartquiz.databinding.ActivityQuizDetailsBinding
import com.smartquiz.models.JoinedQuiz
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class QuizDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQuizDetailsBinding
    private lateinit var joinedQuiz: JoinedQuiz
    private val db = FirebaseFirestore.getInstance()
    private var attemptStatus = ""
    private var submitTime: Long? = null
    private var score: Int? = null
    private var totalScore: Int? = null
    private var userId: String? = null
    private var answersMap: Map<String, Any>? = null

    // Progress dialog
    private var progressDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuizDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        joinedQuiz = intent.getSerializableExtra("joinedQuiz") as JoinedQuiz

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_back)
        supportActionBar?.title = "Quiz Details"

        userId = FirebaseAuth.getInstance().currentUser?.uid

        binding.btnDownloadAnswers.setOnClickListener {
            generateAnswerSheetPdf()
        }

        loadQuizAndAttemptDetails()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        return true
    }

    private fun showProgressDialog(message: String = "Generating PDF...") {
        progressDialog = AlertDialog.Builder(this)
            .setTitle("Please wait")
            .setMessage(message)
            .setCancelable(false)
            .create()
        progressDialog?.show()
    }

    private fun hideProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
    }

    private fun loadQuizAndAttemptDetails() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        db.collection("quizzes").document(joinedQuiz.quizId).get()
            .addOnSuccessListener { quizDoc ->
                val title = quizDoc.getString("title") ?: joinedQuiz.quizTitle
                val description = quizDoc.getString("description") ?: "No description"
                val visibility = quizDoc.getString("visibility") ?: "private"
                binding.tvTitle.text = title
                binding.tvDescription.text = description
                binding.tvVisibility.text = if (visibility == "public") "🌍 Public Quiz" else "🔒 Private Quiz"
            }

        db.collection("quizzes").document(joinedQuiz.quizId)
            .collection("attempts").document(userId)
            .get()
            .addOnSuccessListener { attemptDoc ->
                if (attemptDoc.exists()) {
                    attemptStatus = attemptDoc.getString("status") ?: "In Progress"
                    submitTime = attemptDoc.getLong("submitTime")
                    score = attemptDoc.getLong("score")?.toInt()
                    totalScore = attemptDoc.getLong("totalScore")?.toInt()
                    answersMap = attemptDoc.get("answers") as? Map<String, Any>
                } else {
                    attemptStatus = "In Progress"
                }
                updateUI()
            }
            .addOnFailureListener {
                attemptStatus = "In Progress"
                updateUI()
            }

        binding.tvCreator.text = "Created by: ${joinedQuiz.creatorName}"
        val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        binding.tvJoinDate.text = "Joined: ${dateFormat.format(Date(joinedQuiz.joinTime))}"
    }

    private fun updateUI() {
        when (attemptStatus) {
            "Completed" -> {
                binding.tvStatus.text = "✅ Completed"
                binding.tvStatus.setBackgroundColor(ContextCompat.getColor(this, R.color.success_light))
                binding.tvSubmitInfo.visibility = View.VISIBLE
                val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                val submitDate = if (submitTime != null) dateFormat.format(Date(submitTime!!)) else "Unknown"
                binding.tvSubmitInfo.text = "Submitted: $submitDate\nScore: ${score ?: 0}/${totalScore ?: 0}"
                binding.btnReattempt.visibility = View.GONE
                binding.tvCompletionMessage.visibility = View.VISIBLE
                binding.tvCompletionMessage.text = "This quiz has already been completed."
                binding.btnDownloadAnswers.visibility = View.VISIBLE
            }
            "In Progress" -> {
                binding.tvStatus.text = "⏳ In Progress"
                binding.tvStatus.setBackgroundColor(ContextCompat.getColor(this, R.color.warning_light))
                binding.tvSubmitInfo.visibility = View.GONE
                binding.tvCompletionMessage.visibility = View.GONE
                binding.btnReattempt.visibility = View.VISIBLE
                binding.btnReattempt.text = "Continue Quiz"
                binding.btnDownloadAnswers.visibility = View.GONE
            }
            "Expired" -> {
                binding.tvStatus.text = "❌ Expired"
                binding.tvStatus.setBackgroundColor(ContextCompat.getColor(this, R.color.error_light))
                binding.btnReattempt.visibility = View.GONE
                binding.tvSubmitInfo.visibility = View.GONE
                binding.tvCompletionMessage.visibility = View.VISIBLE
                binding.tvCompletionMessage.text = "This quiz has expired."
                binding.btnDownloadAnswers.visibility = View.GONE
            }
        }

        binding.btnReattempt.setOnClickListener {
            if (attemptStatus == "In Progress") {
                val intent = Intent(this, QuizInstructionsActivity::class.java)
                intent.putExtra("quizId", joinedQuiz.quizId)
                intent.putExtra("quizTitle", joinedQuiz.quizTitle)
                intent.putExtra("creatorId", joinedQuiz.creatorId)
                startActivity(intent)
                finish()
            }
        }
    }

    // ========== PDF Generation (Background) ==========

    private fun generateAnswerSheetPdf() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        if (answersMap == null) {
            showProgressDialog("Loading answers...")
            db.collection("quizzes").document(joinedQuiz.quizId)
                .collection("attempts").document(userId)
                .get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        answersMap = doc.get("answers") as? Map<String, Any>
                        if (answersMap == null) {
                            Toast.makeText(this, "No answers found", Toast.LENGTH_SHORT).show()
                            hideProgressDialog()
                            return@addOnSuccessListener
                        }
                        fetchQuestionsAndGeneratePdf()
                    } else {
                        Toast.makeText(this, "Attempt not found", Toast.LENGTH_SHORT).show()
                        hideProgressDialog()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to fetch answers", Toast.LENGTH_SHORT).show()
                    hideProgressDialog()
                }
            return
        }

        fetchQuestionsAndGeneratePdf()
    }

    private fun fetchQuestionsAndGeneratePdf() {
        showProgressDialog("Loading questions...")
        val questionsMap = mutableMapOf<String, Question>()

        db.collection("quizzes").document(joinedQuiz.quizId)
            .collection("questions")
            .get()
            .addOnSuccessListener { docs ->
                for (doc in docs) {
                    val q = doc.toObject(Question::class.java)
                    q.questionId = doc.id
                    questionsMap[q.questionId] = q
                }
                // Run PDF generation on background thread
                Thread {
                    generatePdfWithQuestions(questionsMap)
                }.start()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load questions", Toast.LENGTH_SHORT).show()
                hideProgressDialog()
            }
    }

    private fun generatePdfWithQuestions(questionsMap: Map<String, Question>) {
        val answers = answersMap ?: return
        val document = PdfDocument()
        val paint = Paint()
        var page = createNewPage(document, paint)
        var canvas = page.canvas
        var yPos = 100f

        val pageWidth = page.info.pageWidth
        val margin = 50f
        val lineHeight = 20f

        paint.textSize = 20f
        paint.isFakeBoldText = true
        canvas.drawText("Answer Sheet", margin, 50f, paint)
        paint.isFakeBoldText = false
        paint.textSize = 12f
        yPos = 90f

        val quizTitle = joinedQuiz.quizTitle
        paint.textSize = 14f
        paint.isFakeBoldText = true
        canvas.drawText("Quiz: $quizTitle", margin, yPos, paint)
        yPos += 20f
        paint.isFakeBoldText = false
        paint.textSize = 12f

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        canvas.drawText("Generated: ${dateFormat.format(Date())}", margin, yPos, paint)
        yPos += 30f

        for ((qId, answer) in answers) {
            val question = questionsMap[qId] ?: continue

            // Build answer text based on type
            val answerText = when (question.questionType) {
                "radio" -> {
                    val idx = answer as? Int
                    if (idx != null && idx in question.options.indices) question.options[idx] else "Not answered"
                }
                "checkbox" -> {
                    val indices = answer as? List<*>
                    if (indices != null) {
                        val texts = indices.filterIsInstance<Int>()
                            .mapNotNull { if (it in question.options.indices) question.options[it] else null }
                        if (texts.isNotEmpty()) texts.joinToString(", ") else "None selected"
                    } else "None selected"
                }
                "descriptive" -> answer as? String ?: "Not answered"
                else -> "Not answered"
            }

            val qText = "Q: ${question.text}"
            val lines = splitText(qText, paint, pageWidth - 2 * margin)
            for (line in lines) {
                canvas.drawText(line, margin, yPos, paint)
                yPos += lineHeight
            }

            paint.textSize = 11f
            paint.color = 0xFF4CAF50.toInt()
            canvas.drawText("   ▶ $answerText", margin, yPos, paint)
            yPos += lineHeight + 8f

            paint.color = 0xFF000000.toInt()
            paint.textSize = 12f

            // Check page overflow
            if (yPos + 60f > page.info.pageHeight - margin) {
                document.finishPage(page)
                page = createNewPage(document, paint)
                canvas = page.canvas
                yPos = margin + 20f
                paint.textSize = 14f
                paint.isFakeBoldText = true
                canvas.drawText("Quiz: $quizTitle (continued)", margin, yPos, paint)
                yPos += 30f
                paint.isFakeBoldText = false
                paint.textSize = 12f
            }
        }

        document.finishPage(page)

        // Post back to UI thread to save and share
        runOnUiThread {
            hideProgressDialog()
            val fileName = "AnswerSheet_${joinedQuiz.quizTitle}_${System.currentTimeMillis()}.pdf"
            val file = File(getExternalFilesDir(null), fileName)
            try {
                FileOutputStream(file).use { fos ->
                    document.writeTo(fos)
                }
                Toast.makeText(this, "PDF saved: $fileName", Toast.LENGTH_LONG).show()

                val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, "Here is my answer sheet for the quiz '${joinedQuiz.quizTitle}'")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Share Answer Sheet"))
            } catch (e: Exception) {
                Toast.makeText(this, "PDF generation failed: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                document.close()
            }
        }
    }

    private fun createNewPage(document: PdfDocument, paint: Paint): PdfDocument.Page {
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, document.pages.size + 1).create()
        val page = document.startPage(pageInfo)
        paint.color = 0xFF000000.toInt()
        paint.textSize = 12f
        return page
    }

    private fun splitText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = ""
        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine = testLine
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine)
                }
                currentLine = word
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }
        return lines
    }
}