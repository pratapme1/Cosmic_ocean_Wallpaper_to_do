# QA Workflow (Multi-Session)

## Purpose
Ensure consistent manual QA across multiple sessions and testers with strict evidence and traceability.

## Structure
- **One test run = one session folder**
- **One feature = one checklist**
- **One screenshot per step**

## Folder Convention
Create a folder per test run:
```
qa-runs/
  YYYY-MM-DD_release-<version>_device-<model>_api-<level>/
    run-info.md
    environment/
    tasks/
    haptics/
    tutorial/
    widgets/
    accessibility/
    custom-wallpaper/
```

## Required Artifacts (per session)
- `run-info.md`
- Screenshots for each step
- `results.csv` (optional but recommended)

## run-info.md Template
```
# Test Run Info
Date:
Tester:
App version:
Device model:
API level:
Emulator name:
Build type: debug/release
Notes:

## Pass Summary
Total tests:
Passed:
Failed:
Blocked:
```

## Step Execution Rules
1. Clear app data before starting a feature suite.
2. Execute steps in order; no skipping.
3. Capture screenshot for every step.
4. Label screenshot: `<feature>_<step>_<result>.png`.
5. Verify screenshot immediately and note expected vs actual.
6. If failure occurs, stop and log issue before continuing.

## Multi-Session Coordination
- Each session must reference the last completed session.
- If resuming, continue from the next untested feature.
- If a feature is re-tested, note “retest” in run-info.md.

## Results Logging (Recommended)
Format: `results.csv`
```
feature,step,result,notes,screenshot
Environment,Toggle On,Pass,,environment_01_pass.png
Environment,Toggle Off,Fail,Wallpaper unchanged,environment_02_fail.png
```

## Review Process
- Tester submits session folder for review.
- Reviewer checks 10% of screenshots randomly.
- Any mismatch triggers full re-review of that feature.

## Exit Criteria
- All features pass with evidence.
- No open critical issues.
