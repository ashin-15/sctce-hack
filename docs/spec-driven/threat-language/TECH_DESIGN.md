# Technical design: local threat-language suggestion

**Status:** Approved design under implementation. Arm64 native runtime and app integration compile; real-device inference and classifier quality remain unverified.

## Current system and insertion point

`TextAnalysis` (`android/processing/analysis`) reads encrypted originals, creates source-mapped text derivatives and delegates event construction to `EventBuilder`. Notification candidates are session-only in `CandidateInbox` and are imported only after explicit user action. Existing cue matching uses `RulesEngine`. The classifier belongs beside event-level analysis after authorized evidence is imported and decoded. It must not run in `SakshiNotificationListener` or `CandidateInbox`.

## Proposed modules

1. Add a platform-neutral suspend `ThreatLanguageClassifier` interface and typed result/refusal types to processing analysis.
2. Reuse `android/processing/llm` `ModelManager`, `LlmEngine`, `GenerationRequest`, and `LlamaCppEngine`; the configured Qwen preset is Qwen2.5 1.5B Instruct Q4_K_M. Current APK evidence shows `libsakshi_llm.so` is missing, so add a pinned llama.cpp Android build/preparation path and honest runtime readiness. A present GGUF alone does not mean the model is running.
3. Add a typed threat-language task adapter over the generation API. Bound input/output, use constrained JSON/GBNF if the native bridge supports it, enforce explicit enums, validate output and abstain on malformed/uncertain answers. Inject it into `TextAnalysis` at the suspendable layer, not synchronous `EventBuilder`. Keep one result/event/source-anchor mapping and cancellation behavior.
4. Reuse the existing suggestion and review APIs if their schema can preserve `classifier_suggestion`, model identity and calibration provenance. Otherwise add the smallest additive migration; preserve historical user edits and rollback compatibility.
5. Extend event review UI to show the text signal separately from user decision, including “analysis can be wrong” and no-safe-inference copy.

## Result contract

`PossibleThreatLanguage(signal, anchors, modelVersion, calibrationVersion)`, `NoThreatSignal(modelVersion, calibrationVersion)`, `NeedsReview(reason, modelVersion?)`. Reasons include unsupported language, ambiguous/low-support input, too long, empty/invalid, missing or mismatched assets, and inference failure. Numeric score is internal unless calibration is verified and product approval explicitly permits display.

## Model policy

Use the existing Qwen2.5 1.5B Instruct Q4_K_M GGUF as the runtime candidate. Its output is an inferred suggestion, not calibrated and not Sakshi ground truth. Keep model/runtime version and loaded GGUF digest in result provenance. Language-family support does not qualify Hindi, Malayalam or code-mixed performance. Do not place GGUF binaries in Git or overwrite/delete the phone's provisioned pack. No online fallback.

Define and version a rubric distinguishing expression of intent/conditional intent to harm from quote/report/denial, fiction/joke, self-directed statements and context-dependent intimidation. Evaluate on licensed person/conversation-disjoint data with language-specific slices before enabling a user-facing negative result. Until then, uncertain/unqualified outputs abstain; model-generated confidence is never a calibrated probability. User corrections remain review decisions and cannot update model weights or prompts/thresholds automatically.

## Privacy, lifecycle and failure

- Inference starts only in user-initiated analysis. No raw text/logits in logs, metrics, crash breadcrumbs, notification text or external services.
- Bound bytes/tokens and avoid uncalibrated max-over-chunk pooling. Long inputs abstain until chunking is validated.
- Run on an injected background dispatcher, support cancellation, lazily load a bounded session and release resources using established app lifecycle patterns. Coordinate with existing `LanguageModelLock` so speech and text generation do not load heavy models concurrently.
- Model or tokenizer absence/hash mismatch, malformed output, OOM, cancellation or unsupported language yields a visible unavailable/needs-review state. Never discard source, suppress rules, or block evidence review.
- User confirm/reject/edit must be versioned separately from immutable inference outputs and originals.

## Compatibility and verification

Audit Android module dependency constraints before selecting ORT version. Require offline unit/integration checks, model-manifest negative tests, existing analysis regressions, review UI flow, and a real-device run with exact APK/model digests. Report model size, cold/warm latency, peak memory, Android API/device, language qualification and quality evaluation separately. Laptop timings and model-card scores are not device/product evidence.

## Alternatives considered

- Rules alone: already present, but context-insensitive and not learned; retain as an independent signal.
- Compact encoder: potentially easier to calibrate, but is not the model the user already provisioned. Deferred pending a separate quality/asset decision.
- Laya: explicitly deferred by the project decision; no validated drop-in Android deployment.
- English Jigsaw threat head: deferred. The approved runtime candidate is Qwen already provisioned in Sakshi; model identity and generative capability do not establish task quality.
