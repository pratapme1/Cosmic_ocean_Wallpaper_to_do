# Changelog

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
