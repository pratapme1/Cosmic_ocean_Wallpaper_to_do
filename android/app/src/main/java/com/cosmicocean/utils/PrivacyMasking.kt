package com.cosmicocean.utils

import android.content.Context
import com.cosmicocean.data.PrivacyLevel
import com.cosmicocean.data.PrivacyPreferencesRepository
import com.cosmicocean.data.StarEntity
import kotlinx.coroutines.flow.first

data class PrivacyMaskedTasks(
    val tasks: List<StarEntity>,
    val totalTaskCount: Int
)

suspend fun applyWallpaperPrivacy(
    context: Context,
    tasks: List<StarEntity>,
    totalTaskCount: Int
): PrivacyMaskedTasks {
    val prefs = PrivacyPreferencesRepository(context).preferencesFlow.first()

    if (prefs.hideAllTasksMode || prefs.defaultPrivacyLevel == PrivacyLevel.HIDDEN) {
        return PrivacyMaskedTasks(emptyList(), 0)
    }

    val maskedTasks = tasks.map { task ->
        task.copy(title = maskTitle(task.title, prefs.defaultPrivacyLevel))
    }

    return PrivacyMaskedTasks(maskedTasks, totalTaskCount)
}

private fun maskTitle(title: String, level: PrivacyLevel): String {
    return when (level) {
        PrivacyLevel.PUBLIC -> title
        PrivacyLevel.INITIALS -> {
            val trimmed = title.trim()
            val initial = trimmed.firstOrNull()?.uppercaseChar() ?: 'T'
            "$initial..."
        }
        PrivacyLevel.CATEGORY -> "Task"
        PrivacyLevel.CUSTOM -> "Private task"
        PrivacyLevel.HIDDEN -> ""
    }
}
