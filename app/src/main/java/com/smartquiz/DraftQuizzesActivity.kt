package com.smartquiz

import android.content.Intent
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.smartquiz.databinding.ActivityDraftQuizzesBinding
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class DraftQuizzesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDraftQuizzesBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private val draftList = mutableListOf<Quiz>()
    private lateinit var adapter: DraftQuizAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDraftQuizzesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_back)
        supportActionBar?.title = "Draft Quizzes"

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        adapter = DraftQuizAdapter(draftList) { action, quiz ->
            when (action) {
                DraftQuizAdapter.Action.EDIT -> editDraft(quiz)
                DraftQuizAdapter.Action.DELETE -> deleteDraft(quiz)
                DraftQuizAdapter.Action.DOWNLOAD_PDF -> downloadQuestionPaper(quiz)
            }
        }

        binding.rvDrafts.layoutManager = LinearLayoutManager(this)
        binding.rvDrafts.adapter = adapter

        binding.btnCreateQuiz.setOnClickListener {
            startActivity(Intent(this, QuizCreationActivity::class.java))
            finish()
        }

        loadDrafts()
    }

    // ---------- LOAD DRAFTS FROM FIRESTORE ----------
    private fun loadDrafts() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("quizzes")
            .whereEqualTo("creatorId", uid)
            .whereEqualTo("status", "DRAFT")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Toast.makeText(this, "Error loading drafts: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                draftList.clear()
                snapshots?.forEach { doc ->
                    val quiz = doc.toObject(Quiz::class.java).apply { quizId = doc.id }
                    draftList.add(quiz)
                }
                // Sort by updatedAt descending (newest first)
                draftList.sortByDescending { it.updatedAt }
                adapter.notifyDataSetChanged()
                binding.tvEmpty.visibility = if (draftList.isEmpty()) View.VISIBLE else View.GONE
                binding.rvDrafts.visibility = if (draftList.isEmpty()) View.GONE else View.VISIBLE
            }
    }

    private fun editDraft(quiz: Quiz) {
        val intent = Intent(this, QuizCreationActivity::class.java)
        intent.putExtra("quizId", quiz.quizId)
        intent.putExtra("isDraft", true)
        startActivity(intent)
        finish()
    }

    // ---------- DELETE DRAFT (with subcollections) ----------
    private fun deleteDraft(quiz: Quiz) {
        AlertDialog.Builder(this)
            .setTitle("Delete Draft")
            .setMessage("This action will permanently remove this draft and its questions.")
            .setPositiveButton("Delete") { _, _ ->
                deleteQuizAndQuestions(quiz.quizId) {
                    Toast.makeText(this, "Draft deleted", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteQuizAndQuestions(quizId: String, onComplete: () -> Unit) {
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
                    db.collection("quizzes").document(quizId).delete()
                        .addOnSuccessListener { onComplete() }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Failed to delete quiz: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }.addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to delete questions: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to fetch questions: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ---------- DOWNLOAD QUESTION PAPER (Draft PDF with Answers) ----------
    private fun downloadQuestionPaper(quiz: Quiz) {
        db.collection("quizzes").document(quiz.quizId).collection("questions")
            .get()
            .addOnSuccessListener { questionDocs ->
                // Deduplicate by questionId using a Map
                val uniqueQuestions = mutableMapOf<String, Question>()
                for (qDoc in questionDocs) {
                    val q = qDoc.toObject(Question::class.java).apply { questionId = qDoc.id }
                    if (!uniqueQuestions.containsKey(q.questionId)) {
                        uniqueQuestions[q.questionId] = q
                    }
                }
                val questions = uniqueQuestions.values.toList()

                val tasks = questions.map { q ->
                    db.collection("quizzes").document(quiz.quizId)
                        .collection("questions_private").document(q.questionId)
                        .get()
                        .continueWith { task ->
                            if (task.isSuccessful && task.result.exists()) {
                                val data = task.result
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
                            q
                        }
                }
                com.google.android.gms.tasks.Tasks.whenAllComplete(tasks)
                    .addOnCompleteListener {
                        val resolvedQuestions = tasks.mapNotNull { if (it.isSuccessful) it.result else null }
                        generateDraftPDF(quiz, resolvedQuestions)
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load questions: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ---------- PDF GENERATION ----------
    private fun generateDraftPDF(quiz: Quiz, questions: List<Question>) {
        try {
            // You can replace this with your existing PDFGenerator if available
            val file = createSimpleDraftPDF(quiz, questions)
            if (file != null) {
                sharePDF(file)
            } else {
                Toast.makeText(this, "Failed to generate PDF", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "PDF generation error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- SIMPLE DRAFT PDF (FALLBACK) ----------
    private fun createSimpleDraftPDF(quiz: Quiz, questions: List<Question>): File? {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 portrait
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        val paint = android.graphics.Paint()
        paint.textSize = 14f
        var y = 40

        // Title
        paint.textSize = 20f
        paint.isFakeBoldText = true
        canvas.drawText("SMART QUIZ - DRAFT", 50f, y.toFloat(), paint)
        y += 40
        paint.textSize = 16f
        canvas.drawText("Quiz: ${quiz.title}", 50f, y.toFloat(), paint)
        y += 30
        canvas.drawText("Status: ${quiz.status}", 50f, y.toFloat(), paint)
        y += 40
        paint.isFakeBoldText = false
        paint.textSize = 14f

        for ((index, q) in questions.withIndex()) {
            if (y > 750) {
                pdfDocument.finishPage(page)
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                y = 40
            }
            canvas.drawText("Q${index + 1}. ${q.text}", 50f, y.toFloat(), paint)
            y += 25
            if (q.questionType != "descriptive") {
                for (i in q.options.indices) {
                    canvas.drawText("  ${'A' + i}. ${q.options[i]}", 60f, y.toFloat(), paint)
                    y += 20
                }
                paint.isFakeBoldText = true
                val correctStr = when (q.questionType) {
                    "radio" -> "Correct Answer: ${'A' + q.correctAnswerIndex}"
                    "checkbox" -> {
                        val indices = q.correctAnswerIndices.map { 'A' + it }
                        "Correct Answers: ${indices.joinToString(", ")}"
                    }
                    else -> ""
                }
                canvas.drawText(correctStr, 50f, y.toFloat(), paint)
                y += 20
                paint.isFakeBoldText = false
            } else {
                canvas.drawText("  Correct Answer: ${q.correctAnswerText}", 60f, y.toFloat(), paint)
                y += 20
            }
            canvas.drawText("Marks: ${q.points}", 50f, y.toFloat(), paint)
            y += 30
            canvas.drawText("--------------------------------", 50f, y.toFloat(), paint)
            y += 20
        }

        pdfDocument.finishPage(page)

        val file = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "${quiz.title}_draft.pdf")
        pdfDocument.writeTo(FileOutputStream(file))
        pdfDocument.close()
        return file
    }

    // ---------- SHARE PDF ----------
    private fun sharePDF(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Draft PDF"))
        } catch (e: Exception) {
            Toast.makeText(this, "Error sharing PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}