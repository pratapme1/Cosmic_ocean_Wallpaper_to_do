package com.cosmicocean.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cosmicocean.utils.ClockUtils
import kotlinx.coroutines.delay
import java.util.*

@Composable
fun ClockOverlay(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val is24h = DateFormat.is24HourFormat(context)

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis()
            // Update every minute (or every second if we want a pulsing dot)
            // Ticking every second for smoother state but minute is enough for clock
            delay(1000)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Time
        Text(
            text = ClockUtils.formatTime(currentTime, is24h),
            color = Color.White,
            fontSize = 64.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = (-2).sp
        )
        
        // Date
        Text(
            text = ClockUtils.formatDate(currentTime),
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 16.sp,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
