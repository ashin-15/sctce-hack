# Handoff: Sakshi sentiment/emotion local-AI design

**Date:** 2 October 2026
**Status:** Research report and visual review deliverables completed. Android implementation and release qualification remain separate work.
**Scope:** "Sentiment / Emotion Analysis Integrated with Local AI for Sakshi Android". This completion covers this handoff only; the sibling handoffs remain separate.

## Enduring objective

Sakshi ("Evidence That Only You Can See") is a privacy-first Android app for harassment-evidence preservation, local pattern detection, human review and user-controlled reporting. Sensitive evidence stays on-device. AI findings are suggestions linked to source evidence, never legal/clinical verdicts. Laya remains **deferred** as recorded in `AGENTS.md`.

## Completed deliverables

- [Main report](research/sentiment-emotion-local-ai-android-design.md): all sections 1-23 populated. Section 23 reconstructs 58 source-register keys, covering all 56 keys cited in the body, including previously unlisted C1-C3 and T1-T5. Entries separate current primary-page verification, historical source inspection, local diagnostics and unresolved rights/device evidence.
- Sections 1-22 reviewed for recommendation structure, task/runtime boundaries, source traceability and experiment claims. Added supporting WHY/WHAT/HOW/MODEL/RUNTIME/DATA/ANDROID/LIMITATIONS contracts; repaired merged prose and restored canonical product/model/API names.
- Reconciled persisted text anchors with `data/sakshi-event-schema.json`: half-open Unicode code-point ranges. Kotlin UTF-16 conversion remains an explicit UI/tokenizer boundary with surrogate checks. The shared schema was not changed; the illustrative analysis result still requires a validated event adapter.
- [Visual artifact](.lavish/sakshi-sentiment-emotion-architecture.html): architecture, decisions, evidence-status distinctions, model/runtime comparisons and synthetic diagnostic examples. It follows the existing Sakshi review-artifact style and keeps phone/quality limitations visible.
- Durable probes remain [extended emotion probe](research/probes/sakshi-emotion-probe.py) and [earlier AI probe](research/probes/sakshi-ai-probe.py).

## Verification and reproducibility

Run from the repository root. Current receipts and scripts:

- [Offline probe receipt](research/verification/sentiment-emotion-probe.json): 20 synthetic fixtures plus one NFKC view per model, 42 total forwards. Correct shapes, finite outputs, equation and original-preservation assertions passed. Records model/config/tokenizer hashes. The pinned model hashes match section 6.
- [Browser receipt](research/verification/sentiment-emotion-browser.json) and [browser check](research/verification/sentiment-emotion-browser-check.mjs): actual Chromium at 1440 and 390 pixels in light/dark modes. Checked five fixture selections, filter including zero results, details, navigation and theme. No page/SVG-text overflow, runtime exceptions or remote requests observed.
- Report checks: numbered sections 1-23, all citation keys resolved, illustrative JSON parses, local links resolve, no em dashes, no preferred reproduction path referencing a temporary-only script.
- Baseline regression: 20 unittest cases, 19 passed and one optional faster-whisper case skipped. No production code or dependency changes were required.
- [Final validation record](research/verification/sentiment-emotion-validation.json): report checks, regression result, artifact digests and explicit qualification limits. Reproduction instructions are in [verification notes](research/verification/README.md).

Local visual review: [Sakshi sentiment/emotion architecture](http://127.0.0.1:4387/session/39e734f716e3b30e). The page uses the existing Sakshi plum/cream/gold design system. Its screenshots were inspected in desktop dark and narrow light views in addition to the automated four-theme/width checks.

Current offline reproduction command:

```bash
uv run --no-project --offline --with onnxruntime==1.22.1 \
  --with tokenizers==0.22.0 --with numpy==2.3.3 \
  python research/probes/sakshi-emotion-probe.py
```

Baseline regression command used with isolated pinned dependencies:

```bash
uv run --no-project --python 3.12 --with scikit-learn==1.7.2 \
  --with numpy==2.2.6 --with pycryptodome==3.23.0 --with psutil==7.0.0 \
  python -m unittest discover -s bench/tests -v
```

Model assets remain under `/tmp` and may disappear after reboot. Re-fetch only in an explicit preparation step, pinning the revisions recorded in section 6:

```bash
hf download minuva/MiniLMv2-toxic-jigsaw-onnx \
  --revision c035f27b6a6d68770f8069a4829f1715a48b8d51 \
  --local-dir /tmp/sakshi-minilm-tox
hf download minuva/MiniLMv2-goemotions-v2-onnx \
  --revision 4fea72b9ec71ba8d84b88e0efa2ace3dcc733bfc \
  --local-dir /tmp/sakshi-minilm-emotion
```

The current unpadded diagnostic reproduces anger 0.722891/insult 0.938573 on the literal fixture, threat 0.000657 on conditional photo exposure, approval 0.941967 on "Fine.", and five unknown tokens among eight behaviour tokens for a Malayalam fixture. These are Linux x86 synthetic diagnostics, **not** accuracy, calibration, emotion intensity, real evidence or Android resource results.

The 64-token padded anger comparison (0.570579 versus 0.722891 unpadded) and earlier laptop p50/RSS timings remain **historical observations**, not rerun measurements. The padding root cause is unisolated. Published device benchmark numbers belong to their authors' exact models/devices/workloads.

## Decisions that must survive editing

- Emotion is independent and optional; it never gates preservation or behaviour review.
- Behaviour is the primary safety-related signal. The six-label Jigsaw baseline lacks comprehensive control, stalking, blackmail and sexual-harassment coverage.
- Intensity is `null` in MVP; raw sigmoid scores are neither calibrated probabilities nor intensity.
- No mandatory LLM. Qwen3-0.6B LiteRT-LM is the first optional integration experiment; Qwen3.5-2B GGUF/llama.cpp is a richer benchmark challenger. No reasoner is qualified by this documentation.
- Kotlin temporal logic owns distinct reviewed-event counts, chronology and provenance. Expressed-language trajectories show source IDs, gaps and uncertainty, never escalation/danger verdicts.
- Unsupported languages route to explicit unknown; no translation-before-classification default or confident English-model result for Indic input.
- No automatic training, LoRA or federation in MVP.
- Originals remain immutable; normalized/extracted text is a versioned derivative with source maps and canonical code-point anchors.

## Open qualification work

The documentation handoff is complete, not a claim that the app or model pack is production-ready. Before implementation/release:

1. Create/verify the Android shell and supported user-mediated evidence acquisition on physical devices, including vendor/version restrictions and notification partial capture.
2. Qualify native tokenizers, graph/padding/backend parity, asset manifests, error states, lifecycle/cancellation and canonical anchor conversion.
3. Collect licensed/consented representative evaluation data with native-speaker review and leakage-safe partitions. Establish per-task/language quality, calibration and router criteria; synthetic fixtures are insufficient.
4. Benchmark actual Android cold/warm latency, peak memory, energy, battery, thermal and scheduling constraints. Proposed budgets are targets, not measurements.
5. Implement/audit vault encryption, nonce/key lifecycle, backup/recovery, logs, correction/deletion propagation and explicit export. Hashes alone cannot establish authenticity or admissibility.
6. Resolve release-specific model/dataset/code rights and refresh mutable primary APIs/source details. Gated Gemma assets, HateXplain license discrepancy, NRC commercial rights and some dataset terms remain unqualified.

No Android implementation, model training, representative quality/calibration experiment, private-data upload or Laya integration was performed in this completion.

## File map and investigation provenance

- `research/sentiment-emotion-local-ai-android-design.md` - completed report and source register.
- `.lavish/sakshi-sentiment-emotion-architecture.html` - completed review artifact.
- `research/verification/` - reproducible diagnostics and browser evidence for this deliverable.
- `research/laya-source-analysis-and-sakshi-local-ai-architecture.md`, `research/local-ai-architecture.md` - prior research boundaries.
- `research/android-evidence-acquisition-specification.md`, `research/whatsapp-view-once-feasibility.md` - supported acquisition design.
- `research/temporal-harassment-patterns.md`, `data/sakshi-event-schema.json` - shared temporal contract; unchanged in this handoff.
- `Harassment_Pattern_Guard.pptx.pdf` - product proposal, not a working application specification.
- Original research history: `/home/ashin/.local/share/devin/cli/summaries/history_d0e509aea77644fd.md`. Used to recover citations, not as a substitute for primary-source or device evidence.

## Style constraints

Use British "behaviour" consistently. No em dashes or automatic agent co-author trailers. Link durable repository artifacts; preserve explicit unknowns and source/measurement status when extending the report.
