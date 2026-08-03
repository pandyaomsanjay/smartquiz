package com.smartquiz

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.smartquiz.databinding.ActivityLeaderboardBinding

class LeaderboardActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLeaderboardBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: LeaderboardAdapter
    private val entries = mutableListOf<LeaderboardEntry>()
    private var listener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLeaderboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_back) // custom back arrow

        db = FirebaseFirestore.getInstance()
        adapter = LeaderboardAdapter(entries)
        binding.rvLeaderboard.layoutManager = LinearLayoutManager(this)
        binding.rvLeaderboard.adapter = adapter
    }

    override fun onStart() {
        super.onStart()
        listener = db.collection("leaderboard")
            .orderBy("totalScore", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                entries.clear()
                snapshot?.documents?.forEach { doc ->
                    val entry = doc.toObject(LeaderboardEntry::class.java)
                    if (entry != null) {
                        entries.add(entry)
                    }
                }
                adapter.notifyDataSetChanged()
            }
    }

    override fun onStop() {
        super.onStop()
        listener?.remove()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}