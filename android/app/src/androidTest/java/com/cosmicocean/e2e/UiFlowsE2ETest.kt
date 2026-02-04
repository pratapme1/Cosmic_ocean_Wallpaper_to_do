package com.cosmicocean.e2e

import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cosmicocean.MainActivity
import com.cosmicocean.auth.TokenManager
import com.cosmicocean.data.CosmicDatabase
import com.cosmicocean.data.StarEntity
import com.cosmicocean.data.PrivacyPreferencesRepository
import com.cosmicocean.test.ScreenshotTestRule
import com.cosmicocean.utils.WallpaperPreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class UiFlowsE2ETest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val screenshotRule = ScreenshotTestRule()

    private val testUserId = "ui-test-user"
    private val testEmail = "ui-test@example.com"

    @Before
    fun ensureLoggedIn() {
        prepareLoggedInState()
    }

    @Test
    fun quickAddCreatesTaskInDatabase() {
        val title = "UI Quick Add ${UUID.randomUUID()}"

        composeTestRule.onNodeWithContentDescription("Add Task").performClick()
        composeTestRule.onNodeWithText("New Cosmic Task").assertExists()
        composeTestRule.onNode(hasSetTextAction()).performTextInput(title)
        composeTestRule.onNodeWithText("Release Star").performClick()

        val db = database()
        composeTestRule.waitUntil(5_000) {
            runBlocking {
                db.starDao().getAllActiveStarsSync().any { it.title == title }
            }
        }
    }

    @Test
    fun searchOverlayFindsSeededTask() {
        val seededTitle = "Find Me ${UUID.randomUUID()}"
        seedStars(listOf(buildStarEntity(seededTitle)))
        recreateActivity()

        composeTestRule.onNodeWithContentDescription("Search").performClick()
        composeTestRule.onNodeWithText("Search Tasks").assertExists()
        composeTestRule.onNode(hasText("Search by title, status, priority...")).performTextInput("Find Me")
        composeTestRule.onNodeWithText(seededTitle).assertExists()

        composeTestRule.onNodeWithText(seededTitle).performClick()
        composeTestRule.onNodeWithText("Search Tasks").assertDoesNotExist()
    }

    @Test
    fun settingsThemeChangePersists() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = WallpaperPreferencesManager(context)

        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.onNodeWithText("Settings & Guide").assertExists()

        composeTestRule.onNodeWithText("Ocean", substring = true).performClick()
        composeTestRule.waitUntil(2_000) { prefs.getTheme() == "ocean" }

        composeTestRule.onNodeWithContentDescription("Close").performClick()
        composeTestRule.onNodeWithText("Settings & Guide").assertDoesNotExist()
    }

    @Test
    fun privacySettingsToggleHideAllTasks() {
        openPrivacySettings()

        toggleBySiblingText("Hide All Tasks")
        composeTestRule.onNodeWithText("Maximum privacy mode active", substring = true).assertExists()

        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.onNodeWithText("Privacy Settings").assertDoesNotExist()
    }

    @Test
    fun privacySettingsPersistHideAllTasks() {
        openPrivacySettings()

        toggleBySiblingText("Hide All Tasks")
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.onNodeWithText("Privacy Settings").assertDoesNotExist()

        openPrivacySettings()
        privacyToggleNode("Hide All Tasks").assertIsOn()

        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.onNodeWithText("Privacy Settings").assertDoesNotExist()
    }

    @Test
    fun privacySettingsOnlyShowLocalControls() {
        openPrivacySettings()

        composeTestRule.onNodeWithText("Work Hours Privacy", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Auto-hide Work Tasks", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Work Hours Start", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Work Hours End", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Quick Reveal", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Biometric Reveal", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Privacy settings sync with your account", substring = true).assertDoesNotExist()

        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.onNodeWithText("Privacy Settings").assertDoesNotExist()
    }

    @Test
    fun environmentSettingsToggleWeatherOverlay() {
        openEnvironmentSettings()

        toggleBySiblingText("Productivity Weather")
        composeTestRule.onNodeWithText("Weather reflects your productivity", substring = true).assertExists()

        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.onNodeWithText("Environment Settings").assertDoesNotExist()
    }

    @Test
    fun environmentSettingsScreenshot() {
        openEnvironmentSettings()

        composeTestRule.onNodeWithText("Environment Settings").assertExists()
        composeTestRule.onNodeWithText("Time of Day", substring = true).assertExists()
        composeTestRule.onNodeWithText("Weather Overlay", substring = true).assertExists()
    }

    private fun openPrivacySettings() {
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.onNodeWithText("Settings & Guide").assertExists()
        composeTestRule.onNodeWithText("Privacy Settings", substring = true).performClick()
        composeTestRule.onNodeWithText("Privacy Settings").assertExists()
    }

    private fun openEnvironmentSettings() {
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.onNodeWithText("Settings & Guide").assertExists()
        composeTestRule.onNodeWithText("Environment Settings", substring = true).performClick()
        composeTestRule.onNodeWithText("Environment Settings").assertExists()
    }

    private fun toggleBySiblingText(label: String) {
        composeTestRule.onNode(isToggleable().and(hasAnySibling(hasText(label, substring = true))))
            .performClick()
    }

    private fun privacyToggleNode(label: String) =
        composeTestRule.onNode(isToggleable().and(hasAnySibling(hasText(label, substring = true))))

    private fun prepareLoggedInState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        TokenManager(context).saveTokens(
            accessToken = "ui-test-access",
            refreshToken = "ui-test-refresh",
            userId = testUserId,
            email = testEmail
        )

        WallpaperPreferencesManager(context).setWallpaperEnabled(false)
        runBlocking {
            withContext(Dispatchers.IO) {
                PrivacyPreferencesRepository(context).resetToDefaults()
                val db = database()
                db.starDao().deleteAllStars()
                db.constellationDao().deleteAllLinks()
                db.orbitDao().deleteAllOrbits()
            }
        }
        recreateActivity()
    }

    private fun seedStars(stars: List<StarEntity>) {
        if (stars.isEmpty()) return
        runBlocking {
            withContext(Dispatchers.IO) {
                database().starDao().insertStars(stars)
            }
        }
    }

    private fun recreateActivity() {
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()
    }

    private fun database(): CosmicDatabase {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return CosmicDatabase.getDatabase(context)
    }

    private fun buildStarEntity(title: String): StarEntity {
        val now = System.currentTimeMillis()
        return StarEntity(
            localId = UUID.randomUUID().toString(),
            serverId = null,
            title = title,
            urgency = 2,
            dueDate = now + 3_600_000,
            x = 100f,
            y = 200f,
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
