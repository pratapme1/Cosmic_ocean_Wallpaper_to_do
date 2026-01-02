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
                // Sync stars list with repository
                // In a real app, we would handle merges more carefully
                stars.clear()
                loadedStars.forEach { star ->
                    stars.add(star)
                    engine.addParticle(star.particle)
                }
            }
        }
    }

    fun addStar(star: Star, parent: Star? = null) {
        viewModelScope.launch {
            repository.addStar(star)
            if (parent != null) {
                orbitalSystem.createOrbit(parent, star)
            }
        }
    }

    fun completeStar(star: Star) {
        viewModelScope.launch {
            android.util.Log.d("MainViewModel", "completeStar called for: ${star.title}")
            star.isCompleted = true
            star.completedAt = System.currentTimeMillis()
            android.util.Log.d("MainViewModel", "Calling updateStar with isCompleted=${star.isCompleted}")
            repository.updateStar(star)
            android.util.Log.d("MainViewModel", "updateStar completed")
        }
    }

    fun archiveStar(star: Star) {
        viewModelScope.launch {
            star.isArchived = true
            star.archivedAt = System.currentTimeMillis()
            repository.updateStar(star)
        }
    }

    fun deleteStar(star: Star) {
        viewModelScope.launch {
            android.util.Log.d("MainViewModel", "deleteStar called for: ${star.title}")
            // Remove from in-memory list
            stars.remove(star)
            // Remove from physics engine
            engine.removeParticle(star.particle)
            // Delete from Room DB and backend API
            repository.deleteStar(star)
            android.util.Log.d("MainViewModel", "deleteStar completed")
        }
    }
}
