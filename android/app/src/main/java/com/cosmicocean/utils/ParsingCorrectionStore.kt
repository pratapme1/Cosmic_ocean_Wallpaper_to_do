package com.cosmicocean.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.math.min

class ParsingCorrectionStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private var cached: MutableMap<String, MutableMap<String, Int>>? = null

    fun recordContext(tokens: List<String>, label: String) {
        if (tokens.isEmpty() || label.isBlank()) return
        val data = loadContextCounts()
        val normalizedLabel = label.lowercase()
        val labelCounts = data.getOrPut(normalizedLabel) { mutableMapOf() }
        tokens.forEach { token ->
            labelCounts[token] = (labelCounts[token] ?: 0) + 1
        }
        saveContextCounts(data)
    }

    fun getContextBoosts(tokens: List<String>): Map<String, Float> {
        if (tokens.isEmpty()) return emptyMap()
        val data = loadContextCounts()
        val boosts = mutableMapOf<String, Float>()
        data.forEach { (label, counts) ->
            var boost = 0f
            tokens.forEach { token ->
                val count = counts[token] ?: 0
                if (count > 0) {
                    boost += min(1f, count * 0.2f)
                }
            }
            if (boost > 0f) boosts[label] = boost
        }
        return boosts
    }

    private fun loadContextCounts(): MutableMap<String, MutableMap<String, Int>> {
        cached?.let { return it }
        val raw = prefs.getString(KEY_CONTEXT_COUNTS, null) ?: return mutableMapOf<String, MutableMap<String, Int>>().also {
            cached = it
        }
        val type = object : TypeToken<MutableMap<String, MutableMap<String, Int>>>() {}.type
        val parsed: MutableMap<String, MutableMap<String, Int>> = gson.fromJson(raw, type) ?: mutableMapOf()
        cached = parsed
        return parsed
    }

    private fun saveContextCounts(data: MutableMap<String, MutableMap<String, Int>>) {
        cached = data
        prefs.edit().putString(KEY_CONTEXT_COUNTS, gson.toJson(data)).apply()
    }

    companion object {
        private const val PREFS_NAME = "parser_corrections"
        private const val KEY_CONTEXT_COUNTS = "context_token_counts"
    }
}
