package com.cosmicocean.effects

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.sin

class Atmosphere {
    private val backgroundStars = List(100) {
        BackgroundStar(
            x = (0..2000).random().toFloat(),
            y = (0..3000).random().toFloat(),
            size = (1..4).random().toFloat(),
            speed = (0.5f + Math.random() * 1.5).toFloat()
        )
    }

    fun draw(drawScope: DrawScope, tick: Int) {
        // 1. Nebula Layer (Subtle gradients)
        drawScope.drawCircle(
            color = Color(0xFF001233).copy(alpha = 0.4f),
            radius = 1000f,
            center = Offset(0f, 0f)
        )
        
        // 2. Twinkling Stars
        backgroundStars.forEach { star ->
            val twinkle = sin(tick * 0.05f * star.speed) * 0.5f + 0.5f
            drawScope.drawCircle(
                color = Color.White.copy(alpha = 0.2f + (0.4f * twinkle)),
                radius = star.size,
                center = Offset(star.x % drawScope.size.width, star.y % drawScope.size.height)
            )
        }
    }

    private data class BackgroundStar(val x: Float, val y: Float, val size: Float, val speed: Float)
}
