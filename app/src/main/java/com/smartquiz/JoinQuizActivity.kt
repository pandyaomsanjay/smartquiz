package com.smartquiz

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.smartquiz.adapters.JoinedQuizAdapter
import com.smartquiz.databinding.ActivityJoinQuizBinding
import com.smartquiz.models.JoinedQuiz

class JoinQuizActivity : AppCompatActivity() {

    private lateinit var binding: ActivityJoinQuizBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: JoinedQuizAdapter
    private val joinedQuizzesList = mutableListOf<JoinedQuiz>()
    private var currentFilter = "All Quizzes"
    private val TAG = "JoinQuiz"

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val scannedCode = result.contents.trim()
            if (scannedCode.matches(Regex("\\d{6}"))) {
                binding.etQuizCode.setText(scannedCode)
                joinQuizWithCode(scannedCode)
            } else {
                Toast.makeText(this, "Invalid QR code (must be 6 digits)", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "Scan cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityJoinQuizBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_back)
        supportActionBar?.title = "Join Quiz"

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        binding.btnJoin.setOnClickListener {
            val code = binding.etQuizCode.text.toString().trim()
            if (code.isEmpty()) {
                Toast.makeText(this, "Enter quiz code", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            joinQuizWithCode(code)
        }

        binding.btnScanQR.setOnClickListener {
            val options = ScanOptions()
            options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            options.setPrompt("Scan the quiz QR code")
            options.setCameraId(0)
            options.setBeepEnabled(true)
            scanLauncher.launch(options)
        }

        adapter = JoinedQuizAdapter(joinedQuizzesList) { joinedQuiz ->
            openQuizDetails(joinedQuiz)
        }
        binding.rvJoinedQuizzes.layoutManager = LinearLayoutManager(this)
        binding.rvJoinedQuizzes.adapter = adapter

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterQuizzes(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.chipGroupFilter.setOnCheckedChangeListener { group, checkedId ->
            currentFilter = when (checkedId) {
                R.id.chipCompleted -> "Completed"
                R.id.chipInProgress -> "In Progress"
                R.id.chipExpired -> "Expired"
                else -> "All Quizzes"
            }
            filterQuizzes(binding.etSearch.text.toString())
        }

        binding.btnRefreshHistory.setOnClickListener {
            loadJoinedQuizzes()
            Toast.makeText(this, "Refreshed", Toast.LENGTH_SHORT).show()
        }

        binding.btnClearFilters.setOnClickListener {
            binding.etSearch.setText("")
            binding.chipGroupFilter.clearCheck()
            binding.chipAll.isChecked = true
            currentFilter = "All Quizzes"
            filterQuizzes("")
        }

        binding.btnEmptyJoin.setOnClickListener {
            binding.etQuizCode.requestFocus()
        }

        loadJoinedQuizzes()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        return true
    }

    private fun joinQuizWithCode(code: String) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        Log.d(TAG, "Searching for quiz with code: $code")
        db.collection("quizzes")
            .whereEqualTo("quizCode", code)
            .get()
            .addOnSuccessListener { docs ->
                if (docs.isEmpty()) {
                    Log.e(TAG, "No quiz found with code: $code")
                    Toast.makeText(this, "Quiz not found with code: $code", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }
                val quizDoc = docs.documents[0]
                val quiz = quizDoc.toObject(Quiz::class.java)
                if (quiz == null) {
                    Toast.makeText(this, "Invalid quiz data", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                quiz.quizId = quizDoc.id

                // Check deadline
                if (quiz.deadline > 0 && System.currentTimeMillis() > quiz.deadline) {
                    Toast.makeText(this, "This quiz has expired", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // Check if user already has a completed attempt
                db.collection("quizzes").document(quiz.quizId!!)
                    .collection("attempts").document(userId)
                    .get()
                    .addOnSuccessListener { attemptDoc ->
                        if (attemptDoc.exists() && attemptDoc.getString("status") == "Completed") {
                            Toast.makeText(this, "You have already completed this quiz. Multiple attempts are not allowed.", Toast.LENGTH_LONG).show()
                            return@addOnSuccessListener
                        }
                        // Check if already joined (in-progress)
                        db.collection("users").document(userId)
                            .collection("joinedQuizzes").document(quiz.quizId!!)
                            .get()
                            .addOnSuccessListener { joinedDoc ->
                                if (joinedDoc.exists()) {
                                    Toast.makeText(this, "You have already joined this quiz", Toast.LENGTH_LONG).show()
                                    return@addOnSuccessListener
                                }
                                proceedToJoin(quiz, userId)
                            }
                            .addOnFailureListener {
                                proceedToJoin(quiz, userId) // fallback
                            }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error checking attempt: ${e.message}")
                        Toast.makeText(this, "Error validating quiz attempt", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to find quiz: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun proceedToJoin(quiz: Quiz, userId: String) {
        val joinTime = System.currentTimeMillis()
        db.collection("users").document(quiz.creatorId).get()
            .addOnSuccessListener { userDoc ->
                val creatorName = userDoc.getString("name") ?: "Unknown Creator"
                val joinedQuiz = JoinedQuiz(
                    quizId = quiz.quizId!!,
                    quizTitle = quiz.title,
                    quizCode = quiz.quizCode,
                    creatorName = creatorName,
                    joinTime = joinTime,
                    submitTime = null,
                    status = "In Progress",
                    score = null,
                    category = quiz.category,
                    allowMultipleAttempts = quiz.allowMultipleAttempts
                )
                db.collection("users").document(userId)
                    .collection("joinedQuizzes").document(quiz.quizId!!)
                    .set(joinedQuiz)
                    .addOnSuccessListener {
                        val intent = Intent(this, QuizInstructionsActivity::class.java)
                        intent.putExtra("quizId", quiz.quizId)
                        intent.putExtra("quizTitle", quiz.title)
                        startActivity(intent)
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to join: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                // Fallback creator name
                val joinedQuiz = JoinedQuiz(
                    quizId = quiz.quizId!!,
                    quizTitle = quiz.title,
                    quizCode = quiz.quizCode,
                    creatorName = "Unknown",
                    joinTime = joinTime,
                    submitTime = null,
                    status = "In Progress",
                    score = null,
                    category = quiz.category,
                    allowMultipleAttempts = quiz.allowMultipleAttempts
                )
                db.collection("users").document(userId)
                    .collection("joinedQuizzes").document(quiz.quizId!!)
                    .set(joinedQuiz)
                    .addOnSuccessListener {
                        val intent = Intent(this, QuizInstructionsActivity::class.java)
                        intent.putExtra("quizId", quiz.quizId)
                        intent.putExtra("quizTitle", quiz.title)
                        startActivity(intent)
                        finish()
                    }
            }
    }



    private fun loadJoinedQuizzes() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId)
            .collection("joinedQuizzes")
            .orderBy("joinTime", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Toast.makeText(this, "Error loading history: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                joinedQuizzesList.clear()
                snapshots?.forEach { doc ->
                    val joined = doc.toObject(JoinedQuiz::class.java)
                    val finalStatus = determineFinalStatus(joined)
                    joined.status = finalStatus
                    joinedQuizzesList.add(joined)
                }
                updateUI()
            }
    }

    private fun determineFinalStatus(joined: JoinedQuiz): String {
        if (joined.status == "Completed") return "Completed"
        return if (isQuizExpired(joined.quizId)) "Expired" else "In Progress"
    }

    private fun isQuizExpired(quizId: String): Boolean {
        // You can implement a real check by fetching the quiz deadline
        // For now return false
        return false
    }

    private fun updateUI() {
        if (joinedQuizzesList.isEmpty()) {
            binding.rvJoinedQuizzes.visibility = android.view.View.GONE
            binding.layoutEmptyState.visibility = android.view.View.VISIBLE
        } else {
            binding.rvJoinedQuizzes.visibility = android.view.View.VISIBLE
            binding.layoutEmptyState.visibility = android.view.View.GONE
            filterQuizzes(binding.etSearch.text.toString())
        }
    }

    private fun filterQuizzes(query: String) {
        val filtered = joinedQuizzesList.filter { joined ->
            val matchesSearch = joined.quizTitle.contains(query, true) ||
                    joined.quizCode.contains(query, true)
            val matchesFilter = when (currentFilter) {
                "Completed" -> joined.status == "Completed"
                "In Progress" -> joined.status == "In Progress"
                "Expired" -> joined.status == "Expired"
                else -> true
            }
            matchesSearch && matchesFilter
        }
        adapter.updateList(filtered)
    }

    private fun openQuizDetails(joinedQuiz: JoinedQuiz) {
        val intent = Intent(this, QuizDetailsActivity::class.java)
        intent.putExtra("joinedQuiz", joinedQuiz)
        startActivity(intent)
    }
}