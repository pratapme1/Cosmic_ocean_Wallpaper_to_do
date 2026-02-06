package com.cosmicocean.integration

import android.content.Context
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cosmicocean.data.StarEntity
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
class WallpaperScenarioScreenshotE2ETest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun generateMultiSizeWallpaperScenarios() {
        val sizes = listOf(
            Pair(720, 1280),
            Pair(1080, 2400),
            Pair(1440, 3120)
        )

        val emptyTasks = emptyList<StarEntity>()
        val fiveTasks = buildTaskList(5)

        sizes.forEach { (width, height) ->
            val empty = LocalWallpaperGenerator.generate(
                tasks = emptyTasks,
                totalTaskCount = 0,
                theme = WallpaperTheme.COSMIC,
                width = width,
                height = height
            )
            writeScreenshot(empty, "scenario_empty_${width}x${height}.png")
            empty.recycle()

            val five = LocalWallpaperGenerator.generate(
                tasks = fiveTasks.take(3),
                totalTaskCount = fiveTasks.size,
                theme = WallpaperTheme.DEEP_OCEAN,
                width = width,
                height = height
            )
            writeScreenshot(five, "scenario_five_tasks_${width}x${height}.png")
            five.recycle()
        }
    }

    @Test
    fun generateLongAndRtlTextScenarios() {
        val longTitle = "Finalize quarterly roadmap and sync with product, design, and engineering stakeholders"
        val rtlTitle = "إنهاء التقرير النهائي قبل الاجتماع"
        val tasks = listOf(
            buildStarEntity(longTitle, dueOffsetMs = 3_600_000),
            buildStarEntity(rtlTitle, dueOffsetMs = 7_200_000),
            buildStarEntity("Follow up with legal about compliance requirements", dueOffsetMs = 18_000_000)
        )

        val sizes = listOf(
            Pair(720, 1280),
            Pair(1080, 2400),
            Pair(1440, 3120)
        )

        sizes.forEach { (width, height) ->
            val bitmap = LocalWallpaperGenerator.generate(
                tasks = tasks,
                totalTaskCount = tasks.size,
                theme = WallpaperTheme.FOREST,
                width = width,
                height = height
            )
            writeScreenshot(bitmap, "scenario_long_rtl_${width}x${height}.png")
            bitmap.recycle()
        }
    }

    private fun buildTaskList(count: Int): List<StarEntity> {
        val now = System.currentTimeMillis()
        return (1..count).map { idx ->
            StarEntity(
                localId = UUID.randomUUID().toString(),
                serverId = null,
                title = "Task $idx",
                urgency = (idx % 3) + 1,
                dueDate = now + idx * 3_600_000L,
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

    private fun buildStarEntity(title: String, dueOffsetMs: Long): StarEntity {
        val now = System.currentTimeMillis()
        return StarEntity(
            localId = UUID.randomUUID().toString(),
            serverId = null,
            title = title,
            urgency = 2,
            dueDate = now + dueOffsetMs,
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
        assertTrue("Wallpaper screenshot should exist at $destPath", lsOutput.isNotBlank() && !lsOutput.contains("No such file"))

        return output
    }

    private fun readShellOutput(pfd: android.os.ParcelFileDescriptor): String {
        pfd.use { descriptor ->
            val input: InputStream = android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            return input.bufferedReader().use { it.readText() }
        }
    }
}
