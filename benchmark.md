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

## M1: data fixtures and preparation — completed with coverage gaps

- Generated and validated 600 synthetic multi-label messages in six language categories; fixed seed 1729; exactly 420/90/90 train/validation/test. Orthographic variants stay within their group/split.
- Rendered 168 synthetic chat screenshots with two layouts, light/dark modes, font/resolution/JPEG/blur/rotation variants. Windows Pillow lacked RAQM, so rendering now uses HarfBuzz + FreeType. Native-speaker/visual validation remains pending.
- Generated 100 structured extraction fixtures with exact source quotes. These are single-message threads; realistic multi-message annotation remains to be added.
- Downloaded CC-BY-4.0 FLEURS test data and prepared 60 complete-utterance concatenations, 10–120 seconds, English/Hindi/Malayalam, in clean/noisy pairs. Attribution, source IDs, transformations, and SHA-256 hashes are retained. Natural Hinglish/Manglish and harassment-domain audio are missing, NOT fabricated.
- Data smoke test passed; regenerated output is deterministic on this host. Image/audio binaries are ignored by Git and reproduced with explicit preparation commands; manifests and public ground-truth text are committed.
- Residual parallel-template leakage across languages and limited semantic diversity make these synthetic sanity checks, not evidence of production accuracy. Labels are authored, not native-speaker-adjudicated.
- Prepared local faster-whisper base assets at exact upstream revision `ebe41f70d5b6dfa9166e2c581c45c9c0cfc57b66`; vocabulary filename corrected after an actual 404. This is an EXTRA budget-friendly candidate, not a substitute for the requested small/large tests.
