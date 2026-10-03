# Acceptance: local threat-language suggestion

**Status:** Frozen and approved by user on 2026-10-03.

| ID | Observable condition | Required evidence | Blocking |
|---|---|---|---|
| AC-001 | User imports/saves an excerpt and starts analysis; inference runs after that action and each result maps to the correct event/source anchor. No inference occurs in listener callbacks. | Synthetic E2E path plus callback/code-path assertion. | Yes |
| AC-002 | With network disabled, a qualified input can be analyzed without any network attempt. | Offline device/instrumentation receipt and manifest/dependency inspection. | Yes |
| AC-003 | Absent/corrupt/wrong-hash model, tokenizer mismatch, malformed output or runtime failure yields visible unavailable/review state; evidence and rule analysis remain available. | Automated negative tests and UI state test. | Yes |
| AC-004 | Positive, negative and abstaining results obey a frozen rubric and calibrated threshold on licensed, conversation/person-disjoint evaluation data. | Per-language confusion matrix, precision, recall, false-negative rate, risk-coverage, slice support and provenance. | Yes before categorical claims. |
| AC-005 | Unqualified Hindi, Malayalam, Manglish and Hinglish inputs abstain; no English-only negative label appears. | Routing/unit tests plus language fixtures. | Yes |
| AC-006 | User can confirm, reject, edit or mark insufficient context. User decisions persist separately and do not modify original text or inference output. | UI flow test and persistence assertions. | Yes |
| AC-007 | A negative model result never hides evidence, suppresses a rule/user concern, labels content “safe,” or prevents review/export of confirmed evidence. | Integration assertions and copy review. | Yes |
| AC-008 | Inputs are bounded; inference is cancellable and off main thread; text/logits are absent from logs/telemetry. | Boundary, cancellation, dispatcher and captured-log checks. | Yes |
| AC-009 | Exact model pack and Android runtime install and execute on a real device. | Reproducible receipt with device/API, APK/model digests, versions, cold/warm latency and peak memory. | Yes to claim Android inference works. |
| AC-010 | Quality claims are limited to measured data/languages and distinguish model-card/Linux experiments, synthetic fixtures and Sakshi evaluation. | Evaluation report reviewed against provenance; no invented metrics. | Yes |

Pass on JVM fakes alone does not satisfy Android runtime or model quality acceptance. Synthetic examples test wiring and known edge behavior only; they do not establish real-world recall.
