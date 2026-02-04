package com.cosmicocean.haptics

import android.content.Context

class DueHapticsStateStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun wasFired(taskId: String, key: String): Boolean {
        return prefs.getLong(keyFor(taskId, key), 0L) > 0L
    }

    fun markFired(taskId: String, key: String, timestamp: Long) {
        prefs.edit().putLong(keyFor(taskId, key), timestamp).apply()
    }

    fun getLastGlobalFire(): Long = prefs.getLong(KEY_LAST_GLOBAL_FIRE, 0L)

    fun setLastGlobalFire(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_GLOBAL_FIRE, timestamp).apply()
    }

    private fun keyFor(taskId: String, key: String): String = "${KEY_PREFIX}_${taskId}_$key"

    companion object {
        private const val PREFS_NAME = "due_haptics_state"
        private const val KEY_PREFIX = "due_haptic"
        private const val KEY_LAST_GLOBAL_FIRE = "last_global_fire"
    }
}
