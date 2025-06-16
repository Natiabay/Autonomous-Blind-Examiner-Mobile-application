package com.example.ablindexaminer.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.ablindexaminer.data.firebase.HybridExamService
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.*

/**
 * Worker to check for scheduled exam publications and publish them if their time has arrived
 */
class PublicationScheduleWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    private val TAG = "PublicationScheduleWorker"
    private val examService = HybridExamService()
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Checking for scheduled exam publications")
            
            // Get current time
            val currentTime = Date()
            
            // Get all scheduled exams
            val scheduledExamsResult = examService.getScheduledExams()
            
            if (scheduledExamsResult.isSuccess) {
                val scheduledExams = scheduledExamsResult.getOrNull() ?: emptyList()
                
                Log.d(TAG, "Found ${scheduledExams.size} scheduled exams")
                
                // Publish exams that are due
                var publishedCount = 0
                for (scheduledExam in scheduledExams) {
                    if (scheduledExam.scheduledPublishDate != null && 
                        scheduledExam.scheduledPublishDate.before(currentTime) &&
                        !scheduledExam.published) {
                        
                        Log.d(TAG, "Publishing due exam: ${scheduledExam.id} - ${scheduledExam.title}")
                        
                        try {
                            // Publish the exam
                            val publishResult = examService.publishExam(scheduledExam.id)
                            
                            if (publishResult.isSuccess) {
                                // Clear the scheduled publish date
                                examService.clearScheduledPublishDate(scheduledExam.id)
                                publishedCount++
                                Log.d(TAG, "Successfully published exam: ${scheduledExam.id}")
                            } else {
                                Log.e(TAG, "Failed to publish exam: ${scheduledExam.id}", publishResult.exceptionOrNull())
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error publishing exam: ${scheduledExam.id}", e)
                        }
                    }
                }
                
                Log.d(TAG, "Published $publishedCount exams automatically")
                Result.success()
            } else {
                Log.e(TAG, "Failed to get scheduled exams", scheduledExamsResult.exceptionOrNull())
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Worker execution failed", e)
            Result.failure()
        }
    }
} 