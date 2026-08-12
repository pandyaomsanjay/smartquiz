package com.smartquiz

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.smartquiz.databinding.ActivityProfileSetupBinding

class ProfileSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileSetupBinding
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var storage: FirebaseStorage
    private var selectedImageUri: Uri? = null

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            selectedImageUri = result.data?.data
            selectedImageUri?.let {
                binding.ivAvatar.setImageURI(it)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        storage = FirebaseStorage.getInstance()

        val userId = auth.currentUser?.uid
        if (userId != null) {
            db.collection("users").document(userId).get()
                .addOnSuccessListener { doc ->
                    val name = doc.getString("name") ?: ""
                    binding.etName.setText(name)
                    val avatarUrl = doc.getString("avatarUrl")
                    if (!avatarUrl.isNullOrEmpty()) {
                        Glide.with(this).load(avatarUrl).into(binding.ivAvatar)
                    }
                }
        }

        binding.ivAvatar.setOnClickListener {
            openImagePicker()
        }

        binding.tvChangePhoto.setOnClickListener {
            openImagePicker()
        }

        binding.btnSaveProfile.setOnClickListener {
            saveProfile()
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        imagePickerLauncher.launch(intent)
    }

    private fun saveProfile() {
        val name = binding.etName.text.toString().trim()
        if (name.isEmpty()) {
            binding.etName.error = "Name is required"
            binding.etName.requestFocus()
            return
        }

        val userId = auth.currentUser?.uid ?: run {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        val updates = mutableMapOf<String, Any>()
        updates["name"] = name

        if (selectedImageUri != null) {
            val ref = storage.reference.child("avatars/$userId.jpg")
            ref.putFile(selectedImageUri!!)
                .addOnSuccessListener {
                    ref.downloadUrl.addOnSuccessListener { uri ->
                        updates["avatarUrl"] = uri.toString()
                        updateFirestore(userId, updates)
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Avatar upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    updateFirestore(userId, updates)
                }
        } else {
            updateFirestore(userId, updates)
        }
    }

    private fun updateFirestore(userId: String, updates: MutableMap<String, Any>) {
        db.collection("users").document(userId)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Profile saved", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, HomeDashboardActivity::class.java))
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save profile: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}