package com.example.ablindexaminer.ui.screens

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.ablindexaminer.data.firebase.FirebaseAuthService
import com.example.ablindexaminer.ui.components.AppBarWithDrawer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import androidx.compose.ui.graphics.Color
import java.net.URLEncoder
import com.example.ablindexaminer.utils.rememberThemeManager



// User data class
data class User(
    val id: String = "",
    val email: String = "",
    val fullName: String = "",
    val role: UserRole,
    val active: Boolean = true,
    val department: String = "",
    val year: String = "",
    val section: String = "",
    val studentId: String = "",
    val courseAssigned: String = "",
    val assignedSection: String = "",
    val assignedYear: String = ""
)

enum class AdminTab {
    DASHBOARD,
    CREATE_ACCOUNT,
    MANAGE_USERS,
    SETTINGS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    username: String,
    onLogout: () -> Unit,
    navController: NavController
) {
    val context = LocalContext.current
    var users by remember { mutableStateOf<List<User>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf(AdminTab.DASHBOARD) }
    var adminName by remember { mutableStateOf("") }
    val authService = remember { FirebaseAuthService() }
    val coroutineScope = rememberCoroutineScope()

    // Text-to-speech for accessibility
    val textToSpeech = remember { initializeTextToSpeech(context) }

    // Load data on first launch
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            try {
                // Get admin's name
                val userId = authService.getCurrentUserId()
                if (userId != null) {
                    val userDataResult = authService.getUserData(userId)
                    if (userDataResult.isSuccess) {
                        val userData = userDataResult.getOrNull()
                        if (userData != null) {
                            adminName = userData["fullName"] as? String ?: username
                        }
                    }
                }

                // Load users from Firebase
                val result = authService.getAllUsers()
                if (result.isSuccess) {
                    val usersList = result.getOrNull()?.map { userData ->
                        User(
                            id = userData["id"] as? String ?: "",
                            email = userData["email"] as? String ?: "",
                            fullName = userData["fullName"] as? String ?: "",
                            role = when (userData["role"] as? String) {
                                "ADMIN" -> UserRole.ADMIN
                                "TEACHER" -> UserRole.TEACHER
                                else -> UserRole.STUDENT
                            },
                            active = userData["active"] as? Boolean ?: true,
                            department = userData["department"] as? String ?: "",
                            year = userData["year"] as? String ?: "",
                            section = userData["section"] as? String ?: "",
                            studentId = userData["studentId"] as? String ?: "",
                            courseAssigned = userData["courseAssigned"] as? String ?: "",
                            assignedSection = userData["assignedSection"] as? String ?: "",
                            assignedYear = userData["assignedYear"] as? String ?: ""
                        )
                    } ?: emptyList()
                    users = usersList
                }

                speak(textToSpeech, "Welcome $adminName to the Admin Dashboard. Here you can manage users and system settings.")
            } catch (e: Exception) {
                // Handle error
            } finally {
                isLoading = false
            }
        }
    }

    AppBarWithDrawer(
        title = "Admin Dashboard",
        navController = navController,
        username = adminName.ifEmpty { username },
        userRole = "Admin",
        onLogout = onLogout
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Welcome message
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Welcome, ${adminName.ifEmpty { username }}",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics {
                        contentDescription = "Welcome, ${adminName.ifEmpty { username }}"
                    }
                )
            }

            // Tab selection
            ScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Tab(
                    selected = selectedTab == AdminTab.DASHBOARD,
                    onClick = {
                        selectedTab = AdminTab.DASHBOARD
                        speak(textToSpeech, "Dashboard tab")
                    },
                    text = { Text("Dashboard") }
                )
                Tab(
                    selected = selectedTab == AdminTab.CREATE_ACCOUNT,
                    onClick = {
                        selectedTab = AdminTab.CREATE_ACCOUNT
                        speak(textToSpeech, "Create Account tab")
                    },
                    text = { Text("Create Account") }
                )
                Tab(
                    selected = selectedTab == AdminTab.MANAGE_USERS,
                    onClick = {
                        selectedTab = AdminTab.MANAGE_USERS
                        speak(textToSpeech, "Manage Users tab")
                    },
                    text = { Text("Manage Users") }
                )
                Tab(
                    selected = selectedTab == AdminTab.SETTINGS,
                    onClick = {
                        selectedTab = AdminTab.SETTINGS
                        speak(textToSpeech, "Settings tab")
                    },
                    text = { Text("Settings") }
                )
            }

            // Tab content
            when (selectedTab) {
                AdminTab.DASHBOARD -> {
                    DashboardTab(users, textToSpeech)
                }
                AdminTab.CREATE_ACCOUNT -> {
                    CreateAccountTab(authService, textToSpeech)
                }
                AdminTab.MANAGE_USERS -> {
                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        ManageUsersTab(users, authService, textToSpeech)
                    }
                }
                AdminTab.SETTINGS -> {
                    SettingsTab(textToSpeech)
                }
            }
        }
    }
}

@Composable
private fun DashboardTab(
    users: List<User>,
    textToSpeech: TextToSpeech
) {
    val uniqueSections = users
        .mapNotNull { it.section ?: it.assignedSection }
        .distinct()

    val uniqueYears = users
        .mapNotNull { it.year ?: it.assignedYear }
        .distinct()

    val instructorsWithSections = users
        .filter { it.role == UserRole.TEACHER && (it.assignedSection != null || it.assignedYear != null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "System Overview",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Viewing complete system statistics",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // Quick stats
        Text(
            text = "User Statistics",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "Students",
                value = users.count { it.role == UserRole.STUDENT }.toString(),
                backgroundColor = MaterialTheme.colorScheme.primary,
                textColor = MaterialTheme.colorScheme.onPrimary,
                contentDescription = "Total number of students"
            )

            StatCard(
                title = "Instructors",
                value = users.count { it.role == UserRole.TEACHER }.toString(),
                backgroundColor = MaterialTheme.colorScheme.tertiary,
                textColor = MaterialTheme.colorScheme.onTertiary,
                contentDescription = "Total number of instructors"
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "Years",
                value = uniqueYears.size.toString(),
                backgroundColor = MaterialTheme.colorScheme.secondary,
                textColor = MaterialTheme.colorScheme.onSecondary,
                contentDescription = "Total number of years"
            )

            StatCard(
                title = "Sections",
                value = uniqueSections.size.toString(),
                backgroundColor = MaterialTheme.colorScheme.error,
                textColor = MaterialTheme.colorScheme.onError,
                contentDescription = "Total number of sections"
            )
        }

        // Instructor Section Assignments
        if (instructorsWithSections.isNotEmpty()) {
            Text(
                text = "Instructor Assignments",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    instructorsWithSections.forEach { instructor ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = instructor.fullName ?: instructor.email,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Row {
                                    Text(
                                        text = instructor.courseAssigned ?: "No course",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (instructor.department != null) {
                                        Text(
                                            text = " • ${instructor.department}",
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // Display both year and section
                            Column(horizontalAlignment = Alignment.End) {
                                instructor.assignedYear?.let {
                                    ElevatedAssistChip(
                                        onClick = { /* Do nothing */ },
                                        label = {
                                            Text(
                                                text = "Year ${it}",
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontSize = 12.sp
                                            )
                                        },
                                        colors = AssistChipDefaults.elevatedAssistChipColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer
                                        ),
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }

                                instructor.assignedSection?.let {
                                    ElevatedAssistChip(
                                        onClick = { /* Do nothing */ },
                                        label = {
                                            Text(
                                                text = "Section ${it}",
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                fontSize = 12.sp
                                            )
                                        },
                                        colors = AssistChipDefaults.elevatedAssistChipColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                                        )
                                    )
                                }
                            }
                        }

                        if (instructor != instructorsWithSections.last()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }

        // Recent activity
        Text(
            text = "Recent Activity",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                ActivityItem(
                    action = "Admin",
                    user = "logged in",
                    time = getCurrentTimeFormatted()
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                ActivityItem(
                    action = "System",
                    user = "initialized",
                    time = getCurrentTimeFormatted()
                )

                if (instructorsWithSections.isNotEmpty()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    ActivityItem(
                        action = "${instructorsWithSections.size}",
                        user = "instructors assigned to classes",
                        time = getCurrentTimeFormatted()
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityItem(
    action: String,
    user: String,
    time: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Activity dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        )

        Spacer(modifier = Modifier.width(16.dp))

        // Activity details
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "$action by $user",
                fontWeight = FontWeight.Medium
            )

            Text(
                text = time,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    backgroundColor: Color,
    textColor: Color,
    contentDescription: String
) {
    Card(
        modifier = Modifier
            .height(130.dp)
            .fillMaxWidth()
            .semantics {
                this.contentDescription = contentDescription
            },
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = title,
                fontSize = 16.sp,
                color = textColor
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateAccountTab(
    authService: FirebaseAuthService,
    textToSpeech: TextToSpeech
) {
    var selectedRole by remember { mutableStateOf(UserRole.STUDENT) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header with icon and title
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Create New Account",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Add new students or instructors to the system",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // Role selection
        Text(
            text = "Select account type:",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 8.dp),
            color = MaterialTheme.colorScheme.onBackground
        )

        // Custom tab-like selection with buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
                .shadow(
                    elevation = 2.dp,
                    shape = RoundedCornerShape(12.dp)
                )
                .clip(RoundedCornerShape(12.dp))
        ) {
            UserRole.values()
                .filter { it != UserRole.ADMIN } // Exclude admin for regular creation
                .forEachIndexed { index, role ->
                    val isSelected = selectedRole == role
                    val isFirst = index == 0
                    val isLast = index == UserRole.values().size - 2 // -2 because we excluded ADMIN

                    val startShape = if (isFirst) 12.dp else 0.dp
                    val endShape = if (isLast) 12.dp else 0.dp

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(
                                RoundedCornerShape(
                                    topStart = startShape,
                                    bottomStart = startShape,
                                    topEnd = endShape,
                                    bottomEnd = endShape
                                )
                            )
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surface
                            )
                            .clickable {
                                selectedRole = role
                                speak(textToSpeech, "${role.name.lowercase()} selected")
                            }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = when(role) {
                                    UserRole.STUDENT -> "Student"
                                    UserRole.TEACHER -> "Instructor"
                                    else -> "Admin"
                                },
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
        }

        // Form based on selected role
        AnimatedVisibility(
            visible = true,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            when (selectedRole) {
                UserRole.STUDENT -> StudentAccountForm(authService, textToSpeech)
                UserRole.TEACHER -> InstructorAccountForm(authService, textToSpeech)
                else -> {} // Admin not needed here
            }
        }
    }
}

@Composable
private fun StudentAccountForm(
    authService: FirebaseAuthService,
    textToSpeech: TextToSpeech
) {
    var username by remember { mutableStateOf("") } // Username for login
    var password by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("") }
    var studentId by remember { mutableStateOf("") } // Student ID as a separate data point
    var email by remember { mutableStateOf("") } // Internal email for Firebase Auth (optional input)

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp)
    ) {
        Text(
            text = "Student Information",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Input fields as per user request
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username (for login)") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )

        OutlinedTextField(
            value = fullName,
            onValueChange = { fullName = it },
            label = { Text("Full Name") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )

        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it },
            label = { Text("Phone Number") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )

        OutlinedTextField(
            value = year,
            onValueChange = { year = it },
            label = { Text("Year") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )

        OutlinedTextField(
            value = section,
            onValueChange = { section = it },
            label = { Text("Section") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )

        OutlinedTextField(
            value = department,
            onValueChange = { department = it },
            label = { Text("Department") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )

        OutlinedTextField(
            value = studentId,
            onValueChange = { studentId = it },
            label = { Text("Student ID (if applicable)") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )

        // Optional Email field for internal use
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email (Internal Firebase Use - Optional)") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )

        // Error/Success messages
        errorMessage?.let {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .background(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) { Text(
                text = it,
                color = MaterialTheme.colorScheme.error
            )
            }
        }

        successMessage?.let {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) { Text(
                text = it,
                color = MaterialTheme.colorScheme.primary
            )
            }
        }

        // Create account button
        Button(
            onClick = {
                // Basic validation (more can be added)
                if (username.isBlank() || password.isBlank() || fullName.isBlank() ||
                    phoneNumber.isBlank() || year.isBlank() || section.isBlank() || department.isBlank()) {
                    errorMessage = "Please fill in all required fields (Username, Password, Full Name, Phone Number, Year, Section, Department)."
                    successMessage = null
                    speak(textToSpeech, errorMessage!!)
                    return@Button
                }

                isLoading = true
                errorMessage = null
                successMessage = null

                coroutineScope.launch {
                    try {
                        // Call createStudentAccount with all relevant data
                        val result = authService.createStudentAccount(
                            email = email.ifBlank { "${username}@ablindexaminer.com" }, // Use provided email or generate one
                            password = password,
                            username = username, // Use the specified username for login
                            fullName = fullName,
                            year = year,
                            id = username, // Add this line - using username as the ID
                            section = section,
                            department = department,
                            phoneNumber = phoneNumber
                        )

                        if (result.isSuccess) {
                            successMessage = "Student account created successfully for ${username}."
                            speak(textToSpeech, successMessage!!)
                            // Clear form on success
                            username = ""
                            password = ""
                            fullName = ""
                            phoneNumber = ""
                            year = ""
                            section = ""
                            department = ""
                            email = ""
                        } else {
                            errorMessage = "Failed to create student account: ${result.exceptionOrNull()?.message}"
                            speak(textToSpeech, errorMessage!!)
                        }
                    } catch (e: Exception) {
                        errorMessage = "Error creating student account: ${e.message}"
                        speak(textToSpeech, errorMessage!!)
                    } finally {
                        isLoading = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Create Account")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstructorAccountForm(
    authService: FirebaseAuthService,
    textToSpeech: TextToSpeech
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") } // Username for login
    var fullName by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("") }
    var courseAssigned by remember { mutableStateOf("") }
    var assignedSection by remember { mutableStateOf("") }
    var assignedYear by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp)
    ) {
        Text(
            text = "Instructor Information",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username (for login)") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email (Internal/Optional)") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        OutlinedTextField(
            value = fullName,
            onValueChange = { fullName = it },
            label = { Text("Full Name") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it },
            label = { Text("Phone Number") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        OutlinedTextField(
            value = department,
            onValueChange = { department = it },
            label = { Text("Department") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        OutlinedTextField(
            value = courseAssigned,
            onValueChange = { courseAssigned = it },
            label = { Text("Course Assigned") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        OutlinedTextField(
            value = assignedSection,
            onValueChange = { assignedSection = it },
            label = { Text("Assigned Section") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        OutlinedTextField(
            value = assignedYear,
            onValueChange = { assignedYear = it },
            label = { Text("Assigned Year") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        // Error/Success messages
        errorMessage?.let {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .background(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        successMessage?.let {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Create account button
        Button(
            onClick = {
                if (email.isBlank() || password.isBlank() || fullName.isBlank() ||
                    department.isBlank() || courseAssigned.isBlank() || assignedSection.isBlank() ||
                    assignedYear.isBlank() || phoneNumber.isBlank() || username.isBlank()) {
                    errorMessage = "All fields are required"
                    successMessage = null
                    speak(textToSpeech, "All fields are required")
                    return@Button
                }

                isLoading = true
                errorMessage = null
                successMessage = null

                coroutineScope.launch {
                    try {
                        val result = authService.createInstructorAccount(
                            email = email,
                            password = password,
                            username = username,
                            fullName = fullName,
                            department = department,
                            courseAssigned = courseAssigned,
                            assignedSection = assignedSection,
                            assignedYear = assignedYear,
                            phoneNumber = phoneNumber
                        )

                        if (result.isSuccess) {
                            successMessage = "Instructor account created successfully"
                            speak(textToSpeech, "Instructor account created successfully")
                            // Clear form
                            email = ""
                            password = ""
                            username = ""
                            fullName = ""
                            department = ""
                            courseAssigned = ""
                            assignedSection = ""
                            assignedYear = ""
                            phoneNumber = ""
                        } else {
                            errorMessage = "Failed to create account: ${result.exceptionOrNull()?.message}"
                            speak(textToSpeech, "Failed to create account")
                        }
                    } catch (e: Exception) {
                        errorMessage = "Error: ${e.message}"
                        speak(textToSpeech, "Error creating account")
                    } finally {
                        isLoading = false
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(8.dp),
            enabled = !isLoading,
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 4.dp
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(
                    "Create Instructor Account",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ManageUsersTab(
    users: List<User>,
    authService: FirebaseAuthService,
    textToSpeech: TextToSpeech
) {
    val coroutineScope = rememberCoroutineScope()
    var filteredUsers by remember { mutableStateOf(users) }
    var selectedFilter by remember { mutableStateOf("ALL") }
    var showEditUserDialog by remember { mutableStateOf(false) }
    var currentEditingUser by remember { mutableStateOf<User?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var userToDelete by remember { mutableStateOf<User?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Manage Users",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Filter chips
        ScrollableTabRow(
            selectedTabIndex = when(selectedFilter) {
                "STUDENT" -> 1
                "TEACHER" -> 2
                else -> 0
            },
            edgePadding = 0.dp,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            FilterChip(
                selected = selectedFilter == "ALL",
                onClick = {
                    selectedFilter = "ALL"
                    filteredUsers = users
                    speak(textToSpeech, "Showing all users")
                },
                label = { Text("All") },
                modifier = Modifier.padding(end = 8.dp)
            )
            FilterChip(
                selected = selectedFilter == "STUDENT",
                onClick = {
                    selectedFilter = "STUDENT"
                    filteredUsers = users.filter { it.role == UserRole.STUDENT }
                    speak(textToSpeech, "Showing students only")
                },
                label = { Text("Students") },
                modifier = Modifier.padding(end = 8.dp)
            )
            FilterChip(
                selected = selectedFilter == "TEACHER",
                onClick = {
                    selectedFilter = "TEACHER"
                    filteredUsers = users.filter { it.role == UserRole.TEACHER }
                    speak(textToSpeech, "Showing instructors only")
                },
                label = { Text("Instructors") },
                modifier = Modifier.padding(end = 8.dp)
            )
        }

        // Success message
        AnimatedVisibility(
            visible = successMessage != null,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            successMessage?.let {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Success",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Error message
        AnimatedVisibility(
            visible = errorMessage != null,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            errorMessage?.let {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Error,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        if (filteredUsers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No users found",
                    modifier = Modifier.semantics {
                        contentDescription = "No users found"
                    }
                )
            }
        } else {
            LazyColumn {
                items(filteredUsers) { user ->
                    UserCard(
                        user = user,
                        onUserAction = { action, userId ->
                            when (action) {
                                "edit" -> {
                                    speak(textToSpeech, "Editing user ${user.fullName ?: user.email}")
                                    currentEditingUser = user
                                    showEditUserDialog = true
                                }
                                "delete" -> {
                                    speak(textToSpeech, "Confirm deletion of user ${user.fullName ?: user.email}")
                                    userToDelete = user
                                    showDeleteConfirmation = true
                                }
                                "toggle_active" -> {
                                    coroutineScope.launch {
                                        isLoading = true
                                        try {
                                            val newStatus = !user.active
                                            val result = authService.updateUserActiveStatus(userId, newStatus)

                                            if (result.isSuccess) {
                                                // Update local list
                                                filteredUsers = filteredUsers.map {
                                                    if (it.id == userId) it.copy(active = newStatus) else it
                                                }

                                                successMessage = if (newStatus) {
                                                    "User ${user.fullName ?: user.email} activated successfully"
                                                } else {
                                                    "User ${user.fullName ?: user.email} deactivated successfully"
                                                }
                                                speak(textToSpeech, successMessage!!)

                                                // Auto-hide success message after 3 seconds
                                                coroutineScope.launch {
                                                    delay(3000)
                                                    successMessage = null
                                                }
                                            } else {
                                                errorMessage = "Failed to update user status: ${result.exceptionOrNull()?.message}"
                                                speak(textToSpeech, "Failed to update user status")

                                                // Auto-hide error message after 3 seconds
                                                coroutineScope.launch {
                                                    delay(3000)
                                                    errorMessage = null
                                                }
                                            }
                                        } catch (e: Exception) {
                                            errorMessage = "Error: ${e.message}"
                                            speak(textToSpeech, "Error updating user status")

                                            // Auto-hide error message after 3 seconds
                                            coroutineScope.launch {
                                                delay(3000)
                                                errorMessage = null
                                            }
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                }
                            }
                        },
                        textToSpeech = textToSpeech
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }

    // Edit user dialog
    if (showEditUserDialog && currentEditingUser != null) {
        val user = currentEditingUser!!
        var editedFullName by remember { mutableStateOf(user.fullName ?: "") }
        var editedEmail by remember { mutableStateOf(user.email) }
        var editedDepartment by remember { mutableStateOf(user.department ?: "") }
        var editedYear by remember { mutableStateOf(user.year ?: "") }
        var editedSection by remember { mutableStateOf(user.section ?: "") }
        var editedPhoneNumber by remember { mutableStateOf("") }
        var dialogLoading by remember { mutableStateOf(false) }

        // Retrieve phone number for the user
        LaunchedEffect(user.id) {
            try {
                val userDataResult = authService.getUserData(user.id)
                if (userDataResult.isSuccess) {
                    val userData = userDataResult.getOrNull()
                    if (userData != null) {
                        editedPhoneNumber = userData["phoneNumber"] as? String ?: ""
                    }
                }
            } catch (e: Exception) {
                // Ignore errors
            }
        }

        AlertDialog(
            onDismissRequest = {
                if (!dialogLoading) {
                    showEditUserDialog = false
                }
            },
            title = { Text("Edit User") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = editedFullName,
                        onValueChange = { editedFullName = it },
                        label = { Text("Full Name") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )

                    OutlinedTextField(
                        value = editedEmail,
                        onValueChange = { editedEmail = it },
                        label = { Text("Email") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )

                    OutlinedTextField(
                        value = editedPhoneNumber,
                        onValueChange = { editedPhoneNumber = it },
                        label = { Text("Phone Number") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )

                    if (user.role == UserRole.STUDENT) {
                        OutlinedTextField(
                            value = editedYear,
                            onValueChange = { editedYear = it },
                            label = { Text("Year") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )

                        OutlinedTextField(
                            value = editedSection,
                            onValueChange = { editedSection = it },
                            label = { Text("Section") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                    } else if (user.role == UserRole.TEACHER) {
                        OutlinedTextField(
                            value = editedDepartment,
                            onValueChange = { editedDepartment = it },
                            label = { Text("Department") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        dialogLoading = true

                        coroutineScope.launch {
                            try {
                                val updateData = mutableMapOf<String, Any>()
                                updateData["fullName"] = editedFullName
                                updateData["email"] = editedEmail
                                updateData["phoneNumber"] = editedPhoneNumber

                                if (user.role == UserRole.STUDENT) {
                                    updateData["year"] = editedYear
                                    updateData["section"] = editedSection
                                } else if (user.role == UserRole.TEACHER) {
                                    updateData["department"] = editedDepartment
                                }

                                val result = authService.updateUserData(user.id, updateData)

                                if (result.isSuccess) {
                                    // Update the local list
                                    val updatedUser = user.copy(
                                        email = editedEmail,
                                        fullName = editedFullName,
                                        department = if (user.role == UserRole.TEACHER) editedDepartment else user.department,
                                        year = if (user.role == UserRole.STUDENT) editedYear else user.year,
                                        section = if (user.role == UserRole.STUDENT) editedSection else user.section
                                    )

                                    filteredUsers = filteredUsers.map {
                                        if (it.id == user.id) updatedUser else it
                                    }

                                    successMessage = "User ${updatedUser.fullName ?: updatedUser.email} updated successfully"
                                    speak(textToSpeech, successMessage!!)

                                    // Auto-hide success message after 3 seconds
                                    coroutineScope.launch {
                                        delay(3000)
                                        successMessage = null
                                    }
                                } else {
                                    errorMessage = "Failed to update user: ${result.exceptionOrNull()?.message}"
                                    speak(textToSpeech, "Failed to update user")

                                    // Auto-hide error message after 3 seconds
                                    coroutineScope.launch {
                                        delay(3000)
                                        errorMessage = null
                                    }
                                }
                            } catch (e: Exception) {
                                errorMessage = "Error: ${e.message}"
                                speak(textToSpeech, "Error updating user")

                                // Auto-hide error message after 3 seconds
                                coroutineScope.launch {
                                    delay(3000)
                                    errorMessage = null
                                }
                            } finally {
                                dialogLoading = false
                                showEditUserDialog = false
                            }
                        }
                    },
                    enabled = !dialogLoading
                ) {
                    if (dialogLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Save Changes")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showEditUserDialog = false },
                    enabled = !dialogLoading
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete confirmation dialog
    if (showDeleteConfirmation && userToDelete != null) {
        val user = userToDelete!!
        var dialogLoading by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = {
                if (!dialogLoading) {
                    showDeleteConfirmation = false
                }
            },
            title = { Text("Delete User") },
            text = {
                Text("Are you sure you want to delete ${user.fullName ?: user.email}? This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        dialogLoading = true

                        coroutineScope.launch {
                            try {
                                val result = authService.deleteUser(user.id)

                                if (result.isSuccess) {
                                    // Remove from local list
                                    filteredUsers = filteredUsers.filter { it.id != user.id }

                                    successMessage = "User ${user.fullName ?: user.email} deleted successfully"
                                    speak(textToSpeech, successMessage!!)

                                    // Auto-hide success message after 3 seconds
                                    coroutineScope.launch {
                                        delay(3000)
                                        successMessage = null
                                    }
                                } else {
                                    errorMessage = "Failed to delete user: ${result.exceptionOrNull()?.message}"
                                    speak(textToSpeech, "Failed to delete user")

                                    // Auto-hide error message after 3 seconds
                                    coroutineScope.launch {
                                        delay(3000)
                                        errorMessage = null
                                    }
                                }
                            } catch (e: Exception) {
                                errorMessage = "Error: ${e.message}"
                                speak(textToSpeech, "Error deleting user")

                                // Auto-hide error message after 3 seconds
                                coroutineScope.launch {
                                    delay(3000)
                                    errorMessage = null
                                }
                            } finally {
                                dialogLoading = false
                                showDeleteConfirmation = false
                            }
                        }
                    },
                    enabled = !dialogLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    if (dialogLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onError
                        )
                    } else {
                        Text("Delete")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmation = false },
                    enabled = !dialogLoading
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun UserCard(
    user: User,
    onUserAction: (String, String) -> Unit,
    textToSpeech: TextToSpeech
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .semantics {
                contentDescription = "User: ${user.fullName.ifEmpty { user.email }}, Role: ${user.role}"
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (user.active)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // User avatar circle
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(
                            if (user.active)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (user.fullName.firstOrNull() ?: user.email.firstOrNull() ?: "U").toString().uppercase(),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (user.active)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // User details
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = user.fullName.ifEmpty { user.email },
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (user.active)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = user.email,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        // Role chip
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = when(user.role) {
                                UserRole.STUDENT -> MaterialTheme.colorScheme.primaryContainer
                                UserRole.TEACHER -> MaterialTheme.colorScheme.secondaryContainer
                                UserRole.ADMIN -> MaterialTheme.colorScheme.tertiaryContainer
                            },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = user.role.name.capitalize(),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = when(user.role) {
                                    UserRole.STUDENT -> MaterialTheme.colorScheme.onPrimaryContainer
                                    UserRole.TEACHER -> MaterialTheme.colorScheme.onSecondaryContainer
                                    UserRole.ADMIN -> MaterialTheme.colorScheme.onTertiaryContainer
                                }
                            )
                        }

                        if (user.role == UserRole.STUDENT) {
                            if (user.year.isNotEmpty() && user.section.isNotEmpty()) {
                                Text(
                                    text = "Year ${user.year}, Section ${user.section}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else if (user.role == UserRole.TEACHER && user.department.isNotEmpty()) {
                            Text(
                                text = "Department: ${user.department}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Action buttons
                Row {
                    // Edit button
                    IconButton(
                        onClick = {
                            onUserAction("edit", user.id)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit User",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Delete button
                    IconButton(
                        onClick = {
                            onUserAction("delete", user.id)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete User",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Status indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (user.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = if (user.active) "Active" else "Inactive",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Active/Inactive toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = if (user.active) "Deactivate" else "Activate",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )

                    Switch(
                        checked = user.active,
                        onCheckedChange = {
                            onUserAction("toggle_active", user.id)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
    ) {
        Text(
            text = "$label: ",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SettingsTab(textToSpeech: TextToSpeech) {
    val context = LocalContext.current
    val themeManager = rememberThemeManager(context)
    
    // Get current states from ThemeManager
    val isDarkModeEnabled by themeManager.isDarkMode
    val isVoiceGuidanceEnabled by themeManager.isVoiceGuidanceEnabled
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "System Settings",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(bottom = 16.dp)
                .semantics {
                    contentDescription = "System Settings"
                }
        )

        // Voice guidance for teachers and admin only
        SwitchSettingItem(
            title = "Enable Voice Guidance",
            description = "Voice guidance for teachers and administrators only",
            initialValue = isVoiceGuidanceEnabled,
            onValueChange = { newValue ->
                themeManager.setVoiceGuidance(newValue)
                if (newValue) {
                    speak(textToSpeech, "Voice guidance enabled for teachers and administrators")
                } else {
                    speak(textToSpeech, "Voice guidance disabled for teachers and administrators")
                }
            }
        )

        // Dark mode setting
        SwitchSettingItem(
            title = "Enable Dark Mode", 
            description = "Switch to dark theme for better visibility. Restart app to see changes.",
            initialValue = isDarkModeEnabled,
            onValueChange = { newValue ->
                themeManager.setDarkMode(newValue)
                val message = if (newValue) "Dark mode enabled. Please restart the app to see changes." else "Dark mode disabled. Please restart the app to see changes."
                if (isVoiceGuidanceEnabled) {
                    speak(textToSpeech, message)
                }
            }
        )
    }
}

@Composable
private fun SwitchSettingItem(
    title: String,
    description: String,
    initialValue: Boolean,
    onValueChange: (Boolean) -> Unit
) {
    var isChecked by remember { mutableStateOf(initialValue) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .semantics {
                contentDescription = "$title: ${if (isChecked) "enabled" else "disabled"}"
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                )
                Text(
                    text = description,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = isChecked,
                onCheckedChange = {
                    isChecked = it
                    onValueChange(it)
                }
            )
        }
    }
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

private fun String.capitalize(): String {
    return this.replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
    }
}

private fun getCurrentTimeFormatted(): String {
    val calendar = Calendar.getInstance()
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val minute = calendar.get(Calendar.MINUTE)
    return String.format("%02d:%02d", hour, minute)
}

@Composable
private fun CreateAccountForm(
    onAccountCreated: (String, String, UserRole) -> Unit,
    textToSpeech: TextToSpeech
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(UserRole.STUDENT) }
    var department by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("1") }
    var section by remember { mutableStateOf("A") }
    var error by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = "Create New Account",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Error message
            if (error.isNotEmpty()) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // Basic info
            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = { Text("Full Name") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Text(if (passwordVisible) "Hide" else "Show")
                    }
                }
            )

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("Confirm Password") },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )

            // Role selection
            Text(
                text = "User Role",
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                RoleSelectionButton(
                    selected = role == UserRole.STUDENT,
                    label = "Student",
                    onClick = { role = UserRole.STUDENT }
                )
                RoleSelectionButton(
                    selected = role == UserRole.TEACHER,
                    label = "Teacher",
                    onClick = { role = UserRole.TEACHER }
                )
                RoleSelectionButton(
                    selected = role == UserRole.ADMIN,
                    label = "Admin",
                    onClick = { role = UserRole.ADMIN }
                )
            }

            // Role specific fields
            when (role) {
                UserRole.STUDENT -> {
                    Text(
                        text = "Student Information",
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = year,
                            onValueChange = { year = it },
                            label = { Text("Year") },
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = section,
                            onValueChange = { section = it },
                            label = { Text("Section") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                UserRole.TEACHER -> {
                    Text(
                        text = "Teacher Information",
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    OutlinedTextField(
                        value = department,
                        onValueChange = { department = it },
                        label = { Text("Department") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                UserRole.ADMIN -> {
                    // No additional fields for admin
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Submit button
            Button(
                onClick = {
                    if (validateForm(email, password, confirmPassword, fullName, role)) {
                        speak(textToSpeech, "Creating account for $fullName")
                        onAccountCreated(email, password, role)
                        // Reset form
                        email = ""
                        password = ""
                        confirmPassword = ""
                        fullName = ""
                        role = UserRole.STUDENT
                        department = ""
                        year = "1"
                        section = "A"
                        error = ""
                    } else {
                        error = "Please complete all fields correctly. Ensure passwords match and are at least 6 characters."
                        speak(textToSpeech, error)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Create Account")
            }
        }
    }
}

@Composable
private fun RoleSelectionButton(
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .padding(4.dp)
            .size(width = 100.dp, height = 48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Text(label)
    }
}

private fun validateForm(
    email: String,
    password: String,
    confirmPassword: String,
    fullName: String,
    role: UserRole
): Boolean {
    return email.isNotBlank() &&
            password.isNotBlank() &&
            password.length >= 6 &&
            password == confirmPassword &&
            fullName.isNotBlank()
}