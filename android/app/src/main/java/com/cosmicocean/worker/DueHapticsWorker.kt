package com.cosmicocean.worker

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cosmicocean.data.CosmicDatabase
import com.cosmicocean.data.EnvironmentPreferencesRepository
import com.cosmicocean.haptics.DueHapticsStateStore
import com.cosmicocean.utils.HapticsUtil
import kotlinx.coroutines.flow.first
import java.time.LocalTime
import java.util.concurrent.TimeUnit

class DueHapticsWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = EnvironmentPreferencesRepository(applicationContext).preferencesFlow.first()
        if (!prefs.dueHapticsEnabled) return Result.success()

        if (prefs.quietHoursEnabled && isWithinQuietHours(prefs.quietHoursStart, prefs.quietHoursEnd)) {
            return Result.success()
        }

        if (prefs.respectDnd && isDndEnabled()) {
            return Result.success()
        }

        val stateStore = DueHapticsStateStore(applicationContext)
        val now = System.currentTimeMillis()

        val minGapMs = TimeUnit.MINUTES.toMillis(prefs.hapticsRateLimitMinutes.toLong())
        val lastGlobal = stateStore.getLastGlobalFire()
        if (lastGlobal > 0 && now - lastGlobal < minGapMs) {
            return Result.success()
        }

        val tasks = CosmicDatabase.getDatabase(applicationContext).starDao().getAllActiveStarsSync()
            .filter { !it.isCompleted && !it.isArchived && it.dueDate != null }

        if (tasks.isEmpty()) return Result.success()

        var fired = false
        for (task in tasks) {
            val dueDate = task.dueDate ?: continue
            val diffMinutes = ((dueDate - now) / 60000L).toInt()

            if (diffMinutes <= prefs.urgentDueMinutes && diffMinutes >= 0) {
                if (!stateStore.wasFired(task.localId, "urgent")) {
                    HapticsUtil.vibrate(applicationContext, durationMs = 80)
                    stateStore.markFired(task.localId, "urgent", now)
                    fired = true
                }
                break
            }

            if (diffMinutes <= prefs.dueSoonMinutes && diffMinutes >= 0) {
                if (!stateStore.wasFired(task.localId, "soon")) {
                    HapticsUtil.vibrate(applicationContext, durationMs = 60)
                    stateStore.markFired(task.localId, "soon", now)
                    fired = true
                }
                break
            }

            if (diffMinutes < 0) {
                val overdueMinutes = -diffMinutes
                if (overdueMinutes >= prefs.overdueMinutes && !stateStore.wasFired(task.localId, "overdue")) {
                    HapticsUtil.vibrate(applicationContext, durationMs = 100)
                    stateStore.markFired(task.localId, "overdue", now)
                    fired = true
                }
                if (fired) break
            }
        }

        if (fired) {
            stateStore.setLastGlobalFire(now)
        }

        return Result.success()
    }

    private fun isWithinQuietHours(startHour: Int, endHour: Int): Boolean {
        val now = LocalTime.now()
        val start = LocalTime.of(startHour, 0)
        val end = LocalTime.of(endHour, 0)
        return if (startHour == endHour) {
            false
        } else if (start.isBefore(end)) {
            now.isAfter(start) && now.isBefore(end)
        } else {
            now.isAfter(start) || now.isBefore(end)
        }
    }

    private fun isDndEnabled(): Boolean {
        return try {
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read DND state: ${e.message}")
            false
        }
    }

    companion object {
        private const val TAG = "DueHapticsWorker"
    }
}
