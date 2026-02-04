package com.cosmicocean.integration

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cosmicocean.data.PrivacyLevel
import com.cosmicocean.data.PrivacyPreferencesRepository
import com.cosmicocean.data.StarEntity
import com.cosmicocean.utils.applyWallpaperPrivacy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PrivacyMaskingE2ETest {

    private lateinit var context: Context
    private lateinit var repo: PrivacyPreferencesRepository

    @Before
    fun setup() = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        repo = PrivacyPreferencesRepository(context)
        repo.resetToDefaults()
    }

    @Test
    fun hideAllTasksModeRemovesTasksFromWallpaper() = runBlocking {
        repo.setHideAllTasksMode(true)

        val tasks = listOf(
            buildStarEntity("Pay rent"),
            buildStarEntity("Book flight")
        )

        val result = applyWallpaperPrivacy(context, tasks, totalTaskCount = 12)

        assertTrue("Tasks should be hidden when hide-all is enabled", result.tasks.isEmpty())
        assertEquals("Total count should be zero when hide-all is enabled", 0, result.totalTaskCount)
    }

    @Test
    fun defaultPrivacyInitialsMasksTitles() = runBlocking {
        repo.setHideAllTasksMode(false)
        repo.setDefaultPrivacyLevel(PrivacyLevel.INITIALS)

        val tasks = listOf(
            buildStarEntity("Pay rent"),
            buildStarEntity("Book flight")
        )

        val result = applyWallpaperPrivacy(context, tasks, totalTaskCount = tasks.size)

        assertEquals("P...", result.tasks[0].title)
        assertEquals("B...", result.tasks[1].title)
        assertEquals(tasks.size, result.totalTaskCount)
    }

    @Test
    fun publicPrivacyLeavesTitlesUnchanged() = runBlocking {
        repo.setHideAllTasksMode(false)
        repo.setDefaultPrivacyLevel(PrivacyLevel.PUBLIC)

        val tasks = listOf(buildStarEntity("Morning run"))

        val result = applyWallpaperPrivacy(context, tasks, totalTaskCount = 7)

        assertEquals("Morning run", result.tasks[0].title)
        assertEquals(7, result.totalTaskCount)
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
