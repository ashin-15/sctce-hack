# Sakshi - Implementation Megaplan

Status: planning document. Date: 2 October 2026. Nothing in this file is implemented unless section 2 says so.

Claim labels used throughout:

| Label | Meaning |
|---|---|
| **[V]** Validated | Measured or tested in this repository, within the stated scope |
| **[E]** Experimentally supported | Limited laptop/synthetic experiment exists; not Android, not representative |
| **[D]** Supported by primary documentation | Android, Play, publisher or statute text inspected by the research reports |
| **[A]** Reasonable engineering assumption | Chosen by this plan; not yet tested |
| **[P]** Requires prototype validation | Must be proven on a physical Android device before any claim |
| **[U]** Unknown | No applicable evidence |
| **[X]** Rejected | Will not be built |
| **[F]** Deferred | Explicitly postponed |

Rule for every number in this plan: Target, Measured, Estimated or Unknown. A laptop measurement is never an Android measurement.

## 1. Executive Summary

Sakshi is a privacy-first Android app that lets a person preserve harassment-related evidence they choose to import, analyse it on the device, see possible patterns over time, review and correct every AI suggestion, and export a report they control.

What exists today is research (twelve reports), a Python laptop benchmark harness with fifteen measured baseline candidates on synthetic fixtures, a JSON event schema, and a provisional stack recommendation. There is no Android project, no Kotlin, no Gradle, no model that has run on a phone.

The plan builds the app in this order, which follows `AGENTS.md` "Engineering Priority" and the critical path in section 30:

1. Pure-Kotlin foundations: event model, integrity primitives, temporal engine (testable on the JVM, no device needed).
2. Android shell, encrypted vault, user-mediated import (Sharesheet, SAF, Photo Picker, manual note, one WhatsApp `.txt` export parser).
3. Text pipeline with rules-assisted highlighting, human review, deterministic temporal pattern cards.
4. Report and verifiable export bundle with an offline verifier.
5. OCR (ML Kit bundled Latin and Devanagari), then STT (whisper.cpp), then optional opt-in notification observation.
6. Neural classifier only after licensed, native-reviewed data exists.

The three things that make the product defensible (research: `technical-differentiation-novelty-stack.md` Tier A) are engineered first-class, not bolted on: capture-aware temporal analysis, a correction-aware source-to-finding-to-pattern-to-report dependency graph, and selective export that verifies offline without Sakshi.

MVP has no LLM, no Laya, no backend, no network permission, no accessibility service, no screen capture, no SMS permissions.

Highest-risk assumptions (section 33): Keystore-gated key access versus background work; SQLCipher plus Room on current AGP with 16 KB pages; ML Kit bundled OCR working with no `INTERNET` permission; Indic text shaping in `PdfDocument`; whisper.cpp memory on a 6 GB phone; and the absence of any representative labelled data for English, Malayalam or Hindi.

## 2. Current Repository State

### 2.1 Inventory and what each item establishes

| Item | Kind | Status | Constraint it places on implementation |
|---|---|---|---|
| `AGENTS.md` | Operating contract | Binding | Android-first, offline AI, human-in-the-loop, evidence-linked, originals preserved, explicit export, minimal architecture, do-not-build list, Laya deferred (2 Oct 2026), language scope English/Malayalam/Hindi incl. Manglish/Hinglish, Observed/Inferred/Pattern/Unknown, engineering priority order, bench verification commands |
| `HANDOFF.md` | Handoff for sentiment/emotion research | In `HEAD`; staged for deletion in the working tree | Decisions that must survive: emotion optional and never a gate; intensity `null`; no mandatory LLM; Kotlin temporal logic owns counts; unsupported language routes to unknown; no on-device training in MVP; code-point anchors; style rules (British "behaviour", no em dashes) |
| `RECOMMENDATION.md` | Provisional stack | Engineering hypothesis, **not benchmarked on Android** | Kotlin+Compose; share/SAF/export-parser import; ML Kit Latin/Devanagari + Tesseract `mal`; whisper.cpp base q5_1; Room+SQLCipher; SHA-256 + append-only chain + signed export root; `PdfDocument` with bundled Indic fonts; WorkManager queue; mandatory user confirmation before export; no LLM in first release; notifications, screen capture and automatic identity matching out of the default first-release path. Until native-language validation exists: ship manual import/review, rules-assisted highlighting, verbatim quotes, encrypted storage, template reports |
| `benchmark.md` | Milestone ledger M0-M3b | Factual record | No Android device was available; all numbers are laptop proxies; full matrix and stacks A-E are incomplete |
| `bench/` (1,919 lines Python, 6 test files) | Offline harness | **[V]** runs on a laptop | Reference implementations to port and to generate cross-language test vectors: `chain`, `merkle` (count-bound v2), `parse_export`, `rule_scores`/`RULES`, `Language.identify`, `link_rows`, `Queue` (resumable jobs), `export_allowed` (review gate), `quote_faithfulness` |
| `bench/prompts.json`, `bench/extraction.schema.json` | Planned LLM prompts and extraction schema | Not executed | Injection-safe system prompt and exact-substring quote rule are reusable when an LLM experiment is ever run |
| `data/sakshi-event-schema.json` | JSON Schema 2020-12, event v1 | **[V]** schema validates the synthetic example and rejects six malformed variants | The interchange contract for events. Section 11.2 analyses it |
| `data/text.jsonl` (600), `screenshots.jsonl` (168), `extraction.jsonl` (100), `audio.jsonl` (60) | Synthetic fixtures + public FLEURS speech | Synthetic; labels pending native-speaker review | Usable only as regression fixtures. 600 texts are variants of 14 templates. Never presented as real evidence |
| `data/labeled_data.csv` | Davidson et al. 2017, 24,783 English tweets | Third-party, MIT repo, tweet rights separate | English auxiliary baseline and pipeline smoke tests only. Not shipped in the APK |
| `data/manifest.json`, `data/audio_provenance.json` | Hashes and provenance | Factual | Pattern to copy for model and asset manifests |
| `results/` | CSVs, per-run metadata, plots, `sample-report.pdf` | **[V]** laptop only | See 2.2 |
| `research/*.md` (12) | Design research | **[D]** and design proposals | Sections 7 and 34 map each to components |
| `research/probes/`, `research/verification/` | Probe scripts and receipts | **[E]** Linux x86 | Two ONNX checkpoints loaded and ran on synthetic strings; pinned hashes recorded |
| `.lavish/*.html` | Review artefacts for the reports | Derived views | No additional constraints |
| `Harassment_Pattern_Guard.pptx.pdf` | Original pitch | Proposal, not a specification | Its "scan chats" and "hash as proof" wording is narrowed by `AGENTS.md` and the acquisition research |

### 2.2 Measured results and their limits

| Slot | Candidate | Measured (laptop, synthetic) | What it does **not** establish |
|---|---|---|---|
| Classifier | rules | macro-F1 0.9851, 0.05 ms/msg | Rules were authored knowing the 14 templates |
| Classifier | TF-IDF + logistic regression | macro-F1 0.4063 | Real-world accuracy |
| Classifier | rules + TF-IDF ensemble | macro-F1 0.9938 | Same; "best fixture score" is not a winner |
| Language ID | script heuristic | accuracy 0.8444 | Romanized/code-mixed robustness |
| Extraction | template extractive | field-F1 0.9935 | Sender/date were given in the input |
| Linking | sender+time rules | recall 0.3067, precision 0.1394 | Case labels were arbitrary |
| OCR | RapidOCR PP-OCRv4 ONNX | CER 0.0327, WER 0.2935 on 36 Latin screenshots, 2.68 s/screenshot, 545 MB peak | Hindi/Malayalam scripts unsupported by those weights; ML Kit and Tesseract untested |
| STT | faster-whisper base int8 | One-item smoke run only (limit=1); full run incomplete | whisper.cpp, Indic quality, phone speed |
| Storage | SQLite + per-row AES-256-GCM | 10,000 inserts 1.01 s | SQLCipher, Keystore, Room |
| Key | Argon2id 64 MiB t=3 | 0.27 s | Phone timing |
| Integrity | SHA-256 chain / Merkle v2 / HMAC / Ed25519 | tamper detected 1.0; 0.018 s / 0.067 s / 0.082 s / 18.55 s per 10,000 (pure-Python Ed25519) | Android crypto timings |
| PDF | ReportLab | 50 incidents 0.12 s | `PdfDocument`; Indic shaping correctness |
| Ingest | WhatsApp `.txt` parser | one fixture, one date format | ZIP, locales, attachments |

### 2.3 Classification of the starting point

1. **Exists and runs:** the Python harness and its tests; the event JSON Schema; synthetic fixtures.
2. **Research/documentation only:** every Android mechanism, every model choice, the vault, the temporal engine, the review flow, the report, the export.
3. **Experimentally validated (narrow):** integrity constructions detect tamper/reorder/truncation; Merkle v1 padding ambiguity found and fixed in v2; resumable queue survives a killed child process; offline guard caught an implicit RapidOCR font download; two MiniLM ONNX checkpoints run under ONNX Runtime 1.22.1 on Linux.
4. **Hypothesised:** everything in `RECOMMENDATION.md`'s table.
5. **Partially implemented:** nothing for Android. Bench adapters cover 16 of 1,514 inventory entries.
6. **Production-ready:** nothing.
7. **Explicitly deferred:** Laya; LLM; Accessibility; MediaProjection; SMS/MMS permissions; on-device training; emotion intensity; languages beyond English/Malayalam/Hindi; vector retrieval; Telegram/Meta service APIs.
8. **Must be validated on a real device:** section 28.
9. **Cannot be implemented (platform):** reading other apps' databases or private storage; recovering uncaptured or deleted messages; View Once payload capture; complete notification history; authenticated sender identity.
10. **Needs legal/privacy/licence/policy review:** section 24 and 25 gates.

### 2.4 Local toolchain facts (probed 2 Oct 2026)

- Android SDK at `~/Android/Sdk` with platforms 35 and 36 and build-tools 35.0.0, 36.0.0, 36.1.0. **No NDK installed.**
- `java` is OpenJDK 27. Android Gradle Plugin needs a supported JDK (17 or 21); a Gradle toolchain must be configured. **[A]**
- `adb devices` lists no device. The earlier research saw an SM-S928B (Android 16, API 36, arm64) but ran nothing on it.
- Host is Linux; the bench README is written for Windows PowerShell and Windows font paths.

## 3. Product Definition

> Sakshi is a privacy-first Android application that lets a person preserve harassment-related digital evidence they select, analyse it locally, see possible temporal patterns, keep provenance and integrity metadata, review every AI finding, and generate reports they control.

### 3.1 Three access boundaries

| Boundary | Definition | Examples | Sakshi position |
|---|---|---|---|
| **User-controlled evidence** (class B) | The user deliberately selects, shares, exports or types it | Screenshots, images, exported chat `.txt`/archive, audio, video, PDF, copied text, manual incident note | Primary and default path |
| **System-authorised observation** (class A/C) | A documented Android API after an explicit in-app opt-in and a system-settings grant | `NotificationListenerService` excerpts from allowlisted apps | Optional, off by default, never required, never described as complete |
| **Unsupported or circumventing access** (class D/E) | No documented entitlement, or defeats a control | Other apps' databases, private storage, backups, keys; View Once or disappearing-content capture; FLAG_SECURE capture; covert screen scraping; guessed provider URIs | Not built, not researched, no fallback towards it |

### 3.2 Product promises

Permissible: "Sakshi helps you preserve and review evidence you explicitly import and, if you opt in, message-related information exposed in supported Android notifications. Availability varies by app, settings and Android version."

Never claimed: reads all chats; recovers deleted messages; captures View Once; proves who sent something; always detects harassment; court-admissible; first of its kind; nothing can ever leave the phone.

## 4. Problem and User Workflow

Problem: evidence of repeated harassment is scattered, easy to lose, hard to order in time, and individual messages often look harmless while the sequence does not. Existing tools either store without analysing, or analyse in the cloud, or classify single messages.

Primary workflow (MVP):

```text
1. Open app, authenticate (device credential / biometric)
2. Create or open a Case
3. Import: share from another app | pick files | pick photos | paste text | write a note
4. Preview what was received -> choose case -> Save
5. App copies exact bytes into the encrypted vault, hashes them, records provenance
6. Processing (on demand or queued): parse export / OCR / STT -> text derivative
7. Rules-assisted highlighting (and, later, a classifier) proposes findings with source spans
8. User reviews each finding: Accept | Reject | Edit | Add context | Mark unknown
9. User confirms sender association, direction, timestamps, boundary notes
10. Temporal engine produces pattern cards with supporting events, gaps and limitations
11. User reviews patterns, selects what to disclose, previews redactions
12. Export: report PDF + manifest bundle, shared through the system share sheet by the user
13. Anyone can verify the bundle offline with the verifier
```

Secondary workflow (post-MVP, optional): opt in to notification observation for chosen apps; excerpts appear in a session-only review inbox; the user decides what becomes evidence.

## 5. Requirements

### 5.1 Functional

| ID | Requirement |
|---|---|
| FR-01 | Create, rename, archive and delete cases; all evidence belongs to exactly one case |
| FR-02 | Receive `ACTION_SEND` and `ACTION_SEND_MULTIPLE` (text, images, audio, video, PDF, text files) with preview and explicit Save |
| FR-03 | Import via SAF `ACTION_OPEN_DOCUMENT` and Photo Picker; no broad storage permission |
| FR-04 | Manual incident note and manual boundary note |
| FR-05 | Copy exact received bytes to encrypted app-private storage, compute SHA-256, record acquisition provenance, never modify originals |
| FR-06 | Produce versioned derivatives (parsed text, OCR text with regions, transcript with time ranges) linked to parent hash and tool version |
| FR-07 | Parse one WhatsApp `.txt` export dialect into message events with byte/record spans; ambiguous dates go to review |
| FR-08 | Propose findings with category, basis, confidence semantics, producer version and evidence anchors |
| FR-09 | Review flow for evidence association, signal, and explanation/pattern as three separate targets |
| FR-10 | Corrections are new revisions; originals and earlier revisions are never overwritten |
| FR-11 | Deterministic temporal engine: distinct-contact counts, episodes, after-boundary recurrence, wording transition, comparable-window density change, each with count bounds and limitations |
| FR-12 | Dependency-aware invalidation: a change to evidence, link, tag or boundary marks dependent patterns and report drafts stale and recomputes them |
| FR-13 | Every displayed result carries exactly one of Observed, Inferred, Pattern, Unknown (plus User-reported for user statements) |
| FR-14 | Report generation with evidence references, hashes, timestamps and their basis, model/rule versions, review state, corrections, report version |
| FR-15 | Export bundle with canonical manifest, hashes, signature, selective disclosure and redaction; only after explicit user review |
| FR-16 | Offline verification of a bundle without Sakshi |
| FR-17 | Deletion of evidence, case, or whole vault, with dependent derivatives, findings, indexes and jobs removed |
| FR-18 | Onboarding that explains scope, limits, and what the app cannot do |
| FR-19 | (Phase 2b) Optional notification observation with allowlist, pause, revoke, session-only default |
| FR-20 | Case-scoped lexical search over reviewed text derivatives |

### 5.2 Non-functional

| ID | Requirement |
|---|---|
| NFR-01 | No network use during analysis; MVP release build declares no `INTERNET` permission |
| NFR-02 | All evidence, derivatives, metadata, indexes and reports encrypted at rest; no plaintext temp files, logs, thumbnails or WorkManager payloads containing evidence |
| NFR-03 | Fail closed: key unavailable, disk full or tamper detected yields an explicit unavailable state, never plaintext fallback |
| NFR-04 | One heavy model loaded at a time; heavy work never on the main thread or in an NLS callback |
| NFR-05 | Works on a 6 GB arm64 phone; degrades (not crashes) on 4 GB **[P]** |
| NFR-06 | Unsupported language, missing model, timeout, OOM and invalid output are distinct states, none of which reads as "benign" |
| NFR-07 | Deterministic, idempotent temporal reducer: same inputs give same patterns regardless of import order |
| NFR-08 | No legal conclusions, guilt labels, danger scores or admissibility claims anywhere in UI, reports or code identifiers |
| NFR-09 | Evidence and its derivatives excluded from cloud backup, device-to-device and cross-platform transfer |
| NFR-10 | Every numeric performance claim backed by a release-build measurement on a named device |

## 6. Scope

### MVP

1. Android shell, Compose UI, app lock.
2. Case management.
3. User-mediated import: Sharesheet, SAF, Photo Picker, pasted text, manual note.
4. Immutable encrypted originals with SHA-256 and provenance.
5. Text pipeline: plain text and one WhatsApp `.txt` export dialect.
6. Rules-assisted highlighting, labelled as a rules baseline, for English, Hindi, Hinglish, Malayalam, Manglish cue lists that are native-speaker reviewed.
7. Human review of associations, findings and patterns.
8. Deterministic temporal engine with four pattern cards and coverage/gap handling.
9. Append-only audit chain and provenance graph.
10. Report (PDF) and verifiable export bundle; JVM offline verifier.
11. Bundled ML Kit Latin OCR for screenshots (first modality after text).

Preserve-only in MVP (saved, hashed, previewed where safe, **not analysed**): audio, video, PDF.

### Phase 2

- ML Kit Devanagari OCR; Tesseract `mal` for Malayalam script (separate validated engine task).
- whisper.cpp base multilingual STT for short audio (60 s cap).
- Opt-in `NotificationListenerService` observation (API 30+), synthetic producer app first.
- Experimental English narrow-toxicity ONNX pack, review-only, uncalibrated, clearly labelled.
- PDF page text/OCR and sparse video frames, only after a secure seekable decrypted source exists.
- Encrypted recovery bundle with user-held secret.

### Future

- Fine-tuned multilingual MiniLM encoder with multi-label head, calibrated per language, ONNX int8, after licensed and native-reviewed data exists.
- Optional expressed-emotion track (never a gate).
- One optional foreground LLM experiment for paraphrase of validated pattern packets.
- EWMA/CUSUM flags, case-scoped semantic retrieval, C2PA interoperability.

### Deferred (decided, with re-entry conditions)

- **Laya.** Reconsidered only with: native Android runtime parity, task quality on Sakshi labels, calibration evidence, device RAM/latency/energy evidence, model-format compatibility, licence verification of pinned checkpoint and tokenizer.
- LLM summaries, on-device training/LoRA, emotion intensity, languages beyond English/Malayalam/Hindi.

### Explicitly Rejected

Silent third-party scraping; private database or backup extraction; key extraction; View Once or disappearing-content circumvention; FLAG_SECURE capture; AccessibilityService collection; MediaProjection collector in the default product; SMS/MMS permissions; always-on high-compute monitoring; cloud AI default; automatic reporting, replying, blocking or contacting anyone; guilt/innocence classification; violence prediction; numeric danger scores; court-admissibility claims; cross-app automatic identity matching; panic wipe; backend services; blockchain anchoring; models that do not fit a 6 GB phone.

## 7. Repository-to-Product Mapping

| Source | Decision it supplies | Product component |
|---|---|---|
| `research/android-evidence-acquisition-specification.md` | Access classes A-E; M1-M9 mechanism contracts; 90-row capability matrix; AC01-AC12; import hardening (sec 8.4) | `:acquisition:importer`, `:acquisition:notifications`, consent UI, device validation plan |
| `research/android-multimodal-evidence-pipeline.md` | Two lanes into one store; shared import sequence (sec 4.2); per-modality preprocessing; resource envelopes (sec 7.4); secure random access (sec 9.2); scope tiers P0/P1/P2 | `:processing:*`, vault blob format, performance budgets |
| `research/notification-intelligence-layer.md` | Event-driven optional layer; observation schema; four dedup layers; three storage classes; key modes; budgets (sec 11); stages A-E | `:acquisition:notifications` (Phase 2b) |
| `research/whatsapp-view-once-feasibility.md` | Incident-preservation not extraction; `viewOnceStatus` and `contentAvailability` enums; VO-01..12; 28 forbidden claims | Manual incident note, report wording, guardrail tests |
| `research/temporal-harassment-patterns.md` + `data/sakshi-event-schema.json` | Event contract; partial-order time; count bounds; pattern rules (sec 8); pattern record (sec 8.5); 16 metamorphic tests (sec 13.4); fixtures A-F | `:core:model`, `:domain:temporal` |
| `research/local-ai-architecture.md` | Small calibrated core + deterministic temporal + optional LLM; temporal-before-benign-stop; routing policy; release gates | AI roadmap, routing states |
| `research/sentiment-emotion-local-ai-android-design.md` | Emotion independent of behaviour; state list; code-point anchors; duplicate/gap policy table; Kotlin component list; ORT boundary; lifecycle state machine | `:ai:*` contracts, review model |
| `research/laya-source-analysis-and-sakshi-local-ai-architecture.md` | Laya not primary detector; first classifier candidates; promotion gates | Deferred list, future gates |
| `research/harassment-detection-datasets.md` | Rights-aware portfolio; label mapping with supervision masks; E4 schema reconciliation; collection protocol; India legal notes | Section 25, 29 |
| `research/existing-systems-survey.md`, `technical-differentiation-novelty-stack.md` | What must not be claimed as novel; Tier A candidates I1-I3; demo script | Product positioning, MVP emphasis |
| `research/deep-research-report.md` | Early exploration; partly superseded | Not used for decisions (see Decision Log D-14) |
| `RECOMMENDATION.md` | Concrete stack hypothesis and fallback | Section 15 model table, section 22 storage |
| `bench/adapters.py` | `chain`, `merkle`, `parse_export`, `RULES`, `Language.identify` | Ported to Kotlin with shared test vectors |
| `bench/pipeline.py` | Resumable job queue, review gate | `ProcessingJob` design, export gate |
| `results/*.csv` | Laptop baselines | Regression references only |

## 8. Architecture

Derived from the research, not from convention. Three properties drive the shape:

1. **One immutable encrypted store, two acquisition lanes** (multimodal report sec 1).
2. **A dependency graph from original to report** so corrections propagate (novelty stack I2).
3. **Pure-Kotlin deterministic core** for events, integrity and temporal logic, so the central innovation is testable without a phone or a model (temporal report sec 12.1, HANDOFF decision).

```text
Android app (single process, no backend)
|
+-- Presentation: Jetpack Compose, ViewModels, navigation
|
+-- Domain (pure Kotlin, JVM-testable)
|     +-- model: Event, Finding, Pattern, spans, epistemic status
|     +-- integrity: sha256, hash chain, Merkle v2, canonical JSON
|     +-- temporal: canonicalizer, partial order, windows, pattern rules
|     +-- review: revision and invalidation rules
|     +-- report: report model, template sentences
|
+-- Data
|     +-- vault: encrypted blobs (chunked AES-256-GCM), key management
|     +-- database: Room over SQLCipher
|     +-- provenance graph + append-only audit chain (tables)
|     +-- jobs: resumable processing queue
|
+-- Acquisition
|     +-- importer: Sharesheet, SAF, Photo Picker, paste, manual note
|     +-- notifications (Phase 2b, optional)
|
+-- Processing
|     +-- text: export parser, script/language hints, view builder
|     +-- ocr (ML Kit; Tesseract later)
|     +-- stt (whisper.cpp, Phase 2)
|     +-- signals: rules engine; classifier adapter (later)
|
+-- Export
      +-- PDF renderer, bundle writer, signer
      +-- verifier (pure JVM, also shipped as a CLI jar)
```

Dependency rule: Presentation -> Domain <- Data/Acquisition/Processing/Export. Domain depends on nothing Android. Processing never writes evidence; it returns derivatives and proposals that the repository persists in a transaction.

## 9. Android Architecture

### 9.1 Project settings

| Setting | Value | Status |
|---|---|---|
| Language / UI | Kotlin, Jetpack Compose, Material 3 | `AGENTS.md` |
| `minSdk` | 26 | **[A]** from multimodal sec 10.2 (proxy file descriptor needs API 26). Notification module additionally gated to API 30+ |
| `compileSdk` / `targetSdk` | 36 | **[D]** Play requires API 36 for new apps and updates from 31 Aug 2026 |
| ABI | `arm64-v8a` only for release | **[A]**; all native libraries must be 16 KB page aligned **[D]** (Play requirement since 1 Nov 2025 for native code on Android 15+) |
| JDK | 17 or 21 via Gradle toolchain | **[A]** host has JDK 27 only |
| DI | Manual constructor injection through one `AppContainer` | **[A]** minimal architecture; revisit only if it hurts |
| Async | Coroutines + Flow; one bounded single-thread dispatcher for inference | sentiment report sec 19 |
| Background | WorkManager with opaque job IDs only | **[D]** |
| Dependency versions | Pinned in `gradle/libs.versions.toml` at Phase 1 after checking current stable releases; no dynamic versions | **[A]** exact versions are deliberately not guessed here |

### 9.2 Module structure

Chosen to keep the JVM-testable core separate and everything else as few modules as possible. Feature UI stays in `:app` as packages until build time or team size justifies splitting.

```text
android/
+-- settings.gradle.kts, build.gradle.kts, gradle/libs.versions.toml
+-- core/
|   +-- model/          (pure Kotlin)  events, spans, enums, schema adapter
|   +-- integrity/      (pure Kotlin)  sha256, chain, merkle, canonical JSON
|   +-- temporal/       (pure Kotlin)  canonicalizer, timeline, pattern rules
|   +-- crypto/         (Android)      Keystore, blob envelope, DB key wrapping
|   +-- database/       (Android)      Room entities, DAOs, migrations
|   +-- vault/          (Android)      EvidenceRepository, provenance, audit, jobs
+-- acquisition/
|   +-- importer/       (Android)      share/SAF/picker/paste/manual, validation
|   +-- notifications/  (Android, Phase 2b)
+-- processing/
|   +-- text/           (pure Kotlin)  WhatsApp parser, script hints, rules engine
|   +-- ocr/            (Android)      ML Kit adapter
|   +-- stt/            (Android+NDK, Phase 2) whisper.cpp JNI
+-- export/
|   +-- report/         (Android)      PdfDocument renderer, bundle writer, signer
|   +-- verifier/       (pure Kotlin)  bundle verification library + CLI main
+-- app/                (Android)      Compose UI: onboarding, cases, import,
|                                      evidence, review, timeline, patterns,
|                                      search, reports, settings
+-- testfixtures/       shared synthetic fixtures (timelines A-F, vectors)
```

The existing Python `bench/`, `data/`, `results/`, `research/` stay where they are. The Android project lives under `android/` so the two toolchains do not interfere.

### 9.3 Manifest (MVP)

- Permissions: `USE_BIOMETRIC` only. **No** `INTERNET`, storage, media, microphone, camera, accessibility, SMS, foreground-service-media-projection, `QUERY_ALL_PACKAGES`.
- One exported activity: the share target, with explicit MIME filters (`text/plain`, `image/*`, `audio/*`, `video/*`, `application/pdf`, `application/zip`, `text/*`). Everything else `exported="false"`.
- `android:allowBackup="false"`, plus `dataExtractionRules` and `fullBackupContent` that exclude every domain; evidence stored under `noBackupFilesDir`.
- `FLAG_SECURE` on all evidence, review and report screens (user-toggleable only with a warning).
- Phase 2b adds the NLS service declaration from notification report sec 2.1 and nothing else.

### 9.4 Threading and lifecycle

- UI thread: rendering only.
- `ioDispatcher`: stream copy, hashing, encryption, database.
- `inferenceDispatcher`: single thread; OCR, STT and any model run serially under a `ModelSessionManager` lease; sessions closed on background/lock after leases finish.
- Process death: job rows remain `pending`/`running`; `running` is reset to `pending` on start (pattern from `bench/pipeline.py: Queue.recover`). Incomplete analysis is never shown as complete.

## 10. Evidence Lifecycle

```text
Acquisition (share / pick / paste / note / opt-in notification)
   -> Validation (scheme, MIME sniff, size/count/pixel/archive limits)
   -> Preview + explicit Save into a chosen case
   -> Original preservation (stream copy -> chunked AES-GCM blob, SHA-256 of received bytes, atomic commit)
   -> Provenance + audit record
   -> Metadata extraction (declared vs detected MIME, size, dimensions, duration, claimed times)
   -> Derivative generation (parsed text / OCR / transcript), versioned, parent-linked
   -> Event construction (one contact unit per source message where the source supports it)
   -> Signal proposal (rules; later classifier) -> Finding (unreviewed)
   -> Human review (accept / reject / edit / add context / unknown)
   -> Temporal aggregation over eligible events -> Pattern (candidate or supported description)
   -> Pattern review
   -> Report snapshot (frozen dependency versions)
   -> Explicit export (user picks content, redaction, destination)
```

### 10.1 Immutable versus derived

| Artefact | Mutability | Notes |
|---|---|---|
| Original evidence blob | Immutable | "Original" means the exact bytes delivered to Sakshi, not the sender's source file |
| Acquisition/provenance record | Immutable | Append-only |
| Audit record | Immutable, hash-chained | |
| Derivative (text, OCR, transcript, normalised view) | Immutable per revision | A re-run or a user edit creates a new revision with `parent_revision` |
| Event revision | Immutable per revision | Matches schema `revision` |
| Finding | Immutable proposal + separate review decisions | |
| Correction / review decision | Immutable, append-only | Latest decision wins for the current view |
| Pattern | Recomputed projection, versioned | Marked stale when a dependency changes |
| Report draft | Recomputed | |
| Report snapshot | Immutable once exported | Later changes create a new snapshot; old one is marked superseded, not deleted |

### 10.2 Relationship chain

```text
Original Evidence --derives--> Derivative --anchors--> Finding --reviewed by--> Correction
        \                                                  |
         \--referenced by--> Event --supports--> Pattern --cited by--> Report Snapshot
```

Invariant: every Finding has at least one anchor into a specific derivative or original revision; every Pattern lists event IDs **and revisions**; every Report Snapshot lists the exact revisions it used. A finding that cannot resolve its anchor is rejected at construction, not displayed.

## 11. Data Model

### 11.1 Storage decision

Room over SQLCipher for all structured data, plus encrypted blob files for large content. Rationale: `RECOMMENDATION.md` selects Room + SQLCipher; three reports warn that Room alone is not encryption and that plaintext columns, FTS and WAL leak (temporal sec 12.1, notification sec 9.2, sentiment sec 18). Whole-database encryption removes the per-column leak class. Status **[A]**; SQLCipher with current Room/AGP and 16 KB pages is **[P]** (validation V-07). Fallback if it fails: plain Room with every sensitive column stored as an AES-GCM envelope and no FTS, which is the scheme the bench actually measured.

### 11.2 The event schema as a contract

`data/sakshi-event-schema.json` (`urn:sakshi:event:1`, `additionalProperties: false`) is treated as the **interchange and export contract** for events. The database stores the same information normalised; `EventSchemaAdapter` in `:core:model` converts both ways and is tested against the schema file itself.

Field semantics (all 20 required top-level fields):

| Field | Semantics | Stored in |
|---|---|---|
| `schema_version` | const 1 | constant |
| `event_id`, `case_id`, `revision` | opaque IDs (max 128 chars); revision integer from 1 | `event`, `event_revision` |
| `event_kind` | message_observation, contact_attempt_observation, user_boundary, user_note, reported_external_event, notification_lifecycle | `event_revision.kind`. Only the first two count as contacts |
| `observed_at` | when Sakshi captured/imported it | `event_revision` |
| `available_at` | when this revision became available to analysis | `event_revision` |
| `timestamp` | earliest/latest bounds, basis (source_claim, collector_wall_clock, user_reported, unknown), precision, source timezone, collector session, monotonic ms | `event_revision` columns |
| `sender` | case-scoped `actor_id`, display label, identity basis (unknown, app_scoped_hint, user_asserted), association review | `actor`, `event_revision` |
| `source` | kind (notification_excerpt, selected_export/text/image/audio/video/document, manual_entry), app claim, profile/conversation scope, source record ID, parser version | `event_revision`, `source_scope` |
| `direction` | incoming, outgoing, system, unknown | `event_revision` |
| `categories[]` | label, basis (rule/classifier/llm suggestion, user_tag), confidence object, producer version, evidence reference IDs, review status | `finding` + `review_decision` |
| `severity` | review priority (ordinary, review, urgent_review, unknown), basis, refs. Triage only | `event_revision` |
| `evidence_references[]` | reference ID, artefact ID, sha256, representation, locator | `evidence_anchor` |
| `user_confirmation` | status, reviewed_at, scope | `event_revision` |
| `deduplication` | distinct_observation, same_representation, possible_duplicate, lifecycle_only; canonical event ID; method version | `event_revision` |
| `coverage` | context completeness, text status, outgoing coverage, gap reference IDs | `event_revision`, `coverage_gap` |
| `boundary` | marker, actor, review status, communication status, unwanted-contact marking | `boundary` |
| `relationship_to_previous_events[]` | typed links with basis, confidence, review status | `event_link` |
| `retention` | session_only, encrypted_candidate (requires expiry), confirmed_vault (requires confirmed review) | `event_revision` |

Confidence object: `semantics` is calibrated_probability (requires value and calibration version), uncalibrated_bounded_score (value, no calibration), not_applicable or unknown (value null). Locators: whole artefact; text span in half-open Unicode code points; audio time in ms; image or page region by region ID.

### 11.3 Mismatches found and how they are handled

| # | Mismatch | Resolution |
|---|---|---|
| M1 | Schema anchors use **Unicode code points**; `notification-intelligence-layer.md` sec 6 specifies UTF-16 offsets | Code points are canonical everywhere persisted (schema, HANDOFF, datasets E1). UTF-16 conversion happens only at the UI/tokenizer boundary with surrogate checks. No schema change |
| M2 | Three label vocabularies: bench (`insult, threat, sexual_harassment, caste_religious_slur, doxxing, coercive_control`), event schema (`ordinary, verbal_abuse, explicit_threat, implied_threat, intimidation, controlling_request, sexual_pressure, privacy_exposure_indicator, contact_request, unknown`), datasets ontology (11 research labels) | Event schema labels are canonical in the app. A versioned `LabelMapping` table maps producers to them: insult -> verbal_abuse; threat -> explicit_threat (implied_threat only by a rule that says so); sexual_harassment -> sexual_pressure; doxxing -> privacy_exposure_indicator; coercive_control -> controlling_request. The producer's own label is kept in `finding.source_label` |
| M3 | `caste_religious_slur` (bench) and `identity_directed_abuse` (datasets) have **no** schema v1 label | MVP: stored as `verbal_abuse` with `source_label` preserved and shown. Proposed **schema v1.1** adds `identity_directed_abuse` to the enum. This is an additive enum change: v1 readers reject v1.1 events, so the export manifest states the event schema version and the verifier supports both. Needs sign-off before use (Open Question Q5) |
| M4 | Schema has no Finding, Pattern, Correction, Report or Derivative entities | By design: schema is single-event. Those entities get their own tables and their own export JSON files (section 21) with separate version numbers |
| M5 | Schema event carries event and annotation revisions together | Database tracks event revisions and review decisions separately; adapter composes them at a stated knowledge cutoff |
| M6 | Bench fixtures (`text.jsonl`) have a flat `sender`, `timestamp`, `labels` shape | `FixtureAdapter` in test code converts fixtures to events with `basis=source_claim`, `identity_basis=unknown`, `user_confirmation=pending`. Fixtures stay synthetic-labelled |
| M7 | Schema cannot express cross-record invariants | The ten application invariants of temporal report sec 6.4 are implemented in `EventValidator` and unit-tested |

No field of schema v1 is changed or removed. If v1.1 is approved: bump `$id` to `urn:sakshi:event:1.1`, keep v1 file, add migration note, regenerate the validation receipt in `research/verification/`.

### 11.4 Tables

All tables live in the SQLCipher database, so every column is encrypted at rest. IDs are random UUIDv4 strings. "Imm" = rows are insert-only.

| Entity | Key fields | PK / FK / indexes | Imm | Deletion |
|---|---|---|---|---|
| `case` | id, title, created_at, status(active/archived), owner_note | PK id | no | Cascades to everything in the case after confirmation |
| `actor` | id, case_id, display_label, identity_basis, association_review | PK id; FK case; idx(case_id) | no (label edits are new audit rows) | With case |
| `source_scope` | id, case_id, source_app_claim, profile_scope, conversation_scope | PK id; FK case | yes | With case |
| `evidence` | id, case_id, acquisition_kind, access_class, received_at, claimed_origin, declared_mime, detected_mime, byte_size, sha256, support_state(saved/analysis_pending/analyzed/partial/unsupported/unavailable/failed), retention_mode | PK id; FK case; unique(case_id, sha256, acquisition_id) ; idx(case_id, received_at) | yes | Deletes blob, derivatives, anchors, findings, jobs |
| `evidence_blob` | evidence_id, path, envelope_version, wrapped_key, chunk_size, chunk_count, plaintext_length | PK evidence_id; FK evidence | yes | File overwritten-then-unlinked best effort; key row destroyed (crypto-erase) |
| `capture_metadata` | evidence_id, importer_mechanism, uri_authority_claim, display_name_claim, exif_json, provider_transform(unknown/none/transcoded), collector_session_id, elapsed_realtime_ms | PK evidence_id | yes | With evidence |
| `derivative` | id, evidence_id, parent_derivative_id, revision, kind(parsed_text/ocr/transcript/normalised_view/user_edit), text, source_map_json, quality_json, tool_id, tool_version, model_version_id, created_at | PK id; FK evidence; idx(evidence_id, kind, revision) | yes | With evidence |
| `region` | id, derivative_id, page_index, polygon_json, transform_json | PK id; FK derivative | yes | With derivative |
| `event` | id, case_id | PK id; FK case | yes | With case or when its only evidence is deleted |
| `event_revision` | event_id, revision, kind, observed_at, available_at, ts_earliest, ts_latest, ts_basis, ts_precision, ts_timezone, collector_session_id, monotonic_ms, actor_id, source_scope_id, source_kind, source_record_id, parser_version, direction, review_priority, confirmation_status, confirmation_scope, reviewed_at, dedup_status, canonical_event_id, dedup_method, coverage_context, text_status, outgoing_coverage, retention_mode, expires_at, consent_generation | PK(event_id, revision); idx(case_id via event, ts_earliest); idx(actor_id) | yes | With event |
| `evidence_anchor` | id, event_id, event_revision, evidence_id, derivative_id, sha256, representation, locator_kind, start_cp, end_cp, start_ms, end_ms, region_id | PK id; FKs | yes | With event or evidence |
| `event_link` | id, from_event, to_event, type, basis, confidence_json, review_status | PK id; idx(from_event) | yes (review is a decision row) | With either event |
| `boundary` | id, case_id, event_id, marker, actor_id, review_status, communication_status, unwanted_contact, scope_note | PK id | yes | With event |
| `coverage_gap` | id, case_id, source_scope_id, start_at, end_at (nullable = unknown), reason | PK id; idx(case_id, start_at) | yes | With case |
| `finding` | id, case_id, event_id, event_revision, label, source_label, basis, confidence_value, confidence_semantics, calibration_version, producer_version, model_version_id, epistemic_status(inferred/user_reported), created_at | PK id; idx(event_id) | yes | With event |
| `finding_anchor` | finding_id, anchor_id | PK both | yes | With finding |
| `review_decision` | id, target_type(association/finding/pattern/explanation), target_id, target_revision, action(accept/reject/edit/add_context/mark_unknown), reason_code, edited_value_json, note, decided_at | PK id; idx(target_type, target_id) | yes | With target |
| `pattern` | id, case_id, type, rule_version, actor_scope, evidence_view(confirmed_only/candidate_preview), knowledge_cutoff, window_start, window_end, clock_basis, measurements_json, interpretation_text, limitations_json, assessment_status(candidate/supported_description/insufficient_context/not_observed/stale), generated_at | PK id; idx(case_id, type) | versioned; stale rows kept until replaced | With case |
| `pattern_support` | pattern_id, event_id, event_revision, role(supporting/context) | PK(pattern_id, event_id, role) ; idx(event_id) | yes | With pattern |
| `report` | id, case_id, title, created_at | PK id | no | With case |
| `report_snapshot` | id, report_id, version, created_at, manifest_sha256, merkle_root, signature, signer_key_id, superseded_by, dependency_json | PK id | yes | Only by explicit user action; audit row remains |
| `audit_record` | seq, at, action, subject_type, subject_id, payload_sha256, prev_hash, this_hash | PK seq | yes, chained | Never deleted except whole-vault wipe; deletion of a subject appends a tombstone row |
| `model_version` | id, name, role, artefact_sha256, tokenizer_sha256, runtime, runtime_version, quantisation, licence, calibration_id, supported_languages_json | PK id | yes | Never |
| `processing_job` | id, evidence_id, stage, status(pending/running/complete/failed/cancelled), source_sha256, attempt, consent_generation, last_error_code | PK id; idx(status) | no | With evidence |
| `label_mapping` | producer, producer_label, schema_label, mapping_version | PK(producer, producer_label, mapping_version) | yes | Never |

Migration rules: Room schema JSON exported and committed; every migration has an instrumented migration test; enum additions are additive; no destructive migration in release builds.

## 12. Provenance Model

A typed directed acyclic graph stored relationally (no graph database):

- Nodes: `evidence`, `derivative`, `event_revision`, `finding`, `review_decision`, `pattern`, `report_snapshot`.
- Edges: derivative -> parent evidence/derivative (with parent hash and transform ID); anchor -> derivative span/region/time range; finding -> anchors; decision -> target revision; pattern -> event revisions; snapshot -> every revision it used.

`ProvenanceGraph.dependentsOf(node)` drives invalidation (section 18.3). `ProvenanceGraph.traceToOriginal(finding)` drives the "show source" action in the UI and the per-claim reference list in reports.

Audit chain: each `audit_record` stores `this_hash = SHA-256(prev_hash || len(payload) as 8-byte big-endian || payload)`, the exact construction of `bench/adapters.py: chain`. The chain head is included in every export manifest. Limits stated in UI and report: the chain detects later modification of recorded history relative to a head the verifier already holds; it does not detect rollback on a fully compromised device and does not prove when anything happened.

## 13. Acquisition Architecture

### 13.1 Lane B: user-mediated import (MVP)

| Mechanism | Android API | Accepts | Class |
|---|---|---|---|
| Share target | `ACTION_SEND`, `ACTION_SEND_MULTIPLE` | text, image, audio, video, PDF, text file, zip | A/B |
| Document picker | `ACTION_OPEN_DOCUMENT` (`OpenMultipleDocuments`) | audio, PDF, text, archives, any file | A/B |
| Photo Picker | `PickVisualMedia` / `PickMultipleVisualMedia` | images, video | A/B |
| Paste | in-app text field | text | B |
| Manual incident note | in-app form | user statement, recalled wording, approximate time, View Once status | B, user-reported |

Shared import sequence (multimodal sec 4.2), implemented once in `EvidenceImporter`:

1. Collect items from the intent/result; treat URI, name, MIME, caller and content as untrusted claims.
2. Accept only `content://` streams and `EXTRA_TEXT`; never fetch a shared web link; never open a `file://` path supplied by another app.
3. Show preview (count, declared type, size if known), destination case, retention statement. No auto-save.
4. On Save: stream-copy off the main thread into an encrypted temp blob while hashing, enforcing limits during the stream even when the provider reports no size.
5. Atomic finalise: rename blob, insert `evidence` + `evidence_blob` + `capture_metadata` + audit row in one transaction. On any failure delete the temp blob and report a visible failure.
6. Enqueue analysis by evidence ID. Never by URI.
7. Persist a URI grant only if offered and needed; default is not to.

Initial limits (Targets, multimodal sec 7.4): image <= 20 MiB and <= 12 MP analysed; audio <= 20 MiB, <= 60 s analysed; video <= 100 MiB preserved; PDF <= 10 MiB; text export <= 5 MiB / 10,000 records; batch <= 10 items; archive entry count, expanded size, compression ratio and nesting capped. Over-limit items are either "preserved, not analysed" or refused before commit; never silently truncated.

### 13.2 Lane A: notification observation (Phase 2b, optional)

Follows `notification-intelligence-layer.md` in full. Summary of binding rules:

- Off by default; app fully usable without it. In-app disclosure and affirmative choice, then `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`. Access granted and listener connected are tracked separately.
- Positive package allowlist; non-allowlisted packages are dropped **before** reading extras.
- Callback does a bounded typed snapshot and a non-blocking `trySend` to one bounded channel (64 snapshots). No disk, crypto, tokenizer or model in the callback.
- Read-only: never invoke actions, reply, dismiss, mark read, open URIs or inflate foreign `RemoteViews`.
- Default retention is session-only (RAM). Expiring encrypted candidate inbox (24 h, 100 records / 2 MiB) is a separate opt-in. Confirmed evidence only by user action.
- Lock-state policy: no private-body analysis while the device is locked unless the user separately opts in.
- Four dedup layers; removal is lifecycle metadata, never "message deleted"; reconnect snapshots are tagged `active_snapshot`.
- Coverage state shown as connected / paused / unavailable / coverage unknown. Never "all clear".

Position in the plan: `RECOMMENDATION.md` keeps notification monitoring out of the default first-release path while `AGENTS.md` lists NLS as a supported mechanism and puts acquisition first. Resolution (Decision D-06): import lane is MVP; NLS is the next phase, built first against a synthetic producer app, and never on the critical path.

### 13.3 Per-app honesty

No app gets a "supported" badge from documentation alone (AC03). The app ships an internal capability table keyed by source app with states `tested(device, app version)`, `unverified`, `unsupported`. WhatsApp export privacy settings (Advanced Chat Privacy / Restricted chat), Signal preview modes, Telegram secret chats and locked chats are first-class "unavailable" states with an offer of manual note or permitted screenshot import. There is no escalation to stronger permissions when something is unavailable.

## 14. Multimodal Processing Pipeline

| Modality | Pipeline | Runs on device | MVP status |
|---|---|---|---|
| Text (paste, share) | preserve -> UTF-8 decode -> script/language hints -> signals | yes | MVP |
| WhatsApp `.txt` export | preserve -> dialect detection -> streaming parse -> message events with byte spans -> signals | yes | MVP (one dialect) |
| Image / screenshot | preserve -> header and dimension check -> EXIF orientation once -> bounded/tiled bitmap -> OCR -> text derivative with regions -> signals | yes | MVP Latin; Phase 2 Devanagari, Malayalam |
| Audio | preserve -> `MediaExtractor` -> `MediaCodec` PCM -> resample 16 kHz mono (channels preserved in metadata) -> STT -> transcript with ms ranges -> signals | yes | Preserve-only in MVP; Phase 2 |
| Video | preserve -> metadata -> sparse frames (<= 30, actual PTS recorded) -> OCR; audio track -> STT; coverage manifest | yes | Preserve-only in MVP; Phase 2 after secure seekable source |
| PDF | preserve -> isolated render -> native text (API 35+) or page OCR | yes | Preserve-only in MVP; Phase 2 |

Rules that apply to every row:

- Derivatives never replace originals. Deskew, crop, denoise, normalise, translate are new derivative revisions with a source map.
- No decrypted evidence file is written to disk for a decoder. Small images are decrypted into bounded RAM. Seekable consumers use `MediaDataSource` (API 23) or `StorageManager.openProxyFileDescriptor` (API 26) over the chunked AES-GCM blob **[P]**. Until that adapter is proven, audio/video/PDF analysis is not enabled.
- OCR and STT confidence are extraction quality, not classifier confidence and not truth.
- Silence, unsupported script, unreadable file and unknown speaker are explicit states; nothing is filled in.
- Screenshot sender, direction and time are user-confirmed; OCR does not decide who said what.

## 15. Local AI Architecture

```text
Evidence
  -> cheap filtering        (availability, size, script/language hints, dedup of representations)
  -> modality extraction    (parser | OCR | STT)
  -> signals                (rules baseline; later compact classifier)
  -> [optional local reasoning: not in MVP]
  -> temporal pattern engine (deterministic, runs for all eligible events, not only flagged ones)
  -> human review
```

Two rules from the research that the code must enforce:

- **Temporal before benign stop** (local-ai sec 1): an ordinary-looking message still enters the temporal engine. A classifier result never removes an event.
- **Routing controls compute, not retention** (local-ai sec 10.1): no model output can prevent the user saving, reviewing or exporting evidence.

### 15.1 Component and model table

| Component | Choice | Task | Params / size | Format, runtime | CPU/GPU/NPU | RAM, cold, warm, battery | Multilingual | Calibration | Accuracy evidence | Licence | Android feasibility | Phase |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Rules baseline | Kotlin cue lists ported from `bench/adapters.py: RULES`, extended and native-speaker reviewed | Highlight candidate spans for review | none; KB | Kotlin | CPU | Estimated negligible; Unknown on device | en, hi, hinglish, ml, manglish cue lists | none; scores are `uncalibrated_bounded_score` or absent | **[V]** 0.985 macro-F1 on fixtures the rules were written for. Real-world: **[U]** | project | High **[A]** | MVP |
| Script/language hints | Kotlin port of `Language.identify` | Route to cue lists; mark unsupported | none | Kotlin | CPU | negligible | script ranges + small lexicons | none | **[V]** 0.844 on fixtures | project | High | MVP |
| OCR Latin | ML Kit Text Recognition v2, **bundled** | Screenshot text + regions | about 4 MB per script per ABI **[D]** | ML Kit SDK | SDK-chosen | Unknown; Target warm <= 2 s per screen, app PSS <= 512 MiB | Latin scripts (covers English, Manglish, Hinglish) | none | Publisher only; Sakshi **[U]** | Google ML Kit terms (not open source) | High **[D]**; no-`INTERNET` operation **[P]** | MVP |
| OCR Devanagari | ML Kit bundled Devanagari | Hindi script | about 4 MB **[D]** | same | same | Unknown | Hindi, Marathi | none | **[U]** | ML Kit terms | High **[D]** | Phase 2 |
| OCR Malayalam | Tesseract 5 + `tessdata_fast` `mal` (5.03 MiB **[D]**) | Malayalam script | LSTM pack | C++/Leptonica via JNI | CPU | Unknown | `mal` | none | **[U]**; ML Kit does not cover Malayalam **[D]** | Apache-2.0 | Conditional **[P]** (JNI, 16 KB, reading order) | Phase 2 |
| STT | whisper.cpp, multilingual **base**, q5_1 | Short audio to text with time ranges | 74 M params; unquantised base 142 MiB disk, about 388 MB memory **[D]** publisher estimate | GGML, NDK/JNI | Arm CPU; Vulkan untested | Unknown on device; Target RTF <= 1 on reference phone, app PSS <= 700 MiB | Whisper multilingual; Malayalam/Hindi quality **[U]** | none | **[E]** laptop faster-whisper base smoke run only, which is not evidence for whisper.cpp q5_1 | MIT (code and weights) | Feasible **[D]**, measured **[P]** | Phase 2 |
| Narrow toxicity (experimental) | `minuva/MiniLMv2-toxic-jigsaw-onnx` rev `c035f27b...`, sha256 `bcd9dfb4...` | Six English toxicity signals, review-only | about 23 M; 22.86 MB INT8 | ONNX, ONNX Runtime Android | CPU | **[E]** Linux p50 6 ms at 128 tokens, RSS about 201 MiB incl. Python; Android **[U]** | English only | uncalibrated | **[E]** probe: misses conditional photo-exposure threat (0.0007), fails negation | model card licence to be re-verified at pin time | Feasible **[A]**; tokenizer parity **[P]** | Phase 2, flagged experimental |
| Expressed emotion (optional) | `minuva/MiniLMv2-goemotions-v2-onnx` rev `4fea72b9...` | 28 emotion sigmoids; never a gate | about 30 M; 30.46 MB | ONNX / ORT | CPU | **[E]** Linux only | English only | uncalibrated; intensity null | **[E]** | to verify | Feasible **[A]** | Future |
| Multilingual classifier | `microsoft/Multilingual-MiniLM-L12-H384` + multi-label head, fine-tuned | Behaviour categories in en/hi/ml + romanised | about 117 M; arm64 INT8 about 113 MiB **[D]** | ONNX int8 / ORT | CPU | **[U]** | pretraining coverage only; task quality **[U]** | required per language after export | none | MIT (backbone); training data rights gate | **[P]** | Future, data-gated |
| Local LLM | none in MVP. Candidates recorded: Qwen2.5-1.5B Q4_K_M (llama.cpp), Qwen3-0.6B (LiteRT-LM), Qwen3.5-2B Q4_K_M | Paraphrase of validated pattern packets | 0.6-2 B | GGUF / .litertlm | CPU, optional GPU | Published third-party phone numbers only, e.g. Qwen3.5-2B Q4_K_M 2.5 GB peak, 6.8 s for 128/128 on a vivo X300 **[D]** | claimed, unvalidated | none | none for Sakshi | Apache-2.0 | **[F]** | Deferred |
| Laya | not used | - | 322 M / 421 M | - | - | - | - | ships uncalibrated **[D]** | none for harassment | Apache-2.0 | **[F]** | Deferred |
| Temporal engine | Pure Kotlin | Counts, episodes, boundary recurrence, transitions | none | Kotlin | CPU | Target p95 < 50 ms per update on a 1,000-event case | language independent | not applicable (no probabilities) | Fixture tests A-F | project | High | MVP |

Desktop benchmark is not an Android benchmark: every "Unknown on device" cell is filled only by section 28 measurements.

### 15.2 Signal states (must all exist as distinct values)

`not_evaluated, unsupported_language, extraction_uncertain, context_missing, uncalibrated, model_unavailable, resource_deferred, cancelled, invalid_output, suggestion, pending_review, confirmed, rejected, edited`. Missing is not neutral and not benign. Busy, timeout and parse failure are never collapsed into an empty success.

### 15.3 False positives, false negatives, abstention

- False positives: every finding is a suggestion until reviewed; reject and edit are first-class; rejected findings leave patterns; rules show the exact matched cue and span; no automatic action exists to be wrong about.
- False negatives: the user can tag any event manually regardless of model output; temporal rules for density and after-boundary recurrence do not depend on any label; the UI never says "no harassment found", only "no cue matched in the reviewed text".
- Abstention: unsupported script or language, low OCR quality, truncated text and missing context produce an explicit state and a manual-review prompt.
- Hallucination: no generative model in MVP. Templates render counts and dates from structured data only. If an LLM is ever added, its output is validated against supplied anchors (IDs exist in the case, quotes are exact substrings, numerals match measurements) and falls back to the template on any failure.
- Prompt injection: imported text is data. No component executes instructions, fetches links or calls tools based on evidence content.

## 16. Multilingual Strategy

Scope: English, Malayalam, Hindi, plus Manglish, Hinglish, native scripts, romanised text, slang and code mixing among these (`AGENTS.md`, user decision 2 Oct 2026). Nothing else is claimed.

| Slice | Text input | OCR | STT | Signals in MVP | Support label shown to user |
|---|---|---|---|---|---|
| English | yes | ML Kit Latin | whisper (Phase 2) | rules cues | Experimental |
| Hindi (Devanagari) | yes | ML Kit Devanagari (Phase 2) | whisper (Phase 2) | rules cues after native review | Experimental |
| Hinglish (Latin) | yes | ML Kit Latin | whisper, quality unknown | rules cues after native review | Experimental |
| Malayalam (script) | yes | Tesseract `mal` (Phase 2) | whisper, quality unknown | rules cues after native review | Experimental |
| Manglish (Latin) | yes | ML Kit Latin | whisper, quality unknown | rules cues after native review | Experimental |
| Mixed / code-switched | yes | per-script | unknown | rules cues; often abstain | Experimental |
| Anything else | preserved | not attempted unless Latin | not claimed | none; `unsupported_language` | Preserved, not analysed |

Rules:

- Preserve the original text exactly. No automatic transliteration, translation or spelling repair before analysis (`RECOMMENDATION.md`, sentiment sec 13). A normalised view is an optional, named derivative with a source map.
- ASCII is not English. Script detection alone cannot identify Manglish or Hinglish; the hint is a hint and is user-correctable.
- No English model is ever run on Indic input to produce a confident result.
- Per-language results are reported separately; no pooled score hides a weak language.
- Cue lists and every synthetic label need native-speaker review before real-world use (`AGENTS.md` project verification). Until reviewed, a cue list ships disabled for that language.

## 17. Temporal Pattern Engine

Core capability, implemented in `:core:temporal` as pure Kotlin from `temporal-harassment-patterns.md` sections 5-8.

### 17.1 Pipeline

```text
event revisions (eligible at knowledge cutoff)
  -> ContactCanonicalizer: representation equivalence, possible-duplicate groups
  -> CaseLinkResolver: actor and conversation scope from reviewed associations only
  -> TemporalProjection: interval partial order, coverage ledger, windows, episodes
  -> PatternReducer: explicit rules -> typed pattern records
  -> PatternExplanation: template sentences from measurements
```

### 17.2 Rules of counting

- A **contact unit** is one distinct incoming `message_observation` or `contact_attempt_observation`. Boundaries, notes, lifecycle records, quoted or reported acts and outgoing messages never count.
- One event with three labels is one contact. A screenshot plus an export row of the same message is one contact once that equivalence is source-supported or user-reviewed; until then it is a possible duplicate.
- Identical text at different source IDs or times stays as separate contacts. Hash or text equality alone never merges.
- Counts are reported as bounds: lower bound collapses each unresolved duplicate group to one; upper bound counts all. Shown as "4 to 5 retained contacts" when they differ.
- Time is a partial order: A is before B only if `A.latest < B.earliest`. Overlap means order unknown; display uses a deterministic tiebreak but pattern eligibility respects the uncertainty.
- Source-claim times and collector times are not compared directly. Monotonic differences are used only inside one collector session.
- Two clocks per event: event time and knowledge time (`available_at`). A late import changes retrospective analysis and never rewrites when something was detected.
- Timezone, DST and clock-change fixtures are part of the test suite.

### 17.3 Coverage and gaps

`coverage_gap` rows and per-event `coverage` drive three behaviours: gaps are shaded on the timeline; comparable-rate and "no contact" statements are disabled across a gap; and the wording distinguishes **"no evidence observed in the selected records"** from "no harassment occurred". The engine has no output that means the latter. Rule results are one of `candidate`, `supported_description`, `insufficient_context`, `not_observed`; "not observed" is rendered with its scope and never as reassurance.

### 17.4 MVP pattern cards

| Pattern type | Inputs | Rule (demo defaults, **not validated thresholds**, all configurable and versioned) | Example wording |
|---|---|---|---|
| `repeated_contact` | distinct contacts in actor scope | Count and span in fixed windows (10 min, 1 h, 1 day, 7 days) | "Six distinct retained incoming observations linked to Person A between 09:05 and 09:40" |
| `recurrence_after_boundary` | reviewed boundary + later contacts | >= 3 contacts after the boundary within one day; wording depends on `communication_status` | "after your selected stop-contact message" versus "after your disengagement note" |
| `wording_transition` | **accepted** tags only | earlier `verbal_abuse`, later `explicit_threat`, same actor scope, within 7 days | "The selected sequence moves from insults to a statement of harm" |
| `density_change` | comparable windows, no gap between them | current hour >= 6 and >= 3x the preceding non-zero hour | "12 retained observations versus 3 in the preceding comparable hour" - a review cue, not a determination |

Also tracked without a card in MVP: episodes by inactivity gap, category transitions matrix, unique contact days. Deferred: EWMA/CUSUM, PELT, BOCPD, Kleinberg, Hawkes, sequence models, graph networks.

### 17.5 What the engine never outputs

No single escalation scalar, no probability for a pattern (two 0.9 scores are not an 81 percent pattern), no future prediction, no intent, no identity claim across apps, no "stalker" or offence names.

## 18. Human Review System

### 18.1 Three review targets (sentiment sec 15)

1. **Evidence association:** sender, direction, time, duplicate relationship, which case.
2. **Signal:** each proposed category on an event.
3. **Pattern and explanation:** the pattern card, its wording, its supporting set.

Confirming preservation of an item is not accepting its AI tags (`user_confirmation.scope`).

### 18.2 Flow

```text
Finding (unreviewed)
  -> evidence preview with the anchored span highlighted in the original/derivative
  -> explanation: which rule or model, which version, what kind of score, what is unknown
  -> Accept | Reject (with reason code) | Edit label or span | Add context | Mark unknown
  -> review_decision row (new revision); original finding and evidence untouched
```

Reject reason codes: wrong sender or quote, extraction error, duplicate, insufficient context, signal absent. A rejection never labels the source text harmless and is never used as a training label.

### 18.3 Dependency-aware recomputation

On any of: evidence deleted, derivative edited, actor association changed, finding decision changed, boundary changed, duplicate status changed, link reviewed, retention expiry -

1. In the same transaction, `ProvenanceGraph.dependentsOf` marks affected patterns `stale` and affected report drafts stale.
2. The reducer recomputes the affected case scope (idempotent; replay from event revisions).
3. Exported snapshots are never altered; if their dependencies changed they are shown as "superseded" with a link to what changed.
4. Export is blocked while any included pattern is stale.

### 18.4 Epistemic status in the UI

Five visual treatments (the four `AGENTS.md` statuses plus User-reported) that are never mixed, each with a text label (not colour alone):

- **Observed** - text or media present in a preserved artefact. Shown as a quote with a source link.
- **User-reported** - the user's own statement. Shown as theirs, with when it was written.
- **Inferred** - rule or model suggestion. Shown with basis, version and "suggestion" wording.
- **Pattern** - supported by multiple events. Shown with the count bounds and the event list.
- **Unknown** - explicitly listed, not hidden.

The same enum drives the database (`finding.epistemic_status`, pattern type), report sections and export JSON, so an inference cannot be rendered as an observation by a UI mistake: the composables take the status as a required parameter and there is no default.

## 19. Search / Retrieval

MVP: case-scoped lexical search over text derivatives and user notes, implemented as a bounded in-memory scan after decryption (cases are small; no plaintext FTS index is written). Filters: case, date range, actor, review state, source kind. Two clearly labelled scopes: "all preserved text" and "accepted findings only".

Counts are always computed by the database or temporal engine, never from search hits. No cross-case results. Semantic retrieval (multilingual-E5-small or similar) is Future and needs a measured context-recall gain plus encrypted vectors and deletion propagation.

## 20. Reporting

### 20.1 Report content

1. Cover: case title chosen by the user, report version, generation time (device clock, stated as such), Sakshi version, rule and model versions.
2. Scope statement: what was selected, what was excluded, acquisition methods, known gaps.
3. Timeline: events in display order with time basis and precision, sender label and its identity basis, direction, source kind.
4. For each included event, clearly separated blocks:
   - **Observed evidence:** verbatim quote or media reference, artefact ID, SHA-256, locator.
   - **User statement / correction:** as written, with time.
   - **Inferred finding:** label, basis, producer version, confidence semantics, review status.
5. **Temporal patterns:** observed measurements, interpretation sentence, limitations, supporting event IDs.
6. **Unknown / insufficient evidence:** listed explicitly (sender unverified, outgoing coverage unknown, gaps, unsupported language items).
7. Integrity appendix: manifest hash, Merkle root, signer key fingerprint, audit chain head, verification instructions, and the limits statement in 21.4.

### 20.2 Rules

- Only user-confirmed items are exportable (port of `bench/pipeline.py: export_allowed`); an empty selection cannot be exported.
- Dates and counts are rendered from structured data.
- A `ForbiddenPhraseGuard` test fails the build if templates contain offence names, "guilty", "proves", "admissible", "stalker", "danger score" or similar.
- Rendering: `android.graphics.pdf.PdfDocument` with bundled Noto Sans, Noto Sans Devanagari and Noto Sans Malayalam (SIL OFL 1.1). Complex-script shaping goes through Android text layout (`StaticLayout`), and each script needs a native-speaker visual check before export is offered for that script **[P]**. Fallback: export verbatim quotes as images of the on-screen text layout.
- The report PDF is a derivative. It is never described as the evidence itself.

## 21. Export / Verification

### 21.1 Bundle layout

```text
case-export-<snapshot-id>/
+-- manifest.json            canonical; lists every file with sha256, size, role
+-- manifest.sig             signature over the manifest bytes
+-- signer.json              public key, key id (sha256 of key), algorithm
+-- events.jsonl             one schema-valid event per line (urn:sakshi:event:1)
+-- findings.json
+-- corrections.json
+-- patterns.json
+-- provenance.json          nodes and edges for the included subset
+-- evidence/                selected originals (only if the user includes them)
+-- derivatives/             selected OCR/transcript/parsed text, redacted copies
+-- report.pdf
+-- verification/README.txt  how to verify, what verification does and does not show
```

### 21.2 Canonical form and hashing

- JSON canonicalisation: UTF-8, object keys sorted by code point, no insignificant whitespace, integers only where possible (timestamps as RFC 3339 strings), following RFC 8785 rules. Implemented once in `:core:integrity` and shared by app and verifier.
- File hashes: SHA-256.
- Manifest root: count-bound Merkle v2 over the sorted file-hash list, exactly as `bench/adapters.py: merkle` (leaf prefix `0x00`, node prefix `0x01`, root = SHA-256(`0x02` || count as 8-byte big-endian || top)). Python-generated vectors are checked in as test fixtures so Kotlin and Python agree.
- Signature: ECDSA P-256 with SHA-256 from an Android Keystore key generated on device (StrongBox when available). The bench measured Ed25519; Keystore Ed25519 needs API 33+, so P-256 is used for `minSdk` 26 (Decision D-09).

### 21.3 Verification procedure (`:export:verifier`, pure JVM, CLI and library)

1. Parse `manifest.json`; recompute canonical bytes; verify signature with the key in `signer.json`; print the key fingerprint.
2. Recompute each file hash; recompute the Merkle root; compare.
3. Validate every event against the bundled schema; resolve every anchor to an included artefact or to an explicit "omitted" entry.
4. Check graph invariants: no finding without an anchor, no pattern citing an absent event, no cross-case reference.
5. Report: verified / failed (which file or edge) / unverifiable-because-omitted.

### 21.4 Limits, printed in the report and by the verifier

```text
hash       != authenticity      (it shows bytes are unchanged since Sakshi stored them)
signature  != identity          (it shows which device key signed; compare the fingerprint out of band)
integrity  != truth
integrity  != legal admissibility
device clock != trusted time
```

A bundle with only redacted derivatives verifies those bytes and the signed lineage statement; it cannot verify hidden originals or that redaction was correct.

### 21.5 Redaction and leakage

- Redaction creates a new derivative with opaque raster replacement; never blur or overlay.
- EXIF/XMP, embedded thumbnails and filenames are stripped or replaced in exported copies; exported file names are opaque IDs.
- Hashes of short guessable texts are not published for omitted items; omitted items appear as a count, not as a hash list.
- A warning is shown if an included original contains content that was redacted elsewhere in the same bundle.
- Sharing uses a `FileProvider` URI and the system share sheet. The app never chooses a recipient.

### 21.6 Key management for export

Signing key: non-exportable, device-bound, no user-auth-per-use requirement but only usable while the app is unlocked. Key loss (uninstall, reset) means old bundles still verify (public key is in the bundle) but no new bundles can be signed with that identity; stated in settings and onboarding.

## 22. Security Architecture

### 22.1 Threat model

| In scope (designed against) | Out of scope (stated limits) |
|---|---|
| Another app reading Sakshi files | Rooted or fully compromised OS, malicious accessibility service, kernel exploit |
| Lost or stolen locked phone | Coerced unlock by someone with the user's credential |
| Cloud backup or device transfer copying evidence | Screen observation or shoulder surfing while unlocked |
| Malicious or malformed imported files | Forensic recovery of flash blocks after deletion |
| Evidence leaking through logs, crash reports, temp files, thumbnails, recents | Copies that remain in the source app, gallery, recorder or cloud provider |
| Tampering with stored blobs or database | Rollback of the whole vault to an older valid state on a compromised device |
| Evidence text trying to steer analysis | Source authenticity: an import may already be forged |

On a compromised, unlocked device the app process can use its keys; Keystore prevents key extraction, not key use. This is said plainly in onboarding and the privacy screen.

### 22.2 Keys

| Key | Type | Protection | Use |
|---|---|---|---|
| `K_master` | AES-256-GCM, Android Keystore, non-exportable, StrongBox if available | User authentication required (biometric or device credential) with a validity window while the app is in an unlocked session | Wraps `K_db` and per-blob keys |
| `K_db` | 32 random bytes | Stored only wrapped by `K_master` | SQLCipher passphrase |
| `K_blob[i]` | AES-256 per evidence blob | Wrapped by `K_master`, stored in `evidence_blob.wrapped_key` | Chunked blob encryption; deleting the row crypto-erases the blob |
| `K_sign` | EC P-256, Keystore | Usable in unlocked session | Export signing |
| `K_ingress` (Phase 2b, opt-in only) | AES-256, Keystore, no per-use auth | Separate, disclosed weaker mode | Encrypting expiring notification candidates while locked |

Strict session mode is the default: if `K_master` is unavailable (locked, before first unlock, invalidated) nothing is written and work is deferred. There is no plaintext spool. Biometric enrolment changes can invalidate keys **[P]**; the behaviour chosen and tested is recorded in validation V-05. Argon2id (bench: 64 MiB, t=3) is used only for the Phase 2 recovery bundle secret, not for daily unlock.

### 22.3 Blob envelope

Versioned header (magic, version, chunk size 64 KiB, plaintext length, blob ID) authenticated as AAD; each chunk encrypted with AES-256-GCM, nonce = 4 random bytes prefix stored in header + 8-byte chunk counter, AAD = blob ID || chunk index || final-chunk flag. This binds order, index and length and prevents truncation and reordering. Random access decrypts and authenticates one chunk at a time. The exact construction is reviewed and tested against tamper, swap, truncation and extension cases before any other module depends on it.

### 22.4 Controls

| Area | Control |
|---|---|
| App authentication | BiometricPrompt with device-credential fallback on launch and after a timeout; lock on background |
| Encryption at rest | SQLCipher database; chunked AES-GCM blobs; no evidence in SharedPreferences or DataStore |
| Secure deletion | Destroy wrapped key (crypto-erase), overwrite-then-unlink best effort, remove derivatives, anchors, findings, jobs, search state; UI states that physical erasure on flash is not guaranteed |
| Logs | A `SafeLog` wrapper that accepts only IDs, enum states and numbers; lint rule forbids `android.util.Log` with string interpolation in non-test code; release build strips debug logging |
| Crash reporting / analytics | None. No third-party SDKs with network access |
| Backup | `allowBackup=false`, exclusion rules for cloud, D2D and cross-platform, `noBackupFilesDir`; verified on device (V-09) |
| Screens | `FLAG_SECURE`; recents thumbnail hidden; own notifications are neutral (no sender, text or category) |
| Export | Only via explicit user action; files written to an app cache directory that is wiped after share and on next launch; `FileProvider` with per-share grants |
| Import validation | Section 13.1; MIME sniffing of magic bytes; declared and detected MIME stored separately |
| Malicious files | Bitmap bounds decoded before allocation; pixel cap; zip entry count, size, ratio, nesting caps; reject `..`, absolute paths and symlinks; HTML never rendered, scripts never executed; XML external entities disabled |
| Parser isolation | PDF rendering (Phase 2) in an isolated, minimally privileged service process as Android documentation recommends; other parsers are bounded pure-Kotlin or platform decoders |
| Integrity | GCM tags on every read; SHA-256 re-verified before export and on demand; audit chain verification on demand |
| Corrupted evidence | Tag failure gives `unavailable (integrity failure)`, the record is kept and flagged, nothing is silently dropped or replaced |
| Path traversal | App-generated UUID file names only; supplied names are stored as claims, never used as paths |
| Prompt injection | Evidence is never concatenated into instructions; no tools, no network, no actions |
| Model assets | Bundled in the APK or imported through an explicit provisioning screen; SHA-256 pinned in `model_version`; refuse to load on mismatch |
| Supply chain | Version catalog with pinned versions, Gradle dependency verification metadata, no dynamic versions |

## 23. Privacy Architecture

- **Data minimisation:** only what the user saves. No contacts, location, device identifiers or usage analytics. Notification lane discards non-allowlisted packages before reading content and keeps ordinary text in RAM only.
- **Purpose separation:** distinct consents for (1) saving selected evidence, (2) local analysis, (3) notification observation, (4) candidate retention, (5) lock-screen preview analysis, (6) export. None implies another.
- **Third-party data:** evidence contains other people's messages. The app does not enrich, look up, or contact anyone. Redaction tools are offered at export.
- **Retention:** user-chosen per case; candidate inbox expiry is logical (24 h Target) with opportunistic physical cleanup.
- **Deletion:** per item, per case, whole vault; propagates to derivatives, findings, patterns, jobs, search state.
- **Disclosures shown in-app before use:** what is stored and where; that analysis is local; that hashes do not prove authenticity; what cannot be protected on a compromised or unlocked device; that key loss or uninstall destroys access; that notification access is broad at OS level and filtered by Sakshi; that ML Kit is a Google SDK that can send usage metrics when a network permission exists (not applicable to the no-`INTERNET` MVP build, verified in V-08); that exported files are outside Sakshi's protection.
- **User safety:** neutral app notifications off by default; no content on the lock screen; no feature claims invisibility; no disguise or panic wipe in MVP.

## 24. Legal / Regulatory / Platform Constraints

This section is engineering guidance. It is not legal advice. Three kinds of statement are kept apart.

### 24.1 India

| Topic | Verified law or policy text | Engineering interpretation | Open question for counsel |
|---|---|---|---|
| DPDP Act 2023 and DPDP Rules 2025 | Rules notified 13 Nov 2025 with staggered commencement; Board provisions in force immediately; core obligations (notice, consent, children's data, rights) effective 18 months later, about 13 May 2027 **[D]** | A local-only app with no vendor-side processing minimises the vendor's role; any future telemetry, support upload, research donation or cloud feature changes that | Whether the personal/domestic exemption covers a user's processing of third parties' messages in this app; vendor obligations if any data ever reaches the vendor; children's data handling |
| IT Act 2000 ss. 43, 66 | Address unauthorised access and copying, and the dishonest/fraudulent criminal context **[D]** | Reinforces the rejected list: no access to other apps' data beyond documented, user-granted surfaces | Whether notification retention of third-party message excerpts raises any issue |
| Bharatiya Sakshya Adhiniyam 2023 s. 63 | Governs admissibility of electronic records, including a certificate requirement **[D]** | Sakshi preserves originals, hashes and derivation records to **support** later integrity comparison. It does not produce a s. 63 certificate and claims no admissibility | What export contents help a lawyer most; whether the report should carry any specific wording |
| Child safety (POCSO and related) | Mandatory-reporting and harmful-content obligations exist **[D]** | MVP is positioned for adults; no automatic reporting; no intimate imagery in any research corpus | Age gating, what the app should do if a minor uses it, obligations on discovering CSAM |
| Recording and interception | Jurisdiction specific | The app does not record calls, screens or microphone. Imported recordings are the user's responsibility; a notice says so | Wording of that notice |

### 24.2 Android and Google Play

| Area | Verified constraint **[D]** | Plan |
|---|---|---|
| Target API | New apps and updates must target API 36 from 31 Aug 2026 | `targetSdk 36` |
| 16 KB page size | Required for apps with native code targeting Android 15+ since 1 Nov 2025 | Verify SQLCipher, Tesseract, whisper.cpp, ORT native libs; AGP 8.5.1+ and NDK r28+ |
| `NotificationListenerService` | Special access granted in system settings; Play User Data and Spyware policies require prominent disclosure, affirmative consent, expected use, no unexpected transmission | Phase 2b only; disclosure screen; no network; allowlist; pause and revoke |
| AccessibilityService | Declaration and approval needed; not an accessibility tool | **Not used** |
| Sensitive permissions (SMS, call log, all-files, broad media) | Restricted; SMS needs default-handler or approved exception; NLS must not be used as a workaround to derive SMS data | **None requested**; SMS apps are excluded from the default notification allowlist |
| Storage | Photo Picker and SAF preferred over broad permissions | Used exclusively |
| Screen capture / MediaProjection | Per-session consent, FGS type, secure windows not capturable | **Not used** |
| Microphone | Runtime permission | **Not requested**; imported audio needs none |
| Background processing | WorkManager quotas (tighter on Android 16); FGS types must match real use | Chunked, resumable, user-initiated work; no FGS in MVP |
| User disclosure and Data safety | Local-only processing may not count as "collection", but in-app disclosure and a privacy policy are still required | Privacy policy and Data safety form prepared from the actual build, reviewed before any Play submission |
| Monitoring / stalkerware | Apps that monitor another person covertly are prohibited | Sakshi analyses only the device owner's own imports and notifications, with persistent visible controls and no remote access |
| Sideload distribution | Play Protect can block sideloaded apps using sensitive permissions in some markets | Hackathon demo by `adb install`; never instruct users to disable protections |

### 24.3 Hard boundaries (restated as tests)

No WhatsApp or other private database access; no key extraction; no View Once or disappearing-message bypass; no sandbox bypass; no unauthorised access to another app; no covert recording; no covert monitoring; no automatic uploads; no automatic accusations; no admissibility claims. Each has a guardrail check in section 27.3 (manifest permission allowlist test, forbidden-API lint, forbidden-phrase test).

Release requires counsel review of the actual data flow and distribution review of the actual build (acquisition AC12). Technical operation is not treated as approval.

## 25. Dataset / Model / Dependency Licensing

Files to create before any public release: `LICENSE` (project licence - **undecided**, Open Question Q1), `THIRD_PARTY_NOTICES`, `DATA_LICENSES.md`, `MODEL_LICENSES.md`. Unclear terms are a blocker, not permission.

### 25.1 Software

| Dependency | Licence (as recorded by research; re-verify at pin time) | Notes |
|---|---|---|
| Kotlin, AndroidX, Compose, Room, WorkManager, Biometric | Apache-2.0 | |
| SQLCipher for Android (community) | BSD-style | Confirm exact artefact and 16 KB support |
| ML Kit Text Recognition | Google ML Kit Terms | Not open source; disclosure of SDK metrics behaviour |
| Tesseract, Leptonica, tessdata_fast | Apache-2.0 / BSD-2-Clause / Apache-2.0 | JNI wrapper licence to check |
| whisper.cpp | MIT | |
| ONNX Runtime Android | MIT | |
| Noto fonts | SIL OFL 1.1 | Bundle licence text |
| Python bench stack (numpy, scikit-learn, RapidOCR, faster-whisper, ReportLab, PyCryptodome, etc.) | Mixed permissive | Development only; not shipped |

### 25.2 Models

| Model | Code / weights licence | Redistribution in APK | Restrictions and actions |
|---|---|---|---|
| Whisper base (GGML) | MIT / MIT | Allowed with notice | Attribute OpenAI Whisper |
| ML Kit bundled models | ML Kit Terms | Via SDK only | Follow terms |
| tessdata_fast `mal`, `hin`, `eng` | Apache-2.0 | Allowed with notice | |
| minuva MiniLMv2 toxicity / GoEmotions ONNX | Verify model card licence and training-data terms (Jigsaw annotations CC0, Wikipedia text CC BY-SA; GoEmotions Apache-2.0) | **Not cleared yet** | Blocker for shipping the experimental pack until verified |
| Multilingual-MiniLM-L12-H384 | MIT | Allowed | A fine-tuned head inherits training-data constraints |
| Qwen family | Apache-2.0 | Deferred | |
| Laya | Apache-2.0 per card | Deferred | Pin and verify if ever revisited |
| Gemma 3 / 3n, Llama, LFM, MobileLLM | Custom terms, some non-commercial | Not planned | Listed so nobody adds them casually |

### 25.3 Datasets

| Dataset | Source / platform | Licence or terms | Real or synthetic | Use | Ship in product? |
|---|---|---|---|---|---|
| `data/text.jsonl`, `extraction.jsonl`, `screenshots.jsonl` | Authored in this repo | Project | **Synthetic** | Regression fixtures | Test assets only, labelled synthetic |
| `data/audio.jsonl` (FLEURS) | Google FLEURS | CC-BY-4.0, attribution recorded | Real read speech | STT smoke tests | No (audio not committed) |
| `data/labeled_data.csv` (Davidson 2017) | Twitter | Repo MIT; tweet rights separate | Real public tweets | English auxiliary baseline, smoke tests | **No** |
| DravidianCodeMix | YouTube comments | CC BY 4.0 (Zenodo) | Real | Candidate Malayalam-English training | Model only, after rights review |
| Uli | Twitter | CC BY 4.0 per repo LICENSE (website says otherwise; archive the artefact) | Real | Candidate Hindi/English gendered-abuse data | Model only, after review |
| CAD | Reddit | CC BY 4.0 | Real | Context and span annotation | Model only, after review |
| HASOC / ICHCL | Twitter | Licence not established; password-protected | Real | Research only until terms obtained | No |
| MACD | ShareChat | CC BY-NC-SA, research only | Real | Research only | **No** (non-commercial) |
| Jigsaw, HateXplain | Wikipedia / Twitter, Gab | CC0 + CC BY-SA text; HateXplain licence conflict (MIT vs CC BY) | Real | Auxiliary English | Resolve before use |
| THREAT, SafeCity, AMiCA, TRAC | various | Academic-only or permission required | Real | Not used without permission | No |
| HateCheck, MHC Hindi | Authored | CC BY 4.0 | Synthetic/templated | Functional diagnostics only | No |
| Future Sakshi-Real | Consented donations | To be defined | Real | Needs ethics, DPIA, legal clearance first | No |
| Future Sakshi-Authored / Synthetic / Fixtures | Project | Project | Fictional / LLM / staged, labelled as such | Tests, demos | Test assets only |

Rules: synthetic never becomes "real" through editing; a model trained on restricted data is restricted; public availability is not consent; research datasets in this repo are not assumed safe to redistribute.

## 26. Performance Budgets

Every number is a **Target** unless marked otherwise. Device measurements replace them in `benchmark.md` as they are taken.

| Component | Cold start / load | Warm latency | Peak RAM (app PSS) | Storage | Battery / thermal | Throughput | Status |
|---|---|---|---|---|---|---|---|
| App launch to unlock prompt | <= 1.5 s | - | <= 200 MiB | APK <= 60 MB without STT | - | - | Target |
| Import (copy + hash + encrypt) | - | >= 20 MB/s | bounded buffers <= 16 MiB | 1.0x input + header | Unknown | - | Target. Laptop: 10,000 small rows 1.01 s **Measured (laptop)** |
| SQLCipher open | <= 500 ms | - | - | - | - | - | Target |
| WhatsApp parse | - | 10,000 records <= 2 s | <= 64 MiB | - | - | - | Target. Laptop fixture 0.006 ms **Measured (laptop)**, not comparable |
| Rules signals | none | <= 5 ms per message | negligible | KB | negligible | - | Target. Laptop 0.05 ms **Measured (laptop)** |
| ML Kit Latin OCR | Unknown | <= 2 s per one-screen image | <= 512 MiB | about 4 MB per script **(publisher)** | Unknown | 1 image at a time | Target |
| Tesseract `mal` | Unknown | <= 5 s per page (UX goal) | Unknown | about 5 MiB pack + libs | Unknown | - | Target, **Unknown** feasibility |
| whisper.cpp base q5_1 | Unknown | RTF <= 1.0 on reference phone | <= 700 MiB | Estimated 60-150 MB | Unknown; pause on thermal status >= SEVERE | <= 60 s clips, one at a time | Target. Publisher memory about 388 MB for unquantised base **(Estimated)** |
| Temporal reducer | none | p95 < 50 ms per update at 1,000 events; full replay of 10,000 events without loading media | <= 32 MiB | - | negligible | - | Target |
| PDF report | - | 50 incidents <= 3 s | <= 128 MiB | - | - | - | Target. Laptop ReportLab 0.12 s **Measured (laptop)**, different renderer |
| Export bundle + verify | - | 100 files <= 5 s | bounded | - | - | - | Target |
| NLS callback (Phase 2b) | - | p95 < 5 ms on main thread | queue <= 2 MiB | - | no wake locks, no polling | 64-snapshot queue | Target |
| Any classifier (Future) | Unknown | p95 <= 300 ms at 128 tokens | incremental <= 300 MiB | 23-113 MB | Unknown | serial | Target |
| Hard gate | - | - | No single stage above 2.5 GB (bench scoring gate) | - | - | - | Gate |

Battery and thermal: no estimate is given. They are measured as energy per job with an idle baseline, or stay Unknown.

## 27. Testing Strategy

### 27.1 Unit tests (JVM, no device)

| Area | Tests |
|---|---|
| Event model | Schema round trip against `data/sakshi-event-schema.json` (date-time format checking on); the six malformed variants from `research/verification/` rejected; all ten application invariants |
| Spans | Code point <-> UTF-16 conversion with emoji, ZWJ sequences, Malayalam and Devanagari combining marks; empty, inverted and out-of-range spans rejected |
| Integrity | SHA-256 known vectors; chain and Merkle v2 equal to Python-generated vectors from `bench/adapters.py`; empty and single-entry boundary; tamper, reorder, truncation, duplicate-padding all detected |
| Canonical JSON | Key ordering, Unicode, number forms; byte-identical output across runs |
| WhatsApp parser | Bench fixture; multiline messages; sender containing a colon; 12 h and 24 h forms; ambiguous `dd/mm` vs `mm/dd` flagged for review, never guessed; system lines; missing-media placeholders |
| Rules engine | Parity with bench `rule_scores` on `data/text.jsonl` test split; spans returned; case folding; no match on quoted negation fixtures is **not** asserted (known weakness, recorded) |
| Temporal | Fixtures A-F from temporal report sec 10; all sixteen metamorphic tests of sec 13.4; partial order; count bounds; DST and timezone shifts; idempotent replay; import-order independence |
| Review / invalidation | Reject one finding and the pattern changes; delete an anchor and dependants go stale; old snapshot unchanged |
| Label mapping | Every bench label maps or is explicitly unmapped; missing label is not negative |
| Report templates | Forbidden-phrase guard; every sentence has an evidence reference; numerals come from measurements |
| Verifier | Valid bundle passes; one flipped byte in any file fails naming the file; removed edge fails; substituted signer key reported as a different fingerprint |

### 27.2 Integration tests (instrumented)

import -> storage (hash stable, blob decrypts, provenance row); import -> OCR -> derivative with regions; import -> rules -> finding with resolvable anchor; finding -> review decision; correction -> recomputation; report -> export bundle; export -> verifier on the host JVM; Room migrations; process-death recovery of the job queue; share intent with `onNewIntent` repeat does not duplicate.

### 27.3 Security tests

Path traversal names; zip bomb, nested zip, symlink entry; decompression-bomb image; truncated and corrupted media; wrong MIME; forged share intent without grant; revoked URI mid-copy; database file byte-flipped (open fails closed); blob chunk flipped, swapped, truncated, extended; database opened without key; key invalidated; wrong user authentication; disk full during import (no partial evidence row); grep of app data directory, logcat and WorkManager database for fixture strings after a full workflow (must find none in plaintext); backup extraction via `adb backup`/D2D shows no evidence; prompt-injection strings in imported text change nothing; manifest permission allowlist test; lint for forbidden APIs (`AccessibilityService`, `MediaProjection`, `READ_SMS`, `MANAGE_EXTERNAL_STORAGE`, `java.net`, `HttpURLConnection`, OkHttp).

### 27.4 ML tests

Per-language and per-slice false positives and false negatives on held-out fixtures and, when available, native-reviewed data; calibration (ECE, Brier, reliability) only for models that claim probabilities; abstention on unsupported script and language; code-mixed and romanised slices; OCR-damaged and STT-damaged text; obfuscation (leet, fullwidth, zero-width); quote, report and negation cases; ambiguity ("Fine."); benign anger; polite coercion; model missing, wrong hash, NaN output, timeout. Synthetic-fixture scores are reported as fixture regression, never as accuracy.

### 27.5 Android tests

Permission denial and revocation (biometric, later notification access); backgrounding mid-import and mid-OCR; process death; low memory; rotation and configuration change during review; device reboot before first unlock; storage exhaustion; battery saver; (Phase 2b) listener disabled, disconnected, force-stopped, reconnect snapshot, queue overflow, locked device policy.

### 27.6 Existing Python suite

`python -m unittest discover -s bench/tests -v` keeps passing; it is the oracle for the shared vectors. Not rewritten.

## 28. Android Device Validation

Nothing below is claimed until run on a physical phone with model, build, patch level, app version, battery and thermal state recorded; one warm-up and five repeats minimum; results appended to `benchmark.md`.

| ID | Assumption to validate | Method | Pass condition |
|---|---|---|---|
| V-01 | Share target receives text, single and multiple streams from WhatsApp, gallery, files, recorder | Manual matrix on reference phone | Exact-byte hash matches source file where source is accessible; denials handled |
| V-02 | Photo Picker and SAF behaviour incl. cloud-provider items, revoked grants | Manual + instrumented | No crash; explicit unavailable states |
| V-03 | WhatsApp export `.txt` real dialect on the reference phone locale | Two consenting test accounts, synthetic messages | Parser fixture updated from the real file; ambiguous dates flagged |
| V-04 | Chunked blob throughput and proxy-FD / `MediaDataSource` random access | Instrumented benchmark | Meets import Target or Target revised with evidence |
| V-05 | Keystore auth-bound key lifecycle: lock, timeout, biometric change, reboot | Instrumented + manual | Fail-closed in every state; documented behaviour |
| V-06 | StrongBox availability and timing | Instrumented | Recorded; fallback to TEE works |
| V-07 | SQLCipher + Room on API 36, arm64, 16 KB page device or emulator image | Build + instrumented | Opens, migrates, passes tamper test |
| V-08 | ML Kit bundled OCR works with no `INTERNET` permission, cold, in airplane mode | Instrumented | Text returned; no crash; packet capture shows no traffic |
| V-09 | Backup, D2D and cross-platform transfer exclude evidence | `adb` backup tooling and a real device transfer | No evidence bytes transferred |
| V-10 | OCR latency and PSS on screenshots (Latin; later Devanagari, Malayalam) | Release build, `dumpsys meminfo`, traces | Numbers recorded per language |
| V-11 | `PdfDocument` shaping for Devanagari and Malayalam | Render fixtures; native-speaker visual review | Reviewer sign-off per script |
| V-12 | whisper.cpp base q5_1 load time, RTF, PSS, thermal on 6 GB and 8+ GB phones | Release build, FLEURS clips, 20-minute sustained run | Numbers recorded; model unloads; no OOM kill |
| V-13 | WorkManager job survival across process death, battery saver, Android 16 quotas | Instrumented + manual | Jobs resume; no fake completion |
| V-14 | NLS payloads per app (WhatsApp first), dedup fixtures, lock-state behaviour, reconnect | Synthetic producer app, then consenting accounts | Section 7 acceptance fixtures of the notification report pass; per-app table filled |
| V-15 | 4 GB degraded mode | Low-RAM device | Text-only path works; heavy stages refuse gracefully |
| V-16 | ORT Android tokenizer and logits parity with the Linux probe receipt | Instrumented | Max abs logit difference within a stated tolerance |
| V-17 | Offline cold launch in airplane mode after install | Manual | Full MVP workflow works |

Reference devices: one named 6 GB arm64 phone (primary, per `RECOMMENDATION.md`), the SM-S928B if available, and one lower-tier or older-API device before any broader claim. Emulators are used for API-level and 16 KB checks only.

## 29. MLOps / Model Preparation

```text
Dataset -> licence verification -> cleaning -> deduplication -> label validation (native speakers)
        -> leakage-safe split (by conversation / person / source / template family, BEFORE augmentation)
        -> training -> calibration on the exported artefact -> error analysis
        -> export (ONNX int8) -> Android parity + resource benchmark -> model_version manifest
```

Tracked for every artefact in a `models/manifest.json` (same style as `data/manifest.json`): dataset IDs and versions, source, licence and rights status (unresolved / research_only / product_cleared), language, modality, labels and ontology version, real or synthetic origin, preprocessing version, split protocol version, model revision, tokenizer hash, quantisation recipe, calibration ID, evaluation receipt path, Android benchmark receipt path.

Rules: missing labels are masked, not negative; thresholds chosen on validation data only; test set never used for selection; per-language results reported; synthetic kept labelled and capped in training mixes; training happens off-device on approved de-identified data; nothing from a user's vault is ever uploaded for training. The existing bench (`python -m bench classifier` etc.) remains the laptop evaluation harness; new adapters are added there, not in a new framework.

MVP needs none of this: it ships rules and ML Kit. The pipeline starts when licensed, native-reviewed data for en/hi/ml exists (first pilot per datasets report D3: 240 windows, 40 per stratum).

## 30. Dependency Graph

```text
core:model ----+
               +--> core:temporal ---------------------------+
core:integrity-+                                             |
      |                                                      v
      +--> core:crypto --> core:database --> core:vault --> app (cases, timeline, review, patterns)
                                   |              ^                 |
                                   |              |                 v
                       acquisition:importer ------+        export:report --> export:verifier
                                   |
                       processing:text (parser, rules) --> findings
                       processing:ocr  -------------------> derivatives
                       processing:stt (Phase 2)
                       acquisition:notifications (Phase 2b)
```

What blocks what:

- `core:model` and `core:integrity` block everything.
- `core:crypto` blocks database and vault; vault blocks importer, processing and export.
- `core:temporal` depends only on `core:model`, so it is built in parallel with the Android foundation.
- Review UI needs findings (rules) and vault.
- Report needs review and temporal; export needs report and integrity; verifier needs only integrity and model.
- OCR, STT and notifications hang off the vault and block nothing on the critical path.

**Critical path to the first demonstrable MVP:** core:model -> core:integrity -> core:crypto -> core:database -> core:vault -> importer (text + WhatsApp export) -> rules findings -> review -> temporal cards -> report -> export -> verifier. Parallel track: core:temporal with fixtures A-F. OCR joins after the text path is end-to-end.

## 31. Implementation Phases

Each phase ends with its Definition of Done (section 38 general rules plus the phase-specific line).

| Phase | Objective | Depends on | Components / paths | Interfaces | DB | ML assets | Tests / benchmarks | Acceptance | Risks |
|---|---|---|---|---|---|---|---|---|---|
| **0** | Analysis and megaplan | - | `MEGAPLAN.md` | - | - | - | - | This document approved | - |
| **1** | Pure-Kotlin core + project skeleton | 0 | `android/` Gradle skeleton, `core/model`, `core/integrity`, empty `app` | `EventSchemaAdapter`, `EventValidator`, `HashChain`, `MerkleV2`, `CanonicalJson` | none | none | 27.1 model, span, integrity, canonical JSON; Python vectors | Section 39 | JDK/AGP mismatch |
| **2** | Temporal engine | 1 | `core/temporal`, `testfixtures` | `ContactCanonicalizer`, `TemporalProjection`, `PatternReducer`, `PatternExplanation` | none | none | Fixtures A-F, 16 metamorphic tests | All pass on JVM; replay of 10,000 synthetic events completes | Over-fitting rules to fixtures |
| **3** | Android foundation: lock, crypto, database, vault | 1 | `core/crypto`, `core/database`, `core/vault`, `app` shell, onboarding, case list | `KeyManager`, `BlobStore`, `EvidenceRepository`, `AuditLog`, `JobQueue` | all tables of 11.4 at version 1 | none | 27.3 crypto and DB tamper tests; V-04..V-07, V-09 | Create case; app locks; DB and blob unreadable without key; backup excluded | SQLCipher/16 KB; Keystore edge cases |
| **4** | Evidence acquisition (import lane) | 3 | `acquisition/importer`, import preview UI, manual note UI | `EvidenceImporter`, `EvidenceValidator` | - | none | 27.2 import tests, 27.3 hostile inputs; V-01..V-03 | Share and pick text, image, audio, video, PDF; exact-byte hash; over-limit handled; preserve-only states shown | Provider quirks |
| **5** | Text intelligence | 2, 4 | `processing/text`, findings UI | `ExportParser`, `LanguageHinter`, `SignalEngine` (rules), `LabelMapping` | `label_mapping` seed | none (cue lists) | Parser tests; rules parity with bench; per-language fixture table | WhatsApp export becomes events; findings appear with highlighted spans and status | Cue lists unreviewed |
| **6** | Human review | 5 | review UI, `core/vault` review logic | `FindingRepository`, `ReviewCoordinator`, `ProvenanceGraph` | - | - | Invalidation tests | Accept/reject/edit/add context/unknown; three review targets; originals untouched | UX complexity |
| **7** | Temporal UI | 2, 6 | timeline, pattern cards, boundary notes, gap shading | `PatternRepository` | - | - | Demo story of temporal sec 14 on device; reducer timing | Fixtures A-F reproduce on the phone; correction changes cards | Wording safety |
| **8** | Search | 6 | search UI | `EvidenceSearch` | - | - | Scope and cross-case tests | Finds text in case; never across cases | - |
| **9** | Reporting and export | 6, 7 | `export/report`, `export/verifier` | `ReportGenerator`, `ExportService`, `IntegrityVerifier`, `Redactor` | `report`, `report_snapshot` | fonts | Verifier tests; V-11 | Bundle verifies on a second offline computer; tampered fixture fails; forbidden-phrase guard green | Indic shaping |
| **10** | OCR | 4 | `processing/ocr` | `OcrProcessor` | - | ML Kit Latin (then Devanagari; Tesseract `mal`) | V-08, V-10; CER on bench screenshots regenerated on Linux fonts | Screenshot -> regions -> findings; unsupported script abstains | No-network ML Kit; JNI |
| **11** | STT | 3 (secure random access) | `processing/stt` | `SttProcessor`, `ModelSessionManager` | - | whisper base q5_1 | V-12 | 60 s clip -> transcript with ranges; silence explicit; model unloads | RAM, thermal, NDK |
| **12** | Notification observation (optional) | 6 | `acquisition/notifications`, producer test app under `android/tools/` | `NotificationNormalizer`, `ObservationDiffer`, `CandidateInbox`, `CoverageState` | candidate tables | none | Notification report stages A-E; V-14 | Dedup fixtures pass; session-only default; revoke stops capture | Play policy, OEM behaviour |
| **13** | Security hardening | 9 | all | - | - | - | Full 27.3 suite; external review of crypto envelope | No plaintext artefacts found; threat model document in `docs/architecture/` | Unknown unknowns |
| **14** | Real-device validation | 9-12 | `benchmark.md` updates | - | - | - | Section 28 table | Every claimed capability has a device receipt | Device availability |
| **15** | Release qualification | 13, 14 | `LICENSE`, notices, privacy policy, Data safety answers | - | - | - | Section 38 release list | Counsel and distribution review complete | Legal ambiguity |

## 32. Milestones

| Milestone | Contains | Demonstrates |
|---|---|---|
| **M-A Core proven** | Phases 1-2 | Event contract, integrity primitives and temporal rules work, with no phone |
| **M-B Vault** | Phases 3-4 | Evidence goes in encrypted, hashed, with provenance |
| **M-C Text MVP** | Phases 5-7 | Import a chat export, review findings, see pattern cards, correct one and watch counts change |
| **M-D Verifiable report** | Phase 9 (+8) | Export, verify offline, tamper and fail |
| **M-E Screenshots** | Phase 10 | OCR lane with regions |
| **M-F Hardened MVP** | Phases 13-14 for MVP scope | Device receipts in `benchmark.md` |
| M-G Audio, M-H Notifications | Phases 11, 12 | Post-MVP |

Hackathon demo = M-C + M-D, with M-E if time allows. `benchmark.md` gets an entry at each milestone.

## 33. Risk Register

Probability and impact: L / M / H.

| Risk | Category | P | I | Detection | Mitigation | Fallback |
|---|---|---|---|---|---|---|
| Android storage/intent restrictions change | Platform | M | M | Release notes review before each release; V-01, V-02 | Use only picker/share APIs | Manual paste and note |
| Notification access incomplete or policy-blocked | Platform / policy | H | M | V-14; Play review | Optional lane, off critical path | Import-only product |
| Selected model too large for 6 GB phone | ML | M | H | V-12 | One model at a time; base not small | whisper tiny; manual transcript |
| Poor Malayalam/Hindi/code-mixed performance | ML | H | H | Per-language tables | Abstain; manual tagging always available; native review | Rules disabled for that language; manual only |
| OCR errors change meaning | ML | H | M | CER, critical-term checks | Show original beside OCR; user edit creates revision | Manual transcription |
| STT hallucination or omission | ML | H | M | WER; silence tests | Ranges, uncertainty, review; no auto-translate | Manual transcript |
| False positives | ML / safety | H | M | Reject-rate metrics; fixtures | Suggestion-only; review gate; no automatic action | Rules off |
| False negatives | ML / safety | H | H | Fixture and pilot analysis | Manual tagging; label-independent temporal rules; no "safe" output | Manual |
| Hallucinated explanation | ML | L (no LLM) | H | Template tests | Templates from structured data | - |
| Evidence corruption | Integrity | L | H | GCM tags; hash recheck | Atomic writes; flag not drop | User re-imports |
| Evidence leakage (logs, temp, backup, recents) | Security | M | H | 27.3 grep and backup tests | SafeLog, no temp plaintext, FLAG_SECURE, backup exclusion | - |
| Key loss or invalidation | Security | M | H | V-05 | Clear disclosure; explicit export; Phase 2 recovery bundle | User keeps exported bundles |
| Device compromise | Security | L-M | H | Not detectable by the app | Honest limits statement | - |
| Battery drain | Performance | M | M | Energy measurement | No always-on work; user-started heavy jobs | Disable stage |
| Thermal throttling | Performance | M | M | Thermal status in V-12 | Pause on SEVERE; 60 s cap | Smaller model |
| Dependency incompatibility (SQLCipher, 16 KB, AGP, JDK) | Engineering | M | H | Phase 1 and 3 builds; V-07 | Pin versions; toolchain | Column-envelope fallback (11.1) |
| Model licensing unclear | Legal | M | M | Section 25 audit | Do not ship until cleared | Rules only |
| Dataset licensing unclear | Legal | H | M | `DATA_LICENSES.md` | Rights registry; separate research and product lineages | Collect own cleared data |
| Google Play rejection | Policy | M | H | Pre-submission review | No sensitive permissions in MVP; disclosures | Sideload demo; drop NLS from Play build |
| Legal ambiguity (DPDP, third-party data, evidence law) | Legal | H | M | Counsel review | No admissibility or legality claims; local only | Narrow features |
| Unsupported media formats | Engineering | H | L | Import tests | Preserve-only state | Screenshot or note |
| Indic text mis-shaped in PDF | Engineering | M | M | V-11 | Android text layout; bundled fonts | Quote-as-image |
| User-safety harm (discovery of the app, distressing content) | Safety | M | H | Specialist review | Neutral notifications, no claims of invisibility | - |
| Scope creep into model zoo or backend | Process | M | M | Decision log | MVP list in section 6 | - |
| Synthetic results mistaken for real accuracy | Process | M | H | Review of every published number | Labels on every table | - |

## 34. Requirements Traceability Matrix

| Requirement | Source file | Source section | Decision | Component | Validation |
|---|---|---|---|---|---|
| Android-first, Kotlin + Compose | `AGENTS.md` | Core Rules | Adopted | `android/` | Build |
| Offline local AI, no cloud fallback | `AGENTS.md` | Core Rules | No `INTERNET` in MVP | Manifest | Permission allowlist test; V-17 |
| Human-in-the-loop | `AGENTS.md` | Core Rules | Review gate before export | `ReviewCoordinator`, `ExportService` | 27.1 export gate |
| Evidence-linked AI claims | `AGENTS.md`; temporal | Core Rules; 6.2 | Anchor required at construction | `finding_anchor`, `EventValidator` | Anchor tests |
| Originals preserved | `AGENTS.md`; multimodal | Core Rules; 4.2 | Immutable blobs, derivatives separate | `BlobStore` | INT-01 hash-stable test |
| Explicit export | `AGENTS.md` | Core Rules | Share sheet only, user-initiated | `ExportService` | No-network and UI tests |
| Minimal architecture | `AGENTS.md` | Core Rules | No backend, manual DI, few modules | Section 9 | Review |
| Do-not-build list | `AGENTS.md` | Do Not Build | Rejected list | Section 6 | Forbidden-API lint |
| Supported acquisition mechanisms | `AGENTS.md`; acquisition | Evidence Acquisition; M1-M5 | Share, SAF, Picker MVP; NLS Phase 2b | `acquisition/*` | V-01, V-02, V-14 |
| View Once not bypassed | `AGENTS.md`; view-once | View Once; VO-01..12 | Manual incident note with status enums | Manual note UI | VO tests |
| Hybrid pipeline, local LLM only when needed | `AGENTS.md`; local-ai | Local AI; 1 | Rules -> classifier later -> temporal; no LLM in MVP | Section 15 | Ablations later |
| Laya deferred | `AGENTS.md` | Laya | Deferred with re-entry gates | - | - |
| Leakage-safe data pipeline | `AGENTS.md`; datasets | Fine-Tuning; B3, F5 | Section 29 | `models/manifest.json` | Split audit |
| Language scope en/ml/hi | `AGENTS.md` | Multilingual | Section 16 | `LanguageHinter` | Per-language tables |
| OCR / STT / frames | `AGENTS.md`; multimodal | Multimodal; 5 | ML Kit, Tesseract, whisper.cpp | `processing/*` | V-10, V-12 |
| Track events over time | `AGENTS.md`; temporal | Pattern Detection; 5, 8 | Deterministic engine | `core/temporal` | Fixtures A-F |
| Observed / Inferred / Pattern / Unknown | `AGENTS.md` | Reliability | Enum end to end | `core/model`, UI, report | UI and report tests |
| Keystore, AES-GCM, SHA-256, manifests | `AGENTS.md`; `RECOMMENDATION.md` | Security; table | Section 22 | `core/crypto`, `core/integrity` | 27.3 |
| Hash is not authenticity | `AGENTS.md` | Security | Limits text | Report, verifier | Forbidden-phrase guard |
| Device prototype for uncertain behaviour | `AGENTS.md` | Research Agent Rules | Section 28 | - | Receipts in `benchmark.md` |
| Engineering priority order | `AGENTS.md` | Engineering Priority | Section 31 order | - | - |
| Never fabricate measurements | `AGENTS.md` | Project verification | Target/Measured labels | Section 26 | Review |
| Event contract | `data/sakshi-event-schema.json` | whole file | Interchange contract | `EventSchemaAdapter` | Schema tests |
| Count contacts, not callbacks | temporal; notification | 7; 7 | Canonicalizer | `ContactCanonicalizer` | Fixture F |
| Capture gaps disable rate claims | temporal; novelty | 8.1; I1 | Coverage ledger | `TemporalProjection` | Metamorphic test 6 |
| Corrections propagate | novelty; sentiment | I2; 15 | Provenance graph + invalidation | `ProvenanceGraph` | Invalidation tests |
| Selective verifiable export | novelty | I3 | Bundle + verifier | `export/*` | Tamper demo |
| Code-point anchors | schema; `HANDOFF.md` | locator; Decisions | Canonical | `Span` | Span tests |
| No plaintext temp for decoders | multimodal | 9.2 | Proxy FD / MediaDataSource | `BlobStore` | V-04 |
| Backup exclusion | acquisition; notification | M8; 9.2 | Rules + no-backup dir | Manifest | V-09 |
| Rules baseline until validation | `RECOMMENDATION.md` | Evidence boundary | MVP signals = rules | `SignalEngine` | Parity with bench |
| Chain + Merkle v2 constructions | `bench/adapters.py`; `benchmark.md` | `chain`, `merkle`; M3b prep | Ported exactly | `core/integrity` | Shared vectors |
| Resumable job queue | `bench/pipeline.py` | `Queue` | Ported design | `JobQueue` | Process-death test |
| Target API 36 | Google Play policy | Target API requirements | `targetSdk 36` | Gradle | Build |
| 16 KB pages | Google Play policy | 16 KB requirement | arm64, aligned libs | Gradle, NDK | V-07 |
| DPDP phased commencement | MeitY / PIB | DPDP Rules 2025 | Counsel gate | Section 24 | Release checklist |

## 35. Decision Log

| ID | Decision | Reason | Evidence | Alternatives considered | Impact | Status |
|---|---|---|---|---|---|---|
| D-01 | `minSdk` 26; notification module API 30+ | Proxy FD for decrypt-on-demand needs 26; platform message parser needs 30. Sources disagree: acquisition sec 5 "API 26+ reasonable", multimodal sec 10.2 "API 26", notification sec 2.2 "API 30" | Cited sections | Single minSdk 30 (simpler, fewer devices) | Wider device reach; one gated module | Proposed |
| D-02 | Room over SQLCipher, plus chunked AES-GCM blob files | `RECOMMENDATION.md` picks SQLCipher; temporal sec 12.1 and notification sec 9.2 warn about plaintext columns/FTS/WAL; bench measured per-row AES-GCM, not SQLCipher | Cited; `results/storage.csv` | Plain Room + column envelopes (kept as fallback) | Native dependency, 16 KB check | Proposed, **[P]** |
| D-03 | Persisted text anchors are Unicode code points | Schema and `HANDOFF.md` say code points; notification sec 6 says UTF-16 | Cited | UTF-16 everywhere | One conversion boundary | Decided (follows schema) |
| D-04 | Event schema labels canonical; bench and research labels mapped; `source_label` kept | Three vocabularies exist (11.3 M2) | `bench/data.py` LABELS; schema enum; datasets E2 | Change schema to bench labels | Mapping table to maintain | Proposed |
| D-05 | MVP signals are a rules baseline; no neural classifier in MVP | `RECOMMENDATION.md`: ship rules-assisted highlighting until native-language validation. Conflicts: sentiment sec 21 proposes two English ONNX packs for the hackathon MVP; local-ai sec 1 proposes a fine-tuned multilingual MiniLM | Cited; probe shows the toxicity pack misses blackmail and fails negation | Ship the two ONNX packs in MVP | Honest, small, multilingual cue lists; ONNX pack moves to Phase 2 as experimental | Proposed. `RECOMMENDATION.md` is the later, repo-level decision |
| D-06 | Import lane is MVP; NLS is Phase 2b and optional | `RECOMMENDATION.md` keeps notifications out of the default first-release path; `AGENTS.md` allows NLS; novelty demo uses it | Cited | NLS in MVP | Lower policy and device risk | Proposed |
| D-07 | No LLM in MVP; candidates only recorded | All three AI reports name different first LLMs (Qwen2.5-1.5B, Qwen3.5-2B, Qwen3-0.6B); none tested; all agree it is optional | Cited | Pick one now | No model, no JNI for MVP | Decided; choice deferred to a later bake-off |
| D-08 | STT model: whisper.cpp multilingual base q5_1, Phase 2 | `RECOMMENDATION.md` says base q5_1; multimodal says tiny or base | Cited | tiny | Larger but better candidate; tiny is fallback | Proposed, **[P]** |
| D-09 | Export signature ECDSA P-256 in Keystore | Bench measured Ed25519, but Keystore Ed25519 needs API 33+ | Android Keystore documentation (to re-verify at implementation) | Software Ed25519 with wrapped key; raise minSdk | Differs from bench candidate | Proposed |
| D-10 | Audit chain = bench `chain`; manifest root = bench Merkle v2 | Tested constructions with a found-and-fixed ambiguity | `benchmark.md` M3b prep; `bench/tests` | Design new ones | Cross-language vectors | Decided |
| D-11 | Emotion analysis is not in MVP and never gates behaviour | `HANDOFF.md` decisions | Cited | Include emotion track | Less model surface | Decided |
| D-12 | Laya stays deferred | `AGENTS.md` user decision 2 Oct 2026 | Cited | - | - | Decided |
| D-13 | Audio, video, PDF are preserve-only until secure random access is proven | Multimodal sec 9.2: defer rather than write plaintext scratch | Cited | Temp decrypted files | Slower modality rollout | Decided |
| D-14 | `deep-research-report.md` is not a decision source | Novelty stack sec 9 "Corrections" supersedes its quantisation, NPU, StrongBox and LoRA claims; it also recommends 7B models and cloud-style attestation | Cited | - | - | Decided |
| D-15 | Android project lives in `android/`; Python bench untouched | Two toolchains; bench is the oracle | - | Repo root Gradle | Clean separation | Proposed |
| D-16 | Manual DI, feature UI inside `:app` | Minimal architecture rule | `AGENTS.md` | Hilt, per-feature modules | Less ceremony | Proposed |
| D-17 | Demo thresholds (>= 3 after boundary, >= 6 and 3x, 7-day transition) are configuration, versioned, labelled demo | Temporal sec 8: "synthetic demo settings, not validated thresholds" | Cited | Hard-code | Honest wording | Decided |
| D-18 | `caste_religious_slur` maps to `verbal_abuse` until schema v1.1 | Schema has no identity-abuse label | 11.3 M3 | Change schema now | Slight loss of specificity in exports | Open (Q5) |

Uncertain after resolution: SQLCipher viability (D-02), signature algorithm (D-09), minSdk (D-01), whether the hackathon judges expect a neural model in the demo (D-05).

## 36. Open Questions

| # | Question | Default used by this plan |
|---|---|---|
| Q1 | Project licence | None chosen; blocker for public release only |
| Q2 | Reference phone(s) and whether the SM-S928B is available | A 6 GB arm64 phone is primary; all Targets assume it |
| Q3 | Hackathon date and demo expectations (is a neural model expected on stage?) | Demo = M-C + M-D with rules baseline |
| Q4 | Distribution: Play Store or sideload | Sideload for the demo; Play-ready constraints kept |
| Q5 | Approve schema v1.1 with `identity_directed_abuse`? | Not approved; mapping in D-18 |
| Q6 | Who performs native-speaker review of Malayalam and Hindi cue lists and fixture labels? | Cue lists ship disabled until reviewed |
| Q7 | Recovery: is an encrypted recovery bundle needed for MVP? | No; disclosed limitation |
| Q8 | Should notification candidates be retainable while locked (weaker `K_ingress` mode)? | No in default; opt-in only |
| Q9 | Should `HANDOFF.md` deletion be committed or reverted? | Left as found |
| Q10 | Legal counsel availability for DPDP, BSA s. 63 wording, child-safety duties | Release gate, not a build gate |

## 37. Validation Backlog

In priority order: V-07 (SQLCipher/16 KB), V-05 (Keystore lifecycle), V-04 (blob random access), V-09 (backup exclusion), V-01..V-03 (import matrix and real export dialect), V-08 (ML Kit offline), V-10 (OCR numbers), V-11 (Indic PDF shaping), V-17 (offline cold launch), V-13 (job survival), V-12 (whisper), V-16 (ORT parity), V-14 (notifications), V-15 (4 GB), V-06 (StrongBox).

Non-device backlog: native-speaker review of fixtures and cue lists; licence verification for the two minuva checkpoints; regenerate screenshot fixtures with Linux font discovery (bench currently assumes Windows fonts); complete the full faster-whisper laptop run or mark it abandoned; resolve HateXplain and Uli licence conflicts before use; realistic multi-message linking fixtures.

## 38. Definition of Done

For any phase:

- Code builds with the pinned toolchain; ktlint/detekt and Android lint clean with no suppressions added.
- Unit and instrumented tests for the phase pass; the Python suite still passes.
- No new permission, network API or forbidden API; manifest allowlist test green.
- Every user-visible AI result carries an epistemic status and an anchor.
- Any number published is labelled Target, Measured (with device) or Estimated.
- `benchmark.md` updated at milestones; this megaplan's Decision Log updated if a decision changed.

Before any release beyond a demo: full section 27.3 suite; section 28 receipts for every claimed capability; per-language results published; native-speaker sign-off for each enabled language and script; licences and notices complete; privacy policy and Data safety form match the build; counsel and distribution review done; offline cold-launch test passed; no claim from the "never claimed" list anywhere in the app or store listing.

## 39. First Implementation Slice

**Slice 1: Gradle skeleton + `core:model` + `core:integrity`, proven on the JVM.** No Android UI beyond an empty launcher activity, no database, no crypto, no acquisition.

### Dependencies (must exist before starting)

- JDK 17 or 21 available to Gradle (host has only JDK 27).
- Android SDK platform 36 and build-tools (present).
- This megaplan approved; Decision D-15 (location `android/`) accepted.
- No NDK, device or model needed.

### Files/modules to create

```text
android/settings.gradle.kts
android/build.gradle.kts
android/gradle.properties
android/gradle/libs.versions.toml
android/gradle/wrapper/*                      (Gradle wrapper)
android/app/build.gradle.kts
android/app/src/main/AndroidManifest.xml      (no permissions; allowBackup=false)
android/app/src/main/kotlin/org/sakshi/app/MainActivity.kt   (empty Compose screen)
android/core/model/build.gradle.kts           (kotlin-jvm)
android/core/model/src/main/kotlin/org/sakshi/core/model/
    Ids.kt  Span.kt  Locator.kt  TimeBounds.kt  Confidence.kt  EpistemicStatus.kt
    Event.kt  Enums.kt  EventSchemaAdapter.kt  EventValidator.kt
android/core/model/src/test/kotlin/org/sakshi/core/model/
    SpanTest.kt  EventSchemaAdapterTest.kt  EventValidatorTest.kt
android/core/integrity/build.gradle.kts       (kotlin-jvm)
android/core/integrity/src/main/kotlin/org/sakshi/core/integrity/
    Sha256.kt  HashChain.kt  MerkleV2.kt  CanonicalJson.kt
android/core/integrity/src/test/kotlin/org/sakshi/core/integrity/
    HashChainTest.kt  MerkleV2Test.kt  CanonicalJsonTest.kt
android/testfixtures/integrity-vectors.json   (generated once from bench/adapters.py chain/merkle)
android/testfixtures/event-valid.json, event-invalid-*.json   (from temporal report 6.3 and research/verification)
android/README.md                             (build and test commands)
```

### Files to modify

- `.gitignore`: add `android/.gradle/`, `android/**/build/`, `android/local.properties`.
- `benchmark.md`: one ledger entry recording the slice and that no device measurement was taken.
- Nothing else. `data/sakshi-event-schema.json` is read by tests from its existing path, not copied.

### Interfaces

```kotlin
// core:model
@JvmInline value class EventId(val value: String)          // 1..128 chars
data class CodePointSpan(val start: Int, val end: Int)     // half-open, start <= end
fun CodePointSpan.toUtf16Range(text: String): IntRange     // throws on out of range
fun utf16ToCodePointSpan(text: String, startUtf16: Int, endUtf16: Int): CodePointSpan  // rejects surrogate splits
enum class EpistemicStatus { OBSERVED, USER_REPORTED, INFERRED, PATTERN, UNKNOWN }
data class Event(/* the 20 required schema fields as typed properties */)
object EventSchemaAdapter { fun toJson(e: Event): String; fun fromJson(json: String): Event }
object EventValidator { fun validate(e: Event, ctx: CaseContext): List<Violation> }  // invariants 2, 3, 4 (self/cross-case links), 5 (cycles), 9

// core:integrity
object Sha256 { fun digest(bytes: ByteArray): ByteArray; fun hex(bytes: ByteArray): String }
object HashChain { fun head(entries: List<ByteArray>): ByteArray; fun next(prev: ByteArray, entry: ByteArray): ByteArray }
object MerkleV2 { fun root(entries: List<ByteArray>): ByteArray }
object CanonicalJson { fun encode(element: JsonElement): ByteArray }
```

### Tests that must pass

1. `HashChain.head` and `MerkleV2.root` equal the Python vectors for 0, 1, 2, 3, 4, 5 and 1,000 entries; a one-byte change, a reorder, a truncation and a duplicated-last-entry all change the result.
2. `CanonicalJson` output is byte-identical for permuted key order and is stable across runs.
3. The synthetic event from temporal report sec 6.3 round-trips `fromJson -> toJson` to canonical-equal JSON and validates against `data/sakshi-event-schema.json` with date-time format checking on.
4. Each malformed variant is rejected by schema validation or by `EventValidator`, with the expected violation.
5. Span conversion is correct for ASCII, a supplementary-plane emoji, a ZWJ emoji sequence, Malayalam and Devanagari strings; inverted, negative and out-of-range spans are rejected; a UTF-16 range splitting a surrogate pair is rejected.
6. `./gradlew :core:model:test :core:integrity:test :app:assembleDebug` succeeds.
7. `python -m unittest discover -s bench/tests -v` still passes (unchanged).

### Acceptance criteria

- From a clean checkout, the three Gradle tasks above pass on the host with no device attached.
- The debug APK's merged manifest declares zero permissions and `allowBackup=false`.
- `core:model` and `core:integrity` have no Android dependency (they are `kotlin("jvm")` modules).
- The vector file records the bench commit hash it was generated from.
- No file outside `android/`, `.gitignore` and `benchmark.md` changed.
- Nothing in this slice claims an Android capability; `benchmark.md` says so.

The next slice after this is Phase 2 (`core:temporal` with fixtures A-F), which also needs no device.

