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

    data class ParticipantStats(
        val userId: String,
        val name: String,
        val email: String,
        val joinTime: Long,
        val submitTime: Long,
        val durationSeconds: Long,
        val score: Int,
        val totalScore: Int,
        val answers: Map<String, Int>,
        val status: String
    ) {
        val percentage: Int get() = if (totalScore > 0) (score * 100 / totalScore) else 0
        val formattedStart: String get() = formatTime(joinTime)
        val formattedEnd: String get() = formatTime(submitTime)
        val formattedDuration: String get() = formatDuration(durationSeconds)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuizStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        quizId = intent.getStringExtra("quizId") ?: ""
        quizTitle = intent.getStringExtra("quizTitle") ?: "Statistics"
        supportActionBar?.title = quizTitle
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        db = FirebaseFirestore.getInstance()

        // Load quiz code and QR
        loadQuizInfo()

        setupRecyclerView()
        setupSearch()
        setupExportButtons()
        loadParticipants()
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
                    val score = (attempt["score"] as? Long)?.toInt() ?: 0
                    val totalScore = (attempt["totalScore"] as? Long)?.toInt() ?: 0
                    val answersMap = attempt["answers"] as? Map<String, Int> ?: emptyMap()
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
                for ((qId, selectedIdx) in participant.answers) {
                    val q = questionMap[qId]
                    if (q != null) {
                        val selectedOption = q.options.getOrNull(selectedIdx) ?: "N/A"
                        answerDetails.append("Q: ${q.text}\nAnswer: $selectedOption\n\n")
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
                fos.write("Name,Email,Join Time,Submit Time,Duration (sec),Score,Total,Percentage,Status\n".toByteArray())
                for (p in filteredList) {
                    val line = "${p.name},${p.email},${p.formattedStart},${p.formattedEnd},${p.durationSeconds},${p.score},${p.totalScore},${p.percentage}%,${p.status}\n"
                    fos.write(line.toByteArray())
                }
            }
            Toast.makeText(this, "Exported to ${file.absolutePath}", Toast.LENGTH_LONG).show()
            // share CSV
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
            
            // Draw title
            paint.textSize = 16f
            paint.isFakeBoldText = true
            canvas.drawText("Quiz Statistics - $quizTitle", margin, 40f, paint)
            
            // Draw generation date
            paint.textSize = 10f
            paint.isFakeBoldText = false
            canvas.drawText("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}", margin, 55f, paint)
            
            // Draw headers
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

        // Save document
        try {
            FileOutputStream(file).use { fos ->
                document.writeTo(fos)
            }
            Toast.makeText(this, "PDF saved to ${file.absolutePath}", Toast.LENGTH_LONG).show()

            // Open the PDF for viewing/sharing
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Open PDF"))
        } catch (e: Exception) {
            Toast.makeText(this, "PDF export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            document.close()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

// Utility functions
fun formatTime(timestamp: Long): String {
    if (timestamp == 0L) return "N/A"
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return String.format("%02d:%02d:%02d", hours, minutes, secs)
}