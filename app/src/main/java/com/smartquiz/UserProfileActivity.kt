package com.smartquiz

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.smartquiz.databinding.ActivityUserProfileBinding

class UserProfileActivity : AppCompatActivity() {
    private lateinit var binding: ActivityUserProfileBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private var selectedImageUri: Uri? = null

    // Image picker
    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            selectedImageUri = result.data?.data
            selectedImageUri?.let {
                binding.ivAvatar.setImageURI(it)
                uploadAvatar(it)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Profile"

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        loadUserData()

        // Change photo click
        binding.tvChangePhoto.setOnClickListener {
            openImagePicker()
        }

        // Update profile button – opens edit name dialog
        binding.btnUpdateProfile.setOnClickListener {
            showEditNameDialog()
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        imagePickerLauncher.launch(intent)
    }

    private fun uploadAvatar(imageUri: Uri) {
        val uid = auth.currentUser?.uid ?: return
        val ref = storage.reference.child("avatars/$uid.jpg")
        ref.putFile(imageUri)
            .addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { uri ->
                    db.collection("users").document(uid).update("avatarUrl", uri.toString())
                        .addOnSuccessListener {
                            Toast.makeText(this, "Avatar updated", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadUserData() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val user = doc.toObject(User::class.java)
                if (user != null) {
                    binding.tvNameDisplay.text = user.name
                    binding.tvEmailDisplay.text = user.email
                    if (user.avatarUrl.isNotEmpty()) {
                        Glide.with(this).load(user.avatarUrl).into(binding.ivAvatar)
                    }
                    setupBadges(user.badges)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showEditNameDialog() {
        val input = EditText(this)
        input.setText(binding.tvNameDisplay.text)
        AlertDialog.Builder(this)
            .setTitle("Edit Name")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    updateName(newName)
                } else {
                    Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateName(newName: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).update("name", newName)
            .addOnSuccessListener {
                binding.tvNameDisplay.text = newName
                Toast.makeText(this, "Name updated", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupBadges(badges: List<String>) {
        val adapter = BadgesAdapter(badges)
        binding.rvBadges.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvBadges.adapter = adapter
    }

    class ViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    inner class BadgesAdapter(private val badges: List<String>) :
        RecyclerView.Adapter<ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val tv = TextView(parent.context)
            tv.setPadding(24, 8, 24, 8)
            tv.setTextColor(ContextCompat.getColor(parent.context, R.color.primary))
            tv.textSize = 12f
            tv.setBackgroundResource(R.drawable.bg_badge)
            return ViewHolder(tv)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.textView.text = badges[position]
        }

        override fun getItemCount() = badges.size
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}