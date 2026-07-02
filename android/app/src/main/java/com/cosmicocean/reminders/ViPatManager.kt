package com.cosmicocean.reminders

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores the GitHub Personal Access Token used to fetch Vi reminders.
 * Same EncryptedSharedPreferences setup as TokenManager, kept in a
 * separate prefs file so clearing auth tokens does not wipe the PAT.
 */
class ViPatManager(context: Context, private val prefs: SharedPreferences? = null) {

    private val finalPrefs: SharedPreferences by lazy {
        prefs ?: createEncryptedPrefs(context)
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            "cosmic_vi_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    companion object {
        private const val KEY_GITHUB_PAT = "github_pat"
    }

    fun savePat(pat: String) {
        finalPrefs.edit().putString(KEY_GITHUB_PAT, pat.trim()).apply()
    }

    fun getPat(): String? {
        return finalPrefs.getString(KEY_GITHUB_PAT, null)?.takeIf { it.isNotBlank() }
    }

    fun hasPat(): Boolean = getPat() != null

    fun clearPat() {
        finalPrefs.edit().remove(KEY_GITHUB_PAT).apply()
    }
}
