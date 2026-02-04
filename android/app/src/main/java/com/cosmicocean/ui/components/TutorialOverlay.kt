package com.cosmicocean.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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

private data class TutorialStep(
    val title: String,
    val body: String
)

@Composable
fun TutorialOverlay(
    onDone: () -> Unit,
    onSkip: () -> Unit
) {
    val steps = remember {
        listOf(
            TutorialStep(
                title = "Dynamic Environments",
                body = "Your wallpaper reacts to tasks, time, and environment settings."
            ),
            TutorialStep(
                title = "Due Alerts",
                body = "Enable haptics to feel when tasks are due soon or overdue."
            ),
            TutorialStep(
                title = "Create, Edit, Complete",
                body = "Tap + to add tasks, tap a task to edit, and mark done to complete."
            ),
            TutorialStep(
                title = "Privacy First",
                body = "Everything works locally. You control what’s shared."
            ),
            TutorialStep(
                title = "Customize",
                body = "Upload a custom wallpaper and keep overlays if you want."
            )
        )
    }

    var index by remember { mutableStateOf(0) }
    val isLast = index == steps.lastIndex

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
                text = steps[index].title,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                text = steps[index].body,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.8f)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                steps.indices.forEach { i ->
                    Box(
                        modifier = Modifier
                            .size(if (i == index) 10.dp else 8.dp)
                            .background(
                                if (i == index) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(999.dp)
                            )
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onSkip) {
                    Text("Skip", color = Color.White.copy(alpha = 0.7f))
                }
                Button(
                    onClick = {
                        if (isLast) onDone() else index += 1
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                ) {
                    Text(if (isLast) "Done" else "Next", color = Color.Black)
                }
            }
        }
    }
}
