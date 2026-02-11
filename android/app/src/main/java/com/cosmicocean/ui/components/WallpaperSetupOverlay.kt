package com.cosmicocean.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag

import androidx.compose.ui.platform.LocalContext
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.util.Log

@Composable
fun WallpaperSetupOverlay(
    onSetupClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    fun triggerWallpaperPicker() {
        try {
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(context, "com.cosmicocean.service.CosmicLiveWallpaperService")
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            onSetupClick() // Also run the callback to update preferences and advance tutorial
        } catch (e: Exception) {
            Log.e("WallpaperSetupOverlay", "Failed to launch live wallpaper picker", e)
            // Fallback: just run the callback
            onSetupClick()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(24.dp)
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color(0xFF1A1A2E), Color(0xFF0F0F1E))
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .border(
                    width = 1.dp,
                    color = Color(0xFF00E5FF).copy(alpha = 0.3f),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "🌌",
                fontSize = 48.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "Live Wallpaper Setup",
                fontSize = 24.sp,
                fontWeight = FontWeight.Light,
                color = Color(0xFF00E5FF),
                textAlign = TextAlign.Center,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "To fully experience the Cosmic Ocean, set it as your system wallpaper. This allows your lock screen to reflect your current tasks and urgency in real-time.",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = { triggerWallpaperPicker() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("wallpaper_setup_confirm_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E5FF),
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text(
                    text = "Setup Wallpaper",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("wallpaper_setup_dismiss_button")
            ) {
                Text("Not Now", color = Color.White.copy(alpha = 0.3f))
            }
        }
    }
}
