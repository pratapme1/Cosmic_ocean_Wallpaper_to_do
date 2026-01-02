package com.cosmicocean.model

data class WeeklyStats(
    val weekStart: String,
    val tasksCompleted: Int = 0,
    val tasksCompletedViaWidget: Int = 0,
    val appOpens: Int = 0
)
