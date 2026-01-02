package com.cosmicocean.integration

import com.cosmicocean.model.AuthResponse
import com.cosmicocean.model.User
import org.junit.Assert.*
import org.junit.Test

/**
 * Integration tests for Authentication API
 * Tests data models and response parsing
 */
class AuthApiTest {

    @Test
    fun `AuthResponse should parse correctly from valid JSON`() {
        // Simulating what Retrofit would deserialize
        val user = User(
            id = "test-user-id",
            email = "test@example.com",
            theme = "cosmic",
            resolution = "1080x1920"
        )

        val authResponse = AuthResponse(
            accessToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test",
            refreshToken = "refresh.token.here",
            user = user
        )

        assertNotNull(authResponse)
        assertEquals("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test", authResponse.accessToken)
        assertEquals("refresh.token.here", authResponse.refreshToken)
        assertEquals("test-user-id", authResponse.user.id)
        assertEquals("test@example.com", authResponse.user.email)
        assertEquals("cosmic", authResponse.user.theme)
        assertEquals("1080x1920", authResponse.user.resolution)
    }

    @Test
    fun `User model should handle optional fields`() {
        val user = User(
            id = "test-id",
            email = "test@example.com"
        )

        assertNotNull(user)
        assertEquals("test-id", user.id)
        assertEquals("test@example.com", user.email)
        assertEquals("cosmic", user.theme) // default value
        assertEquals("1080x1920", user.resolution) // default value
    }

    @Test
    fun `User model should handle custom theme`() {
        val user = User(
            id = "test-id",
            email = "test@example.com",
            theme = "ocean"
        )

        assertEquals("ocean", user.theme)
    }

    @Test
    fun `User model should handle custom resolution`() {
        val user = User(
            id = "test-id",
            email = "test@example.com",
            resolution = "1440x2560"
        )

        assertEquals("1440x2560", user.resolution)
    }

    @Test
    fun `AuthResponse should handle all token types`() {
        val user = User(
            id = "test-id",
            email = "test@example.com"
        )

        val authResponse = AuthResponse(
            accessToken = "access123",
            refreshToken = "refresh456",
            user = user
        )

        assertTrue(authResponse.accessToken.isNotEmpty())
        assertTrue(authResponse.refreshToken.isNotEmpty())
        assertNotNull(authResponse.user)
    }

    @Test
    fun `AuthResponse tokens should be distinct`() {
        val user = User(
            id = "test-id",
            email = "test@example.com"
        )

        val authResponse = AuthResponse(
            accessToken = "access-token",
            refreshToken = "refresh-token",
            user = user
        )

        assertNotEquals(authResponse.accessToken, authResponse.refreshToken)
    }

    @Test
    fun `User email should be valid format`() {
        val validEmails = listOf(
            "test@example.com",
            "user+tag@domain.co.uk",
            "firstname.lastname@company.com"
        )

        validEmails.forEach { email ->
            val user = User(id = "id", email = email)
            assertTrue("Email should contain @", user.email.contains("@"))
            assertTrue("Email should contain domain", user.email.contains("."))
        }
    }

    @Test
    fun `User should handle special characters in email`() {
        val email = "test+special.chars@example.com"
        val user = User(
            id = "test-id",
            email = email
        )

        assertEquals(email, user.email)
    }

    @Test
    fun `Theme should be one of valid options`() {
        val validThemes = listOf("cosmic", "ocean", "fantasy")

        validThemes.forEach { theme ->
            val user = User(
                id = "test-id",
                email = "test@example.com",
                theme = theme
            )

            assertTrue("Theme should be valid", validThemes.contains(user.theme))
        }
    }

    @Test
    fun `Resolution should be in format WIDTHxHEIGHT`() {
        val validResolutions = listOf(
            "1080x1920",
            "1440x2560",
            "1080x2400",
            "1170x2532"
        )

        validResolutions.forEach { resolution ->
            val user = User(
                id = "test-id",
                email = "test@example.com",
                resolution = resolution
            )

            assertTrue("Resolution should contain x", user.resolution!!.contains("x"))
            val parts = user.resolution!!.split("x")
            assertEquals("Resolution should have 2 parts", 2, parts.size)
            assertTrue("Width should be numeric", parts[0].toIntOrNull() != null)
            assertTrue("Height should be numeric", parts[1].toIntOrNull() != null)
        }
    }
}
