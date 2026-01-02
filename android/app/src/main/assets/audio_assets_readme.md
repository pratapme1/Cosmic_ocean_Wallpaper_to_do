# Audio Assets for Cosmic Ocean

This directory should contain the following sound effect files in `.ogg` or `.mp3` format:

## Required Sound Files

### Task Interactions
- **task_complete.ogg** - Positive chime when task is completed (drag to sun)
  - Suggested: Bright, uplifting bell or chime sound
  - Duration: ~500ms
  - Reference: Success notification sound

- **task_archive.ogg** - Subtle whoosh when task is archived (drag to black hole)
  - Suggested: Soft whoosh or fade-out sound
  - Duration: ~300ms
  - Reference: Archive/dismiss sound

- **task_create.ogg** - Quick pop when new task is created
  - Suggested: Light bubble pop or soft click
  - Duration: ~200ms
  - Reference: UI creation sound

### Gestures
- **swirl_progress.ogg** - Subtle loop sound during swirl gesture
  - Suggested: Low-frequency hum or gentle swirl sound
  - Duration: ~100ms (designed to loop smoothly)
  - Reference: Progress/charging sound

- **swirl_complete.ogg** - Satisfying tone when swirl completes (snooze activated)
  - Suggested: Melodic success tone
  - Duration: ~400ms
  - Reference: Achievement sound

### Trophy System
- **trophy_unlock.ogg** - Celebratory sound for trophy unlocks
  - Suggested: Fanfare or triumphant chord
  - Duration: ~800ms
  - Reference: Achievement unlock sound

### UI Interactions
- **tap.ogg** - Subtle click for star taps
  - Suggested: Soft click or tap sound
  - Duration: ~100ms
  - Reference: Button click sound

- **drag_start.ogg** - Gentle sound when drag starts
  - Suggested: Soft grab or pick-up sound
  - Duration: ~150ms
  - Reference: Touch feedback sound

- **drag_end.ogg** - Release sound when drag ends
  - Suggested: Soft drop or release sound
  - Duration: ~150ms
  - Reference: Release feedback sound

- **undo.ogg** - Quick rewind sound for undo action
  - Suggested: Quick reverse whoosh or rewind effect
  - Duration: ~200ms
  - Reference: Undo/revert sound

## Sound Design Guidelines

- **Format**: Use OGG Vorbis format (better compression, Android native support)
- **Sample Rate**: 44.1 kHz recommended
- **Bit Depth**: 16-bit minimum
- **Channels**: Mono preferred (spatial positioning handled by AudioEngine)
- **Volume**: Normalize to -6dB to prevent clipping
- **Fade**: Add 10-20ms fade in/out to prevent clicks

## Free Sound Resources

You can find suitable sound effects from:
- [Freesound.org](https://freesound.org/) - Community sound library
- [Zapsplat.com](https://www.zapsplat.com/) - Free game sound effects
- [Kenney.nl](https://kenney.nl/assets/ui-audio) - Free UI sound pack
- [OpenGameArt.org](https://opengameart.org/) - Free game audio

## Testing

To test sounds in isolation, use the `AudioEngine.play()` method:

```kotlin
audioEngine.play(SoundType.TASK_COMPLETE, volume = 1f)
```

For spatial audio testing, use `playSpatial()`:

```kotlin
audioEngine.playSpatial(
    SoundType.TASK_COMPLETE,
    x = starX,
    y = starY,
    volume = 0.8f
)
```

## Notes

- All sounds are optional - AudioEngine will gracefully handle missing files
- Spatial audio provides stereo panning based on screen position
- Volume automatically scales based on distance from screen center
- Master volume can be adjusted in settings (default: 70%)
