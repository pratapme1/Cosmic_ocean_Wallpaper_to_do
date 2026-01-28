package com.cosmicocean.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cosmicocean.model.Star
import com.cosmicocean.model.Temperature
import kotlinx.coroutines.delay

@Composable
fun AmbientStatusHUD(stars: List<Star>, isInteracting: Boolean) {
    var idleTime by remember { mutableLongStateOf(0L) }
    var currentMessage by remember { mutableStateOf("All clear") }
    
    // Update logic
    LaunchedEffect(stars, isInteracting) {
        if (isInteracting) {
            idleTime = 0
        } else {
            while (true) {
                delay(100)
                idleTime += 100
                
                val overdue = stars.count { it.dueIn < 0 && !it.isCompleted && !it.isArchived }
                val urgent = stars.count { it.dueIn in 0f..120f && !it.isCompleted && !it.isArchived }
                
                val newMessage = when {
                    overdue > 0 -> if (overdue == 1) "1 overdue task" else "$overdue overdue tasks"
                    urgent > 0 -> if (urgent == 1) "1 task due in next 2 hours" else "$urgent tasks due in next 2 hours"
                    else -> "All clear"
                }
                
                if (newMessage != currentMessage) {
                    currentMessage = newMessage
                    idleTime = 0 // Reset on message change
                }
            }
        }
    }

    val targetAlpha = if (idleTime >= 2000 && !isInteracting) 0.5f else 0f
    val animatedAlpha by animateFloatAsState(targetValue = targetAlpha, label = "alpha")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Text(
            text = currentMessage,
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.alpha(animatedAlpha)
        )
    }
}
