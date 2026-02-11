package com.cosmicocean.e2e

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
import org.junit.Before

@RunWith(AndroidJUnit4::class)
class WallpaperConsentE2ETest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val screenshotRule = ScreenshotTestRule()

    @Before
    fun setup() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        TokenManager(context).saveTokens(
            accessToken = "test-access",
            refreshToken = "test-refresh",
            userId = "test-user",
            email = "test@example.com"
        )
        WallpaperPreferencesManager(context).clearAll()
        val envRepo = com.cosmicocean.data.EnvironmentPreferencesRepository(context)
        kotlinx.coroutines.runBlocking {
            envRepo.resetToDefaults()
        }
    }

    @Test
    fun testWallpaperConsentFlow() {
        // Handle Auth if it appears
        if (composeTestRule.onAllNodesWithText("Skip to Guest").fetchSemanticsNodes().isNotEmpty()) {
            composeTestRule.onNodeWithText("Skip to Guest").performClick()
            composeTestRule.waitForIdle()
        }

        // FIRST: Skip App Info (Discovery) overlay
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithTag("discovery_skip_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("discovery_skip_button").performClick()

        // SECOND: Consent dialog should appear
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithText("Live Wallpaper Setup").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Live Wallpaper Setup").assertExists()

        // Test "Not Now" (Dismiss)
        composeTestRule.onNodeWithTag("wallpaper_setup_dismiss_button").performClick()
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithText("Live Wallpaper Setup")
                .fetchSemanticsNodes()
                .isEmpty()
        }
    }

    @Test
    fun testWallpaperConsentSuccessFlow() {
        // Handle Auth if it appears
        if (composeTestRule.onAllNodesWithText("Skip to Guest").fetchSemanticsNodes().isNotEmpty()) {
            composeTestRule.onNodeWithText("Skip to Guest").performClick()
            composeTestRule.waitForIdle()
        }

        // FIRST: Skip App Info (Discovery) overlay
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithTag("discovery_skip_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("discovery_skip_button").performClick()

        // SECOND: Click "Setup Wallpaper"
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithTag("wallpaper_setup_confirm_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("wallpaper_setup_confirm_button").performClick()

        // THIRD: Verify overlay is gone and Tutorial appears
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithText("Live Wallpaper Setup")
                .fetchSemanticsNodes()
                .isEmpty()
        }
        
        composeTestRule.waitUntil(15000) {
            composeTestRule.onAllNodesWithText("Create a task").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Create a task").assertExists()
    }
}