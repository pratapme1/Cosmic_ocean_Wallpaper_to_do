# Changelog

All notable changes to Cosmic Ocean will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.2.0] - 2026-01-03

### Added

#### Backend - Intelligence Layer (Epic 5)
- **NLP Task Parser** (`utils/task-parser.js`) - Natural language processing for task input
  - Duration parsing ("30m", "1h", "quick", "long")
  - Date/time parsing ("tomorrow", "next Monday", "at 3pm")
  - Priority detection ("urgent", "important", "low priority")
  - Category detection (work, personal, health, errands)
  - Context tags (@home, @office, @morning)
  - Recurring patterns ("every day", "weekly", "monthly")

- **Message Engine** (`services/message-engine.js`) - Intelligent wallpaper messages
  - Critical messages for overdue tasks
  - Achievement messages for streaks and milestones
  - Time-context messages (morning, afternoon, evening)
  - Encouragement messages

- **Atmosphere Controller** (`services/atmosphere-controller.js`) - Visual urgency mapping
  - Urgency score calculation (0-100)
  - State mapping (clear, calm, attention, urgent, critical)
  - Visual parameters (particle count, animation speed, color intensity)

- **Stats Aggregator** (`services/stats-aggregator.js`) - User statistics
  - Daily/weekly completion stats
  - Streak calculation with grace period
  - Pattern analysis (peak hours, top categories)
  - In-memory caching with TTL

#### Backend - Comprehensive Testing (Epic 6)
- **263 tests** across all components (target was 150+)
- `tests/message-engine.test.js` - 36 tests
- `tests/atmosphere-controller.test.js` - 44 tests
- `tests/stats-aggregator.test.js` - 44 tests
- `tests/integration/intelligence-pipeline.test.js` - 16 tests
- `tests/wallpaper-matrix.test.js` - 103 tests (75 matrix combinations)

#### Android
- **Real-Time Wallpaper Service** (`service/RealTimeWallpaperService.kt`)
  - 1-minute automatic wallpaper refresh
  - Battery-optimized (runs when screen OFF)
  - Foreground service with notification

### Fixed

- **Stats Aggregator** - Invalid date handling now graceful (was throwing RangeError)
- **Stats Aggregator** - Null task elements now filtered (was throwing TypeError)

### Changed

- Wallpaper generator now uses full intelligence layer for message selection
- Particle system supports override parameters from atmosphere controller

---

## [1.1.0] - 2026-01-03

### Added
- Satori font rendering for serverless environments
- Bundled Inter WOFF fonts for wallpaper text
- Version tracking in health endpoint

### Fixed
- Wallpaper text rendering as boxes on Vercel (font not found)
- Database connection issues with Supabase pooler

---

## [1.0.0] - 2026-01-02

### Added

#### Backend (Epic 2 & 3)
- JWT authentication with refresh tokens
- User CRUD endpoints with GDPR compliance
- Task management API (create, read, update, delete, snooze)
- Rate limiting middleware
- Wallpaper generation with 3 themes (cosmic, ocean, fantasy)
- 5 urgency states (clear, calm, attention, urgent, critical)
- Multi-layer rendering (background, particles, text)
- WCAG-compliant text (10.5:1 contrast ratio)

#### Android (Epic 0)
- Cosmic shader background with AGSL
- Multi-layer star rendering with glow effects
- 4-zone physics system (urgent, future, completed, archived)
- Swirl gesture for snooze (angle-to-duration mapping)
- Hold-to-delete gesture (5 seconds)
- Trophy system with achievements
- Audio engine with spatial sound

#### DevOps
- Vercel serverless deployment
- Supabase PostgreSQL integration
- Release keystore and signed APK
- GitHub repository with CI/CD

---

## Version History

| Version | Date | Highlights |
|---------|------|------------|
| 1.2.0 | 2026-01-03 | Intelligence Layer + 263 Tests |
| 1.1.0 | 2026-01-03 | Satori Font Rendering |
| 1.0.0 | 2026-01-02 | Initial Release |

---

## Upgrade Notes

### 1.1.x → 1.2.0
- No breaking changes
- Backend auto-deploys via Vercel
- Android requires new APK installation

### 1.0.x → 1.1.0
- No breaking changes
- Fixes wallpaper text rendering issue

---

*Maintained by: Vishnu + Claude Code*
