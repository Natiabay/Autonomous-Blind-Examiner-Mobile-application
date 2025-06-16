package com.example.ablindexaminer.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.ablindexaminer.ui.screens.ExamQuestion
import com.example.ablindexaminer.ui.screens.QuestionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import java.util.*
import java.io.InputStream
import java.io.IOException
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import org.apache.commons.io.IOUtils

/**
 * Result class to encapsulate document parsing results and error information
 */
data class DocumentParsingResult(
    val questions: List<ExamQuestion> = emptyList(),
    val success: Boolean = false,
    val errorMessage: String? = null,
    val errorType: ErrorType = ErrorType.NONE,
    val documentInfo: DocumentInfo? = null
) {
    enum class ErrorType {
        NONE,
        FILE_NOT_FOUND,
        INVALID_FORMAT,
        ACCESS_DENIED,
        PARSING_ERROR,
        EMPTY_DOCUMENT,
        UNKNOWN
    }
    
    data class DocumentInfo(
        val fileName: String,
        val fileSize: Long?,
        val paragraphCount: Int,
        val wordCount: Int
    )
}

/**
 * Utility for parsing Word documents to extract different question types.
 */
class WordDocumentParser {
    companion object {
        private const val TAG = "WordDocumentParser"
        private const val MAX_FILE_SIZE_DIRECT_PARSING = 5 * 1024 * 1024 // 5MB threshold
        
        /**
         * Parse a Word document to extract questions with enhanced error handling and feedback
         */
        suspend fun parseDocument(context: Context, uri: Uri): DocumentParsingResult {
            return withContext(Dispatchers.IO) {
                var inputStream: InputStream? = null
                var tempFile: File? = null
                
                try {
                    Log.d(TAG, "Starting document parsing from URI: $uri")
                    
                    // Get file name for better logging
                    val fileName = getFileName(context, uri)
                    Log.d(TAG, "Parsing document: $fileName")
                    
                    // Verify the document exists and is accessible
                    try {
                        inputStream = context.contentResolver.openInputStream(uri)
                        if (inputStream == null) {
                            Log.e(TAG, "Failed to open input stream for URI: $uri")
                            return@withContext DocumentParsingResult(
                                success = false,
                                errorMessage = "Could not open the document. The file may be inaccessible or corrupted.",
                                errorType = DocumentParsingResult.ErrorType.FILE_NOT_FOUND
                            )
                        }
                        
                        // Get file size for better diagnostics
                        val fileSize = getFileSize(context, uri)
                        Log.d(TAG, "Document size: ${fileSize ?: "unknown"} bytes")
                        
                        if (fileSize != null && fileSize <= 0) {
                            return@withContext DocumentParsingResult(
                                success = false,
                                errorMessage = "The document appears to be empty. Please check that it contains exam questions.",
                                errorType = DocumentParsingResult.ErrorType.EMPTY_DOCUMENT,
                                documentInfo = DocumentParsingResult.DocumentInfo(
                                    fileName = fileName,
                                    fileSize = fileSize,
                                    paragraphCount = 0,
                                    wordCount = 0
                                )
                            )
                        }
                        
                        // For large files, use temp file to avoid OOM
                        val isLargeFile = fileSize != null && fileSize > MAX_FILE_SIZE_DIRECT_PARSING
                        
                        // Create a buffered input stream
                        val bufferedInputStream = BufferedInputStream(inputStream, 8192)
                        
                        try {
                            Log.d(TAG, "Creating XWPFDocument from input stream")
                            
                            // Handle document parsing based on size
                            val document = if (isLargeFile) {
                                Log.d(TAG, "Large file detected ($fileSize bytes), using temporary file approach")
                                // Create a temporary file
                                tempFile = File.createTempFile("word_doc_", ".docx", context.cacheDir)
                                val outputStream = FileOutputStream(tempFile)
                                
                                // Copy to temp file
                                IOUtils.copy(bufferedInputStream, outputStream)
                                outputStream.close()
                                bufferedInputStream.close()
                                
                                // Open with FileInputStream which is more memory efficient
                                val fileInputStream = FileInputStream(tempFile)
                                XWPFDocument(fileInputStream)
                            } else {
                                // Standard approach for smaller files
                                XWPFDocument(bufferedInputStream)
                            }
                            
                            val paragraphCount = document.paragraphs.size
                            Log.d(TAG, "Successfully created XWPFDocument, found $paragraphCount paragraphs")
                            
                            if (paragraphCount == 0) {
                                return@withContext DocumentParsingResult(
                                    success = false,
                                    errorMessage = "The document doesn't contain any text. Please ensure it has properly formatted questions.",
                                    errorType = DocumentParsingResult.ErrorType.EMPTY_DOCUMENT,
                                    documentInfo = DocumentParsingResult.DocumentInfo(
                                        fileName = fileName,
                                        fileSize = fileSize,
                                        paragraphCount = 0,
                                        wordCount = 0
                                    )
                                )
                            }
                            
                            // Calculate total word count for diagnostics
                            val wordCount = document.paragraphs.sumOf { 
                                it.text.split(Regex("\\s+")).count { word -> word.isNotEmpty() } 
                            }
                            Log.d(TAG, "Document has approximately $wordCount words")
                            
                            // Process the document paragraphs
                            val questions = extractQuestionsFromDocument(document)
                            
                            // Clean up any questions without text
                            val finalQuestions = questions.filter { it.text.isNotBlank() }
                            
                            if (finalQuestions.isEmpty()) {
                                Log.w(TAG, "No valid questions were extracted from the document")
                                return@withContext DocumentParsingResult(
                                    success = false,
                                    errorMessage = "No questions were found in the document. Please ensure your document follows the expected format:\n" +
                                                  "- Each question should be numbered (e.g., '1.' or 'Question 1')\n" +
                                                  "- For multiple choice questions, options should be labeled A., B., C., etc.\n" +
                                                  "- Include section headings like 'Multiple Choice', 'True/False', etc. for better detection",
                                    errorType = DocumentParsingResult.ErrorType.PARSING_ERROR,
                                    documentInfo = DocumentParsingResult.DocumentInfo(
                                        fileName = fileName,
                                        fileSize = fileSize,
                                        paragraphCount = paragraphCount,
                                        wordCount = wordCount
                                    )
                                )
                            }
                            
                            Log.d(TAG, "Successfully extracted ${finalQuestions.size} questions from document")
                            
                            // Provide detailed statistics about extracted questions
                            val questionTypes = finalQuestions.groupBy { it.type }
                                .mapValues { it.value.size }
                            
                            val questionStats = questionTypes.entries.joinToString(", ") { 
                                "${it.key.name}: ${it.value}" 
                            }
                            Log.d(TAG, "Question type distribution: $questionStats")
                            
                            return@withContext DocumentParsingResult(
                                questions = finalQuestions,
                                success = true,
                                documentInfo = DocumentParsingResult.DocumentInfo(
                                    fileName = fileName,
                                    fileSize = fileSize,
                                    paragraphCount = paragraphCount,
                                    wordCount = wordCount
                                )
                            )
                            
                        } catch (e: IOException) {
                            Log.e(TAG, "IOException while parsing Word document: ${e.message}", e)
                            return@withContext DocumentParsingResult(
                                success = false,
                                errorMessage = "Error reading the document: ${e.message ?: "Unknown IO error"}. Please ensure it's a valid .docx file.",
                                errorType = DocumentParsingResult.ErrorType.INVALID_FORMAT
                            )
                        } catch (e: OutOfMemoryError) {
                            Log.e(TAG, "OutOfMemoryError while parsing Word document: ${e.message}", e)
                            return@withContext DocumentParsingResult(
                                success = false,
                                errorMessage = "The document is too large to process. Please try a smaller document or split it into multiple files.",
                                errorType = DocumentParsingResult.ErrorType.PARSING_ERROR
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing Word document content: ${e.message}", e)
                            return@withContext DocumentParsingResult(
                                success = false,
                                errorMessage = "Failed to process the document: ${e.message ?: "Unknown error"}. Please try a different document format.",
                                errorType = DocumentParsingResult.ErrorType.PARSING_ERROR
                            )
                        }
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Security exception accessing document: ${e.message}", e)
                        return@withContext DocumentParsingResult(
                            success = false,
                            errorMessage = "Permission denied. The app doesn't have access to read this file.",
                            errorType = DocumentParsingResult.ErrorType.ACCESS_DENIED
                        )
                    } finally {
                        try {
                            inputStream?.close()
                            // Delete temporary file if it exists
                            tempFile?.delete()
                        } catch (e: IOException) {
                            Log.e(TAG, "Error cleaning up resources", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected error in parseDocument: ${e.message}", e)
                    return@withContext DocumentParsingResult(
                        success = false,
                        errorMessage = "An unexpected error occurred: ${e.message ?: "Unknown error"}",
                        errorType = DocumentParsingResult.ErrorType.UNKNOWN
                    )
                }
            }
        }
        
        /**
         * Extract questions from the document paragraphs
         */
        private fun extractQuestionsFromDocument(document: XWPFDocument): List<ExamQuestion> {
            val questions = mutableListOf<ExamQuestion>()
            var currentQuestion: ExamQuestion? = null
            var currentQuestionType: QuestionType? = null
            var nextQuestionNumber = 1
            var lastExtractedNumber: Int? = null
            
            // Process document in chunks to avoid memory pressure
            val paragraphs = document.paragraphs
            val totalParagraphs = paragraphs.size
            
            Log.d(TAG, "Starting to process $totalParagraphs paragraphs")
            
            // Use a persistent map to track question numbers we've seen
            val questionNumberMap = mutableMapOf<Int, Boolean>()
            
            // Process each paragraph
            for ((index, paragraph) in paragraphs.withIndex()) {
                try {
                    val text = paragraph.text.trim()
                    if (text.isEmpty()) {
                        // Empty paragraph may mark the end of a question
                        if (currentQuestion != null) {
                            questions.add(currentQuestion)
                            currentQuestion = null
                            // Don't reset question type - it might apply to multiple questions in a section
                        }
                        continue
                    }
                    
                    // Process in chunks and log progress
                    if (index % 50 == 0) {
                        Log.d(TAG, "Processing paragraph $index of $totalParagraphs (${(index.toFloat() / totalParagraphs * 100).toInt()}%)")
                        // Encourage garbage collection for large documents
                        if (index > 0 && index % 200 == 0) {
                            System.gc()
                        }
                    }
                    
                    Log.v(TAG, "Processing paragraph $index: '${text.take(Math.min(50, text.length))}${if (text.length > 50) "..." else ""}'")
                    
                    // Detect section headers
                    if (paragraph.style != null && (paragraph.style.contains("Heading") || 
                        paragraph.style.contains("Title")) || 
                        paragraph.runs.any { it.isBold }) {
                        Log.d(TAG, "Detected section header: $text")
                        // Save any pending question before starting a new section
                        if (currentQuestion != null) {
                            questions.add(currentQuestion)
                            currentQuestion = null
                        }
                        
                        // Reset question type for a new section
                        currentQuestionType = null
                        
                        // Check if the section header indicates a question type
                        when {
                            text.contains("multiple choice", ignoreCase = true) -> {
                                currentQuestionType = QuestionType.MULTIPLE_CHOICE
                                Log.d(TAG, "Section indicates Multiple Choice questions")
                            }
                            text.contains("true/false", ignoreCase = true) || text.contains("true or false", ignoreCase = true) -> {
                                currentQuestionType = QuestionType.TRUE_FALSE
                                Log.d(TAG, "Section indicates True/False questions")
                            }
                            text.contains("short answer", ignoreCase = true) -> {
                                currentQuestionType = QuestionType.SHORT_ANSWER
                                Log.d(TAG, "Section indicates Short Answer questions")
                            }
                            text.contains("fill in the blank", ignoreCase = true) -> {
                                currentQuestionType = QuestionType.FILL_IN_THE_BLANK
                                Log.d(TAG, "Section indicates Fill in the Blank questions")
                            }
                            text.contains("matching", ignoreCase = true) -> {
                                currentQuestionType = QuestionType.MATCHING
                                Log.d(TAG, "Section indicates Matching questions")
                            }
                            text.contains("essay", ignoreCase = true) -> {
                                currentQuestionType = QuestionType.ESSAY
                                Log.d(TAG, "Section indicates Essay questions")
                            }
                        }
                        continue
                    }
                    
                    // Detect question type markers in regular text
                    when {
                        text.contains("multiple choice", ignoreCase = true) -> {
                            currentQuestionType = QuestionType.MULTIPLE_CHOICE
                            Log.d(TAG, "Detected Multiple Choice marker")
                        }
                        text.contains("true/false", ignoreCase = true) || text.contains("true or false", ignoreCase = true) -> {
                            currentQuestionType = QuestionType.TRUE_FALSE
                            Log.d(TAG, "Detected True/False marker")
                        }
                        text.contains("short answer", ignoreCase = true) -> {
                            currentQuestionType = QuestionType.SHORT_ANSWER
                            Log.d(TAG, "Detected Short Answer marker")
                        }
                        text.contains("fill in the blank", ignoreCase = true) -> {
                            currentQuestionType = QuestionType.FILL_IN_THE_BLANK
                            Log.d(TAG, "Detected Fill in the Blank marker")
                        }
                        text.contains("matching", ignoreCase = true) -> {
                            currentQuestionType = QuestionType.MATCHING
                            Log.d(TAG, "Detected Matching marker")
                        }
                        text.contains("essay", ignoreCase = true) -> {
                            currentQuestionType = QuestionType.ESSAY
                            Log.d(TAG, "Detected Essay marker")
                        }
                        // Enhanced pattern for detecting numbered questions - more inclusive of various formats
                        text.matches(Regex("^\\d+\\.\\s+.*")) || 
                        text.matches(Regex("^\\d+\\)\\s+.*")) || 
                        text.matches(Regex("^Question\\s+\\d+.*", RegexOption.IGNORE_CASE)) -> {
                            // We've found a question
                            // Extract question number from text with improved pattern matching
                            val extractedNumber = extractQuestionNumber(text)
                            val questionNumber = if (extractedNumber != null) {
                                // Record this question number
                                questionNumberMap[extractedNumber] = true
                                lastExtractedNumber = extractedNumber
                                extractedNumber
                            } else {
                                // Find the next available sequential number
                                while (questionNumberMap.containsKey(nextQuestionNumber)) {
                                    nextQuestionNumber++
                                }
                                // Record this auto-assigned number
                                questionNumberMap[nextQuestionNumber] = true
                                nextQuestionNumber
                            }
                            Log.d(TAG, "Found question number: $questionNumber")
                            
                            // Add the previous question to the list before creating a new one
                            if (currentQuestion != null) {
                                questions.add(currentQuestion)
                            }
                            
                            // If question type is not detected, try to infer
                            if (currentQuestionType == null) {
                                currentQuestionType = inferQuestionType(paragraph, text)
                            }
                            
                            // Extract question text and remove any points notation for cleaner display
                            var questionText = text.replaceFirst(
                                Regex("^\\d+[.)]\\s+|^Question\\s+\\d+[.:)]\\s*", RegexOption.IGNORE_CASE), 
                                ""
                            )
                            
                            // Extract points if they exist in formats like "[2 points]" or "[2 pts]" or "(2 points)"
                            var points = 1
                            val pointsPattern = Regex("\\[([0-9]+)\\s*(points|pts|point|pt)\\]|\\(([0-9]+)\\s*(points|pts|point|pt)\\)")
                            val pointsMatch = pointsPattern.find(questionText)
                            
                            if (pointsMatch != null) {
                                // Extract the points value
                                val pointsValue = pointsMatch.groupValues[1].toIntOrNull() 
                                    ?: pointsMatch.groupValues[3].toIntOrNull()
                                
                                if (pointsValue != null && pointsValue > 0) {
                                    points = pointsValue
                                    Log.d(TAG, "Extracted point value: $points")
                                    
                                    // Remove the points notation from the question text
                                    questionText = questionText.replace(pointsMatch.value, "").trim()
                                }
                            }
                            
                            // Create a new question
                            val initialOptions = if (currentQuestionType == QuestionType.TRUE_FALSE) {
                                listOf("True", "False")
                            } else {
                                emptyList()
                            }
                            
                            currentQuestion = ExamQuestion(
                                id = UUID.randomUUID().toString(),
                                text = questionText,
                                type = currentQuestionType ?: QuestionType.SHORT_ANSWER, // Default if type can't be determined
                                options = initialOptions,
                                correctAnswer = "",
                                points = points,
                                number = questionNumber
                            )
                            
                            Log.d(TAG, "Created question #$questionNumber: ${questionText.take(50)}${if (questionText.length > 50) "..." else ""} [Points: $points]")
                            
                            // Update next question number
                            nextQuestionNumber = questionNumber + 1
                        }
                        // Check for multiple choice options (A. Option text or A) Option text)
                        text.matches(Regex("^[A-Z][.)]\\s+.*")) -> {
                            if (currentQuestion != null && (currentQuestion.type == QuestionType.MULTIPLE_CHOICE)) {
                                // Extract option letter and text
                                val optionLetter = text.substring(0, 1)
                                val optionText = text.replaceFirst(Regex("^[A-Z][.)]\\s+"), "")
                                
                                // Add option to current question
                                val updatedOptions = currentQuestion.options.toMutableList()
                                updatedOptions.add("$optionLetter. $optionText")
                                
                                currentQuestion = currentQuestion.copy(options = updatedOptions)
                                Log.d(TAG, "Added option $optionLetter to question #${currentQuestion.number}")
                            }
                        }
                        // Check for matching options (1. item, A. match)
                        text.matches(Regex("^\\d+[.)]\\s+.*")) && currentQuestionType == QuestionType.MATCHING -> {
                            if (currentQuestion != null) {
                                // Extract item number and text
                                val itemNumber = text.substring(0, text.indexOf('.'))
                                val itemText = text.replaceFirst(Regex("^\\d+[.)]\\s+"), "")
                                
                                // Add matching item to current question
                                val updatedOptions = currentQuestion.options.toMutableList()
                                updatedOptions.add("$itemNumber. $itemText")
                                
                                currentQuestion = currentQuestion.copy(options = updatedOptions)
                                Log.d(TAG, "Added matching item $itemNumber to question #${currentQuestion.number}")
                            }
                        }
                        // Check for answer line with enhanced matching for various formats
                        text.startsWith("Answer:", ignoreCase = true) || 
                        text.startsWith("Ans:", ignoreCase = true) || 
                        text.matches(Regex("^\\[Answer\\].*", RegexOption.IGNORE_CASE)) -> {
                            val answerText = if (text.contains(":")) {
                                text.substring(text.indexOf(':') + 1).trim()
                            } else if (text.contains("[Answer]")) {
                                text.replaceFirst(Regex("^\\[Answer\\]", RegexOption.IGNORE_CASE), "").trim()
                            } else {
                                text.replaceFirst(Regex("^(Answer|Ans)", RegexOption.IGNORE_CASE), "").trim()
                            }
                            
                            if (currentQuestion != null) {
                                currentQuestion = currentQuestion.copy(correctAnswer = answerText)
                                Log.d(TAG, "Added answer to question #${currentQuestion.number}: $answerText")
                            } else if (questions.isNotEmpty()) {
                                // If no current question, update the last added question
                                val lastQuestion = questions.last()
                                questions[questions.size - 1] = lastQuestion.copy(correctAnswer = answerText)
                                Log.d(TAG, "Added answer to previous question: $answerText")
                            }
                        }
                        // Check for points specification on a separate line
                        text.startsWith("Points:", ignoreCase = true) || 
                        text.matches(Regex("^\\[Points\\s*:\\s*\\d+\\]", RegexOption.IGNORE_CASE)) || 
                        text.matches(Regex("^\\(Points\\s*:\\s*\\d+\\)", RegexOption.IGNORE_CASE)) -> {
                            // Extract the points value
                            val pointsPattern = Regex("\\d+")
                            val pointsMatch = pointsPattern.find(text)
                            
                            if (pointsMatch != null && currentQuestion != null) {
                                val pointsValue = pointsMatch.value.toIntOrNull()
                                if (pointsValue != null && pointsValue > 0) {
                                    currentQuestion = currentQuestion.copy(points = pointsValue)
                                    Log.d(TAG, "Set points for question #${currentQuestion.number}: $pointsValue")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Don't let one paragraph's error affect the entire parsing
                    Log.e(TAG, "Error processing paragraph $index: ${e.message}", e)
                }
            }
            
            // Add the final question if there is one
            if (currentQuestion != null) {
                questions.add(currentQuestion)
            }
            
            // Ensure questions are properly numbered sequentially
            val sortedQuestions = if (questions.isNotEmpty()) {
                // First sort by the existing numbers
                val initialSort = questions.sortedBy { it.number }
                
                // Then renumber to ensure sequential ordering if there are gaps
                val renumbered = initialSort.mapIndexed { index, question ->
                    if (index + 1 != question.number) {
                        question.copy(number = index + 1)
                    } else {
                        question
                    }
                }
                
                renumbered
            } else {
                emptyList()
            }
            
            // Log the summary of extracted questions
            if (sortedQuestions.isNotEmpty()) {
                Log.d(TAG, "Extracted questions summary:")
                sortedQuestions.forEachIndexed { index, question ->
                    Log.d(TAG, "${question.number}. Type: ${question.type}, Text: ${question.text.take(40)}..., Options: ${question.options.size}")
                }
            } else {
                Log.w(TAG, "No questions were extracted from the document")
            }
            
            return sortedQuestions
        }
        
        /**
         * Extract question number from text with improved pattern matching
         */
        private fun extractQuestionNumber(text: String): Int? {
            // Match patterns like "1.", "1)", "Question 1", "Q1.", etc.
            val numberPattern = Regex("^(\\d+)[.)]|^Question\\s+(\\d+)|^Q(\\d+)[.)]", RegexOption.IGNORE_CASE)
            val matchResult = numberPattern.find(text)
            
            return if (matchResult != null) {
                // Get the first non-null group which contains the number
                val numberStr = matchResult.groups[1]?.value 
                    ?: matchResult.groups[2]?.value
                    ?: matchResult.groups[3]?.value
                numberStr?.toIntOrNull()
            } else {
                null
            }
        }
        
        /**
         * Infer question type from content
         */
        private fun inferQuestionType(paragraph: XWPFParagraph, text: String): QuestionType {
            return when {
                text.contains("_____") -> {
                    Log.d(TAG, "Inferred Fill in the Blank from underscores")
                    QuestionType.FILL_IN_THE_BLANK
                }
                paragraph.runs.any { run -> 
                    val runText = run.text()
                    runText.contains("A)") || runText.contains("A.") || 
                    runText.contains("B)") || runText.contains("B.") 
                } -> {
                    Log.d(TAG, "Inferred Multiple Choice from option markers")
                    QuestionType.MULTIPLE_CHOICE
                }
                text.contains("true", ignoreCase = true) && text.contains("false", ignoreCase = true) -> {
                    Log.d(TAG, "Inferred True/False from keywords")
                    QuestionType.TRUE_FALSE
                }
                else -> {
                    Log.d(TAG, "Defaulting to Short Answer")
                    QuestionType.SHORT_ANSWER
                }
            }
        }
        
        /**
         * Get file name from URI
         */
        private fun getFileName(context: Context, uri: Uri): String {
            var fileName = "unknown"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val displayNameIndex = cursor.getColumnIndex("_display_name")
                    if (displayNameIndex != -1) {
                        fileName = cursor.getString(displayNameIndex)
                    }
                }
            }
            return fileName
        }
        
        /**
         * Get file size from URI
         */
        private fun getFileSize(context: Context, uri: Uri): Long? {
            return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex("_size")
                    if (sizeIndex != -1) {
                        cursor.getLong(sizeIndex)
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        }
        
        /**
         * Get a sample document template for instructors
         * @return A string containing instructions on how to format the document
         */
        fun getDocumentInstructionsTemplate(): String {
            return """
                EXAM DOCUMENT FORMATTING GUIDE
                
                This guide shows how to format your Word document to be properly parsed by the A Blind Examiner app.
                
                1. SECTION HEADERS
                   Use headers to indicate question types. Examples:
                   - Multiple Choice Questions
                   - True/False Questions
                   - Short Answer Questions
                   - Fill in the Blank Questions
                   - Matching Questions
                   - Essay Questions
                
                2. QUESTION FORMATTING
                   Number each question sequentially:
                   1. What is the capital of France?
                   2. Which planet is closest to the sun?
                   
                   Alternatively, you can use:
                   Question 1. What is the capital of France?
                   Question 2. Which planet is closest to the sun?
                   
                3. ASSIGNING POINTS
                   You can specify points for each question in several ways:
                   
                   Option 1: Include points in brackets in the question text:
                   1. What is the capital of France? [2 points]
                   
                   Option 2: Include points in parentheses:
                   1. What is the capital of France? (3 points)
                   
                   Option 3: Add a separate line after the question:
                   1. What is the capital of France?
                   Points: 3
                   
                   If no points are specified, the question will be assigned 1 point by default.
                
                4. MULTIPLE CHOICE OPTIONS
                   Format options with letters:
                   A. Paris
                   B. London
                   C. Berlin
                   D. Rome
                
                5. FILL IN THE BLANK
                   Use underscores to indicate blanks:
                   The capital of France is _____.
                
                6. MATCHING QUESTIONS
                   Format items to match:
                   1. France
                   2. Germany
                   3. Italy
                   4. Spain
                   
                   A. Paris
                   B. Berlin
                   C. Rome
                   D. Madrid
                
                7. ANSWERS
                   Include answers after each question (optional):
                   Answer: A
                   
                8. FORMATTING TIPS
                   - Use bold for section headers
                   - Keep a consistent format throughout
                   - Separate questions with blank lines
                   - Avoid special formatting, tables, or images
                
                SAMPLE EXAM SECTION:
                
                Multiple Choice Questions
                
                1. What is the capital of France? [2 points]
                   A. Paris
                   B. London
                   C. Berlin
                   D. Rome
                   Answer: A
                
                2. Which planet is closest to the sun?
                   A. Venus
                   B. Earth
                   C. Mercury
                   D. Mars
                   Answer: C
                   Points: 3
                
                True/False Questions
                
                3. Paris is the capital of France. (2 points)
                   Answer: True
                
                4. Mercury is the largest planet in our solar system.
                   Answer: False
                   Points: 1
            """.trimIndent()
        }
        
        /**
         * Check if a document is likely to be successfully parsed
         * @return A pair of Boolean (likely to succeed) and String (feedback message)
         */
        suspend fun validateDocument(context: Context, uri: Uri): Pair<Boolean, String> {
            return withContext(Dispatchers.IO) {
                var inputStream: InputStream? = null
                try {
                    // Basic checks
                    val fileName = getFileName(context, uri)
                    if (!fileName.lowercase().endsWith(".docx")) {
                        return@withContext Pair(false, "The selected file doesn't appear to be a Word document (.docx). Please select a valid .docx file.")
                    }
                    
                    inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream == null) {
                        return@withContext Pair(false, "Could not open the file. The file may be inaccessible or corrupted.")
                    }
                    
                    // Try to parse as Word document
                    try {
                        val document = XWPFDocument(inputStream)
                        val paragraphs = document.paragraphs
                        
                        if (paragraphs.isEmpty()) {
                            return@withContext Pair(false, "The document appears to be empty. Please ensure it contains text.")
                        }
                        
                        // Look for question markers
                        var hasNumberedQuestions = false
                        var hasQuestionWord = false
                        var hasMultipleChoiceOptions = false
                        var hasSectionHeaders = false
                        
                        for (paragraph in paragraphs) {
                            val text = paragraph.text.trim()
                            
                            if (text.matches(Regex("^\\d+\\.\\s+.*"))) {
                                hasNumberedQuestions = true
                            }
                            
                            if (text.matches(Regex("^Question\\s+\\d+.*", RegexOption.IGNORE_CASE))) {
                                hasQuestionWord = true
                            }
                            
                            if (text.matches(Regex("^[A-D][.)]\\s+.*"))) {
                                hasMultipleChoiceOptions = true
                            }
                            
                            if (paragraph.style != null && (paragraph.style.contains("Heading") || 
                                paragraph.style.contains("Title")) || 
                                paragraph.runs.any { it.isBold }) {
                                hasSectionHeaders = true
                            }
                            
                            // If we found evidence of proper formatting, we can stop checking
                            if ((hasNumberedQuestions || hasQuestionWord) && 
                                (hasMultipleChoiceOptions || hasSectionHeaders)) {
                                break
                            }
                        }
                        
                        if (!hasNumberedQuestions && !hasQuestionWord) {
                            return@withContext Pair(false, "No numbered questions were detected in the document. Please ensure questions are numbered (e.g., '1.' or 'Question 1').")
                        }
                        
                        if (!hasMultipleChoiceOptions && !hasSectionHeaders) {
                            return@withContext Pair(false, "The document doesn't appear to contain properly formatted question options or section headers. See the formatting guide for details.")
                        }
                        
                        return@withContext Pair(true, "The document appears to be properly formatted and should parse successfully.")
                        
                    } catch (e: Exception) {
                        return@withContext Pair(false, "The file could not be processed as a Word document. Error: ${e.message ?: "Unknown error"}")
                    }
                } catch (e: Exception) {
                    return@withContext Pair(false, "Error validating document: ${e.message ?: "Unknown error"}")
                } finally {
                    try {
                        inputStream?.close()
                    } catch (e: IOException) {
                        // Ignore
                    }
                }
            }
        }
    }
} 