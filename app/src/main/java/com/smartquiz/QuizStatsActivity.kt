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
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
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
    private var listener: ListenerRegistration? = null
    private var currentSortOption = 0

    private lateinit var tvTotalParticipants: TextView
    private lateinit var tvCompletionRate: TextView
    private lateinit var tvAvgScore: TextView
    private lateinit var tvHighestScore: TextView
    private lateinit var tvLowestScore: TextView
    private lateinit var tvAvgDuration: TextView

    data class ParticipantStats(
        val userId: String,
        var name: String,
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
        val formattedDuration: String get() = formatDuration(durationSeconds)
        val statusDisplay: String get() = when (status) {
            "Completed" -> "Completed"
            "TIME_EXPIRED" -> "Time Expired"
            "CHEATING_AUTO_SUBMITTED" -> "Automatically Submitted"
            else -> "Submitted"
        }
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

        tvTotalParticipants = binding.tvTotalParticipants
        tvCompletionRate = binding.tvCompletionRate
        tvAvgScore = binding.tvAvgScore
        tvHighestScore = binding.tvHighestScore
        tvLowestScore = binding.tvLowestScore
        tvAvgDuration = binding.tvAvgDuration

        loadQuizInfo()
        setupRecyclerView()
        setupSearch()
        setupSorting()
        setupExportButtons()
        startListening()

        binding.btnDownloadQuestionPaper.setOnClickListener {
            showQuestionPaperOptions()
        }

        binding.btnCheatLogs.setOnClickListener {
            val intent = Intent(this, AdminCheatLogsActivity::class.java)
            intent.putExtra("quizId", quizId)
            startActivity(intent)
        }

        binding.btnAnalytics.setOnClickListener {
            val intent = Intent(this, CreatorAnalyticsActivity::class.java)
            intent.putExtra("quizId", quizId)
            intent.putExtra("quizTitle", quizTitle)
            startActivity(intent)
        }
    }

    private fun setupSorting() {
        val spinner = binding.spinnerSort
        ArrayAdapter.createFromResource(
            this,
            R.array.sort_options,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter
        }
        spinner.setSelection(0)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentSortOption = position
                applyFiltersAndSort()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun applyFiltersAndSort() {
        val query = binding.etSearch.text.toString().trim().lowercase(Locale.getDefault())
        val filtered = participants.filter {
            query.isEmpty() || it.name.lowercase().contains(query) || it.email.lowercase().contains(query)
        }
        val sorted = when (currentSortOption) {
            0 -> filtered.sortedBy { it.name.lowercase(Locale.getDefault()) }
            1 -> filtered.sortedByDescending { it.name.lowercase(Locale.getDefault()) }
            2 -> filtered.sortedByDescending { it.score }
            3 -> filtered.sortedBy { it.score }
            4 -> filtered.sortedByDescending { it.submitTime }
            5 -> filtered.sortedBy { it.submitTime }
            6 -> filtered.sortedByDescending { it.durationSeconds }
            7 -> filtered.sortedBy { it.durationSeconds }
            else -> filtered.sortedBy { it.name.lowercase(Locale.getDefault()) }
        }
        filteredList.clear()
        filteredList.addAll(sorted)
        adapter.submitList(filteredList)
        updateSummary(participants)
        updateChart()
    }

    // ---------- LOAD PARTICIPANTS WITH NAME RESOLUTION ----------
    private fun startListening() {
        listener = db.collection("quizzes").document(quizId)
            .collection("attempts")
            .orderBy("submitTime", Query.Direction.DESCENDING)
            .limit(500)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Toast.makeText(this, "Error loading participants: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                participants.clear()
                val tempList = mutableListOf<ParticipantStats>()
                val missingNameUsers = mutableListOf<String>()

                snapshots?.forEach { doc ->
                    val attempt = doc.data
                    val userId = doc.id
                    val email = attempt["email"] as? String ?: ""
                    val emailPrefix = email.substringBefore("@")
                    var name = attempt["userName"] as? String ?: ""

                    // If name is missing or just the email prefix, we'll fetch profile name
                    if (name.isBlank() || name == emailPrefix) {
                        missingNameUsers.add(userId)
                    }

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

                    tempList.add(
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

                // Resolve missing names from Firestore
                if (missingNameUsers.isNotEmpty()) {
                    val tasks = missingNameUsers.map { userId ->
                        db.collection("users").document(userId).get()
                    }
                    Tasks.whenAllSuccess<DocumentSnapshot>(tasks)
                        .addOnSuccessListener { docs ->
                            val nameMap = mutableMapOf<String, String>()
                            docs.forEach { userDoc ->
                                val profileName = userDoc.getString("name") ?: ""
                                if (profileName.isNotBlank()) {
                                    nameMap[userDoc.id] = profileName
                                }
                            }
                            // Update names in tempList
                            tempList.forEach { p ->
                                if (p.name.isBlank() || p.name == p.email.substringBefore("@")) {
                                    nameMap[p.userId]?.let { p.name = it }
                                }
                            }
                            participants.addAll(tempList)
                            applyFiltersAndSort()
                            binding.tvNoData.visibility = if (participants.isEmpty()) View.VISIBLE else View.GONE
                        }
                        .addOnFailureListener {
                            // If profile fetch fails, use tempList as is
                            participants.addAll(tempList)
                            applyFiltersAndSort()
                            binding.tvNoData.visibility = if (participants.isEmpty()) View.VISIBLE else View.GONE
                        }
                } else {
                    participants.addAll(tempList)
                    applyFiltersAndSort()
                    binding.tvNoData.visibility = if (participants.isEmpty()) View.VISIBLE else View.GONE
                }
            }
    }

    private fun updateSummary(participants: List<ParticipantStats>) {
        val total = participants.size
        val completed = participants.count { it.status == "Completed" }
        val completionRate = if (total > 0) (completed * 100.0 / total) else 0.0
        val scores = participants.map { it.score.toDouble() }
        val avgScore = if (scores.isNotEmpty()) scores.average() else 0.0
        val highest = if (scores.isNotEmpty()) scores.maxOrNull() ?: 0.0 else 0.0
        val lowest = if (scores.isNotEmpty()) scores.minOrNull() ?: 0.0 else 0.0
        val durations = participants.map { it.durationSeconds }
        val avgDuration = if (durations.isNotEmpty()) durations.average() else 0.0

        tvTotalParticipants.text = total.toString()
        tvCompletionRate.text = String.format("%.0f%%", completionRate)
        tvAvgScore.text = String.format("%.1f", avgScore)
        tvHighestScore.text = "Highest: ${highest.toInt()}"
        tvLowestScore.text = "Lowest: ${lowest.toInt()}"
        tvAvgDuration.text = "Avg Time: ${formatDuration(avgDuration.toLong())}"
    }

    private fun updateChart() {
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
            binding.barChart.invalidate()
        }
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
                applyFiltersAndSort()
            }
        })
    }

    private fun setupExportButtons() {
        binding.btnExportCsv.setOnClickListener { exportToCSV() }
        binding.btnExportPdf.setOnClickListener { exportToPDF() }
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

    // ---------- CSV EXPORT (includes Email) ----------
    private fun exportToCSV() {
        val sortedList = getSortedExportList()
        if (sortedList.isEmpty()) {
            Toast.makeText(this, "No participants to export", Toast.LENGTH_SHORT).show()
            return
        }
        val fileName = "quiz_stats_${quizId}_${System.currentTimeMillis()}.csv"
        val file = File(getExternalFilesDir(null), fileName)
        try {
            FileOutputStream(file).use { fos ->
                fos.write("Sr No.,Name,Email,Score,Percentage,Duration,Status\n".toByteArray())
                sortedList.forEachIndexed { index, p ->
                    val line = "${index+1},${p.name},${p.email},${p.score}/${p.totalScore},${p.percentage}%,${p.formattedDuration},${p.statusDisplay}\n"
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

    // ---------- PDF EXPORT (Email removed to prevent overlap) ----------
    private fun exportToPDF() {
        val sortedList = getSortedExportList().take(5000)
        if (sortedList.isEmpty()) {
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
                val pageWidthPoints = 595
                val pageHeightPoints = 842
                val margin = 50f
                val rowHeight = 20f
                // Columns: Sr(35), Name(120), Score(70), %(50), Duration(80), Status(100)
                val colWidths = floatArrayOf(35f, 120f, 70f, 50f, 80f, 100f)

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
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    canvas.drawText("Generated: ${dateFormat.format(Date())}", margin, 55f, paint)

                    paint.textSize = 9f
                    val total = sortedList.size
                    val completed = sortedList.count { it.status == "Completed" }
                    val compRate = if (total > 0) (completed * 100.0 / total) else 0.0
                    val avgScore = if (total > 0) sortedList.map { it.score }.average() else 0.0
                    val highest = if (total > 0) (sortedList.maxByOrNull { it.score }?.score ?: 0) else 0
                    val lowest = if (total > 0) (sortedList.minByOrNull { it.score }?.score ?: 0) else 0
                    val avgDur = if (total > 0) sortedList.map { it.durationSeconds }.average() else 0.0
                    canvas.drawText("Participants: $total | Completed: $completed | Completion: ${String.format("%.1f", compRate)}%", margin, 72f, paint)
                    canvas.drawText("Avg Score: ${String.format("%.1f", avgScore)} | Highest: $highest | Lowest: $lowest | Avg Duration: ${formatDuration(avgDur.toLong())}", margin, 84f, paint)

                    paint.textSize = 10f
                    paint.isFakeBoldText = true
                    val headerY = 100f
                    val xPositions = floatArrayOf(
                        margin,
                        margin + colWidths[0],
                        margin + colWidths[0] + colWidths[1],
                        margin + colWidths[0] + colWidths[1] + colWidths[2],
                        margin + colWidths[0] + colWidths[1] + colWidths[2] + colWidths[3],
                        margin + colWidths[0] + colWidths[1] + colWidths[2] + colWidths[3] + colWidths[4]
                    )
                    canvas.drawText("Sr", xPositions[0], headerY, paint)
                    canvas.drawText("Name", xPositions[1], headerY, paint)
                    canvas.drawText("Score", xPositions[2], headerY, paint)
                    canvas.drawText("%", xPositions[3], headerY, paint)
                    canvas.drawText("Duration", xPositions[4], headerY, paint)
                    canvas.drawText("Status", xPositions[5], headerY, paint)

                    paint.isFakeBoldText = false
                    paint.textSize = 9f
                    return page
                }

                var currentPage = startNewPage()
                var canvas = currentPage.canvas
                yPos = 120f
                val xPositions = floatArrayOf(
                    margin,
                    margin + colWidths[0],
                    margin + colWidths[0] + colWidths[1],
                    margin + colWidths[0] + colWidths[1] + colWidths[2],
                    margin + colWidths[0] + colWidths[1] + colWidths[2] + colWidths[3],
                    margin + colWidths[0] + colWidths[1] + colWidths[2] + colWidths[3] + colWidths[4]
                )

                sortedList.forEachIndexed { index, p ->
                    if (yPos + rowHeight > pageHeightPoints - 50) {
                        document.finishPage(currentPage)
                        currentPage = startNewPage()
                        canvas = currentPage.canvas
                        yPos = 120f
                    }
                    val srNo = (index + 1).toString()
                    val name = p.name.take(25)
                    val score = "${p.score}/${p.totalScore}"
                    val percent = "${p.percentage}%"
                    val duration = p.formattedDuration
                    val status = p.statusDisplay
                    canvas.drawText(srNo, xPositions[0], yPos, paint)
                    canvas.drawText(name, xPositions[1], yPos, paint)
                    canvas.drawText(score, xPositions[2], yPos, paint)
                    canvas.drawText(percent, xPositions[3], yPos, paint)
                    canvas.drawText(duration, xPositions[4], yPos, paint)
                    canvas.drawText(status, xPositions[5], yPos, paint)
                    yPos += rowHeight
                }

                document.finishPage(currentPage)
                FileOutputStream(file).use { fos -> document.writeTo(fos) }
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

    // ---------- EXPORT HELPERS ----------
    private fun getSortedExportList(): List<ParticipantStats> {
        // Use filteredList (already sorted in UI) but ensure names are resolved
        // filteredList already has resolved names from the listener
        return filteredList.sortedBy { it.name.lowercase(Locale.getDefault()) }
    }

    // ---------- Question Paper PDF (unchanged) ----------
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

    override fun onStop() {
        super.onStop()
        listener?.remove()
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