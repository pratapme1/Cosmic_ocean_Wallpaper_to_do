package com.cosmicocean.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmicocean.data.TaskRepository
import com.cosmicocean.effects.ConstellationSystem
import com.cosmicocean.effects.OrbitalSystem
import com.cosmicocean.model.Star
import com.cosmicocean.physics.VerletEngine
import com.cosmicocean.systems.CommandHistory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: TaskRepository,
    val engine: VerletEngine,
    val constellationSystem: ConstellationSystem,
    val orbitalSystem: OrbitalSystem,
    val commandHistory: CommandHistory
) : ViewModel() {

    val stars = mutableStateListOf<Star>()
    val completedStars = mutableStateListOf<Star>()

    init {
        viewModelScope.launch {
            repository.getAllActiveStars().collectLatest { loadedStars ->
                // EPIC 9 FIX: Smart merge to preserve existing star positions
                // Don't clear and recreate - update existing stars instead

                val loadedIds = loadedStars.map { it.id }.toSet()
                val existingIds = stars.map { it.id }.toSet()

                // Remove stars that no longer exist in DB
                stars.removeAll { !loadedIds.contains(it.id) }

                // Update existing stars and add new ones
                loadedStars.forEach { loadedStar ->
                    val existingStar = stars.find { it.id == loadedStar.id }
                    if (existingStar != null) {
                        // EPIC 9: Update properties but KEEP current position
                        existingStar.title = loadedStar.title
                        existingStar.urgency = loadedStar.urgency
                        existingStar.dueDate = loadedStar.dueDate
                        existingStar.isCompleted = loadedStar.isCompleted
                        existingStar.completedAt = loadedStar.completedAt
                        existingStar.isArchived = loadedStar.isArchived
                        existingStar.archivedAt = loadedStar.archivedAt
                        existingStar.updateDueIn() // Update color based on new priority
                    } else {
                        // New star - add it
                        stars.add(loadedStar)
                        engine.addParticle(loadedStar.particle)
                    }
                }
            }
        }
    }

    suspend fun addStar(star: Star, parent: Star? = null) {
        repository.addStar(star)
        if (parent != null) {
            orbitalSystem.createOrbit(parent, star)
        }
    }

    suspend fun completeStar(star: Star) {
        android.util.Log.d("MainViewModel", "completeStar called for: ${star.title}")
        star.isCompleted = true
        star.completedAt = System.currentTimeMillis()
        android.util.Log.d("MainViewModel", "Calling updateStar with isCompleted=${star.isCompleted}")
        repository.updateStar(star)
        android.util.Log.d("MainViewModel", "updateStar completed")
    }

    suspend fun archiveStar(star: Star) {
        star.isArchived = true
        star.archivedAt = System.currentTimeMillis()
        repository.updateStar(star)
    }

    suspend fun deleteStar(star: Star) {
        android.util.Log.d("MainViewModel", "deleteStar called for: ${star.title}")
        // Remove from in-memory list
        stars.remove(star)
        // Remove from physics engine
        engine.removeParticle(star.particle)
        // Delete from Room DB and backend API
        repository.deleteStar(star)
        android.util.Log.d("MainViewModel", "deleteStar completed")
    }

    suspend fun updateStar(star: Star) {
        // EPIC 9: Save star properties (including position) to DB and backend
        repository.updateStar(star)
    }
}
