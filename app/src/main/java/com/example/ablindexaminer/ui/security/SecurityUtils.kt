package com.example.ablindexaminer.ui.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.io.File

/**
 * Utility class for system-level security checks
 * to detect potential security risks or device tampering
 */
object SecurityUtils {
    private const val TAG = "SecurityUtils"
    
    /**
     * Performs a comprehensive security check of the device environment
     * Returns a list of detected security issues
     */
    fun checkDeviceSecurity(context: Context): List<SecurityIssue> {
        val issues = mutableListOf<SecurityIssue>()
        
        // Check for root
        if (isDeviceRooted()) {
            issues.add(SecurityIssue(
                title = "Device is rooted",
                description = "Root access may allow bypassing security measures",
                severity = SecurityIssue.Severity.HIGH
            ))
        }
        
        // Check for developer options
        if (isDeveloperOptionsEnabled(context)) {
            issues.add(SecurityIssue(
                title = "Developer options enabled",
                description = "Developer options may allow USB debugging or mock locations",
                severity = SecurityIssue.Severity.MEDIUM
            ))
        }
        
        // Check for USB debugging
        if (isUsbDebuggingEnabled(context)) {
            issues.add(SecurityIssue(
                title = "USB debugging enabled",
                description = "USB debugging allows external device control",
                severity = SecurityIssue.Severity.HIGH
            ))
        }
        
        // Check for suspicious apps
        checkForSuspiciousApps(context)?.let { issues.add(it) }
        
        // Check for mock locations
        if (isMockLocationEnabled(context)) {
            issues.add(SecurityIssue(
                title = "Mock location enabled",
                description = "Apps can fake device location",
                severity = SecurityIssue.Severity.MEDIUM
            ))
        }
        
        // Check for device emulator
        if (isEmulator()) {
            issues.add(SecurityIssue(
                title = "Running on emulator",
                description = "Exams should be taken on real devices",
                severity = SecurityIssue.Severity.MEDIUM
            ))
        }
        
        return issues
    }
    
    /**
     * Check if the device is rooted by looking for common root indicators
     */
    fun isDeviceRooted(): Boolean {
        // Check for common root management apps
        val rootApps = arrayOf(
            "com.noshufou.android.su",
            "com.noshufou.android.su.elite",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.thirdparty.superuser",
            "com.topjohnwu.magisk"
        )
        
        // Check for common root files
        val rootFiles = arrayOf(
            "/system/app/Superuser.apk",
            "/system/xbin/su",
            "/system/bin/su",
            "/sbin/su",
            "/system/xbin/daemonsu",
            "/system/etc/init.d/99SuperSUDaemon",
            "/system/etc/.installed_su_daemon",
            "/dev/com.koushikdutta.superuser.daemon/"
        )
        
        // Check for su binary
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec("su")
            true  // If no exception, su command worked - device is rooted
        } catch (e: Exception) {
            // Check for root files
            rootFiles.any { File(it).exists() }
        } finally {
            process?.destroy()
        }
    }
    
    /**
     * Check if developer options are enabled
     */
    fun isDeveloperOptionsEnabled(context: Context): Boolean {
        return Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0
        ) == 1
    }
    
    /**
     * Check if USB debugging is enabled
     */
    fun isUsbDebuggingEnabled(context: Context): Boolean {
        return Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.ADB_ENABLED, 0
        ) == 1
    }
    
    /**
     * Check for mock location apps being enabled in developer settings
     */
    fun isMockLocationEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // For Android M and above
            try {
                val locationMode = Settings.Secure.getInt(
                    context.contentResolver, 
                    Settings.Secure.ALLOW_MOCK_LOCATION
                )
                return locationMode != 0
            } catch (e: Exception) {
                Log.e(TAG, "Error checking mock location setting", e)
            }
            
            // Try alternative method for Android M+
            try {
                val appContext = context.applicationContext
                val pm = appContext.packageManager
                val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                
                // Check for apps with mock location permissions
                // Note: In Android M+, we can't directly check for ACCESS_MOCK_LOCATION
                // as it's a hidden permission, so we check for apps with location permissions
                // that are not system apps
                return packages.any { applicationInfo ->
                    applicationInfo.flags and ApplicationInfo.FLAG_INSTALLED != 0 &&
                    applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0 &&
                    (pm.checkPermission(
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        applicationInfo.packageName
                    ) == PackageManager.PERMISSION_GRANTED ||
                    pm.checkPermission(
                        android.Manifest.permission.ACCESS_COARSE_LOCATION,
                        applicationInfo.packageName
                    ) == PackageManager.PERMISSION_GRANTED)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking mock location apps", e)
            }
        } else {
            // For Android L and below
            return Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ALLOW_MOCK_LOCATION, 0
            ) != 0
        }
        
        return false
    }
    
    /**
     * Check if running on an emulator
     */
    fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
                || "google_sdk" == Build.PRODUCT)
    }
    
    /**
     * Check for known security bypass or screen recording apps
     */
    private fun checkForSuspiciousApps(context: Context): SecurityIssue? {
        val suspiciousApps = arrayOf(
            "com.koushikdutta.rommanager",
            "com.dimonvideo.luckypatcher",
            "com.chelpus.lackypatch",
            "com.ramdroid.appquarantine",
            "com.blackmartalpha",
            "org.blackmart.market",
            "com.icecoldapps.screencam",
            "com.icecoldapps.videorecorder",
            "com.koushikdutta.superuser",
            "com.zachspong.temprootremovejb",
            "com.amphoras.hidemyroot",
            "com.saurik.substrate"
        )
        
        try {
            val pm = context.packageManager
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            
            // Get names of suspicious apps that are installed
            val foundApps = installedApps
                .filter { app -> suspiciousApps.contains(app.packageName) }
                .map { app ->
                    try {
                        pm.getApplicationLabel(app).toString()
                    } catch (e: Exception) {
                        app.packageName
                    }
                }
            
            if (foundApps.isNotEmpty()) {
                return SecurityIssue(
                    title = "Potentially harmful apps detected",
                    description = "Found: ${foundApps.joinToString(", ")}",
                    severity = SecurityIssue.Severity.HIGH
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for suspicious apps", e)
        }
        
        return null
    }
    
    /**
     * Data class to represent a security issue
     */
    data class SecurityIssue(
        val title: String,
        val description: String,
        val severity: Severity
    ) {
        enum class Severity {
            LOW, MEDIUM, HIGH
        }
        
        override fun toString(): String {
            return "[$severity] $title: $description"
        }
    }
} 