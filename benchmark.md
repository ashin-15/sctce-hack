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

## M2: offline baseline harness — completed, full adapter matrix remains open

- Added one-command component runners, fresh candidate processes, socket-blocked/local-only inference, one warmup and five repeats, median/p95 CSV output, high-water working-set/RSS sampling, exact asset/source/data hashes, versions, commit/dirty state and per-run hardware metadata.
- Added 1,514 inventory entries spanning requested families, VAD variants, planned LLM quant/runtime/output combinations and export plans. Inventory counts are NOT execution counts. Missing adapters are pending, not unsupported-model skips.
- Implemented sixteen baseline candidate adapters plus scoring/report generation. Full weighted ranking refuses unknown hard gates/missing dimensions instead of inventing battery or integration values.
- Eighteen tests passed, including tamper/reorder/truncation detection, exact-quote checking, validation operating points, offline guard, user-review gate and real laptop process interruption/recovery of a synthetic hash queue.
- Verification found and fixed two harness bugs: NumPy vocabulary indices were not JSON serializable; initial screenshot sampling accidentally omitted the text test split. Failing regression tests reproduced both. Screenshots were regenerated with 84 train / 24 validation / 60 test fixtures, covering every language in the held-out set. No OCR result from the defective initial fixture selection is used.
- Current limits: load time is a single observation; per-language throughput can be pooled; model size excludes app/runtime packages; current queue is single-worker/fixture-only. Native Android, natural code-mixed speech, all neural/LLM adapters, exports, escalation and five actual stacks remain open.

## Branch integration: main into master

- Integrated committed benchmark history from `main` (`586e4bf`) into existing `master` (`7718e72`) using a merge of the two independent histories, without rewriting either history.
- Preserved existing research, presentation and dataset files on master. Resolved the AGENTS.md add/add conflict by retaining Sakshi's complete operating contract and appending the benchmark verification guidance.
- At this initial merge, uncommitted runner fixes and preliminary outputs were intentionally excluded. The subsequent M3a publication below now adds those completed results.

## M3a: measured baseline publication — completed subset, not the full matrix

- Audited and summarized fourteen full applicable-data candidate runs: three classifier baselines, script language ID, template extraction, sender/time linking, SQLite AES-GCM, Argon2id, four integrity schemes, ReportLab and the export-parser fixture. Component CSVs, per-run metadata/trials/predictions, descriptive Pareto plots, language error counts and RECOMMENDATION.md contain actual laptop observations.
- On the synthetic held-out fixtures, measured macro-F1 was 0.4063 for TF-IDF/logistic regression, 0.9851 for authored rules, and 0.9938 for their ensemble. The rules know the fourteen generation templates; these scores do NOT establish real-world harassment accuracy.
- Weighted scores/top-three shortlists and stacks A–E remain withheld: integration, energy/thermal, some licences and native measurements are missing. Empty plot panels and pending stack rows are intentional, not zero-valued results.
- RapidOCR and faster-whisper base (with/without Silero VAD) passed one-item smoke subsets, each with one warmup and five repeats. Smoke rows are tagged and excluded from full-data summaries. Full OCR/STT results are still pending at this milestone.
- The offline guard exposed an implicit RapidOCR visualization-font download; supplying explicit local weights and a local font fixed it without allowing inference networking. PyAV 19 was incompatible with faster-whisper 1.2.1; the reproducible lock pins PyAV 15.1.0.
- OneDrive repeatedly denied environment replacement. With user-approved access, installed all sixty-four hash-locked dependencies outside OneDrive at %LOCALAPPDATA%/SakshiBench/venv. Twenty tests passed in that environment; the codec and metadata-link regressions passed, and offline OCR/STT smoke runs completed.
- Fixed CSV links to point to committed metadata rather than ignored raw output; the publication audit checks five finite timed runs, one warmup, predictions and device fields. Link repairs do not change measured numeric values.
- The CSVs preserve dependency versions, source/data/model hashes and dirty-source status of the original runs. Initial measurements were taken during development; future full runs should use the committed source version. Battery consumption and thermal state are not inferred from laptop timings.
- Ingestion permissions/effort/policy notes are documentation assessments, not native feasibility tests. Natural Hinglish/Manglish audio, native-speaker label/font review, realistic multi-message linking fixtures, neural/LLM/export adapters and Android energy/lifecycle validation remain open.

## M3b preparation: fixture and integrity audit — completed

- A failing font-coverage regression identified twenty-three screenshots whose selected font lacked U+1F61F. Implemented glyph-checked local font fallback, recorded every used font and its SHA-256, and regenerated the 168 screenshots. The text, extraction and public-audio fixtures are unchanged. Glyph availability is verified; native-speaker visual shaping review is still pending.
- A failing integrity regression reproduced duplicate-padding ambiguity in the original Merkle root, including the empty/single-empty-entry boundary. Merkle v2 now commits to the entry count with a separate root domain prefix. Existing M3a measurements remain historical and are not silently replaced; the revised method needs fresh timings.
- Twenty-two tests passed after these fixes. Full OCR, full faster-whisper base with/without VAD, fresh Merkle timings, and the extra laptop proxy pipeline are the next measurement jobs. No results for those jobs are claimed at this preparation milestone.

## Temporal-patterns research verification - 2 October 2026

- Completed the temporal research handoff: primary-source metric cross-checks, event-schema/example validation and a desktop/390px browser check in dark/light themes. Reproduction commands and receipts are in `research/verification/temporal-patterns-README.md`.
- All 84 synthetic replay combinations passed; filtering, theme/disclosure controls, navigation targets and page/SVG bounds passed, with no runtime exceptions or remote requests. Fixed the suggested "recurrence" search returning no algorithm rows.
- Draft 2020-12 schema validation passed with date-time checks; the synthetic example had zero errors and six malformed variants were rejected. Cross-record application invariants are still engineering work.
- Repository suite: 20 tests run, 19 passed and one optional faster-whisper decoder test skipped because the runtime is absent. No inference dependencies or existing measured results changed.
- This milestone establishes artifact verification only. No temporal engine, Android runtime, detection-quality, latency, RAM, battery or real-evidence benchmark was executed. Android evidence acquisition remains the first engineering milestone; temporal implementation and its acceptance tests are specified in report sections 14 and 13.4.
