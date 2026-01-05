# Changelog

All notable changes to the Cosmic Ocean backend will be documented in this file.

## [1.3.2] - 2026-01-05

### Fixed
- **LLM JSON Parsing**: Improved JSON extraction regex to handle Claude's explanatory text
  - Handles responses like "Here is the parsed task: {...}"
  - Strips markdown code blocks (```json```)
  - Better error logging with response preview
  - Graceful fallback to local parser on JSON errors

- **LLM Integration in POST /api/tasks**:
  - Now automatically uses Claude LLM when `ENABLE_LLM_PARSING=true`
  - Previously was hardcoded to local NLP parser only
  - Added `shouldUseLLM` detection logic

- **Claude Timeout Handling**:
  - Increased message generation timeout: 10s → 15s
  - Increased task parsing timeout: 5s → 8s
  - Reduced timeout errors under production load

### Technical
- Updated llm-task-parser.js with nested JSON extraction regex
- Updated server.js POST /api/tasks to use parseLLM() when enabled
- Updated message-generator-llm.js timeout configuration
- Added debug logging for LLM response preview

## [1.2.2] - 2026-01-05

### Added
- New `/api/tasks/all` endpoint that returns all tasks with their current status (active, completed, archived, snoozed)
- Task status field computed in query: `active`, `completed`, `archived`, `snoozed`
- Summary statistics in `/api/tasks/all` response (total, active, completed, archived, snoozed counts)

### Fixed
- **Stars Physics**: Reduced repulsion force from 1.2x to 0.5x, added 15px/frame velocity cap, softened collision force calculation
- **UTC Timezone Bug**: Wallpaper now uses device's local timezone (e.g., Asia/Kolkata) instead of UTC
- **Resolution Scaling**: Implemented pixel-based text truncation using canvas.measureText() instead of character count
- **Font Density**: Reduced density from 2.5x to 2.0x on large screens (1080px+) for better spacing
- Small screens (393px) now show 2 tasks instead of 3 to prevent content overflow

### Changed
- Removed redundant time display from wallpaper (device lock screen already shows time)
- Text truncation now uses actual pixel width measurement (like Android WallpaperGenerator.kt)
- Optimized font sizes: 1080px screens now use 2.0x density instead of 2.5x

### Technical
- Updated `layout-system.js` density calculation for better readability
- Updated `text-renderer.js` with truncateText() function using canvas measurement
- Updated `VerletEngine.kt` physics parameters for smoother star movement

## [1.2.1] - 2026-01-04

### Added
- Epic 7: Hybrid priority system with star-based visual indicators
- Enhanced star physics with improved collision detection
- Resolution scaling for multiple device sizes

### Fixed
- Countdown timer display issues
- Category badge rendering
- Task metadata visibility

## [1.2.0] - 2026-01-03

### Added
- Epic 8: Intelligence Layer with message engine
- Satori-based intelligent messages (OVERDUE, TOMORROW, etc.)
- Comprehensive testing suite for NLP and task parsing
- Star color logic based on task categories

### Fixed
- NLP parsing improvements
- Task schema validation
- Production deployment verification

## [1.0.0] - Initial Release
- Basic task management API
- JWT authentication
- PostgreSQL database integration
- Redis caching
- Wallpaper generation
