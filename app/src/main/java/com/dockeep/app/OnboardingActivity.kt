package com.dockeep.app

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.dockeep.app.ui.Edge
import com.dockeep.app.utils.OnboardingManager

class OnboardingActivity : AppCompatActivity() {
    private lateinit var userNameEditText: EditText
    private lateinit var userNameInputLayout: View
    private lateinit var continueButton: View
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("OnboardingActivity", "onCreate called")
        
        // Check if onboarding is already completed
        val onboardingCompleted = OnboardingManager.isOnboardingCompleted(this)
        Log.d("OnboardingActivity", "Initial onboarding check result: $onboardingCompleted")
        
        if (onboardingCompleted) {
            Log.d("OnboardingActivity", "Onboarding already completed, redirecting to MainActivity")
            redirectToMainActivity()
            return
        }
        
        setContentView(R.layout.activity_onboarding)
        Edge.fit(this, findViewById(android.R.id.content))
        
        // Check if this is the first launch of the app
        val firstLaunchPrefs = getApplicationContext().getSharedPreferences("app_first_launch", MODE_PRIVATE)
        val isFirstLaunch = firstLaunchPrefs.getBoolean("first_launch", true)
        
        if (isFirstLaunch) {
            Log.d("OnboardingActivity", "First launch of the app detected in OnboardingActivity")
            // Mark first launch as completed
            firstLaunchPrefs.edit().putBoolean("first_launch", false).apply()
        } else {
            Log.d("OnboardingActivity", "Not first launch of the app in OnboardingActivity")
        }
        
        // Check initial onboarding state using the robust OnboardingManager
        val userName = OnboardingManager.getUserName(this)
        Log.d("OnboardingActivity", "Initial state - onboardingCompleted: $onboardingCompleted, userName: $userName")
        
        // Initialize views
        initViews()
        
        // Setup click listeners
        setupClickListeners()
    }
    
    private fun initViews() {
        userNameEditText = findViewById(R.id.userNameEditText)
        userNameInputLayout = findViewById(R.id.userNameInputLayout)
        continueButton = findViewById(R.id.continueButton)
    }
    
    private fun setupClickListeners() {
        continueButton.setOnClickListener {
            val userName = userNameEditText.text.toString().trim()
            if (userName.isNotEmpty()) {
                Log.d("OnboardingActivity", "User entered name: $userName")
                // Save user name
                saveUserName(userName)
                
                // Navigate to main activity
                Log.d("OnboardingActivity", "Navigating to MainActivity")
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            } else {
                // The field carries no label to hang an error on, so the
                // message is surfaced directly and focus returns to the input.
                Toast.makeText(this, R.string.ledger_name_required, Toast.LENGTH_SHORT).show()
                userNameEditText.requestFocus()
            }
        }
    }
    
    private fun saveUserName(userName: String) {
        Log.d("OnboardingActivity", "Saving user name using OnboardingManager: $userName")
        
        // Use the robust OnboardingManager to save onboarding status
        OnboardingManager.completeOnboarding(this, userName)
        
        // Verify the save worked
        val savedCompleted = OnboardingManager.isOnboardingCompleted(this)
        val savedName = OnboardingManager.getUserName(this)
        Log.d("OnboardingActivity", "After save - completed: $savedCompleted, userName: $savedName")
    }
    
    private fun redirectToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}