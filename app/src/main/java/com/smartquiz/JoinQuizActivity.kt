package com.smartquiz

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
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
    private val allJoinedQuizzes = mutableListOf<JoinedQuiz>()
    private val filteredQuizzes = mutableListOf<JoinedQuiz>()
    private var currentFilter = "All Quizzes"
    private val TAG = "JoinQuiz"
    private var joinedQuizzesListener: ListenerRegistration? = null

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

        adapter = JoinedQuizAdapter(filteredQuizzes) { joinedQuiz ->
            openQuizDetails(joinedQuiz)
        }
        binding.rvJoinedQuizzes.layoutManager = LinearLayoutManager(this)
        binding.rvJoinedQuizzes.adapter = adapter

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                applyFilter()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.chipGroupFilter.setOnCheckedChangeListener { _, checkedId ->
            currentFilter = when (checkedId) {
                R.id.chipCompleted -> "Completed"
                R.id.chipInProgress -> "In Progress"
                R.id.chipExpired -> "Expired"
                else -> "All Quizzes"
            }
            applyFilter()
        }

        binding.btnRefreshHistory.setOnClickListener {
            refreshData()
            Toast.makeText(this, "Refreshed", Toast.LENGTH_SHORT).show()
        }

        binding.btnClearFilters.setOnClickListener {
            binding.etSearch.setText("")
            binding.chipGroupFilter.clearCheck()
            binding.chipAll.isChecked = true
            currentFilter = "All Quizzes"
            applyFilter()
        }

        binding.btnEmptyJoin.setOnClickListener {
            binding.etQuizCode.requestFocus()
        }

        loadJoinedQuizzes()
    }

    override fun onDestroy() {
        super.onDestroy()
        joinedQuizzesListener?.remove()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
        return true
    }

    // ==================================================================
    // JOIN QUIZ
    // ==================================================================
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

                // ---------- Lifecycle guard ----------
                val lifecycle = quiz.computeStatus(QuizTimeUtils.getServerTimeMs())
                when (lifecycle) {
                    QuizLifecycleStatus.UPCOMING -> {
                        Toast.makeText(
                            this,
                            "This quiz starts at ${QuizTimeUtils.formatDateTime(quiz.startTime)}",
                            Toast.LENGTH_LONG
                        ).show()
                        return@addOnSuccessListener
                    }
                    QuizLifecycleStatus.EXPIRED, QuizLifecycleStatus.DELETED -> {
                        Toast.makeText(
                            this,
                            "This quiz has expired and can no longer be joined.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@addOnSuccessListener
                    }
                    QuizLifecycleStatus.COMPLETED -> {
                        Toast.makeText(this, "This quiz has been completed.", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }
                    QuizLifecycleStatus.LIVE -> { /* proceed */ }
                }

                if (quiz.deadline > 0 && System.currentTimeMillis() > quiz.deadline) {
                    Toast.makeText(this, "This quiz has expired", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // ---------- Check for existing attempt ----------
                db.collection("quizzes").document(quiz.quizId!!)
                    .collection("attempts").document(userId)
                    .get()
                    .addOnSuccessListener { attemptDoc ->
                        if (attemptDoc.exists() && attemptDoc.getString("status") == "Completed") {
                            Toast.makeText(
                                this,
                                "You have already completed this quiz. Multiple attempts are not allowed.",
                                Toast.LENGTH_LONG
                            ).show()
                            return@addOnSuccessListener
                        }
                        // ---------- Check if already joined ----------
                        db.collection("users").document(userId)
                            .collection("joinedQuizzes").document(quiz.quizId!!)
                            .get()
                            .addOnSuccessListener { joinedDoc ->
                                if (joinedDoc.exists()) {
                                    // Already joined — no need to run the transaction.
                                    navigateToQuizInfo(quiz)
                                    return@addOnSuccessListener
                                }
                                proceedToJoin(quiz, userId)
                            }
                            .addOnFailureListener {
                                proceedToJoin(quiz, userId)
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

    // ==================================================================
    // PROCEED TO JOIN — atomic transaction, increments participantCount
    // ==================================================================
    private fun proceedToJoin(quiz: Quiz, userId: String) {
        val joinTime = System.currentTimeMillis()

        // Fetch creator name (read-only). Fall back to "Unknown" if missing.
        db.collection("users").document(quiz.creatorId).get()
            .addOnSuccessListener { userDoc ->
                val creatorName = userDoc.getString("name") ?: "Unknown Creator"
                commitJoinTransaction(quiz, userId, joinTime, creatorName)
            }
            .addOnFailureListener {
                commitJoinTransaction(quiz, userId, joinTime, "Unknown")
            }
    }

    /**
     * Atomically:
     *   1. Ensures a `joinedQuizzes/{quizId}` doc exists for this user.
     *   2. Increments `quizzes/{quizId}.participantCount` on first join only.
     *
     * Re-joins (same user, same quiz) are no-ops for `participantCount`.
     */
    private fun commitJoinTransaction(
        quiz: Quiz,
        userId: String,
        joinTime: Long,
        creatorName: String
    ) {
        val quizId = quiz.quizId ?: run {
            Toast.makeText(this, "Invalid quiz", Toast.LENGTH_SHORT).show()
            return
        }

        val joinedRef = db.collection("users").document(userId)
            .collection("joinedQuizzes").document(quizId)
        val quizRef = db.collection("quizzes").document(quizId)

        db.runTransaction { transaction ->
            val existing = transaction.get(joinedRef)

            if (existing.exists()) {
                // Already joined — do nothing (no double count).
                return@runTransaction null
            }

            val joinedQuiz = JoinedQuiz(
                quizId = quizId,
                quizTitle = quiz.title,
                quizCode = quiz.quizCode,
                creatorId = quiz.creatorId,
                creatorName = creatorName,
                joinTime = joinTime,
                submitTime = null,
                status = "In Progress",
                score = null,
                category = quiz.category,
                allowMultipleAttempts = quiz.allowMultipleAttempts
            )

            transaction.set(joinedRef, joinedQuiz)

            // Atomic increment. If the field doesn't exist, Firestore creates it at 1.
            transaction.update(
                quizRef,
                "participantCount",
                FieldValue.increment(1)
            )

            null
        }.addOnSuccessListener {
            navigateToQuizInfo(quiz)
        }.addOnFailureListener { e ->
            Log.e(TAG, "Join transaction failed: ${e.message}")
            Toast.makeText(this, "Failed to join: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToQuizInfo(quiz: Quiz) {
        val intent = Intent(this, QuizInstructionsActivity::class.java)
        intent.putExtra("quizId", quiz.quizId)
        intent.putExtra("quizTitle", quiz.title)
        intent.putExtra("creatorId", quiz.creatorId)
        startActivity(intent)
        finish()
    }

    // ==================================================================
    // LOAD JOINED QUIZZES (snapshot listener)
    // ==================================================================
    private fun loadJoinedQuizzes() {
        val userId = auth.currentUser?.uid ?: return

        joinedQuizzesListener = db.collection("users").document(userId)
            .collection("joinedQuizzes")
            .orderBy("joinTime", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Toast.makeText(this, "Error loading history: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                val items = snapshots?.mapNotNull { doc ->
                    doc.toObject(JoinedQuiz::class.java)
                } ?: emptyList()

                Log.d(TAG, "Joined quiz documents received: ${items.size}")

                allJoinedQuizzes.clear()
                allJoinedQuizzes.addAll(items)

                applyFilter()
                updateUI()
            }
    }

    // ==================================================================
    // REFRESH (one-shot fetch)
    // ==================================================================
    private fun refreshData() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("users").document(userId)
            .collection("joinedQuizzes")
            .orderBy("joinTime", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshots ->
                val items = snapshots.mapNotNull { doc ->
                    doc.toObject(JoinedQuiz::class.java)
                }

                allJoinedQuizzes.clear()
                allJoinedQuizzes.addAll(items)
                applyFilter()
                updateUI()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Refresh failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ==================================================================
    // UI HELPERS
    // ==================================================================
    private fun updateUI() {
        if (allJoinedQuizzes.isEmpty()) {
            binding.rvJoinedQuizzes.visibility = android.view.View.GONE
            binding.layoutEmptyState.visibility = android.view.View.VISIBLE
            binding.btnEmptyJoin.text = "Join a Quiz"
        } else {
            binding.rvJoinedQuizzes.visibility = android.view.View.VISIBLE
            binding.layoutEmptyState.visibility = android.view.View.GONE
            applyFilter()
        }
    }

    private fun applyFilter() {
        val query = binding.etSearch.text.toString().trim()
        filteredQuizzes.clear()

        for (joined in allJoinedQuizzes) {
            val matchesSearch = query.isEmpty() ||
                    joined.quizTitle.contains(query, ignoreCase = true) ||
                    joined.quizCode.contains(query, ignoreCase = true)

            val matchesFilter = when (currentFilter) {
                "Completed" -> joined.status == "Completed"
                "In Progress" -> joined.status == "In Progress"
                "Expired" -> joined.status == "Expired"
                else -> true
            }

            if (matchesSearch && matchesFilter) {
                filteredQuizzes.add(joined)
            }
        }

        adapter.updateList(filteredQuizzes)
        updateEmptyState()
    }

    private fun updateEmptyState() {
        if (filteredQuizzes.isEmpty()) {
            binding.rvJoinedQuizzes.visibility = android.view.View.GONE
            binding.layoutEmptyState.visibility = android.view.View.VISIBLE
            binding.btnEmptyJoin.text =
                if (allJoinedQuizzes.isNotEmpty()) "No matching quizzes" else "Join a Quiz"
        } else {
            binding.rvJoinedQuizzes.visibility = android.view.View.VISIBLE
            binding.layoutEmptyState.visibility = android.view.View.GONE
        }
    }

    private fun openQuizDetails(joinedQuiz: JoinedQuiz) {
        val intent = Intent(this, QuizDetailsActivity::class.java)
        intent.putExtra("joinedQuiz", joinedQuiz)
        startActivity(intent)
    }
}