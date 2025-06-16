package com.example.ablindexaminer.ui.accessibility

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.math.abs
import com.example.ablindexaminer.ui.screens.Exam

/**
 * Specialized blind navigation controller for Student Dashboard
 */
class StudentDashboardBlindController(
    private val context: Context,
    private val textToSpeech: TextToSpeech
) {
    private var navigationItems = mutableListOf<DashboardNavigationItem>()
    private var currentIndex by mutableStateOf(0)
    private var isNavigationActive by mutableStateOf(true) // Always active for blind users
    private var lastSwipeTime = 0L
    private val swipeThreshold = 80f
    private val swipeTimeThreshold = 250L
    var currentScreen by mutableStateOf("Dashboard")
    
    data class DashboardNavigationItem(
        val id: String,
        val text: String,
        val description: String,
        val detailedDescription: String,
        val action: () -> Unit,
        val isClickable: Boolean = true,
        val itemType: ItemType = ItemType.BUTTON,
        val currentValue: String = "",
        val additionalInfo: String = ""
    )
    
    enum class ItemType {
        BUTTON, TEXT, INPUT_FIELD, TAB, EXAM_CARD, RESULT_CARD, ICON, HEADER, STATUS
    }
    
    fun setNavigationItems(items: List<DashboardNavigationItem>) {
        navigationItems.clear()
        navigationItems.addAll(items)
        currentIndex = 0
        if (items.isNotEmpty()) {
            announceScreenAndCurrentItem()
        }
    }
    
    fun addExamItems(exams: List<Exam>) {
        val examItems = exams.mapIndexed { index, exam ->
            DashboardNavigationItem(
                id = "exam_${exam.id}",
                text = exam.title,
                description = "Exam: ${exam.title}",
                detailedDescription = buildExamDescription(exam, index + 1, exams.size),
                action = { },
                itemType = ItemType.EXAM_CARD,
                additionalInfo = "Duration: ${exam.duration} minutes, Questions: ${exam.questionCount}"
            )
        }
        
        val currentItems = navigationItems.toMutableList()
        currentItems.addAll(examItems)
        setNavigationItems(currentItems)
    }
    
    private fun buildExamDescription(exam: Exam, position: Int, total: Int): String {
        return buildString {
            append("Exam $position of $total. ")
            append("Title: ${exam.title}. ")
            append("Subject: ${exam.subject}. ")
            append("Duration: ${exam.duration} minutes. ")
            append("Questions: ${exam.questionCount}. ")
            append("Type: ${exam.type}. ")
            append("Double tap to start exam.")
        }
    }
    
    private fun announceScreenAndCurrentItem() {
        if (navigationItems.isNotEmpty()) {
            val totalItems = navigationItems.size
            val currentItem = navigationItems[currentIndex]
            val announcement = "On $currentScreen. $totalItems items available. ${getCurrentItemDescription()}"
            speakWithDelay(announcement, 200)
        }
    }
    
    private fun getCurrentItemDescription(): String {
        if (navigationItems.isEmpty()) return "No items available"
        val item = navigationItems[currentIndex]
        val position = "Item ${currentIndex + 1} of ${navigationItems.size}"
        
        return when (item.itemType) {
            ItemType.EXAM_CARD -> "$position. ${item.detailedDescription}"
            ItemType.INPUT_FIELD -> {
                val value = if (item.currentValue.isNotEmpty()) 
                    "Current value: ${item.currentValue}" else "Empty field"
                "$position. ${item.description}. $value. Double tap to edit."
            }
            ItemType.BUTTON -> "$position. ${item.description}. Button. Double tap to activate."
            ItemType.TAB -> "$position. ${item.description}. Tab. Double tap to switch."
            ItemType.HEADER -> "$position. ${item.description}. Header."
            ItemType.STATUS -> "$position. ${item.description}. Status information."
            ItemType.RESULT_CARD -> "$position. ${item.detailedDescription}"
            ItemType.ICON -> "$position. ${item.description}. Icon. Double tap to activate."
            else -> "$position. ${item.description}"
        }
    }
    
    private fun announceCurrentItem() {
        if (navigationItems.isNotEmpty()) {
            val description = getCurrentItemDescription()
            speakWithDelay(description)
        }
    }
    
    fun navigateNext() {
        if (navigationItems.isEmpty()) return
        currentIndex = (currentIndex + 1) % navigationItems.size
        announceCurrentItem()
    }
    
    fun navigatePrevious() {
        if (navigationItems.isEmpty()) return
        currentIndex = if (currentIndex == 0) navigationItems.size - 1 else currentIndex - 1
        announceCurrentItem()
    }
    
    fun activateCurrentItem() {
        if (navigationItems.isEmpty()) return
        val item = navigationItems[currentIndex]
        if (item.isClickable) {
            when (item.itemType) {
                ItemType.EXAM_CARD -> speakWithDelay("Starting exam: ${item.text}")
                ItemType.BUTTON -> speakWithDelay("Activating button: ${item.text}")
                ItemType.TAB -> speakWithDelay("Switching to tab: ${item.text}")
                ItemType.INPUT_FIELD -> speakWithDelay("Editing field: ${item.text}")
                else -> speakWithDelay("Activating: ${item.text}")
            }
            item.action()
        } else {
            speakWithDelay("This item cannot be activated")
        }
    }
    
    fun handleSwipeGesture(deltaX: Float, deltaY: Float) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSwipeTime < swipeTimeThreshold) return
        
        if (abs(deltaX) > swipeThreshold && abs(deltaX) > abs(deltaY)) {
            lastSwipeTime = currentTime
            if (deltaX > 0) {
                // Swipe right - go to previous item
                navigatePrevious()
            } else {
                // Swipe left - go to next item
                navigateNext()
            }
        }
    }
    
    fun handleDoubleTap() {
        activateCurrentItem()
    }
    
    fun announceHelp() {
        val helpText = buildString {
            append("Blind navigation help. ")
            append("Swipe left to go to next item. ")
            append("Swipe right to go to previous item. ")
            append("Double tap to activate current item. ")
            append("Currently on $currentScreen with ${navigationItems.size} items. ")
            append("You are on item ${currentIndex + 1}.")
        }
        speakWithDelay(helpText, 100)
    }
    
    fun announceCurrentStatus() {
        val statusText = buildString {
            append("Current status: ")
            append("Screen: $currentScreen. ")
            append("Item ${currentIndex + 1} of ${navigationItems.size}. ")
            if (navigationItems.isNotEmpty()) {
                append(navigationItems[currentIndex].description)
            }
        }
        speakWithDelay(statusText)
    }
    
    private fun speakWithDelay(text: String, delay: Long = 100) {
        textToSpeech.stop()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            delay(delay)
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }
    
    fun announceCharacterByCharacter(text: String) {
        text.forEach { char ->
            announceCharacter(char.toString())
            Thread.sleep(300) // Pause between characters
        }
    }
    
    fun announceCharacter(char: String) {
        val announcement = when (char.lowercase()) {
            " " -> "space"
            "\n" -> "new line"
            "\t" -> "tab"
            "." -> "period"
            "," -> "comma"
            "!" -> "exclamation mark"
            "?" -> "question mark"
            ":" -> "colon"
            ";" -> "semicolon"
            "'" -> "apostrophe"
            "\"" -> "quotation mark"
            "-" -> "dash"
            "_" -> "underscore"
            "@" -> "at symbol"
            "#" -> "hash"
            "$" -> "dollar sign"
            "%" -> "percent"
            "&" -> "ampersand"
            "*" -> "asterisk"
            "(" -> "left parenthesis"
            ")" -> "right parenthesis"
            "+" -> "plus"
            "=" -> "equals"
            "/" -> "slash"
            "\\" -> "backslash"
            "|" -> "vertical bar"
            "[" -> "left bracket"
            "]" -> "right bracket"
            "{" -> "left brace"
            "}" -> "right brace"
            "<" -> "less than"
            ">" -> "greater than"
            else -> if (char.length == 1 && char[0].isDigit()) "digit $char" else char
        }
        textToSpeech.speak(announcement, TextToSpeech.QUEUE_ADD, null, null)
    }
    
    fun isActive(): Boolean = isNavigationActive
    fun getItemCount(): Int = navigationItems.size
    fun getCurrentItem(): DashboardNavigationItem? = 
        if (navigationItems.isNotEmpty()) navigationItems[currentIndex] else null
}

/**
 * Composable wrapper for student dashboard blind navigation
 */
@Composable
fun StudentDashboardBlindWrapper(
    controller: StudentDashboardBlindController,
    content: @Composable () -> Unit
) {
    var tapCount by remember { mutableStateOf(0) }
    var lastTapTime by remember { mutableStateOf(0L) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastTapTime < 400) {
                            tapCount++
                            if (tapCount == 2) {
                                controller.handleDoubleTap()
                                tapCount = 0
                            }
                        } else {
                            tapCount = 1
                        }
                        lastTapTime = currentTime
                    }
                ) { change, dragAmount ->
                    controller.handleSwipeGesture(dragAmount.x, dragAmount.y)
                }
            }
    ) {
        content()
    }
} 