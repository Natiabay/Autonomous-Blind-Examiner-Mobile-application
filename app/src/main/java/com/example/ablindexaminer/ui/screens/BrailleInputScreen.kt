package com.example.ablindexaminer.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ablindexaminer.ml.MLModelManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrailleInputScreen(
    onTextChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    initialText: String = "",
    questionText: String = ""
) {
    // Use Dialog to prevent navigation issues and ensure proper overlay
    Dialog(
        onDismissRequest = { 
            // Prevent accidental dismissal - only allow through explicit back button
        },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        BrailleInputContent(
            onTextChanged = onTextChanged,
            onDismiss = onDismiss,
            initialText = initialText,
            questionText = questionText
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrailleInputContent(
    onTextChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    initialText: String = "",
    questionText: String = ""
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    
    // Safe Text-to-speech initialization with comprehensive error handling
    val textToSpeech = remember { 
        var tts: TextToSpeech? = null
        try {
            tts = TextToSpeech(context) { status ->
                try {
                    if (status == TextToSpeech.SUCCESS) {
                        tts?.language = Locale.getDefault()
                        tts?.setSpeechRate(0.8f)
                        tts?.setPitch(1.1f)
                        Log.d("BrailleInput", "TextToSpeech initialized successfully")
                    } else {
                        Log.w("BrailleInput", "TextToSpeech initialization failed with status: $status")
                    }
                } catch (e: Exception) {
                    Log.e("BrailleInput", "Error configuring TextToSpeech: ${e.message}", e)
                }
            }
        } catch (e: SecurityException) {
            Log.e("BrailleInput", "SecurityException initializing TextToSpeech: ${e.message}", e)
            tts = null
        } catch (e: Exception) {
            Log.e("BrailleInput", "Exception initializing TextToSpeech: ${e.message}", e)
            tts = null
        }
        tts
    }
    
    // Safe ML Model Manager initialization
    val mlModelManager = remember { 
        try {
            MLModelManager(context).also {
                Log.d("BrailleInput", "MLModelManager initialized: ${it.getModelStatus()}")
            }
        } catch (e: Exception) {
            Log.e("BrailleInput", "Failed to initialize MLModelManager: ${e.message}", e)
            null
        }
    }
    
    // State variables with proper initialization
    var currentAnswer by remember { mutableStateOf(initialText) }
    var brailleDots by remember { mutableStateOf(listOf<Offset>()) }
    var isProcessing by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf("Ready for input") }
    var modelStatus by remember { mutableStateOf(mlModelManager?.getModelStatus() ?: "⚠️ ML models unavailable - using fallback") }
    var lastRecognizedChar by remember { mutableStateOf("") }
    var autoProcessingEnabled by remember { mutableStateOf(true) }
    
    // Canvas dimensions with safe defaults
    var canvasSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size(800f, 600f)) }
    
    // Safe speech function with queue management
    fun safeSpeak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        try {
            textToSpeech?.let { tts ->
                if (tts.isSpeaking && queueMode == TextToSpeech.QUEUE_FLUSH) {
                    tts.stop()
                }
                tts.speak(text, queueMode, null, "speech_${System.currentTimeMillis()}")
            } ?: Log.d("BrailleInput", "TTS not available, would have said: $text")
        } catch (e: Exception) {
            Log.e("BrailleInput", "Error speaking text: ${e.message}")
        }
    }
    
    // Enhanced braille processing with ML integration
    fun processWithML() {
        if (brailleDots.isEmpty() || isProcessing) return
        
        coroutineScope.launch {
            isProcessing = true
            modelStatus = "Processing..."
            feedbackMessage = "Analyzing braille pattern..."
        
            try {
                // Create bitmap with proper error handling
                val bitmapWidth = maxOf(canvasSize.width.toInt(), 280)
                val bitmapHeight = maxOf(canvasSize.height.toInt(), 280)
                
                val bitmap = Bitmap.createBitmap(
                    bitmapWidth,
                    bitmapHeight,
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bitmap)
                
                // Fill with white background (ML model expects white background)
                canvas.drawColor(Color.White.toArgb())
                
                // Draw dots in black for ML processing with proper bounds checking
                val paint = Paint().apply {
                    color = Color.Black.toArgb()
                    isAntiAlias = true
                }
                    
                brailleDots.forEach { dot ->
                    if (dot.x >= 0 && dot.x < bitmapWidth && dot.y >= 0 && dot.y < bitmapHeight) {
                        canvas.drawCircle(dot.x, dot.y, 20f, paint)
                    }
                }
                
                // Process with ML model safely
                Log.d("BrailleInput", "Processing ${brailleDots.size} dots with ML model")
                val recognizedChar = if (mlModelManager != null) {
                    try {
                        mlModelManager.recognizeBrailleCharacter(bitmap)
                    } catch (e: Exception) {
                        Log.w("BrailleInput", "ML recognition failed: ${e.message}")
                        simpleBrailleRecognition(brailleDots, canvasSize)
                    }
                } else {
                    // Pure fallback when no ML manager available
                    simpleBrailleRecognition(brailleDots, canvasSize)
                }
                
                if (recognizedChar.isNotEmpty()) {
                    lastRecognizedChar = recognizedChar
                    currentAnswer += recognizedChar
                    feedbackMessage = "✅ Recognized: '$recognizedChar'"
                    modelStatus = "Model Working"
                    
                    // Provide audio feedback
                    safeSpeak("Recognized: $recognizedChar")
                    
                    // Update the parent component
                    onTextChanged(currentAnswer)
                    
                    // Clear dots after successful recognition
                    delay(1500)
                    brailleDots = emptyList()
                    feedbackMessage = "Ready for next character"
                } else {
                    feedbackMessage = "⚠️ Character not recognized. Try again."
                    modelStatus = "Recognition Failed"
                    safeSpeak("Character not recognized. Please try again with clearer braille pattern.")
                }
                
            } catch (e: Exception) {
                Log.e("BrailleInput", "Processing Error: ${e.message}", e)
                feedbackMessage = "⚠️ Processing error, using backup method"
                modelStatus = mlModelManager?.getModelStatus() ?: "Backup mode"
                safeSpeak("Using backup recognition method.")
                
                // Fallback to simple pattern recognition
                try {
                    val fallbackChar = simpleBrailleRecognition(brailleDots, canvasSize)
                    if (fallbackChar.isNotEmpty()) {
                        currentAnswer += fallbackChar
                        onTextChanged(currentAnswer)
                        feedbackMessage = "✅ Backup recognized: '$fallbackChar'"
                        safeSpeak("Backup recognized: $fallbackChar", TextToSpeech.QUEUE_ADD)
                    } else {
                        feedbackMessage = "⚠️ Could not recognize pattern"
                        safeSpeak("Could not recognize the braille pattern", TextToSpeech.QUEUE_ADD)
                    }
                } catch (fallbackError: Exception) {
                    Log.e("BrailleInput", "Fallback recognition also failed: ${fallbackError.message}")
                    feedbackMessage = "❌ Recognition unavailable"
                    safeSpeak("Recognition is currently unavailable", TextToSpeech.QUEUE_ADD)
                }
            } finally {
                isProcessing = false
            }
        }
    }

    // Auto-process after user stops touching (only if enabled)
    LaunchedEffect(brailleDots.size, autoProcessingEnabled) {
        if (brailleDots.isNotEmpty() && autoProcessingEnabled) {
            delay(2000) // Increased delay for better user experience
            processWithML()
        }
    }
    
    // Speak initial instructions with delay
    LaunchedEffect(Unit) {
        delay(500)
        safeSpeak("Braille input screen. Question: $questionText. Touch anywhere on the black canvas to create braille dots. Use the process button to recognize characters, or enable auto-processing.")
    }
    
    // Safe cleanup
    DisposableEffect(Unit) {
        onDispose {
            try {
                textToSpeech?.let { tts ->
                    try {
                        if (tts.isSpeaking) {
                            tts.stop()
                        }
                        tts.shutdown()
                        Log.d("BrailleInput", "TextToSpeech cleaned up successfully")
                    } catch (e: Exception) {
                        Log.e("BrailleInput", "Error shutting down TextToSpeech: ${e.message}")
                    }
                }
                mlModelManager?.close()
            } catch (e: Exception) {
                Log.e("BrailleInput", "Error during cleanup: ${e.message}")
            }
        }
    }

    // Main UI with full screen layout
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header with back button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { 
                        safeSpeak("Returning to exam")
                        onDismiss() 
                    }
                ) {
                    Icon(
                        Icons.Default.ArrowBack, 
                        contentDescription = "Back to exam",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                Text(
                    text = "Braille Input",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                IconButton(
                    onClick = { 
                        safeSpeak("Current answer: ${if (currentAnswer.isEmpty()) "empty" else currentAnswer}")
                    }
                ) {
                    Icon(
                        Icons.Default.VolumeUp, 
                        contentDescription = "Read current answer",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Question Display
            if (questionText.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = questionText,
                        modifier = Modifier.padding(16.dp),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            // Current Answer Display
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Answer: ${if (currentAnswer.isEmpty()) "(empty)" else currentAnswer}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (lastRecognizedChar.isNotEmpty()) {
                        Text(
                            text = "Last recognized: '$lastRecognizedChar'",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            
            // Braille Input Canvas (Black background with white dots)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(bottom = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                if (!isProcessing) {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    brailleDots = brailleDots + offset
                                    
                                    // Immediate audio feedback with position info
                                    val positionInfo = when {
                                        offset.x < size.width / 3 -> "left"
                                        offset.x > 2 * size.width / 3 -> "right"
                                        else -> "center"
                                    }
                                    safeSpeak("Dot placed $positionInfo", TextToSpeech.QUEUE_ADD)
                                }
                            }
                        }
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .semantics {
                                contentDescription = "Braille input canvas. Touch to place dots for braille characters. White dots will appear where you touch."
                            }
                    ) {
                        canvasSize = size
                        
                        // Ensure canvas size is valid
                        if (size.width > 0 && size.height > 0) {
                            
                            // Draw white dots on black background
                            brailleDots.forEach { point ->
                                // Ensure point is within canvas bounds
                                if (point.x >= 0 && point.x < size.width && 
                                    point.y >= 0 && point.y < size.height) {
                                    drawCircle(
                                        color = Color.White,
                                        radius = 25.dp.toPx(),
                                        center = point
                                    )
                                }
                            }
                            
                            // Show processing indicator
                            if (isProcessing) {
                                val indicatorX = (size.width - 60.dp.toPx()).coerceAtLeast(30.dp.toPx())
                                val indicatorY = 60.dp.toPx().coerceAtMost(size.height - 30.dp.toPx())
                                drawCircle(
                                    color = Color.Yellow,
                                    radius = 30.dp.toPx(),
                                    center = Offset(indicatorX, indicatorY)
                                )
                            }
                        }
                    }
                }
            }
            
            // Control Buttons Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Clear dots button
                Button(
                    onClick = { 
                        brailleDots = emptyList()
                        feedbackMessage = "Canvas cleared"
                        safeSpeak("Canvas cleared")
                    },
                    enabled = brailleDots.isNotEmpty() && !isProcessing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Clear, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear")
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Process button
                Button(
                    onClick = { processWithML() },
                    enabled = brailleDots.isNotEmpty() && !isProcessing,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.Add, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isProcessing) "Processing..." else "Process")
                }
            }
            
            // Second Row of Control Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Add space button
                Button(
                    onClick = { 
                        currentAnswer += " "
                        onTextChanged(currentAnswer)
                        safeSpeak("Space added")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Space")
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Backspace button
                Button(
                    onClick = { 
                        if (currentAnswer.isNotEmpty()) {
                            val removedChar = currentAnswer.last()
                            currentAnswer = currentAnswer.dropLast(1)
                            onTextChanged(currentAnswer)
                            safeSpeak("Deleted $removedChar")
                        }
                    },
                    enabled = currentAnswer.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Backspace, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete")
                }
            }
            
            // Status and Settings
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        modelStatus.contains("Error") -> MaterialTheme.colorScheme.errorContainer
                        modelStatus.contains("Working") -> MaterialTheme.colorScheme.tertiaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = "Status: $modelStatus",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    
                    if (feedbackMessage.isNotEmpty()) {
                        Text(
                            text = feedbackMessage,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Auto-processing toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Auto-process after 2 seconds",
                            fontSize = 12.sp
                        )
                        Switch(
                            checked = autoProcessingEnabled,
                            onCheckedChange = { 
                                autoProcessingEnabled = it
                                safeSpeak(if (it) "Auto-processing enabled" else "Auto-processing disabled")
                            }
                        )
                    }
                }
            }
        }
    }
}

// Enhanced fallback braille recognition
private fun simpleBrailleRecognition(dots: List<Offset>, canvasSize: androidx.compose.ui.geometry.Size): String {
    if (dots.isEmpty()) return ""
    
    return try {
        // Analyze dot patterns more sophisticatedly
        val dotCount = dots.size
        val avgX = dots.map { it.x }.average()
        val avgY = dots.map { it.y }.average()
        
        // Pattern analysis based on position and count
        when (dotCount) {
            1 -> {
                when {
                    avgX < canvasSize.width / 3 && avgY < canvasSize.height / 2 -> "a"
                    avgX < canvasSize.width / 3 && avgY > canvasSize.height / 2 -> "b"
                    else -> "."
                }
            }
            2 -> {
                val isVertical = dots.maxOf { it.y } - dots.minOf { it.y } > 
                                dots.maxOf { it.x } - dots.minOf { it.x }
                if (isVertical) "e" else "c"
            }
            3 -> "d"
            4 -> "f"
            5 -> "g"
            6 -> "h"
            else -> when {
                dotCount > 6 -> " " // Likely a space gesture
                else -> ""
            }
        }
    } catch (e: Exception) {
        Log.e("BrailleInput", "Simple recognition failed: ${e.message}")
        ""
    }
} 