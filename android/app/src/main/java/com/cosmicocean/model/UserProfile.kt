package com.cosmicocean.model

data class UserProfile(
    val id: String,
    val email: String,
    val theme: String = "cosmic",
    val resolution: String = "1080x1920",
    val doneForToday: Boolean = false,
    val doneForTodayAt: Long? = null
)
