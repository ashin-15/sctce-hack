# Sakshi: sentiment, emotion and behaviour in an Android local-AI system

**Research date:** 2 October 2026\
**Status:** Engineering research/design, primary-source inspection and limited Linux synthetic experiments. No Sakshi Android implementation, model training, representative harassment evaluation or phone workload benchmark was executed.\
**Decision carried forward:** Laya is deferred. This report does not reopen that integration decision.

## 1. Executive Summary

**Use expressed emotion as an independent, optional signal alongside behaviour analysis, not as a filter that decides whether harassment exists.** Keep the LLM optional, human review authoritative, and temporal counting algorithmic. The MVP needs two compact baseline checkpoints, not one LLM per task or an emotion-to-danger score.

Recommended first implementable model pack:

- Expressed emotion: `minuva/MiniLMv2-goemotions-v2-onnx`, roughly 30 M parameters, 30.46 MB INT8 ONNX, 28 independent sigmoid outputs; display a restrained subset of actual labels, never fabricate intensity.
- Narrow harmful-language baseline: `minuva/MiniLMv2-toxic-jigsaw-onnx`, roughly 23 M, 22.86 MB INT8 ONNX, six sigmoid outputs. Call it **narrow toxicity signals**, not a comprehensive coercion/stalking/sexual-harassment classifier.
- Runtime: ONNX Runtime **Android** CPU; checkpoint-specific native tokenizer adapter; pinned graph, vocabulary, padding, label order and calibration manifest.
- Optional polarity: three-way `cardiffnlp/twitter-roberta-base-sentiment-latest` as an exported/validated research reference or later distilled head. Do not add a 125 M sentiment model merely to duplicate emotion UI. The two-checkpoint MVP sets sentiment to not_evaluated unless this optional pack is explicitly enabled.
- Optional foreground LLM experiment: pinned Qwen3-0.6B dynamic INT4 `.litertlm`, LiteRT-LM Kotlin. Benchmark a more capable Qwen3.5-2B text-only Q4_K_M GGUF through llama.cpp if the smaller model fails contextual/source-linked quality. One installed reasoner, not both resident by default.
- Temporal engine: pure Kotlin, distinct observed/reviewed events, per-speaker tracks, coverage-aware counts/transitions, optional descriptive expressed-emotion trajectory. No violence prediction.
- Context: authorized adjacent turns first; case-scoped confirmed-evidence retrieval second. Start without a vector database; add English MiniLM or multilingual E5 embeddings only when retrieval ablations justify them.
- Personalization: explicit user notes, case/speaker prototypes and preferences first; **no automatic model retraining or danger-threshold weakening**.

**Why this architecture:** compact encoders provide finite narrow outputs without generation; independent branches avoid negativity-as-abuse errors; temporal logic preserves exact counts/source IDs; optional context reasoning addresses ambiguity without permanent LLM residency. No matched Sakshi benchmark establishes the universally best model. Named candidates are engineering baselines subject to the release gates below, not production safety certification.

Every recommendation distinguishes:

- **Verified source fact:** API/source/config/license declaration inspected.
- **Published measurement:** author/device/workload result, not reproduced here.
- **Local experiment:** Linux synthetic test executed here, with its scope.
- **Design:** proposed engineering decision/target, not measured performance.
- **Unknown:** no applicable artifact, license, measurement or target-language evidence established.

## 2. Proposal context and where emotion belongs

Primary product context: local `Harassment_Pattern_Guard.pptx.pdf`, rendered-text extraction inspected page by page. The proposal says Sakshi detects cross-platform harassment patterns locally, produces evidence-linked incident notes, preserves encrypted immutable originals with SHA-256 integrity metadata, shows timelines/escalation, allows corrections, and generates user-confirmed reports. Its target users include domestic-abuse survivors, cyberbullying victims and students. [P1]

| Proposal component | Relevant proposal language | Engineering interpretation |
| --- | --- | --- |
| Local NLP / behaviour | Repeated insults, sexual/caste slurs, doxxing, controlling language | Multi-label contextual signals with operational rubrics; no generic toxicity equivalence |
| Local LLM | Summarize flagged conversations into source-linked incident notes | Optional bounded interpretation of selected evidence; not legal facts or exact counting |
| Pattern / escalation | Repetition over weeks, low/medium/high per conversation | Distinct-event temporal features with capture gaps; severity labels must be validated, not arbitrary |
| Evidence ingestion | Messages, screenshots, voice notes, email/call references | Supported share/picker/import and opted-in exposed notifications, not universal private-app access |
| OCR / STT | Future Indic OCR/STT and media evidence | Versioned text derivatives with region/time/source maps; original preserved |
| Explainability | Message ID, timestamp, subject linked to every output | Enforced anchor/quote validation plus interpretation label |
| Human review | Victim corrects/removes inaccuracies before timeline/report | Separate source, signal and explanation review decisions; immutable versions |
| Encrypted vault | SQLCipher/libsodium; raw items and AI outputs | SQLCipher database + authenticated original-file encryption, Keystore-wrapped keys, explicit retention |
| Integrity/report | Hash chain/manifest, timeline PDF, user-confirmed summaries | Integrity relative to received bytes, not proof of authenticity/existence at a trusted time or court admissibility |

The proposal's flow-chart/tech-stack/UI pages are not readable implementation specifications from text extraction alone. There is no implemented application here establishing those diagrams as working code. Its broad scanning and hash-as-proof wording must be narrowed by AGENTS/acquisition research: notifications expose partial observations; no private databases/View Once bypass; hashes alone do not prove authenticity. Disguise/duress/recovery are separate security/product requirements, not solved by emotion ML. [P1-P3]

**Insertion point:** after authorized preservation and extraction, inside the text-analysis subsystem beside behaviour classification. It feeds contextual interpretation and an optional expressed-language trajectory. It does not sit in front of behaviour detection as a prerequisite.

### Corrected final topology

```text
ANDROID DEVICE
  Authorized user imports OR optional partial notification observations
       |                         |
       | import Save consent     | transient / separately consented review inbox
       v                         v
  Encrypted originals + hashes + provenance (no preprocessing overwrite)
       |
  Text / OCR / STT / export parser -> versioned source-mapped TextEvidence
       |
  View builder + language/extraction/token-budget support checks
       |
       +-> independent rules + user concern + authorized contact metadata
       |
       +-> Behaviour signals ----+
       +-> Expressed emotion ----+-> Evidence/context policy router
       +-> Optional sentiment ---+       |
       |                                 +-> enough for provisional suggestion
       |                                 +-> needs context: adjacent turns / confirmed retrieval
       |                                 +-> optional installed local LLM, foreground consent
       |                                 +-> unresolved/unavailable/unsupported: human review
       |                                      |
       +---------------------------> Anchor/quote/schema validation
                                              |
                                   Pending findings / candidate timeline
                                              |
                                Confirm / Reject / Edit / Add context
                                              |
                                   Confirmed encrypted evidence/events
                                              |
                            Deterministic temporal engine + pattern versions
                                              |
                              Reviewed incident timeline -> explicit export
```

A provisional temporal preview may help review candidates but must be distinguished from confirmed patterns. Authorized contact-count metadata can inform repetition even when isolated text scores are low, subject to retention consent. Import originals are saved before analysis; passive ordinary notifications are not automatically vaulted. The LLM can be absent without blocking preservation/review/timeline/export.

## 3. Existing Systems: implementation evidence, not feature claims alone

The two inventories below jointly record purpose/model/parameters/quantization/runtime/platform/RAM/latency/languages/data/training/inference/offline/limits. **U means unknown in inspected evidence**, not zero. No unavailable metrics were invented. [E1-E9]

| ID / real implementation | Purpose / model / parameters | Quantization / runtime / platform | RAM / latency / offline |
| --- | --- | --- | --- |
| E1 Google text-classifier Android sample | Binary sentiment; word-vector and MobileBERT assets; word-vector parameter count U, MobileBERT family about 25 M | Compatible `.tflite` metadata, MediaPipe Tasks Text; Kotlin Android; quantization depends on asset | U / helper measures wall time but no phone result established / offline after assets provisioned |
| E2 MobileBERT paper | Distilled bottleneck encoder, about 25 M, downstream NLU | TF/mobile implementation; exact tested quantization per paper, not assigned to every export | U / published 62 ms Pixel 4 under its test / local inference possible |
| E3 2023 mobile-transformer study | Emotion classifiers: MiniLM, MobileBERT, DistilBERT, tiny encoders, etc. | TF 2.11/TFLite versions in paper; FP32/FP16/dynamic/fixed INT8; physical Android app | Device/model-specific results / no universal Sakshi latency / local test app |
| E4 Vigil Android | SAFE/SCAM/HARASSMENT DistilBERT, about 66 M | INT8 ONNX about 67 MB; ORT Java/Kotlin + from-scratch WordPiece; SMS Android | U / U / README no INTERNET permission, source path local |
| E5 AgentHitaAndroid | Rules + context-aware Gemma harm category/severity; intended Gemma 2 B INT4, exact deployed checkpoint identity U | MediaPipe legacy LLM Inference CPU; Room/SQLCipher; Android | Comment claims ~900 MB artifact/4 GB device, not independently measured RAM / U / local inference, app telemetry/config exist |
| E6 BullyAlert | Adaptive cyberbullying alerts/guardian feedback; classifier specifics U in inspected abstract | Android hand-held computation; runtime/quantization U | U / U / device computation documented, acquisition/network not fully offline |
| E7 DialogueRNN | Speaker/global/emotion GRUs + utterance CNN/features; count depends on configuration | PyTorch research; no tested Android export identified | U / U / can operate on local data, not packaged Android system |
| E8 EmotionDynamics / UED | Lexicon-based home base, variability, displacement, rise/recovery; no neural parameters | Python/pandas/numpy/NRC lexicons, research desktop | U / U / local given data/lexicons, not Android-ready library |
| E9 TalkingParents Sentiment Scanner | Positive/neutral/negative scan of own messages with feedback; model U | Proprietary communications app/service, runtime/quantization U | U / U / offline inference U, not a local-AI precedent |
| E10 minuva emotion / toxicity | 30 M GoEmotions /23 M Jigsaw student classifiers | INT8 ONNX CPU reference; FastAPI example is desktop, not Android integration | Prior Linux smoke measurements below / Android U / local with complete assets |
| E11 Google Edge Gallery / llama.android | Local generative inference/model management, compatible Gemma/Qwen/GGUF | LiteRT-LM Kotlin / llama.cpp NDK Kotlin bindings | Model/device-specific; no shared emotion/harassment quality / offline after import |

| ID | Languages / data / training | Inference mechanism / relevant limits |
| --- | --- | --- |
| E1 | English binary movie-review polarity, Model Maker-supported assets | TextClassifier.classify(string); no neutral, harm or intensity inference |
| E2 | English/general NLU distillation | Mobile deployment proof for tested graph, not domain-specific emotion/abuse |
| E3 | English Emotions six-class test, 50-token study inputs | Quantization/accelerator failures show export/device parity matters; accuracy on six emotions !=GoEmotions multi-label F1 |
| E4 | English merged spam/phishing/toxicity/hate data; class-weighted supervised three-way CE | Source uses softmax argmax; training stratifies rows, not conversations, and caps safe rows; do not inherit its scores as calibration |
| E5 | Rules/prompt categories; no public matched target dataset/training established | Bounded previous-message context, lazy upgrade, rules fallback; source conflates safe/unparseable/unavailable as null and logs model response; do not copy those semantics |
| E6 | Instagram/guardian context, adaptive tolerance | Important prior art for device processing +feedback; guardians aren't survivor-owned evidence workflow |
| E7 | IEMOCAP/AVEC, English dialogue, supervised utterance features +RNN | Party-aware context, bidirectional variants may require future turns; TV/acted speech not private Indic messages |
| E8 | Ordered utterances, NRC VAD/emotion words; no gradient training | Inspected source filters stopwords, rolls lexicon-word windows, normalizes turn/order time; raw classifier scores/real-time physical rates not drop-in equivalent |
| E9 | Own authored drafts/sent messages; training/data U | Cannot scan co-parent's messages in help description; product tone feedback isn't verified offline harassment detection |
| E10 | English GoEmotions/Jigsaw +teacher/student recipe | Sigmoid multi-label, narrow constructs; FastAPI/Docker must not become phone architecture |
| E11 | Model pre/post-training publisher corpora | Valid native deployment precedent, not emotional trajectory/abuse evaluation |

### Source-level engineering lessons

- E1 helper creates a new executor on each classify call; use a long-lived bounded executor and explicit lifecycle instead of copying sample plumbing literally.
- E4 source closes tensors/results/session; good ownership precedent. It loads model bytes into Java memory and uses exclusive 3-way argmax; Sakshi should use file-backed/session-owned assets where supported and independent multi-label signals.
- E5 has non-thread-safe native-session guards and comments documenting native crashes. It also returns null for busy, parse failure and safe; Sakshi needs distinct states. Source explicitly logs generated classification responses and uses telemetry. Local inference is not blanket zero-network/zero-evidence-leak certification. Source-available commercial licensing restricts reuse.
- E8 code is MIT, but NRC lexicon/data terms are separate. Its stopword removal and ordinal word-window timing are inappropriate to copy blindly for negation-sensitive harassment or wall-clock escalation.

**Combined-system finding:** emotion/sentiment, conversational state, adaptive device classification, local LLM harm reasoning and temporal cyberbullying each have strong existing precedents. Agent Hita establishes local reasoning +temporal risk design, but not a validated complete sentiment+emotion+behaviour+temporal+LLM stack for all Sakshi languages. No inspected implementation establishes all components together with privacy, evidence retention, source-linked explanations and reproducible Android safety metrics. This is an evidence gap, **not a claim Sakshi is first**.

## 4. Existing Local-AI Approaches and architecture comparison

| Architecture | Accuracy potential / failure | Latency / RAM / battery / size | Explainability / complexity / maintenance |
| --- | --- | --- | --- |
| A One local LLM for everything | Flexible contextual labels, but self-confidence/unwanted-content refusal/hallucination; not reliable exact counting | Prefill+generation every item; substantial weights/KV/scratch and cold loads | One runtime but large prompting/validation burden; generated rationale isn't ground truth |
| B Small models +LLM | Narrow labels efficient; early confident mistakes can block hard cases | Smaller common path; second branch adds load and peak memory | Component metrics/source contracts easier; two tokenizers initially |
| C Confidence-only cascade | Selective gains possible on calibrated data; confident false negatives/language failure remain | Savings depend on route fraction/cold-load policy; escalated latency higher | Simple gate but fragile; no safe universal threshold |
| D Serial emotion->behaviour->intent->LLM->temporal | Cumulative filtering misses, emotion not a harm predicate | Many passes/resident sessions, context generation even when unnecessary | Hard to maintain/attribute errors; not recommended topology |
| Recommended D-prime | Independent emotion/behaviour/rules; conditional context/LLM; review; temporal facts | Small mandatory neural core, optional heavy session; shared student later | Explicit uncertainty/evidence anchors, staged maintainability; conditional safety validation required |

No head-to-head Sakshi dataset/device measurements support a universal numerical ranking. Embeddings aren't compulsory for short authorized context, and an independent intent checkpoint isn't justified by current task data. Temporal and rule components should run without a model.

Cost equation (design, not measured savings):

`E[L] = extraction + rules + behaviour + P(emotion)*emotion + P(retrieval)*retrieval + P(LLM)*LLM + P(cold)*load + validation`.

Common-path savings do not reduce peak RAM if all models remain resident. A long serial filter can compound recall losses; hypothetical independent 0.9-recall gates yield 0.9³=0.729, not real measured Sakshi independence. Do not optimize activation fraction by dropping difficult positive cases.

## 5. Sentiment vs Emotion vs Behaviour: responsibilities and taxonomy

| Construct / owner | What to implement | What not to infer |
| --- | --- | --- |
| Deterministic rules | Notification UI/non-content flags, repost handling, bounded cue candidates, source metadata, explicit stop-boundary markers, distinct temporal counts | Keyword=harassment, UI phrase=guilt, disappearing setting=abuse |
| Sentiment | Optional evaluative negative/neutral/positive distribution and text-view provenance | Negative=danger; positive=benign; neutral=unknown |
| Expressed emotion | Actual model-label distributions: anger, annoyance, fear, sadness, nervousness, disgust, joy, surprise, neutral; retain complete 28-label output | Internal mental state, credibility, trauma diagnosis, confidence=intensity |
| Behaviour | Person-directed insulting/harmful language; domain-trained categories for restriction, coercive demand, conditional exposure, unwanted contact, unwanted sexual conduct, personal-data exposure | Toxicity=all harassment, explicit sexual text=unwanted sexual conduct, address=actual doxxing |
| Intent/roles | Quote/report/negation/target/speaker/conditionality annotations and contextual interpretation | Verified sender identity or actual malicious intent from one text |
| Local LLM | Selected ambiguity/context, contradictory same-task signals, candidate summary/explanation with exact supplied anchors | Exact counting, authenticating evidence, universal code-mix competence, dangerousness/legal verdict |
| Temporal engine | Repetition, persistence after reviewed boundary, observed frequency, category transition, descriptive expressed-language change | Future violence probability, linear anger->intimidation ladder, complete unseen history |

**MVP taxonomy:** preserve the pretrained GoEmotions label vocabulary rather than inventing distress/hostility/frustration classes. Display anger, annoyance, fear, sadness, nervousness, neutral when applicable; other labels remain inspectable. Annoyance is not validated frustration; nervousness isn't a diagnosis of distress; hostility is preferably an operational behaviour/context rubric. Neutral is a trained label, not `1-max(other scores)`. Unknown, not_evaluated and unsupported_language are separate output states, not emotions.

Emotion is multi-label: anger and sadness can coexist. Do not force top-1 or softmax 28 logits. Polarity can also be mixed; a three-way model's limited taxonomy must be disclosed. An emotion-to-polarity lookup is not a trained sentiment classifier. [M1-M4][D1]

### Major recommendation contracts: WHY -> WHAT -> HOW -> MODEL -> RUNTIME -> DATA -> ANDROID -> LIMITATIONS

**R-A: independent expressed-emotion branch**

- WHY: describe language without blaming a distressed survivor or missing a polite threat.
- WHAT: 28 expressed-emotion sigmoid signals with source/role/support status; optional display subset; no intensity.
- HOW: preserve full output, validate labels/finite values and attach versioned analysis/anchors; request emotion independently.
- MODEL: pinned minuva GoEmotions INT8 ONNX.
- RUNTIME: ORT Android CPU with the checkpoint-specific RoBERTa byte-BPE tokenizer.
- DATA: GoEmotions +case-level domain hard negatives, later native-language annotations.
- ANDROID: TextSignalEngine emotion adapter, same bounded inference lease, requested/on-demand trajectory updates.
- LIMITATIONS: English/public Reddit training; padding-sensitive quantized scores; no clinical/harassment claim.

**R-B: behaviour separate from emotion**

- WHY: harm can be calm/positive; negativity can be legitimate complaint/reporting.
- WHAT: six narrow toxicity signals and a separate domain behaviour rubric later.
- HOW: run independently from emotion and retain cue/user/context review paths despite low toxicity.
- MODEL: pinned minuva Jigsaw MiniLM INT8; shared task student is a future training experiment.
- RUNTIME: ORT Android CPU and checkpoint-specific WordPiece tokenizer.
- DATA: Jigsaw/Civil Comments +licensed context, minority-class sampling, domain/native-language cases.
- ANDROID: BehaviourSignalAnalyzer returns inferred candidates/unknown, not safe verdict.
- LIMITATIONS: no reliable pretrained control/blackmail/stalking coverage; calibrate after export.

**R-C: context and optional LLM**

- WHY: “Fine.”, quotes, location references and consent depend on context.
- WHAT: same-case adjacent context, reviewed boundaries and source-linked candidate interpretations.
- HOW: bounded context pack and optional foreground reasoning; validate schema/anchors then request review.
- MODEL: Qwen3-0.6B `.litertlm`; richer GGUF challenger only after quality/device gates.
- RUNTIME: LiteRT-LM Kotlin first; llama.cpp NDK/JNI only for the qualified challenger.
- DATA: licensed conversation counterfactuals, context omitted/changed, exact source-anchor evaluation.
- ANDROID: ContextBuilder +LocalReasoner lazy session; unavailable/invalid output remains reviewable.
- LIMITATIONS: local generation can hallucinate/refuse; native timeout/cancellation must be tested.

**R-D: descriptive temporal engine**

- WHY: pattern persistence is a different task than single-message polarity.
- WHAT: distinct reviewed counts, per-speaker tracks and capture gaps; validated prevalence/intensity only later.
- HOW: versioned deterministic windows/statistics over compatible observations; invalidate after corrections.
- MODEL: no mandatory temporal neural model or LLM; upstream signal identity retained.
- RUNTIME: pure Kotlin/statistics.
- DATA: timestamped sequences with dedup/capture gaps/corrections and roles.
- ANDROID: PatternEngine recomputed transactionally after reviews, index/pattern invalidation on corrections.
- LIMITATIONS: sampling bias/partial history; abstain from comparative rates when coverage differs.

**R-E: personalization without automatic learning**

- WHY: reduce review burden while preserving safety and privacy.
- WHAT: explicit notes/prototypes/preferences and optional deviation features; frozen harm thresholds.
- HOW: use approved reference examples with source/version identity, reset/remove controls and no automatic training.
- MODEL: optional qualified embedder or compatible upstream feature statistics.
- RUNTIME: Kotlin scoring and encrypted case storage; native embedder only if retrieval/personalization gates justify it.
- DATA: user-selected benign examples, explicit purpose/consent, versioned feedback reasons.
- ANDROID: BaselineStore/ReviewFindingUseCase, reset/remove controls; future head-training experiment isolated.
- LIMITATIONS: baseline abuse/poisoning, sparse biased corrections, no guarantee user-normal means harmless.

### Supporting recommendation contracts

| Decision | WHY | WHAT | HOW | MODEL | RUNTIME | DATA | ANDROID | LIMITATIONS |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Optional sentiment | Avoid duplicating emotion UI without a measured need | Not evaluated by default; later three-way polarity | Qualified export, preprocessing and per-slice calibration | Cardiff reference or distilled head | ORT Android | Licensed polarity/context units | Optional adapter in TextSignalEngine | Tweet-domain bias; no safety equivalence |
| Routing | Confident narrow negatives can miss context | Auditable suggestion/context/review actions | Cue/user bypasses; held-out policy tuning; resource checks | No mandatory router model | Kotlin; optional reasoner lease | Separate calibration/policy/test partitions | InferenceRouter and explicit error states | No qualified automatic gate yet; conditional metrics required |
| Multilingual views | ASCII/language tags do not prove task support | Per-language/script/code-mix qualification and abstention | Preserved original, bounded mapped views, native-speaker evaluation | MuRIL/Indic teachers; compact task student later | Qualified ORT export/native tokenizer | Licensed Indic/code-mix and domain hard negatives | ModelSupportRegistry and view builder | English MVP only; normalization/translation can change meaning |
| Evidence memory | Adjacent context may omit reviewed boundaries | Same-case reviewed retrieval only when useful | Adjacent/lexical baseline; measure semantic recall gain | No required embedder; MiniLM/E5 challengers | Kotlin scan plus ORT if enabled | Explicitly confirmed eligible case excerpts | Encrypted store with correction/deletion invalidation | Embeddings sensitive; index compatibility and recall untested |
| Vault/review/provenance | Sources and claims must stay distinct | Immutable originals, review revisions, explicit export | Authenticated encryption, hashes, canonical code-point anchors and versioned rebuilds | No ML required | SQLCipher/Keystore/Kotlin | Authorized evidence, derivatives and review decisions | Repository, reviewer and ReportBuilder | Hash is not authenticity; recovery/compromise/backup need tests |
| Native model lifecycle | Small files do not guarantee low peak memory | One owned session/lease policy and finite optional reasoning | Pin graph/tokenizer/padding/backend; validate outputs; close after leases | Two compact checkpoints; one optional reasoner | ORT CPU; optional LiteRT-LM or llama.cpp | Synthetic parity fixtures plus representative device workload | ModelSessionManager; bounded dispatch; chunked work | No Android parity/resource/cancellation qualification yet |

## 6. Candidate Models and exact artifact choices

| Role / candidate | Metadata / format | Quantization / Android inference contract | Decision / limits |
| --- | --- | --- | --- |
| MVP emotion minuva MiniLMv2 GoEmotions | About 30 M,6 layers, hidden 384,28 logits, RoBERTa vocab;30,458,927 bytes ONNX | Existing optimized INT8; ids/mask int64[B, L], logits float[B,28], sigmoid | Use as review-only English baseline; don't copy card top-1-only display |
| MVP narrow behaviour minuva Jigsaw | About 23 M,6 layers, hidden 384,6 logits;22,864,978 bytes ONNX | Existing INT8; ids/mask/type_ids int64[B, L] ->logits[B,6], sigmoid | Rules/user-context required for categories missing from training |
| SamLowe RoBERTa GoEmotions | About 125 M,28 labels; float 499 MB/INT8 125 MB ONNX | Same multi-label sigmoid; distinct byte tokenizer; ORT | Quality/teacher challenger, not mandatory larger resident model |
| Cardiff latest sentiment | RoBERTa-base, about 125 M,3-way; HF checkpoint | Export FP32 ONNX ->compare INT8 ->ORT; softmax negative/neutral/positive | Optional reference/teacher; not downloaded/exported here; CC-BY 4.0 card |
| DistilBERT SST-2 /fine-tuned task variants | About 66 M, English; binary sentiment or own head | ONNX/INT8 ->ORT; tokenizer+labels per head | No neutral in SST-2; more memory than MiniLM; fine-tune for target labels |
| MobileBERT | About 25 M, bottleneck 24-layer encoder; compatible `.tflite` head | TF fine-tune/export ->metadata task API/LiteRT CPU, or verified ONNX | True Android precedent; not multilingual safety checkpoint |
| ALBERT-base | About 12 M unique weights, shared layers | TF/ONNX head export ->LiteRT/ORT | Storage small but repeated compute; task quality must be matched |
| TinyBERT/MiniLM-L3/ELECTRA-tiny | Small student families | Distill task head ->ONNX/LiteRT ->native API | Lower cost vs capacity/rare-class/context tradeoff |
| Multilingual MiniLM-L12-H384 /E5-small | About 118 M incl multilingual vocab;384 D | Own task head ->ONNX/INT8 parity ->ORT/native tokenizer | Production multilingual student candidate, not prebuilt harassment/emotion model |
| mBERT-base | About 179 M,104 Wikipedia languages | Fine-tune ->ONNX ->ORT; WordPiece | Larger than English MiniLM; romanized/code-mix weakness |
| MuRIL-base /IndicBERTv2 | Indian transliteration pretraining /278 M Indic v2 | Task fine-tune/distill ->ONNX ->ORT; TF/LiteRT alternative | Teacher/backbone shortlist, not automatic phone/task capability |
| DeBERTa-v3-small | 44 M nonembedding+98 M embedding, about 142 M total | Verify disentangled attention export ->ORT | Research teacher, not “44 MB tiny mobile model” |

MVP manifests:

- Emotion Hub **`4fea72b9ec71ba8d84b88e0efa2ace3dcc733bfc`**, `model_optimized_quantized.onnx`; SHA256 **`594ac3bf3c82e2ea187e50982ea2f811ede5377eaad0c8ad23bc04ee8a2486c6`**.
- Behaviour Hub **`c035f27b6a6d68770f8069a4829f1715a48b8d51`**, same file name; SHA256 **`bcd9dfb48cad802ac8f7cd789e1294f1f0b22d532797bd41f5a11694e3c269a0`**.
- Cardiff sentiment reference Hub **`3216a57f2a0d9c45a2e6c20157c20c49fb4bf9c7`**; own export/hash required.

Store tokenizer/config/label-order/padding/calibration hashes too. Export/runtime details must be validated on Android; these hashes are tested Linux assets, not released Sakshi phone artifacts. [M1-M8]

### Local generative candidates: deployment, not size-based selection

| Candidate | Format / runtime / native context | Published resource evidence | Use / limitation |
| --- | --- | --- | --- |
| Qwen3-0.6B | Community dynamic block 32 INT4 `.litertlm`,329 MB,4096 export; LiteRT-LM; publisher 32 k model context | Separate mixed-INT4/context 2048 benchmarks do not apply to 329 MB export | First Kotlin-native low-cost experiment; quality unvalidated |
| Gemma3-1B-IT QAT | 529 MB optimized mobile artifact,2048 benchmark context; LiteRT-LM;32 k native, text-only | Google S24 Ultra benchmark up to 2585 prefill tok/s; startup/cache separate | Gated Gemma terms; prefill not decode; no universal Indic/safety quality |
| Qwen3.5-2B Arm text-only | `Qwen__Qwen3.5-2B_llamacpp_optimized.gguf`, Q4_K_M,1215.36 MB; llama.cpp; deployed 2 k/4 k not 262 k native | vivo X300 CPU,128 input/128 output,4 threads, p50 6782.29 ms, TTFT 983.29 ms,22.09 decode tok/s, peak 2523.97 MB | Richer conditional-context challenger; Android example JNI required, not Python on phone |
| Qwen3-1.7B | GGUF Q4/Q5 conversion or official Q8_0; llama.cpp | Exact chosen quantized file/RAM must be measured; official Q8 about 1.8 GB | Alternative if quality >0.6 B; no “1.7 B means 850 MB total RAM” claim |
| LFM2.5-1.2B-Instruct | Official GGUF/ONNX; compatible hybrid runtime | Publisher mobile efficiency claims; not independently reproduced | English/control experiment; listed 8 languages exclude required Indic; custom LFM license |
| Gemma4-E2B /Phi4-mini /Llama3.2 | Compatible mobile bundle/GGUF/ONNX/.pte per exact graph | Device-specific publisher data, not matched Sakshi result | Optional 1 B-4 B controls only if specific quality need; licenses/language coverage differ |

One optional reasoner, never an LLM per category. The existing architecture report proposes Qwen3.5-2B; the earlier Laya report proposes smaller Qwen3-0.6B. Resolve this as **different experimental tiers**, not contradictory accuracy winners: two-checkpoint MVP has no mandatory LLM, Kotlin-native tiny model first integration experiment, Arm 2 B text model richer CPU benchmark challenger. Final selection requires matched context/anchor quality+device resources. [M9-M12]

## 7. Android Runtime Comparison and conversion contracts

| Runtime | Model/format -> Kotlin/API | CPU/GPU/NPU and engineering constraints | Sakshi recommendation |
| --- | --- | --- | --- |
| ONNX Runtime Android | HF/PyTorch export ->`.onnx`/optional`.ort` ->`OrtEnvironment`,`OrtSession`,`OnnxTensor` ->logits ->application decode | Full Android package has Java/C/C++; CPU baseline; XNNPACK/NNAPI/QNN coverage conditional; NNAPI deprecated since Android 15 | Primary compact text/embedding runtime; full ops before reducing package |
| LiteRT /TensorFlow Lite | TF or supported PyTorch converter ->`.tflite` ->`CompiledModel` or legacy Interpreter | Quantization/delegates depend on graph; current CompiledModel provides Kotlin acceleration API; model metadata/tokenizer not universal | MobileBERT/tiny classifier alternative; no automatic ONNX filename conversion |
| MediaPipe Tasks Text | Compatible `.tflite`+mandatory task metadata ->`TextClassifier.classify(text)` /TextEmbedder | Tokenizer-inclusive supported tasks; labels come from trained model; HF arbitrary graphs unsupported | Convenient independent alternative, not default second text runtime |
| LiteRT-LM | Compatible converted/bundled`.litertlm`+tokenizer/chat metadata ->Kotlin Engine/Conversation/Flow | CPU/GPU/NPU per compatible artifact/build; initialize can take seconds; native libraries/caches required for some backends | First optional generative Kotlin implementation |
| llama.cpp | Supported HF decoder ->GGUF ->Q4_K_M/Q5/Q8 export ->NDK/JNI ->Kotlin Flow | Arm CPU kernels, optional validated GPU builds; portable ABI build, reasonable context; not every architecture supported | Native richer CPU reasoner path, not compulsory alongside LiteRT-LM |
| ExecuTorch | `torch.export`+operator lowering ->`.pte` ->Module/EValue/Tensor via AAR or C++ JNI | XNNPACK/QNN/Vulkan build/operator compatibility; Java API stability version-sensitive | Alternative export experiment, not needed for initial stack |
| Python embedded/server | Native Python dependencies and ABI packages required | Linux/glibc or ARM desktop support !=Android/Bionic; service adds lifecycle/privacy burden | Do not use for inference MVP |

Official MediaPipe **LLM** Inference docs now say maintenance-only and recommend LiteRT-LM Kotlin. This is not a statement that MediaPipe Tasks Text is deprecated. Current llama.cpp docs explicitly describe GGUF loading from app-private files, AiChat/InferenceEngine and Kotlin Flow; don't stop at Python examples. [R1-R5]

### Complete model-to-result flows

```text
Emotion:
  MiniLMv2 RoBERTa GoEmotions checkpoint ->publisher optimized INT8 ONNX
  ->pinned model_optimized_quantized.onnx +tokenizer/config/manifest
  ->ORT Android CPU ->native byte-BPE tokenizer +Kotlin tensor feed
  ->float [B,28] ->sigmoid/calibration per label ->EmotionObservation

Behaviour:
  MiniLMv2 BERT Jigsaw ->publisher INT8 ONNX ->pinned file+WordPiece assets
  ->ORT Android CPU ->Kotlin int64 ids/mask/type_ids ->[B,6]
  ->independent sigmoid/calibration ->NarrowBehaviourSignals

Optional sentiment:
  Cardiff3-way checkpoint ->offline ONNX export with final preprocessing
  ->FP32 reference then tested INT8 ->owned hashed artifact
  ->ORT Android CPU ->Kotlin tokenizer/feed ->[B,3] ->softmax ->SentimentObservation

Production shared student:
  licensed emotion/behaviour/context data ->train/distill same encoder +multiple heads
  ->export named outputs (emotion_logits,behaviour_logits,context_logits,optional sentiment_logits)
  ->float reference/quantization/shape parity ->one ORT session
  ->Kotlin per-head decoder/calibration ->versioned signals

Optional generative tier:
  compatible Qwen/Gemma ->converted quantized.litertlm ->LiteRT-LM Kotlin
  OR Arm Qwen3.5-2B text GGUF ->llama.cpp arm64 NDK/JNI
  ->bounded case prompt ->finite structured output ->schema/anchor/quote checks ->review
```

Don't merge two pretrained heads merely because both names contain MiniLM: current behaviour is BERT WordPiece and emotion is RoBERTa byte-BPE, with distinct vocabularies/weights. Shared student means **new joint training/distillation**, not graph concatenation or swapping tokenizer.

### Quantization requirements

- Freeze preprocessor, tokenizer, padding, length, batch shape before evaluating probability parity.
- Compare source float checkpoint, desktop FP32 graph, optimized graph, quantized graph and Android graph on target slices. INT8 can change argmax, per-label threshold crossings and scores without changing output shapes.
- Dynamic INT8, static calibration, QAT, FP16 and mixed precision are separate artifacts. FP16 on CPU can upcast; quantizing a graph doesn't guarantee GPU/NPU kernels.
- Per-label threshold/temperature must be fit on final deployed export/padding/backend. Record graph/schema/calibration identities.
- Select representative language/rare-category/OCR-STT noise calibration data for static quantization. No universal 4 x RAM/2 x speed/1% accuracy promise.
- Model file storage isn't Java/native/backend memory; avoid reading entire graph into a large Java byte array when a file-backed API suffices. Verify actual mapping/decryption behavior before assuming zero-copy.

## 8. Detailed Inference Pipeline and structures

### One WhatsApp/Instagram evidence unit

1. **Acquisition:** notification observation after explicit opt-in, or user-shared/file-picked text/image/audio. Sender/app/time remain source claims. Notifications do not supply underlying media or full history.
2. **Retention boundary:** selected original Save consent ->hash/encrypt original immediately. Passive observation ->transient or separately consented expiring review inbox; no universal chat archive.
3. **Extraction:** parse text or bounded OCR/STT; source regions/times and quality/truncation recorded. OCR/STT recognition confidence isn't classifier uncertainty. No ambient voice emotion or speaker identity inferred.
4. **View builder:** preserve raw extraction; create checkpoint-contract text view and optional normalization candidates, with many-to-many offset maps. Do not strip negation/quotes/emoji/Indic joining characters.
5. **Support check:** language/script/code-mix, known model slices, token fertility/UNK, available context, not merely app language or ASCII.
6. **Independent channels:** rules/user concern/contact metadata; eligible behaviour; optional expressed emotion and polarity. A failure in one doesn't erase the other or original.
7. **Router:** selected context/user concern/high-impact cues/known weak categories/uncertainty ->context path; others provisional finite suggestions. No auto-store confidence branch.
8. **Context:** same-case authorized adjacent turns, reviewed boundaries, deterministic prior counts; optional confirmed semantic retrieval.
9. **Reasoner:** optional supported foreground local model with finite context/output; unknown/error states explicit; no tool/network execution.
10. **Validation/review:** finite numbers, label enums, source membership, quoted spans, role attribution, truncation, grounding checks; user confirms/rejects/edits/adds context.
11. **Persistence/patterns:** encrypted versioned analysis; distinct reviewed events form temporal facts; provisional previews labelled pending; corrections/deletion invalidate patterns and index.
12. **Report:** user-reviewed selected evidence references and deterministic dates/counts; LLM wording optional; explicit redacted export, no legal conclusion.

### Improved result contract

Illustrative schema; numbers are placeholders for shape demonstration, **not measured/calibrated incident claims**. Raw output and calibrated probability are different fields; no fake intensity or LLM self-confidence.

```json
{
  "schema": "sakshi-text-signals/1",
  "analysis_id": "analysis-184-v1",
  "evidence_id": "message-184",
  "case_id": "case-A",
  "input_derivative_id": "text-184-v2",
  "input_sha256": "manifest-input-digest",
  "speaker": {"id": "speaker-A", "status": "user_asserted", "role": "unknown"},
  "availability": {"source": "notification_excerpt", "coverage": "partial", "truncated": false, "timestamp_status": "publisher_claimed"},
  "language": {"codes": ["en"], "code_mixed": false, "support": "experimental_english"},
  "emotion": {
    "status": "suggestion_uncalibrated",
    "labels": [{"label": "anger", "raw_score": 0.72, "calibrated_probability": null}],
    "intensity": null,
    "rubric_id": "goemotions-labels/1",
    "scope": "expressed_language_only"
  },
  "sentiment": {"status": "not_evaluated", "distribution": null},
  "behaviour": {
    "status": "narrow_model_only",
    "signals": [{"label": "possible_insult_language", "raw_score": 0.84, "calibrated_probability": null, "epistemic_status": "inferred"}],
    "unsupported_categories": ["coercive_control", "stalking", "blackmail"]
  },
  "context": {"requires_context": true, "reasons": ["quote_or_target_uncertainty"], "used_evidence_ids": []},
  "uncertainty": {"reasons": ["uncalibrated", "partial_evidence"], "same_task_disagreement": null, "entropy": null},
  "routing": {"next_action": "human_review", "reasoner_requested": false, "reasoner_status": "not_run"},
  "anchors": [{"derivative_id": "text-184-v2", "span": {"kind": "text", "start": 0, "end": 10, "unit": "unicode_code_points"}, "role": "analyzed_region"}],
  "runs": [{"model_id": "pinned-emotion", "artifact_sha256": "model-manifest-digest", "padding_policy": "manifest-policy", "runtime_id": "ORT-Android-CPU", "calibration_id": null}],
  "review": {"state": "pending", "signal_reviews": [], "evidence_association_review": "pending"}
}
```

Required states: not_evaluated, unsupported_language, extraction_uncertain, context_missing, uncalibrated, model_unavailable, resource_deferred, cancelled, invalid_output, suggestion, pending_review, confirmed/rejected/edited. Missing !=neutral !=benign. Don't collapse busy/timeout/parser failure into null safe.

Persist text anchors as half-open **Unicode code-point** ranges `[start, end)` on the identified immutable derivative, matching `data/sakshi-event-schema.json` (`span.kind="text"`, `span.unit="unicode_code_points"`). This text-signals sketch is a proposed analysis result, not an already validated event-schema payload; an explicit adapter must construct and validate canonical events. Kotlin `String`/Compose APIs use UTF-16 code-unit indices internally. Convert only at the UI/tokenizer boundary with `text.offsetByCodePoints(0, codePointIndex)`; reverse with `text.codePointCount(0, utf16Index)` after checking a boundary does not split a surrogate pair. Validate `0 <= start <= end <= text.codePointCount(0, text.length)` and round-trip both ends. A supplementary emoji occupies one code point but two UTF-16 units; combining/ZWJ sequences contain multiple code points and are not single grapheme offsets. UTF-8 bytes, tokenizer offsets, OCR polygons and audio milliseconds remain separate coordinate systems. Every view transformation needs explicit many-to-many source-span maps and a derivative identity, not index equality. Whole-message classifiers anchor the analyzed region without claiming a learned rationale span. Exact LLM quote matching establishes presence, not interpretation entailment.

### Persistence entities

```text
EvidenceArtifact(id,caseId,originalHash,encryptedPath,acquisitionKind,sourceClaims,retentionPolicy)
TextDerivative(id,artifactId,parentRevision,extractorManifest,textCiphertext,sourceMap,quality,viewPolicy)
AnalysisRun(id,derivativeId,graph/tokenizer/schema/calibration/runtimeIds,paddingShape,inputHash,status)
SignalObservation(runId,label,rawLogit/rawScore,calibratedProbability,rubricId,scope,anchorIds)
ReviewDecision(id,targetType,targetId,action,reason,userEditedValue,revision)
EventAssociation(id,evidenceIds,claimedSpeaker/time,distinctEventStatus,reviewStatus)
PatternSnapshot(id,caseId,policyVersion,sourceEventIds,counts,comparability,gaps,reviewStatus)
EmbeddingRecord(derivativeId,embedderManifest,caseId,vectorCiphertext,indexRevision)
```

Room is schema/access layer, not encryption. SQLCipher Room SupportOpenHelperFactory path is documented; avoid assuming its Android SQLite driver automatically composes with every vector extension/Room bundled driver. Store sensitive scores/relationships/indexes encrypted too. [R6-R7]

## 9. Local AI Router Design: scientific threshold selection

### Features and allowed actions

`x = {languageSupport,extractionQuality,quotedness,roleUncertainty,truncation,length,categoryScores,entropy,sameTaskDisagreement,normalizationInstability,novelty,priorReviewedPattern,userConcern,resourceBudget}`.

Actions: FAST_SUGGESTION, CONTEXT_ONLY, LOCAL_REASONING, HUMAN_REVIEW, EXTRACTION_REVIEW, RESOURCE_DEFERRED. These actions don't choose evidence retention or final factual status. Capture policy/user confirmation owns persistence.

Mandatory bypasses of a confident-negative narrow model:

- User marked concern or requests contextual review.
- Conditional exposure/restrictive/unwanted-contact/explicit-harm cue with source span.
- Prior confirmed boundary/repetition context, even low toxicity.
- Quote/negation/ambiguous target, missing relevant context or destructive truncation.
- Unsupported language/poor extraction: abstain to user rather than automatically trust a larger English model.
- Known weak task slice or disagreement between original/normalized-view scores.

### Entropy and disagreement

For 3-way sentiment: `H=-sum(p_c log p_c)/log(3)`. For independent emotion/behaviour labels use **binary entropy per label**:

`h_l = -(p_l log p_l + (1-p_l)log(1-p_l))/log(2)`.

A mean binary entropy can hide one uncertain rare threat label, so retain per-label h and rubric-sensitive policy. Low entropy isn't OOD detection: confidently wrong outputs remain. Margins/range must be calibrated/evaluated per task.

Same-task disagreement between models with identical label definitions/context: binary probability gap or JS divergence between distributions. Emotion neutral vs behaviour threat is not statistical model disagreement because different targets. More models add cost and correlated mistakes; do not require an ensemble in MVP.

Novelty: e.g. `d=1-max(cos(e_current,e_confirmed_reference))`, only within approved case/speaker/language and same embedding version. It is relevance/deviation, not probability of abuse. Missing benign reference ->unknown, not novel danger.

### Selecting thresholds, not inventing 0.85

1. Freeze rubrics, graph/tokenizer/padding/backend, context policy and train/dev/calibration/test split by conversation/person/source/template family.
2. Fit probability calibration on the final exported backend: per-label temperature+bias/Platt where justified; 3-way shared temperature baseline. Class weighting/resampling changes score/prior interpretation.
3. On a separate **routing-policy tuning** partition, sweep per-label suggestion/defer bands, entropy/disagreement/novelty and cue bypasses. Jointly measure relevant-incident recall, benign false positives, review load, LLM invocation and energy. Calibration-fitting data is not final performance validation.
4. Minimize a documented cost such as `c_FN*missed_harm +c_FP*false_alert +c_H*review_work +c_L*LLM_energy`, subject to expert/user-defined per-category/language recall/false-positive constraints with confidence intervals. Coefficients/constraints are product decisions, not deduced from benchmark popularity.
5. Validate locked policy on test; bootstrap by conversation. Report accepted AND routed/abstained slices, including confidently wrong negatives. Tiny sparse slices fail qualification; don't hide them in macro accuracy.
6. If no policy passes, ship review-only suggestions/context prompts, no automatic correctness/“safe” gate. Human feedback cannot replace representative calibration.

Selection changes the probability population: global calibration does not imply accepted/routed-set calibration. Calibrated selective-classification literature specifically distinguishes these. Conformal prediction sets are a later optional uncertainty mechanism; conversation dependence/drift/selection can violate naive exchangeability, and marginal coverage isn't safety guarantee. [C1-C3]

### Kotlin policy sketch

Proposed pseudocode, not compiled application or official framework API:

```kotlin
fun route(input: TextEvidence, fast: FastSignals, policy: QualifiedPolicy?, request: ReviewRequest): Route {
    if (!input.quality.usable) return Route.extractionReview()
    if (!fast.supportedLanguage) return Route.humanReview("unsupported_language")
    val independentConcern = request.userConcern || fast.ruleCandidates.isNotEmpty() || input.hasReviewedBoundary
    val contextNeed = input.truncated || fast.quoteOrRoleUncertain || fast.contextCue || independentConcern
    if (policy == null || fast.uncalibrated) return Route.reviewOnly(contextNeed)
    val selected = policy.select(fast, input)
    if (!contextNeed && selected.qualifiesForFastSuggestion) return Route.fastSuggestion()
    return if (request.foregroundReasoningConsent && request.deviceBudget.permitsReasoning) {
        Route.localReasoning(contextNeed = true, reasons = selected.reasons)
    } else {
        Route.humanReview("context_or_uncertainty")
    }
}
```

In demo review-only mode a user can explicitly request contextual reasoning even when no policy qualifies; this is not calibrated automation. Resource defer doesn't become lower-confidence benign. Rate-limit repeated analysis by evidence/view/model/review-policy identity, not raw text alone across distinct real events.

## 10. Emotion Intensity and Emotion Trajectory Design

### Intensity: valid future implementations

Emotion presence P(anger) and expressed anger magnitude are distinct. A highly certain classification may indicate mild anger; an intense message may be uncertain. Softmax/sigmoid score is not an intensity label. [T1-T3]

Options:

1. **Regression:** `i=sigmoid(w*h+b)` trained on human comparative intensity labels; Huber/MSE loss. Evaluate MAE, Spearman/Pearson, inter-rater reliability and domain/language transfer. Validity pertains to expressed-language annotation, not internal feeling/danger.
2. **Ordinal:** labels none/low/moderate/high/very-high under a defined rubric. Train cumulative-threshold model (CORAL) or conditional-threshold model (CORN) with monotonic probabilities. Better interpretability than arbitrary five decimals, still needs data.
3. **Distributional ordinal:** retain P(level=k); normalized expected index `I=sum(k*p_k)/(K-1)` and dispersion `Var=sum((k-Ek)^2*p_k)`. Dispersion isn't model epistemic uncertainty and cannot calibrate itself.
4. **Pairwise/best-worst scaling:** human relative comparisons reduce inconsistent direct numeric annotation; SemEval/WASSA use comparative emotion-intensity data. LLM comparative labels can be distillation inputs, not replacement for human test labels.

CORN concrete decode:

`r_j = sigmoid(z_j) = P(Y>j |Y>j-1)`;
`q_j=P(Y>j)=product_{a=0..j}r_a`;
`p_0=1-q_0`, `p_k=q_(k-1)-q_k`, `p_(K-1)=q_(K-2)`.

This guarantees nonnegative masses from conditional probabilities. These computations are a small exported head/Kotlin postprocessor, not an LLM. Do not calibrate cumulative thresholds independently in a way that breaks ordering. CORAL/CORN author code was developed/evaluated mainly on ordinal image tasks; using it for Sakshi text is a proposed adaptation. [T3]

**Dataset/legal constraint:** original NRC emotion-data terms say free research use and no redistribution; commercial rights need clarification. NRC lexicons have separate commercial terms. GoEmotions has presence labels, not intensity. Common HF SemEval configuration exposes E-c, not all EI-reg data. An MIT intensity implementation doesn't license its training corpus. [T1-T3][D1-D3]

**UI:** MVP intensity=null. Future validated rubric can show low/moderate/high expressed intensity with uncertainty/support, not “anger 0.94” or a violence score. Numeric scales may be internal evaluation features; render raw event points and source IDs, not a smooth curve implying psychological precision.

### Independent feature tracks

For distinct evidence event i:

- `E_i[l]`: supported calibrated emotion-presence probability (or clearly marked raw score, not mixed with calibrated runs).
- `B_i[c]`: calibrated behaviour signal under rubric and attributed speaker/context.
- `S_i`: optional polarity distribution;`v_i=p_positive-p_negative` is expected polarity, not intensity.
- `I_i[l]`: optional separately trained intensity, otherwise missing.
- `A_i`: speaker/target/quote status, availability, gaps, review state, model/view/calibration identities.
- `C_W[c]`: distinct reviewed counts per named window, not sum of notification callbacks.

Never combine all into an undocumented weighted “risk score.” Persist a feature vector with supporting IDs and named deterministic pattern predicates.

### Time-aware descriptive statistics (proposed)

Per-speaker/case/model-compatible track; do not interleave survivor and other speaker.

Weighted window:

`w_i=exp(-(t-t_i)/tau)`;
`mu_W=sum(w_i*x_i)/sum(w_i)`.

Weights prioritize recency, not source authenticity or importance. Use only eligible observations; missing/unsupported isn't zero. Don't weight by model confidence to automatically suppress ambiguous threats.

Time-aware EWMA for actual timestamp intervals:

`alpha_i=1-exp(-Delta_t_i/tau)`;
`m_i=(1-alpha_i)*m_(i-1)+alpha_i*x_i`.

First valid event initializes state. Negative/uncertain timestamps require reorder/unknown handling. Same-time events may need event-order aggregation; alpha 0 must not erase a second explicit threat from raw event findings. Long gaps split the displayed track or reinitialize; absence of messages is not evidence of calm.

Volatility descriptor: `RMSSD=sqrt(mean((x_i-x_(i-1))²))`, only adjacent comparable observations. It ignores irregular time by itself; report units/event spacing. Slope: robust/weighted regression of x on actual times or bounded adjacent difference `(x_i-x_(i-1))/Delta_t`; no rate for missing/ambiguous time.

CUSUM candidate: `g_i=max(0,g_(i-1)+x_i-mu_baseline-k)`; flag candidate change when `g_i>h`. k/h/tau/windows must be selected on held-out annotated sequences for desired false-alert/delay tradeoff, not invented danger constants. Correlated model outputs and partial capture violate simple iid null assumptions. Bayesian/change-point/GRU/temporal-transformer models are future research if deterministic descriptions insufficient.

Frequency: `N_W` counts actual distinct observed/reviewed events. Rate per observable duration only when comparable collection coverage is defensible; no inferred percent-captured denominator or complete WhatsApp history. For retrospective imports, source event time and import time separated.

### Pattern algorithm

- Determine reviewed speaker/case/quote associations and distinct-event identities.
- Compare compatible observation windows; carry explicit capture/import gaps.
- Keep repetition/persistence/frequency and behaviour transitions independent of emotion.
- Describe observed category changes or repeated reviewed boundary violations with supporting event IDs and coverage caveats. Do not turn the trajectory into an escalation verdict or future-danger prediction.
- Emotional trajectory describes observed expressed language. Neutral->annoyance->anger is possible, but hostility/intimidation are behaviour/rubric signals, not later universal emotion stages.
- Semantic incident clustering is a review suggestion; same words can be distinct events and different words same incident. Hash equality never silently deletes evidence.
- Rejected/edited/context-corrected signal changes invalidate derived counts/trajectory/pattern snapshots without altering originals.

UED/DialogueRNN/temporal cyberbullying are prior art for home base, variability, rise/recovery, party state and temporal context. Their clinical/fictional/public-platform constructs do not establish safe harassment escalation from phone excerpts. [E7-E8][T4-T5]

## 11. Contextual / Conversation-Level Reasoning

Single-message mode: exact analyzed text, source excerpt/truncation, supported narrow labels, available role/quote status. Low score means no recognized signal in this model's input, not complete benignness. Conversation mode: bounded authorized adjacent turns, same speaker/target associations, reviewed boundaries, prior relevant distinct events and known gaps.

Trigger conversation mode for short ambiguous responses (“Fine.”), sarcasm, conditional demands, reported/quoted harm, implicit location warnings, consent-dependent sexual language, changes in context or user request. A pretrained isolated-text emotion model doesn't become conversational by concatenating arbitrary vault text.

**Context pack, initial design caps not validated universal values:** latest message kept whole where possible; up to six adjacent authorized turns, up to four relevant confirmed excerpts, one reviewed stop/consent boundary, and deterministic count/coverage summary. Allocate by actual selected-model tokens, not character count; reserve output/metadata budget and report excluded evidence. Start 2 k context for mobile, extend only after quality/RAM tests; don't allocate publisher 262 k default.

```text
ContextPack(caseId,inputDerivativeId,allowedEvidenceIds,
            adjacentTurns[role,time,quote,text,anchors],
            retrievedConfirmedExcerpts[score,sourceId,revision],
            reviewedBoundaries,deterministicCounts,knownGaps,
            totalTokens,omittedIds,manifestIds)
```

No cross-case/person memory, private-database scraping, notification-remote-action fetching, screen monitoring or hallucinated speaker identity. Conversation summaries are versioned derivatives with exact source IDs, not replacement evidence. Keep extractive summaries first; don't let generated summary text accumulate as its own ground truth. Episodic summaries later after source/omission/entailment evaluation.

LLM output should list possible supported signals, exact source excerpts, unknown factors and alternatives. It must distinguish “speaker says harm” from “user reports harm.” Dates/counts come from structured data, not regenerated arithmetic. No tool execution/network or action advice solely from tone. Safety experts review any guidance; evidence organization isn't emergency protection.

Evaluate paired counterfactuals with identical target message/different context, masked-context controls, missing speaker information, temporal reorder and malicious evidence instructions. A valid output schema/citation is necessary but insufficient: presence of excerpt doesn't prove entailment.

## 12. Local RAG / Private Evidence Memory

Start with reviewed case/time/speaker association and lexical search within encrypted storage, plus adjacent turns. Add semantic retrieval only when measured relevant-evidence recall improves vs this baseline. No backend/vector server needed.

| Option | Exact path | Memory / latency / security caveat |
| --- | --- | --- |
| all-MiniLM-L6-v2 English | 22.7 M,384 D, masked mean+L2,256 tokens ->ONNX ->ORT CPU/native WordPiece | About 22.7 MB parameter arithmetic if entirely INT8, actual graph/tokenizer/runtime additional; phone latency U |
| multilingual-E5-small | 117.7 M,384 D,512 tokens, query/passage prefix ->ONNX ->tested INT8 ->ORT | Larger multilingual vocab; low-resource/code-mix quality requires tests; no generic 118 MB total RAM |
| MediaPipe TextEmbedder | Compatible `.tflite`+metadata ->TextEmbedder Kotlin | Convenient alternative; arbitrary HF model not automatically compatible |
| Kotlin exact cosine scan | Encrypted FloatArray blobs ->unlock authorized candidate set ->bounded dot products ->topk IDs | 1 k 384 D vectors=1.536 MB raw;10 k=15.36 MB;384 k/3.84 M multiply-adds, not measured latency |
| sqlite-vec | C SQLite extension ->custom compatible Android SQLite/SQLCipher build+JNI ->case-partitioned SQL | Pre-v 1 API changes; Room bundled-driver integrations don't establish SQLCipher compatibility; measure migration/deletion |
| HNSW/native index | Compatible ANN library/index ->JNI ->bounded case search | Needs serialization/encryption/versioning and deletion rebuild; defer until scale warrants |

Kotlin scan is the MVP semantic implementation if needed. Precompute embeddings for **confirmed** evidence, not all ordinary transient messages. Batch after user review under consent; decrypt only selected case chunks, close/clear caches on lock. Embeddings reveal sensitive semantic information and require encryption like text. Raw classifier hidden states aren't necessarily trained retrieval embeddings. [M5-M6][R7]

Pipeline: query tokenization ->query embedding ->same-case eligible candidates ->cosine/lexical/adjacent union ->diversify/remove redundant retrieval representations ->source-version check ->token-budget context pack ->reasoner/review. Semantic similarity doesn't prove identity/duplicate/repetition. Include crucial reviewed boundary regardless of semantic rank. Retrieval misses and reasoning misses reported separately.

Retrieval latency **unknown on target phones**; budget/token count/design arithmetic isn't measurement. Benchmark embedding, vector load/decrypt, scan/ANN, reranking and prompt assembly separately at 1 k/10 k case-size fixtures. Full encrypted index, FTS temporary files, WAL, backup and crash logs must be audited.

## 13. Multilingual & Code-Mixed Strategy and evasive text

**No English checkpoint receives unsupported Indic input and returns a confident safe result in production.** Native script, Latin transliteration, code mixing, slang and language uncertainty are separate qualification slices. Interface language isn't message language. Short texts often defeat language ID; ASCII isn't English.

| Language/slice | Candidate representation/data | Implementation/support policy |
| --- | --- | --- |
| English | Compact MiniLM baselines, GoEmotions/Jigsaw/domain units | Experimental narrow signals until held-out quality/calibration; manual review always |
| Hindi/native +Hinglish | MuRIL multilingual/transliterated teacher, Multilingual MiniLM student, HASOC contextual Hinglish | Own fine-tuned head/native tokenizer tests; not inferred from 100-language claim |
| Malayalam/native +Manglish | MuRIL/Indic teacher, DravidianCodeMix Malayalam-English | Separate native/romanized/mixed test slices; preserve originals even when abstaining |
| Tamil +Tamil-English | MuRIL/Indic, DravidianCodeMix Tamil-English | Native-speaker rubrics; context/consent-label gap |
| Telugu/Kannada | MuRIL/Indic; Kannada CodeMix, additional licensed Telugu domain data needed | Task/data availability explicit; model vocabulary alone insufficient |
| Bengali | MuRIL/Indic, HASOC Bangla releases after terms review | Domain/code-mix annotation needed |
| Marathi/additional Indic | MuRIL/Indic, task-specific HASOC/Indic sources | Future validated packs, not blanket generalization |

MuRIL explicitly trains 17 Indian languages/transliterations; card shows native/transliterated downstream evaluations, not harassment. IndicBERTv2 is 278 M with Indic task coverage, not tiny phone model. Multilingual MiniLM/E5-small has large vocabulary but not an already qualified behaviour/emotion head. Paraphrase multilingual MiniLM's narrower language-card list must not be substituted for E5's XLM-R coverage. [M5-M8][D4-D5]

### Supplied examples

| Text | Context question to resolve | Correct product behaviour |
| --- | --- | --- |
| “nee ivide vannal nokkikko” | Is this an implied warning, ordinary idiom, joke or supplied threat context? Native-speaker review required. | Preserve, language/context unknown to English engine; don't force translation/benign label |
| “Don't tell anyone da” | Harmless surprise/confidentiality vs coercive secrecy, who speaks to whom? | Context candidate, not automatic control/grooming |
| “tum dekh lena” | Routine “you will see” expression vs implied adverse consequence? | Hinglish/Hindi-context review, not exact universal threat translation |
| “nee entha ingane samsarikkunne?” | Question about how someone is speaking, distress/argument/other context? | Native-speaker interpretation; emotion/abuse unknown without validated model |

Local English diagnostic scores in section 17 are not linguistic labels for these utterances. Not having unknown tokenizer tokens doesn't imply semantic competence.

### Translation versus direct multilingual classification

Direct task-fine-tuned multilingual/code-mixed input is preferred where validated. Translation can destroy dialect, pronoun/target ambiguity, negation, conditionality, caste/sexual euphemisms and evidence span fidelity. It adds another expensive/error-prone model. No default translation-before-classification.

If user requests a translation, retain it as a labeled derivative with source associations/uncertainty; never replace original or show it as original evidence. Compare bilingual/native-speaker annotations. Romanization/transliteration can be alternate views, but must preserve ambiguous alternatives and source maps, not silently create one “true” spelling.

### Robust preprocessing for evasive language

**Order:** received bytes/text ->preserve/hash/encrypt ->versioned extraction ->model-specific view ->optional bounded diagnostic normalized view ->signals with transform provenance. Originals never modified.

- Canonical normalization only according to exact tokenizer contract (e.g. NFC where required); preserve source view and many-to-many offsets.
- Compatibility NFKC may map fullwidth text, but changes distinctions. Use a separately named, qualified diagnostic view, not universal destructive default.
- UTS 39 confusable skeleton is identifier/security machinery, not a semantic multilingual rewrite. Flag mixed scripts/confusables; don't Latinize arbitrary Malayalam/Hindi text.
- Limit whitespace/punctuation/leet repairs to clearly scoped Latin candidate tokens (`i d i o t`,`i.d.i.o.t`,`1d10t`) with original-span trace. Preserve alternative interpretations and allow false-positive testing.
- Never globally remove negators, stopwords, punctuation, quotes, emoji, ZWJ/ZWNJ or script marks. Indic joining and emoji sequences can change meaning/rendering. Preserve URLs/names as evidence; model-contract masking (Cardiff @user/http) is a derivative, not evidence deletion.
- Bound candidate views and regex/input lengths. Do not exponentially enumerate spellings or run LLM for all obfuscations. Use character/subword augmentation/distillation and a separate tested character-level challenger if target failures warrant it.
- Same-label original/normalized disagreement is a router feature; don't take maximum raw score over many repairs as a calibrated probability.

HateCheck/Hatemoji/OTH studies demonstrate negation, emoji and Unicode weaknesses, not a guarantee a normalizer fixes Sakshi. Homoglyph normalization improved reported OTH test F1 under its protocol; that result does not transfer automatically to Indic code mix. [U1-U3][D6]

## 14. Personalization without cloud

### Hackathon: preferences and prototypes, not gradient updates

Local personalization is possible without training:

1. User explicitly selects case/speaker-associated examples as representative and states purpose. “Confirmed evidence” isn't necessarily a benign baseline.
2. Store approved contextual notes/stop boundaries/preferred language and correction reasons; update exact case retrieval preferences.
3. Optional prototype: normalized mean of approved embedding vectors `mu=normalize(sum(e_i))`, distance `d=1-cos(e_new,mu)`; track robust emotion feature median/IQR only within comparable model/language/role/view versions.
4. Show “different from selected reference examples,”not “abnormally abusive.” No sufficiently representative references ->baseline unknown.
5. Do not raise threat/coercion thresholds or suppress explicit cue/user concern because language is customary. Repeated abuse can contaminate a “normal” baseline.
6. Reset/remove/recompute baseline and inspect included examples. Freeze source/version identity and test poisoning/shift.

Few-shot context can include a small explicitly approved case rubric/examples in local LLM prompt; it can bias the model or anchor false narratives. Do not personalize safety verdicts by secretly accumulating vault text. Speaker assertions remain user/source claims, not authenticated person models.

### Calibration, threshold adaptation and head learning

A handful of user confirmations is insufficient to fit reliable per-category probabilities. Corrections are selectively sampled, not representative; don't treat overall user-confirmation fraction as calibration. Threshold adaptation affects review workload, not truth. Distinguish reject reasons: wrong role/quote, extraction error, duplicate association, insufficient context, actually absent signal. Rejecting an explanation doesn't label original text harmless.

Future local calibration requires explicit consent, adequate balanced/hard-negative examples and a frozen holdout; retain global classifier/raw outputs and rollback. Prefer fitting a tiny output head/prototype layer over fine-tuning every encoder. Compare with no-adaptation baseline, avoid training and evaluating the same conversation.

### ONNX Runtime on-device training: verified but disproportionate for MVP

Official Android tutorial uses MobileNetV2 frozen feature extractor and a four-class trainable head. Offline Python stage creates ONNX train/eval/optimizer graphs and checkpoint via `artifacts.generate_artifacts`, CE/AdamW. Android Kotlin calls JNI into C++ CheckpointState/TrainingSession; sample CMake imports a training-capable libonnxruntime.so. Training isn't achieved by opening the already INT8 inference graph and calling a Kotlin setter. [R8]

For Sakshi:

```text
frozen validated text encoder ->cached authorized embeddings
  +explicit labels ->small trainable sigmoid/ordinal head
  ->offline generated compatible train/eval/optimizer artifacts+checkpoint
  ->training-enabled ARM64 runtime/JNI OR verified Java training package
  ->bounded local training under opt-in/resource policy
  ->held-out evaluation/calibration ->versioned inference export ->rollbackable activation
```

Java OrtTrainingSession exists, but verify exact Android package/native build capabilities rather than assuming ordinary inference AAR has training ops. Need multi-label loss/missing-label masks, tokenizer/quantized-backbone compatibility, training buffers/checkpoints/optimizer state, key-access policies, session cancellation and energy measurements. Training may still execute encoder forwards/retain activations unless embeddings explicitly separated. Optimizer/memory/battery cost isn't inference cost.

**Decision:** no ONNX training/LoRA/federated learning in hackathon. Production small-head experiment only if privacy/quality gains exceed nonlearning prototypes. Federated updates can leak information and require consent/secure aggregation/differential privacy/threat modeling;“data stays local” alone is not privacy proof. No remote aggregation dependency is needed now.

## 15. Explainability and Human-in-the-Loop Review

| Evidence channel | Useful explanation | Required caveat |
| --- | --- | --- |
| Classifier scores | Possible expressed-label preference | Raw sigmoid isn't calibrated truth/intensity; English domain only |
| Calibrated probability | Validation-population frequency | Not individual authenticity/credibility/legal fact |
| Highlights/attribution | Candidate supporting tokens | Attention/gradient saliency can be unstable; not faithful causality by default |
| Trained rationales | Human-labeled supporting spans | Rationale quality/role/context must be separately evaluated |
| Rule trace | Exact cue/match+span | “mentions an address” isn't actual doxxing |
| Retrieved context | What prior confirmed excerpts were supplied | Similarity isn't causality and retrieval can miss key evidence |
| LLM interpretation | Readable alternative/context explanation | Model-generated, anchors+entailment/user review needed |
| Deterministic patterns | Distinct event IDs/window/coverage/counts | Observed lower bounds, not complete chat history |

User-facing example (synthetic UX, not real finding):

> Possible restrictive-language signal. The selected text asks for approval of people the recipient meets. Expressed emotion is analyzed separately and does not establish harmfulness. Context: three distinct reviewed contact-related events in the selected 48-hour observation window. Sources: 184 (current), 170, 178, 182 (three prior events). Consent, identity and completeness remain unestablished. Confirm, Reject, Edit or Add context.

Only display “anger - high confidence” if confidence qualification actually exists for that graph/task/slice. Otherwise show “possible anger in the wording; experimental model.” No raw intensity decimals/no “AI risk 87.” Exact examples/count windows are illustrative until real qualified events exist.

### Three separate review targets

- **Evidence association:** sender/date/event identity/duplicate/import relationship; user confirmation isn't cryptographic authentication.
- **Signal:** expressed emotion/behaviour/quote/target/context-support label.
- **Explanation/pattern:** generated wording, grounding, counts/comparability and missing-context caveats.

Review actions: confirm, reject, edit, add context, mark unknown. Persist versioned action/reason, not silently mutate original analysis. Editing OCR triggers new derivative/reanalysis; editing a model suggestion doesn't change original message. Corrected source maps invalidate stale anchors. Pattern/retrieval updates occur transactionally or with explicit pending-rebuild state; exports cannot include stale unreviewed summaries.

Feedback uses: case context/retrieval first, review-burden metrics second; separately consented evaluation/training program later. No automatic retraining on user data; poisoning can arise from abusive input or misleading context. Store rejection reasons and actor/user confirmation state, not all rejections as negative training labels.

## 16. Dataset / Fine-Tuning Strategy

| Dataset | Task/language/modality/labels | License evidence | Suitability/gap |
| --- | --- | --- | --- |
| GoEmotions | 58 k English Reddit comments,27 emotions+neutral, multi-label | Google/HF Apache 2 declaration | Presence, not intensity; short public comments, ambiguous labels, domain bias |
| DAIR Emotions | English six classes | Specific release terms must be checked | Benchmark control, no neutral/hostility/coercion truth |
| TweetEval /TimeLMs sentiment | English 3-way social text | Exact dataset/source terms and Cardiff CC-BY 4.0 model terms separate | Distillation/reference, not phone-private conversations |
| Jigsaw toxicity /Civil Comments | English comments, toxicity subtypes/annotator fractions | TFDS/publisher CC 0 release statements | Rare threat/class imbalance; no control/stalking taxonomy |
| HateXplain | English Twitter/Gab, hate/offensive/normal, target, rationales | HF metadata CC-BY 4.0/prose MIT conflict | Resolve release license; context missing; don't copy Vigil's MIT assumption blindly |
| ConvAbuse | English user-bot context, abuse degree/type/target, ambiguous annotations | Author repository CC-BY 4.0 | Nuanced roles/severity, bot-directed abuse !=interpersonal coercion; secondary labels only annotated if abusive |
| MELD /IEMOCAP | English Friends/acted dialogues, emotion/sentiment/multimodal | Code/data/audiovisual rights must be separately verified | Context/roles research, not safety/privacy-domain real evidence |
| SemEval/WASSA intensity | English/Arabic/Spanish intensity/polarity tasks | NRC terms research/no redistribution; commercial unresolved | No Indic; use actual EI-reg/ordinal data, not classification-only columns |
| DravidianCodeMix 2020 | Tamil-English~44 k, Malayalam-English~20 k, Kannada-English~7 k YouTube comments | Original Zenodo CC-BY 4.0 | Real code mix, sentiment/offensive; private-context/consent labels missing |
| HASOC/ICHCL | Hindi/English/Marathi, Hinglish contextual replies, later Bangla | Release-specific rights unresolved here | Positive sentiment can support hateful context; controversial topic selection bias |
| HateCheck/Hatemoji/OTH | Constructed diagnostic/emoji/obfuscation test corpora | Exact downloadable release licenses must be checked | Robustness, not real prevalence/per-user safety certification |

The workspace's `data/labeled_data.csv` was not treated as a verified emotion/intensity corpus. File existence/name alone does not establish provenance, license or conversational labels. [D1-D7]

### Minimum-data hackathon strategy

Use pinned emotion/toxicity checkpoints as experimental review suggestions. Do not train or advertise a universal safety classifier from invented examples. Prepared synthetic fixtures demonstrate invariants, not real-world accuracy.

The first pilot annotation target is **300 English message/context units, 200 Malayalam/Manglish units and 60 short sequences** with roles and capture gaps, reviewed by native speakers. These are proposed counts, not collected data or evidence of release adequacy. Other languages remain unsupported until qualified. Include hard negatives and enough rare-category positives to report intervals; keep synthetic and consented genuine sources separate.

Record source, license/consent, real/synthetic origin, language/script/code mix, claimed speaker/target, adjacent context, emotion labels, behaviour rubric, quote/report/negation status, source mappings, timestamps/gaps, and annotation disagreement. Intensity needs its own annotation rubric. Annotators may answer unknown. Use blind independent annotation and adjudication with safeguards for annotator well-being.

Split by person/conversation/source/template **before** producing windows, normalizations, translations or teacher labels. Keep train, development, calibration, routing-policy tuning and test partitions separate. Sparse data cannot support production calibration; collect more qualification data instead.

### Training experiments in priority order

1. Pretrained baselines, deterministic context and manual review; no new training.
2. Frozen approved embeddings with a logistic/MLP multi-label head as a low-data baseline.
3. Compact supervised encoder or shared student: masked BCE for behaviour/emotion, three-way CE for sentiment, optional context-required/role heads. Train only on labels actually annotated.
4. Licensed offline teacher distillation with human-checked soft labels/rationales. Teacher probabilities are not ground truth or calibration. No default upload of private vault evidence to a cloud teacher or Kaggle.
5. Domain/context fine-tuning and multilingual distillation; quantization-aware training only after a reliable float baseline and labeled parity dataset. Recalibrate the final export.
6. Optional LLM LoRA/SFT on licensed hard cases; merge, convert, requantize and test instruction following, refusal and anchors. An LLM is not needed to count events.
7. Local small-head training only after opt-in, rollback, energy and safety gates. No automatic Laya or federated gradient component.

Class weights, focal sampling and curricula can change score priors; calibrate on deployment-like prevalence. Do not relabel all toxicity as control or emotion negativity as abuse. Include benign identity/caste mentions, quoted reporting and consensual sexual/medical contexts. Evaluate speaker roles and per-language errors alongside macro F1.

## 17. Evaluation & Benchmarking, including local diagnostic evidence

### Local tests in this investigation

The extended Linux probe was **rerun offline on 2 October 2026**: 20 synthetic strings plus one NFKC view, 21 forwards per model, 42 total. Environment: ORT 1.22.1, tokenizers 0.22.0, numpy 2.3.3, x86_64 CPU; script specifies intra-op 4/inter-op 1, 256-token cap and disabled padding. Finite-output, shape, equation and preservation assertions passed. The original investigation recorded an Intel Core Ultra 9 185H; consult the current receipt for current host metadata. This is a diagnostic reproduction, not representative accuracy, calibration, Android, energy or battery evidence. [P4]

| Synthetic diagnostic | Raw observation | Design implication |
| --- | --- | --- |
| "You are an idiot." | Insult 0.938573; anger 0.722891, unpadded | Literal example works; general quality unestablished |
| "You are an 1d10t." | Insult 0.160123; neutral 0.990480 | Obfuscation sensitivity; calibrate after selecting views |
| Fullwidth insult / NFKC view | Insult 0.017269 -> 0.938573; original had one UNK | Helpful here; no universal rewrite claim |
| Quoted insult reporting | Insult 0.858545 | Content presence is not the reporter's conduct |
| "You are not an idiot." | Insult 0.738144 | Negation failure despite a high raw score |
| Angry delivery complaint | Anger 0.978090; toxic 0.003029 | Negative emotion is not abuse |
| Polite restrictive instruction | Caring 0.670918; approval 0.565070; toxic 0.002755 | Positive wording does not establish consent |
| "Pay me or I will share your private photos." | Threat 0.000657; neutral 0.985485 | Narrow baseline misses this category; preserve cue/user-review route |
| "Fine." | Approval 0.941967 | Raw confidence cannot supply missing context |
| Manglish/Hinglish fixtures | English neutral approximately 0.92-0.99 | Unsupported-language route; no linguistic quality claim |
| Malayalam location sentence | Behaviour: five UNKs among eight tokens; emotion: 130 tokens | Tokenization loss/fragmentation requires language qualification |

The current [offline probe receipt](verification/sentiment-emotion-probe.json) records 42 rows and model/config/tokenizer hashes. The durable script [sakshi-emotion-probe.py](probes/sakshi-emotion-probe.py) preserves exact fixture strings and original SHA-256 digests. Its model directories remain temporary and may disappear. Reproduction after explicit asset preparation:

```bash
uv run --no-project --offline --with onnxruntime==1.22.1 \
  --with tokenizers==0.22.0 --with numpy==2.3.3 \
  python research/probes/sakshi-emotion-probe.py
```

**Historical padding finding:** anger 0.570579 with 64-token padding versus 0.722891 unpadded. The padded comparison was recorded in the prior investigation, not rerun in the current unpadded probe; its root cause remains unisolated. Freeze padding/batching in the manifest and test supported shapes. Probability drift is not changing emotion intensity.

CORN masses were nonnegative and summed to one; normalized expected index stayed in [0, 1]. Time-aware EWMA checks covered bounded updates and zero elapsed time. NFKC produced a changed derivative while preserving the original. These are equation/preservation checks, not Kotlin integration or temporal safety validation.

**Historical timing only:** the earlier Laya-report Linux smoke test recorded batch-one forward-only p50 at 128 tokens of 6.044 ms toxicity / 7.796 ms emotion, and at 256 tokens 13.903 / 12.922 ms. Cache, power, affinity and background load were uncontrolled. Process peak RSS of about 201 / 162.5 MiB includes Python/runtime/test allocations. These timings were not rerun here, cannot predict phones, and cannot be added to estimate an Android model pack. [P4]

### Quality metrics

- Emotion: per-label precision/recall/F1, macro/micro F1, label ranking, neutral confusion, role/context errors and abstention; exclusive accuracy is insufficient.
- Behaviour: category PR-AUC, precision/recall/F1, false positives per 1, 000 benign units, false negatives, rare-positive counts and subgroup/language/negation errors.
- Calibration: labelwise Brier/NLL, reliability curves/ECE with bin method, support counts/intervals and accepted/routed/unsupported slices. Small ECE can hide rare-category errors.
- Intensity: MAE, Spearman/Pearson, ordinal within-one accuracy/QWK, annotation agreement and applicable interval calibration. Presence confidence is not intensity.
- Context: paired counterfactuals, omitted/changed context, unknown roles and truncation near critical tokens.
- Temporal: dedup/count correctness, pattern precision/recall, review burden/delay, coverage comparability, correction propagation and source-ID validity.
- Retrieval/explanation: recall@k, reviewed-boundary retention, exact quote/anchor validity, entailment, omissions, refusals and prompt injection.
- End-to-end: capture/extraction availability, model errors, LLM activation/cold loads, resources and user rejection reasons. Selected examples do not measure whole-system recall.

Record data/model/rubric/graph/tokenizer/padding/calibration/runtime hashes. Reject NaN/Infinity in scores and metrics rather than silently treating them as neutral.

### Required functional test suite

| Family | Examples / controls | Expected property |
| --- | --- | --- |
| Explicit harm/insult | Synthetic direct harm; separately consented labeled cases | Candidate signal, credibility unknown |
| Non-abusive anger | Delivery complaint, firm boundary request | Anger does not automatically imply harmful behaviour |
| Sarcasm/jokes | "Great. Just what I needed"; teasing with/without consent context | Context abstention; no forced label |
| Implicit threat | "Fine."; location wording under alternate contexts | Same wording can support different interpretations |
| Coercion/control | Conditional photo exposure/payment, approval of contacts | Independent review cue despite low toxicity |
| Code mix/slang | Supplied phrases, native-script/romanized variants | Explicit language qualification |
| Obfuscation | Leet, fullwidth, punctuation, zero-width marks, emoji | Original preserved; mapped views and score drift recorded |
| Quote/report/negation | Reported insult, refusal, "not an idiot" | Reporter and source speaker are not conflated |
| Identity/sexual context | Benign identities, counterspeech, medical/consensual texts | Unwantedness is not inferred from keywords |
| Temporal/integrity | Reposts, duplicate imports, real repeats, gaps, clock changes, corrections | Distinct-event counts; originals retained |
| Runtime/privacy | Missing model, locked key, cancellation/OOM, bad JSON/anchors, offline/backup | Error/unknown differs from neutral; no cloud fallback or evidence logs |

### Reproducible Android resource benchmark

Use physical 4-6 GB midrange ARM64, 6-8 GB mainstream and 8-12 GB flagship devices across vendors; Pixel 6+ power rails where available. Emulators can test APIs, not phone thermal/battery/LLM throughput. No app/model was installed on Android in this investigation.

Compare identical evidence/context and partitions across float/INT8 classifiers, bounded LLM-only structured classification, and independent classifiers with conditional LLM plus deterministic temporal logic. Add shared-student/retrieval ablations later.

Record APK/build, OS/SoC/RAM/ABI, runtime/graph/tokenizer/schema/calibration, token budgets, padding/batching, threads and backend. Separate process-cold, first compilation and warm-file-cache runs. Use release/profileable builds, synthetic fixtures and airplane mode after provisioning. Record ambient/battery/thermal/background conditions, randomize model order and distinguish cooldown from sustained work.

Measure:

- Installed weights, tokenizers, native SDK and first-run cache bytes.
- Load, tokenize, forward/decode, validation, decrypt/retrieval and full-pipeline p50/p95/p99.
- LLM prefill, time to first token, decode tokens/s, output length and total completion; encoder throughput is a different workload.
- PSS/native heap/RSS and peaks during load/prefill/decode, Java copies and available GPU accounting; accounting methods are not interchangeable.
- CPU time/utilization/simpleperf and available GPU counters; unknown counters remain explicit.
- PowerMetric on supported devices, or idle-subtracted long blocks/current integration/external measurement. Battery percentage change from one short message is invalid evidence.
- Thermal throttling, quotas, memory kill, cancellation, restart, locked keys and queue failures.
- LLM route/cold-load fraction, manual review time, rejection burden, missed important cases and joules per analyzed/review candidate.
- Network, logs, temporary files, indexes, WAL and backup; no content-rich telemetry.

Proposed protocol: 30 process-cold repetitions, 10 warmups plus 200 warm inferences per shape; 32/128/256 tokens and batch 1/2/4; 20-minute sustained replay plus bursts. These are design counts, not measurements. Bootstrap quality intervals by conversation.

**Expected latency remains unknown on target phones.** The published MobileBERT 62 ms Pixel 4 result and Arm Qwen 2B 6.782 s 128-input/128-output workload are source-specific anchors. Initial design targets are text pair/router p95 below 250 ms at 128 tokens on a selected midrange device and incremental AI working set below 300 MiB during text review. They may require one session at a time and must be measured. Foreground LLM work needs finite output and tested cancellation; no generic latency/RAM guarantee. [E2][M10][R9]

## 18. Privacy / Security

- A network-free variant omits INTERNET permission and imports/bundles assets. An explicit model-download variant separates network provisioning from inference and never uploads evidence.
- ML Kit documents local input/output processing alongside SDK metrics/model/compatibility traffic. Disclose this; a strict zero-network build needs an audited native OCR alternative. Do not copy content telemetry from example codelabs. [R12]
- Use SQLCipher-backed Room, Keystore-wrapped keys and authenticated AES-GCM file encryption with fresh nonces and appropriate backup exclusions. Room alone is not encrypted. Review exact SQLCipher build, license and native 16 KB page compatibility. [R6][R13]
- Hashes/provenance support integrity relative to received bytes, not authenticity, trusted time or court proof. Model outputs are derivatives.
- Encrypt embeddings, emotion profiles, relationship/contact metadata, reviews, routing reasons, indexes and temporary stores. Avoid public storage, raw evidence logs, prompt dumps and crash payloads.
- Plaintext exists during inference. Keystore does not defeat active compromise, root or screen observation. Define key-lock/UI/cache lifecycles without promising unbreakable privacy. [R13]
- Optional ordinary notification observations remain transient; candidate inbox retention is explicit and expiring. No always-on microphone, private database scraping, View Once bypass or silent report sharing.
- Bound imported text/archives/views/model assets. Pin signed hashes and validate labels/shapes. No import-time arbitrary code, trust_remote_code or runtime package downloads.
- Imported evidence is inert data. The LLM has no network, tools or external actions; test prompt injection and prohibit cross-case retrieval/history.
- User-directed deletion/revocation rebuilds derived patterns/embeddings without silently rewriting originals. Disclose key recovery and flash-erasure limitations.

## 19. Android Implementation Plan and model lifecycle

### Components and dependency boundaries

| Kotlin component | Responsibility | Model ownership |
| --- | --- | --- |
| `TextEvidenceExtractor` | Text/OCR/STT adapters, source maps and extraction quality | Uses specialist extraction adapters, not generative inference |
| `EvidenceViewBuilder` | Immutable-source derivatives and normalization provenance | Pure bounded transformations with tokenizer-policy identity |
| `ModelSupportRegistry` | Language/task/graph/padding/calibration qualification | Manifest-driven; unknown is explicit |
| `TextSignalEngine` | Runs requested emotion, behaviour and optional polarity tasks | Two adapters initially; one shared student session later |
| `ExpressedEmotionAnalyzer` | Actual label vector and support state, not mental diagnosis | GoEmotions adapter |
| `BehaviourSignalAnalyzer` | Narrow baseline/domain signals, quote/target caveats | Jigsaw baseline/domain student |
| `SentimentAnalyzer` | Optional three-way polarity | Absent default pack; enabled only with qualified export |
| `InferenceRouter` | Deterministic context/LLM/human/resource policy | No model required |
| `ContextBuilder` / `EvidenceRetriever` | Same-case adjacent/reviewed context, bounded token pack | Optional embedder, separate retrieval qualification |
| `LocalReasoner` | Bounded contextual interpretation, alternatives and anchors | One lazy native generative engine |
| `PatternEngine` | Distinct event counts, coverage, transitions and descriptive trajectories | Pure Kotlin/statistics |
| `ReviewFindingUseCase` | Signal/source/explanation review, invalidation and revisions | No retraining |
| `EvidenceRepository` / `BaselineStore` | Encrypted artifacts, derivatives, models, reviews, local references | SQLCipher/Keystore/file encryption |
| `ModelSessionManager` | Assets, leases, thread ownership, load/unload and cancellation | All native sessions centrally owned |
| `ReportBuilder` | Deterministic reviewed timeline/counts, explicit export | Optional reviewed prose, never mandatory LLM |

Compose UI -> ViewModel -> analysis/review/export use cases -> domain signal/router/pattern services -> repositories and native adapters. Do not place native models in every ViewModel or serialize raw evidence into WorkManager `Data`.

### Proposed Kotlin contract

Design sketch, not compiled application code. Domain types and serialization need implementation/tests in the Android project; dependencies are not being added to this research workspace.

```kotlin
data class EmotionScore(
    val label: String,
    val rawScore: Float,
    val calibratedProbability: Float?,
    val calibrationId: String?
)

data class EmotionObservation(
    val evidenceId: EvidenceId,
    val derivativeId: DerivativeId,
    val status: SignalStatus,
    val scores: List<EmotionScore>,
    val intensity: Float?,
    val modelRunId: ModelRunId
)

interface ExpressedEmotionAnalyzer {
    suspend fun analyze(input: TextEvidence): EmotionObservation
}

suspend fun analyzeEvidence(input: TextEvidence, request: ReviewRequest): PendingAnalysis {
    val views = viewBuilder.build(input)
    val support = supportRegistry.assess(views)
    val rules = ruleEngine.observe(input, views)
    val behaviour = if (support.behaviourAllowed) {
        sessions.withBehaviour { it.analyze(views.primary) }
    } else {
        BehaviourObservation.unsupported(input)
    }
    val emotion = if (request.includeExpressedEmotion && support.emotionAllowed) {
        sessions.withEmotion { it.analyze(views.primary) }
    } else {
        EmotionObservation.notEvaluated(input)
    }
    val fast = FastSignals(behaviour, emotion, rules, support)
    val route = router.route(input, fast, qualifiedPolicy, request)
    val context = if (route.needsContext) contextBuilder.build(input, route) else ContextPack.empty()
    val interpretation = if (route.action == RouteAction.LOCAL_REASONING) {
        sessions.withReasoner { it.interpret(input, context, request.outputBudget) }
    } else {
        Interpretation.notRun(route.reason)
    }
    return validator.combine(input, fast, context, interpretation, route).pendingReview()
}

suspend fun applyReview(command: ReviewCommand): ReviewedFinding = repository.transaction {
    val decision = saveReviewRevision(command)
    invalidateDerivedPatternsAndEmbeddings(decision)
    scheduleAuthorizedRebuild(decision.caseId)
    decision.finding
}
```

`rawScore` is an independent sigmoid score, not intensity. Construction boundary checks finite values, bounds, exact labels and manifest identity. Do not catch all exceptions and return an empty successful label list; propagate cancellation and distinguish native/runtime/schema errors from unsupported input.

### Concrete ORT Android API boundary

Use `com.microsoft.onnxruntime:onnxruntime-android:<tested-pinned-version>`. Linux probes use ORT 1.22.1; verify the matching Android artifact/native requirements before locking a release. The example shows a two-input emotion model, not the three-input behaviour feed. [R1]

```kotlin
val env = OrtEnvironment.getEnvironment()
val options = OrtSession.SessionOptions().apply {
    setIntraOpNumThreads(threadPolicy.intraOp)
    setInterOpNumThreads(1)
}
val session = options.use { env.createSession(modelPath, it) }
OnnxTensor.createTensor(env, arrayOf(inputIds)).use { ids ->
    OnnxTensor.createTensor(env, arrayOf(attentionMask)).use { mask ->
        session.run(mapOf("input_ids" to ids, "attention_mask" to mask)).use { result ->
            val output = result[0] as OnnxTensor
            val logits = FloatArray(28)
            output.floatBuffer.orElseThrow().get(logits)
            consumeFiniteEmotionLogits(logits)
        }
    }
}
```

The registry owns `session.close()` after the last lease. Validate output tensor type/shape at initialization; handle optional FloatBuffer according to the pinned Java API. Token IDs/masks are LongArray and graph labels come from the manifest, not hard-coded index guesses. Sigmoid/calibration application is outside the graph unless explicitly exported. `token_type_ids` must be supplied to the behaviour artifact.

### Local LLM native entry points

LiteRT-LM docs: `Engine(EngineConfig(modelPath, backend=Backend.CPU()))`, `initialize()`, `createConversation(config)`, `sendMessage` or `sendMessageAsync` Flow, and `close()`. Use background thread, bounded output/context, fresh per-analysis conversation, no tools and no remote actions. Pin a released compatible API rather than `latest.release`. CPU fallback must be validated for the exact model, not presumed after GPU allocation failure. [R3]

llama.cpp current Android binding: app-private GGUF file -> `AiChat` facade -> `InferenceEngine` -> prompt formatting/prefill/decode -> Kotlin Flow. NDK build uses portable `arm64-v8a`, `GGML_NATIVE=OFF`; do not globally require a newer CPU instruction set for every phone. Test actual supported Qwen3.5 graph/version before adopting the Arm export. [R4]

Schema/grammar-constrained decoding prevents some syntactic failures, not wrong semantics. If the pinned Kotlin API cannot enforce the needed schema, parse strictly, reject malformed fields, keep the human route and do not silently convert invalid output into safe. A Qwen thinking-channel UI switch isn't necessarily disabling reasoning tokens; verify chat-template behaviour and output limits for the artifact.

### Scheduling and Android constraints

- NLS callbacks: bounded snapshot and nonblocking handoff only, no tokenizer/model/disk-heavy work on callback/main thread. Supported evidence acquisition does not imply complete background delivery.
- Interactive review: ViewModel-scoped use case with injected bounded inference dispatcher, lifecycle/session owner, progress and cancel. Blocking native work does not become cancellable merely through `withTimeout`; use the runtime's actual cancellation/termination mechanism and test it.
- Durable authorized extraction/import/reindex: WorkManager with evidence IDs and constraints, idempotent revisions/checkpoints, bounded batches and stop-reason handling. `CoroutineWorker` supports suspending work but native calls still need appropriate dispatch/lifecycle.
- Android 16 long-running workers can exhaust job quotas even when WorkManager starts a foreground service. No real-time guarantee. Prefer chunked work and visible user-requested reasoning. A foreground service must have a legitimate type/use case and permission/launch compliance; don't label arbitrary LLM work as media processing or misuse service types to evade quotas. [R9-R10]
- User-imported audio analysis does not require live microphone recording permission. OCR/STT supports the selected artifact, not passive attachment recovery.
- Key locked/user revoked consent: work defers until authorized key access, not plaintext-cache workaround.

### Lifecycle state machine

```text
UNPROVISIONED -> verify/import signed model pack -> AVAILABLE_COLD
AVAILABLE_COLD -> authorized request -> LOADING -> READY_LEASED
READY_LEASED -> inference -> READY_IDLE
READY_IDLE -> short bounded reuse window OR explicit close -> AVAILABLE_COLD
Any request -> unsupported / resource deferred / cancelled / invalid output
UI hidden/background/key lock -> stop new heavy work, close after leases finish
Process death -> pending analysis revision remains incomplete, never fake success
```

Keep the frequent narrow behaviour session resident **during an authorized review session**, not universally at app startup or indefinitely in a notification service. Load emotion on requested review/trajectory work, reuse briefly, then release. One reasoner loaded only when needed; serialize OCR/STT/LLM to avoid additive peaks. Shared student later permits one text encoder pass.

Cache key includes evidence derivative revision, text-view policy, graph/tokenizer/padding/schema/calibration/runtime identity and context/review-policy version. Cache identical inference representations if useful; do not collapse separate real messages just because text is equal. Sensitive caches encrypted or short-lived in memory. KV/chat history cannot leak across cases.

Current Android memory guidance says running-low, moderate and complete trim levels are no longer delivered since API 34 (Android 14). Use supported UI_HIDDEN/BACKGROUND opportunities plus proactive lifecycle/device budgeting. Don't wait for a native-memory-pressure callback that Android does not reliably provide. Release reconstructible heavy sessions/caches on lifecycle transitions, not during a live lease. Handle OOM/process death honestly. [R11]

File mapping can reduce copies/page-in overhead, not eliminate working memory. Decrypting model weights may remove zero-copy mapping benefits, while evidence must never be mapped as a publicly readable plaintext file. Native libraries must meet target ABI/min SDK/16 KB-page requirements. GPU/NPU may improve throughput but add compile/cache/backend buffers; use measured per-device pack qualification, not NNAPI or NPU marketing assumptions.

## 20. Innovation Opportunities

The following are **differentiation hypotheses**, not unsupported first-in-world claims. UED, contextual models, local classifiers, ensembles, retrieval and temporal cyberbullying already exist.

| Existing approach | Current limitation/gap to verify | Sakshi addition | Technology / Android / MVP | Future research |
| --- | --- | --- | --- | --- |
| Emotion/tone classifiers | Negativity confused with perpetrator conduct | Role-aware emotion and behaviour shown separately | Two compact adapters, quote/role context; demonstrate angry benign complaint vs calm restrictive demand | Shared-head/context role training, user-safety validation |
| UED/home-base trajectories | Public/fictional language and complete-sequence assumptions | Capture-aware descriptive tracks with unsupported/missing segments and exact event IDs | Kotlin windows/statistics; demo a gap breaks a line and disables rate claim | Irregular sampling uncertainty and qualified intensity |
| Local LLM risk tools | Parse failures/unsupported models can appear safe | Observable unknown/error states and evidence-anchor validation | Deterministic router +optional reasoner; demonstrate bad output becomes review | Conditional calibration and grounding/refusal evaluation |
| Local RAG | Generic memory can cross boundaries or omit consent context | Same-case confirmed retrieval plus mandatory reviewed boundary evidence | Encrypted vectors/lexical/adjacent union; bounded context demo | Retrieval recall, index encryption and correction-aware memory |
| Adaptive classification/prototypes | User-normal can normalize abuse; sparse feedback biased | Explicit reference-set personalization without suppressing harm cues | Encrypted local prototypes and reset; show deviation as context, not danger | Poisoning-safe small-head training and subgroup calibration |
| Ensembles/disagreement | Incompatible tasks/uncalibrated averaging | Same-task or original/view instability as auditable router feature | One bounded normalization view; source-preserving drift demo | Ensemble value-versus-energy ablation, robust student distillation |
| Multilingual safety NLP | Language tags treated as universal task competence | Per-slice support/abstention and native correction with preserved text | Support badges; Manglish preserved even when English AI abstains | Indic/code-mix domain benchmark and compact multilingual student |
| User-confirmed timelines | Corrections fail to propagate into derived patterns | Versioned review-to-pattern invalidation graph | Transactional rebuild/source references; reject one event and counts change | Integrity/version migration and independent export verification |

**Strongest judge demonstration:** two independent tracks reveal why angry language is not automatically abusive and calm language can still warrant context; the timeline cites distinct reviewed events, visibly shows missing observations, and retracts derived pattern claims after user correction. This is technically defensible without numerical danger or invented emotion intensity.

## 21. Hackathon MVP and acceptance criteria

Build exactly:

1. Supported text/screenshot import, one short user-selected audio lane if extraction is already available. Preserve received originals and provenance in encrypted storage before analysis.
2. Two pinned compact ONNX adapters with actual checkpoint tokenizers, explicit padding policy, shape/label/finite checks. Behaviour runs independently; emotion shown as expressed-language suggestion. Sentiment not_evaluated by default; intensity null.
3. Language/extraction qualification states, independent bounded rule cues/user concern and manual Add context.
4. Review UI: source -> suggestion -> Confirm/Reject/Edit/Add context, separate role/quote attribution. No automatic accusation or safe verdict.
5. Pure Kotlin distinct-event timeline, reviewed counts/behaviour transitions, coverage gaps and optional descriptive emotion points. No required LLM, vector DB or gradient training.
6. Explicit reviewed redacted report export with source IDs and integrity metadata, not an admissibility guarantee.
7. Optional foreground Qwen LiteRT-LM demo only if already qualified for device/context/anchor/cancellation; otherwise demonstrate honest human-only context route.

Acceptance tests: airplane-mode functionality after assets ready; no original overwrite; source-map correctness; unsupported Indic preserves input/abstains; quoted harm not attributed to reporter; low emotion/toxicity cannot block user-marked evidence; no repeated notification/window double-counting; correction changes derived counts; invalid/model-unavailable/timeout not neutral; no raw evidence logs; no remote fallback.

Effort: medium for two native text adapters and review contracts if an Android shell exists; high for OCR/STT/vault integration and native tokenizer parity; high for optional generative branch; very high/data-limited for universal multilingual, calibrated intensity and on-device training. Relative scope, not calendar estimates. This repository currently supplies research rather than an established app, so integration prerequisites must be created/verified.

## 22. Post-Hackathon Research Roadmap

- Establish consented/licensed domain evaluation with native-speaker roles, context, partial observations and rare-category counts; qualify each task/language rather than advertise blanket support.
- Compare frozen embedding head, compact supervised student, multi-head student and optional contextual reasoner on matched inputs. Optimize language tokenization/vocabulary/weight memory only with task parity.
- Add retrieval only when lexical/adjacent context misses important evidence; benchmark energy, correction/deletion and index encryption.
- Collect intensity labels with clear rubric/comparative annotation and rights; compare regression/CORAL/CORN, per-language calibration and UI comprehension before exposing magnitude.
- Learn routing policy on independent data; measure accepted and abstained populations and conditional reasoning value, not merely LLM call reduction.
- Experiment with opt-in local prototypes/small-head adaptation, holdout/rollback/poisoning safeguards. Gradient training/federation remain separate privacy research.
- Validate safety guidance with specialists/users; no clinical/legal conclusions or automatic contact/report actions.
- Publish repeatable device results, source/runtime/tokenizer manifests, hard-negative slices and privacy/backup/log/thermal failure evidence. Laya remains deferred.

## 23. Source register, experiment boundaries and verification


The missing register was reconstructed on **2 October 2026** from the saved investigation history (`history_d0e509aea77644fd.md`), the sibling Laya report and primary publisher pages. Keys below belong to **this report**; similarly named keys in sibling reports need not have the same mapping. A source link does not establish a Sakshi result.

**Verification legend:** **Live** means the primary page was opened during this completion pass; **History** means the original inspection is recoverable in the saved history or sibling register but its specific claim was not independently refreshed here; **Local** means a repository artifact or current diagnostic run. Mutable main-branch pages need version pinning before implementation. Historical source-level details are not current compatibility guarantees.

### Model artifacts and candidate families

- **M1:** [minuva emotion card](https://huggingface.co/minuva/MiniLMv2-goemotions-v2-onnx), [recorded revision](https://huggingface.co/minuva/MiniLMv2-goemotions-v2-onnx/tree/4fea72b9ec71ba8d84b88e0efa2ace3dcc733bfc). **Live + Local.** Card declares English, quantized ONNX and 30M student; current offline forward confirms the local graph contract. No calibration or Android qualification.
- **M2:** [minuva toxicity card](https://huggingface.co/minuva/MiniLMv2-toxic-jigsaw-onnx), [recorded revision](https://huggingface.co/minuva/MiniLMv2-toxic-jigsaw-onnx/tree/c035f27b6a6d68770f8069a4829f1715a48b8d51). **Live + Local.** Jigsaw/teacher lineage and 23M student documented. The raw README URL failed this pass; the rendered primary card succeeded. Six outputs do not cover all harassment behaviours.
- **M3:** [SamLowe GoEmotions ONNX](https://huggingface.co/SamLowe/roberta-base-go_emotions-onnx), [DistilBERT SST-2](https://huggingface.co/distilbert/distilbert-base-uncased-finetuned-sst-2-english), [ALBERT](https://arxiv.org/abs/1909.11942), [DeBERTa-v3-small](https://huggingface.co/microsoft/deberta-v3-small), [multilingual BERT](https://huggingface.co/google-bert/bert-base-multilingual-cased). **History.** Comparison families, not downloaded/Android-tested Sakshi alternatives. TinyBERT/ELECTRA/MiniLM-L3 task-specific examples are covered by E3; a family name does not identify a deployment artifact.
- **M4:** [Cardiff three-way sentiment](https://huggingface.co/cardiffnlp/twitter-roberta-base-sentiment-latest). **Live.** English/tweet-domain reference; export, exact preprocessing, redistribution review and device parity remain required.
- **M5:** [English all-MiniLM-L6-v2](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2), [paraphrase multilingual MiniLM](https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2). **History.** Distinct embedding artifacts/token limits/language lists; neither is a trained Sakshi behaviour head.
- **M6:** [multilingual E5-small](https://huggingface.co/intfloat/multilingual-e5-small), [author E5 source](https://github.com/microsoft/unilm/tree/master/e5). **History.** Prefix/pooling/embedding contract; code-mix retrieval and encrypted phone indexing untested.
- **M7:** [MuRIL author card](https://huggingface.co/google/muril-base-cased). **History.** Native/transliterated pretraining is not qualified harassment performance.
- **M8:** [AI4Bharat IndicBERT](https://github.com/AI4Bharat/IndicBERT). **History.** Version-2 task/backbone comparison; exact fine-tuned graph and phone deployment not established.
- **M9:** [Qwen3-0.6B LiteRT community card](https://huggingface.co/litert-community/Qwen3-0.6B), [recorded pack revision](https://huggingface.co/litert-community/Qwen3-0.6B/tree/a3c5d805ae362dff7f580bc25f2dfb9a5a7eaa76), [Qwen3-1.7B official GGUF](https://huggingface.co/Qwen/Qwen3-1.7B-GGUF). **Live card; History exact export details.** No selected reasoner was executed here; community conversion is distinct from upstream checkpoint and published mixed-INT4 benchmarks.
- **M10:** [Arm Qwen3.5-2B vivo X300 export/benchmark](https://huggingface.co/Arm/qwen3-5-2b-q4-k-m-ggml-llama-cpp-vivo-x300). **Live.** The reported latency/memory/size are publisher measurements on that workload, not independent reproduction or Sakshi quality. Raw README access failed; rendered card exposed the benchmark table.
- **M11:** [Gemma3 model card](https://ai.google.dev/gemma/docs/core/model_card_3), [Google mobile benchmark article](https://developers.googleblog.com/en/gemma-3-on-mobile-and-web-with-google-ai-edge/), [LiteRT-LM overview](https://developers.google.com/edge/litert-lm/overview). **History.** The exact gated mobile asset was not downloaded or license-accepted here; numerical prefill results cannot be transferred to another bundle. Gemma4/Phi4/Llama3.2 are comparison families only, not selected validated packs.
- **M12:** [LiquidAI LFM2.5-1.2B-Instruct](https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct). **History.** Publisher formats/languages/licensing need exact revision review; no native Sakshi benchmark.

### Android runtime, lifecycle, storage and privacy

- **R1:** [ORT mobile guide](https://onnxruntime.ai/docs/tutorials/mobile/), [Java OrtSession](https://onnxruntime.ai/docs/api/java/ai/onnxruntime/OrtSession.html), [Java OnnxTensor](https://onnxruntime.ai/docs/api/java/ai/onnxruntime/OnnxTensor.html). **Live guide; History API sketch.** Android package and quantized CPU-first path are supported; the Kotlin sketch is not compiled or tokenizer-qualified.
- **R2:** [MediaPipe Tasks text classifier Android](https://ai.google.dev/edge/mediapipe/solutions/text/text_classifier/android), [LiteRT compiled-model guide](https://ai.google.dev/edge/litert/inference). **History.** Compatible task metadata/graph required; not arbitrary HF graph support.
- **R3:** [LiteRT-LM Android Kotlin](https://developers.google.com/edge/litert-lm/android). **Live.** Engine initialization, conversation lifetime, Flow streaming and native errors documented. Pin a compatible release and test actual cancellation, resource limits and CPU backend.
- **R4:** [llama.cpp Android guide](https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md), [Android binding](https://github.com/ggml-org/llama.cpp/tree/master/examples/llama.android). **Live guide; History binding details.** Portable NDK/JNI path, not Python inference on Android. Exact graph/backend ABI requires qualification.
- **R5:** [MediaPipe LLM Inference Android](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android), [ExecuTorch Android integration](https://docs.pytorch.org/executorch/stable/using-executorch-android.html). **Live MediaPipe; History ExecuTorch.** Maintenance-only status applies specifically to the LLM API; alternative export paths remain graph-specific.
- **R6:** [SQLCipher Android repository](https://github.com/sqlcipher/sqlcipher-android). **Live.** Room integration does not make every SQLite extension/build automatically compatible or establish a complete vault threat model.
- **R7:** [sqlite-vec author source](https://github.com/asg017/sqlite-vec), [room-vec integration](https://github.com/hub-bla/room-vec). **History.** Retrieval/index alternatives; no verified SQLCipher+Room+vector combination was executed.
- **R8:** [ORT Android training tutorial](https://onnxruntime.ai/docs/tutorials/on-device-training/android-app.html), [training example source](https://github.com/microsoft/onnxruntime-training-examples/tree/master/on_device_training/mobile/android/c-cpp), [OrtTrainingSession API](https://onnxruntime.ai/docs/api/java/ai/onnxruntime/OrtTrainingSession.html). **Live tutorial; History code/API.** Demonstrates a particular image head/JNI build, not automatic training support in any ordinary inference AAR.
- **R9:** [WorkManager long-running workers](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running), [Android power profiling](https://developer.android.com/topic/performance/power/setup), [Macrobenchmark metrics](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics). **Live worker quota guidance; benchmark methods are proposed.** No battery measurements here.
- **R10:** [Android 16 behaviour changes](https://developer.android.com/about/versions/16/behavior-changes-all), [data-transfer task choices](https://developer.android.com/develop/background-work/background-tasks/data-transfer-options), [worker definition](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work). **History.** Distinguish scheduling, legitimate foreground-service types and visible user requests.
- **R11:** [ComponentCallbacks2](https://developer.android.com/reference/android/content/ComponentCallbacks2), [low-memory-killer guidance](https://developer.android.com/topic/performance/issues/lmk). **Live API; History LMK.** Running/complete/moderate trim levels are not delivered since API 34; do not depend on them for native session safety.
- **R12:** [ML Kit terms and privacy disclosures](https://developers.google.com/ml-kit/terms). **History.** Local processing and SDK traffic are separate properties; the strict offline variant still needs network/log audit.
- **R13:** [Android Keystore](https://developer.android.com/privacy-and-security/keystore), [backup configuration](https://developer.android.com/identity/data/autobackup). **Design references, not a verified Sakshi implementation.** Key wrapping, nonce policy, backup exclusion and lock/recovery need implementation and device tests.

### Datasets, text robustness and calibration

- **D1:** [GoEmotions paper](https://aclanthology.org/2020.acl-main.372/), [Google dataset source](https://github.com/google-research/google-research/tree/master/goemotions). **Live paper; History release.** Presence labels, not validated intensity, psychological state or harassment truth.
- **D2:** [SemEval-2018 Affect in Tweets](https://aclanthology.org/S18-1001/). **History.** Task/configuration distinctions matter; a classification-only mirror is not an EI-reg corpus.
- **D3:** [NRC emotion/sentiment data terms](https://saifmohammad.com/WebPages/SentimentEmotionLabeledData.html), [NRC lexicon terms](https://saifmohammad.com/WebPages/lexicons.html). **Live data terms; History lexicons.** Rights differ by release; free research/no redistribution wording is not commercial clearance.
- **D4:** [DravidianCodeMix original Zenodo record](https://zenodo.org/records/4750858). **History.** Public code-mix comments and declared license, not private harassment-context ground truth.
- **D5:** [HASOC ICHCL](https://hasocfire.github.io/hasoc/2022/ichcl.html). **History.** Contextual Hinglish examples; release-specific rights/domain limits remain open.
- **D6:** [HateCheck](https://aclanthology.org/2021.acl-long.4/), [Hatemoji](https://aclanthology.org/2022.naacl-main.97/), [OTH study](https://aclanthology.org/2023.findings-emnlp.192/). **Live OTH; History other papers.** Diagnostic corpora are not prevalence or phone safety evaluations; exact downloadable rights must be checked.
- **D7:** [ConvAbuse source](https://github.com/amandacurry/convabuse), [ConvAbuse paper](https://aclanthology.org/2021.emnlp-main.587/), [Jigsaw toxicity TFDS](https://www.tensorflow.org/datasets/catalog/wikipedia_toxicity_subtypes), [Civil Comments TFDS](https://www.tensorflow.org/datasets/catalog/civil_comments), [HateXplain author source](https://github.com/hate-alert/HateXplain), [DAIR emotion card](https://huggingface.co/datasets/dair-ai/emotion). **History.** These are separate releases/taxonomies; label prevalence, context and licensing cannot be merged by filename. HateXplain rights conflict recorded in the investigation remains unresolved.
- **U1:** [Unicode UAX #15 normalization](https://www.unicode.org/reports/tr15/). **Live.** Canonical/compatibility transformations do not guarantee preservation of semantic distinctions.
- **U2:** [Unicode UTS #39 security mechanisms](https://www.unicode.org/reports/tr39/). **Live.** Confusable skeletons are not universal multilingual semantic repair.
- **U3:** [OTH author paper](https://aclanthology.org/2023.findings-emnlp.192/). **Live.** Its normalization improvement is under a specific corpus/protocol; no transfer claim for Indic or Sakshi.
- **C1:** [Guo et al., calibration](https://proceedings.mlr.press/v70/guo17a.html). **History.** A calibration method reference, not evidence that current raw outputs are calibrated.
- **C2:** [Calibrated selective classification](https://arxiv.org/abs/2208.12084). **History.** Conditional selection changes the population; no qualified Sakshi routing policy was trained.
- **C3:** [Hierarchical selective classification](https://arxiv.org/abs/2405.11533). **History.** Risk/coverage framework, not a safety guarantee. Conformal exchangeability caveats in section 9 are design cautions; no conformal predictor was implemented/tested.
- **T1:** [WASSA emotion-intensity task](https://aclanthology.org/S17-1007/), [SemEval task](https://aclanthology.org/S18-1001/). **History.** Comparative annotation/task precedent, not collected Sakshi intensity labels.
- **T2:** [NRC dataset terms](https://saifmohammad.com/WebPages/SentimentEmotionLabeledData.html). **Live.** Separate task/data rights from model/code license.
- **T3:** [CORAL/CORN author code/docs](https://github.com/Raschka-research-group/coral-pytorch), [CORN paper](https://arxiv.org/abs/2111.08851). **History + Local equations.** Ordinal-image precedent; text-intensity adaptation remains proposed.
- **T4:** [UED paper](https://arxiv.org/abs/2103.01345), [EmotionDynamics author implementation](https://github.com/Priya22/EmotionDynamics). **History.** Utterance dynamics do not authenticate emotional state or predict violence.
- **T5:** [Temporal cyberbullying paper record](https://par.nsf.gov/biblio/10301308-modeling-temporal-patterns-cyberbullying-detection-hierarchical-attention-networks), [Instagram temporal-properties paper](https://ysilva.cs.luc.edu/BullyBlocker/documents/tempcb-cybersafety20.pdf). **History.** Existing temporal prior art; different capture/domains from Sakshi.

### Existing implementation evidence and local project sources

- **E1:** [Google Android text-classification sample](https://github.com/tensorflow/examples/tree/master/lite/examples/text_classification/android), [MediaPipe task API](https://ai.google.dev/edge/mediapipe/solutions/text/text_classifier/android). **History.** Native task precedent; sample executor/lifecycle details need reinspection before reuse.
- **E2:** [MobileBERT paper](https://aclanthology.org/2020.acl-main.195/). **Live.** Publisher Pixel 4 latency, not reproduced Sakshi latency.
- **E3:** [Panopoulos et al., mobile transformer benchmark](https://arxiv.org/html/2306.11426v1). **Live.** Physical Galaxy A71/S20 FE, TF 2.11, emotion classification and quantization comparisons. Its six-class/50-token accuracy is not GoEmotions multi-label F1.
- **E4:** [Vigil developer repository](https://github.com/kevintheliao/Vigil). **Live repository; History detailed source inspection.** README/implementation claims are not an independent security or quality audit; exact source commit/license needs review before reuse.
- **E5:** [Agent Hita developer repository](https://github.com/Agent-Hita/AgentHitaAndroid). **Live repository; History detailed source inspection.** Null/error/logging/session observations refer to inspected source, not a tested current release. Source-available license restrictions and telemetry must be reviewed.
- **E6:** [BullyAlert author paper](https://arxiv.org/abs/1811.00405). **History.** Device/adaptive-alert prior art; offline acquisition/computational metrics unestablished here.
- **E7:** [DialogueRNN paper](https://aaai.org/papers/06818-dialoguernn-an-attentive-rnn-for-emotion-detection-in-conversations/), [MELD paper](https://aclanthology.org/P19-1050/). **History.** Speaker/context research, not an Android safety pack; audiovisual/corpus rights are separate.
- **E8:** [EmotionDynamics implementation](https://github.com/Priya22/EmotionDynamics), [UED paper](https://arxiv.org/abs/2103.01345). **History.** MIT code does not grant NRC corpus/lexicon rights; ordinal word windows differ from capture-aware physical time.
- **E9:** [TalkingParents Sentiment Scanner help](https://support.talkingparents.com/hc/en-us/articles/38689078511383-How-to-use-Sentiment-Scanner). **History.** Own-message tone feature; model/runtime/offline/resource details remain unknown.
- **E10:** **M1/M2** and [minuva reference server](https://github.com/minuva/fast-nlp-text-emotion). **Local graphs; History server source.** Desktop reference is not an Android integration.
- **E11:** [Google AI Edge Gallery](https://github.com/google-ai-edge/gallery), [llama.android](https://github.com/ggml-org/llama.cpp/tree/master/examples/llama.android). **History.** Native generative deployment examples; no matched harassment quality evidence.
- **P1:** [local product proposal](../Harassment_Pattern_Guard.pptx.pdf). **Local artifact, historical text extraction.** Product intent, not an implementation/acceptance record; diagrams still need visual inspection before treating them as requirements.
- **P2:** [project operating contract](../AGENTS.md). **Local.** Laya deferral, supported acquisition, review, evidence/privacy boundaries and honest device verification.
- **P3:** [Android acquisition specification](android-evidence-acquisition-specification.md), [View Once feasibility](whatsapp-view-once-feasibility.md). **Local design.** Supported/user-mediated access only; uncertain vendor/version behaviour still requires real-device prototypes.
- **P4:** [durable extended probe](probes/sakshi-emotion-probe.py), [earlier AI probe](probes/sakshi-ai-probe.py), [Laya report diagnostic boundaries](laya-source-analysis-and-sakshi-local-ai-architecture.md). **Local current rerun of extended probe; historical smoke timings/padding comparison.** See section 17 and [current diagnostic receipt](verification/sentiment-emotion-probe.json); synthetic data is not real evidence.

### Verification boundary and implementation implications

All 23 numbered report sections are populated. Every citation key used in the body resolves above, including C1-C3 and T1-T5 omitted from the original handoff inventory. Recommendation contracts cover emotion, behaviour, optional context/reasoning, temporal logic, personalization and the supporting runtime/data/privacy choices. JSON examples are illustrative and require canonical event adapters; Kotlin examples are design sketches, not compiled application APIs.

The current offline probe reproduces output shapes, finite values, fixture scores and mathematical preservation assertions. Model file hashes/graph sizes belong to the local diagnostic pack; independent phone qualification is still required. Earlier laptop p50/RSS and padding observations remain historical. No sentiment export, multilingual task training, LLM inference, real-device acquisition prototype, Android tokenizer parity, quality/calibration study, cancellation/thermal/power benchmark or clinical/legal acceptance was established.

Before implementation: freeze model/tokenizer/padding/calibration/runtime manifests; preserve canonical code-point anchors with tested Kotlin boundary conversion; build the supported import/review flow; keep English signals experimental and unsupported language explicit; qualify native devices and representative consented/native-speaker slices. Reinspect mutable source APIs and resolve model/dataset/code redistribution rights. Keep Laya, gradient training and default cloud inference deferred. Review-surface completion is documentation evidence, not product acceptance.
