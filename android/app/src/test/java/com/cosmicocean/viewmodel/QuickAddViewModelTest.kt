package com.cosmicocean.viewmodel

import com.cosmicocean.data.TaskRepository
import com.cosmicocean.model.ParsedTaskResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@ExperimentalCoroutinesApi
class QuickAddViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var mockRepository: TaskRepository
    private lateinit var viewModel: QuickAddViewModel

    @Before
    fun setup() {
        mockRepository = mock()
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

        `when`(mockRepository.parseTaskInput(title)).thenReturn(
            ParsedTaskResult(
                title = "Call mom",
                dueDate = null,
                dueTime = null,
                estimateMinutes = null,
                priority = 2,
                category = null,
                energyLevel = "medium",
                contextTags = emptyList(),
                isRecurring = false,
                recurringPattern = null,
                confidence = 0.8,
                source = "local_parser",
                reason = "local_only"
            )
        )
        `when`(mockRepository.addStar(anyNonNull())).thenReturn("local-id")

        viewModel.addTask(title, 1080f, 1920f)
        advanceUntilIdle()

        verify(mockRepository).parseTaskInput(title)
        verify(mockRepository).addStar(anyNonNull())
        assertTrue("State should be success", viewModel.uiState.value.isSuccess)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyNonNull(): T {
        return ArgumentMatchers.any<T>()
    }
}
