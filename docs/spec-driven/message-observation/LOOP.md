# Execution loop

State: judging

Authorisation: user explicitly requested immediate app integration on 3 October 2026. Current work supersedes Accessibility deferral only for bounded user-started visible-text observation; unsupported access remains excluded.

Loop 1: integrate existing notifications, independent Accessibility probe, local neutral cue reminders and Stitch UI. All agents returned implementation. First combined build reached app Kotlin compilation but notification settings configuration allowlist test correctly failed after adding two consent booleans. Updated its exact allowed keys and roundtrip assertions. Both corrected checks passed. A UI test import mismatch was also corrected before the successful combined verification.

Required evidence and remaining limitations: see ACCEPTANCE.md. No third-party messaging app compatibility or threat-quality claim has been established. User/system permission grants cannot be bypassed for convenience.

## Loop 2 - runtime verification and delivery

Implemented and fixed notification connection state restoration across vault lock/unlock. Added generation-safe snapshot batching so an old callback cannot enter a new unlocked session. Six extra lifecycle/interleaving regressions pass. Restricted saving to active cases. Added system-binder-permission and disabled-by-default checks to the manifest verifier.

Evidence: combined selected JVM run passed 615 tests with 56 opt-in gallery skips; separate enabled gallery interaction run passed. App and acquisition-module lint pass (app 0 errors, 14 baseline warnings; no new collection/UI warning). Final APK build, mapping recheck and manifest check pass. Three isolated synthetic Accessibility device tests and one actual synthetic notification producer/listener device test pass on the attached Android 16 phone. Receipts in `research/verification/android-observation-2026-10-03/`. Test-only grants were removed and both test APKs uninstalled; the user's app grants were not set by the test harness. Final debug app installed without data clearing. No commits/pushes or model downloads.

Main judgment: implementation and automated/synthetic checks delivered; end-user and release acceptance still pending. Android system grants must be completed through the app's user-facing flows. The pending asynchronous prompt asks which grant the user has completed; it is not treated as consent by timeout.

Open acceptance: real end-user biometric/consent/save/alert flow, actual background-vault/screen-lock lifecycle on a user session, and per-app/version/OEM coverage. AC-02, AC-04 and AC-09 remain partly unverified; the synthetic fixture subset of AC-05 passes. No third-party capture reliability or threat-quality result is inferred from test receipts.

ENV-001 separate task: Python benchmark data refers to `C:\Windows\Fonts\Nirmala.ttc`. Cached Python 3.12 reference suite ran 22 tests with one font-path error and one optional decoder skip. Resolve benchmark font portability without silently rewriting raw manifests or substituting unverified glyph coverage.

Next action: user grants access in Android settings, then validate ordinary-message capture/review/save in selected apps. Keep all protected/ephemeral paths excluded and record failed/unknown coverage. Current state remains judging until these manual acceptance items are evidenced.
