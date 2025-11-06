package com.dockeep.app

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.dockeep.app.utils.OnboardingManager
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ProfileActivity : AppCompatActivity() {
    companion object {
        private const val PREFS_NAME = "app_prefs"
        private const val THEME_PREF = "is_dark_theme"
    }
    
    private lateinit var profileImageView: ImageView
    private lateinit var changePhotoButton: Button
    private lateinit var usernameEditText: TextInputEditText
    private lateinit var usernameInputLayout: TextInputLayout
    private lateinit var cancelButton: Button
    private lateinit var saveButton: Button
    private lateinit var sharedPreferences: SharedPreferences
    
    private var selectedImageUri: Uri? = null
    private var currentUsername: String? = null
    private var isDarkTheme = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Restore theme preference using the same approach as MainActivity
        loadThemePreference()
        updateTheme()
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        
        // Initialize views
        initViews()
        
        // Setup SharedPreferences
        sharedPreferences = getSharedPreferences("profile_prefs", MODE_PRIVATE)
        
        // Load current profile data
        loadProfileData()
        
        // Setup click listeners
        setupClickListeners()
        
        // Setup toolbar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle("Profile")
    }
    
    private fun initViews() {
        profileImageView = findViewById(R.id.profileImageView)
        changePhotoButton = findViewById(R.id.changePhotoButton)
        usernameEditText = findViewById(R.id.usernameEditText)
        usernameInputLayout = findViewById(R.id.usernameInputLayout)
        cancelButton = findViewById(R.id.cancelButton)
        saveButton = findViewById(R.id.saveButton)
    }
    
    private fun loadProfileData() {
        // Load username from OnboardingManager
        currentUsername = OnboardingManager.getUserName(this)
        usernameEditText.setText(currentUsername)
        
        // Load profile photo if exists
        val profilePhotoPath = sharedPreferences.getString("profile_photo_path", null)
        if (profilePhotoPath != null) {
            val profilePhotoFile = File(profilePhotoPath)
            if (profilePhotoFile.exists()) {
                profileImageView.setImageURI(Uri.fromFile(profilePhotoFile))
            }
        }
    }
    
    private fun setupClickListeners() {
        changePhotoButton.setOnClickListener {
            openImagePicker()
        }
        
        cancelButton.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        
        saveButton.setOnClickListener {
            saveProfile()
        }
    }
    
    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        imagePickerLauncher.launch(intent)
    }
    
    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            if (data != null && data.data != null) {
                selectedImageUri = data.data
                try {
                    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val source = ImageDecoder.createSource(this.contentResolver, selectedImageUri!!)
                        ImageDecoder.decodeBitmap(source)
                    } else {
                        MediaStore.Images.Media.getBitmap(this.contentResolver, selectedImageUri)
                    }
                    profileImageView.setImageBitmap(bitmap)
                    // Make the image circular
                    profileImageView.scaleType = ImageView.ScaleType.CENTER_CROP
                } catch (e: IOException) {
                    Log.e("ProfileActivity", "Error loading image", e)
                    Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun saveProfile() {
        val newUsername = usernameEditText.text.toString().trim()
        
        if (newUsername.isEmpty()) {
            usernameInputLayout.error = "Username cannot be empty"
            return
        }
        
        var photoSaved = true
        
        // Save profile photo if selected
        if (selectedImageUri != null) {
            photoSaved = saveProfilePhoto()
        }
        
        if (newUsername != currentUsername) {
            // Save new username using OnboardingManager
            OnboardingManager.completeOnboarding(this, newUsername)
            
            // Also save to our profile preferences
            sharedPreferences.edit()
                .putString("username", newUsername)
                .apply()
        }
        
        if (photoSaved) {
            Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show()
            
            // Set result to indicate that profile was updated
            val resultIntent = Intent()
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        } else {
            Toast.makeText(this, "Error saving profile photo", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun saveProfilePhoto(): Boolean {
        return try {
            if (selectedImageUri == null) {
                Log.d("ProfileActivity", "No image selected, returning true")
                return true
            }
            
            Log.d("ProfileActivity", "Saving profile photo from URI: $selectedImageUri")
            
            var bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(this.contentResolver, selectedImageUri!!)
                ImageDecoder.decodeBitmap(source)
            } else {
                MediaStore.Images.Media.getBitmap(this.contentResolver, selectedImageUri)
            }
            
            // Convert hardware bitmap to software bitmap if needed
            if (bitmap.config == Bitmap.Config.HARDWARE) {
                Log.d("ProfileActivity", "Converting hardware bitmap to software bitmap")
                bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
            }
            
            // Create circular bitmap
            val circularBitmap = getCircularBitmap(bitmap)
            
            // Create file to save the profile photo
            val profilePhotosDir = File(filesDir, "profile_photos")
            if (!profilePhotosDir.exists()) {
                profilePhotosDir.mkdirs()
                Log.d("ProfileActivity", "Created profile photos directory")
            }
            
            val photoFile = File(profilePhotosDir, "profile_photo.png")
            Log.d("ProfileActivity", "Saving photo to: ${photoFile.absolutePath}")
            
            // Delete existing file if it exists
            if (photoFile.exists()) {
                photoFile.delete()
                Log.d("ProfileActivity", "Deleted existing profile photo file")
            }
            
            val outputStream = FileOutputStream(photoFile)
            circularBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.close()
            
            // Verify the file was saved
            if (photoFile.exists()) {
                Log.d("ProfileActivity", "Profile photo saved successfully, file size: ${photoFile.length()} bytes")
            } else {
                Log.e("ProfileActivity", "Profile photo file was not created")
                return false
            }
            
            // Save the photo path in SharedPreferences
            sharedPreferences.edit()
                .putString("profile_photo_path", photoFile.absolutePath)
                .apply()
            
            Log.d("ProfileActivity", "Profile photo path saved to SharedPreferences")
            true
        } catch (e: Exception) {
            Log.e("ProfileActivity", "Error saving profile photo", e)
            false
        }
    }
    
    private fun getCircularBitmap(bitmap: Bitmap): Bitmap {
        val size = Math.min(bitmap.width, bitmap.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()
        val path = Path()
        path.addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW)
        canvas.clipPath(path)
        canvas.drawBitmap(bitmap, (size - bitmap.width) / 2f, (size - bitmap.height) / 2f, paint)
        return output
    }
    
    private fun loadThemePreference() {
        val sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        isDarkTheme = sharedPrefs.getBoolean(THEME_PREF, false)
    }
    
    private fun saveThemePreference() {
        val sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        sharedPrefs.edit()
            .putBoolean(THEME_PREF, isDarkTheme)
            .apply()
    }
    
    private fun updateTheme() {
        val themeMode = if (isDarkTheme) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
        AppCompatDelegate.setDefaultNightMode(themeMode)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}