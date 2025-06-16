package com.example.ablindexaminer

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.ablindexaminer.data.firebase.HybridExamService
import com.example.ablindexaminer.workers.ScheduledExamPublishWorker
import com.google.firebase.FirebaseApp
// Commented out for now until Firebase App Check dependency is added
// import com.google.firebase.appcheck.FirebaseAppCheck
// import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
// import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Main application class for A Blind Examiner
 * Handles Firebase initialization and scheduling background workers
 */
class ABlindExaminerApplication : Application() {
    // Application coroutine scope for long-running tasks
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase
        initializeFirebase()
        
        // Schedule background work for checking scheduled exams
        scheduleExamPublisher()
    }
    
    /**
     * Initialize Firebase with necessary configurations
     */
    private fun initializeFirebase() {
        try {
            // Initialize Firebase
            FirebaseApp.initializeApp(this)
            
            // App Check implementation is temporarily disabled until the dependency is added
            /*
            // Set up App Check based on build type
            val appCheck = FirebaseAppCheck.getInstance()
            // For debug builds, use the debug provider which allows testing
            // For production, this should be replaced with Play Integrity
            appCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            */
            
            Log.d(TAG, "Firebase initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firebase", e)
        }
    }
    
    /**
     * Setup a periodic work request to check for scheduled exams
     * This will run every 1 minute to check if any exams need to be published
     */
    private fun scheduleExamPublisher() {
        try {
            // Set up constraints to ensure the worker runs even when the app is in background
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED) // Require network connection
                .build()
                
            // Create a periodic work request to run every 1 minute
            val publicationWorkRequest = PeriodicWorkRequestBuilder<ScheduledExamPublishWorker>(
                1, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            // Enqueue the work with a unique name
            WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                "exam_publication_scheduler",
                ExistingPeriodicWorkPolicy.KEEP, // Keep existing worker if one exists
                publicationWorkRequest
            )
            
            Log.d("ABlindExaminerApp", "Publication scheduler worker scheduled to run every 1 minute")
            
            // Also run the worker once immediately to check for any exams that should be published now
            val immediateWorkRequest = OneTimeWorkRequestBuilder<ScheduledExamPublishWorker>()
                .setConstraints(constraints)
                .build()
                
            WorkManager.getInstance(applicationContext).enqueue(immediateWorkRequest)
            Log.d(TAG, "Scheduled immediate exam publisher check")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling exam publisher worker", e)
        }
    }
    
    companion object {
        private const val TAG = "ABlindExamApp"
    }
} 