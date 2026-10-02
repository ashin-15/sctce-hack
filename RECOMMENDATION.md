# Sakshi recommendation — provisional, not a final stack ranking

## Evidence boundary

Best fixture macro-F1 among executed baselines: rules-tfidf-ensemble (0.9938). This is not a validated real-world or Android winner.

The requested full candidate matrix and stacks A–E are not complete. All measurements are laptop proxies on authored fixtures/public read speech. Weighted scores/top-three shortlists are deliberately withheld because integration effort, battery/thermal and some model licences remain unverified. Do not infer Android seconds or battery use from these results. See results/SUMMARY.md and results/weaknesses.csv.

## MVP engineering hypothesis (requires validation)

Prefer explicit share/picker/text-export ingestion, serial OCR/STT stages, an independently validated small classifier applied to every message, source-bound template extraction/summaries, encrypted local storage, an externally anchored integrity root, and a mandatory user-confirmation gate before PDF. Start without an LLM; add one only after native memory and exact-quote faithfulness tests. Native ML Kit Latin/Devanagari plus a separately validated Malayalam fallback is a hypothesis, not a measured OCR winner. A phone STT engine is not selected by the extra laptop faster-whisper base test.

The executed TF-IDF/rules baselines are useful regression fixtures but insufficient for harassment decisions. The ensemble may improve recall while increasing false positives; inspect validation-selected thresholds and per-language errors rather than choosing by a single macro-F1.

## Fallback hypothesis

Manual transcript/text import, conservative rules-assisted highlighting, no generative summaries, user-selected verbatim quotes and user-confirmed dates/senders. Never silently drop unflagged evidence or equate negative victim sentiment with harassment. Evidence hashing remains enabled even when ML is disabled. PDF Indic shaping needs visual verification before export is offered to users.

## Riskiest assumptions / data to collect

- English: broader jokes, sarcasm, indirect threats and distress negatives; current rules know the generated templates.
- Hindi: misspelling, dialects, Hindi/English switches, negated/reported threats and native OCR; labels need native review.
- Hinglish: natural speech and spelling/transliteration diversity are absent from the audio set; avoid assuming romanized text is English.
- Malayalam: native-script OCR is not covered by bundled RapidOCR weights; font rendering and STT domain adaptation need review.
- Manglish: natural phonetic spellings and bilingual conversations, including benign uses of trigger vocabulary.
- Mixed: language boundaries, source attribution and quoted speech; semantic parallel-template leakage inflates baseline confidence.
- Current extraction fixtures give date/sender/platform to the extractor; they do not establish recovery of missing/ambiguous metadata.
- Current linking case labels are synthetic arbitrary groups; collect coherent multi-platform sender aliases, time-window boundaries and escalation trajectories.
- Read-speech FLEURS with white noise is not a voice-note harassment benchmark. Natural background noise, code-mixed speakers, and consented in-domain clips remain priorities.
- Public dataset attribution/consent documentation is not a substitute for app-user consent and legal review of intended usage.

## Highest priority real-device re-checks

On a named 6 GB Android phone record SoC, OS, battery state and thermal state for every run. Run one warmup + five repeats; use dumpsys meminfo/Android profiler and batterystats/Battery Historian. Verify serial unloading, 2.5 GB stage hard gate, load/TTFT/decode rates, 30 screenshots + 5 notes, four-GB degraded mode, battery saver, and OS-killed queue recovery. A laptop observed-RSS guard is NOT a four-GB Android cap. Verify offline model availability after cold launch in airplane mode, Keystore key wrapping, review-gated export, Indic font rendering, and native ingestion policy compliance.

## Open work

Implement and test all pending OCR/STT/LID/normalization/neural/embedding/LLM/linking/Android adapters; check exact model identifiers, commercial licences and supported runtime/quant combinations; export family winners to ONNX/TFLite int8 and measure accuracy deltas; manually review 30 LLM summaries; collect missing natural code-mixed audio; run the five actual shortlisted stacks. Pending implementation is not an allowable unsupported-model skip.
