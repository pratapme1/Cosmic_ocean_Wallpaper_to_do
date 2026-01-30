package com.cosmicocean.viewmodel

import com.cosmicocean.data.TaskRepository
import com.cosmicocean.model.Star
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*

@ExperimentalCoroutinesApi
class QuickAddViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var mockRepository: TaskRepository
    private lateinit var viewModel: QuickAddViewModel

    @Before
    fun setup() {
        mockRepository = mock(TaskRepository::class.java)
        Dispatchers.setMain(testDispatcher)
        viewModel = QuickAddViewModel(mockRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `addTask should call repository and set success state`() = runTest {
        val title = "Call mom"
        
        // Use Elvis operator to satisfy Kotlin's non-null parameter check
        // while recording the Mockito matcher.
        `when`(mockRepository.addStar(any(Star::class.java) ?: Star(0f, 0f, "", 0, null))).thenReturn(Unit)
        
        viewModel.addTask(title, 1080f, 1920f)

        verify(mockRepository).addStar(any(Star::class.java) ?: Star(0f, 0f, "", 0, null))
        assertTrue("State should be success", viewModel.uiState.value.isSuccess)
    }
}
