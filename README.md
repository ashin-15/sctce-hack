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

Read this before anything else. The repository is early.

| Area | State |
|---|---|
| Research | Twelve reports in [research/](research/) (Android acquisition, multimodal pipeline, temporal patterns, local AI, datasets, View Once feasibility and others). |
| Plan | [MEGAPLAN.md](MEGAPLAN.md) is the implementation plan. It is a planning document, not a description of finished work. |
| Python benchmark harness | Exists and runs on a laptop. It measures baseline candidates on synthetic fixtures. See [benchmark.md](benchmark.md) and [results/SUMMARY.md](results/SUMMARY.md). |
| Event schema | [data/sakshi-event-schema.json](data/sakshi-event-schema.json) (JSON Schema 2020-12, event v1). |
| Android project | [android/](android/) is at slice 1 only: a Gradle skeleton, a placeholder app shell that declares zero permissions, and two pure-Kotlin JVM modules. |
| `:core:integrity` | Source files present for SHA-256, hash chain, count-bound Merkle v2 and canonical JSON, with unit tests and shared test vectors generated from the Python reference functions. |
| `:core:model` | Typed event model, code-point spans, epistemic status, schema adapter and event validator. 42 JVM unit tests pass, including validation against the event schema. Library code only; nothing in the app uses it yet. |
| Evidence features | None work yet. There is no import, vault, OCR, speech-to-text, review screen, temporal engine, report or export in the app. |
| Android measurements | None. Nothing has been run or measured on an Android device. |

Every benchmark number in this repository is a laptop proxy on synthetic fixtures (or public read speech). None of them is an Android result and none is evidence of real-world accuracy. The text fixtures are orthographic variants of 14 semantic templates, and their labels are pending native-speaker review. The stack in [RECOMMENDATION.md](RECOMMENDATION.md) is a provisional engineering hypothesis, not a benchmark winner.

## End-to-end workflow

This is the product workflow from the megaplan (sections 4, 10, 17, 18, 20, 21). Today only the model and integrity primitives exist, as library code with no UI.

```text
 ACQUIRE            share sheet | file picker | photo picker | paste | manual note
    |               (later, opt-in only: notification observation)
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
 SUGGESTED FINDINGS rules baseline first, compact classifier later; each with source span
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
| 1 | Acquisition | The user shares, picks, pastes or types evidence. Optional notification observation is a later, opt-in phase and is never required. | Planned |
| 2 | Validation | Incoming URIs, names and MIME types are treated as untrusted claims. Limits are enforced while streaming. | Planned |
| 3 | Preview and save | Nothing is stored until the user chooses a case and confirms. | Planned |
| 4 | Original preservation | Exact received bytes are encrypted, hashed with SHA-256 and recorded with provenance. Originals are never overwritten. | Planned. SHA-256, hash chain and Merkle primitives exist as library code. |
| 5 | Derivatives | Parsed text, OCR and transcripts are separate versioned records. They never replace the original. | Planned |
| 6 | Events | Each source message becomes an event under the event schema. | Planned. The typed model and validator exist as tested library code; no event is created by the app yet. |
| 7 | Suggested findings | Rules-assisted highlighting first. A neural classifier only after licensed, native-reviewed data exists. | Planned |
| 8 | Human review | Three separate targets: evidence association, signal, and pattern or explanation. Rejection never labels the text harmless. | Planned |
| 9 | Temporal engine | Pure Kotlin, deterministic and idempotent. It counts only distinct incoming contacts and reports count bounds when duplicates are unresolved. | Implemented as tested library code (`:core:temporal`, 56 JVM tests on synthetic timelines). Not yet connected to the app. |
| 10 | Pattern review | The user reviews each pattern card with its supporting events, gaps and limitations. | Planned |
| 11 | Report | Dates and counts are rendered from structured data. Observed evidence, user statements and inferred findings are kept in separate blocks. | Planned |
| 12 | Export bundle | Canonical manifest, file hashes, signature and the items the user selected. Export is blocked while any included pattern is stale. | Planned |
| 13 | Offline verification | A pure-JVM verifier checks hashes, root and signature without Sakshi. | Planned |

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

The planned UI takes the label as a required parameter with no default, so an inference cannot be drawn as an observation by mistake. Where the evidence is thin, the app should abstain rather than invent certainty.

"No evidence observed in the selected records" is never presented as "no harassment occurred". The records may be incomplete, the user may not have imported everything, and some apps expose nothing. The temporal engine has no output that means the second statement. Results are limited to candidate, supported description, insufficient context and not observed, and "not observed" is always shown with its scope. Gaps in coverage are shaded on the timeline and comparable-rate statements are disabled across them.

## Repository map

| Path | What it is |
|---|---|
| [AGENTS.md](AGENTS.md) | Binding operating contract: core rules, do-not-build list, engineering priority, verification commands. |
| [MEGAPLAN.md](MEGAPLAN.md) | Implementation plan: architecture, data model, phases, milestones, risks, open questions. |
| [RECOMMENDATION.md](RECOMMENDATION.md) | Provisional MVP stack and its evidence boundary. Not benchmarked on Android. |
| [benchmark.md](benchmark.md) | Milestone ledger for the Python benchmark, including gaps. |
| [research/](research/) | Twelve research reports plus `probes/` and `verification/` receipts. |
| [bench/](bench/) | Python offline benchmark harness and its tests. See [bench/README.md](bench/README.md). |
| [data/](data/) | Synthetic fixtures, the event JSON Schema, provenance files and a third-party English tweet CSV used only as an auxiliary baseline. |
| [results/](results/) | Benchmark CSVs, plots, per-run metadata, [SUMMARY.md](results/SUMMARY.md) and a sample report PDF. Laptop proxies only. |
| [android/](android/) | Kotlin Gradle project: `:app`, `:core:model`, `:core:integrity`, shared `testfixtures/`. See [android/README.md](android/README.md). |
| [.lavish/](.lavish/) | HTML review views of the research reports. Derived, not authoritative. |
| [Harassment_Pattern_Guard.pptx.pdf](Harassment_Pattern_Guard.pptx.pdf) | The original pitch. A proposal, not a specification; its wording is narrowed by `AGENTS.md`. |

Android modules today:

| Module | Kind | Contents |
|---|---|---|
| `:app` | Android | Launcher activity with a placeholder screen. Declares no permissions. |
| `:core:model` | Kotlin/JVM | Event contract for the schema, code-point spans, schema adapter, invariant checks. |
| `:core:integrity` | Kotlin/JVM | SHA-256, hash chain, count-bound Merkle v2, RFC 8785 canonical JSON. |

Later modules named in the plan (temporal engine, crypto, database, vault, importer, text, OCR, report, verifier) do not exist yet.

## Development workflow

### How work proceeds

The megaplan orders work by dependency, with the pure-Kotlin core first because it can be tested without a phone. Milestones from megaplan section 32:

| Milestone | Contains | Demonstrates | State |
|---|---|---|---|
| M-A Core proven | Phases 1-2 | Event contract, integrity primitives and temporal rules, with no phone | Done on the JVM: event model, integrity primitives and temporal engine pass their unit tests. No phone involved, no UI. |
| M-B Vault | Phases 3-4 | Evidence goes in encrypted, hashed, with provenance | Not started |
| M-C Text MVP | Phases 5-7 | Import a chat export, review findings, see pattern cards, correct one and watch counts change | Not started |
| M-D Verifiable report | Phase 9 (+8) | Export, verify offline, tamper and fail | Not started |
| M-E Screenshots | Phase 10 | OCR lane with regions | Not started |
| M-F Hardened MVP | Phases 13-14 | Device receipts recorded in `benchmark.md` | Not started |
| M-G / M-H | Phases 11, 12 | Audio, then optional notifications (post-MVP) | Not started |

The planned hackathon demo is M-C plus M-D, with M-E if time allows. `benchmark.md` gets an entry at each milestone.

### Android build and test

Run from `android/`. Gradle needs JDK 21 (the default JDK on the development host is not used) and Android SDK platform 36. No NDK, device or model is needed for this slice.

```sh
cd android
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
./gradlew :core:model:test :core:integrity:test :core:temporal:test :app:assembleDebug
./gradlew :app:lintDebug
```

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

Stated as design intent. Most of this is planned, not built.

- Local only. Evidence and inference stay on the device. There is no backend and no cloud inference fallback.
- The MVP release build is planned to declare no `INTERNET` permission. The current placeholder app declares no permissions at all.
- The planned vault uses the Android Keystore, AES-GCM for blobs, and Room over SQLCipher for structured data. The SQLCipher and Keystore choices still need validation on a device.
- Backups and device transfer are to be excluded for evidence and derivatives.
- Hashes support integrity checking. A hash shows that bytes are unchanged since Sakshi stored them. It does not prove authenticity, who sent something, truth, legal admissibility or trusted time.
- A signature shows which device key signed a bundle. It does not identify a person.
- Fail closed: if a key is unavailable or tampering is detected, the app reports it and does not fall back to plaintext.

## Languages in scope

English, Malayalam and Hindi, including Manglish, Hinglish, native scripts, romanised text, slang and code mixing among these. Other languages are future scope: they are preserved in the vault but not analysed, and the app is meant to say so rather than guess. All in-scope languages are labelled Experimental in the plan, and cue lists stay disabled for a language until a native speaker has reviewed them. Results are to be reported per language.

## Licence

No project licence has been chosen yet (megaplan open question Q1). Third-party data and models carry their own terms, which the megaplan lists as a release gate. No licence file is included.
