package com.cosmicocean.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cosmicocean.model.ParsedTaskResult

/**
 * Epic 8: LLM Intelligence Enhancement
 * Live preview of parsed task data
 *
 * Shows user what the LLM extracted from their input
 * Allows confirmation or editing before task creation
 */
@Composable
fun TaskParsePreview(
    parsedResult: ParsedTaskResult,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (parsedResult.source == "llm") {
                Color(0xFF1E3A5F).copy(alpha = 0.9f) // Blue for LLM
            } else {
                Color(0xFF3E3E3E).copy(alpha = 0.9f) // Gray for fallback
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Task Preview",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                // Source badge
                Text(
                    text = if (parsedResult.source == "llm") "AI Powered" else "Quick Parse",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .background(
                            color = if (parsedResult.source == "llm")
                                Color(0xFF4CAF50).copy(alpha = 0.3f)
                            else
                                Color(0xFFFF9800).copy(alpha = 0.3f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Task Title
            Text(
                text = parsedResult.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Parsed metadata
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Due date/time
                if (parsedResult.dueDate != null || parsedResult.dueTime != null) {
                    MetadataRow(
                        icon = "📅",
                        label = "Due",
                        value = buildString {
                            if (parsedResult.dueDate != null) append(parsedResult.dueDate)
                            if (parsedResult.dueTime != null) {
                                if (isNotEmpty()) append(" at ")
                                append(parsedResult.dueTime)
                            }
                        }
                    )
                }

                // Priority
                MetadataRow(
                    icon = when (parsedResult.priority) {
                        1 -> "🔴"
                        2 -> "🟡"
                        else -> "🟢"
                    },
                    label = "Priority",
                    value = when (parsedResult.priority) {
                        1 -> "High"
                        2 -> "Medium"
                        3 -> "Low"
                        else -> "Unknown"
                    }
                )

                // Category
                parsedResult.category?.let { category ->
                    MetadataRow(
                        icon = when (category) {
                            "work" -> "💼"
                            "personal" -> "👤"
                            "health" -> "💪"
                            "finance" -> "💰"
                            else -> "📋"
                        },
                        label = "Category",
                        value = category.capitalize()
                    )
                }

                // Energy level
                parsedResult.energyLevel?.let { energy ->
                    MetadataRow(
                        icon = when (energy) {
                            "high" -> "⚡"
                            "low" -> "🔋"
                            else -> "💡"
                        },
                        label = "Energy",
                        value = energy.capitalize()
                    )
                }

                // Context tags
                if (!parsedResult.contextTags.isNullOrEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "📍",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Text(
                            text = parsedResult.contextTags.joinToString(", "),
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }
                }

                // Confidence (if LLM)
                if (parsedResult.source == "llm") {
                    MetadataRow(
                        icon = "✨",
                        label = "Confidence",
                        value = "${(parsedResult.confidence * 100).toInt()}%"
                    )
                }

                // Rate limit warning
                parsedResult.rateLimitInfo?.let { rateLimit ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "⚠️ ${rateLimit.message}",
                        fontSize = 12.sp,
                        color = Color(0xFFFFB74D),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Edit")
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Confirm", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun MetadataRow(icon: String, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon,
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$label:",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
    }
}
