package com.example.ablindexaminer.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.ablindexaminer.data.firebase.HybridExamService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.TimeZone
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale
import java.net.URL
import java.net.HttpURLConnection
import kotlin.math.abs

/**
 * Worker to check for scheduled exams and publish them when their time arrives
 */
class ScheduledExamPublishWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    private val TAG = "ScheduledExamWorker"
    private val examService = HybridExamService()
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Get accurate device time and verify with network if possible
            val currentTime = getAccurateTime()
            
            Log.d(TAG, "Checking for scheduled exams to publish at ${formatDateTime(currentTime)}")
            
            // Get all exams with scheduled publish dates
            val scheduledExamsResult = examService.getScheduledExams()
            
            if (scheduledExamsResult.isSuccess) {
                val scheduledExams = scheduledExamsResult.getOrNull() ?: emptyList()
                
                Log.d(TAG, "Found ${scheduledExams.size} exams with scheduled publish dates")
                
                // Check each exam to see if it's time to publish
                for (exam in scheduledExams) {
                    if (exam.scheduledPublishDate != null) {
                        // Normalize timezone to UTC for consistent comparison
                        val scheduledTimeUTC = normalizeToUTC(exam.scheduledPublishDate)
                        val currentTimeUTC = normalizeToUTC(currentTime)
                        
                        // Log detailed time information for debugging
                        Log.d(TAG, "Exam ${exam.id} scheduled for ${formatDateTime(scheduledTimeUTC)}")
                        Log.d(TAG, "Current time (normalized): ${formatDateTime(currentTimeUTC)}")
                        Log.d(TAG, "Scheduled timestamp: ${scheduledTimeUTC.time}, Current timestamp: ${currentTimeUTC.time}")
                        
                        // Compare the scheduled date with current time using millisecond precision
                        if (currentTimeUTC.time >= scheduledTimeUTC.time) {
                            if (!exam.published) {
                                Log.d(TAG, "Publishing exam ${exam.id} - '${exam.title}' as scheduled time has arrived")
                                
                                // Publish the exam
                                val publishResult = examService.updateExamPublishStatus(exam.id, true)
                                
                                if (publishResult.isSuccess) {
                                    Log.d(TAG, "Successfully published exam ${exam.id}")
                                    
                                    // Clear the scheduled publish date
                                    examService.clearScheduledPublishDate(exam.id)
                                    Log.d(TAG, "Cleared scheduled publish date for exam ${exam.id}")
                                } else {
                                    Log.e(TAG, "Failed to publish exam ${exam.id}: ${publishResult.exceptionOrNull()?.message}")
                                }
                            } else {
                                // Exam is already published, just clear the schedule
                                Log.d(TAG, "Exam ${exam.id} is already published, clearing scheduled date")
                                examService.clearScheduledPublishDate(exam.id)
                            }
                        } else {
                            val timeRemaining = (scheduledTimeUTC.time - currentTimeUTC.time) / 1000 / 60 // minutes
                            Log.d(TAG, "Exam ${exam.id} - '${exam.title}' scheduled for ${formatDateTime(scheduledTimeUTC)}, not publishing yet. ${timeRemaining} minutes remaining.")
                        }
                    }
                }
            } else {
                Log.e(TAG, "Failed to get scheduled exams: ${scheduledExamsResult.exceptionOrNull()?.message}")
            }
            
            // Return success to keep worker running
            return@withContext Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in scheduled exam worker", e)
            return@withContext Result.failure()
        }
    }
    
    /**
     * Get the most accurate time possible, using network time if available
     */
    private suspend fun getAccurateTime(): Date {
        try {
            // First try to get network time (more accurate)
            val networkTime = getNetworkTime()
            if (networkTime != null) {
                Log.d(TAG, "Using network time: ${formatDateTime(networkTime)}")
                return networkTime
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting network time, will use device time", e)
        }
        
        // Fall back to device time
        val deviceTime = Date()
        Log.d(TAG, "Using device time: ${formatDateTime(deviceTime)}")
        return deviceTime
    }
    
    /**
     * Try to get the network time for better accuracy
     */
    private suspend fun getNetworkTime(): Date? = withContext(Dispatchers.IO) {
        try {
            // Use NIST time server to get accurate time
            val url = URL("http://time.google.com")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 3000 // 3 second timeout
            connection.readTimeout = 3000
            connection.connect()
            
            // Get time from server
            val networkTimeMillis = connection.date
            if (networkTimeMillis > 0) {
                val networkTime = Date(networkTimeMillis)
                
                // Check if device time is significantly off (more than 5 minutes)
                val deviceTime = Date()
                val timeDifference = abs(deviceTime.time - networkTime.time)
                if (timeDifference > 5 * 60 * 1000) { // 5 minutes in milliseconds
                    Log.w(TAG, "Device time may be incorrect! Difference from network time: ${timeDifference/1000/60} minutes")
                }
                
                return@withContext networkTime
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching network time", e)
        }
        
        return@withContext null
    }
    
    /**
     * Normalize date to UTC for consistent comparison across timezones
     */
    private fun normalizeToUTC(date: Date): Date {
        val calendar = Calendar.getInstance()
        calendar.time = date
        
        val utcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        utcCalendar.set(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH),
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            calendar.get(Calendar.SECOND)
        )
        
        return utcCalendar.time
    }
    
    /**
     * Format date for consistent logging
     */
    private fun formatDateTime(date: Date): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault())
        return dateFormat.format(date)
    }
} 