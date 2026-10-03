# Handoff: Sakshi Android implementation

**Date:** 3 October 2026, morning (IST)
**Branch:** `android-foundation`, pushed to `origin`. `master` is fast-forwarded to the same verified commits (latest `08ba91f` at the time of writing).
**Goal set by the owner:** complete the megaplan (`MEGAPLAN.md`). Also requested: a friendly, easy UI with a safe, calm colour scheme; commit and push after each addition and keep `master` merged.

## Read these first

1. `AGENTS.md` - binding product and engineering rules.
2. `MEGAPLAN.md` - sections 31 (phases), 35 (decision log, now up to D-25), 37 (validation backlog).
3. `benchmark.md` - ledger of every measurement; the newest entries are "Android phases 8, 9, 12 and 13" and "Android phase 11".
4. `docs/architecture/threat-model.md` - security model, 27.3 test coverage table, facts about the owner's new modules.
5. `android/README.md` - build, test, policy-check and speech-to-text preparation commands.

## How the work is done (owner's instructions)

- The main session plans, writes briefs, reviews and verifies. Code is written by Sonnet subagents (`model: "sonnet"`), one module per agent, never committing. The main session reruns builds and tests before claiming anything.
- Commit after each verified addition, push `android-foundation`, then fast-forward `master` (`git push origin HEAD:master`). Never add an agent co-author line to commits.
- Style: no em or en dash anywhere, no `@Suppress`, no lint disabling, zero compiler warnings, no `android.util.Log`, British "behaviour", `…` not `...` in strings. `:tools:policy:test` enforces most of this.
- Downloads only in explicit preparation steps. Never commit model binaries (`models/` and `android/third_party/` are git-ignored).
- The owner works in the same tree at the same time. Before editing a file, check `git status`; do not commit the owner's uncommitted files; when a file both sides touched must be committed, stage only your hunk (for example with `git update-index --cacheinfo`).

## Verified state

Clean checkout of `454130e` (before the notification module and ledger commits): `./gradlew test :app:assembleDebug :app:lintDebug :app:verifyManifestPermissions` passed, 1,304 JVM tests in 15 modules, 0 failures; lint 0 errors, 14 warnings (version notices). Notification module (`3875e66`): 122 JVM tests pass. Device: 40 instrumented tests in five suites, then 33 in the vault and analysis suites including 7 new security tests, all passed on CPH2695 (Android 16), phone unlocked and kept awake with `adb shell svc power stayon usb`.

Command (JDK 21): `cd android && JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew test :app:assembleDebug :app:lintDebug :app:verifyManifestPermissions`. To verify a commit without the owner's uncommitted edits, use a separate `git worktree` and copy `android/local.properties` into it.

## Megaplan phase status

| Phase | State |
|---|---|
| 1-7 core, vault, import, text, review, temporal UI | Done earlier (see git history before `bf0b76c`) |
| 8 Search | Done: filters, accepted-tags scope, notes searchable as the person's own statements |
| 9 Reporting and export | Done in library and app, except the redaction screens in the app (library supports text redaction) and bundled Noto fonts (not approved for download yet; Indic PDF shaping V-11 open) |
| 10 OCR (Latin) | Done; Devanagari and Malayalam OCR deferred by owner (D-23) |
| 11 Speech to text | Library done and device-tested (`processing/stt`, whisper.cpp v1.9.4, base q5_1); analysis integration and app screens not built |
| 12 Notification observation | Library module and importer entry (`commitNotificationExcerpt`) done; not wired into the app |
| 13 Security hardening | Tests and threat model done; storage-exhaustion and image-decoder seams missing |
| 14 Real-device validation | Partly: device suites pass; V-09 backup, V-05 real biometric invalidation, V-10/V-12 release numbers, V-17 airplane-mode launch not done |
| 15 Release qualification | Not started; needs owner decisions (licence Q1, counsel Q10, native-speaker review Q6) |

Stored patterns with staleness and pattern review (megaplan 18.1 to 18.3) and numbered report versions are done.

## Work in flight when this was written (uncommitted)

No subagent is running. Everything the main session verified is committed and on `master`.

1. **Screenshot harness, stopped by the owner.** Partial files in `android/app/src/test/kotlin/org/sakshi/app/screenshots/` and two test-only catalog entries (`androidx-compose-ui-test-junit4`, `androidx-compose-ui-test-manifest`) with three `testImplementation` lines in `app/build.gradle.kts`. Do not commit unless the owner wants the harness resumed; otherwise remove them.
2. **Owner's own uncommitted work (do not touch or commit):** AI model screen, several screens (`CaseListScreen`, `CaseDetailScreen`, `WhoIsWhoScreen`, `EventReviewScreen`, `SearchScreen`), a UI redesign in progress (`ui/theme/Palette.kt`, `Spacing.kt`, `Type.kt`, `ui/components/Buttons.kt`, `SakshiCard.kt`, new `CalmSanctuaryComponents.kt`, `ic_sakshi_logo.xml`, launcher foreground), `strings.xml`, and a design specification in `stitch_design_specification_project/` (and its zip) at the repo root. The friendly-UI request should follow that specification; ask the owner before redesigning anything independently.

Prepared on this machine (not in git): NDK `29.0.14206865` and SDK CMake `3.31.6` under `~/Android/Sdk`, command-line tools under `~/Android/Sdk/cmdline-tools/latest`, whisper.cpp source in `android/third_party/whisper.cpp` (from `android/tools/prepare-whisper.sh`), model `models/whisper.cpp/ggml-base-q5_1.bin` (also pushed to the phone at `/data/local/tmp/`).

Verification helper used this session: a clean `git worktree` of `HEAD` with only the files under test copied in, so the owner's uncommitted edits never affect a result.

## Owner decisions made today

- D-24: keep the on-device LLM module `processing/llm` and the AI model screen (supersedes D-07).
- D-25: keep the MediaProjection module `acquisition/projection` (supersedes that rejected-list entry). Policy check allows `MediaProjection` in that module only.
- Approved preparation: NDK install, whisper base q5_1 download, phone kept unlocked for device tests. Not approved yet: bundled Noto fonts.

## Open problems found (owner's modules, reported, not changed)

- `processing/llm`: `NativeLlmBridge` loads a native library that no module builds, so the deterministic fallback engine is what runs. Model files sit unencrypted in `filesDir/models`; `verifyChecksum` exists but is never called. `importModelStream` joins the display name to the models directory without sanitising (possible `..` escape, untested). Download URLs open in an external browser; the app itself has no `INTERNET` permission.
- `acquisition/projection`: not a dependency of `:app`; its manifest declares `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PROJECTION`, which `:app:verifyManifestPermissions` will reject until the allowlist is changed on purpose.

## Next steps, in order

1. Phase 11 analysis integration (library only, no app needed): `TextAnalysis.analyseAudio` like `analyseImage`; transcript derivative with `TranscriptDocument.sourceMapJson()`; one event per clip via `AudioEventPlan` with text plus audio-time anchors; new not-analysable reasons need matching branches in the app's `AnalysisText.kt` (owner's file area), so coordinate. Details are in the phase 11 subagent's design: see `processing/stt` KDoc and `android/README.md`.
2. Once the owner's app edits are committed: app wiring that is waiting on `app/`:
   - report redaction screens (library API: `ReportSelection.redactions`, `Redactor`, `Built.includedOriginalHoldsRemovedText`, `ExportSummary` redaction counts; a full brief was written once and lost to the usage limit: word-chip removal, live preview, acknowledgement when an included original still holds removed words, standing note that originals carry their own metadata);
   - notification lane: disclosure and consent screen, allowlist and settings, coverage display without any "all clear" wording, `stopAndClear()` on lock and `beginSession()` on unlock, candidate review and import through the new importer entry;
   - speech model preparation screen (`ModelProvisioner.forContext(ctx).importFrom(...)`) and a "Transcribe audio" action; share one `HeavyModelLock` with the LLM's `InferenceLock`;
   - map `NotificationExcerptHandoff` to `NotificationExcerptImport` field by field (no module can do it without a dependency cycle);
   - align with the owner's UI redesign.
3. Notification device test: on this phone `pm grant ... POST_NOTIFICATIONS` is refused from adb; turn on ColorOS "Disable permission monitoring" or tap Allow, then grant listener access with `adb shell cmd notification allow_listener org.sakshi.acquisition.notifications.test/org.sakshi.acquisition.notifications.SakshiNotificationListener`, run `SyntheticNotificationDeviceTest`, and revoke afterwards.
4. Device validation backlog: V-09 backup extraction, V-05 real biometric invalidation, V-17 airplane-mode cold launch, V-10 and V-12 release-build numbers.
5. Owner questions still open: licence (Q1), native-speaker review of cue lists (Q6), counsel (Q10), Noto fonts download, schema v1.1 (Q5).

## Known weaknesses (still true)

- Cue lists are the unreviewed demo set; matching ignores negation and quotes.
- The Python bench suite needs its environment (`sklearn` and others are missing on this Linux machine; not recreated).
- A lost or invalidated device key makes the vault unrecoverable; no recovery bundle.
- The audit chain cannot detect removal of its newest entries unless the head is kept elsewhere.
- Patterns are computed for the report over the selected events only; stored pattern reviews apply to the case-wide descriptions on the patterns screen.
- No screen has been used by a person; TalkBack, large fonts and dark theme are unverified on a device.
