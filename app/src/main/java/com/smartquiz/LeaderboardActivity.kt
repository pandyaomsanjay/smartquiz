package com.smartquiz

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.smartquiz.databinding.ActivityLeaderboardBinding
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class LeaderboardActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLeaderboardBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: LeaderboardAdapter
    private val entries = mutableListOf<LeaderboardEntry>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLeaderboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        adapter = LeaderboardAdapter(entries)
        binding.rvLeaderboard.layoutManager = LinearLayoutManager(this)
        binding.rvLeaderboard.adapter = adapter

        loadLeaderboard()
    }
    private var listener: ListenerRegistration? = null

    override fun onStart() {
        super.onStart()
        listener = db.collection("results")
            .orderBy("score", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                entries.clear()
                snapshot?.documents?.forEach { doc ->
                    val entry = doc.toObject(LeaderboardEntry::class.java)!!
                    entries.add(entry)
                }
                adapter.notifyDataSetChanged()
            }
    }

    override fun onStop() {
        super.onStop()
        listener?.remove()
    }

    private fun loadLeaderboard() {
        // Aggregate scores per user from results collection
        db.collection("results").get()
            .addOnSuccessListener { docs ->
                val scoreMap = mutableMapOf<String, Int>()
                for (doc in docs) {
                    val userId = doc.getString("userId") ?: continue
                    val score = doc.getLong("score")?.toInt() ?: 0
                    scoreMap[userId] = (scoreMap[userId] ?: 0) + score
                }
                if (scoreMap.isEmpty()) return@addOnSuccessListener
                // Fetch user names
                val userIds = scoreMap.keys.toList()
                db.collection("users").whereIn("uid", userIds).get()
                    .addOnSuccessListener { userDocs ->
                        entries.clear()
                        for (userDoc in userDocs) {
                            val uid = userDoc.id
                            val name = userDoc.getString("name") ?: "Unknown"
                            val total = scoreMap[uid] ?: 0
                            entries.add(LeaderboardEntry(uid, name, total))
                        }
                        entries.sortByDescending { it.totalScore }
                        adapter.notifyDataSetChanged()
                    }
            }
    }
}