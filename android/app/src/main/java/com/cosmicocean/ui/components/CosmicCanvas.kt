package com.cosmicocean.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.cosmicocean.effects.Atmosphere
import com.cosmicocean.interactions.GestureController
import com.cosmicocean.interactions.toGestureState
import com.cosmicocean.model.Star
import com.cosmicocean.physics.VerletEngine
import com.cosmicocean.physics.ZoneManager
import com.cosmicocean.utils.LabelPositioning
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

data class Ripple(val x: Float, val y: Float, var radius: Float = 0f, var life: Float = 1.0f)

data class DragState(
    var star: Star? = null,
    var startX: Float = 0f,
    var startY: Float = 0f,
    var velocityX: Float = 0f,
    var velocityY: Float = 0f,
    var lastX: Float = 0f,
    var lastY: Float = 0f,
    var lastTime: Long = System.nanoTime(),
    var dragStarted: Boolean = false
)

data class HoldState(
    var star: Star? = null,
    var startX: Float = 0f,
    var startY: Float = 0f,
    var touchX: Float = 0f,  // CRITICAL FIX: Track touch position
    var touchY: Float = 0f,
    var startTime: Long = 0L,
    var isActive: Boolean = false
)

@Composable
fun CosmicCanvas(
    engine: VerletEngine,
    zoneManager: ZoneManager,
    constellationSystem: com.cosmicocean.effects.ConstellationSystem,
    orbitalSystem: com.cosmicocean.effects.OrbitalSystem,
    stars: List<Star>,
    onStarTap: (Star) -> Unit,
    onEmptyDoubleTap: (Offset) -> Unit,
    onInteraction: () -> Unit,
    onStarFinalized: (Star, String) -> Unit,  // "complete", "archive", or "delete"
    onStarSnooze: (Star, Int) -> Unit = { _, _ -> },
    onStarDragEnd: (Star) -> Unit = { _ -> },  // EPIC 9: Save position after drag
    audioEngine: com.cosmicocean.audio.AudioEngine? = null
) {
    var lastFrameTime by remember { mutableLongStateOf(System.nanoTime()) }
    var tick by remember { mutableIntStateOf(0) }
    val ripples = remember { mutableStateListOf<Ripple>() }
    val atmosphere = remember { Atmosphere() }
    val patinaSystem = remember { com.cosmicocean.effects.PatinaSystem() }
    var activeZoneType by remember { mutableStateOf<String?>(null) }
    val textMeasurer = rememberTextMeasurer()
    val gestureController = remember { GestureController() }
    var gestureState by remember { mutableStateOf(gestureController.toGestureState()) }

    // Drag mechanics constants and state
    val dragState = remember { DragState() }
    val DRAG_START_THRESHOLD = 8f
    val VELOCITY_SMOOTHING = 0.3f

    // Hold-to-delete state (PWA parity: 5 seconds)
    val holdState = remember { HoldState() }
    val HOLD_TO_DELETE_DURATION = 5000L // 5 seconds
    val HOLD_MOVEMENT_THRESHOLD = 30f // Abort if moved >30px

    val pendingFinalization = remember { mutableStateMapOf<String, Long>() }

    LaunchedEffect(Unit) {
        while (true) {
            val currentTime = System.nanoTime()
            val delta = (currentTime - lastFrameTime) / 1_000_000_000f
            lastFrameTime = currentTime

            // EPIC 9: ZoneManager simplified to edge clamping only (no zone forces)
            zoneManager.update(stars, delta)
            engine.update(delta)
            constellationSystem.update(stars, delta)
            orbitalSystem.update(stars, delta)
            stars.forEach { it.update(delta) }
            patinaSystem.update(delta)

            val now = System.currentTimeMillis()
            pendingFinalization.entries.toList().forEach { (id, startTime) ->
                if (now - startTime > 5000) {
                    stars.find { it.title + it.createdAt == id }?.let { star ->
                        // Use string action type: "complete" or "archive"
                        onStarFinalized(star, if (star.isCompleted) "complete" else "archive")
                    }
                    pendingFinalization.remove(id)
                }
            }

            val iterator = ripples.iterator()
            while (iterator.hasNext()) {
                val r = iterator.next()
                r.life -= delta * 1.5f
                r.radius += delta * 200f
                if (r.life <= 0) {
                    iterator.remove()
                }
            }

            // Hold-to-delete auto-trigger (PWA parity: 5sec)
            if (holdState.isActive && holdState.star != null) {
                val star = holdState.star!!

                val elapsed = System.currentTimeMillis() - holdState.startTime
                if (elapsed >= HOLD_TO_DELETE_DURATION) {
                    onStarFinalized(star, "delete")  // CRITICAL FIX: Use "delete" action
                    audioEngine?.play(com.cosmicocean.audio.SoundType.TASK_ARCHIVE, volume = 0.8f)
                    holdState.isActive = false
                    holdState.star = null
                }
            }

            tick++
            delay(16)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { offset -> onEmptyDoubleTap(offset) },
                        onLongPress = { offset ->
                            // Hold-to-delete: Start 5-second timer (PWA parity)
                            onInteraction()
                            stars.find {
                                val dx = it.particle.x - offset.x
                                val dy = it.particle.y - offset.y
                                val touchRadius = maxOf(it.particle.radius * 1.5f, 50f)
                                (dx * dx + dy * dy) < (touchRadius * touchRadius)
                            }?.let { star ->
                                holdState.star = star
                                holdState.startX = star.particle.x
                                holdState.startY = star.particle.y
                                holdState.touchX = offset.x  // CRITICAL FIX: Track touch position
                                holdState.touchY = offset.y
                                holdState.startTime = System.currentTimeMillis()
                                holdState.isActive = true
                                audioEngine?.play(com.cosmicocean.audio.SoundType.TAP, volume = 0.6f)
                            }
                        },
                        onTap = { offset ->
                            onInteraction()
                            ripples.add(Ripple(offset.x, offset.y))

                            // Cancel hold-to-delete if active
                            if (holdState.isActive) {
                                holdState.isActive = false
                                holdState.star = null
                            }

                            // Play tap sound
                            audioEngine?.play(com.cosmicocean.audio.SoundType.TAP, volume = 0.5f)

                            stars.find {
                                val dx = it.particle.x - offset.x
                                val dy = it.particle.y - offset.y
                                val touchRadius = maxOf(it.particle.radius * 1.5f, 50f)
                                (dx * dx + dy * dy) < (touchRadius * touchRadius)
                            }?.let {
                                if (pendingFinalization.containsKey(it.title + it.createdAt)) {
                                    // Undo action
                                    audioEngine?.play(com.cosmicocean.audio.SoundType.UNDO, volume = 0.8f)
                                    pendingFinalization.remove(it.title + it.createdAt)
                                    it.isCompleted = false
                                    it.isArchived = false
                                } else {
                                    onStarTap(it)
                                }
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            onInteraction()
                            // Find star near touch point
                            stars.find {
                                val dx = it.particle.x - offset.x
                                val dy = it.particle.y - offset.y
                                val touchRadius = maxOf(it.particle.radius * 1.5f, 50f)
                                (dx * dx + dy * dy) < (touchRadius * touchRadius)
                            }?.let {
                                // Initialize drag state
                                dragState.star = it
                                dragState.startX = offset.x
                                dragState.startY = offset.y
                                dragState.lastX = offset.x
                                dragState.lastY = offset.y
                                dragState.lastTime = System.nanoTime()
                                dragState.velocityX = 0f
                                dragState.velocityY = 0f
                                dragState.dragStarted = false
                            }
                        },
                        onDrag = { change, dragAmount ->
                            dragState.star?.let { star ->
                                val currentX = change.position.x
                                val currentY = change.position.y

                                // Check if drag has moved past threshold (8px)
                                if (!dragState.dragStarted) {
                                    val dx = currentX - dragState.startX
                                    val dy = currentY - dragState.startY
                                    val distance = sqrt(dx * dx + dy * dy)

                                    if (distance > DRAG_START_THRESHOLD) {
                                        dragState.dragStarted = true
                                        star.isDragging = true
                                        star.particle.isFixed = true
                                        // CRITICAL FIX: Play sound ONCE when drag starts
                                        audioEngine?.play(com.cosmicocean.audio.SoundType.DRAG_START, volume = 0.6f)
                                    } else {
                                        return@let // Don't move star yet
                                    }
                                }

                                // Only execute drag logic when drag is active (dragStarted = true)
                                if (dragState.dragStarted) {
                                    // CRITICAL FIX: Track time for frame-rate independent velocity calculation
                                    val currentTime = System.nanoTime()
                                    val deltaTime = (currentTime - dragState.lastTime) / 1_000_000_000f
                                    dragState.lastTime = currentTime

                                    if (deltaTime > 0) {
                                        // Calculate instantaneous velocity (pixels/second) - PWA parity fix
                                        val instantVx = (currentX - dragState.lastX) / deltaTime
                                        val instantVy = (currentY - dragState.lastY) / deltaTime

                                        // Apply exponential smoothing to velocity
                                        dragState.velocityX = dragState.velocityX * (1f - VELOCITY_SMOOTHING) + instantVx * VELOCITY_SMOOTHING
                                        dragState.velocityY = dragState.velocityY * (1f - VELOCITY_SMOOTHING) + instantVy * VELOCITY_SMOOTHING
                                    }

                                    // Update position with momentum preservation
                                    star.particle.oldX = star.particle.x - dragState.velocityX
                                    star.particle.oldY = star.particle.y - dragState.velocityY
                                    star.particle.x = currentX
                                    star.particle.y = currentY

                                    dragState.lastX = currentX
                                    dragState.lastY = currentY

                                    // Update active zone type
                                    if (star.particle.x > zoneManager.completedZoneX * 0.9f) activeZoneType = "sun"
                                    else if (star.particle.x < zoneManager.archivedZoneX * 1.1f) activeZoneType = "blackhole"
                                    else activeZoneType = null
                                }
                            }
                        },
                        onDragEnd = {
                            dragState.star?.let { star ->
                                if (dragState.dragStarted) {
                                    // Apply velocity boost on release (1.2x)
                                    star.particle.oldX = star.particle.x - dragState.velocityX * 1.2f
                                    star.particle.oldY = star.particle.y - dragState.velocityY * 1.2f

                                    // Play drag end sound
                                    audioEngine?.play(com.cosmicocean.audio.SoundType.DRAG_END, volume = 0.6f)

                                    if (star.particle.x > zoneManager.completedZoneX) {
                                        // CRITICAL FIX: Set completedAt timestamp for fade animation
                                        star.isCompleted = true
                                        star.completedAt = System.currentTimeMillis()

                                        // CRITICAL FIX: Reset velocity to prevent bounce-back
                                        star.particle.oldX = star.particle.x
                                        star.particle.oldY = star.particle.y

                                        pendingFinalization[star.title + star.createdAt] = System.currentTimeMillis()

                                        // Play completion sound
                                        audioEngine?.play(com.cosmicocean.audio.SoundType.TASK_COMPLETE, volume = 1.0f)

                                        // Trigger completion confetti
                                        com.cosmicocean.effects.PatinaPresets.completionBurst(
                                            patinaSystem,
                                            star.particle.x,
                                            star.particle.y
                                        )

                                        onStarFinalized(star, "complete")
                                    } else if (star.particle.x < zoneManager.archivedZoneX) {
                                        // CRITICAL FIX: Set archivedAt timestamp
                                        star.isArchived = true
                                        star.archivedAt = System.currentTimeMillis()

                                        // CRITICAL FIX: Reset velocity to prevent bounce-back
                                        star.particle.oldX = star.particle.x
                                        star.particle.oldY = star.particle.y

                                        pendingFinalization[star.title + star.createdAt] = System.currentTimeMillis()

                                        // Play archive sound
                                        audioEngine?.play(com.cosmicocean.audio.SoundType.TASK_ARCHIVE, volume = 0.8f)

                                        // Trigger archive confetti
                                        com.cosmicocean.effects.PatinaPresets.archiveBurst(
                                            patinaSystem,
                                            star.particle.x,
                                            star.particle.y
                                        )

                                        onStarFinalized(star, "archive")
                                    } else {
                                        // EPIC 9 FIX: Save position after normal drag (not completion/archive)
                                        onStarDragEnd(star)
                                    }
                                }
                            }
                            // Unfix all stars after drag ends and reset drag state
                            stars.forEach {
                                it.isDragging = false
                                it.particle.isFixed = false
                            }
                            dragState.star = null
                            dragState.dragStarted = false
                            activeZoneType = null
                        }
                    )
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val touchPoint = down.position

                        // Check if touch is on a star
                        val star = stars.find {
                            gestureController.isTouchNearStar(touchPoint, it)
                        }

                        if (star != null && !star.isDragging) {
                            gestureController.startSwirl(star, touchPoint)
                            gestureState = gestureController.toGestureState()

                            do {
                                val event = awaitPointerEvent()
                                event.changes.forEach { change ->
                                    if (change.positionChange() != Offset.Zero) {
                                        gestureController.updateSwirl(change.position)
                                        gestureState = gestureController.toGestureState()
                                    }
                                }
                            } while (event.changes.any { it.pressed })

                            // PWA parity: Apply snooze on gesture end if threshold reached
                            if (gestureController.canSnooze()) {
                                val snoozeDuration = gestureController.getSnoozeDuration()
                                onStarSnooze(star, snoozeDuration)
                                audioEngine?.play(com.cosmicocean.audio.SoundType.SWIRL_COMPLETE, volume = 1.0f)
                            }

                            gestureController.endSwirl()
                            gestureState = gestureController.toGestureState()
                        }
                    }
                }
        ) {
        engine.setBounds(size.width, size.height)
        zoneManager.updateScreenSize(size.width, size.height)

        // PWA-ACCURATE: Global screen shake for overdue tasks (cry system)
        val globalShake = Star.getGlobalShakeOffset(stars)

        // Apply global shake to entire canvas
        withTransform({
            translate(globalShake.x, globalShake.y)
        }) {
            atmosphere.draw(this, tick)
            constellationSystem.draw(this, stars)
            orbitalSystem.draw(this, stars)

            // PWA-ACCURATE: Zone circles with glow effect
            // SCALE radius based on screen width (larger screens = larger zones)
            val baseGlowRadius = 120f
            val screenScale = (size.width / 1080f).coerceIn(1f, 2.5f) // Scale for different screen sizes
            val glowRadius = baseGlowRadius * screenScale

            // Completion Zone (Right edge = Green glow circle)
            val completionX = size.width - 60f * screenScale
            val completionY = size.height / 2f
            val sunAlpha = if (activeZoneType == "sun") 0.4f else 0.15f

            // Draw multiple concentric circles for glow effect
            for (i in 0 until 8) {
                val radius = glowRadius * (1f - i / 8f)
                val alpha = sunAlpha * 0.4f * (1f - i / 8f)
                drawCircle(
                    color = Color(0xFF00FF88).copy(alpha = alpha),
                    radius = radius,
                    center = Offset(completionX, completionY)
                )
            }

            // Archive Zone (Left edge = Gray glow circle)
            val archiveX = 60f * screenScale
            val archiveY = size.height / 2f
            val bhAlpha = if (activeZoneType == "blackhole") 0.4f else 0.15f

            // Draw multiple concentric circles for glow effect
            for (i in 0 until 8) {
                val radius = glowRadius * (1f - i / 8f)
                val alpha = bhAlpha * 0.3f * (1f - i / 8f)
                drawCircle(
                    color = Color(0xFF888888).copy(alpha = alpha),
                    radius = radius,
                    center = Offset(archiveX, archiveY)
                )
            }

            // Zone labels (cosmic chips, always visible for guidance)
            val labelAlpha = if (activeZoneType != null) 0.95f else 0.45f
            val labelFontSize = (12f * screenScale).coerceIn(11f, 17f)
            val labelStyle = TextStyle(
                color = Color.White.copy(alpha = labelAlpha),
                fontSize = labelFontSize.sp,
                letterSpacing = (0.4f * screenScale).coerceIn(0.2f, 0.8f).sp,
                fontWeight = FontWeight.SemiBold
            )
            val labelYBase = (size.height / 2f) - (glowRadius * 0.55f)
            val chipPaddingH = (10f * screenScale).coerceIn(8f, 14f)
            val chipPaddingV = (6f * screenScale).coerceIn(5f, 10f)
            val iconRadius = (5f * screenScale).coerceIn(4f, 7f)
            val iconGap = (6f * screenScale).coerceIn(4f, 10f)
            val chipCorner = (14f * screenScale).coerceIn(10f, 18f)

            fun drawZoneChip(
                label: String,
                iconColor: Color,
                anchorX: Float,
                alignRight: Boolean
            ) {
                val layout = textMeasurer.measure(text = label, style = labelStyle)
                val chipWidth = layout.size.width + chipPaddingH * 2f + iconRadius * 2f + iconGap
                val chipHeight = layout.size.height + chipPaddingV * 2f
                val chipX = if (alignRight) anchorX - chipWidth else anchorX
                val chipY = labelYBase.coerceIn(24f, size.height - 24f - chipHeight)

                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.35f * labelAlpha),
                    topLeft = Offset(chipX, chipY),
                    size = androidx.compose.ui.geometry.Size(chipWidth, chipHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(chipCorner, chipCorner)
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.12f * labelAlpha),
                    topLeft = Offset(chipX, chipY),
                    size = androidx.compose.ui.geometry.Size(chipWidth, chipHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(chipCorner, chipCorner),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
                )

                val iconCenterX = chipX + chipPaddingH + iconRadius
                val iconCenterY = chipY + chipHeight / 2f
                drawCircle(
                    color = iconColor.copy(alpha = labelAlpha),
                    radius = iconRadius,
                    center = Offset(iconCenterX, iconCenterY)
                )
                drawCircle(
                    color = Color.Black.copy(alpha = 0.45f * labelAlpha),
                    radius = iconRadius * 0.55f,
                    center = Offset(iconCenterX, iconCenterY)
                )

                val textX = iconCenterX + iconRadius + iconGap
                val textY = chipY + (chipHeight - layout.size.height) / 2f
                drawText(
                    textMeasurer = textMeasurer,
                    text = label,
                    style = labelStyle,
                    topLeft = Offset(textX, textY)
                )
            }

            drawZoneChip(
                label = "Archive",
                iconColor = Color(0xFF6B6F7A),
                anchorX = (archiveX + glowRadius * 0.35f).coerceAtLeast(12f),
                alignRight = false
            )
            drawZoneChip(
                label = "Complete",
                iconColor = Color(0xFF00FF88),
                anchorX = (completionX - glowRadius * 0.35f).coerceAtLeast(12f),
                alignRight = true
            )

            stars.forEach { star ->
                val id = star.title + star.createdAt
                // Draw cry effect first (behind star)
                if (star.isOverdue()) {
                    drawCryEffect(star)
                }
                if (pendingFinalization.containsKey(id)) {
                    drawUndoRing(star, tick)
                }
                // Draw star with per-star shake offset applied
                drawStar(star, tick)
            }

            // Draw labels on top of everything
            // EPIC 9.1: Calculate collision-free label positions ONCE
            val activeStars = stars.filter { !it.isCompleted && !it.isArchived }
            val labelSizes = activeStars.associate { star ->
                // Truncate long task names (max 25 chars)
                val displayText = if (star.title.length > 25) {
                    "${star.title.take(22)}..."
                } else {
                    star.title
                }

                val measuredText = textMeasurer.measure(
                    text = displayText,
                    style = TextStyle(fontSize = 14.sp),
                    constraints = androidx.compose.ui.unit.Constraints(
                        maxWidth = (size.width * 0.3f).toInt()
                    )
                )
                star.id to androidx.compose.ui.geometry.Size(
                    measuredText.size.width.toFloat() + 12f,  // Add padding
                    measuredText.size.height.toFloat() + 6f
                )
            }

            val labelPositions = com.cosmicocean.utils.LabelCollisionDetector.calculateSafePositions(
                stars = activeStars,
                labelSizes = labelSizes,
                screenWidth = size.width,
                screenHeight = size.height
            )

            // Draw labels with collision-free positions
            activeStars.forEach { star ->
                val labelPos = labelPositions[star.id]
                if (labelPos != null) {
                    drawStarLabel(star, textMeasurer, labelPos, size.width, size.height)
                }
            }

            ripples.forEach { ripple ->
                drawCircle(
                    color = Color(0xFF00E5FF).copy(alpha = ripple.life * 0.3f),
                    radius = ripple.radius,
                    center = Offset(ripple.x, ripple.y),
                    style = Stroke(width = 2f)
                )
            }

            // Draw patina particle system
            patinaSystem.draw(this)
        } // End withTransform (global shake)
    }

        // Swirl gesture visual feedback overlay
        SwirlFeedback(
            gestureState = gestureState,
            modifier = Modifier.fillMaxSize()
        )
    }
}

fun DrawScope.drawUndoRing(star: Star, tick: Int) {
    val p = star.particle
    val pulse = (sin(tick * 0.2f) * 0.5f + 0.5f)
    drawCircle(
        color = Color.White.copy(alpha = 0.5f * pulse),
        radius = p.radius * 3f,
        center = Offset(p.x, p.y),
        style = Stroke(width = 4f)
    )
}

/**
 * Cry System - Visual distress signal for overdue tasks
 * PWA-accurate pulsing red halo with intensity based on how overdue the task is
 */
fun DrawScope.drawCryEffect(star: Star) {
    val p = star.particle
    val intensity = star.getCryIntensity()

    // Pulse calculation (0 to 1 over 1 second)
    val pulsePhase = (System.currentTimeMillis() % 1000) / 1000f
    val pulse = sin(pulsePhase * kotlin.math.PI.toFloat() * 2f)

    // Pulsing red halo (outer warning ring)
    drawCircle(
        color = Color.Red.copy(alpha = intensity * 0.3f * abs(pulse)),
        radius = p.radius * (3f + pulse * 0.5f),
        center = Offset(p.x, p.y),
        style = Stroke(width = 2f)
    )

    // Secondary inner warning ring (more subtle)
    drawCircle(
        color = Color.Red.copy(alpha = intensity * 0.2f * abs(pulse)),
        radius = p.radius * (2f + pulse * 0.3f),
        center = Offset(p.x, p.y),
        style = Stroke(width = 1.5f)
    )
}

fun DrawScope.drawStar(star: Star, tick: Int) {
    val p = star.particle
    val pulse = star.getPulse()
    val opacity = star.filterOpacity
    val shake = star.getCryOffset()
    val pos = Offset(p.x + shake.x, p.y + shake.y)

    // PWA-ACCURATE: ARCHIVED stars render ONLY in grayscale (skip normal rendering)
    if (star.isArchived) {
        val GRAY = Color(0xFF888888)

        // Grayscale outer glow (calm, subtle)
        val archivePulse = sin(tick * 0.02f * 0.8f) * 0.2f + 0.8f // Slower, calmer pulse
        val outerGlowRadius = p.radius * 2.0f
        drawCircle(
            color = GRAY.copy(alpha = 0.15f * archivePulse * opacity),
            radius = outerGlowRadius,
            center = pos
        )

        // Grayscale main glow
        drawCircle(
            color = GRAY.copy(alpha = 0.2f * opacity),
            radius = p.radius * 1.5f * pulse,
            center = pos
        )

        // Grayscale core (desaturated, slightly transparent)
        drawCircle(
            color = GRAY.copy(alpha = 0.6f * opacity),
            radius = p.radius * pulse,
            center = pos
        )

        // Light gray center
        drawCircle(
            color = Color(0xFFCCCCCC).copy(alpha = 0.5f * opacity),
            radius = p.radius * 0.4f,
            center = pos
        )

        return // Skip all normal rendering
    }

    // PWA-ACCURATE: COMPLETED stars render ONLY in green (skip normal rendering)
    if (star.isCompleted) {
        val GREEN = Color(0xFF00FF88)

        // Green outer glow (pulsing)
        val completionPulse = sin(tick * 0.02f * 1.5f) * 0.3f + 0.7f
        drawCircle(
            color = GREEN.copy(alpha = 0.2f * completionPulse * opacity),
            radius = p.radius * 2.5f,
            center = pos
        )

        // Green main glow
        drawCircle(
            color = GREEN.copy(alpha = 0.3f * opacity),
            radius = p.radius * 1.8f * pulse,
            center = pos
        )

        // Green core
        drawCircle(
            color = GREEN.copy(alpha = 0.9f * opacity),
            radius = p.radius * pulse,
            center = pos
        )

        // White center
        drawCircle(
            color = Color.White.copy(alpha = 0.7f * opacity),
            radius = p.radius * 0.4f,
            center = pos
        )

        // Green checkmark (√)
        val checkSize = p.radius * 0.8f
        val checkThickness = max(2f, p.radius * 0.15f)
        val path = Path().apply {
            moveTo(pos.x - checkSize * 0.4f, pos.y)
            lineTo(pos.x - checkSize * 0.1f, pos.y + checkSize * 0.4f)
            lineTo(pos.x + checkSize * 0.5f, pos.y - checkSize * 0.3f)
        }
        drawPath(
            path = path,
            color = GREEN.copy(alpha = 0.9f * opacity),
            style = Stroke(width = checkThickness)
        )

        return // Skip all normal rendering
    }

    // NORMAL STAR RENDERING (only for non-completed/non-archived stars)
    val color = Color(star.getColor())

    // Layer 1: Outer glow (20% opacity, 2.5x size) - FIXED to match PWA
    drawCircle(
        color = color.copy(alpha = 0.2f * opacity),
        radius = p.radius * 2.5f,
        center = pos
    )

    // Layer 2: Middle glow (50% opacity, 1.8x size) - PWA-exact
    drawCircle(
        color = color.copy(alpha = 0.5f * opacity),
        radius = p.radius * 1.8f,
        center = pos
    )

    // Layer 3: Core (90% opacity, 1x size) - FIXED to match PWA
    drawCircle(
        color = color.copy(alpha = 0.9f * opacity),
        radius = p.radius,
        center = pos
    )

    // Layer 4: Bright white center (80% opacity, 0.4x size) - ADDED to match PWA
    drawCircle(
        color = Color.White.copy(alpha = 0.8f * opacity),
        radius = p.radius * 0.4f,
        center = pos
    )

    // Recurring task indicator: Cyan echo ring - ADDED to match PWA
    if (star.isRecurring) {
        val echoPhase = (tick * 0.02f) * 0.5f // Slower pulse for echo
        val echoPulse = sin(echoPhase) * 0.5f + 0.5f
        val echoRadius = p.radius * 2.2f + echoPulse * 5f

        drawCircle(
            color = Color(0xFF00FFFF).copy(alpha = (0.3f + echoPulse * 0.2f) * opacity),
            radius = echoRadius,
            center = pos,
            style = Stroke(width = 2f)
        )
    }
}

/**
 * Draw star label with smart collision-free positioning
 * EPIC 9.1: Collision-aware label rendering with reduced opacity
 */
fun DrawScope.drawStarLabel(
    star: Star,
    textMeasurer: TextMeasurer,
    labelPos: Offset,  // EPIC 9.1: Pre-calculated collision-free position
    screenWidth: Float,
    screenHeight: Float
) {
    // Style for the label text
    val textStyle = TextStyle(
        color = Color.White,
        fontSize = 14.sp
    )

    // EPIC 9.1: Truncate long task names (max 25 chars)
    val displayText = if (star.title.length > 25) {
        "${star.title.take(22)}..."
    } else {
        star.title
    }

    // EPIC 9.1: Limit label width to 30% of screen (reduced from 40%)
    val maxLabelWidth = (screenWidth * 0.3f).toInt()

    // Measure text to get dimensions with width constraint
    val measuredText = textMeasurer.measure(
        text = displayText,
        style = textStyle,
        constraints = androidx.compose.ui.unit.Constraints(maxWidth = maxLabelWidth),
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
    )

    val labelWidth = measuredText.size.width.toFloat()
    val labelHeight = measuredText.size.height.toFloat()

    // EPIC 9.1: Semi-transparent background (30% opacity - reduced from 60%)
    // Stars are now visible through labels
    drawRect(
        color = Color.Black.copy(alpha = 0.3f),  // ✅ 50% reduction in opacity
        topLeft = labelPos,
        size = androidx.compose.ui.geometry.Size(
            labelWidth + 12f,
            labelHeight + 6f
        )
    )

    // Draw the text
    drawText(
        textLayoutResult = measuredText,
        topLeft = Offset(labelPos.x + 6f, labelPos.y + 3f)
    )

    // Optional: Draw connection line from star to label
    // drawLine(
    //     color = Color.White.copy(alpha = 0.3f),
    //     start = Offset(star.particle.x, star.particle.y),
    //     end = Offset(labelPos.x, labelPos.y + labelHeight / 2),
    //     strokeWidth = 1f
    // )
}
