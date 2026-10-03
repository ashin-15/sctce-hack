# Requirement plan: run Sakshi's Qwen model for threat-language review

**Status:** User-approved and implemented as a buildable engineering slice; real-device inference remains unverified
**Date:** 3 October 2026
**Technical plan owner:** GPT-6.1 Sol; primary agent owns approval and delivery judgment.
**Scope:** Restore real on-device Qwen2.5 1.5B inference in Sakshi, then call it from user-initiated incoming-text analysis.

## 1. Goal

Use the Qwen2.5 1.5B Instruct model already provisioned for Sakshi on the connected Android phone to produce a local, source-linked suggestion about possible threat language in incoming text. The feature must call the real Qwen runtime. A model file existing on disk, a canned fallback, or a desktop smoke test does not count as model inference.

The product result is a review aid. It can describe text that appears to express direct or conditional intent to harm. It cannot establish credibility, ability, proximity, imminent danger, sender intent as fact, guilt, or legal status. Users can confirm, reject, edit, or mark the suggestion as insufficient context. A negative result never labels a message or person “safe.”

## 2. Verified current state

| Finding | Evidence | Limit |
|---|---|---|
| The app source defines `Qwen2.5 1.5B Instruct (Q4_K_M)` with filename `qwen2.5-1.5b-instruct-q4_k_m.gguf`. | `android/processing/llm/.../ModelManager.kt`, `QWEN_2_5_1_5B`. | Preset configuration does not prove the exact bytes present on the phone. |
| Sakshi imports model files into app-private `files/models`; `ModelManager` hashes files and lists them. | `ModelManager(context)` and its import/hash methods. | The investigation did not read app-private device data or verify the phone file's name/hash. |
| `LlamaCppEngine` and `NativeLlmBridge` define a JNI interface and call `System.loadLibrary("sakshi_llm")`. | `android/processing/llm/.../LlamaCppEngine.kt`, `NativeLlmBridge.kt`. | Interface declarations alone do not provide a native implementation. |
| Installed APK version 0.1.0 targets arm64 and contains no `libsakshi_llm.so` or llama runtime. | Read-only inspection of the installed APK on device CPH2695. | No model generation test occurred. |
| `SessionServices.textAnalysis` uses `RulesEngineFactory.default()` and does not invoke the LLM. | `android/app/.../SessionServices.kt`. | This establishes current source wiring only. |
| The model screen uses `DeterministicFallbackEngine` when native LLM is unavailable, yet currently sets `isRunning=true` and presents misleading readiness text. | `android/app/.../AiModelViewModel.kt`; fallback implementation. | It means the UI's current “running” state does not prove Qwen ran. |
| There is no installed public OPlus/Qwen provider API identified in the read-only package/APK inspection. | Installed package list and APK library inspection. | Does not rule out undocumented/private vendor capabilities; Sakshi must use a supported public interface. |

Device queries did not open model weights, app-private user data, or notification contents. Keep the same boundary during implementation. The app itself may report file readiness and digest through `ModelManager` after the user opens the model screen.

## 3. Proposed user flow

1. User imports text or selects and saves an observed notification excerpt to an active case. Notification collection remains unchanged and performs no inference.
2. User starts analysis from the app. Sakshi checks runtime readiness and verifies the model through its own `ModelManager`.
3. If Qwen is not present, hash verification fails, the native library is missing, model loading fails, or resources are insufficient, show a precise “local model unavailable” state. Preserve evidence and continue existing rule analysis.
4. If ready, acquire the shared heavy-model lock, send each bounded event's text as quoted untrusted evidence to Qwen, and request one constrained result. Do not run analysis on the UI thread.
5. Validate the returned structure, allowed enum, evidence quote as an exact substring, and anchor-to-event mapping. Treat malformed, unsupported, ambiguous, truncated, or uncertain output as `NeedsReview`.
6. Persist the model suggestion/status with model/runtime/version provenance, separate from user review. The UI labels any positive result “Possible threat language - AI suggestion” and shows the supporting source text.
7. User confirms, rejects, edits, or marks insufficient context. The decision is a separate immutable review decision. It does not alter the original or the model's output.

## 4. Runtime requirements

### RQ-001: Real Android native engine

- Supply the implementation for `libsakshi_llm.so` through Android NDK/CMake and the JNI methods declared in `NativeLlmBridge`.
- Use a pinned llama.cpp source revision compatible with the Qwen2.5 GGUF and current JNI call contract. Record repository URL, immutable commit, license, build flags and source digest.
- Follow the STT module's explicit preparation pattern: a dedicated `android/tools/prepare-llama.sh` fetches/verifies source; Gradle never downloads native source as a side effect. The source preparation command is explicit and reproducible.
- Build `arm64-v8a` first, matching the connected CPH2695 phone. Keep CPU inference as the baseline. Do not claim GPU/NPU acceleration without a separate verified backend.
- Disable native networking, curl, server features and unnecessary examples/tests in the app library. Keep compiler warnings enabled and address warnings in owned code.
- Ensure ELF load segments support 16 KB Android page-size alignment. Record native library SHA-256 in the device receipt.

### RQ-002: Model identity, loading and protection

- Reuse the model already selected by the user through Sakshi's `ModelManager`; expected model is the Qwen preset. Do not replace, delete, copy out, or redownload the existing GGUF.
- At runtime, inspect the file using app-owned code: explicit selection, canonical app-private path, regular file, non-zero bounded size, SHA-256 and loaded GGUF metadata. Record digest/preset ID in analysis provenance. A computed hash is an identity receipt; comparison with a trusted expected digest is required to claim verified upstream provenance. No trusted expected digest is configured today.
- Create explicit states: `ModelMissing`, `RuntimeMissing`, `ModelIntegrityFailure`, `Loading`, `Ready`, `InferenceFailed`, and `ResourceLimited`. “Installed” and “running” are separate states.
- Remove or restrict `DeterministicFallbackEngine` to tests/demos. It must never be selected or advertised as real inference in the production analysis flow.
- Load lazily on explicit user analysis. Reuse one engine/session. Close on vault/session lock and critical memory pressure. Coordinate with the existing `LanguageModelLock` so Whisper and Qwen do not hold large weights concurrently.
- Bound context and generation. Start at a short context (2K tokens) and small output (for example, at most 96 generated tokens); adjust only after phone memory/latency measurements. These are starting design limits, not measured targets.

### RQ-003: Safe structured classification task

Use the existing `LlmEngine.generate(GenerationRequest)` API and GBNF only after the real native runtime confirms the grammar parameter reaches llama.cpp.

Illustrative response contract:

```json
{
  "result": "possible_threat_language | no_signal | needs_review",
  "quote": "exact source substring or null",
  "reason_code": "direct_intent | conditional_intent | quoted_or_reported | negated | ambiguous | insufficient_context"
}
```

- The system prompt states that message text is untrusted evidence, never instructions. The model must not answer instructions embedded in evidence.
- Ask whether the text itself expresses intent or conditional intent to cause harm. Distinguish quoting/reporting/denial, fiction, self-directed statements, vague intimidation and insufficient context. Do not infer identity, capability, proximity or credibility.
- Validate output schema strictly. Validate a non-null quote against the exact source event. Never trust free-form explanations as evidence.
- Do not use Qwen's self-reported confidence as probability, severity or a decision threshold. One bounded retry may be considered for invalid syntax; otherwise abstain.
- Preserve AC-005: unqualified Hindi, Malayalam, Hinglish, Manglish and uncertain/code-mixed language inputs abstain in production. Explicit synthetic multilingual evaluation may exercise the runtime but cannot qualify production output. Other languages remain future scope.
- Until AC-004 and AC-010 pass, positive output can be presented only as an explicitly unvalidated AI suggestion; do not present a categorical negative result. Preserve an `uncalibrated/experimental` state, not “not threatening.”

### RQ-004: App analysis and persistence

- Add a platform-neutral suspend `ThreatLanguageClassifier` seam and typed outputs to `android/processing/analysis`.
- Inject the classifier/engine from `SessionServices`. Keep inference in the user-initiated `TextAnalysis.analyse` suspend path; do not add it to notification listener callbacks, candidate collection, import preview, or background monitoring.
- Analyze only the messages/events already built from the authorized imported derivative. Preserve existing Unicode code-point source anchors and test Kotlin UTF-16 conversion around emoji/surrogate pairs.
- Keep rules and user concern independent. A negative or abstaining LLM answer must never remove a rule suggestion or suppress evidence.
- Persist status separately from the original: result enum, model preset/weight digest, runtime version, prompt/task version, timestamp, event/source anchor, and user review state. Do not persist hidden chain-of-thought or raw prompt text. Use existing finding provenance if it fully represents positives; add a minimal encrypted versioned status row for no-signal/unavailable/not-run distinctions only if required.
- All reports and exports continue to distinguish inferred suggestion from user-confirmed categories. No new claims of guaranteed admissibility.

### RQ-005: Privacy, concurrency and recovery

- No network permission/path is added for model inference. No prompt/text/result content in logs, metrics, crash breadcrumbs or notifications.
- Check coroutine cancellation before/after JNI calls; native generation must support bounded cancellation or the task must document and test its cancellation latency. Free native handles exactly once.
- Convert native errors/OOM to typed refusal outcomes without losing or mutating evidence. Avoid keeping two multi-gigabyte models resident together.
- Migration must be additive, tested with the current database and prior schema, and preserve existing review history. Failure to initialize the new native runtime must leave rules, evidence browsing, review and export usable.

## 5. Options and recommendation

| Option | Benefit | Cost / risk | Decision |
|---|---|---|---|
| A. Package pinned llama.cpp JNI runtime and reuse Qwen in Sakshi | Uses the model already selected and stored by the user; one offline path; reuses current `LlmEngine` contract. | Native source/build, ABI/NDK work, memory and quality validation; model file identity still needs in-app verification. | **Recommended.** It is the only repository-supported route consistent with current Qwen preset and local-only app boundaries. |
| B. Call a vendor/OPlus private or undocumented API | Could avoid bundling weights/runtime. | No supported public API has been identified; vendor-specific access is unverified, brittle and may cross app isolation boundaries. | Do not pursue unless a documented public Android API is identified. |
| C. Retain deterministic fallback when native runtime is missing | JVM demo tests remain easy. | It is not inference and currently creates a false “model running” claim. | Remove from production routing; keep only as a test double. |
| D. Replace Qwen with compact ONNX threat head | Smaller specialized classifier may offer more repeatable finite labels. | Different model/provisioning decision, English-only candidate, unvalidated domain quality, and no Android tokenizer integration. | Deferred. Reconsider only through a separate evidence-based model decision. |

## 6. Implementation sequence

1. **Truthful runtime readiness:** correct model screen states, stop claiming fallback is real inference, and add tests for runtime-missing/model-missing states.
2. **Pinned native source and engine:** add source preparation script, NDK/CMake arm64 build, JNI implementation, page alignment, strict initialization and output/error contract. Verify APK actually contains `lib/arm64-v8a/libsakshi_llm.so`.
3. **Real generation smoke:** use synthetic prompt through the same `LlmEngine` path on an Android device with the existing app-managed Qwen pack. Capture model digest from app code, native library/APK digests, load success, short generated response, cold/warm latency and peak-memory proxy. Never expose private data.
4. **Threat-language task:** add bounded prompt, grammar, parser, quote/anchor validation, typed abstention and focused synthetic unit cases; keep negative as non-reassuring/unqualified until evaluation.
5. **Analysis wiring:** connect through `SessionServices`/`TextAnalysis` after user starts analysis, with `LanguageModelLock`, saved-message event mapping, source anchors and independent rule results.
6. **Review UI and persistence:** expose possible signal and explicit status, review choices, provenance, correction history and accurate export semantics. Add the minimal migration if current tables cannot represent analysis states.
7. **Acceptance:** run required Kotlin/JVM/app checks, offline/static native checks, model-specific Android smoke test and end-user path on CPH2695. Complete the licensed, conversation-disjoint quality evaluation before exposing categorical no-signal output.

## 7. Blocking proof and acceptance gates

- **Runtime gate:** app-generated receipt proves `LlamaCppEngine.status == Ready`, a real output came from JNI/llama.cpp, and no `DeterministicFallbackEngine` was involved.
- **Pack gate:** app code records expected model preset, actual file size/hash and successful load. Device ADB inspection of private app files is not an allowed substitute.
- **Offline gate:** airplane-mode/model task completes with no network requests; APK contains only required local native dependencies.
- **Safety gate:** malformed/truncated output, embedded prompt instructions, quoted threat, explicit/conditional threats, denial/negation, self-harm reference, fiction and missing context are handled by validated schemas/refusals and user review.
- **Quality gate:** licensed person/conversation-disjoint data; per-language precision, recall, false-negative rate, risk coverage/abstention and slice support. No invented performance target or synthetic-only claim.
- **Device gate:** record device/API/ABI, artifact digests, model/runtime/task versions, cold and warm latency, RAM/thermal conditions and failures. Build success is not runtime evidence.

## 8. Deferred and unresolved

- Exact GGUF bytes currently on phone: user reports installed; preset filename/quantization are configured, but private file hash/metadata has not been verified through Sakshi itself.
- Android compatibility of the selected immutable llama.cpp source baseline; source preparation/build remain unexecuted.
- Qwen threat-language quality, calibration, Malayalam/Hindi/Manglish/Hinglish operating point and false-negative tolerance.
- Whether no-signal results need a dedicated encrypted table or can be represented safely by existing event/finding structures.
- Real-device memory/latency envelope on CPH2695.

## 9. Approval and task handoff

This plan supersedes the prior MiniLM implementation choice for the current task. It uses the user's existing Qwen2.5 model and requires real llama.cpp integration because the installed APK lacks the native library. The previous approval covered the broad feature scope, but the newly discovered native-runtime work and revised model choice should be approved before production edits continue. After approval, GPT-6 Luna receives the updated `AGENT_PLAN.md` task and may implement the sequence above. The primary agent owns diff review and acceptance judgment.

## 10. Executable native design - Sol review

These details refine RQ-001..RQ-005. The user approved this plan on 2026-10-03. Source preparation and arm64 compilation are now verified; no model hash verification through Sakshi or real generation occurred.

### 10.1 Source preparation and arm64 build

Use **candidate** llama.cpp release `b6500`, immutable commit `a7a98e0fffed794396b3fbad4dcdbbc184963645`, from `https://github.com/ggml-org/llama.cpp`. The upstream release resolves to that commit; it is an API-reviewed starting point, not an Android compatibility result. The source carries the MIT license. Record notices for bundled dependencies separately. Sources: [release](https://github.com/ggml-org/llama.cpp/releases/tag/b6500), [commit](https://github.com/ggml-org/llama.cpp/commit/a7a98e0fffed794396b3fbad4dcdbbc184963645), [license](https://raw.githubusercontent.com/ggml-org/llama.cpp/b6500/LICENSE).

Implement `android/tools/prepare-llama.sh` and a tracked source lock/notice file. The explicit script fetches the exact commit into a temporary directory, verifies `HEAD`, records the tree ID, checks out detached source and atomically installs it into ignored `android/third_party/llama.cpp`. Reject a conflicting/dirty destination rather than silently deleting it. A rerun verifies the existing source tree, not merely a marker. If using an archive instead, record its measured SHA-256 and origin before freezing the script; no archive checksum has been measured in this plan. Gradle/CMake never fetch source or models. Missing or mismatched preparation must fail with the explicit command to run.

Mirror the existing STT toolchain: NDK `29.0.14206865`, CMake `3.31.6`, minimum API 26, `arm64-v8a`, `c++_static`. Add `src/main/cpp/CMakeLists.txt`, `sakshi_llm.cpp` and a native symbol export map under `android/processing/llm`; configure `externalNativeBuild` for target `sakshi_llm`. Build optimized CPU code and PIC static llama/ggml dependencies. Set `BUILD_SHARED_LIBS=OFF`, `LLAMA_BUILD_COMMON/TESTS/TOOLS/EXAMPLES/SERVER=OFF`, `LLAMA_CURL=OFF`; disable GGML native-host tuning, OpenMP, dynamic backends, RPC, GPU backends, BLAS, KleidiAI and optional dependency downloads. Enable CPU and baseline arm64 instructions only. Reject unused or obsolete CMake options after inspecting the configure output. The pinned [CMake options](https://raw.githubusercontent.com/ggml-org/llama.cpp/b6500/CMakeLists.txt) support a library-only build; [upstream Android instructions](https://raw.githubusercontent.com/ggml-org/llama.cpp/b6500/docs/android.md) describe NDK cross-compilation. Exact flags/toolchain compatibility still require a build.

Apply `-Wall -Wextra -Wpedantic -Werror` to owned JNI code and retain upstream warning settings. Link with 16 KB load-segment alignment. Inspect both ELF alignment and APK packaging alignment using installed NDK/build tools; flag settings alone are insufficient proof. Whisper and llama bundle different ggml revisions in separate libraries: hide static dependency symbols with an export map and `--exclude-libs,ALL`, export only JNI registration/entry points, and verify no ggml symbol interposition occurs when both libraries load. Stop and report upstream/toolchain defects rather than disabling warnings or replacing the pin silently.

### 10.2 JNI and real cancellation contract

Replace the nullable-string-only bridge with typed load/generation outcomes. Freeze the Kotlin/JNI signature together before implementation: initialization returns a managed handle or error; generation accepts distinct system/user strings, grammar, token budget, stop sequences and a request identifier; cancel targets that identifier; close is idempotent; metadata reports source commit, model description, quantization metadata and context settings. A missing method/library must produce `RuntimeMissing`, never a success.

The selected [upstream C API](https://raw.githubusercontent.com/ggml-org/llama.cpp/b6500/include/llama.h) exposes model/context loading, vocabulary tokenization, chat templating, grammar sampling, CPU abort callbacks and model-load progress callbacks. Implement against that exact header, avoiding deprecated APIs. Validate every return code and buffer size.

Native ownership uses a handle registry and reference-counted session objects rather than trusting arbitrary pointer values. Each object owns the model, context, sampler and atomic cancellation flag. Initialize backend once per process. Close marks the object unavailable and requests cancellation; it frees resources only after active generation returns. Cancel must never wait on the generation mutex. Catch native allocation/other exceptions at the JNI boundary and convert them to stable error enums. Reset request-local sampler/KV state after every event so earlier evidence cannot leak into later inference.

Coroutine cancellation requires a separate watcher that can run while JNI blocks. Follow `SttProcessor`'s watcher pattern, run generation on a dedicated background dispatcher and signal native cancel from another execution path. Check the atomic flag between prompt batches and generated tokens; use CPU abort and load-progress callbacks for long work. On cancellation, rethrow `CancellationException` after cleanup rather than returning success. Bound startup and inference with a monotonic deadline; initial **proposed** ceilings are 60 seconds for load and 30 seconds per generation, with a device-tested cancellation response target of two seconds. These are engineering limits, not measured performance. A failure to meet the cancellation gate requires rework or a revised approved contract.

Use standard UTF-8 byte arrays at the JNI boundary; Java modified UTF-8 helpers do not safely represent every emoji/NUL case. Assemble token pieces into complete UTF-8 before decoding. Enforce byte/output budgets and report truncation as failure. Forward grammar into the actual grammar sampler before selection, then validate JSON again in Kotlin. A token-limit ending is not a completed result.

### 10.3 Model session and truthful readiness

`LlamaCppEngine` currently loads in its constructor. `AiModelViewModel.refreshInstalledModel` currently hashes and creates the engine synchronously. Move hashing, metadata inspection and model loading to the manager's background dispatcher. Opening the screen displays file/runtime states and does not automatically allocate weights. “Installed,” “runtime available,” “model loaded,” and “last real inference succeeded” must have distinct meanings.

Select the preset file explicitly through `ModelManager`, not the first arbitrary `.gguf`. Exclude `.tmp`, enforce a canonical path inside `modelsDir`, reject symlinks/oversized or malformed files, and serialize imports/deletion with active leases. Hash before loading and record actual metadata. No expected upstream hash is configured today: first observation may establish a local identity baseline, but must say `provenance_unverified`. A later mismatch fails without deleting/replacing the model. If the file is not already imported, show the existing user-mediated picker flow; do not extract another app's private model.

The existing lock is ineffective across all paths: STT uses `InferenceLock`'s **process-global** mutex, but `LlamaCppEngine` constructs a **per-instance** lock, and constructor loading happens outside it. Introduce one LLM session manager that acquires the same global heavy-model lock across hash/check, load, all event generations and free. Remove the nested engine-level lock or inject an explicit already-held lease to avoid deadlock. Default to unload in `finally` at the end of a user analysis operation; reuse within that operation only. Release model weights before releasing the global lock. Route the model screen's smoke action through this manager too. Vault lock, navigation cancellation and memory pressure request cancel and deferred release; they must never free an in-flight handle or block the main thread.

### 10.4 Qwen input and task wiring

The current engine concatenates system and user prompts with a newline. Replace this with the loaded model's supported chat template and assistant generation prefix; validate a versioned Qwen ChatML fallback only if metadata lacks a usable template. Tokenize the complete formatted input using the actual vocabulary and reserve room for output. Remove the current `length / 3` context estimate. Start at context 2048, prompt batch 128 and output 96 tokens; these are proposed resource caps. If a supporting quote cannot fit, return review/truncation rather than silently cutting it.

Treat evidence as JSON-escaped data within the user role. Prevent literal ChatML control-token strings inside evidence from becoming role boundaries by escaping/reserving them and testing their tokenization. Do not alter stored evidence: build quotes/offsets against the original event body. Prompt instructions request the defined enum/reason/quote only; no confidence score or free-form reasoning. Prompt isolation and grammar do not prove immunity to prompt injection, so adversarial examples and abstention remain required.

Place a platform-neutral suspend classifier interface in `processing/analysis`; implement the Qwen adapter in `processing/llm` or app wiring without circular module dependencies. In `TextAnalysis`, enrich `BuildResult.Built` after `EventBuilder` returns and before saving; keep `EventBuilder` synchronous. For this slice, only saved text/notification excerpts/visible snapshots and parsed text exports are eligible; OCR/audio expansion is deferred. Classify each event body span separately, exclude known outgoing export messages, and keep unknown direction explicit. Never send a whole export as one event. Bound the queue, expose progress/cancel and record unprocessed events as not-run when the budget is reached.

Find the quote only inside the event body. Reject empty, nonmatching or multiply occurring quotes unless an independently validated locator disambiguates them. Convert UTF-16 indices to half-open code-point spans and offset into the derivative. Preserve the existing evidence hash convention and body references; do not attach model-file hashes as evidence hashes. Append a separate pending category assessment and reference when positive; retain existing rule assessments even when their label is identical. Unsupported-language, uncertain extraction and resource failures produce explicit review states. Preserve AC-005 and avoid language guesses based only on Latin script for Hinglish/Manglish.

### 10.5 Persistence, delivery slices and evidence

Existing insert-only finding/review tables support positive suggestions and separate user decisions. They do not provide a natural record for completed no-signal versus unavailable versus not-run. Add the smallest encrypted append-only `threat_analysis_run` record linked to case, event revision and derivative, with request identity, status/reason, model digest/metadata, runtime/prompt version and timestamps; no raw prompt, hidden reasoning or extra evidence copy. Validate atomic positive finding/run insertion, cancellation cleanup, stable retry identity and unchanged review history. Freeze this DTO/schema with the primary agent before edits. Add a Room migration from the current schema with no destructive fallback; old rows mean not-run. Binary rollback must not open a newer unsupported database or erase it.

Luna implements serially: (1) source/build plus honest readiness; (2) real JNI generation/cancellation and session lifecycle; (3) constrained classifier and source anchors; (4) minimal persistence/review and analysis integration. Every slice reports files, commands, outcomes and remaining ACs. Expand `AGENT_PLAN.md` ownership to the exact new database/schema paths before persistence work. Do not delegate overlapping files concurrently.

Required evidence after implementation:

- JVM tests for missing runtime/model, invalid enums/quotes, Unicode, token-budget refusal, language abstention, independent rules, retries and immutable decisions; migration checks on prior schema.
- Native/instrumented checks for library load, real generated output, grammar enforcement, malformed GGUF, repeated requests/KV reset, cancellation during load/decode, memory pressure, double close and simultaneous Whisper/Qwen requests. Use isolated synthetic data; failure cases must not corrupt the user's provisioned model.
- Update the existing app with the same package/signature using an install that preserves data. Do not uninstall/clear storage, pull weights or access private evidence with ADB. Test in an isolated synthetic case through import, analysis and review. App-owned receipt provides model hash/metadata; preserve the pre-existing model before and after.
- Record source commit/tree, NDK/CMake/compiler flags, APK/library/model digests, Android API/ABI, device memory/thermal conditions, cold hash/load/generation times, repeated warm generation times, process PSS/peak sampling method, cancellation latency and failures. Process PSS is a whole-process proxy, not exact model RAM.
- With the model already provisioned, verify the user path with network disabled. Manifest/static dependency checks and airplane mode are complementary evidence. Capture logs containing synthetic requests and confirm evidence text/prompt/output are absent; only sanitized error codes and timings may be logged.
- Use existing Gradle verification tasks: focused `:processing:llm:testDebugUnitTest`, `:processing:analysis:testDebugUnitTest`, `:app:testDebugUnitTest`, `:app:assembleDebug` and applicable lint/device instrumentation tasks. Document unavailable tasks/toolchains rather than inventing passes. Keep `benchmark.md` current with observed measurements and AC status.

Runtime acceptance may pass while threat-quality acceptance remains blocked. Qwen's language support, generated synthetic examples and successful inference do not establish calibrated precision/recall. AC-004/AC-010 require the licensed disjoint evaluation and approved rubric. Exact phone model bytes/hash, toolchain build, cancellation/resource envelope and licensed quality data remain open evidence gates.
