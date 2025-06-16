package com.example.ablindexaminer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.activity.compose.BackHandler
import com.example.ablindexaminer.ui.screens.*
import com.example.ablindexaminer.data.firebase.FirebaseExamService
import com.example.ablindexaminer.ui.screens.UserRole

/**
 * Main navigation component for the Blind Examiner application.
 * Centralizes all navigation routes and screen transitions.
 */
@Composable
fun BlindExaminerApp() {
    val navController = rememberNavController()
    val navigationActions = remember(navController) {
        NavigationActions(navController)
    }

    NavHost(
        navController = navController,
        startDestination = "login"
    ) {
        composable("login") {
            LoginScreen(
                onLoginSuccess = { username, role ->
                    when (role) {
                        UserRole.STUDENT -> navigationActions.navigateToStudentDashboard(username)
                        UserRole.TEACHER -> navigationActions.navigateToTeacherDashboard(username)
                        UserRole.ADMIN -> navigationActions.navigateToAdminDashboard(username)
                    }
                }
            )
        }

        // Student routes
        composable(
            route = "student_dashboard/{username}",
            arguments = listOf(navArgument("username") { type = NavType.StringType })
        ) { backStackEntry ->
            val username = backStackEntry.arguments?.getString("username") ?: ""
            // Decode the URL-encoded username
            val decodedUsername = try {
                java.net.URLDecoder.decode(username, "UTF-8")
            } catch (e: Exception) {
                username // Fallback to original if decoding fails
            }
            
            // Handle back button to prevent going to a different dashboard
            BackHandler {
                // This prevents navigation to another dashboard
                // Just show a dialog or toast here instead of navigating
            }
            StudentDashboardScreen(
                username = decodedUsername,
                onLogout = { navigationActions.navigateToLogin() },
                navController = navController,
                onExamSelected = { exam -> 
                    navigationActions.navigateToExam(exam.id, exam.title, exam.duration)
                }
            )
        }

        composable(
            route = "exam-taking/{examId}?type={type}",
            arguments = listOf(
                navArgument("examId") { type = NavType.StringType },
                navArgument("type") { 
                    type = NavType.StringType 
                    defaultValue = "MULTIPLE_CHOICE"
                    nullable = true
                }
            )
        ) { backStackEntry ->
            val examId = backStackEntry.arguments?.getString("examId") ?: ""
            val examType = backStackEntry.arguments?.getString("type") ?: "MULTIPLE_CHOICE"
            
            // Get exam details from Firebase before showing the exam screen
            ExamDetailsFetcher(
                examId = examId,
                onExamDetailsLoaded = { exam ->
                    ExamScreen(
                        examId = exam.id,
                        examTitle = exam.title,
                        duration = exam.duration,
                        examType = examType,
                        onExamComplete = { score, total ->
                            navigationActions.navigateToResults(score, total)
                        }
                    )
                }
            )
        }

        composable(
            route = "result/{score}/{total}",
            arguments = listOf(
                navArgument("score") { type = NavType.IntType },
                navArgument("total") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val score = backStackEntry.arguments?.getInt("score") ?: 0
            val total = backStackEntry.arguments?.getInt("total") ?: 0

            ResultScreen(
                score = score,
                totalPoints = total,
                onDismiss = {
                    navigationActions.popBackToStudentDashboard()
                }
            )
        }

        // Teacher routes
        composable(
            route = "teacher_dashboard/{username}",
            arguments = listOf(navArgument("username") { type = NavType.StringType })
        ) { backStackEntry ->
            val username = backStackEntry.arguments?.getString("username") ?: ""
            // Handle back button to prevent going to a different dashboard
            BackHandler {
                // This prevents navigation to another dashboard
                // Just show a dialog or toast here instead of navigating
            }
            TeacherDashboardScreen(
                username = username,
                onLogout = { navigationActions.navigateToLogin() },
                navController = navController
            )
        }

        // Admin routes
        composable(
            route = "admin_dashboard/{username}",
            arguments = listOf(navArgument("username") { type = NavType.StringType })
        ) { backStackEntry ->
            val username = backStackEntry.arguments?.getString("username") ?: ""
            // Handle back button to prevent going to a different dashboard
            BackHandler {
                // This prevents navigation to another dashboard
                // Just show a dialog or toast here instead of navigating
            }
            AdminDashboardScreen(
                username = username,
                onLogout = { navigationActions.navigateToLogin() },
                navController = navController
            )
        }
        
        // Profile route
        composable(
            route = "profile/{username}/{userRole}",
            arguments = listOf(
                navArgument("username") { type = NavType.StringType },
                navArgument("userRole") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val username = backStackEntry.arguments?.getString("username") ?: ""
            val userRole = backStackEntry.arguments?.getString("userRole") ?: ""
            
            // Use different profile screens based on user role
            if (userRole.equals("Admin", ignoreCase = true)) {
                AdminProfileScreen(
                    navController = navController,
                    username = username
                )
            } else {
            ProfileScreen(
                navController = navController,
                username = username,
                userRole = userRole
            )
            }
        }
        
        // Contact Us route
        composable("contact_us") {
            ContactUsScreen(
                navController = navController
            )
        }

        // Add this route for editing an exam
        composable(
            route = "editExam/{examId}",
            arguments = listOf(navArgument("examId") { type = NavType.StringType })
        ) { backStackEntry ->
            val examId = backStackEntry.arguments?.getString("examId") ?: ""
            // You may need to get the username from a shared ViewModel or navController's previous entry
            // For now, pass an empty string or retrieve as needed
            TeacherExamDetailsScreen(
                examId = examId,
                username = "", // TODO: Pass the actual username if available
                onLogout = { /* TODO: Implement logout if needed */ },
                onNavigateBack = { navController.popBackStack() },
                navController = navController
            )
        }
    }
}

/**
 * Navigation actions to help with routing and ensure popups are handled correctly.
 */
class NavigationActions(private val navController: NavHostController) {
    
    fun navigateToLogin() {
        navController.navigate("login") {
            popUpTo("login") { inclusive = true }
        }
    }
    
    fun navigateToStudentDashboard(username: String) {
        // Use Uri encoding to handle special characters in username
        val encodedUsername = java.net.URLEncoder.encode(username, "UTF-8")
        navController.navigate("student_dashboard/$encodedUsername") {
            popUpTo("login") { inclusive = true }
        }
    }
    
    fun navigateToTeacherDashboard(username: String) {
        val encodedUsername = java.net.URLEncoder.encode(username, "UTF-8")
        navController.navigate("teacher_dashboard/$encodedUsername") {
            popUpTo("login") { inclusive = true }
        }
    }
    
    fun navigateToAdminDashboard(username: String) {
        val encodedUsername = java.net.URLEncoder.encode(username, "UTF-8")
        navController.navigate("admin_dashboard/$encodedUsername") {
            popUpTo("login") { inclusive = true }
        }
    }
    
    fun navigateToExam(examId: String, examTitle: String, duration: Int, examType: String = "MULTIPLE_CHOICE") {
        navController.navigate("exam-taking/$examId?type=$examType")
    }
    
    fun navigateToResults(score: Int, total: Int) {
        navController.navigate("result/$score/$total") {
            popUpTo("exam-taking/{examId}?type={type}") { inclusive = true }
        }
    }
    
    fun navigateToProfile(username: String, userRole: String) {
        val encodedUsername = java.net.URLEncoder.encode(username, "UTF-8")
        navController.navigate("profile/$encodedUsername/$userRole")
    }
    
    fun navigateToContactUs() {
        navController.navigate("contact_us")
    }
    
    fun popBackToStudentDashboard() {
        navController.popBackStack(
            route = "student_dashboard/{username}",
            inclusive = false
        )
    }
}

@Composable
fun ExamDetailsFetcher(
    examId: String,
    onExamDetailsLoaded: @Composable (Exam) -> Unit
) {
    val examService = remember { FirebaseExamService() }
    var exam by remember { mutableStateOf<Exam?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Fetch exam details
    LaunchedEffect(examId) {
        try {
            val examResult = examService.getExamById(examId)
            if (examResult.isSuccess) {
                exam = examResult.getOrNull()
            }
        } catch (e: Exception) {
            // Handle error
        } finally {
            isLoading = false
        }
    }
    
    if (isLoading) {
        LoadingScreen()
    } else if (exam != null) {
        onExamDetailsLoaded(exam!!)
    } else {
        ErrorScreen()
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun ErrorScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Error loading exam. Please try again.")
    }
} 