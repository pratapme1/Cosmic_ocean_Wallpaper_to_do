package com.cosmicocean.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cosmicocean.data.CosmicDatabase
import com.cosmicocean.data.StarEntity
import com.cosmicocean.data.TaskRepository
import com.cosmicocean.model.Star
import com.cosmicocean.network.LocalOnlyApiService
import com.cosmicocean.network.NetworkModule
import com.cosmicocean.network.ViReminderWritePayload
import com.cosmicocean.network.ViSupabaseReminderRow
import com.cosmicocean.reminders.RemoteRemindersRepository
import com.cosmicocean.reminders.ViReminderMapper
import com.cosmicocean.reminders.ViSupabaseKeyManager
import com.cosmicocean.sync.SyncManager
import com.cosmicocean.test.ScreenshotTestRule
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SupabaseReminderCrudE2ETest {

    @get:Rule
    val screenshotRule = ScreenshotTestRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: CosmicDatabase
    private lateinit var taskRepository: TaskRepository
    private lateinit var remoteRepository: RemoteRemindersRepository

    @Before
    fun setup() {
        val key = InstrumentationRegistry.getArguments().getString("supabaseAnonKey")
        assumeTrue("supabaseAnonKey instrumentation argument is required", !key.isNullOrBlank())

        ViSupabaseKeyManager(context).saveKey(key!!)
        database = CosmicDatabase.getDatabase(context)
        val apiService = LocalOnlyApiService(context)
        val syncManager = SyncManager(
            syncQueueDao = database.syncQueueDao(),
            starDao = database.starDao(),
            apiService = apiService,
            context = context
        )
        taskRepository = TaskRepository(database.starDao(), apiService, context, syncManager)
        remoteRepository = RemoteRemindersRepository.getInstance(context)
    }

    @Test
    fun appCreatedReminderMirrorsCreateUpdateCompleteAndDelete() = runBlocking {
        val api = NetworkModule.getViSupabaseApi(context)
        val createLocalId = "codex-app-${UUID.randomUUID()}"
        val createRemoteId = remoteIdForLocal(createLocalId)
        val deleteLocalId = "codex-app-delete-${UUID.randomUUID()}"
        val deleteRemoteId = remoteIdForLocal(deleteLocalId)

        try {
            val star = Star(
                x = 120f,
                y = 240f,
                title = "Codex app create ${createLocalId.takeLast(6)}",
                urgency = 2,
                dueDate = dueDateMillis("2026-07-11"),
                id = createLocalId
            )
            taskRepository.addStar(star)

            val created = awaitActiveRow(createRemoteId)
            assertEquals(star.title, created.text)
            assertEquals("2026-07-11", created.due)

            star.title = "Codex app update ${createLocalId.takeLast(6)}"
            star.dueDate = dueDateMillis("2026-07-12")
            taskRepository.updateStar(star)

            val updated = awaitActiveRow(createRemoteId)
            assertEquals(star.title, updated.text)
            assertEquals("2026-07-12", updated.due)

            star.isCompleted = true
            star.completedAt = System.currentTimeMillis()
            taskRepository.updateStar(star)
            awaitNoActiveRow(createRemoteId)

            val deleteStar = Star(
                x = 140f,
                y = 260f,
                title = "Codex app delete ${deleteLocalId.takeLast(6)}",
                urgency = 2,
                dueDate = dueDateMillis("2026-07-13"),
                id = deleteLocalId
            )
            taskRepository.addStar(deleteStar)
            assertNotNull(awaitActiveRow(deleteRemoteId))

            taskRepository.deleteStar(deleteStar)
            awaitNoActiveRow(deleteRemoteId)
        } finally {
            api.deleteReminder(idFilter = "eq.$createRemoteId")
            api.deleteReminder(idFilter = "eq.$deleteRemoteId")
            database.starDao().deleteStarByLocalId(createLocalId)
            database.starDao().deleteStarByLocalId(deleteLocalId)
        }
    }

    @Test
    fun assistantCreatedReminderSyncsCreateUpdateDeleteAndAppDelete() = runBlocking {
        val api = NetworkModule.getViSupabaseApi(context)
        val assistantRemoteId = UUID.randomUUID().toString()
        val appDeleteRemoteId = UUID.randomUUID().toString()

        try {
            upsertRemote(assistantRemoteId, "Assistant create ${assistantRemoteId.takeLast(6)}", "2026-07-14")
            assertTrue(remoteRepository.refresh())

            val localId = ViReminderMapper.localIdFor(assistantRemoteId)
            val created = database.starDao().getByLocalId(localId)
            assertNotNull(created)
            assertEquals("Vi · Assistant create ${assistantRemoteId.takeLast(6)}", created!!.title)

            upsertRemote(assistantRemoteId, "Assistant update ${assistantRemoteId.takeLast(6)}", "2026-07-15")
            assertTrue(remoteRepository.refresh())

            val updated = database.starDao().getByLocalId(localId)
            assertNotNull(updated)
            assertEquals("Vi · Assistant update ${assistantRemoteId.takeLast(6)}", updated!!.title)

            api.deleteReminder(idFilter = "eq.$assistantRemoteId")
            assertTrue(remoteRepository.refresh())
            assertNull(database.starDao().getByLocalId(localId))

            upsertRemote(appDeleteRemoteId, "Assistant app delete ${appDeleteRemoteId.takeLast(6)}", "2026-07-16")
            assertTrue(remoteRepository.refresh())

            val appDeleteLocalId = ViReminderMapper.localIdFor(appDeleteRemoteId)
            val appDeleteEntity = database.starDao().getByLocalId(appDeleteLocalId)
            assertNotNull(appDeleteEntity)

            taskRepository.deleteStar(appDeleteEntity!!.toStar())
            awaitNoActiveRow(appDeleteRemoteId)
        } finally {
            api.deleteReminder(idFilter = "eq.$assistantRemoteId")
            api.deleteReminder(idFilter = "eq.$appDeleteRemoteId")
            database.starDao().deleteStarByLocalId(ViReminderMapper.localIdFor(assistantRemoteId))
            database.starDao().deleteStarByLocalId(ViReminderMapper.localIdFor(appDeleteRemoteId))
        }
    }

    private suspend fun upsertRemote(remoteId: String, text: String, due: String) {
        val response = NetworkModule.getViSupabaseApi(context).createReminder(
            body = ViReminderWritePayload(
                id = remoteId,
                due = due,
                text = text,
                done = false
            )
        )
        assertTrue("Supabase upsert failed: HTTP ${response.code()}", response.isSuccessful)
    }

    private suspend fun awaitActiveRow(remoteId: String): ViSupabaseReminderRow {
        repeat(10) {
            activeRow(remoteId)?.let { return it }
            delay(500)
        }
        throw AssertionError("Active Supabase reminder $remoteId was not found")
    }

    private suspend fun awaitNoActiveRow(remoteId: String) {
        repeat(10) {
            if (activeRow(remoteId) == null) return
            delay(500)
        }
        throw AssertionError("Supabase reminder $remoteId was still active")
    }

    private suspend fun activeRow(remoteId: String): ViSupabaseReminderRow? {
        val response = NetworkModule.getViSupabaseApi(context).getActiveReminders()
        assertTrue("Supabase active read failed: HTTP ${response.code()}", response.isSuccessful)
        return response.body().orEmpty().firstOrNull { it.id == remoteId }
    }

    private fun remoteIdForLocal(localId: String): String {
        return UUID.nameUUIDFromBytes("cosmic-ocean:$localId".toByteArray()).toString()
    }

    private fun dueDateMillis(date: String): Long {
        return LocalDate.parse(date)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    private fun StarEntity.toStar(): Star {
        return Star(
            x = x,
            y = y,
            title = title,
            urgency = urgency,
            dueDate = dueDate,
            contextTag = contextTag,
            isSubtask = isSubtask,
            parentId = parentId,
            createdAt = createdAt,
            id = localId
        ).also { star ->
            star.isCompleted = isCompleted
            star.completedAt = completedAt
            star.isArchived = isArchived
            star.archivedAt = archivedAt
        }
    }
}
