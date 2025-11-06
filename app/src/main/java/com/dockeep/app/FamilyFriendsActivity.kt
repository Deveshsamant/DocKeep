package com.dockeep.app

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.dockeep.app.adapter.DragDropPersonAdapter
import com.dockeep.app.adapter.PersonItemTouchHelperCallback
import com.dockeep.app.database.Person
import com.dockeep.app.viewmodel.PersonViewModel
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import android.widget.Toast

class FamilyFriendsActivity : AppCompatActivity() {
    companion object {
        private const val PREFS_NAME = "app_prefs"
        private const val THEME_PREF = "is_dark_theme"
    }
    
    private lateinit var viewModel: PersonViewModel
    private lateinit var recyclerView: RecyclerView
    private lateinit var fab: ExtendedFloatingActionButton
    private lateinit var emptyStateLayout: View
    
    private var people: MutableList<Person> = mutableListOf()
    private var dragDropAdapter: DragDropPersonAdapter? = null
    private var isDarkTheme = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Restore theme preference using the same approach as MainActivity
        loadThemePreference()
        updateTheme()
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_family_friends)

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
        recyclerView = findViewById(R.id.recyclerView)
        fab = findViewById(R.id.fab)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)
        
        // Setup toolbar
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupRecyclerView() {
        // Always use 2 cards per row for better visibility of content
        val spanCount = 2
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

    private fun setupViewModel() {
        viewModel = ViewModelProvider(this, PersonViewModel.Factory(application))
            .get(PersonViewModel::class.java)
    }

    private fun setupClickListeners() {
        fab.setOnClickListener {
            showAddPersonDialog()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_family_friends, menu)
        // Update theme icon based on current theme
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
                // Navigate to developer details
                val intent = Intent(this, DeveloperDetailsActivity::class.java)
                startActivity(intent)
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
        
        // Update the theme menu item
        invalidateOptionsMenu()
        
        // Recreate the activity to apply the theme change
        recreate()
    }

    private fun loadPeople() {
        viewModel.getAllPeople().observe(this) { peopleList ->
            people.clear()
            people.addAll(peopleList)
            updateUI()
        }
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

    private fun showAddPersonDialog() {
        val builder = AlertDialog.Builder(this, R.style.CustomDialogTheme)
        val inflater = layoutInflater
        val dialogLayout = inflater.inflate(R.layout.dialog_create_person, null)
        val personNameEditText = dialogLayout.findViewById<TextInputEditText>(R.id.personNameEditText)

        builder.setView(dialogLayout)
            .setPositiveButton(R.string.ok) { _, _ ->
                val personName = personNameEditText.text.toString().trim()
                if (personName.isNotEmpty()) {
                    createPerson(personName)
                } else {
                    Toast.makeText(this, R.string.person_name_required, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.cancel()
            }

        val dialog = builder.create()
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

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}