package com.example.ablindexaminer.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.clickable
import androidx.navigation.NavController
import com.example.ablindexaminer.data.firebase.FirebaseAuthService
import com.example.ablindexaminer.data.firebase.FirebaseExamService
import com.example.ablindexaminer.data.firebase.HybridExamService
import com.example.ablindexaminer.data.model.Question as ModelQuestion
import kotlinx.coroutines.*
import com.example.ablindexaminer.data.model.TeacherExam
import com.example.ablindexaminer.ui.components.AppBarWithDrawer
import com.example.ablindexaminer.ui.components.FlowRow
import com.example.ablindexaminer.data.WordDocumentParser
import com.example.ablindexaminer.data.DocumentParsingResult
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.outlined.Search
import kotlin.time.ExperimentalTime

// Add experimental annotation to enable unit conversions
@OptIn(kotlin.ExperimentalStdlibApi::class)

// Enum for question types
enum class QuestionType(val displayName: String) {
    MULTIPLE_CHOICE("Multiple Choice"),
    TRUE_FALSE("True/False"),
    SHORT_ANSWER("Short Answer"),
    FILL_IN_THE_BLANK("Fill in the Blank"),
    MATCHING("Matching"),
    ESSAY("Essay")
}

// Main exam data model
data class TeacherExam(
    val id: String,
    val title: String,
    val subject: String,
    val duration: Int,
    val questionCount: Int,
    val date: Date,
    val published: Boolean,
    val targetYear: String = "ALL",
    val targetSection: String = "ALL",
    val questions: List<ExamQuestion> = emptyList(),
    val scheduledPublishDate: Date? = null
)

// More detailed question model for teacher use
data class ExamQuestion(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val type: QuestionType,
    val options: List<String> = emptyList(),
    val correctAnswer: String,
    val points: Int = 1,
    val number: Int = 0 // Added for sequential ordering
)

// Data model for dashboard statistics
data class ExamStatistics(
    val totalExams: Int,
    val publishedExams: Int,
    val totalQuestions: Int,
    val averageScore: Float,
    val examsByType: Map<QuestionType, Int>
)

// Dashboard tab options
enum class DashboardTab {
    MY_EXAMS, CREATE_EXAM, STATISTICS, STUDENT_RESULTS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherDashboardScreen(
    username: String,
    onLogout: () -> Unit,
    navController: NavController
) {
    val context = LocalContext.current
    var exams by remember { mutableStateOf<List<TeacherExam>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf(DashboardTab.MY_EXAMS) }
    var teacherName by remember { mutableStateOf("") }
    
    // Variables for exam management
    var showCreateExamDialog by remember { mutableStateOf(false) }
    var showQuestionsList by remember { mutableStateOf(false) }
    var isLoadingQuestions by remember { mutableStateOf(false) }
    var questionsForExam by remember { mutableStateOf<List<com.example.ablindexaminer.ui.screens.ExamQuestion>>(emptyList()) }
    var selectedExamId by remember { mutableStateOf("") }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var examToDelete by remember { mutableStateOf("") }
    
    // Text-to-speech for accessibility
    val textToSpeech = remember { initializeTextToSpeech(context) }
    
    // Initialize Firebase services
    val authService = remember { FirebaseAuthService() }
    val examService = remember { FirebaseExamService() }
    val hybridExamService = remember { HybridExamService() }
    val coroutineScope = rememberCoroutineScope()
    
    // Function to refresh the exams list
    val refreshExams = {
        coroutineScope.launch {
            try {
                val userId = authService.getCurrentUserId()
                if (userId != null) {
                    isLoading = true
                    hybridExamService.getTeacherExams(userId).collect { examList ->
                        exams = examList
                        isLoading = false
                        Log.d("TeacherDashboard", "Refreshed exams list, found ${examList.size} exams")
                    }
                }
            } catch (e: Exception) {
                Log.e("TeacherDashboard", "Error refreshing exams", e)
                isLoading = false
            }
        }
    }
    
    // Load exams from Firebase
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            try {
                val userId = authService.getCurrentUserId()

                if (userId != null) {
                    // Get teacher's information
                    val userDataResult = authService.getUserData(userId)
                    if (userDataResult.isSuccess) {
                        val userData = userDataResult.getOrNull()
                        if (userData != null) {
                            teacherName = userData["fullName"] as? String ?: username
                        }
                    }
                    
                    // Get teacher's exams from Firebase
                    hybridExamService.getTeacherExams(userId).collect { examList ->
                        exams = examList
                        isLoading = false
                    }
                    
                    speak(
                        textToSpeech,
                        "Welcome $teacherName to the Teacher Dashboard. Here you can manage your exams."
                    )
                } else {
                    // Handle unauthenticated user
                    isLoading = false
                    // In a real app, you might want to redirect to login
                }
            } catch (e: Exception) {
                // Handle error
                isLoading = false
            }
        }
    }

    // When returning from an exam, refresh the data
    BackHandler {
        refreshExams()
        navController.popBackStack()
    }
    
    // State for schedule dialog
    var showScheduleDialog by remember { mutableStateOf(false) }
    
    AppBarWithDrawer(
        title = "Teacher Dashboard",
        navController = navController,
        username = teacherName.ifEmpty { username },
        userRole = "Teacher",
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
                    text = "Welcome, ${teacherName.ifEmpty { username }}",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics {
                        contentDescription = "Welcome, ${teacherName.ifEmpty { username }}"
                    }
                )

                Text(
                    text = "Create and manage exams for your students",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics {
                        contentDescription = "Create and manage exams for your students"
                    }
                )
            }

            // Tab navigation
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                TabOption(
                    icon = Icons.Outlined.Collections,
                    title = "My Exams",
                    isSelected = selectedTab == DashboardTab.MY_EXAMS,
                    onClick = { selectedTab = DashboardTab.MY_EXAMS }
                )

                TabOption(
                    icon = Icons.Outlined.Add,
                    title = "Create Exam",
                    isSelected = selectedTab == DashboardTab.CREATE_EXAM,
                    onClick = { selectedTab = DashboardTab.CREATE_EXAM }
                )

                TabOption(
                    icon = Icons.Outlined.BarChart,
                    title = "Statistics",
                    isSelected = selectedTab == DashboardTab.STATISTICS,
                    onClick = { selectedTab = DashboardTab.STATISTICS }
                )

                TabOption(
                    icon = Icons.Outlined.Groups,
                    title = "Student Results",
                    isSelected = selectedTab == DashboardTab.STUDENT_RESULTS,
                    onClick = { selectedTab = DashboardTab.STUDENT_RESULTS }
                )
            }

            // Content based on selected tab
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    DashboardTab.MY_EXAMS -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            } else if (exams.isEmpty()) {
                                Column(
                                    modifier = Modifier.align(Alignment.Center),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Inbox,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    )
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    Text(
                                        text = "No exams yet",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    Text(
                                        text = "Create an exam to get started",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp)
                                ) {
                                    items(exams) { exam ->
                                        ExamItem(
                                            exam = exam,
                                            onEdit = { 
                                                // Navigate to edit exam screen
                                                navController.navigate("editExam/${exam.id}")
                                            },
                                            onViewQuestions = { 
                                                coroutineScope.launch {
                                                    isLoadingQuestions = true
                                                    try {
                                                        val result = hybridExamService.getQuestionsForExam(exam.id)
                                                        if (result.isSuccess) {
                                                            questionsForExam = result.getOrDefault(emptyList())
                                                            selectedExamId = exam.id
                                                            showQuestionsList = true
                                                        } else {
                                                            speak(textToSpeech, "Failed to load questions")
                                                        }
                                                    } catch (e: Exception) {
                                                        speak(textToSpeech, "Error: ${e.message}")
                                                    }
                                                    isLoadingQuestions = false
                                                }
                                            },
                                            onDelete = { 
                                                examToDelete = exam.id
                                                showDeleteConfirmation = true
                                            },
                                            onPublishToggle = { examId, isPublished ->
                                                coroutineScope.launch {
                                                    try {
                                                        val result = if (isPublished) {
                                                            hybridExamService.publishExam(examId)
                                                        } else {
                                                            hybridExamService.unpublishExam(examId)
                                                        }
                                                        
                                                        if (result.isSuccess) {
                                                            val message = if (isPublished) "Exam published" else "Exam unpublished"
                                                            speak(textToSpeech, message)
                                                            // Refresh the exams list after publishing/unpublishing
                                                            refreshExams()
                                                        } else {
                                                            val action = if (isPublished) "publish" else "unpublish"
                                                            speak(textToSpeech, "Failed to $action exam")
                                                        }
                                                    } catch (e: Exception) {
                                                        speak(textToSpeech, "Error: ${e.message}")
                                                    }
                                                }
                                            },
                                            onSchedulePublication = { 
                                                selectedExamId = exam.id
                                                showScheduleDialog = true
                                                // The refresh happens in the dialog's onDismiss callback
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                    }
                                }
                            }
                            
                            // FAB for creating a new exam
                            FloatingActionButton(
                                onClick = { showCreateExamDialog = true },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(16.dp),
                                containerColor = MaterialTheme.colorScheme.primary
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Create new exam",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }

                    DashboardTab.CREATE_EXAM -> {
                        CreateExamTab(
                            textToSpeech = textToSpeech,
                            onExamCreated = { exam, questions ->
                                onExamCreated(exam, questions)
                                // Refresh the exams list after creating a new exam
                                refreshExams()
                                // Show a success message to the user
                                coroutineScope.launch {
                                    delay(1000) // Brief delay to allow database updates to process
                                    speak(textToSpeech, "Exam ${exam.title} created successfully with ${questions.size} questions.")
                                }
                            }
                        )
                    }

                    DashboardTab.STATISTICS -> {
                        StatisticsTab(
                            exams = exams,
                            isLoading = isLoading,
                            textToSpeech = textToSpeech
                        )
                    }

                    DashboardTab.STUDENT_RESULTS -> {
                        StudentResultsTab(
                            exams = exams,
                            isLoading = isLoading,
                            textToSpeech = textToSpeech
                        )
                    }
                }
            }
        }
    }
    
    // Create exam dialog
    if (showCreateExamDialog) {
        AddExamDialog(
            onDismiss = { showCreateExamDialog = false },
            onExamAdded = { exam ->
                // Create exam in Firebase
                coroutineScope.launch {
                    try {
                        val userId = authService.getCurrentUserId()
                        if (userId != null) {
                            val createResult = hybridExamService.createExam(
                                exam.copy(teacherId = userId),
                                userId
                            )
                            if (createResult.isSuccess) {
                                // Refresh the exams list
                                refreshExams()
                                speak(textToSpeech, "Exam created successfully")
                            } else {
                                speak(textToSpeech, "Failed to create exam")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("TeacherDashboard", "Error creating exam", e)
                        speak(textToSpeech, "Error creating exam: ${e.message}")
                    }
                }
                showCreateExamDialog = false
            }
        )
    }
    
    // Questions list dialog
    if (showQuestionsList) {
        QuestionsListDialog(
            examId = selectedExamId,
            questions = questionsForExam,
            isLoading = isLoadingQuestions,
            onDismiss = { showQuestionsList = false },
            onEditQuestion = { questionId ->
                // Handle question edit (could navigate to question edit screen)
                Log.d("TeacherDashboard", "Edit question: $questionId")
            }
        )
    }
    
    // Delete confirmation dialog
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Exam") },
            text = { Text("Are you sure you want to delete this exam? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                val result = hybridExamService.deleteExam(examToDelete)
                                if (result.isSuccess) {
                                    speak(textToSpeech, "Exam deleted successfully")
                                    refreshExams()
                                } else {
                                    speak(textToSpeech, "Failed to delete exam")
                                }
                            } catch (e: Exception) {
                                Log.e("TeacherDashboard", "Error deleting exam", e)
                                speak(textToSpeech, "Error: ${e.message}")
                            }
                        }
                        showDeleteConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun TabOption(
    icon: ImageVector,
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Tab(
        selected = isSelected,
        onClick = onClick,
        text = { Text(text = title, fontSize = 14.sp) },
        icon = { Icon(imageVector = icon, contentDescription = null) },
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun ExamsTab(
    exams: List<TeacherExam>,
    isLoading: Boolean,
    onEditExam: (String) -> Unit,
    onTogglePublish: (String, Boolean) -> Unit,
    onDeleteExam: (String) -> Unit,
    onSchedulePublication: (String) -> Unit = {},
    onViewQuestions: (String) -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (isLoading) {
            Column(
                modifier = Modifier.align(Alignment.Center),
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
        } else if (exams.isEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.LibraryBooks,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No exams yet",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Use the Create Exam tab to get started",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn {
                items(exams) { exam ->
                    ExamItem(
                        exam = exam,
                        onEdit = { onEditExam(exam.id) },
                        onViewQuestions = { onViewQuestions(exam.id) },
                        onDelete = { onDeleteExam(exam.id) },
                        onPublishToggle = { examId, isPublished -> onTogglePublish(examId, isPublished) },
                        onSchedulePublication = { onSchedulePublication(exam.id) }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExamItem(
    exam: TeacherExam,
    onEdit: (String) -> Unit,
    onViewQuestions: (String) -> Unit,
    onDelete: (String) -> Unit,
    onPublishToggle: (String, Boolean) -> Unit,
    onSchedulePublication: (String) -> Unit
) {
    var showScheduleDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val hybridExamService = remember { HybridExamService() }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // First row: Title and publish status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Title and subject
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = exam.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Text(
                        text = exam.subject,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Publication status chip
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (exam.published) 
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    else if (exam.scheduledPublishDate != null)
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)
                    else
                        MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (exam.published) 
                                Icons.Default.Visibility
                            else if (exam.scheduledPublishDate != null)
                                Icons.Default.Schedule
                            else
                                Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = if (exam.published) 
                                MaterialTheme.colorScheme.primary
                            else if (exam.scheduledPublishDate != null)
                                MaterialTheme.colorScheme.tertiary
                            else
                                MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(4.dp))
                        
                        Text(
                            text = if (exam.published) 
                                "Published"
                            else if (exam.scheduledPublishDate != null) {
                                val dateFormat = SimpleDateFormat("MMM dd 'at' hh:mm a", Locale.getDefault())
                                "Scheduled: ${dateFormat.format(exam.scheduledPublishDate)}"
                            } else
                                "Not Published",
                            fontSize = 12.sp,
                            color = if (exam.published) 
                                MaterialTheme.colorScheme.primary
                            else if (exam.scheduledPublishDate != null)
                                MaterialTheme.colorScheme.tertiary
                            else
                                MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            // Second row: Exam details
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Questions count
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.QuestionAnswer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    Text(
                        text = "${exam.questionCount} Questions",
                        fontSize = 14.sp
                    )
                }
                
                // Duration
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    Text(
                        text = "${exam.duration} Minutes",
                        fontSize = 14.sp
                    )
                }
                
                // Target class
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    Text(
                        text = "Year ${exam.targetYear}${if (exam.targetSection != "ALL") ", Section ${exam.targetSection}" else ""}",
                        fontSize = 14.sp
                    )
                }
            }
            
            // Third row: Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Edit button
                OutlinedButton(
                    onClick = { onEdit(exam.id) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit exam",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit")
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // View questions button
                OutlinedButton(
                    onClick = { onViewQuestions(exam.id) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = "View questions",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Questions")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Fourth row: Additional actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Schedule button
                IconButton(
                    onClick = { showScheduleDialog = true },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = "Schedule exam publication",
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                // Publish toggle button
                IconButton(
                    onClick = { onPublishToggle(exam.id, !exam.published) },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (exam.published) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                        )
                ) {
                    Icon(
                        imageVector = if (exam.published) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (exam.published) "Unpublish exam" else "Publish exam",
                        tint = if (exam.published) MaterialTheme.colorScheme.primary
                              else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                // Delete button 
                IconButton(
                    onClick = { onDelete(exam.id) },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete exam",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
    
    // Schedule dialog
    if (showScheduleDialog) {
        SchedulePublishDialog(
            examId = exam.id,
            examTitle = exam.title,
            currentScheduledDate = exam.scheduledPublishDate,
            onDismiss = { 
                showScheduleDialog = false
                onSchedulePublication(exam.id) // Trigger refresh after scheduling
            }
        )
    }
}

@Composable
private fun SchedulePublishDialog(
    examId: String,
    examTitle: String,
    currentScheduledDate: Date?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val hybridExamService = remember { HybridExamService() }
    
    // Format for display
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    
    // Current date and time for initial values
    val currentDate = remember { Calendar.getInstance() }
    
    // If there's already a scheduled date, use it
    if (currentScheduledDate != null) {
        currentDate.time = currentScheduledDate
    } else {
        // Otherwise set to 1 day in the future by default
        currentDate.add(Calendar.DAY_OF_MONTH, 1)
    }
    
    // State for selected date and time
    var selectedDate by remember { mutableStateOf(currentDate.time) }
    var selectedTime by remember { mutableStateOf(timeFormat.format(currentDate.time)) }
    var datePickerExpanded by remember { mutableStateOf(false) }
    var timePickerExpanded by remember { mutableStateOf(false) }
    
    // State for schedule status
    var isScheduling by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    
    // Function to apply time to the selected date
    fun applyTimeToDate(date: Date, timeStr: String): Date {
        try {
            val calendar = Calendar.getInstance()
            calendar.time = date
            
            val timeCal = Calendar.getInstance()
            timeCal.time = timeFormat.parse(timeStr) ?: return date
            
            calendar.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
            calendar.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
            calendar.set(Calendar.SECOND, 0)
            
            return calendar.time
        } catch (e: Exception) {
            Log.e("ScheduleDialog", "Error parsing time", e)
            return date
        }
    }
    
    // Function to schedule the exam
    fun scheduleExam() {
        val scheduledDateTime = applyTimeToDate(selectedDate, selectedTime)
        
        // Ensure the scheduled time is in the future
        if (scheduledDateTime.before(Date())) {
            errorMessage = "Scheduled time must be in the future"
            return
        }
        
        isScheduling = true
        errorMessage = null
        
        coroutineScope.launch {
            try {
                val result = hybridExamService.scheduleExamPublication(examId, scheduledDateTime)
                isScheduling = false
                
                if (result.isSuccess) {
                    successMessage = "Exam scheduled for ${dateFormat.format(scheduledDateTime)} at ${timeFormat.format(scheduledDateTime)}"
                    // Auto-dismiss after showing success message
                    delay(1500)
                    onDismiss()
                } else {
                    errorMessage = "Failed to schedule exam: ${result.exceptionOrNull()?.message ?: "Unknown error"}"
                }
            } catch (e: Exception) {
                isScheduling = false
                errorMessage = "Error: ${e.message}"
            }
        }
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title
                Text(
                    text = "Schedule Exam Publication",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Exam title
                Text(
                    text = examTitle,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Date selector
                OutlinedButton(
                    onClick = { datePickerExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Date: ${dateFormat.format(selectedDate)}",
                        fontSize = 16.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Time selector
                OutlinedButton(
                    onClick = { timePickerExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Time: $selectedTime",
                        fontSize = 16.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Instructions
                Text(
                    text = "The exam will automatically be published at the scheduled date and time.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Error message
                errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
                
                // Success message
                successMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
                
                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    
                    Button(
                        onClick = { scheduleExam() },
                        modifier = Modifier.weight(1f),
                        enabled = !isScheduling
                    ) {
                        if (isScheduling) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Schedule")
                        }
                    }
                }
            }
        }
    }
    
    // Date picker dialog
    if (datePickerExpanded) {
        val datePickerDialog = android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val newDate = Calendar.getInstance()
                newDate.set(year, month, dayOfMonth)
                selectedDate = newDate.time
                datePickerExpanded = false
            },
            currentDate.get(Calendar.YEAR),
            currentDate.get(Calendar.MONTH),
            currentDate.get(Calendar.DAY_OF_MONTH)
        )
        
        // Set minimum date to current date
        datePickerDialog.datePicker.minDate = System.currentTimeMillis()
        
        // Show the date picker
        datePickerDialog.show()
    }
    
    // Time picker dialog
    if (timePickerExpanded) {
        val timePickerDialog = android.app.TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val newTime = Calendar.getInstance()
                newTime.set(Calendar.HOUR_OF_DAY, hourOfDay)
                newTime.set(Calendar.MINUTE, minute)
                selectedTime = timeFormat.format(newTime.time)
                timePickerExpanded = false
            },
            currentDate.get(Calendar.HOUR_OF_DAY),
            currentDate.get(Calendar.MINUTE),
            false
        )
        
        // Show the time picker
        timePickerDialog.show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExamDialog(
    onDismiss: () -> Unit,
    onExamAdded: (TeacherExam) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("60") }
    var questionCount by remember { mutableStateOf("10") }
    var examDate by remember { mutableStateOf(Date()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var targetYear by remember { mutableStateOf("ALL") }
    var targetSection by remember { mutableStateOf("ALL") }
    
    // State for dropdown menus
    var yearExpanded by remember { mutableStateOf(false) }
    var sectionExpanded by remember { mutableStateOf(false) }
    
    // Options for year and section
    val yearOptions = listOf("ALL", "1", "2", "3", "4", "5")
    val sectionOptions = listOf("ALL", "A", "B", "C", "D", "E", "F")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Exam") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Exam Title") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )

                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )

                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it },
                    label = { Text("Duration (minutes)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )

                OutlinedTextField(
                    value = questionCount,
                    onValueChange = { questionCount = it },
                    label = { Text("Number of Questions") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )

                // Date picker
                OutlinedTextField(
                    value = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(examDate),
                    onValueChange = { },
                    label = { Text("Exam Date") },
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Filled.ArrowDropDown, "Select date")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )

                // Target year selector
                Text(
                    text = "Target Year",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                // Replace dropdown with filter chips
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalSpacing = 8.dp
                ) {
                    yearOptions.forEach { option ->
                        FilterChip(
                            selected = targetYear == option,
                            onClick = { targetYear = option },
                            label = { Text(if (option == "ALL") "All Years" else "Year $option") }
                        )
                    }
                }
                
                // Target section selector
                Text(
                    text = "Target Section",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                // Replace dropdown with filter chips
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalSpacing = 8.dp
                ) {
                    sectionOptions.forEach { option ->
                        FilterChip(
                            selected = targetSection == option,
                            onClick = { targetSection = option },
                            label = { Text(if (option == "ALL") "All Sections" else "Section $option") }
                        )
                    }
                }

                if (showDatePicker) {
                    // TODO: Implement date picker here. For now, we'll just close it
                    showDatePicker = false
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // Create exam with the input data
                    val newExam = TeacherExam(
                        id = UUID.randomUUID()
                            .toString(), // In real app, this would be from Firebase
                        title = title,
                        subject = subject,
                        duration = duration.toIntOrNull() ?: 60,
                        questionCount = questionCount.toIntOrNull() ?: 10,
                        date = examDate,
                        published = false, // New exams start as unpublished
                        targetYear = targetYear,
                        targetSection = targetSection
                    )
                    onExamAdded(newExam)
                },
                enabled = title.isNotBlank() && subject.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatDate(date: Date): String {
    val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
    return dateFormat.format(date)
}

private fun initializeTextToSpeech(context: Context): TextToSpeech {
    return TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) {
            TextToSpeech.LANG_AVAILABLE
        }
    }
}

private fun speak(textToSpeech: TextToSpeech, text: String) {
    val utteranceId = UUID.randomUUID().toString()
    textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
}

// Enhanced Word document processing with better error handling and feedback
private suspend fun processWordDocument(
    context: Context,
    uri: Uri,
    onComplete: (List<ExamQuestion>) -> Unit,
    onError: (String) -> Unit
) {
    Log.d("TeacherDashboard", "Starting Word document validation for URI: $uri")
    
    // First validate the document format
    val (isValid, validationMessage) = WordDocumentParser.validateDocument(context, uri)
    if (!isValid) {
        Log.w("TeacherDashboard", "Document validation failed: $validationMessage")
        onError(validationMessage)
        onComplete(emptyList())
        return
    }
    
    Log.d("TeacherDashboard", "Document validation successful, starting parsing")
    
    try {
        // Parse the document using our improved WordDocumentParser
        val result = WordDocumentParser.parseDocument(context, uri)
        
        if (!result.success || result.questions.isEmpty()) {
            // Parsing failed or no questions found
            val errorMsg = result.errorMessage ?: "Failed to extract any questions from the document."
            Log.e("TeacherDashboard", "Document parsing error: $errorMsg (${result.errorType})")
            
            // Provide helpful debugging information
            result.documentInfo?.let { info ->
                Log.d("TeacherDashboard", "Document stats: ${info.fileName}, size: ${info.fileSize ?: "unknown"} bytes, " +
                    "${info.paragraphCount} paragraphs, ~${info.wordCount} words")
            }
            
            // Show error to user with guidance
            val userMessage = when (result.errorType) {
                DocumentParsingResult.ErrorType.FILE_NOT_FOUND -> 
                    "The document file could not be opened. It may be inaccessible or corrupted."
                
                DocumentParsingResult.ErrorType.EMPTY_DOCUMENT ->
                    "The document appears to be empty. Please ensure it contains properly formatted exam questions."
                
                DocumentParsingResult.ErrorType.INVALID_FORMAT ->
                    "Invalid document format. Please ensure you're uploading a valid .docx file."
                
                DocumentParsingResult.ErrorType.PARSING_ERROR -> {
                    val guideMsg = "\n\nWould you like to see a guide on how to format your document properly?"
                    result.errorMessage + guideMsg
                }
                
                DocumentParsingResult.ErrorType.ACCESS_DENIED ->
                    "Permission denied. The app doesn't have access to read this file."
                
                else -> result.errorMessage ?: "An unknown error occurred while processing the document."
            }
            
            onError(userMessage)
            onComplete(emptyList())
            return
        }
        
        // Successful parsing
        val questions = result.questions
        val docInfo = result.documentInfo
        
        // Log success details
        Log.d("TeacherDashboard", "Successfully parsed ${questions.size} questions from document")
        if (docInfo != null) {
            Log.d("TeacherDashboard", "Document stats: ${docInfo.fileName}, size: ${docInfo.fileSize ?: "unknown"} bytes, " +
                  "${docInfo.paragraphCount} paragraphs, ~${docInfo.wordCount} words")
        }
        
        // Log question details
        val questionTypes = questions.groupBy { it.type }.mapValues { it.value.size }
        Log.d("TeacherDashboard", "Question types: ${questionTypes.map { "${it.key.name}=${it.value}" }.joinToString(", ")}")
        
        questions.forEachIndexed { index, question ->
            Log.d("TeacherDashboard", "Question ${index + 1}: Type=${question.type}, " +
                  "Text=${question.text.take(50)}${if (question.text.length > 50) "..." else ""}")
            Log.d("TeacherDashboard", "  Options: ${question.options.size}, " +
                  "Answer: ${question.correctAnswer.take(20)}${if (question.correctAnswer.length > 20) "..." else ""}")
        }
        
        // Callback with the parsed questions
        onComplete(questions)
    } catch (e: Exception) {
        Log.e("TeacherDashboard", "Unexpected error processing Word document", e)
        onError("An unexpected error occurred: ${e.message ?: "Unknown error"}")
        onComplete(emptyList())
    }
}

@Composable
private fun CreateExamTab(
    textToSpeech: TextToSpeech,
    onExamCreated: (com.example.ablindexaminer.data.model.TeacherExam, List<ExamQuestion>) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("60") }
    var targetYear by remember { mutableStateOf("ALL") }
    var targetSection by remember { mutableStateOf("ALL") }
    var examDate by remember { mutableStateOf(Date()) }
    var isManualCreation by remember { mutableStateOf(true) }
    var questions by remember { mutableStateOf<List<ExamQuestion>>(emptyList()) }
    var showAddQuestionDialog by remember { mutableStateOf(false) }
    var selectedDocumentUri by remember { mutableStateOf<Uri?>(null) }
    var documentProcessingStatus by remember { mutableStateOf("") }
    var selectedQuestionType by remember { mutableStateOf(QuestionType.MULTIPLE_CHOICE) }
    
    // Edit question state
    var showEditQuestionDialog by remember { mutableStateOf(false) }
    var questionToEdit by remember { mutableStateOf<ExamQuestion?>(null) }
    
    // State for formatting guide dialog
    var showFormattingGuide by remember { mutableStateOf(false) }
    
    // State for viewing all questions dialog
    var showAllQuestionsDialog by remember { mutableStateOf(false) }
    
    val coroutineScope = rememberCoroutineScope()
    
    // For handling feedback messages
    var showFeedback by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf("") }
    var isErrorFeedback by remember { mutableStateOf(false) }
    
    // Define year and section options
    val yearOptions = listOf("ALL", "1", "2", "3", "4", "5")
    val sectionOptions = listOf("ALL", "A", "B", "C", "D", "E", "F")
    
    // Document picker
    val context = LocalContext.current
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedDocumentUri = uri
            documentProcessingStatus = "Processing document..."
            
            // Set up progress indicator
            val progressUpdater = CoroutineScope(Dispatchers.Main)
            var progressJob = progressUpdater.launch {
                var dots = 1
                while(true) {
                    delay(500)
                    documentProcessingStatus = "Processing document${".".repeat(dots)}"
                    dots = (dots % 3) + 1
                }
            }
            
            // Request persistent URI permissions for Android 10+
            try {
                // Take persistable URI permission to maintain access
                val contentResolver = context.contentResolver
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                
                // Check if we can take persistable permission
                try {
                    contentResolver.takePersistableUriPermission(uri, takeFlags)
                    Log.d("TeacherDashboard", "Successfully taken persistable URI permission for $uri")
                } catch (e: SecurityException) {
                    // Just log the error but continue - we still have the temporary permission
                    Log.e("TeacherDashboard", "Error taking persistable URI permission: ${e.message}", e)
                }
            } catch (e: Exception) {
                Log.e("TeacherDashboard", "Error handling URI permission: ${e.message}", e)
                // Continue anyway, as we might still be able to open the file
            }
            
            // Parse the document with improved error handling
            coroutineScope.launch {
                try {
                    // Cancel progress indicator eventually
                    try {
                        // First validate if file seems processable
                        val (isValid, message) = WordDocumentParser.validateDocument(context, uri)
                        if (!isValid) {
                            documentProcessingStatus = message
                            feedbackMessage = message
                            isErrorFeedback = true
                            showFeedback = true
                            progressJob.cancel()
                            return@launch
                        }
                        
                        // Process with stronger error handling
                        val result = try {
                            System.gc() // Suggest garbage collection before heavy operation
                            WordDocumentParser.parseDocument(context, uri)
                        } catch (e: OutOfMemoryError) {
                            Log.e("TeacherDashboard", "OutOfMemoryError processing document", e)
                            progressJob.cancel()
                            DocumentParsingResult(
                                success = false,
                                errorMessage = "The document is too large to process. Please try a smaller document or split it into multiple files.",
                                errorType = DocumentParsingResult.ErrorType.PARSING_ERROR
                            )
                        }
                        
                        progressJob.cancel() // Stop progress indicator
                        
                        if (result.success) {
                            // Success - update questions
                            questions = result.questions
                            documentProcessingStatus = "Successfully imported ${questions.size} questions."
                            
                            // Make sure we have non-empty title and subject
                            if (title.isEmpty()) {
                                // Use filename as title if not set
                                result.documentInfo?.fileName?.let { fileName ->
                                    title = fileName.replace(Regex("\\.docx?$"), "")
                                }
                            }
                            if (subject.isEmpty()) {
                                subject = "General"
                            }
                            
                            // Show success feedback
                            feedbackMessage = "Document imported successfully with ${questions.size} questions!"
                            isErrorFeedback = false
                        showFeedback = true
                        } else {
                            // Error parsing the document
                            documentProcessingStatus = result.errorMessage ?: "Failed to parse document."
                            
                            // Show error feedback
                            feedbackMessage = result.errorMessage ?: "Failed to parse document."
                            isErrorFeedback = true
                            showFeedback = true
                        }
                    } finally {
                        progressJob.cancel() // Ensure progress indicator is stopped
                    }
                } catch (e: Exception) {
                    // Exception during parsing
                    progressJob.cancel() // Ensure progress indicator is stopped
                    Log.e("TeacherDashboard", "Exception processing document", e)
                    documentProcessingStatus = "Error: ${e.message}"
                    
                    // Show error feedback
                    feedbackMessage = "Error parsing document: ${e.message}"
                    isErrorFeedback = true
                    showFeedback = true
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Exam creation heading
        Text(
            text = "Create a New Exam",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Input method toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = isManualCreation,
                onClick = { isManualCreation = true },
                label = { Text("Create Manually") },
                leadingIcon = {
                    if (isManualCreation) {
                        Icon(Icons.Default.Check, contentDescription = null, Modifier.size(16.dp))
                    }
                }
            )

            FilterChip(
                selected = !isManualCreation,
                onClick = { isManualCreation = false },
                label = { Text("Upload Document") },
                leadingIcon = {
                    if (!isManualCreation) {
                        Icon(Icons.Default.Check, contentDescription = null, Modifier.size(16.dp))
                    }
                }
            )
        }

        // Document upload UI or Manual creation UI based on selection
        if (!isManualCreation) {
            // Document upload card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Upload Exam Document",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    // Document upload instructions
                    Text(
                        text = "Upload a Word document (.docx) containing your exam questions.\n\n" +
                            "The document should include questions with the following formats:\n" +
                            "• Multiple Choice (with options labeled A, B, C, D)\n" +
                            "• True/False questions\n" +
                            "• Short Answer questions\n" +
                            "• Fill in the Blank questions (use underscore _____)\n" +
                            "• Matching questions (with numbered items and lettered matches)\n\n" +
                            "Each question should include 'Answer:' followed by the correct answer.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                    
                    // Upload button
                    Button(
                        onClick = {
                            documentPickerLauncher.launch("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                        },
                        modifier = Modifier
                            .padding(bottom = 16.dp)
                            .semantics { contentDescription = "Upload document" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Upload,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select Document")
                    }
                    
                    // Selected document info
                    if (selectedDocumentUri != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "Document Selected",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                
                                Text(
                                    text = selectedDocumentUri.toString().split("/").last(),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                                
                                Text(
                                    text = documentProcessingStatus,
                                    color = if (documentProcessingStatus.contains("Failed")) 
                                        MaterialTheme.colorScheme.error 
                                    else MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontSize = 14.sp,
                                    fontWeight = if (documentProcessingStatus.contains("successfully")) 
                                        FontWeight.Bold 
                                    else FontWeight.Normal
                                )
                            }
                        }
                    }
                    
                    // Questions preview (if any)
                    if (questions.isNotEmpty()) {
                        Text(
                            text = "Extracted Questions (${questions.size})",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                        
                        // Question types summary
                        val questionTypeCounts = questions.groupBy { it.type }
                            .mapValues { it.value.size }
                        
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalSpacing = 8.dp
                        ) {
                            questionTypeCounts.forEach { (type, count) ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = when (type) {
                                        QuestionType.MULTIPLE_CHOICE -> MaterialTheme.colorScheme.primary
                                        QuestionType.TRUE_FALSE -> MaterialTheme.colorScheme.tertiary
                                        QuestionType.SHORT_ANSWER -> MaterialTheme.colorScheme.secondary
                                        QuestionType.FILL_IN_THE_BLANK -> MaterialTheme.colorScheme.error
                                        QuestionType.MATCHING -> MaterialTheme.colorScheme.errorContainer
                                        QuestionType.ESSAY -> MaterialTheme.colorScheme.primaryContainer
                                        else -> MaterialTheme.colorScheme.error
                                    }.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "${type.displayName}: $count",
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        color = when (type) {
                                            QuestionType.MULTIPLE_CHOICE -> MaterialTheme.colorScheme.primary
                                            QuestionType.TRUE_FALSE -> MaterialTheme.colorScheme.tertiary
                                            QuestionType.SHORT_ANSWER -> MaterialTheme.colorScheme.secondary
                                            QuestionType.FILL_IN_THE_BLANK -> MaterialTheme.colorScheme.error
                                            QuestionType.MATCHING -> MaterialTheme.colorScheme.errorContainer
                                            QuestionType.ESSAY -> MaterialTheme.colorScheme.primaryContainer
                                            else -> MaterialTheme.colorScheme.error
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                        
                        // Preview first few questions
                        val previewQuestions = questions.take(3)
                        previewQuestions.forEachIndexed { index, question ->
                            QuestionPreviewCard(
                                questionNumber = index + 1,
                                question = question,
                                onDelete = {
                                    // Remove this question from the list
                                    questions = questions.filterIndexed { i, _ -> i != index }
                                },
                                onEdit = { 
                                    // Set question to edit and show dialog
                                    questionToEdit = question
                                    showEditQuestionDialog = true
                                }
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        
                        // Show more indicator if there are more than preview questions
                        if (questions.size > previewQuestions.size) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "... and ${questions.size - previewQuestions.size} more questions",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Button(
                                    onClick = { showAllQuestionsDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.List,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("View All Questions")
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Manual question creation UI
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Add Questions Manually",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    if (questions.isEmpty()) {
                        // Empty state
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Quiz,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = "No questions added yet",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            
                            Text(
                                text = "Add questions to your exam using the button below",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                            )
                        }
                    } else {
                        // Show questions
                        Text(
                            text = "Questions (${questions.size})",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        // Question counter by type
                        val questionTypeCounts = questions.groupBy { it.type }
                            .mapValues { it.value.size }
                        
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalSpacing = 8.dp
                        ) {
                            questionTypeCounts.forEach { (type, count) ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = when (type) {
                                        QuestionType.MULTIPLE_CHOICE -> MaterialTheme.colorScheme.primary
                                        QuestionType.TRUE_FALSE -> MaterialTheme.colorScheme.tertiary
                                        QuestionType.SHORT_ANSWER -> MaterialTheme.colorScheme.secondary
                                        QuestionType.FILL_IN_THE_BLANK -> MaterialTheme.colorScheme.error
                                        QuestionType.MATCHING -> MaterialTheme.colorScheme.errorContainer
                                        QuestionType.ESSAY -> MaterialTheme.colorScheme.primaryContainer
                                        else -> MaterialTheme.colorScheme.error
                                    }.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "${type.displayName}: $count",
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        color = when (type) {
                                            QuestionType.MULTIPLE_CHOICE -> MaterialTheme.colorScheme.primary
                                            QuestionType.TRUE_FALSE -> MaterialTheme.colorScheme.tertiary
                                            QuestionType.SHORT_ANSWER -> MaterialTheme.colorScheme.secondary
                                            QuestionType.FILL_IN_THE_BLANK -> MaterialTheme.colorScheme.error
                                            QuestionType.MATCHING -> MaterialTheme.colorScheme.errorContainer
                                            QuestionType.ESSAY -> MaterialTheme.colorScheme.primaryContainer
                                            else -> MaterialTheme.colorScheme.error
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                        
                        // List of questions
                        questions.forEachIndexed { index, question ->
                            QuestionPreviewCard(
                                questionNumber = index + 1,
                                question = question,
                                onDelete = {
                                    questions = questions.filterIndexed { i, _ -> i != index }
                                },
                                onEdit = { 
                                    // Set question to edit and show dialog
                                    questionToEdit = question
                                    showEditQuestionDialog = true
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                    
                    // Add question button
                    Button(
                        onClick = { showAddQuestionDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Question")
                    }
                }
            }
        }

        // Show feedback message if there is one
        if (showFeedback) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isErrorFeedback) 
                        MaterialTheme.colorScheme.errorContainer 
                    else MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isErrorFeedback) Icons.Default.Info else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (isErrorFeedback) 
                                MaterialTheme.colorScheme.error 
                            else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = feedbackMessage.replace("\n\nWould you like to see a guide on how to format your document properly?", ""),
                            color = if (isErrorFeedback) 
                                MaterialTheme.colorScheme.error 
                            else MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    // Show formatting guide button if error is related to document formatting
                    if (isErrorFeedback && feedbackMessage.contains("Would you like to see a guide")) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { showFormattingGuide = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Help,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("View Formatting Guide")
                        }
                    }
                }
            }
        }

        // Basic exam details card (only show if it's manual creation or a document has been uploaded)
        if (isManualCreation || (selectedDocumentUri != null && questions.isNotEmpty())) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Basic Details",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Exam Title") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text("Subject") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = duration,
                        onValueChange = { duration = it },
                        label = { Text("Duration (minutes)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    )

                    // Replace filter chips with dropdown for target year
                    val yearDropdownExpanded = remember { mutableStateOf(false) }
                    
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                    ) {
                        OutlinedButton(
                            onClick = { yearDropdownExpanded.value = true },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(16.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (targetYear == "ALL") "All Years" else "Year $targetYear",
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Start
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select Year"
                            )
                        }
                        
                        DropdownMenu(
                            expanded = yearDropdownExpanded.value,
                            onDismissRequest = { yearDropdownExpanded.value = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            yearOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(if (option == "ALL") "All Years" else "Year $option") },
                                    onClick = {
                                        targetYear = option
                                        yearDropdownExpanded.value = false
                                    },
                                    leadingIcon = {
                                        if (targetYear == option) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Dropdown for selecting target section
                    Text(
                        text = "Target Section",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    // Replace filter chips with dropdown for target section
                    val sectionDropdownExpanded = remember { mutableStateOf(false) }
                    
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { sectionDropdownExpanded.value = true },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(16.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (targetSection == "ALL") "All Sections" else "Section $targetSection",
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Start
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select Section"
                            )
                        }
                        
                        DropdownMenu(
                            expanded = sectionDropdownExpanded.value,
                            onDismissRequest = { sectionDropdownExpanded.value = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            sectionOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(if (option == "ALL") "All Sections" else "Section $option") },
                                    onClick = {
                                        targetSection = option
                                        sectionDropdownExpanded.value = false
                                    },
                                    leadingIcon = {
                                        if (targetSection == option) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Create exam button
        Button(
            onClick = {
                try {
                    if (title.isBlank()) {
                        feedbackMessage = "Please enter an exam title"
                        showFeedback = true
                        return@Button
                    }

                    if (subject.isBlank()) {
                        feedbackMessage = "Please enter a subject"
                        showFeedback = true
                        return@Button
                    }

                    if (questions.isEmpty()) {
                        feedbackMessage = "Please add at least one question to the exam"
                        showFeedback = true
                        return@Button
                    }

                    // First, verify Firebase connection
                    coroutineScope.launch {
                        // Create the exam
                        Log.d(
                            "TeacherDashboard",
                            "Creating new exam: $title for $targetYear/$targetSection with ${questions.size} questions"
                        )
                        val newExam = com.example.ablindexaminer.data.model.TeacherExam(
                            id = "", // Leave blank, it will be filled by Firebase
                            title = title,
                            subject = subject,
                            duration = duration.toIntOrNull() ?: 60,
                            questionCount = questions.size,
                            date = examDate,
                            published = false, // Default to unpublished
                            targetYear = targetYear,
                            targetSection = targetSection
                        )

                        // Call the onExamCreated callback which handles Firebase saving
                        onExamCreated(newExam, questions)
                        Log.d("TeacherDashboard", "Exam creation initiated")
                        
                        // Provide feedback using text-to-speech
                        speak(textToSpeech, "Exam creation initiated. Please wait while your exam is being saved.")
                        
                        // Show visual feedback
                        feedbackMessage = "Exam created successfully!"
                        isErrorFeedback = false
                        showFeedback = true

                        // Reset form (keep feedback visible)
                        title = ""
                        subject = ""
                        duration = "60"
                        targetYear = "ALL"
                        targetSection = "ALL"
                        examDate = Date()
                        questions = emptyList()
                    }
                } catch (e: Exception) {
                    Log.e("TeacherDashboard", "Error creating exam: ${e.message}", e)
                    feedbackMessage = "Error: ${e.message}"
                    isErrorFeedback = true
                    showFeedback = true
                    speak(textToSpeech, "Error creating exam: ${e.message}")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Create Exam", fontSize = 16.sp)
        }
    }
    
    // Add question dialog
    if (showAddQuestionDialog) {
        AddQuestionDialog(
            onDismiss = { showAddQuestionDialog = false },
            onQuestionAdded = { newQuestion ->
                questions = questions + newQuestion
                showAddQuestionDialog = false
            }
        )
    }
    
    // Edit question dialog
    if (showEditQuestionDialog && questionToEdit != null) {
        EditQuestionDialog(
            question = questionToEdit!!,
            onDismiss = { 
                showEditQuestionDialog = false
                questionToEdit = null
            },
            onQuestionEdited = { editedQuestion ->
                // Update the question in the list
                questions = questions.map { existingQuestion ->
                    if (existingQuestion.id == editedQuestion.id) editedQuestion else existingQuestion
                }
                showEditQuestionDialog = false
                questionToEdit = null
            }
        )
    }
    
    // Show formatting guide dialog if needed
    if (showFormattingGuide) {
        FormattingGuideDialog(
            onDismiss = { showFormattingGuide = false }
        )
    }

    // Show all questions dialog
    if (showAllQuestionsDialog) {
        AllQuestionsDialog(
            questions = questions,
            onDismiss = { showAllQuestionsDialog = false },
            onQuestionEdited = { editedQuestion ->
                // Update the question in the list
                questions = questions.map { existingQuestion ->
                    if (existingQuestion.id == editedQuestion.id) editedQuestion else existingQuestion
                }
            },
            onQuestionDeleted = { questionId ->
                // Remove the question from the list
                questions = questions.filter { it.id != questionId }
                // If all questions are deleted, close the dialog
                if (questions.isEmpty()) {
                    showAllQuestionsDialog = false
                }
            }
        )
    }
}

@Composable
private fun QuestionPreviewCard(
    questionNumber: Int,
    question: ExamQuestion,
    onDelete: () -> Unit,
    onEdit: (ExamQuestion) -> Unit  // Add parameter for edit functionality
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Question number badge
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = questionNumber.toString(),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Question type badge
                    Box(
                        modifier = Modifier
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = question.type.displayName,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontSize = 12.sp
                        )
                    }
                }

                // Action buttons
                Row {
                    // Edit button
                    IconButton(
                        onClick = { onEdit(question) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit question",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                }

                // Delete button
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Delete question",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    }
                }
            }

            // Question text
            Text(
                text = question.text,
                modifier = Modifier.padding(vertical = 8.dp),
                fontSize = 14.sp
            )

            // Preview of options or correct answer
            if (question.options.isNotEmpty()) {
                Text(
                    text = "Options: ${
                        question.options.take(2).joinToString(", ")
                    }${if (question.options.size > 2) "..." else ""}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "Correct answer: ${question.correctAnswer}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddQuestionDialog(
    onDismiss: () -> Unit,
    onQuestionAdded: (ExamQuestion) -> Unit
) {
    var questionText by remember { mutableStateOf("") }
    var selectedQuestionType by remember { mutableStateOf(QuestionType.MULTIPLE_CHOICE) }
    var options by remember { mutableStateOf(List(4) { "" }) }
    var correctAnswer by remember { mutableStateOf("") }
    var points by remember { mutableStateOf("1") }
    
    // For matching question type
    var matchingItems by remember { mutableStateOf(List(4) { "" }) }
    var matchingAnswers by remember { mutableStateOf(List(4) { "" }) }

    // Allow adding/removing matching items
    var matchingItemCount by remember { mutableStateOf(4) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                // Dialog title
                Text(
                    text = "Add Question",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .semantics { contentDescription = "Add Question dialog" }
                )

                // Question type selector using filter chips
                Text(
                    text = "Question Type",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .semantics { contentDescription = "Select question type" }
                )
                
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalSpacing = 8.dp,
                    verticalSpacing = 8.dp
                ) {
                    QuestionType.values().forEach { type ->
                        FilterChip(
                            selected = selectedQuestionType == type,
                            onClick = { 
                                selectedQuestionType = type
                                
                                // Reset options based on question type
                                when (type) {
                                    QuestionType.MULTIPLE_CHOICE -> options = List(4) { "" }
                                    QuestionType.TRUE_FALSE -> options = listOf("True", "False")
                                    QuestionType.MATCHING -> {
                                        matchingItemCount = 4  // Default starting with 4 items
                                        matchingItems = List(matchingItemCount) { "" }
                                        matchingAnswers = List(matchingItemCount) { "" }
                                    }
                                    QuestionType.SHORT_ANSWER, QuestionType.FILL_IN_THE_BLANK -> options = emptyList()
                                    QuestionType.ESSAY -> options = emptyList()
                                    else -> options = emptyList()
                                }
                                
                                // Reset correct answer
                                correctAnswer = ""
                            },
                            label = { Text(type.displayName) },
                            modifier = Modifier.semantics { 
                                contentDescription = "Question type: ${type.displayName}" 
                            }
                        )
                    }
                }

                // Question text input
                OutlinedTextField(
                    value = questionText,
                    onValueChange = { questionText = it },
                    label = { Text("Question Text") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .semantics { contentDescription = "Enter question text" }
                )

                // Dynamic content based on question type
                when (selectedQuestionType) {
                    QuestionType.MULTIPLE_CHOICE -> {
                        Text(
                            text = "Options",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .padding(bottom = 8.dp)
                                .semantics { contentDescription = "Enter multiple choice options" }
                        )

                        options.forEachIndexed { index, option ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                Text(
                                    text = "${('A' + index)}.",
                                    modifier = Modifier.width(24.dp),
                                    fontWeight = FontWeight.Bold
                                )

                                OutlinedTextField(
                                    value = option,
                                    onValueChange = { newValue ->
                                        options =
                                            options.toMutableList().also { it[index] = newValue }
                                    },
                                    label = { Text("Option ${('A' + index)}") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .semantics { contentDescription = "Enter option ${('A' + index)}" }
                                )
                            }
                        }

                        // Correct answer
                        Text(
                            text = "Correct Answer",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                                .semantics { contentDescription = "Select correct answer" }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ('A'..'D').forEachIndexed { index, letter ->
                                FilterChip(
                                    selected = correctAnswer == letter.toString(),
                                    onClick = { correctAnswer = letter.toString() },
                                    label = { Text(letter.toString()) },
                                    modifier = Modifier.semantics { 
                                        contentDescription = "Option $letter" 
                                    }
                                )
                            }
                        }
                    }

                    QuestionType.TRUE_FALSE -> {
                        // True/False options
                        Text(
                            text = "Correct Answer",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                                .semantics { contentDescription = "Select true or false as correct answer" }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            FilterChip(
                                selected = correctAnswer == "True",
                                onClick = { correctAnswer = "True" },
                                label = { Text("True") },
                                modifier = Modifier.semantics { 
                                    contentDescription = "True option" 
                                }
                            )

                            FilterChip(
                                selected = correctAnswer == "False",
                                onClick = { correctAnswer = "False" },
                                label = { Text("False") },
                                modifier = Modifier.semantics { 
                                    contentDescription = "False option" 
                                }
                            )
                        }
                    }

                    QuestionType.SHORT_ANSWER -> {
                        // Correct answer for short answer
                        OutlinedTextField(
                            value = correctAnswer,
                            onValueChange = { correctAnswer = it },
                            label = { Text("Correct Answer (Key phrases)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .semantics { contentDescription = "Enter correct answer key phrases" }
                        )

                        Text(
                            text = "Note: Enter key phrases that should be included in a correct answer.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    QuestionType.FILL_IN_THE_BLANK -> {
                        // Correct answer for fill in the blank
                        OutlinedTextField(
                            value = correctAnswer,
                            onValueChange = { correctAnswer = it },
                            label = { Text("Correct Answer") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .semantics { contentDescription = "Enter correct answer for the blank" }
                        )

                        Text(
                            text = "Note: In the question text, use underscores (_____) to indicate the blank.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    QuestionType.MATCHING -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Column A Items",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.semantics { contentDescription = "Enter matching items" }
                                )
                                
                                // Controls for adding/removing items
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (matchingItemCount > 2) {
                                                matchingItemCount--
                                                matchingItems = matchingItems.take(matchingItemCount).toList()
                                                matchingAnswers = matchingAnswers.take(matchingItemCount).toList()
                                            }
                                        },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Remove,
                                            contentDescription = "Remove matching item",
                                            tint = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    
                                    Text(
                                        text = "$matchingItemCount",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                    
                                    IconButton(
                                        onClick = {
                                            if (matchingItemCount < 10) {
                                                matchingItemCount++
                                                matchingItems = matchingItems.toMutableList().apply {
                                                    while (size < matchingItemCount) add("")
                                                }
                                                matchingAnswers = matchingAnswers.toMutableList().apply {
                                                    while (size < matchingItemCount) add("")
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Add matching item",
                                            tint = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }
                            
                            Divider(modifier = Modifier.padding(vertical = 8.dp))
                            
                            // Column A (Items)
                            matchingItems.indices.forEach { index ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                ) {
                                    Text(
                                        text = "${index + 1}.",
                                        modifier = Modifier.width(24.dp),
                                        fontWeight = FontWeight.Bold
                                    )
                                    
                                    OutlinedTextField(
                                        value = matchingItems[index],
                                        onValueChange = { newValue ->
                                            matchingItems = matchingItems.toMutableList().also { it[index] = newValue }
                                        },
                                        label = { Text("Item ${index + 1}") },
                                        modifier = Modifier
                                            .weight(1f)
                                            .semantics { contentDescription = "Enter item ${index + 1}" }
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Column B (Answers)
                            Text(
                                text = "Column B Items",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            Divider(modifier = Modifier.padding(bottom = 8.dp))
                            
                            matchingAnswers.forEachIndexed { index, answer ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                ) {
                                    Text(
                                        text = "${('A' + index)}.",
                                        modifier = Modifier.width(24.dp),
                                        fontWeight = FontWeight.Bold
                                    )
                                    
                                    OutlinedTextField(
                                        value = answer,
                                        onValueChange = { newValue ->
                                            matchingAnswers = matchingAnswers.toMutableList().also { it[index] = newValue }
                                        },
                                        label = { Text("Match ${('A' + index)}") },
                                        modifier = Modifier
                                            .weight(1f)
                                            .semantics { contentDescription = "Enter match ${('A' + index)}" }
                                    )
                                }
                            }
                            
                            // Correct matching pairs
                            Text(
                                text = "Matching Pairs (Format: 1A,2B,3C,4D)",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            
                            OutlinedTextField(
                                value = correctAnswer,
                                onValueChange = { correctAnswer = it },
                                label = { Text("Matching Pairs") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .semantics { contentDescription = "Enter matching pairs in format 1A,2B,3C,4D" }
                            )
                        }
                    }
                    
                    QuestionType.ESSAY -> {
                        // Essay question type
                        OutlinedTextField(
                            value = correctAnswer,
                            onValueChange = { correctAnswer = it },
                            label = { Text("Model Answer (Optional)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .semantics { contentDescription = "Enter model answer for essay question" }
                        )

                        Text(
                            text = "Note: For essay questions, a model answer is optional and used for grading guidance.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    
                    else -> {
                        // Default case for any future question types
                        OutlinedTextField(
                            value = correctAnswer,
                            onValueChange = { correctAnswer = it },
                            label = { Text("Correct Answer") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                    }
                }

                // Points
                OutlinedTextField(
                    value = points,
                    onValueChange = { points = it },
                    label = { Text("Points") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .semantics { contentDescription = "Enter points for this question" }
                )

                // Action buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = "Cancel adding question" }
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            // Validation
                            if (questionText.isBlank()) {
                                return@Button
                            }
                            
                            // Create the question based on type
                            val newQuestion = when (selectedQuestionType) {
                                QuestionType.MULTIPLE_CHOICE -> {
                                    ExamQuestion(
                                        text = questionText,
                                        type = selectedQuestionType,
                                        options = options.mapIndexed { index, option ->
                                            "${('A' + index)}. $option"
                                        },
                                        correctAnswer = correctAnswer,
                                        points = points.toIntOrNull() ?: 1
                                    )
                                }
                                QuestionType.TRUE_FALSE -> {
                                    ExamQuestion(
                                        text = questionText,
                                        type = selectedQuestionType,
                                        options = listOf("True", "False"),
                                        correctAnswer = correctAnswer,
                                        points = points.toIntOrNull() ?: 1
                                    )
                                }
                                QuestionType.SHORT_ANSWER -> {
                                    ExamQuestion(
                                        text = questionText,
                                        type = selectedQuestionType,
                                        options = emptyList(),
                                        correctAnswer = correctAnswer,
                                        points = points.toIntOrNull() ?: 1
                                    )
                                }
                                QuestionType.FILL_IN_THE_BLANK -> {
                                    ExamQuestion(
                                        text = questionText,
                                        type = selectedQuestionType,
                                        options = emptyList(),
                                        correctAnswer = correctAnswer,
                                        points = points.toIntOrNull() ?: 1
                                    )
                                }
                                QuestionType.MATCHING -> {
                                    // Create pairs of matching items for options
                                    val matchingOptions = mutableListOf<String>()
                                    // Add Column A items
                                    matchingItems.forEachIndexed { index, item ->
                                        matchingOptions.add("${index + 1}. $item")
                                    }
                                    // Add Column B items
                                    matchingAnswers.forEachIndexed { index, answer ->
                                        matchingOptions.add("${('A' + index)}. $answer")
                                    }
                                    
                                    ExamQuestion(
                                        text = questionText,
                                        type = selectedQuestionType,
                                        options = matchingOptions,
                                        correctAnswer = correctAnswer,
                                        points = points.toIntOrNull() ?: 1
                                    )
                                }
                                QuestionType.ESSAY -> {
                                    ExamQuestion(
                                        text = questionText,
                                        type = selectedQuestionType,
                                        options = emptyList(),
                                        correctAnswer = correctAnswer,
                                        points = points.toIntOrNull() ?: 1
                                    )
                                }
                                else -> {
                                    // Default case for any future question types
                                    ExamQuestion(
                                        text = questionText,
                                        type = selectedQuestionType,
                                        options = emptyList(),
                                        correctAnswer = correctAnswer,
                                        points = points.toIntOrNull() ?: 1
                                    )
                                }
                            }
                            
                            onQuestionAdded(newQuestion)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = "Add question to exam" }
                    ) {
                        Text("Add Question")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatisticsTab(
    exams: List<TeacherExam>,
    isLoading: Boolean,
    textToSpeech: TextToSpeech
) {
    // Calculate statistics
    val totalExams = exams.size
    val publishedExams = exams.count { it.published }
    val totalQuestions = exams.sumOf { it.questionCount }
    
    // Exams by section
    val examsBySection = exams.groupBy { it.targetSection }
        .mapValues { it.value.size }
        .toSortedMap(compareBy { if (it == "ALL") "0" else it })
    
    // Exams by year
    val examsByYear = exams.groupBy { it.targetYear }
        .mapValues { it.value.size }
        .toSortedMap(compareBy { if (it == "ALL") "0" else it })

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Summary card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Summary",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatCard(
                                title = "Total Exams",
                                value = totalExams.toString(),
                                icon = Icons.AutoMirrored.Outlined.Assignment,
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            StatCard(
                                title = "Published",
                                value = publishedExams.toString(),
                                icon = Icons.Outlined.Visibility,
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            StatCard(
                                title = "Questions",
                                value = totalQuestions.toString(),
                                icon = Icons.Outlined.QuestionAnswer,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Exams by section
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Exams by Section",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        if (examsBySection.isEmpty()) {
                            Text(
                                text = "No data available",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column {
                                examsBySection.forEach { (section, count) ->
                                    val displaySection =
                                        if (section == "ALL") "All Sections" else "Section $section"
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = displaySection,
                                            fontSize = 16.sp
                                        )

                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ) {
                                            Text(
                                                text = count.toString(),
                                                modifier = Modifier.padding(
                                                    horizontal = 8.dp,
                                                    vertical = 4.dp
                                                ),
                                                fontSize = 14.sp
                                            )
                                        }
                                    }

                                    if (section != examsBySection.keys.last()) {
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // Exams by year
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Exams by Year",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        if (examsByYear.isEmpty()) {
                            Text(
                                text = "No data available",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column {
                                examsByYear.forEach { (year, count) ->
                                    val displayYear =
                                        if (year == "ALL") "All Years" else "Year $year"
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = displayYear,
                                            fontSize = 16.sp
                                        )

                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ) {
                                            Text(
                                                text = count.toString(),
                                                modifier = Modifier.padding(
                                                    horizontal = 8.dp,
                                                    vertical = 4.dp
                                                ),
                                                fontSize = 14.sp
                                            )
                                        }
                                    }

                                    if (year != examsByYear.keys.last()) {
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // Recent exams
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Recent Exams",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        if (exams.isEmpty()) {
                            Text(
                                text = "No exams created yet",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column {
                                exams.sortedByDescending { it.date }.take(5).forEach { exam ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                text = exam.title,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Medium
                                            )

                                            Text(
                                                text = "${exam.subject} • ${formatDate(exam.date)}",
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .clip(
                                                    androidx.compose.foundation.shape.RoundedCornerShape(
                                                        16.dp
                                                    )
                                                )
                                                .background(
                                                    if (exam.published) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.surfaceVariant
                                                )
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = if (exam.published) "Published" else "Draft",
                                                fontSize = 12.sp,
                                                color = if (exam.published) MaterialTheme.colorScheme.onPrimary
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (exam != exams.sortedByDescending { it.date }.take(5)
                                            .last()
                                    ) {
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = title,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Extension function to convert ExamQuestion to Firebase Question model
 */
private fun ExamQuestion.toQuestion(): com.example.ablindexaminer.data.model.Question {
    return com.example.ablindexaminer.data.model.Question(
        id = this.id,
        text = this.text,
        options = this.options,
        correctAnswer = this.correctAnswer,
        type = this.type.name, // Convert enum to string name
        points = this.points
    )
}

/**
 * Dialog to display the document formatting guide
 */
@Composable
private fun FormattingGuideDialog(
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .heightIn(max = 600.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Document Formatting Guide",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close guide"
                        )
                    }
                }
                
                // Guide content
                val guideText = WordDocumentParser.getDocumentInstructionsTemplate()
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = guideText,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Close button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Got it")
                }
            }
        }
    }
} 

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditQuestionDialog(
    question: ExamQuestion,
    onDismiss: () -> Unit,
    onQuestionEdited: (ExamQuestion) -> Unit
) {
    var questionText by remember { mutableStateOf(question.text) }
    var selectedQuestionType by remember { mutableStateOf(question.type) }
    var options by remember { mutableStateOf(question.options) }
    var correctAnswer by remember { mutableStateOf(question.correctAnswer) }
    var points by remember { mutableStateOf(question.points.toString()) }
    
    // For matching question type
    var matchingItems by remember {
        mutableStateOf(
            if (question.type == QuestionType.MATCHING && question.options.size >= 2) {
                question.options.take(question.options.size / 2)
            } else {
                List(4) { "" }
            }
        )
    }
    var matchingAnswers by remember {
        mutableStateOf(
            if (question.type == QuestionType.MATCHING && question.options.size >= 2) {
                question.options.drop(question.options.size / 2)
            } else {
                List(4) { "" }
            }
        )
    }

    // Add variable for number of matching items
    var matchingItemCount by remember {
        mutableStateOf(
            if (question.type == QuestionType.MATCHING && question.options.size >= 2) {
                question.options.size / 2
            } else {
                4
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                // Dialog title
                Text(
                    text = "Edit Question",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .semantics { contentDescription = "Edit Question dialog" }
                )

                // Question type selector using filter chips
                Text(
                    text = "Question Type",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .semantics { contentDescription = "Select question type" }
                )
                
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalSpacing = 8.dp,
                    verticalSpacing = 8.dp
                ) {
                    QuestionType.values().forEach { type ->
                        FilterChip(
                            selected = selectedQuestionType == type,
                            onClick = { 
                                selectedQuestionType = type
                                
                                // Reset options based on question type
                                when (type) {
                                    QuestionType.MULTIPLE_CHOICE -> options = if (question.type == QuestionType.MULTIPLE_CHOICE) {
                                        question.options
                                    } else {
                                        List(4) { "" }
                                    }
                                    QuestionType.TRUE_FALSE -> options = listOf("True", "False")
                                    QuestionType.MATCHING -> {
                                        if (question.type == QuestionType.MATCHING) {
                                            matchingItems = question.options.take(question.options.size / 2)
                                            matchingAnswers = question.options.drop(question.options.size / 2)
                                        } else {
                                            matchingItems = List(4) { "" }
                                            matchingAnswers = List(4) { "" }
                                        }
                                    }
                                    QuestionType.SHORT_ANSWER, QuestionType.FILL_IN_THE_BLANK -> options = emptyList()
                                    QuestionType.ESSAY -> options = emptyList()
                                    else -> options = emptyList()
                                }
                            },
                            label = { Text(type.displayName) },
                            modifier = Modifier.semantics { 
                                contentDescription = "Question type: ${type.displayName}" 
                            }
                        )
                    }
                }

                // Question text input
                Text(
                    text = "Question Text",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .semantics { contentDescription = "Question text input" }
                )
                
                OutlinedTextField(
                    value = questionText,
                    onValueChange = { questionText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .semantics { contentDescription = "Enter question text" },
                    placeholder = { Text("Enter the question here") },
                    minLines = 2
                )

                // Question options - varies by type
                when (selectedQuestionType) {
                    QuestionType.MULTIPLE_CHOICE -> {
                        Text(
                            text = "Options",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .padding(bottom = 8.dp)
                                .semantics { contentDescription = "Options input section" }
                        )
                        
                        // Get options from the existing question if available
                        val currentOptions = if (question.type == QuestionType.MULTIPLE_CHOICE) {
                            // Extract option text from formatted options (remove letter prefix)
                            question.options.map { option ->
                                option.replaceFirst(Regex("^[A-Z]\\.\\s+"), "")
                            }
                        } else {
                            List(4) { "" }
                        }
                        
                        // Initialize options if not already set
                        if (options.isEmpty() || options.all { option -> 
                            option.matches(Regex("^[A-Z]\\. .*")) 
                        }) {
                            options = currentOptions
                        }
                        
                        // Updated option count to match actual options
                        val optionCount = maxOf(4, options.size)
                        val mutableOptions = options.toMutableList()
                        while (mutableOptions.size < optionCount) {
                            mutableOptions.add("")
                        }
                        options = mutableOptions
                        
                        ('A' until 'A' + optionCount).forEachIndexed { index, letter ->
                            OutlinedTextField(
                                value = if (index < options.size) options[index] else "",
                                onValueChange = { 
                                    val newOptions = options.toMutableList()
                                    if (index < newOptions.size) {
                                        newOptions[index] = it
                                    } else {
                                        while (newOptions.size <= index) {
                                            newOptions.add("")
                                        }
                                        newOptions[index] = it
                                    }
                                    options = newOptions
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .semantics { contentDescription = "Option $letter" },
                                placeholder = { Text("Option $letter") },
                                leadingIcon = { 
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = letter.toString(),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            )
                        }
                    }
                    
                    QuestionType.MATCHING -> {
                        // Column A
                        Text(
                            text = "Column A",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .padding(bottom = 8.dp)
                                .semantics { contentDescription = "Column A input section" }
                        )
                        
                        matchingItems.indices.forEach { index ->
                            OutlinedTextField(
                                value = matchingItems[index],
                                onValueChange = { 
                                    val newItems = matchingItems.toMutableList()
                                    newItems[index] = it
                                    matchingItems = newItems
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .semantics { contentDescription = "Column A item ${index + 1}" },
                                placeholder = { Text("Item ${index + 1}") },
                                leadingIcon = { 
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = (index + 1).toString(),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            )
                        }
                        
                        // Column B
                        Text(
                            text = "Column B",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .padding(top = 16.dp, bottom = 8.dp)
                                .semantics { contentDescription = "Column B input section" }
                        )
                        
                        matchingAnswers.indices.forEach { index ->
                            OutlinedTextField(
                                value = matchingAnswers[index],
                                onValueChange = { 
                                    val newAnswers = matchingAnswers.toMutableList()
                                    newAnswers[index] = it
                                    matchingAnswers = newAnswers
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .semantics { contentDescription = "Column B item ${index + 1}" },
                                placeholder = { Text("Match ${index + 1}") },
                                leadingIcon = { 
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.secondary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = ('A' + index).toString(),
                                            color = MaterialTheme.colorScheme.onSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            )
                        }
                    }
                    
                    QuestionType.TRUE_FALSE -> {
                        // No additional UI needed, options are fixed
                        options = listOf("True", "False")
                    }
                    
                    else -> {
                        // No options for other question types
                    }
                }

                // Correct answer
                Text(
                    text = "Correct Answer",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .padding(top = 16.dp, bottom = 8.dp)
                        .semantics { contentDescription = "Correct answer input" }
                )
                
                when (selectedQuestionType) {
                    QuestionType.MULTIPLE_CHOICE -> {
                        OutlinedTextField(
                            value = correctAnswer,
                            onValueChange = { correctAnswer = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = "Enter correct answer" },
                            placeholder = { Text("Enter the correct answer (e.g., A, B, C, D)") }
                        )
                    }
                    
                    QuestionType.TRUE_FALSE -> {
                        // Radio buttons for true/false
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = correctAnswer == "True",
                                onClick = { correctAnswer = "True" },
                                modifier = Modifier.semantics { contentDescription = "True" }
                            )
                            Text(
                                text = "True",
                                modifier = Modifier
                                    .clickable { correctAnswer = "True" }
                                    .padding(start = 8.dp, end = 16.dp)
                            )
                            
                            RadioButton(
                                selected = correctAnswer == "False",
                                onClick = { correctAnswer = "False" },
                                modifier = Modifier.semantics { contentDescription = "False" }
                            )
                            Text(
                                text = "False",
                                modifier = Modifier
                                    .clickable { correctAnswer = "False" }
                                    .padding(start = 8.dp)
                            )
                        }
                    }
                    
                    QuestionType.MATCHING -> {
                        // Matching answers format: "1-A, 2-B, 3-C, 4-D"
                        OutlinedTextField(
                            value = correctAnswer,
                            onValueChange = { correctAnswer = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = "Enter correct matching pairs" },
                            placeholder = { Text("Example: 1-A, 2-B, 3-C, 4-D") }
                        )
                    }
                    
                    else -> {
                        // Text field for other question types
                        OutlinedTextField(
                            value = correctAnswer,
                            onValueChange = { correctAnswer = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = "Enter correct answer" },
                            placeholder = { Text("Enter the correct answer") }
                        )
                    }
                }

                // Points value
                Text(
                    text = "Points",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .padding(top = 16.dp, bottom = 8.dp)
                        .semantics { contentDescription = "Points input" }
                )
                
                OutlinedTextField(
                    value = points,
                    onValueChange = { 
                        // Only allow numeric input
                        if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                            points = it
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .semantics { contentDescription = "Enter points value" },
                    placeholder = { Text("Points for this question") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                // Action buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    // Cancel button
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.semantics { contentDescription = "Cancel" }
                    ) {
                        Text("Cancel")
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Save button
                    Button(
                        onClick = {
                            // Validation
                            if (questionText.isBlank()) {
                                return@Button
                            }
                            
                            // Create the edited question based on type
                            val editedQuestion = when (selectedQuestionType) {
                                QuestionType.MULTIPLE_CHOICE -> {
                                    ExamQuestion(
                                        id = question.id, // Keep the original ID
                                        text = questionText,
                                        type = selectedQuestionType,
                                        options = options.mapIndexed { index, option ->
                                            "${('A' + index)}. $option"
                                        },
                                        correctAnswer = correctAnswer,
                                        points = points.toIntOrNull() ?: 1,
                                        number = question.number // Keep the original number
                                    )
                                }
                                QuestionType.TRUE_FALSE -> {
                                    ExamQuestion(
                                        id = question.id,
                                        text = questionText,
                                        type = selectedQuestionType,
                                        options = listOf("True", "False"),
                                        correctAnswer = correctAnswer,
                                        points = points.toIntOrNull() ?: 1,
                                        number = question.number
                                    )
                                }
                                QuestionType.SHORT_ANSWER -> {
                                    ExamQuestion(
                                        id = question.id,
                                        text = questionText,
                                        type = selectedQuestionType,
                                        options = emptyList(),
                                        correctAnswer = correctAnswer,
                                        points = points.toIntOrNull() ?: 1,
                                        number = question.number
                                    )
                                }
                                QuestionType.FILL_IN_THE_BLANK -> {
                                    ExamQuestion(
                                        id = question.id,
                                        text = questionText,
                                        type = selectedQuestionType,
                                        options = emptyList(),
                                        correctAnswer = correctAnswer,
                                        points = points.toIntOrNull() ?: 1,
                                        number = question.number
                                    )
                                }
                                QuestionType.MATCHING -> {
                                    // Create pairs of matching items for options
                                    val matchingOptions = mutableListOf<String>()
                                    // Add Column A items
                                    matchingItems.forEachIndexed { index, item ->
                                        matchingOptions.add("${index + 1}. $item")
                                    }
                                    // Add Column B items
                                    matchingAnswers.forEachIndexed { index, answer ->
                                        matchingOptions.add("${('A' + index)}. $answer")
                                    }
                                    
                                    ExamQuestion(
                                        id = question.id,
                                        text = questionText,
                                        type = selectedQuestionType,
                                        options = matchingOptions,
                                        correctAnswer = correctAnswer,
                                        points = points.toIntOrNull() ?: 1,
                                        number = question.number
                                    )
                                }
                                QuestionType.ESSAY -> {
                                    ExamQuestion(
                                        id = question.id,
                                        text = questionText,
                                        type = selectedQuestionType,
                                        options = emptyList(),
                                        correctAnswer = correctAnswer,
                                        points = points.toIntOrNull() ?: 1,
                                        number = question.number
                                    )
                                }
                            }
                            
                            // Call the callback with the edited question
                            onQuestionEdited(editedQuestion)
                            onDismiss()
                        },
                        modifier = Modifier.semantics { contentDescription = "Save changes" }
                    ) {
                        Text("Save Changes")
                    }
                }
            }
        }
    }
} 

// Add this function somewhere in the file, outside any composable function
private fun onExamCreated(exam: TeacherExam, questions: List<ExamQuestion>) {
    Log.d("TeacherDashboard", "Creating exam: ${exam.title} with ${questions.size} questions")
    
    // Use coroutineScope to handle Firestore operations
    kotlinx.coroutines.GlobalScope.launch {
        try {
            val hybridExamService = HybridExamService()
            val authService = FirebaseAuthService()
            
            // Get the current teacher's ID
            val teacherId = authService.getCurrentUserId()
            if (teacherId == null) {
                Log.e("TeacherDashboard", "Failed to get current user ID")
                return@launch
            }
            
            // Convert TeacherExam from UI model to data model
            val modelExam = com.example.ablindexaminer.data.model.TeacherExam(
                id = exam.id,
                title = exam.title,
                subject = exam.subject,
                duration = exam.duration,
                questionCount = exam.questionCount,
                date = exam.date,
                published = exam.published,
                teacherId = teacherId,
                targetYear = exam.targetYear,
                targetSection = exam.targetSection
            )
            
            // First create the exam to get its ID
            val createResult = hybridExamService.createExam(modelExam, teacherId)
            
            if (createResult.isSuccess) {
                val examId = createResult.getOrNull()
                if (examId != null) {
                    Log.d("TeacherDashboard", "Exam created with ID: $examId, adding ${questions.size} questions")
                    
                    // Add each question to the exam using HybridExamService
                    questions.forEachIndexed { index, question ->
                        // Convert ExamQuestion to data.model.Question
                        val modelQuestion = com.example.ablindexaminer.data.model.Question(
                            id = question.id,
                            text = question.text,
                            options = question.options,
                            correctAnswer = question.correctAnswer,
                            type = question.type.name, // Convert enum to string name
                            points = question.points,
                            number = index + 1 // Ensure sequential numbering
                        )
                        
                        // Add the question to Firestore
                        val addResult = hybridExamService.addQuestionToExam(examId, modelQuestion)
                        if (addResult.isSuccess) {
                            Log.d("TeacherDashboard", "Added question ${index + 1} successfully")
                        } else {
                            Log.e("TeacherDashboard", "Failed to add question ${index + 1}: ${addResult.exceptionOrNull()?.message}")
                        }
                    }
                    
                    Log.d("TeacherDashboard", "Exam creation completed successfully")
                } else {
                    Log.e("TeacherDashboard", "Created exam ID was null")
                }
            } else {
                Log.e("TeacherDashboard", "Failed to create exam: ${createResult.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            Log.e("TeacherDashboard", "Error creating exam", e)
        }
    }
}

/**
 * Dialog to display all questions with editing capability
 */
@Composable
private fun AllQuestionsDialog(
    questions: List<ExamQuestion>,
    onDismiss: () -> Unit,
    onQuestionEdited: (ExamQuestion) -> Unit,
    onQuestionDeleted: (String) -> Unit
) {
    // State for the selected question to edit
    var questionToEdit by remember { mutableStateOf<ExamQuestion?>(null) }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 600.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "All Questions (${questions.size})",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }
                }
                
                // Question list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(questions) { question ->
                        QuestionPreviewCard(
                            questionNumber = question.number,
                            question = question,
                            onDelete = { onQuestionDeleted(question.id) },
                            onEdit = { questionToEdit = it }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                
                // Close button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 16.dp)
                ) {
                    Text("Done")
                }
            }
        }
    }
    
    // Edit question dialog
    if (questionToEdit != null) {
        EditQuestionDialog(
            question = questionToEdit!!,
            onDismiss = { questionToEdit = null },
            onQuestionEdited = { 
                onQuestionEdited(it)
                questionToEdit = null
            }
        )
    }
}

@Composable
private fun StudentResultsTab(
    exams: List<TeacherExam>,
    isLoading: Boolean,
    textToSpeech: TextToSpeech
) {
    val coroutineScope = rememberCoroutineScope()
    val hybridExamService = remember { HybridExamService() }
    val authService = remember { FirebaseAuthService() }
    
    // State for student exam records
    var studentRecords by remember { mutableStateOf<List<StudentExamRecord>>(emptyList()) }
    var loadingRecords by remember { mutableStateOf(true) }
    var selectedRecord by remember { mutableStateOf<StudentExamRecord?>(null) }
    var showDetailDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showAnalyticsDialog by remember { mutableStateOf(false) }
    
    // State for filtering
    var filterSubject by remember { mutableStateOf("") }
    var filterStudent by remember { mutableStateOf("") }
    var selectedExamForAnalytics by remember { mutableStateOf<String?>(null) }
    
    // Analytics data
    var examAnalytics by remember { mutableStateOf<ExamAnalytics?>(null) }
    
    // Fetch student exam records
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            try {
                // Get the current teacher's ID
                val teacherId = authService.getCurrentUserId()
                if (teacherId != null) {
                    // Get all student exam records for exams created by this teacher
                    val result = hybridExamService.getTeacherStudentExamRecords(teacherId)
                    if (result.isSuccess) {
                        studentRecords = result.getOrDefault(emptyList())
                        Log.d("TeacherDashboard", "Loaded ${studentRecords.size} student exam records")
                    } else {
                        Log.e("TeacherDashboard", "Failed to load student records: ${result.exceptionOrNull()?.message}")
                    }
                }
                loadingRecords = false
            } catch (e: Exception) {
                Log.e("TeacherDashboard", "Error loading student records", e)
                loadingRecords = false
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header with analytics button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
                Text(
                text = "Student Results",
                fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            
            Button(
                onClick = { showAnalyticsDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                )
                ) {
                    Icon(
                    imageVector = Icons.Default.Analytics,
                    contentDescription = "Exam Analytics"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Analytics")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Filter controls
        Row(
            modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = filterSubject,
                        onValueChange = { filterSubject = it },
                label = { Text("Filter by Subject") },
                modifier = Modifier.weight(1f)
            )
            
                    OutlinedTextField(
                        value = filterStudent,
                        onValueChange = { filterStudent = it },
                label = { Text("Filter by Student") },
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Results content
        if (loadingRecords) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            // Filter records
            val filteredRecords = studentRecords.filter { record ->
                (filterSubject.isBlank() || record.subject.contains(filterSubject, true)) &&
                (filterStudent.isBlank() || record.studentName.contains(filterStudent, true))
            }
            
                if (filteredRecords.isEmpty()) {
                EmptyStateMessage(
                    message = "No student results found",
                    description = "Student exam results will appear here after they complete exams you've created.",
                    icon = Icons.Default.Assessment,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                } else {
                // Group records by exam for better organization
                val recordsByExam = filteredRecords.groupBy { it.id }
                
                LazyColumn {
                    recordsByExam.forEach { (examId, records) ->
                        item {
                            ExamResultsSection(
                                examId = examId,
                                examTitle = records.first().title,
                                examSubject = records.first().subject,
                                records = records,
                                onRecordClick = { record ->
                                    selectedRecord = record
                                    showDetailDialog = true
                                },
                                onEditGrade = { record ->
                                    selectedRecord = record
                                    showEditDialog = true
                                },
                                onAnalyticsClick = {
                                    selectedExamForAnalytics = examId
                                    examAnalytics = calculateExamAnalytics(records)
                                    showAnalyticsDialog = true
                                }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
    
    // Analytics Dialog
    if (showAnalyticsDialog) {
        ExamAnalyticsDialog(
            examId = selectedExamForAnalytics,
            examTitle = studentRecords.find { it.id == selectedExamForAnalytics }?.title ?: "Exam",
            analytics = examAnalytics ?: calculateOverallAnalytics(studentRecords),
            onDismiss = { 
                showAnalyticsDialog = false
                selectedExamForAnalytics = null
                examAnalytics = null
            }
        )
    }
    
    // Question details dialog
    if (showDetailDialog && selectedRecord != null) {
        StudentExamDetailsDialog(
            record = selectedRecord!!,
            onDismiss = { showDetailDialog = false },
            onEditGrade = {
                showDetailDialog = false
                showEditDialog = true
            }
        )
    }
    
    // Edit grade dialog
    if (showEditDialog && selectedRecord != null) {
        EditGradeDialog(
            record = selectedRecord!!,
            onDismiss = { showEditDialog = false },
            onSave = { recordId, newScore ->
                coroutineScope.launch {
                    try {
                        val result = hybridExamService.updateStudentExamScore(recordId, newScore)
                        if (result.isSuccess) {
                            // Update the local list
                            studentRecords = studentRecords.map { 
                                if (it.recordId == recordId) it.copy(score = newScore) else it 
                            }
                            
                            // Update the selected record
                            selectedRecord = selectedRecord?.copy(score = newScore)
                            
                            // Close the dialog
                            showEditDialog = false
                            
                            // Notify success
                            // Toast.makeText(context, "Grade updated successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            // Toast.makeText(context, "Failed to update grade", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e("TeacherDashboard", "Error updating grade", e)
                        // Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }
}

// Data class for analytics
data class ExamAnalytics(
    val totalStudents: Int,
    val averageScore: Double,
    val highestScore: Int,
    val lowestScore: Int,
    val averageCompletion: Double,
    val questionAnalytics: List<QuestionAnalytics>,
    val scoreDistribution: Map<String, Int>,
    val commonMistakes: List<CommonMistake>
)

data class QuestionAnalytics(
    val questionNumber: Int,
    val questionText: String,
    val correctAnswers: Int,
    val incorrectAnswers: Int,
    val skippedAnswers: Int,
    val successRate: Double,
    val averagePoints: Double
)

data class CommonMistake(
    val questionText: String,
    val correctAnswer: String,
    val commonWrongAnswer: String,
    val frequency: Int
)

// Missing Composable functions
@Composable
private fun QuestionsListDialog(
    examId: String,
    questions: List<ExamQuestion>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onEditQuestion: (String) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
    Card(
        modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp)
    ) {
        Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                    Text(
                        text = "Exam Questions",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (questions.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No questions found for this exam")
                    }
                } else {
                    LazyColumn {
                        itemsIndexed(questions) { index, question ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onEditQuestion(question.id) },
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                                    modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                                        text = "Question ${index + 1}",
                                        fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                        )
                                    Spacer(modifier = Modifier.height(4.dp))
                        Text(
                                        text = question.text,
                                        fontSize = 16.sp,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (question.options.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        question.options.forEachIndexed { optionIndex, option ->
                    Text(
                                                text = "${('A' + optionIndex)}) $option",
                        fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExamResultsSection(
    examId: String,
    examTitle: String,
    examSubject: String,
    records: List<StudentExamRecord>,
    onRecordClick: (StudentExamRecord) -> Unit,
    onEditGrade: (StudentExamRecord) -> Unit,
    onAnalyticsClick: () -> Unit
    ) {
        Card(
        modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
            modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                Column {
                        Text(
                        text = examTitle,
                        fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                        text = examSubject,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                }
                
                Button(
                    onClick = onAnalyticsClick,
                    modifier = Modifier.size(40.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        Icons.Default.Analytics,
                        contentDescription = "View Analytics",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            LazyColumn {
                items(records) { record ->
                    StudentRecordItem(
                        record = record,
                        onClick = { onRecordClick(record) },
                        onEditGrade = { onEditGrade(record) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun StudentRecordItem(
    record: StudentExamRecord,
    onClick: () -> Unit,
    onEditGrade: () -> Unit
) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
            .clickable { onClick() },
                            colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                    text = record.studentName,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                    text = "Score: ${record.score}%",
                    fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
            }
            
            IconButton(onClick = onEditGrade) {
                    Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit Grade"
                    )
            }
        }
    }
}

@Composable
private fun ExamAnalyticsDialog(
    examId: String?,
    examTitle: String,
    analytics: ExamAnalytics,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                        text = "Analytics: $examTitle",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Summary statistics
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                Text(
                                text = "Summary Statistics",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Total Students: ${analytics.totalStudents}")
                            Text("Average Score: ${String.format("%.1f", analytics.averageScore)}%")
                            Text("Highest Score: ${analytics.highestScore}%")
                            Text("Lowest Score: ${analytics.lowestScore}%")
                        }
                    }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                    // Score distribution
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                Text(
                                text = "Score Distribution",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                Spacer(modifier = Modifier.height(8.dp))
                            analytics.scoreDistribution.forEach { (range, count) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(range)
                                    Text("$count students")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentExamDetailsDialog(
    record: StudentExamRecord,
    onDismiss: () -> Unit,
    onEditGrade: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Student Details",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Column(
                        modifier = Modifier
                            .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text("Student: ${record.studentName}", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text("Exam: ${record.title}", fontSize = 16.sp)
                    Text("Subject: ${record.subject}", fontSize = 16.sp)
                    Text("Score: ${record.score}%", fontSize = 16.sp)
                    Text("Date: ${SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(record.date)}", fontSize = 16.sp)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                Button(
                        onClick = onEditGrade,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Edit Grade")
                    }
                }
            }
        }
    }
}

@Composable
private fun EditGradeDialog(
    record: StudentExamRecord,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val hybridExamService = remember { HybridExamService() }
    var newScore by remember { mutableStateOf(record.score.toString()) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Grade") },
        text = {
            Column {
                Text("Student: ${record.studentName}")
                Text("Current Score: ${record.score}%")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = newScore,
                    onValueChange = { newScore = it },
                    label = { Text("New Score") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val score = newScore.toIntOrNull()
                    if (score != null && score in 0..100) {
                        onSave(record.recordId, score)
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Helper functions
private fun calculateExamAnalytics(records: List<StudentExamRecord>): ExamAnalytics {
    if (records.isEmpty()) {
        return ExamAnalytics(
            totalStudents = 0,
            averageScore = 0.0,
            highestScore = 0,
            lowestScore = 0,
            averageCompletion = 0.0,
            questionAnalytics = emptyList(),
            scoreDistribution = emptyMap(),
            commonMistakes = emptyList()
        )
    }
    
    val scores = records.map { it.score }
    val scoreDistribution = mapOf(
        "90-100%" to scores.count { it >= 90 },
        "80-89%" to scores.count { it in 80..89 },
        "70-79%" to scores.count { it in 70..79 },
        "60-69%" to scores.count { it in 60..69 },
        "Below 60%" to scores.count { it < 60 }
    )
    
    return ExamAnalytics(
        totalStudents = records.size,
        averageScore = scores.average(),
        highestScore = scores.maxOrNull() ?: 0,
        lowestScore = scores.minOrNull() ?: 0,
        averageCompletion = 100.0, // Assuming all records are completed
        questionAnalytics = emptyList(), // Would need detailed question data
        scoreDistribution = scoreDistribution,
        commonMistakes = emptyList() // Would need detailed answer data
    )
}

private fun calculateOverallAnalytics(records: List<StudentExamRecord>): ExamAnalytics {
    return calculateExamAnalytics(records)
}
