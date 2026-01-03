package com.cosmicocean.physics

import com.cosmicocean.model.Star
import kotlin.math.*

class ZoneManager(var screenWidth: Float, var screenHeight: Float) {

    // Boundaries
    var urgentZoneY = screenHeight * 0.80f
    var futureZoneY = screenHeight * 0.20f
    var completedZoneX = screenWidth * 0.85f
    var archivedZoneX = screenWidth * 0.15f

    // Force strengths - REDUCED for smoother mobile experience
    private val urgencyGravity = 0.06f    // Was 0.12, reduced to prevent aggressive movement
    private val futureAntiGravity = 0.04f // Was 0.08, reduced for gentle floating
    private val completionDrift = 0.03f   // Was 0.06
    private val archiveDrift = 0.03f      // Was 0.06

    // PWA-PARITY: Throttle updates to 60Hz (prevents over-aggressive zone forces)
    private var lastUpdate: Float = 0f
    private val updateInterval: Float = 0.0167f // 60 Hz like PWA

    fun update(stars: List<Star>, delta: Float) {
        // Throttle to 60 Hz for consistent physics (PWA-parity)
        lastUpdate += delta
        if (lastUpdate < updateInterval) return
        lastUpdate = 0f

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
            // HYBRID ZONE LOGIC: Priority + Time based
            val dueIn = star.dueIn
            val urgency = star.urgency

            // P1 = ALWAYS push toward bottom (user said urgent/asap/critical)
            if (urgency == 1) {
                val factor = 0.8f  // Strong but not overwhelming
                star.particle.y += urgencyGravity * factor * normalizedDelta
                // Extra push if overdue
                if (dueIn < 0) {
                    star.particle.y += min(abs(dueIn) * 0.001f, 0.1f) * normalizedDelta
                }
            }
            // P3 = Push toward top (low priority, future)
            else if (urgency == 3) {
                val factor = 0.6f
                star.particle.y -= futureAntiGravity * factor * normalizedDelta
            }
            // P2 = Time-based (middle zone, slight forces based on due time)
            else if (dueIn < 120) { // Due within 2 hours
                val factor = min(1f, (120f - dueIn) / 120f) * 0.5f
                star.particle.y += urgencyGravity * factor * normalizedDelta
            } else if (dueIn > 1440) { // Future (> 24 hours)
                val factor = min(1f, (dueIn - 1440f) / 2880f) * 0.5f
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
