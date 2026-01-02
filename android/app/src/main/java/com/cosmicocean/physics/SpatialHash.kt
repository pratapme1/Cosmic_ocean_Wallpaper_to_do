package com.cosmicocean.physics

import kotlin.math.*

class SpatialHash(private val cellSize: Float = 100f) {
    private val grid = mutableMapOf<String, MutableList<Particle>>()
    private val particleToCell = mutableMapOf<Particle, String>()

    private fun getKey(x: Float, y: Float): String {
        val cx = floor(x / cellSize).toInt()
        val cy = floor(y / cellSize).toInt()
        return "$cx,$cy"
    }

    fun insert(particle: Particle) {
        val key = getKey(particle.x, particle.y)
        grid.getOrPut(key) { mutableListOf() }.add(particle)
        particleToCell[particle] = key
    }

    fun getNearby(x: Float, y: Float, radius: Float): List<Particle> {
        val cells = ceil(radius / cellSize).toInt()
        val cx = floor(x / cellSize).toInt()
        val cy = floor(y / cellSize).toInt()
        val nearby = mutableListOf<Particle>()

        for (dx in -cells..cells) {
            for (dy in -cells..cells) {
                val key = "${cx + dx},${cy + dy}"
                grid[key]?.let { nearby.addAll(it) }
            }
        }
        return nearby
    }

    fun getParticlesInCell(particle: Particle): List<Particle> {
        val key = particleToCell[particle] ?: return emptyList()
        val parts = key.split(",")
        val cx = parts[0].toInt()
        val cy = parts[1].toInt()
        val nearby = mutableListOf<Particle>()

        for (dx in -1..1) {
            for (dy in -1..1) {
                val neighborKey = "${cx + dx},${cy + dy}"
                grid[neighborKey]?.let { nearby.addAll(it) }
            }
        }
        return nearby
    }

    fun clear() {
        grid.clear()
        particleToCell.clear()
    }
}
