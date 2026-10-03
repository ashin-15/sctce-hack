# MediaProjection acceptance contract

Status: Frozen for screenshots and bounded bursts. Approved by the user through "Do the recommended decisions" on 2026-10-03. Every criterion is release-blocking unless marked optional; none is marked passed until evidence is recorded.

- AC-001 (FR-001/004): User starts from foreground, accepts disclosure and real OS consent. One service and one VirtualDisplay start; new session asks again. Denial/cancellation/reused result/concurrent request creates no capture. Evidence: device flow recording, state/count assertions, manifest/service tests.
- AC-002 (FR-002): On API 34+ user selects an app; only permitted app output appears. Test system notifications/navigation, app switching and selected-content visibility. On API 26-33 display-wide disclosure is shown. Full-display capture is never represented as app-only. Evidence: synthetic device frames and scope metadata.
- AC-003 (FR-003): Synthetic FLAG_SECURE and policy-disabled windows cannot be bypassed. Dark ordinary window and blocked/blank output do not produce claimed proof of View Once/FLAG_SECURE. Evidence: captured fixtures, review/export wording assertions; OEM limits documented.
- AC-004 (FR-004): Stop button, OS chip stop where supported, lock, timeout, resource limit, competing projection and initialization failure terminate acquisition, close resources once, and reject late writes. No boot/sticky restart. Evidence: real-device/instrumentation state transitions and post-stop frame/file counts.
- AC-005 (FR-006): Background capture does not keep the vault unlocked. Draft/thumbnail/metadata files contain no plaintext evidence. Wrong-key/nonce/tag/metadata tampering fails closed. Review is inaccessible while locked. Evidence: instrumentation/storage inspection, crypto misuse/corruption tests, vault lifecycle regression.
- AC-006 (FR-006): Approved stop/revoke/lock/expiry/key policy is implemented exactly. Process death follows declared recovery/loss behavior; orphan ciphertext cleaned. Discard prevents later review/import and clears key ownership. Evidence: process/lifecycle tests, user-facing loss notice, before/after files. Physical flash erasure is not claimed.
- AC-007 (FR-005/008): Selected frame imports to selected case with SHA-256 matching captured original PNG after vault decryption. No derivative overwrites original. Failed import does not delete the pending frame; retry/recreation imports once. Evidence: byte parity, database/audit receipts, fault injection and save-idempotency tests.
- AC-008 (FR-005): Metadata separates capture time from unknown message time and user claims from verified capture facts. No grants/tokens/private data in logs/notifications/saved state. Reports label screen observations accurately. Evidence: metadata roundtrip, log/manifest scan, evidence/report fixtures.
- AC-009 (FR-002/009): Burst obeys every approved time/frame/pixel/byte/queue limit, shows gaps, and survives transient no-frame readiness. Exact duplicates may be grouped; one-character changes remain available. Evidence: synthetic change fixtures, limit/backpressure tests, device resource receipts.
- AC-010 (FR-002/004): Rotation/window resize changes geometry without a second VirtualDisplay or stale/corrupt output. Partial reader/service startup and repeated close do not leak resources. Evidence: device geometry fixtures, surface lifecycle/initialization fault tests.
- AC-011 (FR-007/008): Offline OCR/local analysis yields linked derivatives or an explicit failure/no-text status while saved originals stay readable. Unsupported script OCR is disclosed. No inference download/network fallback. Evidence: offline device runs, hash parity and derivative linkage assertions.
- AC-012 (FR-007/009): Layout guesses remain unconfirmed; sender/direction/time unknown until reviewed. Multiple overlapping screenshots do not alone create repeated-event escalation. Evidence: user review and temporal regression fixtures with accepted/rejected/edited findings and source coordinates.
- AC-013 (FR-008/010): Capture permission does not authorize export or additional alerts. Report/export requires user action; neutral alert consent stays separate. Evidence: UI/action/export tests and manifest inspection.
- AC-014 (FR-010): Existing notification, Accessibility, picker, Sharesheet, vault unlock/background lock, review and report flows pass their established regression gates. Evidence: relevant JVM/device suites and lint, separately labelled baseline/environment issues.
- AC-015 (FR-002/004/006): Approved capture/review budgets pass measured device tests; low space/oversize/full queue produce bounded safe outcomes and truthful receipts. Evidence: target/OEM/API inventory, peak memory/disk/timing/drops/battery/thermal measurements. Proposed bounds are not measurements.
- AC-016 (FR-010): Install/upgrade/feature-disable/rollback preserves saved evidence and metadata, stops active capture, clears temporary data, and prevents debug-only collector exposure. Evidence: release merged-manifest checks, upgrade/rollback tests, APK/device/version digest receipt. Relevant API 26/29/33/34/35-QPR1/36 coverage recorded.
- AC-017 (FR-011, deferred): Video implementation requires a new scope approval and revised acceptance contract.
- AC-018 (FR-011, deferred): Internal audio implementation requires a new scope approval and revised acceptance contract.

## Required acceptance report

For every applicable criterion record `ID | PASS/FAIL/BLOCKED | exact evidence path | device/build | caveat`. A missing blocking receipt is not a pass. Optional video/audio gates are excluded only when D-001 explicitly excludes them. This plan introduces no legal-authenticity or guaranteed-admissibility acceptance claim.

## Current judgment - 2026-10-03

No blocking criterion is marked PASS based on source or installation alone. All remain BLOCKED pending user-approved device capture and associated evidence. Existing automated evidence supports components only:

| ID | Status | Evidence and remaining gap |
|---|---|---|
| AC-001 | BLOCKED | Gradle build/manifest receipt; real consent, denial, and one-use session behavior not exercised. |
| AC-002 | BLOCKED | Android 16/API 36 device identified; app/window selection and displayed scope have not been captured. |
| AC-003 | BLOCKED | Blank-frame heuristic unit tests; protected/ordinary-dark device behavior not tested. |
| AC-004 | BLOCKED | Session/lifecycle source and JVM coverage; physical stop, revocation, lock, timeout, and late-write checks pending. |
| AC-005 | BLOCKED | Encrypted-store JVM tests; locked-device review gate and on-device storage inspection pending. |
| AC-006 | BLOCKED | Store/process-death JVM behavior; device lock/revocation/expiry cleanup pending. |
| AC-007 | BLOCKED | Import path compiled; on-device byte parity, retry/idempotency, and audit receipt pending. |
| AC-008 | BLOCKED | Metadata/source compiled; device metadata and log inspection pending. |
| AC-009 | BLOCKED | Bounded sampler tests; device resource/gap receipts pending. |
| AC-010 | BLOCKED | Build only; real rotation/resize and faulted surface lifecycle pending. |
| AC-011 | BLOCKED | Existing analysis integration compiles; saved capture OCR/derivative behavior on device pending. |
| AC-012 | BLOCKED | Parser regression tests preserve unknown authorship/direction; user review and temporal duplicate regression pending. |
| AC-013 | BLOCKED | Explicit UI and manifest compile; export and alert-consent interaction checks pending. |
| AC-014 | BLOCKED | Existing observation receipts are in `research/verification/android-observation-2026-10-03/`; projection regression against those flows not rerun in this verification. |
| AC-015 | BLOCKED | Hard caps are implemented; no Android resource, thermal, battery, storage, or latency measurements taken. |
| AC-016 | BLOCKED | Debug APK installed preserving data; release build, upgrade/rollback, and API inventory remain untested. |

Build/test/install evidence and the no-capture limitation are recorded in `research/verification/android-mediaprojection-2026-10-03/README.md`.
