package com.cosmicocean.integration

import android.content.Context
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cosmicocean.data.PrivacyLevel
import com.cosmicocean.data.PrivacyPreferencesRepository
import com.cosmicocean.data.StarEntity
import com.cosmicocean.utils.applyWallpaperPrivacy
import com.cosmicocean.wallpaper.LocalWallpaperGenerator
import com.cosmicocean.wallpaper.WallpaperTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PrivacyWallpaperScreenshotE2ETest {

    private lateinit var context: Context
    private lateinit var repo: PrivacyPreferencesRepository

    @Before
    fun setup() = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        repo = PrivacyPreferencesRepository(context)
        repo.resetToDefaults()
    }

    @Test
    fun generateWallpaperWithPrivacyInitialsScreenshot() = runBlocking {
        repo.setHideAllTasksMode(false)
        repo.setDefaultPrivacyLevel(PrivacyLevel.INITIALS)

        val tasks = listOf(
            buildStarEntity("Pay rent"),
            buildStarEntity("Book flight"),
            buildStarEntity("Call mom")
        )

        val masked = applyWallpaperPrivacy(context, tasks, totalTaskCount = tasks.size)

        val bitmap = LocalWallpaperGenerator.generate(
            tasks = masked.tasks,
            totalTaskCount = masked.totalTaskCount,
            theme = WallpaperTheme.DEEP_OCEAN,
            width = 1080,
            height = 2400,
            achievementCount = 0,
            streakDays = 0
        )

        val output = writeScreenshot(bitmap, "privacy_wallpaper_initials.png")
        bitmap.recycle()

        assertTrue("Wallpaper screenshot should exist", output.exists())
        assertTrue("Wallpaper screenshot should be non-empty", output.length() > 0L)
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

        val lsOutput = readShellOutput(uiAutomation.executeShellCommand("ls -l $destPath"))
        if (lsOutput.isBlank() || lsOutput.contains("No such file")) {
            throw IllegalStateException("Failed to copy wallpaper screenshot to $destPath")
        }

        return output
    }

    private fun readShellOutput(pfd: android.os.ParcelFileDescriptor): String {
        pfd.use { descriptor ->
            val input: InputStream = android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            return input.bufferedReader().use { it.readText() }
        }
    }

    private fun buildStarEntity(title: String): StarEntity {
        val now = System.currentTimeMillis()
        return StarEntity(
            localId = UUID.randomUUID().toString(),
            serverId = null,
            title = title,
            urgency = 2,
            dueDate = now + 3_600_000,
            x = 120f,
            y = 240f,
            createdAt = now,
            isSubtask = false,
            isRecurring = false,
            echoInterval = null,
            isCompleted = false,
            completedAt = null,
            isArchived = false,
            archivedAt = null
        )
    }
}
