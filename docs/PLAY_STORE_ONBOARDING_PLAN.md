# Play Store Onboarding Plan (Local-Only Release)

**Version:** 2.6.3  
**Date:** 2026-02-04  
**Target:** Android (local-only, no backend sync)

---

## Scope
- Release the Android app as a **local-only** product.
- No account sync, no backend auth required.
- Privacy and environment features remain **on-device only**.

## Current Status
- Code changes for local-only mode implemented.
- Cleartext traffic disabled.
- Backups disabled.
- Tests passing (`connectedDebugAndroidTest`, `testDebugUnitTest`).

## Required Items (Before Play Store Upload)
1. **Privacy Policy URL**
   - Host a privacy policy stating local-only data storage.
   - No data shared with third parties.
2. **Data Safety Form**
   - Declare no data collected or shared if accurate.
   - Confirm no account or cloud sync.
3. **Store Listing Assets**
   - App icon, feature graphic, screenshots.
   - Short description and full description.
4. **Release Signing**
   - Confirm keystore file and credentials.
   - Validate release signing in CI or local build.
5. **Release Bundle (AAB)**
   - Build `:app:bundleRelease` for Play Store upload.
6. **In-App Messaging**
   - Ensure UI and settings indicate local-only behavior.
   - Mention no account sync, data stays on device.

## Release Artifacts
- APK (local verification):  
  `android/app/build/outputs/apk/release/app-release.apk`  
  `android/app/build/outputs/apk/release/cosmic-ocean-v2.6.3.apk`
- AAB (Play Store):  
  `./android/gradlew -p android :app:bundleRelease`

## Testing Checklist
1. `./android/gradlew -p android :app:connectedDebugAndroidTest`
2. `./android/gradlew -p android :app:testDebugUnitTest`
3. Manual smoke test on device:
   - App opens without login errors.
   - Tasks render correctly on wallpaper.
   - Privacy masking works.
   - Environment toggle off removes overlays.

## Compliance & Policy Checklist
1. Privacy policy hosted and linked in Play Console.
2. Data Safety form completed.
3. No cleartext traffic in release.
4. Backups disabled for private data.
5. No debug-only menus visible in release.

## Open Questions
1. Do we want to keep local-only indefinitely or add optional login later?
2. Will we include analytics or crash reporting in v2.6.3?
3. Which screenshot set should be used for store listing?

---

## Acceptance Criteria
- AAB upload succeeds in Play Console.
- Data Safety and privacy policy approved.
- Review passes with no policy warnings.
