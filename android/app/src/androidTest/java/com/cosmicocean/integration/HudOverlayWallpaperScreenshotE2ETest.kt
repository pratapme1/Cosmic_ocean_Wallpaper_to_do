package com.cosmicocean.integration

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cosmicocean.data.StarEntity
import com.cosmicocean.wallpaper.HudOverlayRenderConfig
import com.cosmicocean.wallpaper.LocalWallpaperGenerator
import com.cosmicocean.wallpaper.WallpaperTheme
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class HudOverlayWallpaperScreenshotE2ETest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun generatedWallpaperDrawsHudOverlayClearOfTaskText() {
        val overlay = createHudOverlay()
        val bitmap = LocalWallpaperGenerator.generate(
            tasks = buildTasks(),
            totalTaskCount = 3,
            theme = WallpaperTheme.COSMIC,
            width = 1080,
            height = 2400,
            hudOverlay = HudOverlayRenderConfig(
                bitmap = overlay,
                verticalPositionPercent = 80,
                opacityPercent = 90
            )
        )

        val output = writeScreenshot(bitmap, "hud_overlay_generated_1080x2400.png")

        assertTrue("HUD overlay screenshot should exist", output.exists())
        assertTrue("HUD overlay should leave visible cyan pixels", containsHudPixels(bitmap))

        bitmap.recycle()
        overlay.recycle()
    }

    @Test
    fun customWallpaperDrawsHudOverlayClearOfTaskText() {
        val overlay = createHudOverlay()
        val customBackground = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(6, 18, 32))
        }
        val bitmap = LocalWallpaperGenerator.generateWithCustomBackground(
            tasks = buildTasks(),
            totalTaskCount = 3,
            customBackground = customBackground,
            width = 1080,
            height = 2400,
            theme = WallpaperTheme.DEEP_OCEAN,
            hudOverlay = HudOverlayRenderConfig(
                bitmap = overlay,
                verticalPositionPercent = 80,
                opacityPercent = 90
            )
        )

        val output = writeScreenshot(bitmap, "hud_overlay_custom_1080x2400.png")

        assertTrue("Custom HUD overlay screenshot should exist", output.exists())
        assertTrue("Custom HUD overlay should leave visible cyan pixels", containsHudPixels(bitmap))

        bitmap.recycle()
        overlay.recycle()
        customBackground.recycle()
    }

    private fun createHudOverlay(): Bitmap {
        val bitmap = Bitmap.createBitmap(992, 203, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val linePaint = Paint().apply {
            isAntiAlias = true
            color = Color.argb(230, 0, 220, 255)
            style = Paint.Style.STROKE
            strokeWidth = 10f
        }
        val fillPaint = Paint().apply {
            isAntiAlias = true
            color = Color.argb(180, 0, 220, 255)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(24f, 28f, 968f, 175f), 32f, 32f, linePaint)
        repeat(6) { index ->
            val cx = 110f + index * 150f
            canvas.drawCircle(cx, 101f, 38f, fillPaint)
            canvas.drawLine(cx - 52f, 101f, cx + 52f, 101f, linePaint)
        }
        return bitmap
    }

    private fun buildTasks(): List<StarEntity> {
        val now = System.currentTimeMillis()
        return listOf(
            buildTask("Review HUD overlay placement", now + 60 * 60_000L, 2),
            buildTask("Confirm reminder text remains readable", now + 2 * 60 * 60_000L, 2),
            buildTask("Replace hud-overlay.png and restart wallpaper", now + 3 * 60 * 60_000L, 1)
        )
    }

    private fun buildTask(title: String, dueDate: Long, urgency: Int): StarEntity {
        val now = System.currentTimeMillis()
        return StarEntity(
            localId = UUID.randomUUID().toString(),
            serverId = null,
            title = title,
            urgency = urgency,
            dueDate = dueDate,
            x = 120f,
            y = 240f,
            createdAt = now,
            isSubtask = false,
            parentId = null,
            isRecurring = false,
            echoInterval = null,
            isCompleted = false,
            completedAt = null,
            isArchived = false,
            archivedAt = null
        )
    }

    private fun containsHudPixels(bitmap: Bitmap): Boolean {
        var count = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val color = bitmap.getPixel(x, y)
                if (Color.green(color) > 150 && Color.blue(color) > 170 && Color.red(color) < 90) {
                    count++
                    if (count > 400) return true
                }
                x += 6
            }
            y += 6
        }
        return false
    }

    private fun writeScreenshot(bitmap: Bitmap, fileName: String): File {
        val dir = context.getExternalFilesDir("screenshots")
            ?: throw IllegalStateException("External files dir unavailable")
        if (!dir.exists() && !dir.mkdirs()) {
            throw IllegalStateException("Failed to create screenshot dir: ${dir.absolutePath}")
        }
        val output = File(dir, fileName)
        FileOutputStream(output).use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                throw IllegalStateException("Failed to compress HUD overlay screenshot")
            }
        }

        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val destDir = "/data/local/tmp/AndroidTestScreenshots/wallpaper"
        val destPath = "$destDir/$fileName"
        uiAutomation.executeShellCommand("mkdir -p $destDir").close()
        uiAutomation.executeShellCommand("cp ${output.absolutePath} $destPath").close()

        var lsOutput = readShellOutput(uiAutomation.executeShellCommand("ls -l $destPath"))
        if (lsOutput.isBlank()) {
            Thread.sleep(200)
            lsOutput = readShellOutput(uiAutomation.executeShellCommand("ls -l $destPath"))
        }
        if (lsOutput.isBlank() || lsOutput.contains("No such file")) {
            throw IllegalStateException("Failed to copy HUD overlay screenshot to $destPath (ls: '$lsOutput')")
        }

        return output
    }

    private fun readShellOutput(pfd: android.os.ParcelFileDescriptor): String {
        pfd.use { descriptor ->
            val input: InputStream = android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            return input.bufferedReader().use { it.readText() }
        }
    }
}
