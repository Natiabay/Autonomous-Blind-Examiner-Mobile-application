package com.example.ablindexaminer.data.model

import java.util.*

/**
 * Model representing an exam created by a teacher
 */
data class TeacherExam(
    val id: String = "",
    val title: String = "",
    val subject: String = "",
    val duration: Int = 60, // in minutes
    val questionCount: Int = 0,
    val date: Date = Date(), // Current date by default
    val published: Boolean = false,
    val teacherId: String = "",
    val targetYear: String = "ALL",
    val targetSection: String = "ALL",
    val scheduledPublishDate: Date? = null
) 