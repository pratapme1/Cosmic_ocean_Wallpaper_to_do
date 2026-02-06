package com.cosmicocean.effects

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.cosmicocean.model.Star
import com.cosmicocean.physics.VerletEngine
import kotlin.math.*

data class OrbitalRelationship(
    val parentId: String,
    val childId: String,
    var orbitRadius: Float,
    var orbitAngle: Float,
    var angularVelocity: Float,
    var eccentricity: Float,
    var phaseOffset: Float,
    val baseRadius: Float
)

class OrbitalSystem(private val engine: VerletEngine) {
    private val orbits = mutableListOf<OrbitalRelationship>()

    private val MIN_ORBIT_RADIUS = 60f
    private val MAX_ORBIT_RADIUS = 100f
    private val BASE_ANGULAR_VELOCITY = 0.8f
    private val MIN_ECCENTRICITY = 0.05f
    private val MAX_ECCENTRICITY = 0.15f
    private val SUBTASK_SIZE_MULTIPLIER = 0.85f

    fun createOrbit(parent: Star, child: Star) {
        val existingChildren = orbits.filter { it.parentId == parent.id }
        val childIndex = existingChildren.size
        
        val phaseOffset = (childIndex / max(1f, (existingChildren.size + 1).toFloat())) * PI.toFloat() * 2f
        val orbitRadius = MIN_ORBIT_RADIUS + Math.random().toFloat() * (MAX_ORBIT_RADIUS - MIN_ORBIT_RADIUS)
        val angularVelocity = BASE_ANGULAR_VELOCITY * (MAX_ORBIT_RADIUS / orbitRadius)
        val eccentricity = MIN_ECCENTRICITY + Math.random().toFloat() * (MAX_ECCENTRICITY - MIN_ECCENTRICITY)
        
        val baseRadius = child.particle.radius
        child.particle.radius = baseRadius * SUBTASK_SIZE_MULTIPLIER
        
        orbits.add(OrbitalRelationship(
            parentId = parent.id,
            childId = child.id,
            orbitRadius = orbitRadius,
            orbitAngle = phaseOffset,
            angularVelocity = angularVelocity,
            eccentricity = eccentricity,
            phaseOffset = phaseOffset,
            baseRadius = baseRadius
        ))
    }

    fun resetOrbits(stars: List<Star>) {
        if (orbits.isEmpty()) return
        val baseRadiusByChild = orbits.associate { it.childId to it.baseRadius }
        stars.forEach { star ->
            val baseRadius = baseRadiusByChild[star.id]
            if (baseRadius != null) {
                star.particle.radius = baseRadius
                star.particle.isFixed = false
            }
        }
        orbits.clear()
    }

    fun update(stars: List<Star>, delta: Float) {
        orbits.forEach { orbit ->
            val parent = stars.find { it.id == orbit.parentId } ?: return@forEach
            val child = stars.find { it.id == orbit.childId } ?: return@forEach

            orbit.orbitAngle += orbit.angularVelocity * delta
            if (orbit.orbitAngle > PI * 2) orbit.orbitAngle -= (PI * 2).toFloat()

            val a = orbit.orbitRadius
            val b = orbit.orbitRadius * (1 - orbit.eccentricity)
            
            val x = parent.particle.x + cos(orbit.orbitAngle) * a
            val y = parent.particle.y + sin(orbit.orbitAngle) * b

            // Set child position
            child.particle.x = x
            child.particle.y = y
            
            // Maintain velocity for physics engine
            val prevAngle = orbit.orbitAngle - orbit.angularVelocity * delta
            child.particle.oldX = parent.particle.x + cos(prevAngle) * a
            child.particle.oldY = parent.particle.y + sin(prevAngle) * b
            
            child.particle.isFixed = true
        }
    }

    fun draw(drawScope: DrawScope, stars: List<Star>) {
        orbits.forEach { orbit ->
            val parent = stars.find { it.id == orbit.parentId } ?: return@forEach
            val child = stars.find { it.id == orbit.childId } ?: return@forEach

            // Draw orbit path
            val a = orbit.orbitRadius
            val b = orbit.orbitRadius * (1 - orbit.eccentricity)
            val center = androidx.compose.ui.geometry.Offset(parent.particle.x, parent.particle.y)
            val topLeft = androidx.compose.ui.geometry.Offset(center.x - a, center.y - b)
            val size = androidx.compose.ui.geometry.Size(a * 2f, b * 2f)

            drawScope.drawOval(
                color = Color.White,
                topLeft = topLeft,
                size = size,
                alpha = 0.18f,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f)
            )

            // Subtle connector line between parent and child
            drawScope.drawLine(
                color = Color.White,
                start = center,
                end = androidx.compose.ui.geometry.Offset(child.particle.x, child.particle.y),
                strokeWidth = 1f,
                alpha = 0.2f
            )
        }
    }
}
