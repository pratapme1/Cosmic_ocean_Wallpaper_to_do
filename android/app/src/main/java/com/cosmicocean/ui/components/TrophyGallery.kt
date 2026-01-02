package com.cosmicocean.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.cosmicocean.model.Star

@Composable
fun TrophyGallery(
    completedStars: List<Star>,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1A1A2E)
        ) {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🏆 Trophy Gallery", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close", tint = Color.White)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("${completedStars.size} tasks completed", fontSize = 14.sp, color = Color.White.copy(0.7f))
                Spacer(Modifier.height(20.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    val trophies = listOf(
                        Triple("🌟 First", 1, completedStars.size >= 1),
                        Triple("⭐ Started", 10, completedStars.size >= 10),
                        Triple("✨ Roll", 25, completedStars.size >= 25),
                        Triple("💫 Productive", 50, completedStars.size >= 50),
                        Triple("🌠 Achiever", 100, completedStars.size >= 100),
                        Triple("🏅 Champion", 250, completedStars.size >= 250),
                        Triple("🏆 Master", 500, completedStars.size >= 500),
                        Triple("👑 Legend", 1000, completedStars.size >= 1000)
                    )
                    items(trophies) { (name, threshold, unlocked) ->
                        Card(
                            Modifier.fillMaxWidth().height(100.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = if (unlocked) Color(0xFFFFD700) else Color(0xFF2A2A3E))
                        ) {
                            Column(Modifier.fillMaxSize().padding(12.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                                Text(name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.graphicsLayer { alpha = if (unlocked) 1f else 0.3f })
                                Text("$threshold tasks", fontSize = 10.sp, color = Color.White.copy(0.6f))
                            }
                        }
                    }
                }
            }
        }
    }
}
