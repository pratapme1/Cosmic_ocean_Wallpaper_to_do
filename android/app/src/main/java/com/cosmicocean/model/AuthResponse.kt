package com.cosmicocean.model

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: User
)

data class User(
    val id: String,
    val email: String,
    val theme: String? = "cosmic",
    val resolution: String? = "1080x1920"
)
