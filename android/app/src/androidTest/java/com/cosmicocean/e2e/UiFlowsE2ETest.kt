package com.cosmicocean.e2e

import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import com.cosmicocean.MainActivity
import com.cosmicocean.auth.TokenManager
import com.cosmicocean.data.CosmicDatabase
import com.cosmicocean.data.StarEntity
import com.cosmicocean.data.PrivacyPreferencesRepository
import com.cosmicocean.data.EnvironmentPreferencesRepository
import com.cosmicocean.test.ScreenshotTestRule
import com.cosmicocean.utils.WallpaperPreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
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

        ensureHudVisible()
        composeTestRule.onNodeWithContentDescription("Add Task").performClick()
        composeTestRule.onNodeWithText("New Cosmic Task").assertExists()
        composeTestRule.onNodeWithTag("quick_add_title", useUnmergedTree = true)
            .performTextInput(title)
        composeTestRule.onNodeWithText("Release Star").performClick()

        val db = database()
        composeTestRule.waitUntil(5_000) {
            runBlocking {
                db.starDao().getAllActiveStarsSync().any { it.title == title }
            }
        }
    }

    @Test
    fun quickAddRecurringAndSubtaskPersist() {
        val parentId = UUID.randomUUID().toString()
        val parentTitle = "Parent ${UUID.randomUUID()}"
        val title = "Recurring Quick Add ${UUID.randomUUID()}"

        seedStars(listOf(buildStarEntity(parentTitle, localId = parentId)))
        recreateActivity()

        ensureHudVisible()
        composeTestRule.onNodeWithContentDescription("Add Task").performClick()
        composeTestRule.onNodeWithText("New Cosmic Task").assertExists()

        composeTestRule.onNodeWithTag("quick_add_recurring_toggle", useUnmergedTree = true)
            .performClick()
        composeTestRule.onNodeWithTag("quick_add_recurrence_field", useUnmergedTree = true)
            .performClick()
        composeTestRule.onNodeWithText("Weekly", substring = true).performClick()
        composeTestRule.onNodeWithTag("quick_add_subtask_toggle", useUnmergedTree = true)
            .performClick()
        composeTestRule.onNodeWithTag("quick_add_parent_field", useUnmergedTree = true)
            .performClick()
        composeTestRule.onNodeWithText(parentTitle, substring = true).performClick()

        composeTestRule.onNodeWithTag("quick_add_title", useUnmergedTree = true)
            .performTextInput(title)
        composeTestRule.onNodeWithText("Release Star").performClick()

        val db = database()
        composeTestRule.waitUntil(6_000) {
            runBlocking {
                db.starDao().getAllActiveStarsSync().any {
                    it.title == title &&
                        it.isRecurring &&
                        it.echoInterval == "WEEKLY" &&
                        it.isSubtask &&
                        it.parentId == parentId
                }
            }
        }
    }

    @Test
    fun subtaskParentLinkPersists() {
        val parentId = UUID.randomUUID().toString()
        val parentTitle = "Parent ${UUID.randomUUID()}"
        val childTitle = "Child ${UUID.randomUUID()}"
        seedStars(listOf(buildStarEntity(parentTitle, localId = parentId)))
        recreateActivity()

        ensureHudVisible()
        composeTestRule.onNodeWithContentDescription("Add Task").performClick()
        composeTestRule.onNodeWithText("New Cosmic Task").assertExists()

        composeTestRule.onNodeWithTag("quick_add_subtask_toggle", useUnmergedTree = true)
            .performClick()
        composeTestRule.onNodeWithTag("quick_add_parent_field", useUnmergedTree = true)
            .performClick()
        composeTestRule.onNodeWithText(parentTitle, substring = true).performClick()

        composeTestRule.onNodeWithTag("quick_add_title", useUnmergedTree = true)
            .performTextInput(childTitle)
        composeTestRule.onNodeWithText("Release Star").performClick()

        val db = database()
        composeTestRule.waitUntil(6_000) {
            runBlocking {
                db.starDao().getAllActiveStarsSync().any {
                    it.title == childTitle &&
                        it.isSubtask &&
                        it.parentId == parentId
                }
            }
        }
    }

    @Test
    fun searchOverlayFindsSeededTask() {
        val seededTitle = "Find Me ${UUID.randomUUID()}"
        seedStars(listOf(buildStarEntity(seededTitle)))
        recreateActivity()

        ensureHudVisible()
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

        ensureHudVisible()
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
        ensureEnvironmentEnabled()

        val isEnabled = composeTestRule
            .onAllNodesWithText("Weather reflects your productivity", substring = true)
            .fetchSemanticsNodes()
            .isNotEmpty()

        if (isEnabled) {
            toggleBySiblingText("Productivity Weather")
            waitForText("Weather overlay is disabled")
        }

        toggleBySiblingText("Productivity Weather")
        waitForText("Weather reflects your productivity")

        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.onNodeWithText("Environment Settings").assertDoesNotExist()
    }

    @Test
    fun environmentSettingsDisableAllEffects() {
        openEnvironmentSettings()

        val enabledCopyPresent = composeTestRule
            .onAllNodesWithText("Dynamic environment effects are enabled", substring = true)
            .fetchSemanticsNodes()
            .isNotEmpty()

        if (enabledCopyPresent) {
            toggleBySiblingText("Enable Environment")
        }

        composeTestRule.onNodeWithText("Environment effects are disabled", substring = true).assertExists()
        composeTestRule.onNodeWithText("Enable environment effects to use time-of-day settings", substring = true).assertExists()
        composeTestRule.onNodeWithText("Enable environment effects to use the weather overlay", substring = true).assertExists()
        captureScreenshot("environmentSettingsDisabled")

        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.onNodeWithText("Environment Settings").assertDoesNotExist()
    }

    @Test
    fun environmentToggleShowsWallpaperBeforeAfter() {
        composeTestRule.waitForIdle()
        captureScreenshot("wallpaper_env_on")

        openEnvironmentSettings()
        val disabledCopyPresent = composeTestRule
            .onAllNodesWithText("Environment effects are disabled", substring = true)
            .fetchSemanticsNodes()
            .isNotEmpty()
        if (disabledCopyPresent) {
            toggleBySiblingText("Enable Environment")
        }

        toggleBySiblingText("Enable Environment")
        composeTestRule.onNodeWithText("Environment effects are disabled", substring = true).assertExists()
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.onNodeWithText("Environment Settings").assertDoesNotExist()
        composeTestRule.waitForIdle()
        Thread.sleep(600)
        captureScreenshot("wallpaper_env_off")
    }

    @Test
    fun homeHudAutoHideAndShowOnTap() {
        composeTestRule.waitForIdle()
        // Wait for HUD auto-hide delay
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithContentDescription("Settings").fetchSemanticsNodes().isEmpty()
        }
        captureScreenshot("home_hud_hidden")

        composeTestRule.onRoot().performTouchInput {
            click(Offset(100f, 100f))
        }

        composeTestRule.waitUntil(3_000) {
            composeTestRule.onAllNodesWithContentDescription("Settings").fetchSemanticsNodes().isNotEmpty()
        }
        captureScreenshot("home_hud_visible")
    }

    @Test
    fun environmentSettingsScreenshot() {
        openEnvironmentSettings()

        composeTestRule.onNodeWithText("Environment Settings").assertExists()
        composeTestRule.onNodeWithText("Time of Day", substring = true).assertExists()
        composeTestRule.onNodeWithText("Weather Overlay", substring = true).assertExists()
    }

    @Test
    fun privacySettingsCloseButtonScreenshot() {
        openPrivacySettings()

        composeTestRule.onNodeWithContentDescription("Close").assertExists()
        captureScreenshot("privacy_settings_close")

        composeTestRule.onNodeWithContentDescription("Close").performClick()
        composeTestRule.onNodeWithText("Privacy Settings").assertDoesNotExist()
    }

    @Test
    fun environmentSettingsCloseButtonScreenshot() {
        openEnvironmentSettings()

        composeTestRule.onNodeWithContentDescription("Close").assertExists()
        captureScreenshot("environment_settings_close")

        composeTestRule.onNodeWithContentDescription("Close").performClick()
        composeTestRule.onNodeWithText("Environment Settings").assertDoesNotExist()
    }

    @Test
    fun editTaskUpdatesTitleAndUrgency() {
        val originalTitle = "Edit Me ${UUID.randomUUID()}"
        val updatedTitle = "Updated ${UUID.randomUUID()}"

        val metrics = canvasMetrics()
        seedStars(listOf(buildStarEntity(originalTitle, x = metrics.width * 0.5f, y = metrics.height * 0.4f)))
        recreateActivity()

        val starId = getStarIdByTitle(originalTitle)
        composeTestRule.activity.updateStarForTest(starId, updatedTitle, 3, 60f)

        composeTestRule.waitUntil(12_000) {
            val snapshot = composeTestRule.activity.getStarSnapshot(starId)
            snapshot?.first == updatedTitle && snapshot.second == 3
        }
    }

    @Test
    fun editOverlayShowsTitle() {
        val originalTitle = "Edit Overlay ${UUID.randomUUID()}"
        val metrics = canvasMetrics()
        seedStars(listOf(buildStarEntity(originalTitle, x = metrics.width * 0.5f, y = metrics.height * 0.4f)))
        recreateActivity()

        openEditOverlayForStarId(getStarIdByTitle(originalTitle))
        composeTestRule.onNodeWithTag("edit_title", useUnmergedTree = true)
            .assertTextContains(originalTitle, substring = true)
        composeTestRule.onNodeWithContentDescription("Close").performClick()
        composeTestRule.onNodeWithText("Edit Task").assertDoesNotExist()
    }

    @Test
    fun startFocusSessionAndStop() {
        val title = "Focus Task ${UUID.randomUUID()}"
        val metrics = canvasMetrics()
        seedStars(listOf(buildStarEntity(title, x = metrics.width * 0.5f, y = metrics.height * 0.45f)))
        recreateActivity()

        openEditOverlayForStarId(getStarIdByTitle(title))
        composeTestRule.onNodeWithText("Start Focus Session").performClick()
        composeTestRule.onNodeWithText("25 minutes").performClick()

        composeTestRule.onNodeWithText("Edit Task").assertExists()
        composeTestRule.onNodeWithContentDescription("Close").performClick()

        composeTestRule.waitUntil(3_000) {
            composeTestRule.onAllNodesWithText("Focus:", substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        captureScreenshot("focus_session_active")
        composeTestRule.onNodeWithText("Stop").performClick()
        composeTestRule.waitUntil(3_000) {
            composeTestRule.onAllNodesWithText("Focus:", substring = true)
                .fetchSemanticsNodes()
                .isEmpty()
        }
    }

    @Test
    fun snoozeOverdueShowsDialog() {
        val overdueTitle = "Overdue ${UUID.randomUUID()}"
        seedStars(listOf(buildStarEntity(overdueTitle, dueOffsetMs = -3_600_000)))
        recreateActivity()

        ensureHudVisible()
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.onNodeWithText("Settings & Guide").assertExists()
        composeTestRule.onNodeWithText("Snooze Overdue", substring = true).performClick()

        composeTestRule.onNodeWithText("Snooze Overdue Tasks").assertExists()
        captureScreenshot("snooze_overdue_dialog")
        composeTestRule.onNodeWithText("Snooze").performClick()
        composeTestRule.onNodeWithText("Snooze Overdue Tasks").assertDoesNotExist()
    }

    @Test
    fun clearAllDataClearsTasks() {
        seedStars(
            listOf(
                buildStarEntity("Clear 1 ${UUID.randomUUID()}"),
                buildStarEntity("Clear 2 ${UUID.randomUUID()}")
            )
        )
        recreateActivity()

        ensureHudVisible()
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.onNodeWithText("Settings & Guide").assertExists()
        composeTestRule.onNodeWithText("Clear All Data", substring = true).performClick()

        composeTestRule.waitUntil(5_000) {
            runBlocking {
                database().starDao().getAllActiveStarsSync().isEmpty()
            }
        }
    }

    @Test
    fun exportDataKeepsSettingsOpen() {
        ensureHudVisible()
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.onNodeWithText("Settings & Guide").assertExists()
        composeTestRule.onNodeWithText("Export Data", substring = true).performClick()
        composeTestRule.onNodeWithText("Settings & Guide").assertExists()
    }

    @Test
    fun doneForTodayClosesSettings() {
        ensureHudVisible()
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.onNodeWithText("Settings & Guide").assertExists()
        composeTestRule.onNodeWithText("Done For Today", substring = true).performClick()
        composeTestRule.onNodeWithText("Settings & Guide").assertDoesNotExist()
    }

    @Test
    fun themeChangePersistsAfterRecreate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = WallpaperPreferencesManager(context)

        ensureHudVisible()
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.onNodeWithText("Settings & Guide").assertExists()
        composeTestRule.onNodeWithText("Fantasy", substring = true).performClick()
        composeTestRule.onNodeWithContentDescription("Close").performClick()

        recreateActivity()
        composeTestRule.waitUntil(3_000) { prefs.getTheme() == "fantasy" }
    }

    @Test
    fun dragStarToArchiveAndCompleteZones() {
        val metrics = canvasMetrics()
        val startX = metrics.width * 0.5f
        val startY = metrics.height * 0.55f

        val archiveId = UUID.randomUUID().toString()
        val completeId = UUID.randomUUID().toString()
        seedStars(
            listOf(
                buildStarEntity("Archive ${UUID.randomUUID()}", localId = archiveId, x = startX, y = startY),
                buildStarEntity("Complete ${UUID.randomUUID()}", localId = completeId, x = startX, y = startY + 140f)
            )
        )
        recreateActivity()
        composeTestRule.waitForIdle()

        val (archiveZoneX, completeZoneX) = composeTestRule.activity.getZoneTargets()
        val archiveTarget = Offset(archiveZoneX - 20f, metrics.topInset + metrics.height * 0.5f)
        val completeTarget = Offset(completeZoneX + 20f, metrics.topInset + metrics.height * 0.55f)

        val archiveStart = waitForStarPosition(archiveId)
        swipeOnDevice(archiveStart, archiveTarget)
        composeTestRule.waitUntil(8_000) {
            runBlocking {
                database().starDao().getByLocalId(archiveId)?.isArchived == true
            }
        }

        val completeStart = waitForStarPosition(completeId)
        swipeOnDevice(completeStart, completeTarget)
        composeTestRule.waitUntil(8_000) {
            runBlocking {
                database().starDao().getByLocalId(completeId)?.isCompleted == true
            }
        }
    }

    private fun openPrivacySettings() {
        ensureHudVisible()
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.onNodeWithText("Settings & Guide").assertExists()
        composeTestRule.onNodeWithText("Privacy Settings", substring = true).performClick()
        composeTestRule.onNodeWithText("Privacy Settings").assertExists()
    }

    private fun openEnvironmentSettings() {
        ensureHudVisible()
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.onNodeWithText("Settings & Guide").assertExists()
        composeTestRule.onNodeWithText("Environment Settings", substring = true).performClick()
        composeTestRule.onNodeWithText("Environment Settings").assertExists()
    }

    private fun toggleBySiblingText(label: String) {
        composeTestRule.onNode(isToggleable().and(hasAnySibling(hasText(label, substring = true))))
            .performClick()
    }

    private fun waitForText(text: String, timeoutMs: Long = 5_000) {
        composeTestRule.waitUntil(timeoutMs) {
            composeTestRule.onAllNodesWithText(text, substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun ensureHudVisible() {
        composeTestRule.onRoot().performTouchInput {
            click(Offset(100f, 100f))
        }
        composeTestRule.waitUntil(3_000) {
            composeTestRule.onAllNodesWithContentDescription("Settings")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun ensureEnvironmentEnabled() {
        val disabledCopyPresent = composeTestRule
            .onAllNodesWithText("Environment effects are disabled", substring = true)
            .fetchSemanticsNodes()
            .isNotEmpty()
        if (disabledCopyPresent) {
            toggleBySiblingText("Enable Environment")
            waitForText("Dynamic environment effects are enabled")
        }
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

        val wallpaperPrefs = WallpaperPreferencesManager(context)
        wallpaperPrefs.setWallpaperEnabled(false)
        wallpaperPrefs.setWallpaperConsent(true)
        runBlocking {
            withContext(Dispatchers.IO) {
                PrivacyPreferencesRepository(context).resetToDefaults()
                EnvironmentPreferencesRepository(context).resetToDefaults()
                EnvironmentPreferencesRepository(context).setTutorialSeen(true)
                val db = database()
                db.starDao().deleteAllStars()
                db.constellationDao().deleteAllLinks()
                db.orbitDao().deleteAllOrbits()
            }
        }
        recreateActivity()
        ensureOnMainScreen()
    }

    private fun ensureOnMainScreen() {
        composeTestRule.waitForIdle()
        val guestNodes = composeTestRule
            .onAllNodesWithText("Continue as Guest", substring = true)
            .fetchSemanticsNodes()
        if (guestNodes.isNotEmpty()) {
            composeTestRule.onNodeWithText("Continue as Guest", substring = true).performClick()
        }
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithContentDescription("Settings").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun captureScreenshot(tag: String) {
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File("/data/local/tmp/AndroidTestScreenshots/manual")

        dismissSystemUiAnrIfPresent()

        if (!dir.exists()) {
            uiAutomation.executeShellCommand("mkdir -p ${dir.absolutePath}").close()
        }

        val fileName = "${tag}_${System.currentTimeMillis()}.png"
        val externalDir = targetContext.getExternalFilesDir("screenshots")
            ?: throw AssertionError("External files dir unavailable for screenshots")
        if (!externalDir.exists() && !externalDir.mkdirs()) {
            throw AssertionError("Failed to create external screenshot directory: ${externalDir.absolutePath}")
        }
        val externalFile = File(externalDir, fileName)

        val bitmap = uiAutomation.takeScreenshot()
            ?: throw AssertionError("UIAutomation returned null screenshot")
        FileOutputStream(externalFile).use { out ->
            if (!bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)) {
                throw AssertionError("Failed to compress screenshot for $tag")
            }
        }
        bitmap.recycle()

        uiAutomation.executeShellCommand("cp ${externalFile.absolutePath} ${dir.absolutePath}/${fileName}").close()
    }

    private fun dismissSystemUiAnrIfPresent() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val dialog = device.findObject(UiSelector().textContains("System UI isn't responding"))
        if (dialog.exists()) {
            val waitButton = device.findObject(UiSelector().textContains("Wait"))
            if (waitButton.exists()) {
                waitButton.click()
            } else {
                val closeButton = device.findObject(UiSelector().textContains("Close app"))
                if (closeButton.exists()) {
                    closeButton.click()
                }
            }
            device.waitForIdle()
        }
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

    private fun buildStarEntity(
        title: String,
        localId: String = UUID.randomUUID().toString(),
        x: Float = 120f,
        y: Float = 240f,
        dueOffsetMs: Long = 3_600_000
    ): StarEntity {
        val now = System.currentTimeMillis()
        return StarEntity(
            localId = localId,
            serverId = null,
            title = title,
            urgency = 2,
            dueDate = now + dueOffsetMs,
            x = x,
            y = y,
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

    private data class CanvasMetrics(
        val width: Float,
        val height: Float,
        val topInset: Float,
        val bottomInset: Float
    )

    private fun canvasMetrics(): CanvasMetrics {
        val metrics = composeTestRule.activity.resources.displayMetrics
        val insets = composeTestRule.activity.window.decorView.rootWindowInsets
        val bars = insets?.getInsets(android.view.WindowInsets.Type.systemBars())
        val top = bars?.top ?: 0
        val bottom = bars?.bottom ?: 0
        val height = (metrics.heightPixels - top - bottom).toFloat()
        return CanvasMetrics(
            width = metrics.widthPixels.toFloat(),
            height = height,
            topInset = top.toFloat(),
            bottomInset = bottom.toFloat()
        )
    }

    private fun openEditOverlayForStarId(starId: String) {
        composeTestRule.waitUntil(5_000) {
            composeTestRule.activity.hasStarInViewModel(starId)
        }
        composeTestRule.activity.openEditForStarId(starId)
        composeTestRule.waitUntil(3_000) {
            composeTestRule.onAllNodesWithText("Edit Task")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun getStarIdByTitle(title: String): String {
        return runBlocking {
            database().starDao().getAllActiveStarsSync().first { it.title == title }.localId
        }
    }

    private fun waitForStarPosition(starId: String): Offset {
        var pos: Pair<Float, Float>? = null
        composeTestRule.waitUntil(3_000) {
            pos = composeTestRule.activity.getStarPosition(starId)
            pos != null
        }
        val metrics = canvasMetrics()
        val (x, y) = pos ?: Pair(metrics.width * 0.5f, metrics.height * 0.5f)
        return Offset(x, y + metrics.topInset)
    }

    private fun dragOnCanvas(from: Offset, to: Offset) {
        composeTestRule.onRoot().performTouchInput {
            down(from)
            val steps = 6
            for (i in 1..steps) {
                val t = i / steps.toFloat()
                val x = from.x + (to.x - from.x) * t
                val y = from.y + (to.y - from.y) * t
                moveTo(Offset(x, y))
            }
            up()
        }
    }

    private fun swipeOnDevice(from: Offset, to: Offset) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.swipe(from.x.toInt(), from.y.toInt(), to.x.toInt(), to.y.toInt(), 30)
    }
}
