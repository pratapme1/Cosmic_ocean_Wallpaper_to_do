package com.cosmicocean.physics

import com.cosmicocean.model.Star
import kotlin.math.*

class ZoneManager(var screenWidth: Float, var screenHeight: Float) {
    
    // Boundaries
    var urgentZoneY = screenHeight * 0.80f
    var futureZoneY = screenHeight * 0.20f
    var completedZoneX = screenWidth * 0.85f
    var archivedZoneX = screenWidth * 0.15f

    // Force strengths
    private val urgencyGravity = 0.12f
    private val futureAntiGravity = 0.08f
    private val completionDrift = 0.06f
    private val archiveDrift = 0.06f

    fun update(stars: List<Star>, delta: Float) {
        val normalizedDelta = delta * 60f

        stars.forEach { star ->
            if (star.particle.isFixed) return@forEach

            // 1. Archive Drift - archived stars drift left slowly
            if (star.isArchived) {
                val driftFactor = min(1f, star.particle.x / archivedZoneX)
                star.particle.x -= archiveDrift * driftFactor * normalizedDelta
                // Edge clamping for archived stars
                val padding = star.particle.radius + 10f
                star.particle.x = star.particle.x.coerceAtLeast(padding)
                star.particle.y = star.particle.y.coerceIn(padding, screenHeight - padding)
                return@forEach
            }

            // 2. Completed stars - no forces (will drift right via Star.update after 10s)
            if (star.isCompleted) {
                return@forEach
            }

            // 3. Vertical Urgency Forces (only for active stars)
            val dueIn = star.dueIn
            if (dueIn < 120) { // Urgent (< 2 hours)
                val factor = min(1f, (120f - dueIn) / 120f)
                star.particle.y += urgencyGravity * factor * normalizedDelta
                if (dueIn < 0) {
                    star.particle.y += min(abs(dueIn) * 0.001f, 0.1f) * normalizedDelta
                }
            } else if (dueIn > 1440) { // Future (> 24 hours)
                val factor = min(1f, (dueIn - 1440f) / 2880f)
                star.particle.y -= futureAntiGravity * factor * normalizedDelta
            }

            // 4. Edge clamping - prevent stars from going off-screen
            // Clamp X (with padding for star radius)
            val padding = star.particle.radius + 10f
            star.particle.x = star.particle.x.coerceIn(padding, screenWidth - padding)

            // Clamp Y (with padding for star radius)
            star.particle.y = star.particle.y.coerceIn(padding, screenHeight - padding)
        }
    }

    fun updateScreenSize(width: Float, height: Float) {
        screenWidth = width
        screenHeight = height
        urgentZoneY = height * 0.80f
        futureZoneY = height * 0.20f
        completedZoneX = width * 0.85f
        archivedZoneX = width * 0.15f
    }
}
