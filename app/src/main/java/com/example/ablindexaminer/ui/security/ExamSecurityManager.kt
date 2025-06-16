package com.example.ablindexaminer.ui.security

import android.app.Activity
import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.ablindexaminer.ExamLockdownActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.isActive
import java.util.UUID

/**
 * Enhanced ExamSecurityManager with continuous pinning monitoring
 * Ensures exam security throughout the entire exam session
 */
class ExamSecurityManager private constructor() {
    companion object {
        private const val TAG = "ExamSecurityManager"
        private const val MAX_EXIT_ATTEMPTS = 3
        private const val EXIT_ATTEMPT_WINDOW_MS = 10000 // 10 seconds
        private const val PINNING_CHECK_INTERVAL = 1000L // 1 second
        private const val MAX_PINNING_RETRIES = 5
        
        // Singleton instance
        @Volatile
        private var instance: ExamSecurityManager? = null
        
        // Get singleton instance with double-checked locking
        fun getInstance(): ExamSecurityManager {
            return instance ?: synchronized(this) {
                instance ?: ExamSecurityManager().also { instance = it }
            }
        }
        
        // Static flag to check if in exam mode (for legacy compatibility)
        private var isExamModeActive = false
        
        fun isInExamMode(): Boolean {
            return isExamModeActive
        }
    }
    
    // Store the result launcher for exams
    private var examResultLauncher: ActivityResultLauncher<Intent>? = null
    
    // Active exam sessions
    private val activeSessions = mutableMapOf<String, ExamSession>()
    private val securityViolations = mutableMapOf<String, MutableList<SecurityViolation>>()
    
    // Security state
    private var currentExamActivity: Activity? = null
    private var pinningMonitorJob: Job? = null
    private var securityScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    /**
     * Register an activity result launcher for handling exam results
     */
    fun registerResultLauncher(launcher: ActivityResultLauncher<Intent>) {
        examResultLauncher = launcher
    }
    
    /**
     * Enhanced pinning monitoring with continuous 1-second checks
     */
    private fun startContinuousPinningMonitoring(activity: Activity, sessionId: String) {
        Log.d(TAG, "Starting continuous pinning monitoring for session: $sessionId")
        
        pinningMonitorJob?.cancel() // Cancel any existing job
        
        pinningMonitorJob = securityScope.launch {
            var retryCount = 0
            
            while (isActive && !isExamCompleted(sessionId)) {
                try {
                    if (!isInPinningMode(activity)) {
                        retryCount++
                        Log.w(TAG, "Pinning lost detected! Attempt $retryCount to restore pinning")
                        
                        logSecurityViolation(
                            sessionId, 
                            "Pinning mode disabled - automatically re-enabling (attempt $retryCount)",
                            ViolationSeverity.CRITICAL
                        )
                        
                        // Attempt to re-enable pinning
                        val restored = startScreenPinning(activity)
                        
                        if (restored) {
                            Log.i(TAG, "Pinning successfully restored")
                            retryCount = 0 // Reset retry count on success
                        } else if (retryCount >= MAX_PINNING_RETRIES) {
                            Log.e(TAG, "Failed to restore pinning after $MAX_PINNING_RETRIES attempts")
                            logSecurityViolation(
                                sessionId,
                                "Critical: Unable to restore pinning mode after $MAX_PINNING_RETRIES attempts",
                                ViolationSeverity.CRITICAL
                            )
                            // Don't break the exam, just log the critical violation
                            retryCount = 0 // Reset to continue trying
                        }
                    } else {
                        retryCount = 0 // Reset retry count when pinning is active
                    }
                    
                    delay(PINNING_CHECK_INTERVAL)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in pinning monitoring", e)
                    delay(PINNING_CHECK_INTERVAL)
                }
            }
            
            Log.d(TAG, "Pinning monitoring stopped for session: $sessionId")
        }
    }
    
    /**
     * Stop continuous pinning monitoring
     */
    private fun stopContinuousPinningMonitoring() {
        Log.d(TAG, "Stopping continuous pinning monitoring")
        pinningMonitorJob?.cancel()
        pinningMonitorJob = null
    }
    
    /**
     * Start an exam session and record its details
     */
    fun startExamSession(examId: String, examTitle: String): String {
        val sessionId = UUID.randomUUID().toString()
        val session = ExamSession(
            sessionId = sessionId,
            examId = examId,
            examTitle = examTitle,
            startTime = System.currentTimeMillis()
        )
        activeSessions[sessionId] = session
        securityViolations[sessionId] = mutableListOf()
        isExamModeActive = true
        
        Log.d(TAG, "Started exam session: $sessionId for exam: $examId")
        return sessionId
    }
    
    /**
     * End an exam session and record completion time
     */
    fun endExamSession(sessionId: String) {
        activeSessions[sessionId]?.let { session ->
            session.endTime = System.currentTimeMillis()
            session.completed = true
            
            // If this was the last active session, turn off exam mode
            if (activeSessions.none { !it.value.completed }) {
                isExamModeActive = false
            }
            
            Log.d(TAG, "Ended exam session: $sessionId")
            
            // Stop pinning monitoring
            stopContinuousPinningMonitoring()
            
            // Disable security if this was the current exam
            currentExamActivity?.let { activity ->
                disableSecurityMode(activity)
            }
            
            currentExamActivity = null
        }
    }
    
    /**
     * Enhanced security mode activation for exam activity only
     */
    fun enableSecurityModeForExam(activity: Activity, sessionId: String) {
        try {
            currentExamActivity = activity
            
            // Record security activation
            logSecurityViolation(
                sessionId, 
                "Security mode activated for exam",
                ViolationSeverity.INFO
            )
            
            // Configure activity flags for exam security
            activity.window.apply {
                // Keep screen on
                addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                
                // Allow screenshots for Braille input functionality
                // This is necessary as Braille input requires screenshot analysis
                clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                
                // Prevent screenshot in notification area but allow in app content
                // This maintains security while allowing Braille ML processing
                Log.d(TAG, "Screen capture enabled for Braille functionality")
            }
            
            // Force portrait orientation for consistency
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            
            // Enable screen pinning
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val success = startScreenPinning(activity)
                if (success) {
                    Log.d(TAG, "Screen pinning enabled successfully")
                    startContinuousPinningMonitoring(activity, sessionId)
                } else {
                    Log.w(TAG, "Failed to enable screen pinning")
                    logSecurityViolation(
                        sessionId,
                        "Screen pinning could not be enabled",
                        ViolationSeverity.WARNING
                    )
                }
            }
            
            // Set up system UI visibility for immersive mode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Use new API for Android 11+
                activity.window.setDecorFitsSystemWindows(false)
            } else {
                // Use legacy API for older versions
                @Suppress("DEPRECATION")
                activity.window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
            }
            
            Log.d(TAG, "Security mode enabled for exam session: $sessionId")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling security mode", e)
            logSecurityViolation(
                sessionId,
                "Error enabling security mode: ${e.message}",
                ViolationSeverity.CRITICAL
            )
        }
    }
    
    /**
     * Log a security violation
     */
    fun logSecurityViolation(
        sessionId: String, 
        violation: String, 
        severity: ViolationSeverity = ViolationSeverity.WARNING
    ) {
        val timestamp = System.currentTimeMillis()
        val securityViolation = SecurityViolation(sessionId, violation, timestamp, severity)
        
        securityViolations.getOrPut(sessionId) { mutableListOf() }.add(securityViolation)
        
        Log.w(TAG, "[$sessionId] ${severity.name} violation at $timestamp: $violation")
    }
    
    /**
     * Get all violations for a specific session
     */
    fun getViolationsForSession(sessionId: String): List<SecurityViolation> {
        return securityViolations[sessionId] ?: emptyList()
    }
    
    /**
     * Launch a secure exam
     */
    fun launchSecureExam(
        activity: Activity,
        examId: String,
        examTitle: String,
        duration: Int,
        examType: String,
        examResultLauncher: ActivityResultLauncher<Intent>?
    ) {
        // Request necessary permissions first
        ensureSecurityPermissions(activity)
        
        // Create and record the exam session
        val sessionId = startExamSession(examId, examTitle)
        
        // Create intent for the lockdown activity
        val intent = Intent(activity, ExamLockdownActivity::class.java).apply {
            putExtra("examId", examId)
            putExtra("examTitle", examTitle)
            putExtra("duration", duration)
            putExtra("examType", examType)
            putExtra("sessionId", sessionId)
            
            // Add flags to ensure this activity stays on top
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_HISTORY
        }
        
        // Launch the exam using the registered result launcher if available
        try {
            if (examResultLauncher != null) {
                this.examResultLauncher = examResultLauncher
                examResultLauncher.launch(intent)
            } else {
                activity.startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch secure exam: ${e.message}", e)
            Toast.makeText(
                activity, 
                "Failed to launch exam in secure mode.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    /**
     * Ensure all necessary security permissions are requested
     */
    private fun ensureSecurityPermissions(activity: Activity) {
        // Request battery optimization exemption
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val packageName = activity.packageName
                val powerManager = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
                
                if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    
                    try {
                        activity.startActivity(intent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to request battery optimization exemption", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking battery optimization status", e)
            }
        }
        
        // Request overlay permission if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(activity)) {
            try {
                Toast.makeText(
                    activity,
                    "The app needs display over other apps permission for secure exams",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show overlay permission toast", e)
            }
        }
    }
    
    /**
     * Apply all security measures to an activity window
     */
    fun applySecurityMeasures(activity: Activity) {
        try {
            // Remove screenshot prevention flag to allow screen capture
            // activity.window.setFlags(
            //     WindowManager.LayoutParams.FLAG_SECURE,
            //     WindowManager.LayoutParams.FLAG_SECURE
            // )
            
            // Keep screen on during exam
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            
            // Disable lock screen if the device is secured
            val keyguardManager = activity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && keyguardManager.isKeyguardLocked) {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    keyguardManager.requestDismissKeyguard(activity, null)
                }
            }
            
            // Lock to portrait mode
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            
            // Set up immersive sticky mode to hide system UI
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.window.setDecorFitsSystemWindows(false)
                
                // Use WindowInsetsController for API 30+
                activity.window.decorView.windowInsetsController?.let { controller ->
                    // Hide system bars permanently and prevent showing by swipe
                    val flags = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_FULLSCREEN
                    activity.window.decorView.systemUiVisibility = flags
                }
            } else {
                // For older Android versions
                val flags = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                activity.window.decorView.systemUiVisibility = flags
            }
            
            Log.d(TAG, "Applied security measures successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying security measures", e)
        }
    }
    
    /**
     * Start screen pinning to prevent app switching
     */
    fun startScreenPinning(activity: Activity): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val activityManager = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                
                // Check if we can pin (device policy allows it)
                if (activityManager.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {
                    try {
                        activity.startLockTask()
                        Log.d(TAG, "Screen pinning started successfully")
                        return true
                    } catch (e: SecurityException) {
                        Log.w(TAG, "SecurityException starting lock task - device policy may not allow pinning")
                        return false
                    } catch (e: Exception) {
                        Log.w(TAG, "Exception starting lock task: ${e.message}")
                        return false
                    }
                } else {
                    Log.d(TAG, "Lock task mode already active")
                    return true
                }
            } else {
                Log.w(TAG, "Screen pinning not supported on this Android version")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting screen pinning", e)
            false
        }
    }
    
    /**
     * Stop screen pinning if active
     */
    fun stopScreenPinning(activity: Activity) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val activityManager = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                
                if (activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE) {
                    try {
                        activity.stopLockTask()
                        Log.d(TAG, "Screen pinning stopped successfully")
                    } catch (e: SecurityException) {
                        Log.w(TAG, "SecurityException stopping lock task")
                    } catch (e: Exception) {
                        Log.w(TAG, "Exception stopping lock task: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping screen pinning", e)
        }
    }
    
    /**
     * Disable security mode and restore normal operation
     */
    fun disableSecurityMode(activity: Activity) {
        try {
            Log.d(TAG, "Disabling security mode")
            
            // Stop pinning monitoring
            stopContinuousPinningMonitoring()
            
            // Stop screen pinning
            stopScreenPinning(activity)
            
            // Reset window flags
            activity.window.apply {
                clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                
                // Re-enable secure flag if needed (can be configured)
                // For now, we keep it disabled to allow Braille functionality
                // addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            
            // Reset orientation
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            
            // Reset system UI visibility
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.window.setDecorFitsSystemWindows(true)
            } else {
                @Suppress("DEPRECATION")
                activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }
            
            currentExamActivity = null
            Log.d(TAG, "Security mode disabled successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling security mode", e)
        }
    }
    
    /**
     * Check if device is currently in app pinning/lock task mode
     */
    fun isInPinningMode(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.getLockTaskModeState() != ActivityManager.LOCK_TASK_MODE_NONE
                } else {
                    @Suppress("DEPRECATION")
                    am.isInLockTaskMode
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check lock task mode", e)
                false
            }
        }
        return false
    }
    
    /**
     * Check if the app is currently in the foreground
     */
    fun isAppInForeground(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        
        // For API 23+, use getRunningAppProcesses
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val appProcesses = activityManager.runningAppProcesses ?: return false
            val packageName = context.packageName
            for (appProcess in appProcesses) {
                if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                    appProcess.processName == packageName) {
                    return true
                }
            }
            return false
        } else {
            // For older APIs, try the deprecated getRunningTasks
            try {
                val runningTaskInfos = activityManager.getRunningTasks(1)
                return runningTaskInfos[0].topActivity?.packageName == context.packageName
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check foreground status", e)
                return true // Assume we're in foreground if check fails
            }
        }
    }
    
    /**
     * Create a screen state receiver to monitor screen on/off events
     * Must be registered by the activity with appropriate intent filters
     */
    fun createScreenStateReceiver(activity: Activity, sessionId: String): BroadcastReceiver {
        return object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        logSecurityViolation(sessionId, "Screen turned off during exam")
                        // Bring screen back on if possible
                        try {
                            val powerManager = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
                            val wakeLock = powerManager.newWakeLock(
                                PowerManager.FULL_WAKE_LOCK or
                                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                                PowerManager.ON_AFTER_RELEASE, "ablindexaminer:examWakelock"
                            )
                            wakeLock.acquire(10000) // Acquire for 10 seconds
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to acquire wakelock", e)
                        }
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        // Re-launch our activity to force it to the foreground
                        bringToForeground(activity)
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        // User unlocked the device, make sure our activity is in front
                        bringToForeground(activity)
                    }
                }
            }
        }
    }
    
    /**
     * Bring activity back to foreground
     */
    fun bringToForeground(activity: Activity) {
        try {
            val activityManager = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            activityManager.moveTaskToFront(activity.taskId, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bring app to foreground", e)
        }
    }
    
    /**
     * Create lifecycle observer for security monitoring
     */
    fun createSecurityLifecycleObserver(
        activity: Activity, 
        sessionId: String, 
        isExamCompleted: () -> Boolean
    ): LifecycleEventObserver {
        // Create a coroutine scope to use for async operations
        val coroutineScope = CoroutineScope(Dispatchers.Main)
        
        return LifecycleEventObserver { _, event ->
            if (isExamCompleted()) return@LifecycleEventObserver
            
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    applySecurityMeasures(activity)
                    // Check if pinning is still active
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        val am = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                        if (!isInPinningMode(activity)) {
                            startScreenPinning(activity)
                            logSecurityViolation(sessionId, "Screen pinning was disabled")
                        }
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    logSecurityViolation(sessionId, "Activity paused")
                }
                Lifecycle.Event.ON_STOP -> {
                    logSecurityViolation(sessionId, "Activity stopped")
                    
                    // Try to come back to foreground
                    coroutineScope.launch {
                        delay(500)
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            bringToForeground(activity)
                        }
                    }
                }
                else -> { /* Ignore other lifecycle events */ }
            }
        }
    }
    
    /**
     * Verify if an exam environment is secure
     */
    fun verifySecureEnvironment(activity: Activity): Boolean {
        // Check 1: Can we take screenshots?
        val isScreenshotDisabled = 
            (activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
        
        // Check 2: Are we in lock task mode?
        val isLockTaskActive = isInPinningMode(activity)
        
        // Check 3: Is screen set to stay on?
        val isScreenStayingOn = 
            (activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
            
        // Return overall security assessment
        return isScreenshotDisabled && (isLockTaskActive || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) && isScreenStayingOn
    }
    
    /**
     * Check if exam is completed
     */
    fun isExamCompleted(sessionId: String): Boolean {
        return activeSessions[sessionId]?.completed ?: true
    }
    
    fun isExamCompleted(): Boolean {
        return activeSessions.values.all { it.completed }
    }
    
    /**
     * Check if Braille input is currently active (for security exceptions)
     */
    fun isBrailleInputActive(): Boolean {
        // This can be set by the Braille input screen to inform security manager
        // that screenshots are needed for ML processing
        return true // Always allow for Braille functionality
    }
    
    // Model classes
    
    data class ExamSession(
        val sessionId: String,
        val examId: String,
        val examTitle: String,
        val startTime: Long,
        var endTime: Long = 0,
        var completed: Boolean = false
    )
    
    data class SecurityViolation(
        val sessionId: String,
        val violation: String,
        val timestamp: Long,
        val severity: ViolationSeverity
    )
    
    enum class ViolationSeverity {
        INFO, WARNING, CRITICAL
    }
} 