package com.cosmicocean.e2e

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cosmicocean.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NewFeaturesE2ETest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testCustomWallpaperButtonPresent() {
        setupAuthAndPreferences()
        
        // Click canvas to show HUD
        composeTestRule.onRoot().performClick()
        
        // Wait for HUD to be visible
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithTag("custom_wallpaper_button").fetchSemanticsNodes().isNotEmpty()
        }
        
        // Check for "Custom Wallpaper" button
        composeTestRule.onNodeWithText("Custom Wallpaper").assertExists()
    }

    @Test
    fun testSettingsMenuCleanedUp() {
        setupAuthAndPreferences()
        
        // Click canvas to show HUD
        composeTestRule.onRoot().performClick()

        // Wait for HUD
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithTag("settings_button").fetchSemanticsNodes().isNotEmpty()
        }

        // Open settings
        composeTestRule.onNodeWithTag("settings_button").performClick()

        // Wait for Settings to appear
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty()
        }

        // Check title is "Settings"
        composeTestRule.onNodeWithText("Settings").assertExists()
        
        // Verify "Logout" and "Export Data" are NOT present
        composeTestRule.onNodeWithText("🚪 Logout").assertDoesNotExist()
        composeTestRule.onNodeWithText("💾 Export Data").assertDoesNotExist()
        composeTestRule.onNodeWithText("Settings & Guide").assertDoesNotExist()
    }

    @Test
    fun testEnvironmentSettingsCleanedUp() {
        setupAuthAndPreferences()
        
        // Click canvas to show HUD
        composeTestRule.onRoot().performClick()

        // Wait for HUD
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithTag("settings_button").fetchSemanticsNodes().isNotEmpty()
        }

        // Open settings
        composeTestRule.onNodeWithTag("settings_button").performClick()

        // Open Environment Settings
        composeTestRule.onNodeWithText("🌤️ Environment Settings").performClick()

        // Wait for Environment Settings to appear
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Environment Settings").fetchSemanticsNodes().isNotEmpty()
        }

        // Verify "System Integration" and "Setup Wallpaper" are NOT present
        composeTestRule.onNodeWithText("System Integration").assertDoesNotExist()
        composeTestRule.onNodeWithText("Setup Wallpaper").assertDoesNotExist()
        composeTestRule.onNodeWithText("🖼️").assertDoesNotExist()
    }

    @Test
    fun testSearchOverlaySubtaskLabel() {
        setupAuthAndPreferences()
        
        // Click canvas to show HUD
        composeTestRule.onRoot().performClick()

        // Wait for HUD
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithTag("add_task_button").fetchSemanticsNodes().isNotEmpty()
        }

        // Open Quick Add
        composeTestRule.onNodeWithTag("add_task_button").performClick()
        
        // Wait for Quick Add overlay
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithTag("quick_add_title").fetchSemanticsNodes().isNotEmpty()
        }
        
        composeTestRule.onNodeWithTag("quick_add_title").performTextInput("Parent Task")
        composeTestRule.onNodeWithText("Release Star").performClick()
        
        // Create subtask
        composeTestRule.onRoot().performClick()
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithTag("add_task_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("add_task_button").performClick()
        
        // Wait for Quick Add overlay
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithTag("quick_add_title").fetchSemanticsNodes().isNotEmpty()
        }
        
        composeTestRule.onNodeWithTag("quick_add_title").performTextInput("Subtask Task")
        composeTestRule.onNodeWithTag("quick_add_subtask_toggle").performClick()
        
        // Select parent (first one)
        composeTestRule.onNodeWithTag("quick_add_parent_field").performClick()
        // Use onAllNodesWithText and pick the one that is likely the dropdown item
        composeTestRule.onAllNodesWithText("Parent Task").onLast().performClick()
        
        composeTestRule.onNodeWithText("Release Star").performClick()

        // Open Search
        composeTestRule.onRoot().performClick()
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithTag("search_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("search_button").performClick()
        
        // Wait for Search Overlay
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Search Tasks").fetchSemanticsNodes().isNotEmpty()
        }
        
        // Check for SUBTASK label
        composeTestRule.onNodeWithText("SUBTASK").assertExists()
    }

    private fun setupAuthAndPreferences() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        com.cosmicocean.auth.TokenManager(context).saveTokens(
            accessToken = "test-access",
            refreshToken = "test-refresh",
            userId = "test-user",
            email = "test@example.com"
        )
        
        // Ensure tutorial and discovery don't block
        val envRepo = com.cosmicocean.data.EnvironmentPreferencesRepository(context)
        val wallpaperPrefs = com.cosmicocean.utils.WallpaperPreferencesManager(context)
        
        kotlinx.coroutines.runBlocking {
            envRepo.setTutorialSeen(true)
            envRepo.setTutorialStep(4)
            wallpaperPrefs.setWallpaperConsent(true)
            wallpaperPrefs.setWallpaperEnabled(true)
        }
        
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()

        // Handle AuthScreen if it still appears
        if (composeTestRule.onAllNodesWithText("Skip to Guest").fetchSemanticsNodes().isNotEmpty()) {
            composeTestRule.onNodeWithText("Skip to Guest").performClick()
            composeTestRule.waitForIdle()
        }
    }
}