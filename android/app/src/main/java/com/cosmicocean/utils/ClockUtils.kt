package com.cosmicocean.utils

import java.text.SimpleDateFormat
import java.util.*

object ClockUtils {

    fun formatTime(timeMillis: Long, is24h: Boolean): String {
        val pattern = if (is24h) "HH:mm" else "hh:mm a"
        val sdf = SimpleDateFormat(pattern, Locale.ENGLISH)
        return sdf.format(Date(timeMillis))
    }

    fun formatDate(timeMillis: Long): String {
        val sdf = SimpleDateFormat("EEEE, dd MMMM", Locale.ENGLISH)
        return sdf.format(Date(timeMillis))
    }
}
