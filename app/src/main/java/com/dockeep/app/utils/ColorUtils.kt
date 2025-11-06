package com.dockeep.app.utils

import android.content.Context
import android.graphics.Color
import androidx.appcompat.app.AppCompatDelegate
import com.dockeep.app.R
import kotlin.math.absoluteValue

data class PlaceholderColorScheme(
    val backgroundColor: Int,
    val textColor: Int
)

object ColorUtils {
    fun getPlaceholderColorScheme(context: Context, name: String): PlaceholderColorScheme {
        // Check if dark theme is enabled
        val isDarkTheme = when (AppCompatDelegate.getDefaultNightMode()) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            else -> {
                // For MODE_NIGHT_FOLLOW_SYSTEM or MODE_NIGHT_UNSPECIFIED, check system setting
                val uiMode = context.resources.configuration.uiMode
                (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
        }
        
        if (name.isEmpty()) {
            return if (isDarkTheme) {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_A_dark),
                    context.getColor(R.color.placeholder_text_A_dark)
                )
            } else {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_A_light),
                    context.getColor(R.color.placeholder_text_A_light)
                )
            }
        }
        
        val firstLetter = name.first().uppercaseChar()
        return when (firstLetter) {
            'A' -> if (isDarkTheme) {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_A_dark),
                    context.getColor(R.color.placeholder_text_A_dark)
                )
            } else {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_A_light),
                    context.getColor(R.color.placeholder_text_A_light)
                )
            }
            'B' -> if (isDarkTheme) {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_B_dark),
                    context.getColor(R.color.placeholder_text_B_dark)
                )
            } else {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_B_light),
                    context.getColor(R.color.placeholder_text_B_light)
                )
            }
            'C' -> if (isDarkTheme) {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_C_dark),
                    context.getColor(R.color.placeholder_text_C_dark)
                )
            } else {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_C_light),
                    context.getColor(R.color.placeholder_text_C_light)
                )
            }
            'D' -> if (isDarkTheme) {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_D_dark),
                    context.getColor(R.color.placeholder_text_D_dark)
                )
            } else {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_D_light),
                    context.getColor(R.color.placeholder_text_D_light)
                )
            }
            'E' -> if (isDarkTheme) {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_E_dark),
                    context.getColor(R.color.placeholder_text_E_dark)
                )
            } else {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_E_light),
                    context.getColor(R.color.placeholder_text_E_light)
                )
            }
            'F' -> if (isDarkTheme) {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_F_dark),
                    context.getColor(R.color.placeholder_text_F_dark)
                )
            } else {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_F_light),
                    context.getColor(R.color.placeholder_text_F_light)
                )
            }
            'G' -> if (isDarkTheme) {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_G_dark),
                    context.getColor(R.color.placeholder_text_G_dark)
                )
            } else {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_G_light),
                    context.getColor(R.color.placeholder_text_G_light)
                )
            }
            'H' -> if (isDarkTheme) {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_H_dark),
                    context.getColor(R.color.placeholder_text_H_dark)
                )
            } else {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_H_light),
                    context.getColor(R.color.placeholder_text_H_light)
                )
            }
            'I' -> if (isDarkTheme) {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_I_dark),
                    context.getColor(R.color.placeholder_text_I_dark)
                )
            } else {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_I_light),
                    context.getColor(R.color.placeholder_text_I_light)
                )
            }
            'J' -> if (isDarkTheme) {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_J_dark),
                    context.getColor(R.color.placeholder_text_J_dark)
                )
            } else {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_J_light),
                    context.getColor(R.color.placeholder_text_J_light)
                )
            }
            'K' -> if (isDarkTheme) {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_K_dark),
                    context.getColor(R.color.placeholder_text_K_dark)
                )
            } else {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_K_light),
                    context.getColor(R.color.placeholder_text_K_light)
                )
            }
            'L' -> if (isDarkTheme) {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_L_dark),
                    context.getColor(R.color.placeholder_text_L_dark)
                )
            } else {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_L_light),
                    context.getColor(R.color.placeholder_text_L_light)
                )
            }
            'M' -> if (isDarkTheme) {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_M_dark),
                    context.getColor(R.color.placeholder_text_M_dark)
                )
            } else {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_M_light),
                    context.getColor(R.color.placeholder_text_M_light)
                )
            }
            'N' -> if (isDarkTheme) {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_N_dark),
                    context.getColor(R.color.placeholder_text_N_dark)
                )
            } else {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_N_light),
                    context.getColor(R.color.placeholder_text_N_light)
                )
            }
            'O' -> if (isDarkTheme) {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_O_dark),
                    context.getColor(R.color.placeholder_text_O_dark)
                )
            } else {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_O_light),
                    context.getColor(R.color.placeholder_text_O_light)
                )
            }
            'P' -> if (isDarkTheme) {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_P_dark),
                    context.getColor(R.color.placeholder_text_P_dark)
                )
            } else {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_P_light),
                    context.getColor(R.color.placeholder_text_P_light)
                )
            }
            'Q' -> if (isDarkTheme) {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_Q_dark),
                    context.getColor(R.color.placeholder_text_Q_dark)
                )
            } else {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_Q_light),
                    context.getColor(R.color.placeholder_text_Q_light)
                )
            }
            'R' -> if (isDarkTheme) {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_R_dark),
                    context.getColor(R.color.placeholder_text_R_dark)
                )
            } else {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_R_light),
                    context.getColor(R.color.placeholder_text_R_light)
                )
            }
            'S' -> if (isDarkTheme) {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_S_dark),
                    context.getColor(R.color.placeholder_text_S_dark)
                )
            } else {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_S_light),
                    context.getColor(R.color.placeholder_text_S_light)
                )
            }
            'T' -> if (isDarkTheme) {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_T_dark),
                    context.getColor(R.color.placeholder_text_T_dark)
                )
            } else {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_T_light),
                    context.getColor(R.color.placeholder_text_T_light)
                )
            }
            'U' -> if (isDarkTheme) {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_U_dark),
                    context.getColor(R.color.placeholder_text_U_dark)
                )
            } else {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_U_light),
                    context.getColor(R.color.placeholder_text_U_light)
                )
            }
            'V' -> if (isDarkTheme) {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_V_dark),
                    context.getColor(R.color.placeholder_text_V_dark)
                )
            } else {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_V_light),
                    context.getColor(R.color.placeholder_text_V_light)
                )
            }
            'W' -> if (isDarkTheme) {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_W_dark),
                    context.getColor(R.color.placeholder_text_W_dark)
                )
            } else {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_W_light),
                    context.getColor(R.color.placeholder_text_W_light)
                )
            }
            'X' -> if (isDarkTheme) {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_X_dark),
                    context.getColor(R.color.placeholder_text_X_dark)
                )
            } else {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_X_light),
                    context.getColor(R.color.placeholder_text_X_light)
                )
            }
            'Y' -> if (isDarkTheme) {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_Y_dark),
                    context.getColor(R.color.placeholder_text_Y_dark)
                )
            } else {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_Y_light),
                    context.getColor(R.color.placeholder_text_Y_light)
                )
            }
            'Z' -> if (isDarkTheme) {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_Z_dark),
                    context.getColor(R.color.placeholder_text_Z_dark)
                )
            } else {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_Z_light),
                    context.getColor(R.color.placeholder_text_Z_light)
                )
            }
            else -> if (isDarkTheme) {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_A_dark),
                    context.getColor(R.color.placeholder_text_A_dark)
                )
            } else {
                PlaceholderColorScheme(
                    context.getColor(R.color.placeholder_A_light),
                    context.getColor(R.color.placeholder_text_A_light)
                )
            }
        }
    }
    
    fun getColorIndexForName(name: String): Int {
        if (name.isEmpty()) return 0
        return name.first().uppercaseChar().code - 'A'.code
    }
    
    /**
     * Calculate luminance of a color to determine if text should be light or dark
     * This ensures proper contrast for readability
     */
    fun calculateLuminance(color: Int): Double {
        val red = Color.red(color) / 255.0
        val green = Color.green(color) / 255.0
        val blue = Color.blue(color) / 255.0
        
        // Apply gamma correction
        val r = if (red <= 0.03928) red / 12.92 else Math.pow((red + 0.055) / 1.055, 2.4)
        val g = if (green <= 0.03928) green / 12.92 else Math.pow((green + 0.055) / 1.055, 2.4)
        val b = if (blue <= 0.03928) blue / 12.92 else Math.pow((blue + 0.055) / 1.055, 2.4)
        
        // Calculate luminance
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }
    
    /**
     * Determine if text should be light or dark based on background color
     * Returns true if text should be light (white), false if dark (black)
     */
    fun shouldUseLightText(backgroundColor: Int): Boolean {
        val luminance = calculateLuminance(backgroundColor)
        // If background is dark (low luminance), use light text
        return luminance < 0.5
    }
}