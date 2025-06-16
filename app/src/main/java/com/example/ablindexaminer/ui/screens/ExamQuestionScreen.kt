package com.example.ablindexaminer.ui.screens

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.ablindexaminer.ml.MLModelManager
import com.example.ablindexaminer.data.model.Question
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamQuestionScreen(
    question: Question,
    onAnswerSubmitted: (Float) -> Unit,
    textToSpeech: TextToSpeech
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mlManager = remember { MLModelManager(context) }
    val navController = rememberNavController()
    
    var showBrailleInput by remember { mutableStateOf(false) }
    var currentAnswer by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var isLoadingModel by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Question header
        Text(
            text = "Question Type: ${question.type}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // Question text with TTS support
        Text(
            text = question.text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Error message if any
        errorMessage?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        
        when (question.type.lowercase()) {
            "short_answer", "fill_in_blank" -> {
                if (showBrailleInput) {
                    // Show braille input screen
                    BrailleInputScreen(
                        onTextChanged = { answer ->
                            currentAnswer = answer
                        },
                        onDismiss = {
                            showBrailleInput = false
                            
                            // Calculate similarity score using ASAG model if answer is provided
                            if (currentAnswer.isNotEmpty()) {
                            scope.launch {
                                isSubmitting = true
                                isLoadingModel = true
                                errorMessage = null
                                
                                try {
                                    // Provide feedback about model loading
                                    textToSpeech.speak(
                                        "Loading grading model, please wait...",
                                        TextToSpeech.QUEUE_FLUSH,
                                        null,
                                        null
                                    )
                                    
                                    val score = mlManager.calculateAnswerSimilarity(
                                            currentAnswer,
                                        question.correctAnswer
                                    )
                                    
                                    // Convert similarity (0-1) to points
                                    val points = score * question.points
                                    
                                    // Provide audio feedback about the score
                                    textToSpeech.speak(
                                        "Your answer has been graded. You received ${String.format("%.1f", points)} out of ${question.points} points.",
                                        TextToSpeech.QUEUE_FLUSH,
                                        null,
                                        null
                                    )
                                    
                                    onAnswerSubmitted(points)
                                } catch (e: Exception) {
                                    errorMessage = "Error grading answer: ${e.message}"
                                    textToSpeech.speak(
                                        "Sorry, there was an error grading your answer. Please try again.",
                                        TextToSpeech.QUEUE_FLUSH,
                                        null,
                                        null
                                    )
                                } finally {
                                    isSubmitting = false
                                    isLoadingModel = false
                                    }
                                }
                            }
                        },
                        initialText = currentAnswer,
                        questionText = question.text
                    )
                } else {
                    // Show answer preview and input button
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (currentAnswer.isNotEmpty()) {
                            Text(
                                text = "Your answer: $currentAnswer",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                        }
                        
                        Button(
                            onClick = { showBrailleInput = true },
                            enabled = !isSubmitting && !isLoadingModel
                        ) {
                            Text(if (currentAnswer.isEmpty()) "Enter Answer in Braille" else "Edit Answer")
                        }
                        
                        if (isLoadingModel) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    text = "Loading grading model...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        } else if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
            else -> {
                Text(
                    text = "Unsupported question type: ${question.type}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
    
    // Initial TTS instruction
    LaunchedEffect(Unit) {
        textToSpeech.speak(
            "Question type: ${question.type}. ${question.text}. Touch the button at the bottom of the screen to start writing your answer in Braille.",
            TextToSpeech.QUEUE_FLUSH,
            null,
            null
        )
    }
    
    DisposableEffect(Unit) {
        onDispose {
            mlManager.close()
        }
    }
} 