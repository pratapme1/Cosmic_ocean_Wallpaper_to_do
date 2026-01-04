package com.cosmicocean.physics

import kotlin.math.*

class VerletEngine {
    private val particles = mutableListOf<Particle>()
    private var gravityX = 0f
    private var gravityY = 0f
    private val damping = 0.98f  // INCREASED back to 0.98 to dampen oscillations

    private var boundsWidth = 0f
    private var boundsHeight = 0f

    // FIX: Dynamic spacing based on screen size
    // Mobile screens are smaller, so we need proportionally less spacing
    private var targetSpacing = 60f  // REDUCED from 80 for small screens (393px width)
    private var targetSpacingSq = 3600f
    private val REPULSION_FORCE_COEFF = 0.5f  // REDUCED from 1.2 to prevent aggressive bouncing

    // Velocity cap to prevent explosive movements
    private val MAX_VELOCITY = 15f

    private var spatialHash = SpatialHash(80f)
    private var useSpatialHash = true

    fun setBounds(width: Float, height: Float) {
        // Only update if bounds actually changed (avoid recreating spatial hash every frame)
        if (boundsWidth == width && boundsHeight == height) return

        boundsWidth = width
        boundsHeight = height

        // FIX: Scale spacing by screen density (smaller screens = MUCH smaller spacing)
        val densityFactor = (width / 1080f).coerceIn(0.4f, 1.0f)  // Allow smaller factor for tiny screens
        targetSpacing = 50f * densityFactor  // REDUCED base from 70 to 50 for small screens
        targetSpacingSq = targetSpacing * targetSpacing

        // Rebuild spatial hash with new cell size (only when bounds change)
        spatialHash = SpatialHash(targetSpacing)
    }

    fun addParticle(particle: Particle) {
        particles.add(particle)
    }

    fun removeParticle(particle: Particle) {
        particles.remove(particle)
    }

    fun update(delta: Float) {
        // Clamp delta to prevent instability
        val d = min(delta, 0.016f)

        // Update particles
        particles.forEach { p ->
            if (p.isFixed) return@forEach

            // Calculate velocity from position delta
            val vx = (p.x - p.oldX) * damping
            val vy = (p.y - p.oldY) * damping

            // Store current position
            p.oldX = p.x
            p.oldY = p.y

            // Cap velocity to prevent explosive movements
            val cappedVx = vx.coerceIn(-MAX_VELOCITY, MAX_VELOCITY)
            val cappedVy = vy.coerceIn(-MAX_VELOCITY, MAX_VELOCITY)

            // Verlet integration
            p.x += cappedVx + gravityX * d
            p.y += cappedVy + gravityY * d

            // Store velocity for external use
            p.vx = cappedVx
            p.vy = cappedVy
        }

        applyConstraints()
        applyInteractions()
    }

    private fun applyConstraints() {
        particles.forEach { p ->
            if (p.isFixed) return@forEach

            // Bounce off edges with energy loss
            if (p.x - p.radius < 0) {
                p.x = p.radius
                p.oldX = p.x + (p.x - p.oldX) * 0.7f
            } else if (p.x + p.radius > boundsWidth) {
                p.x = boundsWidth - p.radius
                p.oldX = p.x + (p.x - p.oldX) * 0.7f
            }

            if (p.y - p.radius < 0) {
                p.y = p.radius
                p.oldY = p.y + (p.y - p.oldY) * 0.7f
            } else if (p.y + p.radius > boundsHeight) {
                p.y = boundsHeight - p.radius
                p.oldY = p.y + (p.y - p.oldY) * 0.7f
            }
        }
    }

    private fun applyInteractions() {
        if (useSpatialHash) {
            applyInteractionsOptimized()
        } else {
            applyInteractionsBruteForce()
        }
    }

    private fun applyInteractionsOptimized() {
        spatialHash.clear()
        particles.forEach { spatialHash.insert(it) }

        val processed = mutableSetOf<Particle>()

        particles.forEach { p1 ->
            if (p1.isFixed) return@forEach

            val nearby = spatialHash.getParticlesInCell(p1)
            nearby.forEach { p2 ->
                if (p1 === p2 || p2.isFixed || processed.contains(p2)) return@forEach

                val dx = p2.x - p1.x
                val dy = p2.y - p1.y
                val distSq = dx * dx + dy * dy

                if (distSq < 1f) return@forEach

                // FIX: Use dynamic targetSpacing instead of hardcoded value
                val baseMinDist = (p1.radius + p2.radius) * 1.3f  // REDUCED from 1.8 for gentler separation
                val minDist = max(baseMinDist, targetSpacing * 0.8f)  // Use 80% of targetSpacing
                val minDistSq = minDist * minDist

                if (distSq < minDistSq) {
                    val dist = sqrt(distSq)
                    // SOFTENED force calculation: use square root to reduce explosive force when very close
                    val penetration = minDist - dist
                    val force = sqrt(max(0f, penetration)) * REPULSION_FORCE_COEFF
                    val fx = (dx / dist) * force
                    val fy = (dy / dist) * force

                    p1.x -= fx / p1.mass
                    p1.y -= fy / p1.mass
                    p2.x += fx / p2.mass
                    p2.y += fy / p2.mass
                }

                // FIX: Greatly reduced gravitational clustering (was causing piling)
                // Only apply very weak attraction to prevent complete scattering
                val maxGravityDist = 200f  // Reduced from 300
                val maxGravityDistSq = maxGravityDist * maxGravityDist

                if (distSq < maxGravityDistSq && distSq > minDistSq) {
                    val dist = sqrt(distSq)
                    val gravityStrength = 0.00002f * (1f - dist / maxGravityDist)  // REDUCED from 0.0001
                    val fx = (dx / dist) * gravityStrength
                    val fy = (dy / dist) * gravityStrength

                    p1.x += fx * p2.mass
                    p1.y += fy * p2.mass
                    p2.x -= fx * p1.mass
                    p2.y -= fy * p1.mass
                }
            }
            processed.add(p1)
        }
    }

    private fun applyInteractionsBruteForce() {
        for (i in 0 until particles.size) {
            val p1 = particles[i]
            if (p1.isFixed) continue

            for (j in i + 1 until particles.size) {
                val p2 = particles[j]
                if (p2.isFixed) continue

                val dx = p2.x - p1.x
                val dy = p2.y - p1.y
                val distSq = dx * dx + dy * dy

                if (distSq < 1f) continue

                // FIX: Use dynamic targetSpacing instead of hardcoded value
                val baseMinDist = (p1.radius + p2.radius) * 1.3f  // REDUCED from 1.8 for gentler separation
                val minDist = max(baseMinDist, targetSpacing * 0.8f)  // Use 80% of targetSpacing
                val minDistSq = minDist * minDist

                if (distSq < minDistSq) {
                    val dist = sqrt(distSq)
                    // SOFTENED force calculation: use square root to reduce explosive force when very close
                    val penetration = minDist - dist
                    val force = sqrt(max(0f, penetration)) * REPULSION_FORCE_COEFF
                    val fx = (dx / dist) * force
                    val fy = (dy / dist) * force

                    p1.x -= fx / p1.mass
                    p1.y -= fy / p1.mass
                    p2.x += fx / p2.mass
                    p2.y += fy / p2.mass
                }

                // FIX: Greatly reduced gravitational clustering (was causing piling)
                val maxGravityDist = 200f  // Reduced from 300
                val maxGravityDistSq = maxGravityDist * maxGravityDist

                if (distSq < maxGravityDistSq && distSq > minDistSq) {
                    val dist = sqrt(distSq)
                    val gravityStrength = 0.00002f * (1f - dist / maxGravityDist)  // REDUCED from 0.0001
                    val fx = (dx / dist) * gravityStrength
                    val fy = (dy / dist) * gravityStrength

                    p1.x += fx * p2.mass
                    p1.y += fy * p2.mass
                    p2.x -= fx * p1.mass
                    p2.y -= fy * p1.mass
                }
            }
        }
    }

    fun applyImpulse(x: Float, y: Float, radius: Float, strength: Float) {
        particles.forEach { p ->
            if (p.isFixed) return@forEach

            val dx = p.x - x
            val dy = p.y - y
            val distSq = dx * dx + dy * dy
            val radiusSq = radius * radius

            if (distSq < radiusSq && distSq > 0) {
                val dist = sqrt(distSq)
                val force = (1f - dist / radius) * strength
                val fx = (dx / dist) * force
                val fy = (dy / dist) * force

                p.x += fx / p.mass
                p.y += fy / p.mass
            }
        }
    }

    fun getParticles(): List<Particle> = particles
}
