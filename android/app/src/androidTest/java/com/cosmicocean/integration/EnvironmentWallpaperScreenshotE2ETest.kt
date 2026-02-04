package com.cosmicocean.integration

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cosmicocean.data.StarEntity
import com.cosmicocean.ui.state.EnvironmentPreferences
import com.cosmicocean.ui.state.ParticleIntensity
import com.cosmicocean.ui.state.TimeOfDayMode
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
class EnvironmentWallpaperScreenshotE2ETest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun generateEnvironmentWallpaperStormScreenshot() {
        val prefs = EnvironmentPreferences(
            timeOfDayMode = TimeOfDayMode.MANUAL,
            manualTimePeriod = "night",
            weatherOverlayEnabled = true,
            particleIntensity = ParticleIntensity.HIGH,
            wallpaperMode = "generated",
            isWallpaperEnabled = true
        )

        val tasks = buildStormTasks()
        val activeCount = tasks.count { !it.isCompleted }

        val bitmap = LocalWallpaperGenerator.generate(
            tasks = tasks.filter { !it.isCompleted }.take(3),
            totalTaskCount = activeCount,
            theme = WallpaperTheme.COSMIC,
            width = 1080,
            height = 2400,
            achievementCount = 0,
            streakDays = 0,
            environmentPreferences = prefs,
            weatherTasks = tasks
        )

        val output = writeScreenshot(bitmap, "environment_wallpaper_night_storm.png")
        bitmap.recycle()

        assertTrue("Wallpaper screenshot should exist", output.exists())
        assertTrue("Wallpaper screenshot should be non-empty", output.length() > 0L)
    }

    @Test
    fun generateEnvironmentCustomWallpaperScreenshot() {
        val prefs = EnvironmentPreferences(
            timeOfDayMode = TimeOfDayMode.MANUAL,
            manualTimePeriod = "evening",
            weatherOverlayEnabled = true,
            particleIntensity = ParticleIntensity.MEDIUM,
            wallpaperMode = "custom",
            isWallpaperEnabled = true
        )

        val tasks = buildStormTasks()
        val activeCount = tasks.count { !it.isCompleted }

        val customBackground = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.parseColor("#0B1F3A"))
        }

        val bitmap = LocalWallpaperGenerator.generateWithCustomBackground(
            tasks = tasks.filter { !it.isCompleted }.take(3),
            totalTaskCount = activeCount,
            customBackground = customBackground,
            width = 1080,
            height = 2400,
            achievementCount = 0,
            streakDays = 0,
            theme = WallpaperTheme.DEEP_OCEAN,
            environmentPreferences = prefs,
            weatherTasks = tasks
        )

        val output = writeScreenshot(bitmap, "environment_custom_wallpaper_evening.png")
        bitmap.recycle()
        customBackground.recycle()

        assertTrue("Custom wallpaper screenshot should exist", output.exists())
        assertTrue("Custom wallpaper screenshot should be non-empty", output.length() > 0L)
    }

    @Test
    fun generateEnvironmentWallpaperDisabledScreenshot() {
        val prefs = EnvironmentPreferences(
            environmentEnabled = false,
            timeOfDayMode = TimeOfDayMode.MANUAL,
            manualTimePeriod = "night",
            weatherOverlayEnabled = true,
            particleIntensity = ParticleIntensity.HIGH,
            wallpaperMode = "generated",
            isWallpaperEnabled = true
        )

        val tasks = buildStormTasks()
        val activeCount = tasks.count { !it.isCompleted }

        val bitmap = LocalWallpaperGenerator.generate(
            tasks = tasks.filter { !it.isCompleted }.take(3),
            totalTaskCount = activeCount,
            theme = WallpaperTheme.COSMIC,
            width = 1080,
            height = 2400,
            achievementCount = 0,
            streakDays = 0,
            environmentPreferences = prefs,
            weatherTasks = tasks
        )

        val output = writeScreenshot(bitmap, "environment_wallpaper_night_storm_env_off.png")
        bitmap.recycle()

        assertTrue("Wallpaper screenshot should exist", output.exists())
        assertTrue("Wallpaper screenshot should be non-empty", output.length() > 0L)
    }

    @Test
    fun generateEnvironmentCustomWallpaperDisabledScreenshot() {
        val prefs = EnvironmentPreferences(
            environmentEnabled = false,
            timeOfDayMode = TimeOfDayMode.MANUAL,
            manualTimePeriod = "evening",
            weatherOverlayEnabled = true,
            particleIntensity = ParticleIntensity.MEDIUM,
            wallpaperMode = "custom",
            isWallpaperEnabled = true
        )

        val tasks = buildStormTasks()
        val activeCount = tasks.count { !it.isCompleted }

        val customBackground = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.parseColor("#0B1F3A"))
        }

        val bitmap = LocalWallpaperGenerator.generateWithCustomBackground(
            tasks = tasks.filter { !it.isCompleted }.take(3),
            totalTaskCount = activeCount,
            customBackground = customBackground,
            width = 1080,
            height = 2400,
            achievementCount = 0,
            streakDays = 0,
            theme = WallpaperTheme.DEEP_OCEAN,
            environmentPreferences = prefs,
            weatherTasks = tasks
        )

        val output = writeScreenshot(bitmap, "environment_custom_wallpaper_evening_env_off.png")
        bitmap.recycle()
        customBackground.recycle()

        assertTrue("Custom wallpaper screenshot should exist", output.exists())
        assertTrue("Custom wallpaper screenshot should be non-empty", output.length() > 0L)
    }

    private fun buildStormTasks(): List<StarEntity> {
        val now = System.currentTimeMillis()
        return listOf(
            buildStarEntity("Overdue critical", now - 48 * 3600_000L, urgency = 3, completed = false),
            buildStarEntity("Overdue task", now - 6 * 3600_000L, urgency = 2, completed = false),
            buildStarEntity("Upcoming task", now + 4 * 3600_000L, urgency = 2, completed = false),
            buildStarEntity("Completed task", now - 2 * 3600_000L, urgency = 1, completed = true)
        )
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
                throw IllegalStateException("Failed to compress wallpaper screenshot")
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
            throw IllegalStateException("Failed to copy wallpaper screenshot to $destPath (ls: '$lsOutput')")
        }

        return output
    }

    private fun readShellOutput(pfd: android.os.ParcelFileDescriptor): String {
        pfd.use { descriptor ->
            val input: InputStream = android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            return input.bufferedReader().use { it.readText() }
        }
    }

    private fun buildStarEntity(
        title: String,
        dueDate: Long?,
        urgency: Int,
        completed: Boolean
    ): StarEntity {
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
            isRecurring = false,
            echoInterval = null,
            isCompleted = completed,
            completedAt = if (completed) now else null,
            isArchived = false,
            archivedAt = null
        )
    }
}
