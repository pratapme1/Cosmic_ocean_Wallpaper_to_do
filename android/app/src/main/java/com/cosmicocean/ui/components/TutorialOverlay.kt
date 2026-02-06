package com.cosmicocean.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TutorialOverlay(
    title: String,
    body: String,
    stepIndex: Int,
    totalSteps: Int,
    onSkip: () -> Unit
) {
    val safeIndex = stepIndex.coerceIn(0, totalSteps.coerceAtLeast(1) - 1)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .background(Color(0xFF1A1A2E), RoundedCornerShape(20.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                text = body,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.8f)
            )

            Text(
                text = "Complete this step to continue.",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.6f)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (0 until totalSteps).forEach { i ->
                    Box(
                        modifier = Modifier
                            .size(if (i == safeIndex) 10.dp else 8.dp)
                            .background(
                                if (i == safeIndex) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(999.dp)
                            )
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onSkip) {
                    Text("Skip", color = Color.White.copy(alpha = 0.7f))
                }
            }
        }
    }
}
