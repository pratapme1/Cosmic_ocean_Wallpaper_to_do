package com.cosmicocean.model

import androidx.compose.ui.geometry.Offset
import com.cosmicocean.physics.Particle
import kotlin.math.*

enum class Temperature { BLUE, ORANGE, RED }
enum class EchoInterval { DAILY, WEEKLY, MONTHLY }

class Star(
    x: Float,
    y: Float,
    var title: String,
    var urgency: Int, // 1, 2, 3
    var dueDate: Long?, // Timestamp
    val isSubtask: Boolean = false,
    val isRecurring: Boolean = false,
    val echoInterval: EchoInterval? = null,
    val createdAt: Long = System.currentTimeMillis(),
    var id: String = "star-${System.currentTimeMillis()}"
) {
    val particle: Particle
    var temperature: Temperature = Temperature.BLUE
    var dueIn: Float = 0f // minutes
    var isCompleted: Boolean = false
    var completedAt: Long? = null
    var isArchived: Boolean = false
    var archivedAt: Long? = null
    var isDragging: Boolean = false
    var filterOpacity: Float = 1.0f
    var targetFilterOpacity: Float = 1.0f
    var isSnoozed: Boolean = false
    var snoozeUntil: Long? = null

    private var phase: Float = (Math.random() * PI * 2).toFloat()
    private val pulseSpeed: Float = (1.0 + Math.random() * 0.5).toFloat()
    private val shakeSeedX: Float = (Math.random() * 1000).toFloat()
    private val shakeSeedY: Float = (Math.random() * 1000).toFloat()

    init {
        updateDueIn()

        // Initialize particle with urgency-based mass and radius
        particle = Particle(
            x = x,
            y = y,
            oldX = x,
            oldY = y,
            mass = MASS_MAP[urgency] ?: MASS_DEFAULT,
            radius = RADIUS_MAP[urgency] ?: RADIUS_DEFAULT
        )
    }

    fun updateDueIn() {
        dueDate?.let {
            val diffMs = it - System.currentTimeMillis()
            dueIn = diffMs / 60000f
        }
        // CRITICAL FIX: If no due date, treat as future (BLUE), not urgent
        // Tasks without due dates should be calm blue, not orange
        temperature = when {
            dueDate == null -> Temperature.BLUE  // No due date = Future/Calm
            dueIn < 0 -> Temperature.RED         // Overdue
            dueIn < 120 -> Temperature.ORANGE    // Due within 2 hours
            else -> Temperature.BLUE             // Future
        }
    }

    fun getColor(): Int {
        return when (temperature) {
            Temperature.BLUE -> 0xFF3AA0FF.toInt()
            Temperature.ORANGE -> 0xFFFF9A3A.toInt()
            Temperature.RED -> 0xFFFF3B30.toInt()
        }
    }

    fun update(delta: Float) {
        if (!isArchived && !isCompleted) {
            updateDueIn()
        }

        // PWA-accurate completion animation: Auto-fade and drift to achievement zone after 10 seconds
        if (isCompleted && !isArchived && completedAt != null) {
            val elapsedMs = System.currentTimeMillis() - completedAt!!
            val ACHIEVEMENT_DELAY_MS = 10000L // 10 seconds
            val FADE_DURATION_MS = 2000L // 2 seconds to fade out

            if (elapsedMs >= ACHIEVEMENT_DELAY_MS) {
                val fadeProgress = ((elapsedMs - ACHIEVEMENT_DELAY_MS).toFloat() / FADE_DURATION_MS).coerceIn(0f, 1f)
                targetFilterOpacity = max(0f, 1f - fadeProgress)

                // Drift right at 30px/sec
                particle.x += 30f * delta

                // Signal for removal when fade completes
                if (fadeProgress >= 1.0f) {
                    particle.radius = 0f
                }
            }
        }

        // Pulse logic
        phase += 0.02f * pulseSpeed

        // Smooth filter opacity
        if (abs(filterOpacity - targetFilterOpacity) > 0.01f) {
            filterOpacity += (targetFilterOpacity - filterOpacity) * 0.15f
        }
    }

    fun getPulse(): Float = sin(phase) * 0.15f + 1f

    /**
     * Get cry shake offset for this star
     * PWA-accurate: Per-star randomized shake with intensity based on overdue time
     */
    fun getCryOffset(): Offset {
        if (temperature != Temperature.RED) return Offset.Zero

        val intensity = getCryIntensity()
        if (intensity == 0f) return Offset.Zero

        // High-frequency jitter (~30Hz) for realistic shake
        val time = System.currentTimeMillis() * 0.03f

        // Each star has unique shake pattern
        val shakeX = sin(time + shakeSeedX) * 3f * intensity
        val shakeY = sin(time * 1.3f + shakeSeedY) * 3f * intensity

        return Offset(shakeX, shakeY)
    }

    /**
     * Calculate global screen shake intensity from all crying stars
     */
    companion object {
        // MOBILE-OPTIMIZED: Larger than PWA for touch interaction
        // PWA uses 26/18/12 but that's too small for mobile touch targets
        // Mobile needs ~2x size for comfortable interaction
        private const val RADIUS_P1_URGENT = 52f    // Largest (2x PWA for mobile)
        private const val RADIUS_P2_NORMAL = 36f    // Medium (2x PWA for mobile)
        private const val RADIUS_P3_FUTURE = 24f    // Smallest (2x PWA for mobile)
        private const val RADIUS_DEFAULT = RADIUS_P2_NORMAL

        // Physics Model: INVERSE mass (urgent tasks accelerate faster)
        // P1 (urgent): LOW mass (1) → faster response to urgency forces
        // P2 (normal): MED mass (2) → moderate response
        // P3 (future): HIGH mass (4) → slower response (more inertia)
        private const val MASS_P1_URGENT = 1f    // Lightest (responds quickly to forces)
        private const val MASS_P2_NORMAL = 2f    // Medium
        private const val MASS_P3_FUTURE = 4f    // Heaviest (resists forces)
        private const val MASS_DEFAULT = MASS_P2_NORMAL

        private val RADIUS_MAP = mapOf(
            1 to RADIUS_P1_URGENT,
            2 to RADIUS_P2_NORMAL,
            3 to RADIUS_P3_FUTURE
        )

        private val MASS_MAP = mapOf(
            1 to MASS_P1_URGENT,
            2 to MASS_P2_NORMAL,
            3 to MASS_P3_FUTURE
        )

        fun getGlobalShakeOffset(stars: List<Star>): Offset {
            val cryingStars = stars.filter { it.isOverdue() && !it.isCompleted && !it.isArchived }
            if (cryingStars.isEmpty()) return Offset.Zero

            // Sum all cry intensities
            val totalIntensity = cryingStars.sumOf { it.getCryIntensity().toDouble() }.toFloat()
            val scaledIntensity = min(1.0f, totalIntensity * 0.3f)

            // Global shake (affects entire canvas)
            val time = System.currentTimeMillis() * 0.05f
            val shakeX = sin(time) * 5f * scaledIntensity
            val shakeY = sin(time * 1.4f) * 5f * scaledIntensity

            return Offset(shakeX, shakeY)
        }
    }

    /**
     * Check if this star is overdue
     */
    fun isOverdue(): Boolean {
        return temperature == Temperature.RED && dueIn < 0
    }

    /**
     * Get days overdue (for cry intensity calculation)
     */
    fun getDaysOverdue(): Float {
        if (!isOverdue()) return 0f
        return abs(dueIn) / (60f * 24f) // Convert minutes to days
    }

    /**
     * Get cry intensity (0-1)
     */
    fun getCryIntensity(): Float {
        if (!isOverdue()) return 0f
        return min(getDaysOverdue() * 0.2f, 1f)
    }

    /**
     * Snooze this star for a specified duration in minutes
     * Default: 60 minutes (1 hour)
     */
    fun snooze(durationMinutes: Int = 60) {
        val snoozeTime = System.currentTimeMillis() + (durationMinutes * 60 * 1000)
        snoozeUntil = snoozeTime
        isSnoozed = true
    }

    /**
     * Check if snooze has expired and clear snooze state if so
     */
    fun checkSnoozeExpired(): Boolean {
        snoozeUntil?.let { until ->
            if (System.currentTimeMillis() >= until) {
                clearSnooze()
                return true
            }
        }
        return false
    }

    /**
     * Clear snooze state
     */
    fun clearSnooze() {
        isSnoozed = false
        snoozeUntil = null
    }

    /**
     * Get remaining snooze time in minutes
     */
    fun getRemainingSnoozeMinutes(): Int {
        snoozeUntil?.let { until ->
            val remaining = (until - System.currentTimeMillis()) / (60 * 1000)
            return maxOf(0, remaining.toInt())
        }
        return 0
    }
}
