# Feature Backlog (Android-only, Task-Focused Retention)

## Scope
- Platform: Android app only
- Goal: Improve daily retention and task follow-through
- Audience: Task-focused users
- Privacy: Local-first; optional opt-in for place lookup

## Prioritization Key
- P0: Core experience blockers
- P1: Quick wins
- P2: Core flow enhancements
- P3: Retention drivers
- P4: Quality & delight

## P0 — Core Experience

### 1) Context-Aware Environment (Geofences)
- **Problem**: Environment is static; no real-world context
- **Outcome**: Wallpaper adapts to place context (Home/Work/Grocery/etc.)
- **Dependencies**: Location permissions, local place storage
- **Est.**: 2–3 weeks
- **Status**: In progress (manual context selector + local preference only; no geofences yet)
- **Acceptance Criteria**:
  - Users can define at least 3 places with names and radius
  - Environment switches when entering/exiting geofence
  - No network required unless user opts into place lookup
  - Clear privacy messaging and opt-out
  - Conflict resolution defined for overlapping places
- **Risks**: Battery usage, background location limitations

### 2) Intelligent Task/Reminder Pairing
- **Problem**: Tasks not connected to context
- **Outcome**: Tasks auto-suggest a context
- **Dependencies**: Task metadata, parsing engine
- **Est.**: 1–2 weeks
- **Status**: In progress (hybrid parser adds context tags + manual context selection)
- **Acceptance Criteria**:
  - Context suggestion shown on task create/edit
  - User can override suggestion
  - Suggestions improve with user corrections
- **Risks**: Incorrect suggestions; user trust

### 3) Enhanced Parsing Engine (Rules + On-Device ML)
- **Problem**: Current parsing misses intent/context
- **Outcome**: Extract time, priority, location/context, recurrence
- **Dependencies**: On-device ML model, fallback rules
- **Est.**: 2–3 weeks
- **Status**: In progress (rule-based hybrid + confidence gating + QA matrix; on-device ML pending)
- **Acceptance Criteria**:
  - Extracts date/time for common phrases
  - Extracts priority cues (urgent, critical, ASAP)
  - Suggests context from keywords
  - Falls back to deterministic rules if ML unavailable
  - Quick correction UX for wrong suggestions
- **Risks**: Model size, device performance

### 4) Due-Soon Haptics
- **Problem**: Users miss due tasks when wallpaper is subtle
- **Outcome**: Optional vibration/haptic when due thresholds hit
- **Dependencies**: Alarm scheduling, settings UI
- **Est.**: 1 week
- **Status**: Done (2026-02-04)
- **Acceptance Criteria**:
  - Thresholds configurable (e.g., 30m, 10m, overdue 1h)
  - Quiet hours + DND respected
  - User can disable haptics per threshold
  - Rate-limit policy prevents notification spam
- **Risks**: Annoyance; too frequent notifications

### 5) Lightweight Tutorial Overlay
- **Problem**: Users miss core features
- **Outcome**: 3–5 step onboarding tour
- **Dependencies**: UI overlay system
- **Est.**: 3–5 days
- **Status**: Done (2026-02-04)
- **Acceptance Criteria**:
  - First-run overlay shows environment, due alerts, privacy
  - Skippable and re-openable from Settings
- **Risks**: Users dismiss too fast

### 6) Priority Mapping Definition (Foundational)
- **Problem**: Priority-driven environments need consistent mapping
- **Outcome**: Defined tiers + visual mapping for environments
- **Dependencies**: Task schema updates
- **Est.**: 3–5 days
- **Status**: Done (2026-02-04)
- **Acceptance Criteria**:
  - Priority tiers defined (e.g., Critical, High, Normal, Low)
  - Visual/environment mapping documented and applied
  - Default priority set for legacy tasks

## P1 — Quick Wins

### 6) Daily Focus Widget
- **Outcome**: Top 3 tasks + streak on home screen
- **Est.**: 1 week
- **Status**: Done (2026-02-04)
- **Acceptance Criteria**:
  - Widget updates at least every 15 minutes
  - Tapping opens the app to task list

### 7) Smart “Next Task” Chip
- **Outcome**: Shows highest impact task
- **Est.**: 3–5 days
- **Status**: Done (2026-02-04)
- **Acceptance Criteria**:
  - Uses priority + due time + context
  - One tap opens task details

### 8) Overdue Heatmap
- **Outcome**: Visual urgency on wallpaper
- **Est.**: 3–5 days
- **Status**: Done (2026-02-04)
- **Acceptance Criteria**:
  - Heatmap levels based on overdue counts
  - Optional toggle

### 9) One-Tap “Snooze All Overdue”
- **Outcome**: Quick recovery when backlog builds
- **Est.**: 2–3 days
- **Status**: Done (2026-02-04)
- **Acceptance Criteria**:
  - Configurable snooze duration
  - Summary confirmation

### 10) Completion Celebration (Light)
- **Outcome**: Positive feedback loop
- **Est.**: 2–3 days
- **Status**: Done (2026-02-04)
- **Acceptance Criteria**:
  - Short animation + haptic
  - Optional toggle

### 11) Custom Wallpaper Overlay Rules
- **Problem**: Overlays unclear on custom images
- **Outcome**: Defined behavior for overlays on custom wallpapers
- **Est.**: 3–5 days
- **Acceptance Criteria**:
  - User toggle for overlays on custom wallpapers
  - Clear preview of combined result
  - Persisted per wallpaper

## P2 — Core Flow Enhancements

### 12) Focus Sessions
- **Outcome**: Timed focus tied to a task
- **Est.**: 1–2 weeks
- **Status**: Done (2026-02-04)
- **Acceptance Criteria**:
  - 25/45/60 min presets
  - Wallpaper shifts during focus

### 13) Progress-Based Themes
- **Outcome**: Theme evolves with completion rate
- **Est.**: 1 week
- **Acceptance Criteria**:
  - 0–100% completion mapping

### 14) Contextual Short Task Suggestions
- **Outcome**: Suggest tasks under 15 minutes
- **Est.**: 1 week
- **Status**: Done (2026-02-04)
- **Acceptance Criteria**:
  - Prompts only during idle windows

### 15) Weekly Review Mode
- **Outcome**: Weekly planning flow
- **Est.**: 1–2 weeks
- **Acceptance Criteria**:
  - Summarizes overdue + upcoming
  - “Plan week” action

### 16) Task Batching
- **Outcome**: Group tasks by tag/project
- **Est.**: 1 week
- **Acceptance Criteria**:
  - Quick batch start flow

## P3 — Retention Drivers

### 17) Streaks + Recovery
- **Outcome**: Keep streaks alive
- **Est.**: 1 week
- **Acceptance Criteria**:
  - One recovery per week

### 18) Personalized Daily Summary
- **Outcome**: Morning/evening summary
- **Est.**: 1 week
- **Acceptance Criteria**:
  - Generated locally, no network required

### 19) Ambient Reminders
- **Outcome**: Low intrusion cues
- **Est.**: 1 week
- **Status**: Done (2026-02-04)
- **Acceptance Criteria**:
  - Subtle visual or sound cues

## P4 — Quality & Delight

### 20) Adaptive Motion Budget
- **Outcome**: Scale effects by battery/perf
- **Est.**: 1 week
- **Acceptance Criteria**:
  - Lower motion on low battery

### 21) Accessibility Pass
- **Outcome**: High contrast + reduced motion
- **Est.**: 1 week
- **Acceptance Criteria**:
  - Toggles for contrast and motion

## Dependencies / Shared Foundations
- Location + Geofence framework
- Task schema additions: context, priority, due windows
- Parsing engine extensibility
- Settings UX for privacy + haptics
- Migration plan for new task fields

## Conflict Resolution + Fallback Rules
- **Overlapping geofences**: Select the smallest-radius (most specific) location. If ties, use user priority order.
- **No location permission**: Default to manual context selection; do not show location prompts repeatedly.
- **Low battery / background limits**: Freeze context to last known; show a subtle “context paused” indicator.
- **Custom wallpaper**: Overlays default OFF unless user enables; respect per-wallpaper setting.

## UI/UX Design Criteria (Required)
- **Clarity first**: Critical states (overdue, due soon) must be readable in < 2 seconds.
- **One-tap control**: Any new feature must have a quick way to disable or mute.
- **Minimal cognitive load**: Avoid more than 2 new controls per screen.
- **Respect privacy**: All location and context explanations must be explicit and skippable.
- **Quiet by default**: Haptics and alerts must have quiet hours and DND compliance.
- **Preview before apply**: For any environment change, show a live preview.
- **Consistency**: Same meaning for color and intensity across wallpaper, settings, and widgets.
- **Accessibility**: All overlays support high contrast and reduced motion.

## Per-Feature UI Acceptance Checklists

### Context-Aware Environment (Geofences)
- Onboarding explains why location is needed and offers skip
- Place creation has clear radius preview
- Current context is visible on main screen
- Manual override is available in one tap

### Intelligent Task/Reminder Pairing
- Suggestion chip is visible on task create/edit
- Suggestion can be dismissed or changed in one tap
- Display confidence or simple explanation (keyword-based)

### Enhanced Parsing Engine
- Parsed fields are previewed before save
- User can correct with one tap and feedback is stored

### Due-Soon Haptics
- Toggle and threshold settings visible on one screen
- Quiet hours toggle is near the thresholds
- Test vibration button available

### Lightweight Tutorial Overlay
- Can be skipped anytime
- Reopenable from Settings
- Total steps <= 5 screens

### Daily Focus Widget
- Widget has empty/zero state
- Tapping widget opens “Today” view

### Smart “Next Task” Chip
- Has clear icon + label
- Dismissable for the day

### Overdue Heatmap
- Toggle for heatmap on wallpaper
- Preview of each heatmap level

### One-Tap “Snooze All Overdue”
- Confirmation dialog shows count + new due time

### Completion Celebration
- Disable toggle next to animation settings

### Focus Sessions
- Start/stop visible on task detail
- Wallpaper shows focus state clearly

### Progress-Based Themes
- Progress meter shown in settings
- Preview for 0%, 50%, 100%

### Contextual Short Task Suggestions
- Suggestions appear only when user is idle
- One tap to start or dismiss

### Weekly Review Mode
- Entry point in settings + home
- Flow <= 3 screens to finish plan

### Task Batching
- Batch selection UI is clear and reversible

### Streaks + Recovery
- Streak progress visible
- Recovery usage is explicit and confirmed

### Personalized Daily Summary
- Summary preview shown before enabling

### Ambient Reminders
- Intensity slider + on/off
- No sound by default

### Adaptive Motion Budget
- Auto mode toggle + manual override

### Accessibility Pass
- High-contrast toggle
- Reduced motion toggle

## Testing Strategy
- Unit tests for parsing + context assignment
- Integration tests for geofence + wallpaper state
- UI tests for tutorial and toggles
- Regression tests for wallpaper rendering
- Geofence simulation tests with fake location
