package com.cosmicocean.integration

import com.cosmicocean.model.Star
import com.cosmicocean.physics.VerletEngine
import com.cosmicocean.physics.ZoneManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HudSafeAreaIntegrationTest {

    companion object {
        private const val SCREEN_WIDTH = 1080f
        private const val SCREEN_HEIGHT = 2400f
        private const val DELTA = 0.016f
    }

    @Test
    fun `stars avoid HUD zones after physics update`() {
        val zoneManager = ZoneManager(SCREEN_WIDTH, SCREEN_HEIGHT)
        val hudZone = ZoneManager.ZoneRect(
            left = SCREEN_WIDTH - 220f,
            top = 40f,
            right = SCREEN_WIDTH - 20f,
            bottom = 220f
        )
        zoneManager.updateReservedZones(listOf(hudZone))

        val star = Star(
            x = SCREEN_WIDTH - 120f,
            y = 120f,
            title = "Test",
            urgency = 2,
            dueDate = System.currentTimeMillis() + 3_600_000
        )

        zoneManager.update(listOf(star), DELTA)

        val strictlyInside = star.particle.x > hudZone.left &&
            star.particle.x < hudZone.right &&
            star.particle.y > hudZone.top &&
            star.particle.y < hudZone.bottom
        assertFalse("Star should be pushed out of HUD zone", strictlyInside)
    }

    @Test
    fun `engine update keeps stars within safe bounds`() {
        val engine = VerletEngine()
        engine.setBounds(SCREEN_WIDTH, SCREEN_HEIGHT)
        val zoneManager = ZoneManager(SCREEN_WIDTH, SCREEN_HEIGHT)
        val hudZone = ZoneManager.ZoneRect(
            left = 0f,
            top = SCREEN_HEIGHT - 260f,
            right = 260f,
            bottom = SCREEN_HEIGHT
        )
        zoneManager.updateReservedZones(listOf(hudZone))

        val star = Star(
            x = 120f,
            y = SCREEN_HEIGHT - 120f,
            title = "Test",
            urgency = 2,
            dueDate = System.currentTimeMillis() + 3_600_000
        )

        zoneManager.update(listOf(star), DELTA)
        engine.addParticle(star.particle)
        engine.update(DELTA)

        assertTrue("Star should remain within screen bounds", star.particle.x in 0f..SCREEN_WIDTH)
        assertTrue("Star should remain within screen bounds", star.particle.y in 0f..SCREEN_HEIGHT)
        val strictlyInside = star.particle.x > hudZone.left &&
            star.particle.x < hudZone.right &&
            star.particle.y > hudZone.top &&
            star.particle.y < hudZone.bottom
        assertFalse("Star should not remain under HUD zone", strictlyInside)
    }
}
