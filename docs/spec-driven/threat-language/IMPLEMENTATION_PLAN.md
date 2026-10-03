# Local threat-language analysis for incoming text

**Status:** Approved implementation in progress. The app/native engineering slice compiles and focused unit tests pass; no real-device Qwen inference or threat-language quality validation has been demonstrated.

**Owner:** Sakshi Android app
**Scope:** Analyze user-authorized incoming text locally and offer an evidence-linked suggestion that the text may express a threat. Human review remains authoritative.

## 1. Product outcome

When the user reviews a captured notification excerpt or imports/shares text, Sakshi can run a compact local text model and display one of three outcomes:

- **Possible threat language** - a calibrated textual signal suggests direct or conditional intent to harm.
- **No threat signal found** - the model did not detect this narrow textual pattern. This does not mean the message is safe or non-harassing.
- **Needs review / unavailable** - the model cannot responsibly classify the input because of language coverage, uncertainty, malformed/too-long input, or model/runtime failure.

The UI must not call this “credible threat,” predict danger, infer sender intent as fact, or treat a negative output as reassurance. Show the exact source excerpt and let the user confirm, reject, or edit the suggestion. Preserve the original evidence and source mapping unchanged.

## 2. Current app facts

- `TextAnalysis` in `android/processing/analysis` decodes user-imported text, builds events and currently supplies the `RulesEngine` to `EventBuilder`.
- Notification excerpts already enter this analysis path as one plain-text event when the user imports them; summary-only observations are preserved without message analysis.
- `CandidateInbox` in `android/acquisition/notifications` holds observations in memory. A candidate is not vault evidence until an explicit user action imports it. Do not run model inference inside `NotificationListenerService` or persist a notification automatically.
- Existing rule cues can produce suggestions but are not a learned, calibrated threat model. Their current matches are suggestions, not findings of fact.
- The app defines a Qwen2.5 1.5B Instruct Q4_K_M GGUF preset in `android/processing/llm/ModelManager.kt`; its configured filename is `qwen2.5-1.5b-instruct-q4_k_m.gguf`. The user says this model is installed on the phone. If imported through Sakshi, it is stored under app-private `files/models`.
- Device inspection found that the installed Sakshi APK has no `libsakshi_llm.so`/llama runtime, and current `SessionServices` does not connect Qwen to text analysis. `AiModelViewModel` falls back to `DeterministicFallbackEngine` while reporting a model as running. The exact on-device file was not inspected; preset identity and user report are not a device hash/metadata check.
- The implementation should reuse the Qwen manager/runtime abstraction, build/package a real llama.cpp Android runtime if required, correct readiness UI and invoke only after explicit analysis. Qwen multilingual capability/generated labels are not Sakshi quality evidence. Laya remains deferred.
- Product languages are English, Malayalam and Hindi, including Latin-script code-mixed Manglish/Hinglish. Until each language/domain is qualified, return `NeedsReview(UNSUPPORTED_LANGUAGE)` rather than a categorical negative result.

## 3. Proposed user flow

1. User grants and enables the already-existing, opt-in notification observation flow, or imports/shares a text item.
2. For notifications, user selects a candidate and explicitly saves/imports it. No inference occurs in the collector callback. For imported text, original preservation and the app's existing import consent remain unchanged.
3. User opens analysis. App decodes the text using the current source-mapped analysis path and invokes the model on a bounded background dispatcher.
4. Model output passes artifact/runtime version, input bounds, structured-output schema, language-support and uncertainty checks.
5. App stores a versioned machine suggestion linked to the event and exact source span. It does not mutate or replace source text.
6. Review UI explains the suggestion in non-alarmist language and provides Confirm, Reject, Edit, and “not enough context” actions. A rejection never deletes evidence or suppresses later review.
7. Confirmed user decisions become reviewed labels/events through existing review APIs; model suggestions remain distinguishable from user decisions in storage, reports, and temporal analysis.

## 4. Functional requirements

| ID | Requirement |
|---|---|
| FR-001 | Run threat-language inference only after user-initiated analysis of imported/saved evidence; never infer in the notification callback. |
| FR-002 | Use on-device inference only. The runtime has no network fallback and must remain usable with network disabled. |
| FR-003 | Return a typed result: `PossibleThreatLanguage`, `NoThreatSignal`, or `NeedsReview(reason)`. Reasons include unsupported language, low confidence/ambiguous context, invalid input, model unavailable, and inference failure. |
| FR-004 | Treat outputs as textual signals only. Do not derive credibility, danger, guilt, legal status, or sender identity. |
| FR-005 | Keep original text immutable; link each suggestion to the evidence/event ID, model pack digest/version, calibration version, and relevant source span when available. |
| FR-006 | Expose model output for explicit user review; never auto-alert contacts, generate legal conclusions, auto-save a notification, or block saving based on a negative score. |
| FR-007 | Keep existing rule cues independent. Either signal can create a review suggestion; a low model score cannot erase a rule cue or user concern. |
| FR-008 | Add explicit model readiness and unsupported-language states. Missing/corrupt assets or runtime errors degrade to “analysis unavailable” while preserving import, evidence viewing, and existing rule-based analysis. |
| FR-009 | Keep inference bounded and cancellable, off the UI thread, with text/token limits from configuration and no text in logs/telemetry. |
| FR-010 | Record user confirm/reject/edit separately from model inference. Corrections are not automatically used for retraining or threshold changes. |
| FR-011 | Require separate qualification for Malayalam, Hindi, Manglish and Hinglish before showing model-generated negative classifications for those inputs. |

## 5. Technical design

### Components and boundaries

- **`ThreatLanguageClassifier` interface** in `android/processing/analysis`: suspend API taking bounded normalized text plus language/locale metadata and returning a typed result. It must not depend on Android UI or vault storage.
- **`OnnxThreatLanguageClassifier` adapter** in an Android runtime module: owns ONNX Runtime session, model digest verification, input tensor construction, tokenizer, sigmoid decoding and deterministic cleanup. Keep the model behind an interface so tests and fallback operation do not require a large model binary.
- **Model pack manifest:** pin checkpoint repository/revision, artifact filename and SHA-256, tokenizer files and revisions, label order, preprocessing/token limits, output interpretation, calibration parameters/version, license/notice, and qualified language/domain. Fail closed on any mismatch.
- **Pipeline integration:** inject classifier into `TextAnalysis`/`EventBuilder` where each parsed message event is built. Do not put model work in `CandidateInbox` or listener service. Batch per-message inference where safe, while retaining one result per event and cancellation boundaries.
- **Persistence:** use the existing event suggestion/review model if it can preserve `basis=local_model`, model/calibration version and source anchor without schema ambiguity. Otherwise propose a minimal additive schema migration. Do not put raw message text or logits in diagnostics. Store score only if the model output is validated/calibrated and the UI needs it; retain the categorical result and its version regardless.
- **Review UI:** extend the existing event review screen/view model. Machine suggestion and user decision must be visibly separate, and export must use only user-confirmed language or clearly label model suggestions as unreviewed.

### Model and calibration policy

- Runtime candidate: the existing Qwen2.5 1.5B Instruct Q4_K_M GGUF. Use a bounded prompt and constrained structured output through `GenerationRequest`/`LlmEngine`. Generated output is an inferred suggestion, not a calibrated probability or Sakshi ground truth.
- Before any positive/negative user-facing classification, define an operational rubric: direct/conditional threat to harm vs quotation/reporting/denial, joking/fiction, vague intimidation, self-directed statements, and multilingual/code-mixed cases. `NoThreatSignal` may be shown only after a valid qualified calibration set establishes an accepted operating point.
- Evaluate per qualified language/domain on person/conversation-disjoint licensed data. Report precision, recall, false-negative rate, risk-coverage/abstention and slice support. Qwen-generated confidence is not a probability. Do not show a categorical negative result until evaluation validates it.
- If Qwen lacks sufficient performance, run error analysis and evaluate a better compact classifier or locally trained/distilled head. Do not treat the LLM as comprehensive threat detection.
- Reuse the user's Qwen pack through Sakshi's own `ModelManager` only after the app verifies supported identity/digest. Do not inspect/export app-private model contents through ADB or overwrite/delete the model. Pin and reproducibly prepare any llama.cpp source; no model binary in Git.

### Proposed result contract

```kotlin
sealed interface ThreatLanguageResult {
    data class PossibleThreatLanguage(
        val signal: ThreatSignal,
        val evidenceAnchors: List<SourceAnchor>,
        val modelVersion: String,
        val calibrationVersion: String,
    ) : ThreatLanguageResult

    data class NoThreatSignal(
        val modelVersion: String,
        val calibrationVersion: String,
    ) : ThreatLanguageResult

    data class NeedsReview(val reason: Reason, val modelVersion: String?) : ThreatLanguageResult
}
```

The final Kotlin contract must fit existing public type and naming conventions. A numeric confidence must not be presented as probability unless the manifest says the relevant language/domain was calibrated and acceptance data supports it.

### Resource and failure behavior

- Load lazily after explicit analysis, on a background dispatcher. Keep one bounded session; release on app lifecycle/low-memory as supported by current architecture.
- Cap raw text and model tokens, handle zero-length, malformed Unicode, giant inputs, and long-message chunking deterministically. Avoid max-over-window scores unless separately calibrated; preferably return `NeedsReview(INPUT_TOO_LONG)` for unqualified long text.
- On cancellation, do not persist partial outputs. On model/runtime failure, preserve originals and existing analysis, return a visible unavailable state, and permit retry.
- No inference content in logs, crash breadcrumbs, analytics, or notifications. Do not copy evidence into clipboard automatically.

## 6. Work plan and ownership for Luna

Implement as one integrated vertical slice after this spec is approved. The implementation agent owns production code and focused tests; the primary agent reviews the diff and judges system acceptance.

1. **Repository audit and contract fit:** inspect `TextAnalysis`, `EventBuilder`, event suggestions, persistence/review APIs, Gradle module graph and dependency policy. Report exact target files and any contract/migration blocker before editing.
2. **Typed classifier seam and result semantics:** add the classifier interface/result/refusal types; retain rules as independent suggestions. Add fake-classifier tests proving positive, negative, abstention and failure behavior without bundled model assets.
3. **Android local runtime and pack validation:** add ONNX Runtime Android integration only if current dependency conventions permit it; implement strict manifest/hash/label/tokenizer checks and bounded lifecycle. If exact checkpoint/tokenizer assets cannot be legally and reproducibly prepared, deliver a runnable explicit model-preparation workflow and clear unavailable UI state, not fabricated outputs.
4. **Pipeline and review integration:** invoke only in user-started analysis after text/event extraction; connect outputs to exact events/source anchors; add user review actions and preserve model-vs-user provenance.
5. **End-to-end validation and evidence:** run required Android JVM tests and project baseline, offline smoke test, artifact-integrity negative tests, and a user-path test from imported notification candidate through save, analysis, review and correction. A real Android device is required for runtime/latency/RAM evidence before calling device inference “working.”

### Likely files to inspect/edit (verify after audit)

- `android/processing/analysis/**`
- `android/processing/text/**` only where shared result/event contract requires it
- `android/core/vault/**` only for a necessary additive suggestion-provenance change
- `android/app/**` for dependency wiring, readiness state and review UI
- `android/**/build.gradle.kts` for a narrowly scoped runtime dependency
- `android/processing/analysis/src/test/**`, relevant app/review tests and test fixtures
- `docs/spec-driven/threat-language/**` for implementation evidence and loop status

Do not edit notification acquisition permissions/behavior, model binaries, user datasets, benchmark ground truth, export semantics, or unrelated screens without returning for scope review.

## 7. Acceptance contract

| ID | Scenario and observable pass condition | Evidence required | Blocking |
|---|---|---|---|
| AC-001 | User imports/saves a text notification and starts analysis; inference runs on-device after the action, and each result is linked to the correct event/source span. | End-to-end test/receipt with synthetic fixture; code path inspection confirms no listener callback inference. | Yes |
| AC-002 | With network unavailable, the same qualified input produces the same result within deterministic tolerance; no network request is attempted. | Offline Android test or instrumented device receipt; dependency/manifest inspection. | Yes |
| AC-003 | Model asset absent, hash mismatch, tokenizer mismatch, malformed outputs, or runtime error yields visible unavailable/needs-review state; evidence remains viewable and rules still run. | Automated negative tests and review UI state test. | Yes |
| AC-004 | Supported calibrated examples produce only the categorical signal supported by threshold; quote/report/negation/fiction and ambiguous context abstain or are not mislabeled per agreed rubric. | Frozen, licensed, person/conversation-disjoint evaluation set; confusion matrix, precision/recall, false-negative and abstention results. | Yes |
| AC-005 | Unsupported Malayalam/Hindi/code-mixed cases return `NeedsReview(UNSUPPORTED_LANGUAGE)` until separately qualified; no English-only negative label is shown. | Language-routing tests and fixture matrix. | Yes |
| AC-006 | User can confirm, reject, edit or mark insufficient context; changes persist as user decisions and never overwrite model output/original text. | Review flow test and stored-record assertions. | Yes |
| AC-007 | A negative model result never deletes evidence, hides rules/user concern, prevents export of confirmed evidence, or produces a “safe” label. | Integration assertions and UI copy review. | Yes |
| AC-008 | Input is bounded, inference cancellable, work off main thread, and no text/logits appear in logs or telemetry. | Boundary/cancellation tests, thread assertion, captured-log test or deterministic code review. | Yes |
| AC-009 | Exact phone inference pack installs/loads and runs; disclose cold/warm latency, peak memory, artifact size, supported Android/API/device, and limitations. | Reproducible real-device receipt with exact APK/model digests and model/runtime versions. | Yes for claiming “working on Android”; model quality gate remains separate. |
| AC-010 | Measured model quality is reported by language/domain/slice and does not equate model-card results, generated fixtures or synthetic examples with real-world threat accuracy. | Dataset provenance and evaluator report; no fabricated statistics. | Yes before user-facing threat/no-threat claims beyond an experimental suggestion. |

## 8. Non-goals and safety boundaries

- Credibility, imminent danger, legal determination, guilt, identity attribution, or emergency prediction.
- Automatic emergency contact, response, blocking, evidence deletion, evidence upload, or background notification inference.
- Reading private messenger databases, circumventing app locks/View Once/disappearing messages, decrypting content, or recovering missed notifications.
- Treating toxicity, sentiment, emotion, user-reported concern, or a generic LLM answer as a complete threat detector.
- Treating no signal, missing text, unsupported language or failed inference as “not threatening” or safe.
- Laya integration, cloud inference, automatic fine-tuning, automatic threshold adaptation, and automatic learning from user corrections.

## 9. Risks and unresolved decisions

1. **Quality/data blocker:** no qualified Sakshi threat-language evaluation set or accepted operating point is established. Qwen is an available runtime candidate, not a validated detector. Resolve with licensed conversation-disjoint data and rubric before enabling categorical negative results.
2. **Language blocker:** Qwen's advertised multilingual ability does not establish Hindi/Malayalam/romanized mixed-language quality. Abstain in unqualified slices.
3. **Runtime blocker:** installed APK lacks its llama native library; exact phone GGUF identity and actual Android generation are not yet verified. Pin and reproducibly prepare the runtime without modifying the user's existing model.
4. **Context:** a single message may quote, report, deny or joke about a threat. Adjacent context may be absent; absence must lead to uncertainty, not invented intent.
5. **State persistence:** confirm current suggestion schema can version model outputs and preserve decision history. Any schema change requires migration/rollback evidence.
6. **Product wording:** UI should say “possible threat language” and “no threat signal found,” with a persistent note that automated analysis can be wrong. No severity scale until separately specified and validated.

## 10. Decision gate

This document freezes the approved behavior and acceptance criteria. The user approved implementation on 2026-10-03. Luna implemented the current engineering slice; the primary agent owns independent diff review and acceptance judgment. Build and test evidence, plus remaining AC gates, are recorded in `LOOP.md`.
