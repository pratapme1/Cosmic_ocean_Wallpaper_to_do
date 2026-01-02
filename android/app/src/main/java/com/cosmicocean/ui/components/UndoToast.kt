package com.cosmicocean.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Undo Toast Notification
 * PWA-accurate: 5-second countdown with Undo button for task completion/archival
 */
@Composable
fun UndoToast(
    message: String,
    actionType: String, // "completed" or "archived"
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    durationMs: Long = 5000L
) {
    var progress by remember { mutableFloatStateOf(1f) }
    var isVisible by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val startTime = System.currentTimeMillis()
        val endTime = startTime + durationMs

        while (System.currentTimeMillis() < endTime) {
            val elapsed = System.currentTimeMillis() - startTime
            progress = 1f - (elapsed.toFloat() / durationMs)
            delay(16) // ~60fps
        }

        // Auto-dismiss after countdown
        isVisible = false
        delay(300) // Wait for animation
        onDismiss()
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = getToastColor(actionType),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(16.dp)
            ) {
                // Message row with undo button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Message
                    Text(
                        text = message,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // Undo button
                    Button(
                        onClick = {
                            scope.launch {
                                isVisible = false
                                delay(300)
                                onUndo()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.2f),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(
                            text = "UNDO",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Progress bar
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = Color.White.copy(alpha = 0.8f),
                    trackColor = Color.White.copy(alpha = 0.2f)
                )
            }
        }
    }
}

/**
 * Get toast background color based on action type
 */
private fun getToastColor(actionType: String): Color {
    return when (actionType.lowercase()) {
        "completed" -> Color(0xFF00FF88) // Green
        "archived" -> Color(0xFF888888) // Gray
        "snoozed" -> Color(0xFF00E5FF) // Cyan
        else -> Color(0xFF3AA0FF) // Blue
    }
}

/**
 * Undo Toast Manager - Manages multiple undo toasts
 */
@Composable
fun UndoToastContainer(
    toasts: List<UndoToastData>,
    onUndo: (String) -> Unit,
    onDismiss: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = 80.dp), // Space for bottom UI
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            toasts.forEach { toast ->
                UndoToast(
                    message = toast.message,
                    actionType = toast.actionType,
                    onUndo = { onUndo(toast.id) },
                    onDismiss = { onDismiss(toast.id) }
                )
            }
        }
    }
}

/**
 * Data class for undo toast
 */
data class UndoToastData(
    val id: String,
    val message: String,
    val actionType: String, // "completed", "archived", "snoozed"
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Undo Toast State Manager
 */
class UndoToastManager {
    private val _toasts = mutableStateListOf<UndoToastData>()
    val toasts: List<UndoToastData> get() = _toasts

    /**
     * Show a new toast
     */
    fun show(id: String, message: String, actionType: String) {
        // Remove existing toast with same ID
        _toasts.removeAll { it.id == id }

        // Add new toast
        _toasts.add(UndoToastData(id, message, actionType))
    }

    /**
     * Dismiss a toast
     */
    fun dismiss(id: String) {
        _toasts.removeAll { it.id == id }
    }

    /**
     * Clear all toasts
     */
    fun clearAll() {
        _toasts.clear()
    }
}

/**
 * Remember undo toast manager
 */
@Composable
fun rememberUndoToastManager(): UndoToastManager {
    return remember { UndoToastManager() }
}
