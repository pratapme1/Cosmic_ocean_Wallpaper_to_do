package com.cosmicocean.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cosmicocean.model.Star

@Composable
fun SearchOverlay(
    stars: List<Star>,
    onDismiss: () -> Unit,
    onStarSelected: (Star) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filteredStars = stars.filter { 
        it.title.contains(query, ignoreCase = true) 
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF000814).copy(alpha = 0.95f)
    ) {
        Column(modifier = Modifier.padding(24.dp).padding(top = 32.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Search the Ocean", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF00E5FF))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Filter stars...", color = Color.Gray) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF14141E),
                    unfocusedContainerColor = Color(0xFF14141E),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filteredStars) { star ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onStarSelected(star) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(Color(star.getColor()), shape = androidx.compose.foundation.shape.CircleShape)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(star.title, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (star.dueIn < 0) "Overdue" else "Due in ${star.dueIn.toInt()}m",
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Divider(color = Color.White.copy(alpha = 0.1f))
                }
            }
        }
    }
}
