package com.example.ablindexaminer.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

class ThemeManager private constructor(private val context: Context) {
    companion object {
        @Volatile
        private var INSTANCE: ThemeManager? = null
        
        fun getInstance(context: Context): ThemeManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ThemeManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val sharedPrefs: SharedPreferences = 
        context.getSharedPreferences("admin_settings", Context.MODE_PRIVATE)
    
    private val _isDarkMode = mutableStateOf(sharedPrefs.getBoolean("dark_mode", false))
    val isDarkMode: State<Boolean> = _isDarkMode
    
    private val _isVoiceGuidanceEnabled = mutableStateOf(sharedPrefs.getBoolean("voice_guidance", true))
    val isVoiceGuidanceEnabled: State<Boolean> = _isVoiceGuidanceEnabled
    
    fun setDarkMode(enabled: Boolean) {
        _isDarkMode.value = enabled
        sharedPrefs.edit().putBoolean("dark_mode", enabled).apply()
    }
    
    fun setVoiceGuidance(enabled: Boolean) {
        _isVoiceGuidanceEnabled.value = enabled
        sharedPrefs.edit().putBoolean("voice_guidance", enabled).apply()
    }
    
    fun shouldShowVoiceGuidance(userRole: String?): Boolean {
        // Voice guidance only for teachers and administrators
        return _isVoiceGuidanceEnabled.value && (userRole == "TEACHER" || userRole == "ADMIN")
    }
}

class ThemeViewModel(context: Context) : ViewModel() {
    private val themeManager = ThemeManager.getInstance(context)
    
    val isDarkMode = themeManager.isDarkMode
    val isVoiceGuidanceEnabled = themeManager.isVoiceGuidanceEnabled
    
    fun setDarkMode(enabled: Boolean) {
        themeManager.setDarkMode(enabled)
    }
    
    fun setVoiceGuidance(enabled: Boolean) {
        themeManager.setVoiceGuidance(enabled)
    }
    
    fun shouldShowVoiceGuidance(userRole: String?): Boolean {
        return themeManager.shouldShowVoiceGuidance(userRole)
    }
}

@Composable
fun rememberThemeManager(context: Context): ThemeManager {
    return remember { ThemeManager.getInstance(context) }
}