package com.cosmicocean.effects

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Patina Particle System - Completion Confetti
 * PWA-accurate: Physics-based particles that scatter and fade on task completion
 */
class PatinaSystem {

    private val particles = mutableListOf<PatinaParticle>()
    private val maxParticles = 30

    /**
     * Trigger confetti burst at specified location
     */
    fun burst(x: Float, y: Float, color: Color = Color(0xFF00FF88)) {
        // Create particles in a radial burst pattern
        val particleCount = 20 + Random.nextInt(10)

        for (i in 0 until particleCount) {
            val angle = (i.toFloat() / particleCount) * 2 * Math.PI + Random.nextDouble(-0.3, 0.3)
            val speed = 100f + Random.nextFloat() * 150f
            val vx = cos(angle).toFloat() * speed
            val vy = sin(angle).toFloat() * speed

            particles.add(
                PatinaParticle(
                    x = x,
                    y = y,
                    vx = vx,
                    vy = vy,
                    color = randomizeColor(color),
                    size = 3f + Random.nextFloat() * 4f,
                    life = 1f,
                    gravity = 200f + Random.nextFloat() * 100f,
                    drag = 0.97f + Random.nextFloat() * 0.02f
                )
            )
        }

        // Limit total particle count
        while (particles.size > maxParticles) {
            particles.removeAt(0)
        }
    }

    /**
     * Update all particles
     */
    fun update(delta: Float) {
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val particle = iterator.next()
            particle.update(delta)

            // Remove dead particles
            if (particle.life <= 0f) {
                iterator.remove()
            }
        }
    }

    /**
     * Draw all particles
     */
    fun draw(drawScope: DrawScope) {
        particles.forEach { particle ->
            particle.draw(drawScope)
        }
    }

    /**
     * Check if any particles are active
     */
    fun hasActiveParticles(): Boolean {
        return particles.isNotEmpty()
    }

    /**
     * Clear all particles
     */
    fun clear() {
        particles.clear()
    }

    /**
     * Randomize color slightly for variety
     */
    private fun randomizeColor(baseColor: Color): Color {
        val hueShift = Random.nextFloat() * 0.2f - 0.1f // ±10% hue shift
        val saturationShift = Random.nextFloat() * 0.2f // +0-20% saturation

        return when (Random.nextInt(5)) {
            0 -> Color(0xFFFFD700) // Gold
            1 -> Color(0xFF00E5FF) // Cyan
            2 -> Color(0xFFFF3B30) // Red
            3 -> Color(0xFF00FF88) // Green
            else -> baseColor
        }
    }
}

/**
 * Individual particle with physics
 */
data class PatinaParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Color,
    val size: Float,
    var life: Float = 1f,
    val gravity: Float = 300f,
    val drag: Float = 0.98f
) {
    private var rotation: Float = Random.nextFloat() * 360f
    private var rotationSpeed: Float = Random.nextFloat() * 720f - 360f

    /**
     * Update particle physics
     */
    fun update(delta: Float) {
        // Apply gravity
        vy += gravity * delta

        // Apply drag
        vx *= drag
        vy *= drag

        // Update position
        x += vx * delta
        y += vy * delta

        // Update rotation
        rotation += rotationSpeed * delta

        // Decay life
        life -= delta * 1.5f // Particles last ~0.66 seconds
    }

    /**
     * Draw particle
     */
    fun draw(drawScope: DrawScope) {
        val particleSize = size // Store size before entering drawScope context
        with(drawScope) {
            // Fade out based on life
            val alpha = life.coerceIn(0f, 1f)

            // Draw particle as a circle with glow
            drawCircle(
                color = color.copy(alpha = alpha * 0.3f),
                radius = particleSize * 2f,
                center = Offset(x, y)
            )

            drawCircle(
                color = color.copy(alpha = alpha * 0.8f),
                radius = particleSize,
                center = Offset(x, y)
            )
        }
    }
}

/**
 * Preset burst patterns
 */
object PatinaPresets {

    /**
     * Standard completion burst - green confetti
     */
    fun completionBurst(system: PatinaSystem, x: Float, y: Float) {
        system.burst(x, y, Color(0xFF00FF88))
    }

    /**
     * Achievement unlock burst - gold confetti
     */
    fun achievementBurst(system: PatinaSystem, x: Float, y: Float) {
        system.burst(x, y, Color(0xFFFFD700))
    }

    /**
     * Archive burst - gray confetti
     */
    fun archiveBurst(system: PatinaSystem, x: Float, y: Float) {
        system.burst(x, y, Color(0xFF888888))
    }

    /**
     * Snooze burst - cyan confetti
     */
    fun snoozeBurst(system: PatinaSystem, x: Float, y: Float) {
        system.burst(x, y, Color(0xFF00E5FF))
    }
}
