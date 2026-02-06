package com.cosmicocean.physics

import com.cosmicocean.model.Star
import kotlin.math.*

class ZoneManager(var screenWidth: Float, var screenHeight: Float) {

    data class ZoneRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        fun expanded(padding: Float): ZoneRect {
            return ZoneRect(left - padding, top - padding, right + padding, bottom + padding)
        }

        fun contains(x: Float, y: Float): Boolean {
            return x > left && x < right && y > top && y < bottom
        }
    }

    // Completion/Archive zone boundaries (for swipe gestures only)
    var completedZoneX = screenWidth * 0.85f
    var archivedZoneX = screenWidth * 0.15f
    private var reservedZones: List<ZoneRect> = emptyList()

    // EPIC 9: SIMPLIFIED - No zone forces, only edge clamping
    fun update(stars: List<Star>, delta: Float) {
        stars.forEach { star ->
            if (star.particle.isFixed) return@forEach

            // Edge clamping only - prevent stars from going off-screen
            val padding = star.particle.radius + 10f
            star.particle.x = star.particle.x.coerceIn(padding, screenWidth - padding)
            star.particle.y = star.particle.y.coerceIn(padding, screenHeight - padding)

            if (!star.isDragging && reservedZones.isNotEmpty()) {
                val adjusted = clampToReservedZones(star.particle.x, star.particle.y, padding)
                star.particle.x = adjusted.first
                star.particle.y = adjusted.second
            }
        }
    }

    fun updateScreenSize(width: Float, height: Float) {
        screenWidth = width
        screenHeight = height
        completedZoneX = width * 0.85f
        archivedZoneX = width * 0.15f
    }

    fun updateReservedZones(zones: List<ZoneRect>) {
        reservedZones = zones
    }

    fun clampToSafeArea(x: Float, y: Float, radius: Float): Pair<Float, Float> {
        val padding = radius + 10f
        val clampedX = x.coerceIn(padding, screenWidth - padding)
        val clampedY = y.coerceIn(padding, screenHeight - padding)
        return clampToReservedZones(clampedX, clampedY, padding)
    }

    private fun clampToReservedZones(x: Float, y: Float, padding: Float): Pair<Float, Float> {
        var newX = x
        var newY = y

        reservedZones.forEach { rect ->
            val expanded = rect.expanded(padding)
            if (expanded.contains(newX, newY)) {
                val candidates = listOf(
                    Pair(expanded.left, newY),
                    Pair(expanded.right, newY),
                    Pair(newX, expanded.top),
                    Pair(newX, expanded.bottom)
                ).map { (cx, cy) ->
                    Pair(
                        cx.coerceIn(padding, screenWidth - padding),
                        cy.coerceIn(padding, screenHeight - padding)
                    )
                }

                val outsideCandidates = candidates.filterNot { (cx, cy) -> expanded.contains(cx, cy) }
                val fallback = if (outsideCandidates.isNotEmpty()) outsideCandidates else candidates

                val nearest = fallback.minBy { (cx, cy) ->
                    val dx = cx - newX
                    val dy = cy - newY
                    dx * dx + dy * dy
                }

                newX = nearest.first
                newY = nearest.second
            }
        }

        return Pair(newX, newY)
    }
}
