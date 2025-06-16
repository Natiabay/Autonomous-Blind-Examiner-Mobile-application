package com.example.ablindexaminer.ui.security

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

/**
 * Device Administrator receiver for enhanced security controls
 * 
 * This receiver enables our app to use enhanced lockdown features
 * when granted device administrator privileges. This is optional and
 * improves security when available, but the app will fall back to
 * regular security measures when these permissions aren't granted.
 */
class ExamDeviceAdminReceiver : DeviceAdminReceiver() {
    companion object {
        private const val TAG = "ExamDeviceAdmin"
    }
    
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device admin enabled")
        Toast.makeText(
            context, 
            "Enhanced exam security enabled", 
            Toast.LENGTH_SHORT
        ).show()
    }
    
    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w(TAG, "Device admin disabled")
        Toast.makeText(
            context, 
            "Enhanced exam security disabled", 
            Toast.LENGTH_SHORT
        ).show()
    }
    
    override fun onLockTaskModeEntering(context: Context, intent: Intent, pkg: String) {
        super.onLockTaskModeEntering(context, intent, pkg)
        Log.i(TAG, "Lock task mode entered for package: $pkg")
    }
    
    override fun onLockTaskModeExiting(context: Context, intent: Intent) {
        super.onLockTaskModeExiting(context, intent)
        Log.i(TAG, "Lock task mode exited")
    }
} 