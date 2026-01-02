package com.cosmicocean.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.cosmicocean.data.TaskRepository
import com.cosmicocean.effects.ConstellationSystem
import com.cosmicocean.effects.OrbitalSystem
import com.cosmicocean.physics.VerletEngine
import com.cosmicocean.systems.CommandHistory

class MainViewModelFactory(
    private val repository: TaskRepository,
    private val engine: VerletEngine,
    private val constellationSystem: ConstellationSystem,
    private val orbitalSystem: OrbitalSystem,
    private val commandHistory: CommandHistory
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository, engine, constellationSystem, orbitalSystem, commandHistory) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
