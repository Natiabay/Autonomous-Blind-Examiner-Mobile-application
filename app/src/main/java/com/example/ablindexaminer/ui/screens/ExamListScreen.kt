package com.example.ablindexaminer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.navigation.NavController
import com.example.ablindexaminer.data.firebase.FirebaseExamService
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import android.util.Log
import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.ui.platform.LocalContext
import java.util.*
import java.text.SimpleDateFormat
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamListScreen(
    navController: NavController
) {
    val context = LocalContext.current
    var exams by remember { mutableStateOf<List<Exam>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val examService = remember { FirebaseExamService() }
    val textToSpeech = remember { setupTextToSpeech(context) }
    
    LaunchedEffect(Unit) {
        try {
            speak(textToSpeech, "Loading available exams. Please wait.")
            examService.getPublishedExams().collect { examList ->
                exams = examList
                isLoading = false
                if (examList.isEmpty()) {
                    speak(textToSpeech, "No exams are currently available.")
                } else {
                    speak(textToSpeech, "Found ${examList.size} available exams. Swipe through the list to explore them.")
                }
            }
        } catch (e: Exception) {
            Log.e("ExamListScreen", "Error loading exams: ${e.message}", e)
            error = e.message
            isLoading = false
            speak(textToSpeech, "Error loading exams. ${e.message}")
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Available Exams") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.Default.ArrowBack, 
                            contentDescription = "Back",
                            modifier = Modifier.semantics {
                                contentDescription = "Go back to previous screen"
                            }
                        )
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
        ) {
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { 
                    searchQuery = it
                    if (it.isNotEmpty()) {
                        speak(textToSpeech, "Searching for exams containing ${it}")
                    }
                },
                label = { Text("Search exams") },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "Search field. Enter keywords to filter exams."
                    },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search, 
                        contentDescription = "Search icon"
                    )
                }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Loading exams...",
                                modifier = Modifier.semantics {
                                    contentDescription = "Loading exams. Please wait."
                                }
                            )
                        }
                    }
                }
                error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Error: $error",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.semantics {
                                    contentDescription = "Error loading exams: $error"
                                }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    isLoading = true
                                    error = null
                                    speak(textToSpeech, "Retrying to load exams")
                                    scope.launch {
                                        try {
                                            examService.getPublishedExams().collect { examList ->
                                                exams = examList
                                                isLoading = false
                                            }
                                        } catch (e: Exception) {
                                            error = e.message
                                            isLoading = false
                                        }
                                    }
                                }
                            ) {
                                Text("Try Again")
                            }
                        }
                    }
                }
                exams.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No exams available",
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.semantics {
                                    contentDescription = "No exams are currently available"
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Check back later for new exams",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                else -> {
                    val filteredExams = if (searchQuery.isBlank()) {
                        exams
                    } else {
                        exams.filter {
                            it.title.contains(searchQuery, ignoreCase = true) ||
                            it.subject.contains(searchQuery, ignoreCase = true)
                        }
                    }
                    
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredExams) { exam ->
                            ExamCard(
                                exam = exam,
                                onClick = {
                                    speak(textToSpeech, "Starting exam: ${exam.title}")
                                    navController.navigate("exam-taking/${exam.id}?type=${exam.type}")
                                }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExamCard(
    exam: Exam,
    onClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
    val formattedDate = dateFormat.format(exam.date)
    
    // Format the question type for display
    val formattedType = exam.type.replace("_", " ")
    val questionTypeColor = when (exam.type) {
        "MULTIPLE_CHOICE" -> MaterialTheme.colorScheme.primary
        "TRUE_FALSE" -> MaterialTheme.colorScheme.tertiary
        "SHORT_ANSWER" -> MaterialTheme.colorScheme.secondary
        "FILL_IN_THE_BLANK" -> MaterialTheme.colorScheme.error
        "MATCHING" -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.primary
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "Exam: ${exam.title}. Subject: ${exam.subject}. Type: ${exam.type.lowercase().replace('_', ' ')}. Duration: ${exam.duration} minutes. Questions: ${exam.questionCount}. Date: $formattedDate. Double tap to start."
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Add question type badge
            Box(
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(questionTypeColor.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = formattedType,
                    color = questionTypeColor,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp
                )
            }
            
            Text(
                text = exam.title,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = exam.subject,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Duration: ${exam.duration} minutes",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Questions: ${exam.questionCount}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Date: $formattedDate",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onClick,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Start Exam")
            }
        }
    }
}

private fun setupTextToSpeech(context: Context): TextToSpeech {
    var textToSpeech: TextToSpeech? = null
    textToSpeech = TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) {
            val result = TextToSpeech.LANG_AVAILABLE
            // Set speaking rate slightly slower for better comprehension
            textToSpeech?.setSpeechRate(0.85f)
        }
    }
    return textToSpeech
}

private fun speak(textToSpeech: TextToSpeech, text: String) {
    val utteranceId = UUID.randomUUID().toString()
    textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
} 