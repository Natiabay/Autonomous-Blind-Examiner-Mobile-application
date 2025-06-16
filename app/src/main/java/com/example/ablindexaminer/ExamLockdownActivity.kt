package com.example.ablindexaminer

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.ablindexaminer.ui.screens.ExamContent
import com.example.ablindexaminer.ui.security.ExamSecurityManager
import com.example.ablindexaminer.ui.security.SecurityUtils
import com.example.ablindexaminer.ui.theme.ABlindExaminerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Enhanced ExamLockdownActivity with improved security and proper exam completion flow
 */
class ExamLockdownActivity : ComponentActivity() {
    
    // Security manager instance
    private val securityManager = ExamSecurityManager.getInstance()
    
    // Exam state
    private var isExamCompleted = false
    private var showingWarningDialog = false
    private var lastExitAttemptTime = 0L
    private var exitAttemptCount = 0
    
    // Session tracking
    private lateinit var sessionId: String
    private lateinit var examId: String
    private lateinit var examTitle: String
    
    // Constants
    companion object {
        private const val MAX_EXIT_ATTEMPTS = 3
        private const val EXIT_ATTEMPT_WINDOW_MS = 10000 // 10 seconds
        private const val TAG = "ExamLockdown"
    }
    
    // Screen state receiver
    private lateinit var screenStateReceiver: BroadcastReceiver
    
    // Back press callback
    private val backPressCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (!isExamCompleted) {
                showSecurityWarning("Back navigation attempted")
            } else {
                // If exam is completed, allow navigation back
                this.remove()
                onBackPressedDispatcher.onBackPressed()
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Get exam parameters
        examId = intent.getStringExtra("examId") ?: ""
        examTitle = intent.getStringExtra("examTitle") ?: ""
        val duration = intent.getIntExtra("duration", 60)
        val examType = intent.getStringExtra("examType") ?: "MULTIPLE_CHOICE"
        sessionId = intent.getStringExtra("sessionId") ?: ""
        
        // Validate required parameters
        if (examId.isEmpty() || sessionId.isEmpty()) {
            Log.e(TAG, "Missing required exam parameters")
            finish()
            return
        }
        
        // Register back press callback
        onBackPressedDispatcher.addCallback(this, backPressCallback)
        
        // Set up screen state monitoring
        setupScreenStateReceiver()
        
        // Enable enhanced security mode for exam
        securityManager.enableSecurityModeForExam(this, sessionId)
        
        // Register lifecycle observer for security monitoring
        lifecycle.addObserver(securityManager.createSecurityLifecycleObserver(this, sessionId) { isExamCompleted })
        
        // Set up the UI
        setContent {
            ABlindExaminerTheme {
                ExamLockdownScreen(
                    examId = examId,
                    examTitle = examTitle,
                    duration = duration,
                    examType = examType,
                    onExamComplete = { score, total ->
                        handleExamCompletion(score, total)
                    }
                )
            }
        }
        
        // Start foreground monitoring
        startForegroundMonitoring()
        
        // Check device security capabilities
        checkDeviceSecurity()
    }
    
    /**
     * Handle exam completion with proper security cleanup and result flow
     */
    private fun handleExamCompletion(score: Int, total: Int) {
        Log.d(TAG, "Exam completed with score: $score/$total")
        
        isExamCompleted = true
        
        // FIRST: Stop screen pinning immediately when exam completes
        try {
            securityManager.stopScreenPinning(this)
            Log.d(TAG, "Screen pinning stopped after exam completion")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping screen pinning", e)
        }
        
        // End the exam session - this will stop pinning monitoring and disable security
        securityManager.endExamSession(sessionId)
        
        // Get security violations for reporting
        val violations = securityManager.getViolationsForSession(sessionId)
        
        // Show result screen within this activity
        setContent {
            ABlindExaminerTheme {
                ExamResultScreen(
                    score = score,
                    total = total,
                    examTitle = examTitle,
                    hasViolations = violations.isNotEmpty(),
                    onReturnToDashboard = {
                        // Ensure security is fully disabled before returning
                        try {
                            securityManager.disableSecurityMode(this@ExamLockdownActivity)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error disabling security mode", e)
                        }
                        
                        // Return result to calling activity and finish
                        val resultIntent = Intent().apply {
                            putExtra("examId", examId)
                            putExtra("score", score)
                            putExtra("total", total)
                            putExtra("examCompleted", true)
                            // Include security violations logs if any
                            if (violations.isNotEmpty()) {
                                putExtra("securityViolations", 
                                    violations.map { "${it.timestamp}: ${it.violation}" }.toTypedArray())
                            }
                        }
                        setResult(Activity.RESULT_OK, resultIntent)
                        finish()
                    }
                )
            }
        }
    }
    
    /**
     * Set up screen state monitoring receiver
     */
    private fun setupScreenStateReceiver() {
        screenStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        if (!isExamCompleted) {
                            securityManager.logSecurityViolation(sessionId, "Screen turned off during exam")
                        }
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        if (!isExamCompleted) {
                            securityManager.logSecurityViolation(sessionId, "Screen turned on during exam")
                        }
                    }
                }
            }
        }
        
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        
        registerReceiver(screenStateReceiver, filter)
    }
    
    /**
     * Check device security capabilities and warn if needed
     */
    private fun checkDeviceSecurity() {
        lifecycleScope.launch {
            delay(1000) // Give time for setup
            
            val securityIssues = SecurityUtils.checkDeviceSecurity(this@ExamLockdownActivity)
            
            // Log high severity issues
            val highSeverityIssues = securityIssues.filter { 
                it.severity == SecurityUtils.SecurityIssue.Severity.HIGH 
            }
            
            if (highSeverityIssues.isNotEmpty()) {
                securityManager.logSecurityViolation(
                    sessionId = sessionId,
                    violation = "Critical security issues detected: ${highSeverityIssues.joinToString { it.title }}",
                    severity = ExamSecurityManager.ViolationSeverity.CRITICAL
                )
            }
        }
    }
    
    /**
     * Start a foreground monitoring job that ensures this activity stays in the foreground
     */
    private fun startForegroundMonitoring() {
        lifecycleScope.launch {
            while (!isExamCompleted) {
                if (!securityManager.isAppInForeground(this@ExamLockdownActivity) && !showingWarningDialog) {
                    securityManager.logSecurityViolation(sessionId, "Exam activity moved to background")
                    securityManager.bringToForeground(this@ExamLockdownActivity)
                }
                delay(1000) // Check every second
            }
        }
    }
    
    /**
     * Display security warning - ONLY for key presses (Home, Back, Recent Apps, etc.)
     */
    private fun showSecurityWarning(reason: String = "") {
        // Only show warning for actual key presses
        val shouldShowDialog = reason.contains("blocked", ignoreCase = true) ||
                              reason.contains("back navigation", ignoreCase = true) ||
                              reason.contains("key", ignoreCase = true)
        
        securityManager.logSecurityViolation(sessionId, reason)
        
        if (shouldShowDialog) {
            showingWarningDialog = true
            // Don't auto-dismiss - let user manually dismiss via "Return to Exam" button
        }
    }
    
    /**
     * Exam result screen shown after completion
     */
    @Composable
    private fun ExamResultScreen(
        score: Int,
        total: Int,
        examTitle: String,
        hasViolations: Boolean,
        onReturnToDashboard: () -> Unit
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Success header
                    Text(
                        text = "Exam Completed!",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Exam title
                    Text(
                        text = examTitle,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Score card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Your Score",
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "$score / $total",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            val percentage = if (total > 0) (score * 100) / total else 0
                            Text(
                                text = "($percentage%)",
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Security status
                    if (hasViolations) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = "⚠️ Security violations were detected during this exam. Your instructor has been notified.",
                                modifier = Modifier.padding(12.dp),
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                textAlign = TextAlign.Center
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    
                    // Information text
                    Text(
                        text = "Your exam has been submitted successfully. Results have been saved and sent to your instructor.",
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Return button
                    Button(
                        onClick = onReturnToDashboard,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Return to Dashboard")
                    }
                }
            }
        }
    }
    
    /**
     * Main exam screen with security monitoring
     */
    @Composable
    private fun ExamLockdownScreen(
        examId: String,
        examTitle: String,
        duration: Int,
        examType: String,
        onExamComplete: (Int, Int) -> Unit
    ) {
        var showWarning by remember { mutableStateOf(showingWarningDialog) }
        
        // Update warning state when activity flag changes
        if (showWarning != showingWarningDialog) {
            showWarning = showingWarningDialog
        }
        
        // Effect to handle cleanup
        DisposableEffect(key1 = Unit) {
            onDispose {
                if (isExamCompleted) {
                    securityManager.disableSecurityMode(this@ExamLockdownActivity)
                }
            }
        }
        
        // Render the exam content
        ExamContent(
            examId = examId,
            examTitle = examTitle,
            duration = duration,
            examType = examType,
            showSecurityWarning = showWarning,
            onSecurityWarningDismiss = { 
                showingWarningDialog = false
                
                // Re-apply security measures
                lifecycleScope.launch {
                    // Reapply all security measures
                    securityManager.applySecurityMeasures(this@ExamLockdownActivity)
                    
                    // Ensure we're in the foreground
                    securityManager.bringToForeground(this@ExamLockdownActivity)
                    
                    // The continuous monitoring will handle re-pinning automatically
                }
            },
            onExamComplete = onExamComplete
        )
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!isExamCompleted) {
            showSecurityWarning("Back button pressed")
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
    
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!isExamCompleted) {
            securityManager.logSecurityViolation(sessionId, "User leave hint received")
            
            lifecycleScope.launch {
                delay(500)
                securityManager.bringToForeground(this@ExamLockdownActivity)
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        if (!isExamCompleted) {
            securityManager.logSecurityViolation(sessionId, "Activity paused - potential exit attempt")
        }
    }
    
    override fun onStop() {
        super.onStop()
        if (!isExamCompleted) {
            securityManager.logSecurityViolation(sessionId, "Activity stopped - exit attempt detected")
        }
    }
    
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus && !isExamCompleted) {
            securityManager.logSecurityViolation(sessionId, "Window lost focus - potential app switch")
            
            // Try to regain focus
            lifecycleScope.launch {
                delay(100)
                securityManager.bringToForeground(this@ExamLockdownActivity)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Unregister the receiver
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister receiver", e)
        }
        
        // Ensure security is disabled
        if (!isExamCompleted) {
            securityManager.endExamSession(sessionId)
        }
        securityManager.disableSecurityMode(this)
    }
} 