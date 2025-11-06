package com.dockeep.app

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.Intent
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
import androidx.core.view.isVisible
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
import com.dockeep.app.utils.ColorUtils
import com.dockeep.app.utils.FileUtils
import com.dockeep.app.utils.OnboardingManager
import com.dockeep.app.viewmodel.DocumentViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
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
    private lateinit var fab: ExtendedFloatingActionButton
    private lateinit var menuButton: ImageView
    private lateinit var searchButton: ImageView
    private lateinit var searchEditText: TextInputEditText
    private lateinit var searchInputLayout: TextInputLayout
    private lateinit var emptyStateLayout: View
    private lateinit var sidebarLayout: LinearLayout
    private lateinit var sidebarOverlay: View
    private lateinit var userNameDisplay: TextView
    private lateinit var userNameDisplayContainer: LinearLayout
    private lateinit var userNameIcon: ImageView
    private lateinit var themeOption: LinearLayout
    private lateinit var themeIcon: ImageView
    private lateinit var themeText: TextView
    // Add missing sidebar option views
    private lateinit var exportOption: LinearLayout
    private lateinit var importOption: LinearLayout
    private lateinit var familyFriendsOption: LinearLayout
    private lateinit var aboutDeveloperOption: LinearLayout
    
    // Add missing variables for export functionality
    private var isExportInProgress = false
    private var isExportTriggeredByUser = false
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
        menuButton = findViewById(R.id.menuButton)
        searchButton = findViewById(R.id.searchButton)
        searchEditText = findViewById(R.id.searchEditText)
        searchInputLayout = findViewById(R.id.searchInputLayout)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)
        sidebarLayout = findViewById(R.id.sidebarLayout)
        sidebarOverlay = findViewById(R.id.sidebarOverlay)
        userNameDisplay = findViewById(R.id.userNameDisplay)
        userNameDisplayContainer = findViewById(R.id.userNameDisplayContainer)
        userNameIcon = findViewById(R.id.userNameIcon)
        themeOption = findViewById(R.id.themeOption)
        themeIcon = findViewById(R.id.themeIcon)
        themeText = findViewById(R.id.themeText)
        // Initialize missing sidebar option views
        exportOption = findViewById(R.id.exportOption)
        importOption = findViewById(R.id.importOption)
        familyFriendsOption = findViewById(R.id.familyFriendsOption)
        aboutDeveloperOption = findViewById(R.id.aboutDeveloperOption)
    }

    private fun setupViewModels() {
        viewModel = ViewModelProvider(this, DocumentViewModel.Factory(application))[DocumentViewModel::class.java]
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
            }
        )
        recyclerView.adapter = dragDropAdapter

        // Setup item touch helper for drag and drop
        val itemTouchHelper = ItemTouchHelper(DocumentItemTouchHelperCallback(dragDropAdapter!!))
        itemTouchHelper.attachToRecyclerView(recyclerView)
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

        familyFriendsOption.setOnClickListener {
            openFamilyFriends()
            closeSidebar()
        }

        aboutDeveloperOption.setOnClickListener {
            openDeveloperDetails()
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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load theme preference
        loadThemePreference()
        
        updateTheme()

        setContentView(R.layout.activity_main)

        // Initialize views
        initViews()

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
    }

    private fun updateThemeButton() {
        if (isDarkTheme) {
            themeIcon.setImageResource(R.drawable.dark)
            themeText.text = getString(R.string.dark_theme)
        } else {
            themeIcon.setImageResource(R.drawable.light)
            themeText.text = getString(R.string.light_theme)
        }
    }

    private fun loadDocuments() {
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

        imageMap.clear()
        imageCountMap.clear()

        for (document in docs) {
            viewModel.getImagesForDocument(document.id).observe(this) { images ->
                Log.d("MainActivity", "Processing images for document ${document.name}: ${images.size} images")

                imageCountMap[document.id] = images.size

                // Get the first image as thumbnail
                if (images.isNotEmpty()) {
                    imageMap[document.id] = images.first().imagePath
                } else {
                    imageMap[document.id] = null
                }

                updateUI()
            }
        }
    }

    private fun updateUI() {
        if (documents.isEmpty()) {
            emptyStateLayout.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyStateLayout.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE

            // Create or update adapter with new data
            if (dragDropAdapter == null) {
                dragDropAdapter = DragDropDocumentAdapter(
                    documents.toMutableList(),
                    onDocumentClick = { document ->
                        // Open document detail activity for result
                        val intent = Intent(this, DocumentDetailActivity::class.java)
                        intent.putExtra("document_id", document.id)
                        documentDetailLauncher.launch(intent)
                    },
                    onDocumentMoved = { updatedDocuments ->
                        viewModel.updateDocumentOrder(updatedDocuments.toList())
                    }
                )
                recyclerView.adapter = dragDropAdapter

                // Setup drag and drop
                val callback = DocumentItemTouchHelperCallback(dragDropAdapter!!)
                val touchHelper = ItemTouchHelper(callback)
                touchHelper.attachToRecyclerView(recyclerView)
            } else {
                // Update existing adapter with new data
                dragDropAdapter!!.updateDocuments(documents)
            }
        }
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
                    setMargins(0, 0, 16, 0) // Add right margin
                }
                
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
            .setPositiveButton(R.string.ok) { _, _ ->
                val documentName = documentNameEditText.text.toString().trim()
                if (documentName.isNotEmpty()) {
                    // Check if document with same name already exists
                    val isDocumentExists = documents.any { it.name.equals(documentName, ignoreCase = true) }
                    if (isDocumentExists) {
                        Toast.makeText(this, "Document with this name already exists", Toast.LENGTH_SHORT).show()
                    } else {
                        createDocument(documentName)
                    }
                } else {
                    Toast.makeText(this, R.string.document_name_required, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.cancel()
            }

        val dialog = builder.create()
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
        setTextColor(resources.getColorStateList(R.color.chip_text_color, null))
        setChipStrokeColorResource(R.color.chip_stroke_color)
        chipStrokeWidth = 2f
        chipCornerRadius = 24f
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextAppearance(R.style.CustomChipChoice)
    }

    private fun createDocument(name: String) {
        Log.d("MainActivity", "CreateDocument called with name: $name")

        val colorIndex = ColorUtils.getColorIndexForName(name)
        val document = Document(
            name = name,
            colorIndex = colorIndex
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
                                val output = FileOutputStream(outputFile)
                                val buffer = ByteArray(1024)
                                var length: Int

                                while (zipInputStream.read(buffer).also { length = it } > 0) {
                                    output.write(buffer, 0, length)
                                }

                                output.close()

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
                                val output = FileOutputStream(outputFile)
                                val buffer = ByteArray(1024)
                                var length: Int

                                while (zipInputStream.read(buffer).also { length = it } > 0) {
                                    output.write(buffer, 0, length)
                                }

                                output.close()

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
        
        val importAllOption = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.importAllOption)
        val importPersonalOption = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.importPersonalOption)
        val cancelButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.cancelButton)
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
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
        if (progressDialog == null) {
            val builder = AlertDialog.Builder(this)
            builder.setView(R.layout.dialog_progress)
            builder.setCancelable(false)
            progressDialog = builder.create()
        }
        progressDialog?.show()
    }

    private fun hideProgressDialog() {
        progressDialog?.dismiss()
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
                        
                        // Update tracking variables
                        lastProfilePhotoPath = profilePhotoPath
                        lastProfilePhotoModified = lastModified
                    } catch (e: IOException) {
                        Log.e("MainActivity", "Error loading profile image", e)
                        userNameIcon.setImageResource(R.drawable.ic_person)
                        
                        // Reset tracking variables on error
                        lastProfilePhotoPath = null
                        lastProfilePhotoModified = 0
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Unexpected error loading profile image", e)
                        userNameIcon.setImageResource(R.drawable.ic_person)
                        
                        // Reset tracking variables on error
                        lastProfilePhotoPath = null
                        lastProfilePhotoModified = 0
                    }
                } else {
                    // Do nothing if file has not changed
                }
                // If file hasn't changed, we don't need to reload it
            } else {
                // If profile photo file doesn't exist, show default icon
                userNameIcon.setImageResource(R.drawable.ic_person)
                
                // Reset tracking variables
                lastProfilePhotoPath = null
                lastProfilePhotoModified = 0
            }
        } else {
            // If no profile photo path is saved, show default icon
            userNameIcon.setImageResource(R.drawable.ic_person)
            
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
        
        val exportAllOption = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.exportAllOption)
        val exportPersonalOption = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.exportPersonalOption)
        val cancelButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.cancelButton)
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
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
    
    private fun exportAllDocuments(includeFamilyAndFriends: Boolean = true) {
        // Log when export is triggered
        Log.d("MainActivity", "ExportAllDocuments called with includeFamilyAndFriends: $includeFamilyAndFriends")
        
        // Mark that export was triggered by user action
        isExportTriggeredByUser = true
        
        // Prevent multiple simultaneous exports
        if (isExportInProgress) {
            Toast.makeText(this, "Export already in progress", Toast.LENGTH_SHORT).show()
            return
        }
        
        isExportInProgress = true
        
        // Show a progress dialog
        showProgressDialog()
        
        // Get user name for export file naming
        val sharedPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val userName = sharedPrefs.getString("user_name", "User")

        // Run in background thread to avoid blocking UI
        Thread {
            try {
                // Get all documents synchronously
                val allDocuments = runBlocking {
                    withContext(Dispatchers.IO) {
                        viewModel.getAllDocumentsSync()
                    }
                }
                
                // Filter documents based on the includeFamilyAndFriends flag
                val filteredDocuments = if (includeFamilyAndFriends) {
                    allDocuments
                } else {
                    // Only include documents that don't have a personId (main user documents)
                    allDocuments.filter { it.personId == null }
                }
                
                this@MainActivity.runOnUiThread {
                    if (filteredDocuments.isNotEmpty()) {
                        collectDocumentDataForExport(filteredDocuments, userName ?: "User")
                    } else {
                        hideProgressDialog()
                        isExportInProgress = false
                        isExportTriggeredByUser = false
                        Toast.makeText(this@MainActivity, "No documents to export", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error getting documents for export", e)
                this@MainActivity.runOnUiThread {
                    hideProgressDialog()
                    isExportInProgress = false
                    isExportTriggeredByUser = false
                    Toast.makeText(this@MainActivity, "Error preparing export: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
    
    private fun collectDocumentDataForExport(documents: List<Document>, userName: String) {
        Log.d("MainActivity", "CollectDocumentDataForExport called with ${documents.size} documents")
        
        // Check if export was triggered by user
        if (!isExportTriggeredByUser) {
            Log.d("MainActivity", "Export not triggered by user, skipping")
            hideProgressDialog()
            isExportInProgress = false
            return
        }
        
        // Run in background thread
        Thread {
            try {
                // Get documents with images and person information synchronously
                val (mainUserDocuments, personDocuments) = runBlocking {
                    withContext(Dispatchers.IO) {
                        val mainUserDocs = mutableMapOf<String, List<String>>()
                        val personDocs = mutableMapOf<String, MutableMap<String, List<String>>>()
                        
                        // Get all persons first
                        val allPersons = viewModel.getAllPeopleSync()
                        val personMap = allPersons.associateBy { it.id }
                        
                        // Collect all documents (including empty ones)
                        for (document in documents) {
                            val images = viewModel.getImagesForDocumentSync(document.id)
                            // Filter out images that don't actually exist on the file system
                            val existingImagePaths = images.map { documentImage -> documentImage.imagePath }.filter { imagePath ->
                                val imageFile = File(imagePath)
                                imageFile.exists()
                            }
                            
                            if (document.personId == null) {
                                // Main user document
                                mainUserDocs[document.name] = existingImagePaths
                            } else {
                                // Person document - get person name
                                val person = personMap[document.personId]
                                if (person != null) {
                                    val personName = person.name
                                    if (!personDocs.containsKey(personName)) {
                                        personDocs[personName] = mutableMapOf()
                                    }
                                    personDocs[personName]!![document.name] = existingImagePaths
                                } else {
                                    // Fallback to main user if person not found
                                    mainUserDocs[document.name] = existingImagePaths
                                }
                            }
                        }
                        
                        // Also add persons who don't have any documents
                        for (person in allPersons) {
                            if (!personDocs.containsKey(person.name)) {
                                // Person has no documents, but we still want to include them
                                personDocs[person.name] = mutableMapOf()
                            }
                        }
                        
                        // Now we need to ensure all persons in the documents are also included
                        // even if they don't have any documents
                        for (document in documents) {
                            if (document.personId != null) {
                                val person = personMap[document.personId]
                                if (person != null) {
                                    val personName = person.name
                                    if (!personDocs.containsKey(personName)) {
                                        personDocs[personName] = mutableMapOf()
                                    }
                                }
                            }
                        }
                        
                        Pair(mainUserDocs.toMap(), personDocs.mapValues { entry -> entry.value.toMap() }.toMap())
                    }
                }
                
                this@MainActivity.runOnUiThread {
                    Log.d("MainActivity", "Received documents with images data")
                    hideProgressDialog()
                    if (isExportTriggeredByUser) {
                        createHierarchicalExportZip(userName, mainUserDocuments, personDocuments)
                    } else {
                        Log.d("MainActivity", "Export not triggered by user, skipping")
                    }
                    isExportInProgress = false
                    isExportTriggeredByUser = false
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error collecting document data for export", e)
                this@MainActivity.runOnUiThread {
                    hideProgressDialog()
                    isExportInProgress = false
                    isExportTriggeredByUser = false
                    Toast.makeText(this@MainActivity, "Error preparing export: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
    
    private fun createHierarchicalExportZip(userName: String, mainUserDocuments: Map<String, List<String>>, personDocuments: Map<String, Map<String, List<String>>>) {
        // Double-check that export is still in progress and was triggered by user
        if (!isExportInProgress || !isExportTriggeredByUser) {
            Log.d("MainActivity", "Export was cancelled or not triggered by user, skipping zip creation")
            return
        }
        
        // Create a hierarchical structured zip file with user's name, even if there are no images
        val zipFile = FileUtils.createHierarchicalZipFile(this, userName, mainUserDocuments, personDocuments)

        if (zipFile != null && zipFile.exists()) {
            // Create share intent for the zip file
            val uri = FileUtils.getUriForFile(this, zipFile)

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, uri as android.os.Parcelable)
                type = "application/zip"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(shareIntent, "Export documents via"))
            isExportInProgress = false
            isExportTriggeredByUser = false
        } else {
            Toast.makeText(this, "Failed to create export file", Toast.LENGTH_SHORT).show()
            isExportInProgress = false
            isExportTriggeredByUser = false
        }
    }
    
    private fun openFamilyFriends() {
        val intent = Intent(this, FamilyFriendsActivity::class.java)
        startActivity(intent)
    }
    
    private fun openDeveloperDetails() {
        val intent = Intent(this, DeveloperDetailsActivity::class.java)
        startActivity(intent)
    }
    
    private fun openProfile() {
        val intent = Intent(this, ProfileActivity::class.java)
        profileLauncher.launch(intent)
    }

    private fun performSearch(query: String) {
        if (query.isNotEmpty()) {
            viewModel.searchDocuments(query).observe(this) { docs ->
                documents.clear()
                documents.addAll(docs)
                updateUI()
                
                // Load images for search results
                loadDocumentImages(docs)
            }
        } else {
            // If query is empty, load all documents
            loadDocuments()
        }
    }
}