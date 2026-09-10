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
import android.view.View
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.dockeep.app.utils.AppLock
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
import com.dockeep.app.utils.ColorUtils
import android.view.LayoutInflater
import com.dockeep.app.utils.LedgerPdf
import com.dockeep.app.utils.OcrReader
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.dockeep.app.ui.dockAsLedgerSheet
import com.dockeep.app.utils.FileUtils
import com.dockeep.app.utils.MediaExport
import com.dockeep.app.ImageViewerActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*


class DocumentDetailActivity : AppCompatActivity() {

    private companion object {
        /** Pages the scanner will take in one session. */
        const val SCAN_PAGE_LIMIT = 20
    }

    /** Set after a capture so the next load can offer a name from the scan. */
    private var suggestAfterNextLoad = false

    private lateinit var viewModel: DocumentViewModel
    private lateinit var recyclerView: RecyclerView
    private lateinit var fab: View
    private lateinit var deleteFab: View
    private lateinit var backButton: View
    private lateinit var editButton: View
    private lateinit var moreButton: View
    private lateinit var actionScan: View
    private lateinit var actionPdf: View
    private lateinit var actionShare: View
    private lateinit var detailEmptyState: View
    private lateinit var docTile: TextView
    private lateinit var docTitle: TextView
    private lateinit var statScans: TextView
    private lateinit var statSize: TextView
    private lateinit var statUpdated: TextView
    private lateinit var personChip: TextView
    private lateinit var tagContainer: android.widget.LinearLayout
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

    /**
     * The ML Kit document scanner: live edge detection, perspective
     * correction and the enhance filters, returning one JPEG per page.
     */
    private val scanLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val scan = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
        val uris = scan?.pages?.map { it.imageUri }.orEmpty()
        if (uris.isNotEmpty()) {
            addImagesToDocument(uris)
        }
    }

    /** The scan handed to the editor, so its result can be applied to it. */
    private var editingBlock: DocumentImage? = null

    private val editorLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val edited = editingBlock
        editingBlock = null
        if (result.resultCode != RESULT_OK) return@registerForActivityResult

        // The editor wrote back over the same path, so the cached thumbnail is
        // stale.
        com.bumptech.glide.Glide.get(this).clearMemory()
        imageAdapter?.notifyDataSetChanged()

        lifecycleScope.launch {
            // So is the recognised text. This matters most for redaction: the
            // black boxes are burned into the pixels, but the text read before
            // the edit still holds whatever was under them, and that copy is
            // what search and "Read text" answer from. Clearing it makes the
            // indexer read the redacted image instead.
            edited?.let { viewModel.invalidateOcr(it.id) }
            document?.let { viewModel.updateDocument(it.copy(updatedAt = Date())) }
            reloadImages()
        }
    }

    /** The scan a permission prompt interrupted, resumed once it is granted. */
    private var pendingSave: DocumentImage? = null

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val queued = pendingSave
        pendingSave = null
        if (isGranted) {
            queued?.let { downloadSingleImage(it) }
        } else {
            Toast.makeText(this, R.string.ledger_save_needs_permission, Toast.LENGTH_LONG).show()
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
        backButton = findViewById(R.id.backButton)
        editButton = findViewById(R.id.editButton)
        moreButton = findViewById(R.id.moreButton)
        actionScan = findViewById(R.id.actionScan)
        actionPdf = findViewById(R.id.actionPdf)
        actionShare = findViewById(R.id.actionShare)
        detailEmptyState = findViewById(R.id.detailEmptyState)
        docTile = findViewById(R.id.docTile)
        docTitle = findViewById(R.id.docTitle)
        statScans = findViewById(R.id.statScans)
        statSize = findViewById(R.id.statSize)
        statUpdated = findViewById(R.id.statUpdated)
        personChip = findViewById(R.id.personChip)
        tagContainer = findViewById(R.id.tagContainer)
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

        backButton.setOnClickListener { finish() }
        editButton.setOnClickListener { editDocument() }
        actionScan.setOnClickListener { showAddImageOptions() }
        actionPdf.setOnClickListener { showDownloadOptions() }
        actionShare.setOnClickListener { showShareOptions() }

        // The design keeps only one overflow, and it carries the two verbs
        // that did not fit the four-up row.
        moreButton.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menu.add(0, 1, 0, getString(R.string.rename))
            popup.menu.add(0, 2, 1, getString(R.string.share_all))
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> { editDocument(); true }
                    2 -> { showShareOptions(); true }
                    else -> false
                }
            }
            popup.show()
        }
    }

    /**
     * Fills the title block and the three-figure stat strip. Size is summed
     * from the files on disk rather than stored, so it stays honest after a
     * scan is deleted outside the app.
     */
    private fun bindHeader() {
        val doc = document ?: return

        val letter = doc.name.trim().firstOrNull()?.uppercaseChar()?.toString()
            ?: getString(R.string.placeholder_default_letter)
        docTile.text = letter
        val colors = ColorUtils.getPlaceholderColorScheme(this, doc.name)
        docTile.background?.mutate()?.setColorFilter(
            colors.backgroundColor,
            android.graphics.PorterDuff.Mode.SRC_IN
        )
        docTile.setTextColor(colors.textColor)
        docTitle.text = doc.name.uppercase()

        // Only scans are counted; notes are not "scans" of anything.
        bindTags()
        statScans.text = images.count { it.isImage }.toString()
        statSize.text = formatSize(
            images.filter { it.isImage }
                .sumOf { runCatching { File(it.imagePath).length() }.getOrDefault(0L) }
        )
        statUpdated.text = SimpleDateFormat("dd MMM", Locale.getDefault()).format(doc.updatedAt)

        // The person chip only appears for documents filed under someone.
        val personId = doc.personId
        if (personId == null) {
            personChip.visibility = View.GONE
        } else {
            lifecycleScope.launch {
                val person = viewModel.getPersonByIdSync(personId)
                if (person == null) {
                    personChip.visibility = View.GONE
                } else {
                    personChip.visibility = View.VISIBLE
                    personChip.text = person.name
                }
            }
        }
    }

    /**
     * Draws the tag row: one box per tag, then the add affordance.
     *
     * Tapping a tag offers to remove it; tapping "+ Tag" opens a sheet that
     * both accepts a new name and lists the tags already in use, so the same
     * label does not get typed three slightly different ways.
     */
    private fun bindTags() {
        val doc = document ?: return
        lifecycleScope.launch {
            val tags = viewModel.getTagsForDocumentSync(doc.id)
            tagContainer.removeAllViews()

            val inflater = LayoutInflater.from(this@DocumentDetailActivity)
            for (tag in tags) {
                val chip = inflater.inflate(
                    R.layout.item_ledger_tag, tagContainer, false
                ) as TextView
                chip.text = tag.name
                chip.setOnClickListener { confirmRemoveTag(tag) }
                tagContainer.addView(chip)
            }

            val add = inflater.inflate(
                R.layout.item_ledger_tag, tagContainer, false
            ) as TextView
            add.setText(R.string.ledger_add_tag)
            add.setTextColor(
                ContextCompat.getColor(this@DocumentDetailActivity, R.color.ledger_accent_700)
            )
            add.setOnClickListener { showAddTagSheet() }
            tagContainer.addView(add)
        }
    }

    private fun confirmRemoveTag(tag: com.dockeep.app.database.Tag) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.ledger_tag_remove, tag.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                val doc = document ?: return@setPositiveButton
                lifecycleScope.launch {
                    viewModel.removeTag(doc.id, tag.id)
                    bindTags()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAddTagSheet() {
        val doc = document ?: return
        val view = layoutInflater.inflate(R.layout.dialog_add_tag, null)
        val field = view.findViewById<EditText>(R.id.tagField)
        val existing = view.findViewById<android.widget.LinearLayout>(R.id.existingTags)

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()
            .dockAsLedgerSheet()

        // Offer what is already in use, so labels stay consistent.
        lifecycleScope.launch {
            val all = viewModel.getAllTagsSync()
            val inflater = LayoutInflater.from(this@DocumentDetailActivity)
            for (tag in all) {
                val chip = inflater.inflate(R.layout.item_ledger_tag, existing, false) as TextView
                chip.text = tag.name
                chip.setOnClickListener { field.setText(tag.name) }
                existing.addView(chip)
            }
        }

        view.findViewById<View>(R.id.sheetClose).setOnClickListener { dialog.dismiss() }
        view.findViewById<View>(R.id.sheetSave).setOnClickListener {
            val name = field.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, R.string.ledger_tag_hint, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                viewModel.addTag(doc.id, name)
                bindTags()
            }
            dialog.dismiss()
        }
        dialog.show()
    }

    /**
     * Reads the first scan and offers its title as the document's name.
     *
     * Only offered while the document still carries the generic name it was
     * created with, and only once there is something to read.
     */
    private fun maybeSuggestNameFromScan() {
        val doc = document ?: return
        val firstScan = images.firstOrNull { it.isImage } ?: return

        lifecycleScope.launch {
            val text = firstScan.ocrText ?: viewModel.readScanText(firstScan) ?: return@launch
            val suggestion = OcrReader.suggestName(text) ?: return@launch
            if (suggestion.equals(doc.name, ignoreCase = true)) return@launch
            if (isFinishing || isDestroyed) return@launch

            AlertDialog.Builder(this@DocumentDetailActivity)
                .setTitle(R.string.ledger_rename_suggest_title)
                .setMessage(getString(R.string.ledger_rename_suggest_body, suggestion))
                .setPositiveButton(R.string.rename) { _, _ ->
                    lifecycleScope.launch {
                        viewModel.updateDocument(doc.copy(name = suggestion, updatedAt = Date()))
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    /**
     * Shows what OCR read off a scan.
     *
     * The text is already cached on the block once it has been indexed; if the
     * backlog has not reached this page yet it is read on the spot, so the
     * action never simply reports "nothing yet".
     */
    private fun showScanTextSheet(block: DocumentImage) {
        val view = layoutInflater.inflate(R.layout.dialog_scan_text, null)
        val textView = view.findViewById<TextView>(R.id.ocrText)
        val spinner = view.findViewById<View>(R.id.ocrProgress)
        val copyButton = view.findViewById<View>(R.id.ocrCopy)
        val saveButton = view.findViewById<View>(R.id.ocrSaveNote)

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()
            .dockAsLedgerSheet()

        view.findViewById<View>(R.id.sheetClose).setOnClickListener { dialog.dismiss() }
        dialog.show()

        lifecycleScope.launch {
            val text = block.ocrText ?: viewModel.readScanText(block)
            spinner.visibility = View.GONE

            val body = text?.trim().orEmpty()
            if (body.isEmpty()) {
                textView.setText(R.string.ledger_ocr_empty)
                copyButton.isEnabled = false
                saveButton.isEnabled = false
                copyButton.alpha = 0.4f
                saveButton.alpha = 0.4f
                return@launch
            }

            textView.text = body

            copyButton.setOnClickListener {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(
                    android.content.ClipData.newPlainText(document?.name ?: "DocKeep", body)
                )
                Toast.makeText(
                    this@DocumentDetailActivity,
                    R.string.ledger_ocr_copied,
                    Toast.LENGTH_SHORT
                ).show()
                dialog.dismiss()
            }

            saveButton.setOnClickListener {
                // Keeping it as a note makes the text part of the document
                // rather than a transient read: it exports, it prints, it
                // stays searchable even if the scan is later deleted.
                saveTextBlock(null, body)
                Toast.makeText(
                    this@DocumentDetailActivity,
                    R.string.ledger_ocr_saved,
                    Toast.LENGTH_SHORT
                ).show()
                dialog.dismiss()
            }
        }
    }

    /** Bytes as the design writes them: "6.4 MB", "812 KB". */
    private fun formatSize(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1048576.0)
        bytes >= 1024L -> String.format(Locale.getDefault(), "%d KB", bytes / 1024)
        else -> "$bytes B"
    }

    private fun loadDocument() {
        // Load document details
        viewModel.getDocumentById(documentId).observe(this) { doc ->
            if (doc != null) {
                document = doc
                bindHeader()
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

        bindHeader()
        detailEmptyState.visibility = if (images.isEmpty()) View.VISIBLE else View.GONE

        if (suggestAfterNextLoad && images.any { it.isImage }) {
            suggestAfterNextLoad = false
            maybeSuggestNameFromScan()
        }
        
        // Only create a new adapter if we don't have one or if the images have changed significantly
        if (imageAdapter == null) {
            imageAdapter = ImageAdapter(
                sortedImages.toMutableList(),
                onImageClick = { image ->
                    // A note has nothing to open full screen, so tapping it
                    // edits it instead.
                    if (image.isText) showTextBlockDialog(image) else viewFullImage(image)
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
                onEditClick = { image ->
                    if (image.isText) showTextBlockDialog(image) else editScan(image)
                },
                onPdfClick = { image ->
                    exportBlockAsPdf(image)
                },
                onSharePdfClick = { image ->
                    shareBlockAsPdf(image)
                },
                onReadTextClick = { image ->
                    showScanTextSheet(image)
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
            // Update existing adapter with new data. The touch helper stays
            // attached from the first pass; re-attaching would stack another.
            imageAdapter?.updateImages(sortedImages)
        }
    }

    /**
     * Attaches drag-to-reorder, exactly once.
     *
     * This used to run on every updateUI — which fires on every data change —
     * building a fresh ItemTouchHelper each time and attaching it without
     * detaching the last. After a few edits several helpers were all reading
     * the same touch stream, which is why dragging a block behaved erratically
     * or stopped responding.
     */
    private fun setupDragAndDrop() {
        val adapter = imageAdapter ?: return
        if (itemTouchHelper != null) return

        itemTouchHelper = ItemTouchHelper(ImageItemTouchHelperCallback(adapter)).also {
            it.attachToRecyclerView(recyclerView)
        }
    }

    private fun saveImageOrder(images: List<DocumentImage>) {
        // Save the new order to the database
        if (images.isNotEmpty()) {
            viewModel.updateImageOrder(images)
        }
    }

    private fun showDownloadOptions() {
        // Inflate the custom dialog layout
        val dialogView = layoutInflater.inflate(R.layout.dialog_download_options, null)
        
        // Find views in the dialog
        val pdfOption = dialogView.findViewById<View>(R.id.pdfOption)
        val galleryOption = dialogView.findViewById<View>(R.id.galleryOption)
        val cancelButton = dialogView.findViewById<View>(R.id.cancelButton)
        
        // Create and show the dialog
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
            .dockAsLedgerSheet()
        
        // Apply fade-in animation when the dialog is shown
        dialog.setOnShowListener {
            val animation = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.dialog_fade_in)
            dialogView.startAnimation(animation)
        }
        
        // Set click listeners for the options
        pdfOption.setOnClickListener {
            dialog.dismiss()
            showPdfBlockPicker(PdfIntent.SAVE)
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


    // ══ Scanning ════════════════════════════════════════════════════════

    /**
     * Opens the document scanner. Falls back to a plain camera capture when
     * the scanner module cannot be provided — an older Play Services, or a
     * device without it at all — so capture always works.
     */
    private fun startDocumentScan() {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(SCAN_PAGE_LIMIT)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()

        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(this)
            .addOnSuccessListener { sender ->
                runCatching {
                    scanLauncher.launch(IntentSenderRequest.Builder(sender).build())
                }.onFailure {
                    Log.e("DocumentDetail", "Could not start the scanner", it)
                    checkCameraPermissionAndOpen()
                }
            }
            .addOnFailureListener { e ->
                Log.e("DocumentDetail", "Scanner unavailable, using the camera", e)
                Toast.makeText(this, R.string.ledger_scanner_unavailable, Toast.LENGTH_LONG).show()
                checkCameraPermissionAndOpen()
            }
    }

    // ══ Editing a scan ══════════════════════════════════════════════════

    /**
     * Opens the full editor for a scan already in the document: rotate, crop,
     * document filter and brightness / contrast.
     */
    private fun editScan(image: DocumentImage) {
        val file = File(image.imagePath)
        if (!file.exists()) {
            Toast.makeText(this, R.string.ledger_scan_missing, Toast.LENGTH_SHORT).show()
            return
        }
        editingBlock = image
        editorLauncher.launch(ScanEditorActivity.intent(this, image.imagePath))
    }

    // ══ Notes ═══════════════════════════════════════════════════════════

    /**
     * Adds or edits a text block. Notes sit in the same ordered sequence as
     * scans, so a note can explain the scan above it.
     */
    private fun showTextBlockDialog(existing: DocumentImage?) {
        val view = layoutInflater.inflate(R.layout.dialog_text_block, null)
        val field = view.findViewById<EditText>(R.id.textBlockField)
        field.setText(existing?.text.orEmpty())
        field.setSelection(field.text.length)
        field.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()
            .dockAsLedgerSheet()

        view.findViewById<View>(R.id.sheetClose).setOnClickListener { dialog.dismiss() }
        view.findViewById<View>(R.id.sheetSave).setOnClickListener {
            val body = field.text.toString().trim()
            if (body.isEmpty()) {
                Toast.makeText(this, R.string.ledger_note_empty, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            saveTextBlock(existing, body)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun saveTextBlock(existing: DocumentImage?, body: String) {
        lifecycleScope.launch {
            if (existing == null) {
                viewModel.insertImageSync(
                    DocumentImage(
                        documentId = documentId,
                        imagePath = "",
                        // Append after the last block. images.size would
                        // collide: the repository numbers scans from 1, so a
                        // three-scan document already holds order 3.
                        order = (images.maxOfOrNull { it.order } ?: 0) + 1,
                        blockType = DocumentImage.BLOCK_TEXT,
                        text = body
                    )
                )
            } else {
                viewModel.updateImage(existing.copy(text = body))
            }
            document?.let { viewModel.updateDocument(it.copy(updatedAt = Date())) }
        }
    }

    // ══ Single scan to PDF ══════════════════════════════════════════════


    // ══ PDF page picker ═════════════════════════════════════════════════

    /** What the picked pages are wanted for. */
    private enum class PdfIntent { SAVE, SHARE }

    /**
     * Asks which blocks belong in the PDF, then builds it.
     *
     * Scans start selected and notes do not, because a note is a private
     * annotation and sending one unasked would leak it — but every block is
     * individually switchable, which the old fixed with/without-notes pair
     * could not express.
     */
    private fun showPdfBlockPicker(intent: PdfIntent) {
        if (images.isEmpty()) {
            Toast.makeText(this, R.string.ledger_pdf_no_scans, Toast.LENGTH_SHORT).show()
            return
        }

        val view = layoutInflater.inflate(R.layout.dialog_pdf_blocks, null)
        val list = view.findViewById<android.widget.LinearLayout>(R.id.pdfBlockList)
        val confirm = view.findViewById<View>(R.id.pdfConfirm)
        val confirmLabel = view.findViewById<TextView>(R.id.pdfConfirmLabel)

        val ordered = images.sortedBy { it.order }
        val chosen = ordered.filter { it.isImage }.map { it.id }.toMutableSet()
        val rows = mutableMapOf<Long, View>()

        fun refresh() {
            for ((id, row) in rows) {
                row.findViewById<View>(R.id.blockCheck).isSelected = id in chosen
            }
            confirmLabel.text = when {
                chosen.isEmpty() -> getString(R.string.ledger_pdf_pick_nothing)
                intent == PdfIntent.SHARE ->
                    getString(R.string.ledger_pdf_share_n, chosen.size)
                else -> getString(R.string.ledger_pdf_save_n, chosen.size)
            }
            confirm.alpha = if (chosen.isEmpty()) 0.5f else 1f
        }

        ordered.forEachIndexed { index, block ->
            val row = layoutInflater.inflate(R.layout.item_ledger_pdf_block, list, false)
            row.findViewById<TextView>(R.id.blockOrdinal).text =
                String.format(Locale.getDefault(), "%03d", index + 1)
            row.findViewById<TextView>(R.id.blockLabel).text = if (block.isText) {
                // The note's own opening words identify it better than "Note".
                block.text?.trim()?.take(40).takeUnless { it.isNullOrBlank() }
                    ?: getString(R.string.ledger_note_title)
            } else {
                getString(R.string.ledger_pdf_scan_n, index + 1)
            }
            row.setOnClickListener {
                if (!chosen.add(block.id)) chosen.remove(block.id)
                refresh()
            }
            rows[block.id] = row
            list.addView(row)
        }

        view.findViewById<View>(R.id.pdfPickAll).setOnClickListener {
            chosen.clear()
            chosen.addAll(ordered.map { it.id })
            refresh()
        }
        view.findViewById<View>(R.id.pdfPickScans).setOnClickListener {
            chosen.clear()
            chosen.addAll(ordered.filter { it.isImage }.map { it.id })
            refresh()
        }
        view.findViewById<View>(R.id.pdfPickNone).setOnClickListener {
            chosen.clear()
            refresh()
        }

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()
            .dockAsLedgerSheet()

        view.findViewById<View>(R.id.sheetClose).setOnClickListener { dialog.dismiss() }

        confirm.setOnClickListener {
            if (chosen.isEmpty()) {
                Toast.makeText(this, R.string.ledger_pdf_pick_nothing, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Keep the document's own order, not the order they were ticked.
            val blocks = ordered.filter { it.id in chosen }
            dialog.dismiss()
            buildPdf(blocks, intent)
        }

        refresh()
        dialog.show()
    }

    /** Writes the chosen blocks out, then saves or shares the result. */
    private fun buildPdf(blocks: List<DocumentImage>, intent: PdfIntent) {
        val name = document?.name ?: "Document"
        showPdfProgress()

        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    val stamp =
                        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    LedgerPdf.write(
                        this@DocumentDetailActivity,
                        blocks,
                        name,
                        File(pdfOutputDir(), "${name}_$stamp.pdf")
                    )
                }.getOrNull()
            }

            hidePdfProgress()
            if (isFinishing || isDestroyed) return@launch

            if (file == null) {
                Toast.makeText(
                    this@DocumentDetailActivity,
                    R.string.ledger_pdf_failed,
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            when (intent) {
                PdfIntent.SAVE -> savePdfToFileManager(file)
                PdfIntent.SHARE -> sharePdf(file)
            }
        }
    }

    private var pdfProgress: AlertDialog? = null

    private fun showPdfProgress() {
        if (isFinishing || isDestroyed) return
        hidePdfProgress()
        pdfProgress = AlertDialog.Builder(this)
            .setView(R.layout.dialog_progress)
            .setCancelable(false)
            .create()
        runCatching { pdfProgress?.show() }
    }

    private fun hidePdfProgress() {
        val dialog = pdfProgress ?: return
        pdfProgress = null
        runCatching { if (dialog.isShowing) dialog.dismiss() }
    }

    /**
     * Builds a PDF of one block and shares it, rather than filing it away.
     *
     * The common case for a single page: send this one scan, not the whole
     * document.
     */
    private fun shareBlockAsPdf(block: DocumentImage) {
        val name = document?.name ?: "Document"
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) {
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val target = File(pdfOutputDir(), "${name}_${stamp}.pdf")
                LedgerPdf.writeSingle(this@DocumentDetailActivity, block, name, target)
            }
            if (file == null) {
                Toast.makeText(
                    this@DocumentDetailActivity,
                    R.string.ledger_pdf_failed,
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                sharePdf(file)
            }
        }
    }

    /** Exports one block on its own, as the design's per-scan action. */
    private fun exportBlockAsPdf(block: DocumentImage) {
        val name = document?.name ?: "Document"
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) {
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val target = File(pdfOutputDir(), "${name}_${stamp}.pdf")
                LedgerPdf.writeSingle(this@DocumentDetailActivity, block, name, target)
            }
            if (file == null) {
                Toast.makeText(
                    this@DocumentDetailActivity,
                    R.string.ledger_pdf_failed,
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                savePdfToFileManager(file)
            }
        }
    }

    /** Where generated PDFs are written before being published or shared. */
    private fun pdfOutputDir(): File {
        val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getExternalFilesDir(null)
        } else {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        } ?: filesDir
        if (!dir.exists()) dir.mkdirs()
        return dir
    }


    /**
     * Copies a generated PDF into Documents/DocKeep.
     *
     * The temporary file it was built into is removed afterwards: it lives in
     * the app's own external directory and would otherwise be a second copy of
     * every PDF the user ever saved.
     */
    private fun savePdfToFileManager(pdfFile: File) {
        val name = pdfFile.nameWithoutExtension
        lifecycleScope.launch {
            val uri = MediaExport.savePdf(this@DocumentDetailActivity, pdfFile, name)
            runCatching { pdfFile.delete() }

            if (isFinishing || isDestroyed) return@launch
            Toast.makeText(
                this@DocumentDetailActivity,
                if (uri != null) R.string.ledger_pdf_saved else R.string.ledger_pdf_save_failed,
                Toast.LENGTH_SHORT
            ).show()
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

    /**
     * Saves every scan in the document to the gallery.
     *
     * Notes are skipped — they have no image behind them and used to be
     * counted as failures, so a document with two notes always reported that
     * two images had failed to save.
     */
    private fun saveAllImagesToGallery() {
        val scans = images.sortedBy { it.order }.filter { it.isImage }
        if (scans.isEmpty()) {
            Toast.makeText(this, R.string.ledger_pdf_no_scans, Toast.LENGTH_SHORT).show()
            return
        }

        val name = document?.name ?: "DocKeep"
        showPdfProgress()

        lifecycleScope.launch {
            var saved = 0
            scans.forEachIndexed { index, block ->
                val file = File(block.imagePath)
                if (file.exists() &&
                    MediaExport.saveImage(this@DocumentDetailActivity, file, "$name ${index + 1}") != null
                ) {
                    saved++
                }
            }

            hidePdfProgress()
            if (isFinishing || isDestroyed) return@launch

            val failed = scans.size - saved
            Toast.makeText(
                this@DocumentDetailActivity,
                if (failed == 0) {
                    getString(R.string.ledger_saved_n_to_gallery, saved)
                } else {
                    getString(R.string.ledger_saved_n_with_failures, saved, failed)
                },
                Toast.LENGTH_LONG
            ).show()
        }
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
        // The viewer pages through scans only, so notes are filtered out here
        // and the starting position is taken within that filtered list.
        val scans = images.filter { it.isImage }
        val position = scans.indexOfFirst { it.id == image.id }
        if (position != -1) {
            val intent = Intent(this, ImageViewerActivity::class.java).apply {
                putExtra(ImageViewerActivity.EXTRA_IMAGES, ArrayList(scans))
                putExtra(ImageViewerActivity.EXTRA_INITIAL_POSITION, position)
                putExtra(ImageViewerActivity.EXTRA_DOCUMENT_NAME, document?.name ?: "Document")
            }
            startActivity(intent)
        } else {
            Toast.makeText(this, R.string.ledger_scan_missing, Toast.LENGTH_SHORT).show()
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

    /**
     * Copies one scan out to the device gallery.
     *
     * The name it lands under is the document's, plus its page number: the
     * stored file is called "12-20260910-441.jpg", which means nothing once it
     * is sitting among the user's photos.
     */
    private fun downloadSingleImage(image: DocumentImage) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Remember what was being saved. Previously the grant callback did
            // nothing at all, so the user allowed the permission and then had
            // to find the menu and tap Save a second time.
            pendingSave = image
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }

        val file = File(image.imagePath)
        if (!file.exists()) {
            Toast.makeText(this, R.string.ledger_scan_missing, Toast.LENGTH_SHORT).show()
            return
        }

        val page = images.sortedBy { it.order }.indexOfFirst { it.id == image.id } + 1
        val name = document?.name ?: "DocKeep"

        lifecycleScope.launch {
            val uri = MediaExport.saveImage(
                this@DocumentDetailActivity,
                file,
                if (page > 0) "$name $page" else name
            )
            if (isFinishing || isDestroyed) return@launch
            Toast.makeText(
                this@DocumentDetailActivity,
                if (uri != null) R.string.ledger_saved_to_gallery else R.string.ledger_save_failed,
                Toast.LENGTH_SHORT
            ).show()
        }
    }


    private fun showShareOptions() {
        // Inflate the custom dialog layout
        val dialogView = layoutInflater.inflate(R.layout.dialog_share_options, null)
        
        // Find views in the dialog
        val sharePdfOption = dialogView.findViewById<View>(R.id.sharePdfOption)
        val shareImagesOption = dialogView.findViewById<View>(R.id.shareImagesOption)

        // The picker covers the with/without-notes choice and everything
        // between it, so the second PDF row is redundant.
        dialogView.findViewById<View>(R.id.sharePdfNotesOption).visibility = View.GONE
        dialogView.findViewById<View>(R.id.sharePdfNotesRule).visibility = View.GONE
        val cancelButton = dialogView.findViewById<View>(R.id.cancelButton)
        
        // Create and show the dialog
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
            .dockAsLedgerSheet()
        
        // Apply fade-in animation when the dialog is shown
        dialog.setOnShowListener {
            val animation = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.dialog_fade_in)
            dialogView.startAnimation(animation)
        }
        
        // Set click listeners for the options
        sharePdfOption.setOnClickListener {
            dialog.dismiss()
            showPdfBlockPicker(PdfIntent.SHARE)
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
        val galleryOption = dialogView.findViewById<View>(R.id.galleryOption)
        val cameraOption = dialogView.findViewById<View>(R.id.cameraOption)
        val cancelButton = dialogView.findViewById<View>(R.id.cancelButton)
        
        // Create and show the dialog
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
            .dockAsLedgerSheet()
        
        // Set click listeners for the options
        galleryOption.setOnClickListener {
            dialog.dismiss()
            selectImagesFromGallery()
        }
        
        // The camera row now opens the real scanner rather than a plain
        // capture: edge detection and perspective correction are the point.
        cameraOption.setOnClickListener {
            dialog.dismiss()
            startDocumentScan()
        }

        dialogView.findViewById<View>(R.id.noteOption).setOnClickListener {
            dialog.dismiss()
            showTextBlockDialog(null)
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
        // Read the new page so search can find it, and offer its title.
        suggestAfterNextLoad = true
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

    override fun onResume() {
        super.onResume()
        // Coming back from recents can land directly on this screen, which
        // would show its contents without the vault ever being unlocked.
        // Finishing returns to the gated home screen, which does the asking.
        if (AppLock.shouldChallenge(this)) finish()
    }

}