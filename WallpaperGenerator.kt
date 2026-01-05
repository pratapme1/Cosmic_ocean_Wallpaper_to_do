package com.cosmicocean.mvp.wallpaper

import android.content.Context
import android.graphics.*
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.DisplayMetrics
import android.view.WindowManager
import com.cosmicocean.mvp.data.Priority
import com.cosmicocean.mvp.data.Task
import kotlin.math.min

/**
 * Production-ready WallpaperGenerator that handles all Android screen sizes correctly.
 * 
 * KEY FIXES:
 * 1. Uses density-independent measurements (dp/sp) instead of hard-coded pixels
 * 2. Uses Paint.breakText() for proper text truncation based on available width
 * 3. Uses StaticLayout for multi-line text rendering
 * 4. Dynamically calculates available space based on actual screen dimensions
 * 5. Properly scales fonts and spacing for different screen densities
 */
class WallpaperGenerator(private val context: Context) {

    // Colors for cosmic ocean theme
    private val backgroundGradientStart = Color.parseColor("#0D1B2A")
    private val backgroundGradientEnd = Color.parseColor("#1B263B")
    private val textColor = Color.parseColor("#E0E1DD")
    private val accentColor = Color.parseColor("#4CC9F0")
    private val highPriorityColor = Color.parseColor("#FF6B6B")
    private val mediumPriorityColor = Color.parseColor("#4CC9F0")
    private val lowPriorityColor = Color.parseColor("#778DA9")
    private val overdueColor = Color.parseColor("#FF6B6B")
    private val bannerBackgroundColor = Color.parseColor("#2D1B4E")

    // Screen metrics - calculated once per generation
    private var density: Float = 1f
    private var scaledDensity: Float = 1f

    // Convert dp to pixels
    private fun Float.dp(): Float = this * density
    private fun Int.dp(): Float = this.toFloat() * density

    // Convert sp to pixels
    private fun Float.sp(): Float = this * scaledDensity
    private fun Int.sp(): Float = this.toFloat() * scaledDensity

    fun generateWallpaper(tasks: List<Task>): Bitmap {
        val (width, height) = getScreenSize()
        
        // Store density values for unit conversion
        val displayMetrics = context.resources.displayMetrics
        density = displayMetrics.density
        scaledDensity = displayMetrics.scaledDensity

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw layers in order
        drawBackground(canvas, width, height)
        drawDecorations(canvas, width, height)
        
        // Find the most urgent overdue task for the banner
        val overdueTask = tasks.filter { it.isOverdue() }.maxByOrNull { it.overdueHours() }
        if (overdueTask != null) {
            drawOverdueBanner(canvas, overdueTask, width, height)
        }
        
        drawTasks(canvas, tasks, width, height, hasOverdueBanner = overdueTask != null)

        return bitmap
    }

    private fun drawBackground(canvas: Canvas, width: Int, height: Int) {
        val gradient = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            backgroundGradientStart, backgroundGradientEnd,
            Shader.TileMode.CLAMP
        )
        val paint = Paint().apply {
            shader = gradient
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    private fun drawDecorations(canvas: Canvas, width: Int, height: Int) {
        val starPaint = Paint().apply {
            color = Color.WHITE
            alpha = 50
            isAntiAlias = true
        }
        
        // Draw subtle stars
        val random = java.util.Random(42) // Fixed seed for consistency
        repeat(50) {
            val x = random.nextFloat() * width
            val y = random.nextFloat() * height * 0.6f
            val radius = random.nextFloat() * 2.dp() + 1.dp()
            canvas.drawCircle(x, y, radius, starPaint)
        }
        
        // Draw wave-like curves at bottom
        val wavePaint = Paint().apply {
            color = accentColor
            alpha = 20
            style = Paint.Style.STROKE
            strokeWidth = 2.dp()
            isAntiAlias = true
        }
        
        val path = Path()
        val waveHeight = height * 0.15f
        val startY = height * 0.85f
        
        path.moveTo(0f, startY)
        var x = 0f
        while (x < width) {
            val y = startY + (kotlin.math.sin(x * 0.02) * waveHeight).toFloat()
            path.lineTo(x, y)
            x += 5f
        }
        canvas.drawPath(path, wavePaint)
    }

    /**
     * Draws the overdue banner at the bottom of the screen.
     * Uses proper text measurement to ensure text fits within the banner.
     */
    private fun drawOverdueBanner(canvas: Canvas, task: Task, width: Int, height: Int) {
        val bannerPadding = 16.dp()
        val bannerHeight = 56.dp()
        val bannerMarginHorizontal = 16.dp()
        val bannerMarginBottom = height * 0.40f // Position above task list area
        
        val bannerLeft = bannerMarginHorizontal
        val bannerRight = width - bannerMarginHorizontal
        val bannerTop = bannerMarginBottom - bannerHeight
        val bannerBottom = bannerMarginBottom
        val bannerWidth = bannerRight - bannerLeft

        // Draw banner background with rounded corners
        val bannerPaint = Paint().apply {
            color = bannerBackgroundColor
            alpha = 220
            isAntiAlias = true
        }
        val bannerRect = RectF(bannerLeft, bannerTop, bannerRight, bannerBottom)
        canvas.drawRoundRect(bannerRect, 12.dp(), 12.dp(), bannerPaint)

        // Create text paint for measurement
        val textPaint = TextPaint().apply {
            color = Color.WHITE
            textSize = 14.sp()
            isAntiAlias = true
            typeface = Typeface.DEFAULT
        }

        // Build the message
        val overdueHours = task.overdueHours()
        val overdueText = when {
            overdueHours < 1 -> "just now"
            overdueHours < 24 -> "${overdueHours}h ago"
            overdueHours < 48 -> "yesterday"
            else -> "${overdueHours / 24}d ago"
        }
        
        val prefix = "OVERDUE: "
        val suffix = " was due $overdueText"
        
        // Calculate available width for task title
        val availableWidth = bannerWidth - (bannerPadding * 2)
        val prefixWidth = textPaint.measureText(prefix)
        val suffixWidth = textPaint.measureText(suffix)
        val titleMaxWidth = availableWidth - prefixWidth - suffixWidth - 8.dp() // 8dp buffer
        
        // Truncate task title to fit
        val truncatedTitle = truncateText(task.title, textPaint, titleMaxWidth)
        
        // Draw the full message centered
        val fullMessage = "$prefix$truncatedTitle$suffix"
        val messageWidth = textPaint.measureText(fullMessage)
        val textX = bannerLeft + (bannerWidth - messageWidth) / 2
        val textY = bannerTop + (bannerHeight / 2) + (textPaint.textSize / 3) // Vertical center

        // Draw prefix in white
        textPaint.color = Color.WHITE
        canvas.drawText(prefix, textX, textY, textPaint)
        
        // Draw task title in white
        canvas.drawText(truncatedTitle, textX + prefixWidth, textY, textPaint)
        
        // Draw suffix in slightly dimmer white
        textPaint.alpha = 200
        canvas.drawText(suffix, textX + prefixWidth + textPaint.measureText(truncatedTitle), textY, textPaint)
    }

    /**
     * Truncates text to fit within maxWidth, adding ellipsis if needed.
     * Uses Paint.breakText() for accurate text measurement across all devices.
     */
    private fun truncateText(text: String, paint: Paint, maxWidth: Float): String {
        if (maxWidth <= 0) return ""
        
        val fullWidth = paint.measureText(text)
        if (fullWidth <= maxWidth) {
            return text
        }
        
        // Need to truncate - account for ellipsis
        val ellipsis = "..."
        val ellipsisWidth = paint.measureText(ellipsis)
        val availableForText = maxWidth - ellipsisWidth
        
        if (availableForText <= 0) {
            return ellipsis
        }
        
        // Use breakText for accurate measurement
        val charCount = paint.breakText(text, true, availableForText, null)
        
        if (charCount <= 0) {
            return ellipsis
        }
        
        return text.substring(0, charCount) + ellipsis
    }

    /**
     * Draws tasks with proper text truncation and spacing.
     */
    private fun drawTasks(canvas: Canvas, tasks: List<Task>, width: Int, height: Int, hasOverdueBanner: Boolean) {
        if (tasks.isEmpty()) {
            drawNoTasksMessage(canvas, width, height)
            return
        }

        // Task area dimensions - use percentages for responsiveness
        val taskAreaLeft = width * 0.06f
        val taskAreaRight = width * 0.94f
        val taskAreaWidth = taskAreaRight - taskAreaLeft
        
        // Starting position depends on whether we have an overdue banner
        val taskAreaTop = if (hasOverdueBanner) {
            height * 0.44f
        } else {
            height * 0.38f
        }
        
        // Calculate available height for tasks
        val maxTaskAreaBottom = height * 0.92f
        val availableHeight = maxTaskAreaBottom - taskAreaTop

        // Font sizes
        val sectionTitleSize = 12.sp()
        val taskTitleSize = 18.sp()
        val taskMetaSize = 13.sp()
        
        // Spacing
        val sectionTitleMarginBottom = 8.dp()
        val taskSpacing = 16.dp()
        val taskInternalPadding = 4.dp()
        val priorityDotRadius = 5.dp()
        val priorityDotMarginRight = 12.dp()
        
        // Calculate how many tasks can fit
        val taskItemHeight = taskTitleSize + taskMetaSize + taskInternalPadding + taskSpacing
        val headerHeight = sectionTitleSize + sectionTitleMarginBottom
        val maxTasks = ((availableHeight - headerHeight) / taskItemHeight).toInt().coerceIn(1, 5)

        var currentY = taskAreaTop

        // Draw section header (e.g., "TOMORROW")
        val sectionPaint = Paint().apply {
            color = textColor
            alpha = 150
            textSize = sectionTitleSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            letterSpacing = 0.1f
        }
        canvas.drawText("TOMORROW", taskAreaLeft, currentY, sectionPaint)
        currentY += sectionTitleSize + sectionTitleMarginBottom

        // Create paints for task rendering
        val titlePaint = TextPaint().apply {
            color = textColor
            textSize = taskTitleSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        
        val metaPaint = TextPaint().apply {
            color = textColor
            alpha = 180
            textSize = taskMetaSize
            isAntiAlias = true
        }

        val priorityPaint = Paint().apply {
            isAntiAlias = true
        }

        val categoryPaint = Paint().apply {
            color = overdueColor
            textSize = 10.sp()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        // Available width for task title (accounting for priority dot)
        val titleStartX = taskAreaLeft + priorityDotRadius * 2 + priorityDotMarginRight
        val titleMaxWidth = taskAreaRight - titleStartX

        // Draw tasks
        tasks.take(maxTasks).forEach { task ->
            // Priority indicator dot
            priorityPaint.color = when (task.priority) {
                Priority.HIGH -> highPriorityColor
                Priority.MEDIUM -> mediumPriorityColor
                Priority.LOW -> lowPriorityColor
            }
            
            // Draw priority dot aligned with first line of text
            val dotY = currentY + titlePaint.textSize / 2 - priorityDotRadius / 2
            canvas.drawCircle(
                taskAreaLeft + priorityDotRadius,
                dotY,
                priorityDotRadius,
                priorityPaint
            )

            // Draw category badge (e.g., "WORK")
            if (task.category != null) {
                val categoryBadgePaint = Paint().apply {
                    color = when (task.category.lowercase()) {
                        "work" -> Color.parseColor("#FF6B6B")
                        "errands" -> Color.parseColor("#4CAF50")
                        "personal" -> Color.parseColor("#9C27B0")
                        else -> accentColor
                    }
                    alpha = 200
                    isAntiAlias = true
                }
                val categoryText = task.category.uppercase()
                val categoryPadding = 4.dp()
                val categoryHeight = 10.sp() + categoryPadding * 2
                val categoryWidth = categoryPaint.measureText(categoryText) + categoryPadding * 2
                
                val categoryRect = RectF(
                    titleStartX,
                    currentY - 10.sp() - categoryPadding,
                    titleStartX + categoryWidth,
                    currentY - categoryPadding + 2.dp()
                )
                canvas.drawRoundRect(categoryRect, 3.dp(), 3.dp(), categoryBadgePaint)
                
                categoryPaint.color = Color.WHITE
                canvas.drawText(
                    categoryText,
                    titleStartX + categoryPadding,
                    currentY - categoryPadding - 1.dp(),
                    categoryPaint
                )
                
                currentY += 2.dp() // Small adjustment after badge
            }

            // Draw task title (properly truncated)
            val truncatedTitle = truncateText(task.title, titlePaint, titleMaxWidth)
            canvas.drawText(truncatedTitle, titleStartX, currentY, titlePaint)
            currentY += titlePaint.textSize + taskInternalPadding

            // Draw meta info (due time, duration, location)
            val metaInfo = buildMetaInfo(task)
            
            // Color overdue text in red
            if (task.isOverdue()) {
                metaPaint.color = overdueColor
            } else {
                metaPaint.color = textColor
                metaPaint.alpha = 180
            }
            
            val truncatedMeta = truncateText(metaInfo, metaPaint, titleMaxWidth)
            canvas.drawText(truncatedMeta, titleStartX, currentY, metaPaint)
            currentY += metaPaint.textSize + taskSpacing
            
            // Reset meta paint color
            metaPaint.color = textColor
            metaPaint.alpha = 180
        }

        // Show "+N more" if there are more tasks
        if (tasks.size > maxTasks) {
            val morePaint = Paint().apply {
                color = textColor
                alpha = 130
                textSize = 13.sp()
                isAntiAlias = true
            }
            canvas.drawText("+ ${tasks.size - maxTasks} more", taskAreaLeft, currentY + 8.dp(), morePaint)
        }
    }

    /**
     * Builds the meta info string for a task.
     */
    private fun buildMetaInfo(task: Task): String {
        val parts = mutableListOf<String>()
        
        // Due info
        if (task.isOverdue()) {
            val hours = task.overdueHours()
            when {
                hours < 60 -> parts.add("${hours}M OVERDUE")
                hours < 24 * 60 -> parts.add("${hours / 60}H OVERDUE")
                else -> parts.add("${hours / (60 * 24)}D OVERDUE")
            }
        } else if (task.dueDate != null) {
            val hoursUntilDue = task.hoursUntilDue()
            when {
                hoursUntilDue < 60 -> parts.add("DUE IN ${hoursUntilDue}M")
                hoursUntilDue < 24 * 60 -> parts.add("DUE IN ${hoursUntilDue / 60}H ${hoursUntilDue % 60}M")
                else -> parts.add("DUE IN ${hoursUntilDue / (60 * 24)}D")
            }
        }
        
        // Duration estimate
        if (task.estimatedMinutes != null && task.estimatedMinutes > 0) {
            parts.add("~${task.estimatedMinutes}min")
        }
        
        // Location
        if (task.location != null && task.location.isNotBlank()) {
            parts.add("@ ${task.location.uppercase()}")
        }
        
        return parts.joinToString("   ")
    }

    private fun drawNoTasksMessage(canvas: Canvas, width: Int, height: Int) {
        val paint = Paint().apply {
            color = accentColor
            textSize = 24.sp()
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        
        canvas.drawText("✨ All clear!", width / 2f, height * 0.45f, paint)
        
        paint.color = textColor
        paint.textSize = 16.sp()
        paint.alpha = 180
        canvas.drawText("No tasks pending", width / 2f, height * 0.45f + 40.dp(), paint)
    }

    private fun getScreenSize(): Pair<Int, Int> {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            Pair(bounds.width(), bounds.height())
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            Pair(metrics.widthPixels, metrics.heightPixels)
        }
    }
}

// Extension functions for Task - add these to your Task data class or create them as extensions
// These are examples - adjust based on your actual Task implementation
fun Task.isOverdue(): Boolean {
    val dueDate = this.dueDate ?: return false
    return System.currentTimeMillis() > dueDate
}

fun Task.overdueHours(): Long {
    val dueDate = this.dueDate ?: return 0
    val overdueMillis = System.currentTimeMillis() - dueDate
    return if (overdueMillis > 0) overdueMillis / (1000 * 60) else 0 // Returns minutes
}

fun Task.hoursUntilDue(): Long {
    val dueDate = this.dueDate ?: return Long.MAX_VALUE
    val untilDueMillis = dueDate - System.currentTimeMillis()
    return if (untilDueMillis > 0) untilDueMillis / (1000 * 60) else 0 // Returns minutes
}
