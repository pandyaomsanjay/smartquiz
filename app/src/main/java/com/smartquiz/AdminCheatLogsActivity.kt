package com.smartquiz

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.smartquiz.databinding.ActivityAdminCheatLogsBinding

class AdminCheatLogsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminCheatLogsBinding
    private lateinit var db: FirebaseFirestore
    private val logs = mutableListOf<CheatLog>()
    private lateinit var adapter: CheatLogAdapter
    private val TAG_LOG = "AdminCheatLogs"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminCheatLogsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_back)

        db = FirebaseFirestore.getInstance()
        val currentUser = FirebaseAuth.getInstance().currentUser

        if (currentUser == null) {
            Toast.makeText(this, "Authentication required", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val quizId = intent.getStringExtra("quizId")?.takeIf { it.isNotBlank() }
        supportActionBar?.title =
            if (quizId != null) "Cheat Logs - Quiz" else "Cheating Logs"

        adapter = CheatLogAdapter(logs)
        binding.rvCheatLogs.layoutManager = LinearLayoutManager(this)
        binding.rvCheatLogs.adapter = adapter

        loadLogs(quizId)
    }

    private fun loadLogs(quizId: String?) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val uid = currentUser.uid

        if (quizId != null) {
            // ---------- Per-quiz logs ----------
            // Verify the caller created the quiz (or has custom claim admin).
            db.collection("quizzes").document(quizId).get()
                .addOnSuccessListener { quizDoc ->
                    if (!quizDoc.exists()) {
                        Toast.makeText(this, "Quiz not found", Toast.LENGTH_SHORT).show()
                        finish()
                        return@addOnSuccessListener
                    }
                    val creatorId = quizDoc.getString("creatorId") ?: ""
                    if (creatorId != uid) {
                        Toast.makeText(
                            this,
                            "You can only view cheat logs for quizzes you created",
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                        return@addOnSuccessListener
                    }
                    fetchLogs(quizId, creatorIdFilter = uid, uid = uid)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG_LOG, "quiz lookup failed", e)
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
        } else {
            // ---------- Global view ----------
            // Only the current user's own quizzes' logs (rule-compatible).
            fetchLogs(quizId = null, creatorIdFilter = uid, uid = uid)
        }
    }

    private fun fetchLogs(quizId: String?, creatorIdFilter: String, uid: String) {
        var query = if (quizId == null) {
            db.collectionGroup("cheat_logs")
        } else {
            db.collection("quizzes").document(quizId).collection("cheat_logs")
        }

        // Rule-compatible filter
        query = query.whereEqualTo("creatorId", creatorIdFilter)

        query.get()
            .addOnSuccessListener { docs ->
                val fetched = docs.toObjects(CheatLog::class.java)
                val sorted = fetched.sortedByDescending { it.timestamp }
                logs.clear()
                logs.addAll(sorted)
                adapter.notifyDataSetChanged()

                if (sorted.isEmpty()) {
                    val msg = if (quizId == null)
                        "No logs found" else "No logs for this quiz"
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG_LOG, "query failed (quizId=$quizId)", e)
                val message = when {
                    e is FirebaseFirestoreException
                            && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                        "You can only view cheat logs for your own quizzes"
                    e is FirebaseFirestoreException
                            && e.code == FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
                        "Missing index. Check Logcat."
                    else -> "Error: ${e.message}"
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}