package com.cosmicocean.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.DecimalFormat
import kotlin.math.roundToInt

/**
 * Performance Monitor - Debug overlay showing FPS and memory usage
 * Only visible in debug builds
 */
@Composable
fun PerformanceMonitor(
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (!enabled) return

    var fps by remember { mutableIntStateOf(0) }
    var memoryUsed by remember { mutableFloatStateOf(0f) }
    var memoryMax by remember { mutableFloatStateOf(0f) }
    var frameCount by remember { mutableIntStateOf(0) }
    var lastFrameTime by remember { mutableLongStateOf(System.nanoTime()) }

    val performanceTracker = remember { PerformanceTracker() }

    LaunchedEffect(Unit) {
        while (true) {
            // Update FPS
            frameCount++
            val currentTime = System.nanoTime()
            val delta = (currentTime - lastFrameTime) / 1_000_000_000.0

            if (delta >= 1.0) {
                fps = frameCount
                frameCount = 0
                lastFrameTime = currentTime
            }

            // Update memory stats
            val memStats = performanceTracker.getMemoryStats()
            memoryUsed = memStats.usedMB
            memoryMax = memStats.maxMB

            delay(16) // ~60fps update rate
        }
    }

    Box(
        modifier = modifier
            .padding(8.dp)
            .background(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // FPS counter
            PerformanceMetric(
                label = "FPS",
                value = fps.toString(),
                color = getFpsColor(fps)
            )

            // Memory usage
            val memoryPercent = if (memoryMax > 0f && memoryUsed.isFinite() && memoryMax.isFinite()) {
                val percent = ((memoryUsed / memoryMax) * 100)
                if (percent.isFinite()) percent.roundToInt() else 0
            } else {
                0
            }
            PerformanceMetric(
                label = "MEM",
                value = "${memoryUsed.toInt()}/${memoryMax.toInt()}MB ($memoryPercent%)",
                color = getMemoryColor(memoryPercent)
            )
        }
    }
}

/**
 * Individual performance metric display
 */
@Composable
private fun PerformanceMetric(
    label: String,
    value: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Color.White.copy(alpha = 0.7f)
        )

        Text(
            text = value,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = color
        )
    }
}

/**
 * Get color based on FPS value
 */
private fun getFpsColor(fps: Int): Color {
    return when {
        fps >= 55 -> Color(0xFF00FF88) // Green - Good
        fps >= 45 -> Color(0xFFFFD700) // Yellow - OK
        fps >= 30 -> Color(0xFFFF9A3A) // Orange - Warning
        else -> Color(0xFFFF3B30) // Red - Poor
    }
}

/**
 * Get color based on memory usage percentage
 */
private fun getMemoryColor(percent: Int): Color {
    return when {
        percent < 60 -> Color(0xFF00FF88) // Green - Good
        percent < 75 -> Color(0xFFFFD700) // Yellow - OK
        percent < 90 -> Color(0xFFFF9A3A) // Orange - Warning
        else -> Color(0xFFFF3B30) // Red - Critical
    }
}

/**
 * Performance tracking utility
 */
class PerformanceTracker {

    private val runtime = Runtime.getRuntime()
    private val formatter = DecimalFormat("#.##")

    /**
     * Get current memory statistics
     */
    fun getMemoryStats(): MemoryStats {
        val maxMemory = runtime.maxMemory()
        val allocatedMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = allocatedMemory - freeMemory

        return MemoryStats(
            usedMB = (usedMemory / (1024 * 1024)).toFloat(),
            allocatedMB = (allocatedMemory / (1024 * 1024)).toFloat(),
            maxMB = (maxMemory / (1024 * 1024)).toFloat(),
            freeMB = (freeMemory / (1024 * 1024)).toFloat()
        )
    }

    /**
     * Get FPS statistics over a time window
     */
    fun calculateFPS(frameTimes: List<Long>): Double {
        if (frameTimes.size < 2) return 0.0

        val totalTime = (frameTimes.last() - frameTimes.first()) / 1_000_000_000.0
        return if (totalTime > 0) frameTimes.size / totalTime else 0.0
    }

    /**
     * Check if performance is acceptable
     */
    fun isPerformanceGood(fps: Int, memoryPercent: Int): Boolean {
        return fps >= 45 && memoryPercent < 85
    }
}

/**
 * Memory statistics data class
 */
data class MemoryStats(
    val usedMB: Float,
    val allocatedMB: Float,
    val maxMB: Float,
    val freeMB: Float
)

/**
 * Extended performance monitor with detailed stats
 */
@Composable
fun DetailedPerformanceMonitor(
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (!enabled) return

    var fps by remember { mutableIntStateOf(0) }
    var avgFrameTime by remember { mutableFloatStateOf(0f) }
    var memoryStats by remember { mutableStateOf(MemoryStats(0f, 0f, 0f, 0f)) }

    val performanceTracker = remember { PerformanceTracker() }
    val frameTimes = remember { mutableListOf<Long>() }
    var lastFrameNano by remember { mutableLongStateOf(System.nanoTime()) }

    LaunchedEffect(Unit) {
        while (true) {
            val currentNano = System.nanoTime()
            val frameDelta = currentNano - lastFrameNano
            lastFrameNano = currentNano

            frameTimes.add(currentNano)

            // Keep only last 60 frames
            if (frameTimes.size > 60) {
                frameTimes.removeAt(0)
            }

            // Calculate FPS from frame times
            if (frameTimes.size >= 2) {
                fps = performanceTracker.calculateFPS(frameTimes).roundToInt()
                avgFrameTime = (frameDelta / 1_000_000f) // Convert to ms
            }

            // Update memory stats
            memoryStats = performanceTracker.getMemoryStats()

            delay(16)
        }
    }

    Box(
        modifier = modifier
            .padding(8.dp)
            .background(
                color = Color.Black.copy(alpha = 0.8f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Header
            Text(
                text = "PERFORMANCE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF3AA0FF)
            )

            // FPS
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "FPS:",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Text(
                    text = fps.toString(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = getFpsColor(fps)
                )
            }

            // Frame time
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Frame:",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Text(
                    text = "${avgFrameTime.roundToInt()}ms",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White
                )
            }

            // Memory used
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Memory:",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White.copy(alpha = 0.7f)
                )
                val memPercent = if (memoryStats.maxMB > 0f) {
                    ((memoryStats.usedMB / memoryStats.maxMB) * 100).roundToInt()
                } else {
                    0
                }
                Text(
                    text = "${memoryStats.usedMB.toInt()}MB ($memPercent%)",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = getMemoryColor(memPercent)
                )
            }

            // Max memory
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Max Heap:",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Text(
                    text = "${memoryStats.maxMB.toInt()}MB",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White
                )
            }
        }
    }
}
