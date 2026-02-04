package com.cosmicocean.e2e

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cosmicocean.MainActivity
import com.cosmicocean.auth.TokenManager
import com.cosmicocean.BuildConfig
import com.cosmicocean.utils.WallpaperPreferencesManager
import com.cosmicocean.test.ScreenshotTestRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WallpaperConsentE2ETest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val screenshotRule = ScreenshotTestRule()

    @Test
    fun testWallpaperConsentFlow() {
        // Ensure the consent dialog is shown by clearing the preference before recreation
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        TokenManager(context).saveTokens(
            accessToken = "test-access",
            refreshToken = "test-refresh",
            userId = "test-user",
            email = "test@example.com"
        )
        WallpaperPreferencesManager(context).clearAll()
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()

        val prefs = WallpaperPreferencesManager(context)
        if (BuildConfig.LOCAL_ONLY) {
            // Local-only flow auto-enables wallpaper updates without consent dialog
            composeTestRule.onNodeWithText("Cosmic Wallpaper Setup").assertDoesNotExist()
            assert(prefs.isWallpaperEnabled())
        } else {
            // Online flow expects consent dialog
            composeTestRule.onNodeWithText("Cosmic Wallpaper Setup").assertExists()

            // Test "Not Now" (Disable Sync)
            composeTestRule.onNodeWithText("Not Now").performClick()
            composeTestRule.onNodeWithText("Cosmic Wallpaper Setup").assertDoesNotExist()
        }
    }
}
