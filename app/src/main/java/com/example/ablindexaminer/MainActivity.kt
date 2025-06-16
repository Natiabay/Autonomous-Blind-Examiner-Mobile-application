package com.example.ablindexaminer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.example.ablindexaminer.ui.navigation.BlindExaminerApp
import com.example.ablindexaminer.ui.screens.*
import com.example.ablindexaminer.ui.theme.ABlindExaminerTheme
import com.example.ablindexaminer.utils.ThemeManager
import com.example.ablindexaminer.utils.rememberThemeManager
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import androidx.work.WorkManager
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import com.example.ablindexaminer.workers.PublicationScheduleWorker
import java.util.concurrent.TimeUnit

/**
 * Main entry point for the Blind Examiner application.
 * This activity delegates to composable functions for UI implementation.
 */
class MainActivity : ComponentActivity() {
    
    // Result handler for when exam completes
    private lateinit var examResultLauncher: ActivityResultLauncher<Intent>
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Set up automatic exam publication scheduler
        setupAutomaticScheduler()
        
        // Set up basic security - keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Set up result handling for exams
        examResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                val score = data?.getIntExtra("score", 0) ?: 0
                val total = data?.getIntExtra("total", 0) ?: 0
                
                // Show success toast
                Toast.makeText(
                    this,
                    "Exam completed. Score: $score / $total",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        
        setContent {
            val themeManager = rememberThemeManager(this)
            val isDarkMode by themeManager.isDarkMode
            
            ABlindExaminerTheme(darkTheme = isDarkMode) {
                // A surface container using the 'primary' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Use the centralized navigation rather than duplicate code
                    BlindExaminerApp()
                }
            }
        }
    }
    
    /**
     * Set up the automatic exam publication scheduler
     */
    private fun setupAutomaticScheduler() {
        try {
            val workRequest = PeriodicWorkRequestBuilder<PublicationScheduleWorker>(
                15, TimeUnit.MINUTES // Check every 15 minutes for scheduled publications
            ).build()
            
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "ExamPublicationScheduler",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            
            Log.d("MainActivity", "Automatic exam publication scheduler initialized")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error setting up automatic scheduler", e)
        }
    }
    
    companion object {
        /**
         * Convenience method to launch secure exam 
         * (Kept for compatibility with existing code)
         */
        fun launchSecureExam(
            activity: Activity, 
            examId: String, 
            examTitle: String, 
            duration: Int, 
            examType: String = "MULTIPLE_CHOICE"
        ) {
            // This is a placeholder implementation
            // The actual functionality should be implemented in a proper security manager class
            val intent = Intent(activity, activity.javaClass)
            intent.putExtra("examId", examId)
            intent.putExtra("examTitle", examTitle)
            intent.putExtra("duration", duration)
            intent.putExtra("examType", examType)
            activity.startActivity(intent)
        }
    }
}