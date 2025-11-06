package com.dockeep.app

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.dockeep.app.adapter.ImageAdapter
import com.dockeep.app.adapter.ImageItemTouchHelperCallback
import com.dockeep.app.database.Document
import com.dockeep.app.database.DocumentImage
import com.dockeep.app.viewmodel.DocumentViewModel
import com.dockeep.app.utils.FileUtils
import com.dockeep.app.ImageViewerActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

// PDF imports
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document as PdfLayoutDocument
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Image as PdfImage
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.layout.properties.UnitValue
import com.itextpdf.layout.properties.HorizontalAlignment
import com.itextpdf.layout.element.AreaBreak
import com.itextpdf.layout.properties.AreaBreakType

class DocumentDetailActivity : AppCompatActivity() {
    private lateinit var viewModel: DocumentViewModel
    private lateinit var recyclerView: RecyclerView
    private lateinit var fab: FloatingActionButton
    private lateinit var deleteFab: FloatingActionButton
    private var documentId: Long = -1
    private var document: Document? = null
    private var images: MutableList<DocumentImage> = mutableListOf()
    private var imageAdapter: ImageAdapter? = null
    private var itemTouchHelper: ItemTouchHelper? = null

    // For selecting images from gallery
    private val selectImagesLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            addImagesToDocument(uris)
        }
    }

    // For capturing image with camera
    private var currentPhotoPath: String? = null
    private val captureImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            currentPhotoPath?.let { photoPath ->
                val uri = Uri.fromFile(File(photoPath))
                addImagesToDocument(listOf(uri))
            }
        }
    }

    // Permission launcher for camera
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openCamera()
        } else {
            Toast.makeText(this, "Camera permission is required to take photos", Toast.LENGTH_SHORT).show()
        }
    }

    // Permission launcher for storage
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted
        } else {
            Toast.makeText(this, "Storage permission is required to save images to gallery", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_document_detail)

        // Get document ID from intent
        documentId = intent.getLongExtra("document_id", -1)
        if (documentId == -1L) {
            finish()
            return
        }

        // Initialize views
        initViews()

        // Setup RecyclerView
        setupRecyclerView()

        // Setup ViewModel
        setupViewModel()

        // Setup click listeners
        setupClickListeners()

        // Load document and images
        loadDocument()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.imagesRecyclerView)
        fab = findViewById(R.id.fab)
        deleteFab = findViewById(R.id.deleteFab)

        // Setup toolbar
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupRecyclerView() {
        // The RecyclerView is already set up with GridLayoutManager in XML
        // Just ensure we have the right reference
        val layoutManager = recyclerView.layoutManager
        if (layoutManager !is androidx.recyclerview.widget.GridLayoutManager) {
            // Set spanCount to 2 for better visibility of download and share buttons
            val spanCount = 2
            recyclerView.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, spanCount)
        }
        
        // Optimize RecyclerView for ultra-smooth scrolling and animations
        recyclerView.setHasFixedSize(true)
        recyclerView.setItemViewCacheSize(30) // Increased cache size
        recyclerView.setRecycledViewPool(androidx.recyclerview.widget.RecyclerView.RecycledViewPool())
        recyclerView.recycledViewPool.setMaxRecycledViews(0, 20) // Set max recycled views
        
        // Disable item animator to prevent interference with drag and drop
        recyclerView.itemAnimator = null
        
        // Improve scrolling behavior for long-distance drags
        recyclerView.isNestedScrollingEnabled = false
        recyclerView.setScrollingTouchSlop(RecyclerView.TOUCH_SLOP_PAGING)
        
        // Add smooth scrolling with custom parameters for better long-distance dragging
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                // Optimize performance during scrolling
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    recyclerView.recycledViewPool.clear()
                }
            }
        })
    }

    private fun setupViewModel() {
        viewModel = ViewModelProvider(this, DocumentViewModel.Factory(application))[DocumentViewModel::class.java]
    }

    private fun setupClickListeners() {
        fab.setOnClickListener {
            showAddImageOptions()
        }

        deleteFab.setOnClickListener {
            document?.let { showDeleteConfirmation(it) }
        }
    }

    private fun loadDocument() {
        // Load document details
        viewModel.getDocumentById(documentId).observe(this) { doc ->
            if (doc != null) {
                document = doc
                supportActionBar?.title = doc.name
            } else {
                Toast.makeText(this, "Document not found", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        // Load images
        viewModel.getImagesForDocument(documentId).observe(this) { imgs ->
            images.clear()
            // Sort images by order when loading
            images.addAll(imgs.sortedBy { it.order })
            updateUI()
        }
    }

    private fun updateUI() {
        // Sort images by order before displaying
        val sortedImages = images.sortedBy { it.order }
        
        // Only create a new adapter if we don't have one or if the images have changed significantly
        if (imageAdapter == null) {
            imageAdapter = ImageAdapter(
                sortedImages.toMutableList(),
                onImageClick = { image ->
                    viewFullImage(image)
                },
                onShareClick = { image ->
                    shareSingleImage(image)
                },
                onDownloadClick = { image ->
                    downloadSingleImage(image)
                },
                onDeleteClick = { image ->
                    showDeleteImageConfirmation(image)
                },
                onImageReordered = { updatedImages ->
                    // Handle image reordering and save to database
                    // Create a copy to avoid ConcurrentModificationException
                    val updatedImagesCopy = updatedImages.toList()
                    images.clear()
                    images.addAll(updatedImagesCopy)
                    saveImageOrder(updatedImagesCopy)
                }
            )
            
            // Setup drag and drop BEFORE assigning adapter to RecyclerView
            setupDragAndDrop()
            
            // Assign adapter to RecyclerView after setting up drag and drop
            recyclerView.adapter = imageAdapter
        } else {
            // Update existing adapter with new data
            imageAdapter?.updateImages(sortedImages)
            // Ensure drag and drop is still set up
            setupDragAndDrop()
        }
    }

    private fun setupDragAndDrop() {
        // Ensure we have a valid adapter
        val adapter = imageAdapter ?: return
        
        // Create the callback and touch helper
        val callback = ImageItemTouchHelperCallback(adapter)
        itemTouchHelper = ItemTouchHelper(callback)
        
        // Attach to RecyclerView with post to ensure proper initialization
        recyclerView.post {
            itemTouchHelper?.attachToRecyclerView(recyclerView)
        }
    }

    private fun saveImageOrder(images: List<DocumentImage>) {
        // Save the new order to the database
        if (images.isNotEmpty()) {
            viewModel.updateImageOrder(images)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_document_detail, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        // Ensure icons are always visible in the action bar
        menu.findItem(R.id.action_download_options)?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu.findItem(R.id.action_share_all)?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu.findItem(R.id.action_edit)?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_download_options -> {
                showDownloadOptions()
                true
            }
            R.id.action_share_all -> {
                showShareOptions()
                true
            }
            R.id.action_edit -> {
                editDocument()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showDownloadOptions() {
        // Inflate the custom dialog layout
        val dialogView = layoutInflater.inflate(R.layout.dialog_download_options, null)
        
        // Find views in the dialog
        val pdfOption = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.pdfOption)
        val galleryOption = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.galleryOption)
        val cancelButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.cancelButton)
        
        // Create and show the dialog
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        // Apply fade-in animation when the dialog is shown
        dialog.setOnShowListener {
            val animation = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.dialog_fade_in)
            dialogView.startAnimation(animation)
        }
        
        // Set click listeners for the options
        pdfOption.setOnClickListener {
            dialog.dismiss()
            generateAndSavePdf()
        }
        
        galleryOption.setOnClickListener {
            dialog.dismiss()
            saveAllImagesToGallery()
        }
        
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun generateAndSavePdf() {
        if (images.isEmpty()) {
            Toast.makeText(this, "No images to include in PDF", Toast.LENGTH_SHORT).show()
            return
        }

        // Show progress dialog
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("Generating PDF")
            .setMessage("Please wait...")
            .setCancelable(false)
            .show()

        Thread {
            try {
                // Create PDF file
                val docDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    getExternalFilesDir(null)
                } else {
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                }
                
                if (docDir != null && !docDir.exists()) {
                    docDir.mkdirs()
                }
                
                val fileName = "${document?.name ?: "Document"}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.pdf"
                val pdfFile = File(docDir, fileName)
                
                // Create PDF document
                val writer = PdfWriter(pdfFile)
                val pdfDoc = PdfDocument(writer)
                val pdfLayoutDocument = PdfLayoutDocument(pdfDoc)
                
                // Set default page size for all pages
                pdfDoc.setDefaultPageSize(PageSize.A4)
                
                // Add each image on a separate page (no text at all)
                for ((index, image) in images.withIndex()) {
                    val imageFile = File(image.imagePath)
                    if (imageFile.exists()) {
                        try {
                            // Add a new page for each image (starting from the second image)
                            if (index > 0) {
                                pdfLayoutDocument.add(AreaBreak(AreaBreakType.NEXT_PAGE))
                            }
                            
                            val imageData = ImageDataFactory.create(imageFile.absolutePath)
                            val pdfImage = PdfImage(imageData)
                            
                            // Scale image to fit the page while maintaining aspect ratio
                            pdfImage.setAutoScale(true)
                            pdfImage.setMaxWidth(PageSize.A4.width - 40) // Add some margins
                            pdfImage.setMaxHeight(PageSize.A4.height - 40)
                            
                            // Center the image on the page
                            pdfImage.setHorizontalAlignment(HorizontalAlignment.CENTER)
                            
                            // Add the image to the document
                            pdfLayoutDocument.add(pdfImage)
                        } catch (e: Exception) {
                            Log.e("PDFGeneration", "Error adding image to PDF: ${e.message}")
                        }
                    }
                }
                
                pdfLayoutDocument.close()
                
                // Dismiss progress dialog and save the PDF directly
                runOnUiThread {
                    progressDialog.dismiss()
                    savePdfToFileManager(pdfFile)
                }
            } catch (e: Exception) {
                Log.e("PDFGeneration", "Error generating PDF: ${e.message}")
                runOnUiThread {
                    progressDialog.dismiss()
                    Toast.makeText(this, "Error generating PDF: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun savePdfToFileManager(pdfFile: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use MediaStore for Android 10 and above
                val contentValues = ContentValues().apply {
                    put(MediaStore.Files.FileColumns.DISPLAY_NAME, pdfFile.name)
                    put(MediaStore.Files.FileColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.Files.FileColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/DocKeep")
                }

                val resolver = contentResolver
                val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)

                uri?.let { pdfUri ->
                    resolver.openOutputStream(pdfUri).use { outputStream ->
                        FileInputStream(pdfFile).use { inputStream ->
                            inputStream.copyTo(outputStream!!)
                        }
                    }
                    Toast.makeText(this, "PDF saved to Documents/DocKeep", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Use traditional file copying for older Android versions
                val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                val dockeepDir = File(documentsDir, "DocKeep")
                if (!dockeepDir.exists()) {
                    dockeepDir.mkdirs()
                }

                val newFile = File(dockeepDir, pdfFile.name)
                FileInputStream(pdfFile).use { input ->
                    FileOutputStream(newFile).use { output ->
                        input.copyTo(output)
                    }
                }

                // Notify the media scanner about the new file
                MediaScannerConnection.scanFile(this, arrayOf(newFile.toString()), null, null)
                Toast.makeText(this, "PDF saved to Documents/DocKeep", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error saving PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sharePdf(pdfFile: File) {
        try {
            val pdfUri = FileUtils.getUriForFile(this, pdfFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, pdfUri as android.os.Parcelable)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share PDF"))
        } catch (e: Exception) {
            Toast.makeText(this, "Error sharing PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveAllImagesToGallery() {
        if (images.isEmpty()) {
            Toast.makeText(this, "No images to save", Toast.LENGTH_SHORT).show()
            return
        }

        // Show progress dialog
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("Saving Images")
            .setMessage("Saving images to gallery...")
            .setCancelable(false)
            .show()

        Thread {
            var successCount = 0
            var errorCount = 0
            
            for (image in images) {
                val imageFile = File(image.imagePath)
                if (imageFile.exists()) {
                    try {
                        val savedUri = saveImageToGallery(imageFile)
                        if (savedUri != null) {
                            successCount++
                        } else {
                            errorCount++
                        }
                    } catch (e: Exception) {
                        errorCount++
                        Log.e("SaveImages", "Error saving image: ${e.message}")
                    }
                } else {
                    errorCount++
                }
            }
            
            // Dismiss progress dialog and show result on UI thread
            runOnUiThread {
                progressDialog.dismiss()
                val message = if (errorCount == 0) {
                    "All images saved to gallery successfully!"
                } else {
                    "$successCount images saved successfully. $errorCount images failed to save."
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private fun showDeleteConfirmation(document: Document) {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_delete)
            .setMessage(R.string.delete_document_confirmation)
            .setPositiveButton(R.string.yes) { _, _ ->
                deleteDocument(document)
            }
            .setNegativeButton(R.string.no) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun deleteDocument(document: Document) {
        // Create an intent to notify MainActivity to delete the document
        val resultIntent = Intent()
        resultIntent.putExtra("deleted_document_id", document.id)
        setResult(RESULT_OK, resultIntent)

        // Delete the document through the ViewModel
        viewModel.deleteDocument(document)

        // Show success message and finish the activity
        Toast.makeText(this, R.string.document_deleted_success, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun viewFullImage(image: DocumentImage) {
        // Launch the image viewer activity with all images in the document
        val position = images.indexOfFirst { it.id == image.id }
        if (position != -1) {
            val intent = Intent(this, ImageViewerActivity::class.java).apply {
                putExtra(ImageViewerActivity.EXTRA_IMAGES, ArrayList(images))
                putExtra(ImageViewerActivity.EXTRA_INITIAL_POSITION, position)
                putExtra(ImageViewerActivity.EXTRA_DOCUMENT_NAME, document?.name ?: "Document")
            }
            startActivity(intent)
        } else {
            Toast.makeText(this, "Image not found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareSingleImage(image: DocumentImage) {
        val imageFile = File(image.imagePath)
        if (imageFile.exists()) {
            val imageUri = FileUtils.getUriForFile(this, imageFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, imageUri as android.os.Parcelable)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share image"))
        } else {
            Toast.makeText(this, "Image file not found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun downloadSingleImage(image: DocumentImage) {
        // Check and request storage permission if needed
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }
        }

        val imageFile = File(image.imagePath)
        if (imageFile.exists()) {
            try {
                // Save image to gallery
                val savedUri = saveImageToGallery(imageFile)
                if (savedUri != null) {
                    Toast.makeText(this, "Image saved to gallery", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to save image to gallery", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error saving image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Image file not found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveImageToGallery(imageFile: File): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use MediaStore for Android 10 and above
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, imageFile.name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/DocKeep")
                }

                val resolver = contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                uri?.let { imageUri ->
                    resolver.openOutputStream(imageUri).use { outputStream ->
                        FileInputStream(imageFile).use { inputStream ->
                            inputStream.copyTo(outputStream!!)
                        }
                    }
                }
                uri
            } else {
                // Use traditional file copying for older Android versions
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val dockeepDir = File(picturesDir, "DocKeep")
                if (!dockeepDir.exists()) {
                    dockeepDir.mkdirs()
                }

                val newFile = File(dockeepDir, imageFile.name)
                FileInputStream(imageFile).use { input ->
                    FileOutputStream(newFile).use { output ->
                        input.copyTo(output)
                    }
                }

                // Notify the media scanner about the new file
                MediaScannerConnection.scanFile(this, arrayOf(newFile.toString()), null, null)

                Uri.fromFile(newFile)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun showShareOptions() {
        // Inflate the custom dialog layout
        val dialogView = layoutInflater.inflate(R.layout.dialog_share_options, null)
        
        // Find views in the dialog
        val sharePdfOption = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.sharePdfOption)
        val shareImagesOption = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.shareImagesOption)
        val cancelButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.cancelButton)
        
        // Create and show the dialog
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        // Apply fade-in animation when the dialog is shown
        dialog.setOnShowListener {
            val animation = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.dialog_fade_in)
            dialogView.startAnimation(animation)
        }
        
        // Set click listeners for the options
        sharePdfOption.setOnClickListener {
            dialog.dismiss()
            generateAndSharePdf()
        }
        
        shareImagesOption.setOnClickListener {
            dialog.dismiss()
            shareAllImages()
        }
        
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun generateAndSharePdf() {
        if (images.isEmpty()) {
            Toast.makeText(this, "No images to include in PDF", Toast.LENGTH_SHORT).show()
            return
        }

        // Show progress dialog
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("Generating PDF")
            .setMessage("Please wait...")
            .setCancelable(false)
            .show()

        Thread {
            try {
                // Create PDF file
                val docDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    getExternalFilesDir(null)
                } else {
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                }
                
                if (docDir != null && !docDir.exists()) {
                    docDir.mkdirs()
                }
                
                val fileName = "${document?.name ?: "Document"}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.pdf"
                val pdfFile = File(docDir, fileName)
                
                // Create PDF document
                val writer = PdfWriter(pdfFile)
                val pdfDoc = PdfDocument(writer)
                val pdfLayoutDocument = PdfLayoutDocument(pdfDoc)
                
                // Set default page size for all pages
                pdfDoc.setDefaultPageSize(PageSize.A4)
                
                // Add each image on a separate page (no text at all)
                for ((index, image) in images.withIndex()) {
                    val imageFile = File(image.imagePath)
                    if (imageFile.exists()) {
                        try {
                            // Add a new page for each image (starting from the second image)
                            if (index > 0) {
                                pdfLayoutDocument.add(AreaBreak(AreaBreakType.NEXT_PAGE))
                            }
                            
                            val imageData = ImageDataFactory.create(imageFile.absolutePath)
                            val pdfImage = PdfImage(imageData)
                            
                            // Scale image to fit the page while maintaining aspect ratio
                            pdfImage.setAutoScale(true)
                            pdfImage.setMaxWidth(PageSize.A4.width - 40) // Add some margins
                            pdfImage.setMaxHeight(PageSize.A4.height - 40)
                            
                            // Center the image on the page
                            pdfImage.setHorizontalAlignment(HorizontalAlignment.CENTER)
                            
                            // Add the image to the document
                            pdfLayoutDocument.add(pdfImage)
                        } catch (e: Exception) {
                            Log.e("PDFGeneration", "Error adding image to PDF: ${e.message}")
                        }
                    }
                }
                
                pdfLayoutDocument.close()
                
                // Dismiss progress dialog and share the PDF
                runOnUiThread {
                    progressDialog.dismiss()
                    sharePdf(pdfFile)
                }
            } catch (e: Exception) {
                Log.e("PDFGeneration", "Error generating PDF: ${e.message}")
                runOnUiThread {
                    progressDialog.dismiss()
                    Toast.makeText(this, "Error generating PDF: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun shareAllImages() {
        if (images.isEmpty()) {
            Toast.makeText(this, "No images to share", Toast.LENGTH_SHORT).show()
            return
        }

        val imageUris = ArrayList<Uri>()
        for (image in images) {
            val imageFile = File(image.imagePath)
            if (imageFile.exists()) {
                val imageUri = FileUtils.getUriForFile(this, imageFile)
                imageUris.add(imageUri)
            }
        }

        if (imageUris.isNotEmpty()) {
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, imageUris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share all images"))
        } else {
            Toast.makeText(this, "No images found to share", Toast.LENGTH_SHORT).show()
        }
    }



    private fun editDocument() {
        // Show a dialog to rename the document
        val editText = android.widget.EditText(this)
        document?.let { doc ->
            editText.setText(doc.name)
        }

        AlertDialog.Builder(this)
            .setTitle("Rename Document")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty() && newName != document?.name) {
                    document?.let { doc ->
                        val updatedDocument = doc.copy(name = newName)
                        viewModel.updateDocument(updatedDocument)
                        supportActionBar?.title = newName
                        Toast.makeText(this, "Document renamed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddImageOptions() {
        // Inflate the custom dialog layout
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_images, null)
        
        // Find views in the dialog
        val galleryOption = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.galleryOption)
        val cameraOption = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cameraOption)
        val cancelButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.cancelButton)
        
        // Create and show the dialog
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        // Set click listeners for the options
        galleryOption.setOnClickListener {
            dialog.dismiss()
            selectImagesFromGallery()
        }
        
        cameraOption.setOnClickListener {
            dialog.dismiss()
            checkCameraPermissionAndOpen()
        }
        
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun selectImagesFromGallery() {
        selectImagesLauncher.launch("image/*")
    }

    private fun checkCameraPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            openCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun openCamera() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            // Ensure that there's a camera activity to handle the intent
            takePictureIntent.resolveActivity(packageManager)?.also {
                // Create the File where the photo should go
                val photoFile: File? = try {
                    createImageFile()
                } catch (_: Exception) {
                    // Error occurred while creating the File
                    null
                }

                // Continue only if the File was successfully created
                photoFile?.also {
                    val photoURI: Uri = FileProvider.getUriForFile(
                        this,
                        "${packageName}.fileprovider",
                        it
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    captureImageLauncher.launch(takePictureIntent)
                }
            }
        }
    }

    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_${timeStamp}_"
        val storageDir = getExternalFilesDir(null)
        val image = File.createTempFile(
            imageFileName, /* prefix */
            ".jpg", /* suffix */
            storageDir      /* directory */
        )

        // Save a file: path for use with ACTION_VIEW intents
        currentPhotoPath = image.absolutePath
        return image
    }

    private fun addImagesToDocument(imageUris: List<Uri>) {
        viewModel.addImagesToDocument(documentId, imageUris)
        Toast.makeText(this, "Added ${imageUris.size} image(s)", Toast.LENGTH_SHORT).show()
        
        // Reload just the images to show newly added images
        reloadImages()
    }
    
    private fun reloadImages() {
        lifecycleScope.launch {
            try {
                val updatedImages = viewModel.getImagesForDocumentSync(documentId)
                images.clear()
                // Sort images by order when reloading
                images.addAll(updatedImages.sortedBy { it.order })
                imageAdapter?.updateImages(images.sortedBy { it.order })
            } catch (e: Exception) {
                Log.e("DocumentDetailActivity", "Error reloading images", e)
            }
        }
    }

    private fun showDeleteImageConfirmation(image: DocumentImage) {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_delete)
            .setMessage("Are you sure you want to delete this image?")
            .setPositiveButton(R.string.yes) { _, _ ->
                deleteImage(image)
            }
            .setNegativeButton(R.string.no) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun deleteImage(image: DocumentImage) {
        // Delete the image through the ViewModel
        lifecycleScope.launch {
            viewModel.deleteImageSync(image)
        }

        // Remove the image from the list
        images.remove(image)

        // Update the UI
        imageAdapter?.updateImages(images)

        Toast.makeText(this, "Image deleted", Toast.LENGTH_SHORT).show()
    }
}