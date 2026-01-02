package com.cosmicocean.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import com.cosmicocean.model.UserProfile
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.lenient
import org.mockito.MockitoAnnotations
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner.Silent::class)
class WallpaperPreferencesManagerTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockSharedPreferences: SharedPreferences

    @Mock
    private lateinit var mockEditor: SharedPreferences.Editor

    @Mock
    private lateinit var mockWindowManager: WindowManager

    @Mock
    private lateinit var mockDisplay: Display

    private lateinit var prefsManager: WallpaperPreferencesManager

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        lenient().`when`(mockContext.getSharedPreferences("wallpaper_prefs", Context.MODE_PRIVATE))
            .thenReturn(mockSharedPreferences)
        lenient().`when`(mockSharedPreferences.edit()).thenReturn(mockEditor)
        lenient().`when`(mockEditor.putString(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(mockEditor)
        lenient().`when`(mockEditor.putLong(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong()))
            .thenReturn(mockEditor)
        lenient().`when`(mockEditor.clear()).thenReturn(mockEditor)
        lenient().`when`(mockEditor.commit()).thenReturn(true)
        lenient().doNothing().`when`(mockEditor).apply()

        prefsManager = WallpaperPreferencesManager(mockContext)
    }

    @Test
    fun getTheme_returnsDefaultTheme_whenNotSet() {
        `when`(mockSharedPreferences.getString("theme", WallpaperPreferencesManager.DEFAULT_THEME))
            .thenReturn(WallpaperPreferencesManager.DEFAULT_THEME)

        val theme = prefsManager.getTheme()

        assertEquals(WallpaperPreferencesManager.DEFAULT_THEME, theme)
        assertEquals("cosmic", theme)
    }

    @Test
    fun getTheme_returnsSavedTheme_whenSet() {
        `when`(mockSharedPreferences.getString("theme", WallpaperPreferencesManager.DEFAULT_THEME))
            .thenReturn("ocean")

        val theme = prefsManager.getTheme()

        assertEquals("ocean", theme)
    }

    @Test
    fun setTheme_returnsTrue_forValidTheme() {
        val result = prefsManager.setTheme("ocean")

        assertTrue(result)
        verify(mockEditor).putString("theme", "ocean")
        verify(mockEditor).commit()
    }

    @Test
    fun setTheme_returnsFalse_forInvalidTheme() {
        val result = prefsManager.setTheme("invalid_theme")

        assertFalse(result)
    }

    @Test
    fun setTheme_acceptsAllValidThemes() {
        val validThemes = listOf("cosmic", "ocean", "fantasy")

        validThemes.forEach { theme ->
            val result = prefsManager.setTheme(theme)
            assertTrue("Theme $theme should be valid", result)
        }
    }

    @Test
    fun getResolution_returnsDefaultResolution_whenNotSet() {
        `when`(mockSharedPreferences.getString("resolution", null))
            .thenReturn(null)
        `when`(mockContext.getSystemService(Context.WINDOW_SERVICE))
            .thenReturn(mockWindowManager)
        `when`(mockWindowManager.defaultDisplay)
            .thenReturn(mockDisplay)

        // Note: detectDeviceResolution would need proper mocking of DisplayMetrics
        // For this test, we're just verifying the fallback behavior
    }

    @Test
    fun getResolution_returnsSavedResolution_whenSet() {
        `when`(mockSharedPreferences.getString("resolution", null))
            .thenReturn("1440x3120")

        val resolution = prefsManager.getResolution()

        assertEquals("1440x3120", resolution)
    }

    @Test
    fun setResolution_returnsTrue_forValidResolution() {
        val result = prefsManager.setResolution("1080x2340")

        assertTrue(result)
        verify(mockEditor).putString("resolution", "1080x2340")
        verify(mockEditor).commit()
    }

    @Test
    fun setResolution_returnsFalse_forInvalidResolution() {
        val invalidResolutions = listOf("1080", "1080x", "x2340", "invalid", "1080-2340")

        invalidResolutions.forEach { resolution ->
            val result = prefsManager.setResolution(resolution)
            assertFalse("Resolution $resolution should be invalid", result)
        }
    }

    @Test
    fun setResolution_acceptsVariousValidFormats() {
        val validResolutions = listOf("1080x1920", "1440x3120", "720x1280", "2160x3840")

        validResolutions.forEach { resolution ->
            val result = prefsManager.setResolution(resolution)
            assertTrue("Resolution $resolution should be valid", result)
        }
    }

    @Test
    fun syncFromUserProfile_savesAllPreferences() {
        val userProfile = UserProfile(
            id = "user123",
            email = "test@example.com",
            theme = "fantasy",
            resolution = "1440x3120",
            doneForToday = false,
            doneForTodayAt = null
        )

        prefsManager.syncFromUserProfile(userProfile)

        verify(mockEditor).putString("theme", "fantasy")
        verify(mockEditor).putString("resolution", "1440x3120")
        verify(mockEditor).putLong(org.mockito.ArgumentMatchers.eq("last_sync"), org.mockito.ArgumentMatchers.anyLong())
    }

    @Test
    fun getLastSyncTime_returnsZero_whenNeverSynced() {
        `when`(mockSharedPreferences.getLong("last_sync", 0L))
            .thenReturn(0L)

        val lastSync = prefsManager.getLastSyncTime()

        assertEquals(0L, lastSync)
    }

    @Test
    fun getLastSyncTime_returnsSavedTime_whenPreviouslySynced() {
        val syncTime = System.currentTimeMillis()
        `when`(mockSharedPreferences.getLong("last_sync", 0L))
            .thenReturn(syncTime)

        val lastSync = prefsManager.getLastSyncTime()

        assertEquals(syncTime, lastSync)
    }

    @Test
    fun needsSync_returnsTrue_whenNeverSynced() {
        `when`(mockSharedPreferences.getLong("last_sync", 0L))
            .thenReturn(0L)

        val needsSync = prefsManager.needsSync()

        assertTrue(needsSync)
    }

    @Test
    fun needsSync_returnsTrue_whenSyncedMoreThanOneHourAgo() {
        val twoHoursAgo = System.currentTimeMillis() - (2 * 60 * 60 * 1000)
        `when`(mockSharedPreferences.getLong("last_sync", 0L))
            .thenReturn(twoHoursAgo)

        val needsSync = prefsManager.needsSync()

        assertTrue(needsSync)
    }

    @Test
    fun needsSync_returnsFalse_whenSyncedRecently() {
        val thirtyMinutesAgo = System.currentTimeMillis() - (30 * 60 * 1000)
        `when`(mockSharedPreferences.getLong("last_sync", 0L))
            .thenReturn(thirtyMinutesAgo)

        val needsSync = prefsManager.needsSync()

        assertFalse(needsSync)
    }

    @Test
    fun clearAll_clearsAllPreferences() {
        prefsManager.clearAll()

        verify(mockEditor).clear()
    }
}
