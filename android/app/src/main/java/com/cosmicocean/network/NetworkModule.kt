package com.cosmicocean.network

import android.content.Context
import android.util.Log
import com.cosmicocean.auth.TokenManager
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {
    private const val TAG = "NetworkModule"
    private var baseUrl = com.cosmicocean.BuildConfig.API_BASE_URL

    // Flag to prevent concurrent token refresh
    @Volatile
    private var isRefreshing = false

    fun getApi(context: Context): ApiService {
        if (com.cosmicocean.BuildConfig.LOCAL_ONLY) {
            return LocalOnlyApiService(context.applicationContext)
        }

        val tokenManager = TokenManager(context)

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logging)
            // Auth interceptor - adds Bearer token to requests
            .addInterceptor { chain ->
                val token = tokenManager.getAccessToken()
                val request = if (token != null) {
                    chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $token")
                        .build()
                } else {
                    chain.request()
                }
                chain.proceed(request)
            }
            // Token refresh interceptor - handles 401 responses
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val response = chain.proceed(originalRequest)

                // If we get 401 Unauthorized and have a refresh token, try to refresh
                if (response.code == 401 && !isAuthEndpoint(originalRequest)) {
                    val refreshToken = tokenManager.getRefreshToken()

                    if (refreshToken != null && !isRefreshing) {
                        synchronized(this) {
                            if (!isRefreshing) {
                                isRefreshing = true
                                Log.d(TAG, "Token expired, attempting refresh...")

                                try {
                                    val newToken = refreshAccessToken(refreshToken)
                                    if (newToken != null) {
                                        // Save new token
                                        val userId = tokenManager.getUserId() ?: ""
                                        val email = tokenManager.getEmail() ?: ""
                                        tokenManager.saveTokens(newToken, refreshToken, userId, email)
                                        Log.d(TAG, "Token refreshed successfully")

                                        // Close old response and retry with new token
                                        response.close()

                                        val newRequest = originalRequest.newBuilder()
                                            .header("Authorization", "Bearer $newToken")
                                            .build()

                                        isRefreshing = false
                                        return@addInterceptor chain.proceed(newRequest)
                                    } else {
                                        Log.w(TAG, "Token refresh failed - clearing tokens")
                                        tokenManager.clearTokens()
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Token refresh error: ${e.message}")
                                    tokenManager.clearTokens()
                                } finally {
                                    isRefreshing = false
                                }
                            }
                        }
                    }
                }

                response
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    /**
     * Check if the request is to an auth endpoint (to avoid refresh loops)
     */
    private fun isAuthEndpoint(request: Request): Boolean {
        val path = request.url.encodedPath
        return path.contains("/api/auth/")
    }

    /**
     * Synchronously refresh the access token
     */
    private fun refreshAccessToken(refreshToken: String): String? {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

            val json = Gson().toJson(mapOf("refreshToken" to refreshToken))
            val body = json.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("${baseUrl}api/auth/refresh")
                .post(body)
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                val tokenResponse = Gson().fromJson(responseBody, TokenRefreshResult::class.java)
                tokenResponse?.accessToken
            } else {
                Log.w(TAG, "Refresh token request failed: ${response.code}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh token: ${e.message}")
            null
        }
    }

    /**
     * Internal class for parsing refresh response
     */
    private data class TokenRefreshResult(val accessToken: String?)
}
