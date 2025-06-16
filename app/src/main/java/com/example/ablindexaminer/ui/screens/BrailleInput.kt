package com.example.ablindexaminer.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import android.speech.tts.TextToSpeech
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

data class BrailleDot(
    val position: Int,
    val x: Float,
    val y: Float,
    var isPressed: Boolean = false
)

@Composable
fun BrailleInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isFreeInput: Boolean = false,
    onAnswerAccepted: (String) -> Unit = {},
    autoAcceptDelay: Long = 1000 // Default 1 second delay
) {
    val coroutineScope = rememberCoroutineScope()
    var lastInputTime by remember { mutableStateOf(0L) }
    var currentInput by remember { mutableStateOf(value) }
    var dotPattern by remember { mutableStateOf(BooleanArray(6) { false }) }
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val tts = remember { TextToSpeech(context, null) }

    LaunchedEffect(currentInput) {
        if (currentInput != value) {
            onValueChange(currentInput)
            
            // Start timer for auto-accept
            val currentTime = System.currentTimeMillis()
            lastInputTime = currentTime

            delay(autoAcceptDelay)
            if (lastInputTime == currentTime && currentInput.isNotBlank()) {
                onAnswerAccepted(currentInput)
                if (!isFreeInput) {
                    currentInput = ""
                    dotPattern = BooleanArray(6) { false }
                }
            }
        }
    }

    fun speak(text: String) { tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null) }

    Column(
        modifier = modifier
            .padding(16.dp)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Display current input
        Text(
            text = "Current Input: $currentInput",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Braille input grid
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            for (row in 0..2) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    for (col in 0..1) {
                        val index = row * 2 + col
                        BrailleDot(
                            isActive = dotPattern[index],
                            onToggle = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                dotPattern[index] = !dotPattern[index]
                                speak("Dot ${index + 1} ${if (dotPattern[index]) "on" else "off"}")
                                val brailleChar = convertBrailleToChar(dotPattern)
                                if (brailleChar != null) {
                                    currentInput = if (isFreeInput) {
                                        currentInput + brailleChar
                                    } else {
                                        brailleChar.toString()
                                    }
                                    speak("$brailleChar added")
                                    dotPattern = BooleanArray(6) { false }
                                }
                            }
                        )
                    }
                }
            }
        }

        // Backspace button for free input mode
        if (isFreeInput && currentInput.isNotEmpty()) {
            Text(
                text = "Tap here to delete last character",
                modifier = Modifier
                    .padding(top = 8.dp)
                    .pointerInput(Unit) {
                        detectTapGestures {
                            currentInput = currentInput.dropLast(1)
                            speak("Character deleted")
                        }
                    }
            )
        }
    }
}

@Composable
private fun BrailleDot(
    isActive: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .size(48.dp)
            .pointerInput(Unit) {
                detectTapGestures { onToggle() }
            },
        shape = CircleShape,
        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        tonalElevation = if (isActive) 8.dp else 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {}
}

private fun convertBrailleToChar(dots: BooleanArray): Char? {
    // Basic Braille to ASCII conversion
    // This is a simplified version - you should expand this based on your needs
    val pattern = dots.map { if (it) 1 else 0 }.joinToString("")
    return when (pattern) {
        "100000" -> 'A'
        "110000" -> 'B'
        "100100" -> 'C'
        "100110" -> 'D'
        "100010" -> 'E'
        "110100" -> 'F'
        "110110" -> 'G'
        "110010" -> 'H'
        "010100" -> 'I'
        "010110" -> 'J'
        "101000" -> 'K'
        "111000" -> 'L'
        "101100" -> 'M'
        "101110" -> 'N'
        "101010" -> 'O'
        "111100" -> 'P'
        "111110" -> 'Q'
        "111010" -> 'R'
        "011100" -> 'S'
        "011110" -> 'T'
        "101001" -> 'U'
        "111001" -> 'V'
        "010111" -> 'W'
        "101101" -> 'X'
        "101111" -> 'Y'
        "101011" -> 'Z'
        else -> null
    }
}

// Helper to convert 6-dot pattern to character
private fun convertToBrailleDots(dots: List<Boolean>): String {
    val pressed = dots.mapIndexedNotNull { i, b -> if (b) i + 1 else null }.toSet()
    return when {
        pressed.isEmpty() -> ""
        pressed == setOf(1) -> "A"
        pressed == setOf(1, 2) -> "B"
        pressed == setOf(1, 4) -> "C"
        pressed == setOf(1, 4, 5) -> "D"
        pressed == setOf(1, 5) -> "E"
        pressed == setOf(1, 2, 4) -> "F"
        pressed == setOf(1, 2, 4, 5) -> "G"
        pressed == setOf(1, 2, 5) -> "H"
        pressed == setOf(2, 4) -> "I"
        pressed == setOf(2, 4, 5) -> "J"
        pressed == setOf(1, 3) -> "K"
        pressed == setOf(1, 2, 3) -> "L"
        pressed == setOf(1, 3, 4) -> "M"
        pressed == setOf(1, 3, 4, 5) -> "N"
        pressed == setOf(1, 3, 5) -> "O"
        pressed == setOf(1, 2, 3, 4) -> "P"
        pressed == setOf(1, 2, 3, 4, 5) -> "Q"
        pressed == setOf(1, 2, 3, 5) -> "R"
        pressed == setOf(2, 3, 4) -> "S"
        pressed == setOf(2, 3, 4, 5) -> "T"
        pressed == setOf(1, 3, 6) -> "U"
        pressed == setOf(1, 2, 3, 6) -> "V"
        pressed == setOf(2, 4, 5, 6) -> "W"
        pressed == setOf(1, 3, 4, 6) -> "X"
        pressed == setOf(1, 3, 4, 5, 6) -> "Y"
        pressed == setOf(1, 3, 5, 6) -> "Z"
        pressed == setOf(1, 6) -> "1"
        pressed == setOf(1, 2, 6) -> "2"
        pressed == setOf(1, 4, 6) -> "3"
        pressed == setOf(1, 4, 5, 6) -> "4"
        pressed == setOf(1, 5, 6) -> "5"
        pressed == setOf(1, 2, 4, 6) -> "6"
        pressed == setOf(1, 2, 4, 5, 6) -> "7"
        pressed == setOf(1, 2, 5, 6) -> "8"
        pressed == setOf(2, 4, 6) -> "9"
        pressed == setOf(2, 4, 5, 6) -> "0"
        pressed == setOf(2, 3, 4, 6) -> "."
        pressed == setOf(2, 3) -> ","
        pressed == setOf(2, 3, 5) -> "?"
        pressed == setOf(2, 3, 5, 6) -> "!"
        pressed == setOf(3, 6) -> "-"
        else -> "#"
    }
} 