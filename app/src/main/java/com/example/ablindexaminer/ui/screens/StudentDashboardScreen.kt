package com.example.ablindexaminer.ui.screens

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import com.example.ablindexaminer.data.firebase.FirebaseAuthService
import com.example.ablindexaminer.data.firebase.FirebaseExamService
import com.example.ablindexaminer.data.firebase.HybridExamService
import com.example.ablindexaminer.ui.components.AppBarWithDrawer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import com.example.ablindexaminer.ExamLauncher
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.AssignmentTurnedIn
import androidx.compose.material.icons.outlined.AssignmentLate
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.ablindexaminer.ui.security.ExamSecurityManager
import android.app.Activity
import android.widget.Toast
import com.example.ablindexaminer.ui.accessibility.StudentDashboardBlindController
import com.example.ablindexaminer.ui.accessibility.StudentDashboardBlindWrapper


// Remove mock exam data for testing
// Mock exam data for testing when Firebase is not available
// private fun getMockStudentExams(): List<Exam> {
//     return listOf(
//         Exam(
//             id = "mock1",
//             title = "Introduction to Computer Science",
//             subject = "Computer Science",
//             duration = 120,
//             questionCount = 20,
//             date = Date(),
//             type = "MULTIPLE_CHOICE"
//         ),
//         Exam(
//             id = "mock2",
//             title = "Physics Fundamentals",
//             subject = "Physics",
//             duration = 90,
//             questionCount = 15,
//             date = Date(),
//             type = "MIXED"
//         )
//     )
// }

data class Exam(
    val id: String,
    val title: String,
    val subject: String,
    val duration: Int, // in minutes
    val questionCount: Int,
    val date: Date,
    val type: String = "MULTIPLE_CHOICE", // Default to multiple choice
    val targetYear: String = "ALL", // Add target year property with default value
    val targetSection: String = "ALL" // Add target section property with default value
)

// Add new data model for exam history
data class StudentExamRecord(
    val id: String,
    val title: String,
    val subject: String,
    val date: Date,
    val score: Int,
    val totalPoints: Int,
    val answered: Int,
    val totalQuestions: Int,
    val questionDetails: List<QuestionAttempt> = emptyList(),
    val studentName: String = "", // Added for teacher view
    val studentId: String = "",   // Added for teacher view
    val recordId: String = ""     // Document ID for updating
)

data class QuestionAttempt(
    val id: String,
    val questionText: String,
    val correctAnswer: String,
    val studentAnswer: String?,
    val isCorrect: Boolean,
    val type: String,
    val options: List<String>? = null,
    val points: Int = 1
)

// Enum for dashboard tabs
enum class StudentDashboardTab {
    AVAILABLE_EXAMS, EXAM_HISTORY
}

// Helper function to refresh student data - moved outside composable
suspend fun refreshStudentData(
    userId: String,
    studentYear: String,
    studentSection: String,
    hybridExamService: HybridExamService,
    onAvailableExamsUpdated: (List<Exam>) -> Unit,
    onExamHistoryUpdated: (List<StudentExamRecord>) -> Unit,
    onError: (String) -> Unit
) {
    try {
        Log.d("StudentDashboard", "Refreshing student data for: $userId, Year: $studentYear, Section: $studentSection")
        
        // Refresh available exams
        val availableExamsResult = hybridExamService.getAvailableExamsForStudent(
            userId,
            studentYear,
            studentSection
        )
        
        if (availableExamsResult.isSuccess) {
            val availableExams = availableExamsResult.getOrDefault(emptyList())
            Log.d("StudentDashboard", "Retrieved ${availableExams.size} available exams for student")
            onAvailableExamsUpdated(availableExams)
        } else {
            Log.e("StudentDashboard", "Failed to get available exams: ${availableExamsResult.exceptionOrNull()?.message}")
            onError("Could not load available exams")
        }
        
        // Refresh exam history
        val historyResult = hybridExamService.getStudentExamRecords(userId)
        if (historyResult.isSuccess) {
            val examHistory = historyResult.getOrDefault(emptyList())
            Log.d("StudentDashboard", "Retrieved ${examHistory.size} exam records")
            onExamHistoryUpdated(examHistory)
        } else {
            Log.e("StudentDashboard", "Failed to get exam history: ${historyResult.exceptionOrNull()?.message}")
            onError("Could not load exam history")
        }
    } catch (e: Exception) {
        Log.e("StudentDashboard", "Error refreshing student data", e)
        onError("Error refreshing data: ${e.message}")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentDashboardScreen(
    username: String,
    onLogout: () -> Unit,
    navController: NavController,
    onExamSelected: (Exam) -> Unit
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val textToSpeech = remember { initializeTextToSpeech(context) }
    
    // Initialize blind navigation controller for accessibility
    val blindController = remember { 
        StudentDashboardBlindController(context, textToSpeech)
    }
    
    // Initialize the security manager for secure exam launches
    val securityManager = remember { ExamSecurityManager.getInstance() }
    
    // State variables - declare these first
    var studentName by remember { mutableStateOf("") }
    var studentYear by remember { mutableStateOf("1") }
    var studentSection by remember { mutableStateOf("A") }
    var exams by remember { mutableStateOf<List<Exam>>(emptyList()) }
    var examHistory by remember { mutableStateOf<List<StudentExamRecord>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(StudentDashboardTab.AVAILABLE_EXAMS) }
    
    // State for question details dialog
    var showQuestionDetailsDialog by remember { mutableStateOf(false) }
    var selectedExamRecord by remember { mutableStateOf<StudentExamRecord?>(null) }
    
    // Get current user ID and services
    val authService = remember { FirebaseAuthService() }
    val hybridExamService = remember { HybridExamService() }
    val coroutineScope = rememberCoroutineScope()
    
    // Set up exam result launcher to handle completion
    val examResultLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Handle exam completion result
        if (result.resultCode == Activity.RESULT_OK) {
            val score = result.data?.getIntExtra("score", 0) ?: 0
            val total = result.data?.getIntExtra("total", 0) ?: 0
            val examId = result.data?.getStringExtra("examId") ?: ""
            val examCompleted = result.data?.getBooleanExtra("examCompleted", false) ?: false
            
            if (examCompleted) {
                // Show completion message
                Toast.makeText(
                    context,
                    "Exam completed successfully! Score: $score/$total",
                    Toast.LENGTH_LONG
                ).show()
                
                // Refresh student data to reflect completion
                coroutineScope.launch {
                    isLoading = true
                    try {
                        Log.d("StudentDashboard", "Refreshing data after exam completion: $examId")
                        
                        val userId = authService.getCurrentUserId()
                        if (userId != null) {
                            // Use the centralized refresh function
                            refreshStudentData(
                                userId = userId,
                                studentYear = studentYear,
                                studentSection = studentSection,
                                hybridExamService = hybridExamService,
                                onAvailableExamsUpdated = { updatedExams: List<Exam> ->
                                    exams = updatedExams
                                    Log.d("StudentDashboard", "Available exams updated: ${updatedExams.size} exams")
                                },
                                onExamHistoryUpdated = { updatedHistory: List<StudentExamRecord> ->
                                    examHistory = updatedHistory
                                    Log.d("StudentDashboard", "Exam history updated: ${updatedHistory.size} records")
                                },
                                onError = { errorMsg: String ->
                                    error = errorMsg
                                    Log.e("StudentDashboard", "Error refreshing after exam completion: $errorMsg")
                                }
                            )
                            
                            // Provide audio feedback about the updated state
                            delay(1000)
                            if (exams.isEmpty()) {
                                speak(textToSpeech, "All exams completed! Check your Exam History to see your results.")
                            } else {
                                speak(textToSpeech, "Exam completed. You now have ${exams.size} available exams remaining.")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("StudentDashboard", "Error refreshing exams after completion", e)
                        error = "Failed to refresh exam list: ${e.message}"
                    } finally {
                        isLoading = false
                    }
                }
            }
        }
    }
    
    // Register the result launcher with security manager
    LaunchedEffect(examResultLauncher) {
        securityManager.registerResultLauncher(examResultLauncher)
    }
    
    // Function to refresh available exams
    val refreshExams = {
        coroutineScope.launch {
            isLoading = true
            error = null
            
            val userId = authService.getCurrentUserId()
            if (userId != null) {
                refreshStudentData(
                    userId = userId,
                    studentYear = studentYear,
                    studentSection = studentSection,
                    hybridExamService = hybridExamService,
                    onAvailableExamsUpdated = { newExams: List<Exam> ->
                        exams = newExams
                    },
                    onExamHistoryUpdated = { newHistory: List<StudentExamRecord> ->
                        examHistory = newHistory
                    },
                    onError = { errorMessage: String ->
                        error = errorMessage
                        speak(textToSpeech, "Error: $errorMessage")
                    }
                )
            } else {
                Log.e("StudentDashboard", "No user ID available for refresh")
                error = "You need to be logged in to view exams"
                speak(textToSpeech, "Error: You need to be logged in to view exams")
            }
            
            isLoading = false
        }
    }
    
    
    // Set up periodic refresh to catch scheduled exams that become available
    LaunchedEffect(Unit) {
        while (true) {
            delay(60000) // Check every minute for new exams
            if (!isLoading) {
                refreshExams()
            }
        }
    }
    
    // Load student data on initial composition and when returning from exams
    LaunchedEffect(Unit) {
        try {
            isLoading = true
            error = null
            val userId = authService.getCurrentUserId()
            
            if (userId != null) {
                // Get student's information from Firestore
                val userSnapshot = authService.getUserData(userId)
                
                if (userSnapshot.isSuccess) {
                    val userData = userSnapshot.getOrNull()
                    
                    if (userData != null) {
                        // Extract student information
                        studentName = userData["fullName"] as? String ?: ""
                        studentYear = userData["year"] as? String ?: "1"
                        studentSection = userData["section"] as? String ?: "A"
                        
                        Log.d("StudentDashboard", "Student: $studentName, Year: $studentYear, Section: $studentSection")
                        
                        // Now use the helper function to load both available exams and exam history
                        refreshStudentData(
                            userId = userId,
                            studentYear = studentYear,
                            studentSection = studentSection,
                            hybridExamService = hybridExamService,
                            onAvailableExamsUpdated = { availableExams: List<Exam> ->
                                exams = availableExams
                                
                                // Speak the number of available exams
                                coroutineScope.launch {
                                    delay(1000) // Small delay
                                    if (availableExams.isEmpty()) {
                                        Log.d("StudentDashboard", "No exams available for this student")
                                        speak(textToSpeech, "You have no available exams at this time. Check the Exam History tab to see exams you've already taken.")
                                    } else {
                                        Log.d("StudentDashboard", "Found ${availableExams.size} exams for this student")
                                        speak(textToSpeech, "You have ${availableExams.size} exams available. Swipe through the list to hear details about each exam.")
                                    }
                                }
                            },
                            onExamHistoryUpdated = { history: List<StudentExamRecord> ->
                                examHistory = history
                            },
                            onError = { errorMessage: String ->
                                error = errorMessage
                                speak(textToSpeech, "Error: $errorMessage")
                            }
                        )
                        
                        isLoading = false
                    } else {
                        Log.e("StudentDashboard", "User data is null despite successful retrieval")
                        error = "Could not retrieve student information"
                        isLoading = false
                        speak(textToSpeech, "Error: Could not retrieve your student information. Please contact support.")
                    }
                } else {
                    Log.e("StudentDashboard", "Failed to get user data: ${userSnapshot.exceptionOrNull()}")
                    error = "Could not retrieve student information"
                    isLoading = false
                    speak(textToSpeech, "Error: Could not retrieve your student information. Please contact support.")
                }
            } else {
                Log.e("StudentDashboard", "No user is logged in")
                error = "You need to be logged in to view exams"
                isLoading = false
                speak(textToSpeech, "Error: You need to be logged in to view exams. Please log in again.")
            }
        } catch (e: Exception) {
            Log.e("StudentDashboard", "Error loading student dashboard", e)
            error = "An error occurred: ${e.message}"
            isLoading = false
            speak(textToSpeech, "Error loading dashboard. Please try again later.")
        }
    }
    
    // When returning from an exam, refresh the data
    BackHandler {
        // Refresh exams first, then navigate back
        coroutineScope.launch {
        refreshExams()
            // Add a small delay to ensure refresh completes
            delay(500)
        navController.popBackStack()
        }
    }
    
    // Set up navigation items for blind accessibility
    LaunchedEffect(selectedTab, exams, examHistory, isLoading) {
        if (!isLoading) {
            val navigationItems = mutableListOf<StudentDashboardBlindController.DashboardNavigationItem>()
            
            // Add welcome message
            navigationItems.add(
                StudentDashboardBlindController.DashboardNavigationItem(
                    id = "welcome",
                    text = "Welcome ${studentName.ifEmpty { username }}",
                    description = "Welcome ${studentName.ifEmpty { username }}",
                    detailedDescription = "Welcome to your student dashboard, ${studentName.ifEmpty { username }}. You are viewing exams for Year $studentYear, Section $studentSection.",
                    action = { },
                    isClickable = false,
                    itemType = StudentDashboardBlindController.ItemType.HEADER
                )
            )
            
            // Add refresh button
            navigationItems.add(
                StudentDashboardBlindController.DashboardNavigationItem(
                    id = "refresh",
                    text = "Refresh Exams",
                    description = "Refresh exams button",
                    detailedDescription = "Refresh button to update your available exams and history",
                    action = { 
                        refreshExams()
                        speak(textToSpeech, "Refreshing exams. Please wait.")
                    },
                    itemType = StudentDashboardBlindController.ItemType.BUTTON
                )
            )
            
            // Add tab navigation
            navigationItems.add(
                StudentDashboardBlindController.DashboardNavigationItem(
                    id = "available_exams_tab",
                    text = "Available Exams Tab",
                    description = "Available exams tab",
                    detailedDescription = "Switch to available exams tab to view exams you can take",
                    action = { 
                        selectedTab = StudentDashboardTab.AVAILABLE_EXAMS
                        speak(textToSpeech, "Available exams tab selected. Showing exams you can take.")
                    },
                    itemType = StudentDashboardBlindController.ItemType.TAB
                )
            )
            
            navigationItems.add(
                StudentDashboardBlindController.DashboardNavigationItem(
                    id = "exam_history_tab",
                    text = "Exam History Tab",
                    description = "Exam history tab",
                    detailedDescription = "Switch to exam history tab to view your completed exams and results",
                    action = { 
                        selectedTab = StudentDashboardTab.EXAM_HISTORY
                        speak(textToSpeech, "Exam history tab selected. Showing your completed exams.")
                    },
                    itemType = StudentDashboardBlindController.ItemType.TAB
                )
            )
            
            // Add content based on selected tab
            when (selectedTab) {
                StudentDashboardTab.AVAILABLE_EXAMS -> {
                    if (exams.isNotEmpty()) {
                        exams.forEachIndexed { index, exam ->
                            navigationItems.add(
                                StudentDashboardBlindController.DashboardNavigationItem(
                                    id = "exam_${exam.id}",
                                    text = exam.title,
                                    description = "Exam: ${exam.title}",
                                    detailedDescription = "Exam ${index + 1} of ${exams.size}. Title: ${exam.title}. Subject: ${exam.subject}. Duration: ${exam.duration} minutes. Questions: ${exam.questionCount}. Double tap to start this exam.",
                                    action = {
                                        speak(textToSpeech, "Starting exam: ${exam.title}. Security measures are being activated. Good luck!")
                                        if (context is Activity) {
                                            securityManager.launchSecureExam(
                                                activity = context,
                                                examId = exam.id,
                                                examTitle = exam.title,
                                                duration = exam.duration,
                                                examType = exam.type,
                                                examResultLauncher = examResultLauncher
                                            )
                                        } else {
                                            onExamSelected(exam)
                                        }
                                    },
                                    itemType = StudentDashboardBlindController.ItemType.EXAM_CARD,
                                    additionalInfo = "Duration: ${exam.duration} minutes, Questions: ${exam.questionCount}"
                                )
                            )
                        }
                    } else {
                        navigationItems.add(
                            StudentDashboardBlindController.DashboardNavigationItem(
                                id = "no_exams",
                                text = "No available exams",
                                description = "No available exams",
                                detailedDescription = "You have no available exams at this time. Check back later for new exams assigned to your class.",
                                action = { },
                                isClickable = false,
                                itemType = StudentDashboardBlindController.ItemType.STATUS
                            )
                        )
                    }
                }
                StudentDashboardTab.EXAM_HISTORY -> {
                    if (examHistory.isNotEmpty()) {
                        examHistory.forEachIndexed { index, record ->
                            navigationItems.add(
                                StudentDashboardBlindController.DashboardNavigationItem(
                                    id = "history_${record.id}",
                                    text = record.title,
                                    description = "Completed exam: ${record.title}",
                                    detailedDescription = "Exam result ${index + 1} of ${examHistory.size}. Title: ${record.title}. Subject: ${record.subject}. Score: ${record.score} out of ${record.totalPoints} points. Answered: ${record.answered} out of ${record.totalQuestions} questions. Double tap to view details.",
                                    action = {
                                        selectedExamRecord = record
                                        showQuestionDetailsDialog = true
                                        speak(textToSpeech, "Opening detailed results for ${record.title}")
                                    },
                                    itemType = StudentDashboardBlindController.ItemType.RESULT_CARD
                                )
                            )
                        }
                    } else {
                        navigationItems.add(
                            StudentDashboardBlindController.DashboardNavigationItem(
                                id = "no_history",
                                text = "No exam history",
                                description = "No exam history",
                                detailedDescription = "You haven't completed any exams yet. Take some exams to see your results here.",
                                action = { },
                                isClickable = false,
                                itemType = StudentDashboardBlindController.ItemType.STATUS
                            )
                        )
                    }
                }
            }
            
            blindController.setNavigationItems(navigationItems)
            blindController.currentScreen = when (selectedTab) {
                StudentDashboardTab.AVAILABLE_EXAMS -> "Available Exams"
                StudentDashboardTab.EXAM_HISTORY -> "Exam History"
            }
        }
    }

    StudentDashboardBlindWrapper(controller = blindController) {
        AppBarWithDrawer(
            title = "Student Dashboard",
            navController = navController,
            username = studentName.ifEmpty { username },
            userRole = "Student",
            onLogout = onLogout
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Welcome message
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Welcome, ${studentName.ifEmpty { username }}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics {
                            contentDescription = "Welcome, ${studentName.ifEmpty { username }}"
                        }
                    )
                    
                    Text(
                        text = "You are seeing exams for Year $studentYear, Section $studentSection",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    
                    // Add a refresh button at the top for better visibility and accessibility
                    Button(
                        onClick = { 
                            refreshExams() 
                            speak(textToSpeech, "Refreshing exams. Please wait.")
                        },
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 8.dp)
                            .semantics { 
                                contentDescription = "Refresh exams button. Double tap to refresh your available exams and history."
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Refresh Exams")
                    }
                }
                
                // Tab row for switching between available exams and history
                TabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ) {
                    Tab(
                        selected = selectedTab == StudentDashboardTab.AVAILABLE_EXAMS,
                        onClick = { 
                            selectedTab = StudentDashboardTab.AVAILABLE_EXAMS
                            speak(textToSpeech, "Available exams tab. Showing exams you can take.")
                        },
                        text = { Text("Available Exams") },
                        icon = { Icon(imageVector = Icons.Default.AssignmentTurnedIn, contentDescription = null) },
                        modifier = Modifier.semantics { 
                            contentDescription = "Switch to available exams tab. Double tap to view exams you can take." 
                        }
                    )
                    
                    Tab(
                        selected = selectedTab == StudentDashboardTab.EXAM_HISTORY,
                        onClick = { 
                            selectedTab = StudentDashboardTab.EXAM_HISTORY
                            speak(textToSpeech, "Exam history tab. Showing exams you have completed.")
                        },
                        text = { Text("Exam History") },
                        icon = { Icon(imageVector = Icons.Default.History, contentDescription = null) },
                        modifier = Modifier.semantics { 
                            contentDescription = "Switch to exam history tab. Double tap to view your completed exams." 
                        }
                    )
                }
                
                // Content based on selected tab
                when (selectedTab) {
                    StudentDashboardTab.AVAILABLE_EXAMS -> {
                        // Available exams content
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                            } else if (error != null) {
                                ErrorMessage(
                                    errorMessage = error!!,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            } else if (exams.isEmpty()) {
                                EmptyStateMessage(
                                    message = "No available exams",
                                    description = "Check back later for new exams assigned to your class.",
                                    icon = Icons.Default.AssignmentLate,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    item {
                                        Text(
                                            text = "Available Exams (${exams.size})",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .padding(bottom = 16.dp)
                                                .semantics {
                                                    contentDescription = "Available exams section. You have ${exams.size} exams available to take."
                                                }
                                        )
                                    }
                                    
                                    items(exams) { exam ->
                                        ExamCard(
                                            exam = exam,
                                            onClick = {
                                                hapticFeedback.performHapticFeedback(
                                                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                                                )
                                                speak(textToSpeech, "Starting exam: ${exam.title}. Security measures are being activated. Good luck!")
                                                
                                                // Launch exam in secure mode immediately when Start Exam is pressed
                                                if (context is Activity) {
                                                    securityManager.launchSecureExam(
                                                        activity = context,
                                                        examId = exam.id,
                                                        examTitle = exam.title,
                                                        duration = exam.duration,
                                                        examType = exam.type,
                                                        examResultLauncher = examResultLauncher
                                                    )
                                                } else {
                                                    // Fallback to the callback if context is not an activity
                                                onExamSelected(exam)
                                                }
                                            },
                                            textToSpeech = textToSpeech
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                    }
                                }
                            }
                        }
                    }
                    
                    StudentDashboardTab.EXAM_HISTORY -> {
                        // Exam history content
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                            } else if (examHistory.isEmpty()) {
                                EmptyStateMessage(
                                    message = "No exam history",
                                    description = "You haven't taken any exams yet. When you complete exams, your results will appear here.",
                                    icon = Icons.Default.History,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    item {
                                        Text(
                                            text = "Exam History (${examHistory.size})",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .padding(bottom = 16.dp)
                                                .semantics {
                                                    contentDescription = "Exam history section. You have completed ${examHistory.size} exams."
                                                }
                                        )
                                    }
                                    
                                    items(examHistory) { record ->
                                        ExamHistoryCard(
                                            record = record,
                                            onClick = {
                                                selectedExamRecord = record
                                                showQuestionDetailsDialog = true
                                                speak(textToSpeech, "Opening exam details for ${record.title}.")
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
        }
    }
    
    // Question details dialog
    if (showQuestionDetailsDialog && selectedExamRecord != null) {
        ExamDetailsDialog(
            record = selectedExamRecord!!,
            onDismiss = { 
                showQuestionDetailsDialog = false
                speak(textToSpeech, "Closing exam details.")
            }
        )
    }
}

@Composable
fun ExamHistoryCard(
    record: StudentExamRecord,
    onClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val scorePercentage = (record.score * 100f / record.totalPoints).toInt()
    
    val scoreColor = when {
        scorePercentage >= 80 -> MaterialTheme.colorScheme.primary
        scorePercentage >= 60 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    
    // Calculate skipped questions
    val skippedQuestions = record.totalQuestions - record.answered
    val hasSkippedQuestions = skippedQuestions > 0
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with title and date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = record.subject,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = dateFormat.format(record.date),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                // Score badge
                Surface(
                        shape = RoundedCornerShape(12.dp),
                    color = scoreColor.copy(alpha = 0.1f),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = "$scorePercentage%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = scoreColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Score
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Score",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${record.score}/${record.totalPoints}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = scoreColor
                        )
                }
                
                // Answered questions
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Answered",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${record.answered}/${record.totalQuestions}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                }
                
                // Skipped questions (if any)
                if (hasSkippedQuestions) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Skipped",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Skipped questions",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$skippedQuestions",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
            
            // Performance indicator
            if (hasSkippedQuestions) {
            Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp)
            ) {
                Icon(
                        imageVector = Icons.Default.Info,
                    contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(16.dp)
                )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (skippedQuestions == 1) {
                            "1 question was left unanswered"
                        } else {
                            "$skippedQuestions questions were left unanswered"
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            
            // Tap to view details indicator
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tap to view detailed results",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
fun ExamDetailsDialog(
    record: StudentExamRecord,
    onDismiss: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
    val formattedDate = dateFormat.format(record.date)
    val scorePercentage = (record.score * 100f / record.totalPoints).toInt()
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = record.title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = record.subject,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Completed on $formattedDate",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    
                    // Score circle
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    scorePercentage >= 80 -> MaterialTheme.colorScheme.primary
                                    scorePercentage >= 60 -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.error
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$scorePercentage%",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                
                Divider(modifier = Modifier.padding(vertical = 16.dp))
                
                // Score details
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Score",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${record.score}/${record.totalPoints}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Answered",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${record.answered}/${record.totalQuestions}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                // Question details title
                Text(
                    text = "Question Details",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // Question list
                if (record.questionDetails.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No question details available",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        itemsIndexed(record.questionDetails) { index, question ->
                            QuestionDetailItem(
                                question = question,
                                index = index
                            )
                            
                            if (index < record.questionDetails.size - 1) {
                                Divider(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                )
                            }
                        }
                    }
                }
                
                // Close button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun QuestionDetailItem(
    question: QuestionAttempt,
    index: Int
) {
    val isSkipped = question.studentAnswer.isNullOrBlank()
    
    Column(modifier = Modifier.fillMaxWidth()) {
        // Question number and text
        Row(verticalAlignment = Alignment.Top) {
            // Question number with status indicator
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isSkipped -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            question.isCorrect -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.error
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSkipped) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Skipped",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                Text(
                    text = "${index + 1}",
                    color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Question text and details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = question.questionText,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                if (isSkipped) {
                    // Skipped question display
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Question was skipped",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = "Correct answer: ${question.correctAnswer}",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    // Answered question display
                if (question.type == "MULTIPLE_CHOICE" && !question.options.isNullOrEmpty()) {
                    question.options?.forEachIndexed { optIndex, option ->
                        val isSelected = question.studentAnswer == option
                        val isCorrect = question.correctAnswer == option
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                                val optionLetter = ('A' + optIndex).toString()
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                    isSelected && isCorrect -> MaterialTheme.colorScheme.primary
                                    isSelected && !isCorrect -> MaterialTheme.colorScheme.error
                                                !isSelected && isCorrect -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                                else -> MaterialTheme.colorScheme.surfaceVariant
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = optionLetter,
                                        color = if (isSelected || isCorrect) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(12.dp))
                            
                            Text(
                                text = option,
                                    fontSize = 14.sp,
                                color = when {
                                    isSelected && isCorrect -> MaterialTheme.colorScheme.primary
                                    isSelected && !isCorrect -> MaterialTheme.colorScheme.error
                                        !isSelected && isCorrect -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurface
                                    },
                                    fontWeight = if (isSelected || isCorrect) FontWeight.Medium else FontWeight.Normal
                                )
                                
                                Spacer(modifier = Modifier.weight(1f))
                                
                                // Status indicators
                                if (isSelected) {
                                    Icon(
                                        imageVector = if (isCorrect) Icons.Default.Check else Icons.Default.Close,
                                        contentDescription = if (isCorrect) "Your correct answer" else "Your incorrect answer",
                                        tint = if (isCorrect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else if (isCorrect) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Correct answer",
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                        }
                    }
                } else {
                    // Other question types (short answer, etc.)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        // Your answer
                        Text(
                            text = "Your answer:",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (question.isCorrect) 
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                else 
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                        Text(
                            text = question.studentAnswer ?: "No answer provided",
                                    fontSize = 14.sp,
                                    color = if (question.isCorrect) 
                                        MaterialTheme.colorScheme.onPrimaryContainer 
                                    else 
                                        MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        
                        // Correct answer if student got it wrong
                        if (!question.isCorrect) {
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = "Correct answer:",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                            Text(
                                text = question.correctAnswer,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Points section
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = when {
                            isSkipped -> MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                            question.isCorrect -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                                imageVector = when {
                                    isSkipped -> Icons.Default.Remove
                                    question.isCorrect -> Icons.Default.Check
                                    else -> Icons.Default.Close
                                },
                        contentDescription = null,
                                tint = when {
                                    isSkipped -> MaterialTheme.colorScheme.onSurfaceVariant
                                    question.isCorrect -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.error
                                },
                        modifier = Modifier.size(16.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    Text(
                                text = when {
                                    isSkipped -> "0 / ${question.points} pts (skipped)"
                                    question.isCorrect -> "${question.points} / ${question.points} pts"
                                    else -> "0 / ${question.points} pts"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = when {
                                    isSkipped -> MaterialTheme.colorScheme.onSurfaceVariant
                                    question.isCorrect -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.error
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateMessage(
    message: String,
    description: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .padding(bottom = 16.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        
        Text(
            text = message,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = description,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private fun initializeTextToSpeech(context: Context): TextToSpeech {
    var textToSpeech: TextToSpeech? = null
    textToSpeech = TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) {
            val result = TextToSpeech.LANG_AVAILABLE
            // Set speaking rate slightly slower for better comprehension
            textToSpeech?.setSpeechRate(0.85f)
            // Increase pitch slightly for better clarity
            textToSpeech?.setPitch(1.05f)
        }
    }
    return textToSpeech
}

private fun speak(textToSpeech: TextToSpeech, text: String) {
    val utteranceId = UUID.randomUUID().toString()
    textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
}

// Placeholder for missing components
@Composable
fun ErrorMessage(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Error") },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

// Simple error message without a dialog
@Composable
fun ErrorMessage(
    errorMessage: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .padding(bottom = 16.dp),
            tint = MaterialTheme.colorScheme.error
        )
        
        Text(
            text = "Error",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.error
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = errorMessage,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// Make ExamCard public instead of private
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamCard(
    exam: Exam,
    onClick: () -> Unit,
    textToSpeech: TextToSpeech? = null
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    
    Card(
        onClick = { onClick() },
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Exam: ${exam.title}, subject: ${exam.subject}, " +
                        "${exam.questionCount} questions, ${exam.duration} minutes"
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with title and type icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Title and subject
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = exam.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Text(
                        text = exam.subject,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                // Exam type icon with visual indicator
                ExamTypeIndicator(type = exam.type)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Exam details row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Questions
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Quiz,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    Text(
                        text = "${exam.questionCount} Questions",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Duration
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    Text(
                        text = "${exam.duration} Minutes",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Date
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    Text(
                        text = dateFormat.format(exam.date),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Start button
            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .semantics { contentDescription = "Start exam ${exam.title}" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = "Start Exam",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// Add ExamTypeIndicator function
@Composable
fun ExamTypeIndicator(type: String) {
    val (icon, color, label) = when (type) {
        "MULTIPLE_CHOICE" -> Triple(
            Icons.Default.CheckCircle,
            MaterialTheme.colorScheme.primary,
            "Multiple Choice"
        )
        "TRUE_FALSE" -> Triple(
            Icons.Default.ToggleOn,
            MaterialTheme.colorScheme.tertiary,
            "True/False"
        )
        "SHORT_ANSWER" -> Triple(
            Icons.Default.TextFields,
            MaterialTheme.colorScheme.secondary,
            "Short Answer"
        )
        "FILL_IN_THE_BLANK" -> Triple(
            Icons.Default.Create, 
            MaterialTheme.colorScheme.error,
            "Fill in the Blank"
        )
        "MATCHING" -> Triple(
            Icons.Default.CompareArrows,
            MaterialTheme.colorScheme.errorContainer,
            "Matching"
        )
        "ESSAY" -> Triple(
            Icons.Default.Description,
            MaterialTheme.colorScheme.primaryContainer,
            "Essay"
        )
        "MIXED" -> Triple(
            Icons.Default.Diversity3,
            MaterialTheme.colorScheme.inversePrimary,
            "Mixed Types"
        )
        else -> Triple(
            Icons.Default.HelpOutline,
            MaterialTheme.colorScheme.outline,
            "Unknown"
        )
    }
    
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.1f),
        modifier = Modifier.semantics { contentDescription = "Exam type: $label" }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            
            Spacer(modifier = Modifier.width(4.dp))
            
            Text(
                text = label,
                fontSize = 12.sp,
                color = color
            )
        }
    }
} 