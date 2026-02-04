package com.cosmicocean.viewmodel

import com.cosmicocean.model.UserPreferencesResponse
import com.cosmicocean.network.ApiService
import com.cosmicocean.ui.state.*
import com.cosmicocean.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.*
import retrofit2.Response

@ExperimentalCoroutinesApi
class EnvironmentSettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var mockApi: ApiService
    private lateinit var mockPrefs: com.cosmicocean.utils.WallpaperPreferencesManager
    private lateinit var viewModel: EnvironmentSettingsViewModel

    @Before
    fun setup() {
        mockApi = mock(ApiService::class.java)
        mockPrefs = mock(com.cosmicocean.utils.WallpaperPreferencesManager::class.java)
        Dispatchers.setMain(testDispatcher)
        viewModel = EnvironmentSettingsViewModel(mockApi, mockPrefs)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadPreferences should update state on success`() = runTest {
        val apiPrefs = UserPreferencesResponse(
            timeOfDayMode = "manual",
            manualTimePeriod = "evening"
        )
        `when`(mockApi.getPreferences()).thenReturn(Response.success(apiPrefs))

        viewModel.loadPreferences()
        
        val prefs = viewModel.uiState.value.preferences
        assertEquals(TimeOfDayMode.MANUAL, prefs.timeOfDayMode)
        assertEquals("evening", prefs.manualTimePeriod)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `updatePreference should trigger API call`() = runTest {
        `when`(mockApi.updatePreferences(anyPrefsMap())).thenReturn(Response.success(UserPreferencesResponse()))

        viewModel.updatePreference("time_of_day_mode", "manual")

        if (BuildConfig.LOCAL_ONLY) {
            verify(mockApi, never()).updatePreferences(anyPrefsMap())
        } else {
            verify(mockApi).updatePreferences(anyPrefsMap())
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun anyPrefsMap(): Map<String, Any> {
        return ArgumentMatchers.anyMap<String, Any>() as Map<String, Any>
    }
}
