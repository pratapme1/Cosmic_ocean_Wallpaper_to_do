package com.cosmicocean.effects

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.cosmicocean.model.Star
import com.cosmicocean.physics.VerletEngine
import kotlin.math.*

data class ConstellationLink(
    val starAId: String,
    val starBId: String,
    val createdAt: Long = System.currentTimeMillis(),
    var strength: Float = 0f,
    var fadeDirection: FadeDirection = FadeDirection.IN
)

enum class FadeDirection { IN, OUT, STABLE }

data class LinkParticle(
    val linkId: String,
    var position: Float,
    val speed: Float
)

class ConstellationSystem(private val engine: VerletEngine) {
    private val links = mutableMapOf<String, ConstellationLink>()
    private val linkParticles = mutableListOf<LinkParticle>()

    private val LINK_FADE_SPEED = 0.02f
    private val SPRING_STRENGTH = 0.0003f
    private val SPRING_DAMPING = 0.95f
    private val PARTICLE_SPEED = 0.3f
    private val PARTICLE_COUNT_PER_LINK = 3

    fun addLink(starA: Star, starB: Star) {
        val id = getLinkId(starA.id, starB.id)
        if (links.containsKey(id)) return

        links[id] = ConstellationLink(starA.id, starB.id)
        
        repeat(PARTICLE_COUNT_PER_LINK) {
            linkParticles.add(
                LinkParticle(
                    linkId = id,
                    position = Math.random().toFloat(),
                    speed = PARTICLE_SPEED * (0.8f + Math.random().toFloat() * 0.4f)
                )
            )
        }
    }

    fun removeLink(starAId: String, starBId: String) {
        val id = getLinkId(starAId, starBId)
        links[id]?.fadeDirection = FadeDirection.OUT
    }

    private fun getLinkId(id1: String, id2: String): String {
        return if (id1 < id2) "${id1}_${id2}" else "${id2}_${id1}"
    }

    fun update(stars: List<Star>, delta: Float) {
        val linksToRemove = mutableListOf<String>()

        links.forEach { (id, link) ->
            when (link.fadeDirection) {
                FadeDirection.IN -> {
                    link.strength = min(1f, link.strength + LINK_FADE_SPEED)
                    if (link.strength >= 1f) link.fadeDirection = FadeDirection.STABLE
                }
                FadeDirection.OUT -> {
                    link.strength = max(0f, link.strength - LINK_FADE_SPEED)
                    if (link.strength <= 0f) linksToRemove.add(id)
                }
                FadeDirection.STABLE -> {}
            }
        }

        linksToRemove.forEach { id ->
            links.remove(id)
            linkParticles.removeAll { it.linkId == id }
        }

        linkParticles.forEach { p ->
            p.position += p.speed * delta
            if (p.position > 1f) p.position = 0f
        }

        applySpringConstraints(stars)
    }

    private fun applySpringConstraints(stars: List<Star>) {
        links.forEach { (_, link) ->
            val starA = stars.find { it.id == link.starAId }
            val starB = stars.find { it.id == link.starBId }
            if (starA == null || starB == null) return@forEach

            val pA = starA.particle
            val pB = starB.particle

            if (pA.isFixed || pB.isFixed) return@forEach

            val dx = pB.x - pA.x
            val dy = pB.y - pA.y
            val distance = sqrt(dx * dx + dy * dy)

            if (distance < 1f) return@forEach

            val force = distance * SPRING_STRENGTH * link.strength
            val fx = (dx / distance) * force
            val fy = (dy / distance) * force

            pA.x += fx / pA.mass
            pA.y += fy / pA.mass
            pB.x -= fx / pB.mass
            pB.y -= fy / pB.mass

            pA.oldX = pA.oldX * SPRING_DAMPING + pA.x * (1 - SPRING_DAMPING)
            pA.oldY = pA.oldY * SPRING_DAMPING + pA.y * (1 - SPRING_DAMPING)
            pB.oldX = pB.oldX * SPRING_DAMPING + pB.x * (1 - SPRING_DAMPING)
            pB.oldY = pB.oldY * SPRING_DAMPING + pB.y * (1 - SPRING_DAMPING)
        }
    }

    fun draw(drawScope: DrawScope, stars: List<Star>) {
        val time = System.currentTimeMillis() / 1000f

        links.forEach { (id, link) ->
            val starA = stars.find { it.id == link.starAId }
            val starB = stars.find { it.id == link.starBId }
            if (starA == null || starB == null) return@forEach

            val colorA = Color(starA.getColor())
            val colorB = Color(starB.getColor())
            
            val pulseA = sin(time * 1.5f + link.createdAt * 0.001f) * 0.2f + 0.8f
            val pulseB = sin(time * 1.5f + link.createdAt * 0.001f + PI.toFloat()) * 0.2f + 0.8f
            val pulse = (pulseA + pulseB) / 2f
            val baseAlpha = 0.4f * link.strength * pulse

            // Draw link line
            drawScope.drawLine(
                color = colorA, // Simplified: use colorA instead of blending
                start = androidx.compose.ui.geometry.Offset(starA.particle.x, starA.particle.y),
                end = androidx.compose.ui.geometry.Offset(starB.particle.x, starB.particle.y),
                strokeWidth = 2f,
                alpha = baseAlpha
            )

            // Draw traveling particles
            linkParticles.filter { it.linkId == id }.forEach { p ->
                val x = starA.particle.x + (starB.particle.x - starA.particle.x) * p.position
                val y = starA.particle.y + (starB.particle.y - starA.particle.y) * p.position
                drawScope.drawCircle(
                    color = colorA,
                    radius = 3f,
                    center = androidx.compose.ui.geometry.Offset(x, y),
                    alpha = baseAlpha * 1.5f
                )
            }
        }
    }
}
