# Sakshi benchmark — milestone ledger

## M0: repository and resource audit

Completed: cloned the empty requested repository; verified Python, Git, baseline scientific packages, and the laptop hardware. No inference service is used.

- Host: Lenovo 83ER, Intel Core i5-12450H, 16,857,817,088 bytes physical RAM, Windows 11 Home Single Language 10.0.26200.
- Android: unavailable. Target is a hypothetical 6 GB CPU-only phone; a laptop RAM constraint is NOT an Android emulator or a predictor of battery/thermal behaviour.
- Download permission: at most 5 GB. Public speech data requested; FLEURS dataset card lists CC-BY-4.0. Downloaded assets will retain source URLs, hashes and attribution.
- Thermal sensors: not yet measured; unknown is not equivalent to cool.
- No model measurements yet. No accuracy, latency, energy, or stack recommendation is asserted.

## Milestones

1. M1: deterministic synthetic text, screenshot and extraction datasets; public speech import with provenance and validation.
2. M2: offline runner, complete requested candidate inventory, metrics, measurement metadata, scoring, and tests.
3. M3: execute obtainable laptop candidates within budget; publish actual CSVs, plots, weaknesses and provisional recommendations.
4. M4: integration/degraded-mode tests and final completeness audit. Native Android work and unimplemented model adapters remain explicitly open when unavailable.

A milestone is recorded as complete only for work actually performed. This ledger is updated and pushed with each milestone. The complete model/runtime matrix cannot be represented as completed by a handful of baselines.
