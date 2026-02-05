# Cosmic Ocean — Android Wallpaper Task App

> **Your lock screen wallpaper is your task list.**  
> Local-first, offline-capable, privacy-aware.

---

## **Overview**

Cosmic Ocean turns your lock screen into a calm, dynamic task surface.  
All core features run **locally on Android**: tasks, wallpaper generation, privacy, and environment visuals.

---

## **Key Android Features (Local-Only)**

- **On-device wallpaper generation** (no backend dependency)
- **Real-time updates** via foreground service + worker
- **Custom wallpapers** with task overlay
- **Environment settings** applied to wallpaper  
  - Time-of-day (auto/manual)
  - Productivity-based weather overlays
  - Particle intensity control
- **Context mode** for task-aware suggestions (manual selector)
- **Privacy controls** for wallpaper tasks  
  - Public, Initials, Hidden  
  - Hide-all mode
- **Due haptics** with quiet hours, DND respect, and rate limits
- **Overdue heatmap** (optional wallpaper urgency layer)
- **Focus sessions** with overlay + wallpaper dimming
- **Ambient reminders** (subtle visual cues)
- **Contextual short-task suggestions** (<= 15 minutes)
- **“Next task” chip** for highest-impact task
- **Wallpaper signals** (next-task highlight, short-task badge, ambient pulse, focus/context badges, completion burst)
- **Hybrid local parsing** (token-weighted context/priority/energy inference)
- **Completion celebration** on task finish
- **One-tap snooze all overdue**
- **Daily focus widget** (local DB + streaks)
- **Lightweight tutorial overlay** (first-run guidance)
- **Offline-first data** with Room + DataStore

---

## **Project Structure**

```
android/    # Android app (active)
backend/    # API (optional for local-first mode)
archive/    # Archived docs and assets (ignored)
```

---

## **Build & Test (Android)**

```bash
# Unit tests
./gradlew testDebugUnitTest

# Instrumentation/UI tests (emulator)
./gradlew :app:connectedDebugAndroidTest
```

**UI test screenshots:**  
`android/app/build/reports/androidTests/connected/debug/screenshots/AndroidTestScreenshots/`

---

## **Release APK**

```bash
./gradlew :app:assembleRelease
```

Output:  
`android/app/build/outputs/apk/release/app-release.apk`

---

## **Changelog & Versioning**

- Release notes live in `CHANGELOG.md`
- Current Android version: **2.7.1**

---

## **Notes**

- Environment visuals are **not location-based**.  
  They use **device time** and **local task state** only.
- Privacy and environment settings are **local-only** by default.

---

**Last Updated:** 2026-02-04
