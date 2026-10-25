package com.smartquiz

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.smartquiz.databinding.ActivityAdminUsersBinding
import com.smartquiz.databinding.ItemAdminUserBinding

class AdminUsersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminUsersBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private val allUsers = mutableListOf<User>()
    private val filtered = mutableListOf<User>()
    private lateinit var adapter: UsersAdapter
    private val TAG = "AdminUsers"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminUsersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        adapter = UsersAdapter(filtered) { user -> showUserActions(user) }
        binding.rvUsers.layoutManager = LinearLayoutManager(this)
        binding.rvUsers.adapter = adapter

        // Setup Search
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                applyFilter(s.toString())
            }
        })

        loadUsers()
    }

    private fun loadUsers() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE

        db.collection("users").get()
            .addOnSuccessListener { docs ->
                binding.progressBar.visibility = View.GONE
                allUsers.clear()
                docs.forEach { doc ->
                    try {
                        val user = doc.toObject(User::class.java)
                        user.uid = doc.id
                        allUsers.add(user)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse user ${doc.id}", e)
                    }
                }
                // Sort alphabetically
                allUsers.sortBy { it.name.lowercase() }
                applyFilter(binding.etSearch.text.toString())
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.tvEmpty.visibility = View.VISIBLE
                Log.e(TAG, "loadUsers failed", e)
                Toast.makeText(this, "Failed to load users: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun applyFilter(query: String) {
        filtered.clear()
        if (query.isBlank()) {
            filtered.addAll(allUsers)
        } else {
            val q = query.lowercase()
            filtered.addAll(allUsers.filter {
                it.name.lowercase().contains(q) || it.email.lowercase().contains(q)
            })
        }
        adapter.notifyDataSetChanged()
        binding.tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    // ==================================================================
    // USER ACTIONS (Ban, Block, Unban)
    // ==================================================================
    private fun showUserActions(user: User) {
        val currentUid = auth.currentUser?.uid
        val isSelf = (user.uid == currentUid)

        val actions = mutableListOf<String>()

        if (user.isBanned) {
            actions.add("Unban User")
        } else {
            actions.add("Ban User")
            actions.add("Block with Reason")
        }
        actions.add("Cancel")

        MaterialAlertDialogBuilder(this)
            .setTitle("${user.name}\n${user.email}")
            .setItems(actions.toTypedArray()) { _, which ->
                val choice = actions[which]
                when (choice) {
                    "Ban User" -> {
                        if (isSelf) {
                            Toast.makeText(this, "You cannot ban your own account", Toast.LENGTH_LONG).show()
                            return@setItems
                        }
                        updateUserStatus(user, banned = true, reason = null)
                    }
                    "Unban User" -> updateUserStatus(user, banned = false, reason = null)
                    "Block with Reason" -> {
                        if (isSelf) {
                            Toast.makeText(this, "You cannot block your own account", Toast.LENGTH_LONG).show()
                            return@setItems
                        }
                        promptBlockReason(user)
                    }
                    else -> { /* Cancel */ }
                }
            }
            .show()
    }

    private fun promptBlockReason(user: User) {
        val input = EditText(this)
        input.hint = "Reason for blocking (e.g., Cheating)"

        // Wrap EditText in a FrameLayout to add padding to the dialog
        val container = FrameLayout(this).apply {
            setPadding(60, 20, 60, 0)
            addView(input)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Block ${user.name}")
            .setView(container)
            .setPositiveButton("Block") { _, _ ->
                val reason = input.text.toString().trim()
                if (reason.isEmpty()) {
                    Toast.makeText(this, "Please enter a reason", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                updateUserStatus(user, banned = true, reason = reason)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateUserStatus(user: User, banned: Boolean, reason: String?) {
        val updates = mutableMapOf<String, Any>("isBanned" to banned)

        if (reason != null) {
            updates["banReason"] = reason
        }
        if (!banned) {
            updates["banReason"] = "" // Clear reason on unban
        }

        db.collection("users").document(user.uid).update(updates)
            .addOnSuccessListener {
                Toast.makeText(
                    this,
                    if (banned) "${user.name} blocked" else "${user.name} unblocked",
                    Toast.LENGTH_SHORT
                ).show()
                loadUsers() // Refresh list
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    // ==================================================================
    // ADAPTER
    // ==================================================================
    inner class UsersAdapter(
        private val items: List<User>,
        private val onClick: (User) -> Unit
    ) : RecyclerView.Adapter<UsersAdapter.VH>() {

        inner class VH(val b: ItemAdminUserBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemAdminUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val user = items[position]
            val b = holder.b

            b.tvName.text = user.name.ifBlank { "(no name)" }
            b.tvEmail.text = user.email

            if (user.avatarUrl.isNotBlank()) {
                Glide.with(holder.itemView.context).load(user.avatarUrl).into(b.ivAvatar)
            } else {
                b.ivAvatar.setImageResource(R.drawable.ic_avatar_placeholder)
            }

            // Set Role Chip
            b.chipRole.text = user.role.uppercase()
            when (user.role.lowercase()) {
                "admin", "super_admin" -> {
                    b.chipRole.setChipBackgroundColorResource(R.color.primary)
                    b.chipRole.setTextColor(ContextCompat.getColor(
                        holder.itemView.context, android.R.color.white))
                }
                else -> {
                    b.chipRole.setChipBackgroundColorResource(R.color.surface_variant)
                    b.chipRole.setTextColor(ContextCompat.getColor(
                        holder.itemView.context, R.color.text_secondary))
                }
            }

            // Set Blocked Status Chip
            if (user.isBanned) {
                b.chipStatus.visibility = View.VISIBLE
                b.chipStatus.text = "BLOCKED"
                b.chipStatus.setChipBackgroundColorResource(R.color.error)
                b.chipStatus.setTextColor(ContextCompat.getColor(
                    holder.itemView.context, android.R.color.white))
            } else {
                b.chipStatus.visibility = View.GONE
            }

            b.root.setOnClickListener { onClick(user) }
        }

        override fun getItemCount() = items.size
    }
}