package com.cosmicocean.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cosmicocean.model.Star

@Composable
fun AchievementWall(
    completedStars: List<Star>,
    onClick: () -> Unit = {}
) {
    if (completedStars.isEmpty()) return

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val isSmallScreen = screenHeight < 600.dp
    
    val wallHeight = if (isSmallScreen) 60.dp else 80.dp
    val starSize = if (isSmallScreen) 24.dp else 32.dp
    val contentPadding = if (isSmallScreen) 16.dp else 32.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(wallHeight)
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color(0xFF000814).copy(alpha = 0.95f))
                )
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = contentPadding),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(completedStars) { star ->
                Box(
                    modifier = Modifier
                        .size(starSize)
                        .background(Color(0xFF00FF88), shape = CircleShape)
                )
            }
        }
    }
}
