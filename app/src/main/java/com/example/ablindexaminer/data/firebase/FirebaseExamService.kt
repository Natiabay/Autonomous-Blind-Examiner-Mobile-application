package com.example.ablindexaminer.data.firebase

import android.util.Log
import com.example.ablindexaminer.ui.screens.Exam
import com.example.ablindexaminer.data.model.Question
import com.example.ablindexaminer.data.model.TeacherExam
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class FirebaseExamService {
    private val db = FirebaseFirestore.getInstance()
    private val examsCollection = db.collection("exams")
    private val questionsCollection = db.collection("questions")
    private val answersCollection = db.collection("answers")
    
    init {
        Log.d("FirebaseExamService", "Initializing with Firestore collections: exams, questions, answers")
    }
    
    // Get published exams for a specific student based on their year and section
    fun getPublishedExamsForStudent(studentYear: String, studentSection: String): Flow<List<Exam>> = callbackFlow {
        Log.d("FirebaseExamService", "Getting published exams for student - Year: $studentYear, Section: $studentSection")
        
        val listener = examsCollection
            .whereEqualTo("published", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseExamService", "Listen failed", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    val exams = mutableListOf<Exam>()
                    Log.d("FirebaseExamService", "Received ${snapshot.documents.size} exams from Firestore")
                    
                    for (doc in snapshot.documents) {
                        try {
                            val examData = doc.data
                            if (examData != null) {
                                val targetYear = examData["targetYear"] as? String ?: "ALL"
                                val targetSection = examData["targetSection"] as? String ?: "ALL"
                                
                                // Check if this exam targets this student's year and section
                                if ((targetYear == "ALL" || targetYear == studentYear) && 
                                    (targetSection == "ALL" || targetSection == studentSection)) {
                                    
                                    Log.d("FirebaseExamService", "Exam ${doc.id} matches student criteria, adding to list")
                                    exams.add(
                                        Exam(
                                            id = doc.id,
                                            title = examData["title"] as? String ?: "",
                                            subject = examData["subject"] as? String ?: "",
                                            duration = (examData["duration"] as? Long)?.toInt() ?: 60,
                                            questionCount = (examData["questionCount"] as? Long)?.toInt() ?: 0,
                                            date = parseDate(examData["date"] as? String),
                                            type = examData["type"] as? String ?: "MULTIPLE_CHOICE"
                                        )
                                    )
                                } else {
                                    Log.d("FirebaseExamService", "Exam ${doc.id} does not match student criteria")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("FirebaseExamService", "Error parsing exam ${doc.id}", e)
                        }
                    }
                    
                    Log.d("FirebaseExamService", "Sending ${exams.size} exams to student dashboard")
                    trySend(exams)
                }
            }
        
        awaitClose {
            listener.remove()
        }
    }
    
    // Getting published exams (general method)
    fun getPublishedExams(): Flow<List<Exam>> = callbackFlow {
        val listener = examsCollection
            .whereEqualTo("published", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseExamService", "Listen failed", error)
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
                                        type = examData["type"] as? String ?: "MULTIPLE_CHOICE"
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            Log.e("FirebaseExamService", "Error parsing exam", e)
                        }
                    }
                    trySend(exams)
                }
            }
        
        awaitClose {
            listener.remove()
        }
    }
    
    // Get all exams for a teacher
    fun getTeacherExams(teacherId: String): Flow<List<TeacherExam>> = callbackFlow {
        val listener = examsCollection
            .whereEqualTo("teacherId", teacherId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseExamService", "Listen failed", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    val exams = mutableListOf<TeacherExam>()
                    for (doc in snapshot.documents) {
                        try {
                            val examData = doc.data
                            if (examData != null) {
                                exams.add(
                                    TeacherExam(
                                        id = doc.id,
                                        title = examData["title"] as? String ?: "",
                                        subject = examData["subject"] as? String ?: "",
                                        duration = (examData["duration"] as? Long)?.toInt() ?: 60,
                                        questionCount = (examData["questionCount"] as? Long)?.toInt() ?: 0,
                                        date = parseDate(examData["date"] as? String),
                                        published = examData["published"] as? Boolean ?: false,
                                        targetYear = examData["targetYear"] as? String ?: "ALL",
                                        targetSection = examData["targetSection"] as? String ?: "ALL"
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            Log.e("FirebaseExamService", "Error parsing exam", e)
                        }
                    }
                    trySend(exams)
                }
            }
        
        awaitClose {
            listener.remove()
        }
    }
    
    // Get exam by ID
    suspend fun getExamById(examId: String): Result<Exam> {
        return try {
            val docSnapshot = examsCollection.document(examId).get().await()
            if (docSnapshot.exists()) {
                val examData = docSnapshot.data
                if (examData != null) {
                    val result = Exam(
                        id = docSnapshot.id,
                        title = examData["title"] as? String ?: "",
                        subject = examData["subject"] as? String ?: "",
                        duration = (examData["duration"] as? Long)?.toInt() ?: 60,
                        questionCount = (examData["questionCount"] as? Long)?.toInt() ?: 0,
                        date = parseDate(examData["date"] as? String),
                        type = examData["type"] as? String ?: "MULTIPLE_CHOICE"
                    )
                    Result.success(result)
                } else {
                    Result.failure(Exception("Failed to parse exam data"))
                }
            } else {
                Result.failure(Exception("Exam not found"))
            }
        } catch (e: Exception) {
            Log.e("FirebaseExamService", "Get exam by ID failed", e)
            Result.failure(e)
        }
    }
    
    // Create a new exam
    suspend fun createExam(exam: TeacherExam, teacherId: String): Result<String> {
        return try {
            Log.d("FirebaseExamService", "Creating exam: ${exam.title} for teacher: $teacherId")
            
            // Format date
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val dateStr = sdf.format(exam.date)
            
            // Create exam data with exam type included
            val examData = hashMapOf(
                "title" to exam.title,
                "subject" to exam.subject,
                "duration" to exam.duration,
                "questionCount" to exam.questionCount,
                "date" to dateStr,
                "published" to exam.published,
                "teacherId" to teacherId,
                "targetYear" to exam.targetYear,
                "targetSection" to exam.targetSection,
                // Store the dominant question type or "MIXED" if there are different types
                "type" to "MULTIPLE_CHOICE" // Default type - cannot access questions from data model
            )
            
            // Add exam to Firestore
            val result = examsCollection.add(examData).await()
            Log.d("FirebaseExamService", "Exam created with ID: ${result.id}")
            
            Result.success(result.id)
        } catch (e: Exception) {
            Log.e("FirebaseExamService", "Create exam failed", e)
            Result.failure(e)
        }
    }
    
    // Update exam publish status
    suspend fun updateExamPublishStatus(examId: String, published: Boolean): Result<Unit> {
        return try {
            Log.d("FirebaseExamService", "Updating exam publish status - ID: $examId, Published: $published")
            examsCollection.document(examId).update("published", published).await()
            Log.d("FirebaseExamService", "Exam publish status updated successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseExamService", "Update exam publish status failed: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    // Delete an exam
    suspend fun deleteExam(examId: String): Result<Unit> {
        return try {
            // Delete the exam document
            examsCollection.document(examId).delete().await()
            
            // Delete all questions for this exam
            val questionsToDelete = questionsCollection
                .whereEqualTo("examId", examId)
                .get()
                .await()
            
            for (questionDoc in questionsToDelete.documents) {
                questionsCollection.document(questionDoc.id).delete().await()
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseExamService", "Delete exam failed", e)
            Result.failure(e)
        }
    }
    
    // Get questions for an exam
    suspend fun getQuestionsForExam(examId: String): Result<List<com.example.ablindexaminer.data.model.Question>> {
        return try {
            Log.d("FirebaseExamService", "Fetching questions for exam: $examId")
            
            // First, try to fetch from the exam's questions subcollection
            val subcollectionSnapshot = examsCollection.document(examId)
                .collection("questions")
                .get()
                .await()
            
            val questions = mutableListOf<com.example.ablindexaminer.data.model.Question>()
            
            // Process questions from the subcollection
            for (document in subcollectionSnapshot.documents) {
                val data = document.data
                if (data != null) {
                    @Suppress("UNCHECKED_CAST")
                    val question = com.example.ablindexaminer.data.model.Question(
                        id = document.id,
                        text = data["text"] as? String ?: "",
                        options = data["options"] as? List<String>,
                        correctAnswer = data["correctAnswer"] as? String ?: "",
                        type = data["type"] as? String ?: "MULTIPLE_CHOICE", 
                        points = (data["points"] as? Long)?.toInt() ?: 1,
                        number = (data["number"] as? Long)?.toInt() ?: 0
                    )
                    questions.add(question)
                    Log.d("FirebaseExamService", "Fetched question from subcollection: ${question.text}")
                }
            }
            
            // If no questions found in subcollection, check the legacy location
            if (questions.isEmpty()) {
                Log.d("FirebaseExamService", "No questions in subcollection, checking main collection")
                val querySnapshot = questionsCollection
                    .whereEqualTo("examId", examId)
                    .get()
                    .await()
                
                for (document in querySnapshot.documents) {
                    val data = document.data
                    if (data != null) {
                        @Suppress("UNCHECKED_CAST")
                        val question = com.example.ablindexaminer.data.model.Question(
                            id = document.id,
                            text = data["text"] as? String ?: "",
                            options = data["options"] as? List<String>,
                            correctAnswer = data["correctAnswer"] as? String ?: "",
                            type = data["type"] as? String ?: "MULTIPLE_CHOICE",
                            points = (data["points"] as? Long)?.toInt() ?: 1,
                            number = (data["number"] as? Long)?.toInt() ?: 0
                        )
                        questions.add(question)
                        Log.d("FirebaseExamService", "Fetched question from main collection: ${question.text}")
                        
                        // Copy question to the subcollection for future consistency
                        try {
                            val questionData = hashMapOf(
                                "text" to question.text,
                                "options" to question.options,
                                "correctAnswer" to question.correctAnswer,
                                "type" to question.type,
                                "examId" to examId,
                                "points" to (question.points as Int),
                                "number" to (question.number as Int)
                            )
                            
                            examsCollection.document(examId)
                                .collection("questions")
                                .add(questionData)
                                .await()
                                
                            Log.d("FirebaseExamService", "Migrated question to subcollection: ${question.text}")
                        } catch (e: Exception) {
                            Log.e("FirebaseExamService", "Failed to migrate question", e)
                        }
                    }
                }
            }
            
            // Sort by question number
            val sortedQuestions = questions.sortedBy { it.number }
            
            Log.d("FirebaseExamService", "Fetched ${sortedQuestions.size} questions for exam $examId")
            Result.success(sortedQuestions)
        } catch (e: Exception) {
            Log.e("FirebaseExamService", "Get questions for exam failed", e)
            Result.failure(e)
        }
    }
    
    // Add a question to an exam
    suspend fun addQuestion(examId: String, question: com.example.ablindexaminer.data.model.Question): Result<String> {
        return try {
            Log.d("FirebaseExamService", "Adding question to exam $examId: ${question.text}")
            
            // Create question data
            val questionData = hashMapOf(
                "text" to question.text,
                "options" to question.options,
                "correctAnswer" to question.correctAnswer,
                "type" to question.type, // Store the exact question type
                "examId" to examId,
                "points" to (question.points as Int), // Cast to Int to avoid type mismatch
                "number" to (question.number as Int)  // Cast to Int to avoid type mismatch
            )
            
            // Add question to exam's questions subcollection in Firestore
            val result = examsCollection.document(examId)
                .collection("questions")
                .add(questionData)
                .await()
            
            Log.d("FirebaseExamService", "Question added with ID: ${result.id} to exam subcollection")
            
            Result.success(result.id)
        } catch (e: Exception) {
            Log.e("FirebaseExamService", "Add question failed", e)
            Result.failure(e)
        }
    }
    
    // Submit exam answers
    suspend fun submitExamAnswers(
        examId: String, 
        studentId: String, 
        answers: Map<String, String>,
        score: Int,
        total: Int
    ): Result<Unit> {
        return try {
            val submissionData = hashMapOf(
                "examId" to examId,
                "studentId" to studentId,
                "answers" to answers,
                "score" to score,
                "total" to total,
                "percentage" to (score.toFloat() / total.toFloat() * 100),
                "submitted" to com.google.firebase.Timestamp.now()
            )
            
            answersCollection.add(submissionData).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseExamService", "Submit exam answers failed", e)
            Result.failure(e)
        }
    }
    
    // Get exam results for a student
    suspend fun getStudentResults(studentId: String): Result<List<Map<String, Any>>> {
        return try {
            val snapshot = answersCollection
                .whereEqualTo("studentId", studentId)
                .get()
                .await()
            
            val results = mutableListOf<Map<String, Any>>()
            
            for (doc in snapshot.documents) {
                val resultData = doc.data
                if (resultData != null) {
                    // Get exam details
                    val examId = resultData["examId"] as? String ?: ""
                    val examDoc = examsCollection.document(examId).get().await()
                    
                    if (examDoc.exists()) {
                        val examData = examDoc.data
                        if (examData != null) {
                            val combinedData = resultData.toMutableMap()
                            combinedData["examTitle"] = examData["title"] ?: ""
                            combinedData["examSubject"] = examData["subject"] ?: ""
                            results.add(combinedData)
                        }
                    }
                }
            }
            
            Result.success(results)
        } catch (e: Exception) {
            Log.e("FirebaseExamService", "Get student results failed", e)
            Result.failure(e)
        }
    }
    
    // Helper functions for date formatting
    private fun parseDate(dateString: String?): Date {
        return if (dateString != null) {
            try {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateString) ?: Date()
            } catch (e: Exception) {
                Date()
            }
        } else {
            Date()
        }
    }
    
    private fun formatDate(date: Date): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
    }
}