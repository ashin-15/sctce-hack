# Handoff: Sakshi sentiment/emotion local-AI design

**Date:** 2 October 2026
**Scope:** Deep research + engineering design task "Sentiment / Emotion Analysis Integrated with Local AI for Sakshi Android". This handoff lets a new session or engineer resume without the prior conversation.

## Enduring objective

Sakshi ("Evidence That Only You Can See") is a privacy-first Android app for harassment-evidence preservation, local pattern detection, human review, and user-controlled reporting. Sensitive evidence stays on-device; AI findings are suggestions linked to source evidence, never legal/clinical verdicts. Laya is **deferred** (recorded in `AGENTS.md`); do not reopen that decision.

## Current state

### Done

- Main report: `research/sentiment-emotion-local-ai-android-design.md` (995 lines). Sections 1-22 are written: executive summary, proposal mapping, existing systems (Vigil, Agent Hita, BullyAlert, BullyBlocker, TalkingParents, MELD/DialogueRNN, HASOC, UED), architecture comparison, candidate models, runtime comparison, pipeline schemas, router design, intensity/trajectory math, context/RAG, multilingual/code-mixed strategy, personalization, explainability, datasets, evaluation/benchmark plan, privacy, Android implementation plan (Kotlin components, ORT boundary code, LiteRT-LM/llama.cpp entry points, WorkManager quotas, lifecycle state machine), innovation table, MVP definition, roadmap.
- Probe scripts preserved into the repo: `research/probes/sakshi-emotion-probe.py`, `research/probes/sakshi-ai-probe.py` (copied from `/tmp`, which is wiped on reboot).
- Prior deliverables intact: `research/laya-source-analysis-and-sakshi-local-ai-architecture.md`, `.lavish/sakshi-laya-local-ai.html`, `AGENTS.md` Laya-deferral note.

### Not done (in priority order)

1. **Section 23 of the report is an empty heading** ("Source register, experiment boundaries and verification"). The body text cites keys `[M1]-[M12]`, `[R1]-[R11]`, `[D1]-[D7]`, `[U1]-[U3]`, `[P1]-[P4]`, `[E1]-[E9]` but the register itself was never written. Reconstruct it from the research notes and the conversation history file (see below); do not fabricate citations. Suggested grouping: M=model artifacts/cards, R=runtime/framework docs, D=datasets, U=Unicode/robustness studies, P=proposal/local probes, E=existing systems.
2. **Lavish visual artifact missing.** The task requires `.lavish/` HTML with inline-SVG architecture diagram, comparison tables, decision cards, evidence-status labels, no horizontal overflow, verified in light/dark/narrow. Follow the style of `.lavish/sakshi-laya-local-ai.html`. Build with `lavish-axi` (installed at `~/.npm-global/bin/lavish-axi`; invoke the `lavish` skill for usage). Suggested name: `.lavish/sakshi-sentiment-emotion-architecture.html`.
3. **Report verification pass:** confirm every required section from the task exists (the 22-section list in the task maps onto current sections 1-22), every major recommendation carries the WHY → WHAT → HOW → MODEL → RUNTIME → DATA → ANDROID → LIMITATIONS structure, and no claim presents Linux diagnostics or publisher benchmarks as Sakshi/Android measurements.
4. **Commit.** Nothing is committed or pushed. Existing commits: `dd5abcc` (prior-art baseline), `0836200` (acquisition boundaries). Match that style; do not add a co-author trailer.

## Reproduce the local experiments

Model dirs live in `/tmp` and will not survive reboot. Re-fetch if gone:

```bash
hf download minuva/MiniLMv2-toxic-jigsaw-onnx --local-dir /tmp/sakshi-minilm-tox
hf download minuva/MiniLMv2-goemotions-v2-onnx --local-dir /tmp/sakshi-minilm-emotion
uv run --no-project \
  --with onnxruntime==1.22.1 --with tokenizers==0.22.0 --with numpy==2.3.3 \
  python research/probes/sakshi-emotion-probe.py
```

Key probe findings already encoded in the report: raw scores shift with padding (anger 0.571 padded vs 0.723 unpadded on one fixture), the English tokenizer emits 5 UNKs on Malayalam, quoted/negated abuse scores high, conditional photo-exposure scores low on threat, "Fine." scores 0.94 approval. All are Linux x86 synthetic diagnostics, not quality, calibration, or device results.

## Decisions that must survive editing

- Emotion is an independent branch, never a gate on evidence preservation; low emotion/toxicity cannot suppress evidence.
- Behaviour detection is the primary safety signal; the Jigsaw baseline is narrow (misses coercion/stalking categories) and must be labelled as such.
- Intensity is `null` in MVP; raw sigmoid scores are not probabilities and not intensity.
- No mandatory LLM; optional reasoner tier is Qwen3-0.6B LiteRT-LM first, Qwen3.5-2B GGUF/llama.cpp as benchmark challenger. LLM never counts events or owns chronology/provenance.
- Temporal engine is deterministic Kotlin over distinct reviewed events; trajectories show source IDs, gaps, uncertainty; trajectories never claim escalation.
- Unsupported languages route to explicit `unknown`, never a confident English-model label; no translation-before-classification default.
- No on-device training/LoRA/federation in MVP despite ORT Android training support.
- Originals immutable; normalization produces versioned derivative views with source-span maps.

## File map

- `research/sentiment-emotion-local-ai-android-design.md` - the deliverable being finished.
- `research/probes/` - preserved probe scripts (paths inside still point at `/tmp/sakshi-minilm-*`).
- `research/local-ai-architecture.md`, `research/laya-source-analysis-and-sakshi-local-ai-architecture.md` - prior reports this one builds on.
- `research/existing-systems-survey.md`, `research/harassment-detection-datasets.md`, `research/temporal-harassment-patterns.md`, `data/sakshi-event-schema.json` - sibling research (some authored in parallel sessions).
- `.lavish/*.html` - existing visual artifacts to match for theme/style.
- `Harassment_Pattern_Guard.pptx.pdf` - product proposal (binary; extract text, do not read as UTF-8).
- Full conversation history: `/home/ashin/.local/share/devin/cli/summaries/history_d0e509aea77644fd.md` - contains the source list needed to reconstruct section 23.

## Style constraints

- No em dashes (project rule; the report intentionally avoids them - keep it that way).
- British spelling of "behaviour" is used throughout the report; stay consistent.
- Cite repository files rather than pasting long excerpts; keep code excerpts under ~20 lines.
