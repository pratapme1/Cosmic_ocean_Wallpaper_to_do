package com.cosmicocean.utils

import android.content.Context
import android.content.SharedPreferences

object UserSession {
    private const val PREFS_NAME = "cosmic_prefs"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_EMAIL = "user_email"

    fun init(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveUser(context: Context, userId: String, email: String) {
        val prefs = init(context)
        prefs.edit().apply {
            putString(KEY_USER_ID, userId)
            putString(KEY_EMAIL, email)
            apply()
        }
    }

    fun getUserId(context: Context): String? {
        return init(context).getString(KEY_USER_ID, null)
    }

    fun isLoggedIn(context: Context): Boolean {
        return getUserId(context) != null
    }
    
    fun clear(context: Context) {
        init(context).edit().clear().apply()
    }
}
