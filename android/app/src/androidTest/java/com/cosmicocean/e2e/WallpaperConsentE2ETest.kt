package com.cosmicocean.e2e

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cosmicocean.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WallpaperConsentE2ETest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testWallpaperConsentFlow() {
        // Assert that the consent dialog is visible (assuming it's a fresh run or preference not set)
        composeTestRule.onNodeWithText("Cosmic Wallpaper Setup").assertExists()
        
        // Test "Not Now" (Disable Sync)
        composeTestRule.onNodeWithText("Not Now").performClick()
        composeTestRule.onNodeWithText("Cosmic Wallpaper Setup").assertDoesNotExist()
        
        // TODO: Verification of Preference state or worker scheduling could be added here
        // if we had access to the underlying preference manager or mock the worker.
    }
}
