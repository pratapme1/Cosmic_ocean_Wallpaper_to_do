package com.cosmicocean.physics

import com.cosmicocean.model.Star
import kotlin.math.*

class ZoneManager(var screenWidth: Float, var screenHeight: Float) {

    // Completion/Archive zone boundaries (for swipe gestures only)
    var completedZoneX = screenWidth * 0.85f
    var archivedZoneX = screenWidth * 0.15f

    // EPIC 9: SIMPLIFIED - No zone forces, only edge clamping
    fun update(stars: List<Star>, delta: Float) {
        stars.forEach { star ->
            if (star.particle.isFixed) return@forEach

            // Edge clamping only - prevent stars from going off-screen
            val padding = star.particle.radius + 10f
            star.particle.x = star.particle.x.coerceIn(padding, screenWidth - padding)
            star.particle.y = star.particle.y.coerceIn(padding, screenHeight - padding)
        }
    }

    fun updateScreenSize(width: Float, height: Float) {
        screenWidth = width
        screenHeight = height
        completedZoneX = width * 0.85f
        archivedZoneX = width * 0.15f
    }
}
