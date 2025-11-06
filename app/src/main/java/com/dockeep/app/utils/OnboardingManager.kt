package com.dockeep.app.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object OnboardingManager {
    private const val TAG = "OnboardingManager"
    private const val ONBOARDING_FILE = "onboarding_status.dat"
    private const val COMPLETED_MARKER = "COMPLETED"
    
    /**
     * Checks if onboarding has been completed
     */
    fun isOnboardingCompleted(context: Context): Boolean {
        return try {
            // First check the file-based approach
            val file = File(context.filesDir, ONBOARDING_FILE)
            if (file.exists()) {
                val content = file.readText().trim()
                if (content == COMPLETED_MARKER) {
                    Log.d(TAG, "Onboarding completed (file-based check)")
                    return true
                } else {
                    Log.d(TAG, "Onboarding file exists but content is: '$content'")
                }
            } else {
                Log.d(TAG, "Onboarding file does not exist")
            }
            
            // Fallback to SharedPreferences
            val prefs = context.getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)
            val completed = prefs.getBoolean("completed", false)
            val userName = prefs.getString("user_name", null)
            Log.d(TAG, "Onboarding completed (SharedPreferences check): completed=$completed, userName=$userName")
            completed
        } catch (e: Exception) {
            Log.e(TAG, "Error checking onboarding status", e)
            false
        }
    }
    
    /**
     * Marks onboarding as completed and saves the user's name
     */
    fun completeOnboarding(context: Context, userName: String) {
        try {
            Log.d(TAG, "Saving onboarding completion for user: $userName")
            
            // Save to SharedPreferences
            val prefs = context.getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("completed", true)
                .putString("user_name", userName)
                .apply()
            
            // Verify SharedPreferences save
            val savedCompleted = prefs.getBoolean("completed", false)
            val savedUserName = prefs.getString("user_name", null)
            Log.d(TAG, "Verified SharedPreferences save: completed=$savedCompleted, userName=$savedUserName")
            
            // Also save to file for redundancy
            val file = File(context.filesDir, ONBOARDING_FILE)
            file.writeText(COMPLETED_MARKER)
            
            // Verify file save
            if (file.exists()) {
                val content = file.readText().trim()
                Log.d(TAG, "Verified file save: content=$content")
            } else {
                Log.e(TAG, "File save verification failed: file does not exist")
            }
            
            Log.d(TAG, "Onboarding marked as completed for user: $userName")
        } catch (e: Exception) {
            Log.e(TAG, "Error completing onboarding", e)
        }
    }
    
    /**
     * Gets the saved user name
     */
    fun getUserName(context: Context): String? {
        return try {
            val prefs = context.getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)
            val userName = prefs.getString("user_name", null)
            Log.d(TAG, "Retrieved user name: $userName")
            userName
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user name", e)
            null
        }
    }
    
    /**
     * Resets onboarding status (for testing purposes)
     */
    fun resetOnboarding(context: Context) {
        try {
            Log.d(TAG, "Resetting onboarding status")
            
            // Clear SharedPreferences
            val prefs = context.getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
            
            // Delete onboarding file
            val file = File(context.filesDir, ONBOARDING_FILE)
            if (file.exists()) {
                file.delete()
                Log.d(TAG, "Deleted onboarding file")
            }
            
            Log.d(TAG, "Onboarding status reset")
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting onboarding", e)
        }
    }
}