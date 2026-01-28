# Changelog

## [1.3.16] - 2026-01-28

### Fixed
- **Screen Resolution & Edge Gaps**: Updated resolution detection to use `currentWindowMetrics` (or `getRealMetrics`), capturing the *full* physical screen size (including status/nav bars). This fixes the wallpaper "edge gap" and "+1" UI cropping issues.
- **Ghost Overdue Tasks**: Changed task deletion logic to delete by ID (`deleteStarById`) rather than entity matching, ensuring tasks are reliably removed from the database and "Overdue" count.
- **Wallpaper Text Handling**: Improved layout constraints for wallpaper text rendering by ensuring correct canvas size is reported to the backend.

### Verified
- **Resolution**: Verified code now accesses full window bounds.
- **DB Integrity**: Verified deletion uses primary key for reliability.
