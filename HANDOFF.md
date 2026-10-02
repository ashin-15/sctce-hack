# Handoff: Sakshi Android implementation

**Date:** 2 October 2026
**Branch:** `android-foundation` (tracks `origin/android-foundation`)
**Status:** Foundation implementation complete. All 837 JVM tests across all 12 modules pass (0 failures, 0 errors, 0 skipped), Android Lint passes with 0 errors, manifest permissions strictly verified, and 33 connected Android tests pass on the attached Samsung SM-S928B. All UI workarounds are migrated to the library APIs. Ready for owner review and manual on-device flow checks.

This file replaces the earlier research handoff (sentiment and emotion design). That document is still in git history at commit `7a18ef2`; its decisions that still bind this work are carried in `AGENTS.md` and in `MEGAPLAN.md` section 2.

## Read these first

1. `AGENTS.md` - binding product and engineering rules, including the do-not-build list.
2. `MEGAPLAN.md` - the implementation plan. Sections 31 (phases), 35 (decision log) and 37 (validation backlog) are the ones to keep open.
3. `benchmark.md` - the ledger. Every measurement and every "not verified" statement is recorded there. Update it at each milestone.
4. `android/README.md` - build and test commands.

## How the work is being done

- The main session designs, writes briefs, reviews and verifies. Code is written by Sonnet subagents from those briefs. This was the owner's explicit instruction.
- Each subagent is confined to one module, forbidden to commit, and must report what it did not verify. The main session reruns the build and tests before reporting anything as working.
- Device tests use synthetic data inside the test package's own sandbox. Nothing else on the phone is read.
- Style rules in force everywhere: no em dash or en dash, no `@Suppress`, no lint disabling, zero compiler warnings, no `android.util.Log` in main code, British "behaviour", no agent co-author trailer on commits.
- Commits and pushes happen only when the owner asks.

## Git state

Committed and pushed:

| Commit | Contents |
|---|---|
| `3a74118` | Megaplan, root README, Gradle skeleton, `:core:model`, `:core:integrity` |
| `01b20b4` | `:core:temporal` |
| `366d666` | `:core:crypto`, `:core:database`, `:core:vault`, app lock and case screens, first device tests |

Ready to commit: the full foundation implementation covering `:acquisition:importer`, `:processing:text`, `:processing:analysis`, `:export:bundle`, `:export:report`, the event store, review coordinator, derivative store and actor registry in `:core:vault`, schema additions in `:core:database`, the design system and entire screen hierarchy (onboarding, lock, cases, import, analysis, timeline, review, who-is-who, patterns, report preview and export).

## Modules

| Module | Kind | State |
|---|---|---|
| `:core:model` | Kotlin/JVM | Typed event contract for `data/sakshi-event-schema.json`, code-point spans, invariants (42 tests) |
| `:core:integrity` | Kotlin/JVM | SHA-256, hash chain, Merkle v2, canonical JSON; matches `bench/adapters.py` on shared vectors (28 tests) |
| `:core:temporal` | Kotlin/JVM | Deterministic pattern engine, four demo rules, template explanations, zoned render, typed facts (62 tests) |
| `:core:crypto` | Kotlin/JVM | Chunked AES-256-GCM blob envelope, key wrapping interface (33 tests) |
| `:core:database` | Android | Room schema (26 tables), insert-only triggers, SQLCipher open path (34 tests) |
| `:core:vault` | Android | Keystore wrapper, blob store, audit chain, case/evidence repositories, event store, actor registry, derivative store, review coordinator (142 JVM tests, 22 device tests) |
| `:acquisition:importer` | Android | Share intents, pickers, paste, manual notes, streaming limits (84 JVM tests, 4 device tests) |
| `:processing:text` | Kotlin/JVM | Language hints, rules cue engine, label mapping, WhatsApp text-export parser (52 tests) |
| `:processing:analysis` | Android | Imported text to derivative, events, single-pass EventText, TextAnalyser, on-demand patterns (48 JVM tests, 2 device tests) |
| `:export:bundle` | Kotlin/JVM | Bundle writer, offline verifier, command-line tool (47 tests) |
| `:export:report` | Android | Report model, PDF renderer, Keystore signer, export service, audit records (38 JVM tests, 5 device tests) |
| `:app` | Android | Onboarding, lock, cases, import, analysis, timeline, review, who is who, patterns, report preview and export (227 tests) |

## Last full verification by the main session

Full verification command: `cd android && JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew test :app:assembleDebug :app:lintDebug :app:verifyManifestPermissions` passed in 2m 8s:
- 837 JVM unit tests passed, 0 failures, 0 errors, 0 skipped.
- `:app:lintDebug` passed with 0 errors.
- `:app:verifyManifestPermissions` confirmed only `USE_BIOMETRIC`, `USE_FINGERPRINT`, and dynamic receiver permission; zero `INTERNET` access.

Device test command: `./gradlew :core:vault:connectedDebugAndroidTest :acquisition:importer:connectedDebugAndroidTest :processing:analysis:connectedDebugAndroidTest :export:report:connectedDebugAndroidTest` passed in 1m 51s on Samsung SM-S928B (Android 16, API 36):
- 33 connected device tests passed, 0 failures, 0 errors, 0 skipped.

## Completed items in this pass

1. **Library enhancements:**
   - `:core:vault`: added `loadLatest(EventId)`, `revisions(EventId)`, `observeEvent(EventId)`, `senderClaims(CaseId)`, `observeSenderClaims(CaseId)`, `decisionsForEvent(EventId)`, atomic `assignSenderToNewPerson(...)`, `clearBoundary(...)`, `ActorRegistry.observe(...)`, and `ActorRegistry.rename(...)`.
   - `:core:temporal`: added zoned `PatternExplanation.render` and `PatternFacts` for localization.
   - `:processing:analysis`: added `EventText.bodiesOf(events)` and `quotesOf(event)` for single-pass code-point body/quote slicing, `TextAnalyser` interface, and `CasePatterns.compute(..., withSupportingEvents = true)`.
   - `:export:report`: added zoned pattern sentences, Keystore signing integration, PDF rendering, export audit records, and export error discriminators.

2. **Report and export screens (`:app`):**
   - Implemented report selection (with zero preselected items), on-screen preview of the exact model, Keystore-signed PDF/zip export, result screen with signing key ID, and secure private sharing via FileProvider with export cache cleanup on discard/leave/lock/unlock.

3. **Workaround migrations and cleanups:**
   - `HistoryRows.kt`: eliminated JSON regex parsing in favor of typed `DecisionTargetKind` and `DecisionChange`.
   - `EventReviewViewModel.kt`: switched from revision probing to `vault.events.loadLatest(EventId)`.
   - `WhoIsWhoViewModel.kt`: observed actors via `vault.actors.observe(CaseId)` and used atomic `vault.review.assignSenderToNewPerson(...)`.
   - `PatternsViewModel.kt`: migrated to `CasePatterns.compute(..., withSupportingEvents = true)`.
   - `ReportViewModel.kt` and `TimelineViewModel.kt`: migrated to `EventText(vault).bodiesOf(...)`.
   - `BodySlices.kt`: deleted obsolete workaround class and migrated tests.
   - Resolved test deadlocks in `ReportViewModelTest`.

## Waiting on the owner

- **Manual checks on the phone.** Unlock flow; share an image from another app into a case; pickers; lock on background; and the script in the next section. The share check matters most: whether the read grant on a shared file survives the hand-off from `ShareTargetActivity` to `MainActivity`. If it does not, every shared file fails to save and the trampoline design needs rework.
- **Picker grace window.** While a system picker or share sheet opened from the app is showing, the app does not lock, for at most two minutes. This weakens lock-on-background on purpose. Needs a yes or no.
- **Recording the sending app.** Imported items carry no record of which app shared them. Proposal: store the sender package name as a claim.
- **Look and feel review** of the design system (debug builds: long-press "Sakshi" in the case list to open the design catalogue): dark theme, double font size, the "Note." prefix on evidence rows, Malayalam and Hindi rendering.
- Megaplan open questions Q1 to Q10 (project licence, reference phone, hackathon date and expectations, schema v1.1, native-speaker review, recovery, counsel).

## Manual test script (synthetic data)

Paste into a new case with "Paste text":

```
24/09/2026, 21:05 - synthetic-alex: please stop messaging me
24/09/2026, 21:10 - synthetic-sam: hello
24/09/2026, 21:12 - synthetic-sam: why do you ignore me
24/09/2026, 21:15 - synthetic-sam: answer me
24/09/2026, 21:20 - synthetic-sam: you are an idiot
24/09/2026, 21:25 - synthetic-sam: reply now
24/09/2026, 21:30 - synthetic-sam: I am waiting
```

1. Save, tap "Analyse now", choose `synthetic-alex` as yourself, continue.
2. Open the timeline, tap "hello", then "This is ..." and name the sender as a new person.
3. Open "please stop messaging me", choose that person under Boundary, tap "This is where I asked them to stop".
4. Open "Patterns": expect a "Contact after a boundary" card whose sentence contains "6", with its limits listed.

## Not verified

- Any screen used by a person; TalkBack; large fonts; dark theme.
- A file share from another app; the Photo Picker and document picker on the device.
- The unlock path with a user present, the not-authenticated and key-invalidated paths, lock on background.
- Backup and device-transfer exclusion (megaplan V-09).
- Android versions below 16 on a device (the pre-Android 13 share path ran only under Robolectric), 16 KB page devices, lower-RAM devices.
- Correct shaping of Malayalam and Devanagari in the exported PDF. Only "first page is not blank" was checked.
- Any release-build, battery or thermal measurement.
- A real WhatsApp export from any locale. The parser has only seen synthetic text.

## Known weaknesses and deviations from the megaplan

- **Cue lists are the unreviewed demo set** ported from `bench/adapters.py`. No native speaker has checked them. Matching has no context: negated and quoted phrases match. The megaplan says unreviewed lists ship disabled for real use; the demo build has them on and says so in the review screen.
- **Rules versus fixture gold labels:** 19 of 600 synthetic rows differ. This is a fixture regression figure, not accuracy.
- **Schema v1 of the database was changed in place several times** (unreleased). Any device with an older debug install needs its app data cleared.
- **Compose BOM 2026.06.01 and API 36** are pinned because Android SDK platform 37 is not installed. Lint shows version-availability warnings; they are left open. `TopAppBar` and `ModalBottomSheet` are experimental in this BOM and are not used.
- **Keystore unlock window on API 26 to 29** is set through reflection because the only API for it is deprecated. Untested on such a device.
- **Export signature** is ECDSA P-256 (megaplan decision D-09), not the Ed25519 the bench measured. Nothing ties the signing key to a person: a re-signed bundle verifies with a different key id, so the key id must be compared out of band.
- **PDF uses system fonts**, not bundled Noto fonts.
- **Patterns are computed on demand** and not stored in the `pattern` tables yet.
- **The audit chain** cannot detect removal of its newest entries unless the head is kept elsewhere.
- **A lost or invalidated device key makes the vault unrecoverable.** There is no recovery bundle.
- **The orphan-file sweep** must only run at start-up; nothing enforces that.
- **Python suite on Linux:** `test_screenshot_text_has_renderable_glyphs` errors because the committed screenshot manifest references Windows font paths. Not caused by the Android work.

## Not built yet (megaplan order)

1. OCR for screenshots (ML Kit bundled Latin, megaplan phase 10), including the check that it works with no `INTERNET` permission.
2. Storing patterns and report snapshots; staleness tracking in the database rather than recompute on demand.
3. Search (phase 8).
4. Audio transcription (phase 11) and the secure seekable source it needs.
5. Optional notification observation (phase 12).
6. Security hardening pass (phase 13): the full 27.3 test list, forbidden-API lint, log audit.
7. Release qualification (phase 15): licence, notices, privacy policy, counsel review.
