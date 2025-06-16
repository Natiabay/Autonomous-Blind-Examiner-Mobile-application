package com.example.ablindexaminer.ui.screens

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.sqrt
import kotlin.math.abs
import android.util.Log
import com.example.ablindexaminer.data.firebase.FirebaseExamService
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.ablindexaminer.ui.security.SecureExamScreen
import com.example.ablindexaminer.data.model.Question as ModelQuestion
import com.example.ablindexaminer.data.firebase.FirebaseAuthService
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.navigation.NavController
import com.example.ablindexaminer.data.firebase.HybridExamService
import android.view.KeyEvent
import java.text.SimpleDateFormat
import com.example.ablindexaminer.MainActivity
import com.example.ablindexaminer.ui.security.ExamSecurityManager
import com.example.ablindexaminer.ui.components.FlowRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import com.example.ablindexaminer.ml.MLModelManager
import com.example.ablindexaminer.ui.screens.BrailleInputScreen
import com.example.ablindexaminer.ui.accessibility.BlindNavigationController
import com.example.ablindexaminer.ui.accessibility.BlindNavigationWrapper
import com.example.ablindexaminer.ui.accessibility.NavigationItem

// Model for exam questions
data class Question(
    val id: String,
    val text: String,
    val options: List<String>? = null, // For multiple choice, null for short answer
    val answer: String,
    val type: String = "MULTIPLE_CHOICE", // Default to multiple choice, but can be any QuestionType name
    val number: Int = 0, // Added for sorting
    val points: Int? = null // Added for point value
)

// Mock exam data
private val mockExamQuestions = listOf(
    Question(
        id = "q1",
        text = "What is the main purpose of an operating system?",
        options = listOf(
            "A. To provide a user interface",
            "B. To manage hardware resources",
            "C. To run applications",
            "D. All of the above"
        ),
        answer = "D",
        type = "MULTIPLE_CHOICE"
    ),
    Question(
        id = "q2",
        text = "Which data structure uses LIFO ordering?",
        options = listOf(
            "A. Queue",
            "B. Stack",
            "C. Tree",
            "D. Graph"
        ),
        answer = "B",
        type = "MULTIPLE_CHOICE"
    ),
    Question(
        id = "q3",
        text = "The CPU is often described as the 'brain' of the computer.",
        options = listOf("True", "False"),
        answer = "True",
        type = "TRUE_FALSE"
    ),
    Question(
        id = "q4",
        text = "Explain briefly what is meant by 'polymorphism' in object-oriented programming.",
        options = null, // Short answer question
        answer = "Polymorphism is the ability of different objects to respond to the same message or method in different ways.",
        type = "SHORT_ANSWER"
    ),
    Question(
        id = "q5",
        text = "In computer networks, the device that connects different networks together is called a _____.",
        options = null,
        answer = "router",
        type = "FILL_IN_THE_BLANK"
    )
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun ExamScreen(
    examId: String,
    examTitle: String,
    duration: Int, // in minutes
    examType: String = "MULTIPLE_CHOICE", // Add the exam type parameter
    onExamComplete: (Int, Int) -> Unit // (score, total) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    
    // Get Firebase services
    val examService = remember { FirebaseExamService() }
    val hybridExamService = remember { HybridExamService() }
    
    // State for exam questions
    var questions by remember { mutableStateOf<List<Question>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    
    // Exam state
    var currentQuestionIndex by remember { mutableStateOf(0) }
    var answers by remember { mutableStateOf(mutableMapOf<String, String>()) }
    var remainingTimeInSeconds by remember { mutableStateOf(duration * 60) }
    var isExamCompleted by remember { mutableStateOf(false) }
    var showExitWarning by remember { mutableStateOf(false) }
    var showTimerWarning by remember { mutableStateOf(false) }
    
    // Dialog states
    var showUnansweredDialog by remember { mutableStateOf(false) }
    var showResultDialog by remember { mutableStateOf(false) }
    var unansweredQuestionsList by remember { mutableStateOf<List<Question>>(emptyList()) }
    var examScore by remember { mutableStateOf(0) }
    var examTotalPoints by remember { mutableStateOf(0) }
    
    // Navigation helpers for unanswered questions
    var isReviewingUnanswered by remember { mutableStateOf(false) }
    var currentUnansweredIndex by remember { mutableStateOf(0) }
    
    // Security manager and session tracking
    val examSecurityManager = remember { ExamSecurityManager.getInstance() }
    var sessionId by remember { mutableStateOf<String?>(null) }
    
    // Format the question type for display
    val formattedType = examType.replace("_", " ")
    val questionTypeColor = when (examType) {
        "MULTIPLE_CHOICE" -> MaterialTheme.colorScheme.primary
        "TRUE_FALSE" -> MaterialTheme.colorScheme.tertiary
        "SHORT_ANSWER" -> MaterialTheme.colorScheme.secondary
        "FILL_IN_THE_BLANK" -> MaterialTheme.colorScheme.error
        "MATCHING" -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.primary
    }
    
    // Text-to-speech for accessibility
    val textToSpeech = remember { setupTextToSpeech(context) }
    
    // Enable exam lockdown mode when screen is shown
    DisposableEffect(Unit) {
        // Enable strict exam mode and store session ID
        sessionId = ExamSecurityManager.getInstance().startExamSession(examId, examTitle)
        
        onDispose {
            // Always disable security mode when leaving the screen
            // This ensures cleanup even if the screen is disposed unexpectedly
            try {
                val activity = context as? Activity
                if (activity != null) {
                    examSecurityManager.disableSecurityMode(activity)
                    Log.d("ExamScreen", "Security mode disabled in DisposableEffect cleanup")
                }
            } catch (e: Exception) {
                Log.e("ExamScreen", "Error disabling security mode in cleanup", e)
            }
            
            // End exam session if we have a sessionId
            try {
                sessionId?.let { id ->
                    ExamSecurityManager.getInstance().endExamSession(id)
                }
            } catch (e: Exception) {
                Log.e("ExamScreen", "Error ending exam session in cleanup", e)
            }
            
            // Cleanup text-to-speech
            try {
                textToSpeech?.shutdown()
            } catch (e: Exception) {
                Log.e("ExamScreen", "Error shutting down TTS", e)
            }
        }
    }
    
    // Watch for exam completion
    LaunchedEffect(isExamCompleted) {
        if (isExamCompleted) {
            // Delay a bit before disabling exam mode to ensure the navigation completes
            delay(500)
            // No need to explicitly call anything here as session is ended in onExamComplete
        }
    }
    
    // Apply security measures for exam integrity
    SecureExamScreen(
        isExamActive = !isExamCompleted,
        onSecurityViolation = {
            if (!isExamCompleted) {
                speak(textToSpeech, "Security violation detected. Please continue your exam.")
                showExitWarning = true
                // Display a dialog warning the user they should not try to exit
                coroutineScope.launch {
                    showExitWarning = true
                    delay(3000) // Keep the warning visible for 3 seconds
                    showExitWarning = false
                }
            }
        }
    )
    
    // Get current question safely
    val getCurrentQuestion = {
        if (questions.isNotEmpty() && currentQuestionIndex < questions.size) {
            questions[currentQuestionIndex]
        } else {
            // Default empty question as fallback (should never happen with proper loading)
            Question(
                id = "empty",
                text = "No question available",
                options = emptyList(),
                answer = "",
                type = "MULTIPLE_CHOICE"
            )
        }
    }
    
    // Function to handle answer selection
    val onAnswerSelected = { answer: String ->
        val currentQuestion = getCurrentQuestion()
        answers[currentQuestion.id] = answer
        speak(textToSpeech, "You selected: $answer")
    }
    
    // Load exam questions
    LaunchedEffect(examId) {
        isLoading = true
        loadError = null
        
        try {
            Log.d("ExamScreen", "Attempting to load questions for exam: $examId")
            
            // First try with HybridExamService to check both databases
            val hybridResult = hybridExamService.getQuestionsForExam(examId)
            
            // If successful with hybrid service, use those questions
            if (hybridResult.isSuccess) {
                val modelQuestions = hybridResult.getOrNull() ?: emptyList()
                Log.d("ExamScreen", "Loaded ${modelQuestions.size} questions from hybrid service")
                
                // Convert ModelQuestion to ui.screens.Question
                val convertedQuestions = modelQuestions.map { modelQuestion ->
                    Question(
                        id = modelQuestion.id,
                        text = modelQuestion.text,
                        options = modelQuestion.options,
                        answer = modelQuestion.correctAnswer,
                        type = modelQuestion.type.toString(), // Convert QuestionType to String
                        number = modelQuestion.number,
                        points = modelQuestion.points
                    )
                }
                
                questions = convertedQuestions
                isLoading = false
                
                // Announce first question after loading
                if (convertedQuestions.isNotEmpty()) {
                    announceFirstQuestion(textToSpeech, examTitle, convertedQuestions[0])
                } else {
                    Log.e("ExamScreen", "No questions found using hybrid service")
                    loadError = "No questions found for this exam"
                    speak(textToSpeech, "Error: No questions found for this exam.")
                }
            } else {
                // Fall back to legacy method
                Log.d("ExamScreen", "Hybrid service failed, falling back to FirebaseExamService")
                val loadedQuestions = loadExamQuestions(examId, examService)
                
                if (loadedQuestions.isEmpty()) {
                    Log.e("ExamScreen", "No questions found using fallback method")
                    loadError = "Could not load exam questions. Please try again later."
                    speak(textToSpeech, "Error: Could not load questions for this exam.")
                } else {
                    questions = loadedQuestions
                    announceFirstQuestion(textToSpeech, examTitle, loadedQuestions[0])
                }
                
                isLoading = false
            }
        } catch (e: Exception) {
            Log.e("ExamScreen", "Error loading exam questions", e)
            loadError = "Error: ${e.message}"
            isLoading = false
            speak(textToSpeech, "Error loading exam. ${e.message}")
        }
    }
    
    // Timer effect
    LaunchedEffect(Unit) {
        while (remainingTimeInSeconds > 0 && !isExamCompleted) {
            delay(1000)
            remainingTimeInSeconds--
            
            // Announce time remaining every 30 minutes
            if (remainingTimeInSeconds > 0 && remainingTimeInSeconds % (30 * 60) == 0) {
                val minutesLeft = remainingTimeInSeconds / 60
                speak(textToSpeech, "You have $minutesLeft minutes remaining.")
            }
            
            // Show warning at 10 minutes
            if (remainingTimeInSeconds == 10 * 60) {
                showTimerWarning = true
                speak(textToSpeech, "Warning: Only 10 minutes remaining.")
            }
            
            // Final warning at 5 minutes
            if (remainingTimeInSeconds == 5 * 60) {
                speak(textToSpeech, "Warning: Only 5 minutes remaining. Please finish your exam soon.")
                showTimerWarning = true
            }
            
            // Critical warning at 1 minute
            if (remainingTimeInSeconds == 60) {
                speak(textToSpeech, "Critical warning: Only 1 minute remaining.")
                showTimerWarning = true
            }
        }
        
        if (remainingTimeInSeconds <= 0 && !isExamCompleted) {
            speak(textToSpeech, "Time's up. Your exam will be submitted automatically.")
            submitExam(answers, questions, context, textToSpeech, onExamComplete)
            isExamCompleted = true
        }
    }
    
    // Keyboard navigation tracking variables
    var focusedElement by remember { mutableStateOf(0) } // 0=question, 1=option1, 2=option2, etc.
    var totalFocusableElements by remember { mutableStateOf(1) } // Start with just the question
    
    // Handle keyboard navigation
    DisposableEffect(Unit) {
        val handler = Handler(Looper.getMainLooper())
        val keyEventRunnable = object : Runnable {
            override fun run() {
                // Simulate keypress handling
                handler.postDelayed(this, 100)
            }
        }
        handler.post(keyEventRunnable)
        
        onDispose {
            handler.removeCallbacks(keyEventRunnable)
        }
    }
    
    // Process key events for navigation
    fun processKeyEvent(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_TAB -> {
                // Navigate to next element
                focusedElement = (focusedElement + 1) % totalFocusableElements
                val currentQuestion = getCurrentQuestion()
                
                if (focusedElement == 0) {
                    speakNow(textToSpeech, "Question ${currentQuestionIndex + 1}: ${currentQuestion.text}")
                } else if (currentQuestion.options != null && focusedElement <= currentQuestion.options.size) {
                    // Focus on an option
                    val optionIndex = focusedElement - 1
                    val option = currentQuestion.options[optionIndex]
                    val optionLetter = ('A' + optionIndex).toString()
                    speakNow(textToSpeech, "Option $optionLetter: ${getOptionText(option)}")
                } else {
                    // Focus on buttons or other UI elements
                    when (focusedElement) {
                        currentQuestion.options?.size?.plus(1) ?: 1 -> 
                            speakNow(textToSpeech, "Previous question button")
                        currentQuestion.options?.size?.plus(2) ?: 2 -> 
                            speakNow(textToSpeech, "Next question button")
                        currentQuestion.options?.size?.plus(3) ?: 3 -> 
                            speakNow(textToSpeech, "Read current status button")
                    }
                }
                true
            }
            KeyEvent.KEYCODE_ENTER -> {
                // Activate the focused element
                val currentQuestion = getCurrentQuestion()
                
                if (focusedElement > 0 && currentQuestion.options != null && focusedElement <= currentQuestion.options.size) {
                    // Select the option
                    val optionIndex = focusedElement - 1
                    val optionLetter = ('A' + optionIndex).toString()
                    onAnswerSelected(optionLetter)
                    speakNow(textToSpeech, "Selected option $optionLetter")
                } else if (focusedElement == currentQuestion.options?.size?.plus(1) ?: 1) {
                    // Previous button
                    if (currentQuestionIndex > 0) {
                        currentQuestionIndex--
                        focusedElement = 0 // Reset focus to question
                        val question = questions[currentQuestionIndex]
                        speakNow(textToSpeech, "Previous question. Question ${currentQuestionIndex + 1}: ${question.text}")
                    }
                } else if (focusedElement == currentQuestion.options?.size?.plus(2) ?: 2) {
                    // Next button
                    if (currentQuestionIndex < questions.size - 1) {
                        currentQuestionIndex++
                        focusedElement = 0 // Reset focus to question
                        val question = questions[currentQuestionIndex]
                        speakNow(textToSpeech, "Next question. Question ${currentQuestionIndex + 1}: ${question.text}")
                    }
                }
                true
            }
            else -> false
        }
    }
    
    // Update focusable elements when question changes
    LaunchedEffect(currentQuestionIndex) {
        val currentQuestion = getCurrentQuestion()
        totalFocusableElements = (currentQuestion.options?.size ?: 0) + 4 // Question + options + 3 buttons
        focusedElement = 0 // Reset to question focus
    }
    
    // Navigation logic for screen reader users
    var lastTapTime by remember { mutableStateOf(0L) }
    val doubleTapThreshold = 300L // 300ms threshold for double-tap detection
    
    // Handle keyboard navigation events using a coroutine
    fun handleKeyNavigation(event: KeyEvent, direction: Int): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (direction > 0 && currentQuestionIndex < questions.size - 1) {
                // Move to next question
                coroutineScope.launch {
                    moveToNextQuestion(textToSpeech, currentQuestionIndex, questions) {
                        currentQuestionIndex = it
                    }
                }
                return true
            } else if (direction < 0 && currentQuestionIndex > 0) {
                // Move to previous question
                speak(textToSpeech, "Moving to previous question.")
                currentQuestionIndex--
                val previousQuestion = questions[currentQuestionIndex]
                val answer = answers[previousQuestion.id] ?: ""
                speak(textToSpeech, "Question ${currentQuestionIndex + 1}: ${previousQuestion.text}. Your current answer is: $answer")
                return true
            }
        }
        return false
    }
    
    // Prevent back button from closing the activity
    BackHandler(enabled = !isExamCompleted) {
        showExitWarning = true
        speak(textToSpeech, "Warning: You cannot exit the exam until you submit. Please complete your exam.")
    }
    
    // Handle swipe gestures for navigation
    var dragStartX by remember { mutableStateOf(0f) }
    var dragEndX by remember { mutableStateOf(0f) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = examTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 18.sp
                        )
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(questionTypeColor.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = formattedType,
                                color = questionTypeColor,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    // Timer display
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = if (remainingTimeInSeconds < 300) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formatTime(remainingTimeInSeconds),
                            fontWeight = FontWeight.Medium,
                            color = if (remainingTimeInSeconds < 300) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        // Main content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                // Loading indicator
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading exam questions...",
                        modifier = Modifier.semantics {
                            contentDescription = "Loading exam questions. Please wait."
                        }
                    )
                }
            } else if (questions.isEmpty()) {
                // No questions found
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No questions found for this exam",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Please contact your instructor",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            onExamComplete(0, 0) // Return to dashboard
                        }
                    ) {
                        Text("Return to Dashboard")
                    }
                }
            } else {
                // Question display and interaction area
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    dragStartX = offset.x
                                },
                                onDragEnd = {
                                    val dragDistance = dragEndX - dragStartX
                                    // Detect horizontal swipe
                                    if (abs(dragDistance) > 100) { // Minimum swipe distance
                                        if (dragDistance > 0) {
                                            // Swipe right - navigate to next UI element within the question
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            val currentQuestion = getCurrentQuestion()
                                            speakNow(textToSpeech, "Navigating forward within question ${currentQuestionIndex + 1}")
                                            
                                            // This will be handled by the focus management within the question
                                            focusedElement = (focusedElement + 1) % totalFocusableElements
                                            
                                            when (focusedElement) {
                                                0 -> speakNow(textToSpeech, "Question text: ${currentQuestion.text}")
                                                
                                                in 1..(currentQuestion.options?.size ?: 0) -> {
                                                    val optionIndex = focusedElement - 1
                                                    val option = currentQuestion.options?.get(optionIndex) ?: ""
                                                    val optionLetter = ('A' + optionIndex).toString()
                                                    speakNow(textToSpeech, "Option $optionLetter: ${getOptionText(option)}")
                                                }
                                                
                                                else -> {
                                                    // Navigation buttons or other elements
                                                    val remainingElements = focusedElement - (currentQuestion.options?.size ?: 0)
                                                    when (remainingElements) {
                                                        1 -> speakNow(textToSpeech, "Previous question button")
                                                        2 -> speakNow(textToSpeech, "Next question button")
                                                        3 -> speakNow(textToSpeech, "Submit button")
                                                        else -> speakNow(textToSpeech, "Navigation element")
                                                    }
                                                }
                                            }
                                        } else {
                                            // Swipe left - navigate to previous UI element within the question
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            speakNow(textToSpeech, "Navigating backward within question ${currentQuestionIndex + 1}")
                                            
                                            // Go to previous element, with wrap-around
                                            focusedElement = if (focusedElement > 0) 
                                                focusedElement - 1 
                                            else 
                                                totalFocusableElements - 1
                                            
                                            val currentQuestion = getCurrentQuestion()
                                            
                                            when (focusedElement) {
                                                0 -> speakNow(textToSpeech, "Question text: ${currentQuestion.text}")
                                                
                                                in 1..(currentQuestion.options?.size ?: 0) -> {
                                                    val optionIndex = focusedElement - 1
                                                    val option = currentQuestion.options?.get(optionIndex) ?: ""
                                                    val optionLetter = ('A' + optionIndex).toString()
                                                    speakNow(textToSpeech, "Option $optionLetter: ${getOptionText(option)}")
                                                }
                                                
                                                else -> {
                                                    // Navigation buttons or other elements
                                                    val remainingElements = focusedElement - (currentQuestion.options?.size ?: 0)
                                                    when (remainingElements) {
                                                        1 -> speakNow(textToSpeech, "Previous question button")
                                                        2 -> speakNow(textToSpeech, "Next question button")
                                                        3 -> speakNow(textToSpeech, "Submit button")
                                                        else -> speakNow(textToSpeech, "Navigation element")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragEndX = change.position.x
                                }
                            )
                        }
                ) {
                    // Question counter
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        progress = { (currentQuestionIndex + 1).toFloat() / questions.size }
                    )
                    Text(
                        text = "Question ${currentQuestionIndex + 1} of ${questions.size}",
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .semantics {
                                contentDescription = "Question ${currentQuestionIndex + 1} of ${questions.size}"
                            },
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // Question content
                    renderQuestion(
                        question = getCurrentQuestion(),
                        answer = answers[getCurrentQuestion().id],
                        onAnswerSelected = { answer ->
                            answers[getCurrentQuestion().id] = answer
                        },
                        textToSpeech = textToSpeech,
                        isRandomAccess = false
                    )
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    // Navigation buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = {
                                if (isReviewingUnanswered) {
                                    // Navigate to previous unanswered question
                                    if (currentUnansweredIndex > 0) {
                                        currentUnansweredIndex--
                                        val unansweredQuestion = unansweredQuestionsList[currentUnansweredIndex]
                                        val questionIndex = questions.indexOf(unansweredQuestion)
                                        if (questionIndex != -1) {
                                            currentQuestionIndex = questionIndex
                                            speakNow(textToSpeech, "Previous unanswered question. Question ${questionIndex + 1} of ${questions.size}. Unanswered question ${currentUnansweredIndex + 1} of ${unansweredQuestionsList.size}. ${unansweredQuestion.text}")
                                        }
                                    } else {
                                        speakNow(textToSpeech, "This is the first unanswered question.")
                                    }
                                } else {
                                    // Normal navigation
                                if (currentQuestionIndex > 0) {
                                    currentQuestionIndex--
                                    val question = questions[currentQuestionIndex]
                                    speakNow(textToSpeech, "Question ${currentQuestionIndex + 1} of ${questions.size}: ${question.text}")
                                    if (question.options != null) {
                                        coroutineScope.launch {
                                            delay(2000)
                                            speakWithDelay(textToSpeech, "Options: ${question.options.joinToString(". ")}")
                                        }
                                    }
                                } else {
                                    speakNow(textToSpeech, "This is the first question. No previous questions available.")
                                    }
                                }
                            },
                            enabled = if (isReviewingUnanswered) currentUnansweredIndex > 0 else currentQuestionIndex > 0,
                            modifier = Modifier
                                .weight(1f)
                                .semantics { 
                                    contentDescription = "Previous question button. ${if(currentQuestionIndex > 0) "Active" else "Disabled"}"
                                }
                        ) {
                            Text("Previous")
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        if (isReviewingUnanswered && currentUnansweredIndex < unansweredQuestionsList.size - 1) {
                            Button(
                                onClick = {
                                    // Navigate to next unanswered question
                                    currentUnansweredIndex++
                                    val unansweredQuestion = unansweredQuestionsList[currentUnansweredIndex]
                                    val questionIndex = questions.indexOf(unansweredQuestion)
                                    if (questionIndex != -1) {
                                        currentQuestionIndex = questionIndex
                                        speakNow(textToSpeech, "Next unanswered question. Question ${questionIndex + 1} of ${questions.size}. Unanswered question ${currentUnansweredIndex + 1} of ${unansweredQuestionsList.size}. ${unansweredQuestion.text}")
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { 
                                        contentDescription = "Next unanswered question button"
                                    }
                            ) {
                                Text("Next Unanswered")
                            }
                        } else if (!isReviewingUnanswered && currentQuestionIndex < questions.size - 1) {
                            Button(
                                onClick = {
                                    currentQuestionIndex++
                                    val question = questions[currentQuestionIndex]
                                    speakNow(textToSpeech, "Question ${currentQuestionIndex + 1} of ${questions.size}: ${question.text}")
                                    if (question.options != null) {
                                        coroutineScope.launch {
                                            delay(2000)
                                            speakWithDelay(textToSpeech, "Options: ${question.options.joinToString(". ")}")
                                        }
                                    } else {
                                        speakWithDelay(textToSpeech, "This is a ${question.type.lowercase().replace('_', ' ')} question. Use the text field to answer.")
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { 
                                        contentDescription = "Next question button"
                                    }
                            ) {
                                Text("Next")
                            }
                        } else if (isReviewingUnanswered) {
                            // Show "Exit Review" button when in review mode and at the end of unanswered questions
                            Button(
                                onClick = {
                                    isReviewingUnanswered = false
                                    currentUnansweredIndex = 0
                                    speakNow(textToSpeech, "Exiting unanswered questions review. You can now navigate normally or submit your exam.")
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { 
                                        contentDescription = "Exit review mode button"
                                    }
                            ) {
                                Text("Exit Review")
                            }
                        } else {
                            Button(
                                onClick = {
                                    // Calculate unanswered questions
                                    val unansweredQuestions = questions.filter { question ->
                                        val answer = answers[question.id]
                                        answer.isNullOrBlank()
                                    }
                                    
                                    if (unansweredQuestions.isNotEmpty()) {
                                        showUnansweredDialog = true
                                        unansweredQuestionsList = unansweredQuestions
                                        speakNow(textToSpeech, "You have ${unansweredQuestions.size} unanswered questions. Please review them before submitting.")
                                    } else {
                                        speakNow(textToSpeech, "Submitting your exam. All questions have been answered.")
                                        submitExam(answers, questions, context, textToSpeech, onExamComplete)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .semantics { 
                                        contentDescription = "Submit exam button"
                                    }
                            ) {
                                Text("Submit Exam")
                            }
                        }
                    }
                }
            }
            
            // Timer warning dialog
            if (showTimerWarning) {
                AlertDialog(
                    onDismissRequest = { showTimerWarning = false },
                    title = { Text("Time Warning") },
                    text = { 
                        Text(
                            text = when {
                                remainingTimeInSeconds <= 60 -> "Only 1 minute remaining!"
                                remainingTimeInSeconds <= 5 * 60 -> "Only ${remainingTimeInSeconds / 60} minutes remaining!"
                                else -> "${remainingTimeInSeconds / 60} minutes remaining."
                            },
                            fontWeight = if (remainingTimeInSeconds <= 5 * 60) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = { showTimerWarning = false }
                        ) {
                            Text("Continue Exam")
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = when {
                                remainingTimeInSeconds <= 5 * 60 -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                )
            }
            
            // Exit warning dialog
            if (showExitWarning) {
                AlertDialog(
                    onDismissRequest = { showExitWarning = false },
                    title = { Text("Security Warning") },
                    text = { 
                        Text(
                            "Do not attempt to exit the exam using system buttons. " +
                            "This is a violation of exam rules and may be reported.", 
                            color = MaterialTheme.colorScheme.error
                        ) 
                    },
                    confirmButton = {
                        Button(onClick = { showExitWarning = false }) {
                            Text("Continue Exam")
                        }
                    },
                    icon = { Icon(Icons.Default.Warning, contentDescription = "Warning Icon") }
                )
            }
            
            // Unanswered questions dialog
            if (showUnansweredDialog) {
                AlertDialog(
                    onDismissRequest = { showUnansweredDialog = false },
                    title = { Text("Unanswered Questions") },
                    text = {
                        Column {
                            Text(
                                text = "You have ${unansweredQuestionsList.size} unanswered questions:",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 200.dp)
                            ) {
                                items(unansweredQuestionsList.size) { index ->
                                    val question = unansweredQuestionsList[index]
                                    val questionNumber = questions.indexOf(question) + 1
                                    Text(
                                        text = "• Question $questionNumber: ${question.text.take(50)}${if (question.text.length > 50) "..." else ""}",
                                        modifier = Modifier.padding(vertical = 2.dp),
                                        fontSize = 14.sp
                                    )
                                }
                            }
                            Text(
                                text = "\nDo you want to review them or submit anyway?",
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showUnansweredDialog = false
                                speakNow(textToSpeech, "Submitting exam with unanswered questions.")
                                submitExam(answers, questions, context, textToSpeech, onExamComplete)
                            }
                        ) {
                            Text("Submit Anyway")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showUnansweredDialog = false
                                // Navigate to the first unanswered question
                                if (unansweredQuestionsList.isNotEmpty()) {
                                    isReviewingUnanswered = true
                                    currentUnansweredIndex = 0
                                    val firstUnansweredQuestion = unansweredQuestionsList[0]
                                    val firstUnansweredIndex = questions.indexOf(firstUnansweredQuestion)
                                    if (firstUnansweredIndex != -1) {
                                        currentQuestionIndex = firstUnansweredIndex
                                        speakNow(textToSpeech, "Reviewing unanswered questions. Question ${firstUnansweredIndex + 1} of ${questions.size}. You have ${unansweredQuestionsList.size} unanswered questions remaining. ${firstUnansweredQuestion.text}")
                                    }
                                } else {
                                    speakNow(textToSpeech, "Review your answers before submitting.")
                                }
                            }
                        ) {
                            Text("Review Questions")
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                )
            }
            
            // Exam result dialog
            if (showResultDialog) {
                AlertDialog(
                    onDismissRequest = { 
                        showResultDialog = false
                        // Disable security mode before returning to dashboard
                        val activity = context as? Activity
                        if (activity != null) {
                            examSecurityManager.disableSecurityMode(activity)
                        }
                        onExamComplete(examScore, examTotalPoints)
                    },
                    title = { Text("Exam Completed!") },
                    text = {
                        Column {
                            Text(
                                text = "Your exam has been submitted successfully.",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Final Score",
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "$examScore / $examTotalPoints",
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    val percentage = if (examTotalPoints > 0) (examScore * 100) / examTotalPoints else 0
                                    Text(
                                        text = "($percentage%)",
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            
                            Text(
                                text = "\nYour results have been saved and sent to your instructor.",
                                modifier = Modifier.padding(top = 16.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showResultDialog = false
                                speakNow(textToSpeech, "Returning to dashboard. Your exam score is $examScore out of $examTotalPoints points.")
                                // Disable security mode BEFORE calling onExamComplete to ensure PIN is disabled
                                try {
                                    val activity = context as? Activity
                                    if (activity != null) {
                                        examSecurityManager.disableSecurityMode(activity)
                                        // Also force stop screen pinning if still active
                                        examSecurityManager.stopScreenPinning(activity)
                                    }
                                } catch (e: Exception) {
                                    Log.e("ExamScreen", "Error disabling security mode", e)
                                }
                                // Call the navigation callback
                                onExamComplete(examScore, examTotalPoints)
                            }
                        ) {
                            Text("Return to Dashboard")
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                )
            }
        }
    }
}

private suspend fun moveToNextQuestion(textToSpeech: TextToSpeech, currentIndex: Int, questions: List<Question>, updateIndex: (Int) -> Unit) {
    if (currentIndex < questions.size - 1) {
        val nextIndex = currentIndex + 1
        updateIndex(nextIndex)
        speak(textToSpeech, "Question ${nextIndex + 1}. ${questions[nextIndex].text}")
        
        // Announce based on question type
        when (questions[nextIndex].type) {
            "MULTIPLE_CHOICE", "TRUE_FALSE" -> {
                delay(2000)
                speak(textToSpeech, "Options: ${questions[nextIndex].options?.joinToString(". ") ?: ""}")
            }
            "FILL_IN_THE_BLANK" -> {
                speak(textToSpeech, "This is a fill in the blank question. Enter the missing word.")
            }
            "SHORT_ANSWER" -> {
                speak(textToSpeech, "This is a short answer question. Provide a brief response.")
            }
            "MATCHING" -> {
                speak(textToSpeech, "This is a matching question. Match items from the left with those on the right.")
            }
            else -> {
                speak(textToSpeech, "Answer the question using the appropriate input method.")
            }
        }
    }
}

/**
 * Submit the completed exam and calculate score
 */
private fun submitExam(
    answers: Map<String, String>,
    questions: List<Question>,
    context: Context,
    textToSpeech: TextToSpeech,
    onExamComplete: (Int, Int) -> Unit
) {
    // FIRST: Disable security mode and PIN immediately when submit is pressed
    try {
        val examSecurityManager = ExamSecurityManager.getInstance()
        if (context is Activity) {
            examSecurityManager.stopScreenPinning(context)
            examSecurityManager.disableSecurityMode(context)
        }
        Log.d("ExamScreen", "Security mode disabled on exam submission")
    } catch (e: Exception) {
        Log.e("ExamScreen", "Error disabling security mode on submission", e)
    }
    var score = 0
    var totalPoints = 0
    
    // Calculate score based on correct answers
    val questionDetails = mutableListOf<QuestionAttempt>()
    
    questions.forEach { question ->
        // Default point value is 1 if not specified
        val points = question.points ?: 1
        totalPoints += points
        
        val userAnswer = answers[question.id] ?: ""
        var isCorrect = false
        
        when (question.type) {
            "MULTIPLE_CHOICE", "TRUE_FALSE" -> {
                // Direct comparison for selection questions
                isCorrect = userAnswer.equals(question.answer, ignoreCase = true)
                if (isCorrect) {
                    score += points
                }
            }
            "SHORT_ANSWER", "FILL_IN_BLANK" -> {
                // Use ASAG model for intelligent grading
                val correctAnswer = question.answer ?: ""
                if (correctAnswer.isNotEmpty() && userAnswer.isNotEmpty()) {
                    val mlModelManager = MLModelManager(context)
                    val similarity = mlModelManager.calculateAnswerSimilarity(userAnswer, correctAnswer)
                    
                    // Award points based on similarity (threshold can be adjusted)
                    val similarityThreshold = 0.7f
                    val partialPoints = (similarity * points).toInt()
                    
                    isCorrect = similarity >= similarityThreshold
                    score += if (isCorrect) points else partialPoints
                    
                    mlModelManager.close()
                    
                    Log.d("ExamScreen", "ASAG Grading - Question: ${question.text}")
                    Log.d("ExamScreen", "Student Answer: $userAnswer")
                    Log.d("ExamScreen", "Correct Answer: $correctAnswer")
                    Log.d("ExamScreen", "Similarity: $similarity, Points awarded: ${if (isCorrect) points else partialPoints}")
                } else {
                    // Fallback to simple keyword matching
                val correctAnswer = question.answer?.lowercase() ?: ""
                val userAnswerLower = userAnswer.lowercase()
                
                isCorrect = userAnswerLower.contains(correctAnswer) || correctAnswer.contains(userAnswerLower)
                if (isCorrect) {
                    score += points
                    }
                }
            }
            else -> {
                // Default comparison
                isCorrect = userAnswer.equals(question.answer, ignoreCase = true)
                if (isCorrect) {
                    score += points
                }
            }
        }
        
        // Add details about this question attempt
        questionDetails.add(
            QuestionAttempt(
                id = question.id,
                questionText = question.text,
                correctAnswer = question.answer ?: "",
                studentAnswer = userAnswer,
                isCorrect = isCorrect,
                type = question.type,
                options = question.options,
                points = points
            )
        )
    }
    
    // Get the exam ID from the first question
    // First question ID format is usually "examId-questionNumber"
    val examId = questions.firstOrNull()?.id?.substringBefore('-') ?: ""
    
    // Get title and subject if available from the first question
    val examTitle = questions.firstOrNull()?.text?.substringBefore('?')?.take(30)?.plus("...") ?: "Exam"
    val examSubject = "General"
    
    // Save exam record to Firebase
    val coroutineScope = CoroutineScope(Dispatchers.Main)
    val authService = FirebaseAuthService()
    val hybridExamService = HybridExamService()
    
    coroutineScope.launch {
        try {
            val userId = authService.getCurrentUserId()
            
            if (userId != null) {
                // Check if student has already taken this exam
                val hasTakenResult = hybridExamService.hasStudentTakenExam(userId, examId)
                if (hasTakenResult.isSuccess && hasTakenResult.getOrDefault(false)) {
                    // Student has already taken this exam, just return the score
                    Log.d("ExamScreen", "Student has already taken this exam, skipping record creation")
                    onExamComplete(score, totalPoints)
                    return@launch
                }
                
                // Get student info
                val userDataResult = authService.getUserData(userId)
                
                if (userDataResult.isSuccess) {
                    val userData = userDataResult.getOrNull()
                    val studentName = userData?.get("fullName") as? String ?: "Unknown Student"
                    
                    // Create exam record
                    val answeredCount = answers.values.count { it.isNotBlank() }
                    val examRecord = StudentExamRecord(
                        id = examId,
                        title = examTitle,
                        subject = examSubject,
                        date = Date(),
                        score = score,
                        totalPoints = totalPoints,
                        answered = answeredCount,
                        totalQuestions = questions.size,
                        questionDetails = questionDetails,
                        studentName = studentName,
                        studentId = userId
                    )
                    
                    // Save to Firebase
                    Log.d("ExamScreen", "Saving exam record for student $userId, exam $examId")
                    val result = hybridExamService.saveStudentExamRecord(examRecord, userId)
                    
                    if (result.isSuccess) {
                        Log.d("ExamScreen", "Successfully saved exam record")
                        
                        // Integrate ML-based answer scoring for short answer questions
                        val enhancedQuestionDetails = questionDetails.map { question ->
                            if (question.type == "SHORT_ANSWER" && !question.studentAnswer.isNullOrBlank()) {
                                try {
                                    // Use ML model to calculate similarity score
                                    val mlModelManager = MLModelManager(context)
                                    val similarityScore = mlModelManager.calculateAnswerSimilarity(
                                        question.studentAnswer ?: "",
                                        question.correctAnswer
                                    )
                                    
                                    // Adjust correctness based on similarity threshold
                                    val isCorrectByML = similarityScore >= 0.75f // 75% similarity threshold
                                    val updatedPoints = if (isCorrectByML) question.points else 0
                                    
                                    Log.d("ExamScreen", "ML scoring for question ${question.id}: similarity=$similarityScore, isCorrect=$isCorrectByML")
                                    
                                    question.copy(
                                        isCorrect = question.isCorrect || isCorrectByML, // Keep original if already correct
                                        points = if (question.isCorrect) question.points else updatedPoints
                                    )
                                } catch (e: Exception) {
                                    Log.e("ExamScreen", "Error applying ML scoring to question ${question.id}", e)
                                    question // Return original if ML fails
                                }
                            } else {
                                question
                            }
                        }
                        
                        // Recalculate score with ML enhancements
                        val enhancedScore = enhancedQuestionDetails.sumOf { 
                            if (it.isCorrect) it.points else 0 
                        }
                        
                        // Update the exam record with enhanced scoring
                        val enhancedExamRecord = examRecord.copy(
                            score = enhancedScore,
                            questionDetails = enhancedQuestionDetails
                        )
                        
                        // Save the enhanced record
                        if (enhancedScore != score) {
                            Log.d("ExamScreen", "Updating exam record with ML-enhanced score: $enhancedScore (was $score)")
                            hybridExamService.saveStudentExamRecord(enhancedExamRecord, userId)
                        }
                        
                        // Automatically migrate exam from "available" to "completed" status
                        try {
                            Log.d("ExamScreen", "Migrating exam $examId from available to completed status")
                            
                            // Mark exam as taken by this student (prevent retaking)
                            val migrationResult = hybridExamService.markExamAsCompleted(examId, userId)
                            if (migrationResult.isSuccess) {
                                Log.d("ExamScreen", "Successfully migrated exam to completed status")
                            } else {
                                Log.e("ExamScreen", "Failed to migrate exam status: ${migrationResult.exceptionOrNull()?.message}")
                            }
                        } catch (e: Exception) {
                            Log.e("ExamScreen", "Error during exam migration", e)
                        }
                        
                        // Provide feedback on ML enhancements if any
                        if (enhancedScore > score) {
                            // Additional points awarded through ML scoring
                            val bonusPoints = enhancedScore - score
                            speak(textToSpeech, "Your exam has been submitted. ML analysis awarded you $bonusPoints additional points for short answer accuracy.")
                        } else {
                            speak(textToSpeech, "Your exam has been submitted successfully.")
                        }
                    } else {
                        Log.e("ExamScreen", "Failed to save exam record: ${result.exceptionOrNull()?.message}")
                        speak(textToSpeech, "There was an error saving your exam. Please contact your instructor.")
                    }
                } else {
                    Log.e("ExamScreen", "Could not retrieve user data: ${userDataResult.exceptionOrNull()?.message}")
                }
            } else {
                Log.e("ExamScreen", "No user ID available, cannot save exam record")
            }
        } catch (e: Exception) {
            Log.e("ExamScreen", "Error saving exam record", e)
        }
        
        // Call the completion handler with the score
        onExamComplete(score, totalPoints)
    }
}

private fun formatTime(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%02d:%02d".format(minutes, remainingSeconds)
}

private fun setupTextToSpeech(context: Context): TextToSpeech {
    var textToSpeech: TextToSpeech? = null
    textToSpeech = TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) {
            val result = TextToSpeech.LANG_AVAILABLE
            // Set speaking rate slightly slower for better comprehension
            textToSpeech?.setSpeechRate(0.85f)
            // Set slightly higher pitch for clearer distinction
            textToSpeech?.setPitch(1.05f)
        }
    }
    return textToSpeech
}

/**
 * Process exam text for better accessibility and reading
 * This helps with proper pronunciation by screen readers
 */
private fun processTextForAccessibility(text: String): String {
    // Replace common abbreviations
    var processedText = text
        .replace("e.g.", "for example")
        .replace("i.e.", "that is")
        .replace("etc.", "etcetera")
        .replace("vs.", "versus")
        .replace("fig.", "figure")
        .replace("eq.", "equation")
        
    // Add spaces after periods that don't have them
    processedText = processedText.replace(Regex("(\\.)([A-Z])"), ". $2")
    
    // Handle mathematical symbols for better speech
    processedText = processedText
        .replace("+", " plus ")
        .replace("-", " minus ")
        .replace("*", " times ")
        .replace("/", " divided by ")
        .replace("=", " equals ")
        .replace(">", " greater than ")
        .replace("<", " less than ")
        .replace(">=", " greater than or equal to ")
        .replace("<=", " less than or equal to ")
        
    // Handle code syntax for better speech
    processedText = processedText
        .replace("{", " open curly brace ")
        .replace("}", " close curly brace ")
        .replace("[", " open bracket ")
        .replace("]", " close bracket ")
        .replace("(", " open parenthesis ")
        .replace(")", " close parenthesis ")
        .replace(";", " semicolon ")
        
    // Normalize whitespace
    processedText = processedText.replace(Regex("\\s+"), " ").trim()
    
    return processedText
}

/**
 * Format numbers to be more readable by speech
 */
private fun formatNumbersForSpeech(text: String): String {
    // Handle phone numbers
    var result = Regex("(\\d{3})[- ]?(\\d{3})[- ]?(\\d{4})").replace(text) { matchResult ->
        val (area, prefix, line) = matchResult.destructured
        "$area, $prefix, $line"
    }
    
    // Handle decimal numbers
    result = Regex("(\\d+)\\.(\\d+)").replace(result) { matchResult ->
        val (whole, decimal) = matchResult.destructured
        "$whole point $decimal"
    }
    
    return result
}

private fun speak(textToSpeech: TextToSpeech, text: String) {
    val processedText = processTextForAccessibility(formatNumbersForSpeech(text))
    val utteranceId = UUID.randomUUID().toString()
    textToSpeech.speak(processedText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
}

/**
 * Speaks text with a delay before queuing next speech
 * Useful for sequential announcements
 */
private fun speakWithDelay(textToSpeech: TextToSpeech, text: String) {
    val processedText = processTextForAccessibility(formatNumbersForSpeech(text))
    val utteranceId = UUID.randomUUID().toString()
    textToSpeech.speak(processedText, TextToSpeech.QUEUE_ADD, null, utteranceId)
}

/**
 * Interrupt ongoing speech and speak text immediately
 */
private fun speakNow(textToSpeech: TextToSpeech, text: String) {
    val processedText = processTextForAccessibility(formatNumbersForSpeech(text))
    val utteranceId = UUID.randomUUID().toString()
    textToSpeech.speak(processedText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
}

/**
 * Simple braille character detection based on touch points.
 * This is a simplified implementation - a real app would use more sophisticated
 * pattern recognition.
 */
private fun detectBrailleCharacter(points: List<Offset>): String {
    if (points.isEmpty()) return ""
    
    // For this demo, we'll implement a very simple detection:
    // Divide the screen into a 2x3 grid (standard braille cell)
    // and check which cells were touched
    
    // First, normalize the points to a standard braille cell
    val minX = points.minOf { it.x }
    val maxX = points.maxOf { it.x }
    val minY = points.minOf { it.y }
    val maxY = points.maxOf { it.y }
    
    val width = maxX - minX
    val height = maxY - minY
    
    // If touches are too close together, they might be accidental
    if (width < 20 && height < 20 && points.size > 1) return ""
    
    // For now, implement a very simple detection just for letters A-Z
    // In braille, the number of dots determines the letter
    return when (points.size) {
        1 -> "A" // Single dot in top-left is 'A'
        2 -> "B" // Two dots vertically is 'B'
        3 -> "C" // Three dots is 'C'
        4 -> "D" // Four dots is 'D'
        5 -> "E" // Five dots is 'E'
        6 -> "F" // Six dots is 'F'
        else -> "" // Unknown pattern
    }
}

// Load questions for the current exam
private suspend fun loadExamQuestions(examId: String, examService: FirebaseExamService): List<Question> {
    Log.d("ExamScreen", "Loading questions for exam: $examId using HybridExamService")
    val hybridExamService = HybridExamService()
    val questionsResult = hybridExamService.getQuestionsForExam(examId)
    
    return if (questionsResult.isSuccess) {
        val examQuestions = questionsResult.getOrNull() ?: emptyList()
        Log.d("ExamScreen", "Loaded ${examQuestions.size} questions for exam $examId using HybridExamService")
        
        // Convert ExamQuestion to UI Question
        val questions = examQuestions.map { examQuestion ->
            Question(
                id = examQuestion.id,
                text = examQuestion.text,
                options = examQuestion.options,
                answer = examQuestion.correctAnswer,
                type = examQuestion.type.toString(), // Convert QuestionType enum to String
                number = examQuestion.number,
                points = examQuestion.points
            )
        }
        
        // Group questions by type, then sort each group by number
        // Define the preferred type order
        val typeOrder = listOf(
            "MULTIPLE_CHOICE",
            "TRUE_FALSE",
            "FILL_IN_THE_BLANK",
            "MATCHING",
            "SHORT_ANSWER",
            "ESSAY"
        )
        
        // Sort by type first (using the defined order), then by question number within each type
        questions.sortedWith(
            compareBy(
                { typeIndex -> typeOrder.indexOf(typeIndex.type).let { if (it == -1) Int.MAX_VALUE else it } },
                { it.number }
            )
        )
    } else {
        // If we get an error from HybridExamService, try the original method as fallback
        try {
            Log.d("ExamScreen", "HybridExamService failed, trying original FirebaseExamService")
            val modelQuestionsResult = examService.getQuestionsForExam(examId)
            
            if (modelQuestionsResult.isSuccess) {
                val modelQuestions = modelQuestionsResult.getOrNull() ?: emptyList()
                modelQuestions.map { modelQuestion ->
                    Question(
                        id = modelQuestion.id,
                        text = modelQuestion.text,
                        options = modelQuestion.options,
                        answer = modelQuestion.correctAnswer,
                        type = modelQuestion.type,
                        number = modelQuestion.number ?: 0, // Set default to 0 if number is null
                        points = modelQuestion.points
                    )
                }.sortedBy { it.number }
            } else {
                // If both methods fail, return mock questions for testing
                mockExamQuestions
            }
        } catch (e: Exception) {
            Log.e("ExamScreen", "Both services failed, using mock questions", e)
            mockExamQuestions
        }
    }
}

/**
 * Convert a model question from data layer to UI question for the exam screen
 */
private fun convertModelQuestionToUiQuestion(modelQuestion: ModelQuestion): Question {
    // If the question doesn't have a specified type, try to determine it from its structure
    val questionType = if (modelQuestion.type.isBlank()) {
        when {
            modelQuestion.options?.size == 2 && 
                modelQuestion.options[0] == "True" && 
                modelQuestion.options[1] == "False" -> "TRUE_FALSE"
            modelQuestion.options != null && modelQuestion.options.isNotEmpty() -> "MULTIPLE_CHOICE"
            modelQuestion.text.contains("_____") -> "FILL_IN_THE_BLANK"
            else -> "SHORT_ANSWER"
        }
    } else {
        modelQuestion.type
    }
    
    // Convert ModelQuestion to Question for the UI
    return Question(
        id = modelQuestion.id,
        text = modelQuestion.text,
        options = modelQuestion.options,
        answer = modelQuestion.correctAnswer,
        type = questionType,
        number = modelQuestion.number ?: 0, // Set default to 0 if number is null
        points = modelQuestion.points
    )
}

// In the renderQuestion function, update to handle all question types
@Composable
private fun renderQuestion(
    question: Question,
    answer: String?,
    onAnswerSelected: (String) -> Unit,
    textToSpeech: TextToSpeech,
    isRandomAccess: Boolean
) {
    // Process question text for better accessibility
    val accessibleQuestionText = processTextForAccessibility(question.text)
    
    // Get formatted question type for display
    val formattedType = when (question.type) {
        "MULTIPLE_CHOICE" -> "MULTIPLE CHOICE"
        "TRUE_FALSE" -> "TRUE OR FALSE"
        "SHORT_ANSWER" -> "SHORT ANSWER"
        "FILL_IN_THE_BLANK" -> "FILL IN THE BLANK"
        "MATCHING" -> "MATCHING"
        "ESSAY" -> "ESSAY"
        else -> question.type.replace("_", " ")
    }
    
    // Get color for the question type
    val typeColor = when (question.type) {
        "MULTIPLE_CHOICE" -> MaterialTheme.colorScheme.primary
        "TRUE_FALSE" -> MaterialTheme.colorScheme.tertiary
        "SHORT_ANSWER" -> MaterialTheme.colorScheme.secondary
        "FILL_IN_THE_BLANK" -> MaterialTheme.colorScheme.error
        "MATCHING" -> MaterialTheme.colorScheme.errorContainer
        "ESSAY" -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.primary
    }
    
    // Use LazyColumn for scrollable content instead of a regular Column
    // This is crucial for accessibility as it allows blind users to navigate through longer content
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        // Item for question type header
        item {
            Text(
                text = formattedType,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = typeColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .background(
                        color = typeColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(8.dp)
                    .semantics { 
                        contentDescription = "Question type: $formattedType" 
                    }
                    .clickable {
                        // Voice announcement of question type
                        speakNow(textToSpeech, "This is a $formattedType question")
                    }
            )
        }
        
        // Item for question text
        item {
            Text(
                text = question.text, // Keep original text for visual display
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .semantics { 
                        contentDescription = "Question: $accessibleQuestionText" // Use processed text for screen readers
                    }
                    .clickable {
                        // Read the question text aloud
                        speakNow(textToSpeech, "Question: $accessibleQuestionText")
                    }
            )
        }
        
        // Content based on question type
        item {
            when (question.type) {
                "MULTIPLE_CHOICE" -> {
                    renderMultipleChoiceQuestion(question, answer, onAnswerSelected, textToSpeech)
                }
                "TRUE_FALSE" -> {
                    renderTrueFalseQuestion(question, answer, onAnswerSelected, textToSpeech)
                }
                "SHORT_ANSWER" -> {
                    renderShortAnswerQuestion(question, answer, onAnswerSelected, textToSpeech)
                }
                "FILL_IN_THE_BLANK" -> {
                    renderFillInBlankQuestion(question, answer, onAnswerSelected, textToSpeech)
                }
                "MATCHING" -> {
                    renderMatchingQuestion(question, answer, onAnswerSelected, textToSpeech)
                }
                else -> {
                    // Fallback for unknown types
                    Text(
                        text = "Unknown question type: ${question.type}",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun renderMultipleChoiceQuestion(
    question: Question,
    answer: String?,
    onAnswerSelected: (String) -> Unit,
    textToSpeech: TextToSpeech
) {
    // Column layout with section for question instructions
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        // Instructions for blind users
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Instructions",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Double tap on an option to select it. Swipe left or right to navigate between questions.",
                    fontSize = 14.sp
                )
            }
        }
        
        // Display options in a two-column grid
        Text(
            text = "Please select one answer:",
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(vertical = 8.dp)
                .semantics {
                    contentDescription = "Please select one answer from the following options"
                }
        )
        
        // Two-column layout for options
        question.options?.chunked(2)?.forEach { rowOptions ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowOptions.forEachIndexed { columnIndex, option ->
                    val index = if (rowOptions.size == 2) columnIndex else 0
                    val optionLetter = if (option.matches(Regex("^[A-D][.)].*"))) {
                        option.substring(0, 1)
                    } else {
                        ('A' + (columnIndex + (rowOptions.size * index))).toString()
                    }
                    
                    val isSelected = answer == optionLetter
                    var lastTapTime by remember { mutableStateOf(0L) }
                    var touchStartTime by remember { mutableStateOf(0L) }
                    var touchDuration by remember { mutableStateOf(0L) }
                    
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .padding(4.dp)
                            .pointerInteropFilter { event ->
                                when (event.action) {
                                    MotionEvent.ACTION_DOWN -> {
                                        touchStartTime = System.currentTimeMillis()
                                        true
                                    }
                                    MotionEvent.ACTION_UP -> {
                                        touchDuration = System.currentTimeMillis() - touchStartTime
                                        val currentTime = System.currentTimeMillis()
                                        
                                        // Long press for option exploration (3 seconds)
                                        if (touchDuration > 2000) {
                                            speakNow(textToSpeech, "Option $optionLetter is ${if (isSelected) "selected" else "not selected"}. Double tap to ${if (isSelected) "unselect" else "select"}.")
                                            touchStartTime = 0
                                        } else if (currentTime - lastTapTime < 300) {
                                            // Double tap detected
                                            onAnswerSelected(optionLetter)
                                            speakNow(textToSpeech, "Selected option $optionLetter: ${getOptionText(option)}")
                                        } else {
                                            // Single tap - announce the option
                                            speakNow(textToSpeech, "Option $optionLetter: ${getOptionText(option)}. Double tap to select.")
                                        }
                                        lastTapTime = currentTime
                                        true
                                    }
                                    else -> false
                                }
                            }
                            .semantics { 
                                contentDescription = "Option $optionLetter: ${getOptionText(option)}. ${if(isSelected) "Selected" else "Not selected"}. Double tap to select." 
                            },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) 
                                MaterialTheme.colorScheme.primaryContainer 
                            else MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) 
                                MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Option letter in a circle
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) 
                                            MaterialTheme.colorScheme.primary 
                                        else MaterialTheme.colorScheme.outlineVariant,
                                        shape = CircleShape
                                    )
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) 
                                            MaterialTheme.colorScheme.primary 
                                        else MaterialTheme.colorScheme.surface
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = optionLetter,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) 
                                        MaterialTheme.colorScheme.onPrimary 
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            // Option text
                            Text(
                                text = getOptionText(option),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                
                // If there's only one option in the row, add an empty spacer for the second column
                if (rowOptions.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
        
        // Current selection status
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Your selection: ",
                    fontWeight = FontWeight.Medium
                )
                
                if (answer != null) {
                    val selectedOption = question.options?.find { 
                        it.startsWith("$answer.") || it.startsWith("$answer)") || 
                        it.startsWith("${('A' + answer.first().code - 'A'.code)}.")
                    }
                    
                    Text(
                        text = "Option $answer - ${selectedOption?.let { getOptionText(it) } ?: "None"}",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = "None selected",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        // Accessibility assistance buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { 
                    speakNow(textToSpeech, "Question: ${question.text}")
                    val options = question.options?.mapIndexed { index, option ->
                        val optionLetter = if (option.matches(Regex("^[A-D][.)].*"))) {
                            option.substring(0, 1)
                        } else {
                            ('A' + index).toString()
                        }
                        "Option $optionLetter: ${getOptionText(option)}"
                    }?.joinToString(". ") ?: ""
                    
                    speakWithDelay(textToSpeech, "Options: $options")
                },
                modifier = Modifier
                    .weight(1f)
                    .semantics { 
                        contentDescription = "Read question and options again"
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Read Question")
            }
            
            OutlinedButton(
                onClick = { 
                    if (answer != null) {
                        val selectedOption = question.options?.find { 
                            it.startsWith("$answer.") || it.startsWith("$answer)") || 
                            it.startsWith("${('A' + answer.first().code - 'A'.code)}.")
                        }
                        
                        speakNow(textToSpeech, "You selected Option $answer: ${selectedOption?.let { getOptionText(it) } ?: "Unknown"}")
                    } else {
                        speakNow(textToSpeech, "You haven't selected any option yet. Double tap on an option to select it.")
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .semantics { 
                        contentDescription = "Read your current selection"
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("My Selection")
            }
        }
    }
}

@Composable
private fun renderTrueFalseQuestion(
    question: Question,
    answer: String?,
    onAnswerSelected: (String) -> Unit,
    textToSpeech: TextToSpeech
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        listOf("True", "False").forEach { option ->
            val isSelected = answer == option
            var lastTapTime by remember { mutableStateOf(0L) }
            
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clickable { 
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastTapTime < 300) {
                            // Double tap detected
                            onAnswerSelected(option)
                            speakNow(textToSpeech, "Selected $option")
                        } else {
                            // Single tap - announce the option
                            speakNow(textToSpeech, "Option: $option. Double tap to select.")
                        }
                        lastTapTime = currentTime
                    }
                    .semantics { 
                        contentDescription = "Option: $option. ${if(isSelected) "Selected" else "Not selected"}. Double tap to select."
                    },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) 
                        MaterialTheme.colorScheme.primary 
                    else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = option,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) 
                                MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.onSurface
                        )
                        
                        if (isSelected) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.Done,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun renderShortAnswerQuestion(
    question: Question,
    answer: String?,
    onAnswerSelected: (String) -> Unit,
    textToSpeech: TextToSpeech
) {
    var showBrailleInput by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Display the question type at the top
        Text(
            text = "SHORT ANSWER",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .background(
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(8.dp)
        )
        
        // Larger text box with more space
        OutlinedTextField(
            value = answer ?: "",
            onValueChange = { onAnswerSelected(it) },
            label = { Text("Your Answer") },
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp) // Much larger height for better visibility
                .padding(vertical = 8.dp)
                .semantics { 
                    contentDescription = "Short answer input field. ${if (answer.isNullOrEmpty()) "Empty" else "Current answer: $answer"}"
                },
            placeholder = { Text("Type your answer here") },
            textStyle = TextStyle(fontSize = 18.sp), // Larger text size
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.secondary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(8.dp),
            maxLines = 10,
            minLines = 6
        )

        // Helper text for blind users
        Text(
            text = "Type your detailed answer in the field above.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, bottom = 16.dp)
        )
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Read back button for answer verification
            Button(
                onClick = { 
                    if (answer.isNullOrEmpty()) {
                        speakNow(textToSpeech, "No answer provided yet. Use the text field to type your answer.")
                    } else {
                        speakNow(textToSpeech, "Your current answer is: $answer")
                    }
                },
                modifier = Modifier
                    .semantics { 
                        contentDescription = "Read back your answer"
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Read My Answer")
            }
            
            // Add braille input button for accessibility
            Button(
                onClick = { 
                    // Show braille input screen
                    showBrailleInput = true
                },
                modifier = Modifier
                    .semantics { 
                        contentDescription = "Open braille input for this question"
                    },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.TouchApp,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Braille Input")
            }
        }
    }
    
    // Show braille input screen when requested
    if (showBrailleInput) {
        BrailleInputScreen(
            onTextChanged = { newText ->
                onAnswerSelected(newText)
            },
            onDismiss = {
                showBrailleInput = false
            },
            initialText = answer ?: "",
            questionText = question.text
        )
    }
}

@Composable
private fun renderFillInBlankQuestion(
    question: Question,
    answer: String?,
    onAnswerSelected: (String) -> Unit,
    textToSpeech: TextToSpeech
) {
    var showBrailleInput by remember { mutableStateOf(false) }
    // Format question to highlight the blank
    val questionText = question.text
    val formattedQuestion = buildAnnotatedString {
        val parts = questionText.split("_____")
        
        if (parts.size <= 1) {
            // Regular question without blank marker
            append(questionText)
        } else {
            // Question with blank
            append(parts[0])
            
            // Add blank
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) {
                append("_____")
            }
            
            // Add remaining text if any
            if (parts.size > 1) {
                append(parts[1])
            }
        }
    }
    
    // Display formatted question
    Text(
        text = formattedQuestion,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .padding(bottom = 24.dp)
            .semantics { contentDescription = "Question: ${question.text}" }
    )
    
    // Input field for the blank
    OutlinedTextField(
        value = answer ?: "",
        onValueChange = { onAnswerSelected(it) },
        label = { Text("Your Answer") },
        placeholder = { Text("Fill in the blank...") },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .semantics { contentDescription = "Fill in the blank text field" },
        singleLine = true
    )
    
    // Braille input button
    Button(
        onClick = { 
            showBrailleInput = true
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .semantics { 
                contentDescription = "Open braille input for this fill-in-blank question"
            },
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Icon(
            imageVector = Icons.Default.TouchApp,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Use Braille Input")
    }
    
    // Show braille input screen when requested
    if (showBrailleInput) {
        BrailleInputScreen(
            onTextChanged = { newText ->
                onAnswerSelected(newText)
            },
            onDismiss = {
                showBrailleInput = false
            },
            initialText = answer ?: "",
            questionText = question.text
        )
    }
}

@Composable
private fun renderMatchingQuestion(
    question: Question,
    answer: String?,
    onAnswerSelected: (String) -> Unit,
    textToSpeech: TextToSpeech
) {
    val options = question.options ?: emptyList()
    
    // Split options into two columns
    val columnA = options.filter { it.matches(Regex("^\\d+.*")) }
    val columnB = options.filter { it.matches(Regex("^[A-Z].*")) }
    
    // Matching input
    var currentAnswer by remember { mutableStateOf(answer ?: "") }
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Instructions card for blind users
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "How to Answer Matching Questions",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Match items from Column A with items from Column B by entering pairs like 1B,2A,3C,4D in the field below. Double tap on a row to hear it read aloud.",
                    fontSize = 14.sp
                )
            }
        }
        
        // Matching table with improved accessibility
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(8.dp)
                )
                .clip(RoundedCornerShape(8.dp))
        ) {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(vertical = 12.dp)
            ) {
                Text(
                    text = "Column A",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                )
                
                // Divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(24.dp)
                        .background(MaterialTheme.colorScheme.outline)
                )
                
                Text(
                    text = "Column B",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                )
            }
            
            // Item rows - ensure we use the maximum number of items from either column
            val maxItems = maxOf(columnA.size, columnB.size)
            
            for (i in 0 until maxItems) {
                // Horizontal divider
                if (i > 0) {
                    Divider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        thickness = 1.dp
                    )
                }
                
                // Extract the item number and content from columnA
                val columnAItem = if (i < columnA.size) columnA[i] else ""
                val columnANumber = columnAItem.takeWhile { it.isDigit() }
                val columnAContent = columnAItem.dropWhile { it.isDigit() }.trim()
                
                // Extract the letter and content from columnB
                val columnBItem = if (i < columnB.size) columnB[i] else ""
                val columnBLetter = if (columnBItem.isNotEmpty()) columnBItem.first().toString() else ""
                val columnBContent = columnBItem.drop(1).trim()
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            // Read this row aloud when clicked for better accessibility
                            val columnAText = if (columnAItem.isNotEmpty()) "Column A item $columnANumber: $columnAContent" else ""
                            val columnBText = if (columnBItem.isNotEmpty()) "Column B item $columnBLetter: $columnBContent" else ""
                            speakNow(textToSpeech, "$columnAText matches with $columnBText")
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Column A item with highlighted number
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (columnAItem.isNotEmpty()) {
                            // Number badge
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = columnANumber,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = columnAContent)
                        }
                    }
                    
                    // Vertical divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    )
                    
                    // Column B item with highlighted letter
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (columnBItem.isNotEmpty()) {
                            // Letter badge
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = columnBLetter,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = columnBContent)
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Current matches visualization
        if (currentAnswer.isNotEmpty()) {
            Text(
                text = "Your Current Matches",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Parse the current answer string into pairs
            val pairs = currentAnswer.split(",").filter { it.length >= 2 }
            
            // Replace FlowRow with standard Row + Column layout
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                // Group pairs into rows of 3
                pairs.chunked(3).forEach { rowPairs ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowPairs.forEach { pair ->
                            if (pair.length >= 2) {
                                val leftNumber = pair.first().toString()
                                val rightLetter = pair.last().toString()
                                
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                            .fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "$leftNumber → $rightLetter",
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Add spacers for incomplete rows
                        repeat(3 - rowPairs.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        
        // Input for matching pairs
        OutlinedTextField(
            value = currentAnswer,
            onValueChange = { 
                currentAnswer = it
                onAnswerSelected(it)
            },
            label = { Text("Your Matches (e.g., 1B,2A,3C,4D)") },
            placeholder = { Text("Enter matching pairs...") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .semantics { contentDescription = "Matching pairs input field" }
        )
    
        // Helper text
        Text(
            text = "Specify your matches in format: 1B,2A,3C,4D where numbers come from Column A and letters from Column B",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, start = 4.dp)
        )
        
        // Accessibility help buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { 
                    val readableItems = columnA.mapIndexed { index, item ->
                        val number = item.takeWhile { it.isDigit() }
                        val content = item.dropWhile { it.isDigit() }.trim()
                        "Item $number: $content"
                    }.joinToString(". ")
                    
                    speakNow(textToSpeech, "Column A items: $readableItems")
                },
                modifier = Modifier
                    .weight(1f)
                    .semantics { 
                        contentDescription = "Read Column A items"
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Read Column A")
            }
            
            OutlinedButton(
                onClick = { 
                    val readableItems = columnB.mapIndexed { index, item ->
                        val letter = item.first().toString()
                        val content = item.drop(1).trim()
                        "Item $letter: $content"
                    }.joinToString(". ")
                    
                    speakNow(textToSpeech, "Column B items: $readableItems")
                },
                modifier = Modifier
                    .weight(1f)
                    .semantics { 
                        contentDescription = "Read Column B items"
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Read Column B")
            }
        }
        
        // Read current matches button
        if (currentAnswer.isNotEmpty()) {
            OutlinedButton(
                onClick = {
                    val pairs = currentAnswer.split(",").filter { it.length >= 2 }
                    if (pairs.isNotEmpty()) {
                        val matchText = pairs.joinToString(", ") { pair ->
                            if (pair.length >= 2) {
                                val leftNumber = pair.first().toString()
                                val rightLetter = pair.last().toString()
                                "Number $leftNumber matches with Letter $rightLetter"
                            } else {
                                ""
                            }
                        }
                        speakNow(textToSpeech, "Your current matches: $matchText")
                    } else {
                        speakNow(textToSpeech, "You haven't entered any valid matches yet.")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .semantics { 
                        contentDescription = "Read my current matches"
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Read My Matches")
            }
        }
    }
}

// Helper function to extract option text without the letter prefix
private fun getOptionText(option: String): String {
    // Check if option starts with "A. ", "B. ", etc.
    val regex = Regex("^[A-Z][.)]\\s+(.*)$")
    val match = regex.find(option)
    
    return if (match != null) {
        match.groupValues[1]
    } else {
        option
    }
}

private suspend fun announceFirstQuestion(textToSpeech: TextToSpeech, examTitle: String, question: Question) {
    speakNow(textToSpeech, "Exam loaded: $examTitle. Starting with question 1.")
    
    delay(3000) // Wait a bit before reading options
    
    if (question.options != null) {
        speakWithDelay(textToSpeech, "Options: ${question.options?.joinToString(". ") ?: ""}")
        speakWithDelay(textToSpeech, "This is a multiple choice question. Double tap on an option to select it. Swipe left and right to navigate between questions.")
        speakWithDelay(textToSpeech, "You can also press and hold on an option for 3 seconds to hear its status.")
    } else if (question.type == "FILL_IN_THE_BLANK") {
        speakWithDelay(textToSpeech, "This is a fill in the blank question. Use the text field to enter your answer.")
    } else if (question.type == "SHORT_ANSWER") {
        speakWithDelay(textToSpeech, "This is a short answer question. Use the text field to type your response.")
    } else {
        speakWithDelay(textToSpeech, "This is a ${question.type.lowercase().replace('_', ' ')} question. Use the appropriate input method to answer.")
    }
    
    speakWithDelay(textToSpeech, "Use the previous and next buttons at the bottom of the screen to navigate between questions. The submit button will be available on the last question.")
    speakWithDelay(textToSpeech, "You can also use the tab key to navigate through elements and enter key to activate them.")
}

@Composable
fun MultipleChoiceQuestion(
    question: Question,
    selectedAnswer: String?,
    onAnswerSelected: (String) -> Unit,
    textToSpeech: TextToSpeech
) {
    val options = question.options ?: return
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Question text
        Text(
            text = question.text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
                .semantics {
                    contentDescription = "Question: ${question.text}"
                }
        )
        
        // Options
        options.forEachIndexed { index, option ->
            val optionLetter = ('A' + index).toString()
            val isSelected = selectedAnswer == optionLetter
            
            // Option card with enhanced visual feedback
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable {
                        // Announce the selection with TTS
                        speak(textToSpeech, "Selected option $optionLetter: $option")
                        onAnswerSelected(optionLetter)
                    }
                    .semantics {
                        contentDescription = "Option $optionLetter: $option. ${if (isSelected) "Selected" else "Not selected"}."
                    },
                elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                ),
                border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Option indicator (circle with letter)
                    Surface(
                        shape = CircleShape,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = optionLetter,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    // Option text
                    Text(
                        text = option,
                        fontSize = 16.sp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    
                    if (isSelected) {
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        // Selected indicator
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}