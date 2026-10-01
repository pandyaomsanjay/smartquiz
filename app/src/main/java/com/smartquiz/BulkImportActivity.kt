package com.smartquiz

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.smartquiz.databinding.ActivityBulkImportBinding
import java.io.File
import java.io.FileOutputStream

class BulkImportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBulkImportBinding
    private var selectedUri: Uri? = null
    private var selectedFormat: QuestionFileParser.Format? = null

    // Pending questions (post-parse). The user can edit/delete entries here.
    private val pendingQuestions = mutableListOf<Question>()
    // Duplicate indices (positions in pendingQuestions) flagged by the parser.
    private val duplicateIndices = mutableSetOf<Int>()
    // Original parse errors (row-level).
    private var parseErrors: List<QuestionFileParser.RowError> = emptyList()

    private lateinit var previewAdapter: ImportPreviewAdapter

    private val TAG = "BulkImport"

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            Log.d(TAG, "File picker cancelled")
            return@registerForActivityResult
        }
        try {
            handlePickedFile(uri)
        } catch (t: Throwable) {
            Log.e(TAG, "Unhandled crash in file picker callback", t)
            Toast.makeText(
                this,
                "Could not read file: ${t.message ?: t.javaClass.simpleName}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBulkImportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // ------------------------------------------------------------
        // Preview RecyclerView
        // ------------------------------------------------------------
        previewAdapter = ImportPreviewAdapter(
            emptyList(),
            onEdit = { pendingIndex -> editPendingQuestionSafely(pendingIndex) },
            onDelete = { pendingIndex -> deletePendingQuestionSafely(pendingIndex) }
        )
        binding.rvPreview.layoutManager = LinearLayoutManager(this)
        binding.rvPreview.adapter = previewAdapter
        binding.rvPreview.visibility = View.GONE

        // ------------------------------------------------------------
        // File picker — system OpenDocument picker, multiple MIME types
        // ------------------------------------------------------------
        binding.btnSelectFile.setOnClickListener {
            try {
                filePicker.launch(
                    arrayOf(
                        "text/csv",
                        "text/comma-separated-values",
                        "application/json",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "*/*"
                    )
                )
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to launch file picker", t)
                Toast.makeText(this, "Could not open file picker", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnImport.setOnClickListener { confirmImportSafely() }

        binding.btnDownloadCsvTemplate.setOnClickListener { downloadCsvTemplateSafely() }

        binding.btnDownloadXlsxTemplate.setOnClickListener {
            Toast.makeText(
                this,
                "Open the CSV template in Excel and save as .xlsx to import.",
                Toast.LENGTH_LONG
            ).show()
        }

        binding.btnViewJsonFormat.setOnClickListener { showJsonFormatSafely() }

        // Duplicate action buttons — hidden by default
        binding.btnRemoveDuplicates.setOnClickListener { removeDuplicatesSafely() }
        binding.btnKeepDuplicates.setOnClickListener { keepDuplicatesSafely() }
        binding.llDuplicateActions.visibility = View.GONE
    }

    // ==================================================================
    // FILE PICK → PARSE → PREVIEW
    // ==================================================================
    private fun handlePickedFile(uri: Uri) {
        try {
            val format = QuestionFileParser.detectFormat(this, uri)
            if (format == null) {
                Toast.makeText(
                    this,
                    "Unsupported file type. Use .csv, .json, or .xlsx",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            selectedUri = uri
            selectedFormat = format

            val name = QuestionFileParser.queryFileName(this, uri).ifBlank { "file" }
            binding.tvSelectedFile.text = "Selected file:\n$name"

            binding.tvFileStats.visibility = View.VISIBLE
            binding.tvFileStats.text = "Parsing…"

            val result = try {
                QuestionFileParser.parse(this, uri, format)
            } catch (t: Throwable) {
                Log.e(TAG, "Parse threw", t)
                binding.tvFileStats.text =
                    "Failed to parse: ${t.message ?: t.javaClass.simpleName}"
                binding.rvPreview.visibility = View.GONE
                binding.llActions.visibility = View.GONE
                binding.llDuplicateActions.visibility = View.GONE
                return
            }

            parseErrors = result.invalid
            pendingQuestions.clear()
            pendingQuestions.addAll(result.valid.map { it.question })

            duplicateIndices.clear()
            duplicateIndices.addAll(QuestionFileParser.findDuplicateIndices(result.valid))

            val total = pendingQuestions.size + parseErrors.size
            binding.tvFileStats.text = buildString {
                append("$total questions detected\n")
                append("Valid: ${pendingQuestions.size}")
                if (parseErrors.isNotEmpty()) append("   Invalid: ${parseErrors.size}")
                if (duplicateIndices.isNotEmpty())
                    append("\n⚠ ${duplicateIndices.size} duplicate questions detected")
            }

            if (pendingQuestions.isEmpty() && parseErrors.isEmpty()) {
                binding.rvPreview.visibility = View.GONE
                binding.llActions.visibility = View.GONE
                binding.llDuplicateActions.visibility = View.GONE
                binding.btnImport.isEnabled = false
                Toast.makeText(this, "No questions found in file", Toast.LENGTH_SHORT).show()
                return
            }

            renderPreview()

            binding.rvPreview.visibility = View.VISIBLE
            binding.llActions.visibility = View.VISIBLE
            binding.llDuplicateActions.visibility =
                if (duplicateIndices.isNotEmpty()) View.VISIBLE else View.GONE
            binding.btnImport.isEnabled = pendingQuestions.isNotEmpty()

        } catch (t: Throwable) {
            Log.e(TAG, "Unhandled crash in handlePickedFile", t)
            binding.tvFileStats.visibility = View.VISIBLE
            binding.tvFileStats.text = "Error: ${t.message ?: t.javaClass.simpleName}"
            binding.rvPreview.visibility = View.GONE
            binding.llActions.visibility = View.GONE
            binding.llDuplicateActions.visibility = View.GONE
            Toast.makeText(
                this,
                "Could not read file. Check Logcat for details.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ==================================================================
    // PREVIEW RENDERING
    // ==================================================================
    private fun renderPreview() {
        try {
            val rows = mutableListOf<ImportPreviewAdapter.Row>()
            var displayIndex = 0

            // 1. Valid / duplicate questions first (in original order)
            pendingQuestions.forEachIndexed { i, q ->
                displayIndex++
                if (duplicateIndices.contains(i)) {
                    rows.add(ImportPreviewAdapter.Row.Duplicate(displayIndex, i, q))
                } else {
                    rows.add(ImportPreviewAdapter.Row.Valid(displayIndex, i, q))
                }
            }

            // 2. Row-level errors last
            parseErrors.forEach { err ->
                displayIndex++
                rows.add(ImportPreviewAdapter.Row.Invalid(displayIndex, err.error, err.raw))
            }

            previewAdapter.update(rows)
        } catch (t: Throwable) {
            Log.e(TAG, "renderPreview failed", t)
        }
    }

    // ==================================================================
    // EDIT PENDING QUESTION (safe wrapper)
    // ==================================================================
    private fun editPendingQuestionSafely(index: Int) {
        try {
            editPendingQuestion(index)
        } catch (t: Throwable) {
            Log.e(TAG, "editPendingQuestion crashed", t)
            Toast.makeText(
                this,
                "Could not edit question: ${t.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun editPendingQuestion(index: Int) {
        val q = pendingQuestions.getOrNull(index) ?: return

        val dialogView = layoutInflater.inflate(R.layout.dialog_add_question, null)

        val etQuestionText = dialogView.findViewById<android.widget.EditText>(R.id.etQuestionText)
        val etOption1 = dialogView.findViewById<android.widget.EditText>(R.id.etOption1)
        val etOption2 = dialogView.findViewById<android.widget.EditText>(R.id.etOption2)
        val etOption3 = dialogView.findViewById<android.widget.EditText>(R.id.etOption3)
        val etOption4 = dialogView.findViewById<android.widget.EditText>(R.id.etOption4)
        val spinnerType = dialogView.findViewById<android.widget.Spinner>(R.id.spinnerQuestionType)
        val spinnerCorrect = dialogView.findViewById<android.widget.Spinner>(R.id.spinnerCorrect)
        val llOptions = dialogView.findViewById<android.widget.LinearLayout>(R.id.llOptionsContainer)
        val llDescriptive = dialogView.findViewById<android.widget.LinearLayout>(R.id.llDescriptiveContainer)
        val llCorrectCheckbox = dialogView.findViewById<android.widget.LinearLayout>(R.id.llCorrectCheckbox)
        val tvCorrectRadio = dialogView.findViewById<android.widget.TextView>(R.id.tvCorrectRadio)
        val cbCorrect1 = dialogView.findViewById<android.widget.CheckBox>(R.id.cbCorrect1)
        val cbCorrect2 = dialogView.findViewById<android.widget.CheckBox>(R.id.cbCorrect2)
        val cbCorrect3 = dialogView.findViewById<android.widget.CheckBox>(R.id.cbCorrect3)
        val cbCorrect4 = dialogView.findViewById<android.widget.CheckBox>(R.id.cbCorrect4)
        val etCorrectAnswerText = dialogView.findViewById<android.widget.EditText>(R.id.etCorrectAnswerText)
        val etPoints = dialogView.findViewById<android.widget.EditText>(R.id.etPoints)

        // Pre-fill with the current values
        etQuestionText.setText(q.text)
        spinnerType.setSelection(
            when (q.questionType) {
                "radio" -> 0
                "checkbox" -> 1
                else -> 2
            }
        )
        q.options.getOrNull(0)?.let { etOption1.setText(it) }
        q.options.getOrNull(1)?.let { etOption2.setText(it) }
        q.options.getOrNull(2)?.let { etOption3.setText(it) }
        q.options.getOrNull(3)?.let { etOption4.setText(it) }
        when (q.questionType) {
            "radio" -> spinnerCorrect.setSelection(q.correctAnswerIndex)
            "checkbox" -> q.correctAnswerIndices.forEach {
                when (it) {
                    0 -> cbCorrect1.isChecked = true
                    1 -> cbCorrect2.isChecked = true
                    2 -> cbCorrect3.isChecked = true
                    3 -> cbCorrect4.isChecked = true
                }
            }
            else -> etCorrectAnswerText.setText(q.correctAnswerText)
        }
        etPoints.setText(q.points.toString())

        // Type-driven visibility
        fun applyVisibility(pos: Int) {
            when (pos) {
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
        applyVisibility(spinnerType.selectedItemPosition)
        spinnerType.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    applyVisibility(position)
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }

        AlertDialog.Builder(this)
            .setTitle("Edit Imported Question")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                try {
                    val text = etQuestionText.text.toString().trim()
                    val type = when (spinnerType.selectedItemPosition) {
                        0 -> "radio"
                        1 -> "checkbox"
                        else -> "descriptive"
                    }
                    val points = etPoints.text.toString().toIntOrNull() ?: 0
                    if (text.isBlank() || points <= 0) {
                        Toast.makeText(
                            this,
                            "Fill question text and points",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@setPositiveButton
                    }

                    val updated = when (type) {
                        "radio" -> {
                            val opts = listOf(
                                etOption1.text.toString().trim(),
                                etOption2.text.toString().trim(),
                                etOption3.text.toString().trim(),
                                etOption4.text.toString().trim()
                            ).filter { it.isNotEmpty() }
                            if (opts.size < 2) {
                                Toast.makeText(
                                    this, "Radio needs ≥2 options", Toast.LENGTH_SHORT
                                ).show()
                                return@setPositiveButton
                            }
                            q.copy(
                                text = text,
                                options = opts,
                                questionType = "radio",
                                correctAnswerIndex = spinnerCorrect.selectedItemPosition,
                                points = points
                            )
                        }
                        "checkbox" -> {
                            val opts = listOf(
                                etOption1.text.toString().trim(),
                                etOption2.text.toString().trim(),
                                etOption3.text.toString().trim(),
                                etOption4.text.toString().trim()
                            ).filter { it.isNotEmpty() }
                            val correct = mutableListOf<Int>()
                            if (cbCorrect1.isChecked) correct.add(0)
                            if (cbCorrect2.isChecked) correct.add(1)
                            if (cbCorrect3.isChecked) correct.add(2)
                            if (cbCorrect4.isChecked) correct.add(3)
                            if (opts.size < 2 || correct.isEmpty()) {
                                Toast.makeText(
                                    this,
                                    "Checkbox needs options + ≥1 correct",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@setPositiveButton
                            }
                            q.copy(
                                text = text,
                                options = opts,
                                questionType = "checkbox",
                                correctAnswerIndices = correct,
                                points = points
                            )
                        }
                        else -> {
                            val ans = etCorrectAnswerText.text.toString().trim()
                            if (ans.isBlank()) {
                                Toast.makeText(
                                    this, "Descriptive needs an answer", Toast.LENGTH_SHORT
                                ).show()
                                return@setPositiveButton
                            }
                            q.copy(
                                text = text,
                                options = emptyList(),
                                questionType = "descriptive",
                                correctAnswerText = ans,
                                points = points
                            )
                        }
                    }

                    pendingQuestions[index] = updated
                    renderPreview()
                    Toast.makeText(this, "Question updated", Toast.LENGTH_SHORT).show()
                } catch (t: Throwable) {
                    Log.e(TAG, "Saving edited question crashed", t)
                    Toast.makeText(
                        this,
                        "Failed to save: ${t.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ==================================================================
    // DELETE PENDING QUESTION
    // ==================================================================
    private fun deletePendingQuestionSafely(index: Int) {
        try {
            if (index !in pendingQuestions.indices) return
            MaterialAlertDialogBuilder(this)
                .setTitle("Delete Question")
                .setMessage("Remove this question from the pending import?")
                .setPositiveButton("Delete") { _, _ ->
                    try {
                        pendingQuestions.removeAt(index)
                        rebuildDuplicateFlags()
                        renderPreview()
                        binding.llDuplicateActions.visibility =
                            if (duplicateIndices.isNotEmpty()) View.VISIBLE else View.GONE
                        binding.btnImport.isEnabled = pendingQuestions.isNotEmpty()
                    } catch (t: Throwable) {
                        Log.e(TAG, "Delete post-confirm crashed", t)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (t: Throwable) {
            Log.e(TAG, "deletePendingQuestion crashed", t)
        }
    }

    /** Recomputes duplicate flags after the pending list changes. */
    private fun rebuildDuplicateFlags() {
        duplicateIndices.clear()
        val seen = mutableSetOf<String>()
        pendingQuestions.forEachIndexed { i, q ->
            val key = q.text.trim().lowercase()
            if (!seen.add(key)) duplicateIndices.add(i)
        }
    }

    // ==================================================================
    // DUPLICATE HANDLING
    // ==================================================================
    private fun removeDuplicatesSafely() {
        try {
            if (duplicateIndices.isEmpty()) return
            val toRemove = duplicateIndices.sortedDescending()
            toRemove.forEach { pendingQuestions.removeAt(it) }
            duplicateIndices.clear()
            renderPreview()
            binding.llDuplicateActions.visibility = View.GONE
            Toast.makeText(this, "Duplicates removed", Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            Log.e(TAG, "removeDuplicates crashed", t)
        }
    }

    private fun keepDuplicatesSafely() {
        try {
            duplicateIndices.clear()
            renderPreview()
            binding.llDuplicateActions.visibility = View.GONE
            Toast.makeText(this, "Duplicates kept", Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            Log.e(TAG, "keepDuplicates crashed", t)
        }
    }

    // ==================================================================
    // CONFIRM IMPORT
    // ==================================================================
    private fun confirmImportSafely() {
        try {
            if (pendingQuestions.isEmpty()) {
                Toast.makeText(this, "No valid questions to import", Toast.LENGTH_SHORT).show()
                return
            }

            MaterialAlertDialogBuilder(this)
                .setTitle("Import Questions")
                .setMessage("Import ${pendingQuestions.size} questions into a new quiz?")
                .setPositiveButton("Import") { _, _ ->
                    try {
                        val questions = ArrayList(pendingQuestions)
                        val intent = Intent(this, QuizCreationActivity::class.java)
                        intent.putExtra("importedQuestions", questions)
                        startActivity(intent)
                        finish()
                    } catch (t: Throwable) {
                        Log.e(TAG, "Failed to open QuizCreationActivity", t)
                        Toast.makeText(
                            this,
                            "Could not open quiz editor: ${t.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (t: Throwable) {
            Log.e(TAG, "confirmImport crashed", t)
        }
    }

    // ==================================================================
    // TEMPLATES & HELP
    // ==================================================================
    private fun downloadCsvTemplateSafely() {
        try {
            val header =
                "question,type,optionA,optionB,optionC,optionD,answer,marks,negativeMarks\n"
            val rows = listOf(
                "\"What is TCP?\",\"RADIO\",\"Protocol\",\"Language\",\"Database\",\"OS\",\"A\",2,0.5",
                "\"Which are transport protocols?\",\"CHECKBOX\",\"TCP\",\"UDP\",\"HTTP\",\"FTP\",\"A|B\",2,0.5",
                "\"Explain TCP.\",\"DESCRIPTIVE\",\"\",\"\",\"\",\"\",\"Transmission Control Protocol\",5,0"
            )
            val csv = header + rows.joinToString("\n") + "\n"

            val file = File(getExternalFilesDir(null), "questions_template.csv")
            FileOutputStream(file).use { it.write(csv.toByteArray()) }
            Toast.makeText(
                this,
                "Template saved: ${file.absolutePath}",
                Toast.LENGTH_LONG
            ).show()
        } catch (t: Throwable) {
            Log.e(TAG, "downloadCsvTemplate crashed", t)
            Toast.makeText(
                this,
                "Failed to save template: ${t.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showJsonFormatSafely() {
        try {
            val example = """
            {
              "questions": [
                {
                  "question": "What is TCP?",
                  "type": "RADIO",
                  "options": ["Protocol", "Language", "Database", "OS"],
                  "correctAnswer": ["Protocol"],
                  "marks": 2
                },
                {
                  "question": "Which are transport protocols?",
                  "type": "CHECKBOX",
                  "options": ["TCP", "UDP", "HTTP", "FTP"],
                  "correctAnswer": ["TCP", "UDP"],
                  "marks": 2
                },
                {
                  "question": "Explain TCP.",
                  "type": "DESCRIPTIVE",
                  "correctAnswer": ["Transmission Control Protocol"],
                  "marks": 5
                }
              ]
            }
            """.trimIndent()

            AlertDialog.Builder(this)
                .setTitle("JSON Format")
                .setMessage(example)
                .setPositiveButton("OK", null)
                .show()
        } catch (t: Throwable) {
            Log.e(TAG, "showJsonFormat crashed", t)
        }
    }

    // ==================================================================
    // NAVIGATION
    // ==================================================================
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}