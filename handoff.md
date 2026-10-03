# Handoff: local Qwen threat-language implementation and device verification

Date: 2026-10-03
Repository: `/home/ashin/Hackathon/sctce-hack`
Status: **Runs on the device. First passing isolated device run on 3 October 2026. Not calibrated and not release accepted.**

## User intent and authorization

Implement offline incoming-text threat-language analysis using the Qwen2.5 1.5B model already on the connected Android phone. The owner asked on 3 October 2026 to continue this work and get the feature working.

## Current result

- The earlier `inference_failed` was the duplicate `llama_sampler_accept` in the JNI loop. It is removed and grammar-constrained generation works.
- The classifier now asks the model to sort a message into six kinds and maps them to the three stored statuses; a guard sends quotes that sit inside quotation marks to `needs_review`.
- `QwenThreatDeviceTest` passes on CPH2695 with nine synthetic fixtures: three English threats flagged with exact source quotes, ordinary, greeting and insult messages without a suggestion, quoted speech to review, Romanized Hindi refused. Results and timings are in `benchmark.md` ("Android local Qwen threat-language suggestions").
- Known false positive: "I could kill for a cup of tea right now." is labelled a possible threat. The fixture records it and does not gate on it.
- About 33 s per message on this phone. Do not describe the feature as validated, calibrated or release accepted.

## Automatic analysis (added 3 October 2026)

Owner decision: analysis may run without a button, but only once the vault is unlocked. `android/app/.../analysis/AnalysisQueue.kt` is created in `SessionServices`, started at unlock and closed at lock. It analyses saved text in active cases one item at a time; pictures, recordings and notes are not started automatically. A lock in the middle of a run writes nothing and the item runs again after the next unlock. JVM tests and one real-Qwen device test pass; see `benchmark.md`. Still to build: the status line with pause and stop, a settings switch, battery and thermal guards, and one model load per batch.

## Next steps

1. End-user check on the phone (needs the owner to unlock): share a text into Sakshi, run analysis, confirm the suggestion and its quote on the review screen, accept or reject it.
2. Malayalam and Hindi, native script and Romanized, are refused today, so threats in those languages are not flagged. Qualifying them needs labelled synthetic data and native-speaker review (owner question Q6).
3. Speed: measure where the 33 s goes (per-run 1.1 GB hash, model load, prompt evaluation, per-token grammar pass over the full vocabulary) before changing anything.
4. A labelled evaluation set for false-positive and false-negative rates; figures of speech are the first known weakness.
5. Device cancellation, memory, battery and thermal checks.

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

Fixtures: nine synthetic messages listed in the test and in `benchmark.md`.

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

## Remaining acceptance limits

- No calibrated threat/no-threat quality, multilingual performance, real-world safety or legal claim is supported.
- Malayalam/Hindi and Romanized/code-mixed quality are unqualified; current classifier conservatively abstains for detected unqualified language signals.
- No full end-user unlock/import/analyze/review UI workflow was completed this turn.
- No device cancellation/resource/battery benchmark was completed this turn.
- Build reports existing Gradle deprecations and inability to strip `libsakshi_llm.so`; assembly still succeeds. Do not silently suppress warnings.
- Do not commit models, audio, private evidence or keys.

## Temporary tooling

`/tmp/sakshi_verify_ui.py` is a restricted UI-label/tap helper created this turn. It uses a temporary UI XML at `/data/local/tmp/sakshi-verify-ui.xml`. Neither contains a private vault extraction. They were not cleaned before the user requested stop.

## Operational constraints

Follow repository AGENTS instructions. Keep inference offline and app-owned. Do not extract private app databases or model weights through ADB, clear app data, uninstall the production app, change user grants or inspect private messages. Use synthetic evidence and supported user-mediated acquisition. Preserve original text and Unicode code-point anchors. Findings are reviewable suggestions, with explicit unknown/abstention states.
