package com.cosmicocean.interactions

import androidx.compose.ui.geometry.Offset
import com.cosmicocean.model.Star
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.math.abs

/**
 * Swirl Gesture Detection for Snooze
 * PWA-accurate: Detects circular motion over stars, triggers snooze at 720° (2 full circles)
 */
class GestureController {

    private var isTracking = false
    private var targetStar: Star? = null
    private val pathPoints = mutableListOf<Offset>()
    private var cumulativeAngle = 0f
    private var lastAngle: Float? = null
    private var centerPoint: Offset? = null

    // Gesture configuration - PWA parity
    private val minSnoozeDegrees = 90f // Minimum rotation to trigger snooze (15min)
    private val minPathLength = 5 // Minimum points to detect gesture
    private val maxDistanceFromCenter = 100f // Maximum drift from center
    private val minAngleDelta = 5f // Minimum angle change to count

    /**
     * Start tracking a swirl gesture
     */
    fun startSwirl(star: Star, touchPoint: Offset) {
        isTracking = true
        targetStar = star
        pathPoints.clear()
        pathPoints.add(touchPoint)
        cumulativeAngle = 0f
        lastAngle = null
        centerPoint = Offset(star.particle.x, star.particle.y)
    }

    /**
     * Update swirl path with new touch point
     * PWA parity: No longer returns boolean - snooze applied on gesture end
     */
    fun updateSwirl(touchPoint: Offset) {
        if (!isTracking || centerPoint == null) return

        pathPoints.add(touchPoint)

        // Keep only recent points (sliding window)
        if (pathPoints.size > 50) {
            pathPoints.removeAt(0)
        }

        // Check if still near the star
        val distance = distanceFromCenter(touchPoint)
        if (distance > maxDistanceFromCenter) {
            // Drifted too far, cancel gesture
            cancelSwirl()
            return
        }

        // Calculate angle from center
        val center = centerPoint!!
        val dx = touchPoint.x - center.x
        val dy = touchPoint.y - center.y
        val currentAngle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()

        // Update cumulative angle
        if (lastAngle != null) {
            var angleDelta = currentAngle - lastAngle!!

            // Normalize angle delta to -180 to 180
            while (angleDelta > 180f) angleDelta -= 360f
            while (angleDelta < -180f) angleDelta += 360f

            // Only count significant changes
            if (abs(angleDelta) > minAngleDelta) {
                cumulativeAngle += abs(angleDelta)
            }
        }

        lastAngle = currentAngle
    }

    /**
     * End swirl gesture
     */
    fun endSwirl() {
        isTracking = false
        targetStar = null
        pathPoints.clear()
        cumulativeAngle = 0f
        lastAngle = null
        centerPoint = null
    }

    /**
     * Cancel swirl gesture
     */
    fun cancelSwirl() {
        endSwirl()
    }

    /**
     * Check if minimum snooze threshold is reached
     */
    fun canSnooze(): Boolean {
        return cumulativeAngle >= minSnoozeDegrees
    }

    /**
     * Get current swirl progress (0.0 to 1.0)
     * PWA parity: Progress based on angle milestones
     */
    fun getSwirlProgress(): Float {
        return when {
            cumulativeAngle < 90f -> cumulativeAngle / 90f * 0.2f
            cumulativeAngle < 180f -> 0.2f + (cumulativeAngle - 90f) / 90f * 0.2f
            cumulativeAngle < 270f -> 0.4f + (cumulativeAngle - 180f) / 90f * 0.2f
            cumulativeAngle < 360f -> 0.6f + (cumulativeAngle - 270f) / 90f * 0.2f
            cumulativeAngle < 540f -> 0.8f + (cumulativeAngle - 360f) / 180f * 0.15f
            else -> 0.95f + (cumulativeAngle - 540f) / 180f * 0.05f
        }.coerceIn(0f, 1f)
    }

    /**
     * Get path points for visual feedback
     */
    fun getPathPoints(): List<Offset> {
        return pathPoints.toList()
    }

    /**
     * Check if currently tracking a swirl
     */
    fun isSwirling(): Boolean {
        return isTracking
    }

    /**
     * Get the star being swirled
     */
    fun getTargetStar(): Star? {
        return targetStar
    }

    /**
     * Calculate distance from center point
     */
    private fun distanceFromCenter(point: Offset): Float {
        val center = centerPoint ?: return Float.MAX_VALUE
        val dx = point.x - center.x
        val dy = point.y - center.y
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Detect if touch is near a star
     */
    fun isTouchNearStar(touchPoint: Offset, star: Star, threshold: Float = 50f): Boolean {
        val dx = touchPoint.x - star.particle.x
        val dy = touchPoint.y - star.particle.y
        val distance = sqrt(dx * dx + dy * dy)
        return distance <= threshold
    }

    /**
     * Get cumulative angle for debugging
     */
    fun getCumulativeAngle(): Float {
        return cumulativeAngle
    }

    /**
     * Get snooze duration in minutes based on cumulative angle
     * PWA-accurate mapping (FIXED: use >= for correct boundaries):
     * - 90° = 15 minutes
     * - 180° = 30 minutes
     * - 270° = 1 hour (60 minutes)
     * - 360° = 2 hours (120 minutes)
     * - 540° = 1 day (1440 minutes)
     */
    fun getSnoozeDuration(): Int {
        return when {
            cumulativeAngle >= 540f -> 1440 // 1 day
            cumulativeAngle >= 360f -> 120  // 2 hours
            cumulativeAngle >= 270f -> 60   // 1 hour
            cumulativeAngle >= 180f -> 30   // 30 min
            cumulativeAngle >= 90f -> 15    // 15 min
            else -> 0
        }
    }

    /**
     * Get human-readable snooze duration string
     */
    fun getSnoozeDurationString(): String {
        val minutes = getSnoozeDuration()
        return when {
            minutes == 0 -> ""
            minutes < 60 -> "${minutes}m"
            minutes < 1440 -> "${minutes / 60}h"
            else -> "1d"
        }
    }

    /**
     * Get center point for visual feedback
     */
    fun getCenterPoint(): Offset? {
        return centerPoint
    }

    /**
     * Calculate visual feedback radius based on progress
     */
    fun getFeedbackRadius(): Float {
        val baseRadius = 30f
        val maxRadius = 60f
        val progress = getSwirlProgress()
        return baseRadius + (maxRadius - baseRadius) * progress
    }

    /**
     * Get trail alpha for visual feedback
     */
    fun getTrailAlpha(): Float {
        return getSwirlProgress() * 0.6f
    }
}

/**
 * Gesture state data class for UI feedback
 */
data class GestureState(
    val isActive: Boolean = false,
    val progress: Float = 0f,
    val centerPoint: Offset? = null,
    val pathPoints: List<Offset> = emptyList(),
    val feedbackRadius: Float = 30f,
    val trailAlpha: Float = 0f,
    val snoozeDuration: String = ""
)

/**
 * Convert GestureController state to GestureState for UI
 */
fun GestureController.toGestureState(): GestureState {
    return GestureState(
        isActive = isSwirling(),
        progress = getSwirlProgress(),
        centerPoint = getCenterPoint(),
        pathPoints = getPathPoints(),
        feedbackRadius = getFeedbackRadius(),
        trailAlpha = getTrailAlpha(),
        snoozeDuration = getSnoozeDurationString()
    )
}
