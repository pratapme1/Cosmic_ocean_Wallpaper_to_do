package com.cosmicocean.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import com.cosmicocean.data.StarEntity
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/**
 * LocalWallpaperGenerator - On-Device Wallpaper Generation
 *
 * Generates wallpaper locally without network dependency.
 * Ported from backend/services/wallpaper-generator-enhanced.js
 *
 * Features:
 * - Gradient backgrounds based on urgency
 * - Particle systems (stars/bubbles)
 * - Task display with "RIGHT NOW" header
 * - Achievement display
 * - Dark overlay for custom backgrounds
 * - Auto font contrast adjustment
 * - WCAG-compliant text rendering
 */
object LocalWallpaperGenerator {
    private const val TAG = "LocalWallpaperGen"

    // Particle system constants (aligned with backend particle-system.js)
    private const val PARTICLE_SEED_PRIME = 7919

    private data class ParticleParams(
        val count: Int,
        val sizeMin: Float,
        val sizeMax: Float,
        val opacityMin: Float,
        val opacityMax: Float,
        val twinkleMinMs: Int = 0,
        val twinkleMaxMs: Int = 0,
        val riseSpeed: Float = 0f,
        val wobble: Float = 0f
    )

    // Layout constants (matching backend layout-system.js)
    private const val SAFE_ZONE_TOP_RATIO = 0.12f  // Clock/status bar area
    private const val TASK_ZONE_START_RATIO = 0.35f
    private const val TASK_ZONE_END_RATIO = 0.75f
    private const val MARGIN_HORIZONTAL_RATIO = 0.06f

    // Backend-aligned layout config (ported from backend/services/layout-system.js)
    private data class SafeInsets(
        val top: Float,
        val clockZone: Float,
        val bottom: Float,
        val left: Float,
        val right: Float,
        val density: Float
    )

    private data class LayoutZone(
        val y: Float,
        val height: Float,
        val centerY: Float
    )

    private data class LayoutZones(
        val system: LayoutZone,
        val clock: LayoutZone,
        val scene: LayoutZone,
        val transition: LayoutZone,
        val task: LayoutZone,
        val interaction: LayoutZone,
        val navigation: LayoutZone
    )

    private data class Margins(
        val horizontal: Float,
        val vertical: Float
    )

    private data class TypographyScale(
        val displayLarge: Float,
        val displayMedium: Float,
        val headlineLarge: Float,
        val headlineMedium: Float,
        val titleLarge: Float,
        val titleMedium: Float,
        val bodyLarge: Float,
        val bodyMedium: Float,
        val labelLarge: Float,
        val labelMedium: Float,
        val labelSmall: Float
    )

    private data class LayoutConfig(
        val width: Int,
        val height: Int,
        val safeInsets: SafeInsets,
        val layoutZones: LayoutZones,
        val margins: Margins,
        val typography: TypographyScale
    )

    private fun getLayoutConfig(width: Int, height: Int): LayoutConfig {
        val safe = calculateSafeInsets(width, height)
        val zones = calculateLayoutZones(width, height, safe)
        val category = getBreakpointCategory(width / safe.density)
        val margins = getResponsiveMargins(category, safe.density)
        val typography = getTypographyScale(safe.density, category)
        return LayoutConfig(width, height, safe, zones, margins, typography)
    }

    private fun calculateSafeInsets(width: Int, height: Int): SafeInsets {
        val density = when {
            width >= 1440 -> 2.5f
            width >= 1080 -> 2.0f
            width >= 720 -> 1.5f
            width >= 540 -> 1.2f
            else -> 1.0f
        }

        val statusBarHeight = (32 * density)
        val cutoutHeight = (20 * density)
        val navBarHeight = (40 * density)
        val clockZoneHeight = (120 * density)
        val edgeInset = (16 * density)

        return SafeInsets(
            top = maxOf(statusBarHeight, cutoutHeight),
            clockZone = clockZoneHeight,
            bottom = navBarHeight,
            left = edgeInset,
            right = edgeInset,
            density = density
        )
    }

    private fun calculateLayoutZones(width: Int, height: Int, safe: SafeInsets): LayoutZones {
        val systemZoneHeight = safe.top
        val navZoneHeight = safe.bottom
        val availableHeight = height - systemZoneHeight - navZoneHeight

        val clockZoneHeight = (availableHeight * 0.12f)
        val sceneZoneHeight = (availableHeight * 0.40f)
        val transitionZoneHeight = (availableHeight * 0.05f)
        val taskZoneHeight = (availableHeight * 0.28f)
        val interactionZoneHeight = (availableHeight * 0.07f)

        var currentY = systemZoneHeight

        val system = LayoutZone(
            y = 0f,
            height = systemZoneHeight,
            centerY = systemZoneHeight / 2f
        )
        val clock = LayoutZone(
            y = currentY,
            height = clockZoneHeight,
            centerY = currentY + clockZoneHeight / 2f
        )
        val scene = LayoutZone(
            y = currentY + clockZoneHeight,
            height = sceneZoneHeight,
            centerY = currentY + clockZoneHeight + sceneZoneHeight / 2f
        )
        val transition = LayoutZone(
            y = currentY + clockZoneHeight + sceneZoneHeight,
            height = transitionZoneHeight,
            centerY = currentY + clockZoneHeight + sceneZoneHeight + transitionZoneHeight / 2f
        )
        val task = LayoutZone(
            y = currentY + clockZoneHeight + sceneZoneHeight + transitionZoneHeight,
            height = taskZoneHeight,
            centerY = currentY + clockZoneHeight + sceneZoneHeight + transitionZoneHeight + taskZoneHeight / 2f
        )
        val interaction = LayoutZone(
            y = currentY + clockZoneHeight + sceneZoneHeight + transitionZoneHeight + taskZoneHeight,
            height = interactionZoneHeight,
            centerY = currentY + clockZoneHeight + sceneZoneHeight + transitionZoneHeight + taskZoneHeight + interactionZoneHeight / 2f
        )
        val navigation = LayoutZone(
            y = height - navZoneHeight,
            height = navZoneHeight,
            centerY = height - navZoneHeight / 2f
        )

        return LayoutZones(system, clock, scene, transition, task, interaction, navigation)
    }

    private fun getBreakpointCategory(widthDp: Float): String {
        return when {
            widthDp < 360f -> "compact"
            widthDp < 400f -> "standard"
            widthDp < 600f -> "large"
            else -> "xlarge"
        }
    }

    private fun getResponsiveMargins(category: String, density: Float): Margins {
        val baseDp = when (category) {
            "compact" -> 16f
            "standard" -> 24f
            "large" -> 32f
            else -> 48f
        }
        return Margins(
            horizontal = baseDp * density,
            vertical = baseDp * density * 0.75f
        )
    }

    private fun getTypographyScale(density: Float, category: String): TypographyScale {
        val scaleFactor = when (category) {
            "compact" -> 0.9f
            "standard" -> 1.0f
            "large" -> 1.05f
            else -> 1.1f
        }

        fun size(baseSp: Float): Float {
            val scaled = baseSp * scaleFactor
            val min = baseSp * 0.85f
            val max = baseSp * 1.2f
            val clamped = maxOf(min, minOf(max, scaled))
            return clamped * density
        }

        return TypographyScale(
            displayLarge = size(32f),
            displayMedium = size(28f),
            headlineLarge = size(24f),
            headlineMedium = size(20f),
            titleLarge = size(18f),
            titleMedium = size(16f),
            bodyLarge = size(16f),
            bodyMedium = size(14f),
            labelLarge = size(14f),
            labelMedium = size(12f),
            labelSmall = size(10f)
        )
    }

    /**
     * Generate wallpaper bitmap with generated theme
     * Shows top 3 tasks with +more indicator
     */
    fun generate(
        tasks: List<StarEntity>,
        totalTaskCount: Int,
        theme: WallpaperTheme,
        width: Int,
        height: Int,
        achievementCount: Int = 0,
        streakDays: Int = 0
    ): Bitmap {
        Log.d(TAG, "Generating wallpaper: ${width}x${height}, theme=$theme, tasks=${tasks.size}, total=$totalTaskCount")

        val sortedTasks = sortTasksByDueDate(tasks)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // CRITICAL FIX: Fill with solid base color first to prevent black screen
        val urgency = if (sortedTasks.isNotEmpty()) calculateUrgency(sortedTasks[0]) else UrgencyLevel.CLEAR
        val colors = theme.getColors(urgency)
        canvas.drawColor(colors.gradientEnd)

        // Layer 1: Gradient background
        drawGradientBackground(canvas, colors, width, height)

        // Layer 2: Particle system
        drawParticles(canvas, colors, width, height, theme, urgency)

        // Layer 2.5: Transition gradient (scene → task zone)
        drawTransitionGradient(canvas, colors, width, height)

        // Layer 3: Achievement panel (if any)
        val achievementReservedSpace = if (achievementCount > 0 || streakDays > 0) {
            drawAchievementPanel(canvas, achievementCount, streakDays, colors, width, height)
        } else {
            0f
        }

        // Layer 4: Task display
        if (sortedTasks.isNotEmpty()) {
            drawTaskListEnhanced(
                canvas,
                sortedTasks,
                totalTaskCount,
                colors,
                width,
                height,
                achievementReservedSpace
            )
        } else {
            drawClearState(canvas, colors, width, height)
        }

        return bitmap
    }

    /**
     * Generate wallpaper bitmap with custom background image
     * Shows top 3 tasks with +more indicator
     * Includes dark overlay for text readability
     */
    fun generateWithCustomBackground(
        tasks: List<StarEntity>,
        totalTaskCount: Int,
        customBackground: Bitmap,
        width: Int,
        height: Int,
        achievementCount: Int = 0,
        streakDays: Int = 0
    ): Bitmap {
        Log.d(TAG, "Generating wallpaper with custom background: ${width}x${height}, tasks=${tasks.size}")

        val sortedTasks = sortTasksByDueDate(tasks)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // CRITICAL FIX: Fill with solid black first to prevent transparency issues
        canvas.drawColor(Color.BLACK)

        // Layer 1: Custom background image (scaled to cover)
        try {
            val scaledBackground = scaleToCover(customBackground, width, height)
            Log.d(TAG, "Scaled background: ${scaledBackground.width}x${scaledBackground.height}")
            canvas.drawBitmap(scaledBackground, 0f, 0f, null)

            if (scaledBackground != customBackground && !scaledBackground.isRecycled) {
                scaledBackground.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error drawing custom background: ${e.message}", e)
            canvas.drawColor(Color.parseColor("#1A1A2E"))
        }

        // Layer 2: Dark overlay for text readability (40% opacity like backend)
        val overlayPaint = Paint().apply {
            color = Color.BLACK
            alpha = 102  // 40% of 255
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)

        // Calculate urgency for styling
        val urgency = if (sortedTasks.isNotEmpty()) calculateUrgency(sortedTasks[0]) else UrgencyLevel.CLEAR

        // Use high contrast colors for custom backgrounds
        val colors = getCustomBackgroundColors(urgency)

        // Layer 3: Achievement panel (if any)
        val achievementReservedSpace = if (achievementCount > 0 || streakDays > 0) {
            drawAchievementPanel(canvas, achievementCount, streakDays, colors, width, height)
        } else {
            0f
        }

        // Layer 4: Task display
        if (sortedTasks.isNotEmpty()) {
            drawTaskListEnhanced(
                canvas,
                sortedTasks,
                totalTaskCount,
                colors,
                width,
                height,
                achievementReservedSpace
            )
        } else {
            drawClearState(canvas, colors, width, height)
        }

        return bitmap
    }

    /**
     * Scale bitmap to cover (crop to fit) like CSS background-size: cover
     */
    private fun scaleToCover(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val sourceRatio = source.width.toFloat() / source.height
        val targetRatio = targetWidth.toFloat() / targetHeight

        val scaledWidth: Int
        val scaledHeight: Int

        if (sourceRatio > targetRatio) {
            // Source is wider - scale by height, crop width
            scaledHeight = targetHeight
            scaledWidth = (source.width * (targetHeight.toFloat() / source.height)).toInt()
        } else {
            // Source is taller - scale by width, crop height
            scaledWidth = targetWidth
            scaledHeight = (source.height * (targetWidth.toFloat() / source.width)).toInt()
        }

        val scaled = Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true)

        // Crop to center
        val x = (scaledWidth - targetWidth) / 2
        val y = (scaledHeight - targetHeight) / 2

        return Bitmap.createBitmap(scaled, x.coerceAtLeast(0), y.coerceAtLeast(0), targetWidth, targetHeight)
    }

    /**
     * High contrast colors for custom backgrounds
     */
    private fun getCustomBackgroundColors(urgency: UrgencyLevel): ThemeColors {
        return ThemeColors(
            gradientStart = Color.TRANSPARENT,
            gradientEnd = Color.TRANSPARENT,
            taskCircle = when (urgency) {
                UrgencyLevel.CRITICAL -> Color.parseColor("#FF3B30") // red
                UrgencyLevel.URGENT -> Color.parseColor("#FF3B30")   // red (due < 24h)
                UrgencyLevel.ATTENTION -> Color.parseColor("#FF9A3A") // orange (tomorrow)
                else -> Color.parseColor("#3AA0FF") // blue (future)
            },
            taskCircleGlow = Color.WHITE,
            titleColor = Color.WHITE,
            subtitleColor = Color.parseColor("#E0E0E0"),
            particleColor = Color.WHITE
        )
    }

    private fun getDueDatePriorityColor(dueDate: Long?): Int {
        if (dueDate == null) return Color.parseColor("#3AA0FF") // future/no date
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val dueLocalDate = Instant.ofEpochMilli(dueDate).atZone(zone).toLocalDate()
        return when {
            dueLocalDate.isBefore(today) -> Color.parseColor("#FF3B30") // overdue
            dueLocalDate.isEqual(today) -> Color.parseColor("#FF3B30")  // due today
            dueLocalDate.isEqual(today.plusDays(1)) -> Color.parseColor("#FF9A3A") // due tomorrow
            else -> Color.parseColor("#3AA0FF") // future
        }
    }

    /**
     * Calculate urgency level from task
     */
    private fun calculateUrgency(task: StarEntity?): UrgencyLevel {
        if (task == null) return UrgencyLevel.CLEAR

        val dueDate = task.dueDate ?: return UrgencyLevel.CALM

        val now = System.currentTimeMillis()
        val hoursUntilDue = (dueDate - now) / (1000 * 60 * 60)

        return when {
            hoursUntilDue < 0 -> UrgencyLevel.CRITICAL    // Overdue
            hoursUntilDue < 4 -> UrgencyLevel.CRITICAL    // Due in 4 hours
            hoursUntilDue < 24 -> UrgencyLevel.URGENT     // Due today
            hoursUntilDue < 48 -> UrgencyLevel.ATTENTION  // Due tomorrow
            else -> UrgencyLevel.CALM
        }
    }

    /**
     * Draw gradient background
     */
    private fun drawGradientBackground(
        canvas: Canvas,
        colors: ThemeColors,
        width: Int,
        height: Int
    ) {
        val centerX = width / 2f
        val centerY = height * 0.4f
        val radius = max(width, height).toFloat()

        val gradient = RadialGradient(
            centerX,
            centerY,
            radius,
            colors.gradientStart,
            colors.gradientEnd,
            Shader.TileMode.CLAMP
        )

        val paint = Paint().apply {
            shader = gradient
            isAntiAlias = true
        }

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    /**
     * Draw transition gradient layer to blend scene into task zone (backend parity).
     */
    private fun drawTransitionGradient(
        canvas: Canvas,
        colors: ThemeColors,
        width: Int,
        height: Int
    ) {
        val layout = getLayoutConfig(width, height)
        val zone = layout.layoutZones.transition

        val transparent = Color.argb(0, Color.red(colors.gradientEnd), Color.green(colors.gradientEnd), Color.blue(colors.gradientEnd))
        val mid = Color.argb(77, Color.red(colors.gradientEnd), Color.green(colors.gradientEnd), Color.blue(colors.gradientEnd)) // 30%
        val deep = Color.argb(179, Color.red(colors.gradientEnd), Color.green(colors.gradientEnd), Color.blue(colors.gradientEnd)) // 70%

        val gradient = LinearGradient(
            0f,
            zone.y,
            0f,
            zone.y + zone.height,
            intArrayOf(transparent, mid, deep),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )

        val paint = Paint().apply {
            shader = gradient
            isAntiAlias = true
        }

        canvas.drawRect(0f, zone.y, width.toFloat(), zone.y + zone.height, paint)
    }

    /**
     * Draw particle system (stars, bubbles, etc.)
     */
    private fun drawParticles(
        canvas: Canvas,
        colors: ThemeColors,
        width: Int,
        height: Int,
        theme: WallpaperTheme,
        urgency: UrgencyLevel
    ) {
        val layout = getLayoutConfig(width, height)
        val density = layout.safeInsets.density
        val timestamp = System.currentTimeMillis()

        val params = when (theme) {
            WallpaperTheme.DEEP_OCEAN, WallpaperTheme.OCEAN -> getBubbleParams(urgency)
            else -> getStarParams(urgency)
        }

        val zones = listOf(
            Pair(layout.layoutZones.clock, 0.6f),
            Pair(layout.layoutZones.scene, 1.0f),
            Pair(layout.layoutZones.transition, 0.8f),
            Pair(layout.layoutZones.task, 0.4f),
            Pair(layout.layoutZones.interaction, 0.3f)
        )

        val paint = Paint().apply { isAntiAlias = true }

        for (i in 0 until params.count) {
            val seed = i * PARTICLE_SEED_PRIME
            val zoneIndex = ((i.toFloat() / params.count) * zones.size).toInt().coerceIn(0, zones.size - 1)
            val (zone, weight) = zones[zoneIndex]

            if (seededRandom(seed) > weight) continue

            val baseX = randomRange(
                layout.margins.horizontal,
                width - layout.margins.horizontal,
                seededRandom(seed + 1)
            )
            val baseY = randomRange(zone.y, zone.y + zone.height, seededRandom(seed + 2))
            val size = randomRange(params.sizeMin, params.sizeMax, seededRandom(seed + 3)) * density
            val baseOpacity = randomRange(params.opacityMin, params.opacityMax, seededRandom(seed + 4))

            val twinkleScale = if (params.twinkleMinMs > 0 && params.twinkleMaxMs > 0) {
                val twinkleDuration = randomRange(
                    params.twinkleMinMs.toFloat(),
                    params.twinkleMaxMs.toFloat(),
                    seededRandom(seed + 5)
                )
                val phase = ((timestamp + i * 100L) % twinkleDuration.toLong()) / twinkleDuration
                (sin(phase * Math.PI * 2) * 0.5 + 0.5).toFloat()
            } else {
                1f
            }

            val opacity = (baseOpacity * twinkleScale * 255).toInt().coerceIn(20, 255)

            val wobbleOffset = if (params.wobble > 0f) {
                sin((timestamp / 1000f) + i) * params.wobble * density
            } else {
                0f
            }
            val riseOffset = if (params.riseSpeed > 0f) {
                ((timestamp / 1000f) * params.riseSpeed + i * 50f) % height
            } else {
                0f
            }

            val x = baseX + wobbleOffset
            val y = (baseY + riseOffset) % height

            paint.color = colors.particleColor
            paint.alpha = opacity

            when (theme) {
                WallpaperTheme.COSMIC -> {
                    drawStar(canvas, x, y, size, paint)
                }
                WallpaperTheme.DEEP_OCEAN, WallpaperTheme.OCEAN -> {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = maxOf(0.6f, density * 0.4f)
                    canvas.drawCircle(x, y, size, paint)
                    paint.style = Paint.Style.FILL
                }
                WallpaperTheme.FOREST, WallpaperTheme.FANTASY -> {
                    canvas.drawCircle(x, y, size * 0.8f, paint)
                    if (seededRandom(seed + 6) > 0.7f) {
                        paint.alpha = (opacity * 0.35f).toInt()
                        canvas.drawCircle(x, y, size * 1.8f, paint)
                    }
                }
                WallpaperTheme.MINIMAL -> {
                    canvas.drawCircle(x, y, size * 0.5f, paint)
                }
                else -> {
                    canvas.drawCircle(x, y, size, paint)
                }
            }
        }
    }

    private fun getStarParams(urgency: UrgencyLevel): ParticleParams {
        return when (urgency) {
            UrgencyLevel.CALM -> ParticleParams(40, 2f, 6f, 0.5f, 0.9f, 3000, 5000)
            UrgencyLevel.ATTENTION -> ParticleParams(60, 3f, 8f, 0.6f, 1.0f, 2000, 4000)
            UrgencyLevel.URGENT -> ParticleParams(80, 4f, 10f, 0.7f, 1.0f, 1000, 3000)
            UrgencyLevel.CRITICAL -> ParticleParams(120, 5f, 12f, 0.8f, 1.0f, 500, 2000)
            UrgencyLevel.CLEAR -> ParticleParams(50, 3f, 7f, 0.6f, 1.0f, 2000, 4000)
        }
    }

    private fun getBubbleParams(urgency: UrgencyLevel): ParticleParams {
        return when (urgency) {
            UrgencyLevel.CALM -> ParticleParams(30, 6f, 16f, 0.4f, 0.7f, riseSpeed = 20f, wobble = 5f)
            UrgencyLevel.ATTENTION -> ParticleParams(45, 8f, 20f, 0.5f, 0.8f, riseSpeed = 35f, wobble = 8f)
            UrgencyLevel.URGENT -> ParticleParams(60, 10f, 24f, 0.6f, 0.9f, riseSpeed = 50f, wobble = 12f)
            UrgencyLevel.CRITICAL -> ParticleParams(90, 12f, 28f, 0.7f, 1.0f, riseSpeed = 70f, wobble = 16f)
            UrgencyLevel.CLEAR -> ParticleParams(40, 8f, 18f, 0.5f, 0.8f, riseSpeed = 25f, wobble = 6f)
        }
    }

    private fun seededRandom(seed: Int): Float {
        val x = sin(seed.toDouble()) * 10000
        return (x - floor(x)).toFloat()
    }

    private fun randomRange(min: Float, max: Float, seed: Float): Float {
        return min + seed * (max - min)
    }

    /**
     * Draw a 4-point star
     */
    private fun drawStar(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        canvas.drawLine(cx - size, cy, cx + size, cy, paint)
        canvas.drawLine(cx, cy - size, cx, cy + size, paint)
        canvas.drawCircle(cx, cy, size * 0.3f, paint)
    }

    /**
     * Draw achievement panel on right side of task zone.
     * Returns the reserved width to avoid task overlap.
     */
    private fun drawAchievementPanel(
        canvas: Canvas,
        achievementCount: Int,
        streakDays: Int,
        colors: ThemeColors,
        width: Int,
        height: Int
    ): Float {
        val layout = getLayoutConfig(width, height)
        val density = layout.safeInsets.density
        val taskZone = layout.layoutZones.task

        val rightPadding = maxOf(24f * density, width * 0.04f)
        val panelWidth = maxOf(64f * density, width * 0.12f)
        val panelX = width - rightPadding - panelWidth
        val panelY = taskZone.y + (16f * density)

        val titleSize = 18f * density
        val labelSize = 8f * density
        val badgeSize = 28f * density
        val badgeLabelSize = 7.5f * density
        val streakValueSize = 14f * density
        val streakLabelSize = 8f * density
        val innerPadding = 10f * density
        val dividerHeight = maxOf(1f, density * 0.6f)

        val topBlockHeight = titleSize + labelSize + (6f * density)
        val badgeBlockHeight = if (achievementCount > 0) {
            dividerHeight + (8f * density) + badgeSize + badgeLabelSize + (4f * density)
        } else {
            0f
        }
        val streakBlockHeight = if (streakDays > 0) {
            dividerHeight + (8f * density) + streakValueSize + streakLabelSize + (4f * density)
        } else {
            0f
        }
        val panelHeight = topBlockHeight + badgeBlockHeight + streakBlockHeight + (innerPadding * 2f)

        val panelRect = RectF(panelX, panelY, panelX + panelWidth, panelY + panelHeight)

        val panelPaint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
            alpha = 120
        }
        val borderPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = maxOf(1f, density * 0.6f)
            color = Color.argb(50, 255, 255, 255)
        }

        canvas.drawRoundRect(panelRect, 16f * density, 16f * density, panelPaint)
        canvas.drawRoundRect(panelRect, 16f * density, 16f * density, borderPaint)

        var currentY = panelY + innerPadding + titleSize

        val countPaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#FFD700")
            textSize = titleSize
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(achievementCount.toString(), panelX + panelWidth / 2f, currentY, countPaint)

        currentY += labelSize + (6f * density)

        val labelPaint = Paint().apply {
            isAntiAlias = true
            color = colors.subtitleColor
            textSize = labelSize
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        canvas.drawText("ACHIEVEMENTS", panelX + panelWidth / 2f, currentY, labelPaint)

        if (achievementCount > 0) {
            val dividerY = currentY + (8f * density)
            val dividerPaint = Paint().apply {
                isAntiAlias = true
                color = Color.argb(40, 255, 255, 255)
                strokeWidth = dividerHeight
            }
            canvas.drawLine(
                panelX + innerPadding,
                dividerY,
                panelX + panelWidth - innerPadding,
                dividerY,
                dividerPaint
            )

            val badgeCenterY = dividerY + (8f * density) + badgeSize / 2f
            val badgePaint = Paint().apply {
                isAntiAlias = true
                color = Color.parseColor("#4ADE80")
            }
            canvas.drawCircle(panelX + panelWidth / 2f, badgeCenterY, badgeSize / 2f, badgePaint)

            val badgeIconPaint = Paint().apply {
                isAntiAlias = true
                color = Color.WHITE
                textSize = badgeSize * 0.5f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText("🏆", panelX + panelWidth / 2f, badgeCenterY + badgeIconPaint.textSize * 0.35f, badgeIconPaint)

            val badgeLabelPaint = Paint().apply {
                isAntiAlias = true
                color = colors.subtitleColor
                textSize = badgeLabelSize
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            val badgeLabelY = badgeCenterY + badgeSize / 2f + badgeLabelSize + (4f * density)
            canvas.drawText("RECENT", panelX + panelWidth / 2f, badgeLabelY, badgeLabelPaint)

            currentY = badgeLabelY
        }

        if (streakDays > 0) {
            val dividerY = currentY + (8f * density)
            val dividerPaint = Paint().apply {
                isAntiAlias = true
                color = Color.argb(40, 255, 255, 255)
                strokeWidth = dividerHeight
            }
            canvas.drawLine(
                panelX + innerPadding,
                dividerY,
                panelX + panelWidth - innerPadding,
                dividerY,
                dividerPaint
            )

            currentY = dividerY + (8f * density) + streakValueSize

            val streakPaint = Paint().apply {
                isAntiAlias = true
                color = colors.titleColor
                textSize = streakValueSize
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText("🔥 $streakDays", panelX + panelWidth / 2f, currentY, streakPaint)

            currentY += streakLabelSize + (4f * density)
            val streakLabelPaint = Paint().apply {
                isAntiAlias = true
                color = colors.subtitleColor
                textSize = streakLabelSize
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            canvas.drawText("DAY STREAK", panelX + panelWidth / 2f, currentY, streakLabelPaint)
        }

        return panelWidth + rightPadding
    }

    /**
     * Draw enhanced task list matching backend format
     * "RIGHT NOW" header with tasks below
     */
    private fun drawTaskListEnhanced(
        canvas: Canvas,
        tasks: List<StarEntity>,
        totalTaskCount: Int,
        colors: ThemeColors,
        width: Int,
        height: Int,
        reservedRightSpace: Float = 0f
    ) {
        val layout = getLayoutConfig(width, height)
        val marginH = layout.margins.horizontal
        val taskZone = layout.layoutZones.task
        val typography = layout.typography

        var currentY = taskZone.y + layout.margins.vertical + typography.labelMedium + 20f

        // "RIGHT NOW" header (matching backend style)
        val headerPaint = Paint().apply {
            isAntiAlias = true
            color = colors.subtitleColor
            textSize = typography.labelLarge
            typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        }
        headerPaint.letterSpacing = (2f / headerPaint.textSize).coerceAtLeast(0.02f)

        // Add text shadow for readability
        headerPaint.setShadowLayer(4f, 2f, 2f, Color.argb(100, 0, 0, 0))
        canvas.drawText("RIGHT NOW", marginH, currentY, headerPaint)

        currentY += layout.margins.vertical + 10f

        // Draw up to 3 tasks
        val titlePaint = TextPaint().apply {
            isAntiAlias = true
            color = colors.titleColor
            textSize = typography.headlineLarge
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setShadowLayer(4f, 2f, 2f, Color.argb(100, 0, 0, 0))
        }

        val metaPaint = Paint().apply {
            isAntiAlias = true
            color = colors.subtitleColor
            textSize = typography.bodyMedium
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            setShadowLayer(3f, 1f, 1f, Color.argb(80, 0, 0, 0))
        }

        val circleRadius = typography.labelSmall / 2f
        val textStartX = marginH + circleRadius * 2f + 10f
        val maxWidth = (width - marginH - textStartX - reservedRightSpace)
            .toInt()
            .coerceAtLeast(100)

        tasks.take(3).forEachIndexed { index, task ->
            // Priority indicator circle
            val circleColor = getDueDatePriorityColor(task.dueDate)

            val circlePaint = Paint().apply {
                isAntiAlias = true
                color = circleColor
                style = Paint.Style.FILL
            }

            val circleY = currentY - typography.titleLarge / 3f
            canvas.drawCircle(marginH, circleY, circleRadius, circlePaint)

            // Task title with wrapping
            val titleLayout = StaticLayout.Builder
                .obtain(task.title, 0, task.title.length, titlePaint, maxWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setMaxLines(2)
                .setEllipsize(android.text.TextUtils.TruncateAt.END)
                .build()

            val titleTop = currentY - titleLayout.getLineBaseline(0)
            canvas.save()
            canvas.translate(textStartX, titleTop)
            titleLayout.draw(canvas)
            canvas.restore()

            currentY += titleLayout.height + 5f

            // Time estimate / due date
            val metaText = formatDueDate(task.dueDate)
            if (metaText != null) {
                canvas.drawText(metaText, textStartX, currentY, metaPaint)
                currentY += metaPaint.textSize + 10f
            }

            currentY += layout.margins.vertical
        }

        // Remaining count
        val remainingCount = totalTaskCount - tasks.size.coerceAtMost(3)
        if (remainingCount > 0) {
            canvas.drawText("+ $remainingCount more today", marginH, currentY, metaPaint)
        }
    }

    /**
     * Draw clear state (no tasks)
     */
    private fun drawClearState(
        canvas: Canvas,
        colors: ThemeColors,
        width: Int,
        height: Int
    ) {
        val centerX = width / 2f
        val layout = getLayoutConfig(width, height)
        val centerY = layout.layoutZones.task.centerY

        // Draw checkmark circle
        val circleRadius = width * 0.12f
        val circlePaint = Paint().apply {
            isAntiAlias = true
            color = colors.taskCircle
            alpha = 180
            style = Paint.Style.FILL
        }
        canvas.drawCircle(centerX, centerY, circleRadius, circlePaint)

        // Draw checkmark
        val checkPaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = circleRadius * 0.15f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val checkSize = circleRadius * 0.5f
        canvas.drawLine(
            centerX - checkSize * 0.5f, centerY,
            centerX - checkSize * 0.1f, centerY + checkSize * 0.4f,
            checkPaint
        )
        canvas.drawLine(
            centerX - checkSize * 0.1f, centerY + checkSize * 0.4f,
            centerX + checkSize * 0.5f, centerY - checkSize * 0.3f,
            checkPaint
        )

        // "All clear ✨" text with glow effect
        val messagePaint = Paint().apply {
            isAntiAlias = true
            color = colors.titleColor
            textSize = layout.typography.headlineLarge
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setShadowLayer(8f, 0f, 0f, colors.taskCircleGlow)
        }

        canvas.drawText(
            "All clear ✨",
            centerX,
            centerY + circleRadius + (width * 0.1f),
            messagePaint
        )

        val subtitlePaint = Paint().apply {
            isAntiAlias = true
            color = colors.subtitleColor
            textSize = width * 0.035f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        canvas.drawText(
            "Rest. You earned it.",
            centerX,
            centerY + circleRadius + (width * 0.16f),
            subtitlePaint
        )
    }

    /**
     * Format due date for display
     */
    private fun formatDueDate(dueDateMs: Long?): String? {
        if (dueDateMs == null) return null

        val now = System.currentTimeMillis()
        val diff = dueDateMs - now

        if (diff < 0) {
            val hoursAgo = -diff / (1000 * 60 * 60)
            return when {
                hoursAgo < 1 -> "⚠️ Overdue"
                hoursAgo < 24 -> "⚠️ Overdue by ${hoursAgo}h"
                else -> "⚠️ Overdue by ${hoursAgo / 24}d"
            }
        }

        val hours = diff / (1000 * 60 * 60)
        val minutes = (diff / (1000 * 60)) % 60

        return when {
            hours < 1 -> "Due in ${minutes}min"
            hours < 24 -> "Due in ${hours}h"
            hours < 48 -> "Due tomorrow"
            else -> {
                val dateFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
                "Due ${dateFormat.format(Date(dueDateMs))}"
            }
        }
    }

    private fun sortTasksByDueDate(tasks: List<StarEntity>): List<StarEntity> {
        return tasks.sortedWith(
            compareBy<StarEntity> { it.dueDate == null }
                .thenBy { it.dueDate ?: Long.MAX_VALUE }
                .thenBy { it.urgency }
        )
    }
}
