package com.cosmicocean

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.cosmicocean.auth.TokenManager
import com.cosmicocean.network.NetworkModule
import com.cosmicocean.network.LocalOnlyUserStore
import com.cosmicocean.ui.components.AuthScreen
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.util.TimeZone

class LoginActivity : ComponentActivity() {
    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tokenManager = TokenManager(this)

        if (com.cosmicocean.BuildConfig.LOCAL_ONLY && !tokenManager.isLoggedIn()) {
            val userStore = LocalOnlyUserStore(this)
            val user = userStore.getOrCreateUser()
            tokenManager.saveTokens(
                "local-access-${user.id}",
                "local-refresh-${user.id}",
                user.id,
                user.email
            )
            navigateToMain()
            return
        }

        // Check if already logged in
        if (tokenManager.isLoggedIn()) {
            navigateToMain()
            return
        }

        setContent {
            MaterialTheme {
                var errorMessage by remember { mutableStateOf<String?>(null) }
                var isLoading by remember { mutableStateOf(false) }

                AuthScreen(
                    onLogin = { email, password ->
                        if (isLoading) return@AuthScreen
                        performLogin(email, password) { error ->
                            errorMessage = error
                            isLoading = false
                        }
                        isLoading = true
                    },
                    onRegister = { email, password ->
                        if (isLoading) return@AuthScreen
                        performRegister(email, password) { error ->
                            errorMessage = error
                            isLoading = false
                        }
                        isLoading = true
                    },
                    onSkipToGuest = {
                        navigateToMain()
                    },
                    onForgotPassword = { email ->
                        performForgotPassword(email)
                    },
                    errorMessage = errorMessage
                )
            }
        }
    }

    private fun performLogin(email: String, password: String, onError: (String?) -> Unit) {
        lifecycleScope.launch {
            try {
                val response = NetworkModule.getApi(this@LoginActivity).login(
                    mapOf(
                        "email" to email,
                        "password" to password
                    )
                )

                if (response.isSuccessful && response.body() != null) {
                    val authResponse = response.body()!!
                    tokenManager.saveTokens(
                        authResponse.accessToken,
                        authResponse.refreshToken,
                        authResponse.user.id,
                        authResponse.user.email
                    )
                    navigateToMain()
                } else {
                    // Check for wrong password (401 Unauthorized)
                    when (response.code()) {
                        401 -> onError("❌ Incorrect email or password")
                        404 -> onError("❌ User not found. Please register first.")
                        else -> onError("❌ Login failed. Please try again.")
                    }
                }
            } catch (e: HttpException) {
                when (e.code()) {
                    401 -> onError("❌ Incorrect email or password")
                    404 -> onError("❌ User not found. Please register first.")
                    else -> onError("❌ Network error: ${e.message()}")
                }
            } catch (e: Exception) {
                // Fallback for offline/testing - use guest mode
                Toast.makeText(
                    this@LoginActivity,
                    "⚠️ Connection error. Using guest mode.",
                    Toast.LENGTH_LONG
                ).show()
                navigateToMain()
            }
        }
    }

    private fun performRegister(email: String, password: String, onError: (String?) -> Unit) {
        lifecycleScope.launch {
            try {
                // Validate password length (must match backend requirement)
                if (password.length < 8) {
                    onError("❌ Password must be at least 8 characters")
                    return@launch
                }

                // Get device timezone (e.g., "America/New_York", "Asia/Kolkata")
                val deviceTimezone = TimeZone.getDefault().id

                val response = NetworkModule.getApi(this@LoginActivity).register(
                    mapOf(
                        "email" to email,
                        "password" to password,
                        "timezone" to deviceTimezone
                    )
                )

                if (response.isSuccessful && response.body() != null) {
                    val authResponse = response.body()!!
                    tokenManager.saveTokens(
                        authResponse.accessToken,
                        authResponse.refreshToken,
                        authResponse.user.id,
                        authResponse.user.email
                    )
                    Toast.makeText(
                        this@LoginActivity,
                        "✨ Account created successfully!",
                        Toast.LENGTH_SHORT
                    ).show()
                    navigateToMain()
                } else {
                    when (response.code()) {
                        409 -> onError("❌ Email already registered. Please login instead.")
                        400 -> onError("❌ Invalid email or password format")
                        else -> onError("❌ Registration failed. Please try again.")
                    }
                }
            } catch (e: HttpException) {
                when (e.code()) {
                    409 -> onError("❌ Email already registered. Please login instead.")
                    400 -> onError("❌ Invalid email or password format")
                    else -> onError("❌ Network error: ${e.message()}")
                }
            } catch (e: Exception) {
                // Fallback for offline/testing - use guest mode
                Toast.makeText(
                    this@LoginActivity,
                    "⚠️ Connection error. Using guest mode.",
                    Toast.LENGTH_LONG
                ).show()
                navigateToMain()
            }
        }
    }

    private fun performForgotPassword(email: String) {
        // For now, just show a toast with instructions
        // In production, this would call an API endpoint to send a reset email
        Toast.makeText(
            this,
            "📧 Password reset link sent to $email\n\nCheck your inbox and spam folder.",
            Toast.LENGTH_LONG
        ).show()

        // TODO: Implement actual password reset API call
        // lifecycleScope.launch {
        //     try {
        //         NetworkModule.getApi(this@LoginActivity).requestPasswordReset(
        //             mapOf("email" to email)
        //         )
        //     } catch (e: Exception) {
        //         // Handle error
        //     }
        // }
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
