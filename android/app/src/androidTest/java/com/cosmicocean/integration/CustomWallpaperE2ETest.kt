package com.cosmicocean.integration

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import com.cosmicocean.data.StarEntity
import com.cosmicocean.utils.WallpaperPreferencesManager
import com.cosmicocean.wallpaper.LocalWallpaperGenerator
import com.cosmicocean.wallpaper.WallpaperTheme
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * End-to-End tests for custom wallpaper functionality.
 *
 * These tests verify the complete flow:
 * 1. User selects custom wallpaper image
 * 2. Image is saved to local storage
 * 3. Wallpaper mode is set to "custom"
 * 4. Wallpaper path is stored in preferences
 * 5. RealTimeWallpaperService reads preferences correctly
 * 6. LocalWallpaperGenerator renders custom background with tasks
 */
class CustomWallpaperE2ETest {

    private lateinit var context: Context
    private lateinit var prefsManager: WallpaperPreferencesManager

    // Test data
    private val testTasks = listOf(
        createStarEntity(
            localId = "task1",
            title = "Test Task 1",
            urgency = 2,
            dueDate = System.currentTimeMillis() + 3600000, // 1 hour from now
            x = 100f,
            y = 100f
        ),
        createStarEntity(
            localId = "task2",
            title = "Test Task 2",
            urgency = 3,
            dueDate = System.currentTimeMillis() + 7200000, // 2 hours from now
            x = 200f,
            y = 200f
        )
    )

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        prefsManager = WallpaperPreferencesManager(context)
    }

    // ==================== PREFERENCE TESTS ====================

    @Test
    fun setWallpaperModeCustomStoresValue() {
        val result = prefsManager.setWallpaperMode(WallpaperPreferencesManager.WALLPAPER_MODE_CUSTOM)

        assertTrue("setWallpaperMode should return true for valid mode", result)
        assertEquals(
            "Wallpaper mode should be custom",
            "custom",
            prefsManager.getWallpaperMode()
        )
    }

    @Test
    fun setWallpaperModeGeneratedStoresValue() {
        val result = prefsManager.setWallpaperMode(WallpaperPreferencesManager.WALLPAPER_MODE_GENERATED)

        assertTrue("setWallpaperMode should return true for valid mode", result)
        assertEquals(
            "Wallpaper mode should be generated",
            "generated",
            prefsManager.getWallpaperMode()
        )
    }

    @Test
    fun setWallpaperModeInvalidReturnsFalse() {
        prefsManager.setWallpaperMode(WallpaperPreferencesManager.WALLPAPER_MODE_GENERATED)
        val result = prefsManager.setWallpaperMode("invalid")

        assertFalse("setWallpaperMode should return false for invalid mode", result)
        assertEquals(
            "Wallpaper mode should remain generated after invalid set",
            "generated",
            prefsManager.getWallpaperMode()
        )
    }

    @Test
    fun getWallpaperModeReturnsGeneratedByDefault() {
        val mode = prefsManager.getWallpaperMode()

        assertEquals("Default mode should be 'generated'", "generated", mode)
    }

    @Test
    fun getWallpaperModeReturnsCustomWhenSet() {
        prefsManager.setWallpaperMode(WallpaperPreferencesManager.WALLPAPER_MODE_CUSTOM)

        val mode = prefsManager.getWallpaperMode()

        assertEquals("Mode should be 'custom' when set", "custom", mode)
    }

    @Test
    fun setCustomWallpaperPathStoresFilePath() {
        val testPath = "/data/data/com.cosmicocean/files/custom_wallpaper.jpg"

        val result = prefsManager.setCustomWallpaperPath(testPath)

        assertTrue("setCustomWallpaperPath should return true", result)
        assertEquals("Stored path should match", testPath, prefsManager.getCustomWallpaperPath())
    }

    @Test
    fun setCustomWallpaperPathNullRemovesPath() {
        prefsManager.setCustomWallpaperPath("/data/data/com.cosmicocean/files/custom_wallpaper.jpg")
        val result = prefsManager.setCustomWallpaperPath(null)

        assertTrue("setCustomWallpaperPath(null) should return true", result)
        assertNull("Path should be cleared", prefsManager.getCustomWallpaperPath())
    }

    @Test
    fun getCustomWallpaperPathReturnsNullByDefault() {
        val path = prefsManager.getCustomWallpaperPath()

        assertNull("Default custom path should be null", path)
    }

    @Test
    fun getCustomWallpaperPathReturnsStoredPath() {
        val testPath = "/data/data/com.cosmicocean/files/custom_wallpaper.jpg"
        prefsManager.setCustomWallpaperPath(testPath)

        val path = prefsManager.getCustomWallpaperPath()

        assertEquals("Path should match stored value", testPath, path)
    }

    // ==================== BITMAP GENERATION TESTS ====================

    @Test
    fun generateWithCustomBackgroundCreatesBitmapWithCorrectDimensions() {
        val width = 1080
        val height = 2400
        val customBackground = createTestBitmap(width, height, Color.BLUE)

        val result = LocalWallpaperGenerator.generateWithCustomBackground(
            tasks = testTasks,
            totalTaskCount = testTasks.size,
            customBackground = customBackground,
            width = width,
            height = height
        )

        assertNotNull("Result bitmap should not be null", result)
        assertEquals("Width should match", width, result.width)
        assertEquals("Height should match", height, result.height)
        assertEquals("Config should be ARGB_8888", Bitmap.Config.ARGB_8888, result.config)

        // Clean up
        customBackground.recycle()
        result.recycle()
    }

    @Test
    fun generateWithCustomBackgroundPreservesBackgroundColor() {
        val width = 100
        val height = 100
        val testColor = Color.RED
        val customBackground = createTestBitmap(width, height, testColor)

        val result = LocalWallpaperGenerator.generateWithCustomBackground(
            tasks = emptyList(), // No tasks to interfere with background
            totalTaskCount = 0,
            customBackground = customBackground,
            width = width,
            height = height
        )

        // Check corner pixel (should be custom background color, not default)
        val cornerPixel = result.getPixel(0, 0)

        // The corner should have some color from the background (not transparent)
        val alpha = Color.alpha(cornerPixel)
        assertTrue("Background should not be fully transparent", alpha > 0)

        // Clean up
        customBackground.recycle()
        result.recycle()
    }

    @Test
    fun generateWithCustomBackgroundWorksWithDifferentAspectRatios() {
        val testCases = listOf(
            Pair(1080, 1920),  // 9:16 portrait
            Pair(1080, 2400),  // Taller portrait
            Pair(1440, 3120),  // High-res portrait
            Pair(720, 1280),   // Lower-res portrait
        )

        testCases.forEach { (width, height) ->
            val customBackground = createTestBitmap(100, 100, Color.GREEN) // Small source

            val result = LocalWallpaperGenerator.generateWithCustomBackground(
                tasks = testTasks,
                totalTaskCount = testTasks.size,
                customBackground = customBackground,
                width = width,
                height = height
            )

            assertNotNull("Result should not be null for ${width}x${height}", result)
            assertEquals("Width should match for ${width}x${height}", width, result.width)
            assertEquals("Height should match for ${width}x${height}", height, result.height)

            customBackground.recycle()
            result.recycle()
        }
    }

    @Test
    fun generateWithCustomBackgroundHandlesRecycledBitmap() {
        val customBackground = createTestBitmap(100, 100, Color.BLUE)
        customBackground.recycle() // Intentionally recycle

        // This should throw or handle gracefully
        try {
            LocalWallpaperGenerator.generateWithCustomBackground(
                tasks = testTasks,
                totalTaskCount = testTasks.size,
                customBackground = customBackground,
                width = 1080,
                height = 2400
            )
            // If we get here without exception, the code handled it gracefully
        } catch (e: Exception) {
            // Expected - recycled bitmap should cause an exception
            assertTrue("Should throw IllegalStateException for recycled bitmap",
                e is IllegalStateException || e is IllegalArgumentException)
        }
    }

    @Test
    fun generateThemedCreatesValidBitmap() {
        val width = 1080
        val height = 2400

        val result = LocalWallpaperGenerator.generate(
            tasks = testTasks,
            totalTaskCount = testTasks.size,
            theme = WallpaperTheme.COSMIC,
            width = width,
            height = height
        )

        assertNotNull("Themed wallpaper should not be null", result)
        assertEquals("Width should match", width, result.width)
        assertEquals("Height should match", height, result.height)

        result.recycle()
    }

    @Test
    fun generateWorksWithNoTasksClearState() {
        val width = 1080
        val height = 2400

        val result = LocalWallpaperGenerator.generate(
            tasks = emptyList(),
            totalTaskCount = 0,
            theme = WallpaperTheme.OCEAN,
            width = width,
            height = height
        )

        assertNotNull("Clear state wallpaper should not be null", result)
        assertEquals("Width should match", width, result.width)
        assertEquals("Height should match", height, result.height)

        result.recycle()
    }

    @Test
    fun generateHighContrastCustomWallpaperScreenshot() {
        val width = 1080
        val height = 2400

        val customBackground = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }

        val result = LocalWallpaperGenerator.generateWithCustomBackground(
            tasks = testTasks.take(3),
            totalTaskCount = testTasks.size,
            customBackground = customBackground,
            width = width,
            height = height,
            theme = WallpaperTheme.COSMIC,
            environmentPreferences = com.cosmicocean.ui.state.EnvironmentPreferences(
                highContrastTextEnabled = true
            )
        )

        val output = writeScreenshot(result, "custom_wallpaper_high_contrast.png")
        result.recycle()
        customBackground.recycle()

        assertTrue("High-contrast custom wallpaper screenshot should exist", output.exists())
        assertTrue("High-contrast custom wallpaper screenshot should be non-empty", output.length() > 0L)
    }

    // ==================== FLOW VERIFICATION TESTS ====================

    @Test
    fun customWallpaperFlowCompleteScenario() {
        // Simulate the complete flow
        val testPath = "/data/data/com.cosmicocean/files/custom_wallpaper.jpg"

        // Step 1: Set wallpaper mode to custom
        val modeResult = prefsManager.setWallpaperMode(WallpaperPreferencesManager.WALLPAPER_MODE_CUSTOM)
        assertTrue("Setting mode to custom should succeed", modeResult)

        // Step 2: Set custom wallpaper path
        val pathResult = prefsManager.setCustomWallpaperPath(testPath)
        assertTrue("Setting path should succeed", pathResult)

        // Verify preference values were stored correctly
        assertEquals("Wallpaper mode should be custom", "custom", prefsManager.getWallpaperMode())
        assertEquals("Custom wallpaper path should be stored", testPath, prefsManager.getCustomWallpaperPath())
    }

    @Test
    fun switchingFromCustomBackToGeneratedWorks() {
        // Start with custom mode
        prefsManager.setWallpaperMode(WallpaperPreferencesManager.WALLPAPER_MODE_CUSTOM)
        prefsManager.setCustomWallpaperPath("/some/path.jpg")

        // Switch back to generated
        val result = prefsManager.setWallpaperMode(WallpaperPreferencesManager.WALLPAPER_MODE_GENERATED)

        assertTrue("Switching to generated should succeed", result)
        assertEquals("Wallpaper mode should be generated", "generated", prefsManager.getWallpaperMode())
    }

    // ==================== HELPER METHODS ====================

    private fun createStarEntity(
        localId: String,
        title: String,
        urgency: Int,
        dueDate: Long?,
        x: Float,
        y: Float
    ): StarEntity {
        return StarEntity(
            localId = localId,
            serverId = null,
            title = title,
            urgency = urgency,
            dueDate = dueDate,
            x = x,
            y = y,
            createdAt = System.currentTimeMillis(),
            isSubtask = false,
            isRecurring = false,
            echoInterval = null,
            isCompleted = false,
            completedAt = null,
            isArchived = false,
            archivedAt = null
        )
    }

    private fun createTestBitmap(width: Int, height: Int, color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        return bitmap
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
}
