package com.dockeep.app

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.dockeep.app.ui.dockAsLedgerSheet
import com.dockeep.app.ui.Edge
import com.dockeep.app.utils.AppLock
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.dockeep.app.adapter.DragDropPersonAdapter
import com.dockeep.app.adapter.PersonItemTouchHelperCallback
import com.dockeep.app.database.Person
import com.dockeep.app.viewmodel.PersonViewModel
import android.widget.TextView
import com.dockeep.app.ui.LedgerGridDecoration
import com.dockeep.app.ui.LedgerNav
import com.dockeep.app.viewmodel.DocumentViewModel
import android.widget.Toast

class FamilyFriendsActivity : AppCompatActivity() {
    companion object {
        private const val PREFS_NAME = "app_prefs"
        private const val THEME_PREF = "is_dark_theme"
    }
    
    private lateinit var viewModel: PersonViewModel
    private lateinit var documentViewModel: DocumentViewModel
    private lateinit var recyclerView: RecyclerView
    private lateinit var fab: View
    private lateinit var emptyStateLayout: View
    private lateinit var backButton: View
    private lateinit var peopleSubtitle: TextView
    private lateinit var navDocs: View
    private lateinit var navPeople: View
    private lateinit var navYou: View
    
    private var people: MutableList<Person> = mutableListOf()
    private var dragDropAdapter: DragDropPersonAdapter? = null
    private var isDarkTheme = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Restore theme preference using the same approach as MainActivity
        loadThemePreference()
        updateTheme()
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_family_friends)
        Edge.fit(this, findViewById(android.R.id.content), findViewById(R.id.bottomNav))

        // Initialize views
        initViews()

        // Setup RecyclerView
        setupRecyclerView()

        // Setup ViewModel
        setupViewModel()

        // Setup click listeners
        setupClickListeners()

        // Load people
        loadPeople()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.peopleRecyclerView)
        fab = findViewById(R.id.fab)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)
        backButton = findViewById(R.id.backButton)
        peopleSubtitle = findViewById(R.id.peopleSubtitle)
        navDocs = findViewById(R.id.navDocs)
        navPeople = findViewById(R.id.navPeople)
        navYou = findViewById(R.id.navYou)

        // This screen owns the People tab in the shared bottom bar.
        LedgerNav.markActive(this, LedgerNav.Tab.PEOPLE)
    }

    private fun setupRecyclerView() {
        // Always use 2 cards per row for better visibility of content
        val spanCount = 2
        val layoutManager = GridLayoutManager(this, spanCount)
        recyclerView.layoutManager = layoutManager
        recyclerView.addItemDecoration(LedgerGridDecoration(this, spanCount))

        // Optimize RecyclerView for better long-distance dragging
        // The grid is wrap_content inside a ScrollView, so its height does
        // change with its contents; claiming otherwise mismeasures it.
        recyclerView.setHasFixedSize(false)
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

    private fun setupViewModel() {
        viewModel = ViewModelProvider(this, PersonViewModel.Factory(application))
            .get(PersonViewModel::class.java)
        documentViewModel = ViewModelProvider(this, DocumentViewModel.Factory(application))
            .get(DocumentViewModel::class.java)
    }

    private fun setupClickListeners() {
        fab.setOnClickListener {
            showAddPersonDialog()
        }

        backButton.setOnClickListener { finish() }

        // Docs and You are other destinations; People is this screen.
        navDocs.setOnClickListener { finish() }
        navPeople.setOnClickListener { /* already here */ }
        navYou.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

    }

    /**
     * Header line and the Unassigned row both need document counts, which live
     * in the document store rather than the person store.
     */
    private fun bindCounts() {
        documentViewModel.getAllDocuments().observe(this) { docs ->
            lastDocuments = docs
            refreshHeader()

            // Feed the cells their per-person document colours and counts.
            dragDropAdapter?.setDocumentsByPerson(
                docs.filter { it.personId != null }.groupBy { it.personId!! }
            )

            // The Unassigned row used to sit here. Its Assign action only ever
            // called finish(), so it advertised something the app could not
            // do; filing a document under someone is done from the document
            // itself.
        }
    }

    /** The last document list seen, so the header can be rebuilt from either source. */
    private var lastDocuments: List<com.dockeep.app.database.Document> = emptyList()

    /**
     * Rewrites the header line.
     *
     * People and documents arrive on two independent LiveData streams, and the
     * header needs both. It used to be written only from the document
     * observer, so it printed whatever the people list happened to hold at
     * that instant — reliably "0 people" on a screen showing several.
     */
    private fun refreshHeader() {
        val filed = lastDocuments.count { it.personId != null }
        peopleSubtitle.text = getString(
            R.string.ledger_people_summary,
            resources.getQuantityString(R.plurals.ledger_people_n, people.size, people.size),
            resources.getQuantityString(R.plurals.ledger_filed_n, filed, filed)
        )
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

    private fun toggleTheme() {
        // Toggle the theme
        isDarkTheme = !isDarkTheme
        
        // Save the preference
        saveThemePreference()
        
        // Apply the theme
        updateTheme()
        
        // Recreate the activity to apply the theme change
        recreate()
    }

    private fun loadPeople() {
        viewModel.getAllPeople().observe(this) { peopleList ->
            people.clear()
            people.addAll(peopleList)
            updateUI()
            // The header counts people, so it has to be rewritten here too.
            refreshHeader()
        }
        bindCounts()
    }

    private fun updateUI() {
        if (people.isEmpty()) {
            emptyStateLayout.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyStateLayout.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE

            // Create or update adapter with new data
            if (dragDropAdapter == null) {
                dragDropAdapter = DragDropPersonAdapter(
                    people,
                    onPersonClick = { person ->
                        // Open documents activity for this person
                        openPersonDocuments(person)
                    },
                    onPersonMoved = { updatedPeople ->
                        // Update person order with a copy to avoid ConcurrentModificationException
                        viewModel.updatePersonOrder(updatedPeople.toList())
                    },
                    onPersonDelete = { person ->
                        // This is no longer used
                    }
                )
                recyclerView.adapter = dragDropAdapter

                // Setup drag and drop
                val callback = PersonItemTouchHelperCallback(dragDropAdapter!!)
                val touchHelper = ItemTouchHelper(callback)
                touchHelper.attachToRecyclerView(recyclerView)
            } else {
                // Update existing adapter with new data
                dragDropAdapter!!.updatePeople(people)
            }
        }
    }

    /**
     * The add-person sheet.
     *
     * The layout was redesigned into a Ledger sheet — kicker, title, one
     * field, and its own accent action bar — but this method was still driving
     * the old Material dialog. Three things were wrong at once: the field was
     * looked up as a TextInputEditText when the layout now holds a plain
     * EditText, which threw ClassCastException the moment the sheet opened;
     * the sheet was never docked, so it floated as a centred card; and the
     * confirm/cancel buttons came from AlertDialog, which draws nothing
     * usable against CustomDialogTheme's transparent window.
     */
    private fun showAddPersonDialog() {
        val dialogLayout = layoutInflater.inflate(R.layout.dialog_create_person, null)
        val personNameEditText = dialogLayout.findViewById<EditText>(R.id.personNameEditText)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogLayout)
            .create()
            .dockAsLedgerSheet()

        fun submit() {
            val personName = personNameEditText.text.toString().trim()
            if (personName.isEmpty()) {
                Toast.makeText(this, R.string.person_name_required, Toast.LENGTH_SHORT).show()
                return
            }
            createPerson(personName)
            dialog.dismiss()
        }

        dialogLayout.findViewById<View>(R.id.sheetAddPerson).setOnClickListener { submit() }

        // The field declares imeOptions="actionDone"; without this the key
        // does nothing and the only way on is the button.
        personNameEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit()
                true
            } else {
                false
            }
        }

        dialog.show()
    }

    private fun createPerson(name: String) {
        val person = Person(
            name = name
        )

        viewModel.insertPerson(person)
    }

    private fun deletePerson(person: Person) {
        viewModel.deletePerson(person)
    }

    private fun openPersonDocuments(person: Person) {
        val intent = Intent(this, PersonDocumentsActivity::class.java)
        intent.putExtra("person_id", person.id)
        startActivity(intent)
    }


    override fun onResume() {
        super.onResume()
        // Coming back from recents can land directly on this screen, which
        // would show its contents without the vault ever being unlocked.
        // Finishing returns to the gated home screen, which does the asking.
        if (AppLock.shouldChallenge(this)) finish()
    }

}