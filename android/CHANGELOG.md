# Changelog

## [1.3.16] - 2026-01-28

### Fixed
- **Screen Resolution & Edge Gaps**: Updated resolution detection to use `currentWindowMetrics` (or `getRealMetrics`), capturing the *full* physical screen size (including status/nav bars). This fixes the wallpaper "edge gap" and "+1" UI cropping issues.
- **Ghost Overdue Tasks**: Changed task deletion logic to delete by ID (`deleteStarById`) rather than entity matching, ensuring tasks are reliably removed from the database and "Overdue" count.
- **Wallpaper Text Handling**: Improved layout constraints for wallpaper text rendering by ensuring correct canvas size is reported to the backend.

### Verified
- **Resolution**: Verified code now accesses full window bounds.
- **DB Integrity**: Verified deletion uses primary key for reliability.

## [1.3.15] - 2026-01-28

### Fixed
- **Critical Wallpaper Fix**: Addressed race condition where wallpaper updated before backend sync, showing stale data. `MainViewModel` actions are now suspended and awaited.
- **Overdue Count**: Fixed logic in `AmbientStatusHUD` to exclude completed and archived tasks from the "Overdue" count.
- **Wallpaper Restriction**: Enforced `WallpaperManager.FLAG_LOCK` to ensure wallpaper updates only apply to the Lock Screen, as intended.
- **Layout Responsiveness**: Fixed `AchievementWall` layout on smaller screens by adjusting dimensions based on screen height.
- **Task Creation**: `QuickAddActivity` now fully integrated with `TaskRepository` for actual task creation with random placement.
- **Performance**: Restricted `PerformanceMonitor` overlay to DEBUG builds only.
- **Physics**: Updated star physics to be stable (no zone forces), fixing tests and visual behavior.

### Verified
- **E2E Testing**: Backend APIs (Task Creation, wallpaper generation, etc.) passed E2E validation.
- **Integration**: Validated `ApiModels.kt` matches Backend API contract.
