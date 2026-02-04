# Release Sequencing Plan

## Goal
Deliver retention-focused Android improvements in 3 milestones with minimal risk.

## Milestone 1A — Foundations (2–3 weeks)
**Theme**: Reliability + baseline UX
- Due-Soon Haptics (with rate-limit policy)
- Lightweight Tutorial Overlay
- Priority Mapping Definition (tiers + environment mapping)
- No-location fallback mode (manual context toggle)

## Milestone 1B — Context Intelligence (2–3 weeks)
**Theme**: Smarter environment
- Context-Aware Environment (geofences)
- Intelligent Task/Reminder Pairing
- Enhanced Parsing Engine (rules + on-device ML)

**Acceptance**
- All P0 acceptance criteria pass
- Battery impact within target (no sustained background drain)
- No regressions in wallpaper rendering

## Milestone 2 — Quick Wins + Flow (3–4 weeks)
**Theme**: Daily engagement
- Daily Focus Widget
- Smart “Next Task” Chip
- Overdue Heatmap
- One-Tap “Snooze All Overdue”
- Completion Celebration
- Focus Sessions
 - Custom Wallpaper Overlay Rules

**Acceptance**
- Widget refresh reliability verified
- Focus mode stable across app restarts
 - Overlay rules validated for custom wallpapers

## Milestone 3 — Retention + Polish (3–4 weeks)
**Theme**: Long-term stickiness
- Progress-Based Themes
- Contextual Short Task Suggestions
- Weekly Review Mode
- Task Batching
- Streaks + Recovery
- Personalized Daily Summary
- Ambient Reminders
- Adaptive Motion Budget
- Accessibility Pass

**Acceptance**
- A11y contrast + reduced motion verified
- Low-battery behavior validated

## Release Gates
- No crashes in core flows
- All automated tests green
- Manual pass for environment settings + wallpaper states
- App size within current budget

## Risks & Mitigations
- Location/geofencing battery impact: throttle checks, use geofence APIs
- Parsing model size/perf: lightweight model, fallback rules
- Haptics annoyance: strict quiet hours + user controls
- Overlapping geofences: resolve by nearest radius or user priority

## Metrics (Local)
- Daily active tasks completed
- Streak retention (7-day)
- Overdue recovery rate
