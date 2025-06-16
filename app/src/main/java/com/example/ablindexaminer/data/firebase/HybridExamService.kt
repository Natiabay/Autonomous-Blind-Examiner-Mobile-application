package com.example.ablindexaminer.data.firebase

import android.util.Log
import com.example.ablindexaminer.ui.screens.Exam
import com.example.ablindexaminer.ui.screens.StudentExamRecord
import com.example.ablindexaminer.ui.screens.QuestionAttempt
import com.example.ablindexaminer.data.model.TeacherExam
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * A service that exclusively uses Firestore for all database operations
 * (Previously was a hybrid service that used both Firestore and Realtime Database)
 */
class HybridExamService {
    private val TAG = "HybridExamService"
    
    // Firestore references
    private val db = FirebaseFirestore.getInstance()
    private val firestoreExamsCollection = db.collection("exams")
    private val firestoreQuestionsCollection = db.collection("questions")
    private val examRecordsCollection = db.collection("examRecords")
    
    init {
        Log.d(TAG, "Initializing service with Firestore collections only")
    }
    
    /**
     * Get all published exams
     */
    fun getPublishedExams(): Flow<List<Exam>> = callbackFlow {
        Log.d(TAG, "Getting all published exams from Firestore")
        
        val firestoreListener = firestoreExamsCollection
            .whereEqualTo("published", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Firestore listen error", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                } 
                
                if (snapshot != null) {
                    val exams = mutableListOf<Exam>()
                    for (doc in snapshot.documents) {
                        try {
                            val examData = doc.data
                            if (examData != null) {
                                exams.add(
                                    Exam(
                                        id = doc.id,
                                        title = examData["title"] as? String ?: "",
                                        subject = examData["subject"] as? String ?: "",
                                        duration = (examData["duration"] as? Long)?.toInt() ?: 60,
                                        questionCount = (examData["questionCount"] as? Long)?.toInt() ?: 0,
                                        date = parseDate(examData["date"] as? String),
                                        type = examData["type"] as? String ?: "MULTIPLE_CHOICE",
                                        targetYear = examData["targetYear"] as? String ?: "ALL",
                                        targetSection = examData["targetSection"] as? String ?: "ALL"
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing Firestore exam", e)
                        }
                    }
                    
                    Log.d(TAG, "Firestore returned ${exams.size} exams")
                    trySend(exams)
                } else {
                    trySend(emptyList())
                }
            }
            
        awaitClose {
            firestoreListener.remove()
        }
    }

    /**
     * Create a new exam in Firestore
     */
    suspend fun createExam(exam: TeacherExam, userId: String): Result<String> {
        try {
            Log.d(TAG, "Creating exam: ${exam.title} in Firestore")
            
            // Default to MULTIPLE_CHOICE as the question type when no questions are available
            val dominantQuestionType = "MULTIPLE_CHOICE"
            
            // Convert the exam to a map for Firestore
            val examMap = hashMapOf(
                "title" to exam.title,
                "subject" to exam.subject,
                "duration" to exam.duration,
                "questionCount" to exam.questionCount,
                "date" to SimpleDateFormat("yyyy-MM-dd", Locale.US).format(exam.date),
                "published" to exam.published,
                "teacherId" to userId,
                "targetYear" to exam.targetYear,
                "targetSection" to exam.targetSection,
                "type" to dominantQuestionType, // Store the dominant question type
                "createdAt" to com.google.firebase.Timestamp.now(),
                "scheduledPublishDate" to exam.scheduledPublishDate
            )
            
            // Save to Firestore
            val firestoreDocRef = if (exam.id.isBlank()) {
                firestoreExamsCollection.add(examMap).await()
            } else {
                firestoreExamsCollection.document(exam.id).set(examMap).await()
                firestoreExamsCollection.document(exam.id)
            }
            
            val examId = firestoreDocRef.id
            Log.d(TAG, "Exam saved to Firestore with ID: $examId")
            
            return Result.success(examId)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating exam", e)
            return Result.failure(e)
        }
    }

    /**
     * Parse date string from Firebase to Date object
     */
    private fun parseDate(dateStr: String?): Date {
        if (dateStr.isNullOrEmpty()) {
            return Date()
        }
        
        return try {
            // Try different date formats
            val formats = listOf(
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
                SimpleDateFormat("yyyy-MM-dd", Locale.US),
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            )
            
            for (format in formats) {
                try {
                    return format.parse(dateStr) ?: Date()
                } catch (e: Exception) {
                    // Try next format
                }
            }
            
            Date()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing date: $dateStr", e)
            Date()
        }
    }
    
    /**
     * Update an existing exam in Firestore
     */
    suspend fun updateExam(exam: TeacherExam): Result<Boolean> {
        if (exam.id.isBlank()) {
            return Result.failure(Exception("Exam ID cannot be blank for updates"))
        }
        
        try {
            Log.d(TAG, "Updating exam ${exam.id}: ${exam.title} in Firestore")
            
            // Convert the exam to a map for Firestore
            val examMap = hashMapOf(
                "title" to exam.title,
                "subject" to exam.subject,
                "duration" to exam.duration,
                "questionCount" to exam.questionCount,
                "date" to SimpleDateFormat("yyyy-MM-dd", Locale.US).format(exam.date),
                "published" to exam.published,
                "teacherId" to exam.teacherId,
                "targetYear" to exam.targetYear,
                "targetSection" to exam.targetSection,
                "updatedAt" to com.google.firebase.Timestamp.now(),
                "scheduledPublishDate" to exam.scheduledPublishDate
            )
            
            // Update in Firestore
            firestoreExamsCollection.document(exam.id).update(examMap as Map<String, Any>).await()
            Log.d(TAG, "Exam updated in Firestore: ${exam.id}")
            
            return Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating exam", e)
            return Result.failure(e)
        }
    }
    
    /**
     * Get questions for an exam, with improved duplicate detection
     */
    suspend fun getQuestionsForExam(examId: String): Result<List<com.example.ablindexaminer.ui.screens.ExamQuestion>> {
        return try {
            Log.d(TAG, "Fetching questions for exam: $examId from Firestore")
            
            // Get questions from Firestore
            val questionsByRef = HashMap<String, com.example.ablindexaminer.ui.screens.ExamQuestion>()
            val questionsSnapshot = firestoreExamsCollection.document(examId)
                .collection("questions")
                .get()
                .await()
                
            // Process Firestore questions with duplicate prevention
            for (doc in questionsSnapshot.documents) {
                val questionId = doc.id
                val questionData = doc.data
                if (questionData != null && !questionsByRef.containsKey(questionId)) {
                    val number = (questionData["number"] as? Long)?.toInt() ?: 0
                    val text = questionData["text"] as? String ?: ""
                    val options = questionData["options"] as? List<String>
                    val correctAnswer = questionData["correctAnswer"] as? String ?: ""
                    val typeStr = questionData["type"] as? String
                    val points = (questionData["points"] as? Long)?.toInt() ?: 1
                    
                    val type = when (typeStr) {
                        "MULTIPLE_CHOICE" -> com.example.ablindexaminer.ui.screens.QuestionType.MULTIPLE_CHOICE
                        "TRUE_FALSE" -> com.example.ablindexaminer.ui.screens.QuestionType.TRUE_FALSE
                        "SHORT_ANSWER" -> com.example.ablindexaminer.ui.screens.QuestionType.SHORT_ANSWER
                        "FILL_IN_THE_BLANK" -> com.example.ablindexaminer.ui.screens.QuestionType.FILL_IN_THE_BLANK
                        "MATCHING" -> com.example.ablindexaminer.ui.screens.QuestionType.MATCHING
                        "ESSAY" -> com.example.ablindexaminer.ui.screens.QuestionType.ESSAY
                        else -> com.example.ablindexaminer.ui.screens.QuestionType.MULTIPLE_CHOICE
                    }
                    
                    val question = com.example.ablindexaminer.ui.screens.ExamQuestion(
                        id = questionId,
                        text = text,
                        options = options ?: emptyList(),
                        correctAnswer = correctAnswer,
                        type = type,
                        points = points,
                        number = number
                    )
                    
                    questionsByRef[questionId] = question
                }
            }
            
            // Look for additional questions in the main questions collection
            val additionalQuestionsSnapshot = firestoreQuestionsCollection
                .whereEqualTo("examId", examId)
                .get()
                .await()
                
            for (doc in additionalQuestionsSnapshot.documents) {
                val questionId = doc.id
                val questionData = doc.data
                if (questionData != null && !questionsByRef.containsKey(questionId)) {
                    val number = (questionData["number"] as? Long)?.toInt() ?: 0
                    val text = questionData["text"] as? String ?: ""
                    val options = questionData["options"] as? List<String>
                    val correctAnswer = questionData["correctAnswer"] as? String ?: ""
                    val typeStr = questionData["type"] as? String
                    val points = (questionData["points"] as? Long)?.toInt() ?: 1
                    
                    val type = when (typeStr) {
                        "MULTIPLE_CHOICE" -> com.example.ablindexaminer.ui.screens.QuestionType.MULTIPLE_CHOICE
                        "TRUE_FALSE" -> com.example.ablindexaminer.ui.screens.QuestionType.TRUE_FALSE
                        "SHORT_ANSWER" -> com.example.ablindexaminer.ui.screens.QuestionType.SHORT_ANSWER
                        "FILL_IN_THE_BLANK" -> com.example.ablindexaminer.ui.screens.QuestionType.FILL_IN_THE_BLANK
                        "MATCHING" -> com.example.ablindexaminer.ui.screens.QuestionType.MATCHING
                        "ESSAY" -> com.example.ablindexaminer.ui.screens.QuestionType.ESSAY
                        else -> com.example.ablindexaminer.ui.screens.QuestionType.MULTIPLE_CHOICE
                    }
                    
                    val question = com.example.ablindexaminer.ui.screens.ExamQuestion(
                        id = questionId,
                        text = text,
                        options = options ?: emptyList(),
                        correctAnswer = correctAnswer,
                        type = type,
                        points = points,
                        number = number
                    )
                    
                    questionsByRef[questionId] = question
                }
            }
            
            // Convert to list and sort by question number
            val sortedQuestions = questionsByRef.values.toList().sortedBy { it.number }
            
            // Update the exam type based on the questions
            if (sortedQuestions.isNotEmpty()) {
                updateExamType(examId, sortedQuestions)
            }
            
            Log.d(TAG, "Found ${sortedQuestions.size} questions for exam $examId from Firestore")
            Result.success(sortedQuestions)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting questions for exam", e)
            Result.failure(e)
        }
    }

    /**
     * Determine the overall exam type based on the question types
     */
    fun determineExamType(questions: List<com.example.ablindexaminer.ui.screens.ExamQuestion>): String {
        if (questions.isEmpty()) {
            return "MULTIPLE_CHOICE" // Default
        }
        
        // Check if there's only one type of question
        val questionTypes = questions.map { it.type }.toSet()
        
        return if (questionTypes.size == 1) {
            // All questions are of the same type
            questionTypes.first().name
        } else {
            // Mixed question types
            "MIXED"
        }
    }
    
    /**
     * Update the exam type based on its questions
     */
    private suspend fun updateExamType(examId: String, questions: List<com.example.ablindexaminer.ui.screens.ExamQuestion>) {
        try {
            val examType = determineExamType(questions)
            
            // Update exam type in Firestore
            firestoreExamsCollection.document(examId)
                .update("type", examType)
                .await()
                
            Log.d(TAG, "Updated exam type to $examType for exam $examId")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating exam type", e)
        }
    }

    /**
     * Add a question to an exam in Firestore
     */
    suspend fun addQuestionToExam(examId: String, question: com.example.ablindexaminer.data.model.Question): Result<String> {
        try {
            Log.d(TAG, "Adding question to exam $examId in Firestore")
            
            // Create question data for Firestore
            val questionData = hashMapOf(
                "examId" to examId,
                "text" to question.text,
                "options" to question.options,
                "correctAnswer" to question.correctAnswer,
                "type" to question.type,
                "number" to question.number,
                "points" to question.points,
                "createdAt" to com.google.firebase.Timestamp.now()
            )
            
            // Save to Firestore exam subcollection
            val firestoreRef = firestoreExamsCollection
                .document(examId)
                .collection("questions")
                .add(questionData)
                .await()
                
            val questionId = firestoreRef.id
            Log.d(TAG, "Question saved to Firestore with ID: $questionId")
            
            // Get all questions to update the exam type
            val questionsResult = getQuestionsForExam(examId)
            if (questionsResult.isSuccess) {
                val questions = questionsResult.getOrNull() ?: emptyList()
                updateExamType(examId, questions)
            }
            
            return Result.success(questionId)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding question to exam", e)
            return Result.failure(e)
        }
    }

    /**
     * Publish an exam (set published = true) in Firestore
     */
    suspend fun publishExam(examId: String): Result<Boolean> {
        try {
            Log.d(TAG, "Publishing exam $examId in Firestore")
            
            // Update published flag in Firestore
            firestoreExamsCollection.document(examId)
                .update(
                    mapOf(
                        "published" to true,
                        "scheduledPublishDate" to null
                    )
                )
                .await()
            
            Log.d(TAG, "Exam published in Firestore: $examId")
            return Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error publishing exam", e)
            return Result.failure(e)
        }
    }

    /**
     * Unpublish an exam (set published = false) in Firestore
     */
    suspend fun unpublishExam(examId: String): Result<Boolean> {
        try {
            Log.d(TAG, "Unpublishing exam $examId in Firestore")
            
            // Update published flag to false in Firestore
            firestoreExamsCollection.document(examId)
                .update("published", false)
                .await()
                
            Log.d(TAG, "Exam $examId unpublished successfully")
            return Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error unpublishing exam", e)
            return Result.failure(e)
        }
    }

    /**
     * Schedule exam publication by setting scheduledPublishDate in Firestore
     */
    suspend fun scheduleExamPublication(examId: String, scheduledDate: Date): Result<Boolean> {
        try {
            Log.d(TAG, "Scheduling exam $examId for publication on $scheduledDate")
            
            // Update scheduledPublishDate in Firestore
            firestoreExamsCollection.document(examId)
                .update(
                    mapOf(
                        "scheduledPublishDate" to scheduledDate,
                        "published" to false
                    )
                )
                .await()
            
            Log.d(TAG, "Exam scheduled for publication in Firestore: $examId")
            return Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling exam publication", e)
            return Result.failure(e)
        }
    }

    /**
     * Update exam publish status
     */
    suspend fun updateExamPublishStatus(examId: String, published: Boolean): Result<Boolean> {
        try {
            Log.d(TAG, "Setting exam $examId publish status to $published")
            
            firestoreExamsCollection.document(examId)
                .update("published", published)
                .await()
                
            Log.d(TAG, "Successfully updated exam publish status")
            return Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating exam publish status", e)
            return Result.failure(e)
        }
    }

    /**
     * Delete an exam and its questions
     */
    suspend fun deleteExam(examId: String): Result<Boolean> {
        return try {
            Log.d(TAG, "Deleting exam: $examId")
            
            // First, delete all questions in the exam subcollection
            val questionsSnapshot = firestoreExamsCollection.document(examId)
                .collection("questions")
                .get()
                .await()
                
            // Delete each question document
            for (doc in questionsSnapshot.documents) {
                firestoreExamsCollection.document(examId)
                    .collection("questions")
                    .document(doc.id)
                    .delete()
                    .await()
            }
            
            // Also delete any questions in the main questions collection
            val additionalQuestionsSnapshot = firestoreQuestionsCollection
                .whereEqualTo("examId", examId)
                .get()
                .await()
                
            for (doc in additionalQuestionsSnapshot.documents) {
                firestoreQuestionsCollection.document(doc.id)
                    .delete()
                    .await()
            }
            
            // Finally, delete the exam document
            firestoreExamsCollection.document(examId)
                .delete()
                .await()
                
            Log.d(TAG, "Exam and associated questions deleted successfully")
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting exam", e)
            Result.failure(e)
        }
    }

    /**
     * Get all exams created by a specific teacher
     */
    fun getTeacherExams(teacherId: String): Flow<List<TeacherExam>> = callbackFlow {
        Log.d(TAG, "Getting exams for teacher $teacherId from Firestore")
        
        val query = if (teacherId.isBlank()) {
            firestoreExamsCollection
        } else {
            firestoreExamsCollection.whereEqualTo("teacherId", teacherId)
        }
        
        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Firestore listen error", error)
                trySend(emptyList())
                return@addSnapshotListener
            }
            
            if (snapshot != null) {
                val exams = mutableListOf<TeacherExam>()
                for (doc in snapshot.documents) {
                    try {
                        val data = doc.data
                        if (data != null) {
                            val id = doc.id
                            val title = data["title"] as? String ?: ""
                            val subject = data["subject"] as? String ?: ""
                            val duration = (data["duration"] as? Long)?.toInt() ?: 60
                            val questionCount = (data["questionCount"] as? Long)?.toInt() ?: 0
                            val dateStr = data["date"] as? String
                            val published = data["published"] as? Boolean ?: false
                            val teacherId = data["teacherId"] as? String ?: ""
                            val targetYear = data["targetYear"] as? String ?: "ALL"
                            val targetSection = data["targetSection"] as? String ?: "ALL"
                            val scheduledPublishTimestamp = data["scheduledPublishDate"] as? com.google.firebase.Timestamp
                            
                            // Parse scheduled publish date if present
                            val scheduledPublishDate = scheduledPublishTimestamp?.toDate()
                            
                            exams.add(
                                TeacherExam(
                                    id = id,
                                    title = title,
                                    subject = subject,
                                    duration = duration,
                                    questionCount = questionCount,
                                    date = parseDate(dateStr),
                                    published = published,
                                    teacherId = teacherId,
                                    targetYear = targetYear,
                                    targetSection = targetSection,
                                    scheduledPublishDate = scheduledPublishDate
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing teacher exam", e)
                    }
                }
                
                Log.d(TAG, "Found ${exams.size} exams for teacher $teacherId")
                trySend(exams)
            } else {
                trySend(emptyList())
            }
        }
        
        awaitClose {
            listener.remove()
        }
    }
    
    /**
     * Get student exam records from Firestore
     */
    suspend fun getStudentExamRecords(studentId: String): Result<List<com.example.ablindexaminer.ui.screens.StudentExamRecord>> {
        return try {
            Log.d(TAG, "Fetching exam records for student: $studentId")
            
            // Fetch from Firestore using the studentId field
            val examRecordsSnapshot = examRecordsCollection
                .whereEqualTo("studentId", studentId)
                .get()
                .await()
                
            val examRecords = mutableListOf<com.example.ablindexaminer.ui.screens.StudentExamRecord>()
            
            if (examRecordsSnapshot.isEmpty) {
                Log.d(TAG, "No exam records found for student: $studentId")
                return Result.success(emptyList())
            }
            
            for (doc in examRecordsSnapshot.documents) {
                try {
                    val data = doc.data ?: continue
                    
                    // Extract basic record data
                    val examId = data["examId"] as? String ?: ""
                    val title = data["examTitle"] as? String ?: ""
                    val subject = data["subject"] as? String ?: ""
                    
                    // Handle both Timestamp and String date formats
                    val completedDate = when {
                        data["completedDate"] is com.google.firebase.Timestamp -> {
                            (data["completedDate"] as? com.google.firebase.Timestamp)?.toDate() ?: Date()
                        }
                        data["completedDate"] is String -> {
                            parseDate(data["completedDate"] as String)
                        }
                        else -> Date()
                    }
                    
                    val score = (data["score"] as? Long)?.toInt() ?: 0
                    val totalPoints = (data["totalPoints"] as? Long)?.toInt() ?: 0
                    val answered = (data["answered"] as? Long)?.toInt() ?: 0
                    val totalQuestions = (data["totalQuestions"] as? Long)?.toInt() ?: 0
                    val recordId = doc.id  // Store document ID for later reference
                    
                    // Get question details
                    val questionDetails = mutableListOf<com.example.ablindexaminer.ui.screens.QuestionAttempt>()
                    val questionsData = data["questions"] as? List<Map<String, Any>> ?: emptyList()
                    
                    for (questionData in questionsData) {
                        val questionId = questionData["id"] as? String ?: ""
                        val questionText = questionData["text"] as? String ?: ""
                        val correctAnswer = questionData["correctAnswer"] as? String ?: ""
                        val studentAnswer = questionData["studentAnswer"] as? String ?: ""
                        val isCorrect = questionData["isCorrect"] as? Boolean ?: false
                        val questionType = questionData["type"] as? String ?: "MULTIPLE_CHOICE"
                        val points = (questionData["points"] as? Long)?.toInt() ?: 1
                        
                        // Get options if available
                        val options = questionData["options"] as? List<String>
                        
                        questionDetails.add(
                            com.example.ablindexaminer.ui.screens.QuestionAttempt(
                                id = questionId,
                                questionText = questionText,
                                correctAnswer = correctAnswer,
                                studentAnswer = studentAnswer,
                                isCorrect = isCorrect,
                                type = questionType,
                                options = options,
                                points = points
                            )
                        )
                    }
                    
                    // Create and add the student record
                    val studentRecord = com.example.ablindexaminer.ui.screens.StudentExamRecord(
                        id = examId,
                        title = title,
                        subject = subject,
                        date = completedDate,
                        score = score,
                        totalPoints = totalPoints,
                        answered = answered,
                        totalQuestions = totalQuestions,
                        questionDetails = questionDetails,
                        studentName = "",  // Not needed for student's own records
                        studentId = studentId,
                        recordId = recordId
                    )
                    
                    examRecords.add(studentRecord)
                    Log.d(TAG, "Added exam record: $title for student: $studentId")
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing exam record", e)
                }
            }
            
            // Sort by most recent first
            val sortedRecords = examRecords.sortedByDescending { it.date }
            Log.d(TAG, "Found ${sortedRecords.size} exam records for student: $studentId")
            Result.success(sortedRecords)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting student exam records", e)
            Result.failure(e)
        }
    }

    /**
     * Save student exam record to Firestore
     */
    suspend fun saveStudentExamRecord(record: com.example.ablindexaminer.ui.screens.StudentExamRecord, studentId: String): Result<Boolean> {
        return try {
            Log.d(TAG, "Saving exam record for student: $studentId, exam: ${record.title}")
            
            // Convert question details to map
            val questionDetails = record.questionDetails.map { question ->
                mapOf(
                    "id" to question.id,
                    "text" to question.questionText,
                    "correctAnswer" to question.correctAnswer,
                    "studentAnswer" to question.studentAnswer,
                    "isCorrect" to question.isCorrect,
                    "type" to question.type,
                    "options" to question.options,
                    "points" to question.points
                )
            }
            
            // Create record data
            val recordData = hashMapOf(
                "studentId" to studentId,
                "examId" to record.id,
                "examTitle" to record.title,
                "subject" to record.subject,
                "completedDate" to SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(record.date),
                "score" to record.score,
                "totalPoints" to record.totalPoints,
                "answered" to record.answered,
                "totalQuestions" to record.totalQuestions,
                "questions" to questionDetails,
                "createdAt" to com.google.firebase.Timestamp.now()
            )
            
            // Save to Firestore
            examRecordsCollection.add(recordData).await()
            
            Log.d(TAG, "Successfully saved exam record for student $studentId")
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving student exam record", e)
            return Result.failure(e)
        }
    }

    /**
     * Check if a student has already taken an exam
     */
    suspend fun hasStudentTakenExam(studentId: String, examId: String): Result<Boolean> {
        return try {
            Log.d(TAG, "Checking if student $studentId has taken exam $examId")
            
            // Query exam records for this student ID and exam ID
            val query = examRecordsCollection
                .whereEqualTo("studentId", studentId)
                .whereEqualTo("examId", examId)
                .limit(1) // We only need to know if at least one record exists
                .get()
                .await()
                
            val hasTaken = !query.isEmpty
            
            Log.d(TAG, "Student $studentId ${if(hasTaken) "has" else "has not"} taken exam $examId")
            Result.success(hasTaken)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if student has taken exam", e)
            Result.failure(e)
        }
    }

    /**
     * Get published exams that a student has not taken yet
     */
    suspend fun getAvailableExamsForStudent(studentId: String, studentYear: String, studentSection: String): Result<List<Exam>> {
        try {
            Log.d(TAG, "Getting available exams for student: $studentId, Year: $studentYear, Section: $studentSection")
            
            // First, check and publish any scheduled exams that are now due
            val currentTime = Date()
            val scheduledExamsResult = getScheduledExams()
            if (scheduledExamsResult.isSuccess) {
                val scheduledExams = scheduledExamsResult.getOrNull() ?: emptyList()
                for (scheduledExam in scheduledExams) {
                    if (scheduledExam.scheduledPublishDate != null && 
                        scheduledExam.scheduledPublishDate.before(currentTime) && 
                        !scheduledExam.published) {
                        Log.d(TAG, "Auto-publishing scheduled exam: ${scheduledExam.id}")
                        publishExam(scheduledExam.id)
                    }
                }
            }
            
            // Get a list of exams the student has already taken
            val takenExamsResult = getStudentExamRecords(studentId)
            val takenExamIds = if (takenExamsResult.isSuccess) {
                takenExamsResult.getOrNull()?.map { it.id }?.toSet() ?: emptySet()
            } else {
                emptySet()
            }
            
            Log.d(TAG, "Student has already taken ${takenExamIds.size} exams")
            
            // Get all published exams from Firestore (including newly auto-published ones)
            val examSnapshot = firestoreExamsCollection
                .whereEqualTo("published", true)
                .get()
                .await()
            
            val availableExams = mutableListOf<Exam>()
            
            // Filter exams by year and section and check if student has already taken them
            for (doc in examSnapshot.documents) {
                try {
                    val examData = doc.data ?: continue
                    val examId = doc.id
                    
                    // Skip if the student has already taken this exam
                    if (takenExamIds.contains(examId)) {
                        Log.d(TAG, "Skipping exam $examId as student has already taken it")
                        continue
                    }
                    
                    val targetYear = examData["targetYear"] as? String ?: "ALL"
                    val targetSection = examData["targetSection"] as? String ?: "ALL"
                    
                    // Check if this exam targets this student's year and section
                    if ((targetYear == "ALL" || targetYear == studentYear) && 
                        (targetSection == "ALL" || targetSection == studentSection)) {
                        
                        // Double-check if student has already taken this exam
                        val hasTakenResult = hasStudentTakenExam(studentId, examId)
                        if (hasTakenResult.isSuccess && hasTakenResult.getOrDefault(false)) {
                            Log.d(TAG, "Confirmed student has already taken exam $examId, skipping")
                            continue
                        }
                        
                        // Add exam to available list
                        availableExams.add(
                            Exam(
                                id = examId,
                                title = examData["title"] as? String ?: "",
                                subject = examData["subject"] as? String ?: "",
                                duration = (examData["duration"] as? Long)?.toInt() ?: 60,
                                questionCount = (examData["questionCount"] as? Long)?.toInt() ?: 0,
                                date = parseDate(examData["date"] as? String ?: ""),
                                type = examData["type"] as? String ?: "MULTIPLE_CHOICE",
                                targetYear = targetYear,
                                targetSection = targetSection
                            )
                        )
                        
                        Log.d(TAG, "Added exam $examId to available exams for student")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing exam", e)
                }
            }
            
            Log.d(TAG, "Found ${availableExams.size} available exams for student")
            return Result.success(availableExams)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available exams for student", e)
            return Result.failure(e)
        }
    }

    /**
     * Get all exams that have scheduled publish dates
     */
    suspend fun getScheduledExams(): Result<List<TeacherExam>> {
        return try {
            Log.d(TAG, "Getting all exams with scheduled publish dates")
            
            // Query Firestore for exams with scheduledPublishDate not null
            val examSnapshot = firestoreExamsCollection
                .whereNotEqualTo("scheduledPublishDate", null)
                .get()
                .await()
            
            if (examSnapshot.isEmpty) {
                Log.d(TAG, "No exams with scheduled publish dates found")
                return Result.success(emptyList())
            }
            
            val scheduledExams = mutableListOf<TeacherExam>()
            
            for (doc in examSnapshot.documents) {
                try {
                    val data = doc.data ?: continue
                    
                    val id = doc.id
                    val title = data["title"] as? String ?: ""
                    val subject = data["subject"] as? String ?: ""
                    val duration = (data["duration"] as? Long)?.toInt() ?: 60
                    val questionCount = (data["questionCount"] as? Long)?.toInt() ?: 0
                    val published = data["published"] as? Boolean ?: false
                    val teacherId = data["teacherId"] as? String ?: ""
                    val targetYear = data["targetYear"] as? String ?: "ALL"
                    val targetSection = data["targetSection"] as? String ?: "ALL"
                    val scheduledPublishTimestamp = data["scheduledPublishDate"] as? com.google.firebase.Timestamp
                    
                    // Parse and check the scheduled publish date
                    val scheduledPublishDate = scheduledPublishTimestamp?.toDate()
                    
                    // Add to the list if there's a scheduled date
                    if (scheduledPublishDate != null) {
                        val exam = TeacherExam(
                            id = id,
                            title = title,
                            subject = subject,
                            duration = duration,
                            questionCount = questionCount,
                            date = Date(), // Use current date as fallback
                            published = published,
                            teacherId = teacherId,
                            targetYear = targetYear,
                            targetSection = targetSection,
                            scheduledPublishDate = scheduledPublishDate
                        )
                        
                        scheduledExams.add(exam)
                        Log.d(TAG, "Found scheduled exam: $id, scheduled for: $scheduledPublishDate")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing scheduled exam", e)
                }
            }
            
            Log.d(TAG, "Found ${scheduledExams.size} exams with scheduled publish dates")
            Result.success(scheduledExams)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting scheduled exams", e)
            Result.failure(e)
        }
    }

    /**
     * Clear the scheduled publish date for an exam after it's been published
     */
    suspend fun clearScheduledPublishDate(examId: String): Result<Boolean> {
        try {
            Log.d(TAG, "Clearing scheduled publish date for exam $examId")
            
            firestoreExamsCollection.document(examId)
                .update("scheduledPublishDate", com.google.firebase.firestore.FieldValue.delete())
                .await()
            
            Log.d(TAG, "Successfully cleared scheduled publish date for exam $examId")
            return Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing scheduled publish date", e)
            return Result.failure(e)
        }
    }

    /**
     * Mark an exam as completed by a student to prevent retaking
     */
    suspend fun markExamAsCompleted(examId: String, studentId: String): Result<Boolean> {
        return try {
            Log.d(TAG, "Marking exam $examId as completed for student $studentId")
            
            // Create a completion record in a separate collection
            val completionData = hashMapOf(
                "examId" to examId,
                "studentId" to studentId,
                "completedAt" to com.google.firebase.Timestamp.now(),
                "status" to "completed"
            )
            
            // Add to exam completions collection
            firestoreExamsCollection.add(completionData)
                .await()
            
            Log.d(TAG, "Successfully marked exam as completed")
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error marking exam as completed", e)
            Result.failure(e)
        }
    }

    /**
     * Check if an exam has been completed by a student
     */
    suspend fun isExamCompletedByStudent(examId: String, studentId: String): Result<Boolean> {
        return try {
            val completionsQuery = firestoreExamsCollection
                .whereEqualTo("examId", examId)
                .whereEqualTo("studentId", studentId)
                .get()
                .await()
            
            val isCompleted = !completionsQuery.isEmpty
            Log.d(TAG, "Exam $examId completion status for student $studentId: $isCompleted")
            
            Result.success(isCompleted)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking exam completion status", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get all student exam records for a specific teacher (for teacher dashboard)
     */
    suspend fun getTeacherStudentExamRecords(teacherId: String): Result<List<StudentExamRecord>> {
        try {
            Log.d(TAG, "Getting student exam records for teacher: $teacherId")
            
            // First, get all exams created by this teacher
            val teacherExamsSnapshot = firestoreExamsCollection
                .whereEqualTo("teacherId", teacherId)
                .get()
                .await()
            
            val teacherExamIds = teacherExamsSnapshot.documents.map { it.id }
            
            if (teacherExamIds.isEmpty()) {
                Log.d(TAG, "No exams found for teacher $teacherId")
                return Result.success(emptyList())
            }
            
            // Get all student records for these exams
            val studentRecords = mutableListOf<StudentExamRecord>()
            
            for (examId in teacherExamIds) {
                val recordsSnapshot = examRecordsCollection
                    .whereEqualTo("examId", examId)
                    .get()
                    .await()
                
                for (recordDoc in recordsSnapshot.documents) {
                    try {
                        val data = recordDoc.data ?: continue
                        
                        // Parse question details
                        val questionDetailsData = data["questionDetails"] as? List<Map<String, Any>> ?: emptyList()
                        val questionDetails = questionDetailsData.map { questionData ->
                            QuestionAttempt(
                                id = questionData["id"] as? String ?: "",
                                questionText = questionData["questionText"] as? String ?: "",
                                correctAnswer = questionData["correctAnswer"] as? String ?: "",
                                studentAnswer = questionData["studentAnswer"] as? String,
                                isCorrect = questionData["isCorrect"] as? Boolean ?: false,
                                type = questionData["type"] as? String ?: "MULTIPLE_CHOICE",
                                options = questionData["options"] as? List<String> ?: emptyList(),
                                points = (questionData["points"] as? Long)?.toInt() ?: 1
                            )
                        }
                        
                        val record = StudentExamRecord(
                            id = data["examId"] as? String ?: "",
                            title = data["examTitle"] as? String ?: "",
                            subject = data["examSubject"] as? String ?: "",
                            date = (data["completedAt"] as? com.google.firebase.Timestamp)?.toDate() ?: Date(),
                            score = (data["score"] as? Long)?.toInt() ?: 0,
                            totalPoints = (data["totalPoints"] as? Long)?.toInt() ?: 0,
                            answered = (data["answered"] as? Long)?.toInt() ?: 0,
                            totalQuestions = (data["totalQuestions"] as? Long)?.toInt() ?: 0,
                            questionDetails = questionDetails,
                            studentName = data["studentName"] as? String ?: "Unknown Student",
                            studentId = data["studentId"] as? String ?: "",
                            recordId = recordDoc.id
                        )
                        
                        studentRecords.add(record)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing student record", e)
                    }
                }
            }
            
            Log.d(TAG, "Found ${studentRecords.size} student records for teacher")
            return Result.success(studentRecords)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting teacher student records", e)
            return Result.failure(e)
        }
    }
    
    /**
     * Update a student's exam score (for manual grading)
     */
    suspend fun updateStudentExamScore(recordId: String, newScore: Int): Result<Boolean> {
        try {
            Log.d(TAG, "Updating student exam score for record: $recordId")
            
            examRecordsCollection.document(recordId)
                .update("score", newScore)
                .await()
            
            Log.d(TAG, "Successfully updated student exam score")
            return Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating student exam score", e)
            return Result.failure(e)
        }
    }
}