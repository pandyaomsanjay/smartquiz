package com.smartquiz

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.smartquiz.databinding.ActivityAdminCheatLogsBinding

class AdminCheatLogsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminCheatLogsBinding
    private lateinit var db: FirebaseFirestore
    private val logs = mutableListOf<CheatLog>()
    private lateinit var adapter: CheatLogAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminCheatLogsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        db = FirebaseFirestore.getInstance()
        adapter = CheatLogAdapter(logs)
        binding.rvCheatLogs.layoutManager = LinearLayoutManager(this)
        binding.rvCheatLogs.adapter = adapter

        loadLogs()
    }

    private fun loadLogs() {
        db.collection("cheat_logs")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { docs ->
                logs.clear()
                for (doc in docs) {
                    val log = doc.toObject(CheatLog::class.java)
                    logs.add(log)
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load logs: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}