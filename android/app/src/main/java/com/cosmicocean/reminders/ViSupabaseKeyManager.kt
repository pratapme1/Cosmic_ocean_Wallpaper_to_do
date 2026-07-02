package com.cosmicocean.reminders

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores the Supabase anon key used for the Vi reminders table.
 * Same EncryptedSharedPreferences file that used to hold the GitHub PAT;
 * the stale PAT entry is dropped the first time a key is saved.
 */
class ViSupabaseKeyManager(context: Context, private val prefs: SharedPreferences? = null) {

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
        private const val KEY_SUPABASE_ANON = "supabase_anon_key"
        private const val LEGACY_KEY_GITHUB_PAT = "github_pat"
    }

    fun saveKey(key: String) {
        finalPrefs.edit()
            .putString(KEY_SUPABASE_ANON, key.trim())
            .remove(LEGACY_KEY_GITHUB_PAT)
            .apply()
    }

    fun getKey(): String? {
        return finalPrefs.getString(KEY_SUPABASE_ANON, null)?.takeIf { it.isNotBlank() }
    }

    fun hasKey(): Boolean = getKey() != null

    fun clearKey() {
        finalPrefs.edit()
            .remove(KEY_SUPABASE_ANON)
            .remove(LEGACY_KEY_GITHUB_PAT)
            .apply()
    }
}
