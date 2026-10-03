# PRD: local threat-language suggestion

**Status:** Frozen, approved by user on 2026-10-03.

## Problem

Sakshi can preserve and analyze user-imported text and notification excerpts, but has no validated local model that offers a threat-language suggestion. Existing lexical cues are limited and context-insensitive. Users need an optional local signal that helps prioritize review without being told that a message is safe or that a person is dangerous.

## Goal and users

For a user reviewing their own imported or explicitly saved text evidence, run local analysis on demand and provide an evidence-linked suggestion: possible threat language, no threat signal found, or needs review/unavailable. Users decide whether to confirm, reject, edit, or add context.

## Requirements

- **FR-001:** Inference runs only after user-started analysis, never from the notification collector callback.
- **FR-002:** All inference is local; no cloud/network fallback.
- **FR-003:** Results are positive signal, narrow negative signal, or explicit abstention/unavailable.
- **FR-004:** Results indicate textual signals only, never credibility, danger, guilt or legal status.
- **FR-005:** Preserve original evidence and link suggestions to source/event anchors and exact model/calibration versions.
- **FR-006:** Review decisions remain user-authored and distinct from model outputs.
- **FR-007:** Existing rule signals/user concern remain independent and cannot be erased by a negative model score.
- **FR-008:** Missing/corrupt model assets and inference failures preserve all evidence and allow existing analysis to continue.
- **FR-009:** English-only assets cannot emit negative classifications for unqualified Hindi, Malayalam, Hinglish or Manglish input.

## Scope

Included: user-initiated inference of imported/shared text or explicitly saved notification excerpts, narrow language signal, evidence-linked review, offline runtime, model readiness/refusal states, and validation.

Excluded: automated threat response, danger or credibility prediction, automatic notification capture-to-save, private-app scraping, cloud inference, Laya, model retraining from corrections, and treating absent/unsupported text as safe.

## Success measures

1. In an offline end-to-end user path, a saved text excerpt receives one versioned result linked to the correct event/span, or an explicit abstention.
2. Failure to load the model does not block evidence review or rule analysis.
3. A qualified evaluation set reports per-language precision, recall, false-negative rate, and abstention/risk-coverage with provenance and uncertainty. No threshold or quality claim is accepted from synthetic fixtures alone.
4. A real Android device receipt records exact artifact/runtime versions, cold/warm latency, peak memory, and supported device/API scope.

## Assumptions and blockers

- **ASSUMPTION:** Analysis remains user-initiated in the current event review workflow.
- **BLOCKED:** No operational threat-language rubric, accepted threshold, or Sakshi-qualified evaluation set is frozen.
- **BLOCKED:** The proposed English Jigsaw threat output has no established Android parity or language quality here.
- **ASSUMPTION:** The first implementation can report abstention for languages without a qualified model pack.

## Decision log

- 2026-10-03: Use “possible threat language” and “no threat signal found”; prohibit “safe,” “credible threat,” or danger claims.
- 2026-10-03: Keep rules, user concern, and model output independent; preserve user review authority.
- 2026-10-03: Proposed compact ONNX baseline is a candidate only; Laya remains deferred per existing research decision.
- 2026-10-03: User approved this PRD and its paired technical/acceptance scope for implementation. This does not establish model quality or language qualification.
- 2026-10-03: User identified the on-phone model as Qwen2.5 1.5B. Sol 6.1 confirmed the matching configured Q4_K_M preset, but not the private device file's digest. Native runtime work is newly required because the installed APK does not contain llama.cpp.
