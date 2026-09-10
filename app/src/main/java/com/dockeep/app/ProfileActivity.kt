package com.dockeep.app

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.dockeep.app.utils.AppLock
import androidx.appcompat.app.AppCompatDelegate
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.dockeep.app.ui.LedgerNav
import com.dockeep.app.utils.ColorUtils
import com.dockeep.app.utils.OnboardingManager
import com.dockeep.app.viewmodel.DocumentViewModel
import com.dockeep.app.viewmodel.PersonViewModel
import kotlinx.coroutines.launch
import java.util.Locale
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ProfileActivity : AppCompatActivity() {
    companion object {
        private const val PREFS_NAME = "app_prefs"
        private const val THEME_PREF = "is_dark_theme"

        private const val PHOTO_DIR = "profile_photos"
        private const val PROFILE_PHOTO = "profile_photo.jpg"
        private const val PENDING_PHOTO = "profile_photo_pending.jpg"

        /** Avatars are shown at 88dp; this is generous even at xxxhdpi. */
        private const val MAX_PHOTO_PX = 512
    }
    
    private lateinit var profileImageView: ImageView
    private lateinit var profileInitial: TextView
    private lateinit var changePhotoButton: View
    private lateinit var usernameEditText: EditText
    private lateinit var usernameInputLayout: View
    private lateinit var cancelButton: View
    private lateinit var saveButton: View
    private lateinit var storageRows: LinearLayout
    private lateinit var storageSection: View
    private lateinit var statDocuments: TextView
    private lateinit var statScansTotal: TextView
    private lateinit var statOnDisk: TextView
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var documentViewModel: DocumentViewModel
    private lateinit var personViewModel: PersonViewModel
    
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

        // This screen owns the You tab in the shared bottom bar.
        LedgerNav.markActive(this, LedgerNav.Tab.YOU)

        documentViewModel = ViewModelProvider(this, DocumentViewModel.Factory(application))
            .get(DocumentViewModel::class.java)
        personViewModel = ViewModelProvider(this, PersonViewModel.Factory(application))
            .get(PersonViewModel::class.java)
        bindStats()
    }
    
    private fun initViews() {
        profileImageView = findViewById(R.id.profileImageView)
        profileInitial = findViewById(R.id.profileInitial)
        changePhotoButton = findViewById(R.id.changePhotoButton)
        usernameEditText = findViewById(R.id.usernameEditText)
        usernameInputLayout = findViewById(R.id.usernameInputLayout)
        cancelButton = findViewById(R.id.cancelButton)
        saveButton = findViewById(R.id.saveButton)
        storageRows = findViewById(R.id.storageRows)
        storageSection = findViewById(R.id.storageSection)
        statDocuments = findViewById(R.id.statDocuments)
        statScansTotal = findViewById(R.id.statScansTotal)
        statOnDisk = findViewById(R.id.statOnDisk)

        findViewById<View>(R.id.navDocs).setOnClickListener { finish() }
        findViewById<View>(R.id.navPeople).setOnClickListener {
            startActivity(Intent(this, FamilyFriendsActivity::class.java))
            finish()
        }
    }

    /** Paints the avatar tile from the name, the way every other tile is set. */
    private fun bindAvatarTile(name: String) {
        val letter = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        profileInitial.text = letter
        val colors = ColorUtils.getPlaceholderColorScheme(this, name)
        profileInitial.background?.mutate()?.setColorFilter(
            colors.backgroundColor,
            android.graphics.PorterDuff.Mode.SRC_IN
        )
        profileInitial.setTextColor(colors.textColor)
    }

    /**
     * Fills the three-figure strip and the storage breakdown. Sizes are summed
     * from the files themselves so the total matches what is really on disk.
     */
    private fun bindStats() {
        documentViewModel.getAllDocuments().observe(this) { docs ->
            statDocuments.text = docs.size.toString()

            lifecycleScope.launch {
                var scans = 0
                var totalBytes = 0L
                val bytesByPerson = mutableMapOf<Long?, Long>()

                for (doc in docs) {
                    val images = documentViewModel.getImagesForDocumentSync(doc.id)
                    // Notes are blocks, not scans.
                    scans += images.count { it.isImage }
                    val docBytes = images.filter { it.isImage }.sumOf {
                        runCatching { File(it.imagePath).length() }.getOrDefault(0L)
                    }
                    totalBytes += docBytes
                    bytesByPerson[doc.personId] =
                        (bytesByPerson[doc.personId] ?: 0L) + docBytes
                }

                statScansTotal.text = scans.toString()
                statOnDisk.text = formatSize(totalBytes)
                bindStorageRows(bytesByPerson)
            }
        }
    }

    private suspend fun bindStorageRows(bytesByPerson: Map<Long?, Long>) {
        storageRows.removeAllViews()

        val people = personViewModel.getAllPeopleSync()
        val selfName = (OnboardingManager.getUserName(this) ?: "")
            .ifBlank { getString(R.string.ledger_you) }

        // Documents belonging to the phone owner carry no personId, so that
        // bucket heads the list under the display name.
        val entries = mutableListOf<Triple<String, Long, String>>()
        bytesByPerson[null]?.let { entries.add(Triple(selfName, it, selfName)) }
        for (person in people) {
            val bytes = bytesByPerson[person.id] ?: continue
            entries.add(Triple(person.name, bytes, person.name))
        }

        if (entries.isEmpty()) {
            storageSection.visibility = View.GONE
            return
        }
        storageSection.visibility = View.VISIBLE

        val largest = entries.maxOf { it.second }.coerceAtLeast(1L)
        val inflater = LayoutInflater.from(this)

        for ((name, bytes, colorKey) in entries.sortedByDescending { it.second }) {
            val row = inflater.inflate(R.layout.item_ledger_storage_row, storageRows, false)
            val swatch = row.findViewById<View>(R.id.storageSwatch)
            val colors = ColorUtils.getPlaceholderColorScheme(this, colorKey)
            swatch.background?.mutate()?.setColorFilter(
                colors.backgroundColor,
                android.graphics.PorterDuff.Mode.SRC_IN
            )
            row.findViewById<TextView>(R.id.storageName).text = name
            row.findViewById<TextView>(R.id.storageSize).text = formatSize(bytes)

            // Bar length is relative to the largest holder, so the breakdown
            // reads as a comparison rather than as absolute space.
            val fill = row.findViewById<View>(R.id.storageFill)
            fill.post {
                val trackWidth = (fill.parent as View).width
                val params = fill.layoutParams
                params.width =
                    (trackWidth * bytes.toDouble() / largest).toInt().coerceAtLeast(2)
                fill.layoutParams = params
            }
            storageRows.addView(row)
        }
    }

    /** Bytes as the design writes them: "218 MB", "812 KB". */
    private fun formatSize(bytes: Long): String = when {
        bytes >= 1024L * 1024L ->
            String.format(Locale.getDefault(), "%.0f MB", bytes / 1048576.0)
        bytes >= 1024L -> String.format(Locale.getDefault(), "%d KB", bytes / 1024)
        else -> "$bytes B"
    }
    
    private fun loadProfileData() {
        // Load username from OnboardingManager
        currentUsername = OnboardingManager.getUserName(this)
        usernameEditText.setText(currentUsername)
        bindAvatarTile(currentUsername ?: "")

        // Keep the tile in step while the name is being edited.
        usernameEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                bindAvatarTile(s?.toString() ?: "")
            }
        })
        
        // Load profile photo if exists
        val profilePhotoPath = sharedPreferences.getString("profile_photo_path", null)
        if (profilePhotoPath != null) {
            val profilePhotoFile = File(profilePhotoPath)
            if (profilePhotoFile.exists()) {
                profileImageView.setImageURI(Uri.fromFile(profilePhotoFile))
                profileImageView.visibility = View.VISIBLE
            }
        }
    }
    
    private fun setupClickListeners() {
        changePhotoButton.setOnClickListener {
            openImagePicker()
        }
        
        cancelButton.setOnClickListener {
            discardPendingPhoto()
            onBackPressedDispatcher.onBackPressed()
        }
        
        saveButton.setOnClickListener {
            saveProfile()
        }
    }
    
    private fun openImagePicker() {
        // The system photo picker grants read access to the single item the
        // user chose, on every API level, with no storage permission at all.
        imagePickerLauncher.launch(
            PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                .build()
        )
    }

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@registerForActivityResult

        // Decode and write it out straight away. The grant on this URI is
        // transient, so re-reading it at Save time is what used to fail.
        val staged = stagePickedPhoto(uri)
        if (staged == null) {
            Toast.makeText(this, R.string.ledger_photo_error, Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }

        selectedImageUri = uri
        profileImageView.setImageBitmap(staged)
        profileImageView.visibility = View.VISIBLE
        profileImageView.scaleType = ImageView.ScaleType.CENTER_CROP
    }

    /**
     * Decodes the picked image, crops it square and writes it to the pending
     * file. Returns the bitmap for preview, or null if it could not be read.
     *
     * Square, not circular: the design's avatar is a tile like every other
     * letter tile in the app.
     */
    private fun stagePickedPhoto(uri: Uri): Bitmap? {
        return try {
            var bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    // Hardware bitmaps cannot be read back for compression.
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }

            // Bitmap.Config.HARDWARE only exists from API 26, and only
            // ImageDecoder can produce one, so the check is gated.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                bitmap.config == Bitmap.Config.HARDWARE
            ) {
                bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
            }

            val square = cropSquare(bitmap)

            val dir = File(filesDir, PHOTO_DIR).apply { if (!exists()) mkdirs() }
            FileOutputStream(File(dir, PENDING_PHOTO)).use { out ->
                square.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            square
        } catch (e: Exception) {
            Log.e("ProfileActivity", "Could not read the picked image", e)
            null
        }
    }

    /** Centre-crops to a square and caps the long edge, so tiles stay small. */
    private fun cropSquare(source: Bitmap): Bitmap {
        val size = minOf(source.width, source.height)
        val x = (source.width - size) / 2
        val y = (source.height - size) / 2
        val cropped = Bitmap.createBitmap(source, x, y, size, size)
        return if (size > MAX_PHOTO_PX) {
            Bitmap.createScaledBitmap(cropped, MAX_PHOTO_PX, MAX_PHOTO_PX, true)
        } else {
            cropped
        }
    }


    private fun saveProfile() {
        val newUsername = usernameEditText.text.toString().trim()
        
        if (newUsername.isEmpty()) {
            // The field has no label to hang an error on in this design, so
            // the message is surfaced directly and focus returns to the input.
            Toast.makeText(this, R.string.ledger_name_required, Toast.LENGTH_SHORT).show()
            usernameEditText.requestFocus()
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
    
    /**
     * Promotes the pending file written at pick time to the live one. No
     * decoding happens here, so nothing depends on a URI grant that may have
     * already lapsed.
     */
    private fun saveProfilePhoto(): Boolean {
        return try {
            val dir = File(filesDir, PHOTO_DIR)
            val pending = File(dir, PENDING_PHOTO)
            if (!pending.exists()) return true

            val target = File(dir, PROFILE_PHOTO)
            if (target.exists()) target.delete()

            pending.copyTo(target, overwrite = true)
            pending.delete()

            if (!target.exists() || target.length() == 0L) {
                Log.e("ProfileActivity", "Profile photo was not written")
                return false
            }

            sharedPreferences.edit()
                .putString("profile_photo_path", target.absolutePath)
                .apply()
            true
        } catch (e: Exception) {
            Log.e("ProfileActivity", "Error saving profile photo", e)
            false
        }
    }

    /** Drops a staged photo the user picked but never saved. */
    private fun discardPendingPhoto() {
        runCatching { File(File(filesDir, PHOTO_DIR), PENDING_PHOTO).delete() }
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

    override fun onResume() {
        super.onResume()
        // Coming back from recents can land directly on this screen, which
        // would show its contents without the vault ever being unlocked.
        // Finishing returns to the gated home screen, which does the asking.
        if (AppLock.shouldChallenge(this)) finish()
    }

}