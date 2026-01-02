package com.cosmicocean.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cosmicocean.audio.AudioEngine

/**
 * Audio Settings Component
 * Provides volume slider and mute toggle for the AudioEngine
 */
@Composable
fun AudioSettings(
    audioEngine: AudioEngine?,
    modifier: Modifier = Modifier
) {
    var volume by remember { mutableFloatStateOf(audioEngine?.getMasterVolume() ?: 0.7f) }
    var isMuted by remember { mutableStateOf(audioEngine?.isMuted() ?: false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFF2A2A3E),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(20.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Audio",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            // Mute toggle
            TextButton(
                onClick = {
                    isMuted = !isMuted
                    audioEngine?.setMuted(isMuted)
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (isMuted) Color(0xFFFF3B30) else Color.White
                )
            ) {
                Text(
                    text = if (isMuted) "🔇 MUTED" else "🔊 ON",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Volume slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Volume",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.width(70.dp)
            )

            Slider(
                value = volume,
                onValueChange = { newVolume ->
                    volume = newVolume
                    audioEngine?.setMasterVolume(newVolume)
                    // Unmute if volume increased from 0
                    if (newVolume > 0 && isMuted) {
                        isMuted = false
                        audioEngine?.setMuted(false)
                    }
                },
                enabled = !isMuted,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF3AA0FF),
                    activeTrackColor = Color(0xFF3AA0FF),
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                ),
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Volume percentage
            Text(
                text = "${(volume * 100).toInt()}%",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (isMuted) Color.White.copy(alpha = 0.4f) else Color.White,
                modifier = Modifier.width(45.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Additional info text
        Text(
            text = if (isMuted) "Sound effects are muted" else "Sound effects enabled",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.6f)
        )
    }
}

/**
 * Compact audio settings for settings panel
 */
@Composable
fun CompactAudioSettings(
    audioEngine: AudioEngine?,
    modifier: Modifier = Modifier
) {
    var volume by remember { mutableFloatStateOf(audioEngine?.getMasterVolume() ?: 0.7f) }
    var isMuted by remember { mutableStateOf(audioEngine?.isMuted() ?: false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Mute button
        TextButton(
            onClick = {
                isMuted = !isMuted
                audioEngine?.setMuted(isMuted)
            },
            colors = ButtonDefaults.textButtonColors(
                contentColor = if (isMuted) Color(0xFFFF3B30) else Color.White
            ),
            modifier = Modifier.size(40.dp)
        ) {
            Text(
                text = if (isMuted) "🔇" else "🔊",
                fontSize = 20.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Volume slider
        Slider(
            value = volume,
            onValueChange = { newVolume ->
                volume = newVolume
                audioEngine?.setMasterVolume(newVolume)
                if (newVolume > 0 && isMuted) {
                    isMuted = false
                    audioEngine?.setMuted(false)
                }
            },
            enabled = !isMuted,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF3AA0FF),
                activeTrackColor = Color(0xFF3AA0FF),
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
            ),
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Percentage text
        Text(
            text = "${(volume * 100).toInt()}%",
            fontSize = 14.sp,
            color = if (isMuted) Color.White.copy(alpha = 0.4f) else Color.White,
            modifier = Modifier.width(45.dp)
        )
    }
}

/**
 * Audio test button - plays a test sound
 */
@Composable
fun AudioTestButton(
    audioEngine: AudioEngine?,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = {
            audioEngine?.play(
                com.cosmicocean.audio.SoundType.TAP,
                volume = 1f
            )
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF3AA0FF),
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Text(
            text = "🔊 Test Sound",
            fontSize = 14.sp
        )
    }
}
