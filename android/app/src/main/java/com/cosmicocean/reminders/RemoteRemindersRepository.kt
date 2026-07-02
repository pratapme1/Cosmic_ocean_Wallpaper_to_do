package com.cosmicocean.reminders

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.cosmicocean.data.CosmicDatabase
import com.cosmicocean.data.StarDao
import com.cosmicocean.data.StarEntity
import com.cosmicocean.network.NetworkModule
import com.cosmicocean.network.ViCompletePayload
import com.cosmicocean.network.ViReminderWritePayload
import com.cosmicocean.network.ViSupabaseReminderRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * Remote reminders source ("Vi" tasks), backed by the Supabase
 * vi_assistant_reminders table.
 *
 * refresh() (WallpaperWorker cycle, live wallpaper loop, app open) pulls the
 * active rows and reconciles them into Room as remote-owned tasks, so the app
 * canvas, widget and wallpaper all render them like normal tasks. Completing
 * one PATCHes the row (queued and retried on the next cycle when offline).
 * Failures never propagate: on any fetch error the Room rows from the last
 * good sync keep being served.
 */
class RemoteRemindersRepository private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "RemoteReminders"
        private const val SYNC_PREFS_NAME = "vi_reminders_sync"
        private const val KEY_PENDING_COMPLETIONS = "pending_completions"
        private const val KEY_PENDING_WRITES = "pending_writes"
        private const val KEY_LOCAL_MIRRORS = "local_mirrors"
        private const val LEGACY_CACHE_FILE_NAME = "vi_reminders_cache.json"

        @Volatile
        private var INSTANCE: RemoteRemindersRepository? = null

        fun getInstance(context: Context): RemoteRemindersRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RemoteRemindersRepository(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    private val keyManager = ViSupabaseKeyManager(appContext)
    private val starDao: StarDao get() = CosmicDatabase.getDatabase(appContext).starDao()
    private val syncPrefs: SharedPreferences =
        appContext.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
    private val syncMutex = Mutex()
    private val queueLock = Any()

    /**
     * Fetches the active reminders from Supabase and reconciles Room. Never
     * throws. Also flushes any queued completion PATCHes, so the worker cycle
     * doubles as the offline retry loop.
     */
    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        deleteLegacyCache()

        if (!keyManager.hasKey()) {
            Log.d(TAG, "No Supabase key configured - skipping reminders sync")
            return@withContext false
        }

        flushPendingWrites()
        flushPendingCompletions()

        try {
            val response = NetworkModule.getViSupabaseApi(appContext).getActiveReminders()
            if (!response.isSuccessful) {
                Log.w(TAG, "Reminders fetch failed: HTTP ${response.code()} - keeping last sync")
                return@withContext false
            }

            val rows = response.body()
            if (rows == null) {
                Log.w(TAG, "Reminders fetch returned no body - keeping last sync")
                return@withContext false
            }

            syncMutex.withLock { reconcileIntoRoom(rows) }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Reminders fetch error: ${e.message} - keeping last sync")
            false
        }
    }

    private suspend fun reconcileIntoRoom(rows: List<ViSupabaseReminderRow>) {
        val mirrors = readMirrorMap()
        val existing = mutableMapOf<String, StarEntity>()
        val localToRemote = mutableMapOf<String, String>()
        val pendingWriteIds = pendingWriteRemoteIds()
        starDao.getViReminders().forEach { existing[it.localId] = it }
        mirrors.keys().forEach { remoteId ->
            val localId = mirrors.optString(remoteId)
            if (localId.isNotBlank()) {
                localToRemote[localId] = remoteId
                starDao.getByLocalId(localId)?.let { existing[localId] = it }
            }
        }
        val now = System.currentTimeMillis()
        val fetchedLocalIds = mutableSetOf<String>()

        rows.forEach { row ->
            val fresh = ViReminderMapper.toStarEntity(row, now = now) ?: return@forEach
            val localMirrorId = row.id?.let { mirrors.optString(it, "") }?.takeIf { it.isNotBlank() }
            val targetFresh = if (localMirrorId != null) {
                val current = existing[localMirrorId]
                fresh.copy(
                    localId = localMirrorId,
                    title = row.text?.trim().orEmpty(),
                    x = current?.x ?: fresh.x,
                    y = current?.y ?: fresh.y,
                    contextTag = current?.contextTag,
                    syncStatus = current?.syncStatus ?: "synced"
                )
            } else {
                fresh
            }
            fetchedLocalIds += targetFresh.localId
            val current = existing[targetFresh.localId]
            when {
                current == null -> starDao.insertStarWithTransaction(targetFresh)
                // Remote owns title/due date; everything local (canvas position,
                // completed/deleted flags while a PATCH is still in flight) is kept.
                current.title != targetFresh.title || current.dueDate != targetFresh.dueDate ->
                    starDao.insertStarWithTransaction(
                        current.copy(title = targetFresh.title, dueDate = targetFresh.dueDate, updatedAt = now)
                    )
            }
        }

        // Rows the table no longer returns (completed/removed upstream) disappear.
        existing.keys.filter { it !in fetchedLocalIds }.forEach { staleLocalId ->
            val remoteId = ViReminderMapper.remoteIdOf(staleLocalId) ?: localToRemote[staleLocalId]
            if (remoteId != null && remoteId in pendingWriteIds) return@forEach
            starDao.deleteStarByLocalId(staleLocalId)
            forgetMirrorForLocal(staleLocalId)
        }

        Log.d(TAG, "Synced ${fetchedLocalIds.size} Vi reminders into Room")
    }

    suspend fun mirrorLocalCreate(localId: String, title: String, dueDate: Long?) = withContext(Dispatchers.IO) {
        if (ViReminderMapper.isRemoteReminder(localId) || title.isBlank()) return@withContext
        val remoteId = remoteIdForLocal(localId)
        rememberMirror(remoteId, localId)
        enqueueWrite("upsert", remoteId, localId, title.trim(), dueDateToString(dueDate))
        flushPendingWrites()
    }

    suspend fun mirrorLocalUpdate(localId: String, title: String, dueDate: Long?) = withContext(Dispatchers.IO) {
        if (ViReminderMapper.isRemoteReminder(localId) || title.isBlank()) return@withContext
        val remoteId = remoteIdForLocal(localId)
        rememberMirror(remoteId, localId)
        enqueueWrite("upsert", remoteId, localId, title.trim(), dueDateToString(dueDate))
        flushPendingWrites()
    }

    suspend fun mirrorLocalComplete(localId: String, completedAt: Long?) = withContext(Dispatchers.IO) {
        if (ViReminderMapper.isRemoteReminder(localId)) return@withContext
        val remoteId = existingRemoteIdForLocal(localId) ?: remoteIdForLocal(localId)
        enqueueWrite(
            op = "complete",
            remoteId = remoteId,
            localId = localId,
            text = null,
            due = null,
            completedAt = Instant.ofEpochMilli(completedAt ?: System.currentTimeMillis()).toString()
        )
        flushPendingWrites()
    }

    suspend fun mirrorLocalDelete(localId: String) = withContext(Dispatchers.IO) {
        val remoteId = ViReminderMapper.remoteIdOf(localId)
            ?: existingRemoteIdForLocal(localId)
            ?: remoteIdForLocal(localId)
        enqueueWrite("delete", remoteId, localId, null, null)
        flushPendingWrites()
    }

    /**
     * Pushes "done" for a Vi task to Supabase. The Room row is expected to be
     * marked completed by the caller already (instant local hide); this only
     * handles the remote write, queueing it for the next worker cycle if the
     * PATCH cannot be delivered right now.
     */
    suspend fun completeReminder(localId: String) = withContext(Dispatchers.IO) {
        val remoteId = ViReminderMapper.remoteIdOf(localId) ?: return@withContext
        enqueueCompletion(remoteId, Instant.now().toString())
        flushPendingCompletions()
    }

    suspend fun flushPendingWrites() = withContext(Dispatchers.IO) {
        if (!keyManager.hasKey()) return@withContext
        val pending = readJsonArray(KEY_PENDING_WRITES)
        if (pending.length() == 0) return@withContext

        val delivered = mutableSetOf<String>()
        for (i in 0 until pending.length()) {
            val entry = pending.optJSONObject(i) ?: continue
            val op = entry.optString("op")
            val remoteId = entry.optString("id")
            if (remoteId.isBlank()) continue

            try {
                val response = when (op) {
                    "upsert" -> NetworkModule.getViSupabaseApi(appContext).createReminder(
                        body = ViReminderWritePayload(
                            id = remoteId,
                            text = entry.optString("text"),
                            due = entry.optString("due").takeIf { it.isNotBlank() },
                            done = false
                        )
                    )
                    "delete" -> NetworkModule.getViSupabaseApi(appContext)
                        .deleteReminder(idFilter = "eq.$remoteId")
                    "complete" -> NetworkModule.getViSupabaseApi(appContext).completeReminder(
                        idFilter = "eq.$remoteId",
                        body = ViCompletePayload(
                            completedAt = entry.optString("completed_at")
                                .takeIf { it.isNotBlank() }
                                ?: Instant.now().toString()
                        )
                    )
                    else -> null
                }
                if (response?.isSuccessful == true) {
                    delivered += queueKey(op, remoteId)
                    if (op == "delete" || op == "complete") {
                        forgetMirror(remoteId)
                    }
                } else if (response != null) {
                    Log.w(TAG, "Reminder $op for $remoteId failed: HTTP ${response.code()}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Reminder $op for $remoteId error: ${e.message} - will retry")
            }
        }

        synchronized(queueLock) {
            val queue = readJsonArray(KEY_PENDING_WRITES)
            val remaining = JSONArray()
            for (i in 0 until queue.length()) {
                val entry = queue.optJSONObject(i) ?: continue
                if (queueKey(entry.optString("op"), entry.optString("id")) !in delivered) {
                    remaining.put(entry)
                }
            }
            writeJsonArray(KEY_PENDING_WRITES, remaining)
        }
    }

    /** Sends every queued completion; keeps entries whose PATCH still fails. */
    suspend fun flushPendingCompletions() = withContext(Dispatchers.IO) {
        if (!keyManager.hasKey()) return@withContext
        val pending = readJsonArray(KEY_PENDING_COMPLETIONS)
        if (pending.length() == 0) return@withContext

        val delivered = mutableSetOf<String>()
        for (i in 0 until pending.length()) {
            val entry = pending.optJSONObject(i) ?: continue
            val id = entry.optString("id")
            val completedAt = entry.optString("completed_at")
            if (id.isNullOrBlank()) continue

            try {
                val response = NetworkModule.getViSupabaseApi(appContext).completeReminder(
                    idFilter = "eq.$id",
                    body = ViCompletePayload(completedAt = completedAt)
                )
                if (response.isSuccessful) {
                    delivered += id
                } else {
                    Log.w(TAG, "Completion PATCH for $id failed: HTTP ${response.code()}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Completion PATCH for $id error: ${e.message} - will retry")
            }
        }

        // Re-read under the lock so completions enqueued mid-flush survive.
        synchronized(queueLock) {
            val queue = readJsonArray(KEY_PENDING_COMPLETIONS)
            val remaining = JSONArray()
            for (i in 0 until queue.length()) {
                val entry = queue.optJSONObject(i) ?: continue
                if (entry.optString("id") !in delivered) remaining.put(entry)
            }
            writeJsonArray(KEY_PENDING_COMPLETIONS, remaining)
            if (remaining.length() > 0) {
                Log.d(TAG, "${remaining.length()} Vi completion(s) still queued for retry")
            }
        }
    }

    private fun enqueueCompletion(remoteId: String, completedAt: String) {
        synchronized(queueLock) {
            val queue = readJsonArray(KEY_PENDING_COMPLETIONS)
            for (i in 0 until queue.length()) {
                if (queue.optJSONObject(i)?.optString("id") == remoteId) return
            }
            queue.put(JSONObject().put("id", remoteId).put("completed_at", completedAt))
            writeJsonArray(KEY_PENDING_COMPLETIONS, queue)
        }
    }

    private fun enqueueWrite(
        op: String,
        remoteId: String,
        localId: String,
        text: String?,
        due: String?,
        completedAt: String? = null
    ) {
        synchronized(queueLock) {
            val queue = readJsonArray(KEY_PENDING_WRITES)
            val compacted = JSONArray()
            for (i in 0 until queue.length()) {
                val entry = queue.optJSONObject(i) ?: continue
                if (entry.optString("id") != remoteId) compacted.put(entry)
            }
            compacted.put(
                JSONObject()
                    .put("op", op)
                    .put("id", remoteId)
                    .put("local_id", localId)
                    .put("text", text ?: "")
                    .put("due", due ?: "")
                    .put("completed_at", completedAt ?: "")
            )
            writeJsonArray(KEY_PENDING_WRITES, compacted)
        }
    }

    private fun readJsonArray(key: String): JSONArray {
        return try {
            JSONArray(syncPrefs.getString(key, "[]") ?: "[]")
        } catch (e: Exception) {
            JSONArray()
        }
    }

    private fun writeJsonArray(key: String, queue: JSONArray) {
        syncPrefs.edit().putString(key, queue.toString()).apply()
    }

    private fun pendingWriteRemoteIds(): Set<String> {
        val pending = readJsonArray(KEY_PENDING_WRITES)
        val ids = mutableSetOf<String>()
        for (i in 0 until pending.length()) {
            pending.optJSONObject(i)?.optString("id")?.takeIf { it.isNotBlank() }?.let { ids += it }
        }
        return ids
    }

    /** Removes every Vi row from Room (used when the key is disconnected). */
    suspend fun clearLocal() = withContext(Dispatchers.IO) {
        try {
            starDao.getViReminders().forEach { starDao.deleteStarByLocalId(it.localId) }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear Vi reminders: ${e.message}")
        }
        writeJsonArray(KEY_PENDING_COMPLETIONS, JSONArray())
        writeJsonArray(KEY_PENDING_WRITES, JSONArray())
        syncPrefs.edit().remove(KEY_LOCAL_MIRRORS).apply()
        deleteLegacyCache()
    }

    private fun readMirrorMap(): JSONObject {
        return try {
            JSONObject(syncPrefs.getString(KEY_LOCAL_MIRRORS, "{}") ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun writeMirrorMap(map: JSONObject) {
        syncPrefs.edit().putString(KEY_LOCAL_MIRRORS, map.toString()).apply()
    }

    private fun rememberMirror(remoteId: String, localId: String) {
        val map = readMirrorMap()
        map.put(remoteId, localId)
        writeMirrorMap(map)
    }

    private fun forgetMirror(remoteId: String) {
        val map = readMirrorMap()
        map.remove(remoteId)
        writeMirrorMap(map)
    }

    private fun forgetMirrorForLocal(localId: String) {
        val map = readMirrorMap()
        val toRemove = mutableListOf<String>()
        map.keys().forEach { remoteId ->
            if (map.optString(remoteId) == localId) toRemove += remoteId
        }
        toRemove.forEach { map.remove(it) }
        if (toRemove.isNotEmpty()) writeMirrorMap(map)
    }

    private fun existingRemoteIdForLocal(localId: String): String? {
        val map = readMirrorMap()
        map.keys().forEach { remoteId ->
            if (map.optString(remoteId) == localId) return remoteId
        }
        return null
    }

    private fun remoteIdForLocal(localId: String): String {
        return existingRemoteIdForLocal(localId)
            ?: UUID.nameUUIDFromBytes("cosmic-ocean:$localId".toByteArray()).toString()
    }

    private fun dueDateToString(dueDate: Long?): String? {
        return dueDate?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString()
        }
    }

    private fun queueKey(op: String, remoteId: String): String = "$op:$remoteId"

    /** Drops the disk cache left behind by the old GitHub JSON feed. */
    private fun deleteLegacyCache() {
        try {
            val legacy = File(appContext.filesDir, LEGACY_CACHE_FILE_NAME)
            if (legacy.exists()) legacy.delete()
        } catch (e: Exception) {
            // best effort only
        }
    }
}
