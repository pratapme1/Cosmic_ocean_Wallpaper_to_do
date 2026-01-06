
● All project files updated. Now here's the Step-by-Step Device Testing Guide for end-to-end testing:

  ---
  📱 Device Testing Guide - End-to-End Functionality Test

  Pre-requisites

  1. Install fresh release APK: /home/vi/supernova/cosmic-ocean-release.apk
  2. Clear app data before testing
  3. Ensure internet connection active
  4. Backend live at: https://cosmic-ocean-api.vercel.app

  ---
  Phase 1: Authentication Flow (5 tests)

  | #   | Test                  | Steps                                                   | Expected Result                            |
  |-----|-----------------------|---------------------------------------------------------|--------------------------------------------|
  | 1   | New User Registration | Open app → Register tab → Enter email/password → Submit | Account created, redirected to main screen |
  | 2   | Login                 | Open app → Enter registered credentials → Submit        | Login success, tasks loaded                |
  | 3   | Wrong Password        | Enter wrong password                                    | Error message: "Invalid credentials"       |
  | 4   | Logout                | Menu → Logout                                           | Returns to login screen, token cleared     |
  | 5   | Session Persistence   | Login → Close app → Reopen                              | Auto-logged in, no login prompt            |

  ---
  Phase 2: Task CRUD Operations (8 tests)

  | #   | Test          | Steps                                  | Expected Result                                  |
  |-----|---------------|----------------------------------------|--------------------------------------------------|
  | 6   | Create Task   | Double-tap screen → Enter title → Save | New star appears with glow                       |
  | 7   | Edit Task     | Tap star → Edit → Change title → Save  | Label updates immediately                        |
  | 8   | Complete Task | Drag star to right edge (>85%)         | Star moves to completion zone, wallpaper updates |
  | 9   | Archive Task  | Drag star to left edge (<15%)          | Star drifts left, removed from active            |
  | 10  | Delete Task   | Hold star for 5 seconds                | Red rings appear, explosion, star deleted        |
  | 11  | Snooze Task   | Swirl gesture on star (90-540°)        | Snooze duration shown, due date updated          |
  | 12  | Undo Delete   | Delete star → Tap undo toast           | Star reappears in original position              |
  | 13  | Backend Sync  | Create task → Check backend            | Task exists in database                          |

  ---
  Phase 3: Wallpaper Generation (10 tests)

  | #   | Test               | Steps                          | Expected Result                         |
  |-----|--------------------|--------------------------------|-----------------------------------------|
  | 14  | Generate Wallpaper | Menu → Set Wallpaper           | Lock screen shows task with theme       |
  | 15  | Cosmic Theme       | Set theme → Cosmic → Generate  | Purple/blue gradient with stars         |
  | 16  | Ocean Theme        | Set theme → Ocean → Generate   | Teal/blue gradient with bubbles         |
  | 17  | Fantasy Theme      | Set theme → Fantasy → Generate | Purple/pink gradient with sparkles      |
  | 18  | Urgency: Calm      | No urgent tasks → Generate     | Gentle colors, slow particles           |
  | 19  | Urgency: Critical  | Overdue task → Generate        | Intense colors, fast particles          |
  | 20  | Text Rendering     | Long task title → Generate     | Text truncated, readable on lock screen |
  | 21  | Resolution Match   | Generate on device             | Fits screen without stretching          |
  | 22  | Auto-Update        | Wait 1 minute                  | Wallpaper refreshes automatically       |
  | 23  | Done For Today     | Mark done → Generate           | Shows "You're all done!" message        |

  ---
  Phase 4: Physics & Interactions (8 tests)

  | #   | Test                 | Steps                   | Expected Result                  |
  |-----|----------------------|-------------------------|----------------------------------|
  | 24  | Star Sizes           | Create P1/P2/P3 tasks   | Large/Medium/Small stars visible |
  | 25  | Touch Targets        | Tap near star edge      | Star selected (50px tolerance)   |
  | 26  | Drag Momentum        | Flick star              | Star continues with momentum     |
  | 27  | Zone Forces - Urgent | Task due < 2h           | Star drifts toward bottom        |
  | 28  | Zone Forces - Future | Task due > 24h          | Star floats toward top           |
  | 29  | Edge Clamping        | Drag star off-screen    | Star stays on screen             |
  | 30  | Cry System           | Let task become overdue | Red glow, screen shake           |
  | 31  | 60 FPS               | 50+ stars on screen     | Smooth animations, no lag        |

  ---
  Phase 5: Offline/Sync Behavior (4 tests)

  | #   | Test                | Steps                         | Expected Result         |
  |-----|---------------------|-------------------------------|-------------------------|
  | 32  | Offline Create      | Airplane mode → Create task   | Task saved locally      |
  | 33  | Offline Sync        | Turn on internet              | Tasks sync to backend   |
  | 34  | Conflict Resolution | Edit same task on two devices | Last write wins         |
  | 35  | Wallpaper Offline   | Airplane mode → Generate      | Uses cached or fallback |

  ---
  Phase 6: Edge Cases (5 tests)

  | #   | Test               | Steps                   | Expected Result                |
  |-----|--------------------|-------------------------|--------------------------------|
  | 36  | Empty State        | No tasks                | Shows encouragement message    |
  | 37  | 100+ Tasks         | Create 100 tasks        | All render, smooth performance |
  | 38  | Special Characters | Task with emoji/symbols | Renders correctly              |
  | 39  | Very Long Title    | 200+ character title    | Truncates gracefully           |
  | 40  | App Crash Recovery | Force kill → Reopen     | Tasks preserved, auto-login    |

  ---
  Test Results Template

  ## Device Testing Results
  **Device:** [Model]
  **Android Version:** [Version]
  **Date:** [Date]

  | Phase | Passed | Failed | Notes |
  |-------|--------|--------|-------|
  | Authentication | /5 | | |
  | Task CRUD | /8 | | |
  | Wallpaper | /10 | | |
  | Physics | /8 | | |
  | Offline/Sync | /4 | | |
  | Edge Cases | /5 | | |
  | **TOTAL** | /40 | | |

  ---
  Total: 40 manual tests covering all core functionality.
