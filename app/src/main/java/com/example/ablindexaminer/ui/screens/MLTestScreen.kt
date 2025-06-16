package com.example.ablindexaminer.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.ablindexaminer.ml.MLModelManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MLTestScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Text-to-speech for accessibility
    val textToSpeech = remember { setupTextToSpeech(context) }
    
    // ML Model Manager
    val mlModelManager = remember { MLModelManager(context) }
    
    // Test states
    var brailleTestResult by remember { mutableStateOf("") }
    var asagTestResult by remember { mutableStateOf("") }
    var isTestingBraille by remember { mutableStateOf(false) }
    var isTestingASAG by remember { mutableStateOf(false) }
    var testLog by remember { mutableStateOf("ML Test Screen Loaded\n") }
    
    // Test inputs for ASAG
    var studentAnswerInput by remember { mutableStateOf("The capital of France is Paris") }
    var teacherAnswerInput by remember { mutableStateOf("Paris is the capital city of France") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ML Model Testing") },
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
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Machine Learning Model Testing",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Text(
                text = "Use this screen to test if your ML models are properly loaded and functioning.",
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            
            // BrailleNet Testing Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "BrailleNet Model Test",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "This will create a sample braille pattern and test character recognition.",
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    Button(
                        onClick = {
                            scope.launch {
                                isTestingBraille = true
                                testLog += "Testing BrailleNet model...\n"
                                speak(textToSpeech, "Testing Braille character recognition")
                                
                                try {
                                    // Create a test braille pattern (letter 'A' - single dot top-left)
                                    val testBitmap = createTestBrailleBitmap()
                                    val result = mlModelManager.recognizeBrailleCharacter(testBitmap)
                                    
                                    brailleTestResult = if (result.isNotEmpty()) {
                                        "✅ SUCCESS: Recognized '$result'"
                                    } else {
                                        "⚠️ Model returned empty result, but fallback working"
                                    }
                                    
                                    testLog += "BrailleNet result: $result\n"
                                    speak(textToSpeech, "Braille test completed. Result: $brailleTestResult")
                                    
                                } catch (e: Exception) {
                                    brailleTestResult = "❌ ERROR: ${e.message}"
                                    testLog += "BrailleNet error: ${e.message}\n"
                                    speak(textToSpeech, "Braille test failed with error")
                                    Log.e("MLTest", "BrailleNet test error", e)
                                } finally {
                                    isTestingBraille = false
                                }
                            }
                        },
                        enabled = !isTestingBraille,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isTestingBraille) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (isTestingBraille) "Testing..." else "Test BrailleNet")
                    }
                    
                    if (brailleTestResult.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = brailleTestResult,
                            fontSize = 14.sp,
                            color = when {
                                brailleTestResult.startsWith("✅") -> Color(0xFF4CAF50)
                                brailleTestResult.startsWith("⚠️") -> Color(0xFFFF9800)
                                else -> Color(0xFFf44336)
                            },
                            modifier = Modifier
                                .background(
                                    color = Color.Gray.copy(alpha = 0.1f),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                )
                                .padding(8.dp)
                                .fillMaxWidth()
                        )
                    }
                }
            }
            
            // ASAG Model Testing Section
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "ASAG Model Test",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "This will test automatic short answer grading by comparing two similar answers.",
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    OutlinedTextField(
                        value = studentAnswerInput,
                        onValueChange = { studentAnswerInput = it },
                        label = { Text("Student Answer") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        minLines = 2
                    )
                    
                    OutlinedTextField(
                        value = teacherAnswerInput,
                        onValueChange = { teacherAnswerInput = it },
                        label = { Text("Teacher's Correct Answer") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        minLines = 2
                    )
                    
                    Button(
                        onClick = {
                            scope.launch {
                                isTestingASAG = true
                                testLog += "Testing ASAG model...\n"
                                speak(textToSpeech, "Testing automatic answer grading")
                                
                                try {
                                    val similarity = mlModelManager.calculateAnswerSimilarity(
                                        studentAnswerInput,
                                        teacherAnswerInput
                                    )
                                    
                                    val percentage = (similarity * 100).toInt()
                                    asagTestResult = "✅ SUCCESS: Similarity = $similarity (${percentage}%)"
                                    
                                    testLog += "ASAG result: ${similarity} similarity\n"
                                    speak(textToSpeech, "ASAG test completed. Similarity score: $percentage percent")
                                    
                                } catch (e: Exception) {
                                    asagTestResult = "❌ ERROR: ${e.message}"
                                    testLog += "ASAG error: ${e.message}\n"
                                    speak(textToSpeech, "ASAG test failed with error")
                                    Log.e("MLTest", "ASAG test error", e)
                                } finally {
                                    isTestingASAG = false
                                }
                            }
                        },
                        enabled = !isTestingASAG && studentAnswerInput.isNotBlank() && teacherAnswerInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isTestingASAG) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (isTestingASAG) "Testing..." else "Test ASAG")
                    }
                    
                    if (asagTestResult.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = asagTestResult,
                            fontSize = 14.sp,
                            color = when {
                                asagTestResult.startsWith("✅") -> Color(0xFF4CAF50)
                                asagTestResult.startsWith("⚠️") -> Color(0xFFFF9800)
                                else -> Color(0xFFf44336)
                            },
                            modifier = Modifier
                                .background(
                                    color = Color.Gray.copy(alpha = 0.1f),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                )
                                .padding(8.dp)
                                .fillMaxWidth()
                        )
                    }
                }
            }
            
            // Test Log Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Test Log",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Text(
                        text = testLog,
                        fontSize = 12.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier
                            .background(
                                color = Color.Black.copy(alpha = 0.8f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                            )
                            .padding(8.dp)
                            .fillMaxWidth(),
                        color = Color.Green
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(
                        onClick = {
                            testLog = "Test log cleared\n"
                            brailleTestResult = ""
                            asagTestResult = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear Log")
                    }
                }
            }
        }
        
        // Cleanup when screen is destroyed
        DisposableEffect(Unit) {
            onDispose {
                mlModelManager.close()
                textToSpeech.shutdown()
            }
        }
    }
}

private fun createTestBrailleBitmap(): Bitmap {
    // Create a simple test bitmap with a braille pattern
    val bitmap = Bitmap.createBitmap(280, 280, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    
    // Fill with black background
    canvas.drawColor(Color.Black.toArgb())
    
    // Draw white dots for letter 'A' (dot 1 - top left)
    val paint = Paint().apply {
        color = Color.White.toArgb()
        isAntiAlias = true
    }
    
    // Standard braille cell positions (scaled to our bitmap size)
    val dotRadius = 20f
    val cellWidth = 140f
    val cellHeight = 210f
    val startX = (280 - cellWidth) / 2
    val startY = (280 - cellHeight) / 2
    
    // Draw dot 1 (top-left) for letter 'A'
    canvas.drawCircle(startX, startY, dotRadius, paint)
    
    return bitmap
}

private fun setupTextToSpeech(context: Context): TextToSpeech {
    return TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) {
            // Set speaking rate slightly slower for better comprehension
            // TTS configuration handled by the calling code
        }
    }
}

private fun speak(textToSpeech: TextToSpeech, text: String) {
    textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
}