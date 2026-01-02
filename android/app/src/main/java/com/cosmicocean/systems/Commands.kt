package com.cosmicocean.systems

import com.cosmicocean.effects.ConstellationSystem
import com.cosmicocean.effects.OrbitalSystem
import com.cosmicocean.model.Star
import com.cosmicocean.physics.VerletEngine

class CreateStarCommand(
    private val star: Star,
    private val stars: MutableList<Star>,
    private val engine: VerletEngine,
    private val orbitalSystem: OrbitalSystem,
    private val parentStar: Star? = null
) : Command {
    override val description: String = "Create star ${star.title}"

    override fun execute() {
        stars.add(star)
        engine.addParticle(star.particle)
        if (star.isSubtask && parentStar != null) {
            orbitalSystem.createOrbit(parentStar, star)
        }
    }

    override fun undo() {
        stars.remove(star)
        engine.removeParticle(star.particle)
        // Note: OrbitalSystem should have a method to remove an orbit
    }
}

class LinkStarsCommand(
    private val starA: Star,
    private val starB: Star,
    private val constellationSystem: ConstellationSystem
) : Command {
    override val description: String = "Link ${starA.title} and ${starB.title}"

    override fun execute() {
        constellationSystem.addLink(starA, starB)
    }

    override fun undo() {
        constellationSystem.removeLink(starA.id, starB.id)
    }
}
