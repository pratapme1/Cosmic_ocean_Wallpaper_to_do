package com.cosmicocean.auth

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.mockito.junit.MockitoJUnitRunner

/**
 * Unit tests for TokenManager
 * Tests token storage, retrieval, and validation
 */
@RunWith(MockitoJUnitRunner::class)
class TokenManagerTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockSharedPreferences: SharedPreferences

    @Mock
    private lateinit var mockEditor: SharedPreferences.Editor

    private lateinit var tokenManager: TokenManager

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        `when`(mockSharedPreferences.edit()).thenReturn(mockEditor)
        `when`(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor)
        `when`(mockEditor.apply()).then { }

        tokenManager = TokenManager(mockContext, mockSharedPreferences)
    }

    @Test
    fun `saveTokens should store all tokens and user info`() {
        val accessToken = "test.access.token"
        val refreshToken = "test.refresh.token"
        val userId = "test-user-id"
        val email = "test@example.com"

        tokenManager.saveTokens(accessToken, refreshToken, userId, email)

        verify(mockEditor).putString("access_token", accessToken)
        verify(mockEditor).putString("refresh_token", refreshToken)
        verify(mockEditor).putString("user_id", userId)
        verify(mockEditor).putString("email", email)
        verify(mockEditor).apply()
    }

    @Test
    fun `getAccessToken should return stored token`() {
        val expectedToken = "test.access.token"
        `when`(mockSharedPreferences.getString("access_token", null))
            .thenReturn(expectedToken)

        val actualToken = tokenManager.getAccessToken()

        assertEquals(expectedToken, actualToken)
    }

    @Test
    fun `getAccessToken should return null when no token stored`() {
        `when`(mockSharedPreferences.getString("access_token", null))
            .thenReturn(null)

        val actualToken = tokenManager.getAccessToken()

        assertNull(actualToken)
    }

    @Test
    fun `getRefreshToken should return stored token`() {
        val expectedToken = "test.refresh.token"
        `when`(mockSharedPreferences.getString("refresh_token", null))
            .thenReturn(expectedToken)

        val actualToken = tokenManager.getRefreshToken()

        assertEquals(expectedToken, actualToken)
    }

    @Test
    fun `getUserId should return stored user ID`() {
        val expectedUserId = "test-user-id"
        `when`(mockSharedPreferences.getString("user_id", null))
            .thenReturn(expectedUserId)

        val actualUserId = tokenManager.getUserId()

        assertEquals(expectedUserId, actualUserId)
    }

    @Test
    fun `getEmail should return stored email`() {
        val expectedEmail = "test@example.com"
        `when`(mockSharedPreferences.getString("email", null))
            .thenReturn(expectedEmail)

        val actualEmail = tokenManager.getEmail()

        assertEquals(expectedEmail, actualEmail)
    }

    @Test
    fun `isLoggedIn should return true when access token exists`() {
        `when`(mockSharedPreferences.getString("access_token", null))
            .thenReturn("test.access.token")

        val result = tokenManager.isLoggedIn()

        assertTrue(result)
    }

    @Test
    fun `isLoggedIn should return false when no access token`() {
        `when`(mockSharedPreferences.getString("access_token", null))
            .thenReturn(null)

        val result = tokenManager.isLoggedIn()

        assertFalse(result)
    }

    @Test
    fun `clearTokens should clear all stored data`() {
        `when`(mockSharedPreferences.edit()).thenReturn(mockEditor)
        `when`(mockEditor.clear()).thenReturn(mockEditor)

        tokenManager.clearTokens()

        verify(mockEditor).clear()
        verify(mockEditor).apply()
    }

    @Test
    fun `saveTokens should handle empty strings`() {
        tokenManager.saveTokens("", "", "", "")

        verify(mockEditor).putString("access_token", "")
        verify(mockEditor).putString("refresh_token", "")
        verify(mockEditor).putString("user_id", "")
        verify(mockEditor).putString("email", "")
        verify(mockEditor).apply()
    }

    @Test
    fun `saveTokens should handle special characters in email`() {
        val email = "test+special@example.com"

        tokenManager.saveTokens("token1", "token2", "id", email)

        verify(mockEditor).putString("email", email)
    }

    @Test
    fun `getters should handle consecutive calls`() {
        `when`(mockSharedPreferences.getString("access_token", null))
            .thenReturn("token1")

        val token1 = tokenManager.getAccessToken()
        val token2 = tokenManager.getAccessToken()

        assertEquals(token1, token2)
        verify(mockSharedPreferences, times(2)).getString("access_token", null)
    }
}
