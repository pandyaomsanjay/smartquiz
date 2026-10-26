package com.smartquiz

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
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

        val quizId = intent.getStringExtra("quizId")?.takeIf { it.isNotBlank() }
        supportActionBar?.title = if (quizId != null) "Cheat Logs - Quiz" else "All Cheating Logs"

        adapter = CheatLogAdapter(logs)
        binding.rvCheatLogs.layoutManager = LinearLayoutManager(this)
        binding.rvCheatLogs.adapter = adapter

        loadLogs(quizId)
    }

    private fun loadLogs(quizId: String?) {
        var query = if (quizId == null) {
            // Global view for Admin
            db.collectionGroup("cheat_logs")
        } else {
            // Specific quiz view
            db.collection("quizzes").document(quizId).collection("cheat_logs")
        }

        query.get()
            .addOnSuccessListener { docs ->
                val fetched = docs.toObjects(CheatLog::class.java)
                val sorted = fetched.sortedByDescending { it.timestamp }

                logs.clear()
                logs.addAll(sorted)
                adapter.notifyDataSetChanged()

                if (sorted.isEmpty()) {
                    val msg = if (quizId == null) "No logs found in the system" else "No logs for this quiz"
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG_LOG, "query failed", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}