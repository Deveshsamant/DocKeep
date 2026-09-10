package com.dockeep.app

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.View.VISIBLE
import android.view.inputmethod.EditorInfo
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.dockeep.app.ui.LedgerGridDecoration
import com.dockeep.app.ui.LedgerNav
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import java.util.Date
import java.util.Locale
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.dockeep.app.adapter.DocumentAutocompleteAdapter
import com.dockeep.app.adapter.DocumentItemTouchHelperCallback
import com.dockeep.app.adapter.DragDropDocumentAdapter
import com.dockeep.app.database.Document
import com.dockeep.app.database.DocumentImage
import com.dockeep.app.database.Person
import com.dockeep.app.utils.ColorUtils
import com.dockeep.app.ui.dockAsLedgerSheet
import com.dockeep.app.utils.FileUtils
import com.dockeep.app.utils.AppLock
import com.dockeep.app.utils.OnboardingManager
import com.dockeep.app.viewmodel.DocumentViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class MainActivity : AppCompatActivity() {
    companion object {
        private const val PREFS_NAME = "app_prefs"
        private const val THEME_PREF = "is_dark_theme"
    }
    
    private lateinit var viewModel: DocumentViewModel
    private lateinit var recyclerView: RecyclerView
    private lateinit var fab: View
    private lateinit var fabLabel: TextView
    private lateinit var fabIcon: ImageView
    private lateinit var menuButton: ImageView
    private lateinit var searchButton: ImageView
    private lateinit var searchEditText: EditText
    private lateinit var searchInputLayout: View
    private lateinit var searchClearButton: ImageView
    private lateinit var emptyStateLayout: View
    private lateinit var sidebarLayout: LinearLayout
    private lateinit var sidebarOverlay: View
    private lateinit var drawerCloseButton: ImageView
    private lateinit var headerSubtitle: TextView
    private lateinit var userNameDisplay: TextView
    private lateinit var userNameDisplayContainer: LinearLayout
    private lateinit var userNameIcon: ImageView
    private lateinit var userNameLetter: TextView
    private lateinit var themeOption: LinearLayout
    private lateinit var themeIcon: ImageView
    private lateinit var themeText: TextView
    private lateinit var exportOption: LinearLayout
    private lateinit var exportSizeText: TextView
    private lateinit var importOption: LinearLayout
    private lateinit var lockOption: LinearLayout
    private lateinit var lockSwitch: androidx.appcompat.widget.SwitchCompat
    private lateinit var feedbackOption: LinearLayout
    private lateinit var versionText: TextView

    /** Hides the grid behind the unlock prompt so it cannot be read past it. */
    private lateinit var lockCurtain: View
    private lateinit var lockRetry: View

    /**
     * Built in onCreate, not at prompt time: androidx keeps the prompt in a
     * retained fragment, and constructing it here is what lets a result
     * survive the activity being recreated behind the system PIN screen.
     */
    private var biometricPrompt: androidx.biometric.BiometricPrompt? = null

    /**
     * True while a prompt is on screen. An activity field on purpose — the
     * previous singleton flag outlived the activity and wedged the lock.
     */
    private var promptShowing = false

    // Bulk selection
    private lateinit var selectionBar: View
    private lateinit var bulkShare: View
    private lateinit var bulkMove: View
    private lateinit var bulkDelete: View
    private var itemTouchHelper: ItemTouchHelper? = null

    // Bottom navigation — the design moved navigation out of the drawer.
    private lateinit var navDocs: View
    private lateinit var navPeople: View
    private lateinit var navYou: View

    // Filter tabs across the top of the grid.
    private lateinit var tabViews: List<LedgerTab>

    /** One entry in the filter strip: its row, label and accent underline. */
    private data class LedgerTab(
        val order: DocumentSort,
        val root: View,
        val label: TextView,
        val underline: View
    )

    /** How the grid is currently ordered. */
    private enum class DocumentSort { MANUAL, RECENT, PERSON, ALPHA }

    private var currentSort = DocumentSort.MANUAL

    /** Guards against re-subscribing to the document list on every reload. */
    private var documentsObserved = false

    /** The live search query, held so the next keystroke can detach it. */
    private var searchQuery:
        Pair<LiveData<List<Document>>, Observer<List<Document>>>? = null

    /** Per-document image observers, tracked so they can be detached. */
    private val imageObservers =
        mutableListOf<Pair<LiveData<List<DocumentImage>>, Observer<List<DocumentImage>>>>()
    
    // Add missing variables for export functionality
    private var isExportInProgress = false
    // Add variable for import functionality
    private var isImportingPersonalOnly = false

    private var isDarkTheme = false
    private var documents: MutableList<Document> = mutableListOf()
    private val imageMap = mutableMapOf<Long, String?>()
    private val imageCountMap = mutableMapOf<Long, Int>()
    private var dragDropAdapter: DragDropDocumentAdapter? = null
    private var progressDialog: AlertDialog? = null

    private val documentDetailLauncher = registerForActivityResult<Intent, ActivityResult>(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Reload documents to reflect any changes
            loadDocuments()
        }
    }

    private var isSidebarOpen = false

    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerView)
        fab = findViewById(R.id.fab)
        fabLabel = findViewById(R.id.fabLabel)
        fabIcon = findViewById(R.id.fabIcon)
        menuButton = findViewById(R.id.menuButton)
        searchButton = findViewById(R.id.searchButton)
        searchEditText = findViewById(R.id.searchEditText)
        searchInputLayout = findViewById(R.id.searchInputLayout)
        searchClearButton = findViewById(R.id.searchClearButton)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)
        sidebarLayout = findViewById(R.id.sidebarLayout)
        sidebarOverlay = findViewById(R.id.sidebarOverlay)
        drawerCloseButton = findViewById(R.id.drawerCloseButton)
        headerSubtitle = findViewById(R.id.headerSubtitle)
        userNameDisplay = findViewById(R.id.userNameDisplay)
        userNameDisplayContainer = findViewById(R.id.userNameDisplayContainer)
        userNameIcon = findViewById(R.id.userNameIcon)
        userNameLetter = findViewById(R.id.userNameLetter)
        themeOption = findViewById(R.id.themeOption)
        themeIcon = findViewById(R.id.themeIcon)
        themeText = findViewById(R.id.themeText)
        exportOption = findViewById(R.id.exportOption)
        exportSizeText = findViewById(R.id.exportSizeText)
        importOption = findViewById(R.id.importOption)
        lockOption = findViewById(R.id.lockOption)
        lockSwitch = findViewById(R.id.lockSwitch)
        feedbackOption = findViewById(R.id.feedbackOption)
        versionText = findViewById(R.id.versionText)
        versionText.text = versionLabel()
        lockCurtain = findViewById(R.id.lockCurtain)
        lockRetry = findViewById(R.id.lockRetry)
        lockRetry.setOnClickListener { showUnlockPrompt() }
        selectionBar = findViewById(R.id.selectionBar)
        bulkShare = findViewById(R.id.bulkShare)
        bulkMove = findViewById(R.id.bulkMove)
        bulkDelete = findViewById(R.id.bulkDelete)

        navDocs = findViewById(R.id.navDocs)
        navPeople = findViewById(R.id.navPeople)
        navYou = findViewById(R.id.navYou)

        // Home owns the Docs tab in the shared bottom bar.
        LedgerNav.markActive(this, LedgerNav.Tab.DOCS)

        tabViews = listOf(
            LedgerTab(
                DocumentSort.MANUAL,
                findViewById(R.id.tabAll),
                findViewById(R.id.tabAllLabel),
                findViewById(R.id.tabAllUnderline)
            ),
            LedgerTab(
                DocumentSort.RECENT,
                findViewById(R.id.tabRecent),
                findViewById(R.id.tabRecentLabel),
                findViewById(R.id.tabRecentUnderline)
            ),
            LedgerTab(
                DocumentSort.PERSON,
                findViewById(R.id.tabPerson),
                findViewById(R.id.tabPersonLabel),
                findViewById(R.id.tabPersonUnderline)
            ),
            LedgerTab(
                DocumentSort.ALPHA,
                findViewById(R.id.tabAlpha),
                findViewById(R.id.tabAlphaLabel),
                findViewById(R.id.tabAlphaUnderline)
            )
        )
    }

    private fun setupViewModels() {
        viewModel = ViewModelProvider(this, DocumentViewModel.Factory(application))[DocumentViewModel::class.java]
    }

    private fun setupRecyclerView() {
        // Always use 2 cards per row for better visibility of content
        val spanCount = if (resources.configuration.smallestScreenWidthDp >= 600) 3 else 2
        val layoutManager = GridLayoutManager(this, spanCount)
        recyclerView.layoutManager = layoutManager

        // The Ledger grid has no card margins: cells sit flush and are parted
        // by a 2px rule the decoration reserves and paints.
        recyclerView.addItemDecoration(LedgerGridDecoration(this, spanCount))

        // Optimize RecyclerView for better long-distance dragging
        recyclerView.setHasFixedSize(true)
        recyclerView.setItemViewCacheSize(30)
        recyclerView.setRecycledViewPool(RecyclerView.RecycledViewPool())
        recyclerView.recycledViewPool.setMaxRecycledViews(0, 20)
        
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
        
        dragDropAdapter = DragDropDocumentAdapter(
            documents.toMutableList(),
            onDocumentClick = { document ->
                val intent = Intent(this, DocumentDetailActivity::class.java)
                intent.putExtra("document_id", document.id)
                documentDetailLauncher.launch(intent)
            },
            onDocumentMoved = { updatedDocuments ->
                // Update the order of documents with a copy to avoid ConcurrentModificationException
                viewModel.updateDocumentOrder(updatedDocuments.toList())
            },
            onSelectionChanged = { selected -> onSelectionChanged(selected) },
            onStartDrag = { holder -> itemTouchHelper?.startDrag(holder) }
        )
        recyclerView.adapter = dragDropAdapter

        // Setup item touch helper for drag and drop
        itemTouchHelper = ItemTouchHelper(DocumentItemTouchHelperCallback(dragDropAdapter!!))
        itemTouchHelper?.attachToRecyclerView(recyclerView)
    }

    private fun setupClickListeners() {
        fab.setOnClickListener {
            showCreateDocumentDialog()
        }

        menuButton.setOnClickListener {
            toggleSidebar()
        }

        searchButton.setOnClickListener {
            toggleSearch()
        }

        // Add TextWatcher for search functionality
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                performSearch(s.toString())
            }
        })

        // Add EditorActionListener for search action
        searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch(searchEditText.text.toString())
                true
            } else {
                false
            }
        }

        // Add click listener for theme option
        themeOption.setOnClickListener {
            toggleTheme()
            closeSidebar()
        }

        // Add click listeners for sidebar options
        exportOption.setOnClickListener {
            showExportOptionsDialog()
            closeSidebar()
        }

        importOption.setOnClickListener {
            showImportOptionsDialog()
            closeSidebar()
        }

        // Make user name clickable to open profile
        userNameDisplayContainer.setOnClickListener {
            openProfile()
            closeSidebar()
        }

        // Sidebar overlay click to close
        sidebarOverlay.setOnClickListener {
            closeSidebar()
        }

        drawerCloseButton.setOnClickListener {
            closeSidebar()
        }

        lockOption.setOnClickListener { toggleAppLock() }

        feedbackOption.setOnClickListener {
            closeSidebar()
            openIssueTracker()
        }

        // Quick starts on the empty state. These were laid out but never
        // wired, so tapping one did nothing at all.
        val quickStarts = mapOf(
            R.id.quickStartAadhaar to R.string.ledger_suggest_aadhaar,
            R.id.quickStartPan to R.string.ledger_suggest_pan,
            R.id.quickStartPassport to R.string.ledger_suggest_passport,
            R.id.quickStartLicense to R.string.ledger_suggest_license
        )
        for ((viewId, labelId) in quickStarts) {
            findViewById<View>(viewId)?.setOnClickListener {
                createDocument(getString(labelId))
            }
        }

        bulkShare.setOnClickListener { bulkShareSelected() }
        bulkMove.setOnClickListener { bulkMoveSelected() }
        bulkDelete.setOnClickListener { bulkDeleteSelected() }

        searchClearButton.setOnClickListener {
            if (searchEditText.text.isNullOrEmpty()) {
                closeSearch()
                loadDocuments()
            } else {
                searchEditText.setText("")
            }
        }

        // Bottom navigation. Docs is this screen, so it only closes overlays.
        navDocs.setOnClickListener {
            closeSidebar()
            closeSearch()
            loadDocuments()
        }

        navPeople.setOnClickListener {
            closeSidebar()
            openFamilyFriends()
        }

        navYou.setOnClickListener {
            closeSidebar()
            openProfile()
        }

        // Filter strip.
        tabViews.forEach { tab ->
            tab.root.setOnClickListener { selectSort(tab.order) }
        }
    }

    /**
     * Applies one of the four orderings from the filter strip and repaints the
     * accent underline. Sorting happens on the already-loaded list so that
     * switching tabs never re-queries the database.
     */
    private fun selectSort(order: DocumentSort) {
        currentSort = order
        tabViews.forEach { tab ->
            val active = tab.order == order
            TextViewCompat.setTextAppearance(
                tab.label,
                if (active) R.style.TextAppearance_Ledger_TabActive
                else R.style.TextAppearance_Ledger_Tab
            )
            tab.underline.setBackgroundColor(
                ContextCompat.getColor(
                    this,
                    if (active) R.color.ledger_accent else android.R.color.transparent
                )
            )
        }
        updateUI()
    }

    /** Returns [documents] arranged for the active filter tab. */
    private fun sortedDocuments(): List<Document> = when (currentSort) {
        DocumentSort.MANUAL -> documents.toList()
        DocumentSort.RECENT -> documents.sortedByDescending { it.updatedAt }
        DocumentSort.ALPHA -> documents.sortedBy { it.name.lowercase() }
        DocumentSort.PERSON -> documents.sortedWith(
            compareBy({ it.personId ?: Long.MAX_VALUE }, { it.name.lowercase() })
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load theme preference
        loadThemePreference()
        
        updateTheme()

        setContentView(R.layout.activity_main)

        // Initialize views
        initViews()

        // Must happen in onCreate; see biometricPrompt.
        setUpBiometricPrompt()

        // Setup ViewModels
        setupViewModels()

        // Setup RecyclerView
        setupRecyclerView()

        // Setup click listeners
        setupClickListeners()

        // Load documents
        loadDocuments()

        // Check if user has completed onboarding
        if (!OnboardingManager.isOnboardingCompleted(this)) {
            redirectToOnboarding()
            return
        }

        // Update user name display
        updateUserNameDisplay()

        // Clean up orphaned image entries
        cleanupOrphanedEntries()

        // Read any scans that have not been read yet, so search can look
        // inside them. Bounded per pass; onResume picks up the rest.
        viewModel.indexUnreadScans()

        loadTagsForGrid()
    }

    private fun cleanupOrphanedEntries() {
        Thread {
            try {
                runBlocking {
                    withContext(Dispatchers.IO) {
                        viewModel.cleanupOrphanedImageEntriesSync()
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error cleaning up orphaned entries", e)
            }
        }.start()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save sidebar state
        outState.putBoolean("sidebar_open", sidebarLayout.isVisible)
    }

    override fun onResume() {
        super.onResume()

        // The vault re-locks after a spell in the background.
        enforceAppLock()
        lockSwitch.isChecked = AppLock.isEnabled(this)

        // Continue the OCR backlog a batch at a time.
        if (::viewModel.isInitialized) {
            viewModel.indexUnreadScans()
            loadTagsForGrid()
        }
        // Ensure theme button is updated when activity resumes
        updateThemeButton()

        // Update user name display and profile photo when activity resumes
        updateUserNameDisplay()
    }

    private fun toggleSearch() {
        if (searchInputLayout.isVisible) {
            searchInputLayout.visibility = View.GONE
            // Clear search when hiding
            searchEditText.setText("")
            loadDocuments()
        } else {
            searchInputLayout.visibility = View.VISIBLE
            searchEditText.requestFocus()
        }
    }

    private fun toggleSidebar() {
        if (sidebarLayout.isVisible) {
            closeSidebar()
        } else {
            closeSearch()
            openSidebar()
            // Update theme button when sidebar opens with a slight delay
            recyclerView.post {
                updateThemeButton()
            }
        }
    }

    private fun openSidebar() {
        updateVaultSize()
        sidebarLayout.visibility = View.VISIBLE
        sidebarOverlay.visibility = View.VISIBLE
        isSidebarOpen = true

        // Update user name display and profile photo when sidebar is opened
        updateUserNameDisplay()
    }

    private fun closeSidebar() {
        sidebarLayout.visibility = View.GONE
        sidebarOverlay.visibility = View.GONE
        isSidebarOpen = false
    }

    private fun closeSearch() {
        searchInputLayout.visibility = View.GONE
        searchEditText.setText("")
        detachSearchObserver()
    }

    /**
     * Turns the vault lock on or off.
     *
     * Refuses to arm when the device has nothing to authenticate with, rather
     * than storing a preference that would silently do nothing.
     */
    private fun toggleAppLock() {
        val enabling = !AppLock.isEnabled(this)
        if (enabling && !AppLock.canAuthenticate(this)) {
            Toast.makeText(this, R.string.ledger_lock_unavailable, Toast.LENGTH_LONG).show()
            lockSwitch.isChecked = false
            return
        }
        AppLock.setEnabled(this, enabling)
        lockSwitch.isChecked = enabling
        Toast.makeText(
            this,
            if (enabling) R.string.ledger_lock_on else R.string.ledger_lock_off,
            Toast.LENGTH_SHORT
        ).show()
    }

    /**
     * The installed version, shown against the feedback row.
     *
     * Read from the package rather than hard-coded, so it cannot drift from
     * what was actually shipped — and it is the first thing worth knowing when
     * someone reports a problem.
     */
    private fun versionLabel(): String = try {
        "v" + packageManager.getPackageInfo(packageName, 0).versionName
    } catch (_: PackageManager.NameNotFoundException) {
        ""
    }

    /** Opens the GitHub issue tracker for bug reports and feature requests. */
    private fun openIssueTracker() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.ledger_issues_url)))
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // A phone with no browser at all is rare but possible, and an
            // unhandled intent would take the whole app down.
            Toast.makeText(this, R.string.ledger_no_browser, Toast.LENGTH_LONG).show()
        }
    }

    private fun setUpBiometricPrompt() {
        biometricPrompt = androidx.biometric.BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {

                override fun onAuthenticationSucceeded(
                    result: androidx.biometric.BiometricPrompt.AuthenticationResult
                ) {
                    promptShowing = false
                    AppLock.markUnlocked()
                    lockCurtain.visibility = View.GONE
                }

                override fun onAuthenticationError(code: Int, message: CharSequence) {
                    // The curtain stays, and the Unlock button on it is the
                    // way back in. Finishing here — as this used to — left no
                    // route to retry, and a transient error looked like a
                    // broken app.
                    promptShowing = false
                }

                override fun onAuthenticationFailed() {
                    // One unrecognised finger; the prompt stays up for another
                    // attempt and the system shows its own message.
                }
            }
        )
    }

    /**
     * Asks for authentication before the vault is readable.
     *
     * The curtain stays up until the prompt succeeds, so the grid is never
     * visible behind it — including in the recents thumbnail.
     */
    private fun enforceAppLock() {
        if (!AppLock.shouldChallenge(this)) {
            lockCurtain.visibility = View.GONE
            return
        }
        lockCurtain.visibility = View.VISIBLE
        showUnlockPrompt()
    }

    private fun showUnlockPrompt() {
        // Already asking, or the screen is going away.
        if (promptShowing || isFinishing || isDestroyed) return

        val prompt = biometricPrompt ?: return
        promptShowing = true

        fun promptInfo(authenticators: Int) =
            androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.ledger_unlock_title))
                .setSubtitle(getString(R.string.ledger_unlock_subtitle))
                .setAllowedAuthenticators(authenticators)
                .build()

        // Biometrics plus device credential is rejected outright on API 28-29,
        // so a failure there retries with biometrics alone rather than leaving
        // a screen with nothing on it. The Unlock button is the last resort.
        val started = runCatching { prompt.authenticate(promptInfo(AppLock.ALLOWED)) }
            .recoverCatching { first ->
                Log.w("MainActivity", "Combined authenticators refused; retrying", first)
                prompt.authenticate(
                    promptInfo(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK)
                )
            }
            .isSuccess

        if (!started) {
            Log.e("MainActivity", "Could not show the unlock prompt")
            promptShowing = false
        }
    }

    /**
     * Fills the size shown against "Export all as ZIP".
     *
     * The row had the figure in the design and a bound view in the layout, but
     * nothing ever wrote to it, so it sat empty.
     */
    private fun updateVaultSize() {
        lifecycleScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    viewModel.getAllDocumentsSync().sumOf { doc ->
                        viewModel.getImagesForDocumentSync(doc.id)
                            .filter { it.isImage }
                            .sumOf { block ->
                                runCatching { File(block.imagePath).length() }.getOrDefault(0L)
                            }
                    }
                }.getOrDefault(0L)
            }
            exportSizeText.text = formatVaultSize(bytes)
        }
    }

    private fun formatVaultSize(bytes: Long): String = when {
        bytes >= 1024L * 1024L ->
            String.format(Locale.getDefault(), "%.0f MB", bytes / 1048576.0)
        bytes >= 1024L -> String.format(Locale.getDefault(), "%d KB", bytes / 1024)
        else -> "$bytes B"
    }

    /** Paints each cell's tag line, so labels are visible without opening. */
    private fun loadTagsForGrid() {
        lifecycleScope.launch {
            val tags = viewModel.getAllTagsSync().associateBy { it.id }
            val byDocument = viewModel.getAllTagPairingsSync()
                .groupBy { it.documentId }
                .mapValues { entry ->
                    entry.value.mapNotNull { tags[it.tagId]?.name }
                }
            dragDropAdapter?.setTagsByDocument(byDocument)
        }
    }

    // ══ Bulk selection ══════════════════════════════════════════════════

    /**
     * Swaps the primary action bar for the bulk one and retitles the header
     * while cells are selected.
     */
    private fun onSelectionChanged(selected: Set<Long>) {
        val active = selected.isNotEmpty()
        selectionBar.visibility = if (active) View.VISIBLE else View.GONE
        fab.visibility = if (active) View.GONE else View.VISIBLE

        if (active) {
            headerSubtitle.text = getString(R.string.ledger_selected, selected.size)
        } else {
            updateHeaderCounts()
        }
    }

    /** Back leaves selection before it leaves the screen. */
    override fun onBackPressed() {
        when {
            dragDropAdapter?.isSelectionMode() == true -> dragDropAdapter?.clearSelection()
            isSidebarOpen -> closeSidebar()
            searchInputLayout.isVisible -> {
                closeSearch()
                loadDocuments()
            }
            else -> @Suppress("DEPRECATION") super.onBackPressed()
        }
    }

    /** Shares every scan in every selected document as one attachment set. */
    private fun bulkShareSelected() {
        val chosen = dragDropAdapter?.selectedDocuments().orEmpty()
        if (chosen.isEmpty()) return

        lifecycleScope.launch {
            val uris = ArrayList<Uri>()
            withContext(Dispatchers.IO) {
                for (doc in chosen) {
                    for (block in viewModel.getImagesForDocumentSync(doc.id)) {
                        if (!block.isImage) continue
                        val file = File(block.imagePath)
                        if (file.exists()) {
                            uris.add(FileUtils.getUriForFile(this@MainActivity, file))
                        }
                    }
                }
            }

            if (uris.isEmpty()) {
                Toast.makeText(this@MainActivity, R.string.ledger_nothing_to_share, Toast.LENGTH_SHORT).show()
                return@launch
            }

            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share)))
            dragDropAdapter?.clearSelection()
        }
    }

    /** Files every selected document under a person, or back under the owner. */
    private fun bulkMoveSelected() {
        val chosen = dragDropAdapter?.selectedDocuments().orEmpty()
        if (chosen.isEmpty()) return

        lifecycleScope.launch {
            val people = viewModel.getAllPeopleSync()
            if (people.isEmpty()) {
                Toast.makeText(this@MainActivity, R.string.ledger_no_people_yet, Toast.LENGTH_LONG).show()
                return@launch
            }

            // "Me" first: moving a document back off a person's shelf is as
            // common as moving one onto it.
            val labels = (listOf(getString(R.string.ledger_move_to_me)) + people.map { it.name })
                .toTypedArray()

            AlertDialog.Builder(this@MainActivity)
                .setTitle(R.string.ledger_move)
                .setItems(labels) { _, which ->
                    val target = if (which == 0) null else people[which - 1]
                    lifecycleScope.launch {
                        for (doc in chosen) {
                            viewModel.updateDocumentSync(
                                doc.copy(personId = target?.id, updatedAt = Date())
                            )
                        }
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.ledger_moved, target?.name ?: labels[0]),
                            Toast.LENGTH_SHORT
                        ).show()
                        dragDropAdapter?.clearSelection()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun bulkDeleteSelected() {
        val chosen = dragDropAdapter?.selectedDocuments().orEmpty()
        if (chosen.isEmpty()) return

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.ledger_bulk_delete_title, chosen.size))
            .setMessage(R.string.ledger_bulk_delete_body)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    for (doc in chosen) {
                        viewModel.deleteDocumentSync(doc)
                    }
                    dragDropAdapter?.clearSelection()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateThemeButton() {
        if (isDarkTheme) {
            themeIcon.setImageResource(R.drawable.ic_theme_dark)
            themeText.text = getString(R.string.ledger_theme_dark)
        } else {
            themeIcon.setImageResource(R.drawable.ic_theme_light)
            themeText.text = getString(R.string.ledger_theme_light)
        }
    }

    private fun loadDocuments() {
        // Observe once. This used to re-subscribe on every call — from
        // onResume, from closing search, from the nav bar — stacking a fresh
        // observer each time, so a single database change fanned out into as
        // many updateUI() passes as there had been calls.
        if (documentsObserved) return
        documentsObserved = true

        // Load only documents for the main user (not associated with any person)
        viewModel.getDocumentsForMainUser().observe(this) { docs ->
            documents.clear()
            documents.addAll(docs)
            updateUI()

            // Load images for each document
            loadDocumentImages(docs)
        }
    }

    private fun loadDocumentImages(docs: List<Document>) {
        Log.d("MainActivity", "LoadDocumentImages called with ${docs.size} documents")

        // Drop the observers registered for the previous document set before
        // registering new ones, otherwise every reload leaves its predecessors
        // attached and each image edit fires them all.
        imageObservers.forEach { (liveData, observer) -> liveData.removeObserver(observer) }
        imageObservers.clear()

        imageMap.clear()
        imageCountMap.clear()

        for (document in docs) {
            val liveData = viewModel.getImagesForDocument(document.id)
            val observer = Observer<List<DocumentImage>> { images ->
                imageCountMap[document.id] = images.count { it.isImage }
                imageMap[document.id] = images.firstOrNull { it.isImage }?.imagePath

                // Counts arrive after the documents, so push them to the cells.
                dragDropAdapter?.setImageCounts(imageCountMap)
            }
            liveData.observe(this, observer)
            imageObservers.add(liveData to observer)
        }
    }

    private fun updateUI() {
        updateHeaderCounts()

        if (documents.isEmpty()) {
            emptyStateLayout.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            // On the empty state the primary action invites the first scan.
            fabLabel.setText(R.string.ledger_scan_first)
            fabIcon.setImageResource(R.drawable.ic_camera)
        } else {
            emptyStateLayout.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            fabLabel.setText(R.string.ledger_new_document)
            fabIcon.setImageResource(R.drawable.ic_add)

            // Reordering by drag only makes sense while the manual order is
            // the one on screen; the other tabs present a derived ordering.
            dragDropAdapter?.setReorderEnabled(currentSort == DocumentSort.MANUAL)
            dragDropAdapter?.updateDocuments(sortedDocuments())
        }
    }

    /**
     * Keeps the tracked line under the wordmark and the "All n" tab in step
     * with the loaded document list.
     */
    private fun updateHeaderCounts() {
        val count = documents.size
        val userName = currentUserName()

        headerSubtitle.text = if (count == 0) {
            getString(R.string.ledger_nothing_filed, userName)
        } else {
            val docs = if (count == 1) {
                getString(R.string.ledger_document_count_one)
            } else {
                getString(R.string.ledger_documents_count, count)
            }
            getString(R.string.ledger_on_device, userName, docs)
        }

        tabViews.firstOrNull { it.order == DocumentSort.MANUAL }?.label?.text =
            getString(R.string.ledger_tab_all_count, count)
    }

    /** The display name, preferring the profile edit over the onboarding one. */
    private fun currentUserName(): String {
        val profilePrefs = getSharedPreferences("profile_prefs", MODE_PRIVATE)
        val onboardingPrefs = getSharedPreferences("onboarding_prefs", MODE_PRIVATE)
        return profilePrefs.getString("username", null)
            ?: onboardingPrefs.getString("user_name", null)
            ?: "You"
    }

    private fun showCreateDocumentDialog() {
        val builder = AlertDialog.Builder(this, R.style.CustomDialogTheme)
        val inflater = layoutInflater
        val dialogLayout = inflater.inflate(R.layout.dialog_create_document_enhanced, null)
        val documentNameEditText = dialogLayout.findViewById<AutoCompleteTextView>(R.id.documentNameEditText)
        val suggestionsButtonContainer = dialogLayout.findViewById<LinearLayout>(R.id.suggestionsButtonContainer)
        val noSuggestionsText = dialogLayout.findViewById<TextView>(R.id.noSuggestionsText)

        // Comprehensive list of document names
        val commonDocumentNames = listOf(
            "Aadhaar Card",
            "PAN Card",
            "Voter ID",
            "Passport",
            "Driving License",
            "Ration Card",
            "Birth Certificate",
            "Death Certificate",
            "Marriage Certificate",
            "Divorce Certificate",
            "Caste Certificate",
            "Domicile Certificate",
            "Income Certificate",
            "Residence Proof",
            "Electricity Bill",
            "Water Bill",
            "Gas Bill",
            "Mobile Bill",
            "Landline Bill",
            "Property Tax Receipt",
            "Property Sale Deed",
            "Property Registration Document",
            "Land Ownership Paper",
            "House Lease Agreement",
            "Rent Agreement",
            "Mutation Certificate",
            "Property Tax Assessment",
            "Building Plan Approval",
            "Occupancy Certificate",
            "House Insurance Policy",
            "Vehicle RC Book",
            "Vehicle Insurance Policy",
            "Pollution Certificate",
            "Vehicle Purchase Invoice",
            "Vehicle Loan Agreement",
            "Vehicle Service History",
            "Vehicle Warranty Card",
            "Vehicle Fitness Certificate",
            "Driving Test Result",
            "Vehicle Road Tax Receipt",
            "Health Insurance Policy",
            "Life Insurance Policy",
            "Term Insurance Policy",
            "Mediclaim Policy",
            "Insurance Premium Receipt",
            "Nominee Details Sheet",
            "LIC Policy Bond",
            "ULIP Policy Document",
            "Policy Renewal Receipt",
            "Claim Settlement Letter",
            "Bank Passbook",
            "Cheque Book",
            "ATM/Debit Card Copy",
            "Credit Card Copy",
            "Bank Statement",
            "Loan Agreement",
            "Fixed Deposit Receipt",
            "Recurring Deposit Passbook",
            "Mutual Fund Statement",
            "Share Certificate",
            "Demat Account Proof",
            "Stock Portfolio Snapshot",
            "UPI Transaction History",
            "Wallet Transaction Receipt",
            "Investment Portfolio Summary",
            "Income Tax Return (ITR)",
            "Form 16",
            "Form 26AS",
            "Form 15G / 15H",
            "Tax Payment Challan",
            "TDS Certificate",
            "GST Registration Certificate",
            "GST Return Filing Proof",
            "Business PAN Copy",
            "CA Audit Report",
            "Tax Notice Copy",
            "School Marksheet (10th)",
            "School Marksheet (12th)",
            "Graduation Degree",
            "Post-Graduation Degree",
            "Diploma Certificate",
            "Transfer Certificate",
            "Migration Certificate",
            "Character Certificate",
            "Bonafide Certificate",
            "Internship Certificate",
            "College ID Card",
            "Entrance Exam Admit Card",
            "Entrance Exam Result",
            "Scholarship Letter",
            "Education Loan Agreement",
            "Coaching Fee Receipt",
            "Study Material Receipts",
            "Attendance Record",
            "Tuition Fee Receipt",
            "Exam Form Submission Proof",
            "Appointment Letter",
            "Offer Letter",
            "Salary Slip",
            "Increment Letter",
            "Experience Letter",
            "Relieving Letter",
            "Promotion Letter",
            "HR Policy Document",
            "PF Statement",
            "ESIC Card",
            "Professional Tax Receipt",
            "Office ID Card",
            "Training Certificate",
            "Bonus Receipt",
            "Attendance Sheet",
            "Work From Home Policy",
            "Project Completion Certificate",
            "Employment Contract",
            "Internship Offer Letter",
            "COVID Vaccination Certificate",
            "Medical Report",
            "Prescription",
            "Blood Test Report",
            "X-Ray / MRI Scan Report",
            "Hospital Bill",
            "Surgery Report",
            "Health Card",
            "Medical Insurance Card",
            "Doctor Prescription Slip",
            "Discharge Summary",
            "Diagnostic Report",
            "Pathology Report",
            "OPD Prescription",
            "Dental Report",
            "Eye Test Report",
            "Blood Donation Certificate",
            "Health Checkup Summary",
            "Medical Fitness Certificate",
            "Hospital Admission Form",
            "Vehicle Loan Documents",
            "Home Loan Documents",
            "Education Loan Proof",
            "Personal Loan Proof",
            "Loan Repayment Schedule",
            "EMI Receipt",
            "NOC from Bank",
            "Loan Closure Letter",
            "Loan Sanction Letter",
            "Loan Account Statement",
            "Collateral Document",
            "Guarantor Form",
            "Mortgage Deed",
            "Hypothecation Cancellation",
            "Gold Loan Receipt",
            "Shareholder Certificate",
            "Dividend Statement",
            "IPO Application",
            "Mutual Fund SIP Statement",
            "KYC Acknowledgment",
            "Broker Contract Note",
            "Demat Holding Statement",
            "Stock Portfolio Snapshot",
            "UPI Transaction History",
            "Wallet Transaction Receipt",
            "Investment Portfolio Summary",
            "Income Tax Return (ITR)",
            "Form 16",
            "Form 26AS",
            "Form 15G / 15H",
            "Tax Payment Challan",
            "TDS Certificate",
            "GST Registration Certificate",
            "GST Return Filing Proof",
            "Business PAN Copy",
            "CA Audit Report",
            "Tax Notice Copy",
            "School Marksheet (10th)",
            "School Marksheet (12th)",
            "Graduation Degree",
            "Post-Graduation Degree",
            "Diploma Certificate",
            "Transfer Certificate",
            "Migration Certificate",
            "Character Certificate",
            "Bonafide Certificate",
            "Internship Certificate",
            "College ID Card",
            "Entrance Exam Admit Card",
            "Entrance Exam Result",
            "Scholarship Letter",
            "Education Loan Agreement",
            "Coaching Fee Receipt",
            "Study Material Receipts",
            "Attendance Record",
            "Tuition Fee Receipt",
            "Exam Form Submission Proof",
            "Appointment Letter",
            "Offer Letter",
            "Salary Slip",
            "Increment Letter",
            "Experience Letter",
            "Relieving Letter",
            "Promotion Letter",
            "HR Policy Document",
            "PF Statement",
            "ESIC Card",
            "Professional Tax Receipt",
            "Office ID Card",
            "Training Certificate",
            "Bonus Receipt",
            "Attendance Sheet",
            "Work From Home Policy",
            "Project Completion Certificate",
            "Employment Contract",
            "Internship Offer Letter",
            "COVID Vaccination Certificate",
            "Medical Report",
            "Prescription",
            "Blood Test Report",
            "X-Ray / MRI Scan Report",
            "Hospital Bill",
            "Surgery Report",
            "Health Card",
            "Medical Insurance Card",
            "Doctor Prescription Slip",
            "Discharge Summary",
            "Diagnostic Report",
            "Pathology Report",
            "OPD Prescription",
            "Dental Report",
            "Eye Test Report",
            "Blood Donation Certificate",
            "Health Checkup Summary",
            "Medical Fitness Certificate",
            "Hospital Admission Form",
            "Vehicle Loan Documents",
            "Home Loan Documents",
            "Education Loan Proof",
            "Personal Loan Proof",
            "Loan Repayment Schedule",
            "EMI Receipt",
            "NOC from Bank",
            "Loan Closure Letter",
            "Loan Sanction Letter",
            "Loan Account Statement",
            "Collateral Document",
            "Guarantor Form",
            "Mortgage Deed",
            "Hypothecation Cancellation",
            "Gold Loan Receipt",
            "Shareholder Certificate",
            "Dividend Statement",
            "IPO Application",
            "Mutual Fund SIP Statement",
            "KYC Acknowledgment",
            "Broker Contract Note",
            "Demat Holding Statement",
            "Trading Account Summary",
            "Annual Report Copy",
            "Stock Transaction Receipt",
            "Bond Certificate",
            "National Savings Certificate (NSC)",
            "Post Office Passbook",
            "Sukanya Samriddhi Account Proof",
            "PPF Passbook",
            "EPF Passbook",
            "NPS Account Statement",
            "Senior Citizen Savings Scheme Proof",
            "Fixed Deposit Advice",
            "Recurring Deposit Receipt",
            "Election Voter Slip",
            "Voter ID Application Form",
            "Polling Booth Slip",
            "Party Membership Card",
            "Election Campaign Permission Copy",
            "Local Election ID",
            "Voting Acknowledgment",
            "Electoral Roll Slip",
            "Nomination Paper",
            "Candidature Approval Letter",
            "Visa",
            "Flight Tickets",
            "Travel Insurance",
            "Hotel Booking",
            "Tour Plan",
            "Boarding Pass",
            "Foreign Currency Receipt",
            "Travel Permit",
            "International Driving Permit",
            "Train Ticket",
            "Bus Ticket",
            "Travel Voucher",
            "Car Rental Agreement",
            "Toll Receipt",
            "Travel Expense Bill",
            "Travel Claim Form",
            "Office Travel Authorization",
            "Journey Completion Proof",
            "Trip Photos Backup",
            "Court Order",
            "FIR Copy",
            "Police Report",
            "Legal Notice",
            "Affidavit",
            "Power of Attorney",
            "Agreement Copy",
            "Sale Agreement",
            "Notarized Document",
            "Case File Copy",
            "Court Summon",
            "Bail Order",
            "Judgement Copy",
            "Property Dispute Papers",
            "Advocate Letter",
            "Evidence Copy",
            "Witness Affidavit",
            "Lease Agreement",
            "Consent Letter",
            "RTI Application Copy",
            "RTI Response Letter",
            "Legal Heir Certificate",
            "Arbitration Agreement",
            "Court Fee Receipt",
            "Sub-Registrar Acknowledgement",
            "Will Document",
            "Property Inheritance Proof",
            "Gift Deed",
            "Mortgage Release Letter",
            "Police Verification Report",
            "Digital Signature Certificate",
            "Aadhaar XML File",
            "DigiLocker Backup",
            "Password Sheet",
            "App Login Credentials",
            "Email Backup File",
            "Google Account Recovery Codes",
            "iCloud Recovery Key",
            "UPI QR Backup",
            "NetBanking Credentials Sheet",
            "2FA Recovery Codes",
            "Digital Invoice",
            "Screenshot Receipts",
            "Subscription Proof",
            "Antivirus License Key",
            "Software Purchase Bill",
            "License File",
            "Software Activation Key",
            "Device Warranty Card",
            "Purchase Invoice",
            "AMC Contract",
            "Service Report",
            "Repair Bill",
            "Replacement Invoice",
            "Electronic Gadget Purchase Bill",
            "Earphone / Mobile Bill",
            "Laptop Invoice",
            "Printer Invoice",
            "Hard Drive Warranty",
            "Power Bank Bill",
            "Smartwatch Bill",
            "Keyboard / Mouse Invoice",
            "Bluetooth Speaker Bill",
            "Monitor Purchase Bill",
            "Router Bill",
            "Web Hosting Invoice",
            "Domain Renewal Bill",
            "SSL Certificate Proof",
            "Digital Marketing Invoice",
            "Software Renewal Receipt",
            "App Subscription Bill",
            "Cloud Storage Invoice",
            "OTT Subscription Bill",
            "Canva / Figma Subscription Proof",
            "Netflix / Hotstar Invoice",
            "Amazon Prime Bill",
            "Spotify / YouTube Premium Bill",
            "Steam Purchase Invoice",
            "Game Subscription Bill",
            "ChatGPT Plus Receipt",
            "Adobe Subscription Proof",
            "Office 365 License",
            "Dropbox Subscription Proof",
            "E-Book Purchase Receipt",
            "Investment Proof",
            "Stock Demat Summary",
            "FD Renewal Advice",
            "RD Closure Receipt",
            "Bank Locker Agreement",
            "Safe Deposit Bill",
            "Bank Correspondence Letter",
            "PAN Correction Form",
            "Aadhaar Update Slip",
            "Mobile Number Link Proof",
            "eKYC Acknowledgment",
            "Signature Proof",
            "Thumb Impression Sheet",
            "Old Photo ID Copy",
            "NOC from Employer",
            "Salary Account Details Sheet",
            "Hospital ID Card",
            "Therapy Reports",
            "Pharmacy Receipts",
            "Ambulance Receipts",
            "Vaccination Record",
            "Baby Health Record",
            "Medical Leave Certificate",
            "Health Camp Certificate",
            "Disability Pension Paper",
            "Health Insurance Renewal Receipt",
            "Passport Renewal Form",
            "Travel NOC from Employer",
            "Visa Application Receipt",
            "Hotel Voucher",
            "International SIM Card",
            "Embassy Appointment Slip",
            "Invitation Letter for Visa",
            "Visa Rejection Letter",
            "Immigration Stamps Copy",
            "Travel Credit Card Statement",
            "FIR Copy (Duplicate)",
            "Police Clearance Certificate",
            "Arbitration Award",
            "Legal Opinion Copy",
            "RTI Appeal Form",
            "Will Witness Copy",
            "Court Judgement",
            "Court Fees Receipt",
            "Notary Certificate",
            "Affidavit of Loss",
            "Power of Attorney (Registered)",
            "Legal Consultation Invoice",
            "Advocate ID Copy",
            "RTI Receipt",
            "RTI Appeal",
            "Disability Certificate",
            "Senior Citizen Card",
            "Widow Pension Proof",
            "BPL Card",
            "Labour Card",
            "Ujjwala Gas Connection Proof",
            "PM Kisan Samman Nidhi Proof",
            "PMAY Application Form",
            "Jan Dhan Account Proof",
            "Ayushman Bharat Card",
            "Pension Yojana Proof",
            "Food Security Card",
            "Employment Guarantee Card (MGNREGA)",
            "PM Jeevan Jyoti Bima Yojana",
            "PM Suraksha Bima Yojana",
            "Kisan Credit Card (KCC)",
            "Gas Subsidy Receipt",
            "E-Shram Card",
            "Old Age Pension Proof",
            "Antyodaya Card",
            "Widow Pension Proof",
            "Disability Pension Proof",
            "Ration Subsidy Receipt",
            "Income Proof for Scheme",
            "Beneficiary Application Form",
            "Water Tax Receipt",
            "Property Maintenance Bill",
            "Society Maintenance Bill",
            "Apartment Registration Document",
            "Builder Agreement",
            "Possession Letter",
            "Allotment Letter",
            "Car Parking Allotment Letter",
            "Flat Registration Certificate",
            "House NOC",
            "Builder Warranty",
            "Property Possession Certificate",
            "Society Membership Form",
            "Maintenance Bill Receipts",
            "Vehicle Service Receipt",
            "AC Service Bill",
            "Furniture Purchase Invoice",
            "Electronic Appliance Bill",
            "Mobile Recharge Receipt",
            "OTT Renewal Proof",
            "Movie Ticket",
            "Event Pass",
            "Club Membership Card",
            "Gym Membership Proof",
            "Gym Fee Receipt",
            "Yoga Class Certificate",
            "Library Card",
            "Volunteer Certificate",
            "NGO Membership Proof",
            "Seminar Certificate",
            "Internship Completion Certificate",
            "Conference Pass",
            "Exhibition Pass",
            "Training Workshop Proof",
            "Donation Certificate",
            "Blood Donation Proof",
            "Organ Donation Proof",
            "Family Photo Album Backup",
            "Personal Notes Backup",
            "Resume PDF",
            "Cover Letter Template",
            "Portfolio Images Folder",
            "Awards Certificate",
            "Recommendation Letter",
            "Appreciation Certificate",
            "Personal Diary Backup",
            "Fitness Tracker Report",
            "Diet Chart",
            "Medical Expense Reimbursement",
            "Hospital Claim Receipt",
            "Vaccine Passport",
            "Emergency Contact Sheet",
            "Fire Insurance Policy",
            "Life Insurance Renewal Receipt",
            "Term Insurance Renewal Receipt",
            "Personal Accident Insurance Policy",
            "Travel Insurance Policy",
            "Pet Insurance Policy",
            "Business Insurance Policy",
            "Health ID (ABHA) Card",
            "PPF Account Statement",
            "Sukanya Samriddhi Yojana Proof",
            "NPS Investment Receipt",
            "Kisan Vikas Patra Proof",
            "Post Office RD Receipt",
            "Postal Life Insurance Proof",
            "Post Office Savings Account Passbook",
            "Stamp Collection Photos",
            "Old Currency Notes Photo",
            "Art Ownership Certificate",
            "Jewellery Purchase Bill",
            "Jewellery Valuation Certificate",
            "Gold Certificate",
            "Hallmark Proof",
            "Silver Purchase Receipt",
            "Watch Purchase Bill",
            "Handbag Invoice",
            "Perfume Bill",
            "Sunglasses Invoice",
            "Clothing Store Bill",
            "Shoe Purchase Bill",
            "Gift Voucher",
            "Gift Receipt",
            "Festival Purchase Invoice",
            "Electronics Exchange Proof",
            "Cashback Proof Screenshot",
            "Cashback Reward Summary",
            "Bank Cashback Notification",
            "Reward Points Statement",
            "Credit Card Reward Summary",
            "Loan Closure NOC",
            "EMI Schedule",
            "Credit Report (CIBIL)",
            "CIBIL Login Screenshot",
            "Bank Complaint Acknowledgment",
            "Grievance Closure Letter",
            "Consumer Forum Complaint Copy",
            "Consumer Forum Judgment Copy",
            "RTI for Consumer Matter",
            "Product Complaint Form",
            "Warranty Claim Receipt",
            "Replacement Invoice",
            "Amazon Order Invoice",
            "Flipkart Order Invoice",
            "Myntra Order Invoice",
            "Ajio Order Invoice",
            "Swiggy Bill",
            "Zomato Bill",
            "Paytm Order Invoice",
            "E-Commerce Wallet Statement",
            "Google Pay History",
            "PhonePe History",
            "UPI App Screenshot",
            "Bank App Screenshot",
            "Wallet Balance Screenshot",
            "Cashback History",
            "Amazon Prime Renewal Proof",
            "Netflix Renewal Proof",
            "Spotify Renewal Proof",
            "YouTube Premium Proof",
            "ChatGPT Subscription Proof",
            "Adobe Creative Cloud Proof",
            "Canva Renewal Proof",
            "Figma Renewal Proof",
            "OTT Password Sheet",
            "Investment Goals Document",
            "Savings Tracker Sheet",
            "Monthly Budget Planner",
            "Tax Deduction Summary",
            "Expense Tracker Sheet",
            "Financial Planner File",
            "Bank Reconciliation Sheet",
            "Monthly Bills Record",
            "Document Backup List",
            "Digital Asset Backup List",
            "Emergency Info Sheet",
            "Important Numbers Sheet",
            "Family Tree Chart",
            "Personal Notes Summary",
            "DocKeep App Settings Backup"
        )

        // Create autocomplete adapter
        val autocompleteAdapter = DocumentAutocompleteAdapter(
            this,
            R.layout.dropdown_item,
            documents,
            commonDocumentNames
        )
        documentNameEditText.setAdapter(autocompleteAdapter)

        // Add suggestion buttons (show first 15 for better UI)
        suggestionsButtonContainer.removeAllViews()
        commonDocumentNames.take(15).forEach { documentName ->
            val button = com.google.android.material.button.MaterialButton(this).apply {
                text = documentName
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 2, 0)
                }

                // Ledger chip: 2px rule, square, no fill, tracked micro-caps.
                cornerRadius = 0
                strokeWidth = (2 * resources.displayMetrics.density).toInt()
                setStrokeColorResource(R.color.ledger_rule)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                elevation = 0f
                stateListAnimator = null
                insetTop = 0
                insetBottom = 0
                minHeight = 0
                minWidth = 0
                isAllCaps = true
                letterSpacing = 0.1f
                textSize = 10.5f
                typeface = androidx.core.content.res.ResourcesCompat.getFont(
                    this@MainActivity, R.font.archivo_semibold
                )
                setTextColor(
                    ContextCompat.getColor(this@MainActivity, R.color.ledger_text)
                )
                
                // Check if document already exists
                val isDocumentExists = documents.any { it.name.equals(documentName, ignoreCase = true) }
                if (isDocumentExists) {
                    isEnabled = false
                    alpha = 0.5f
                } else {
                    setOnClickListener {
                        documentNameEditText.setText(documentName)
                        documentNameEditText.setSelection(documentName.length)
                    }
                }
            }
            
            suggestionsButtonContainer.addView(button)
        }

        builder.setView(dialogLayout)

        val dialog = builder.create()

        // Dock the sheet to the bottom edge, full width, with no window
        // insets: the design's sheets are flush panels, not floating cards.
        dialog.window?.let { window ->
            window.setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
            )
            window.setGravity(android.view.Gravity.BOTTOM)
            window.setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val sheetCreateLabel = dialogLayout.findViewById<TextView>(R.id.sheetCreateLabel)

        // "File under". The row was in the layout from the start but nothing
        // ever filled it, so the choice the design offers could not be made.
        val fileUnderChips =
            dialogLayout.findViewById<com.google.android.material.chip.ChipGroup>(R.id.fileUnderChips)
        var fileUnderPersonId: Long? = null

        lifecycleScope.launch {
            val people = viewModel.getAllPeopleSync()
            fileUnderChips.removeAllViews()

            // The owner first and selected, since most documents are theirs.
            val mine = layoutInflater.inflate(
                R.layout.item_ledger_tag, fileUnderChips, false
            ) as TextView
            mine.text = currentUserName()
            mine.isSelected = true
            fileUnderChips.addView(mine)

            val chips = mutableListOf(mine)
            for (person in people) {
                val chip = layoutInflater.inflate(
                    R.layout.item_ledger_tag, fileUnderChips, false
                ) as TextView
                chip.text = person.name
                fileUnderChips.addView(chip)
                chips.add(chip)
            }

            chips.forEachIndexed { index, chip ->
                chip.setOnClickListener {
                    chips.forEach { it.isSelected = false }
                    chip.isSelected = true
                    fileUnderPersonId = if (index == 0) null else people[index - 1].id
                }
            }
        }

        // The Create bar names what it will make, as the design shows.
        fun refreshCreateLabel() {
            val typed = documentNameEditText.text.toString().trim()
            sheetCreateLabel.text = if (typed.isEmpty()) {
                getString(R.string.create_document)
            } else {
                getString(R.string.ledger_sheet_create, typed.uppercase())
            }
        }
        refreshCreateLabel()
        documentNameEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = refreshCreateLabel()
        })

        dialogLayout.findViewById<View>(R.id.sheetClose).setOnClickListener {
            dialog.dismiss()
        }

        dialogLayout.findViewById<View>(R.id.sheetCreate).setOnClickListener {
            val documentName = documentNameEditText.text.toString().trim()
            when {
                documentName.isEmpty() ->
                    Toast.makeText(this, R.string.document_name_required, Toast.LENGTH_SHORT).show()
                documents.any { it.name.equals(documentName, ignoreCase = true) } ->
                    Toast.makeText(this, R.string.ledger_document_exists, Toast.LENGTH_SHORT).show()
                else -> {
                    createDocument(documentName, fileUnderPersonId)
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
        
        // Remove the automatic dropdown showing to comply with user preference
        // Dropdown should only appear after user begins typing
        
        // Add listener to show message when no suggestions are available
        documentNameEditText.setOnItemClickListener { _, _, position, _ ->
            val selectedDocument = autocompleteAdapter.getItem(position)
            if (selectedDocument != null) {
                documentNameEditText.setText(selectedDocument)
                documentNameEditText.setSelection(selectedDocument.length)
            }
        }
        
        // Add text watcher to show/hide no suggestions message
        documentNameEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: Editable?) {
                // Hide the no suggestions text by default
                noSuggestionsText.visibility = View.GONE
                
                // Check if we have suggestions
                val text = s.toString()
                if (text.isNotEmpty()) {
                    // Filter the suggestions
                    val matchingSuggestions = commonDocumentNames.filter { 
                        it.lowercase().startsWith(text.lowercase()) 
                    }
                    
                    // Show message if no suggestions found
                    if (matchingSuggestions.isEmpty()) {
                        noSuggestionsText.visibility = View.VISIBLE
                    }
                }
            }
        })
    }

    // Extension function to set chip style
    private fun com.google.android.material.chip.Chip.setChipStyle() {
        setChipBackgroundColorResource(R.color.chip_background_color)
        // Resources#getColorStateList(int, Theme) is API 23; this app
        // ships to 21, where that overload does not exist.
        setTextColor(
            androidx.core.content.ContextCompat.getColorStateList(
                context, R.color.chip_text_color
            )
        )
        setChipStrokeColorResource(R.color.chip_stroke_color)
        chipStrokeWidth = 2f
        chipCornerRadius = 24f
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextAppearance(R.style.CustomChipChoice)
    }

    /**
     * Creates a document, optionally filed under a person.
     *
     * @param personId null files it under the phone's owner, which is what the
     *   quick starts and a plain create both want.
     */
    private fun createDocument(name: String, personId: Long? = null) {
        Log.d("MainActivity", "CreateDocument called with name: $name, person: $personId")

        val colorIndex = ColorUtils.getColorIndexForName(name)
        val document = Document(
            name = name,
            colorIndex = colorIndex,
            personId = personId
        )

        viewModel.insertDocument(document)
    }

    private val importFileLauncher = registerForActivityResult<Intent, ActivityResult>(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("MainActivity", "ImportFileLauncher result: ${result.resultCode}")

        if (result.resultCode == RESULT_OK) {
            result.data?.data?.also { uri ->
                Log.d("MainActivity", "Selected file URI: $uri")
                // Check if the selected file is a ZIP file
                val fileName = getFileNameFromUri(uri)
                if (fileName != null && fileName.lowercase().endsWith(".zip")) {
                    // Handle the selected ZIP file
                    handleImportedZipFile(uri)
                } else {
                    // Show error message if not a ZIP file
                    Toast.makeText(this, "Please select a ZIP file", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            Log.d("MainActivity", "Import cancelled or failed")
            Toast.makeText(this, "Import cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    // Helper function to get file name from URI
    private fun getFileNameFromUri(uri: Uri): String? {
        var fileName: String? = null
        val cursor = contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    fileName = it.getString(nameIndex)
                }
            }
        }
        return fileName
    }

    private fun handleImportedZipFile(uri: Uri) {
        Log.d("MainActivity", "HandleImportedZipFile called with URI: $uri")

        try {
            // Show a progress dialog
            showProgressDialog()

            // Process the ZIP file in a background thread
            Thread {
                try {
                    val documentsAddedCount = processImportedZipFile(uri)

                    // Update UI on main thread
                    runOnUiThread {
                        hideProgressDialog()
                        if (documentsAddedCount > 0) {
                            Toast.makeText(this, "Documents imported successfully! $documentsAddedCount documents added.", Toast.LENGTH_LONG).show()
                            // Refresh the document list
                            loadDocuments()
                        } else if (documentsAddedCount == 0) {
                            Toast.makeText(this, "No new documents to import.", Toast.LENGTH_LONG).show()
                        }
                        else {
                            Toast.makeText(this, "Failed to import documents", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error importing documents", e)
                    runOnUiThread {
                        hideProgressDialog()
                        Toast.makeText(this, "Error importing documents: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error handling ZIP file", e)
            Toast.makeText(this, "Error handling ZIP file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun processImportedZipFile(uri: Uri): Int {
        Log.d("MainActivity", "ProcessImportedZipFile called with URI: $uri")

        return try {
            // Get input stream for the ZIP file
            contentResolver.openInputStream(uri)?.use { input ->
                Log.d("MainActivity", "Successfully opened input stream for ZIP file")

                // Create document entries map to track which documents we need to create
                // Structure: PersonName -> DocumentName -> List<ImagePaths>
                val personDocumentEntries = mutableMapOf<String, MutableMap<String, MutableList<String>>>()

                // Extract the ZIP file
                val zipInputStream = ZipInputStream(input)
                var entry: ZipEntry?

                while (zipInputStream.nextEntry.also { entry = it } != null) {
                    entry?.let { zipEntry ->
                        Log.d("MainActivity", "Processing ZIP entry: ${zipEntry.name}")

                        // Process both files and directories
                        // Get the path structure
                        val entryName = zipEntry.name
                        val parts = entryName.split("/")

                        // If importing personal only, skip FriendsAndFamily entries
                        if (isImportingPersonalOnly && parts.size >= 1 && parts[0] == "FriendsAndFamily") {
                            Log.d("MainActivity", "Skipping FriendsAndFamily entry during personal-only import: $entryName")
                            return@let
                        }

                        if (parts.size >= 3 && parts[0] == "FriendsAndFamily") {
                            // Structure: FriendsAndFamily/PersonName/DocumentName/[image.jpg or empty folder]
                            val personName = parts[1]
                            val documentName = parts[2]
                            
                            // Add person to entries map if not already there
                            if (!personDocumentEntries.containsKey(personName)) {
                                personDocumentEntries[personName] = mutableMapOf()
                            }

                            // Add document to person's entries map if not already there
                            if (!personDocumentEntries[personName]!!.containsKey(documentName)) {
                                personDocumentEntries[personName]!![documentName] = mutableListOf()
                            }
                            
                            // If this is a file (not a directory), process it
                            if (!zipEntry.isDirectory && parts.size >= 4) {
                                val fileName = parts[3]

                                Log.d("MainActivity", "Found person: $personName, document: $documentName, file: $fileName")

                                // Only create document folder if we actually have a file to extract
                                val documentDir = File(getExternalFilesDir(null), "Documents/$documentName")
                                if (documentDir.mkdirs()) {
                                    Log.d("MainActivity", "Created document folder: ${documentDir.absolutePath}")
                                }

                                // Extract the file
                                val outputFile = File(documentDir, fileName)
                                FileOutputStream(outputFile).use { output ->
                                    val buffer = ByteArray(8192)
                                    var length: Int
                                    while (zipInputStream.read(buffer).also { length = it } > 0) {
                                        output.write(buffer, 0, length)
                                    }
                                }

                                // Add image path to document entries
                                personDocumentEntries[personName]!![documentName]?.add(outputFile.absolutePath)
                                Log.d("MainActivity", "Extracted file to: ${outputFile.absolutePath}")
                            } else if (zipEntry.isDirectory && parts.size >= 3) {
                                Log.d("MainActivity", "Found person directory: $personName, document: $documentName")
                                // For directories, we don't need to create anything yet
                                // The document will be created when we process the actual files
                                // Make sure the document entry exists so it gets processed later
                                if (!personDocumentEntries[personName]!!.containsKey(documentName)) {
                                    personDocumentEntries[personName]!![documentName] = mutableListOf()
                                }
                            }
                        } else if (parts.size >= 1 && parts[0] != "FriendsAndFamily") {
                            // Structure: UserName's DocumentName/[image.jpg or empty folder] (main user documents)
                            // Or: DocumentName/[image.jpg or empty folder] (legacy format)
                            val documentFolder = parts[0]
                            
                            // Skip empty parts or root directory entries
                            if (documentFolder.isEmpty()) {
                                return@let
                            }
                            
                            // Skip FriendsAndFamily root directory
                            if (documentFolder == "FriendsAndFamily") {
                                return@let
                            }
                            
                            // Check if this is the new format with user name prefix
                            val documentName = if (documentFolder.contains("'s ")) {
                                // Extract the actual document name by removing the "UserName's " prefix
                                documentFolder.substring(documentFolder.indexOf("'s ") + 3)
                            } else {
                                // Legacy format - use the folder name as document name
                                documentFolder
                            }

                            Log.d("MainActivity", "Found main user document: $documentName")

                            // Add to main user entries (use "MainUser" as person name)
                            val personName = "MainUser"
                            if (!personDocumentEntries.containsKey(personName)) {
                                personDocumentEntries[personName] = mutableMapOf()
                            }

                            // Add document to entries map if not already there
                            if (!personDocumentEntries[personName]!!.containsKey(documentName)) {
                                personDocumentEntries[personName]!![documentName] = mutableListOf()
                            }
                            
                            // If this is a file (not a directory), process it
                            if (!zipEntry.isDirectory && parts.size >= 2) {
                                val fileName = parts[1]

                                Log.d("MainActivity", "Found main user document: $documentName, file: $fileName")

                                // Only create document folder if we actually have a file to extract
                                val documentDir = File(getExternalFilesDir(null), "Documents/$documentName")
                                if (documentDir.mkdirs()) {
                                    Log.d("MainActivity", "Created document folder: ${documentDir.absolutePath}")
                                }

                                // Extract the file
                                val outputFile = File(documentDir, fileName)
                                FileOutputStream(outputFile).use { output ->
                                    val buffer = ByteArray(8192)
                                    var length: Int
                                    while (zipInputStream.read(buffer).also { length = it } > 0) {
                                        output.write(buffer, 0, length)
                                    }
                                }

                                // Add image path to document entries
                                personDocumentEntries[personName]!![documentName]?.add(outputFile.absolutePath)
                                Log.d("MainActivity", "Extracted file to: ${outputFile.absolutePath}")
                            } else if (zipEntry.isDirectory && parts.size >= 1) {
                                Log.d("MainActivity", "Found main user document directory: $documentName")
                                // For directories, we don't need to create anything yet
                                // The document will be created when we process the actual files
                                // Add an empty list for this document to ensure it gets created even if empty
                                if (!personDocumentEntries[personName]!!.containsKey(documentName)) {
                                    personDocumentEntries[personName]!![documentName] = mutableListOf()
                                }
                            }
                        } else {
                            Log.d("MainActivity", "Skipping entry with insufficient path parts: $entryName")
                        }
                    }
                }

                zipInputStream.close()
                Log.d("MainActivity", "Finished extracting ZIP file. Found ${personDocumentEntries.size} persons")

                var totalDocumentsCreated = 0

                // Process each person and their documents
                for ((personName, documentEntries) in personDocumentEntries) {
                    Log.d("MainActivity", "Processing person: $personName with ${documentEntries.size} documents")

                    // If importing personal only, skip person-specific documents
                    if (isImportingPersonalOnly && personName != "MainUser") {
                        Log.d("MainActivity", "Skipping person-specific documents during personal-only import: $personName")
                        continue
                    }

                    if (personName == "MainUser") {
                        // Handle main user documents
                        for ((documentName, imagePaths) in documentEntries) {
                            Log.d("MainActivity", "Processing main user document: $documentName with ${imagePaths.size} images")

                            // Only create document if it has images or if it's an empty document that was explicitly exported
                            // Check if this is a valid document to import (has images or was explicitly created as an empty document)
                            if (imagePaths.isNotEmpty()) {
                                // Check if document already exists (synchronous call)
                                val document = runBlocking {
                                    viewModel.getDocumentByNameSync(documentName)
                                }

                                val documentId: Long = if (document == null) {
                                    // Document doesn't exist, create it
                                    val colorIndex = ColorUtils.getColorIndexForName(documentName)
                                    val newDocument = Document(
                                        name = documentName,
                                        colorIndex = colorIndex
                                        // personId is null for main user documents
                                    )

                                    val id = runBlocking {
                                        viewModel.insertDocumentSync(newDocument)
                                    }
                                    Log.d("MainActivity", "Created new main user document: $documentName with ID: $id")
                                    totalDocumentsCreated++
                                    id
                                } else {
                                    Log.d("MainActivity", "Main user document already exists: $documentName with ID: ${document.id}")
                                    document.id
                                }

                                // Add images to the document if we have a valid document ID and there are images
                                if (documentId != -1L && imagePaths.isNotEmpty()) {
                                    Log.d("MainActivity", "Adding ${imagePaths.size} images to document ID: $documentId")
                                    runBlocking {
                                        for ((index, imagePath) in imagePaths.withIndex()) {
                                            val documentImage = DocumentImage(
                                                documentId = documentId,
                                                imagePath = imagePath,
                                                order = index
                                            )
                                            viewModel.insertImageSync(documentImage)
                                            Log.d("MainActivity", "Added image to document: $imagePath")
                                        }
                                    }
                                }
                            } else {
                                Log.d("MainActivity", "Skipping empty main user document: $documentName (no images to import)")
                            }
                        }
                    } else {
                        // Handle person-specific documents
                        // Check if person already exists
                        val personId: Long = runBlocking {
                            val person = viewModel.getPersonByNameSync(personName)
                            if (person == null) {
                                // Person doesn't exist, create them
                                val newPerson = Person(name = personName)
                                val id = viewModel.insertPersonSync(newPerson)
                                Log.d("MainActivity", "Created new person: $personName with ID: $id")
                                return@runBlocking id
                            } else {
                                Log.d("MainActivity", "Person already exists: $personName with ID: ${person.id}")
                                return@runBlocking person.id
                            }
                        }

                        // Process documents for this person
                        for ((documentName, imagePaths) in documentEntries) {
                            Log.d("MainActivity", "Processing person document: $documentName with ${imagePaths.size} images")

                            // Only create document if it has images or if it's an empty document that was explicitly exported
                            // Check if this is a valid document to import (has images or was explicitly created as an empty document)
                            if (imagePaths.isNotEmpty()) {
                                // Check if document already exists for this person (synchronous call)
                                val document = runBlocking {
                                    viewModel.getDocumentByNameAndPersonSync(documentName, personId)
                                }

                                val documentId: Long = if (document == null) {
                                    // Document doesn't exist, create it
                                    val colorIndex = ColorUtils.getColorIndexForName(documentName)
                                    val newDocument = Document(
                                        name = documentName,
                                        colorIndex = colorIndex,
                                        personId = personId  // Associate with the person
                                    )

                                    val id = runBlocking {
                                        viewModel.insertDocumentSync(newDocument)
                                    }
                                    Log.d("MainActivity", "Created new person document: $documentName with ID: $id")
                                    totalDocumentsCreated++
                                    id
                                } else {
                                    Log.d("MainActivity", "Person document already exists: $documentName with ID: ${document.id}")
                                    document.id
                                }

                                // Add images to the document if we have a valid document ID and there are images
                                if (documentId != -1L && imagePaths.isNotEmpty()) {
                                    Log.d("MainActivity", "Adding ${imagePaths.size} images to document ID: $documentId")
                                    runBlocking {
                                        for ((index, imagePath) in imagePaths.withIndex()) {
                                            val documentImage = DocumentImage(
                                                documentId = documentId,
                                                imagePath = imagePath,
                                                order = index
                                            )
                                            viewModel.insertImageSync(documentImage)
                                            Log.d("MainActivity", "Added image to document: $imagePath")
                                        }
                                    }
                                }
                            } else {
                                Log.d("MainActivity", "Skipping empty person document: $documentName (no images to import)")
                            }
                        }
                    }
                }

                // Reset the import flag after processing
                isImportingPersonalOnly = false
                
                totalDocumentsCreated
            }.also { result ->
                if (result == null || result < 0) {
                    Log.e("MainActivity", "Error processing ZIP file")
                }
            } ?: -1
        } catch (e: Exception) {
            Log.e("MainActivity", "Error processing ZIP file", e)
            return -1
        }
    }

    private fun importDocuments() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "application/zip"
        importFileLauncher.launch(intent)
    }

    private fun showImportOptionsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_import_options, null)
        
        val importAllOption = dialogView.findViewById<View>(R.id.importAllOption)
        val importPersonalOption = dialogView.findViewById<View>(R.id.importPersonalOption)
        val cancelButton = dialogView.findViewById<View>(R.id.cancelButton)
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
            .dockAsLedgerSheet()
        
        importAllOption.setOnClickListener {
            dialog.dismiss()
            isImportingPersonalOnly = false
            importDocuments()
        }
        
        importPersonalOption.setOnClickListener {
            dialog.dismiss()
            isImportingPersonalOnly = true
            importDocuments()
        }
        
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun showProgressDialog() {
        // A dialog cannot be attached to a window that is going away.
        if (isFinishing || isDestroyed) return

        // Built fresh each time: a cached dialog outlives the activity it was
        // created against and throws BadTokenException on the next show().
        hideProgressDialog()
        progressDialog = AlertDialog.Builder(this)
            .setView(R.layout.dialog_progress)
            .setCancelable(false)
            .create()
        runCatching { progressDialog?.show() }
            .onFailure { Log.w("MainActivity", "Could not show progress", it) }
    }

    private fun hideProgressDialog() {
        val dialog = progressDialog ?: return
        progressDialog = null
        // dismiss() on a dialog whose activity has gone throws
        // IllegalArgumentException: View not attached to window manager.
        runCatching { if (dialog.isShowing) dialog.dismiss() }
            .onFailure { Log.w("MainActivity", "Could not dismiss progress", it) }
    }

    private val profileLauncher = registerForActivityResult<Intent, ActivityResult>(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Profile was updated, refresh the user name display and profile photo
            updateUserNameDisplay()
            // Also refresh the sidebar menu if it's open
            if (sidebarLayout.isVisible) {
                updateUserNameDisplay()
            }
        }
    }

    private fun redirectToOnboarding() {
        // Debug log
        Log.d("MainActivity", "Redirecting to onboarding")

        // Redirect to onboarding activity
        val intent = Intent(this, OnboardingActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    // Keep track of the last loaded profile photo path and modification time
    private var lastProfilePhotoPath: String? = null
    private var lastProfilePhotoModified: Long = 0

    private fun updateUserNameDisplay() {
        // Load username from multiple sources, with priority:
        // 1. Profile preferences (most recent updates)
        // 2. Onboarding preferences (initial setup)
        // 3. Default "User"
        
        val profilePrefs = getSharedPreferences("profile_prefs", MODE_PRIVATE)
        val onboardingPrefs = getSharedPreferences("onboarding_prefs", MODE_PRIVATE)
        
        val userName = profilePrefs.getString("username", null) 
            ?: onboardingPrefs.getString("user_name", null) 
            ?: "User"
            
        userNameDisplay.text = userName

        // The drawer avatar is a letter tile by default, coloured from the
        // same A-Z table as the document cells; a saved photo covers it.
        val letter = userName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        userNameLetter.text = letter
        val avatarColors = ColorUtils.getPlaceholderColorScheme(this, userName)
        userNameLetter.background?.mutate()?.setColorFilter(
            avatarColors.backgroundColor,
            android.graphics.PorterDuff.Mode.SRC_IN
        )
        userNameLetter.setTextColor(avatarColors.textColor)

        // Load profile photo if exists
        val profilePhotoPath = profilePrefs.getString("profile_photo_path", null)
        if (profilePhotoPath != null) {
            val profilePhotoFile = File(profilePhotoPath)
            if (profilePhotoFile.exists()) {
                // Check if the file has been modified since last load
                val lastModified = profilePhotoFile.lastModified()
                if (profilePhotoPath != lastProfilePhotoPath || lastModified != lastProfilePhotoModified) {
                    // File has changed or is new, load it
                    try {
                        val bitmap = when {
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
                                val source = ImageDecoder.createSource(contentResolver, Uri.fromFile(profilePhotoFile))
                                ImageDecoder.decodeBitmap(source)
                            }
                            else -> {
                                @Suppress("DEPRECATION")
                                MediaStore.Images.Media.getBitmap(contentResolver, Uri.fromFile(profilePhotoFile))
                            }
                        }
                        userNameIcon.setImageBitmap(bitmap)
                        userNameIcon.visibility = View.VISIBLE

                        // Update tracking variables
                        lastProfilePhotoPath = profilePhotoPath
                        lastProfilePhotoModified = lastModified
                    } catch (e: IOException) {
                        Log.e("MainActivity", "Error loading profile image", e)
                        userNameIcon.visibility = View.GONE
                        
                        // Reset tracking variables on error
                        lastProfilePhotoPath = null
                        lastProfilePhotoModified = 0
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Unexpected error loading profile image", e)
                        userNameIcon.visibility = View.GONE
                        
                        // Reset tracking variables on error
                        lastProfilePhotoPath = null
                        lastProfilePhotoModified = 0
                    }
                } else {
                    // Do nothing if file has not changed
                }
                // If file hasn't changed, we don't need to reload it
            } else {
                // If profile photo file doesn't exist, fall back to the tile
                userNameIcon.visibility = View.GONE
                
                // Reset tracking variables
                lastProfilePhotoPath = null
                lastProfilePhotoModified = 0
            }
        } else {
            // If no profile photo path is saved, fall back to the tile
            userNameIcon.visibility = View.GONE
            
            // Reset tracking variables
            lastProfilePhotoPath = null
            lastProfilePhotoModified = 0
        }
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
        val themeMode: Int
        if (isDarkTheme) {
            themeMode = AppCompatDelegate.MODE_NIGHT_YES
        } else {
            themeMode = AppCompatDelegate.MODE_NIGHT_NO
        }
        AppCompatDelegate.setDefaultNightMode(themeMode)
    }
    
    private fun toggleTheme() {
        // Toggle the theme
        isDarkTheme = !isDarkTheme
        
        // Save the preference
        saveThemePreference()
        
        // Apply the theme
        updateTheme()
        
        // Update the theme button icon and text
        updateThemeButton()
        
        // Recreate the activity to apply the theme change
        recreate()
    }
    
    private fun showExportOptionsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_export_options, null)
        
        val exportAllOption = dialogView.findViewById<View>(R.id.exportAllOption)
        val exportPersonalOption = dialogView.findViewById<View>(R.id.exportPersonalOption)
        val cancelButton = dialogView.findViewById<View>(R.id.cancelButton)
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
            .dockAsLedgerSheet()
        
        exportAllOption.setOnClickListener {
            dialog.dismiss()
            exportAllDocuments(includeFamilyAndFriends = true)
        }
        
        exportPersonalOption.setOnClickListener {
            dialog.dismiss()
            exportAllDocuments(includeFamilyAndFriends = false)
        }
        
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    /**
     * Exports the vault as a ZIP and hands it to the share sheet.
     *
     * Everything — reading the documents, gathering their scans, and building
     * the archive — happens on the IO dispatcher. The previous version built
     * the ZIP inside runOnUiThread, which froze the UI for the whole
     * compression and showed up as an ANR rather than as a slow export.
     *
     * @param includeFamilyAndFriends false exports only the owner's own shelf.
     */
    private fun exportAllDocuments(includeFamilyAndFriends: Boolean = true) {
        if (isExportInProgress) {
            Toast.makeText(this, R.string.ledger_export_in_progress, Toast.LENGTH_SHORT).show()
            return
        }

        isExportInProgress = true
        showProgressDialog()

        lifecycleScope.launch {
            val outcome = runCatching {
                withContext(Dispatchers.IO) { buildExportArchive(includeFamilyAndFriends) }
            }

            // The dialog is dismissed only once the archive is actually built,
            // and always — including on failure, which used to leave it up.
            hideProgressDialog()
            isExportInProgress = false

            if (isFinishing || isDestroyed) return@launch

            val zip = outcome.getOrNull()
            when {
                outcome.isFailure -> {
                    Log.e("MainActivity", "Export failed", outcome.exceptionOrNull())
                    Toast.makeText(
                        this@MainActivity,
                        R.string.ledger_export_failed,
                        Toast.LENGTH_LONG
                    ).show()
                }
                zip == null ->
                    Toast.makeText(
                        this@MainActivity,
                        R.string.ledger_export_nothing,
                        Toast.LENGTH_SHORT
                    ).show()
                else -> shareExport(zip)
            }
        }
    }

    /**
     * Gathers every document and writes the archive. Runs off the main thread.
     *
     * @return the archive, or null when there was nothing to export.
     */
    private suspend fun buildExportArchive(includeFamilyAndFriends: Boolean): File? {
        val userName = currentUserName()

        val all = viewModel.getAllDocumentsSync()
        val documents = if (includeFamilyAndFriends) all else all.filter { it.personId == null }
        if (documents.isEmpty()) return null

        val peopleById = viewModel.getAllPeopleSync().associateBy { it.id }

        // LinkedHashMap so the archive keeps the order the grid shows.
        val mine = LinkedHashMap<String, List<String>>()
        val byPerson = LinkedHashMap<String, MutableMap<String, List<String>>>()

        for (document in documents) {
            // Notes have no file behind them, and a scan whose file has gone
            // would only produce an empty entry.
            val paths = viewModel.getImagesForDocumentSync(document.id)
                .filter { it.isImage }
                .map { it.imagePath }
                .filter { File(it).exists() }

            val person = document.personId?.let { peopleById[it] }
            if (person == null) {
                mine[uniqueFolderName(mine.keys, document.name)] = paths
            } else {
                val shelf = byPerson.getOrPut(person.name) { LinkedHashMap() }
                shelf[uniqueFolderName(shelf.keys, document.name)] = paths
            }
        }

        // People with nothing filed still appear, so the structure round-trips
        // through import.
        if (includeFamilyAndFriends) {
            for (person in peopleById.values) {
                byPerson.getOrPut(person.name) { LinkedHashMap() }
            }
        }

        return FileUtils.createHierarchicalZipFile(
            applicationContext,
            userName,
            mine,
            byPerson.mapValues { it.value.toMap() }
        )
    }

    /**
     * Makes a document's folder name unique within its shelf.
     *
     * Documents are keyed by name, so two called "Passport" used to collapse
     * into one and the first one's scans never reached the archive at all.
     */
    private fun uniqueFolderName(taken: Set<String>, name: String): String {
        if (name !in taken) return name
        var n = 2
        while ("$name ($n)" in taken) n++
        return "$name ($n)"
    }

    private fun shareExport(zipFile: File) {
        val uri = runCatching { FileUtils.getUriForFile(this, zipFile) }.getOrNull()
        if (uri == null) {
            Toast.makeText(this, R.string.ledger_export_failed, Toast.LENGTH_LONG).show()
            return
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
            type = "application/zip"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // A device with nothing able to receive a ZIP would otherwise throw
        // ActivityNotFoundException straight out of the export.
        runCatching {
            startActivity(
                Intent.createChooser(shareIntent, getString(R.string.ledger_export_via))
            )
        }.onFailure {
            Log.e("MainActivity", "No app could receive the export", it)
            Toast.makeText(this, R.string.ledger_export_no_app, Toast.LENGTH_LONG).show()
        }
    }
    
    private fun openFamilyFriends() {
        val intent = Intent(this, FamilyFriendsActivity::class.java)
        startActivity(intent)
    }

    private fun openProfile() {
        val intent = Intent(this, ProfileActivity::class.java)
        startActivity(intent)
    }

    private fun performSearch(query: String) {
        // Drop the previous query before starting another. Without this every
        // keystroke left its own live query attached: typing "passport" ran
        // eight of them at once, every later database change fanned out to all
        // eight, and because the older ones still emitted, whichever finished
        // last won — so the grid could show results for a prefix the user had
        // already typed past.
        detachSearchObserver()

        if (query.isEmpty()) {
            loadDocuments()
            return
        }

        // Full text: the name, any note in the document, and whatever OCR
        // read off its scans.
        val liveData = viewModel.searchDocumentsFullText(query)
        val observer = Observer<List<Document>> { docs ->
            documents.clear()
            documents.addAll(docs)
            updateUI()
            loadDocumentImages(docs)
        }
        liveData.observe(this, observer)
        searchQuery = liveData to observer
    }

    private fun detachSearchObserver() {
        searchQuery?.let { (liveData, observer) -> liveData.removeObserver(observer) }
        searchQuery = null
    }
}