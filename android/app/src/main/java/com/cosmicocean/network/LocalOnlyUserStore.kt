package com.cosmicocean.network

import android.content.Context
import java.util.UUID

data class LocalOnlyUser(
    val id: String,
    val email: String,
    val theme: String,
    val resolution: String,
    val wallpaperToken: String,
    val doneForToday: Boolean,
    val doneForTodayAt: Long?
)

class LocalOnlyUserStore(context: Context) {
    private val prefs = context.getSharedPreferences("local_only_user", Context.MODE_PRIVATE)

    fun getUser(): LocalOnlyUser? {
        val id = prefs.getString(KEY_ID, null) ?: return null
        val email = prefs.getString(KEY_EMAIL, null) ?: return null
        val theme = prefs.getString(KEY_THEME, DEFAULT_THEME) ?: DEFAULT_THEME
        val resolution = prefs.getString(KEY_RESOLUTION, DEFAULT_RESOLUTION) ?: DEFAULT_RESOLUTION
        val wallpaperToken = prefs.getString(KEY_WALLPAPER_TOKEN, null) ?: generateWallpaperToken()
        val doneForToday = prefs.getBoolean(KEY_DONE_FOR_TODAY, false)
        val doneForTodayAt = prefs.getLong(KEY_DONE_FOR_TODAY_AT, 0L).takeIf { it > 0L }

        return LocalOnlyUser(
            id = id,
            email = email,
            theme = theme,
            resolution = resolution,
            wallpaperToken = wallpaperToken,
            doneForToday = doneForToday,
            doneForTodayAt = doneForTodayAt
        )
    }

    fun getOrCreateUser(email: String = DEFAULT_EMAIL): LocalOnlyUser {
        return getUser() ?: createUser(email)
    }

    fun createUser(email: String): LocalOnlyUser {
        val user = LocalOnlyUser(
            id = UUID.randomUUID().toString(),
            email = email,
            theme = DEFAULT_THEME,
            resolution = DEFAULT_RESOLUTION,
            wallpaperToken = generateWallpaperToken(),
            doneForToday = false,
            doneForTodayAt = null
        )
        saveUser(user)
        return user
    }

    fun updateUser(updates: Map<String, String>): LocalOnlyUser {
        val existing = getOrCreateUser()
        val updated = existing.copy(
            theme = updates["theme"] ?: existing.theme,
            resolution = updates["resolution"] ?: existing.resolution
        )
        saveUser(updated)
        return updated
    }

    fun markDoneForToday(): LocalOnlyUser {
        val existing = getOrCreateUser()
        val updated = existing.copy(
            doneForToday = true,
            doneForTodayAt = System.currentTimeMillis()
        )
        saveUser(updated)
        return updated
    }

    fun regenerateWallpaperToken(): LocalOnlyUser {
        val existing = getOrCreateUser()
        val updated = existing.copy(wallpaperToken = generateWallpaperToken())
        saveUser(updated)
        return updated
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun saveUser(user: LocalOnlyUser) {
        prefs.edit()
            .putString(KEY_ID, user.id)
            .putString(KEY_EMAIL, user.email)
            .putString(KEY_THEME, user.theme)
            .putString(KEY_RESOLUTION, user.resolution)
            .putString(KEY_WALLPAPER_TOKEN, user.wallpaperToken)
            .putBoolean(KEY_DONE_FOR_TODAY, user.doneForToday)
            .putLong(KEY_DONE_FOR_TODAY_AT, user.doneForTodayAt ?: 0L)
            .apply()
    }

    private fun generateWallpaperToken(): String = UUID.randomUUID().toString()

    companion object {
        private const val KEY_ID = "user_id"
        private const val KEY_EMAIL = "email"
        private const val KEY_THEME = "theme"
        private const val KEY_RESOLUTION = "resolution"
        private const val KEY_WALLPAPER_TOKEN = "wallpaper_token"
        private const val KEY_DONE_FOR_TODAY = "done_for_today"
        private const val KEY_DONE_FOR_TODAY_AT = "done_for_today_at"

        private const val DEFAULT_EMAIL = "local@device"
        private const val DEFAULT_THEME = "cosmic"
        private const val DEFAULT_RESOLUTION = "1080x1920"
    }
}
