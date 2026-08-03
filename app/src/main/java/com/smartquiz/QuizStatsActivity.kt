package com.smartquiz

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.smartquiz.databinding.ActivityQuizStatsBinding
import com.google.zxing.BarcodeFormat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class QuizStatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQuizStatsBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var quizId: String
    private lateinit var quizTitle: String
    private lateinit var adapter: ParticipantStatsAdapter
    private val participants = mutableListOf<ParticipantStats>()
    private val filteredList = mutableListOf<ParticipantStats>()

    private var progressDialog: AlertDialog? = null

    data class ParticipantStats(
        val userId: String,
        val name: String,
        val email: String,
        val joinTime: Long,
        val submitTime: Long,
        val durationSeconds: Long,
        val score: Int,
        val totalScore: Int,
        val answers: Map<String, Any>,
        val status: String
    ) {
        val percentage: Int get() = if (totalScore > 0) (score * 100 / totalScore) else 0
        val formattedStart: String get() = formatTime(joinTime)
        val formattedEnd: String get() = formatTime(submitTime)
        // Now uses the utility from QuizApplication
        val formattedDuration: String get() = formatDuration(durationSeconds)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuizStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        quizId = intent.getStringExtra("quizId") ?: ""
        quizTitle = intent.getStringExtra("quizTitle") ?: "Statistics"

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = quizTitle
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_back)

        db = FirebaseFirestore.getInstance()

        loadQuizInfo()
        setupRecyclerView()
        setupSearch()
        setupExportButtons()
        loadParticipants()

        binding.btnDownloadQuestionPaper.setOnClickListener {
            showQuestionPaperOptions()
        }

        // ✅ FIXED: No app‑level role check – Firestore rules handle permissions
        binding.btnCheatLogs.setOnClickListener {
            val intent = Intent(this, AdminCheatLogsActivity::class.java)
            intent.putExtra("quizId", quizId)
            startActivity(intent)
        }
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

    private fun loadQuizInfo() {
        db.collection("quizzes").document(quizId).get()
            .addOnSuccessListener { doc ->
                val quizCode = doc.getString("quizCode") ?: "------"
                binding.tvQuizCode.text = quizCode
                generateQRCode(quizCode)
            }
            .addOnFailureListener {
                binding.tvQuizCode.text = "Error"
                Toast.makeText(this, "Failed to load quiz code", Toast.LENGTH_SHORT).show()
            }
    }

    private fun generateQRCode(code: String) {
        try {
            val encoder = BarcodeEncoder()
            val bitmap: Bitmap = encoder.encodeBitmap(code, BarcodeFormat.QR_CODE, 300, 300)
            binding.ivQrCode.setImageBitmap(bitmap)
            binding.ivQrCode.setOnClickListener {
                shareQRCode(bitmap, code)
            }
        } catch (e: Exception) {
            binding.ivQrCode.setImageResource(R.drawable.ic_qr_code_placeholder)
        }
    }

    private fun shareQRCode(bitmap: Bitmap, code: String) {
        val cachePath = File(cacheDir, "qr_$code.png")
        FileOutputStream(cachePath).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", cachePath)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Join my quiz using code: $code\nScan QR or enter code in SmartQuiz app.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share Quiz"))
    }

    private fun setupRecyclerView() {
        adapter = ParticipantStatsAdapter(filteredList) { participant ->
            showParticipantAnswers(participant)
        }
        binding.rvParticipants.layoutManager = LinearLayoutManager(this)
        binding.rvParticipants.adapter = adapter
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterParticipants()
            }
        })
    }

    private fun setupExportButtons() {
        binding.btnExportCsv.setOnClickListener { exportToCSV() }
        binding.btnExportPdf.setOnClickListener { exportToPDF() }
    }

    private fun loadParticipants() {
        db.collection("quizzes").document(quizId).collection("attempts")
            .get()
            .addOnSuccessListener { attemptDocs ->
                participants.clear()
                for (doc in attemptDocs) {
                    val attempt = doc.data
                    val userId = doc.id
                    val name = attempt["userName"] as? String ?: "Unknown"
                    val email = attempt["email"] as? String ?: ""
                    val joinTime = attempt["joinTime"] as? Long ?: 0
                    val submitTime = attempt["submitTime"] as? Long ?: 0
                    val duration = attempt["duration"] as? Long ?: 0

                    val score = when (val s = attempt["score"]) {
                        is Double -> s.toInt()
                        is Long -> s.toInt()
                        is Int -> s
                        else -> 0
                    }
                    val totalScore = when (val ts = attempt["totalScore"]) {
                        is Double -> ts.toInt()
                        is Long -> ts.toInt()
                        is Int -> ts
                        else -> 0
                    }

                    val answersMap = attempt["answers"] as? Map<String, Any> ?: emptyMap()
                    val status = attempt["status"] as? String ?: "submitted"

                    participants.add(
                        ParticipantStats(
                            userId = userId,
                            name = name,
                            email = email,
                            joinTime = joinTime,
                            submitTime = submitTime,
                            durationSeconds = duration,
                            score = score,
                            totalScore = totalScore,
                            answers = answersMap,
                            status = status
                        )
                    )
                }
                participants.sortWith(compareByDescending<ParticipantStats> { it.score }
                    .thenBy { it.durationSeconds })
                filterParticipants()
                updateSummaryAndChart()
                binding.tvNoData.visibility = if (participants.isEmpty()) View.VISIBLE else View.GONE
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading participants: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun filterParticipants() {
        val query = binding.etSearch.text.toString().trim().lowercase(Locale.getDefault())
        filteredList.clear()
        filteredList.addAll(participants.filter {
            query.isEmpty() || it.name.lowercase().contains(query) || it.email.lowercase().contains(query)
        })
        adapter.submitList(filteredList)
        adapter.notifyDataSetChanged()
    }

    private fun updateSummaryAndChart() {
        val total = participants.size
        val avgScore = if (total > 0) participants.map { it.score }.average() else 0.0
        val totalDuration = participants.sumOf { it.durationSeconds }
        binding.tvSummary.text = "Total: $total | Avg score: ${String.format("%.1f", avgScore)} | Total time: ${formatDuration(totalDuration)}"

        val scores = participants.map { it.score.toFloat() }.sorted()
        val entries = scores.mapIndexed { i, score -> BarEntry(i.toFloat(), score) }
        if (entries.isNotEmpty()) {
            val dataSet = BarDataSet(entries, "Scores")
            dataSet.color = Color.parseColor("#4F46E5")
            val barData = BarData(dataSet)
            binding.barChart.data = barData
            binding.barChart.description.isEnabled = false
            binding.barChart.invalidate()
        } else {
            binding.barChart.clear()
        }
    }

    private fun showParticipantAnswers(participant: ParticipantStats) {
        db.collection("quizzes").document(quizId).collection("questions")
            .get()
            .addOnSuccessListener { questionDocs ->
                val questionMap = mutableMapOf<String, Question>()
                for (doc in questionDocs) {
                    val q = doc.toObject(Question::class.java)
                    q.questionId = doc.id
                    questionMap[q.questionId] = q
                }
                val answerDetails = StringBuilder()
                for ((qId, answer) in participant.answers) {
                    val q = questionMap[qId]
                    if (q != null) {
                        val display = when (q.questionType) {
                            "radio" -> {
                                val idx = when (answer) {
                                    is Int -> answer
                                    is Long -> answer.toInt()
                                    else -> -1
                                }
                                if (idx in q.options.indices) q.options[idx] else "N/A"
                            }
                            "checkbox" -> {
                                val indices = when (answer) {
                                    is List<*> -> answer.mapNotNull {
                                        when (it) {
                                            is Int -> it
                                            is Long -> it.toInt()
                                            else -> null
                                        }
                                    }
                                    else -> emptyList()
                                }
                                if (indices.isNotEmpty()) {
                                    indices.mapNotNull { if (it in q.options.indices) q.options[it] else null }
                                        .joinToString(", ")
                                } else "None selected"
                            }
                            "descriptive" -> answer as? String ?: "N/A"
                            else -> "N/A"
                        }
                        answerDetails.append("Q: ${q.text}\nAnswer: $display\n\n")
                    } else {
                        answerDetails.append("Q: (unknown) $qId\nAnswer: $answer\n\n")
                    }
                }
                AlertDialog.Builder(this)
                    .setTitle("${participant.name}'s Answers")
                    .setMessage(answerDetails.toString())
                    .setPositiveButton("OK", null)
                    .show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load questions", Toast.LENGTH_SHORT).show()
            }
    }

    private fun exportToCSV() {
        val fileName = "quiz_stats_${quizId}_${System.currentTimeMillis()}.csv"
        val file = File(getExternalFilesDir(null), fileName)
        try {
            FileOutputStream(file).use { fos ->
                fos.write("Name,Email,Join Time,Submit Time,Duration,Score,Total,Percentage,Status\n".toByteArray())
                for (p in filteredList) {
                    // Use formatDuration for the duration column
                    val formattedDuration = formatDuration(p.durationSeconds)
                    val line = "${p.name},${p.email},${p.formattedStart},${p.formattedEnd},$formattedDuration,${p.score},${p.totalScore},${p.percentage}%,${p.status}\n"
                    fos.write(line.toByteArray())
                }
            }
            Toast.makeText(this, "Exported to ${file.absolutePath}", Toast.LENGTH_LONG).show()
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share CSV"))
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportToPDF() {
        if (filteredList.isEmpty()) {
            Toast.makeText(this, "No participants to export", Toast.LENGTH_SHORT).show()
            return
        }

        showProgressDialog("Exporting PDF...")
        Thread {
            try {
                val fileName = "quiz_stats_${quizId}_${System.currentTimeMillis()}.pdf"
                val file = File(getExternalFilesDir(null), fileName)
                val document = PdfDocument()
                val paint = Paint()
                val rowHeight = 20f
                val pageWidthPoints = 595
                val pageHeightPoints = 842
                val margin = 50f

                var yPos: Float
                var currentPageIndex = 0

                fun startNewPage(): PdfDocument.Page {
                    currentPageIndex++
                    val pageInfo = PdfDocument.PageInfo.Builder(pageWidthPoints, pageHeightPoints, currentPageIndex).create()
                    val page = document.startPage(pageInfo)
                    val canvas = page.canvas

                    paint.textSize = 16f
                    paint.isFakeBoldText = true
                    canvas.drawText("Quiz Statistics - $quizTitle", margin, 40f, paint)

                    paint.textSize = 10f
                    paint.isFakeBoldText = false
                    canvas.drawText("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}", margin, 55f, paint)

                    paint.textSize = 11f
                    paint.isFakeBoldText = true
                    val headerY = 80f
                    canvas.drawText("Name", margin, headerY, paint)
                    canvas.drawText("Email", 200f, headerY, paint)
                    canvas.drawText("Score", 350f, headerY, paint)
                    canvas.drawText("Percentage", 450f, headerY, paint)
                    canvas.drawText("Status", 530f, headerY, paint)

                    paint.isFakeBoldText = false
                    paint.textSize = 10f
                    return page
                }

                var currentPage = startNewPage()
                var canvas = currentPage.canvas
                yPos = 100f

                for (p in filteredList) {
                    if (yPos + rowHeight > pageHeightPoints - 50) {
                        document.finishPage(currentPage)
                        currentPage = startNewPage()
                        canvas = currentPage.canvas
                        yPos = 100f
                    }
                    canvas.drawText(p.name.take(20), margin, yPos, paint)
                    canvas.drawText(p.email.take(25), 200f, yPos, paint)
                    canvas.drawText("${p.score}/${p.totalScore}", 350f, yPos, paint)
                    canvas.drawText("${p.percentage}%", 450f, yPos, paint)
                    canvas.drawText(p.status, 530f, yPos, paint)
                    yPos += rowHeight
                }

                document.finishPage(currentPage)

                FileOutputStream(file).use { fos ->
                    document.writeTo(fos)
                }
                document.close()

                runOnUiThread {
                    hideProgressDialog()
                    Toast.makeText(this, "PDF saved to ${file.absolutePath}", Toast.LENGTH_LONG).show()
                    val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/pdf")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(intent, "Open PDF"))
                }
            } catch (e: Exception) {
                runOnUiThread {
                    hideProgressDialog()
                    Toast.makeText(this, "PDF export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun showQuestionPaperOptions() {
        val options = arrayOf("📄 Question Paper Only", "📝 Question Paper with Answers")
        AlertDialog.Builder(this)
            .setTitle("Download Question Paper")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> generateQuestionPaperPdf(includeAnswers = false)
                    1 -> generateQuestionPaperPdf(includeAnswers = true)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun generateQuestionPaperPdf(includeAnswers: Boolean) {
        if (quizId.isEmpty()) {
            Toast.makeText(this, "Quiz ID missing", Toast.LENGTH_SHORT).show()
            return
        }

        showProgressDialog("Loading questions...")

        db.collection("quizzes").document(quizId)
            .collection("questions")
            .get()
            .addOnSuccessListener { docs ->
                val questions = mutableListOf<Question>()
                for (doc in docs) {
                    val q = doc.toObject(Question::class.java)
                    q.questionId = doc.id
                    questions.add(q)
                }
                if (questions.isEmpty()) {
                    Toast.makeText(this, "No questions found", Toast.LENGTH_SHORT).show()
                    hideProgressDialog()
                    return@addOnSuccessListener
                }

                if (includeAnswers) {
                    val tasks = questions.map { q ->
                        db.collection("quizzes").document(quizId)
                            .collection("questions_private").document(q.questionId)
                            .get()
                            .continueWith { task ->
                                if (task.isSuccessful && task.result.exists()) {
                                    val data = task.result
                                    when (q.questionType) {
                                        "radio" -> q.correctAnswerIndex = data.getLong("correctAnswerIndex")?.toInt() ?: 0
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
                                        "descriptive" -> q.correctAnswerText = data.getString("correctAnswerText") ?: ""
                                    }
                                }
                                q
                            }
                    }
                    Tasks.whenAllSuccess<Question>(tasks)
                        .addOnSuccessListener { questionList ->
                            Thread {
                                generatePdfWithQuestionsAndAnswers(questionList, includeAnswers = true)
                            }.start()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Failed to load correct answers: ${e.message}", Toast.LENGTH_SHORT).show()
                            hideProgressDialog()
                        }
                } else {
                    Thread {
                        generatePdfWithQuestionsAndAnswers(questions, includeAnswers = false)
                    }.start()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load questions: ${e.message}", Toast.LENGTH_SHORT).show()
                hideProgressDialog()
            }
    }

    private fun generatePdfWithQuestionsAndAnswers(questions: List<Question>, includeAnswers: Boolean) {
        val document = PdfDocument()
        val paint = Paint()
        var page = createNewQuestionPaperPage(document, paint)
        var canvas = page.canvas
        var yPos = 100f

        val pageWidth = page.info.pageWidth
        val margin = 50f
        val lineHeight = 20f

        paint.textSize = 20f
        paint.isFakeBoldText = true
        val title = if (includeAnswers) "Question Paper with Answers" else "Question Paper"
        canvas.drawText(title, margin, 50f, paint)
        paint.isFakeBoldText = false
        paint.textSize = 14f
        yPos = 90f

        paint.textSize = 14f
        paint.isFakeBoldText = true
        canvas.drawText("Quiz: $quizTitle", margin, yPos, paint)
        yPos += 20f
        paint.isFakeBoldText = false
        paint.textSize = 12f

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        canvas.drawText("Generated: ${dateFormat.format(Date())}", margin, yPos, paint)
        yPos += 30f

        val totalMarks = questions.sumOf { it.points }
        canvas.drawText("Total Marks: $totalMarks", margin, yPos, paint)
        yPos += 30f

        for ((index, q) in questions.withIndex()) {
            if (yPos + 100f > page.info.pageHeight - margin) {
                document.finishPage(page)
                page = createNewQuestionPaperPage(document, paint)
                canvas = page.canvas
                yPos = margin + 20f
                paint.textSize = 14f
                paint.isFakeBoldText = true
                canvas.drawText("Quiz: $quizTitle (continued)", margin, yPos, paint)
                yPos += 30f
                paint.isFakeBoldText = false
                paint.textSize = 12f
            }

            val qText = "${index + 1}. ${q.text}"
            val lines = splitTextForPdf(qText, paint, pageWidth - 2 * margin)
            for (line in lines) {
                canvas.drawText(line, margin, yPos, paint)
                yPos += lineHeight
            }

            paint.textSize = 11f
            if (q.questionType != "descriptive") {
                for ((optIdx, option) in q.options.withIndex()) {
                    val prefix = if (includeAnswers && (
                                (q.questionType == "radio" && optIdx == q.correctAnswerIndex) ||
                                        (q.questionType == "checkbox" && q.correctAnswerIndices.contains(optIdx))
                                )) "✓ " else "  "
                    val optText = "$prefix${('A' + optIdx)}. $option"
                    canvas.drawText(optText, margin + 20f, yPos, paint)
                    yPos += lineHeight
                }
            }

            if (includeAnswers) {
                when (q.questionType) {
                    "radio" -> {
                        val correctLetter = ('A' + q.correctAnswerIndex).toString()
                        paint.textSize = 11f
                        paint.color = 0xFF4CAF50.toInt()
                        canvas.drawText("   Correct Answer: $correctLetter", margin + 20f, yPos, paint)
                        yPos += lineHeight + 8f
                    }
                    "checkbox" -> {
                        val correctLetters = q.correctAnswerIndices.map { ('A' + it).toString() }
                        val lettersStr = if (correctLetters.isNotEmpty()) correctLetters.joinToString(", ") else "None"
                        paint.textSize = 11f
                        paint.color = 0xFF4CAF50.toInt()
                        canvas.drawText("   Correct Options: $lettersStr", margin + 20f, yPos, paint)
                        yPos += lineHeight + 8f
                    }
                    "descriptive" -> {
                        paint.textSize = 11f
                        paint.color = 0xFF4CAF50.toInt()
                        canvas.drawText("   Correct Answer: ${q.correctAnswerText}", margin + 20f, yPos, paint)
                        yPos += lineHeight + 8f
                    }
                }
                paint.color = 0xFF000000.toInt()
                paint.textSize = 12f
            }

            paint.textSize = 11f
            paint.color = 0xFF2196F3.toInt()
            canvas.drawText("   Points: ${q.points}", margin + 20f, yPos, paint)
            yPos += lineHeight + 10f
            paint.color = 0xFF000000.toInt()
            paint.textSize = 12f
        }

        document.finishPage(page)

        runOnUiThread {
            hideProgressDialog()
            val suffix = if (includeAnswers) "_with_answers" else "_only"
            val fileName = "QuestionPaper${suffix}_${quizTitle}_${System.currentTimeMillis()}.pdf"
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
                    val shareText = if (includeAnswers) "Question paper with answers for '$quizTitle'" else "Question paper (without answers) for '$quizTitle'"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Share Question Paper"))
            } catch (e: Exception) {
                Toast.makeText(this, "PDF generation failed: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                document.close()
            }
        }
    }

    private fun createNewQuestionPaperPage(document: PdfDocument, paint: Paint): PdfDocument.Page {
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, document.pages.size + 1).create()
        val page = document.startPage(pageInfo)
        paint.color = 0xFF000000.toInt()
        paint.textSize = 12f
        return page
    }

    private fun splitTextForPdf(text: String, paint: Paint, maxWidth: Float): List<String> {
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

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

// ========== Utility functions ==========
fun formatTime(timestamp: Long): String {
    if (timestamp == 0L) return "N/A"
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}