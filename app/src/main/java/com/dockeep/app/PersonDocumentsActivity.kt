package com.dockeep.app

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dockeep.app.ui.Edge
import com.dockeep.app.utils.AppLock
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.dockeep.app.adapter.DocumentAutocompleteAdapter
import com.dockeep.app.adapter.DocumentItemTouchHelperCallback
import com.dockeep.app.adapter.DragDropDocumentAdapter
import com.dockeep.app.database.Document
import com.dockeep.app.database.DocumentImage
import com.dockeep.app.database.Person
import com.dockeep.app.viewmodel.DocumentViewModel
import com.dockeep.app.viewmodel.PersonViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.dockeep.app.ui.LedgerGridDecoration
import com.dockeep.app.utils.ColorUtils

class PersonDocumentsActivity : AppCompatActivity() {
    companion object {
        private const val PREFS_NAME = "app_prefs"
        private const val THEME_PREF = "is_dark_theme"
    }
    
    private lateinit var documentViewModel: DocumentViewModel
    private lateinit var personViewModel: PersonViewModel
    private lateinit var recyclerView: RecyclerView
    private lateinit var fab: View
    private lateinit var deleteFab: View
    private lateinit var emptyStateLayout: View
    private lateinit var backButton: View
    private lateinit var renameButton: View
    private lateinit var personTile: TextView
    private lateinit var personNameView: TextView
    private lateinit var personMeta: TextView
    
    private var documents: MutableList<Document> = mutableListOf()
    private val imageMap = mutableMapOf<Long, String?>()
    private val imageCountMap = mutableMapOf<Long, Int>()

    /** Per-document image observers, tracked so they can be detached. */
    private val imageObservers =
        mutableListOf<Pair<LiveData<List<DocumentImage>>, Observer<List<DocumentImage>>>>()
    private var dragDropAdapter: DragDropDocumentAdapter? = null
    
    private var personId: Long = -1
    private var person: Person? = null
    private var isDarkTheme = false
    private var itemTouchHelper: ItemTouchHelper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        loadThemePreference()
        updateTheme()
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_person_documents)
        Edge.fit(this, findViewById(android.R.id.content))

        personId = intent.getLongExtra("person_id", -1)
        if (personId == -1L) {
            finish()
            return
        }

        initViews()
        setupRecyclerView()
        setupViewModels()
        setupClickListeners()
        loadPerson()
        loadDocuments()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerView)
        fab = findViewById(R.id.fab)
        deleteFab = findViewById(R.id.deleteFab)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)
        backButton = findViewById(R.id.backButton)
        renameButton = findViewById(R.id.renameButton)
        personTile = findViewById(R.id.personTile)
        personNameView = findViewById(R.id.personName)
        personMeta = findViewById(R.id.personMeta)

        backButton.setOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        // Always use 2 cards per row for better visibility of content
        val spanCount = if (resources.configuration.smallestScreenWidthDp >= 600) 3 else 2
        val layoutManager = GridLayoutManager(this, spanCount)
        recyclerView.layoutManager = layoutManager
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
    }

    private fun setupViewModels() {
        documentViewModel = ViewModelProvider(this, DocumentViewModel.Factory(application))
            .get(DocumentViewModel::class.java)
            
        personViewModel = ViewModelProvider(this, PersonViewModel.Factory(application))
            .get(PersonViewModel::class.java)
    }

    private fun setupClickListeners() {
        fab.setOnClickListener {
            showCreateDocumentDialog()
        }

        deleteFab.setOnClickListener {
            showDeleteAllConfirmationDialog()
        }

        renameButton.setOnClickListener {
            showRenamePersonDialog()
        }
    }

    /** Renames the shelf. The tile colour follows the new name. */
    private fun showRenamePersonDialog() {
        val current = person ?: return
        val dialogLayout = layoutInflater.inflate(R.layout.dialog_create_person, null)
        val field = dialogLayout.findViewById<android.widget.EditText>(R.id.personNameEditText)
        field.setText(current.name)
        field.setSelection(field.text.length)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogLayout)
            .setPositiveButton(R.string.ok) { _, _ ->
                val newName = field.text.toString().trim()
                if (newName.isEmpty()) {
                    Toast.makeText(this, R.string.person_name_required, Toast.LENGTH_SHORT).show()
                } else {
                    personViewModel.updatePerson(current.copy(name = newName))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.show()
    }

    private fun loadThemePreference() {
        val sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        isDarkTheme = sharedPrefs.getBoolean(THEME_PREF, false)
    }
    
    private fun saveThemePreference() {
        val sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        sharedPrefs.edit().putBoolean(THEME_PREF, isDarkTheme).apply()
    }
    
    private fun updateTheme() {
        val themeMode = if (isDarkTheme) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        AppCompatDelegate.setDefaultNightMode(themeMode)
    }

    private fun toggleTheme() {
        isDarkTheme = !isDarkTheme
        saveThemePreference()
        updateTheme()
        invalidateOptionsMenu()
        recreate()
    }

    private fun loadPerson() {
        personViewModel.getPersonById(personId).observe(this) { personData ->
            person = personData
            bindPersonHeader()
        }
    }

    /** Paints the person tile and the counts line above the grid. */
    private fun bindPersonHeader() {
        val name = person?.name ?: return
        personNameView.text = name.uppercase()

        val letter = name.trim().firstOrNull()?.uppercaseChar()?.toString()
            ?: getString(R.string.placeholder_default_letter)
        personTile.text = letter
        val colors = ColorUtils.getPlaceholderColorScheme(this, name)
        personTile.background?.mutate()?.setColorFilter(
            colors.backgroundColor,
            android.graphics.PorterDuff.Mode.SRC_IN
        )
        personTile.setTextColor(colors.textColor)

        val docCount = documents.size
        val scanCount = imageCountMap.values.sum()
        personMeta.text = getString(R.string.ledger_shelf_meta, docCount, scanCount)
    }

    private fun loadDocuments() {
        documentViewModel.getDocumentsForPerson(personId).observe(this) { docs ->
            val sortedDocs = docs.sortedBy { it.order }
            val isListUpdated = documents.size != sortedDocs.size || documents != sortedDocs
            
            if (isListUpdated) {
                documents.clear()
                documents.addAll(sortedDocs)
                updateUI()
            }
            
            loadDocumentImages(docs)
        }
    }

    private fun loadDocumentImages(docs: List<Document>) {
        // Detach the previous set first. This runs from inside the document
        // observer, so without it every change to the shelf stacked another
        // observer per document and each later edit fired the whole pile.
        imageObservers.forEach { (liveData, observer) -> liveData.removeObserver(observer) }
        imageObservers.clear()

        imageMap.clear()
        imageCountMap.clear()

        docs.forEach { document ->
            val liveData = documentViewModel.getImagesForDocument(document.id)
            val observer = Observer<List<DocumentImage>> { images ->
                imageCountMap[document.id] = images.count { it.isImage }
                imageMap[document.id] = images.firstOrNull { it.isImage }?.imagePath

                // The adapter copies the counts at construction, when they are
                // still empty, so they have to be pushed in as they arrive —
                // otherwise the cells on a person's shelf never showed a scan
                // count at all.
                dragDropAdapter?.setImageCounts(imageCountMap)
                bindPersonHeader()
            }
            liveData.observe(this, observer)
            imageObservers.add(liveData to observer)
        }
    }

    private fun updateUI() {
        val hasDocuments = documents.isNotEmpty()
        emptyStateLayout.visibility = if (hasDocuments) View.GONE else View.VISIBLE
        recyclerView.visibility = if (hasDocuments) View.VISIBLE else View.GONE
        deleteFab.visibility = View.VISIBLE

        if (dragDropAdapter == null) {
            dragDropAdapter = DragDropDocumentAdapter(
                documents,
                imageMap,
                imageCountMap,
                onDocumentClick = { document ->
                    val intent = Intent(this, DocumentDetailActivity::class.java)
                    intent.putExtra("document_id", document.id)
                    startActivity(intent)
                },
                onDocumentMoved = { updatedDocuments ->
                    documentViewModel.updateDocumentOrder(updatedDocuments)
                }
            )
            recyclerView.adapter = dragDropAdapter
            
            val callback = DocumentItemTouchHelperCallback(dragDropAdapter!!)
            itemTouchHelper = ItemTouchHelper(callback)
            itemTouchHelper?.attachToRecyclerView(recyclerView)
        } else {
            dragDropAdapter!!.updateDocuments(documents)
        }
    }

    private fun showCreateDocumentDialog() {
        val builder = AlertDialog.Builder(this, R.style.CustomDialogTheme)
        val inflater = layoutInflater
        val dialogLayout = inflater.inflate(R.layout.dialog_create_document_enhanced, null)
        val documentNameEditText = dialogLayout.findViewById<AutoCompleteTextView>(R.id.documentNameEditText)
        val suggestionsButtonContainer = dialogLayout.findViewById<LinearLayout>(R.id.suggestionsButtonContainer)
        val noSuggestionsText = dialogLayout.findViewById<TextView>(R.id.noSuggestionsText)

        val commonDocumentNames = resources.getStringArray(R.array.document_names).toList()

        val autocompleteAdapter = DocumentAutocompleteAdapter(this, R.layout.dropdown_item, documents, commonDocumentNames)
        documentNameEditText.setAdapter(autocompleteAdapter)

        suggestionsButtonContainer.removeAllViews()
        commonDocumentNames.take(15).forEach { docName ->
            val button = MaterialButton(this).apply {
                text = docName
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 16, 0) }
                
                isEnabled = !documents.any { it.name.equals(docName, ignoreCase = true) }
                alpha = if (isEnabled) 1.0f else 0.5f

                setOnClickListener {
                    documentNameEditText.setText(docName)
                    documentNameEditText.setSelection(docName.length)
                }
            }
            suggestionsButtonContainer.addView(button)
        }

        builder.setView(dialogLayout)
            .setPositiveButton(R.string.ok) { _, _ ->
                val docName = documentNameEditText.text.toString().trim()
                when {
                    docName.isEmpty() -> Toast.makeText(this, R.string.document_name_required, Toast.LENGTH_SHORT).show()
                    documents.any { it.name.equals(docName, ignoreCase = true) } -> Toast.makeText(this, "Document with this name already exists", Toast.LENGTH_SHORT).show()
                    else -> createDocument(docName)
                }
            }
            .setNegativeButton(R.string.cancel, null)

        val dialog = builder.create()
        dialog.show()
        
        documentNameEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s.toString()
                noSuggestionsText.visibility = if (text.isNotEmpty() && commonDocumentNames.none { it.lowercase().startsWith(text.lowercase()) }) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            }
        })
    }

    private fun createDocument(name: String) {
        val colorIndex = com.dockeep.app.utils.ColorUtils.getColorIndexForName(name)
        val document = Document(name = name, colorIndex = colorIndex, personId = personId)
        documentViewModel.insertDocument(document)
    }

    private fun showDeleteAllConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_delete)
            .setMessage("Are you sure you want to delete ${person?.name} and all their documents?")
            .setPositiveButton(R.string.yes) { _, _ -> deletePersonAndDocuments() }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun deletePersonAndDocuments() {
        person?.let {
            personViewModel.deletePerson(it)
            finish()
        }
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