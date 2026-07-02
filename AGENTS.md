# Agent Rules

## Golden Rule: UI Test Screenshots
- For every UI/instrumentation test run, capture screenshots and verify them.
- Test runs are not considered complete until screenshots are produced and reviewed.

## Session Closeout
- Before ending a session, update `docs/STATUS_TRACKING.md`, `docs/FEATURE_BACKLOG.md`, `CHANGELOG.md`, and `AGENTS.md` to reflect completed work.
- 2026-02-05: Play Store prep performed (unit + UI tests, screenshots, AAB build).
- 2026-02-05: Wallpaper typography/layout overhaul + custom overlay readability + EXIF handling (unit + wallpaper screenshot tests, evidence saved).
- 2026-02-05: Homepage HUD auto-hide + zone labels + safe HUD insets (unit + UI tests, screenshots saved).
- 2026-02-06: Wallpaper scenario coverage + RTL/long text handling + noise cache perf tweak (unit + UI tests, screenshots saved).
- 2026-02-06: Custom wallpaper scaling safety + UI test ANR dismissal (unit + UI tests, screenshots saved).
- 2026-02-06: Settings close buttons + environment defaults off + sync prompt visibility (unit + UI tests, screenshots saved).
- 2026-02-06: Removed legacy LLM settings screen (unit + UI tests, screenshots saved).
- 2026-02-06: Full e2e instrumentation run completed (unit + UI tests, screenshots saved).
- 2026-02-06: Deep e2e suite expansion + double run completed (screenshots saved; no skips after edit/focus stabilization).
- 2026-02-06: Verified no-skip e2e runs with screenshot review (qa-runs/2026-02-06-e2e-run2, qa-runs/2026-02-06-e2e-run3).
- 2026-02-06: Lock screen wallpaper CRUD verification + full e2e run (screenshots saved, qa-runs/2026-02-06-e2e-run4).
- 2026-02-06: Custom wallpaper overlay removal + action-based tutorial + recurring/subtask UX + consent flow fix (unit + UI tests, screenshots saved, qa-runs/2026-02-06-e2e-full-run6).
- 2026-02-06: Parent-linked subtasks with orbit canvas + wallpaper indent (unit + UI tests, screenshots saved, qa-runs/2026-02-06-subtasks).
- 2026-07-02: Live wallpaper cloud refresh cadence + immediate local wallpaper preference updates (unit + UI tests, screenshot reviewed, qa-runs/2026-07-02-wallpaper-refresh).
- 2026-07-02: Supabase Vi reminder bidirectional CRUD sync validation (unit tests + live REST smoke + live app-path CRUD instrumentation + UI screenshots reviewed, qa-runs/2026-07-02-supabase-reminders).

## Release Discipline
- Every release APK build must follow this order: update `CHANGELOG.md` → commit/push → rebuild APK named from changelog version.

## Post-Feature Test Gate
- After every feature is implemented, run unit tests and automated UI tests on an emulator.
- Only after those tests pass, proceed to commit/push and generate the APK.
