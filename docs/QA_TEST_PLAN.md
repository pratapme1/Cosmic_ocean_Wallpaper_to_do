# QA Test Plan (Android)

## Scope
- Manual UI testing on emulator for all user-visible features
- Regression checks for wallpaper rendering and environment settings
- No automated test changes in this plan

## Strict Workflow Policy
1. **Set test device**: Use a single emulator profile for each run and record device name + API level.
2. **Clean start**: Clear app data before each test pass.
3. **Single variable**: Change only one setting at a time.
4. **Evidence required**: Capture a screenshot for every UI test step.
5. **Verification**: Tester must verify each screenshot matches expected behavior.
6. **Logging**: Record pass/fail with a short note.
7. **No skipping**: If blocked, mark as blocked with reason.

## Emulator Setup
- Device: Pixel 5 (or equivalent)
- API Level: 33 or higher
- Enable animations: ON (for motion tests)
- Location simulation: ON (for geofence tests)

## Manual UI Test Matrix

### Environment & Context
- Enable/disable Environment toggle; confirm wallpaper updates
- Environment Mode toggle changes time-of-day visuals
- Weather Overlay toggle changes overlay appearance
- Particle intensity change reflects on wallpaper
- Context manual switch updates wallpaper immediately
- Geofence entry/exit switches context correctly

### Tasks & Reminders
- Create task with due time; verify due soon indicator
- Create overdue task; verify overdue state
- “Next Task” chip opens correct task
- Snooze all overdue updates all due times

### Haptics & Alerts
- Due soon haptic fires at threshold
- Quiet hours suppress haptics
- DND respected
- Test vibration button works

### Tutorial
- First-run overlay appears
- Skip works and doesn’t reappear
- Reopen tutorial from Settings

### Widgets
- Daily Focus Widget shows top tasks
- Tapping widget opens Today view
- Empty state is correct

### Accessibility
- High contrast toggle increases readability
- Reduced motion reduces animation intensity

### Custom Wallpapers
- Custom wallpaper with overlays ON
- Custom wallpaper with overlays OFF
- Preview before apply works

## Regression Checklist
- App launch time normal
- No crashes on navigation
- Wallpaper renders under low memory
- Settings persist after restart

## Pass/Fail Criteria
- All tests in matrix pass
- No critical regressions
- No blocked tests without owner sign-off
