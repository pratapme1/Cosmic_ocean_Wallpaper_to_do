# Issue Notes

- Test: UiFlowsE2ETest.privacySettingsToggleHideAllTasks
- Failure: instrumentation crashed during test run
- Evidence:
  - Report: android/app/build/reports/androidTests/connected/debug/index.html
  - Logcat: android/app/build/outputs/androidTest-results/connected/debug/Medium_Phone_API_36.1(AVD) - 16/logcat-com.cosmicocean.e2e.UiFlowsE2ETest-privacySettingsToggleHideAllTasks.txt
  - Screenshot: qa-runs/2026-02-05_release-2.7.1_device-sdk_gphone64_x86_64_api-36_retest-1/ui-tests/run_01_fail.png

Potential cause notes from logcat:
- Multiple Binder transaction failures (Operation not permitted)
- SELinux denied for service
- System services missing in emulator (persistent_data_block)
