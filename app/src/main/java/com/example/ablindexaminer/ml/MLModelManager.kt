package com.example.ablindexaminer.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.Rot90Op
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

class MLModelManager(private val context: Context) {
    private var brailleInterpreter: Interpreter? = null
    private var asagInterpreter: Interpreter? = null
    private var isInitialized = false
    private var modelLoadingErrors = mutableMapOf<String, String>()
    
    private val imageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(28, 28, ResizeOp.ResizeMethod.BILINEAR))
        .add(Rot90Op(0))
        .build()

    companion object {
        private const val TAG = "MLModelManager"
        private const val CONFIDENCE_THRESHOLD = 0.5f
    }

    init {
        // Initialize models safely with comprehensive error handling
        try {
            initializeModels()
        } catch (e: Exception) {
            Log.e(TAG, "Critical error during MLModelManager initialization", e)
            // Continue without models - use fallback methods only
            isInitialized = false
        }
    }

    private fun initializeModels() {
        try {
        loadBrailleModel()
            loadAsagModel()
            isInitialized = true
            Log.d(TAG, "MLModelManager initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing ML models", e)
            isInitialized = false
            // Store the error for user feedback
            modelLoadingErrors["initialization"] = "ML models failed to initialize: ${e.message}"
        }
    }

    private fun loadBrailleModel() {
        try {
            val brailleOptions = Interpreter.Options().apply {
                setNumThreads(2) // Reduced from 4 for stability
                setUseNNAPI(false) // Disable NNAPI to avoid compatibility issues
            }
            
            // Try multiple paths for the Braille model in correct order
            val possiblePaths = listOf(
                "models/BrailleNet.tflite",
                "braillenet.tflite/BrailleNet.tflite",
                "BrailleNet.tflite",
                "models/braillenet.tflite",
                "assets/BrailleNet.tflite"
            )
            
            var modelLoaded = false
            for (path in possiblePaths) {
                try {
                    val brailleModelFile = FileUtil.loadMappedFile(context, path)
                    brailleInterpreter = Interpreter(brailleModelFile, brailleOptions)
                    Log.i(TAG, "BrailleNet model loaded successfully from: $path")
                    modelLoaded = true
                    break
                } catch (e: Exception) {
                    Log.d(TAG, "Failed to load Braille model from path: $path - ${e.message}")
                }
            }
            
            if (!modelLoaded) {
                Log.w(TAG, "BrailleNet model not found in any expected location - using fallback recognition")
                modelLoadingErrors["braille"] = "BrailleNet model not found - using pattern recognition fallback"
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading BrailleNet model", e)
            modelLoadingErrors["braille"] = "BrailleNet model error: ${e.message}"
            brailleInterpreter = null
        }
    }

    private fun loadAsagModel() {
        try {
            val asagOptions = Interpreter.Options().apply {
                setNumThreads(2) // Reduced from 4 for stability
                setUseNNAPI(false) // Disable NNAPI to avoid compatibility issues
            }
            
            // Try multiple paths for the ASAG model in correct order
            val possiblePaths = listOf(
                "models/asag_model.tflite",
                "asag_model.tflite/asag_model.tflite",
                "asag_model.tflite",
                "models/asag.tflite",
                "assets/asag_model.tflite"
            )
            
            var modelLoaded = false
            for (path in possiblePaths) {
                try {
                    val asagModelFile = FileUtil.loadMappedFile(context, path)
                    asagInterpreter = Interpreter(asagModelFile, asagOptions)
                    Log.i(TAG, "ASAG model loaded successfully from: $path")
                    modelLoaded = true
                    break
                } catch (e: Exception) {
                    Log.d(TAG, "Failed to load ASAG model from path: $path - ${e.message}")
                }
            }
            
            if (!modelLoaded) {
                Log.w(TAG, "ASAG model not found in any expected location - using text similarity fallback")
                modelLoadingErrors["asag"] = "ASAG model not found - using text similarity fallback"
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading ASAG model", e)
            modelLoadingErrors["asag"] = "ASAG model error: ${e.message}"
            asagInterpreter = null
        }
    }

    fun getModelStatus(): String {
        return when {
            brailleInterpreter != null && asagInterpreter != null -> "✅ All ML models loaded"
            brailleInterpreter != null -> "⚠️ Braille model loaded, ASAG using fallback"
            asagInterpreter != null -> "⚠️ ASAG model loaded, Braille using fallback"
            else -> "⚠️ Using fallback methods (no ML models)"
        }
    }

    fun getModelErrors(): Map<String, String> = modelLoadingErrors.toMap()

    fun recognizeBrailleCharacter(bitmap: Bitmap): String {
        return try {
            if (brailleInterpreter == null) {
                Log.d(TAG, "Braille interpreter not available, using pattern recognition fallback")
                return recognizeBraillePattern(bitmap)
            }

            val tensorImage = TensorImage.fromBitmap(bitmap)
            val processedImage = imageProcessor.process(tensorImage)
            
            val inputBuffer = processedImage.buffer
            val outputBuffer = ByteBuffer.allocateDirect(1 * 64 * 4) // Support more characters
                .order(ByteOrder.nativeOrder())

            brailleInterpreter?.run(inputBuffer, outputBuffer) 
                ?: return recognizeBraillePattern(bitmap)

            outputBuffer.rewind()
            val outputs = FloatArray(64) // Extended character set
            for (i in outputs.indices) {
                outputs[i] = outputBuffer.getFloat()
            }

            val maxIndex = outputs.indices.maxByOrNull { outputs[it] } ?: return recognizeBraillePattern(bitmap)
            val maxProb = outputs[maxIndex]
            
            if (maxProb > CONFIDENCE_THRESHOLD) {
                val recognizedChar = when (maxIndex) {
                    in 0..25 -> ('a'.code + maxIndex).toChar() // lowercase a-z
                    in 26..51 -> ('A'.code + (maxIndex - 26)).toChar() // uppercase A-Z
                    in 52..61 -> ('0'.code + (maxIndex - 52)).toChar() // digits 0-9
                    62 -> ' ' // space
                    63 -> '.' // period
                    else -> '?'
                }.toString()
                
                Log.d(TAG, "ML recognized character: $recognizedChar with confidence: $maxProb")
                recognizedChar
            } else {
                Log.d(TAG, "Low ML confidence: $maxProb, using pattern fallback")
                recognizeBraillePattern(bitmap)
            }
        } catch (e: Exception) {
            Log.w(TAG, "ML braille recognition failed: ${e.message}, using fallback")
            recognizeBraillePattern(bitmap)
        }
    }

    private fun recognizeBraillePattern(bitmap: Bitmap): String {
        return try {
            // Enhanced pattern recognition fallback
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            
            var whitePixelCount = 0
            var totalPixels = pixels.size
            
            pixels.forEach { pixel ->
                val red = (pixel shr 16) and 0xFF
                val green = (pixel shr 8) and 0xFF
                val blue = pixel and 0xFF
                val brightness = (red + green + blue) / 3
                if (brightness > 128) whitePixelCount++
            }
            
            val whiteRatio = whitePixelCount.toFloat() / totalPixels.toFloat()
            
            // Enhanced character mapping based on pixel density
            when {
                whiteRatio < 0.05 -> "" // Too few dots
                whiteRatio < 0.15 -> "a" // Single dot pattern
                whiteRatio < 0.25 -> "b" // Two dot pattern  
                whiteRatio < 0.35 -> "c" // Three dot pattern
                whiteRatio < 0.45 -> "d" // Four dot pattern
                whiteRatio < 0.55 -> "e" // Five dot pattern
                whiteRatio < 0.65 -> "f" // Six dot pattern
                whiteRatio < 0.75 -> " " // Space
                else -> "." // Period or complex pattern
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pattern recognition failed: ${e.message}")
            "" // Return empty on any error
        }
    }

    fun calculateAnswerSimilarity(studentAnswer: String, teacherAnswer: String): Float {
        if (studentAnswer.isBlank() || teacherAnswer.isBlank()) {
            return 0f
        }

        return try {
            if (asagInterpreter == null) {
                Log.d(TAG, "ASAG interpreter not available, using text similarity fallback")
                return calculateSimpleTextSimilarity(studentAnswer, teacherAnswer)
            }

                val maxLength = 512
                val inputBuffer = ByteBuffer.allocateDirect(2 * maxLength * 4)
                    .order(ByteOrder.nativeOrder())

                val studentTokens = preprocessText(studentAnswer)
                val teacherTokens = preprocessText(teacherAnswer)

                // Add student answer tokens with padding
                for (i in 0 until maxLength) {
                    inputBuffer.putFloat(if (i < studentTokens.size) studentTokens[i].hashCode().toFloat() else 0f)
                }

                // Add teacher answer tokens with padding
                for (i in 0 until maxLength) {
                    inputBuffer.putFloat(if (i < teacherTokens.size) teacherTokens[i].hashCode().toFloat() else 0f)
                }

                val outputBuffer = ByteBuffer.allocateDirect(1 * 4)
                    .order(ByteOrder.nativeOrder())

            asagInterpreter?.run(inputBuffer, outputBuffer) 
                ?: return calculateSimpleTextSimilarity(studentAnswer, teacherAnswer)

                outputBuffer.rewind()
                val similarity = outputBuffer.getFloat().coerceIn(0f, 1f)
                
            Log.d(TAG, "ML similarity score: $similarity")
            similarity
            
        } catch (e: Exception) {
            Log.w(TAG, "ML similarity calculation failed: ${e.message}, using text fallback")
            calculateSimpleTextSimilarity(studentAnswer, teacherAnswer)
        }
    }

    // Enhanced fallback similarity calculation
    private fun calculateSimpleTextSimilarity(answer1: String, answer2: String): Float {
        val words1 = preprocessText(answer1).toSet()
        val words2 = preprocessText(answer2).toSet()
        
        if (words1.isEmpty() && words2.isEmpty()) return 1f
        if (words1.isEmpty() || words2.isEmpty()) return 0f
        
        // Calculate Jaccard similarity
        val intersection = words1.intersect(words2).size
        val union = words1.union(words2).size
        val jaccardSimilarity = if (union > 0) intersection.toFloat() / union.toFloat() else 0f
        
        // Calculate cosine similarity for better accuracy
        val allWords = words1.union(words2).toList()
        val vector1 = allWords.map { if (words1.contains(it)) 1 else 0 }
        val vector2 = allWords.map { if (words2.contains(it)) 1 else 0 }
        
        val dotProduct = vector1.zip(vector2).sumOf { it.first * it.second }
        val magnitude1 = kotlin.math.sqrt(vector1.sumOf { it * it }.toDouble())
        val magnitude2 = kotlin.math.sqrt(vector2.sumOf { it * it }.toDouble())
        
        val cosineSimilarity = if (magnitude1 * magnitude2 > 0) {
            (dotProduct / (magnitude1 * magnitude2)).toFloat()
        } else 0f
        
        // Return weighted average of both similarities
        return (jaccardSimilarity * 0.6f + cosineSimilarity * 0.4f).coerceIn(0f, 1f)
    }

    private fun preprocessText(text: String): List<String> {
        return text.lowercase()
            .replace("[^a-z0-9\\s]".toRegex(), " ")
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() }
            .take(512)
    }

    fun close() {
        try {
            brailleInterpreter?.close()
            asagInterpreter?.close()
            brailleInterpreter = null
            asagInterpreter = null
            isInitialized = false
            Log.d(TAG, "ML models closed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing ML models: ${e.message}", e)
        }
    }
} 