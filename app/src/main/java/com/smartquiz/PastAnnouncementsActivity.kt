package com.smartquiz

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.smartquiz.databinding.ActivityPastAnnouncementsBinding
import java.text.SimpleDateFormat
import java.util.*

class PastAnnouncementsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPastAnnouncementsBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: AnnouncementHistoryAdapter
    private val allAnnouncements = mutableListOf<AnnouncementHistoryItem>()
    private val filteredAnnouncements = mutableListOf<AnnouncementHistoryItem>()
    private var listener: ListenerRegistration? = null
    private val TAG = "PastAnnouncements"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPastAnnouncementsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        db = FirebaseFirestore.getInstance()

        adapter = AnnouncementHistoryAdapter(filteredAnnouncements) { item ->
            showDetailsDialog(item)
        }
        binding.rvAnnouncements.layoutManager = LinearLayoutManager(this)
        binding.rvAnnouncements.adapter = adapter

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                applySearch(s.toString())
            }
        })

        binding.btnCreateAnnouncement.setOnClickListener {
            startActivity(Intent(this, AdminAnnouncementsActivity::class.java))
        }

        loadAnnouncements()
    }

    private fun loadAnnouncements() {
        binding.progressBar.visibility = View.VISIBLE
        binding.layoutEmpty.visibility = View.GONE
        binding.rvAnnouncements.visibility = View.GONE

        // Listen to the existing "announcements" collection
        listener = db.collection("announcements")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                binding.progressBar.visibility = View.GONE

                if (error != null) {
                    Log.e(TAG, "Error loading announcements", error)
                    Toast.makeText(this, "Failed to load: ${error.message}", Toast.LENGTH_SHORT).show()
                    binding.layoutEmpty.visibility = View.VISIBLE
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    allAnnouncements.clear()
                    for (doc in snapshot.documents) {
                        val title = doc.getString("title") ?: "Untitled"
                        val message = doc.getString("message") ?: ""
                        // Handle either Long or Timestamp types safely
                        val timestamp = doc.getLong("timestamp") ?:
                        doc.getTimestamp("timestamp")?.toDate()?.time ?: 0L

                        allAnnouncements.add(
                            AnnouncementHistoryItem(doc.id, title, message, timestamp)
                        )
                    }

                    updateSummaryUI()
                    applySearch(binding.etSearch.text.toString())
                }
            }
    }

    private fun updateSummaryUI() {
        val total = allAnnouncements.size
        binding.tvTotalAnnouncements.text = "$total Announcements"

        if (total > 0) {
            val latestDate = allAnnouncements.maxOfOrNull { it.timestamp } ?: 0L
            if (latestDate > 0) {
                val formatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                binding.tvLatestDate.text = "Latest: ${formatter.format(Date(latestDate))}"
            }
        } else {
            binding.tvLatestDate.text = "Latest: —"
        }
    }

    private fun applySearch(query: String) {
        val lowerQuery = query.trim().lowercase()
        filteredAnnouncements.clear()

        if (lowerQuery.isEmpty()) {
            filteredAnnouncements.addAll(allAnnouncements)
        } else {
            filteredAnnouncements.addAll(allAnnouncements.filter {
                it.title.lowercase().contains(lowerQuery) || it.message.lowercase().contains(lowerQuery)
            })
        }

        adapter.updateList(filteredAnnouncements)

        if (filteredAnnouncements.isEmpty()) {
            binding.rvAnnouncements.visibility = View.GONE
            binding.layoutEmpty.visibility = View.VISIBLE
            if (allAnnouncements.isNotEmpty()) {
                // If no matches but history exists
                binding.layoutEmpty.findViewById<View>(R.id.btnCreateAnnouncement).visibility = View.GONE
            } else {
                binding.layoutEmpty.findViewById<View>(R.id.btnCreateAnnouncement).visibility = View.VISIBLE
            }
        } else {
            binding.rvAnnouncements.visibility = View.VISIBLE
            binding.layoutEmpty.visibility = View.GONE
        }
    }

    private fun showDetailsDialog(item: AnnouncementHistoryItem) {
        val formatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        val dateStr = if (item.timestamp > 0) formatter.format(Date(item.timestamp)) else "Unknown Date"

        val fullMessage = "${item.message}\n\nPublished: $dateStr"

        MaterialAlertDialogBuilder(this)
            .setTitle(item.title)
            .setMessage(fullMessage)
            .setPositiveButton("Close", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        listener?.remove() // Prevent memory leaks
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}