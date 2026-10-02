# AGENTS.md — Sakshi

## Purpose

Sakshi is a **privacy-first Android app** that helps users organize harassment-related evidence, analyze possible patterns locally, preserve evidence integrity, and create user-reviewed reports.

This file is the compact operating contract for coding and research agents.

## Core Rules

- **Android-first.** Prefer Kotlin + Jetpack Compose and Android-supported APIs.
- **Local-first AI.** Sensitive evidence stays on-device by default.
- **Human-in-the-loop.** AI findings are suggestions; the user confirms, rejects, or edits them.
- **Evidence-linked.** AI claims must trace back to actual source evidence.
- **Originals preserved.** Never overwrite source evidence during preprocessing.
- **Explicit export.** Do not silently upload or share evidence.
- **Minimal architecture.** Avoid unnecessary backend complexity.

## Do Not Build

- Silent third-party app scraping.
- Direct/private database extraction from WhatsApp, Instagram, Telegram, Signal, etc.
- Circumventing View Once, disappearing-content, encryption, sandbox, or access controls.
- Cloud AI as the default path.
- Always-on high-compute monitoring.
- Automatic legal conclusions or claims of guilt.
- Claims of guaranteed court admissibility.
- Huge models unsuitable for phones.
- A complex backend without a concrete requirement.

## Evidence Acquisition

Use supported or user-mediated Android mechanisms such as:

- `NotificationListenerService`
- Android Sharesheet / `ACTION_SEND` / `ACTION_SEND_MULTIPLE`
- Storage Access Framework / Photo Picker
- app-private storage
- Android Keystore

For each messaging app, clearly distinguish **notification data**, **user-shared evidence**, **supported APIs**, and **unsupported/circumventing access**.

### WhatsApp View Once

Treat View Once as a special research constraint. Determine what can be legitimately detected, imported, or analyzed through supported/user-mediated workflows. Do **not** implement or research bypasses.

## Local AI

Prefer a small, hybrid pipeline:

```text
Evidence/Event
  → cheap filtering
  → small classifier / Laya
  → local LLM only when needed
  → temporal pattern engine
```

Optimize for accuracy, calibration, false-positive/negative rates, latency, RAM, battery, offline use, and multilingual support.

Do not choose models by size or popularity alone.

## Laya

Research reference (integration deferred):

`https://github.com/NandhaKishorM/laya`

User decision on 2 October 2026: defer Laya from the current Android implementation plan. Prioritize compact local classifiers, human review, and a separate temporal engine. No validated drop-in Laya Android deployment was established; future reconsideration requires native-runtime parity, task quality, calibration, and device-resource evidence. See `research/laya-source-analysis-and-sakshi-local-ai-architecture.md`.

Verify the actual repository, including architecture, checkpoints, decision types, training/fine-tuning workflow, evaluation, calibration, runtime requirements, mobile feasibility, model format, and license.

Do not assume desktop compatibility means Android compatibility.

## Fine-Tuning / Data

Use:

```text
Dataset → clean → validate labels → leakage-safe split
→ fine-tune → calibrate → error analysis → export → Android benchmark
```

Avoid train/test leakage across the same conversation/person/source where applicable.

Record dataset source, language, modality, labels, license, context, limitations, and whether data is real or synthetic. Never present synthetic data as real-world evidence.

## Multilingual

Research and test English, Malayalam, Hindi, Tamil, Telugu, Kannada, Bengali, Marathi, Hinglish, Romanized Indic text, and code-mixed/slang text.

Report per-language performance. Prefer explicit uncertainty over overconfident unsupported results.

## Multimodal

```text
Image → OCR
Audio → STT
Video → frames + OCR + STT
Text → local classification/reasoning
```

Keep originals separate from OCR, transcripts, model outputs, and reports.

## Pattern Detection

Do not reduce Sakshi to message-by-message classification. Track events over time for repetition, frequency, category transitions, and possible escalation.

AI output should describe **observed evidence and patterns**, not legal conclusions.

## Reliability / Safety

Design for the main risks:

- Android access restrictions
- false positives
- false negatives
- hallucination
- key loss
- evidence leakage
- device compromise
- multilingual weakness
- user-safety failure

AI must distinguish:

- **Observed** — directly present in evidence.
- **Inferred** — model interpretation.
- **Pattern** — supported by multiple events.
- **Unknown** — not established.

Prefer abstention to fabricated certainty.

## Security / Evidence Integrity

Research and use appropriate mechanisms such as:

- Android Keystore
- AES-GCM
- SHA-256 integrity hashes
- encrypted local storage
- integrity manifests / audit records where justified

A hash supports integrity checking; it does not by itself prove authenticity, guilt, or legal admissibility.

## Research Agent Rules

Every research task must:

1. Use primary sources where possible.
2. Separate verified facts, inference, and open questions.
3. Record limitations and conflicting evidence.
4. Avoid unsupported novelty claims.
5. End with concrete implementation implications.

For uncertain platform behaviour, **prototype it on a real Android device** instead of assuming.

## Engineering Priority

Build and validate in this order:

1. Android evidence acquisition.
2. Local text/OCR/STT pipeline.
3. Small local classifier integration (Laya deferred).
4. User review flow.
5. Temporal pattern engine.
6. Encrypted evidence vault + integrity metadata.
7. Report generation.
8. Advanced multilingual/personalization features.

Keep the MVP small, demonstrable, offline-capable, and technically defensible.

# Project verification

Run commands from the repository root. Baseline environment: Python 3.12+ with requirements.txt. Run `python -m unittest discover -s bench/tests -v`. Generate data with `python -m bench data`. Run component commands listed in bench/README.md. Inference must be offline; downloads only in explicit preparation commands. Never fabricate measurements or treat laptop proxies as Android results. Synthetic labels and summaries need native-speaker review before real-world use. Keep benchmark.md updated at milestones. Do not commit audio, model binaries, keys or consented private data.

Windows setup findings: Python 3.12.14, hash-locked requirements-lock.txt. OneDrive rejected hardlinks and later denied package replacement in both `.venv` and `.venv-bench`; those environments may be incomplete and must not be used. The approved replacement environment is outside OneDrive at `%LOCALAPPDATA%\SakshiBench\venv`; use `uv pip sync --python <environment>/Scripts/python.exe --link-mode copy --require-hashes requirements-lock.txt`. faster-whisper 1.2.1 requires the pinned PyAV 15.1.0 for its metadata_errors audio-decoder API. RapidOCR 3.3.1 must use a local Global.font_path as well as local weight paths or it attempts a visualization-font download during inference. Run the audio decoder regression test and offline OCR smoke test when changing dependencies.
