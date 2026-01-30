package com.cosmicocean.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cosmicocean.data.TaskRepository
import com.cosmicocean.model.Star
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Random

data class QuickAddUiState(
    val isSaving: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null
)

class QuickAddViewModel(
    private val repository: TaskRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuickAddUiState())
    val uiState: StateFlow<QuickAddUiState> = _uiState.asStateFlow()

    fun addTask(title: String, screenWidth: Float, screenHeight: Float) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                // Calculate random position
                val random = Random()
                val horizontalPadding = screenWidth * 0.15f
                val verticalPadding = screenHeight * 0.1f
                
                val x = horizontalPadding + random.nextFloat() * (screenWidth - 2 * horizontalPadding)
                val y = verticalPadding + random.nextFloat() * (screenHeight - 2 * verticalPadding)
                
                // Create star (Priority default 2, no description)
                val star = Star(x, y, title, 2, null)
                
                // Save
                repository.addStar(star)
                
                _uiState.update { it.copy(isSaving = false, isSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, errorMessage = e.message) }
            }
        }
    }
}
