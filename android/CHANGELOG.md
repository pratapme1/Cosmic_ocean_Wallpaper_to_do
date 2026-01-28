# Changelog

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
