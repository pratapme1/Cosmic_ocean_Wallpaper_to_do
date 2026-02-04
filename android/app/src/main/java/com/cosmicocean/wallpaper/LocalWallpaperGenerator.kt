package com.cosmicocean.wallpaper

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
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
import com.cosmicocean.ui.state.ContextMode
import com.cosmicocean.ui.state.EnvironmentPreferences
import com.cosmicocean.ui.state.ParticleIntensity
import com.cosmicocean.ui.state.TimeOfDayMode
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.PI
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

    private data class EnvGradientStop(
        val offset: Float,
        val color: Int
    )

    private data class EnvOverlay(
        val type: String,
        val opacity: Float,
        val color: Int? = null,
        val colors: List<Int>? = null,
        val count: Int? = null,
        val rays: Int? = null,
        val positionX: Float? = null,
        val positionY: Float? = null,
        val spread: Float? = null,
        val density: Int? = null,
        val coverage: Float? = null,
        val lightningFrequencyMs: Long? = null,
        val lightningIntensity: Float? = null
    )

    private data class EnvParticles(
        val type: String,
        val count: Int,
        val color: Int? = null,
        val colors: List<Int>? = null,
        val speed: Float = 0.2f,
        val sizeMin: Float = 1f,
        val sizeMax: Float = 3f,
        val opacityMin: Float = 0.3f,
        val opacityMax: Float = 0.8f,
        val twinkle: Boolean = false,
        val glow: Boolean = false,
        val blur: Boolean = false,
        val angle: Float = 0f,
        val length: Float = 0f
    )

    private data class EnvAmbient(
        val brightness: Float,
        val saturation: Float,
        val warmth: Float,
        val glow: Float = 0f,
        val flash: Boolean = false
    )

    private data class EnvironmentVisuals(
        val period: String,
        val gradientStops: List<EnvGradientStop>,
        val overlay: EnvOverlay?,
        val particles: EnvParticles?,
        val ambient: EnvAmbient
    )

    private data class WeatherVisuals(
        val state: String,
        val overlay: EnvOverlay?,
        val particles: EnvParticles?,
        val ambient: EnvAmbient,
        val message: String?
    )

    private data class ProductivityMetrics(
        val totalTasks: Int,
        val completedTasks: Int,
        val pendingTasks: Int,
        val overdueTasks: Int,
        val criticalOverdue: Int,
        val completionRate: Float,
        val maxOverdueHours: Float,
        val isAllClear: Boolean
    )

    private data class ShortSuggestion(
        val title: String,
        val estimateMinutes: Int
    )

    // Layout constants (matching backend layout-system.js)
    private const val SAFE_ZONE_TOP_RATIO = 0.12f  // Clock/status bar area
    private const val TASK_ZONE_START_RATIO = 0.35f
    private const val TASK_ZONE_END_RATIO = 0.75f
    private const val MARGIN_HORIZONTAL_RATIO = 0.06f
    private const val COMPLETION_CELEBRATION_WINDOW_MS = 2 * 60 * 1000L
    private const val AMBIENT_DUE_SOON_WINDOW_MS = 2 * 60 * 60 * 1000L

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
        streakDays: Int = 0,
        environmentPreferences: EnvironmentPreferences? = null,
        weatherTasks: List<StarEntity>? = null,
        recentCompletionAt: Long? = null
    ): Bitmap {
        Log.d(TAG, "Generating wallpaper: ${width}x${height}, theme=$theme, tasks=${tasks.size}, total=$totalTaskCount")

        val now = System.currentTimeMillis()
        val sortedTasks = sortTasksByDueDate(tasks)
        val allTasks = weatherTasks ?: tasks
        val focusEnabled = environmentPreferences?.focusModeEnabled == true
        val contextBadge = buildContextBadge(environmentPreferences)
        val shortSuggestion = findShortSuggestion(allTasks, environmentPreferences)
        val showAmbientPulse = environmentPreferences?.ambientRemindersEnabled == true &&
            shouldShowAmbientPulse(allTasks, now)
        val showCelebration = recentCompletionAt != null &&
            now - recentCompletionAt < COMPLETION_CELEBRATION_WINDOW_MS

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // CRITICAL FIX: Fill with solid base color first to prevent black screen
        val urgency = if (sortedTasks.isNotEmpty()) calculateUrgency(sortedTasks[0]) else UrgencyLevel.CLEAR
        val colors = theme.getColors(urgency)
        canvas.drawColor(colors.gradientEnd)

        // Layer 1: Environment gradient + overlays (time of day, weather)
        val envEnabled = environmentPreferences?.environmentEnabled ?: true
        val intensityMultiplier = if (envEnabled) {
            drawEnvironmentLayers(
                canvas = canvas,
                width = width,
                height = height,
                themeColors = colors,
                environmentPreferences = environmentPreferences,
                weatherTasks = weatherTasks ?: tasks,
                isCustomBackground = false
            )
        } else {
            drawGradientBackground(canvas, colors, width, height)
            0f
        }

        // Layer 2: Theme particle system (scaled by environment intensity)
        if (envEnabled) {
            drawParticles(canvas, colors, width, height, theme, urgency, intensityMultiplier)
        }

        if (envEnabled && environmentPreferences?.overdueHeatmapEnabled == true) {
            val metrics = calculateProductivityMetrics(allTasks, now)
            drawOverdueHeatmap(canvas, metrics, width, height)
        }

        if (showAmbientPulse) {
            drawAmbientPulse(canvas, width, height, colors, now)
        }

        if (focusEnabled) {
            drawFocusOverlay(canvas, width, height)
        }

        if (showCelebration) {
            drawCompletionBurst(canvas, width, height, colors, now)
        }

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
                achievementReservedSpace,
                highlightFirstTask = true,
                badges = buildBadgeRow(
                    focusEnabled = focusEnabled,
                    contextBadge = contextBadge,
                    shortSuggestion = shortSuggestion
                )
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
        streakDays: Int = 0,
        theme: WallpaperTheme = WallpaperTheme.DEEP_OCEAN,
        environmentPreferences: EnvironmentPreferences? = null,
        weatherTasks: List<StarEntity>? = null,
        recentCompletionAt: Long? = null
    ): Bitmap {
        Log.d(TAG, "Generating wallpaper with custom background: ${width}x${height}, tasks=${tasks.size}")

        val now = System.currentTimeMillis()
        val sortedTasks = sortTasksByDueDate(tasks)
        val allTasks = weatherTasks ?: tasks
        val focusEnabled = environmentPreferences?.focusModeEnabled == true
        val contextBadge = buildContextBadge(environmentPreferences)
        val shortSuggestion = findShortSuggestion(allTasks, environmentPreferences)
        val showAmbientPulse = environmentPreferences?.ambientRemindersEnabled == true &&
            shouldShowAmbientPulse(allTasks, now)
        val showCelebration = recentCompletionAt != null &&
            now - recentCompletionAt < COMPLETION_CELEBRATION_WINDOW_MS

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

        // Layer 2.5: Environment overlays on top of custom background
        if (environmentPreferences?.environmentEnabled != false) {
            drawEnvironmentLayers(
                canvas = canvas,
                width = width,
                height = height,
                themeColors = colors,
                environmentPreferences = environmentPreferences,
                weatherTasks = weatherTasks ?: tasks,
                isCustomBackground = true
            )
        }

        if (environmentPreferences?.environmentEnabled != false && environmentPreferences?.overdueHeatmapEnabled == true) {
            val metrics = calculateProductivityMetrics(allTasks, now)
            drawOverdueHeatmap(canvas, metrics, width, height)
        }

        if (showAmbientPulse) {
            drawAmbientPulse(canvas, width, height, colors, now)
        }

        if (focusEnabled) {
            drawFocusOverlay(canvas, width, height)
        }

        if (showCelebration) {
            drawCompletionBurst(canvas, width, height, colors, now)
        }

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
                achievementReservedSpace,
                highlightFirstTask = true,
                badges = buildBadgeRow(
                    focusEnabled = focusEnabled,
                    contextBadge = contextBadge,
                    shortSuggestion = shortSuggestion
                )
            )
        } else {
            drawClearState(canvas, colors, width, height)
        }

        return bitmap
    }

    private fun drawEnvironmentLayers(
        canvas: Canvas,
        width: Int,
        height: Int,
        themeColors: ThemeColors,
        environmentPreferences: EnvironmentPreferences?,
        weatherTasks: List<StarEntity>,
        isCustomBackground: Boolean
    ): Float {
        if (environmentPreferences == null) {
            if (!isCustomBackground) {
                drawGradientBackground(canvas, themeColors, width, height)
            }
            return 1f
        }

        val now = System.currentTimeMillis()
        val focusEnabled = environmentPreferences.focusModeEnabled
        val intensityMultiplier = if (focusEnabled) {
            environmentPreferences.particleIntensity.multiplier * 0.4f
        } else {
            environmentPreferences.particleIntensity.multiplier
        }
        val environment = resolveEnvironment(environmentPreferences, themeColors, now)
        val weather = if (environmentPreferences.weatherOverlayEnabled && !focusEnabled) {
            resolveWeather(weatherTasks, themeColors, now)
        } else {
            null
        }

        val ambient = blendAmbient(environment.ambient, weather?.ambient)
        val gradientAlpha = if (isCustomBackground) 0.35f else 1f
        val ambientStrength = if (isCustomBackground) 0.6f else 1f

        drawEnvironmentGradient(canvas, environment.gradientStops, width, height, gradientAlpha)
        applyAmbientAdjustment(canvas, width, height, ambient, ambientStrength)

        environment.overlay?.let { drawOverlay(canvas, it, width, height, now) }
        weather?.overlay?.let { drawOverlay(canvas, it, width, height, now) }

        environment.particles?.let {
            drawEnvParticles(canvas, it, width, height, intensityMultiplier, now, themeColors)
        }
        weather?.particles?.let {
            drawEnvParticles(canvas, it, width, height, intensityMultiplier, now, themeColors)
        }

        return intensityMultiplier
    }

    private fun drawOverdueHeatmap(
        canvas: Canvas,
        metrics: ProductivityMetrics,
        width: Int,
        height: Int
    ) {
        if (metrics.overdueTasks <= 0 && metrics.criticalOverdue <= 0) return

        val intensity = (
            metrics.overdueTasks / 5f +
                metrics.criticalOverdue / 3f +
                (metrics.maxOverdueHours / 24f)
            ).coerceAtMost(1f)

        val alpha = (0.08f + intensity * 0.35f).coerceAtMost(0.5f)
        val paint = Paint().apply {
            shader = RadialGradient(
                width / 2f,
                height * 0.6f,
                max(width, height).toFloat(),
                intArrayOf(
                    Color.argb((alpha * 255).toInt(), 255, 59, 48),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0.2f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    private fun drawFocusOverlay(canvas: Canvas, width: Int, height: Int) {
        val basePaint = Paint().apply {
            color = Color.parseColor("#00121f")
            alpha = 90
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), basePaint)

        val centerX = width / 2f
        val centerY = height / 2f
        val vignette = Paint().apply {
            shader = RadialGradient(
                centerX,
                centerY,
                width * 0.7f,
                intArrayOf(Color.argb(0, 0, 0, 0), Color.argb(140, 0, 0, 0)),
                floatArrayOf(0.4f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), vignette)
    }

    private fun drawAmbientPulse(
        canvas: Canvas,
        width: Int,
        height: Int,
        colors: ThemeColors,
        now: Long
    ) {
        val layout = getLayoutConfig(width, height)
        val centerX = width / 2f
        val centerY = layout.layoutZones.task.centerY
        val phase = (now % 60000L).toFloat() / 60000f
        val pulse = 0.35f + 0.25f * sin(phase * 2f * PI.toFloat())
        val alpha = (pulse * 160f).toInt().coerceIn(30, 160)

        val glow = applyAlpha(colors.taskCircleGlow, alpha)
        val paint = Paint().apply {
            shader = RadialGradient(
                centerX,
                centerY,
                width * 0.45f,
                intArrayOf(glow, Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    private fun drawCompletionBurst(
        canvas: Canvas,
        width: Int,
        height: Int,
        colors: ThemeColors,
        now: Long
    ) {
        val layout = getLayoutConfig(width, height)
        val centerX = width - layout.margins.horizontal * 2.5f
        val centerY = layout.layoutZones.task.y + layout.margins.vertical * 2f
        val baseRadius = width * 0.03f
        val phase = (now % 4000L).toFloat() / 4000f
        val pulse = 0.6f + 0.4f * sin(phase * 2f * PI.toFloat())
        val radius = baseRadius * (1.0f + pulse * 0.4f)

        val linePaint = Paint().apply {
            isAntiAlias = true
            color = applyAlpha(colors.taskCircleGlow, 200)
            strokeWidth = max(2f, baseRadius * 0.2f)
            style = Paint.Style.STROKE
        }

        for (i in 0 until 8) {
            val angle = i * (PI.toFloat() / 4f)
            val startX = centerX + cos(angle) * radius * 0.3f
            val startY = centerY + sin(angle) * radius * 0.3f
            val endX = centerX + cos(angle) * radius * 1.4f
            val endY = centerY + sin(angle) * radius * 1.4f
            canvas.drawLine(startX, startY, endX, endY, linePaint)
        }

        val corePaint = Paint().apply {
            isAntiAlias = true
            color = applyAlpha(colors.titleColor, 220)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(centerX, centerY, radius * 0.35f, corePaint)
    }

    private fun applyAlpha(color: Int, alpha: Int): Int {
        return Color.argb(
            alpha.coerceIn(0, 255),
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
    }

    private fun resolveEnvironment(
        preferences: EnvironmentPreferences,
        themeColors: ThemeColors,
        now: Long
    ): EnvironmentVisuals {
        val period = resolveTimePeriod(preferences, now)
        val base = environmentForPeriod(period)
        val tintedStops = base.gradientStops.map { stop ->
            stop.copy(color = blendColors(stop.color, themeColors.gradientEnd, 0.12f))
        }
        val tintedParticles = base.particles?.let { spec ->
            val blendedColor = spec.color?.let { blendColors(it, themeColors.particleColor, 0.25f) }
            val blendedColors = spec.colors?.map { blendColors(it, themeColors.particleColor, 0.2f) }
            spec.copy(
                color = blendedColor ?: spec.color,
                colors = blendedColors ?: spec.colors
            )
        }

        return base.copy(gradientStops = tintedStops, particles = tintedParticles)
    }

    private fun resolveTimePeriod(preferences: EnvironmentPreferences, now: Long): String {
        if (preferences.timeOfDayMode == TimeOfDayMode.MANUAL) {
            return when (preferences.manualTimePeriod.lowercase(Locale.US)) {
                "dawn" -> "dawn"
                "morning" -> "morning"
                "afternoon" -> "afternoon"
                "evening" -> "evening"
                "night" -> "night"
                else -> "morning"
            }
        }

        val hour = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).hour
        return when {
            hour in 5..6 -> "dawn"
            hour in 7..11 -> "morning"
            hour in 12..16 -> "afternoon"
            hour in 17..19 -> "evening"
            else -> "night"
        }
    }

    private fun environmentForPeriod(period: String): EnvironmentVisuals {
        return when (period) {
            "dawn" -> EnvironmentVisuals(
                period = "dawn",
                gradientStops = listOf(
                    EnvGradientStop(0f, Color.parseColor("#1a1a2e")),
                    EnvGradientStop(0.3f, Color.parseColor("#4a1942")),
                    EnvGradientStop(0.5f, Color.parseColor("#c94b4b")),
                    EnvGradientStop(0.7f, Color.parseColor("#f2994a")),
                    EnvGradientStop(1f, Color.parseColor("#ffb347"))
                ),
                overlay = EnvOverlay(
                    type = "sunrise_rays",
                    opacity = 0.3f,
                    color = Color.parseColor("#ffb347"),
                    rays = 8
                ),
                particles = EnvParticles(
                    type = "dust_motes",
                    count = 15,
                    color = Color.parseColor("#ffe0b2"),
                    speed = 0.3f,
                    sizeMin = 1f,
                    sizeMax = 3f
                ),
                ambient = EnvAmbient(brightness = 0.7f, warmth = 0.8f, saturation = 1.1f)
            )
            "morning" -> EnvironmentVisuals(
                period = "morning",
                gradientStops = listOf(
                    EnvGradientStop(0f, Color.parseColor("#87ceeb")),
                    EnvGradientStop(0.4f, Color.parseColor("#98d8e8")),
                    EnvGradientStop(0.7f, Color.parseColor("#b0e0f0")),
                    EnvGradientStop(1f, Color.parseColor("#e0f4ff"))
                ),
                overlay = EnvOverlay(
                    type = "clouds",
                    opacity = 0.4f,
                    count = 5,
                    color = Color.parseColor("#ffffff")
                ),
                particles = EnvParticles(
                    type = "floating_specks",
                    count = 20,
                    color = Color.parseColor("#ffffff"),
                    speed = 0.4f,
                    sizeMin = 1f,
                    sizeMax = 2f
                ),
                ambient = EnvAmbient(brightness = 1.0f, warmth = 0.5f, saturation = 1.0f)
            )
            "afternoon" -> EnvironmentVisuals(
                period = "afternoon",
                gradientStops = listOf(
                    EnvGradientStop(0f, Color.parseColor("#1e3c72")),
                    EnvGradientStop(0.3f, Color.parseColor("#2a5298")),
                    EnvGradientStop(0.6f, Color.parseColor("#4a90c2")),
                    EnvGradientStop(1f, Color.parseColor("#87ceeb"))
                ),
                overlay = EnvOverlay(
                    type = "sun_rays",
                    opacity = 0.2f,
                    color = Color.parseColor("#ffd700"),
                    rays = 12,
                    positionX = 0.7f,
                    positionY = 0.2f
                ),
                particles = EnvParticles(
                    type = "light_beams",
                    count = 8,
                    color = Color.parseColor("#fff8dc"),
                    speed = 0.1f,
                    sizeMin = 2f,
                    sizeMax = 5f
                ),
                ambient = EnvAmbient(brightness = 0.95f, warmth = 0.6f, saturation = 1.05f)
            )
            "evening" -> EnvironmentVisuals(
                period = "evening",
                gradientStops = listOf(
                    EnvGradientStop(0f, Color.parseColor("#2c3e50")),
                    EnvGradientStop(0.25f, Color.parseColor("#614385")),
                    EnvGradientStop(0.5f, Color.parseColor("#c94b4b")),
                    EnvGradientStop(0.75f, Color.parseColor("#f2994a")),
                    EnvGradientStop(1f, Color.parseColor("#f8b500"))
                ),
                overlay = EnvOverlay(
                    type = "sunset_glow",
                    opacity = 0.35f,
                    color = Color.parseColor("#ff6b6b"),
                    spread = 0.6f
                ),
                particles = EnvParticles(
                    type = "warm_drift",
                    count = 12,
                    color = Color.parseColor("#ffd6a5"),
                    speed = 0.25f,
                    sizeMin = 2f,
                    sizeMax = 4f
                ),
                ambient = EnvAmbient(brightness = 0.8f, warmth = 0.9f, saturation = 1.15f)
            )
            else -> EnvironmentVisuals(
                period = "night",
                gradientStops = listOf(
                    EnvGradientStop(0f, Color.parseColor("#0d1b2a")),
                    EnvGradientStop(0.3f, Color.parseColor("#1b263b")),
                    EnvGradientStop(0.6f, Color.parseColor("#2d3f5f")),
                    EnvGradientStop(1f, Color.parseColor("#3a506b"))
                ),
                overlay = EnvOverlay(
                    type = "starfield",
                    opacity = 0.6f,
                    density = 150
                ),
                particles = EnvParticles(
                    type = "stars",
                    count = 50,
                    color = Color.parseColor("#ffffff"),
                    speed = 0.05f,
                    sizeMin = 1f,
                    sizeMax = 3f,
                    twinkle = true
                ),
                ambient = EnvAmbient(brightness = 0.6f, warmth = 0.2f, saturation = 0.9f)
            )
        }
    }

    private fun resolveWeather(
        tasks: List<StarEntity>,
        themeColors: ThemeColors,
        now: Long
    ): WeatherVisuals {
        val metrics = calculateProductivityMetrics(tasks, now)
        val state = determineWeatherState(metrics)
        val base = when (state) {
            "rainbow" -> WeatherVisuals(
                state = "rainbow",
                overlay = EnvOverlay(
                    type = "rainbow_arc",
                    opacity = 0.4f,
                    positionX = 0.5f,
                    positionY = 0.3f,
                    colors = listOf(
                        Color.parseColor("#ff6b6b"),
                        Color.parseColor("#feca57"),
                        Color.parseColor("#48dbfb"),
                        Color.parseColor("#1dd1a1"),
                        Color.parseColor("#5f27cd")
                    ),
                    spread = 0.8f
                ),
                particles = EnvParticles(
                    type = "sparkles",
                    count = 40,
                    colors = listOf(
                        Color.parseColor("#ffd700"),
                        Color.parseColor("#ff69b4"),
                        Color.parseColor("#00ff7f"),
                        Color.parseColor("#87ceeb")
                    ),
                    speed = 0.6f,
                    sizeMin = 2f,
                    sizeMax = 5f,
                    glow = true,
                    twinkle = true
                ),
                ambient = EnvAmbient(brightness = 1.1f, warmth = 0.7f, saturation = 1.2f, glow = 0.3f),
                message = "All clear!"
            )
            "cloudy" -> WeatherVisuals(
                state = "cloudy",
                overlay = EnvOverlay(
                    type = "clouds",
                    opacity = 0.35f,
                    count = 4,
                    color = Color.parseColor("#d3d3d3"),
                    coverage = 0.3f
                ),
                particles = EnvParticles(
                    type = "mist",
                    count = 20,
                    color = Color.parseColor("#e0e0e0"),
                    speed = 0.1f,
                    sizeMin = 3f,
                    sizeMax = 8f,
                    blur = true
                ),
                ambient = EnvAmbient(brightness = 0.9f, warmth = 0.4f, saturation = 0.95f),
                message = null
            )
            "overcast" -> WeatherVisuals(
                state = "overcast",
                overlay = EnvOverlay(
                    type = "heavy_clouds",
                    opacity = 0.5f,
                    count = 6,
                    color = Color.parseColor("#9e9e9e"),
                    coverage = 0.6f
                ),
                particles = EnvParticles(
                    type = "fog",
                    count = 30,
                    color = Color.parseColor("#bdbdbd"),
                    speed = 0.08f,
                    sizeMin = 5f,
                    sizeMax = 15f,
                    blur = true
                ),
                ambient = EnvAmbient(brightness = 0.75f, warmth = 0.35f, saturation = 0.85f),
                message = "Tasks need attention"
            )
            "storm" -> WeatherVisuals(
                state = "storm",
                overlay = EnvOverlay(
                    type = "storm_clouds",
                    opacity = 0.6f,
                    color = Color.parseColor("#424242"),
                    coverage = 0.8f,
                    lightningFrequencyMs = 5000L,
                    lightningIntensity = 0.8f
                ),
                particles = EnvParticles(
                    type = "rain",
                    count = 60,
                    color = Color.parseColor("#90caf9"),
                    speed = 1.5f,
                    sizeMin = 1f,
                    sizeMax = 2f,
                    angle = -10f,
                    length = 15f
                ),
                ambient = EnvAmbient(brightness = 0.6f, warmth = 0.2f, saturation = 0.7f, flash = true),
                message = "Critical tasks overdue!"
            )
            else -> WeatherVisuals(
                state = "clear",
                overlay = EnvOverlay(
                    type = "subtle_glow",
                    opacity = 0.15f,
                    color = Color.parseColor("#87ceeb"),
                    positionX = 0.7f,
                    positionY = 0.2f
                ),
                particles = EnvParticles(
                    type = "light_dust",
                    count = 15,
                    color = Color.parseColor("#ffffff"),
                    speed = 0.2f,
                    sizeMin = 1f,
                    sizeMax = 2f
                ),
                ambient = EnvAmbient(brightness = 1.0f, warmth = 0.5f, saturation = 1.0f, glow = 0.1f),
                message = null
            )
        }

        val tintedParticles = base.particles?.let { spec ->
            val blendedColor = spec.color?.let { blendColors(it, themeColors.particleColor, 0.2f) }
            val blendedColors = spec.colors?.map { blendColors(it, themeColors.particleColor, 0.2f) }
            spec.copy(
                color = blendedColor ?: spec.color,
                colors = blendedColors ?: spec.colors
            )
        }

        return base.copy(particles = tintedParticles)
    }

    private object WeatherThresholds {
        const val COMPLETION_EXCELLENT = 0.9f
        const val COMPLETION_GOOD = 0.7f
        const val COMPLETION_MODERATE = 0.5f
        const val OVERDUE_CLOUDY = 2
        const val OVERDUE_OVERCAST = 5
        const val OVERDUE_STORM = 10
        const val CRITICAL_CLOUDY = 1
        const val CRITICAL_OVERCAST = 2
        const val CRITICAL_STORM = 3
        const val HOURS_WARNING = 2f
        const val HOURS_SERIOUS = 12f
        const val HOURS_CRITICAL = 24f
    }

    private fun calculateProductivityMetrics(tasks: List<StarEntity>, now: Long): ProductivityMetrics {
        if (tasks.isEmpty()) {
            return ProductivityMetrics(
                totalTasks = 0,
                completedTasks = 0,
                pendingTasks = 0,
                overdueTasks = 0,
                criticalOverdue = 0,
                completionRate = 1.0f,
                maxOverdueHours = 0f,
                isAllClear = true
            )
        }

        var completedTasks = 0
        var overdueTasks = 0
        var criticalOverdue = 0
        var maxOverdueHours = 0f

        tasks.forEach { task ->
            if (task.isCompleted) {
                completedTasks++
                return@forEach
            }

            val dueDate = task.dueDate ?: return@forEach
            if (dueDate < now) {
                overdueTasks++
                val hoursOverdue = (now - dueDate).toFloat() / (1000f * 60f * 60f)
                maxOverdueHours = max(maxOverdueHours, hoursOverdue)
                if (task.urgency >= 3) {
                    criticalOverdue++
                }
            }
        }

        val totalTasks = tasks.size
        val pendingTasks = totalTasks - completedTasks
        val completionRate = if (totalTasks > 0) completedTasks.toFloat() / totalTasks.toFloat() else 1f
        val isAllClear = pendingTasks == 0 && totalTasks > 0

        return ProductivityMetrics(
            totalTasks = totalTasks,
            completedTasks = completedTasks,
            pendingTasks = pendingTasks,
            overdueTasks = overdueTasks,
            criticalOverdue = criticalOverdue,
            completionRate = completionRate,
            maxOverdueHours = maxOverdueHours,
            isAllClear = isAllClear
        )
    }

    private fun shouldShowAmbientPulse(tasks: List<StarEntity>, now: Long): Boolean {
        return tasks.any { task ->
            if (task.isCompleted || task.isArchived) return@any false
            val dueDate = task.dueDate ?: return@any false
            val diff = dueDate - now
            diff < 0 || diff <= AMBIENT_DUE_SOON_WINDOW_MS
        }
    }

    private fun findShortSuggestion(
        tasks: List<StarEntity>,
        prefs: EnvironmentPreferences?
    ): ShortSuggestion? {
        if (tasks.isEmpty()) return null
        val contextMode = prefs?.contextMode ?: ContextMode.AUTO
        val manualContext = prefs?.manualContext?.trim().orEmpty()

        val candidates = tasks.mapNotNull { task ->
            if (task.isCompleted || task.isArchived) return@mapNotNull null
            val estimate = extractEstimateMinutes(task.title) ?: return@mapNotNull null
            if (estimate > 15) return@mapNotNull null
            if (contextMode == ContextMode.MANUAL && manualContext.isNotEmpty()) {
                if (!matchesContext(task.title, manualContext)) return@mapNotNull null
            }
            task to estimate
        }

        val sorted = candidates.sortedWith(
            compareBy<Pair<StarEntity, Int>> { it.first.dueDate == null }
                .thenBy { it.first.dueDate ?: Long.MAX_VALUE }
                .thenBy { it.second }
        )

        return sorted.firstOrNull()?.let { ShortSuggestion(it.first.title, it.second) }
    }

    private fun extractEstimateMinutes(title: String): Int? {
        val lower = title.lowercase(Locale.US)
        val minRegex = Regex("(\\d{1,3})\\s*(m|min|mins|minutes)")
        val hourRegex = Regex("(\\d{1,3})\\s*(h|hr|hrs|hours)")
        val minMatch = minRegex.find(lower)
        if (minMatch != null) {
            return minMatch.groupValues[1].toIntOrNull()
        }
        val hourMatch = hourRegex.find(lower)
        if (hourMatch != null) {
            val hours = hourMatch.groupValues[1].toIntOrNull() ?: return null
            return hours * 60
        }
        return null
    }

    private fun matchesContext(title: String, contextValue: String): Boolean {
        if (contextValue.isBlank()) return true
        if (contextValue.lowercase(Locale.US) == "custom") return true
        val lower = title.lowercase(Locale.US)
        val token = "@${contextValue.lowercase(Locale.US)}"
        return lower.contains(token) || lower.contains(contextValue.lowercase(Locale.US))
    }

    private fun buildContextBadge(prefs: EnvironmentPreferences?): String? {
        if (prefs == null) return null
        return when (prefs.contextMode) {
            ContextMode.MANUAL -> {
                val label = prefs.manualContext.trim()
                if (label.isEmpty()) null else label.replaceFirstChar { it.uppercase() }
            }
            ContextMode.AUTO -> "Auto"
        }
    }

    private fun buildBadgeRow(
        focusEnabled: Boolean,
        contextBadge: String?,
        shortSuggestion: ShortSuggestion?
    ): List<String> {
        val badges = mutableListOf<String>()
        if (focusEnabled) badges.add("🪐 FOCUS")
        if (!contextBadge.isNullOrBlank()) badges.add("🧭 ${contextBadge.uppercase()}")
        if (shortSuggestion != null) badges.add("☄️ ${shortSuggestion.estimateMinutes}m QUICK")
        return badges
    }

    private fun determineWeatherState(metrics: ProductivityMetrics): String {
        if (metrics.isAllClear && metrics.totalTasks > 0) {
            return "rainbow"
        }

        if (metrics.criticalOverdue >= WeatherThresholds.CRITICAL_STORM ||
            metrics.overdueTasks >= WeatherThresholds.OVERDUE_STORM ||
            metrics.maxOverdueHours >= WeatherThresholds.HOURS_CRITICAL
        ) {
            return "storm"
        }

        if (metrics.criticalOverdue >= WeatherThresholds.CRITICAL_OVERCAST ||
            metrics.overdueTasks >= WeatherThresholds.OVERDUE_OVERCAST ||
            metrics.maxOverdueHours >= WeatherThresholds.HOURS_SERIOUS ||
            metrics.completionRate < WeatherThresholds.COMPLETION_MODERATE
        ) {
            return "overcast"
        }

        if (metrics.criticalOverdue >= WeatherThresholds.CRITICAL_CLOUDY ||
            metrics.overdueTasks >= WeatherThresholds.OVERDUE_CLOUDY ||
            metrics.maxOverdueHours >= WeatherThresholds.HOURS_WARNING ||
            metrics.completionRate < WeatherThresholds.COMPLETION_GOOD
        ) {
            return "cloudy"
        }

        return "clear"
    }

    private fun blendAmbient(environment: EnvAmbient, weather: EnvAmbient?): EnvAmbient {
        if (weather == null) return environment
        return EnvAmbient(
            brightness = environment.brightness * weather.brightness,
            saturation = environment.saturation * weather.saturation,
            warmth = (environment.warmth + weather.warmth) / 2f,
            glow = max(environment.glow, weather.glow),
            flash = weather.flash
        )
    }

    private fun drawEnvironmentGradient(
        canvas: Canvas,
        stops: List<EnvGradientStop>,
        width: Int,
        height: Int,
        alpha: Float
    ) {
        if (stops.isEmpty()) return
        val colors = stops.map { applyAlpha(it.color, alpha) }.toIntArray()
        val positions = stops.map { it.offset }.toFloatArray()

        val gradient = LinearGradient(
            0f,
            0f,
            0f,
            height.toFloat(),
            colors,
            positions,
            Shader.TileMode.CLAMP
        )

        val paint = Paint().apply {
            shader = gradient
            isAntiAlias = true
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    private fun applyAmbientAdjustment(
        canvas: Canvas,
        width: Int,
        height: Int,
        ambient: EnvAmbient,
        strength: Float
    ) {
        val paint = Paint().apply { isAntiAlias = true }

        if (ambient.brightness < 1f) {
            val alpha = ((1f - ambient.brightness) * 140f * strength).toInt().coerceIn(0, 180)
            paint.color = Color.argb(alpha, 0, 0, 0)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        } else if (ambient.brightness > 1f) {
            val alpha = ((ambient.brightness - 1f) * 120f * strength).toInt().coerceIn(0, 140)
            paint.color = Color.argb(alpha, 255, 255, 255)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }

        val warmthOffset = ambient.warmth - 0.5f
        if (warmthOffset > 0.05f) {
            val alpha = (warmthOffset * 100f * strength).toInt().coerceIn(0, 80)
            paint.color = Color.argb(alpha, 255, 179, 71)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        } else if (warmthOffset < -0.05f) {
            val alpha = (-warmthOffset * 100f * strength).toInt().coerceIn(0, 80)
            paint.color = Color.argb(alpha, 74, 144, 226)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
    }

    private fun drawOverlay(
        canvas: Canvas,
        overlay: EnvOverlay,
        width: Int,
        height: Int,
        timestamp: Long
    ) {
        when (overlay.type) {
            "sunrise_rays", "sun_rays" -> {
                val rays = overlay.rays ?: 8
                val centerX = (overlay.positionX ?: 0.5f) * width
                val centerY = (overlay.positionY ?: 0.8f) * height
                val color = overlay.color ?: Color.WHITE
                drawSunRays(canvas, centerX, centerY, rays, color, overlay.opacity, width, height)
            }
            "sunset_glow" -> {
                val centerX = width / 2f
                val centerY = height * 0.75f
                val radius = max(width, height) * (overlay.spread ?: 0.6f)
                val color = overlay.color ?: Color.WHITE
                drawRadialGlow(canvas, centerX, centerY, radius, color, overlay.opacity)
            }
            "clouds", "heavy_clouds", "storm_clouds" -> {
                val count = overlay.count ?: 5
                val coverage = overlay.coverage ?: if (overlay.type == "clouds") 0.3f else 0.6f
                val color = overlay.color ?: Color.WHITE
                drawCloudLayer(canvas, count, coverage, color, overlay.opacity, width, height, timestamp)
                if (overlay.type == "storm_clouds") {
                    drawLightningFlash(canvas, overlay, width, height, timestamp)
                }
            }
            "starfield" -> {
                val density = overlay.density ?: 120
                drawStarfield(canvas, density, overlay.opacity, width, height, timestamp)
            }
            "rainbow_arc" -> {
                val colors = overlay.colors ?: emptyList()
                val centerX = (overlay.positionX ?: 0.5f) * width
                val centerY = (overlay.positionY ?: 0.35f) * height
                val spread = overlay.spread ?: 0.8f
                drawRainbowArc(canvas, centerX, centerY, spread, colors, overlay.opacity, width, height)
            }
            "subtle_glow" -> {
                val centerX = (overlay.positionX ?: 0.7f) * width
                val centerY = (overlay.positionY ?: 0.2f) * height
                val color = overlay.color ?: Color.WHITE
                val radius = max(width, height) * 0.6f
                drawRadialGlow(canvas, centerX, centerY, radius, color, overlay.opacity)
            }
        }
    }

    private fun drawEnvParticles(
        canvas: Canvas,
        spec: EnvParticles,
        width: Int,
        height: Int,
        intensityMultiplier: Float,
        timestamp: Long,
        themeColors: ThemeColors
    ) {
        val layout = getLayoutConfig(width, height)
        val density = layout.safeInsets.density
        val count = max(1, (spec.count * intensityMultiplier).toInt())
        val paint = Paint().apply { isAntiAlias = true }

        val yMin = 0f
        val yMax = when (spec.type) {
            "rain" -> height.toFloat()
            "fog", "mist" -> height * 0.8f
            else -> height * 0.65f
        }

        for (i in 0 until count) {
            val seed = i * 37 + spec.type.hashCode()
            val baseX = randomRange(
                layout.margins.horizontal,
                width - layout.margins.horizontal,
                seededRandom(seed)
            )
            val baseY = randomRange(yMin, yMax, seededRandom(seed + 1))
            val size = randomRange(spec.sizeMin, spec.sizeMax, seededRandom(seed + 2)) * density
            val baseOpacity = randomRange(spec.opacityMin, spec.opacityMax, seededRandom(seed + 3))

            val twinkleScale = if (spec.twinkle) {
                val phase = ((timestamp + i * 120L) % 4000L).toFloat() / 4000f
                (sin(phase * Math.PI * 2) * 0.5 + 0.5).toFloat()
            } else {
                1f
            }

            val alpha = (baseOpacity * twinkleScale * 255).toInt().coerceIn(10, 255)
            val color = spec.colors?.let { colors ->
                colors[abs(seed) % colors.size]
            } ?: spec.color ?: themeColors.particleColor

            paint.color = color
            paint.alpha = alpha

            when (spec.type) {
                "rain" -> {
                    val angleRad = Math.toRadians(spec.angle.toDouble())
                    val length = if (spec.length > 0f) spec.length * density else size * 6f
                    val dx = cos(angleRad).toFloat() * length
                    val dy = sin(angleRad).toFloat() * length
                    paint.strokeWidth = max(1f, size * 0.4f)
                    canvas.drawLine(baseX, baseY, baseX + dx, baseY + dy, paint)
                }
                "light_beams" -> {
                    paint.strokeWidth = max(1f, size * 0.6f)
                    val beamHeight = height * 0.4f
                    canvas.drawLine(baseX, baseY, baseX, baseY + beamHeight, paint)
                }
                "sparkles" -> {
                    drawStar(canvas, baseX, baseY, size, paint)
                }
                "stars" -> {
                    drawStar(canvas, baseX, baseY, size, paint)
                }
                else -> {
                    canvas.drawCircle(baseX, baseY, size, paint)
                    if (spec.glow) {
                        paint.alpha = (alpha * 0.35f).toInt()
                        canvas.drawCircle(baseX, baseY, size * 1.8f, paint)
                    }
                }
            }
        }
    }

    private fun drawSunRays(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        rays: Int,
        color: Int,
        opacity: Float,
        width: Int,
        height: Int
    ) {
        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = max(2f, min(width, height) * 0.004f)
            this.color = color
            alpha = (opacity * 255).toInt().coerceIn(0, 255)
        }
        val radius = min(width, height) * 0.9f
        val step = (Math.PI * 2) / rays
        for (i in 0 until rays) {
            val angle = step * i
            val endX = centerX + cos(angle).toFloat() * radius
            val endY = centerY + sin(angle).toFloat() * radius
            canvas.drawLine(centerX, centerY, endX, endY, paint)
        }
    }

    private fun drawRadialGlow(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        color: Int,
        opacity: Float
    ) {
        val gradient = RadialGradient(
            centerX,
            centerY,
            radius,
            applyAlpha(color, opacity),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        val paint = Paint().apply {
            shader = gradient
            isAntiAlias = true
        }
        canvas.drawCircle(centerX, centerY, radius, paint)
    }

    private fun drawCloudLayer(
        canvas: Canvas,
        count: Int,
        coverage: Float,
        color: Int,
        opacity: Float,
        width: Int,
        height: Int,
        timestamp: Long
    ) {
        val paint = Paint().apply {
            isAntiAlias = true
            this.color = color
            alpha = (opacity * 255).toInt().coerceIn(0, 255)
        }
        val cloudHeight = height * coverage
        for (i in 0 until count) {
            val seed = i * 53 + (timestamp / 1000L).toInt()
            val x = randomRange(0f, width.toFloat(), seededRandom(seed))
            val y = randomRange(0f, cloudHeight, seededRandom(seed + 1))
            val size = randomRange(width * 0.12f, width * 0.28f, seededRandom(seed + 2))
            drawCloud(canvas, x, y, size, paint)
        }
    }

    private fun drawCloud(canvas: Canvas, centerX: Float, centerY: Float, size: Float, paint: Paint) {
        val radius = size * 0.3f
        canvas.drawCircle(centerX - radius, centerY, radius, paint)
        canvas.drawCircle(centerX + radius, centerY, radius * 1.1f, paint)
        canvas.drawCircle(centerX, centerY - radius * 0.6f, radius * 1.2f, paint)
        canvas.drawRect(centerX - size * 0.6f, centerY, centerX + size * 0.6f, centerY + radius, paint)
    }

    private fun drawLightningFlash(
        canvas: Canvas,
        overlay: EnvOverlay,
        width: Int,
        height: Int,
        timestamp: Long
    ) {
        val frequency = overlay.lightningFrequencyMs ?: return
        val phase = (timestamp % frequency).toInt()
        if (phase > 220) return
        val intensity = overlay.lightningIntensity ?: 0.6f
        val paint = Paint().apply {
            color = Color.WHITE
            alpha = (intensity * 180).toInt().coerceIn(0, 200)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    private fun drawStarfield(
        canvas: Canvas,
        density: Int,
        opacity: Float,
        width: Int,
        height: Int,
        timestamp: Long
    ) {
        val paint = Paint().apply { isAntiAlias = true }
        val count = max(20, density)
        for (i in 0 until count) {
            val seed = i * 73 + (timestamp / 10000L).toInt()
            val x = randomRange(0f, width.toFloat(), seededRandom(seed))
            val y = randomRange(0f, height * 0.5f, seededRandom(seed + 1))
            val size = randomRange(1f, 3f, seededRandom(seed + 2))
            val alpha = (opacity * 255).toInt().coerceIn(0, 255)
            paint.color = Color.WHITE
            paint.alpha = alpha
            canvas.drawCircle(x, y, size, paint)
        }
    }

    private fun drawRainbowArc(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        spread: Float,
        colors: List<Int>,
        opacity: Float,
        width: Int,
        height: Int
    ) {
        if (colors.isEmpty()) return
        val radius = min(width, height) * spread
        val arcRect = RectF(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )
        val stroke = max(6f, radius * 0.04f)
        colors.forEachIndexed { index, color ->
            val paint = Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                isAntiAlias = true
                this.color = color
                alpha = (opacity * 255).toInt().coerceIn(0, 255)
            }
            val inset = index * stroke * 1.1f
            val rect = RectF(
                arcRect.left + inset,
                arcRect.top + inset,
                arcRect.right - inset,
                arcRect.bottom - inset
            )
            canvas.drawArc(rect, 180f, 180f, false, paint)
        }
    }

    private fun blendColors(colorA: Int, colorB: Int, amount: Float): Int {
        val clamped = amount.coerceIn(0f, 1f)
        val inv = 1f - clamped
        val a = (Color.alpha(colorA) * inv + Color.alpha(colorB) * clamped).toInt()
        val r = (Color.red(colorA) * inv + Color.red(colorB) * clamped).toInt()
        val g = (Color.green(colorA) * inv + Color.green(colorB) * clamped).toInt()
        val b = (Color.blue(colorA) * inv + Color.blue(colorB) * clamped).toInt()
        return Color.argb(a, r, g, b)
    }

    private fun applyAlpha(color: Int, alphaFactor: Float): Int {
        val baseAlpha = Color.alpha(color)
        val newAlpha = (baseAlpha * alphaFactor).toInt().coerceIn(0, 255)
        return Color.argb(newAlpha, Color.red(color), Color.green(color), Color.blue(color))
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

        val baseUrgency = when (task.urgency) {
            1 -> UrgencyLevel.URGENT
            2 -> UrgencyLevel.ATTENTION
            3 -> UrgencyLevel.CALM
            else -> UrgencyLevel.CALM
        }

        val dueDate = task.dueDate ?: return baseUrgency

        val now = System.currentTimeMillis()
        val hoursUntilDue = (dueDate - now) / (1000 * 60 * 60)

        val dueUrgency = when {
            hoursUntilDue < 0 -> UrgencyLevel.CRITICAL    // Overdue
            hoursUntilDue < 4 -> UrgencyLevel.CRITICAL    // Due in 4 hours
            hoursUntilDue < 24 -> UrgencyLevel.URGENT     // Due today
            hoursUntilDue < 48 -> UrgencyLevel.ATTENTION  // Due tomorrow
            else -> UrgencyLevel.CALM
        }

        return if (dueUrgency.ordinal > baseUrgency.ordinal) dueUrgency else baseUrgency
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
        urgency: UrgencyLevel,
        intensityMultiplier: Float
    ) {
        val layout = getLayoutConfig(width, height)
        val density = layout.safeInsets.density
        val timestamp = System.currentTimeMillis()

        val params = when (theme) {
            WallpaperTheme.DEEP_OCEAN, WallpaperTheme.OCEAN -> getBubbleParams(urgency)
            else -> getStarParams(urgency)
        }
        val adjustedParams = params.copy(
            count = max(1, (params.count * intensityMultiplier).toInt())
        )

        val zones = listOf(
            Pair(layout.layoutZones.clock, 0.6f),
            Pair(layout.layoutZones.scene, 1.0f),
            Pair(layout.layoutZones.transition, 0.8f),
            Pair(layout.layoutZones.task, 0.4f),
            Pair(layout.layoutZones.interaction, 0.3f)
        )

        val paint = Paint().apply { isAntiAlias = true }

        for (i in 0 until adjustedParams.count) {
            val seed = i * PARTICLE_SEED_PRIME
            val zoneIndex = ((i.toFloat() / adjustedParams.count) * zones.size).toInt().coerceIn(0, zones.size - 1)
            val (zone, weight) = zones[zoneIndex]

            if (seededRandom(seed) > weight) continue

            val baseX = randomRange(
                layout.margins.horizontal,
                width - layout.margins.horizontal,
                seededRandom(seed + 1)
            )
            val baseY = randomRange(zone.y, zone.y + zone.height, seededRandom(seed + 2))
            val size = randomRange(adjustedParams.sizeMin, adjustedParams.sizeMax, seededRandom(seed + 3)) * density
            val baseOpacity = randomRange(adjustedParams.opacityMin, adjustedParams.opacityMax, seededRandom(seed + 4))

            val twinkleScale = if (adjustedParams.twinkleMinMs > 0 && adjustedParams.twinkleMaxMs > 0) {
                val twinkleDuration = randomRange(
                    adjustedParams.twinkleMinMs.toFloat(),
                    adjustedParams.twinkleMaxMs.toFloat(),
                    seededRandom(seed + 5)
                )
                val phase = ((timestamp + i * 100L) % twinkleDuration.toLong()) / twinkleDuration
                (sin(phase * Math.PI * 2) * 0.5 + 0.5).toFloat()
            } else {
                1f
            }

            val opacity = (baseOpacity * twinkleScale * 255).toInt().coerceIn(20, 255)

            val wobbleOffset = if (adjustedParams.wobble > 0f) {
                sin((timestamp / 1000f) + i) * adjustedParams.wobble * density
            } else {
                0f
            }
            val riseOffset = if (adjustedParams.riseSpeed > 0f) {
                ((timestamp / 1000f) * adjustedParams.riseSpeed + i * 50f) % height
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
        reservedRightSpace: Float = 0f,
        highlightFirstTask: Boolean = false,
        badges: List<String> = emptyList()
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

        if (badges.isNotEmpty()) {
            val badgeHeight = drawBadgeRow(
                canvas = canvas,
                badges = badges,
                colors = colors,
                startX = marginH,
                startY = currentY,
                maxWidth = width - marginH - reservedRightSpace
            )
            if (badgeHeight > 0f) {
                currentY += badgeHeight + layout.margins.vertical * 0.5f
            }
        }

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

            if (highlightFirstTask && index == 0) {
                val glowPaint = Paint().apply {
                    isAntiAlias = true
                    color = colors.taskCircleGlow
                    alpha = 180
                    maskFilter = BlurMaskFilter(circleRadius * 2.4f, BlurMaskFilter.Blur.NORMAL)
                    style = Paint.Style.FILL
                }
                canvas.drawCircle(marginH, circleY, circleRadius * 2.4f, glowPaint)

                val nextPaint = Paint().apply {
                    isAntiAlias = true
                    color = colors.subtitleColor
                    textSize = typography.labelSmall
                    typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                }
                canvas.drawText("NEXT", textStartX, circleY - circleRadius * 1.6f, nextPaint)
            }

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

    private fun drawBadgeRow(
        canvas: Canvas,
        badges: List<String>,
        colors: ThemeColors,
        startX: Float,
        startY: Float,
        maxWidth: Float
    ): Float {
        if (badges.isEmpty()) return 0f

        val textPaint = TextPaint().apply {
            isAntiAlias = true
            color = colors.titleColor
            textSize = max(10f, maxWidth * 0.02f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }

        val paddingH = 12f
        val paddingV = 6f
        val gap = 8f
        val badgeHeight = textPaint.textSize + paddingV * 2f
        var x = startX
        var y = startY
        var maxY = y + badgeHeight

        val bgPaint = Paint().apply {
            isAntiAlias = true
            color = Color.argb(140, 10, 18, 30)
            style = Paint.Style.FILL
        }

        badges.forEach { label ->
            val textWidth = textPaint.measureText(label)
            val badgeWidth = textWidth + paddingH * 2f

            if (x + badgeWidth > startX + maxWidth) {
                x = startX
                y += badgeHeight + gap
            }

            val rect = RectF(x, y, x + badgeWidth, y + badgeHeight)
            canvas.drawRoundRect(rect, badgeHeight / 2f, badgeHeight / 2f, bgPaint)
            val textY = y + badgeHeight - paddingV - 2f
            canvas.drawText(label, x + paddingH, textY, textPaint)

            x += badgeWidth + gap
            maxY = max(maxY, y + badgeHeight)
        }

        return maxY - startY
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
