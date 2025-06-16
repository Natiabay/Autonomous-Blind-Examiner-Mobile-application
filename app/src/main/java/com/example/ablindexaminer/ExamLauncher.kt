package com.example.ablindexaminer

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.example.ablindexaminer.ui.security.ExamSecurityManager

/**
 * Central class to handle launching exams in secure mode
 * This intercepts all exam launches in the application
 */
object ExamLauncher {
    
    // Get the security manager
    private val securityManager = ExamSecurityManager.getInstance()
    
    /**
     * Launch an exam in secure lockdown mode
     * This should be called anywhere an exam needs to be started
     */
    fun launchExam(
        context: Context,
        examId: String,
        examTitle: String,
        duration: Int,
        examType: String = "MULTIPLE_CHOICE"
    ) {
        when (context) {
            is Activity -> {
                // Launch the secure exam directly through the security manager
                securityManager.launchSecureExam(
                    activity = context,
                    examId = examId,
                    examTitle = examTitle,
                    duration = duration,
                    examType = examType,
                    examResultLauncher = null
                )
            }
            else -> {
                // Log an error - we need an activity context
                android.util.Log.e("ExamLauncher", "Failed to launch exam - non-activity context provided")
                throw IllegalArgumentException("ExamLauncher requires an Activity context to launch exams")
            }
        }
    }
}

/**
 * Composition local provider for the exam launcher
 * Use this in composables to get the exam launcher
 */
val LocalExamLauncher = staticCompositionLocalOf<ExamLauncher> {
    error("No ExamLauncher provided")
} 