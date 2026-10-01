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
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        if (currentUser == null) {
            Toast.makeText(this, "Authentication required", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val quizId = intent.getStringExtra("quizId")?.takeIf { it.isNotBlank() }
        supportActionBar?.title = if (quizId != null) "Cheat Logs - Quiz" else "Cheating Logs"

        adapter = CheatLogAdapter(logs)
        binding.rvCheatLogs.layoutManager = LinearLayoutManager(this)
        binding.rvCheatLogs.adapter = adapter

        loadLogs(quizId)
    }

    private fun loadLogs(quizId: String?) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return

        db.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { doc ->
                val role = doc.getString("role")?.lowercase() ?: "user"
                val isAdminRole = (role == "admin" || role == "super_admin")

                if (isAdminRole) {
                    fetchLogs(quizId, isAdmin = true, uid = currentUser.uid)
                } else if (quizId != null) {
                    db.collection("quizzes").document(quizId).get()
                        .addOnSuccessListener { quizDoc ->
                            if (!quizDoc.exists()) {
                                Toast.makeText(this, "Error: Quiz not found", Toast.LENGTH_SHORT).show()
                                finish()
                                return@addOnSuccessListener
                            }
                            val creatorId = quizDoc.getString("creatorId")
                            if (creatorId == currentUser.uid) {
                                // FIX: non-admin creator → apply creatorId filter
                                fetchLogs(quizId, isAdmin = false, uid = currentUser.uid)
                            } else {
                                Toast.makeText(
                                    this,
                                    "Unauthorized: You did not create this quiz",
                                    Toast.LENGTH_LONG
                                ).show()
                                finish()
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG_LOG, "Failed to verify quiz ownership", e)
                            Toast.makeText(this, "Error: Could not verify ownership", Toast.LENGTH_LONG).show()
                            finish()
                        }
                } else {
                    fetchLogs(null, isAdmin = false, uid = currentUser.uid)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG_LOG, "Failed to verify user role", e)
                Toast.makeText(this, "Error: Could not verify permissions", Toast.LENGTH_LONG).show()
                finish()
            }
    }

    private fun fetchLogs(quizId: String?, isAdmin: Boolean, uid: String) {
        var query = if (quizId == null) {
            Log.d(TAG_LOG, "Executing collectionGroup query for 'cheat_logs'")
            db.collectionGroup("cheat_logs")
        } else {
            Log.d(TAG_LOG, "Executing collection query for 'cheat_logs' in quiz: $quizId")
            db.collection("quizzes").document(quizId).collection("cheat_logs")
        }

        // Apply creatorId filter for non-admins so Firestore can prove the rule
        if (!isAdmin) {
            Log.d(TAG_LOG, "Applying creatorId filter for UID: $uid")
            query = query.whereEqualTo("creatorId", uid)
        }

        // ============================================================
        // IMPORTANT: no orderBy here — combining whereEqualTo with
        // orderBy on a different field requires a Firestore composite
        // index. Sorting happens client-side instead.
        // ============================================================
        query.get()
            .addOnSuccessListener { docs ->
                Log.d(TAG_LOG, "Successfully fetched ${docs.size()} logs")
                val fetchedLogs = docs.toObjects(CheatLog::class.java)

                // Newest first
                val sorted = fetchedLogs.sortedByDescending { it.timestamp }

                updateList(sorted)

                if (sorted.isEmpty()) {
                    val msg = if (quizId == null)
                        "No logs found for your quizzes"
                    else
                        "No logs for this quiz"
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG_LOG, "Firestore Query Failed", e)
                val message = when {
                    e is FirebaseFirestoreException
                            && e.code == FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
                        "Missing Firestore index. Please check Logcat for the link."
                    e is FirebaseFirestoreException
                            && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                        "Permission Denied: You may not have access to these logs."
                    else -> "Error fetching logs: ${e.message}"
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
    }

    private fun updateList(newList: List<CheatLog>) {
        logs.clear()
        logs.addAll(newList)
        adapter.notifyDataSetChanged()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}