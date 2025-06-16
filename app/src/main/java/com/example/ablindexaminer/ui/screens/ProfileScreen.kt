@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.ablindexaminer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
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
import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import java.util.*
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    navController: NavController,
    username: String,
    userRole: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authService = remember { FirebaseAuthService() }
    val textToSpeech = remember { setupTextToSpeech(context) }
    
    var name by remember { mutableStateOf(username) }
    var email by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var isEditing by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Load user profile data
    LaunchedEffect(Unit) {
        speak(textToSpeech, "Profile page for $username")
        
        scope.launch {
            try {
                val userId = authService.getCurrentUserId()
                if (userId != null) {
                    val userDataResult = authService.getUserData(userId)
                    
                    if (userDataResult.isSuccess) {
                        val userData = userDataResult.getOrNull()
                        if (userData != null) {
                            // Update the user's full name from Firebase data
                            val fullName = userData["fullName"] as? String
                            if (!fullName.isNullOrBlank()) {
                                name = fullName
                            }
                            
                            email = userData["email"] as? String ?: ""
                            year = userData["year"] as? String ?: ""
                            section = userData["section"] as? String ?: ""
                            department = userData["department"] as? String ?: ""
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
                title = { Text("Profile") },
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
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Profile Header
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
                            imageVector = Icons.Default.Person,
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
                                contentDescription = "User name: $name"
                            }
                        )
                        
                        Text(
                            text = userRole.uppercase(),
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.semantics {
                                contentDescription = "Role: $userRole"
                            }
                        )
                    }
                }
                
                // User Information
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
                            text = "Personal Information",
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
                            onValueChange = { if (isEditing) email = it },
                            label = { Text("Email") },
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
                                .padding(bottom = 8.dp),
                            readOnly = !isEditing
                        )
                        
                        if (userRole.equals("student", ignoreCase = true)) {
                            OutlinedTextField(
                                value = year,
                                onValueChange = { if (isEditing) year = it },
                                label = { Text("Year") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                readOnly = !isEditing
                            )
                            
                            OutlinedTextField(
                                value = section,
                                onValueChange = { if (isEditing) section = it },
                                label = { Text("Section") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                readOnly = !isEditing
                            )
                        } else if (userRole.equals("teacher", ignoreCase = true)) {
                            OutlinedTextField(
                                value = department,
                                onValueChange = { if (isEditing) department = it },
                                label = { Text("Department") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                readOnly = !isEditing
                            )
                        }
                        
                        OutlinedTextField(
                            value = userRole.uppercase(),
                            onValueChange = { },
                            label = { Text("Role") },
                            modifier = Modifier.fillMaxWidth(),
                            readOnly = true
                        )
                    }
                }
                
                // Action buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { isEditing = !isEditing },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isEditing) "Cancel" else "Edit Profile")
                    }
                    
                    if (isEditing) {
                        Button(
                            onClick = {
                                scope.launch {
                                    val userId = authService.getCurrentUserId()
                                    if (userId != null) {
                                        // Update user information
                                        val updateData = mutableMapOf<String, Any>()
                                        updateData["fullName"] = name
                                        updateData["email"] = email
                                        updateData["phoneNumber"] = phoneNumber
                                        
                                        if (userRole.equals("student", ignoreCase = true)) {
                                            updateData["year"] = year
                                            updateData["section"] = section
                                        } else if (userRole.equals("teacher", ignoreCase = true)) {
                                            updateData["department"] = department
                                        }
                                        
                                        try {
                                            authService.updateUserData(userId, updateData)
                                            speak(textToSpeech, "Profile updated successfully")
                                            isEditing = false
                                        } catch (e: Exception) {
                                            speak(textToSpeech, "Failed to update profile")
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save Changes")
                        }
                    }
                }
                
                Button(
                    onClick = { showChangePasswordDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text("Change Password")
                }
                
                // Accessibility help button
                OutlinedButton(
                    onClick = {
                        speak(textToSpeech, "This is your profile page. Here you can view and edit your personal information, including email, year, section, and other details. You can also change your password using the Change Password button.")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Text("Read Profile Information Aloud")
                }
            }
        }
    }

    if (showChangePasswordDialog) {
        var currentPassword by remember { mutableStateOf("") }
        var newPassword by remember { mutableStateOf("") }
        var confirmPassword by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf("") }
        var isChangingPassword by remember { mutableStateOf(false) }
        var successMessage by remember { mutableStateOf("") }
        
        AlertDialog(
            onDismissRequest = { 
                if (!isChangingPassword) {
                    showChangePasswordDialog = false 
                }
            },
            title = { Text("Change Password") },
            text = {
                Column {
                    if (errorMessage.isNotEmpty()) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    
                    if (successMessage.isNotEmpty()) {
                        Text(
                            text = successMessage,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    
                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it },
                        label = { Text("Current Password") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = errorMessage.isNotEmpty(),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("New Password") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = errorMessage.isNotEmpty(),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm New Password") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = errorMessage.isNotEmpty(),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPassword != confirmPassword) {
                            errorMessage = "New passwords don't match"
                            speak(textToSpeech, "New passwords don't match")
                        } else if (newPassword.length < 6) {
                            errorMessage = "Password must be at least 6 characters"
                            speak(textToSpeech, "Password must be at least 6 characters")
                        } else {
                            errorMessage = ""
                            isChangingPassword = true

                            // Implement password change through Firebase
                            scope.launch {
                                try {
                                    // First reauthenticate with current password
                                    val reauthResult = authService.reauthenticateUser(currentPassword)
                                    
                                    if (reauthResult.isSuccess) {
                                        // Then update the password
                                        val updateResult = authService.updatePassword(newPassword)
                                        
                                        if (updateResult.isSuccess) {
                                            successMessage = "Password updated successfully"
                                            speak(textToSpeech, "Password updated successfully")
                                            
                                            // Close dialog after a brief delay
                                            kotlinx.coroutines.delay(2000)
                                            showChangePasswordDialog = false
                                        } else {
                                            errorMessage = updateResult.exceptionOrNull()?.message ?: "Failed to update password"
                                            speak(textToSpeech, "Failed to update password")
                                        }
                                    } else {
                                        errorMessage = "Current password is incorrect"
                                        speak(textToSpeech, "Current password is incorrect")
                                    }
                                } catch (e: Exception) {
                                    errorMessage = e.message ?: "An error occurred"
                                    speak(textToSpeech, "An error occurred while changing password")
                                } finally {
                                    isChangingPassword = false
                                }
                            }
                        }
                    },
                    enabled = !isChangingPassword && currentPassword.isNotBlank() &&
                             newPassword.isNotBlank() && confirmPassword.isNotBlank()
                ) {
                    if (isChangingPassword) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Change Password")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        if (!isChangingPassword) {
                            showChangePasswordDialog = false 
                        }
                    },
                    enabled = !isChangingPassword
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun setupTextToSpeech(context: Context): TextToSpeech {
    var textToSpeech: TextToSpeech? = null
    textToSpeech = TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) {
            val result = TextToSpeech.LANG_AVAILABLE
            // Set speaking rate slightly slower for better comprehension
            textToSpeech?.setSpeechRate(0.9f)
        }
    }
    return textToSpeech
}

private fun speak(textToSpeech: TextToSpeech, text: String) {
    val utteranceId = UUID.randomUUID().toString()
    textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
} 