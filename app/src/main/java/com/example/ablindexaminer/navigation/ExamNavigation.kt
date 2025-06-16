package com.example.ablindexaminer.navigation

import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.ablindexaminer.ui.screens.BrailleInputScreen
import com.example.ablindexaminer.viewmodel.ExamViewModel
import com.example.ablindexaminer.viewmodel.ExamViewModelFactory

sealed class ExamScreen(val route: String) {
    object BrailleInput : ExamScreen("braille_input/{question}/{answerKey}")
}

@Composable
fun ExamNavigation(
    navController: NavHostController,
    textToSpeech: TextToSpeech,
    startDestination: String
) {
    val context = LocalContext.current
    val examViewModel: ExamViewModel = viewModel(
        factory = ExamViewModelFactory(context)
    )

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(
            route = ExamScreen.BrailleInput.route
        ) { backStackEntry ->
            val question = backStackEntry.arguments?.getString("question") ?: ""
            val answerKey = backStackEntry.arguments?.getString("answerKey") ?: ""

            BrailleInputScreen(
                onTextChanged = { studentAnswer ->
                    // Handle the text change here if needed
                    // This could update the exam state or be used for validation
                },
                onDismiss = {
                    // Navigate back when the braille input is dismissed
                    navController.popBackStack()
                },
                initialText = "",
                questionText = question
            )
        }
    }
} 