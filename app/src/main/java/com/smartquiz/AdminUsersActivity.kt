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
    // USER ACTIONS MENU
    // ==================================================================
    private fun showUserActions(user: User) {
        val currentUid = auth.currentUser?.uid
        val isSelf = (user.uid == currentUid)
        val isAdmin = user.role.lowercase() in listOf("admin", "super_admin")

        val actions = mutableListOf<String>()

        // 1. Promote to Admin
        if (!isAdmin && !isSelf) {
            actions.add("Promote to Admin")
        }

        // 2. Ban / Unban
        if (user.isBanned) {
            actions.add("Unban User")
        } else if (!isSelf) {
            actions.add("Ban User")
        }

        // 3. Block / Unblock
        if (user.isBlocked) {
            actions.add("Unblock User")
        } else if (!isSelf) {
            actions.add("Block with Reason")
        }

        actions.add("Cancel")

        MaterialAlertDialogBuilder(this)
            .setTitle("${user.name}\n${user.email}")
            .setItems(actions.toTypedArray()) { _, which ->
                when (val choice = actions[which]) {
                    "Promote to Admin" -> confirmPromote(user)
                    "Ban User" -> confirmBan(user)
                    "Block with Reason" -> promptBlockReason(user)
                    "Unban User" -> confirmUnban(user)
                    "Unblock User" -> confirmUnblock(user)
                }
            }
            .show()
    }

    // ==================================================================
    // ACTION HANDLERS
    // ==================================================================
    private fun confirmPromote(user: User) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Promote to Admin?")
            .setMessage("This user will receive administrator privileges and access to admin-only features.")
            .setPositiveButton("Promote") { _, _ ->
                updateUserFields(user.uid, mapOf("role" to "admin"), "Promoted to Admin")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmBan(user: User) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Ban User?")
            .setMessage("This action will restrict the selected user's access.")
            .setPositiveButton("Ban User") { _, _ ->
                updateUserFields(
                    user.uid,
                    mapOf("isBanned" to true, "banReason" to "Banned by Admin"),
                    "${user.name} has been banned"
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun promptBlockReason(user: User) {
        val input = EditText(this)
        input.hint = "Enter the reason for blocking"

        val container = FrameLayout(this).apply {
            setPadding(60, 20, 60, 0)
            addView(input)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Block ${user.name}")
            .setView(container)
            .setPositiveButton("Block User") { _, _ ->
                val reason = input.text.toString().trim()
                if (reason.isEmpty()) {
                    Toast.makeText(this, "Reason is required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                updateUserFields(
                    user.uid,
                    mapOf("isBlocked" to true, "blockReason" to reason),
                    "${user.name} has been blocked"
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmUnban(user: User) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Unban User?")
            .setMessage("This will restore access for ${user.name}.")
            .setPositiveButton("Unban") { _, _ ->
                updateUserFields(user.uid, mapOf("isBanned" to false), "${user.name} unbanned")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmUnblock(user: User) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Unblock User?")
            .setMessage("This will remove the block restriction for ${user.name}.")
            .setPositiveButton("Unblock") { _, _ ->
                updateUserFields(user.uid, mapOf("isBlocked" to false), "${user.name} unblocked")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateUserFields(uid: String, updates: Map<String, Any>, successMsg: String) {
        db.collection("users").document(uid).update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, successMsg, Toast.LENGTH_SHORT).show()
                loadUsers() // Refresh list
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Update failed for $uid", e)
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
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

            // Role Chip
            b.chipRole.text = user.role.uppercase()
            if (user.role.lowercase() in listOf("admin", "super_admin")) {
                b.chipRole.setChipBackgroundColorResource(R.color.primary)
                b.chipRole.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.white))
            } else {
                b.chipRole.setChipBackgroundColorResource(R.color.surface_variant)
                b.chipRole.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.text_secondary))
            }

            // Status Chip (Banned vs Blocked)
            if (user.isBanned) {
                b.chipStatus.visibility = View.VISIBLE
                b.chipStatus.text = "BANNED"
                b.chipStatus.setChipBackgroundColorResource(R.color.error)
                b.chipStatus.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.white))
            } else if (user.isBlocked) {
                b.chipStatus.visibility = View.VISIBLE
                b.chipStatus.text = "BLOCKED"
                b.chipStatus.setChipBackgroundColorResource(R.color.warning)
                b.chipStatus.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.white))
            } else {
                b.chipStatus.visibility = View.GONE
            }

            b.root.setOnClickListener { onClick(user) }
        }

        override fun getItemCount() = items.size
    }
}