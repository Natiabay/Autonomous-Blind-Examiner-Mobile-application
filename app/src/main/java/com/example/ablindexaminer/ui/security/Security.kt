package com.example.ablindexaminer.ui.security

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler

/**
 * SecurityManager handles exam security concerns such as preventing
 * exit, monitoring system actions, and ensuring exam integrity.
 */
class SecurityManager(private val context: Context) {
    
    /**
     * Lock the screen to prevent screenshots, recent apps view, etc.
     * Should be called at the start of the exam.
     */
    fun lockScreen() {
        try {
            if (context is Activity) {
                // Allow screenshots and screen recordings
                // context.window.setFlags(
                //     WindowManager.LayoutParams.FLAG_SECURE,
                //     WindowManager.LayoutParams.FLAG_SECURE
                // )
                
                // Keep screen on during exam
                context.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                // Set as immersive full screen to make system UI harder to access
                val flags = (android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                        or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
                
                // Apply the UI flags
                context.window.decorView.systemUiVisibility = flags
                
                Log.d("SecurityManager", "Screen locked for security (screenshots allowed)")
            }
        } catch (e: Exception) {
            Log.e("SecurityManager", "Error locking screen: ${e.message}")
        }
    }
    
    /**
     * Unlock the screen when exam is complete.
     */
    fun unlockScreen() {
        try {
            if (context is Activity) {
                // Remove secure flag
                context.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                
                // Allow screen to turn off again
                context.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                
                // Restore normal UI visibility
                context.window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
                
                Log.d("SecurityManager", "Screen unlocked")
            }
        } catch (e: Exception) {
            Log.e("SecurityManager", "Error unlocking screen: ${e.message}")
        }
    }

    /**
     * Register to detect when the app goes to background
     * Will call onViolation callback when user attempts to leave the app
     */
    fun registerForegroundDetection(activity: Activity, onViolation: () -> Unit) {
        if (activity.application != null) {
            var lastPausedTime = 0L
            
            activity.application.registerActivityLifecycleCallbacks(object : android.app.Application.ActivityLifecycleCallbacks {
                override fun onActivityPaused(a: Activity) {
                    if (a === activity) {
                        lastPausedTime = System.currentTimeMillis()
                        Log.d("SecurityManager", "Activity paused")
                    }
                }
                
                override fun onActivityResumed(a: Activity) {
                    if (a === activity) {
                        if (System.currentTimeMillis() - lastPausedTime > 500) {
                            // If more than 0.5 seconds passed, the user likely switched apps
                            onViolation()
                            Log.d("SecurityManager", "Activity resumed after possible app switch")
                        }
                    }
                }
                
                // Implement other required callbacks
                override fun onActivityCreated(a: Activity, savedInstanceState: android.os.Bundle?) {}
                override fun onActivityStarted(a: Activity) {}
                override fun onActivityStopped(a: Activity) {}
                override fun onActivitySaveInstanceState(a: Activity, outState: android.os.Bundle) {}
                override fun onActivityDestroyed(a: Activity) {}
            })
        }
    }
    
    /**
     * Disable the back button to prevent exam exit.
     * Returns a OnBackPressedCallback that should be added to the activity.
     */
    fun disableBackButton(activity: ComponentActivity, onBackAttempt: () -> Unit): OnBackPressedCallback {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Custom action when back is pressed
                onBackAttempt()
                
                // Don't actually go back
                Log.d("SecurityManager", "Back button press intercepted")
            }
        }
        
        activity.onBackPressedDispatcher.addCallback(callback)
        Log.d("SecurityManager", "Back button disabled")
        
        return callback
    }
    
    /**
     * Enable the back button again when exam is complete.
     */
    fun enableBackButton(callback: OnBackPressedCallback) {
        callback.remove()
        Log.d("SecurityManager", "Back button enabled")
    }
    
    /**
     * Process key events to prevent certain key combinations (like task switcher).
     * Return true if the event should be consumed (prevented).
     */
    fun onKeyEvent(keyCode: Int, event: KeyEvent): Boolean {
        // Prevent home button, recent apps, etc.
        return when (keyCode) {
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_POWER -> {
                Log.d("SecurityManager", "Blocked key: $keyCode")
                true // Consume the event
            }
            else -> false // Allow other keys
        }
    }
}

/**
 * Composable function to apply security measures during an exam.
 */
@Composable
fun SecureExamScreen(
    isExamActive: Boolean,
    onSecurityViolation: () -> Unit = {}
) {
    val context = LocalContext.current
    val securityManager = remember { SecurityManager(context) }
    
    DisposableEffect(isExamActive) {
        if (isExamActive && context is Activity) {
            // Apply security measures
            securityManager.lockScreen()
            
            // Register for foreground detection to detect when user tries to leave app
            val activityCallbacks = object : android.app.Application.ActivityLifecycleCallbacks {
                private var lastPauseTime = 0L
                
                override fun onActivityPaused(activity: Activity) {
                    if (activity === context) {
                        lastPauseTime = System.currentTimeMillis()
                        Log.d("SecurityManager", "Exam paused")
                    }
                }
                
                override fun onActivityResumed(activity: Activity) {
                    if (activity === context) {
                        val pauseDuration = System.currentTimeMillis() - lastPauseTime
                        if (lastPauseTime > 0 && pauseDuration > 500) {
                            Log.w("SecurityManager", "Exam resumed after $pauseDuration ms")
                            // User tried to switch apps
                            onSecurityViolation()
                            // Re-lock the screen
                            securityManager.lockScreen()
                        }
                    }
                }
                
                // Required empty implementations
                override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) {}
                override fun onActivityStarted(activity: Activity) {}
                override fun onActivityStopped(activity: Activity) {}
                override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) {}
                override fun onActivityDestroyed(activity: Activity) {}
            }
            
            // Register the callbacks
            context.application.registerActivityLifecycleCallbacks(activityCallbacks)
            
            // Set up UI visibility change listener to detect system UI
            val decorView = context.window.decorView
            decorView.setOnSystemUiVisibilityChangeListener { visibility ->
                if (visibility and android.view.View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
                    // The system bars became visible - user may have swiped
                    Log.d("SecurityManager", "System UI visibility changed")
                    onSecurityViolation()
                    // Re-hide the UI
                    securityManager.lockScreen()
                }
            }
            
            onDispose {
                securityManager.unlockScreen()
                context.application.unregisterActivityLifecycleCallbacks(activityCallbacks)
                decorView.setOnSystemUiVisibilityChangeListener(null)
            }
        } else {
            onDispose { /* nothing to clean up if not active */ }
        }
    }
    
    // Handle back button presses with Compose BackHandler
    if (isExamActive) {
        BackHandler(true) {
            // Prevent back navigation and notify about violation
            onSecurityViolation()
        }
    }
} 