# Execution loop

State: implementation-complete-acceptance-open

Current plan review: GPT-6.1 Sol completed documentation-only native implementation refinement on 2026-10-03. User approved implementation on 2026-10-03. `NATIVE_QWEN_RUNTIME_PLAN.md` section 10 owns the executable native design. Candidate source pin is b6500 / a7a98e0fffed794396b3fbad4dcdbbc184963645; preparation/build/device model digest remain unverified. The primary agent froze the analysis-run persistence contract in `AGENT_PLAN.md`; Luna implementation is active.

## Current loop 1 - specification and handoff

- **Objective:** Define bounded local threat-language analysis for incoming user-authorized text and prepare GPT-6 Luna implementation contract.
- **FR/AC:** FR-001..FR-009; AC-001..AC-010.
- **Assignments:** Primary agent drafted and froze the specification. User approved implementation on 2026-10-03. GPT-6 Luna owns LUNA-01; primary agent owns integration judgment.
- **Dependencies:** Exact licensed model pack and qualified data/rubric remain blockers for threat/no-threat quality claims, not for safe classifier seam/fallback implementation.
- **Outputs:** `IMPLEMENTATION_PLAN.md`, `PRD.md`, `TECH_DESIGN.md`, `ACCEPTANCE.md`, `AGENT_PLAN.md`.
- **Checks/evidence:** Read existing project scope, Android text analysis, candidate inbox and review wiring; cross-checked existing local-AI research and multilingual requirements.
- **Judgment:** The proposed plan is actionable as an engineering slice, but currently available English Jigsaw model evidence does not support Malayalam/Hindi classifications or a real-world safety claim. Define abstention and validation gates accordingly.
- **Risks:** Model license/provenance, Android artifact parity, calibrated operating threshold, dataset adequacy, migration compatibility, and physical-device evidence are unresolved.
- **Next state:** implementing.
- **Next action:** Luna implements LUNA-01 within the approved contract, then reports all required fields; primary agent inspects the diff and verifies acceptance evidence.

## Loop 2 - revise runtime requirement after device inspection

- **Objective:** Identify the model and confirm whether the installed Sakshi APK can run it before native-runtime implementation.
- **FR/AC:** Existing FR-001..FR-009 and AC-001..AC-010 remain the broad approved behavior.
- **Assignments:** GPT-6.1 Sol performed read-only model identification. GPT-6 Luna implementation task is paused pending the revised technical plan approval.
- **Evidence:** Device is CPH2695, `org.sakshi.app` v0.1.0, arm64. The repository preset names Qwen2.5 1.5B Instruct Q4_K_M. The installed APK lacks `lib sakshi_llm.so` and llama runtime. `SessionServices` text analysis uses the rule engine only. `AiModelViewModel` substitutes deterministic fallback while presenting misleading readiness. Exact private device model file digest remains unverified.
- **Outputs:** `NATIVE_QWEN_RUNTIME_PLAN.md`; revised implementation/technical plan; no production code changed.
- **Judgment:** Feature cannot call Qwen through the installed APK today. A real pinned llama.cpp Android build and honest readiness states are prerequisites. User asked for a plan after this gap was confirmed.
- **Risks:** Runtime source/license/build provenance, preserving device model data, native memory/latency, no qualified threat-language evaluation, and unsupported language quality.
- **Next state:** implementation-active after explicit user approval.
- **Next action:** Luna implements the approved native runtime and user-initiated threat-analysis slice; primary agent reviews its evidence and remaining gates.

## Loop 3 - Sol executable native design review

- **Objective:** Turn the native runtime draft into a concrete implementation sequence for Luna.
- **FR/AC:** FR-001..FR-009; AC-001..AC-010 unchanged.
- **Assignments:** GPT-6.1 Sol owns this documentation pass; primary agent owns approval, ownership amendments and judgment.
- **Outputs/files changed:** `NATIVE_QWEN_RUNTIME_PLAN.md` and `LOOP.md` only.
- **Checks/evidence:** Read current LLM bridge/engine/UI, STT native preparation/build/session manager, analysis/event construction and insert-only database/review contracts; inspected official llama.cpp release, commit, C API, license and CMake/Android documentation. No compilation or tests were run for these documentation edits.
- **Results:** Explicit source pin, arm64 build/symbol isolation, cancellable JNI ownership, UTF-8 and chat formatting, global lock across load/run/free, precise source anchors, additive persistence and real-device receipt steps defined.
- **Judgment:** Technical review complete; runtime compatibility, actual device model identity and threat quality remain unproven. This is planning evidence only.
- **Risks:** Source candidate compatibility, absent trusted model digest, CPH2695 resource/cancellation limits, migration approval and qualified multilingual data.
- **Next state:** implementation-active after user approval and ownership amendment.
- **Next action:** Continue LUNA-01 with the persistence contract frozen in `AGENT_PLAN.md`; primary agent validates integration and acceptance evidence.

## Loop 4 - LUNA-01 implementation checkpoint

- **Objective:** Implement the approved native Qwen runtime, explicit user-initiated incoming-text analysis, honest readiness states, source-linked review suggestions and additive persistence.
- **FR/AC:** Engineering implementation addresses portions of FR-001..FR-009 and AC-001..AC-010; none of the quality or device gates are declared accepted here.
- **Files changed:** Android app model/review/session wiring; core database schema, migration and tests; core vault persistence; analysis classifier seam and tests; LLM JNI/CMake/Kotlin runtime, model manager and tests; `android/tools/prepare-llama.sh`; source/runtime/implementation plan status notes. See the working tree diff for the exact list.
- **Checks/evidence:** Prepared llama.cpp b6500 at commit `a7a98e0fffed794396b3fbad4dcdbbc184963645` and verified tree `b611e5e7692c49a935fa09fc9c90a6512466db09`. Passed `./gradlew :processing:llm:testDebugUnitTest :core:database:testDebugUnitTest :core:vault:testDebugUnitTest :processing:analysis:testDebugUnitTest :app:compileDebugKotlin :processing:llm:externalNativeBuildDebug` from `android/` on 2026-10-03. This built the arm64 native library and app Kotlin sources; it did not run model inference on a device.
- **Review fixes/checks:** Added explicit eligibility gates restricting Qwen inputs to plain-text/export events with selected-text, selected-export or notification sources. OCR and transcript tests verify classifier is not called. Classifier cancellation now records `cancelled` analysis runs and writes the evidence/event batch under `NonCancellable`; a regression test checks persisted event, run status and support state. Passed `./gradlew :processing:analysis:testDebugUnitTest :processing:llm:testDebugUnitTest :app:compileDebugKotlin` from `android/` after these fixes.
- **Concurrency/prompt review fixes:** `LlamaCppEngine.generate` now waits under `NonCancellable` for native worker completion after signaling cancellation, so session teardown cannot release the shared inference lock while JNI still decodes. `close()` also waits for any active native call before freeing the model. A lock contention regression test passed. Prompt control-token escaping now matches each full token and advances by its exact length; host C++ regression assertions pass for 10-byte `<|im_sep|>`, 11-byte `<|im_meta|>`, 12-byte `<|im_start|>` and 10-byte `<|im_end|>` plus adjacent following characters. The command `./gradlew :processing:llm:testDebugUnitTest :app:compileDebugKotlin :processing:llm:externalNativeBuildDebug` passed after these changes.
- **Results:** Added constrained JNI inference, cancellation/error states, exact model filename/path checks, a user-triggered classifier seam, strict output/quote validation, source-anchored unreviewed suggestions, encrypted/versioned run persistence and truthful model readiness. Fake-classifier analysis tests cover a source-linked positive and uncalibrated no-signal without a negative finding. App code and native source compile.
- **Judgment:** Reviewable implementation slice, not release acceptance. The connected device's Sakshi-owned model readiness and Qwen generation have not been proved via the app; weights were not fetched or inspected. A configured preset or native build is not evidence that this device can load the exact GGUF.
- **Open acceptance gates:** AC-004/AC-010 evaluation and calibration; AC-005 supported multilingual coverage (script/lexical refusal is conservative but incomplete); AC-008 real cancellation/resource behavior on device; AC-009 Sakshi-owned model load and smoke test. AC-001/AC-002 require an end-to-end device inference receipt for runtime/offline proof. AC-003 needs fuller app-visible failure-state exercise. AC-006 uses existing review actions and separate user annotations, but no dedicated text-edit UI was added. AC-007 has no negative category or safety label; reviewer must verify source immutability and overflow/cross-event behavior from the diff/tests.
- **Risks:** Exact model bytes/provenance are unverified; no trusted expected digest is configured. Quality, calibration, multilingual performance, device memory/latency/thermal behavior and JNI cancellation remain unknown. The model output is an experimental language suggestion only.
- **Next state:** Primary agent independent diff/privacy/UI review; implementation remains active pending its review and open acceptance evidence.

## Loop 5 - primary integration review

- **Review:** Verified the native cancel path waits for JNI completion before session teardown, prompt control-token escaping advances by exact matched token length, and Qwen is gated off OCR/audio paths. Regression tests cover those boundaries.
- **Additional build evidence:** `./gradlew :app:assembleDebug` passed from `android/`. APK contains `lib/arm64-v8a/libsakshi_llm.so`; extracted library LOAD segments use 16 KB alignment (`0x4000`). APK zip alignment was not checked because `zipalign` is unavailable in this environment.
- **Device install/startup:** The debug APK installed successfully with `adb install -r` on connected CPH2695 (arm64), and `org.sakshi.app/.MainActivity` became the resumed activity. No uninstall or data clear occurred. This was a startup/package smoke only; no model screen, analysis request or user evidence was opened.
- **Repository hygiene:** `git diff --check` passed. No em dash characters in the reviewed implementation/docs. No model weights, private phone model files, or user evidence were read or added.
- **Python baseline:** `python -m unittest discover -s bench/tests -v` ran 14 tests but errored in three test modules because installed Python 3.14 environment lacks `scikit-learn` and `Pillow`; one optional Whisper dependency test skipped. This benchmark-suite result is unrelated to the Android implementation and is not a pass.
- **Judgment:** Native source, Kotlin wiring, app compilation and APK assembly are verified. Runtime inference on the connected phone remains unverified because the exact Qwen file was not confirmed through Sakshi's model manager and no app-mediated model smoke was run. Do not describe the device feature as validated or release accepted.
- **Next state:** Implementation complete; device/model and quality acceptance gates remain open.
