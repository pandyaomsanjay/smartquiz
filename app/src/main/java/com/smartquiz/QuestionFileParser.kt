package com.smartquiz

import android.content.Context
import android.net.Uri
import android.util.Log
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Parses CSV / JSON / XLSX files into the existing [Question] model.
 * All exceptions are caught and returned as [RowError] entries so the
 * caller never crashes on malformed input.
 *
 * CSV  → built-in java.io
 * JSON → built-in org.json
 * XLSX → Apache POI for Android (poi-ooxml via JitPack)
 */
object QuestionFileParser {

    private const val TAG = "QuestionFileParser"

    enum class Format { CSV, JSON, XLSX }

    data class ParseResult(
        val valid: List<ParsedQuestion>,
        val invalid: List<RowError>
    )

    data class ParsedQuestion(
        val question: Question,
        val rawRowNumber: Int
    )

    data class RowError(
        val rowNumber: Int,
        val error: String,
        val raw: String
    )

    // ---------------------------------------------------------------
    // Format detection
    // ---------------------------------------------------------------
    fun detectFormat(context: Context, uri: Uri): Format? {
        return try {
            val mime = try { context.contentResolver.getType(uri) ?: "" } catch (e: Exception) { "" }
            val name = queryFileName(context, uri).lowercase()
            Log.d(TAG, "detectFormat: mime='$mime' name='$name'")
            when {
                mime.contains("json", true) || name.endsWith(".json") -> Format.JSON
                mime.contains("csv", true) || mime.contains("comma", true) ||
                        name.endsWith(".csv") -> Format.CSV
                mime.contains("sheet", true) || mime.contains("excel", true) ||
                        name.endsWith(".xlsx") -> Format.XLSX
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "detectFormat failed", e)
            null
        }
    }

    fun queryFileName(context: Context, uri: Uri): String {
        return try {
            var name = ""
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx != -1 && c.moveToFirst()) name = c.getString(idx) ?: ""
            }
            name
        } catch (e: Exception) {
            Log.e(TAG, "queryFileName failed", e)
            ""
        }
    }

    // ---------------------------------------------------------------
    // Public entry point
    // ---------------------------------------------------------------
    fun parse(context: Context, uri: Uri, format: Format): ParseResult {
        return try {
            when (format) {
                Format.CSV -> parseCsv(context, uri)
                Format.JSON -> parseJson(context, uri)
                Format.XLSX -> parseXlsx(context, uri)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "parse() top-level failure", t)
            ParseResult(
                emptyList(),
                listOf(RowError(0, "Could not read file: ${t.message ?: t.javaClass.simpleName}", ""))
            )
        }
    }

    // ===============================================================
    // CSV
    // ===============================================================
    private fun parseCsv(context: Context, uri: Uri): ParseResult {
        val valid = mutableListOf<ParsedQuestion>()
        val invalid = mutableListOf<RowError>()

        val stream = try { context.contentResolver.openInputStream(uri) }
        catch (e: Exception) { Log.e(TAG, "openInputStream failed", e); null }
        if (stream == null) return ParseResult(emptyList(), listOf(RowError(0, "Cannot open file", "")))

        stream.use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                var lineNumber = 0
                val headerLine = reader.readLine()
                    ?: return ParseResult(emptyList(), listOf(RowError(0, "File is empty", "")))
                lineNumber++
                val headers = splitCsvLine(headerLine).map { it.trim().lowercase() }

                var raw: String? = reader.readLine()
                while (raw != null) {
                    lineNumber++
                    if (raw.isNotBlank()) {
                        try {
                            val cells = splitCsvLine(raw)
                            val row = headers.zip(cells).toMap()
                            valid.add(ParsedQuestion(rowToQuestion(row), lineNumber))
                        } catch (e: Exception) {
                            invalid.add(RowError(lineNumber, e.message ?: "Invalid row", raw))
                        }
                    }
                    raw = reader.readLine()
                }
            }
        }
        return ParseResult(valid, invalid)
    }

    private fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    sb.append('"'); i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> { result.add(sb.toString()); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        result.add(sb.toString())
        return result
    }

    // ===============================================================
    // Row → Question  (shared by CSV + XLSX)
    // ===============================================================
    private fun rowToQuestion(row: Map<String, String>): Question {
        val text = row["question"]?.trim().orEmpty()
        if (text.isBlank()) throw IllegalArgumentException("Missing question text")

        val type = row["type"]?.trim()?.lowercase().orEmpty()
        if (type.isBlank()) throw IllegalArgumentException("Missing question type")

        val marksStr = row["marks"]?.trim().orEmpty()
        val marks = if (marksStr.isBlank()) 1
        else (marksStr.toIntOrNull() ?: throw IllegalArgumentException("Invalid marks '$marksStr'"))
        if (marks <= 0) throw IllegalArgumentException("Marks must be > 0")

        val answer = row["answer"]?.trim().orEmpty()

        return when (type) {
            "radio" -> {
                val options = collectOptions(row)
                if (options.size < 2)
                    throw IllegalArgumentException("Radio needs at least 2 options")
                val idx = "ABCD".indexOf(answer.uppercase().take(1))
                if (idx < 0 || idx >= options.size)
                    throw IllegalArgumentException("Radio answer must be A/B/C/D")
                Question(
                    questionId = "imp_${System.currentTimeMillis()}_${text.hashCode()}",
                    text = text, options = options, questionType = "radio",
                    correctAnswerIndex = idx, points = marks
                )
            }
            "checkbox" -> {
                val options = collectOptions(row)
                if (options.size < 2)
                    throw IllegalArgumentException("Checkbox needs at least 2 options")
                val indices = answer.split("|", ",").mapNotNull { tok ->
                    "ABCD".indexOf(tok.trim().uppercase().take(1)).takeIf { it >= 0 }
                }.distinct()
                if (indices.isEmpty())
                    throw IllegalArgumentException("Checkbox needs at least one correct option")
                Question(
                    questionId = "imp_${System.currentTimeMillis()}_${text.hashCode()}",
                    text = text, options = options, questionType = "checkbox",
                    correctAnswerIndices = indices, points = marks
                )
            }
            "descriptive" -> {
                if (answer.isBlank())
                    throw IllegalArgumentException("Descriptive needs the correct answer text")
                Question(
                    questionId = "imp_${System.currentTimeMillis()}_${text.hashCode()}",
                    text = text, options = emptyList(), questionType = "descriptive",
                    correctAnswerText = answer, points = marks
                )
            }
            "scenario" -> throw IllegalArgumentException(
                "Scenario cannot be imported via bulk upload — use the manual editor"
            )
            else -> throw IllegalArgumentException("Invalid question type '$type'")
        }
    }

    private fun collectOptions(row: Map<String, String>): List<String> {
        return listOf("optiona", "optionb", "optionc", "optiond", "optione", "optionf")
            .mapNotNull { row[it]?.trim()?.takeIf { s -> s.isNotEmpty() } }
    }

    // ===============================================================
    // JSON
    // ===============================================================
    private fun parseJson(context: Context, uri: Uri): ParseResult {
        val valid = mutableListOf<ParsedQuestion>()
        val invalid = mutableListOf<RowError>()

        val stream = try { context.contentResolver.openInputStream(uri) }
        catch (e: Exception) { Log.e(TAG, "openInputStream failed", e); null }
        if (stream == null) return ParseResult(emptyList(), listOf(RowError(0, "Cannot open file", "")))

        val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        if (text.isBlank()) return ParseResult(emptyList(), listOf(RowError(0, "File is empty", "")))

        val root = try { JSONObject(text) }
        catch (e: Exception) {
            return ParseResult(emptyList(), listOf(RowError(0, "Invalid JSON: ${e.message}", "")))
        }

        val arr: JSONArray = root.optJSONArray("questions")
            ?: return ParseResult(emptyList(), listOf(RowError(0, "JSON must contain a 'questions' array", "")))

        for (i in 0 until arr.length()) {
            val raw = arr.opt(i)?.toString() ?: ""
            try {
                valid.add(ParsedQuestion(jsonToQuestion(arr.getJSONObject(i)), i + 1))
            } catch (e: Exception) {
                invalid.add(RowError(i + 1, e.message ?: "Invalid JSON entry", raw))
            }
        }
        return ParseResult(valid, invalid)
    }

    private fun jsonToQuestion(obj: JSONObject): Question {
        val text = obj.optString("question").trim()
        if (text.isBlank()) throw IllegalArgumentException("Missing question text")

        val type = obj.optString("type").lowercase().trim()
        if (type.isBlank()) throw IllegalArgumentException("Missing question type")

        val marks = obj.optInt("marks", 1)
        if (marks <= 0) throw IllegalArgumentException("Marks must be > 0")

        val answers = obj.optJSONArray("correctAnswer")

        return when (type) {
            "radio" -> {
                val options = obj.optJSONArray("options")?.toStringList() ?: emptyList()
                if (options.size < 2) throw IllegalArgumentException("Radio needs at least 2 options")
                val correctText = answers?.optString(0).orEmpty()
                if (correctText.isBlank()) throw IllegalArgumentException("Missing correct answer")
                val idx = options.indexOfFirst { it.equals(correctText, true) }
                if (idx < 0) throw IllegalArgumentException("Correct answer not in options list")
                Question(
                    questionId = "imp_${System.currentTimeMillis()}_${text.hashCode()}",
                    text = text, options = options, questionType = "radio",
                    correctAnswerIndex = idx, points = marks
                )
            }
            "checkbox" -> {
                val options = obj.optJSONArray("options")?.toStringList() ?: emptyList()
                if (options.size < 2) throw IllegalArgumentException("Checkbox needs at least 2 options")
                val correct = answers?.toStringList().orEmpty()
                if (correct.isEmpty()) throw IllegalArgumentException("Missing correct answers")
                val indices = correct.mapNotNull { c ->
                    options.indexOfFirst { it.equals(c, true) }.takeIf { it >= 0 }
                }
                if (indices.isEmpty()) throw IllegalArgumentException("Correct answers not found in options")
                Question(
                    questionId = "imp_${System.currentTimeMillis()}_${text.hashCode()}",
                    text = text, options = options, questionType = "checkbox",
                    correctAnswerIndices = indices, points = marks
                )
            }
            "descriptive" -> {
                val correctText = answers?.optString(0).orEmpty()
                if (correctText.isBlank()) throw IllegalArgumentException("Missing correct answer")
                Question(
                    questionId = "imp_${System.currentTimeMillis()}_${text.hashCode()}",
                    text = text, options = emptyList(), questionType = "descriptive",
                    correctAnswerText = correctText, points = marks
                )
            }
            "scenario" -> throw IllegalArgumentException(
                "Scenario cannot be imported via bulk upload — use the manual editor"
            )
            else -> throw IllegalArgumentException("Invalid question type '$type'")
        }
    }

    private fun JSONArray.toStringList(): List<String> {
        val out = mutableListOf<String>()
        for (i in 0 until length()) out.add(optString(i))
        return out
    }

    // ===============================================================
    // XLSX — Apache POI for Android
    // ===============================================================
    private fun parseXlsx(context: Context, uri: Uri): ParseResult {
        val valid = mutableListOf<ParsedQuestion>()
        val invalid = mutableListOf<RowError>()

        val stream = try { context.contentResolver.openInputStream(uri) }
        catch (e: Exception) { Log.e(TAG, "openInputStream failed", e); null }
        if (stream == null) return ParseResult(emptyList(), listOf(RowError(0, "Cannot open file", "")))

        try {
            stream.use { input ->
                XSSFWorkbook(input).use { workbook ->
                    val sheet = workbook.getSheetAt(0)
                    if (sheet == null || sheet.physicalNumberOfRows == 0) {
                        return ParseResult(emptyList(), listOf(RowError(0, "Sheet is empty", "")))
                    }

                    // ---------- Header ----------
                    val headerRow = sheet.getRow(sheet.firstRowNum)
                        ?: return ParseResult(emptyList(), listOf(RowError(0, "Header row missing", "")))

                    val headers = mutableListOf<String>()
                    for (c in 0 until headerRow.lastCellNum.toInt()) {
                        headers.add(cellToString(headerRow.getCell(c)).lowercase())
                    }
                    Log.d(TAG, "XLSX headers: $headers")

                    // ---------- Data rows ----------
                    var rowNum = sheet.firstRowNum
                    for (r in (sheet.firstRowNum + 1)..sheet.lastRowNum) {
                        rowNum = r + 1  // 1-based row number for the user
                        val row = sheet.getRow(r) ?: continue

                        val cells = mutableListOf<String>()
                        for (c in headers.indices) {
                            cells.add(cellToString(row.getCell(c)))
                        }
                        // Skip empty rows
                        if (cells.all { it.isBlank() }) continue

                        val map = headers.zip(cells).toMap()
                        try {
                            valid.add(ParsedQuestion(rowToQuestion(map), rowNum))
                        } catch (e: Exception) {
                            invalid.add(RowError(rowNum, e.message ?: "Invalid row",
                                cells.joinToString(" | ")))
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "XLSX parse failed", t)
            return ParseResult(
                emptyList(),
                listOf(RowError(
                    0,
                    "Could not read Excel file: ${t.message ?: t.javaClass.simpleName}",
                    ""
                ))
            )
        }

        return ParseResult(valid, invalid)
    }

    /**
     * Safely converts a POI [Cell] to string, handling numbers, booleans,
     * formulas, blanks, and dates uniformly.
     */
    private fun cellToString(cell: Cell?): String {
        if (cell == null) return ""
        return try {
            when (cell.cellType) {
                CellType.STRING -> cell.stringCellValue.trim()
                CellType.NUMERIC -> {
                    val d = cell.numericCellValue
                    // Print integers without a trailing ".0"
                    if (d == Math.floor(d) && !d.isInfinite()) d.toLong().toString()
                    else d.toString()
                }
                CellType.BOOLEAN -> cell.booleanCellValue.toString()
                CellType.FORMULA -> try {
                    cell.stringCellValue.trim()
                } catch (e: Exception) {
                    try { cell.numericCellValue.toString() } catch (e2: Exception) { "" }
                }
                CellType.BLANK -> ""
                else -> ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "cellToString failed", e)
            ""
        }
    }

    // ===============================================================
    // Duplicate detection
    // ===============================================================
    fun findDuplicateIndices(questions: List<ParsedQuestion>): Set<Int> {
        val seen = mutableSetOf<String>()
        val dupes = mutableSetOf<Int>()
        questions.forEachIndexed { i, pq ->
            val key = pq.question.text.trim().lowercase()
            if (!seen.add(key)) dupes.add(i)
        }
        return dupes
    }
}