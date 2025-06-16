package com.example.ablindexaminer.ui.screens

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import com.example.ablindexaminer.data.firebase.HybridExamService
import com.example.ablindexaminer.data.model.TeacherExam
import com.example.ablindexaminer.ui.components.AppBarWithDrawer
import com.example.ablindexaminer.ui.components.DateTimePickerDialog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherExamDetailsScreen(
    examId: String,
    username: String,
    onLogout: () -> Unit,
    onNavigateBack: () -> Unit,
    navController: NavController
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val hybridExamService = remember { HybridExamService() }
    
    // Exam details state
    var exam by remember { mutableStateOf<TeacherExam?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Editable fields state
    var title by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("60") }
    var targetYear by remember { mutableStateOf("ALL") }
    var targetSection by remember { mutableStateOf("ALL") }
    var examDate by remember { mutableStateOf(Date()) }
    
    // Schedule publication state
    var showScheduleDialog by remember { mutableStateOf(false) }
    var scheduledDate by remember { mutableStateOf(Date()) } // Default to tomorrow
    var scheduledTime by remember { mutableStateOf("12:00") }
    var isScheduled by remember { mutableStateOf(false) }
    var scheduledDateTime by remember { mutableStateOf<Date?>(null) }
    
    // Date picker state
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    
    // Format for date display
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val timeFormatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    
    // Feedback state
    var showFeedback by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf("") }
    var isErrorFeedback by remember { mutableStateOf(false) }
    
    // Load exam details
    LaunchedEffect(examId) {
        isLoading = true
        try {
            // Implement a getExamById method in HybridExamService
            // For now, we'll simulate loading by using the first exam with matching ID from getTeacherExams
            hybridExamService.getTeacherExams("").collect { teacherExams ->
                val foundExam = teacherExams.find { it.id == examId }
                if (foundExam != null) {
                    exam = foundExam
                    // Initialize editable fields
                    title = foundExam.title
                    subject = foundExam.subject
                    duration = foundExam.duration.toString()
                    targetYear = foundExam.targetYear
                    targetSection = foundExam.targetSection
                    examDate = foundExam.date
                    
                    // Check if exam has scheduled publication
                    // This would require additional fields in the TeacherExam model
                    isScheduled = false // Initialize with default value
                    
                    isLoading = false
                } else {
                    // Exam not found
                    feedbackMessage = "Exam not found. It may have been deleted."
                    isErrorFeedback = true
                    showFeedback = true
                    isLoading = false
                }
            }
        } catch (e: Exception) {
            Log.e("TeacherExamDetails", "Error loading exam: ${e.message}", e)
            feedbackMessage = "Error loading exam: ${e.message}"
            isErrorFeedback = true
            showFeedback = true
            isLoading = false
        }
    }
    
    // Check if exam already has scheduled publication
    LaunchedEffect(exam) {
        val existingExam = exam
        if (existingExam != null) {
            // Check for scheduled publication in the exam data
            val scheduledPublishDate = existingExam.scheduledPublishDate
            if (scheduledPublishDate != null) {
                scheduledDateTime = scheduledPublishDate
                scheduledDate = scheduledPublishDate
                scheduledTime = timeFormatter.format(scheduledPublishDate)
                isScheduled = true
            }
        }
    }
    
    AppBarWithDrawer(
        title = "Edit Exam",
        navController = navController,
        username = username,
        userRole = "Teacher",
        onLogout = onLogout,
        actions = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                // Show loading indicator
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                // Show exam details form
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Heading
                    Text(
                        text = "Edit Exam Details",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                    
                    // Basic details card
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
                            
                            // Target Year Dropdown
                            val yearOptions = listOf("ALL", "1", "2", "3", "4")
                            var yearDropdownExpanded by remember { mutableStateOf(false) }
                            
                            Text(
                                text = "Target Year",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { yearDropdownExpanded = true },
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
                                    expanded = yearDropdownExpanded,
                                    onDismissRequest = { yearDropdownExpanded = false },
                                    modifier = Modifier.fillMaxWidth(0.9f)
                                ) {
                                    yearOptions.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(if (option == "ALL") "All Years" else "Year $option") },
                                            onClick = {
                                                targetYear = option
                                                yearDropdownExpanded = false
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
                            
                            // Target Section Dropdown
                            val sectionOptions = listOf("ALL", "A", "B", "C", "D", "E", "F")
                            var sectionDropdownExpanded by remember { mutableStateOf(false) }
                            
                            Text(
                                text = "Target Section",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { sectionDropdownExpanded = true },
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
                                    expanded = sectionDropdownExpanded,
                                    onDismissRequest = { sectionDropdownExpanded = false },
                                    modifier = Modifier.fillMaxWidth(0.9f)
                                ) {
                                    sectionOptions.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(if (option == "ALL") "All Sections" else "Section $option") },
                                            onClick = {
                                                targetSection = option
                                                sectionDropdownExpanded = false
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
                    
                    // Publication controls card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Publication Controls",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            
                            // Publication status
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 16.dp)
                            ) {
                                Icon(
                                    imageVector = if (exam?.published == true) Icons.Default.CheckCircle else Icons.Default.Schedule,
                                    contentDescription = if (exam?.published == true) "Published" else "Not Published",
                                    tint = if (exam?.published == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                                
                                Spacer(modifier = Modifier.width(8.dp))
                                
                                Text(
                                    text = if (exam?.published == true) "Published" else "Not Published",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            
                            // Show scheduled publication if set
                            if (isScheduled && scheduledDateTime != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = "Scheduled for publication",
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                    
                                    Spacer(modifier = Modifier.width(8.dp))
                                    
                                    Text(
                                        text = "Scheduled for publication on ${dateFormatter.format(scheduledDateTime!!)}" +
                                              " at ${timeFormatter.format(scheduledDateTime!!)}",
                                        fontSize = 16.sp
                                    )
                                }
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Schedule Publication Button
                                Button(
                                    onClick = { showScheduleDialog = true },
                                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                                    enabled = !exam!!.published
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = "Schedule"
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Schedule Publication")
                                }
                                
                                // Publish Now Button
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            try {
                                                val result = hybridExamService.publishExam(examId)
                                                if (result.isSuccess) {
                                                    feedbackMessage = "Exam published successfully!"
                                                    isErrorFeedback = false
                                                    showFeedback = true
                                                } else {
                                                    feedbackMessage = "Failed to publish exam: ${result.exceptionOrNull()?.message ?: "Unknown error"}"
                                                    isErrorFeedback = true
                                                    showFeedback = true
                                                }
                                            } catch (e: Exception) {
                                                feedbackMessage = "Error publishing exam: ${e.message}"
                                                isErrorFeedback = true
                                                showFeedback = true
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                                    enabled = !exam!!.published
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Send,
                                        contentDescription = "Publish"
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Publish Now")
                                }
                            }
                        }
                    }
                    
                    // Save Changes button
                    Button(
                        onClick = {
                            if (exam != null) {
                                // Update exam with edited values
                                val updatedExam = exam!!.copy(
                                    title = title,
                                    subject = subject,
                                    duration = duration.toIntOrNull() ?: 60,
                                    targetYear = targetYear,
                                    targetSection = targetSection
                                )
                                
                                coroutineScope.launch {
                                    val result = hybridExamService.updateExam(updatedExam)
                                    if (result.isSuccess) {
                                        exam = updatedExam
                                        feedbackMessage = "Exam updated successfully."
                                        isErrorFeedback = false
                                        showFeedback = true
                                    } else {
                                        feedbackMessage = "Failed to update exam: ${result.exceptionOrNull()?.message}"
                                        isErrorFeedback = true
                                        showFeedback = true
                                    }
                                }
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
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Changes", fontSize = 16.sp)
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
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isErrorFeedback) Icons.Default.Error else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (isErrorFeedback) 
                                        MaterialTheme.colorScheme.error 
                                    else MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = feedbackMessage,
                                    color = if (isErrorFeedback) 
                                        MaterialTheme.colorScheme.error 
                                    else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Schedule Publication Dialog
    if (showScheduleDialog) {
        AlertDialog(
            onDismissRequest = { showScheduleDialog = false },
            title = { Text("Schedule Publication") },
            text = {
                Column {
                    Text(
                        "Select when this exam should be automatically published to students.",
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    // Use a single button to show the date/time picker
                    OutlinedButton(
                        onClick = { 
                            // Use our new DateTimePickerDialog
                            DateTimePickerDialog(
                                context = context,
                                initialDate = scheduledDateTime ?: Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000), // Default to tomorrow
                                onDateTimeSelected = { selectedDateTime ->
                                    scheduledDateTime = selectedDateTime
                                    scheduledDate = selectedDateTime
                                    scheduledTime = timeFormatter.format(selectedDateTime)
                                    isScheduled = true
                                }
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Select Date and Time"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        if (scheduledDateTime != null) {
                            Text("${dateFormatter.format(scheduledDateTime!!)} at ${timeFormatter.format(scheduledDateTime!!)}")
                        } else {
                            Text("Select Date and Time")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // Save the scheduled publication date to the database
                        if (scheduledDateTime != null) {
                            coroutineScope.launch {
                                try {
                                    val result = hybridExamService.scheduleExamPublication(examId, scheduledDateTime!!)
                                    if (result.isSuccess) {
                                        feedbackMessage = "Exam scheduled for publication on ${dateFormatter.format(scheduledDateTime!!)} at ${timeFormatter.format(scheduledDateTime!!)}"
                                        isErrorFeedback = false
                                        showFeedback = true
                                    } else {
                                        feedbackMessage = "Failed to schedule publication: ${result.exceptionOrNull()?.message ?: "Unknown error"}"
                                        isErrorFeedback = true
                                        showFeedback = true
                                    }
                                } catch (e: Exception) {
                                    feedbackMessage = "Error scheduling publication: ${e.message}"
                                    isErrorFeedback = true
                                    showFeedback = true
                                }
                            }
                        } else {
                            feedbackMessage = "Please select a date and time first"
                            isErrorFeedback = true
                            showFeedback = true
                        }
                        showScheduleDialog = false
                    },
                    enabled = scheduledDateTime != null
                ) {
                    Text("Schedule")
                }
            },
            dismissButton = {
                TextButton(onClick = { showScheduleDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
} 