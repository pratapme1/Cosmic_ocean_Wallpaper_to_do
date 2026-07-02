# Changelog

## [Unreleased]

### Added
- (placeholder)

### Changed
- (placeholder)

### Fixed
- (placeholder)

### Testing
- (placeholder)

## [2.9.0] - 2026-07-02 (Supabase Reminder Sync)

### Added
- Vi reminders now sync through the Supabase `vi_assistant_reminders` table instead of the legacy GitHub JSON feed.
- App-created reminders are mirrored to Supabase with deterministic remote IDs, and app edits, completions, archives, and deletes are queued/retried against Supabase.
- Assistant-created reminders reconcile into Room so the app canvas, widget, and live wallpaper all render the same active reminder set.

### Changed
- Settings now store the Supabase anon key in encrypted preferences and clear legacy GitHub PAT storage.
- Remote reminder refresh runs through Room reconciliation, preserving local canvas positions while allowing Supabase title/date changes to flow back into the app.

### Fixed
- Deleting an assistant-created Vi reminder in the app now removes the Supabase row while staying out of the legacy app sync queue.
- Pending Supabase mirror writes are protected during reconciliation so a failed or offline upsert does not delete the local app task.

### Testing
- `ANDROID_HOME=/home/vi/Android/Sdk ./gradlew testDebugUnitTest --rerun-tasks`
- Live Supabase REST smoke: upsert create, fetch, upsert update, complete/filter inactive, and delete cleanup.
- `adb shell am instrument -w -r -e class com.cosmicocean.e2e.WallpaperConsentE2ETest#testWallpaperConsentFlow com.cosmicocean.test/androidx.test.runner.AndroidJUnitRunner`
- Screenshot evidence reviewed in `qa-runs/2026-07-02-supabase-reminders`.

## [2.8.6] - 2026-07-02 (Wallpaper Refresh Reliability)

### Changed
- Live wallpaper now refreshes remote Vi reminders every 5 minutes while the wallpaper engine is running; WorkManager remains a 15-minute background cache fallback because Android does not support 5-minute periodic work.
- Wallpaper refresh worker no longer writes a legacy static gradient over the user's lock wallpaper.

### Fixed
- Local custom wallpaper mode/path and theme changes now emit an observable render preference snapshot so the live wallpaper updates immediately instead of waiting for a ticker or unrelated database event.
- Environment settings and custom wallpaper uploads now keep wallpaper mode in sync across DataStore and `WallpaperPreferencesManager`.
- Custom mode falls back to generated rendering when the custom path is missing or invalid, avoiding blank wallpaper frames.

### Testing
- `ANDROID_HOME=/home/vi/Android/Sdk ./gradlew testDebugUnitTest`
- `ANDROID_HOME=/home/vi/Android/Sdk PATH=/home/vi/Android/Sdk/platform-tools:$PATH ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.cosmicocean.e2e.CustomWallpaperMultiUploadE2ETest`
- Screenshot evidence reviewed in `qa-runs/2026-07-02-wallpaper-refresh`.

## [2.8.5] - 2026-02-09 (Custom Wallpaper Fix)

### Fixed
- **Custom Wallpaper Persistence**: Fixed a major bug where uploading a new custom wallpaper would fail to refresh the display. The app now uses unique filenames for every upload to bypass the Live Wallpaper engine's path-based cache.

## [2.8.3] - 2026-02-09 (Onboarding UX Polish)

### Added
- **WallpaperSetupOverlay**: Replaced the system AlertDialog with a custom, themed overlay that renders directly on the home canvas.
- **Integrated Onboarding**: Wallpaper setup is now a seamless step between App Info (Discovery) and the CRUD Tutorial.

### Changed
- **Terminology**: Standardized on "Live Wallpaper Setup" and "Setup Wallpaper" throughout the initial flow.

## [2.8.2] - 2026-02-09 (Onboarding Flow UX)

### Changed
- **Onboarding Sequence**: Reordered flow to App Info (Discovery) → Wallpaper Consent → Tutorial.
- **First-Time Logic**: Onboarding now strictly appears only for first-time logins or fresh installs.
- **Discovery Overlay**: Added "Skip tour" test tag and improved dismissal logic to chain into Wallpaper Setup.

### Fixed
- **Wallpaper Consent Test**: Improved E2E test resilience with explicit waits and UI interaction checks.
- **Auth Handling**: E2E tests now robustly handle "Skip to Guest" authentication scenarios.

## [2.7.3] - 2026-02-06 (Device Test Build)

### Changed
- **Release Packaging**: Version bump for device testing (no functional changes from 2.7.2).

### Testing
- **Release APK**: `./gradlew :app:assembleRelease`

## [2.7.2] - 2026-02-06 (Wallpaper QA + Settings UX)

### Added
- **Custom Wallpaper EXIF Support**: Uploaded wallpaper images now respect EXIF orientation before rendering.
- **Wallpaper Scenario Screenshots**: Added instrumentation coverage for multi-size, empty-state, and long/RTL text scenarios.
- **Homepage Zone Labels**: “Archive” and “Complete” labels now render on the canvas for always-on guidance.
- **Settings Close Buttons**: Privacy and Environment settings now include a close button for quick return.
- **Recurring + Subtask Toggles**: Quick Add and Edit overlays now support recurring tasks and subtasks with interval selection.
- **High-Contrast Text Mode**: Optional higher-contrast wallpaper text rendering for custom uploads.
- **Parent-Linked Subtasks**: Subtasks now link to a parent task with parent selection and persistence.

### Changed
- **Wallpaper Typography System**: Unified header, badge, task title, and meta sizing with consistent spacing across device sizes.
- **Custom Wallpaper Rendering**: Uploaded wallpapers now render without environment overlays to preserve the original image.
- **Generated Wallpaper Texture**: Added a subtle noise layer to reduce banding and improve depth.
- **RTL/Long Text Rendering**: Wallpaper task layout now uses bidi wrapping and higher-quality line breaks.
- **Homepage HUD Visibility**: Controls auto-hide after idle and return on touch to reduce clutter.
- **Custom Wallpaper Scaling**: Added safe-copy handling to avoid recycled bitmap crashes during custom wallpaper generation.
- **Environment Defaults**: Environment effects now default to off (weather/particles/heatmap/ambient reminders disabled until enabled).
- **Settings Cleanup**: Removed unused AI/LLM settings screen that had no user entrypoint.
- **Tutorial Flow**: Onboarding now advances only after key actions (create/edit/refresh/customize).
- **All-Clear Visual**: Replaced blue checkmark with a calm orbit indicator.
- **Subtask Visuals**: Canvas now orbits subtasks around parents; wallpaper indents subtasks and caps overflow.

### Fixed
- **Badge Truncation**: Long system badges now ellipsize cleanly without clipping or overlap.
- **Homepage HUD Overlap**: Customize/FAB/toolbar layout no longer shifts or overlaps when auto-hidden.
- **Star Placement vs HUD**: Stars avoid HUD zones and remain draggable at screen edges.
- **Homepage Clock**: Removed always-on clock from ambient HUD.
- **UI Test Stability**: Dismiss System UI ANR dialog before capturing instrumentation screenshots.
- **Sync Prompt Visibility**: Tutorial no longer blocks the wallpaper sync consent dialog.
- **Edit Overlay Testability**: Added stable test tags and debug hooks to reliably exercise edit flows in instrumentation.
- **Local Parser Edge Cases**: “Tomorrow evening” and related phrases now keep the intended title.
- **Immediate Custom Wallpaper Updates**: Force refresh path eliminates long delays after consecutive wallpaper changes.

### Testing
- **Unit Tests**: `./gradlew :app:testDebugUnitTest`
- **Full E2E Suite**: `./gradlew :app:connectedDebugAndroidTest` with screenshots captured and reviewed.
- **Lockscreen CRUD Verification**: Captured lock screen wallpaper screenshots and verified bitmap changes after add/edit/complete.

## [2.7.1] - 2026-02-04 (Parsing Intelligence + UX)

### Added
- **Health Context**: Manual context selector now includes `health`.
- **Parsing QA Matrix**: Deterministic 100-phrase parsing results saved at `docs/qa/parsing-results.md`.
- **Overlay Close Buttons**: Quick Add and Edit Task overlays now include a close button.

### Changed
- **Hybrid Parser Gating**: Context/priority/energy now use raw score + margin thresholds (better tag reliability).
- **Context Weights Expansion**: Added work/home/grocery/commute/health keywords (standup/retro/pto/expense/pharmacy/doctor/etc.).
- **Energy Override**: When confident, energy prediction now overrides default `medium`.
- **Local Parsing**: Weekend parsing added; titles strip trailing prepositions (`by`, `on`, `at`).

### Fixed
- **Priority Heuristics**: “Overdue” now elevates to P1 priority.

### Release
- **Play Store Prep**: Unit + UI tests run on emulator; AAB built for upload.

## [2.7.0] - 2026-02-04 (Local Engagement + Guidance)

### Added
- **Due Haptics**: Configurable thresholds with quiet hours, DND respect, and rate limiting.
- **Tutorial Overlay**: 5-step onboarding tour with CRUD guidance and persistent “seen” state.
- **Swipe-Down Dismiss**: Privacy and Environment settings can be dismissed with a downward swipe.
- **Wallpaper Signals**: Next-task highlight, short-task badge, ambient reminder pulse, focus/context badges, and completion burst on wallpaper refresh.
- **Hybrid Local Parser**: Token-weighted classifier for context, priority, and energy (no network required).
- **Context-Aware Highlight**: Wallpaper highlights the most relevant task for the active context.
- **Intent Forecast Path**: Faint constellation path connecting next tasks.
- **Context Mode**: Manual context selector for task-aware suggestions.
- **Overdue Heatmap**: Optional urgency layer on wallpapers.
- **Focus Sessions**: Timed focus with overlay + wallpaper dimming.
- **Ambient Reminders**: Subtle, low-intrusion cue system.
- **Contextual Short Task Suggestions**: Quick picks for <= 15 minute tasks.
- **Next Task Chip**: Surface the highest-impact task on the main screen.
- **Completion Celebration**: Lightweight completion animation.
- **Snooze All Overdue**: One-tap recovery for overdue backlog.
- **Daily Focus Widget**: Powered by local DB + streaks (no mock data).

### Fixed
- **Tutorial Persistence**: Overlay now dismisses on Done/Skip and won’t show over active overlays.

## [2.6.3] - 2026-02-04 (Local Privacy + Environment Wallpapers)

### Added
- **Local-Only Privacy Settings**: Privacy controls now stored locally and applied to wallpaper task titles (public/initials/hidden).
- **Environment → Wallpaper**: Time-of-day, weather overlay, and particle intensity now render on wallpapers.
- **Custom Wallpaper Effects**: Environment overlays and particles apply to both generated and custom wallpapers.

### Fixed
- **Wallpaper Consistency**: Real-time and worker updates now read local environment preferences for every wallpaper refresh.

## [2.6.2] - 2026-02-03 (Local-Only Visual Parity)

### Design-Only
- **Backend Visual Parity**: Radial gradients and transition blending to match backend layer order.
- **Particle System Alignment**: Zone-weighted particle distribution with urgency-based counts, sizes, and opacity.
- **Typography Refinement**: Updated header/title typefaces to align with backend visual hierarchy.
- **Achievement Panel Styling**: Right-side vertical panel refined with badge + streak sections (no behavior change).

### Fixed
- **Achievement Count**: Wallpaper achievements now reflect total completed tasks (not just milestone trophies).
- **Priority Mapping**: P1/P2/P3 derived from due date (today/overdue = P1, tomorrow = P2, future/no date = P3).
- **Local Parsing**: Added EOD/EOW/EOM/EOY + shorthand date handling and safer priority defaults.

## [2.6.1] - 2026-02-03 (Local-Only Wallpaper Reliability + Backend Layout Alignment)

### Fixed
- **Real-Time Wallpaper Updates**: Auto-enabled wallpaper updates when consent wasn’t set in local-only mode; removed network constraints from wallpaper workers.
- **Custom Wallpaper + Task Overlay**: Task CRUD now reliably triggers local wallpaper refreshes, including custom wallpaper mode.
- **Backend Layout Alignment**: Ported backend layout system (safe zones, margins, typography) to local wallpaper generator.
- **Achievement Panel Placement**: Achievements now render as a right-side vertical panel aligned with the task zone (backend parity).
- **Due Date Priority Colors**: Task indicators follow due-date priority (red <24h/overdue, orange tomorrow, blue future).
- **Visual Parity (Design-Only)**: Radial gradients, transition blending, zone-weighted particles, and refined typography to match backend styling.
- **Resolution Consistency**: Service/worker now use stored portrait resolution for lock screen alignment.

All notable changes to Cosmic Ocean will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).


## [2.4.0] - 2026-02-02 (Custom Wallpaper FIX - Download & Cache)

### 🐛 CRITICAL FIX: Custom Wallpaper Now Works

#### **Root Cause:**
When users uploaded custom wallpapers, the app stored the remote URL but never downloaded the actual image. When wallpaper service tried to display it:
1. Check: Is path a local file? No.
2. Try to load file → File not found → Fail
3. Fallback to generated wallpaper

#### **The Fix:**
Added automatic download and caching:
```kotlin
if (path.startsWith("http")) {
    // Download from URL
    val url = URL(path)
    val localFile = File(filesDir, "custom_wallpaper_cache.jpg")
    url.openStream().use { input ->
        FileOutputStream(localFile).use { output ->
            input.copyTo(output)  // Download complete image
        }
    }
    // Load downloaded bitmap
    return BitmapFactory.decodeFile(localFile.absolutePath)
}
```

**What happens now:**
1. User sets custom wallpaper (URL stored in preferences)
2. Wallpaper service detects custom mode
3. Downloads image from URL to local cache
4. Displays custom wallpaper on lock screen
5. Future updates use cached image (faster)

#### **Files Changed:**
- `RealTimeWallpaperService.kt`:
  - Made `loadCustomWallpaper()` a suspend function
  - Added URL detection logic
  - Added download and caching to local storage
  - Added required imports (withContext, File, FileOutputStream, URL)

### APK
- **File**: `cosmic-ocean-v2.4.0.apk` (8.2 MB)
- **Status**: Custom wallpaper fully functional

---

## [2.3.9] - 2026-02-02 (Multi-Task Wallpaper Display)

### ✨ New Feature: Show Top 3 Tasks on Wallpaper

**Before:**
- Wallpaper showed only 1 task at a time
- Couldn't see multiple pending tasks at a glance

**After:**
- Wallpaper shows top 3 most urgent tasks
- Shows "+N more" indicator if additional tasks exist
- Tasks displayed in list format with urgency indicators

#### **Implementation Details:**

**Daos.kt:**
- Added `getTop3Tasks()` query (LIMIT 3)
- Added `getActiveTaskCount()` for "+more" indicator

**RealTimeWallpaperService.kt:**
- Updated to fetch top 3 tasks instead of just 1
- Passes task list and total count to generator

**LocalWallpaperGenerator.kt:**
- New `drawTaskList()` method - displays up to 3 tasks
- New `drawTaskItem()` method - simplified task card for list view
- Shows colored urgency indicator (red/orange/yellow) for each task
- "+N more" text appears below tasks if count > 3

**Layout:**
- Tasks arranged vertically with spacing
- Each task shows: urgency dot + task title
- Critical tasks: Red indicator
- Urgent tasks: Orange indicator  
- Normal tasks: Theme-colored indicator

### APK
- **File**: `cosmic-ocean-v2.3.9.apk` (8.2 MB)
- **Status**: Multi-task display implemented

---
