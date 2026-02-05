# Agent Rules

## Golden Rule: UI Test Screenshots
- For every UI/instrumentation test run, capture screenshots and verify them.
- Test runs are not considered complete until screenshots are produced and reviewed.

## Session Closeout
- Before ending a session, update `docs/STATUS_TRACKING.md`, `docs/FEATURE_BACKLOG.md`, `CHANGELOG.md`, and `AGENTS.md` to reflect completed work.
- 2026-02-05: Play Store prep performed (unit + UI tests, screenshots, AAB build).
- 2026-02-05: Wallpaper typography/layout overhaul + custom overlay readability + EXIF handling (unit + wallpaper screenshot tests, evidence saved).

## Release Discipline
- Every release APK build must follow this order: update `CHANGELOG.md` → commit/push → rebuild APK named from changelog version.

## Post-Feature Test Gate
- After every feature is implemented, run unit tests and automated UI tests on an emulator.
- Only after those tests pass, proceed to commit/push and generate the APK.
