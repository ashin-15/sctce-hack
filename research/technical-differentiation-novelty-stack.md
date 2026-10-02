# Sakshi: technical differentiation and Novelty Stack

Research snapshot: 2 October 2026.

## Executive conclusion

**Sakshi should differentiate through trustworthy evidence intelligence, not a longer feature list.** Local inference, harassment classification, adaptive detection, multimedia journals, encrypted vaults, timelines, RAG, citations, cryptographic provenance, and reviewed reports all have precedent. Even survivor-focused local-AI evidence apps have public prototype precedent.

The best-supported implementation opportunities in this review are:

1. **Capture-aware longitudinal analysis:** avoid interpreting changed notification visibility, duplicate captures, or retrospective imports as changed harassment frequency.
2. **Correction-aware evidence lineage:** every suggestion points to exact source spans; corrections recompute dependent patterns and reports without altering originals or silently replacing prior reports.
3. **Selective, independently checkable offline disclosure:** export only approved evidence and findings, preserve redaction lineage, and verify the package without Sakshi or a vendor account.

These are **Tier A differentiation candidates relative to the reviewed systems**, not proven inventions, patent novelty, or verified Sakshi capabilities. Their ingredients are established. Their value must be demonstrated by ablations and end-to-end tests. No candidate warrants a “world first” claim.

A defensible pitch is:

> Sakshi is an Android-first evidence intelligence workflow designed to work offline. It links pattern suggestions to source evidence, records known capture limitations, propagates user corrections, and produces selective, independently checkable reports. It describes observed evidence, not guilt or future violence.

## 1. Scope, method, and evidence standard

This report extends `research/existing-systems-survey.md`, the earlier 30-system landscape, with targeted searches for the combinations in the brief. Five angles were investigated: commercial survivor/evidence products; Android/local prototypes; longitudinal/multimodal/personalized research; local retrieval/model infrastructure; and Android/security limitations. Searches also sought direct counterexamples to proposed differentiation, including notification-based local classification, local survivor evidence apps, feedback adaptation, and portable provenance.

Primary sources were preferred: official product documentation, author repositories/model cards, conference/publisher records, NSF public-access records, Android documentation, and C2PA guidance. Vendor and developer descriptions establish a **documented claim**, not independently measured accuracy, security, deployment scale, or usability.

### Evidence labels

- **V - verified source statement:** the cited primary source explicitly describes the capability. It has not necessarily been executed or independently audited.
- **I - engineering inference:** a proposed mechanism or feasibility estimate derived from established components; not an observed product capability.
- **U - unknown:** not established in the inspected sources. Unknown does not mean absent.
- **Commercial:** an offered product/service with commercial documentation. This does not establish every feature's rollout, adoption, or efficacy.
- **Released tool:** distributed application/component, not necessarily commercial.
- **Prototype:** public project or academic implemented demonstration; not assumed production-ready.
- **Proposed method:** framework or design proposal without sufficient implementation/evaluation evidence for the specific claim.

**Operational definitions:** a timestamped list is not a temporal detector; toxicity is not longitudinal harassment; “escalation” here means a supported change in observed frequency/category, not prediction of physical harm; local storage does not mean local inference; citations do not prove entailment; hashes do not establish original authenticity or legal admissibility; user approval is not objective ground truth.

No Android device test, model execution, source-code implementation audit, packet capture, survivor study, or security audit was conducted. No public-source search can establish that a combination does not exist. Patent claim searches, archived product versions, and freedom-to-operate analysis remain outside scope. Reports and repositories are mutable; freeze source/model revisions before implementation. The strongest conclusions are positive precedent findings and concrete testable gaps, not negative market-exclusivity claims.

## 2. Precedent that materially changes the novelty claim

| Precedent | Exact documented capabilities | Maturity and processing boundary | Implication for Sakshi |
|---|---|---|---|
| Aimee Says | Uploaded-document search and pattern review; founder describes AI-generated tagged events, timeline, approve/modify, and attached screenshots/documents | Commercial web service; Android-local/offline execution and byte-level analytical lineage U [R01] | AI + longitudinal documentation + human review already exists. |
| Agent Hita | Android local conversation understanding plus temporal engine tracking urgency, secrecy, dependency, extraction; stores scored events, not raw messages | Public source-available Android project; MediaPipe/Gemma developer claim; no independent production validation established; acquisition uses accessibility, not notifications [R02] | Local AI + longitudinal risk signals already has direct precedent. Its deliberate raw-text non-retention differs from an evidence vault. |
| BullyAlert | Android-side classifier computation; adaptive classification accommodating guardian tolerance; preliminary usage analysis | Implemented academic Android system, MobiCASE 2020; social network acquisition, not proof of offline ingestion or modern Android compatibility [R03] | Local/mobile classification + feedback-based adaptation is not new. |
| Concepcion-Sanchez et al., 2017 | Background notification text/app/sender extraction; accumulate insufficient text in encrypted local database; local word/expression matching and fuzzy decision system | Academic smartphone prototype with reported experiments; publisher PDF accessed through indexed text because direct fetch returned binary; current platform behavior U [R04] | Notification ingestion + local intelligent classification + contextual accumulation + encrypted buffering predates Sakshi. Fuzzy logic is not a modern neural model, but defeats the broad combination claim. |
| BullStop, COLING 2020 | Mobile deep-learning bullying detection and user-configurable deletion/blocking; paper states Play availability | Academic system demonstration with historical public release; locality not established from abstract [R05] | Mobile harassment AI and interventions are established; do not merge it with unrelated similarly named newer apps. |
| SaakshiAI / TheCodeKage | Claims on-device voice-to-structured-incident processing using RunAnywhere/Qwen 2.5 0.5B, encrypted Android records, accumulated timeline, explicit PDF export | Public Android fork/prototype; execution, no-network guarantee, accuracy, original preservation, and multilingual performance U [R06] | A very close broad concept already exists publicly. Similar name is not evidence of affiliation with this Sakshi project. |
| SilentWitness, Devpost | Claims offline Whisper/DeepSeek reasoning, threat escalation, structured summaries, encrypted vault, disguise | Hackathon submission; source/performance evidence not verified; project started Dec 2025 [R07] | Even a claimed offline survivor escalation/vault combination is prior proposal/prototype precedent; marketing does not prove efficacy. |
| Silent Witness / lbfn83 | Photo/voice/text evidence, encrypted PWA records, structured AI analysis, JSON/report/media ZIP | Separate public hackathon project. Explicitly uses private Gemma/STT servers after abandoning browser-local inference [R08] | Local vault + AI evidence reports exists, but private server is not on-device. Do not conflate with the Devpost project. Its failed experiments illustrate integration tradeoffs, not Android benchmarks. |
| AnythingLLM Mobile | Android offline GGUF inference; local embedding, vector database, reranking and document citations; vision/memory and document exports | Released free/open-source Android product with Play/APK links; optional remote modes exist; evidence-vault cryptography U [R09] | Private local document RAG with citations is a deployed-product feature, not just an academic idea. |
| Tella | Offline capture/import, automatic encrypted storage, preserve import metadata, verification features, selective/user-controlled transfer | Released open-source Android/iOS evidence tool, optional servers [R10] | Offline encrypted evidence collection is established. |
| Proofmode and C2PA | Capture provenance, signed content bindings, ingredients/parent relationships, transformation assertions, validation, redaction concepts | Released tools and published standard; Android C2PA conformance advertised by Proofmode [R11, R12] | Hash-linked media lineage and verification are established mechanisms. Do not claim a new cryptographic provenance invention. |
| Axon Evidence | AI-powered capture/search/review/redaction/disclosure; uploaded-file fingerprints, action audit trail, encrypted cloud storage | Commercial enterprise evidence system [R13] | AI + evidence integrity + multimodal evidence management already exists outside consumer local-first apps. |
| TalkingParents | Chronological in-platform communication records, digital-signature PDFs verifiable in PDF readers, authentication codes | Commercial hosted co-parenting platform [R14] | Offline-verifiable signed reports alone are not novel; source platform and trust anchor differ. |
| UCD, 2020 | Session representations incorporating text/network/time; temporal inter-arrival prediction plus GMM bullying estimate | Evaluated academic method with MIT research code; Python 2.7/TensorFlow 1.12 environment, not Android [R15] | Multimodal temporal bullying analysis is prior art, not something introduced by LLMs. |
| XBully, 2019; temporal HAN, 2021 | Heterogeneous multimodal network; hierarchical word/comment attention with temporal information | Evaluated academic social-media methods; on-device survivor workflow U [R16] | Multimodal and temporal modeling have evaluated precedents; neither is a demonstrated physical-harm forecast. |
| PI-Bully | 2018 personalization framework proposal; 2019 work reports real-world dataset evaluation of user-specific/peer-influence detection | Academic proposal followed by evaluated research; not established private on-device feedback adaptation [R17] | Personalized harassment detection has research precedent, but importing personality inference is unnecessary and risky. |
| Laya | Typed choice/score/noul decisions, router, fine-tuning, ONNX path, calibration tools and optional abstention | Reusable component, not an evidence app or Android harassment benchmark [R18] | Typed output, uncertainty handling and classifier routing are existing techniques. |
| DravidianCodeMix / DravidianLangTech; HASOC | Offensive/hate-content datasets and multilingual/code-mixed tasks | Published research resources/shared tasks, not private longitudinal survivor evidence [R19, R20] | Indic/code-mixed abuse classification is not new. The gap is domain-appropriate, acquisition-realistic evaluation and usable mobile deployment. |

Commercially offered products establish broad combinations, academic systems establish methods, and prototypes establish narrower engineering/design precedents. **None of these inspected sources establishes the complete Sakshi target with independent end-to-end validation. This is a validation gap, not proof of uniqueness.**

## 3. Combination-by-combination verdict

| Requested combination | Existing precedent and status | What is still unestablished / potential differentiator |
|---|---|---|
| Local AI + longitudinal harassment analysis | Agent Hita explicitly describes local temporal engine; BullyAlert delegates adaptive classifiers to phones; UCD/HAN validate session/time modeling academically | Capture-aware estimates with evidence retention, user corrections and published Android/false-alert tests. Broad claim already precedented. |
| Local AI + private evidence RAG | AnythingLLM Mobile has offline document retrieval/citations; Aimee searches survivor documents but locality U | Encrypted, case-isolated, review-state-filtered retrieval with exact source/version references and answerability tests. Generic RAG already productized. |
| Local AI + human correction | BullyAlert adaptive guardian feedback; Aimee event editing (not established local); general local learning runtimes | Corrections propagate through temporal claims and selective exports, reproducibly, while originals stay unchanged. |
| Local AI + evidence integrity | Local evidence apps claim encryption; Tella/Proofmode provide local provenance; Axon provides AI + custody in cloud | Strict source-to-OCR/STT-to-label-to-pattern-to-report dependency verification on Android. Exact local-AI/integrity chain U, not proven absent. |
| Notification ingestion + local classification | 2017 fuzzy-system paper directly describes it; newer local-message prototypes also exist | Capture status, grouping/update deduplication, timestamp origin and unknown coverage influence temporal conclusions. Not a new acquisition mechanism. |
| Multimodal local analysis + incident timelines | SaakshiAI claims voice-to-record timeline; SilentWitness claims local speech/threat/vault; multimodal temporal research exists; Aimee timelines are commercial | Cross-modality alignment, no double-counting, uncertainty propagation, exact OCR regions/audio intervals, real phone performance. |
| Multilingual local harassment detection | Indic tasks establish classifiers; multilingual models/runtimes provide building blocks; prototype language claims are not metrics | Measured English/Malayalam/Hindi etc. performance on private-context and OCR/STT-corrupted evidence. All-language MVP not established. |
| Local personalization without cloud training | BullyAlert establishes phone-side adaptive classification; on-device learning runtimes exist; PI-Bully is research precedent | Reversible case-scoped preference/context memory, poisoning protection and held-out improvement without silently changing old reports. |
| Uncertainty-aware harassment detection | Fuzzy decisions, Laya abstention/calibration tools, selective-classification/conformal research | Separately represent capture, transcription, classification and pattern uncertainty, validated per language. Generic confidence/abstention is not new. |
| Privacy-preserving evidence intelligence | Tella local protection, Aimee evidence AI, Axon custody+AI, survivor local-AI prototypes, AnythingLLM private retrieval | Audited full workflow protecting derivatives/indexes/caches and producing bounded, user-approved disclosure. Broad phrase alone is superficial. |

## 4. Android feasibility: what can actually run

The following are engineering assessments, not Sakshi benchmarks.

| Subsystem | Feasibility on current Android | Hard boundary / implementation implication |
|---|---|---|
| User-mediated evidence import | High | Sharesheet/SAF/Photo Picker; copy input bytes into private storage, record import origin. Imported material can already be forged. Never extract private app databases. |
| Notification listener | High for exposed content, incomplete by design | User grants access. Posted/removed/connected/disconnected callbacks exist. Work-profile restrictions and low-RAM older-device restrictions apply. Android 15 redacts OTP-bearing notifications for untrusted listeners [R21]. No historical completeness or View Once recovery. |
| Latin/Devanagari screenshot OCR | High | ML Kit offers bundled on-device models; bundled versus dynamically downloaded must be explicit for first-use offline operation [R22]. |
| Malayalam/Tamil/Telugu/Kannada/Bengali OCR | Conditional | ML Kit v2 lists Latin, Devanagari, Chinese, Japanese, Korean, not these scripts. Tesseract supplies Indic traineddata [R23], but JNI packaging, chat-bubble reading order, fonts, resolution and speed require device tests. A language file is not proven screenshot accuracy. |
| Short audio STT | Feasible with quality/latency limits | whisper.cpp documents Android support. Its general unquantized estimates: tiny 75 MiB/~273 MB, base 142 MiB/~388 MB, small 466 MiB/~852 MB [R24]. These are not Sakshi or device-specific peak-RAM measurements. Use multilingual variants, not `.en`, for Indic work. |
| Compact text classifier | High after training/export validation | ONNX Runtime Mobile supports Android [R25]. Tokenizer, output heads, operator compatibility and quantized parity still matter. CPU baseline before vendor-specific acceleration. |
| Laya | Conditional; evaluate, do not assume | 322M multilingual / 421M English checkpoints. Python SDK does not run natively as a Kotlin dependency. Port tokenizer/schema/heads/calibration around ONNX. Desktop/T4 timings are not Android timings [R18]. |
| Local retrieval | High for a bounded corpus | Lexical search needs no neural model; small embedding vectors fit. 10,000 × 384 × 4-byte floats = 15,360,000 bytes (~14.6 MiB), excluding index, text, metadata and embedder. Protect all of these. |
| Small local LLM | Feasible selectively, device/model dependent | LiteRT-LM documents Kotlin/Android and CPU/GPU/NPU execution [R26]. Qwen2.5-0.5B is a real Apache-2.0 candidate [R27], not a proven harassment model. Use short bounded context/output and templates as fallback. |
| Temporal aggregation and lineage | High | Kotlin/SQLite bookkeeping is cheap compared with inference. Research-grade calibrated escalation or future-harm prediction is not cheap and lacks target data. |
| Encrypted vault and hashes | High for standard mechanisms; assurance takes substantial work | Keystore + AES-GCM, unique nonces, authenticated metadata, private storage, SHA-256; strong lifecycle/recovery policy. Hardware-backed keys are device-dependent [R28]. |
| Long video / always-on multimodal LLM | Poor hackathon default | Decode/frame sampling/audio processing/LLM together impose sustained RAM, battery, thermal and background-work constraints. User-selected short clips and sequential processing only as stretch. |

### Memory planning, not performance promises

Raw weight arithmetic: 322M parameters at one byte each is about 322 MB; at two bytes about 644 MB. A nominal 0.5B model at 4-bit is about 250 MB of raw weights. Actual files and resident memory include unquantized tensors, vocabulary/tokenizer, activations, runtime buffers, KV cache and app/media overhead. These numbers are lower-order planning estimates, **not guaranteed RAM budgets**.

For the hackathon, choose a named physical phone and test one active inference model at a time. A conservative 4 GB test tier can target text/OCR, rules and small-classifier processing; 6-8 GB can be tested for short STT and optional small LLM, but neither tier promises support. Small models can run and still be too inaccurate. Flagship throughput is not a mid-range guarantee; emulator timings are not phone battery/thermal evidence.

### Laya verification and limitations

The inspected repository and model cards identify ModernBERT-large + decision head for English (421M) and mmBERT-base + decision head for multilingual (322M). Outputs are typed `choice`, ordinal `score`, and `noul` yes-probability, with decision/action routing. Training uses RLCD; documented fine-tuning includes dataset creation, temperature fitting, evaluation and export. Repository and inspected English/multilingual cards advertise Apache-2.0; review pinned checkpoint/tokenizer/export terms before distribution [R18].

**The multilingual card's Limits section says it ships uncalibrated**, with mean confidence exceeding accuracy, and advises task-specific temperature fitting. It reports weak low-resource performance and ordinal position bias. Those metrics are for its stated benchmarks, not harassment or Sakshi. Proper-scoring-rule training does not guarantee calibration under domain shift. “No generated text” eliminates fabricated prose as a failure class, not wrong decisions.

The repository's 0.3.23 notes say per-tensor quantization is now the default because a per-channel export had only 32% decision agreement with eager execution. Do not follow older quantization instructions blindly. This is developer-reported evidence, not independently reproduced here.

Before choosing Laya: pin one checkpoint/export; validate tokenizer and schema parity in Kotlin; test ONNX operators and decision heads; compare float/quantized/Android logits, labels and abstention; evaluate target-language context; recalibrate after quantization; measure p50/p95 latency, peak memory and energy. Load one checkpoint for the MVP, not the full router family. No ready-made validated Android harassment checkpoint was established.

## 5. Ten potential innovations, with complete engineering assessments

Effort is relative: **Low** = established bounded plumbing; **Medium** = multiple components plus meaningful tests; **High** = difficult integration or data/evaluation bottleneck; **Very high** = research/user-validation program. These are not calendar estimates. Demo feasibility assumes a prepared Android shell and tested model assets; this workspace does not currently establish an implemented Sakshi app.

### I1. Capture-aware temporal analysis - Tier A candidate

- **Existing precedent:** 2017 notification/fuzzy accumulation [R04]; Agent Hita temporal engine [R02]; UCD and temporal HAN [R15, R16]. Missing-data and observation-window techniques are general established methods.
- **Novelty gap:** reviewed descriptions do not establish a survivor-owned offline temporal detector that explicitly tests whether notification visibility changes caused a trend. It is a narrower integration/evaluation gap, not invention of missing-data statistics.
- **Technical mechanism:** store source app/case, listener status, capture/import time, claimed event time, timestamp provenance, truncation/known-gap flags and revision IDs. Deduplicate notification updates conservatively. Track identical reimports by original hash; semantic duplicates are review suggestions, not automatic deletion. Count unique reviewed events within declared observation windows. Describe lower-bound observed counts; abstain from comparative frequency when coverage is materially incomparable. A connected listener is not proof that all messages were visible; never present a fabricated capture percentage.
- **Required models:** small text category classifier or Laya after validation; deterministic window/rule engine. No LLM required. Do not discard all “non-toxic” events before temporal review; repeated unwanted benign-looking contacts can matter.
- **Android feasibility:** high for bookkeeping; coverage visibility partial. Record crash/restart uncertainty because disconnect callbacks are not guaranteed to execute before process failure.
- **Data requirements:** timestamped conversation/event sequences, separate capture logs and known gaps; fixtures with grouping, updates, duplicates, muted previews, retrospective imports, clock/time-zone changes. Real context labels needed for accuracy claims.
- **Security implications:** provenance and status records need authentication; malicious imports can spoof dates/senders. Show identity/time as claimed unless independently established.
- **Privacy implications:** coverage logs and contact frequencies are sensitive even without text; encrypt and avoid telemetry.
- **Implementation effort:** Medium for rules/replay; High for convincing evaluation across device/app combinations.
- **Hackathon demonstrability:** strong. Replay constant incident rate with improving capture: naive counting produces a false increase, Sakshi marks insufficient comparability. Also replay a real category change with stable capture and show exact contributing evidence.
- **Main failure modes:** non-random missingness, undetected gaps, duplicate merging errors, calendar/clock artifacts, unknown group identity, and false reassurance when there are no observations. Cannot recover unavailable content or predict physical danger.

### I2. Correction-aware source-to-finding dependency graph - Tier A candidate

- **Existing precedent:** Aimee approve/modify events [R01]; BullyAlert feedback [R03]; C2PA ingredients/transforms [R12]; Axon audit trails [R13].
- **Novelty gap:** the exact offline chain from original bytes through OCR/STT, machine interpretation, user correction, temporal aggregate and report revision is not established in the inspected survivor tools. Provenance and event sourcing themselves are common.
- **Technical mechanism:** immutable source node; derivative nodes with parent hash, transform/model/tokenizer/schema version; finding nodes with exact derivative span or image region/audio interval, status and confidence; correction nodes with actor/action and separate user context; pattern nodes listing supporting findings; signed report snapshots listing their dependency versions. Corrections transactionally invalidate/recompute affected patterns and retrieval entries. Old reports remain snapshots marked superseded, not overwritten. Add explanation of which event change changed a pattern.
- **Required models:** OCR/STT and compact classifier; templates can generate explanations. Optional constrained local LLM cannot create valid source IDs outside the graph.
- **Android feasibility:** high for small graphs in SQLite/Room plus encrypted blobs. Offset handling needs Unicode-aware conventions and schema validation.
- **Data requirements:** source/derivative alignment pairs; correction histories; merge/split/time/speaker revision tests; adjudicated interpretations for accuracy evaluation.
- **Security implications:** hash/check all exported nodes and edges; append-only design is not guaranteed rollback/deletion detection against full-device compromise without an external trusted checkpoint.
- **Privacy implications:** correction histories reveal sensitive interpretation and identity; retain minimum necessary locally and disclose history only by choice.
- **Implementation effort:** Medium for text-only graph; High with multimodal alignment and robust migration/export.
- **Hackathon demonstrability:** strong. Reject one false event; counts/pattern explanation/current retrieval/report update, original hashes remain fixed, prior export is retained. Inject an invented source ID and reject it.
- **Main failure modes:** stale dependencies/indexes, offset drift, revisions applied to wrong cases, coercive corrections, user opinion rendered as observed fact, valid citations to unsupported interpretation. Structural validity is not semantic truth.

### I3. Selective offline disclosure with independent verification - Tier A candidate

- **Existing precedent:** TalkingParents signature-verifiable PDFs [R14]; Proofmode/C2PA bundles and redaction lineage [R11, R12]; Tella user-controlled transfer [R10]; Axon disclosure [R13].
- **Novelty gap:** selective disclosure of reviewed local AI findings alongside source/derivative/correction links, verifiable without the originating app, is less established here. Neither signing PDFs nor vendor independence alone is novel.
- **Technical mechanism:** preview selected case/events, approval state, exclusions and identity fields; create sanitized derivative copies and PDF/HTML report plus versioned manifest of selected files, hashes, lineage, model/review versions, omitted-parent status and declared time sources. Sign canonical manifest bytes using Keystore; provide a small offline verifier and signer fingerprint. Encrypt the archive using a reviewed packaging scheme with explicit recipient/key transfer. A redacted-only recipient can verify exported bytes and a signed lineage assertion, but cannot verify hidden original content or redaction correctness without access to it. Do not claim zero-knowledge disclosure.
- **Required models:** none for verification/export; optional local OCR/NER can suggest redactions, always reviewed.
- **Android feasibility:** high for bounded export/signing; safe redaction and compatibility are harder than a ZIP plus checksum.
- **Data requirements:** export/tamper fixtures including filenames, EXIF, thumbnails, PDF text layers, JSON quotes and hidden attachments; recipient interoperability tests.
- **Security implications:** signature verifies bytes against a key, not the owner's real identity. Self-contained public-key replacement can fool an unanchored verifier; independently compare signer fingerprint where identity matters. No trusted wall-clock time offline. Archive parser/path traversal protections matter.
- **Privacy implications:** omitted identities can leak through metadata, originals, quotes or deterministic hashes of guessable content. Avoid publishing a whole-vault hash inventory. Clearly warn if an original containing redacted information is included.
- **Implementation effort:** Medium for selected text export/hash verifier; High for safe multimodal redaction/encryption/trust design.
- **Hackathon demonstrability:** strong bounded demo on a second offline computer: verify selected bundle, mutate source/report/edge and fail validation; redacted-only bundle must explicitly report unavailable original verification.
- **Main failure modes:** metadata leakage, inclusion of unapproved findings, stale signature, key loss/substitution, harmful recipient selection, incomplete export interpreted as complete, false authenticity/admissibility claims.

### I4. Review-aware, evidence-bound local retrieval - Tier B

- **Existing precedent:** AnythingLLM Mobile offline RAG/citations [R09]; Aimee document search/pattern review [R01].
- **Novelty gap:** not RAG itself; retrieval respecting case isolation, review state, original/derivative versions, capture limits and evidence answerability.
- **Technical mechanism:** lexical baseline plus optional dense search; prefilter by case, access/review state and event-time interval before ranking. Keep observed text distinct from inferred labels. Return source snippets and adjacent context; structured queries compute counts in the database, not from top-k snippets. Optional generation is bounded to retrieved evidence; references validated by code; unanswerable questions return Unknown. Rejections of labels do not delete source text: distinguish source-search scope from approved-findings scope. Explain which scope was searched.
- **Required models:** none for lexical baseline; multilingual MiniLM 384-dimensional embedding candidate [R29], after language-specific tests; optional small local LLM [R27]. Embeddings are not anonymized data.
- **Android feasibility:** high at bounded corpus size; sustained indexing and simultaneous LLM/ASR residency conditional.
- **Data requirements:** case-linked query/relevant-span pairs, negative/unanswerable questions, code-mixed retrieval, quotations/negation and prompt-injection tests.
- **Security implications:** imported text is untrusted data; no tools/network/export controlled by evidence instructions. Verify model assets and isolate file parsers.
- **Privacy implications:** encrypt text, embeddings, lexical index, query history and caches; never silently route to remote provider. Device unlock/compromise remains a boundary.
- **Implementation effort:** Medium for local search/filtering; High for robust generation and index lifecycle.
- **Hackathon demonstrability:** good. Find approved evidence for a topic; refuse an unsupported fact; exclude another case; after correction, label-search results update while source search still finds original text.
- **Main failure modes:** top-k misses mistaken for absence, negation errors, stale indexes, cross-case leakage, model interpreting quotes as threats, hallucinated answers despite valid citations. Need entailment evaluation beyond citation coverage.

### I5. Pipeline-level uncertainty and abstention - Tier B

- **Existing precedent:** fuzzy decisions [R04], Laya calibration/abstention [R18], selective classification/conformal risk control [R30]. Specific new cyberbullying uncertainty papers found only in secondary indexes were not used as verified performance evidence.
- **Novelty gap:** separately exposed capture, extraction, language/domain, classifier and pattern uncertainty in a private evidence workflow; not invention of confidence or conformal methods.
- **Technical mechanism:** quality gates for absent/truncated inputs; preserve OCR/STT alternatives or quality flags; fit temperatures and decision/reject thresholds on separate grouped calibration data, per language/category where sample size permits. Report “needs review”, “unsupported language”, “insufficient observed context”, not one fabricated danger percentage. Optional conformal label sets require specified assumptions. Never multiply OCR confidence, classifier probability and capture estimates into a purported calibrated risk probability.
- **Required models:** classifier/Laya with measurable scores; extraction quality checks; no new large model. LLM self-reported confidence is not calibration data.
- **Android feasibility:** high for gating/postprocessing; obtaining reliable calibration under rare outcomes and distribution shift is difficult.
- **Data requirements:** adjudicated labels, disjoint calibration/test conversations, hard negatives, rare threat categories, OCR/STT corruption and domain-shift slices; preserve disagreement.
- **Security implications:** attackers can manipulate text/media to trigger abstention or confident errors; preserve original and show uncertainty instead of losing evidence.
- **Privacy implications:** calibrate on consented/local data; no compulsory upload to improve confidence.
- **Implementation effort:** Medium pipeline gates; High/Very high valid statistical claims.
- **Hackathon demonstrability:** good mechanics demo: degrade screenshot or withhold context and route to review. No statistical reliability claim from a handful of synthetic examples.
- **Main failure modes:** high-confidence errors, unfairly frequent abstention on minority languages, false negatives hidden by routing, non-exchangeable correlated conversations, label uncertainty, accuracy computed only on accepted examples without reporting coverage.

### I6. Indic/code-mixed, acquisition-realistic robustness - Tier B

- **Existing precedent:** DravidianCodeMix/DravidianLangTech Malayalam-English/Tamil-English/Kannada-English offensive content [R19]; HASOC English/Hindi/Marathi and Hindi-English code mix [R20]; multilingual Laya [R18].
- **Novelty gap:** a public, leakage-safe mobile evidence evaluation spanning context, Romanization, OCR/STT and pattern formation, not merely supporting another UI language.
- **Technical mechanism:** preserve original script; normalize only derivatives; test Unicode/romanization/code-switch/noise slices. Start English and Malayalam/Romanized Malayalam text, optionally Hindi; gate unsupported language/modality explicitly. Train/distill a compact multilingual classifier on licensed data and consented domain examples; calibrate and export. Evaluate clean-text versus extracted-text versus end-to-end pattern performance.
- **Required models:** multilingual encoder/Laya candidate, Indic OCR alternative where ML Kit lacks scripts, multilingual STT; model identity/support claims are not validated domain capability.
- **Android feasibility:** conditional by language/modality; text easiest, Indic screenshot OCR and low-resource speech harder. All ten requested language varieties at high accuracy is not a hackathon assumption.
- **Data requirements:** English, Malayalam, Hindi, Tamil, Telugu, Kannada, Bengali, Marathi, Hinglish, Romanized Indic and code-mixed/slang slices. Record source/language/modality/license/labels/context/real-versus-synthetic for each. DravidianCodeMix Zenodo lists CC BY 4.0; HASOC page lists protected downloads, not a verified redistribution license. Telugu/Bengali and private longitudinal datasets were not established by the inspected sources.
- **Security implications:** normalization can change evidential meaning; version it and retain byte originals. Split all captures/derivatives of the same evidence into one data partition.
- **Privacy implications:** public comments are not permission to collect survivors' private chats; use informed consent, minimized access and reviewed retention.
- **Implementation effort:** High for two-language pilot; Very high for full validated coverage.
- **Hackathon demonstrability:** bounded code-mixed examples with separately labeled synthetic fixtures and a modest real-data held-out table. Other languages labeled unvalidated, not silently translated into certainty.
- **Main failure modes:** slurs/reclaimed language, sarcasm, polite coercion, dialect/romanization, translation erasing context, transcription negation errors, imbalanced data, speaker/source leakage and inappropriate mapping of offensive-content labels to harassment incidents.

### I7. Reversible local personalization without weight training - Tier B

- **Existing precedent:** BullyAlert phone-side adaptation [R03]; PI-Bully personalization research [R17]; local RAG memory [R09].
- **Novelty gap:** auditable, revocable case-specific adaptation separating user preferences from factual category labels, without shared training or rewriting old findings.
- **Technical mechanism:** encrypted user-approved context/examples; case-specific contact aliases confirmed by the user; retrieval of similar corrections; bounded threshold settings or a tiny online linear head as a later option. Show before/after suggestions and adaptation version. Keep original base-model result, correction and personalized suggestion separate. Reset/undo supported. Do not automatically infer shared identity across apps or normalize repeated harm as normal because it occurs often.
- **Required models:** existing encoder/embedding model; optional tiny head. On-device LoRA of a generative model is not required.
- **Android feasibility:** high for bounded memory/thresholds; conditional for learning/runtime/export parity.
- **Data requirements:** a sufficient diversity of reviewed examples and untouched later holdout, benign jokes versus unwanted contacts, poisoned/coerced corrections and cold-start tests. Few feedback items cannot establish adaptation quality.
- **Security implications:** coercive phone access can poison feedback; isolation, rollback and change visibility matter.
- **Privacy implications:** highly identifying context never leaves phone by default; deleting personalization should invalidate its cache/index, not delete original evidence silently.
- **Implementation effort:** Medium context memory; High convincing learning evaluation.
- **Hackathon demonstrability:** good narrow example: user-confirmed context changes a suggestion, reset restores base behavior; old signed report unchanged.
- **Main failure modes:** overfitting, confirmation bias, malicious corrections, cross-case contamination, perceived “tolerance” downranking genuine threats, drift and false assurance that personalized equals accurate.

### I8. Cross-modal event alignment without double-counting - Tier B

- **Existing precedent:** XBully/UCD multimodal session modeling [R15, R16]; local speech evidence prototypes [R06, R07]; Aimee mixed-document events [R01].
- **Novelty gap:** explicit observation-level source alignment and ambiguity in a local evidence timeline, not generic OCR + STT + AI.
- **Technical mechanism:** OCR keeps page/image region and reading-order metadata; transcripts keep audio segment intervals; video keeps frame offsets and extracted-audio parent links. Link multiple captures to a candidate event only with confirmed identity/time/context; preserve each source separately. Screenshot, voice narration and notification may refer to the same incident, not three. Quoted speech, user recollection and observed incoming content remain distinct. Do not automatically invent speakers from diarization.
- **Required models:** local OCR/STT plus text classifier; optional perceptual hashing/embedding for duplicate candidates. No full vision-language LLM required for text-bearing evidence.
- **Android feasibility:** high for text + screenshot; conditional for short audio; long video stretch only. Text extraction cannot interpret non-text visual abuse by itself.
- **Data requirements:** aligned screenshot/notification/audio representations, multi-speaker and quoted/recounted speech, ambiguous dates, unrelated same-content items and missed duplicates.
- **Security implications:** hostile media parsers/decompression; bounded file sizes, durations and resource use; never overwrite sources during extraction.
- **Privacy implications:** bystander voices/faces and adjacent conversation can enter derivative text/index; review scope and exports.
- **Implementation effort:** High for useful audio alignment; Very high for reliable general video/speaker matching.
- **Hackathon demonstrability:** good screenshot+notification duplicate candidate and one short audio excerpt; unconfirmed relationship shown as uncertain.
- **Main failure modes:** wrong speaker attribution, narration mistaken for captured event, duplicate false merges, OCR bubble order, hallucinated STT, timestamp conflict and implied visual understanding that the model never performed.

### I9. Tested offline custody and controlled recovery - Tier B

- **Existing precedent:** Tella encrypted offline files/backups/nearby transfer [R10]; local vault prototypes [R06]; Keystore [R28].
- **Novelty gap:** tested threat-model boundaries for every derivative/index/cache, plus explicit recovery choices in a survivor evidence workflow. Encryption alone belongs in Tier C.
- **Technical mechanism:** app-private encrypted blobs/database/index; per-file AES-GCM with nonce discipline and authenticated identifiers; minimal temporary plaintext; Keystore protection and lock policy; explicit OS-backup configuration. Allow opt-in encrypted recovery bundle/transfer with user-held recovery secret; wrap a portable data key, not export a non-exportable Keystore key. Neutral notification content and protected previews. Explain capture while locked versus authentication-gated decryption tradeoffs; no hidden automatic backup.
- **Required models:** none for custody; existing local models must avoid logging evidence and temporary text.
- **Android feasibility:** high for standard cryptography; recovery and background ingest key-use policy need deliberate testing. Not all devices support StrongBox.
- **Data requirements:** no training corpus; synthetic evidence for reinstall, permission-revoke, crash, interrupted import/export, key invalidation/loss, backup and compromised-unlocked-device boundary tests.
- **Security implications:** Keystore prevents key extraction but a compromised process/device may use the key [R28]. Signatures/hashes do not detect all rollback/deletion. Local-only storage creates single-device loss risk.
- **Privacy implications:** recovery helps retention but expands access; encrypted backups still expose size/timing. No app can promise invisibility under coercive unlock/root/malicious accessibility.
- **Implementation effort:** Medium bounded vault; High recovery and security assurance.
- **Hackathon demonstrability:** encrypted-at-rest fixture check, process restart and explicit selected recovery demo. Not a security certification or proof against phone seizure.
- **Main failure modes:** nonce reuse, plaintext logs/thumbnails/SQLite WAL/temp/backup, key invalidation, unrecoverable loss, coercive access, mistaken panic-wipe safety claims. Do not silently destroy evidence.

### I10. Measured resource-aware inference, not always-on LLMs - Tier B for evaluation; Tier C for technique

- **Existing precedent:** Laya routing/typed outputs [R18]; mobile inference runtimes [R25, R26]; sequential cyberbullying feature/timeliness research in the earlier survey. Quantization, caching and scheduled work are established engineering.
- **Novelty gap:** measured error/energy tradeoff for this specific evidence pipeline across real Android tiers, not a new cascade architecture.
- **Technical mechanism:** cheap structural screening, compact classifier for every eligible event, explicit review for uncertain or unsupported content, optional local LLM only for requested bounded context analysis. Preserve non-flagged evidence within user-selected intake scope so longitudinal analysis is not blind to subtle repetition. Cache outputs by source/derivative/model/schema version; defer OCR/STT/batch summaries to user action or constrained work. Sequentially load heavy models. Provide text-only fallback, not silent cloud fallback.
- **Required models:** compact classifier or validated Laya; optional small GGUF/LiteRT-LM model; OCR/STT on demand.
- **Android feasibility:** high bounded pipeline; acceleration depends on exact model/backend/device. NPU availability does not guarantee operator compatibility.
- **Data requirements:** representative normal traffic plus subtle harm, high-uncertainty cases, long contexts, low battery/thermal workloads; same corpus for all baselines.
- **Security implications:** signed/hash-pinned assets, parser/runtime isolation, no evidence in profiler/log outputs.
- **Privacy implications:** run-local by default; downloads outside the evidence workflow must be explicit. “No INTERNET permission” does not prevent export, IPC, OS backup or compromise.
- **Implementation effort:** Medium cascade; High rigorous cross-device comparisons.
- **Hackathon demonstrability:** show actual phone p50/p95 latency, peak resident memory and routing decisions versus classifier-only and always-LLM baselines; quality metrics separate from speed.
- **Main failure modes:** cheap filter misses polite threats, quantization changes decisions/calibration, heavy fallback overheats, cold-start latency, tiny model produces fluent incorrect explanations, cached stale output and resource failure presented as safe classification.

## 6. Data and evaluation program

### Dataset suitability

| Source | Established task / language / modality | License / access | Appropriate use and limitation |
|---|---|---|---|
| DravidianCodeMix [R19] | Real YouTube comments; Tamil-English, Malayalam-English, Kannada-English; sentiment/offensive labels | Zenodo CC BY 4.0 | Initial language/classifier baseline. Not consented private conversations, temporal incidents or escalation labels. Task-specific label counts/splits need inspection; do not sum approximate summary counts into a claimed training size. |
| HASOC 2021 [R20] | Public-post hate/offensive/profanity tasks; English/Hindi/Marathi and Hindi-English conversational code mix | Official page password-protected; redistribution/training terms need confirmation | Text/context starting point. Not a ready-made survivor danger/evidence corpus. |
| UCD/XBully/temporal datasets [R15, R16] | Real social-media sessions, time/network/multimodal features | Verify actual dataset rights separately from MIT implementation license | Temporal-method comparison. Public session labels do not establish private dyadic harassment or physical-risk accuracy. |
| Consented domain corpus | Private-context events/conversations, timeline labels, ambiguity and user review | Not yet established; ethically governed collection required | Needed for meaningful target-domain validation; protect identities and restrict access. Do not solicit traumatic disclosures casually for a demo. |
| Synthetic replay fixtures | Fabricated bilingual incidents, notification loss/duplicates, OCR/STT noise and tampering | Project-created; label synthetic | Engineering tests and demonstrations only. Not real-world efficacy evidence. |

Training flow: source/license audit -> clean and deduplicate -> validate labels and disagreement -> conversation/person/source-isolated train/validation/calibration/test split -> fine-tune/distill -> recalibrate -> error analysis -> export -> Android parity/resource benchmark. Keep all screenshot/text/audio derivatives and synthetic paraphrases of the same underlying item in one split. Apply temporal holdout and isolate people where identifiers exist; if unavailable, disclose that person-disjoint validation cannot be guaranteed. Never use the final test set to select thresholds.

### Minimum reporting

- Per-language/category precision, recall, macro F1, class prevalence and appropriate PR-AUC; confidence intervals clustered by conversation/person where appropriate.
- OCR character/word errors, STT word errors and threat/negation/speaker preservation; downstream changes in findings.
- Pattern precision/recall, false alerts per observed week, detection delay, supporting-event correctness; define category changes versus frequency changes explicitly.
- Calibration (ECE plus reliability plots/Brier score), risk versus coverage, abstention by language/category, and retained false-negative rate. No “95% safe” from uncalibrated softmax.
- Retrieval recall@k and precision, answerability, unsupported-claim/contradiction rate, source/version accuracy and cross-case isolation.
- Graph invariants, correction propagation, tamper detection and redaction leak testing.
- End-to-end p50/p95 latency, cold/warm load, peak RAM including native/GPU considerations, disk/APK/model size, battery/energy estimate methodology, thermal behavior and listener stability on named phones/OS versions.
- Offline tests after provisioning with Wi-Fi and mobile data disabled, plus networking instrumentation/permission/SDK/backup review. Airplane mode alone does not prove there is no deferred upload or other data path.

### Differentiation ablations

| Candidate | Baseline to beat | Evidence of meaningful differentiation |
|---|---|---|
| I1 coverage-aware analysis | Same per-event labels + naive window counts | Fewer false trends under changed visibility/duplicates without hiding actual observed pattern changes. |
| I2 correction dependency graph | Editable event list without dependency/version enforcement | No stale patterns/current retrieval/export after corrections; original hash unchanged; past report reproducibility. |
| I3 selective verified export | Plain PDF/ZIP or signed PDF alone | Export scope/approval invariants, byte/edge tamper detection, clear trust/omission states, no sensitive hidden metadata. |
| I4 bounded retrieval | Lexical baseline and generic top-k RAG | Better relevant-span retrieval/answerability with zero cross-case leaks; counts sourced from database, not top-k. |
| I5/I6 reliability and language | Uncalibrated multilingual model on clean single comments | Measured accepted-risk/coverage tradeoff and end-to-end per-language robustness under acquisition noise. |
| I7 personalization | Frozen base model | Improvement on untouched later examples, reversibility and no regression on serious/subtle harm. |
| I10 cascade | Classifier-only and always-LLM | Same-target quality/resource comparison; no inflated apparent accuracy from dropping hard cases. |

All numerical results above are evaluation targets or source-reported component facts, not fabricated Sakshi results.

## 7. Hackathon scope and demonstrability

### Recommended vertical slice

Build **I1 + a text-first I2 + a bounded I3** on top of standard acquisition/vault/classifier plumbing. These show distinctive behavior without depending on an expensive generative model. Keep general RAG, local training, video and all-language claims out of the critical path.

Proposed demonstration, using clearly labeled synthetic evidence:

1. On a real Android phone, grant notification access and ingest controlled notifications; import one text-bearing screenshot through supported share/file selection. State that controlled notifications test Android plumbing, not complete WhatsApp access. Test each chosen messaging app separately with consenting accounts.
2. Hash/preserve source; local OCR creates a derivative with bounding regions; classify with a tested compact model. A rules-only classifier fallback must be called a rules baseline, not trained AI.
3. Replay a timeline with duplicates and a known listener gap. Show exact observed counts and Unknown/incomparable trend rather than fabricated escalation.
4. Display a genuine observed repetition/category-change fixture with linked sources. An example threatening quote is not itself a validated physical-danger probability.
5. Reject/relabel one event. Current aggregate, label search and report change; original source hashes and prior report do not.
6. Select approved material; preview omissions/redactions; export. On a second offline computer verify the manifest and deliberately tamper with one disposable exported fixture to show failure.
7. Report measured device latency/RAM, model/runtime revision and unsupported-language/missing-capture limitations. Keep network disabled after assets are provisioned; show network instrumentation rather than rely on a slogan.

Optional stretch: one short multilingual audio clip, or source-only lexical retrieval with exact citations. Do not add both a new STT stack and an untested LLM just to inflate the demo feature count.

### Impressive but unrealistic or too risky as a hackathon promise

| Idea | Why not credible as the default scope | Safer substitute |
|---|---|---|
| Forecast violence, grooming outcome or legal harassment from a danger score | Requires defined outcomes, longitudinal follow-up, rare-event calibration and specialist review; temporal toxic-text evidence is not that data | Describe observed repeated contact/category transitions with uncertainty. |
| Reliable ten-language OCR+STT+context reasoning | Script support, dialect/code-mix data, evaluation and quality disparities are unresolved | Two-language text pilot; modality/language capability matrix and explicit abstention. |
| Automatic generative-model LoRA updates after each correction | Training runtime, memory/thermal overhead, sparse labels, forgetting/poisoning, privacy and reproducibility burdens | Reversible correction memory and scoped thresholds; tiny-head learning later. |
| Always-on audio/video/large reasoning model | Battery/thermal/storage/background and consent problems | Explicit user import/record, short clips, one heavy model at a time. |
| Automatic cross-app identity matching | Names/handles are not verified identities; false links fabricate patterns | User-confirmed aliases with reversible uncertain links. |
| Recover View Once/disappearing/private databases | Unsupported/circumventing access and incompatible with project rules | Document legitimate visible notification metadata/user-shared evidence only; unknown content stays unknown. |
| Blockchain guarantees authenticity or admissibility | Hashes/logs cannot prove source truth; external anchoring may disclose metadata and needs trust/network | Local integrity manifests with explicit trust limits; independent verification. |
| Panic wipe or covert app guarantees safety | Irreversible evidence loss and discoverability/coercive-unlock risks | Explicit lock/preview controls and informed recovery/export choices; no automatic destruction. |
| Zero-knowledge proof of correct AI reasoning/redaction | Far more complex than hashing/signing; proof of computation still does not prove correct interpretation | Honest signed lineage plus clear limits when originals are omitted. |
| Root-proof, subpoena-proof, impossible-to-leak device storage | App plaintext/use of keys, backups, IPC, exports and coerced unlock remain relevant | Documented threat model and tested leakage/recovery boundaries. |

## 8. Novelty Stack

**Tier A means strong comparative support for a narrower differentiation opportunity, not strong evidence of global novelty.** Sakshi has not implemented or validated these candidates. If “novelty” means a proven new algorithm or patentable invention, this review has **no confirmed Tier A invention**.

### Tier A: strong evidence of differentiation opportunity

| Stack layer | Candidate | Why it is stronger than a feature claim | What must be proven |
|---|---|---|---|
| Observation-aware patterns | I1 capture-aware longitudinal analysis | Tackles notification incompleteness and duplicate/import artifacts rather than claiming first temporal AI | Coverage-shift replay and real-device capture study; false-trend reduction versus same-label naive baseline. |
| Correctable analytical provenance | I2 source-to-finding-to-correction-to-pattern-to-report graph | Enforced dependencies and reproducible reports, beyond adding an edit button or citation | Transactional propagation, exact valid spans, immutable originals, report version/tamper tests. |
| Controlled portable disclosure | I3 reviewed selective bundle + independent offline verification | Combined analytical review scope, omission states and redaction lineage, beyond signed PDF/export | Offline recipient verification, trust-anchor caveats, metadata-leak/tamper tests. |

### Tier B: interesting but needs more validation

- I4 review-aware, encrypted, case-isolated private evidence retrieval. Generic offline RAG is already productized.
- I5 pipeline-level uncertainty that is calibrated and fairly abstains under extraction/domain/language shift. Methods are established; data and assumptions are the hard part.
- I6 English/Indic/code-mixed acquisition-realistic evaluation and compact mobile deployment. Offensive-content precedents are substantial; target-domain efficacy remains open.
- I7 reversible local personalization/context memory. Mobile adaptive classifiers already exist; meaningful generalization and poisoning resistance need tests.
- I8 cross-modal event alignment, exact source intervals and duplicate-aware timelines. Multimodal temporal methods already exist; survivor evidence alignment is difficult.
- I9 tested offline custody/recovery across derivatives/indexes. Encryption is common; assurance and safe recovery usability are not established here.
- I10 published quality/energy/resource comparisons for the full pipeline. The cascade itself is Tier C.
- **Full-stack integration** of these behaviors: potentially useful but high effort, and close survivor prototypes mean it cannot be sold as automatically unprecedented.

### Tier C: already common or clearly precedented

Local LLM/classifier inference; OCR/STT; multilingual encoders; notification text ingestion and local classification; single-message toxicity/severity scores; incident timelines and multimodal journals; cloud AI document/pattern review; generic private local RAG/citations; approve/edit flows; mobile adaptive classification; encrypted vault/PIN/quick exit/disguise; SHA-256/signatures/provenance manifests; PDF/ZIP exports; model quantization/caching/routing; calibration/abstention as techniques.

“Common” is category-relative or substantial prior art, not a market-share estimate. Some exact combinations are only prototypes/research, not widely deployed consumer features. **Tier C does not mean unimportant:** these are necessary foundations, just not defensible novelty by themselves.

### Tier D: unrealistic / too risky as proposed

Broad future-harm/legal verdict prediction; universal private-app scraping/View Once bypass; guaranteed all-language accuracy; always-on high-compute multimodal surveillance; effortless per-correction LLM training; automatic cross-platform identity certainty; blockchain/admissibility guarantees; cryptographic proof that AI interpretation is true; covert/root-proof/subpoena-proof safety promises; automatic destructive panic wipe.

## 9. Claim boundaries and open questions

### Do not say

- “First privacy-first harassment AI” or “first offline survivor evidence app.”
- “First local temporal analysis / human correction / evidence RAG / encrypted AI vault.”
- “All Indian languages supported accurately” because a multilingual backbone tokenizes them.
- “Calibrated probabilities” because training uses a proper scoring rule, or “safe” because a model abstained.
- “Verified evidence” when only a hash/signature or imported screenshot is available.
- “Court-ready” or “guaranteed admissible” as a technical certification.
- “No data can ever leave the phone” based only on an Android permission or a vendor promise.

### Still unknown

1. Whether another system already implements each exact Tier A chain in an unpublished/private workflow; deeper code audits, product trials, standards/patent/archival searches could demote candidates.
2. Whether these mechanisms materially reduce user burden or harm; specialist and consented survivor review are needed, not just engineering demos.
3. Which physical Android device/OS/app notification combinations Sakshi will support, and what information those apps expose.
4. Which checkpoint has acceptable English/Malayalam/code-mixed harassment performance and Android parity; Laya is a candidate, not a validated choice.
5. How background capture, authentication-gated keys, evidence retention and recovery should trade off in the intended threat model.
6. Whether case-specific review/retention policy should preserve all opted-in evidence or only selected incidents; silent destructive filtering must not be the default.
7. Whether recipients need sources, redacted derivatives, report-only packages or all three, and what signer identity/trusted-time assurance is required.

### Corrections to earlier feasibility assumptions

`research/deep-research-report.md` contains exploratory claims that should not be reused as validated recommendations: universal quantization accuracy/speed percentages, unrestricted NPU availability, StrongBox implying protection of all plaintext under root, hallucination-free reasoning from typed outputs, and large model/on-device training suggestions as easy hackathon work. This report supersedes those assumptions for the differentiation assessment; the historical file is not rewritten. Verification must be model/device/task-specific. Whisper formats and multilingual variants must follow whisper.cpp documentation rather than assume every local model is GGUF.

## 10. Primary source register and access notes

Sources accessed or inspected through primary indexed extracts on 2 October 2026. Performance/security statements remain source-reported unless explicitly identified as engineering arithmetic.

- **R01 - Aimee Says:** [official upload/search/pattern review](https://www.aimeesays.com/en/documents); [founder interview transcript describing generated timeline events, approval, modification and attached evidence](https://kateanthony.com/podcast/episode-353-aimee-says-updates-how-women-are-documenting-abuse-in-real-time-with-anne-wintemute/). Offered commercial behavior, not independently audited locality/efficacy.
- **R02 - Agent Hita:** [developer repository](https://github.com/Agent-Hita/AgentHitaAndroid). Explicit temporal/local model and raw-text non-retention claims; source available, not open source; commercial license required. Do not copy without terms review.
- **R03 - BullyAlert:** [MobiCASE 2020 publisher abstract and metadata](https://eudl.eu/doi/10.1007/978-3-030-64214-3_1); [NSF record](https://par.nsf.gov/biblio/10226740-bullyalert-mobile-application-adaptive-cyberbullying-detection); [author accepted manuscript](https://par.nsf.gov/servlets/purl/10226740) (direct fetch binary, primary indexed extracts consulted). Android computation/adaptation and preliminary usage established; fully offline modern connector not established.
- **R04 - Concepcion-Sanchez, Caballero-Gil, Molina-Gil, 2017:** *Application Based on Fuzzy Logic to Detect and Prevent Cyberbullying Through Smartphones*, CS & IT 7(16), pp. 11-23; [publisher proceedings PDF](https://aircconline.com/csit/csit778.pdf); [publisher index](https://airccj.org/csecfp/library/jvol.php?last=CS+%26+IT&volname=7&volno=16). Sections 3.1/4.1 indexed excerpts describe notification parsing, encrypted local accumulation and local processing. Binary fetch prevented full readable-paper inspection; no performance number repeated.
- **R05 - BullStop:** [COLING 2020 system demonstration abstract/metadata](https://aclanthology.org/2020.coling-demos.13/). Historical availability, not verified current deployment/locality.
- **R06 - SaakshiAI:** [TheCodeKage Android project](https://github.com/TheCodeKage/SakshiAI). README inspected; no code execution/security/model benchmark. Absolute privacy, legal-readiness and any-language claims not adopted as facts.
- **R07 - SilentWitness:** [Devpost submission](https://devpost.com/software/silentwitness). Author-reported architecture/escalation; no independently verified implementation/performance. Distinct from R08.
- **R08 - Silent Witness:** [lbfn83 project](https://github.com/lbfn83/silent-witness-hackathon). Explicit private-server inference and failed browser-local experiments; do not label its deployed architecture fully on-device.
- **R09 - AnythingLLM Mobile:** [official mobile documentation](https://docs.useanything.com/mobile/overview). Android/Play/APK, local GGUF inference, embedding/vector DB/reranking/citations and offline operation. Optional cloud/on-premises routes exist; do not infer evidence-vault encryption.
- **R10 - Tella:** [official offline features/capture/import/encryption](https://tella.app/features). Standard Android, Android FOSS and iOS feature distinctions retained; optional transfer/server connection not silent cloud AI.
- **R11 - Proofmode:** [official products/provenance ecosystem](https://www.proofmode.org/). Capture, verification, developer C2PA support and advertised Android conformance; source truth is not guaranteed by provenance.
- **R12 - C2PA:** [2.4 implementation guidance](https://spec.c2pa.org/specifications/specifications/2.4/guidance/Guidance.html). Assertions, ingredients, transformations, redaction, trust and validation are prior mechanisms. No Sakshi standards-conformance claim.
- **R13 - Axon Evidence:** [official AI/evidence/audit/redaction/cloud product page](https://www.axon.com/products/axon-evidence). Vendor authenticity/admissibility wording is not endorsed as a general mathematical property of hashes.
- **R14 - TalkingParents:** [official unalterable records/signature/PDF verification](https://talkingparents.com/features/unalterable-records). In-platform records and provider identity are different from user-imported screenshots.
- **R15 - UCD:** [author MIT implementation](https://github.com/GitHubLuCheng/UCD). Documents multimodal session representations, inter-arrival timing, GMM and legacy runtime; title in repository uses “Gaussian Mixture Model”, NSF related record uses “Deep Clustering”.
- **R16 - Multimodal/temporal academic work:** [XBully NSF record, WSDM 2019](https://par.nsf.gov/biblio/10110260); [standalone NSF record for *Modeling Temporal Patterns of Cyberbullying Detection with Hierarchical Attention Networks*, ACM/IMS TDS 2021](https://par.nsf.gov/biblio/10301308-modeling-temporal-patterns-cyberbullying-detection-hierarchical-attention-networks), [DOI](https://doi.org/10.1145/3441141). The full-title NSF URL succeeded after the short URL failed; its primary abstract and evaluation description were inspected directly. Earlier CONcISE/Soni precedents and access limitations are catalogued in the existing survey.
- **R17 - Personalization:** [2018 PI-Bully framework proposal NSF record and related 2019 evaluated PI-Bully abstract](https://par.nsf.gov/biblio/10067396). Distinguish proposed 2018 framework from reported 2019 experiments; not a phone privacy implementation.
- **R18 - Laya:** [actual repository](https://github.com/NandhaKishorM/laya); [English/family model card](https://huggingface.co/convaiinnovations/laya); [multilingual card with architecture, license and explicit Limits](https://huggingface.co/convaiinnovations/laya-multilingual). GitHub runtime notes describe 0.3.23, family card has older 0.3.20 notes; use pinned current code/checkpoints, not interchangeable version assumptions.
- **R19 - DravidianCodeMix:** [original Zenodo dataset and CC BY 4.0](https://zenodo.org/records/4750858); [2021 shared-task paper](https://aclanthology.org/anthology-files/pdf/dravidianlangtech/2021.dravidianlangtech-1.17.pdf) (primary indexed abstract inspected). No private-conversation or escalation-label assumption.
- **R20 - HASOC:** [official 2021 dataset/access page](https://hasocfire.github.io/hasoc/2021/dataset.html); [task definitions](https://hasocfire.github.io/hasoc/2021/call_for_participation.html) (indexed primary extracts). Public download links/access are not license clearance.
- **R21 - Android capture limits:** [NotificationListenerService API](https://developer.android.com/reference/android/service/notification/NotificationListenerService); [Android 15 all-app security/OTP-redaction changes](https://developer.android.com/about/versions/15/behavior-changes-all). No bypasses proposed.
- **R22 - ML Kit OCR:** [Android bundled/unbundled model behavior](https://developers.google.com/ml-kit/vision/text-recognition/v2/android); [supported scripts/languages](https://developers.google.com/ml-kit/vision/text-recognition/v2/languages) (official indexed extracts). Do not confuse documentation UI translations with recognizer support.
- **R23 - Tesseract:** [official traineddata/scripts/languages and fast/best tradeoffs](https://tesseract-ocr.github.io/tessdoc/Data-Files.html). Native Android integration and screenshot performance remain to test.
- **R24 - whisper.cpp:** [official repository, Android examples, formats/quantization, disk/memory estimates](https://github.com/ggml-org/whisper.cpp). No claimed target-device multilingual accuracy.
- **R25 - ONNX Runtime Mobile:** [official Android/iOS inference support](https://onnxruntime.ai/docs/get-started/with-mobile.html); [Android NNAPI operator restrictions](https://onnxruntime.ai/docs/execution-providers/NNAPI-ExecutionProvider.html) (indexed docs); [reduced-operator package lifecycle](https://onnxruntime.ai/docs/reference/operators/MobileOps.html) (indexed docs). Generic runtime support is not checkpoint parity.
- **R26 - LiteRT-LM:** [official Android/multimodal/backend overview](https://developers.google.com/edge/litert-lm/overview); [Kotlin Android API](https://developers.google.com/edge/litert-lm/android) (indexed primary docs). Flagship/component benchmarks are not full-pipeline mid-range results.
- **R27 - Small generative baseline:** [Qwen2.5-0.5B-Instruct official model card](https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct), 0.49B parameters, Apache-2.0. It is a bounded baseline, not a recommendation based on validated harassment accuracy or all-Indic capability.
- **R28 - Keystore:** [official key extraction/use, hardware/StrongBox and compromise limits](https://developer.android.com/privacy-and-security/keystore). Protecting key material differs from preventing an attacker using it on a compromised device.
- **R29 - Retrieval embedding candidate:** [paraphrase-multilingual-MiniLM-L12-v2 official card](https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2), 384-dimensional mean pooling, 128-token configured sequence length, Apache-2.0. Listed multilingual coverage is not evidence of every requested language's retrieval quality.
- **R30 - Uncertainty precedent:** [Selective Conformal Risk Control, arXiv v2, April 2026](https://arxiv.org/abs/2512.12844), implemented/evaluated general ML method in a preprint. Establishes methodological precedent, not a deployed harassment guarantee. Exchangeability/selection assumptions matter.

### Excluded or limited evidence

Secondary-index hits titled CARE, SendWise and CIDER-CB described relevant techniques, but primary publication/code provenance was not established sufficiently in this session to adopt their accuracy or deployment claims. They are search leads, not evidence of validated Sakshi capabilities. Likewise, hackathon README/Devpost claims are recorded as claims, not treated as independently verified systems. The earlier landscape remains useful for broader products and historical prior art; this report narrows rather than expands unsupported novelty claims.

## Final implementation implication

Start with reliable supported intake and a small local classifier, then demonstrate **what happens when evidence is missing, duplicated, corrected, or selectively disclosed**. That behavior is more defensible technical differentiation than “AI + vault + timeline”. Publish the fixture results, target-device measurements and honest limitations. Only expand to broader languages, RAG generation and personalization after the bounded evidence chain is correct.
