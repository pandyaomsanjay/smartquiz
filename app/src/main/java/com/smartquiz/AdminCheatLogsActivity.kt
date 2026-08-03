package com.smartquiz

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
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

        // Use takeIf to handle empty strings as null
        val quizId = intent.getStringExtra("quizId")?.takeIf { it.isNotBlank() }
        supportActionBar?.title = if (quizId != null) "Cheat Logs - Quiz" else "Cheating Logs"

        adapter = CheatLogAdapter(logs)
        binding.rvCheatLogs.layoutManager = LinearLayoutManager(this)
        binding.rvCheatLogs.adapter = adapter

        loadLogs(quizId)
    }

    private fun loadLogs(quizId: String?) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return

        Log.d(TAG_LOG, "Checking permissions for UID: ${currentUser.uid}, quizId: $quizId")

        // 1. Verify user role (Admin or Creator)
        db.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { doc ->
                val role = doc.getString("role")?.lowercase() ?: "user"
                // Check for 'admin', 'super_admin', or 'creator'
                val isAdmin = (role == "admin" || role == "super_admin")
                val isCreator = (role == "creator")
                Log.d(TAG_LOG, "User role: $role, isAdmin: $isAdmin, isCreator: $isCreator")

                if (isAdmin) {
                    // Admins are authorized for any quiz or global logs
                    fetchLogs(quizId, true, currentUser.uid)
                } else if (quizId != null) {
                    // Regular user/creator trying to see specific quiz logs — Check if they created it
                    db.collection("quizzes").document(quizId).get()
                        .addOnSuccessListener { quizDoc ->
                            if (!quizDoc.exists()) {
                                Log.e(TAG_LOG, "Quiz $quizId does not exist")
                                Toast.makeText(this, "Error: Quiz not found", Toast.LENGTH_SHORT).show()
                                finish()
                                return@addOnSuccessListener
                            }
                            val creatorId = quizDoc.getString("creatorId")
                            if (creatorId == currentUser.uid) {
                                // User is the creator, proceed to fetch logs.
                                // We treat them as 'admin' for THIS SPECIFIC quiz to bypass the creatorId filter
                                // which can cause PERMISSION_DENIED if not indexed or missing in some docs.
                                Log.d(TAG_LOG, "Authorized as creator for quiz $quizId")
                                fetchLogs(quizId, true, currentUser.uid)
                            } else {
                                Log.w(TAG_LOG, "Unauthorized: User ${currentUser.uid} is not the creator of quiz $quizId")
                                Toast.makeText(this, "Unauthorized: You did not create this quiz", Toast.LENGTH_LONG).show()
                                finish()
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG_LOG, "Failed to verify quiz ownership", e)
                            Toast.makeText(this, "Error: Could not verify ownership", Toast.LENGTH_LONG).show()
                            finish()
                        }
                } else if (isCreator) {
                    // Creator trying to see ALL their logs globally
                    Log.d(TAG_LOG, "Creator fetching their own global logs")
                    fetchLogs(null, false, currentUser.uid)
                } else {
                    Log.w(TAG_LOG, "Unauthorized: Regular user trying to access global logs")
                    Toast.makeText(this, "Unauthorized: Admin or Creator access required", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG_LOG, "Failed to verify user role", e)
                Toast.makeText(this, "Error: Could not verify permissions", Toast.LENGTH_LONG).show()
                finish()
            }
    }

    private fun fetchLogs(quizId: String?, isAdmin: Boolean, uid: String) {
        // Construct the base query
        var query = if (quizId == null) {
            Log.d(TAG_LOG, "Executing collectionGroup query for 'cheat_logs'")
            db.collectionGroup("cheat_logs")
        } else {
            Log.d(TAG_LOG, "Executing collection query for 'cheat_logs' in quiz: $quizId")
            db.collection("quizzes").document(quizId).collection("cheat_logs")
        }

        // Apply creatorId filter for non-admins. 
        // This is REQUIRED if rules are structured as 'resource.data.creatorId == request.auth.uid'
        if (!isAdmin) {
            Log.d(TAG_LOG, "Applying creatorId filter for UID: $uid")
            query = query.whereEqualTo("creatorId", uid)
        }

        // Apply ordering
        query.orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { docs ->
                Log.d(TAG_LOG, "Successfully fetched ${docs.size()} logs")
                val fetchedLogs = docs.toObjects(CheatLog::class.java)
                updateList(fetchedLogs)
                
                if (fetchedLogs.isEmpty()) {
                    val msg = if (quizId == null) "No logs found for your quizzes" else "No logs for this quiz"
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG_LOG, "Firestore Query Failed (quizId=$quizId, isAdmin=$isAdmin, uid=$uid)", e)
                
                val message = when {
                    e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
                        "Missing Firestore index. Please check Logcat for the link to create it."
                    e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                        "Permission Denied: You may not have access to these logs. If you created this quiz, ensure logs include your creatorId."
                    else -> "Error fetching logs: ${e.message}"
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                
                // If specific quiz query failed with permission denied, try to explain why
                if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED && quizId != null) {
                     Log.e(TAG_LOG, "Hint: Check if the 'cheat_logs' documents in quiz '$quizId' have the 'creatorId' field set to '$uid'")
                }
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