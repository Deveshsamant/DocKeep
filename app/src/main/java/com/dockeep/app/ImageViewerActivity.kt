package com.dockeep.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.dockeep.app.adapter.ImageViewerAdapter
import com.dockeep.app.database.DocumentImage
import com.dockeep.app.ui.dockAsLedgerSheet
import com.dockeep.app.utils.AppLock
import com.dockeep.app.utils.FileUtils
import com.dockeep.app.utils.MediaExport
import com.dockeep.app.utils.LedgerPdf
import com.dockeep.app.viewmodel.DocumentViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-screen scan viewer.
 *
 * This is where a scan is actually looked at closely, so it is where the need
 * to straighten it, black out a number, or pull the text off it becomes
 * obvious. The actions along the foot all act on whichever page is showing.
 *
 * Chrome stays dark on both themes: a scan is being read here, and everything
 * that is not the scan should recede.
 */
class ImageViewerActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var documentNameTextView: TextView
    private lateinit var imageCounterTextView: TextView
    private lateinit var closeButton: ImageButton
    private lateinit var adapter: ImageViewerAdapter
    private lateinit var viewModel: DocumentViewModel

    /** Mutable so a deleted page can be dropped without leaving the screen. */
    private var images: MutableList<DocumentImage> = mutableListOf()

    private var initialPosition: Int = 0
    private lateinit var documentName: String

    companion object {
        const val EXTRA_IMAGES = "extra_images"
        const val EXTRA_INITIAL_POSITION = "extra_initial_position"
        const val EXTRA_DOCUMENT_NAME = "extra_document_name"
    }

    /** The page handed to the editor, so its result applies to the right one. */
    private var editingBlock: DocumentImage? = null

    private val editorLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val edited = editingBlock
        editingBlock = null
        if (result.resultCode != RESULT_OK) return@registerForActivityResult

        // The editor rewrote the file in place, so both the cached bitmap and
        // the text recognised from the old pixels are stale. The second matters
        // for redaction: without clearing it, search would still answer on
        // whatever was under the black boxes.
        com.bumptech.glide.Glide.get(this).clearMemory()
        adapter.notifyDataSetChanged()

        lifecycleScope.launch {
            edited?.let { viewModel.invalidateOcr(it.id) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_viewer)

        // Hide status bar for fullscreen experience
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        @Suppress("UNCHECKED_CAST")
        val incoming = intent.getSerializableExtra(EXTRA_IMAGES) as? List<DocumentImage>
        images = incoming.orEmpty().toMutableList()
        initialPosition = intent.getIntExtra(EXTRA_INITIAL_POSITION, 0)
        documentName = intent.getStringExtra(EXTRA_DOCUMENT_NAME) ?: "Document"

        if (images.isEmpty()) {
            finish()
            return
        }

        viewModel = ViewModelProvider(this, DocumentViewModel.Factory(application))
            .get(DocumentViewModel::class.java)

        initViews()
        setupViewPager()
        updateCounter()
    }

    private fun initViews() {
        viewPager = findViewById(R.id.viewPager)
        documentNameTextView = findViewById(R.id.documentName)
        imageCounterTextView = findViewById(R.id.imageCounter)
        closeButton = findViewById(R.id.closeButton)

        documentNameTextView.text = documentName

        closeButton.setOnClickListener { finish() }

        findViewById<View>(R.id.viewerEdit).setOnClickListener { editCurrent() }
        findViewById<View>(R.id.viewerShare).setOnClickListener { shareCurrent() }
        findViewById<View>(R.id.viewerSave).setOnClickListener { saveCurrent() }
        findViewById<View>(R.id.viewerPdf).setOnClickListener { pdfCurrent() }
        findViewById<View>(R.id.viewerText).setOnClickListener { readTextOfCurrent() }
    }

    private fun setupViewPager() {
        adapter = ImageViewerAdapter(images)
        viewPager.adapter = adapter
        viewPager.currentItem = initialPosition

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateCounter()
            }
        })
    }

    private fun updateCounter() {
        val currentPosition = viewPager.currentItem + 1
        imageCounterTextView.text = "$currentPosition/${images.size}"
    }

    /** The page currently on screen, or null if the list has emptied. */
    private fun currentBlock(): DocumentImage? = images.getOrNull(viewPager.currentItem)

    // ══ Actions ═════════════════════════════════════════════════════════

    private fun editCurrent() {
        val block = currentBlock() ?: return
        if (!File(block.imagePath).exists()) {
            Toast.makeText(this, R.string.ledger_scan_missing, Toast.LENGTH_SHORT).show()
            return
        }
        editingBlock = block
        editorLauncher.launch(ScanEditorActivity.intent(this, block.imagePath))
    }

    private fun shareCurrent() {
        val block = currentBlock() ?: return
        val file = File(block.imagePath)
        if (!file.exists()) {
            Toast.makeText(this, R.string.ledger_scan_missing, Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, FileUtils.getUriForFile(this@ImageViewerActivity, file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share)))
    }

    /**
     * Copies the page on screen into the device gallery.
     *
     * Named after the document and its page, not the stored filename, which is
     * an id and a timestamp and means nothing among the user's own photos.
     */
    private fun saveCurrent() {
        val block = currentBlock() ?: return
        val file = File(block.imagePath)
        if (!file.exists()) {
            Toast.makeText(this, R.string.ledger_scan_missing, Toast.LENGTH_SHORT).show()
            return
        }

        val page = viewPager.currentItem + 1
        lifecycleScope.launch {
            val uri = MediaExport.saveImage(
                this@ImageViewerActivity,
                file,
                "$documentName $page"
            )
            if (isFinishing || isDestroyed) return@launch
            Toast.makeText(
                this@ImageViewerActivity,
                if (uri != null) R.string.ledger_saved_to_gallery else R.string.ledger_save_failed,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun pdfCurrent() {
        val block = currentBlock() ?: return
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) {
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val dir = getExternalFilesDir(null) ?: filesDir
                if (!dir.exists()) dir.mkdirs()
                LedgerPdf.writeSingle(
                    this@ImageViewerActivity,
                    block,
                    documentName,
                    File(dir, "${documentName}_$stamp.pdf")
                )
            }
            if (file == null) {
                Toast.makeText(
                    this@ImageViewerActivity,
                    R.string.ledger_pdf_failed,
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(
                    Intent.EXTRA_STREAM,
                    FileUtils.getUriForFile(this@ImageViewerActivity, file)
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.ledger_share_this_pdf)))
        }
    }

    /** Shows what OCR read off the page on screen, and offers to copy it. */
    private fun readTextOfCurrent() {
        val block = currentBlock() ?: return
        val view = layoutInflater.inflate(R.layout.dialog_scan_text, null)
        val textView = view.findViewById<TextView>(R.id.ocrText)
        val spinner = view.findViewById<View>(R.id.ocrProgress)
        val copyButton = view.findViewById<View>(R.id.ocrCopy)
        val saveButton = view.findViewById<View>(R.id.ocrSaveNote)

        // Keeping it as a note belongs to the document screen, which owns the
        // block list; from here the useful action is copying it.
        saveButton.visibility = View.GONE

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
                copyButton.alpha = 0.4f
                return@launch
            }

            textView.text = body
            copyButton.setOnClickListener {
                val clipboard =
                    getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(
                    android.content.ClipData.newPlainText(documentName, body)
                )
                Toast.makeText(
                    this@ImageViewerActivity,
                    R.string.ledger_ocr_copied,
                    Toast.LENGTH_SHORT
                ).show()
                dialog.dismiss()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Coming back from recents can land directly on this screen, which
        // would show its contents without the vault ever being unlocked.
        // Finishing returns to the gated home screen, which does the asking.
        if (AppLock.shouldChallenge(this)) finish()
    }
}
