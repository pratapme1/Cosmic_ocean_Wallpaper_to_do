package com.cosmicocean.e2e

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cosmicocean.MainActivity
import com.cosmicocean.auth.TokenManager
import com.cosmicocean.utils.WallpaperPreferencesManager
import com.cosmicocean.test.ScreenshotTestRule
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomWallpaperMultiUploadE2ETest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val screenshotRule = ScreenshotTestRule()

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        TokenManager(context).saveTokens("test", "test", "user", "test@example.com")
        WallpaperPreferencesManager(context).clearAll()
        
        val envRepo = com.cosmicocean.data.EnvironmentPreferencesRepository(context)
        kotlinx.coroutines.runBlocking {
            envRepo.setTutorialSeen(true)
            envRepo.setTutorialStep(4)
        }
    }

    @Test
    fun testDirectWallpaperPathUpdates() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val wallpaperPrefs = WallpaperPreferencesManager(context)
        
        // 1. Simulate first upload
        val path1 = "/sdcard/Download/image1.png"
        val path2 = "/sdcard/Download/image2.png"

        wallpaperPrefs.setWallpaperMode(WallpaperPreferencesManager.WALLPAPER_MODE_CUSTOM)
        wallpaperPrefs.setCustomWallpaperPath(path1)
        org.junit.Assert.assertEquals(
            path1,
            wallpaperPrefs.getRenderPreferences().customWallpaperPath
        )
        
        // 2. Simulate second upload. The live wallpaper observes this flow, so
        // the stored render snapshot must change immediately, without waiting
        // for a ticker or another database event.
        wallpaperPrefs.setCustomWallpaperPath(path2)

        composeTestRule.waitForIdle()

        org.junit.Assert.assertEquals(path2, wallpaperPrefs.getRenderPreferences().customWallpaperPath)
    }
}
