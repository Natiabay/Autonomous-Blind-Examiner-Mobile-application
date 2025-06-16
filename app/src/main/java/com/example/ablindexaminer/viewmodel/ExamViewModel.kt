package com.example.ablindexaminer.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ablindexaminer.ml.MLModelManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ExamViewModel(context: Context) : ViewModel() {
    private val mlManager = MLModelManager(context)
    
    private val _currentScore = MutableStateFlow(0f)
    val currentScore: StateFlow<Float> = _currentScore

    fun gradeAnswer(studentAnswer: String, teacherAnswer: String, onResult: (Float) -> Unit = {}) {
        viewModelScope.launch {
            val similarity = mlManager.calculateAnswerSimilarity(studentAnswer, teacherAnswer)
            // Convert similarity score (0-1) to grade points (0-10)
            val score = similarity * 10
            _currentScore.value = score
            onResult(score)
        }
    }
    
    suspend fun gradeAnswerSuspend(studentAnswer: String, teacherAnswer: String): Float {
        val similarity = mlManager.calculateAnswerSimilarity(studentAnswer, teacherAnswer)
        // Convert similarity score (0-1) to grade points (0-10)
        val score = similarity * 10
        _currentScore.value = score
        return score
    }

    override fun onCleared() {
        super.onCleared()
        mlManager.close()
    }
}

class ExamViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ExamViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ExamViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
} 