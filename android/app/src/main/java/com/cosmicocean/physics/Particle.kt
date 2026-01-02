package com.cosmicocean.physics

data class Particle(
    var x: Float,
    var y: Float,
    var oldX: Float,
    var oldY: Float,
    var mass: Float = 1.0f,
    var radius: Float = 20.0f,
    var isFixed: Boolean = false,
    var vx: Float = 0f,
    var vy: Float = 0f
)
