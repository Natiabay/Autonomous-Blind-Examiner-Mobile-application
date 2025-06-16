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

/**
 * Navigation item for blind accessibility
 */
data class NavigationItem(
    val id: String,
    val text: String,
    val description: String,
    val action: () -> Unit,
    val isClickable: Boolean = true,
    val isInputField: Boolean = false,
    val currentValue: String = ""
)

/**
 * Controller for blind navigation using TTS and gestures
 */
class BlindNavigationController(
    private val context: Context,
    private val textToSpeech: TextToSpeech
) {
    private var navigationItems = mutableListOf<NavigationItem>()
    private var currentIndex by mutableStateOf(0)
    private var isNavigationActive by mutableStateOf(false)
    private var lastSwipeTime = 0L
    private val swipeThreshold = 100f
    private val swipeTimeThreshold = 300L
    
    fun setNavigationItems(items: List<NavigationItem>) {
        navigationItems.clear()
        navigationItems.addAll(items)
        currentIndex = 0
        if (items.isNotEmpty() && isNavigationActive) {
            announceCurrentItem()
        }
    }
    
    fun activateNavigation() {
        isNavigationActive = true
        if (navigationItems.isNotEmpty()) {
            speakWithDelay("Blind navigation activated. ${navigationItems.size} items available. Currently on: ${getCurrentItemDescription()}")
        }
    }
    
    fun deactivateNavigation() {
        isNavigationActive = false
        textToSpeech.stop()
    }
    
    private fun getCurrentItemDescription(): String {
        if (navigationItems.isEmpty()) return "No items available"
        val item = navigationItems[currentIndex]
        val position = "Item ${currentIndex + 1} of ${navigationItems.size}"
        val description = if (item.isInputField && item.currentValue.isNotEmpty()) {
            "${item.description}. Current value: ${item.currentValue}"
        } else {
            item.description
        }
        return "$position. $description"
    }
    
    private fun announceCurrentItem() {
        if (isNavigationActive && navigationItems.isNotEmpty()) {
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
        if (navigationItems.isEmpty() || !isNavigationActive) return
        val item = navigationItems[currentIndex]
        if (item.isClickable) {
            speakWithDelay("Activating: ${item.text}")
            item.action()
        } else {
            speakWithDelay("This item is not clickable")
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
    
    private fun speakWithDelay(text: String, delay: Long = 100) {
        textToSpeech.stop()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            delay(delay)
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
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
            else -> char
        }
        textToSpeech.speak(announcement, TextToSpeech.QUEUE_FLUSH, null, null)
    }
    
    fun isActive(): Boolean = isNavigationActive
    fun getItemCount(): Int = navigationItems.size
}

/**
 * Composable wrapper for blind navigation
 */
@Composable
fun BlindNavigationWrapper(
    controller: BlindNavigationController,
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
                        // Handle tap detection for double tap
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastTapTime < 500) {
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