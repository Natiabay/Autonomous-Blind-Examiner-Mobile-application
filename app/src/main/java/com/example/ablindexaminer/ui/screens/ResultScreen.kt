package com.example.ablindexaminer.ui.screens

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    score: Int,
    totalPoints: Int,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()
    val percentage = if (totalPoints > 0) (score * 100) / totalPoints else 0
    
    // Check if score meets passing criteria (50% or higher)
    val isPassing = percentage >= 50
    
    // Emoji and message based on score
    val emoji = when {
        percentage >= 90 -> "🏆"
        percentage >= 75 -> "🎉"
        percentage >= 50 -> "👍"
        else -> "📝"
    }
    
    val resultMessage = if (isPassing) {
        when {
            percentage >= 90 -> "Excellent work! You've mastered this material."
            percentage >= 75 -> "Great job! You have a good understanding of the material."
            else -> "Good job! You've passed the exam."
        }
    } else {
        "Keep studying. You'll do better next time."
    }
    
    // Colors based on score
    val scoreColor = when {
        percentage >= 90 -> MaterialTheme.colorScheme.primary
        percentage >= 75 -> MaterialTheme.colorScheme.secondary
        percentage >= 50 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    
    val context = LocalContext.current
    val textToSpeech = remember { setupTextToSpeech(context) }
    
    LaunchedEffect(Unit) {
        speak(textToSpeech, resultMessage)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Exam Results") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Result icon
            if (isPassing) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Passed",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(80.dp)
                )
            } else {
                Text(
                    text = "!",
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics {
                        contentDescription = "Failed"
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Result title
            Text(
                text = if (isPassing) "Congratulations!" else "Exam Completed",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = if (isPassing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics {
                    contentDescription = if (isPassing) "Congratulations!" else "Exam Completed"
                }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Result description
            Text(
                text = if (isPassing) {
                    "You have successfully passed the exam!"
                } else {
                    "You did not meet the passing requirements."
                },
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics {
                    contentDescription = if (isPassing) {
                        "You have successfully passed the exam!"
                    } else {
                        "You did not meet the passing requirements."
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Score card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 4.dp
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Your Score",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "$score/$totalPoints",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics {
                            contentDescription = "$score out of $totalPoints"
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "$percentage%",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPassing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics {
                            contentDescription = "$percentage percent"
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = if (isPassing) "Passed" else "Failed",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isPassing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics {
                            contentDescription = if (isPassing) "Passed" else "Failed"
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Passing criteria
            Text(
                text = "Passing criteria: 50% or higher",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .semantics {
                        contentDescription = "Passing criteria: 50% or higher"
                    }
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Done button
            Button(
                onClick = {
                    speak(textToSpeech, "Returning to dashboard")
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .semantics {
                        contentDescription = "Return to dashboard"
                    }
            ) {
                Text(
                    text = "Return to Dashboard",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
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