# Handoff: local Qwen threat-language implementation and device verification

Date: 2026-10-03
Repository: `/home/ashin/Hackathon/sctce-hack`
Status: **Stopped at user request. Implementation exists, but threat classification has not passed real-device verification.**

## User intent and authorization

Implement offline incoming-text threat-language analysis using the Qwen2.5 1.5B model already on the connected Android phone. The user requested GPT-6.1 Sol for model identification and native-runtime planning, and GPT-6 Luna for implementation. Planning and implementation were approved. The latest request is to stop and create this handoff. Do not resume implementation or testing until asked.

## Current result

- Native llama.cpp is packaged in the updated debug APK and loads on the phone.
- The installed GGUF now loads and produces nonempty real generation output in an app-owned instrumentation smoke.
- The first threat-classification fixture still fails: persisted status is `inference_failed`, expected `possible_threat_language`.
- Ordinary and quoted-message fixtures did not run because the first assertion stopped the test.
- **Do not report the feature as working, validated, calibrated, or release accepted.**

## Implementation already present

The working tree contains uncommitted implementation changes and new files. Preserve them; no commit was requested.

- `android/app/.../SessionServices.kt`: injects the Qwen classifier into user-initiated text analysis.
- `android/processing/llm/src/main/cpp/`: arm64 JNI runtime, CMake configuration, grammar-constrained generation, chat formatting, cancellation and bounded CPU inference.
- `android/processing/llm/.../model/LlmSessionManager.kt`: model validation, app-owned SHA-256 calculation, load/run/free under the shared inference lock.
- `android/processing/llm/.../analysis/QwenThreatLanguageClassifier.kt`: bounded physical-threat-language suggestions, exact source quote validation, explicit abstention and failure states.
- `android/processing/analysis/.../TextAnalysis.kt`: eligible text-event integration; OCR and audio are excluded; cancelled runs preserve event/run persistence.
- `android/core/database/` and `android/core/vault/`: additive Room v2 analysis-run persistence, immutable source events and separate review revisions.
- AI model UI: reports actual file/runtime readiness; no deterministic fallback presented as Qwen inference.
- Plans and prior evidence: `docs/spec-driven/threat-language/`, especially `NATIVE_QWEN_RUNTIME_PLAN.md`, `AGENT_PLAN.md`, `LOOP.md` and `ACCEPTANCE.md`.

### Runtime pin

- llama.cpp: b6500
- Commit: `a7a98e0fffed794396b3fbad4dcdbbc184963645`
- Prepared source tree: `b611e5e7692c49a935fa09fc9c90a6512466db09`
- Prepared sources: ignored `android/third_party/llama.cpp`
- Preparation script: `android/tools/prepare-llama.sh`
- CPU only; context 2048 tokens; prompt limit 1792; output limit 96; up to four threads.

## Connected device and model evidence

- Device: CPH2695, serial `YXDUHAJZ5DZPR8T8`
- Android API 36, ABI `arm64-v8a`
- App: `org.sakshi.app`
- App-managed filename: `qwen2.5-1.5b-instruct-q4_k_m.gguf`
- File size: `1117320736` bytes
- SHA-256 calculated inside app-owned ModelManager:
  `6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e`
- Native metadata: architecture `qwen2`, GGUF file type `15` (Q4_K_M), stored tensor count `1777088000`.
- Upstream model provenance remains unverified. Filename, architecture, count and digest establish local compatibility/identity observations, not authenticated upstream origin.
- APKs were updated using `adb install -r`. App data was not cleared, and the model was not pulled or replaced.

## Verification fixes made this turn

### 1. Quantization identity check

The initial native loader required the model description to contain `Q4_K_M`. Pinned llama.cpp renders this quantization as `Q4_K - Medium`, so the check incorrectly rejected the model after loading.

Changed `sakshi_llm.cpp` to validate `general.architecture` and numeric `general.file_type`, with a compile-time assertion for `LLAMA_FTYPE_MOSTLY_Q4_K_M == 15`. Added `model_identity.h` and host regression `src/test/cpp/model_identity_test.cpp`.

### 2. Stored tensor count guard

The original guard capped parameters at 1.7 billion. App-native metadata reported 1,777,088,000 stored elements, which failed the guard. Pinned `llama_model_n_params` returns stored tensor element count. The compatibility range now allows 1.3 to 1.9 billion, with the architecture/quantization gates intact. This is explicitly a compatibility guard, not model provenance verification. The exact reason this conversion has more stored elements has not been authenticated.

### 3. Safe load diagnostics

Native initialization now returns typed load-stage failure and safe metadata: parameter count, file type and Qwen architecture flag. Kotlin `NativeLoadResult`, `LlamaCppEngine.loadResult` and `LlmSessionUnavailable.metadata` preserve these diagnostics without exposing evidence text.

## First next fix to investigate when resumed

**Strong source-level diagnosis: the native generation loop accepts each sampled token twice. This has not yet been fixed or tested.**

In `android/processing/llm/src/main/cpp/sakshi_llm.cpp`, the loop calls:

```cpp
const llama_token token = llama_sampler_sample(sampler, session->context, -1);
// ... append token piece ...
llama_sampler_accept(sampler, token);
```

Pinned `android/third_party/llama.cpp/src/llama-sampling.cpp`, function `llama_sampler_sample` around lines 407-437, already calls `llama_sampler_accept(smpl, token)` before returning. The second call advances the grammar twice for one output token. The plain generation smoke has no grammar and succeeds; the classifier uses a JSON grammar and fails. This is a concrete integration defect and the leading explanation for the observed classification failure.

On authorized resume:

1. Remove the duplicate explicit acceptance after confirming the pinned sampler contract.
2. Add an appropriate regression for grammar generation/token acceptance and rerun the existing isolated device test.
3. If classification still fails, add safe typed generation-stage diagnostics. Do not log source prompts, evidence or arbitrary model output.
4. Run all three fixtures and check exact Unicode quote anchors, persisted results and immutable review revisions.
5. Preserve/check the model digest after verification, including failure paths if practical.
6. Update `LOOP.md`, `benchmark.md` and a durable device receipt with actual outcomes and timings. This turn's device results have not yet been added to those documents.

## Isolated device verification harness

File: `android/app/src/androidTest/kotlin/org/sakshi/app/QwenThreatDeviceTest.kt`

Opt-in test: `realQwenClassifiesSyntheticMessagesAndPreservesSources`.

- Uses the app-owned ModelManager and installed native runtime/model.
- Runs a real nonempty generation smoke with a short READY request.
- Creates a unique synthetic vault through a ContextWrapper with a separate `noBackupFilesDir`, an in-memory database and a unique unauthenticated test-only Keystore alias.
- Imports synthetic shared-text fixtures and invokes real TextAnalysis/Qwen classification.
- Checks persisted model identity, source text, exact quote linkage and review revision behavior.
- Cleans only its unique scratch folder and test key in `finally`.
- Does not open the user's real vault or bypass its authentication.
- The final size/hash preservation assertions currently run only after the whole fixture loop succeeds. They were not reached in the failing run.

Fixtures:

| Label | Synthetic text | Expected status |
| --- | --- | --- |
| direct | `🙂 I will kill you tonight.` | `possible_threat_language` |
| ordinary | `Can you bring the documents tomorrow?` | `no_signal_uncalibrated` |
| quoted | `She said "I will kill you" in the film.` | `needs_review` |

**Do not run the entire existing vault/device suite against this installed app.** Some existing test helpers clean the normal vault folder. Use this isolated test explicitly.

Commands, from `android/`, when resumed and authorized:

```sh
./gradlew :processing:llm:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e class org.sakshi.app.QwenThreatDeviceTest -e qwenDeviceVerification true org.sakshi.app.test/androidx.test.runner.AndroidJUnitRunner
```

ADB/Gradle may require sandbox escalation. Read instrumentation output: shell exit code 0 does not mean JUnit passed.

Host regression, from repository root:

```sh
g++ -std=c++17 -Iandroid/processing/llm/src/main/cpp android/processing/llm/src/test/cpp/model_identity_test.cpp -o /tmp/sakshi-model-identity-test
/tmp/sakshi-model-identity-test
```

## Latest measured run

Both app and instrumentation APK installs succeeded. Latest build and LLM JVM suite passed. Host identity regression passed.

Latest actual device instrumentation output:

```text
model;digest=6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e;size=1117320736;api=36;abi=arm64-v8a;elapsed_ms=6802
direct;status=inference_failed;elapsed_ms=21620;digest=6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e
Time: 33.914
Tests run: 1, Failures: 1
```

The model smoke elapsed time includes session hashing/loading and short generation, measured after an initial separate digest calculation. Fixture time includes analysis/session hashing/loading, inference and persistence. These are single-run observations, not a latency benchmark or accuracy measurement.

Earlier this turn, the same test failed with `MODEL_IDENTITY_MISMATCH` before the count guard fix. All instrumentation runs shown have finished. No verification command remains active at handoff. The test APK remains installed. App updates may leave the main app locked; do not bypass user authentication.

## Remaining acceptance limits

- Grammar-constrained threat generation is currently failing.
- No calibrated threat/no-threat quality, multilingual performance, real-world safety or legal claim is supported.
- Malayalam/Hindi and Romanized/code-mixed quality are unqualified; current classifier conservatively abstains for detected unqualified language signals.
- No full end-user unlock/import/analyze/review UI workflow was completed this turn.
- No device cancellation/resource/battery benchmark was completed this turn.
- Build reports existing Gradle deprecations and inability to strip `libsakshi_llm.so`; assembly still succeeds. Do not silently suppress warnings.
- No commits were made. Do not commit models, audio, private evidence or keys.

## Temporary tooling

`/tmp/sakshi_verify_ui.py` is a restricted UI-label/tap helper created this turn. It uses a temporary UI XML at `/data/local/tmp/sakshi-verify-ui.xml`. Neither contains a private vault extraction. They were not cleaned before the user requested stop.

## Operational constraints

Follow repository AGENTS instructions. Keep inference offline and app-owned. Do not extract private app databases or model weights through ADB, clear app data, uninstall the production app, change user grants or inspect private messages. Use synthetic evidence and supported user-mediated acquisition. Preserve original text and Unicode code-point anchors. Findings are reviewable suggestions, with explicit unknown/abstention states.
