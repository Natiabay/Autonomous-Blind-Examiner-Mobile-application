package com.example.ablindexaminer.ui.screens

import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ablindexaminer.data.firebase.FirebaseAuthService
import kotlinx.coroutines.launch
import java.util.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import com.example.ablindexaminer.ui.accessibility.BlindNavigationController
import com.example.ablindexaminer.ui.accessibility.BlindNavigationWrapper
import com.example.ablindexaminer.ui.accessibility.NavigationItem

enum class AuthScreen {
    LOGIN,
    FORGOT_PASSWORD,
    VERIFY_CODE,
    NEW_PASSWORD
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: (String, UserRole) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    
    var currentScreen by remember { mutableStateOf(AuthScreen.LOGIN) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var isAccessibilityEnabled by remember { mutableStateOf(true) }
    
    // For password reset process
    var phoneNumber by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }
    var actualVerificationCode by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmNewPassword by remember { mutableStateOf("") }
    var userId by remember { mutableStateOf("") }
    
    val coroutineScope = rememberCoroutineScope()
    val authService = remember { FirebaseAuthService() }
    
    // Text-to-speech engine for accessibility
    val textToSpeech = remember { initializeTextToSpeech(context) }
    
    // Welcome announcement for screen readers
    LaunchedEffect(Unit) {
        if (isAccessibilityEnabled) {
        speak(textToSpeech, "Welcome to Blind Examiner. Please enter your credentials to log in.")
        }
    }

    // Create a richer gradient background with deeper colors
    val gradientColors = listOf(
        Color(0xFF0D324D),  // Deep blue
        Color(0xFF1A4A66),  // Medium blue
        Color(0xFF2E5D7B),  // Lighter blue
        Color(0xFF1F3A4D)   // Bottom edge color
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(colors = gradientColors)
            )
    ) {
        // Accessibility toggle switch with label
        Row(
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopStart),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(
                checked = isAccessibilityEnabled,
                onCheckedChange = {
                    isAccessibilityEnabled = it
                    if (!it) {
                        textToSpeech.stop()
                    } else {
                        speak(textToSpeech, "Accessibility features enabled")
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF3C8DBC),
                    uncheckedThumbColor = Color.LightGray
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isAccessibilityEnabled) "Accessibility On" else "Accessibility Off",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }

        // Login screen content
        when (currentScreen) {
            AuthScreen.LOGIN -> {
                LoginContent(
                    username = username,
                    password = password,
                    isPasswordVisible = isPasswordVisible,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    successMessage = successMessage,
                    isAccessibilityEnabled = isAccessibilityEnabled,
                    onUsernameChange = { username = it },
                    onPasswordChange = { password = it },
                    onPasswordVisibilityToggle = { isPasswordVisible = !isPasswordVisible },
                    onLoginClick = {
                        if (username.isBlank()) {
                            errorMessage = "Username or email is required"
                            if (isAccessibilityEnabled) {
                            speak(textToSpeech, "Username or email is required. Please enter your username or email.")
                            }
                            return@LoginContent
                        }
                        
                        if (password.isBlank()) {
                            errorMessage = "Password is required"
                            if (isAccessibilityEnabled) {
                            speak(textToSpeech, "Password is required. Please enter your password.")
                            }
                            return@LoginContent
                        }
                        
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        isLoading = true
                        errorMessage = null
                        
                        // Check admin credentials
                        if (username == "natiabay1017@gmail.com" && password == "1017912") {
                            if (isAccessibilityEnabled) {
                            speak(textToSpeech, "Admin login successful")
                            }
                            onLoginSuccess(username, UserRole.ADMIN)
                            return@LoginContent
                        }
                        
                        // Authenticate students and instructors (via username or email)
                        coroutineScope.launch {
                            try {
                                val result = authService.signInWithEmailAndPassword(username, password)
                                isLoading = false
                                
                                if (result.isSuccess) {
                                    val userInfo = result.getOrNull()
                                    val userIdentifier = userInfo?.first ?: username
                                    val userRole = userInfo?.second ?: UserRole.STUDENT
                                    if (isAccessibilityEnabled) {
                                    speak(textToSpeech, "Login successful")
                                    }
                                    onLoginSuccess(userIdentifier, userRole)
                                } else {
                                    errorMessage = "Authentication failed. Please check your credentials."
                                    if (isAccessibilityEnabled) {
                                    speak(textToSpeech, "Authentication failed. Please check your credentials.")
                                    }
                                }
                            } catch (e: Exception) {
                                isLoading = false
                                errorMessage = "Error: ${e.message}"
                                if (isAccessibilityEnabled) {
                                speak(textToSpeech, "Authentication error. Please try again.")
                                }
                            }
                        }
                    },
                    onForgotPasswordClick = {
                        currentScreen = AuthScreen.FORGOT_PASSWORD
                        errorMessage = null
                        successMessage = null
                        if (isAccessibilityEnabled) {
                        speak(textToSpeech, "Forgot password screen. Enter your username or email to reset your password.")
                        }
                    },
                    focusManager = focusManager,
                    textToSpeech = textToSpeech
                )
            }
            AuthScreen.FORGOT_PASSWORD -> {
                ForgotPasswordContent(
                    identifier = username,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    successMessage = successMessage,
                    onIdentifierChange = { username = it },
                    onBackClick = {
                        currentScreen = AuthScreen.LOGIN
                        errorMessage = null
                        successMessage = null
                        if (isAccessibilityEnabled) {
                        speak(textToSpeech, "Back to login screen.")
                        }
                    },
                    onResetClick = {
                        if (username.isBlank()) {
                            errorMessage = "Username or email is required"
                            if (isAccessibilityEnabled) {
                            speak(textToSpeech, "Username or email is required. Please enter your username or email.")
                            }
                            return@ForgotPasswordContent
                        }
                        
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        isLoading = true
                        errorMessage = null
                        successMessage = null
                        
                        coroutineScope.launch {
                            try {
                                // For admin (using email)
                                if (username.contains("@")) {
                                    val result = authService.sendPasswordResetEmail(username)
                                    isLoading = false
                                    
                                    if (result.isSuccess) {
                                        successMessage = "Password reset email sent. Please check your inbox."
                                        if (isAccessibilityEnabled) {
                                        speak(textToSpeech, "Password reset email sent. Please check your inbox.")
                                        }
                                    } else {
                                        errorMessage = "Failed to send reset email: ${result.exceptionOrNull()?.message}"
                                        if (isAccessibilityEnabled) {
                                        speak(textToSpeech, "Failed to send reset email. Please try again.")
                                        }
                                    }
                                } else {
                                    // For students and instructors (using username)
                                    val userResult = authService.findUserByUsernameOrEmail(username)
                                    if (userResult.isSuccess && userResult.getOrNull() != null) {
                                        val userData = userResult.getOrNull()!!
                                        userId = userData["id"] as? String ?: ""
                                        val phone = userData["phoneNumber"] as? String
                                        
                                        if (!phone.isNullOrBlank()) {
                                            phoneNumber = phone
                                            // Request verification code
                                            coroutineScope.launch {
                                                try {
                                                    val codeResult = authService.sendSmsVerificationCode(phoneNumber)
                                                    if (codeResult.isSuccess) {
                                                        actualVerificationCode = codeResult.getOrNull() ?: ""
                                                        currentScreen = AuthScreen.VERIFY_CODE
                                                        errorMessage = null
                                                        if (isAccessibilityEnabled) {
                                                        speak(textToSpeech, "Verification code sent to your phone. Please enter the code.")
                                                        }
                                                    } else {
                                                        errorMessage = "Failed to send verification code."
                                                        if (isAccessibilityEnabled) {
                                                        speak(textToSpeech, "Failed to send verification code. Please try again.")
                                                        }
                                                    }
                                                    isLoading = false
                                                } catch (e: Exception) {
                                                    isLoading = false
                                                    errorMessage = "Error: ${e.message}"
                                                }
                                            }
                                        } else {
                                            isLoading = false
                                            errorMessage = "No phone number found for this account."
                                            if (isAccessibilityEnabled) {
                                            speak(textToSpeech, "No phone number found for this account. Please contact support.")
                                            }
                                        }
                                    } else {
                                        isLoading = false
                                        errorMessage = "User not found"
                                        if (isAccessibilityEnabled) {
                                        speak(textToSpeech, "User not found. Please check your username.")
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                isLoading = false
                                errorMessage = "Error: ${e.message}"
                                if (isAccessibilityEnabled) {
                                speak(textToSpeech, "Error processing your request. Please try again.")
                                }
                            }
                        }
                    },
                    focusManager = focusManager
                )
            }
            AuthScreen.VERIFY_CODE -> {
                VerificationCodeContent(
                    verificationCode = verificationCode,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    onCodeChange = { verificationCode = it },
                    onBackClick = {
                        currentScreen = AuthScreen.FORGOT_PASSWORD
                        errorMessage = null
                        if (isAccessibilityEnabled) {
                        speak(textToSpeech, "Back to forgot password screen.")
                        }
                    },
                    onVerifyClick = {
                        if (verificationCode.length != 6) {
                            errorMessage = "Please enter all 6 digits"
                            if (isAccessibilityEnabled) {
                            speak(textToSpeech, "Please enter all 6 digits of the verification code.")
                            }
                            return@VerificationCodeContent
                        }
                        
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        isLoading = true
                        errorMessage = null
                        
                        coroutineScope.launch {
                            try {
                                val result = authService.verifySmsCode(actualVerificationCode, verificationCode)
                                isLoading = false
                                
                                if (result.isSuccess && result.getOrNull() == true) {
                                    currentScreen = AuthScreen.NEW_PASSWORD
                                    if (isAccessibilityEnabled) {
                                    speak(textToSpeech, "Code verified. Please enter your new password.")
                                    }
                                } else {
                                    errorMessage = "Invalid verification code"
                                    if (isAccessibilityEnabled) {
                                    speak(textToSpeech, "Invalid verification code. Please try again.")
                                    }
                                }
                            } catch (e: Exception) {
                                isLoading = false
                                errorMessage = "Error: ${e.message}"
                                if (isAccessibilityEnabled) {
                                speak(textToSpeech, "Error verifying code. Please try again.")
                                }
                            }
                        }
                    },
                    focusManager = focusManager
                )
            }
            AuthScreen.NEW_PASSWORD -> {
                NewPasswordContent(
                    newPassword = newPassword,
                    confirmPassword = confirmNewPassword,
                    isPasswordVisible = isPasswordVisible,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    onNewPasswordChange = { newPassword = it },
                    onConfirmPasswordChange = { confirmNewPassword = it },
                    onPasswordVisibilityToggle = { isPasswordVisible = !isPasswordVisible },
                    onBackClick = {
                        currentScreen = AuthScreen.VERIFY_CODE
                        errorMessage = null
                        if (isAccessibilityEnabled) {
                        speak(textToSpeech, "Back to verification screen.")
                        }
                    },
                    onUpdateClick = {
                        if (newPassword.length < 6) {
                            errorMessage = "Password must be at least 6 characters"
                            if (isAccessibilityEnabled) {
                            speak(textToSpeech, "Password must be at least 6 characters.")
                            }
                            return@NewPasswordContent
                        }
                        
                        if (newPassword != confirmNewPassword) {
                            errorMessage = "Passwords do not match"
                            if (isAccessibilityEnabled) {
                            speak(textToSpeech, "Passwords do not match. Please try again.")
                            }
                            return@NewPasswordContent
                        }
                        
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        isLoading = true
                        errorMessage = null
                        
                        coroutineScope.launch {
                            try {
                                // In real app, update with Firebase Auth Admin SDK
                                val result = authService.resetUserPassword(userId, newPassword) 
                                isLoading = false
                                
                                if (result.isSuccess) {
                                    currentScreen = AuthScreen.LOGIN
                                    username = ""
                                    password = ""
                                    successMessage = "Password updated successfully. Please login with your new password."
                                    if (isAccessibilityEnabled) {
                                    speak(textToSpeech, "Password updated successfully. Please login with your new password.")
                                    }
                                } else {
                                    errorMessage = "Failed to update password: ${result.exceptionOrNull()?.message}"
                                    if (isAccessibilityEnabled) {
                                    speak(textToSpeech, "Failed to update password. Please try again.")
                                    }
                                }
                            } catch (e: Exception) {
                                isLoading = false
                                errorMessage = "Error: ${e.message}"
                                if (isAccessibilityEnabled) {
                                speak(textToSpeech, "Error updating password. Please try again.")
                                }
                            }
                        }
                    },
                    focusManager = focusManager
                )
            }
        }
        
        // Attribution text
        Text(
            text = "© 2023 A. Blind Examiner",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun LoginContent(
    username: String,
    password: String,
    isPasswordVisible: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    successMessage: String?,
    isAccessibilityEnabled: Boolean,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordVisibilityToggle: () -> Unit,
    onLoginClick: () -> Unit,
    onForgotPasswordClick: () -> Unit,
    focusManager: androidx.compose.ui.focus.FocusManager,
    textToSpeech: TextToSpeech
) {
    val focusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    // Accessibility: focus index (0=username, 1=password, 2=forgot, 3=login)
    var focusIndex by remember { mutableStateOf(0) }
    val focusLabels = listOf(
        "Username field. Double tap to enter your username.",
        "Password field. Double tap to enter your password.",
        "Forgot password link. Double tap to reset password.",
        "Login button. Double tap to log in."
    )
    // Announce focus change only if accessibility is enabled
    LaunchedEffect(focusIndex) {
        if (isAccessibilityEnabled) {
            speak(textToSpeech, focusLabels[focusIndex])
        }
    }

    // Gesture handling for swipe and double-tap
    var lastTapTime by remember { mutableStateOf(0L) }
    val doubleTapThreshold = 300L
    var dragStartX by remember { mutableStateOf(0f) }
    var dragEndX by remember { mutableStateOf(0f) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
            .then(
                if (isAccessibilityEnabled) {
                    Modifier
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset: androidx.compose.ui.geometry.Offset -> dragStartX = offset.x },
                                onDragEnd = {
                                    val dragDistance = dragEndX - dragStartX
                                    if (abs(dragDistance) > 80) {
                                        if (dragDistance > 0) {
                                            // Swipe right: next element
                                            focusIndex = (focusIndex + 1) % focusLabels.size
                                        } else {
                                            // Swipe left: previous element
                                            focusIndex = if (focusIndex > 0) focusIndex - 1 else focusLabels.size - 1
                                        }
                                    }
                                },
                                onDrag = { change: androidx.compose.ui.input.pointer.PointerInputChange, _: androidx.compose.ui.geometry.Offset -> dragEndX = change.position.x }
                            )
                        }
                        .pointerInteropFilter { event ->
                            when (event.action) {
                                android.view.MotionEvent.ACTION_UP -> {
                                    val currentTime = System.currentTimeMillis()
                                    if (currentTime - lastTapTime < doubleTapThreshold) {
                                        // Double tap: activate focused element
                                        when (focusIndex) {
                                            0 -> focusRequester.requestFocus()
                                            1 -> passwordFocusRequester.requestFocus()
                                            2 -> onForgotPasswordClick()
                                            3 -> onLoginClick()
                                        }
                                    }
                                    lastTapTime = currentTime
                                }
                            }
                            false
                        }
                } else {
                    Modifier
                }
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo and App Title
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF3C8DBC),  // Bright blue center
                            Color(0xFF1A4A66)   // Darker blue edge
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Visibility,
                contentDescription = "App Logo",
                tint = Color.White,
                modifier = Modifier.size(65.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // App title
        Text(
            text = "Blind Examiner",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif,
            color = Color.White,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .semantics {
                    contentDescription = "Blind Examiner"
                }
        )
        
        Text(
            text = "Accessible Examination Platform",
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier
                .padding(bottom = 40.dp)
        )
        
        // Error message if any
        errorMessage?.let {
            Text(
                text = it,
                color = Color(0xFFFF6B6B),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .semantics {
                        contentDescription = "Error: $it"
                    }
            )
        }
        
        // Success message if any
        successMessage?.let {
            Text(
                text = it,
                color = Color(0xFF4ECCA3),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .semantics {
                        contentDescription = "Success: $it"
                    }
            )
        }
        
        // Username input field
        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = { Text("Username") },
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = "Username Icon",
                    tint = Color.White.copy(alpha = 0.9f)
                )
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { passwordFocusRequester.requestFocus() }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .focusRequester(focusRequester)
                .semantics { contentDescription = "Enter your username" },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.White.copy(alpha = 0.7f),
                focusedLabelColor = Color.White,
                unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White.copy(alpha = 0.9f),
                cursorColor = Color.White
            )
        )
        
        // Password input field
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = "Password Icon",
                    tint = Color.White.copy(alpha = 0.9f)
                )
            },
            trailingIcon = {
                IconButton(onClick = onPasswordVisibilityToggle) {
                    Icon(
                        imageVector = if (isPasswordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (isPasswordVisible) "Hide Password" else "Show Password",
                        tint = Color.White.copy(alpha = 0.9f)
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    onLoginClick()
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .focusRequester(passwordFocusRequester)
                .semantics { contentDescription = "Enter your password" },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.White.copy(alpha = 0.7f),
                focusedLabelColor = Color.White,
                unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White.copy(alpha = 0.9f),
                cursorColor = Color.White
            )
        )
        
        // Forgot password link
        Text(
            text = "Forgot password?",
            color = Color.White,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.End)
                .clickable(onClick = onForgotPasswordClick)
                .padding(vertical = 8.dp)
                .semantics {
                    contentDescription = "Forgot password? Click to reset."
                }
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Login button
        ElevatedButton(
            onClick = onLoginClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .semantics {
                    contentDescription = "Log in button"
                },
            enabled = !isLoading,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.elevatedButtonColors(
                containerColor = Color(0xFF3C8DBC),
                contentColor = Color.White,
                disabledContainerColor = Color(0xFF3C8DBC).copy(alpha = 0.5f)
            ),
            elevation = ButtonDefaults.elevatedButtonElevation(
                defaultElevation = 6.dp
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Login,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Log In",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ForgotPasswordContent(
    identifier: String,
    isLoading: Boolean,
    errorMessage: String?,
    successMessage: String?,
    onIdentifierChange: (String) -> Unit,
    onBackClick: () -> Unit,
    onResetClick: () -> Unit,
    focusManager: androidx.compose.ui.focus.FocusManager
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Back button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.semantics {
                    contentDescription = "Back to login"
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            
            Text(
                text = "Back to Login",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Title and description
        Text(
            text = "Reset Password",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .align(Alignment.Start)
                .semantics {
                    contentDescription = "Reset Password"
                }
        )
        
        Text(
            text = "Enter your username, student ID, or email to reset your password",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier
                .padding(bottom = 32.dp)
                .align(Alignment.Start)
        )
        
        // Error message
        errorMessage?.let {
            Text(
                text = it,
                color = Color(0xFFFF6B6B),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .semantics {
                        contentDescription = "Error: $it"
                    }
            )
        }
        
        // Success message
        successMessage?.let {
            Text(
                text = it,
                color = Color(0xFF4ECCA3),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .semantics {
                        contentDescription = "Success: $it"
                    }
            )
        }
        
        // Email/Username input
        OutlinedTextField(
            value = identifier,
            onValueChange = onIdentifierChange,
            label = { Text("Username, Student ID, or Email") },
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = "Username or Email Icon",
                    tint = Color.White.copy(alpha = 0.9f)
                )
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    onResetClick()
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
                .semantics {
                    contentDescription = "Enter your username, student ID, or email"
                },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.White.copy(alpha = 0.7f),
                focusedLabelColor = Color.White,
                unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White.copy(alpha = 0.9f),
                cursorColor = Color.White
            )
        )
        
        // Reset button
        ElevatedButton(
            onClick = onResetClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .semantics {
                    contentDescription = "Reset password button"
                },
            enabled = !isLoading,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.elevatedButtonColors(
                containerColor = Color(0xFF3C8DBC),
                contentColor = Color.White,
                disabledContainerColor = Color(0xFF3C8DBC).copy(alpha = 0.5f)
            ),
            elevation = ButtonDefaults.elevatedButtonElevation(
                defaultElevation = 6.dp
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.LockReset,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Reset Password",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun VerificationCodeContent(
    verificationCode: String,
    isLoading: Boolean,
    errorMessage: String?,
    onCodeChange: (String) -> Unit,
    onBackClick: () -> Unit,
    onVerifyClick: () -> Unit,
    focusManager: androidx.compose.ui.focus.FocusManager
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Back button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.semantics {
                    contentDescription = "Back to forgot password"
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            
            Text(
                text = "Back",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Title and description
        Text(
            text = "Verification Code",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .align(Alignment.Start)
                .semantics {
                    contentDescription = "Verification Code"
                }
        )
        
        Text(
            text = "Enter the 6-digit code sent to your phone number",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier
                .padding(bottom = 32.dp)
                .align(Alignment.Start)
        )
        
        // Error message
        errorMessage?.let {
            Text(
                text = it,
                color = Color(0xFFFF6B6B),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .semantics {
                        contentDescription = "Error: $it"
                    }
            )
        }
        
        // Verification code input
        OutlinedTextField(
            value = verificationCode,
            onValueChange = { 
                // Only allow digits
                if (it.all { char -> char.isDigit() } && it.length <= 6) {
                    onCodeChange(it)
                }
            },
            label = { Text("Verification Code") },
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Pin,
                    contentDescription = "Code Icon",
                    tint = Color.White.copy(alpha = 0.9f)
                )
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    if (verificationCode.length == 6) {
                        onVerifyClick()
                    }
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
                .semantics {
                    contentDescription = "Enter verification code"
                },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.White.copy(alpha = 0.7f),
                focusedLabelColor = Color.White,
                unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White.copy(alpha = 0.9f),
                cursorColor = Color.White
            )
        )
        
        // Verify button
        ElevatedButton(
            onClick = onVerifyClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .semantics {
                    contentDescription = "Verify code button"
                },
            enabled = !isLoading && verificationCode.length == 6,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.elevatedButtonColors(
                containerColor = Color(0xFF3C8DBC),
                contentColor = Color.White,
                disabledContainerColor = Color(0xFF3C8DBC).copy(alpha = 0.5f)
            ),
            elevation = ButtonDefaults.elevatedButtonElevation(
                defaultElevation = 6.dp
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Verify Code",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun NewPasswordContent(
    newPassword: String,
    confirmPassword: String,
    isPasswordVisible: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    onNewPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onPasswordVisibilityToggle: () -> Unit,
    onBackClick: () -> Unit,
    onUpdateClick: () -> Unit,
    focusManager: androidx.compose.ui.focus.FocusManager
) {
    val confirmPasswordFocusRequester = remember { FocusRequester() }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Back button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.semantics {
                    contentDescription = "Back to verification"
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            
            Text(
                text = "Back",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Title and description
        Text(
            text = "Set New Password",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .align(Alignment.Start)
                .semantics {
                    contentDescription = "Set New Password"
                }
        )
        
        Text(
            text = "Create a new password for your account",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier
                .padding(bottom = 32.dp)
                .align(Alignment.Start)
        )
        
        // Error message
        errorMessage?.let {
            Text(
                text = it,
                color = Color(0xFFFF6B6B),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .semantics {
                        contentDescription = "Error: $it"
                    }
            )
        }
        
        // New password input
        OutlinedTextField(
            value = newPassword,
            onValueChange = onNewPasswordChange,
            label = { Text("New Password") },
            singleLine = true,
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = "Password Icon",
                    tint = Color.White.copy(alpha = 0.9f)
                )
            },
            trailingIcon = {
                IconButton(onClick = onPasswordVisibilityToggle) {
                    Icon(
                        imageVector = if (isPasswordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (isPasswordVisible) "Hide Password" else "Show Password",
                        tint = Color.White.copy(alpha = 0.9f)
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = {
                    confirmPasswordFocusRequester.requestFocus()
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .semantics {
                    contentDescription = "Enter new password"
                },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.White.copy(alpha = 0.7f),
                focusedLabelColor = Color.White,
                unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White.copy(alpha = 0.9f),
                cursorColor = Color.White
            )
        )
        
        // Confirm password input
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = onConfirmPasswordChange,
            label = { Text("Confirm Password") },
            singleLine = true,
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = "Confirm Password Icon",
                    tint = Color.White.copy(alpha = 0.9f)
                )
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    if (newPassword.length >= 6 && newPassword == confirmPassword) {
                        onUpdateClick()
                    }
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
                .focusRequester(confirmPasswordFocusRequester)
                .semantics {
                    contentDescription = "Confirm new password"
                },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.White.copy(alpha = 0.7f),
                focusedLabelColor = Color.White,
                unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White.copy(alpha = 0.9f),
                cursorColor = Color.White
            )
        )
        
        // Update button
        ElevatedButton(
            onClick = onUpdateClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .semantics {
                    contentDescription = "Update password button"
                },
            enabled = !isLoading && newPassword.length >= 6 && newPassword == confirmPassword,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.elevatedButtonColors(
                containerColor = Color(0xFF3C8DBC),
                contentColor = Color.White,
                disabledContainerColor = Color(0xFF3C8DBC).copy(alpha = 0.5f)
            ),
            elevation = ButtonDefaults.elevatedButtonElevation(
                defaultElevation = 6.dp
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Save,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Update Password",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun initializeTextToSpeech(context: Context): TextToSpeech {
    return TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) {
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                TextToSpeech.LANG_AVAILABLE
            } else {
                TextToSpeech.SUCCESS
            }
        }
    }
}

private fun speak(textToSpeech: TextToSpeech, text: String) {
    textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
}

@Composable
fun PasswordRecoveryScreen(
    onBackToLogin: () -> Unit,
    authService: FirebaseAuthService
) {
    val context = LocalContext.current
    var username by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var successMessage by remember { mutableStateOf("") }
    var currentStep by remember { mutableStateOf(1) } // 1: Enter details, 2: Verify SMS, 3: Set new password
    var verificationId by remember { mutableStateOf("") }
    var isCodeSent by remember { mutableStateOf(false) }
    
    // Text-to-speech for accessibility
    val textToSpeech = remember { 
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setSpeechRate(0.8f)
                tts?.setPitch(1.1f)
            }
        }
        tts
    }
    
    DisposableEffect(Unit) {
        onDispose {
            textToSpeech?.shutdown()
        }
    }
    
    LaunchedEffect(Unit) {
        textToSpeech?.speak(
            "Password recovery screen. Enter your username and phone number to reset your password.",
            TextToSpeech.QUEUE_FLUSH,
            null,
            "initial_${System.currentTimeMillis()}"
        )
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            IconButton(
                onClick = {
                    textToSpeech?.speak(
                        "Returning to login screen",
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        "back_${System.currentTimeMillis()}"
                    )
                    onBackToLogin()
                },
                modifier = Modifier.semantics {
                    contentDescription = "Go back to login screen"
                }
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back to Login"
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Title
        Text(
            text = "Reset Password",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics {
                contentDescription = "Reset Password Screen"
            }
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        when (currentStep) {
            1 -> {
                // Step 1: Enter username and phone number
                Text(
                    text = "Step 1: Verify Your Identity",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    placeholder = { Text("Enter your username") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .semantics {
                            contentDescription = "Username input field"
                        },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )
                
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("Phone Number") },
                    placeholder = { Text("+1234567890") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .semantics {
                            contentDescription = "Phone number input field"
                        },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                
                Button(
                    onClick = {
                        if (username.isBlank() || phoneNumber.isBlank()) {
                            errorMessage = "Please enter both username and phone number"
                            textToSpeech?.speak(
                                "Error: Please enter both username and phone number",
                                TextToSpeech.QUEUE_FLUSH,
                                null,
                                "error_${System.currentTimeMillis()}"
                            )
                            return@Button
                        }
                        
                        isLoading = true
                        errorMessage = ""
                        
                        // For demo purposes, we'll simulate SMS sending
                        // In real implementation, you would use Firebase Auth Phone verification
                        CoroutineScope(Dispatchers.Main).launch {
                            try {
                                delay(2000) // Simulate network delay
                                
                                // Simulate successful verification setup
                                verificationId = "demo_verification_${System.currentTimeMillis()}"
                                isCodeSent = true
                                currentStep = 2
                                successMessage = "Verification code sent to $phoneNumber"
                                
                                textToSpeech?.speak(
                                    "Verification code sent to your phone number. Please enter the 6-digit code you received.",
                                    TextToSpeech.QUEUE_FLUSH,
                                    null,
                                    "success_${System.currentTimeMillis()}"
                                )
                            } catch (e: Exception) {
                                errorMessage = "Failed to send verification code: ${e.message}"
                                textToSpeech?.speak(
                                    "Error: Failed to send verification code",
                                    TextToSpeech.QUEUE_FLUSH,
                                    null,
                                    "error_${System.currentTimeMillis()}"
                                )
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .semantics {
                            contentDescription = "Send verification code to phone number"
                        },
                    enabled = !isLoading && username.isNotBlank() && phoneNumber.isNotBlank()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Send Verification Code", fontSize = 16.sp)
                    }
                }
            }
            
            2 -> {
                // Step 2: Verify SMS code
                Text(
                    text = "Step 2: Enter Verification Code",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Text(
                    text = "Enter the 6-digit code sent to $phoneNumber",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                OutlinedTextField(
                    value = verificationCode,
                    onValueChange = { verificationCode = it },
                    label = { Text("Verification Code") },
                    placeholder = { Text("123456") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .semantics {
                            contentDescription = "6-digit verification code input field"
                        },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                
                Button(
                    onClick = {
                        if (verificationCode.length != 6) {
                            errorMessage = "Please enter a valid 6-digit code"
                            textToSpeech?.speak(
                                "Error: Please enter a valid 6-digit code",
                                TextToSpeech.QUEUE_FLUSH,
                                null,
                                "error_${System.currentTimeMillis()}"
                            )
                            return@Button
                        }
                        
                        isLoading = true
                        errorMessage = ""
                        
                        CoroutineScope(Dispatchers.Main).launch {
                            try {
                                delay(1500) // Simulate verification delay
                                
                                // For demo purposes, accept any 6-digit code
                                // In real implementation, verify with Firebase
                                if (verificationCode.length == 6) {
                                    currentStep = 3
                                    successMessage = "Phone number verified successfully"
                                    textToSpeech?.speak(
                                        "Phone number verified. Now enter your new password.",
                                        TextToSpeech.QUEUE_FLUSH,
                                        null,
                                        "success_${System.currentTimeMillis()}"
                                    )
                                } else {
                                    throw Exception("Invalid verification code")
                                }
                            } catch (e: Exception) {
                                errorMessage = "Invalid verification code"
                                textToSpeech?.speak(
                                    "Error: Invalid verification code",
                                    TextToSpeech.QUEUE_FLUSH,
                                    null,
                                    "error_${System.currentTimeMillis()}"
                                )
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .semantics {
                            contentDescription = "Verify the entered code"
                        },
                    enabled = !isLoading && verificationCode.length == 6
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Verify Code", fontSize = 16.sp)
                    }
                }
            }
            
            3 -> {
                // Step 3: Set new password
                Text(
                    text = "Step 3: Set New Password",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("New Password") },
                    placeholder = { Text("Enter new password") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .semantics {
                            contentDescription = "New password input field"
                        },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm Password") },
                    placeholder = { Text("Confirm new password") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .semantics {
                            contentDescription = "Confirm new password input field"
                        },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                
                Button(
                    onClick = {
                        if (newPassword.isBlank() || confirmPassword.isBlank()) {
                            errorMessage = "Please enter and confirm your new password"
                            textToSpeech?.speak(
                                "Error: Please enter and confirm your new password",
                                TextToSpeech.QUEUE_FLUSH,
                                null,
                                "error_${System.currentTimeMillis()}"
                            )
                            return@Button
                        }
                        
                        if (newPassword != confirmPassword) {
                            errorMessage = "Passwords do not match"
                            textToSpeech?.speak(
                                "Error: Passwords do not match",
                                TextToSpeech.QUEUE_FLUSH,
                                null,
                                "error_${System.currentTimeMillis()}"
                            )
                            return@Button
                        }
                        
                        if (newPassword.length < 6) {
                            errorMessage = "Password must be at least 6 characters long"
                            textToSpeech?.speak(
                                "Error: Password must be at least 6 characters long",
                                TextToSpeech.QUEUE_FLUSH,
                                null,
                                "error_${System.currentTimeMillis()}"
                            )
                            return@Button
                        }
                        
                        isLoading = true
                        errorMessage = ""
                        
                        CoroutineScope(Dispatchers.Main).launch {
                            try {
                                // For demo purposes, simulate password update
                                // In real implementation, update password via Firebase
                                delay(2000)
                                
                                successMessage = "Password updated successfully!"
                                textToSpeech?.speak(
                                    "Password updated successfully! Returning to login screen.",
                                    TextToSpeech.QUEUE_FLUSH,
                                    null,
                                    "success_${System.currentTimeMillis()}"
                                )
                                
                                delay(2000)
                                onBackToLogin()
                            } catch (e: Exception) {
                                errorMessage = "Failed to update password: ${e.message}"
                                textToSpeech?.speak(
                                    "Error: Failed to update password",
                                    TextToSpeech.QUEUE_FLUSH,
                                    null,
                                    "error_${System.currentTimeMillis()}"
                                )
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .semantics {
                            contentDescription = "Update password and complete recovery"
                        },
                    enabled = !isLoading && newPassword.isNotBlank() && confirmPassword.isNotBlank()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Update Password", fontSize = 16.sp)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Error message
        if (errorMessage.isNotEmpty()) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .semantics {
                        contentDescription = "Error: $errorMessage"
                    }
            )
        }
        
        // Success message
        if (successMessage.isNotEmpty()) {
            Text(
                text = successMessage,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .semantics {
                        contentDescription = "Success: $successMessage"
                    }
            )
        }
    }
} 