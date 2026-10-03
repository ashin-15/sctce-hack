# MediaProjection verification receipt

Date: 2026-10-03

## Verified

- Projection module: `:acquisition:projection:testDebugUnitTest` passed, 16 tests, 0 failures, 0 skipped. Report: `android/acquisition/projection/build/reports/tests/testDebugUnitTest/index.html`.
- Regression and policy suites: `:app:testDebugUnitTest` passed (413 tests, 56 opt-in skips, 0 failures/errors); `:tools:policy:test` passed (36 tests, 0 failures/errors). The policy scanner covers the narrowly scoped MediaProjection module exception and no-suppression rule.
- App checks: `:app:lintDebug`, `:app:assembleDebug`, and `:app:verifyManifestPermissions` passed in the same Gradle run. Lint reported no errors; existing version/resource warnings and Gradle deprecation notices remain.
- Manifest: `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PROJECTION` are present; the projection capture service is declared non-exported with `mediaProjection` foreground type. The app manifest verifier passed.
- Connected device: OPPO CPH2695, Android 16, API 36. `adb install -r` succeeded, preserving app data; launch succeeded and `pidof org.sakshi.app` returned PID 8318.
- Debug APK SHA-256: `70a94254109e60bbfe625a2aa81ac2d5ea5fe555946e3516036bd26680d83e19`.

## Not exercised

No MediaProjection consent was granted and no pixels were captured during this verification. Android consent/denial, actual screenshot and burst, draft decryption/review/save/discard on device, hash parity through vault storage, OS revocation, lock cleanup, rotation/resize, timeout, resource limits, and release/upgrade behavior remain unverified. This receipt is build and install evidence, not feature acceptance or a performance benchmark. These checks require the phone user to initiate capture and decide whether to approve Android's system prompt.

The feature must not be described as accepted for release until the blocking device criteria in `docs/spec-driven/media-projection/ACCEPTANCE.md` have matching evidence.
