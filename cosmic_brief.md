FEATURE BRIEF — HUD Overlay for the Live Wallpaper
(paste this whole file into the Cosmic Ocean repo agent)

Context (why)
The wallpaper owner uses this app as a personal Jarvis surface: the live wallpaper already shows reminders synced from Supabase. He now wants a second, independent layer: a small transparent PNG "HUD strip" of personal memory-cue icons that floats over the wallpaper. The image is produced OUTSIDE the app (by an assistant that pushes it to his GitHub repo); the app must only draw whatever PNG the user picks. The PNG is the interface — the app must never need changes when the image content evolves.

Feature
Draw a user-picked transparent PNG as an overlay layer in CosmicLiveWallpaperService's render pass, on top of the background art but never overlapping/obscuring the reminder text area.

Requirements
Settings UI (wherever wallpaper settings live today):
"HUD overlay image" — pick a PNG via ACTION_OPEN_DOCUMENT; call takePersistableUriPermission and store the URI in SharedPreferences.
"Vertical position" slider, 0–100% of screen height (default 80%).
"Opacity" slider, 10–100% (default 90%).
"Clear overlay" action.
Rendering (in the wallpaper engine):
Decode the bitmap ONCE and cache it; re-decode only when the URI, sliders, or surface size change (listen to preference changes the same way existing settings do). Never decode in the draw loop.
Scale to 90% of screen width, preserve aspect ratio, center horizontally, draw at the configured vertical position with configured alpha.
Draw order: background art → HUD overlay → reminder/task text. If the configured position would collide with the reminder text block, nudge the overlay up/down to clear it.
Fail-safes (a wallpaper service must never crash):
URI missing / permission revoked / decode failure → skip drawing silently; surface "Overlay image not found" in settings only.
Downsample very large images (inSampleSize) to at most 2× screen width.
Battery/perf: no extra frame invalidations — the overlay only draws as part of frames the engine already renders.
File convention (document this in the settings screen or README)
The expected image is a wide transparent PNG named hud-overlay.png (current one ~992×203), which the user stores under Pictures/ and re-picks only if the URI ever breaks. Replacing the file's content with a new version under the same name must "just work" on the next wallpaper redraw/restart (re-decode on surface restart is acceptable for this).

Out of scope (deliberately)
No fetching the overlay from network/Supabase — local file only, for now.
No in-app editing/composing of the strip.
Do not touch the reminders sync logic.
Acceptance
Pick image → strip appears over the live wallpaper at 80% height, tasks still fully readable.
Move sliders → position/opacity update live (or on settings close).
Replace the file content, restart wallpaper → new image shows.
Revoke/delete the file → wallpaper keeps running, no crash, settings show the not-found note.
Per AGENTS.md: include screenshots of the wallpaper with overlay in the verification, and screenshot-review them.