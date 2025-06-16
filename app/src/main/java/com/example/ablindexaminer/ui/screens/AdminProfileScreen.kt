@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.ablindexaminer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.ablindexaminer.data.firebase.FirebaseAuthService
import com.example.ablindexaminer.utils.ThemeManager
import com.example.ablindexaminer.utils.rememberThemeManager
import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import java.util.*
import kotlinx.coroutines.launch

@Composable
fun AdminProfileScreen(
    navController: NavController,
    username: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authService = remember { FirebaseAuthService() }
    val textToSpeech = remember { setupTextToSpeech(context) }
    val themeManager = rememberThemeManager(context)
    
    var name by remember { mutableStateOf(username) }
    var email by remember { mutableStateOf("natiabay1017@gmail.com") }
    var adminRole by remember { mutableStateOf("System Administrator") }
    var department by remember { mutableStateOf("Information Technology") }
    var phoneNumber by remember { mutableStateOf("") }
    var isEditing by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    
    // Get theme states from ThemeManager
    val isDarkMode by themeManager.isDarkMode
    val isVoiceGuidanceEnabled by themeManager.isVoiceGuidanceEnabled
    
    // Additional settings using SharedPreferences for features not in ThemeManager
    val sharedPref = context.getSharedPreferences("admin_settings", Context.MODE_PRIVATE)
    var autoLogoutEnabled by remember { mutableStateOf(sharedPref.getBoolean("auto_logout", false)) }
    var notificationsEnabled by remember { mutableStateOf(sharedPref.getBoolean("notifications", true)) }
    
    // Load admin profile data
    LaunchedEffect(Unit) {
        if (isVoiceGuidanceEnabled) {
            speak(textToSpeech, "Admin Profile page for $username")
        }
        
        scope.launch {
            try {
                val userId = authService.getCurrentUserId()
                if (userId != null) {
                    val userDataResult = authService.getUserData(userId)
                    
                    if (userDataResult.isSuccess) {
                        val userData = userDataResult.getOrNull()
                        if (userData != null) {
                            // Update admin data from Firebase
                            name = userData["fullName"] as? String ?: "Administrator"
                            email = userData["email"] as? String ?: "natiabay1017@gmail.com"
                            adminRole = userData["adminRole"] as? String ?: "System Administrator"
                            department = userData["department"] as? String ?: "Information Technology"
                            phoneNumber = userData["phoneNumber"] as? String ?: ""
                        }
                    }
                }
            } catch (e: Exception) {
                // Handle error
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Profile") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Admin Profile Header
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AdminPanelSettings,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = name,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics {
                            contentDescription = "Administrator name: $name"
                        }
                    )
                    
                    Text(
                        text = "ADMINISTRATOR",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics {
                            contentDescription = "Role: Administrator"
                        }
                    )
                }
            }
            
            // Admin Information
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Administrator Information",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    OutlinedTextField(
                        value = name,
                        onValueChange = { if (isEditing) name = it },
                        label = { Text("Full Name") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        readOnly = !isEditing
                    )
                    
                    OutlinedTextField(
                        value = email,
                        onValueChange = { /* Email cannot be changed for admin */ },
                        label = { Text("Email") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        readOnly = true // Admin email is fixed
                    )
                    
                    OutlinedTextField(
                        value = adminRole,
                        onValueChange = { if (isEditing) adminRole = it },
                        label = { Text("Admin Role") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        readOnly = !isEditing
                    )
                    
                    OutlinedTextField(
                        value = department,
                        onValueChange = { if (isEditing) department = it },
                        label = { Text("Department") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        readOnly = !isEditing
                    )
                    
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { if (isEditing) phoneNumber = it },
                        label = { Text("Phone Number") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        readOnly = !isEditing
                    )
                }
            }
            
            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (isEditing) {
                    Button(
                        onClick = {
                            scope.launch {
                                isSaving = true
                                try {
                                    val userId = authService.getCurrentUserId()
                                    if (userId != null) {
                                        val adminData = mapOf(
                                            "fullName" to name,
                                            "adminRole" to adminRole,
                                            "department" to department,
                                            "phoneNumber" to phoneNumber
                                        )
                                        val result = authService.updateUserData(userId, adminData)
                                        if (result.isSuccess) {
                                            if (isVoiceGuidanceEnabled) {
                                                speak(textToSpeech, "Admin profile updated successfully")
                                            }
                                            isEditing = false
                                        } else {
                                            if (isVoiceGuidanceEnabled) {
                                                speak(textToSpeech, "Error updating profile")
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    if (isVoiceGuidanceEnabled) {
                                        speak(textToSpeech, "Error updating profile: ${e.message}")
                                    }
                                } finally {
                                    isSaving = false
                                }
                            }
                        },
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Save")
                        }
                    }
                    
                    OutlinedButton(
                        onClick = {
                            isEditing = false
                            if (isVoiceGuidanceEnabled) {
                                speak(textToSpeech, "Edit cancelled")
                            }
                        }
                    ) {
                        Text("Cancel")
                    }
                } else {
                    Button(
                        onClick = {
                            isEditing = true
                            if (isVoiceGuidanceEnabled) {
                                speak(textToSpeech, "Edit mode enabled")
                            }
                        }
                    ) {
                        Text("Edit Profile")
                    }
                    
                    OutlinedButton(
                        onClick = {
                            showChangePasswordDialog = true
                            if (isVoiceGuidanceEnabled) {
                                speak(textToSpeech, "Change password dialog")
                            }
                        }
                    ) {
                        Text("Change Password")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Settings Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Administrator Settings",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    // Voice Guidance Setting
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Voice Guidance",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "For teachers and administrators only",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Switch(
                            checked = isVoiceGuidanceEnabled,
                            onCheckedChange = { enabled ->
                                themeManager.setVoiceGuidance(enabled)
                                val message = if (enabled) "Voice guidance enabled" else "Voice guidance disabled"
                                speak(textToSpeech, message)
                            },
                            modifier = Modifier.semantics {
                                contentDescription = "Voice guidance setting for teachers and administrators"
                            }
                        )
                    }
                    
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    // Dark Mode Setting
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable Dark Mode",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Reduces eye strain in low light",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Switch(
                            checked = isDarkMode,
                            onCheckedChange = { enabled ->
                                themeManager.setDarkMode(enabled)
                                val message = if (enabled) "Dark mode enabled. Please restart the app to see changes." else "Dark mode disabled. Please restart the app to see changes."
                                if (isVoiceGuidanceEnabled) {
                                    speak(textToSpeech, message)
                                }
                            },
                            modifier = Modifier.semantics {
                                contentDescription = "Dark mode setting"
                            }
                        )
                    }
                    
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    // Auto Logout Setting
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto Logout",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Automatically logout after inactivity",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Switch(
                            checked = autoLogoutEnabled,
                            onCheckedChange = { enabled ->
                                autoLogoutEnabled = enabled
                                sharedPref.edit().putBoolean("auto_logout", enabled).apply()
                                
                                val message = if (enabled) "Auto logout enabled" else "Auto logout disabled"
                                if (isVoiceGuidanceEnabled) {
                                    speak(textToSpeech, message)
                                }
                            },
                            modifier = Modifier.semantics {
                                contentDescription = "Auto logout setting"
                            }
                        )
                    }
                    
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    // Notification Settings
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Exam Notifications",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Receive notifications about exam activities",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = { enabled ->
                                notificationsEnabled = enabled
                                sharedPref.edit().putBoolean("notifications", enabled).apply()
                                
                                val message = if (enabled) "Notifications enabled" else "Notifications disabled"
                                if (isVoiceGuidanceEnabled) {
                                    speak(textToSpeech, message)
                                }
                            },
                            modifier = Modifier.semantics {
                                contentDescription = "Exam notifications setting"
                            }
                        )
                    }
                }
            }
        }
    }
    
    // Change Password Dialog
    if (showChangePasswordDialog) {
        AdminChangePasswordDialog(
            onDismiss = { showChangePasswordDialog = false },
            onPasswordChanged = {
                if (isVoiceGuidanceEnabled) {
                    speak(textToSpeech, "Password changed successfully")
                }
                showChangePasswordDialog = false
            },
            textToSpeech = if (isVoiceGuidanceEnabled) textToSpeech else null
        )
    }
}

@Composable
private fun AdminChangePasswordDialog(
    onDismiss: () -> Unit,
    onPasswordChanged: () -> Unit,
    textToSpeech: TextToSpeech?
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    val scope = rememberCoroutineScope()
    val authService = remember { FirebaseAuthService() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Password") },
        text = {
            Column {
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it },
                    label = { Text("Current Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
                
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { 
                        newPassword = it
                        errorMessage = ""
                    },
                    label = { Text("New Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
                
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { 
                        confirmPassword = it
                        errorMessage = ""
                    },
                    label = { Text("Confirm New Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                
                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        currentPassword.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty() -> {
                            errorMessage = "All fields are required"
                            textToSpeech?.speak("All fields are required", TextToSpeech.QUEUE_FLUSH, null, null)
                        }
                        newPassword != confirmPassword -> {
                            errorMessage = "New passwords do not match"
                            textToSpeech?.speak("New passwords do not match", TextToSpeech.QUEUE_FLUSH, null, null)
                        }
                        newPassword.length < 6 -> {
                            errorMessage = "Password must be at least 6 characters"
                            textToSpeech?.speak("Password must be at least 6 characters", TextToSpeech.QUEUE_FLUSH, null, null)
                        }
                        else -> {
                            scope.launch {
                                isLoading = true
                                try {
                                    val result = authService.changePassword(currentPassword, newPassword)
                                    if (result.isSuccess) {
                                        onPasswordChanged()
                                    } else {
                                        errorMessage = "Failed to change password. Check your current password."
                                        textToSpeech?.speak("Failed to change password. Check your current password.", TextToSpeech.QUEUE_FLUSH, null, null)
                                    }
                                } catch (e: Exception) {
                                    errorMessage = "Error: ${e.message}"
                                    textToSpeech?.speak("Error changing password", TextToSpeech.QUEUE_FLUSH, null, null)
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    }
                },
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Change Password")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun setupTextToSpeech(context: android.content.Context): TextToSpeech {
    return TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) {
            // Set speaking rate slightly slower for better comprehension
            // TTS will be configured by the calling code
        }
    }
}

private fun speak(textToSpeech: TextToSpeech, text: String) {
    textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
}