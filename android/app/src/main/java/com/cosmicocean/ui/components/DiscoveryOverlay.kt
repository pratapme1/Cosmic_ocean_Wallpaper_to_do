package com.cosmicocean.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun DiscoveryOverlay(
    onDismiss: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    val totalSteps = 3
    
    val capabilities = listOf(
        CapabilityInfo(
            title = "Invisible Intelligence",
            description = "No messy lists. Your tasks exist as a living constellation. Urgency drives their size and proximity.",
            icon = "🧠",
            color = Color(0xFF00E5FF)
        ),
        CapabilityInfo(
            title = "Atomic Habits",
            description = "Recurring tasks pulse with a unique echo. Build consistency through subtle, dynamic reminders.",
            icon = "⚛️",
            color = Color(0xFFE040FB)
        ),
        CapabilityInfo(
            title = "Deep Focus",
            description = "The ocean disappears when you focus. Enter focus mode to silence the noise and finish what matters.",
            icon = "🌊",
            color = Color(0xFF3AA0FF)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Animated Star Node
            CapabilityNode(
                info = capabilities[step],
                key = step
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Text Info
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    fadeIn(animationSpec = tween(500)) + slideInVertically { it / 2 } with
                    fadeOut(animationSpec = tween(500)) + slideOutVertically { -it / 2 }
                },
                label = "capability_text"
            ) { currentStep ->
                val info = capabilities[currentStep]
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = info.title,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Light,
                        color = info.color,
                        textAlign = TextAlign.Center,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = info.description,
                        fontSize = 16.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Action
            Button(
                onClick = {
                    if (step < totalSteps - 1) {
                        step++
                    } else {
                        onDismiss()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = capabilities[step].color,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text(
                    text = if (step < totalSteps - 1) "Tell me more" else "Enter the Ocean",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("discovery_skip_button")
            ) {
                Text("Skip tour", color = Color.White.copy(alpha = 0.3f))
            }
        }
    }
}

@Composable
private fun CapabilityNode(info: CapabilityInfo, key: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "node_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(120.dp)
    ) {
        // Outer Glow
        Box(
            modifier = Modifier
                .size(100.dp)
                .scale(pulseScale)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(info.color.copy(alpha = 0.4f), Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )
        
        // Inner Core
        Box(
            modifier = Modifier
                .size(60.dp)
                .background(info.color, CircleShape)
                .border(4.dp, Color.White.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(info.icon, fontSize = 28.sp)
        }
    }
}

private data class CapabilityInfo(
    val title: String,
    val description: String,
    val icon: String,
    val color: Color
)
