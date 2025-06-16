package com.example.ablindexaminer.data.model

/**
 * Model representing a question in an exam
 */
data class Question(
    val id: String = "",
    val text: String = "",
    val options: List<String>? = null, // For multiple choice, null for short answer
    val correctAnswer: String = "",
    val type: String = "MULTIPLE_CHOICE", // Default to multiple choice, but can be any QuestionType name
    val points: Int = 1,
    val number: Int = 0 // Question sequence number
) 