package com.cosmicocean.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Trophy Display Badge - Corner indicator showing total trophy count
 * PWA-accurate: Shows in top-right corner, pulses on new unlock, tappable to open gallery
 */
@Composable
fun TrophyDisplay(
    trophyCount: Int,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    isNewUnlock: Boolean = false
) {
    // Pulse animation for new unlocks
    val infiniteTransition = rememberInfiniteTransition(label = "trophy_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isNewUnlock) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = modifier
            .padding(16.dp)
            .size(56.dp)
            .scale(if (isNewUnlock) scale else 1f)
            .background(
                color = Color(0xFF1E1E2E).copy(alpha = 0.9f),
                shape = CircleShape
            )
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Trophy emoji
            Text(
                text = "🏆",
                fontSize = 24.sp
            )

            // Count
            if (trophyCount > 0) {
                Text(
                    text = trophyCount.toString(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // Glow effect for new unlock
        if (isNewUnlock) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        color = Color(0xFFFFD700).copy(alpha = 0.3f * scale),
                        shape = CircleShape
                    )
            )
        }
    }
}

/**
 * Trophy unlock notification - Appears briefly when new trophy unlocked
 */
@Composable
fun TrophyUnlockNotification(
    trophyName: String,
    trophyDescription: String,
    tier: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(true) }

    // Auto-dismiss after 5 seconds
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(5000)
        visible = false
        onDismiss()
    }

    if (visible) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(
                    color = getTierColor(tier).copy(alpha = 0.95f),
                    shape = MaterialTheme.shapes.medium
                )
                .padding(16.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Trophy icon
                    Text(
                        text = getTierEmoji(tier),
                        fontSize = 32.sp
                    )

                    Column {
                        Text(
                            text = "🎉 Trophy Unlocked!",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Text(
                            text = trophyName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Text(
                            text = trophyDescription,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Get tier color
 */
private fun getTierColor(tier: String): Color {
    return when (tier.lowercase()) {
        "bronze" -> Color(0xFFCD7F32)
        "silver" -> Color(0xFFC0C0C0)
        "gold" -> Color(0xFFFFD700)
        "platinum" -> Color(0xFFE5E4E2)
        "cosmic" -> Color(0xFF9B59B6)
        else -> Color.White
    }
}

/**
 * Get tier emoji
 */
private fun getTierEmoji(tier: String): String {
    return when (tier.lowercase()) {
        "bronze" -> "🥉"
        "silver" -> "🥈"
        "gold" -> "🥇"
        "platinum" -> "💎"
        "cosmic" -> "🌌"
        else -> "🏆"
    }
}
