# Acceptance and evidence

| ID | Scenario and required evidence | Blocking |
| --- | --- | --- |
| AC-01 | Default collectors off; only explicitly selected packages inspected; JVM gate/allowlist tests and manifest inspection | yes |
| AC-02 | Consent, permission denial/revocation, pause/off, neutral coverage and draft inbox usable from live app; UI/device check | yes |
| AC-03 | Save notification preserves exact text, independent clocks, sender hints, removal lifecycle; mapping tests plus encrypted import tests | yes |
| AC-04 | Background opt-in leaves vault locked; screen off clears drafts and alerts; real-device lifecycle validation | yes |
| AC-05 | Accessibility five-minute allowlist capture, bounded windows, sensitive/ephemeral rejection, no actions; policy tests plus synthetic visible-window device test | yes |
| AC-06 | Snapshot resembling an export yields one uncertain event with unknown attribution/time, exact preserved quote; analysis regression test | yes |
| AC-07 | Stitch shell uses real cases and routes Evidence/Incidents/Reports and collection controls; compose render/interaction checks | yes |
| AC-08 | Build, app lint, source policy, manifest permission/exported component checks pass; no new suppressed findings | yes |
| AC-09 | Local cue reminders require consent and notification grant, contain no evidence, open review after unlock; device test | yes |

Release is not accepted while a blocking row lacks evidence. Passing library tests is not third-party app compatibility proof. No unmeasured latency/RAM/battery/quality figures are claimed.

## Current results - 3 October 2026

| ID | Result | Evidence or remaining limit |
| --- | --- | --- |
| AC-01 | PASS for implemented default/allowlist policy | Policy/unit suites, merged manifest system-binding guards |
| AC-02 | PARTIAL | Working routes/screens compile; callback tests pass; actual authenticated user flow and OS grants pending |
| AC-03 | PASS for automated contract slice | Mapping roundtrip tests, existing importer/analysis suites; actual user saving pending with AC-02 |
| AC-04 | NOT VERIFIED on user session | Lifecycle implementation and lock/unlock callback regressions pass; actual app switch/screen lock with private vault pending |
| AC-05 | PASS for synthetic fixture slice | Three actual service/window Android 16 tests; no third-party app compatibility claim |
| AC-06 | PASS | VisibleSnapshotAnalysisTest: chat-shaped snapshot stays one uncertain event with unknown attribution/time |
| AC-07 | PASS for render/callback slice | Synthetic Home renders at light/dark 1.0/1.5; workspace and collection navigation callback test; real-device visual acceptance pending |
| AC-08 | PASS | Build/policy/manifest and selected-module lint; 14 baseline app lint warnings remain documented |
| AC-09 | PARTIAL | Consent and permission-gated neutral reminder code; actual posted synthetic notification observation passes; actual app cue reminder/deep link pending |

Release acceptance is pending. Features are installed and available behind their explicit user-granted Android access flows.
