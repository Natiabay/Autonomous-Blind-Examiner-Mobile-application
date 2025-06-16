package com.example.ablindexaminer.ui.security

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.example.ablindexaminer.ExamLockdownActivity

/**
 * Helper class to manage the launching of the secure exam activity
 * and handling the lockdown features for exams
 * 
 * @deprecated This class is deprecated. Use [ExamSecurityManager] instead.
 * The ExamSecurityManager provides more robust security features, centralized security state
 * management, and improved lifecycle handling. All new code should use ExamSecurityManager.
 */
@Deprecated("Use ExamSecurityManager instead", ReplaceWith("ExamSecurityManager.getInstance()"))
object ExamSecurityHelper {
    private const val TAG = "ExamSecurityHelper"
    
    // Store the result launcher for exams
    private var examResultLauncher: ActivityResultLauncher<Intent>? = null
    
    // Track exam sessions for security auditing
    private val activeExamSessions = mutableListOf<String>() // examIds
    
    /**
     * Register a result launcher to use for exam results
     * 
     * @deprecated Use ExamSecurityManager.registerResultLauncher() instead
     */
    @Deprecated("Use ExamSecurityManager.registerResultLauncher() instead", 
                ReplaceWith("ExamSecurityManager.getInstance().registerResultLauncher(launcher)"))
    fun registerResultLauncher(launcher: ActivityResultLauncher<Intent>) {
        examResultLauncher = launcher
        
        // Also register with the new security manager for forward compatibility
        ExamSecurityManager.getInstance().registerResultLauncher(launcher)
    }
    
    /**
     * Launch the exam in lockdown mode with enhanced security features
     * 
     * @deprecated Use ExamSecurityManager.launchSecureExam() instead
     */
    @Deprecated("Use ExamSecurityManager.launchSecureExam() instead", 
                ReplaceWith("ExamSecurityManager.getInstance().launchSecureExam(activity, examId, examTitle, duration, examType)"))
    fun launchSecureExam(
        activity: Activity,
        examId: String,
        examTitle: String,
        duration: Int,
        examType: String = "MULTIPLE_CHOICE",
        resultLauncher: ActivityResultLauncher<Intent>? = null
    ) {
        // Forward to the new security manager
        ExamSecurityManager.getInstance().launchSecureExam(
            activity = activity,
            examId = examId,
            examTitle = examTitle,
            duration = duration,
            examType = examType,
            examResultLauncher = resultLauncher
        )
    }
    
    /**
     * Prepare device for secure exam by requesting necessary permissions
     * and optimization settings
     */
    private fun prepareDeviceForSecureExam(activity: Activity) {
        // Request to ignore battery optimizations for reliable operation
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val packageName = activity.packageName
                val powerManager = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
                
                if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                    // Request permission to ignore battery optimizations
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
        
        // For API 23+, check for system overlay permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(activity)) {
            try {
                Toast.makeText(
                    activity,
                    "The app needs permission to display over other apps for secure exams",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show overlay permission toast", e)
            }
        }
    }
    
    /**
     * Enable app pinning mode if available (works on API 21+)
     */
    fun startAppPinning(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                // Check if we already have permission for app pinning
                if (isAppPinningAvailable(activity)) {
                    activity.startLockTask()
                    Log.d(TAG, "Started lock task successfully")
                } else {
                    // Guide the user to enable app pinning
                    Toast.makeText(
                        activity,
                        "Please enable app pinning in settings to secure the exam",
                        Toast.LENGTH_LONG
                    ).show()
                    
                    // On newer versions, take them to the screen pinning settings
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        try {
                            activity.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to open security settings", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start app pinning: ${e.message}", e)
                // Handle gracefully if we can't start app pinning
                Toast.makeText(
                    activity,
                    "Could not secure exam fully. Please notify your instructor.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    /**
     * Stop app pinning mode if active
     */
    fun stopAppPinning(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                activity.stopLockTask()
                Log.d(TAG, "Stopped lock task successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop app pinning: ${e.message}", e)
            }
        }
    }
    
    /**
     * Check if app pinning is available on this device
     */
    private fun isAppPinningAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false
        }
        
        // For Android M and above, check if lock task is allowed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            return try {
                // On some devices, we can check if we're allowed to use lock task mode
                val lockTaskFeatures = activityManager.getLockTaskModeState()
                
                // We prefer to explicitly check the settings value if possible
                val settingsValue = Settings.Global.getInt(
                    context.contentResolver, 
                    "lock_to_app_enabled", 
                    0
                )
                
                // If the settings value is enabled or we're already allowed to do lock task mode
                settingsValue != 0 || lockTaskFeatures != ActivityManager.LOCK_TASK_MODE_NONE
            } catch (e: Exception) {
                // If we can't check, attempt a direct approach
                try {
                    // Try to detect if we're a device owner or profile owner
                    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                    val admin = ComponentName(context, "com.example.ablindexaminer.DeviceAdminReceiver")
                    dpm.isDeviceOwnerApp(context.packageName) || dpm.isProfileOwnerApp(context.packageName)
                } catch (ex: Exception) {
                    // If all checks fail, we'll try start lock task mode anyway
                    Log.w(TAG, "Could not determine lock task availability", e)
                    true
                }
            }
        } else {
            // On Lollipop, we don't have a reliable way to check,
            // so we'll assume it's available
            return true
        }
    }
    
    /**
     * Check if the device is currently in a secure exam mode
     */
    fun isInSecureExamMode(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // For API 23+, we can check lock task mode state
                    activityManager.getLockTaskModeState() != ActivityManager.LOCK_TASK_MODE_NONE
                } else {
                    // For Lollipop, use the deprecated method
                    @Suppress("DEPRECATION")
                    activityManager.isInLockTaskMode
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check secure exam mode", e)
                false
            }
        }
        return false
    }
    
    /**
     * Verify activity is running in secure environment
     * Returns true if secure, false otherwise
     */
    fun verifySecureEnvironment(activity: Activity): Boolean {
        // List of security checks
        var isSecure = true
        
        // Check 1: Screenshots are now allowed
        // if ((activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE) == 0) {
        //     isSecure = false
        //     Log.w(TAG, "Security check failed: Screenshots not disabled")
        // }
        
        // Check 2: Are we in lock task mode?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val activityManager = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (activityManager.getLockTaskModeState() == ActivityManager.LOCK_TASK_MODE_NONE) {
                    isSecure = false
                    Log.w(TAG, "Security check failed: Not in lock task mode")
                }
            } else {
                @Suppress("DEPRECATION")
                if (!activityManager.isInLockTaskMode) {
                    isSecure = false
                    Log.w(TAG, "Security check failed: Not in lock task mode (legacy)")
                }
            }
        }
        
        return isSecure
    }
    
    /**
     * Log an exam security violation
     */
    fun logSecurityViolation(examId: String, violation: String) {
        val timestamp = System.currentTimeMillis()
        Log.w(TAG, "[$examId] Security violation at $timestamp: $violation")
        
        // In a real implementation, you might want to send this to a server or
        // store it locally for later review by the exam administrator
    }
} 