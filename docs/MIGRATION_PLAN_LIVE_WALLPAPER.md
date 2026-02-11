# Migration Plan: Live Wallpaper Engine

## **1. Executive Summary**
This document outlines the architectural migration from the legacy `RealTimeWallpaperService` (Foreground Service) to the native Android `CosmicLiveWallpaperService` (WallpaperService).

**Goal:** Eliminate battery drain issues and comply with Google Play Store "Foreground Service" policies while maintaining the app's real-time task visualization features.

**Strategy:** "Side-by-Side" implementation. The new engine will be deployed alongside the old one. Users (and QA) can switch between them via the Android System Wallpaper Picker, ensuring zero regression for existing users until the new engine is validated.

---

## **2. Architecture Overview**

### **Legacy Architecture (To be Deprecated)**
*   **Mechanism:** `RealTimeWallpaperService` (Foreground Service).
*   **Trigger:** Runs every 60 seconds (Timer) + BroadcastReceiver.
*   **Drawing:** Allocates a new `Bitmap`, draws to it, saves to disk/memory, calls `WallpaperManager.setBitmap()`.
*   **Issues:** High CPU/IO usage (re-creating bitmaps), prevents Deep Sleep (WakeLocks), violates Play Store Policy.

### **New Architecture (Live Engine)**
*   **Mechanism:** `CosmicLiveWallpaperService` (Android `WallpaperService`).
*   **Trigger:** Reactive Data Stream (`Flow<List<Star>>`).
*   **Drawing:** `WallpaperRenderer` draws directly to the System `Surface` (GPU accelerated).
*   **Benefits:**
    *   **Zero Battery Drain:** Engine stops completely when screen is off (`onVisibilityChanged`).
    *   **True Real-Time:** Updates instantly when DB changes, no 60s delay.
    *   **Efficient:** Caches assets (bitmaps) and reuses Paint objects. No 10MB allocations per frame.

---

## **3. Component Breakdown**

### **A. `WallpaperRenderer` (Pure Logic)**
*   **Responsibility:** Takes a `Canvas` and a `WallpaperState` object. Draws the frame.
*   **Status:** ✅ Implemented & Unit Tested.
*   **Key Feature:** Caches custom background images to avoid file I/O on every frame.

### **B. `CosmicLiveWallpaperService` (The Engine)**
*   **Responsibility:** Manages Android Lifecycle.
    *   `onVisibilityChanged(true)`: Start DB observers, Start Clock ticker.
    *   `onVisibilityChanged(false)`: **STOP EVERYTHING.** Release wake locks (if any), cancel Coroutines.
*   **Status:** 🚧 In Progress.

### **C. Data Integration**
*   **Source:** Room Database (`StarDao`) + DataStore (`EnvironmentPreferences`).
*   **Transformation:** Combines these sources into a single `WallpaperState` object passed to the Renderer.

---

## **4. Verification Strategy**

### **Automated Tests**
*   **Unit Tests:** `WallpaperRendererTest` verifies drawing logic, caching, and urgency color resolution.
*   **Integration Tests:** Verify Service registration and Intent handling.

### **Manual Validation (The "A/B" Test)**
1.  **Visual Equivalence:**
    *   Set Old Wallpaper -> Screenshot.
    *   Set New Live Wallpaper -> Screenshot.
    *   **Pass Criteria:** Images are visually identical.
2.  **Battery "Logcat" Test:**
    *   Turn Screen OFF.
    *   Observe Logcat for `CosmicLiveWallpaper`.
    *   **Pass Criteria:** Logs MUST stop completely. No "Drawing frame" logs while screen is off.
3.  **Real-Time Response:**
    *   Mark a task complete in the app.
    *   **Pass Criteria:** Wallpaper updates instantly (sub-100ms) without needing a "Refresh" tap.

---

## **5. Rollout Plan**

1.  **Alpha:** Ship with `AndroidManifest.xml` changes. Use internal testing to enable the Live Wallpaper via System Settings.
2.  **Beta:** Add a "Try the new Live Wallpaper" button in App Settings that opens the System Picker.
3.  **Production:** Deprecate the old Service. The "Enable Wallpaper" toggle in onboarding will default to the Live Wallpaper Intent.

---

**Author:** Gemini CLI Agent
**Date:** 2026-02-07
