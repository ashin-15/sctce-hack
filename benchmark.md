# Sakshi benchmark - milestone ledger

## M0: repository and resource audit

Completed: cloned the empty requested repository; verified Python, Git, baseline scientific packages, and the laptop hardware. No inference service is used.

- Host: Lenovo 83ER, Intel Core i5-12450H, 16,857,817,088 bytes physical RAM, Windows 11 Home Single Language 10.0.26200.
- Android: unavailable. Target is a hypothetical 6 GB CPU-only phone; a laptop RAM constraint is NOT an Android emulator or a predictor of battery/thermal behaviour.
- Download permission: at most 5 GB. Public speech data requested; FLEURS dataset card lists CC-BY-4.0. Downloaded assets will retain source URLs, hashes and attribution.
- Thermal sensors: not yet measured; unknown is not equivalent to cool.
- No model measurements yet. No accuracy, latency, energy, or stack recommendation is asserted.

## Milestones

1. M1: deterministic synthetic text, screenshot and extraction datasets; public speech import with provenance and validation.
2. M2: offline runner, complete requested candidate inventory, metrics, measurement metadata, scoring, and tests.
3. M3: execute obtainable laptop candidates within budget; publish actual CSVs, plots, weaknesses and provisional recommendations.
4. M4: integration/degraded-mode tests and final completeness audit. Native Android work and unimplemented model adapters remain explicitly open when unavailable.

A milestone is recorded as complete only for work actually performed. This ledger is updated and pushed with each milestone. The complete model/runtime matrix cannot be represented as completed by a handful of baselines.

## M1: data fixtures and preparation - completed with coverage gaps

- Generated and validated 600 synthetic multi-label messages in six language categories; fixed seed 1729; exactly 420/90/90 train/validation/test. Orthographic variants stay within their group/split.
- Rendered 168 synthetic chat screenshots with two layouts, light/dark modes, font/resolution/JPEG/blur/rotation variants. Windows Pillow lacked RAQM, so rendering now uses HarfBuzz + FreeType. Native-speaker/visual validation remains pending.
- Generated 100 structured extraction fixtures with exact source quotes. These are single-message threads; realistic multi-message annotation remains to be added.
- Downloaded CC-BY-4.0 FLEURS test data and prepared 60 complete-utterance concatenations, 10-120 seconds, English/Hindi/Malayalam, in clean/noisy pairs. Attribution, source IDs, transformations, and SHA-256 hashes are retained. Natural Hinglish/Manglish and harassment-domain audio are missing, NOT fabricated.
- Data smoke test passed; regenerated output is deterministic on this host. Image/audio binaries are ignored by Git and reproduced with explicit preparation commands; manifests and public ground-truth text are committed.
- Residual parallel-template leakage across languages and limited semantic diversity make these synthetic sanity checks, not evidence of production accuracy. Labels are authored, not native-speaker-adjudicated.
- Prepared local faster-whisper base assets at exact upstream revision `ebe41f70d5b6dfa9166e2c581c45c9c0cfc57b66`; vocabulary filename corrected after an actual 404. This is an EXTRA budget-friendly candidate, not a substitute for the requested small/large tests.

## M2: offline baseline harness - completed, full adapter matrix remains open

- Added one-command component runners, fresh candidate processes, socket-blocked/local-only inference, one warmup and five repeats, median/p95 CSV output, high-water working-set/RSS sampling, exact asset/source/data hashes, versions, commit/dirty state and per-run hardware metadata.
- Added 1,514 inventory entries spanning requested families, VAD variants, planned LLM quant/runtime/output combinations and export plans. Inventory counts are NOT execution counts. Missing adapters are pending, not unsupported-model skips.
- Implemented sixteen baseline candidate adapters plus scoring/report generation. Full weighted ranking refuses unknown hard gates/missing dimensions instead of inventing battery or integration values.
- Eighteen tests passed, including tamper/reorder/truncation detection, exact-quote checking, validation operating points, offline guard, user-review gate and real laptop process interruption/recovery of a synthetic hash queue.
- Verification found and fixed two harness bugs: NumPy vocabulary indices were not JSON serializable; initial screenshot sampling accidentally omitted the text test split. Failing regression tests reproduced both. Screenshots were regenerated with 84 train / 24 validation / 60 test fixtures, covering every language in the held-out set. No OCR result from the defective initial fixture selection is used.
- Current limits: load time is a single observation; per-language throughput can be pooled; model size excludes app/runtime packages; current queue is single-worker/fixture-only. Native Android, natural code-mixed speech, all neural/LLM adapters, exports, escalation and five actual stacks remain open.

## Branch integration: main into master

- Integrated committed benchmark history from `main` (`586e4bf`) into existing `master` (`7718e72`) using a merge of the two independent histories, without rewriting either history.
- Preserved existing research, presentation and dataset files on master. Resolved the AGENTS.md add/add conflict by retaining Sakshi's complete operating contract and appending the benchmark verification guidance.
- At this initial merge, uncommitted runner fixes and preliminary outputs were intentionally excluded. The subsequent M3a publication below now adds those completed results.

## M3a: measured baseline publication - completed subset, not the full matrix

- Audited and summarized fourteen full applicable-data candidate runs: three classifier baselines, script language ID, template extraction, sender/time linking, SQLite AES-GCM, Argon2id, four integrity schemes, ReportLab and the export-parser fixture. Component CSVs, per-run metadata/trials/predictions, descriptive Pareto plots, language error counts and RECOMMENDATION.md contain actual laptop observations.
- On the synthetic held-out fixtures, measured macro-F1 was 0.4063 for TF-IDF/logistic regression, 0.9851 for authored rules, and 0.9938 for their ensemble. The rules know the fourteen generation templates; these scores do NOT establish real-world harassment accuracy.
- Weighted scores/top-three shortlists and stacks A-E remain withheld: integration, energy/thermal, some licences and native measurements are missing. Empty plot panels and pending stack rows are intentional, not zero-valued results.
- RapidOCR and faster-whisper base (with/without Silero VAD) passed one-item smoke subsets, each with one warmup and five repeats. Smoke rows are tagged and excluded from full-data summaries. Full OCR/STT results are still pending at this milestone.
- The offline guard exposed an implicit RapidOCR visualization-font download; supplying explicit local weights and a local font fixed it without allowing inference networking. PyAV 19 was incompatible with faster-whisper 1.2.1; the reproducible lock pins PyAV 15.1.0.
- OneDrive repeatedly denied environment replacement. With user-approved access, installed all sixty-four hash-locked dependencies outside OneDrive at %LOCALAPPDATA%/SakshiBench/venv. Twenty tests passed in that environment; the codec and metadata-link regressions passed, and offline OCR/STT smoke runs completed.
- Fixed CSV links to point to committed metadata rather than ignored raw output; the publication audit checks five finite timed runs, one warmup, predictions and device fields. Link repairs do not change measured numeric values.
- The CSVs preserve dependency versions, source/data/model hashes and dirty-source status of the original runs. Initial measurements were taken during development; future full runs should use the committed source version. Battery consumption and thermal state are not inferred from laptop timings.
- Ingestion permissions/effort/policy notes are documentation assessments, not native feasibility tests. Natural Hinglish/Manglish audio, native-speaker label/font review, realistic multi-message linking fixtures, neural/LLM/export adapters and Android energy/lifecycle validation remain open.

## M3b preparation: fixture and integrity audit - completed

- A failing font-coverage regression identified twenty-three screenshots whose selected font lacked U+1F61F. Implemented glyph-checked local font fallback, recorded every used font and its SHA-256, and regenerated the 168 screenshots. The text, extraction and public-audio fixtures are unchanged. Glyph availability is verified; native-speaker visual shaping review is still pending.
- A failing integrity regression reproduced duplicate-padding ambiguity in the original Merkle root, including the empty/single-empty-entry boundary. Merkle v2 now commits to the entry count with a separate root domain prefix. Existing M3a measurements remain historical and are not silently replaced; the revised method needs fresh timings.
- Twenty-two tests passed after these fixes. Full OCR, full faster-whisper base with/without VAD, fresh Merkle timings, and the extra laptop proxy pipeline are the next measurement jobs. No results for those jobs are claimed at this preparation milestone.

## Temporal-patterns research verification - 2 October 2026

- Completed the temporal research handoff: primary-source metric cross-checks, event-schema/example validation and a desktop/390px browser check in dark/light themes. Reproduction commands and receipts are in `research/verification/temporal-patterns-README.md`.
- All 84 synthetic replay combinations passed; filtering, theme/disclosure controls, navigation targets and page/SVG bounds passed, with no runtime exceptions or remote requests. Fixed the suggested "recurrence" search returning no algorithm rows.
- Draft 2020-12 schema validation passed with date-time checks; the synthetic example had zero errors and six malformed variants were rejected. Cross-record application invariants are still engineering work.
- Repository suite: 20 tests run, 19 passed and one optional faster-whisper decoder test skipped because the runtime is absent. No inference dependencies or existing measured results changed.
- This milestone establishes artifact verification only. No temporal engine, Android runtime, detection-quality, latency, RAM, battery or real-evidence benchmark was executed. Android evidence acquisition remains the first engineering milestone; temporal implementation and its acceptance tests are specified in report sections 14 and 13.4.

## Dataset research verification - 2 October 2026

- Completed the dataset handoff's source-statistic spot checks and research/temporal schema reconciliation. All 41 source keys resolve; each of the three comparison tables contains D01-D39. Reproduction commands and evidence limits are in `research/verification/datasets-README.md`.
- Re-counted the unchanged supplied Davidson CSV: 24,783 rows, class counts 1,430/19,190/4,163, zero empty or exact-duplicate texts and zero vote-sum mismatches. Its recorded SHA-256 still matches. External source counts are publisher-reported, not locally re-counted corpora.
- Preserved documented arithmetic/access/license conflicts. Updated the proposed pilot to the current English/Malayalam/Hindi scope, including Romanized and mixed forms. Section E4 specifies conversion obligations for all 20 required temporal-event fields, separate annotation availability and research provenance/rights/gold controls.
- Repository suite: 20 tests run, 19 passed, one optional faster-whisper decoder test skipped. No new corpus, model training, inference, dependency change, adapter implementation or Android benchmark occurred. Existing measured results remain unchanged.

## M3b results: full applicable OCR and revised Merkle - completed subset

- Completed one warmup plus five full runs of RapidOCR on all 36 applicable held-out Latin/romanized screenshots. Hindi and Malayalam scripts remain unsupported by these specific bundled weights and are logged separately; this is not a claim about other multilingual OCR weights.
- Measured aggregate CER: 0.03266; WER: 0.29348. Median full-batch elapsed time: 96.68 seconds; peak process RAM: 545.1 MB. These are laptop measurements, not Android forecasts. Per-language metrics and p95 are in results/ocr.csv and committed trial records.
- Sender/timestamp substring-presence proxies were 1.0 on these fixtures, but exact structured metadata parsing is not established. Source text includes visible UI labels; font shaping and labels still require native-speaker review.
- Remeasured count-bound Merkle v2 on 10,000 synthetic entries with a successful valid verification and failed one-byte-tampered verification, using one warmup and five repeats. New rows retain the variant identifier and source hashes; legacy rows remain historical.
- The publication audit now summarizes fifteen full applicable-data candidates. Full sixty-clip faster-whisper base without/with Silero VAD and the proxy pipeline are still running or pending, not completed at this milestone. Weighted ranking, Android energy/thermal results and the five actual requested stacks remain unavailable.

## Recommendation and progress handoff

- Recorded the proposed lean no-LLM Android MVP, its manual-review fallback, and a later untested Qwen2.5-1.5B Q4 experiment in RECOMMENDATION.md. Each proposal is explicitly distinguished from measured candidates; the report generator preserves this decision section.
- At this publication checkpoint, the full sixty-clip faster-whisper base run without VAD completed its warmup and three of five timed batches. Observed completed-batch elapsed times were 800.053, 338.818 and 405.267 seconds. These partial observations are not a final median/p95 or a completion-time estimate. The full VAD run remains queued.
- Committed measured CSVs, plots, trial records, candidate inventory, data manifests, licence/provenance notes, weaknesses, source/runtime hashes, and fixed benchmark code. Model/audio/image binaries and Python environments remain excluded and are recreated by preparation commands.
- Still open: the full requested neural/embedding/LLM/export/runtime matrix, natural code-mixed speech, native-speaker adjudication, native Android/energy/thermal tests, actual stacks A-E and a defensible final weighted ranking. Missing implementation is not recorded as a model-unavailable skip.

## Android slice 1: JVM foundations - 2 October 2026

- Started the Android project under `android/` following `MEGAPLAN.md` section 39: Gradle skeleton (AGP 9.4.1, Kotlin 2.4.20, Gradle 9.8.0, JDK 21 toolchain), a placeholder app shell, and two pure-Kotlin modules. No evidence feature exists yet.
- `:core:integrity` ports the reference `chain` and count-bound `merkle` from `bench/adapters.py` and adds RFC 8785 canonical JSON. Kotlin output matches Python on twelve shared vectors generated at commit `7a18ef2`; 28 JVM tests passed. Canonical JSON was checked against its own listed cases only, not against an independent implementation.
- `:core:model` is the typed form of `data/sakshi-event-schema.json` with code-point spans and application invariants; 42 JVM tests passed, including Draft 2020-12 validation with date-time checking and rejection of the six malformed variants. Section 6.4 invariants that need stored artefacts, consent or retention state are not implemented yet.
- The debug APK builds; its merged manifest declares no permissions and excludes all backup and device-transfer domains. Android lint reports no errors and only version-availability warnings (API 37, newer Compose BOM); API 36 stays pinned per the megaplan.
- No Android device was attached. The APK was not installed or launched, and no latency, memory, battery or thermal measurement was taken. Backup exclusion is configured, not verified on a device.
- Python suite on this Linux host: 22 tests run, 20 passed, one optional faster-whisper test skipped, and `test_screenshot_text_has_renderable_glyphs` errored because the committed screenshot manifest references Windows font paths. The bench code is unchanged by this slice.

## Android phase 2: temporal engine on the JVM - 2 October 2026

- Added `:core:temporal`, a pure-Kotlin deterministic engine over schema events: revision and review selection at a knowledge cutoff, contact canonicalisation with lower and upper count bounds, interval partial order, coverage gaps, and four demonstration rules (repeated contact, recurrence after a reviewed boundary, wording transition, density change) with template explanations.
- 56 JVM tests passed: synthetic timelines A-F from the temporal report, the sixteen metamorphic properties of its section 13.4 (three adapted because quote attribution, language support and citations are outside this module), canonicalisation, eligibility, partial order, determinism under input reordering, and a wording guard on every generated sentence. A 10,000-event synthetic replay completes; no timing claim is made from it.
- Thresholds are demonstration settings, not validated criteria, and every record that uses one says so. The controlling-request pattern is not implemented. Bin alignment across a daylight-saving change is untested.
- All fixtures are synthetic. This establishes rule behaviour only: no detection quality, no real evidence, no Android latency, memory or energy result. Model and integrity modules still pass (42 and 28 tests) and the debug APK still builds.

## Android phase 3: encrypted storage, app lock and cases - 2 October 2026

- Added `:core:crypto` (chunked AES-256-GCM blob envelope with authenticated random access and key wrapping), `:core:database` (Room schema of 26 tables with insert-only triggers and a SQLCipher open path), `:core:vault` (Keystore key wrapper, blob store, audit hash chain, case and evidence repositories, job queue) and the first app screens (onboarding, biometric or device-credential lock, case management).
- JVM test totals after this phase: 261 passed, 0 failed (model 42, integrity 28, temporal 56, crypto 33, database 33, vault 47, app 22). Crypto tests cover modification, reordering, truncation and extension of blobs. Vault tests show an import either commits file, rows and audit entry together or leaves nothing, and that stored files contain no plaintext marker.
- The debug APK builds, lint reports no errors, and a build check restricts the merged manifest to `USE_BIOMETRIC`, the library's legacy `USE_FINGERPRINT` and an app-private AndroidX receiver permission.
- Not verified, because no device or emulator was available: the SQLCipher open path (database tests ran on plain in-memory SQLite under Robolectric), the Android Keystore wrapper (vault tests used a software key wrapper), the biometric prompt, `FLAG_SECURE`, lock on background, backup exclusion, 16 KB page alignment of the SQLCipher native library, and screen rendering. The megaplan device checks V-05, V-07 and V-09 remain open.
- No latency, memory, storage throughput, battery or thermal measurement was taken.

## First device results: storage layer on SM-S928B - 2 October 2026

- Device: Samsung SM-S928B, Android 16 (API 36), SoC SM8650, arm64-v8a, about 11.3 GB RAM, 4 KB page size, security patch 2026-07-05. State during runs: charging over USB, battery 86 to 89 percent, thermal status 0. This is a 12 GB flagship, not the 6 GB reference class the megaplan targets, and its 4 KB pages say nothing about 16 KB page devices.
- 18 instrumented tests in `:core:vault` passed in two separate runs (`./gradlew :core:vault:connectedDebugAndroidTest`), debug build, synthetic data only.
- Verified on this device (megaplan V-07 and parts of V-05, V-06): the SQLCipher database opens and persists across reopen (`cipher_version` 4.19.0 community); a wrong passphrase fails with `SQLiteNotADatabaseException`; one flipped byte in the database file fails with `SQLiteDatabaseCorruptException` instead of returning data; insert-only triggers and foreign keys are active under SQLCipher; the Keystore master key is StrongBox-backed, 256-bit and not exportable; the database, key file and blobs sit under the no-backup directory with owner-only permissions and contain neither the synthetic case title nor the plaintext marker; a flipped blob byte is reported as an authentication failure and the evidence row survives.
- Measured, two runs of three timed imports each after one warm-up (megaplan asks for five repeats, so treat as indicative): importing a 32 MiB stream end to end (encrypt, hash, fsync, database transaction, audit row) ran at 107.4, 128.3, 143.1 MiB/s in run one and 122.6, 112.5, 125.2 MiB/s in run two. Full authenticated verify of 32 MiB took 0.711 s in run one. Random 4 KiB reads averaged 337 and 422 microseconds. First-ever vault open including Keystore key creation took 641 and 701 ms. Appending 1,000 audit rows took 4.11 and 4.95 ms per row; each append is its own transaction.
- The app installs and cold-launches without a crash (584 ms, one run, debug build) and its window carries `FLAG_SECURE`.
- Still unverified: the biometric prompt and the unlock path with a user present, the not-authenticated and key-invalidated paths, lock on background, screen rendering, backup and device-transfer exclusion (V-09), behaviour on a 16 KB page device, lower-RAM devices, and any battery or thermal effect. No release-build measurement was taken.

## Android phases 4 to 9 groundwork: import, text processing, event storage, export bundle - 2 October 2026

- Added `:acquisition:importer` (share intents, picker results, pasted text, manual notes, per-type streaming limits), `:processing:text` (script and language hints, rules cue engine with code-point spans, label mapping, WhatsApp text-export parser), `:export:bundle` (bundle writer, offline verifier and command-line tool), an event store and actor registry in `:core:vault`, and import screens in the app (share target, case detail, preview with explicit save, paste, manual note).
- JVM tests: 555 passed, 0 failed (model 42, integrity 28, temporal 56, crypto 33, database 34, vault 93, importer 80, text 52, bundle 47, app 90). Device tests on the SM-S928B: 21 in `:core:vault` and 4 in `:acquisition:importer` passed, synthetic data, debug build.
- Text processing parity: the Kotlin cue engine and language hints equal the Python reference (`bench/adapters.py` `RULES` and `Language.identify`) on all 600 synthetic fixtures. Rules differ from the fixture gold labels on 19 of 600 rows (en 9, mixed 7, hi 1, hinglish 1, manglish 1, ml 0). This is a fixture regression figure on 14 templates, not accuracy. Cue matching has no context: negated and quoted phrases match, which tests document. No real export file from any locale was parsed.
- Event storage round trip is exact for 200 generated events and timelines A-F, and the temporal engine returns the same result on reloaded events. Fixed during this work: a cancelled import could leave an encrypted file without a database row; region locators and missing hashes could not be stored without loss (schema version 1 changed in place, no migration, unreleased).
- Measured on the SM-S928B (Android 16, SM8650, charging, battery 100 percent, thermal status 0, debug build, one run each, so indicative only): saving events one transaction each took 20.3 and 22.1 ms per event over 1,000 events; batch saving took 423 ms for 1,000 and 3,961 ms for 10,000 events; loading the latest revisions took 368 to 458 ms for 1,000 and 3,720 ms for 10,000.
- Export bundle: 47 JVM tests including 18 tamper cases. Only a software P-256 signer was exercised; signing with the device Keystore is not implemented or tested. A bundle re-signed with another key verifies with a different key id, so the key id must be compared out of band.
- On the device, a text share sent with `am start` to the share target reached the main activity without a crash. Not verified: a share of a file from another app (whether the read grant survives forwarding), the pickers, the unlock flow with a user present, lock on background and the two-minute picker grace window, rotation and process death during import, and every screen's rendering. The pre-Android 13 intent path ran only under Robolectric.

## Android foundation completion and connected verification - 2 October 2026

- Completed the full Android evidence, review, temporal pattern and export flow across all modules:
  - `:core:vault`: added single-event latest and revision loaders, sender claims queries and observation, typed decision history per event, atomic person creation with sender assignment, boundary clearing, actor renaming, and export audit action logging.
  - `:core:temporal`: added zoned pattern explanation rendering and typed facts view for localization.
  - `:processing:analysis`: added single-pass code-point body and quote slicing (`EventText`), a `TextAnalyser` interface, and on-demand case pattern analysis with supporting event snippets and zoned explanations.
  - `:export:report`: added zoned pattern sentence formatting, Android Keystore ECDSA P-256 signing, PDF rendering, export audit records, and export error discriminators.
  - `:app`: implemented the entire UI screen hierarchy: Onboarding, Biometric/Device Lock, Case List and Management, Evidence Acquisition (ShareTargetActivity, system pickers, paste text, manual notes), Analysis Prompts, Timeline (with coverage gap markers, filters and message bodies), Event Review (accept, reject with reason, uncertain, custom user tags, direction, wantedness, boundary markers, and typed decision history), Who Is Who (actor list, sender claims, atomic person creation), Patterns (temporal cards with supporting events), and Report Export (selection, exact on-screen preview matching PDF, Keystore-signed PDF/zip export, key ID display, and secure private sharing via FileProvider with cache cleanup on leave/lock/unlock).
- JVM test suite verification: all 837 tests passed, 0 failures, 0 errors, 0 skipped.
  - `acquisition/importer`: 84
  - `app`: 227
  - `core/crypto`: 33
  - `core/database`: 34
  - `core/integrity`: 28
  - `core/model`: 42
  - `core/temporal`: 62
  - `core/vault`: 142
  - `export/bundle`: 47
  - `export/report`: 38
  - `processing/analysis`: 48
  - `processing/text`: 52
- Connected Android test suite verification on Samsung Galaxy S24 Ultra (SM-S928B, Android 16, API 36):
  - 33 tests passed, 0 failures, 0 errors, 0 skipped (`core:vault`: 22, `acquisition:importer`: 4, `processing:analysis`: 2, `export:report`: 5).
  - Verified on hardware: encrypted event storage, revision assembly, audit chaining, content-provider stream importing, text analysis and derivative generation, on-device Keystore report signing, and PDF report creation.
- Android Lint and manifest verification:
  - `:app:lintDebug`: passed with 0 errors.
  - `:app:verifyManifestPermissions`: passed. Strictly bound permissions: `USE_BIOMETRIC`, `USE_FINGERPRINT`, and dynamic receiver permission. No `INTERNET` permission in release or debug manifest.
- Resolved temporary workarounds:
  - Eliminated `BodySlices.kt` in favour of library-level `EventText(vault).bodiesOf(...)`.
  - Replaced ad-hoc JSON parsing in `HistoryRows` with typed `DecisionTargetKind` and `DecisionChange`.
  - Replaced ViewModel coroutine deadlocks in `ReportViewModelTest`.


## Android phase 10: screenshot OCR, Latin script - 3 October 2026

- Added `:processing:ocr`: bundled ML Kit Text Recognition v2 Latin model (`com.google.mlkit:text-recognition` 16.0.1; the model ships in the APK, nothing is downloaded), one region per recognised line with the engine's own line score and language tag, EXIF orientation passed to the engine as rotation (mirroring recorded, not undone), images above 16 megapixels decoded at a lower power-of-two resolution, and typed outcomes (`Success`, `NoText`, `Failed` with `UNDECODABLE`, `OUT_OF_MEMORY`, `ENGINE_FAILED`). The engine is created on first use and released when the session ends.
- `:processing:analysis`: a detected JPEG, PNG or WebP becomes an OCR derivative (text exactly as recognised, lines joined in engine order) stored with its regions in one audited transaction, and one `selected_image` event whose references all carry `ocr_derivative`. Each rule cue is anchored both to its code-point span and to the image region of the line it was read from. Sender, direction and time stay unknown. A line scored below 0.5 or with no score marks the event `extraction_uncertain` and the evidence as analysed in part; 0.5 is a demonstration setting, not a calibrated threshold. Every image result carries a "Latin script only" warning. No readable Latin text gives `NO_TEXT_RECOGNISED` and writes nothing.
- Network: ML Kit's telemetry dependency (`com.google.android.datatransport`) declares `INTERNET` and `ACCESS_NETWORK_STATE`; `:app:verifyManifestPermissions` caught it. Both are removed in the OCR module manifest, so neither the app nor the test APKs hold them. The bundled native library `libmlkit_google_ocr_pipeline.so` (arm64-v8a) has 16 KB aligned LOAD segments (0x4000), read with `readelf`.
- JVM tests: 867 passed, 0 failed (new: 14 in `:processing:ocr`, 10 image analysis tests, 2 region storage tests, app row and import tests). Events with region anchors validate against `data/sakshi-event-schema.json`.
- Device tests, synthetic images drawn in the test, debug build, one run each, so observations rather than V-10 numbers:

| Device | Test | Result |
|---|---|---|
| SM-S928B, Android 16 | OCR suite (5 tests) | passed; Latin screenshot cold 141 ms, warm 89 ms, 3 lines, lowest line score 0.865, language `en`; Malayalam and Devanagari renders returned `NoText` (130 ms, 85 ms) |
| CPH2695 (MT6835), Android 16 | OCR suite (5 tests) | passed; Latin cold 111 ms, warm 68 ms, lowest line score 0.851; Malayalam and Devanagari `NoText` |
| CPH2695 | Image analysis (2 tests) | passed; import to stored event with region anchor 474 ms end to end; Malayalam screenshot refused with `NO_TEXT_RECOGNISED`, nothing written |
| CPH2695 | Existing text analysis (2 tests) | passed; 1,000 messages 841 ms, 10,000 messages 24,486 ms (thermal status 3 during that run) |

- V-08 status: partly verified. ML Kit returned text in a process whose package holds neither `INTERNET` nor `ACCESS_NETWORK_STATE` (asserted in the test), so the process could not open sockets. Not done: airplane-mode cold launch of the app and packet capture. No crash or `SecurityException` from the telemetry library was seen in logcat on either phone.
- Not verified: OCR in the installed app with a person using it, release-build latency and memory (V-10), real screenshots from messaging apps, dark-mode and low-contrast screenshots, CER on the bench screenshot fixtures (the committed manifest still names Windows font paths, so the fixtures were not regenerated on Linux), Devanagari OCR (ML Kit Devanagari model not added) and Malayalam OCR (Tesseract not added).

## Android phases 8, 9, 12 and 13: search, stored patterns, report versions, redaction, notification lane, security tests - 3 October 2026

- Added since phase 10: case-scoped search with filters and a separate scope for messages with accepted tags; notes searchable as the person's own statements; stored pattern descriptions with content-derived ids, case-wide staleness marked in the same transaction as every correcting write, and per-description review (agree, does not match with a reason, not sure, undo); numbered report versions bound to the previewed content; text redaction in the export library; verifier anchor and graph checks; whole-vault deletion that destroys the key first; an off-by-default notification observation module kept in memory only; policy guardrail tests; security tests and `docs/architecture/threat-model.md`.
- JVM verification on a clean checkout of `454130e`: 1,304 tests in 15 modules, 0 failures, 0 skipped; `:app:assembleDebug`, `:app:lintDebug` (0 errors, 14 warnings, all dependency or target version notices) and `:app:verifyManifestPermissions` passed. The notification module adds 122 tests (commit `3875e66`). These are fixture and unit results, not accuracy figures.
- Device tests, debug build, CPH2695 (MT6835), Android 16, phone unlocked and kept awake over USB, one run each:

| Suite | Tests | Result |
|---|---|---|
| `:core:vault` | 26 (includes 4 new database security tests) | passed: the SQLCipher file does not open as plain SQLite, with an empty or wrong key, or after one flipped byte at any of 10 positions |
| `:processing:analysis` | 7 (includes 3 new leak tests) | passed: no marker text in English, Malayalam or Devanagari, as UTF-8 or UTF-16, in any file of the test sandbox or in the process's own logcat after a full workflow |
| `:acquisition:importer` | 4 | passed |
| `:processing:ocr` | 5 | passed |
| `:export:report` | 5 | passed |

- An earlier run the same morning had one importer test hang for 8.5 minutes while the phone was asleep on the lock screen; with the phone awake the same suite passed. Device results are only recorded from awake, unlocked runs.
- Notification device test: not run. The vendor build refused `pm grant ... POST_NOTIFICATIONS` from adb, so the test skipped itself; listener access was granted for the attempt and removed afterwards.
- Not verified: any screen used by a person; release builds; battery, thermal and memory measurements; real messaging-app notification payloads; backup extraction (V-09); real biometric invalidation.

## Android phase 11: speech to text library, whisper.cpp base q5_1 - 3 October 2026

- Added `:processing:stt`: whisper.cpp `v1.9.4` (tag tarball SHA-256 recorded on first download, trust on first use; fetched only by `android/tools/prepare-whisper.sh`), statically linked into one arm64-v8a `libsakshi_stt.so` (3.0 MB), CPU only, NEON, no OpenMP; all three LOAD segments 16 KB aligned (`readelf -lW`, Align 0x4000). Model `ggml-base-q5_1.bin` from Hugging Face `ggerganov/whisper.cpp` revision `5359861c739e955e79d9a303bcbc70fb988958b1`, SHA-256 `422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898` (matches the upstream LFS record), not bundled; installed only through an explicit provisioning call that verifies the hash.
- Audio is decoded with `MediaExtractor`/`MediaCodec` through a `MediaDataSource` over an authenticated random-access reader, so decrypted bytes never touch a file (V-04 random access exists in `BlobReader`; the decode path was exercised on the device with in-memory sources, not yet with encrypted evidence).
- JVM: 47 tests. Device: 12 tests passed on CPH2695 (MT6835), Android 16, debug build, 4 threads, thermal status 1, battery 100 % at 36.2 C.
- One 60 s synthetic English clip (on-device text to speech, repeated), one warm-up then one measured run. Debug-build observations, not V-12 release measurements:

| Measure | Value |
|---|---|
| Model load including SHA-256 check | 405 ms |
| Transcription | 42,305 ms (real-time factor 0.705) |
| PSS before load / after load / after transcription / after unload | 45.5 MB / 141.5 MB / 377.0 MB / 41.0 MB |
| Word error rate, one short English sentence | 0.000 (language detected `en`) |

- Silence (10 s) gave `NoSpeech` from the signal-level gate; a 440 Hz tone (5 s) gave `NoSpeech` from the model, with no invented words; a 70 s clip was refused as too long before full decoding; forcing Hindi on English speech returned English text, not a translation.
- Not verified: Malayalam, Hindi, code-mixed or noisy real speech; repeats; release build; thermal behaviour over a sustained run; analysis integration (one event per clip with text and audio-time anchors) and the app screens, which are not built yet.
