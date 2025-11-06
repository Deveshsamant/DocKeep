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
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.dockeep.app.adapter.DocumentAutocompleteAdapter
import com.dockeep.app.adapter.DocumentItemTouchHelperCallback
import com.dockeep.app.adapter.DragDropDocumentAdapter
import com.dockeep.app.database.Document
import com.dockeep.app.database.Person
import com.dockeep.app.viewmodel.DocumentViewModel
import com.dockeep.app.viewmodel.PersonViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.FloatingActionButton

class PersonDocumentsActivity : AppCompatActivity() {
    companion object {
        private const val PREFS_NAME = "app_prefs"
        private const val THEME_PREF = "is_dark_theme"
    }
    
    private lateinit var documentViewModel: DocumentViewModel
    private lateinit var personViewModel: PersonViewModel
    private lateinit var recyclerView: RecyclerView
    private lateinit var fab: ExtendedFloatingActionButton
    private lateinit var deleteFab: FloatingActionButton
    private lateinit var emptyStateLayout: View
    
    private var documents: MutableList<Document> = mutableListOf()
    private val imageMap = mutableMapOf<Long, String?>()
    private val imageCountMap = mutableMapOf<Long, Int>()
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
        
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupRecyclerView() {
        // Always use 2 cards per row for better visibility of content
        val spanCount = if (resources.configuration.smallestScreenWidthDp >= 600) 3 else 2
        val layoutManager = GridLayoutManager(this, spanCount)
        recyclerView.layoutManager = layoutManager
        
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
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_person_documents, menu)
        updateThemeMenuItem(menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        menu?.let { updateThemeMenuItem(it) }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.action_theme -> {
                toggleTheme()
                true
            }
            R.id.action_about_developer -> {
                startActivity(Intent(this, DeveloperDetailsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun updateThemeMenuItem(menu: Menu) {
        val themeItem = menu.findItem(R.id.action_theme)
        if (isDarkTheme) {
            themeItem.setIcon(R.drawable.dark)
            themeItem.setTitle(R.string.dark_theme)
        } else {
            themeItem.setIcon(R.drawable.light)
            themeItem.setTitle(R.string.light_theme)
        }
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
            supportActionBar?.title = personData?.name?.let { "$it's Documents" } ?: "Person Documents"
        }
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
        imageMap.clear()
        imageCountMap.clear()

        docs.forEach { document ->
            documentViewModel.getImagesForDocument(document.id).observe(this) { images ->
                imageCountMap[document.id] = images.size
                imageMap[document.id] = images.firstOrNull()?.imagePath
                updateUI()
            }
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
}