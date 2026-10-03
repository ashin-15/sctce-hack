# Sakshi

Sakshi is a privacy-first Android app for people who face repeated harassment. The user chooses what to import, the app preserves it unchanged and encrypted, helps find possible patterns over time on the device, and lets the user review every suggestion before producing a report they control.

Permissible product promise: "Sakshi helps you preserve and review evidence you explicitly import and, if you opt in, message-related information exposed in supported Android notifications. Availability varies by app, settings and Android version."

## What Sakshi does not do

These limits are binding (see [AGENTS.md](AGENTS.md)).

- It does not scrape other apps, read their databases or private storage, or recover deleted messages.
- It does not bypass View Once, disappearing messages, encryption, sandboxing or access controls.
- It does not use cloud AI. Analysis runs on the device. Model provisioning is a separate, explicit preparation step.
- It does not reach legal conclusions, label anyone guilty, or give danger scores.
- It does not claim that its output is admissible in court.
- It does not upload or share anything unless the user explicitly exports it.

## Current status

Read this before anything else. Status as of 3 October 2026: the Android app is a working debug build, verified by unit tests and by instrumented tests on synthetic data. No person has used it end to end, there is no release build, and the on-device language model is an uncalibrated experiment.

| Area | State |
|---|---|
| Research | Twelve reports in [research/](research/) (Android acquisition, multimodal pipeline, temporal patterns, local AI, datasets, View Once feasibility and others). |
| Plan | [MEGAPLAN.md](MEGAPLAN.md) is the implementation plan, with the decision log up to D-25. It is a planning document, not a description of finished work. |
| Feature specifications | [docs/spec-driven/](docs/spec-driven/) holds a PRD, technical design, acceptance list, agent plan and loop log for each newer feature: `message-observation`, `media-projection` and `threat-language`. |
| Python benchmark harness | Exists and runs on a laptop. It measures baseline candidates on synthetic fixtures. See [benchmark.md](benchmark.md) and [results/SUMMARY.md](results/SUMMARY.md). |
| Event schema | [data/sakshi-event-schema.json](data/sakshi-event-schema.json) (JSON Schema 2020-12, event v1). |
| Android project | [android/](android/) has 19 Gradle modules: pure-Kotlin core, encrypted storage, four acquisition modules, text, OCR, speech and language-model processing, analysis, export, policy checks and the Jetpack Compose app. |
| Acquisition | Share sheet, pickers, paste and manual notes work in the app. Three optional, off-by-default lanes are wired into the app, each behind its own disclosure and an explicit Android grant: notification collection, visible text capture (accessibility, at most five minutes) and user-started screenshots (MediaProjection). |
| Processing | WhatsApp text-export parsing, rules cue engine, Latin-script OCR (bundled ML Kit model) and speech to text (whisper.cpp base q5_1, arm64, model installed by an explicit preparation step) feed the analysis pipeline. Devanagari and Malayalam OCR are deferred. |
| On-device language model | Qwen2.5 1.5B Instruct Q4_K_M through llama.cpp b6500 gives threat-language suggestions on plain text, each with an exact source quote for review. It runs on the test phone: the isolated device test passes on nine synthetic messages (English threats flagged, ordinary messages left alone, quoted speech sent to review, Romanized Hindi refused). **Not calibrated and not release accepted:** there is one known false positive on a figure of speech, Malayalam and Hindi are refused so threats in them are not flagged, and a message takes about 33 s on that phone. Saved text in active cases is analysed automatically, one item at a time, only while the vault is unlocked. See [handoff.md](handoff.md). |
| Review and patterns | Event review, who-is-who, timeline, stored pattern descriptions with staleness and per-pattern review, and case-scoped search are in the app. |
| Report and export | Keystore-signed PDF and zip bundle, numbered report versions, offline verifier with a command-line tool. Text redaction exists in the export library; its app screens are not built. |
| Security | Encrypted vault (SQLCipher, AES-256-GCM blobs, Android Keystore), audit hash chain, whole-vault deletion that destroys the key first, a manifest permission allowlist, policy guardrail tests and [docs/architecture/threat-model.md](docs/architecture/threat-model.md). |
| Android measurements | Last full JVM run on a clean checkout (`454130e`): 1,304 tests in 15 modules, 0 failures. Modules added after that were verified by selected suites recorded in [benchmark.md](benchmark.md). Instrumented suites pass on a Samsung SM-S928B and a CPH2695, both Android 16, with synthetic data and debug builds. |
| Not verified | Any screen used by a person, release builds, battery, thermal and memory figures, real messaging-app payloads, a MediaProjection capture on a device (the consent prompt was never approved in testing), backup exclusion, Malayalam and Hindi quality for cues, speech and the language model. |

The two handoff files record session state: [HANDOFF.md](HANDOFF.md) (megaplan phases, morning of 3 October, older than the last two feature commits) and [handoff.md](handoff.md) (the language-model work, its limits and next steps).

Every benchmark number in this repository is recorded in [benchmark.md](benchmark.md). Synthetic fixture metrics and laptop proxies are explicitly distinguished from on-device measurements. The stack in [RECOMMENDATION.md](RECOMMENDATION.md) is a provisional engineering hypothesis, not a benchmark winner.

## End-to-end workflow

This is the product workflow from the megaplan (sections 4, 10, 17, 18, 20, 21).

```text
 ACQUIRE            share sheet | file picker | photo picker | paste | manual note
    |               (opt-in only: notification collection | visible text capture | screenshots)
    v
 VALIDATE           scheme, MIME sniff, size / count / archive limits
    v
 PREVIEW + SAVE     user sees what arrived, picks the case, explicitly saves
    v
 ORIGINAL           exact bytes -> encrypted immutable blob + SHA-256 + provenance + audit record
    v
 DERIVATIVES        parsed text | OCR text with regions | transcript   (versioned, parent-linked)
    v
 EVENTS             one contact unit per source message, schema-valid, code-point anchors
    v
 SUGGESTED FINDINGS rules baseline first, then the local model on plain text; each with source span
    v
 HUMAN REVIEW       accept | reject | edit | add context | mark unknown
    v
 TEMPORAL ENGINE    deterministic: distinct-contact counts, recurrence after a boundary,
                    wording transition, density change, with coverage gaps
    v
 PATTERN REVIEW     user confirms or corrects the pattern cards
    v
 REPORT             PDF built from structured data and user-confirmed items only
    v
 EXPORT BUNDLE      manifest + hashes + signature + selected evidence, shared by the user
    v
 VERIFY OFFLINE     verifier recomputes hashes, Merkle root and signature without Sakshi
```

### Walkthrough and status

| # | Stage | What happens | Status |
|---|---|---|---|
| 1 | Acquisition | The user shares, picks, pastes or types evidence. The optional lanes (notification collection, visible text capture, screenshots) are opt-in and never required. | Import screens built (share target, pickers, paste, manual note); tested on the JVM and through a synthetic provider on a phone, not yet with a real share from another app. The three optional lanes are in the app; notification and accessibility passed isolated synthetic device tests, screenshot capture has not run on a device. No third-party app compatibility is claimed. |
| 2 | Validation | Incoming URIs, names and MIME types are treated as untrusted claims. Limits are enforced while streaming. | Implemented and tested (`:acquisition:importer`). |
| 3 | Preview and save | Nothing is stored until the user chooses a case and confirms. Notification candidates stay in a bounded memory inbox and screenshot drafts stay encrypted until the user saves or discards them. | Screens built; not yet exercised by a person on a device. |
| 4 | Original preservation | Exact received bytes are encrypted, hashed with SHA-256 and recorded with provenance. Originals are never overwritten. | Implemented (`:core:crypto`, `:core:database`, `:core:vault`): encrypted blobs, hash, provenance rows and audit chain. Keystore and SQLCipher paths pass instrumented tests, including wrong-key and flipped-byte checks. |
| 5 | Derivatives | Parsed text, OCR and transcripts are separate versioned records. They never replace the original. | WhatsApp text-export parsing and Latin OCR with line regions are in the analysis pipeline and device-tested. Speech to text is device-tested as a library and connected to the analysis pipeline; the path from an encrypted audio item to a transcript in the app has not been verified on a device. |
| 6 | Events | Each source message becomes an event under the event schema. | Implemented: one event per parsed message, one per image and one per audio clip, stored encrypted with code-point anchors (and image regions or time ranges where they apply). |
| 7 | Suggested findings | Rules-assisted highlighting first. The local model adds reviewable suggestions on plain text, never on OCR or transcripts. | Rules cue engine works in the app; its cue lists are the unreviewed demo set and ignore negation and quotes. Qwen threat-language analysis passes its isolated device test on nine synthetic messages and runs automatically on saved text while the vault is unlocked. One false positive on a figure of speech is known; Malayalam and Hindi are refused. No calibrated or multilingual quality claim is made. |
| 8 | Human review | Three separate targets: evidence association, signal, and pattern or explanation. Rejection never labels the text harmless. | Implemented (event review, who-is-who). Not yet used by a person. |
| 9 | Temporal engine | Pure Kotlin, deterministic and idempotent. It counts only distinct incoming contacts and reports count bounds when duplicates are unresolved. | Implemented (`:core:temporal`) and connected to the app through the patterns screen; tested on synthetic timelines only. |
| 10 | Pattern review | The user reviews each pattern card with its supporting events, gaps and limitations. | Implemented: stored descriptions, staleness marking, and agree, does not match, not sure and undo. |
| 11 | Report | Dates and counts are rendered from structured data. Observed evidence, user statements and inferred findings are kept in separate blocks. | Implemented with selection, preview and numbered versions. Open: redaction screens, bundled Noto fonts and Indic PDF shaping. |
| 12 | Export bundle | Canonical manifest, file hashes, signature and the items the user selected. Export is blocked while any included pattern is stale. | Implemented with on-device Keystore signing (ECDSA P-256) and an export result screen. |
| 13 | Offline verification | A pure-JVM verifier checks hashes, root and signature without Sakshi. | Implemented and tested (`:export:bundle`), including a command-line tool. |

### The four MVP pattern cards

These are the demo rules in the megaplan. Their thresholds are demo defaults, configurable and not validated.

| Pattern | Idea |
|---|---|
| Repeated contact | Distinct incoming contacts from one actor scope counted in fixed windows. |
| Recurrence after boundary | Contacts that follow a boundary message or note the user has marked. |
| Wording transition | A move between accepted categories (for example insults to a statement of harm) within one actor scope. Uses accepted tags only. |
| Density change | A comparable-window rise in contact rate, shown as a review cue, not a determination. |

The engine never outputs a single escalation score, a probability for a pattern, a prediction, a statement of intent, or a cross-app identity claim.

## Observed, User-reported, Inferred, Pattern, Unknown

Every result the app shows carries exactly one of these labels. They are different kinds of statement and are never mixed.

| Label | Meaning |
|---|---|
| Observed | Text or media directly present in a preserved artefact. Shown as a quote with a source link. |
| User-reported | The user's own statement, shown as theirs. |
| Inferred | A rule or model suggestion. Shown with its basis, version and the word "suggestion". |
| Pattern | Supported by multiple events. Shown with count bounds and the event list. |
| Unknown | Explicitly listed, never hidden. |

The UI design takes the label as a required parameter with no default, so an inference cannot be drawn as an observation by mistake. Where the evidence is thin, the app should abstain rather than invent certainty.

"No evidence observed in the selected records" is never presented as "no harassment occurred". The records may be incomplete, the user may not have imported everything, and some apps expose nothing. The temporal engine has no output that means the second statement. Results are limited to candidate, supported description, insufficient context and not observed, and "not observed" is always shown with its scope. Gaps in coverage are shaded on the timeline and comparable-rate statements are disabled across them.

## Repository map

| Path | What it is |
|---|---|
| [AGENTS.md](AGENTS.md) | Binding operating contract: core rules, do-not-build list, engineering priority, verification commands. |
| [MEGAPLAN.md](MEGAPLAN.md) | Implementation plan: architecture, data model, phases, milestones, risks, open questions. |
| [RECOMMENDATION.md](RECOMMENDATION.md) | Provisional MVP stack and its evidence boundary. Not benchmarked on Android. |
| [benchmark.md](benchmark.md) | Milestone ledger for the Python benchmark and every Android phase, including gaps. |
| [HANDOFF.md](HANDOFF.md), [handoff.md](handoff.md) | Session handoffs: megaplan phase state, and the language-model work with its limits and next steps. |
| [docs/architecture/threat-model.md](docs/architecture/threat-model.md) | Security model and test coverage table. |
| [docs/spec-driven/](docs/spec-driven/) | Per-feature PRD, technical design, acceptance list, agent plan and loop log. |
| [research/](research/) | Twelve research reports plus `probes/` and `verification/` receipts, including the 3 October device receipts for observation and MediaProjection. |
| [bench/](bench/) | Python offline benchmark harness and its tests. See [bench/README.md](bench/README.md). |
| [data/](data/) | Synthetic fixtures, the event JSON Schema, provenance files and a third-party English tweet CSV used only as an auxiliary baseline. |
| [results/](results/) | Benchmark CSVs, plots, per-run metadata, [SUMMARY.md](results/SUMMARY.md) and a sample report PDF. Laptop proxies only. |
| [android/](android/) | Kotlin Gradle project with the 19 modules listed below, shared `testfixtures/` and preparation scripts in `tools/`. See [android/README.md](android/README.md). |
| [stitch_design_specification_project/](stitch_design_specification_project/) | Design specification the app's calm colour scheme and components follow. |
| [.lavish/](.lavish/) | HTML review views of the research reports and plans. Derived, not authoritative. |
| [Harassment_Pattern_Guard.pptx.pdf](Harassment_Pattern_Guard.pptx.pdf) | The original pitch. A proposal, not a specification; its wording is narrowed by `AGENTS.md`. |

Android modules today:

| Module | Kind | Contents |
|---|---|---|
| `:core:model` | Kotlin/JVM | Event contract for the schema, code-point spans, schema adapter, invariant checks. |
| `:core:integrity` | Kotlin/JVM | SHA-256, hash chain, count-bound Merkle v2, RFC 8785 canonical JSON. |
| `:core:temporal` | Kotlin/JVM | Deterministic temporal pattern engine with the four demo rules. |
| `:core:crypto` | Kotlin/JVM | Chunked AES-256-GCM blob envelope, key wrapping interface. |
| `:core:database` | Android library | Room schema (version 2, adds threat-analysis runs), insert-only triggers, DAOs, SQLCipher open path. |
| `:core:vault` | Android library | Keystore wrapper, blob store, audit chain, case and evidence repositories, event store, review coordinator, analysis-run store. |
| `:acquisition:importer` | Android library | Share intent, picker and paste readers, streaming limits, manual notes, notification excerpt entry. |
| `:acquisition:notifications` | Android library | Opt-in notification listener, off by default, candidates kept in memory until the user saves them. |
| `:acquisition:accessibility` | Android library | Opt-in visible text capture for chosen apps, bounded to five minutes, cleared on lock. |
| `:acquisition:projection` | Android library | User-started screenshots and bounded bursts behind Android's screen-capture consent, encrypted drafts. Video and audio are deferred. |
| `:processing:text` | Kotlin/JVM | Script and language hints, rules cue engine, WhatsApp text-export parser. |
| `:processing:ocr` | Android library | Bundled ML Kit Latin text recognition with line regions; network permissions removed. |
| `:processing:stt` | Android library (arm64 native) | whisper.cpp base q5_1 speech to text with a hash-pinned model. |
| `:processing:llm` | Android library (arm64 native) | llama.cpp JNI runtime, model manager and the Qwen threat-language classifier (six-kind task, grammar-constrained output). Uncalibrated. |
| `:processing:analysis` | Android library | Analysis pipeline for text, images and audio; derivative storage; on-demand pattern evaluation. |
| `:export:bundle` | Kotlin/JVM | Bundle writer, offline verifier, command-line tool. |
| `:export:report` | Android library | Report model, PDF renderer, Keystore signer, export service, text redaction. |
| `:tools:policy` | Kotlin/JVM | Tests that scan the source tree for forbidden APIs, dependencies, logging, suppressions, typography and wording. |
| `:app` | Android | Onboarding, lock, cases, import, analysis (with an automatic queue for saved text), timeline, event review, who-is-who, patterns, search, report and export, AI model, notification collection, visible capture, delete everything. |

## Development workflow

### How work proceeds

The megaplan orders work by dependency, with the pure-Kotlin core first because it can be tested without a phone. Milestones from megaplan section 32:

| Milestone | Contains | Demonstrates | State |
|---|---|---|---|
| M-A Core proven | Phases 1-2 | Event contract, integrity primitives and temporal rules, with no phone | Done on the JVM. |
| M-B Vault | Phases 3-4 | Evidence goes in encrypted, hashed, with provenance | Done; JVM and instrumented tests pass. Not yet exercised by a person on a device. |
| M-C Text MVP | Phases 5-7 | Import a chat export, review findings, see pattern cards, correct one and watch counts change | Code complete in the app; verified by tests, not by a person. |
| M-D Verifiable report | Phase 9 (+8) | Export, verify offline, tamper and fail | Done except the redaction screens and bundled Noto fonts. Search is done. |
| M-E Screenshots | Phase 10 | OCR lane with regions | Done for Latin script; Devanagari and Malayalam OCR deferred (D-23). |
| M-F Hardened MVP | Phases 13-14 | Device receipts recorded in `benchmark.md` | Partly: security tests and threat model done. Open: storage-exhaustion and image-decoder seams, backup extraction (V-09), real biometric invalidation (V-05), release-build numbers (V-10, V-12), airplane-mode launch (V-17). |
| M-G / M-H | Phases 11, 12 | Audio, then optional notifications (post-MVP) | Speech library device-tested and connected to analysis. Notification collection is in the app with one synthetic device test passed. |
| Release | Phase 15 | Release qualification | Not started; needs decisions on licence (Q1), native-speaker review (Q6) and counsel (Q10). |

Beyond the original phases, three owner-approved additions exist: the on-device language model (D-24), MediaProjection screenshots (D-25) and accessibility visible text capture. Each has a specification under `docs/spec-driven/` with its open acceptance criteria.

`benchmark.md` gets an entry at each milestone.

### How changes are made

- The main session plans, writes the brief, reviews and verifies. Code is written by subagents, one module each, and the main session reruns builds and tests before anything is claimed.
- Newer features follow the specification set in `docs/spec-driven/<feature>/`, and the loop log records each step with its evidence.
- Each verified addition is committed and pushed to `android-foundation`, then `master` is fast-forwarded. Handoff files are refreshed when a piece lands.
- The owner often works in the same tree. Check `git status` first and never commit someone else's uncommitted files.

### Android build and test

Run from `android/`. Gradle needs JDK 21 (the default JDK on the development host is not used), Android SDK platform 36, and NDK `29.0.14206865` with SDK CMake `3.31.6` for the two native modules. The native sources are fetched only by explicit preparation scripts; the Gradle build never downloads and stops with a pointer to the script when a source tree is missing.

```sh
cd android
./tools/prepare-whisper.sh   # pinned whisper.cpp v1.9.4 into third_party/ (git-ignored)
./tools/prepare-llama.sh     # pinned llama.cpp b6500 into third_party/ (git-ignored)
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
./gradlew test :app:assembleDebug
./gradlew :app:lintDebug :app:verifyManifestPermissions
./gradlew :tools:policy:test
```

Model files are never in the APK or in git. The speech model and the language model are installed on the device by explicit preparation actions. Instrumented test commands, the device preparation steps and the per-module limits are in [android/README.md](android/README.md). Run device tests with the phone unlocked and awake, and use only the isolated `QwenThreatDeviceTest` for the language model, because some older test helpers clean the normal vault folder.

The cross-language integrity vectors are generated from the Python reference functions so that Kotlin and Python agree byte for byte. To regenerate, from the repository root:

```sh
uv run --no-project --with numpy --with scikit-learn python android/tools/gen_integrity_vectors.py
```

### Python benchmark harness

Run from the repository root. Baseline environment is Python 3.12 or later with the pinned requirements; setup details, including the Windows environment notes, are in [bench/README.md](bench/README.md).

```sh
python -m unittest discover -s bench/tests -v
python -m bench data
```

`bench/README.md` lists one command per component (classifier, language, OCR, STT, extraction, linking, storage, integrity, PDF, ingest, report). Inference is offline. Downloads of models or public audio happen only in the explicit preparation commands (`prepare-audio`, `prepare-whisper`). Commands that name an unimplemented candidate exit nonzero and record a pending entry instead of fabricating a result.

### Rules for contributors

- Never fabricate measurements. Label every number as Target, Measured (with device) or Estimated.
- A laptop number is not an Android number. Do not infer phone latency, memory or battery from it.
- Synthetic data is labelled synthetic and is never presented as real-world evidence. Synthetic labels and summaries need native-speaker review before real-world use.
- Do not commit audio, model binaries, keys or consented private data.
- Update `benchmark.md` at milestones.
- Keep originals separate from OCR, transcripts, model outputs and reports. Do not overwrite source evidence during preprocessing.
- Do not add anything on the do-not-build list in [AGENTS.md](AGENTS.md).
- Do not claim that a platform behaviour works until it has been prototyped on a real Android device.

## Privacy and security posture

Built and tested unless a line says otherwise. The full model is in [docs/architecture/threat-model.md](docs/architecture/threat-model.md).

- Local only. Evidence and inference stay on the device. There is no backend and no cloud inference fallback.
- The app has no `INTERNET` permission. Its allowlist is `USE_BIOMETRIC`, legacy `USE_FINGERPRINT`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PROJECTION`; `:app:verifyManifestPermissions` fails the build if the merged manifest gains any other permission or exported component.
- The notification listener and the accessibility service are the only system-bound services. Both start disabled, need an explicit grant in Android settings, and are protected by their system binder permissions.
- The vault uses the Android Keystore, AES-256-GCM for blobs, and Room over SQLCipher for structured data. These paths pass instrumented tests on two phones.
- Backups and device transfer are to be excluded for evidence and derivatives. Not yet verified on a device (V-09).
- A lost or invalidated device key makes the vault unrecoverable; there is no recovery bundle.
- The audit chain cannot detect removal of its newest entries unless the head is kept elsewhere.
- Hashes support integrity checking. A hash shows that bytes are unchanged since Sakshi stored them. It does not prove authenticity, who sent something, truth, legal admissibility or trusted time.
- A signature shows which device key signed a bundle. It does not identify a person.
- Fail closed: if a key is unavailable or tampering is detected, the app reports it and does not fall back to plaintext.

## Languages in scope

English, Malayalam and Hindi, including Manglish, Hinglish, native scripts, romanised text, slang and code mixing among these. Other languages are future scope: they are preserved in the vault but not analysed, and the app is meant to say so rather than guess. All in-scope languages are labelled Experimental in the plan, and cue lists stay disabled for a language until a native speaker has reviewed them. Results are to be reported per language.

## Licence

No project licence has been chosen yet (megaplan open question Q1). Third-party data and models carry their own terms, which the megaplan lists as a release gate. No licence file is included.
