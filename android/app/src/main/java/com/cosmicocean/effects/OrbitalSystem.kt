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
    var phaseOffset: Float
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
        
        child.particle.radius *= SUBTASK_SIZE_MULTIPLIER
        
        orbits.add(OrbitalRelationship(
            parentId = parent.id,
            childId = child.id,
            orbitRadius = orbitRadius,
            orbitAngle = phaseOffset,
            angularVelocity = angularVelocity,
            eccentricity = eccentricity,
            phaseOffset = phaseOffset
        ))
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
            
            // Simplified: just a line between parent and child for now
            drawScope.drawLine(
                color = Color.White,
                start = androidx.compose.ui.geometry.Offset(parent.particle.x, parent.particle.y),
                end = androidx.compose.ui.geometry.Offset(child.particle.x, child.particle.y),
                strokeWidth = 1f,
                alpha = 0.2f
            )
        }
    }
}
