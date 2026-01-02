package com.cosmicocean.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.Stable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Audio Engine - SoundPool-based audio playback with spatial positioning
 * PWA-accurate: Manages sound effects with volume control and spatial audio
 */
@Stable
class AudioEngine(private val context: Context) {

    private val soundPool: SoundPool
    private val soundMap = mutableMapOf<SoundType, Int>()
    private var masterVolume: Float = 0.7f
    private var isMuted: Boolean = false

    // Screen dimensions for spatial positioning
    private var screenWidth: Float = 1080f
    private var screenHeight: Float = 1920f

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(10)
            .setAudioAttributes(audioAttributes)
            .build()

        loadSounds()
    }

    /**
     * Load all sound effects
     */
    private fun loadSounds() {
        // Map sound types to resource IDs
        // Note: These resources need to be added to res/raw/
        try {
            soundMap[SoundType.TASK_COMPLETE] = loadSound("task_complete")
            soundMap[SoundType.TASK_ARCHIVE] = loadSound("task_archive")
            soundMap[SoundType.TASK_CREATE] = loadSound("task_create")
            soundMap[SoundType.SWIRL_PROGRESS] = loadSound("swirl_progress")
            soundMap[SoundType.SWIRL_COMPLETE] = loadSound("swirl_complete")
            soundMap[SoundType.TROPHY_UNLOCK] = loadSound("trophy_unlock")
            soundMap[SoundType.TAP] = loadSound("tap")
            soundMap[SoundType.DRAG_START] = loadSound("drag_start")
            soundMap[SoundType.DRAG_END] = loadSound("drag_end")
            soundMap[SoundType.UNDO] = loadSound("undo")
        } catch (e: Exception) {
            // Sound files not found - silently fail (sounds are optional)
            e.printStackTrace()
        }
    }

    /**
     * Load a sound file by name
     */
    private fun loadSound(soundName: String): Int {
        val resourceId = context.resources.getIdentifier(
            soundName,
            "raw",
            context.packageName
        )

        return if (resourceId != 0) {
            soundPool.load(context, resourceId, 1)
        } else {
            -1 // Sound not found
        }
    }

    /**
     * Play a sound effect
     */
    fun play(
        soundType: SoundType,
        volume: Float = 1f,
        rate: Float = 1f,
        loop: Boolean = false
    ) {
        if (isMuted) return

        val soundId = soundMap[soundType] ?: return
        if (soundId == -1) return

        val finalVolume = masterVolume * volume
        val loopMode = if (loop) -1 else 0

        soundPool.play(
            soundId,
            finalVolume,
            finalVolume,
            1,
            loopMode,
            rate
        )
    }

    /**
     * Play a sound with spatial positioning
     * Position is relative to screen coordinates
     */
    fun playSpatial(
        soundType: SoundType,
        x: Float,
        y: Float,
        volume: Float = 1f,
        rate: Float = 1f
    ) {
        if (isMuted) return

        val soundId = soundMap[soundType] ?: return
        if (soundId == -1) return

        // Calculate stereo pan based on x position (-1.0 left, 1.0 right)
        val centerX = screenWidth / 2f
        val pan = ((x - centerX) / centerX).coerceIn(-1f, 1f)

        // Calculate volume based on distance from center
        val centerY = screenHeight / 2f
        val dx = x - centerX
        val dy = y - centerY
        val distance = sqrt(dx * dx + dy * dy)
        val maxDistance = sqrt(centerX * centerX + centerY * centerY)
        val distanceFactor = 1f - (distance / maxDistance).coerceIn(0f, 1f)

        // Apply distance falloff (inverse square law, but clamped)
        val spatialVolume = max(0.3f, distanceFactor.pow(0.5f))

        val leftVolume = masterVolume * volume * spatialVolume * (1f - max(0f, pan))
        val rightVolume = masterVolume * volume * spatialVolume * (1f + min(0f, pan))

        soundPool.play(
            soundId,
            leftVolume,
            rightVolume,
            1,
            0,
            rate
        )
    }

    /**
     * Set master volume (0.0 to 1.0)
     */
    fun setMasterVolume(volume: Float) {
        masterVolume = volume.coerceIn(0f, 1f)
    }

    /**
     * Get master volume
     */
    fun getMasterVolume(): Float = masterVolume

    /**
     * Mute/unmute audio
     */
    fun setMuted(muted: Boolean) {
        isMuted = muted
    }

    /**
     * Check if audio is muted
     */
    fun isMuted(): Boolean = isMuted

    /**
     * Update screen dimensions for spatial audio
     */
    fun updateScreenSize(width: Float, height: Float) {
        screenWidth = width
        screenHeight = height
    }

    /**
     * Release resources
     */
    fun release() {
        soundPool.release()
        soundMap.clear()
    }

    /**
     * Pause all sounds
     */
    fun pauseAll() {
        soundPool.autoPause()
    }

    /**
     * Resume all sounds
     */
    fun resumeAll() {
        soundPool.autoResume()
    }
}

/**
 * Sound effect types
 */
enum class SoundType {
    TASK_COMPLETE,
    TASK_ARCHIVE,
    TASK_CREATE,
    SWIRL_PROGRESS,
    SWIRL_COMPLETE,
    TROPHY_UNLOCK,
    TAP,
    DRAG_START,
    DRAG_END,
    UNDO
}

/**
 * Audio preferences
 */
data class AudioPreferences(
    val masterVolume: Float = 0.7f,
    val isMuted: Boolean = false,
    val spatialAudioEnabled: Boolean = true
)
