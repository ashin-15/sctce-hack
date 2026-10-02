# Laya source analysis and Sakshi's Android local-AI architecture

**Research date:** 2 October 2026\
**Status:** Source investigation, limited Linux experiments, and proposed Android design. Not an implemented or validated safety system.\
**Question:** Should Sakshi use Laya, a different local model, or a cascade, and exactly how should it run on Android?

## 1. Engineering recommendation

**Build a compact, evidence-linked, review-first cascade. Do not use the published Laya checkpoints as Sakshi's primary harassment detector or universal confidence layer.**

Minimum hackathon stack:

1. Supported user-selected text/screenshots/short audio. Optional notifications provide observations, not full messages or chat history.
2. Preserve originals/provenance; OCR/STT produce separate versioned derivatives.
3. Compact English multi-label toxicity classifier as a **narrow signal baseline**, not comprehensive harassment detection. Emotion runs independently, never as a safety gate.
4. Context-dependent categories and unsupported languages remain unknown. Manual marking/review always works.
5. Deterministic temporal engine over distinct observed/reviewed events. Low sentiment/toxicity must not delete or reject evidence.
6. Source-linked explanations, uncertainty and corrections; retention follows source-specific consent and review.
7. One optional local LLM for selected foreground interpretation after the core works on a real phone. Capture/save/review/export must not depend on it.

**Exact first classifier candidates:** `minuva/MiniLMv2-toxic-jigsaw-onnx` and `minuva/MiniLMv2-goemotions-v2-onnx`, pinned `model_optimized_quantized.onnx` artifacts, ONNX Runtime Android CPU. They loaded in the limited Linux experiments in section 10. They have not passed Sakshi quality, calibration or Android gates. This is an implementable baseline selection, not a measured accuracy winner. [M1-M2][R1]

**Production direction:** fine-tune/distill a compact encoder with separate behaviour, expressed-emotion and optional sentiment heads on licensed conversation-aware multilingual data. Add case-scoped retrieval, optional bounded LLM interpretation and deterministic temporal patterns. Evaluate against pretrained baselines rather than assuming the student wins. Laya comparison/porting is deferred from the current program.

**Current user decision:** defer Laya from Sakshi's implementation plan for now. Do not spend the hackathon budget on a Laya port or fine-tune. Retain this source audit as background research, not an active integration dependency. The finding is no validated drop-in Android deployment, not technical impossibility. Any future reconsideration would require conditional quality gains and tokenizer, quantization, calibration, RAM and Android parity gates; raw probabilities do not make it a universal confidence layer.

### Evidence driving the recommendation

| Finding | Evidence status | Consequence |
|---|---|---|
| Laya uses 421M English / 322M multilingual parameters | Source and Hub metadata | Not a tiny replacement for a 23M classifier |
| Held-out ToxicChat run: English accuracy 0.530, macro-F1 about 0.400; multilingual 0.525 / 0.394 | Committed publisher result, not reproduced here | A moderation preset is not demonstrated harassment reliability |
| Current INT8 per-tensor English export agreed with eager on 64/96 decisions; old per-channel 31/96; FP32 96/96 | Contributor #790, maintainer-confirmed | New default is less broken, not accuracy-safe |
| Confidence definitions vary; multilingual ships temperatures [1,1,1] | Source/config | Application-specific calibration required |
| Multiple decisions are separate question-state batch rows | Source | Encoder work grows with decision count |
| Compact ONNX candidates loaded with finite outputs locally | Linux experiment | Concrete tensor contracts, not Android proof |
| Synthetic outside-home message under-detected; English WordPiece lost Malayalam information | Limited experiment | Negative scores cannot authorize evidence rejection |
| No Android workload executed in this investigation | Scope boundary | No phone latency/RAM/energy or safety-accuracy claim |

## 2. Evidence scope and source inventory

Labels:

- **Source fact:** directly inspected implementation/configuration/API/license declaration.
- **Published measurement:** author, contributor, paper or runtime publisher result, retaining hardware/workload/version/metric.
- **Local experiment:** executed here with limitations.
- **Recommendation:** proposed Sakshi design, not measured performance.
- **Open question:** requires native integration, export/device tests, labeled data or specialist review.

Frozen Laya source: `NandhaKishorM/laya`, **`4aa6761be8173de4ce6d92c31b3e40b6eaf59a7c`**, package **0.3.23**, last source commit 2026-10-01. English Hub **`55cf4c4ebb4ebe31b2550e8bdf3bd21b99753851`**; standalone multilingual Hub **`e4e9ddf21a7b1903b7acffd8814ad4307bf63a67`**. Source clone: `/tmp/sakshi-laya-source`. Metadata and tokenizers downloaded; **no Laya weights or forward pass**. Updating the runtime does not retrain checkpoints. [L0-L4]

Inspected beyond README:

| Surface | Files / ranges at frozen commit | Mechanism |
|---|---|---|
| APIs/loading | `__init__.py`, `agent.py:537-734`, `router.py`, `revisions.py` | Lazy imports, strict loading, device/precision, checkpoint routing |
| Architecture | `common.py:472-630` | Encoder, type embedding, transformer head, marker scorer, act head, reward |
| Input | `common.py:75-247`, `agent.py:1012-1071` | Serialization, option rendering, masks, token budget |
| Output/gating | `agent.py:1180-1246`, `confidence.py`, `structured.py` | Probabilities, confidence scales, projection and abstention |
| Long/batch inference | `agent.py:1250-1515` | Separate question rows, batching/window aggregation |
| Calibration | `calibrate.py`, `common.py:664-727`, example34 | Temperatures, sample floors, buckets, runtime clamp |
| Fine-tuning | Full Kaggle notebook cells, `docs/finetune.md` | DDP, soft targets, RLCD + CE, calibration/export |
| Evaluation | `evals.py`, `evals_cli.py`, `docs/evals.md`, `research/scripts/bench_apps.py` | Dataset format, metrics, samples, baseline gates |
| Export | `scripts/export_onnx.py`, `onnx_agent.py`, TypeScript docs | Opset18, tensors, external preprocessing/decoding |
| Benchmarks | `BENCHMARKS.md`, Applications JSON, results inventory | Task failures, latency, refreshed language numbers |
| Examples | Presets, mixed-primitives/conversation/local/long/batch guides | Actual request shapes, not performance guarantees |
| Dependencies/licenses | `pyproject.toml`, repository license, model cards | No core requirements.txt; dependencies in pyproject |
| Issues | #790, #741, #394, #830; issue inventory | Quantization, training ablation, confidence and NaN gates |

This workspace contains research, not an established Sakshi Android app. This report complements the acquisition, multimodal and notification documents without editing concurrent work. No personal evidence/notifications were accessed, and no app/model installed or run on Android.

## 3. Laya architecture and checkpoints

Laya is an **instruction-conditioned discriminative decision model**, not an autoregressive chatbot. State plus caller-defined finite options produces distributions, not generated prose.

```text
State + one typed question/options
  -> token sequence + option-marker positions
  -> bidirectional ModernBERT-family encoder
  -> question-type embedding added to token states
  -> two full transformer decision-head layers
  -> gather option-marker representations
  -> shared LayerNorm/Linear/GELU/Linear scorer
  -> per-option logits -> temperature -> softmax
  -> choice / expected ordinal index / P(true)

First-token representation + distribution features
  -> learned act/escalate head
```

`DecisionModel.forward()` adds `type_emb(qtype)` after encoding and processes the full sequence through the head. Padded option logits are masked with -1e4. The act head reads first-token state plus top probability, top-two margin, normalized entropy and option count. Runtime exposes `action.act_probability`; it is not a validated danger/correctness score or Sakshi cost policy. [L1]

The encoder uses RoPE/local-global attention. The added head uses full attention: an 8k encoder capability does not remove whole-model long-context cost. Unfused attention memory can grow quadratically; actual peak depends on kernels/backend.

| Checkpoint | Architecture/count | Default total budget | Interpretation |
|---|---|---:|---|
| `convaiinnovations/laya` | ModernBERT-large, 28 layers, hidden1024; Hub total421,293,830, about421M |512; head192| English general typed decisions, not harassment specialist |
| `convaiinnovations/laya-multilingual` | mmBERT-base, 22 layers, hidden768, vocab256k;321,908,998, about322M |1024; head256; encoder8192| Wider language coverage, uneven task accuracy, shipping uncalibrated |
| `convaiinnovations/laya-typed-decisions` | English ModernBERT-large +head, about421M |1024| Four synthetic business/security workflows, not interpersonal harassment |

The root Hub bundles sibling subfolders; Router defaults use those. Standalone multilingual SHA is not the root-bundle SHA. Load only needed assets. `Router(preload=True)` loads all three; default `max_loaded=2` may retain English+multilingual, and explicit preload raises the cap. These are poor phone defaults. [L2-L4]

Loading uses allowlisted Hub downloads, safetensors and strict key/shape checks. Tokenizer config compatibility repair exists. `AutoModel` uses SDPA; compilation disabled unless requested. Devices: CUDA, MPS, XPU or CPU. CPU support is real, not necessarily fast enough for phones.

**Memory subtlety:** half-precision checkpoint files are copied by `load_state_dict` into default-FP32 constructed model parameters. CPU defaults FP32; accelerator autocast is not universal FP16 weight residency. Nominal FP32 parameter storage is about **1.685GB English /1.288GB multilingual**, before tokenizer, temporary weights, activations and runtime. Arithmetic lower bounds, not measured RAM. English safetensors file about842.6MB; multilingual643.8MB. TileLang fast path separately uses reduced-precision CUDA-oriented weights/kernels; not Android acceleration evidence. [L2][L10]

## 4. Tokenization, context and efficient multiple decisions

Local tokenization-only inspection with `tokenizers==0.22.0`:

| Checkpoint | Actual tokenizer.json | Special-token IDs | Uncontrolled laptop parse |
|---|---|---|---:|
| English |3,583,228 bytes; BPE, NFC, ByteLevel| CLS50281 SEP50282 MASK50284 PAD50283 UNK50280|about99ms|
| Multilingual |34,363,188 bytes; BPE, space-to-metaspace normalization, Metaspace; Gemma2 lineage| BOS/CLS2 EOS/SEP1 MASK4 PAD0 UNK3|about1,136ms|

These are neither WordPiece nor interchangeable tokenizers. Native/Kotlin port must match normalization, leading spaces, special tokens, Unicode JSON serialization, option order, truncation and marker positions. Encoder config aliases alone are insufficient. A Rust tokenizer JNI bridge is plausible engineering, not an established Laya Android SDK.

One synthetic Malayalam sentence produced76 tokens with English Laya and23 with multilingual, both zero UNK. **Zero unknown tokens does not prove understanding:** byte tokenization can encode unsupported languages. The compact English WordPiece baseline produced8 tokens including5 UNK for that sentence.

Source formatting:

```text
[CLS] <type> question: <instructions> [SEP]
[MASK] option0 [MASK] option1 ... [SEP]
<serialized state> [SEP]
```

- Each option capped48 tokens, then shortened further when head budget is tight; instructions also truncate.
- Literal mask strings are sanitized in text/options to avoid marker interference.
- State room is `max_len - actual_head_length - 1`, not max_len of message text.
- Dict/list state is Unicode-preserving JSON; conversation list is not persistent recurrent memory.
- Plain long text normally keeps its front; conversation-list handling can preserve recent text. Inspect usage rather than guessing character budgets.
- Propagate `state_tokens_dropped`, `truncated_questions`, and collapsed-option diagnostics into Sakshi.

Multilingual supports/documented explicit `max_len=8192`; published long-context test uses only20 cases per length and deteriorates beyond roughly4k. English backbone8192 does not prove the decision checkpoint trained/validated there. [L1-L4][L7]

`predict_long`: overlapping windows; noul takes max P(true); choice/score choose most-confident window. Source warns returned probability is **not calibrated for the document**; maxima increase false positives with window count. Preserve deciding-window anchors and separate aggregation policy.

**Multiple decisions:** batching reduces overhead/raises GPU throughput, but each question is a complete question-plus-state row. State tokenization is reused; encoder representations are not shared across questions. Fixed multi-head classifiers can instead infer multiple labels from one encoder pass.

Published T4 p50:

| Questions |English|Multilingual|
|---:|---:|---:|
|1|39.5ms|32.8ms|
|5|84.5ms|40.1ms|
|10|158.6ms|72.3ms|
|50|771.3ms|337.4ms|

Published four-core EPYC CPU: English580ms for1 /6,244ms for10; multilingual193ms /1,842ms. Another tuned laptop report gives329ms p50 for one question. Different inputs/environments, not phone estimates. CPU question batching saves little in these results. Benchmark Q=1/2/4 with actual tokens on Android. [L7]

## 5. Exact APIs and proposed Sakshi decisions

| API | Use | Caveat |
|---|---|---|
| `laya.load(local_path, device="cpu", compile=False)` /Agent| One offline checkpoint| All tokenizer/config assets required to avoid fallback lookup |
| `agent.predict(state, questions, max_len=..., head_max_len=..., lang=..., min_confidence=...)` /system_one| Mixed primitives| Gate flag is not human review |
| `predict_batch(states, questions, batch_size=..., sort_by_length=...)`| Bounded import throughput| Rows=states×questions; near-ties can shift |
| `predict_long(..., window=..., stride=..., batch_size=...)`| Windowed candidates| No document-level calibrated probability |
| `Router.predict(..., model="multilingual", lang_guess=...)`| Development/checkpoint routing| Script/lexicon routing not reliable Manglish detection |
| `laya.onnx_agent.ONNXAgent(local_checkpoint, onnx_path=...)`| Desktop reference decode| Still imports shared Python/Torch helpers; not Android API |
| `decide(..., schema=..., return_details=True)`| Flat finite-schema projection| No nested objects, arrays, free strings or recursive refs |
| Calibration helpers/save/load | Development fit| Pin schema/backend/quantization/language |
| `moderation_questions()`| Toxic/harassment/threat/spam/severity prompts| Preset existence not measured competence |
| `laya-evals validate/run/compare`| Evaluation plumbing| Finite-number wrapper needed, section7 |

No official Kotlin LayaEngine API established. Android wrapper below is a proposed Sakshi implementation.

### Actual Python request shape, research-only

```python
questions = {
    "dominant_category": {
        "type": "choice",
        "instructions": "Which category best describes the available message text?",
        "criteria": {
            "threat": "language expressing possible harm against a person",
            "abuse": "person-directed insults or degrading language",
            "control": "pressure restricting another person's autonomy",
            "sexual_harassment": "unwanted person-directed sexual conduct in supplied context",
            "benign": "none of these signals is supported by available text",
            "insufficient_context": "context or consent information is missing"
        }
    },
    "threat_language": {
        "type": "noul",
        "instructions": "Does the message express an intention or conditional intention to harm a person, rather than quote, report, or reject someone else's threat?",
        "criteria": {
            "true": "available text supports a speaker-originated harm signal",
            "false": "no such signal is supported by available text"
        }
    },
    "intimidation_evidence": {
        "type": "score",
        "instructions": "How much textual evidence of intimidation is present in supplied message/context? Do not infer absent facts.",
        "criteria": ["none observed", "weak ambiguous suggestion", "some person-directed pressure",
                     "explicit intimidation language", "explicit intimidation with stated consequence"]
    },
    "context_need": {
        "type": "choice",
        "instructions": "Can interpretation be supported using only supplied evidence?",
        "criteria": {"enough_text": "signal is explicit", "needs_prior_turns": "context could change interpretation",
                     "unknown": "insufficient evidence"}
    }
}
agent = laya.load("/absolute/path/to/pinned/local/checkpoint", device="cpu", compile=False)
result = agent.predict(state, questions)
intimidation_0_1 = result["answers"]["intimidation_evidence"]["score"] / 4.0
```

The requested five-way choice is representable, but overlapping categories make it unsuitable as sole production taxonomy. Add insufficient-context and use separate binary/multi-label behaviours: harm language, insult, restrictive demand, conditional exposure demand, unwanted-contact indication, unwanted-sexual-language indication, personal-data-exposure indication. Rubrics/context/data remain mandatory.

**“Does this message contain a credible threat?”** is syntactically valid noul, but inappropriate default output: credibility depends on identity/capability/proximity/prior conduct and absent external facts. Split textual indicators from unknown external factors. Say “possible threat language; credibility not established,” not a binary legal/safety finding.

**“Score intimidation0-1”:** raw score is expected ordinal index `sum(i*p_i)` over0..K-1. Divide byK-1 for a display scale, not P(intimidation), authenticity or danger. `decide()` projects highest-probability level rather than raw expectation. Numeric schema0..1 gives two discrete levels, not arbitrary regression. Preserve rubric/distribution; defer intensity unless specifically validated. [L1][L5]

## 6. RLCD, confidence and calibration

`proper_reward` implements log score +weighted spherical score, subtracting ranked probability score for ordinal questions:

```text
log=sum(target_i*log(q_i)); spherical=dot(target,q)/||q||2
RPS=sum((CDF(q)-CDF(target))²)/(K-1)
reward=log+w_sph*spherical-w_rps*RPS (ordinal only)
```

It masks/clamps and floors log score at-9.21. Ideal strict propriety is an optimization motivation, not a theorem of shipped calibration: clipping, finite capacity, teacher error, training and domain shift matter. Soft CE is already expected log score. [L1][S1]

Notebook: four zero-mean Gaussian noisy-logit projections, detached sampled logits, group-mean advantage, score-function gradient; full-weight soft CE against teacher distributions. `0.0*act.sum()` provides no meaningful act-head adaptation. #741 reports an A100 CE-only/RLCD/direct-proper ablation with79-81% accuracy and no measurable RLCD advantage; contributor result, not reproduced/universal. Require CE baseline. [L8-L9]

| Field | Meaning | Use |
|---|---|---|
|answer_confidence|max(p), all primitives| Gate candidate only after validation |
|confidence, choice/score|1-H(p)/log(K)| Uncalibrated concentration |
|confidence, noul|max(Ptrue,1-Ptrue)| Confidence in positive OR negative |
|noul|P(true)| Binary score, not proven/calibrated automatically |
|act_probability|Learned auxiliary action probability| Not threat/correctness probability |

P(threat)=0.05 can have answer_confidence0.95: a confidence-only router can accept confident false negatives.

English ships fitted temperatures but choice:11+=0.1005828, an extreme sharpener. Runtime clamps **[0.5,5]** with warning; mitigation is not calibration. Multilingual ships [1,1,1], no buckets. Old model-card ECE may predate clamp/RoPE corrections/refreshed evaluation. Published refit ECE0.466->0.081 English and0.314->0.106 multilingual are their experiments, not Sakshi projections. Calibration defaults minimum2,000 records per bucket, minimum10 type fallback; fallback is not validation.

**Sakshi:** split train/development/calibration/test by conversation/person/source. Calibrate after final export/quantization. Per-label sigmoid calibration/thresholds for multi-label models must reflect deployment prevalence; resampling/class-weighted training can distort scores. Report PR-AUC, per-label precision/recall, NLL/Brier, reliability/classwise ECE, risk-coverage curves, per-language slices, conversation-bootstrap intervals. Include confidently wrong negatives; no universal0.85 gate. [S1-S3]

## 7. Training/evaluation audit and local checks

Evaluation JSONL: `state`, `questions`, `expected`; optional tags/language/model. Expected: choice label, numeric ordinal value, boolean. Blank/comment lines allowed; dataset/question fingerprints and revisions recorded. Training instead expects JSON-string state/questions/gold with per-option teacher probability dictionaries. Preprocessed item: ids/markers/qtype/target/label. Choice order follows criteria; noul false/true; ordinal keys string indices. Do not substitute expected for gold without adapter. [L6][L8]

Notebook:1,200 synthetic cases/6,000 question items;400 test cases/2,000 decisions. Two T4 DDP,4epochs,microbatch8/GPU,accumulation4,effective64,encoderLR2.5e-5/head1e-4,AdamW/cosine,fp16,gradient checkpointing/clipping1; full fine-tune, not LoRA.

Source-level pitfalls:

- Calibration split happens **after case-to-question expansion**: questions from one state can cross train/calibration. Split cases/conversations first for Sakshi.
- Preprocessor uses downloaded base512/192 budget; training later writes1024/256. Existing encoded items do not grow. Regenerate with final budget.
- Config max_tokens_per_batch4096 does not itself bound the explicit fixed-microbatch collator. Enforce actual padded-token memory.
- Rolling checkpoint lacks full config/optimizer/scheduler/scaler/RNG for faithful resume; saved each epoch is not complete resumability.
- Current fitting uses runtime clamp constants/removes inherited buckets; old prose says[0.1,10]. Follow source.
- Full original checkpoint corpus/initial-training provenance is not reconstructed by this workflow notebook.

General harness: choice/noul accuracy, score MAE/tolerance, confidence/ECE, latency. `evals._correct()` covers choice/noul, not expected ordinal-score correctness, so overall ECE is not uncertainty validation for every score. Add multi-label/ordinal/selected-cascade metrics externally.

**Local checks on frozen source:**

1. Plain-script `tests/test_evals_api.py`: **40 checks passed**.
2. `python -m laya.evals_cli validate research/evals/fixture.jsonl`: **12 examples /4 IDs validated**.
3. Reproduced #830: `_check_thresholds({"choice_accuracy":NaN},{"choice_accuracy":0.9},{})` returns `[]`; `_parse_pairs(["choice_accuracy=nan"])` accepts NaN. Enforce finite arguments/metrics outside harness.
4. Full test_evals attempt in numpy/pytest-only environment:58 passed,53 failed due absent Torch. Docs' metric-math-never-imports-Torch claim is too strong: `evals.ece()` lazily imports common, which imports Torch. Not evidence of failure with supported dependencies.
5. Initial combined pytest collection hit SystemExit because API suite is a plain script; rerun correctly. No full CI/weighted test claim.

No upstream implementation was modified. No Laya training/inference or Android tests were run.

## 8. Laya task relevance and Android feasibility

| Task | Expressible? | Demonstrated evidence / limitation | Sakshi role |
|---|---|---|---|
|Sentiment|Choice/ordinal|SST-5 English about0.372; ordinal weak| Compare specialist; no harm gate |
|Emotion|Choice/binary|Applications English accuracy0.595/macro-F10.4752; multilingual0.530; typed0.600 on400 DAIR cases| Research comparator, not intensity/distress proof |
|Harassment/toxicity|Preset/custom|Held-out ToxicChat near chance, no Sakshi subtype validation| Not primary pretrained detector |
|Threat|Noul/choice|No credible-threat evaluation; context/negation issues| Textual suggestion only |
|Intent|Choice|MASSIVE/XNLI/business tasks; workflow tuning helps| Mechanism relevant, domain different |
|Conversation|Serialized list/JSON|No validated interpersonal conversation checkpoint| Bounded context, review |
|Temporal|Ask about history|No intrinsic event-state/counting engine| Separate temporal component |
|Multimodal|No native pixel/audio/video input|Text-only encoder; OCR/STT text is ordinary state| No voice/pixel reasoning claim |
|Explanation|No arbitrary prose/spans|Finite decisions only| Templates/rules or independent validated LLM |
|Confidence of another model|Question possible|No Sakshi correctness-estimator training| Not universal confidence layer |

Single-label emotion accuracy, multi-label F1, toxicity AUC and synthetic decision accuracy cannot be ranked as the same metric.

### Android options A-G

| Option | Finding | Required path / recommendation |
|---|---|---|
|A. Direct Android|pip Laya is not Kotlin API|Graph runtime+tokenizer+decoder required; no drop-in Android SDK established|
|B. Embedded Python/Chaquopy|CPython possible, native wheels ABI/version-specific|Verify/cross-build full dependency set; reject as default MVP|
|C. Native inference|Technically plausible, not supplied validated|ORT Java/C++ +NDK tokenizer +Kotlin preprocessing/decode; substantial parity work|
|D. ONNX|Actual opset18 exporter, encouraging reported FP32 parity|Strongest Laya port: safetensors ->FP32 ONNX +assets ->ORT Android CPU ->typed Kotlin decode|
|D. TFLite/LiteRT|No inspected turnkey Laya port|Convert full wrapper, verify SDPA/RoPE/gather/masks/dynamic shapes and quantization; experimental|
|D. Core ML|Apple runtime|Not Android solution|
|E. On-device companion service|Native bound service can host working engine; Python server doesn't solve packaging|Prefer in-process; non-exported Binder/process only if measured need|
|F. llama.cpp|Custom ModernBERT+head, not causal Qwen/Gemma|No off-the-shelf Laya GGUF/converter/backend established; custom architecture work needed|
|G. ExecuTorch|Android .pte/Java/C++ runtime exists|Full torch.export/operator lowering/parity required; credible experiment, not Laya support proof|
|G. Other mobile runtime|General capability isn't graph compatibility|MNN/ncnn etc need operator/dtype/tokenizer port; defer absent concrete need|

Python>=3.10, Torch>=2, Transformers>=4.48, safetensors, HF Hub and numpy are core dependencies. Optional serve=FastAPI/Uvicorn; fast=TileLang; onnx=onnx/onnxruntime/onnxscript; structured=Pydantic. Android/Bionic is not Linux/glibc, and Linux ARM64 wheels/CUDA DGX Spark results do not prove Android support. Chaquopy historical Torch/tokenizers issues establish friction, not impossibility forever; compatible complete modern dependency set remains unestablished. [R5-R6][L12]

### Native ONNX tensor contract

```text
input_ids      int64 [Q,L]
attention_mask int64 [Q,L]
marker_pos     int64 [Q,Kmax]
marker_mask    bool  [Q,Kmax]
qtype          int64 [Q]       choice=0 score=1 noul=2
logits         float [Q,Kmax]
act_logits     float [Q,A]
```

Exporter traces batch2/sequence17/markers3 to prevent size1/equal-dimension specialization. Still test Q=1/2/4 and varying length/options on Android. Graph excludes tokenizer, serialization, question assembly, temperature/confidence, language routing, windowing and schema projection. Implement those separately. [L10]

Use `com.microsoft.onnxruntime:onnxruntime-android:<pinned-version>`, not desktop JVM artifact. Full operator CPU build first; custom reduced build after verification. NNAPI deprecated from Android15; do not require it. QNN/GPU/NPU require compatible graph/dtypes/builds/devices; fallback/partition overhead can erase gains. [R1-R3]

### Quantization evidence adjudicated

| Export |English eager agreement|Multilingual eager agreement|Scope|
|---|---:|---:|---|
|FP32 ONNX|96/96|100% of reported48 decisions|Contributor CPU comparison|
|Dynamic per-channel INT8, old default|31/96|40%|Severe drift|
|Dynamic per-tensor INT8, current default|64/96|83%|Still substantial drift|

**Agreement is not accuracy against truth.** Quantization could fix some wrong outputs, but invalidates presumed probability parity. #790 comments reproduced collapse on both TorchScript/dynamo. The scale-combination root-cause hypothesis in issue/docstring is not established as a universal ONNX per-channel defect; other models can use it. Defensible conclusion is Laya-specific measured drift in those environments. [L11]

Only MatMul weights are quantized. Multilingual256k×768 embedding alone has196.6M parameters, about786MB FP32; unquantized embedding makes “322M INT8=322MB” false. Reported reduction1.4x multilingual /2.8x English. QAT/SmoothQuant/embedding quantization/mixed precision are proposed experiments, not proven fixes. Phone safety use must wait for labeled quality/calibration after export.

## 9. Alternative discriminative and embedding models

Backbones require task training. Separate interfaces need not imply separate resident encoders.

| Candidate |Reason to investigate|Android path|Limitation|
|---|---|---|---|
|MiniLMv2-L6-H384, about23M BERT /30M RoBERTa-vocab|Compact width/depth, existing multi-label ONNX|ONNX+INT8 parity ->ORT CPU ->Kotlin logits/calibration|English narrow labels/domain shift|
|DistilBERT-base, about66M|Mature exports/classifiers|ONNX/INT8 ->ORT; verified TF/LiteRT alternative|Wider; SST-2 binary, no neutral|
|MobileBERT, about25M|Bottlenecked mobile distillation; paper62ms Pixel4|Fine-tuned TF/LiteRT ->metadata task API or CompiledModel/Interpreter|24-layer execution, not multilingual harassment-trained|
|ALBERT-base, about12M unique weights|Factorized/shared weights reduce storage|Supported ONNX/TF export ->CPU mobile|Repeated shared computation remains; small file not tiny compute|
|MiniLM-L3/TinyBERT/ELECTRA-tiny|Cheaper students|Distill/fine-tune ->ONNX/LiteRT|Rare-category/context capacity tradeoff|
|Multilingual MiniLM/E5-small, about118M|Broad vocabulary,384D|ONNX ->tested quantization ->ORT/native tokenizer|Large embedding; language/harassment validation needed|
|mBERT-base, about178M|Broad scripts, WordPiece|Task fine-tune ->ONNX ->ORT|Heavy; transliteration/code-mix weak|
|DeBERTa-v3-small|Strong NLU/teacher candidate|Verify relative-attention export ->ORT|44M nonembedding+98M embedding, about142M total, not44M|
|MuRIL-base|Indian languages +transliterated training|BERT task head ->ONNX ->ORT, TF alternative|Not tiny; needs task training|
|IndicBERTv2,278M|23 Indic+English, IndicXTREME|Fine-tune ->BERT ONNX ->ORT|Large; no interpersonal safety validation|
|mmBERT-small,140M (42M nonembedding)|384-wide modern multilingual|Classifier export ->ORT/ExecuTorch|256k vocab; quantization/operator validation open|

MobileBERT paper and2023 real-Android encoder study establish phone feasibility for tested models. The latter shows quantization/delegate failures and hardware variability; neither validates Sakshi exports/quality. [M4-M8]

**Specialists can be better for narrow labels, but not automatically.** Inspected23M `navodPeiris/minilm-toxic-classifier` reports threat F10.1455/recall0.25 despite ROC-AUC0.9454 and prevalence about0.3%. High AUC does not imply a safe threat detector. Not selected as primary threat model. [M3]

Jigsaw labels: toxic/severe-toxic/obscene/threat/insult/identity-hate. Not coercion/control/blackmail/stalking/doxxing/unwanted sexual conduct. Sexual-explicit is not sexual harassment: target/consent/context matter. For unsupported categories return unknown until rubrics/domain data exist.

### Embeddings and local retrieval

| Model |Contract|Use/limitation|
|---|---|---|
|`sentence-transformers/all-MiniLM-L6-v2`|22.7M,384D,masked mean pooling+L2,256 default; Apache2|English local retrieval, not harm probability|
|`paraphrase-multilingual-MiniLM-L12-v2`|384D mean,128 configured cap; Apache2|Card lists Hindi and about50 languages, not established Malayalam/Tamil/Telugu/Kannada/Bengali coverage|
|`intfloat/multilingual-e5-small`|117,653,760 floating params,12 layers,384D,512 cap,MIT,100 XLM-R languages|Better match to native scripts; query/passage prefixes required, low-resource/code-mix unvalidated|
|MediaPipe TextEmbedder|Kotlin Tasks for compatible .tflite+metadata|Convenient tokenizer-inclusive API, not arbitrary HF ONNX loading|

E5 requires prefixes even for non-English. Masked pooling/normalization and tokenizer must match reference. Token-state export is not automatically sentence embedding. [M9-M11][R7]

`text -> frozen embedding -> logistic/MLP head` is a cheap low-data baseline. Can miss fine-grained negation/roles; compare end-to-end fine-tuned classifier. A classifier's hidden state is not automatically a trained retriever. Shared retrieval+behaviour student needs multi-task evaluation.

For several thousand confirmed items, avoid a server/vector DB. Encrypt384D vectors with evidence/revision IDs; unlock then bounded case-specific cosine scan.10,000 float32 vectors=15.36MB raw values, arithmetic only, excluding metadata/index. Retrieve top-k plus bounded adjacent turns. ANN/native indexes later if measured need, with encryption/deletion/rebuild controls. Similarity is relevance, not same event/person or confirmed repetition.

## 10. Local compact-model experiments

**Executed here, not published or Android benchmarks.** Intel Core Ultra9 185H, x86_64, Linux7.2.8, Python3.12.14; ORT1.22.1, tokenizers0.22.0, numpy2.3.3; CPU EP, intra-op4/inter-op1. Separate Python processes, no affinity/power/thermal/file-cache control; tests may contend with other desktop activity. These are smoke tests, not representative accuracy evaluation.

| Artifact |Pinned Hub revision|Bytes/SHA256|Actual IO|
|---|---|---|---|
|minuva toxicity|c035f27b6a6d68770f8069a4829f1715a48b8d51|22,864,978; `bcd9dfb48cad802ac8f7cd789e1294f1f0b22d532797bd41f5a11694e3c269a0`|int64 ids/mask/type_ids [B,L] ->float logits[B,6]|
|minuva emotion|4fea72b9ec71ba8d84b88e0efa2ace3dcc733bfc|30,458,927; `594ac3bf3c82e2ea187e50982ea2f811ede5377eaad0c8ad23bc04ee8a2486c6`|int64 ids/mask [B,L] ->float logits[B,28]|

All eight synthetic inputs returned correctly shaped finite logits. Sigmoid applied independently; not softmax and not top1-only.

Forward-only repeated-token synthetic input, batch1,5 warmups,30 timed calls per length:

|Tokens|Toxicity p50/p95|Emotion p50/p95|
|---:|---:|---:|
|32|1.798/2.899ms|1.873/2.956ms|
|128|6.044/6.231ms|7.796/14.754ms|
|256|13.903/14.044ms|12.922/13.773ms|

Tokenizer+session load about63/140ms under uncontrolled file cache. Process peak RSS about201/162.5MiB respectively, includes Python/runtime/test/hash allocations, **not isolated model RAM**. Not phone predictions, cold-load guarantees, energy data or fair Laya comparisons.

Diagnostic raw sigmoid scores, no calibration or decision threshold validation:

|Synthetic input|Observed scores|Implication|
|---|---|---|
|“I am angry about the broken delivery, please fix it.”|Emotion anger0.97809; toxic0.00303|Anger is not harassment|
|“I will kill you tonight.”|Threat0.82328|Explicit lexical signal works in this example, not credibility proof|
|“You know where I live.”|Threat0.00029, emotion neutral0.98735|Context missing; negative score cannot establish benignness|
|“Do not threaten me again.”|Threat0.00199, anger0.96095|Reporting/rejecting harm differs from expressing it|
|“I will wait outside your home every night until you answer me.”|Threat0.02237, neutral0.97498|Stalking/control context can evade toxicity/emotion; never discard|
|Malayalam location sentence|English toxicity tokenization5 UNK of8; English emotion130 tokens|Information loss/fragmentation, unsupported language must abstain|
|Manglish location sentence|Low scores despite no UNK|Encoding Latin transliteration is not language competence|

Reproduction environment command used: `uv run --no-project --with onnxruntime==1.22.1 --with tokenizers==0.22.0 --with numpy==2.3.3 python /tmp/sakshi-ai-probe.py <model-dir>`. Temporary script performs local tokenization, feed based on actual input names, finite/shape assertions, sigmoid, timings and hashes. It contains only synthetic strings. Temporary tokenizer probe loads the two pinned Laya tokenizer.json files, inspects serialization metadata/special IDs and counts tokens. No training/model weights for Laya loaded.

Published minuva toxicity ROC-AUC0.98130 versus teacher0.98636, and emotion test F10.482 quantized versus0.48689 float, are **model-card task-specific values**, not comparable to Laya accuracy or proof of threat recall. Keep local observations separate. [M1-M2]

## 11. Local LLMs and exact Android deployment paths

Do not generate on every notification. Load/prefill/output length/KV/thermal cost often dominate headline throughput.

|Candidate|Documented metadata|Concrete Android path|Caveat|
|---|---|---|---|
|Qwen3-0.6B|0.6B,28 layers,GQA,32,768 native context,Apache2|Official Q8_0 GGUF639.4MB ->llama.cpp NDK/JNI ->Kotlin Flow; or .litertlm ->LiteRT-LM Kotlin|Not validated safety reasoning|
|Qwen3-1.7B|1.7B,28 layers,32,768,Apache2|Official Q8_0 about1.8GB ->llama.cpp; own Q4_K_M conversion after quality gate|4-bit weight floor850MB before metadata/scales, not exact RAM|
|Gemma3-1B-IT QAT INT4|1B,text-only,32k native; Gemma terms|Published529MB,2048 exported context ->LiteRT-LM/compatible legacy MediaPipe|Gated access/license, export context differs|
|Gemma3n-E2B|Mobile multimodal effective2B|.litertlm ->LiteRT-LM|Overview about2,965MB artifact,S24 Ultra decode16tok/s; not500MB|
|Qwen3.5-2B|2B backbone+vision,24 hybrid gated-delta/full-attention,262k native,Apache2|Only exact Qwen3.5-compatible converter/runtime/device tests|Different graph from Qwen3; no established measured Sakshi phone path here|
|Gemma4-E2B|Current overview2.58GB artifact|LiteRT-LM supported Android|Newer/S26 benchmarks not reason to inflate MVP|

Other1B-4B Llama/Phi variants are possible compatible-runtime candidates, not additional required engines. No overall accuracy ranking established. [M12-M16][R4][R6]

### Published measurements, preserving scope

- Google Gemma3 post: QAT INT4,529MB,context2048,prefill shapes32/128/512/1024, up to2,585 prefill tok/s on S24 Ultra performance governor. Prefill isn't decode/end-to-end; startup/cache costs remain.
- Current LiteRT Gemma card indexed table: CPU dynamic INT4/context1280 about657MB file,982MB RSS,138 prefill/50 decode tok/s,2.33s TTFT; other variants differ. Direct raw card gated; indexed publisher extracts used, not downloaded/run.
- Qwen community exports differ:329MB dynamic INT4/block32/context4096;474.61MiB mixed INT4/context2048;586MB dynamic INT8/context4096. Do not attach mixed-INT4 timing to329MB artifact.
- Mixed INT4,2048 context,256 prefill+256 decode,LiteRT-LM0.13.1,warm iteration: SM-S937U1 GPU69.38 decode tok/s,0.150s TTFT,585MB private footprint; TECNO LJ9 GPU33.51/0.430s/1,832MB; TECNO CPU8.33/about2,890MB. Publisher retail CLI measurements, not integrated app/vendor-certified results.
- Small file can still yield multi-GB backend memory. Metrics/allocators differ by backend/device; no generic RAM inference.
- Kotlin docs warn initialize can take up to about10s; not a guaranteed startup time.

**First optional reasoning experiment:** `litert-community/Qwen3-0.6B`, `Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm`,329MB,4096 cap, revision **a3c5d805ae362dff7f580bc25f2dfb9a5a7eaa76**, LiteRT-LM. Card says compatible with0.17.1/current builds. Select/lock a compatible released Maven version and artifact hash; do not use latest.release. No throughput/RAM measurement for this chosen artifact was performed here.

If0.6B fails contextual/anchor quality, compare1.7B llama.cpp on capable devices or retain human-only interpretation. LLM is not final authority.

### Complete runtime flows

```text
MiniLM behaviour/emotion ->ONNX ->tested INT8/FP32 ->ORT Android CPU
 ->Kotlin OrtSession +native tokenizer ->sigmoid/calibration ->signals

MiniLM/E5 retrieval ->ONNX +mean pooling/L2 ->tested quantization ->ORT CPU
 ->Kotlin/native tokenizer ->embedding ->case-local search ->evidence IDs

Qwen/Gemma ->compatible .litertlm +chat/tokenizer metadata ->baked quantization
 ->LiteRT-LM CPU, GPU after validation ->Kotlin Engine/Conversation
 ->bounded output ->strict JSON ->anchor validator ->reviewable interpretation

Qwen llama.cpp alternative ->official Q8_0 or validated Q4_K_M GGUF
 ->arm64 NDK .so ->Kotlin JNI/Flow ->tested schema-constrained decoding
 ->structured interpretation with source IDs, not automatic facts
```

LiteRT-LM isn't an arbitrary Laya encoder runtime; GGUF isn't universal Hugging Face compatibility.

## 12. Compare complete architectures

Conditional engineering trade-offs below, **not measured head-to-head scores**. No common Sakshi dataset/device establishes an accuracy winner.

|Architecture|Accuracy potential/failure|Latency/RAM/battery|Context/explanation|Android conditions|
|---|---|---|---|---|
|A Laya only|Flexible labels, weak held-out toxicity; tuning may help|Large encoder,Q rows,long head,current INT8 drift|Typed distributions,no prose|Research niche after export gate|
|B Laya+rules|Exact signals/rules complement; rejecting rules can miss|Laya memory remains|Rule trace+labels|If Laya proven narrow workflow|
|C Small classifier|Stable narrow labels,unknown/context blind spots|Smallest transformer core,fixed outputs|Short windows,templates|Recommended minimum baseline|
|D Classifier+Laya|Possible subset gains,correlated errors|Adds322/421M+tokenizer/load|No free explanatory prose|Only measured conditional benefit|
|E Classifier+LLM|Selected context interpretation,hallucination/refusal|Fast common path,expensive branch; peak remains if resident|Validated evidence-linked output|Recommended extension,not mandatory|
|F Embeddings+classifier+LLM|Retrieves relevant history,retrieval error propagation|Extra encoder/index/cost|Local RAG/source IDs|As confirmed vault grows|
|G Full cascade+temporal|Combines context/history; serial false-negative gates|Depends on activation/lifecycle,not diagram boxes|Deterministic patterns+abstention|Production direction if staged/ablated|

Naive mandatory rule->sentiment->behaviour gates amplify misses. Hypothetical independent0.9-recall gates yield0.9³=0.729; not real measured independence, just warning. **Sentiment never gates safety classification.**

Expected cost model, not measurement:

`E[L] = acquisition/extraction + rules + classifier + P(retrieval)*retrieval + P(LLM)*LLM + P(cold_load)*load + review preparation`.

Cascade doesn't prove lower peak RAM; all-preloaded can be worse. Benchmark lifecycle/bursts and missed incidents jointly with invocation reduction.

## 13. Routing, disagreement and abstention

Route by input/extraction validity, language support/tokenizer diagnostics, truncation, quotes/negation/pronouns, supported-category calibration, rare-label policies, user concern, rule candidates, confirmed prior context, resource/thermal state. High confidence is still a suggestion, never evidence deletion/automatic accusation.

Policy:

1. Invalid/unavailable text: preserve selected original, unknown/extraction state.
2. Unsupported language/severe OCR-STT uncertainty: abstain from English models, correction/review; only validated target-language branch.
3. User-marked/rule candidate: review regardless of sentiment/toxicity.
4. Eligible text: narrow behaviour model; emotion independently when requested.
5. Context-dependent/truncated/novel/uncertain: same-case confirmed retrieval+authorized adjacent turns.
6. One installed/validated LLM only for user-approved foreground interpretation with resource budget. Background policy separately consented/tested.
7. No engine/timeout/bad anchors/schema/unresolved: abstain to human.
8. Retention/review policy controls persistence; temporal engine separate.

No defensible percentage reduction in LLM calls yet. Measure activation/cold-load fractions against relevant-incident recall/review burden.

**Neutral emotion +threat behaviour is not disagreement:** different constructs; polite threats exist and victim anger isn't perpetrator behaviour. Same-task disagreement requires aligned labels/context. JS divergence, score gaps, entropy, perturbation instability can route after validation. Correlated teachers aren't independent witnesses; don't average incompatible probabilities into risk score.

Selective prediction: risk-coverage curves and rejected-population audit. Conformal sets can express multiple plausible labels under exchangeability; marginal coverage isn't person/language safety or explanation truth. Conversation dependence, routing selection, drift and personalization violate naive exchangeability. Multi-label/per-language procedures require adequate calibration. Start explicit abstention states before conformal research. [S1-S3]

## 14. Sentiment, emotion, intensity, context and temporal engine

Keep constructs separate: evaluative polarity; expressed emotion (not mental diagnosis); harmful behaviour/target; expressed intensity (not confidence); temporal events.

Three-way sentiment reference: `cardiffnlp/twitter-roberta-base-sentiment-latest`, English negative/neutral/positive, export/test ONNX then distill onto compact student. Card CC-BY4.0/tweet domain; review artifact/backbone redistribution. Defer resident sentiment unless demo needs it. DistilBERT SST-2 has no neutral. [M4][M17]

GoEmotions model supplies anger/annoyance/fear/sadness/nervousness/neutral, not directly frustration/distress/hostility. Don't silently rename without training. Intensity null in MVP. Dedicated regression/ordinal data required; SemEval2018 EI-reg has anger/fear/joy/sadness intensity, while commonly surfaced HF config is classification only. Verify original terms/columns and inter-rater/domain metrics. [D4]

Trajectories can display reviewed expressed labels, but neutral->frustration->anger->hostility isn't universal validated escalation. Separate speakers/roles, show uncertainty and source IDs, don't smooth away rare threats or interpret survivor distress as perpetrator escalation.

Context: “You know where I live” can be logistics/distress/report/intimidation. Authorized imported turns/notification fragments only, no scraping. Adjacent turns first, case-scoped retrieval second; preserve quotes, speaker/time claims, missing ranges. Generated summaries are derivative indexes, never sole history. Evaluate retrieval recall and counterfactual context; missing evidence cannot be repaired by RAG.

|Temporal approach|Benefit|Feasibility/limitation|
|---|---|---|
|Deterministic windows/rules|Incremental distinct counts,IDs,traces|Cheap Android CPU; recommended MVP; dedup/coverage needed|
|EWMA/CUSUM/rate models|Bursts/baseline change|Irregular capture confounds rates; validate|
|GRU/HMM/small sequence model|Learn transitions cheaply|Labeled sequences/drift/explainability required|
|Temporal transformer|Long dependencies|More data/memory,not current need|
|Graph model|People/event relations|Unverified identities,cross-case privacy; defer|
|LLM history reasoning|Flexible summary|Counting/order/causality unreliable; feed deterministic facts|

Temporal hierarchical-attention/graph cyberbullying work already exists; temporal detection alone not novelty. [D8]

MVP patterns:

- Repetition: distinct reviewed event IDs, not notification reposts or overlapping windows.
- Frequency: observed counts in explicit window; no denominator for all unseen messages.
- Persistence: after user-recorded unwanted-contact boundary, with identity/coverage caveats.
- Transitions: reviewed behaviour signals, no universal severity ordering.
- Possible escalation: newly explicit harm/consequence after earlier restrictive/intimidation signals, supporting IDs listed.
- Emotion trajectory: optional expressed labels under stable rubric, no legal/clinical inference.

Keep event time versus import/posting time, timezone/clock uncertainty, source gaps and roles. Incomparable coverage ->counts only/rate abstention. Hash/semantic duplicates suggest association, never destructive deletion.

## 15. Shared contract and Kotlin design

One global uncertainty scalar hides incompatible failure modes. Use versioned typed records; probability null if no validated calibration. Example is illustrative, not measured:

```json
{
  "schema_version": "sakshi-analysis/1",
  "analysis_id": "analysis-184-v2",
  "evidence_id": "message-184",
  "text_derivative_id": "ocr-184-v1",
  "input_digest": "sha256-of-exact-analyzed-derivative",
  "source": {"kind": "user_import", "identity_status": "user_asserted", "event_time_status": "source_claimed", "coverage": "partial"},
  "language": {"codes": ["en"], "script": "Latin", "code_mixed": false, "support_status": "validated_slice"},
  "extraction": {"status": "user_corrected", "truncated": false, "missing_ranges": []},
  "signals": [{
    "label": "possible_intimidation",
    "epistemic_status": "inferred",
    "raw_score": null,
    "calibrated_probability": null,
    "calibration_id": null,
    "rubric_version": "behaviour-rubric/1",
    "intensity": null,
    "anchors": [{"evidence_id": "message-184", "derivative_id": "ocr-184-v1", "start_utf16": 0, "end_utf16": 21}],
    "supporting_context_ids": ["message-170", "message-178"],
    "limitations": ["credibility_not_established"]
  }],
  "expressed_emotion": {"labels": [], "intensity": null, "status": "not_evaluated"},
  "uncertainty": {"reasons": ["context_dependency"], "same_task_disagreement": null, "prediction_set": null},
  "routing": {"action": "human_review", "reasons": ["context_dependency"], "llm_used": false},
  "model_runs": [{"model_id": "pinned-candidate", "artifact_sha256": "manifest-digest", "runtime": "ORT-Android-CPU", "schema_digest": "question-label-contract-digest"}],
  "review": {"state": "pending", "user_corrections": []}
}
```

Contract invariants:

- Observed=actual source text/record, inferred=interpretation, pattern=multiple supporting event IDs, unknown=not established.
- OCR/STT anchors refer to versioned derivatives with image polygons/page/audio times mapping back to originals. UTF16 text offsets for Kotlin are not UTF8 byte offsets or model-token offsets.
- Probabilities/distributions retain model/schema/calibration/backend identity; unknown !=negative; unavailable !=safe.
- Keep label score, ordinal severity, intensity, correctness confidence and danger separate.
- Evidence IDs returned by LLM must belong to allowed input set; exact quoted strings/offsets must match source derivative. Valid JSON or an existing source ID alone does not prove entailment.
- User corrections create new versions; originals/model outputs preserved independently. Reanalysis doesn't silently rewrite historical review/patterns.

### Recommended components, not one model per file

|Component|Responsibility|MVP decision|
|---|---|---|
|LocalAIEngine /IncidentAnalyzer|Orchestration and provenance contract|One orchestrator, avoid duplicate controllers|
|TextExtractor|OCR/STT/parsing adapters with source maps|Separate modality adapters|
|BehaviourClassifier|Multi-label narrow text signals|ORT adapter; production shared student|
|EmotionAnalyzer /SentimentAnalyzer|Optional heads, different constructs|Interfaces may share model; no safety gate|
|InferenceRouter|Deterministic policy/abstention/resource routing|No neural router required|
|ContextRetriever|Confirmed same-case adjacent+semantic evidence|Adjacent turns first; embeddings later|
|PatternEngine|Distinct reviewed events, windows/coverage, traces|Pure Kotlin core|
|LocalLLMEngine|Bounded optional native reasoning|Lazy-load only; no tools/network|
|LayaEngine|Experimental ORT+native-tokenizer port|Absent default MVP; no fake implementation|
|ModelRegistry /ModelLease|Pinned assets,compatibility,session ownership|One bounded heavy-model lease|
|EvidenceRepository /ReviewInbox|Encrypted records and source-specific retention|No raw text in logs/worker arguments|

Architecture: Compose/ViewModel ->use cases ->domain router/patterns ->repository+model adapters. NLS callbacks only bounded snapshot/nonblocking handoff. WorkManager schedules authorized durable extraction jobs with evidence IDs, not plaintext. Bounded executor/Mutex serializes heavy native inference; no GlobalScope or many concurrent models. Propagate CancellationException; coroutine timeout doesn't stop a synchronous native call by magic. Use runtime cancellation/termination API and lifecycle owner. Close native tensors/results/sessions; don't unload during in-flight calls.

### Kotlin orchestration pseudocode

The following describes proposed interfaces, not compiling application code or implemented APIs. Threading/cancellation/resource lease are mandatory implementation details.

```kotlin
suspend fun analyzeMessage(input: EvidenceText, request: AnalysisRequest): Analysis {
    val quality = assessInput(input)
    if (!quality.hasUsableText) return Analysis.unknown(input, quality.reasons)
    val rules = ruleEngine.observe(input)
    val fast = if (supportRegistry.allows(input.language, quality)) {
        modelLease.withClassifier { behaviourClassifier.classify(input) }
    } else {
        Signals.unsupported(input.language)
    }
    val route = routeInference(input, quality, rules, fast, request)
    val context = if (route.needsContext) contextRetriever.retrieve(input.caseId, input) else Context.empty()
    val slow = if (route.allowLocalReasoning && modelRegistry.canRunReasoner()) {
        modelLease.withReasoner { localLLM.interpret(input, context, request.budget) }
    } else {
        Interpretation.notRun(route.reasons)
    }
    return anchorValidator.combine(input, rules, fast, context, slow, route).pendingReview()
}

fun routeInference(input: EvidenceText, quality: InputQuality, rules: RuleSignals,
                   fast: Signals, request: AnalysisRequest): Route {
    if (!fast.languageSupported || quality.unreliable) return Route.humanReview("unsupported_or_unreliable")
    val needsContext = input.truncated || rules.contextCue || fast.contextDependent || fast.uncertain || request.userMarkedConcern
    val needsReview = rules.hasCandidate || fast.hasCandidate || request.userMarkedConcern || fast.uncalibrated || fast.uncertain
    return Route(needsContext = needsContext,
                 needsReview = needsReview,
                 allowLocalReasoning = needsContext && request.foregroundReasoningConsent,
                 reasons = collectRouteReasons(input, quality, rules, fast))
}

suspend fun analyzeConversation(caseId: CaseId, selectedIds: List<EvidenceId>): ConversationAnalysis {
    val events = repository.authorizedTexts(caseId, selectedIds)
    val analyses = events.map { analyzeMessage(it, AnalysisRequest.reviewOnly()) }
    return ConversationAnalysis(analyses, patternEngine.observeAvailable(events))
}

suspend fun updateIncident(command: UserReviewCommand): Incident {
    return repository.transaction {
        val reviewed = applyReview(command)
        val snapshot = reviewedDistinctEvents(reviewed.caseId)
        savePatterns(patternEngine.detectEscalation(snapshot))
        loadIncident(reviewed.incidentId)
    }
}

fun detectEscalation(events: List<ReviewedEvent>): List<PatternFinding> {
    val distinct = deduplicateRepresentations(events)
    val windows = comparableObservationWindows(distinct)
    return windows.flatMap { window ->
        evidenceLinkedTransitions(window, rubric, coveragePolicy)
    }
}
```

An unsupported-language route can still retain user-marked/rule observations for review; it must not discard them. If confidently negative narrow classifier misses context cues, a separate rule/user/pattern branch must still allow context/manual review. Do not require fast uncertainty to activate all context work.

### Concrete ORT Kotlin tensor pattern

Official Java API establishes OrtEnvironment/OrtSession/OnnxTensor/Result.close. Kotlin callers can use it; below assumes prevalidated equal-length ids/mask and actual manifest input names. This tiny example is not full model adapter or compiled here. [R1]

```kotlin
val environment = OrtEnvironment.getEnvironment()
val options = OrtSession.SessionOptions().apply {
    setIntraOpNumThreads(4)
    setInterOpNumThreads(1)
}
val session = options.use { environment.createSession(modelFile.absolutePath, it) }
OnnxTensor.createTensor(environment, arrayOf(ids)).use { idTensor ->
    OnnxTensor.createTensor(environment, arrayOf(mask)).use { maskTensor ->
        session.run(mapOf("input_ids" to idTensor, "attention_mask" to maskTensor)).use { result ->
            val logits = (result[0].value as Array<FloatArray>)[0].copyOf()
            consumeValidatedLogits(logits)
        }
    }
}
```

Toxicity artifact additionally needs token_type_ids; emotion doesn't. Never reuse the two-input snippet blindly. Registry owns session.close after all leases complete; process-level environment shared. Validate tensor names/dtypes, finite logits and label-order manifest at load.

### Concrete LiteRT-LM Kotlin entry points

Docs expose `Engine(EngineConfig(modelPath, backend=Backend.CPU()))`, initialize, createConversation, sendMessage/sendMessageAsync Flow, close. Use worker thread and one scoped conversation per case/analysis, not shared history across people. [R4]

```kotlin
val engine = Engine(EngineConfig(modelPath = assetPath, backend = Backend.CPU()))
engine.initialize()
engine.createConversation(conversationConfig).use { conversation ->
    val response = conversation.sendMessage(boundedEvidencePrompt)
    validateStructuredResponse(response)
}
engine.close()
```

Configure max context/output tokens through the pinned API, disable automatic tools, no network tools, validate cancellation, and do not log response.toString. Prompt should request finite labels, exact excerpts, allowed IDs, unknown factors, and abstention. Non-thinking Qwen chat-template behavior must be verified in the exact export; removing a thinking channel from UI isn't necessarily disabling internal generation. Constrained decoding limits syntax, not truth.

## 16. Explainability, privacy, storage and licensing

### Explanations

|Technique|Can explain|Cannot prove|
|---|---|---|
|Raw probability|Model preference|Correctness, actual risk, calibrated confidence|
|Calibrated probability|Frequency under validation population|Individual truth or unseen-language guarantee|
|Token highlights/attention|Potential attribution/local evidence|Faithful causality; attention isn't explanation automatically|
|Trained rationale spans|Human-plausible supporting source tokens|Full contextual correctness|
|Rule trace|Exact matching condition/source spans|Semantic validity of every match|
|Retrieved context|Which prior evidence was supplied|Relevance/causality/identity|
|LLM explanation|Candidate interpretation in readable form|Truth, completeness, credibility or legal conclusions|

Use deterministic user-facing templates first:

> Potential intimidation signal. The selected message refers to waiting outside a home until a response. Similar contact-related language appears in three distinct reviewed events. Source: message184 and events170/178/182. Capture may be incomplete; credibility and sender intent are not established. Review, correct or reject this interpretation.

This is an illustrative UX, not a model finding from real evidence. Never show only “AI SCORE87.” Quote anchors must be validated; explanation entailment/source attribution independently tested. Laya's nongenerative output removes prose hallucination/parsing failure modes, **not wrong or fabricated classifications**.

### Privacy threat review

- Runtime inference can be offline after provisioning. HF/SDK/model downloads are network operations, not evidence analysis; explicit download consent and signed/pinned hashes.
- Bundle models for reproducible offline demo; no inference API fallback, server, telemetry endpoint, cloud crash payloads or remote LLMs.
- Laya core has no necessary hosted inference, but hooks/integrations can log or transmit state; disable them and inspect configuration. Local paths must be complete to prevent fallback downloads.
- ML Kit terms say input/output stays on-device, **but SDK metrics/update/compatibility traffic is documented**. Local evidence processing isn't zero-network/zero-telemetry. Disclose and verify. Strict zero-network build should use audited bundled native OCR such as Tesseract or suitable audited alternative instead.
- Generative prompts, embedding vectors, model scores, sender/time metadata and caches are sensitive. Encrypt/delete/rebuild them under same policy as evidence, exclude from analytics/backup.
- No shared storage plaintext temp files. App-private bounded temp only if unavoidable; encrypt where feasible, purge reliably, understand flash erasure limitations. Memory is plaintext during inference; Keystore cannot protect against active device compromise.
- Disable plaintext Logcat/native inference logs, prompt/result dumps, clipboard collection, screenshots in sensitive UI, evidence-rich crash reporting. Benchmark synthetic fixtures only.
- Bound untrusted images/audio/archives/PDF/JSON lengths and resource use. Original bytes immutable; extractions/normalizations/denoising separated.
- Same-app non-exported service/Binder preferred to HTTP. Loopback is not authentication; another app may connect to local TCP port. Separate companion APK complicates consent/sandbox/data handoff.
- Do not use an always-on heavy model service. Load only during authorized analysis; optional notification candidates get a separately consented expiring encrypted review inbox, ordinary transient observations not automatically vaulted.
- User-selected originals may be saved before analysis under import consent; passive candidates require confirmation for evidence retention. Do not silently upload, export originals, or persist complete ordinary notification history.

### Storage proposal

Encrypted app-private/no-backup storage with Keystore-wrapped keys/AES-GCM and fresh unique nonces. Store original bytes+SHA256/provenance, derivative text/source maps/model IDs, analysis records/calibration/schema hashes, review actions/corrections, case-local embeddings if enabled, versioned pattern records and explicit export manifests. Room is not encrypted by default; use an audited encrypted database integration or encrypted columns/files with carefully scoped indexes. No plaintext FTS/vector-index/cache outside vault.

Original hash supports integrity relative to received bytes, not authenticity/guilt/legal admissibility. Timestamp/sender assertions retain provenance. Key-loss/authentication/backup recovery policy is a product decision; no promise Keystore/StrongBox protects plaintext on rooted/compromised phone. Exclude evidence from backup/device-transfer with device validation. Persist/export only explicit reviewed content.

### Licensing register

|Asset|Inspected declaration|Required action|
|---|---|---|
|Laya code/checkpoint cards|Apache2|Ship notices/license; pin exact assets; audit training/tokenizer lineage separately|
|ModernBERT backbone|Apache2|Preserve notices|
|mmBERT backbone|MIT; Gemma2 tokenizer lineage|Review bundled tokenizer artifact terms; do not infer all upstream terms solely from Laya card|
|minuva classifier cards|Apache2|Checkpoint/export notices plus Jigsaw/GoEmotions data terms|
|MiniLM sentence embeddings|Apache2|Tokenizer/base/weights notices|
|multilingual E5-small|MIT|Base/derived assets review|
|Qwen3/Qwen3.5|Apache2|Export/community-conversion provenance and notices|
|Gemma3/3n|Gemma custom terms; gated assets|Accept terms legitimately; review redistribution/use obligations; no gate bypass|
|ORT/llama.cpp/whisper.cpp|MIT family projects|Pin build/licenses, audit dependencies/model weights|
|LiteRT/LiteRT-LM/Tesseract|Apache2 project licensing|Verify exact SDK/native dependencies/assets|
|ML Kit|Google API/SDK terms|Not open-source model redistribution; metrics disclosure|

Model license is not dataset license or evidence-access permission. Training provenance may limit commercial use even when a model card advertises permissive weights. This is engineering due diligence, not legal certification.

## 17. Data and training strategy

### Dataset inventory and gaps

|Source|Language/modality/labels|License/provenance evidence|Use/quality caveat|
|---|---|---|---|
|Jigsaw Wikipedia toxicity subtypes|English text,6 labels incl threat/insult|TFDS source declares CC0 including underlying comments; verify exact competition release/rules|Imbalanced threat about0.3%; encyclopedia talk pages !=private coercion|
|Civil Comments|English comments,toxicity/identity annotations|Exact TFDS release declares CC0; preserve release attribution|Bias/prevalence tests, not comprehensive interpersonal labels|
|GoEmotions|58k English Reddit comments,27 emotions+neutral,multi-label|Google repo/HF declaration Apache2|Subjective,short,social-media; no intensity labels|
|DAIR Emotion|English6 emotions|Specific release license must be rechecked before training/distribution|Useful comparison, no neutral/intensity; not harassment|
|TweetEval sentiment|English tweets,negative/neutral/positive|Dataset release terms/twitter source obligations separately verify|Public social-media domain and slang bias|
|HateXplain|English Twitter/Gab,hate/offensive/normal,target,rationale|HF metadata CC-BY4.0 but prose says MIT: **conflicting**|Resolve data license before shipping; no external conversation context; annotation disagreement|
|ToxicChat0124|10,165 English user-AI prompts,binary toxicity/jailbreak|CC-BY-NC4.0 in publisher card|Research evaluation subject to terms; not commercial-training default; not interpersonal chats|
|DravidianCodeMix2020|Tamil-English~44k,Malayalam-English~20k,Kannada-English~7k YouTube comments; sentiment/offensive|Original Zenodo CC-BY4.0|Real code-mix,imbalance/comment sampling; doesn't supply coercion/stalking truth|
|HASOC/ICHCL|Hindi/English/Marathi and Hinglish contextual threads; later Bangla tasks|Task-specific license/redistribution unresolved here|Valuable context/code-mix tests after terms review; controversial-topic sampling bias|
|IndicXTREME/IndicCorp/MuRIL corpora|Indic NLU/transliteration,not harm labels|AI4Bharat declares datasets CC0,models/code MIT; MuRIL Apache2|Language representation/teacher data,not Sakshi target labels|
|SemEval2018 Affect in Tweets|English/Arabic/Spanish,intensity/classification|Original release terms need verification|Use actual EI-reg not classification-only HF columns|
|HateCheck|Functional crafted tests incl negation/counterspeech|Paper verified,exact asset license must be checked|Diagnostic constructed cases,not real prevalence estimate|
|Typed-decisions|English synthetic business/security,teacher distributions|Apache2 card|Validate plumbing/teacher imitation,not real harassment evidence|

No reviewed dataset establishes full control/coercion/blackmail/stalking/doxxing/sexual-harassment labels across all required languages with temporal evidence/consent context. This is the dominant production bottleneck. Do not manufacture that conclusion from generic toxicity labels. [D1-D9]

### Sakshi-specific evaluation set

Recommendation: start with **600 reviewed message/context units plus120 short sequences**, a design target, not collected data. A minimum demo subset can be smaller but cannot claim population accuracy. English/Malayalam/Hinglish/Manglish first; expand Hindi/Tamil/Telugu/Kannada/Bengali/Marathi/native/transliterated/code-mixed slices explicitly. Require native-speaker annotations and meaningful per-category positive counts rather than universal coverage from a handful per language.

For each unit store source/license/consent, real/synthetic flag, language/script/mix, speaker-role/context, modality/extraction quality, availability/truncation, operational labels, uncertain/disagreement flags, rationale spans, participant/conversation/source grouping. Annotators can answer unknown. Use independent labels/adjudication; report agreement. Opt-in legitimate licensed evidence only; no silent uploads or uncontrolled scraping.

Include:

- Anger/negative sentiment without harassment; polite/neutral intimidation; victim reporting/negation.
- Conditional exposure, controlling requests, repeated unwanted contact, location references; consent ambiguity.
- Quotes/forwarded text, reclaimed slurs, jokes/sarcasm, identity terms benignly used, misspellings/transliteration.
- Counterfactual contexts for identical message, reordered labels, paraphrases, context omitted/changed.
- OCR/STT corruption, cropped screenshots, fragmentary notifications, clock changes, reposts/import duplicates.
- Repetition with gaps versus apparent frequency caused by capture changes.

Synthetic fixtures test mechanisms; never report them as survivor evidence or real-world prevalence. Split by conversation/person/source/template family before questions/windows/augmentation. Calibration and final test never used to choose prompts/thresholds/student. Report confidence intervals and sparse-slice uncertainty. Restrict test access to avoid repeated adaptive tuning.

### Training choices

1. **Pretrained baseline:** fastest implementation, narrow claims, local domain tests mandatory.
2. **Compact supervised multi-label fine-tune:** primary production experiment; BCE with missing-label masks, calibrated scores, category/context-required heads and rationale supervision when licensed.
3. **Knowledge distillation:** teacher models offline on licensed public/consented training data ->human-checked soft targets/rationales ->small student. Different students can share an encoder; compare frozen embedding+linear vs full student.
4. **LLM LoRA:** only if selected hard cases show a clear gap; train away from phone, merge/export/requantize then evaluate anchor faithfulness/refusal/multilingual quality. Not a privacy license to upload real evidence to Kaggle/cloud teacher APIs.
5. **Laya domain fine-tune:** separate experiment with case-level holdout, corrected preprocessing, CE-only vs RLCD ablation; act head separately retrained/evaluated if used.

Distillation can reduce on-device footprint but inherits teacher bias/hallucination. Do not distill only easy synthetic examples or treat LLM confidence as calibrated ground truth. Cost-weighted decisions need deployment-prevalence calibration. QAT can help particular student graphs but never guarantees accuracy parity. On-device gradient fine-tuning is not MVP; user corrections first update local reviewed records/baselines, not covert model retraining.

## 18. Multilingual decision policy

Laya refreshed MASSIVE20-option intent table, about100 samples/language, publisher numbers, **not harassment**:

|Language|English Laya accuracy|Multilingual accuracy|Multilingual ECE|
|---|---:|---:|---:|
|English|0.820|0.710|0.233|
|Hindi|0.100|0.460|0.368|
|Malayalam|0.070|0.280|0.443|
|Tamil|0.120|0.310|0.448|
|Telugu|0.090|0.220|0.497|
|Kannada|0.110|0.300|0.388|
|Bengali|0.080|0.450|0.354|

Current BENCHMARKS refresh says macro0.4008 vs older card0.3661; English0.2269. Use refreshed table with software/clamp context; do not silently blend old calibration figures. No Marathi, Hinglish/Manglish/Tamil-English harassment quality established by this table. [L7]

MuRIL explicitly trains Indian transliteration and includes required native languages; a useful teacher/backbone candidate, not automatically mobile-efficient or code-mix safety-trained. E5-small claims100-language representation and warns low-resource degradation. Multilingual paraphrase MiniLM's card does not establish all Sakshi target languages.

Smaller multilingual classifier **may** beat larger English LLM on Indic task; prove on matched slices. Token fertility, unknown fraction, Unicode/romanization handling, model task data and local slang/cultural context matter. Language ID can abstain; ASCII !=English. User-selected language plus script/compact LID candidate can guide routing, never override evident input. fastText lid.176.ftz is917kB and CC-BY-SA3.0; optional native LID with terms/short-code-mix tests, not required MVP.

No automatic translate-to-English default: translation can lose harm/negation and adds model/runtime/retention risks. Preserve original-language text; user corrections remain independent derivative. Unsupported languages receive manual organization and explicit model abstention.

## 19. Android benchmark and promotion gates

### Hardware matrix and reproducibility

Minimum physical devices: a4-6GB mid-range ARM64 phone, a6-8GB mainstream phone, and an8-12GB flagship (e.g. S24 Ultra/SM8650 or Tensor flagship), preferably different vendors. Existing reports mention a reference phone but no workload validation. Add a Pixel6+ if power-rail metrics desired; deviceSupportsHighPrecisionTracking check. Emulator only tests basic API, not representative latency/thermal/battery.

Pin APK/build/OS/SoC/ABI/RAM/runtime/model hashes/tokenizers/schema/calibration/thread count/backend/context. Release/profileable build, sanitized fixtures, airplane-mode test after assets ready. Record ambient temperature/battery/thermal status/background apps; randomize model order with cool-down, sustained runs separate from cold idle. Don't silently force a performance governor unavailable to ordinary users.

### Arms

Current arms: A compact classifier FP32 and INT8; C chosen local LLM CPU/GPU; D classifier+rules+retrieval+LLM route; E full student+temporal cascade. Deferred research-only arm B: Laya English/multilingual FP32 ONNX and separately INT8, only if the integration decision is revisited. Same labeled evidence/context availability and identical acquisition transformations; no unavailable app backend.

|Metric|Exact method/meaning|
|---|---|
|Download/installed size|Artifact bytes+tokenizers+native libraries+first-run cache, not weights only|
|RAM/peak|App PSS/native heap/RSS and backend GPU buffers; sample during load/prefill/decode; distinguish app/process/private footprint|
|Cold start|Process launch+model parse/load, first cache compilation separate from warm-file-cache cold process|
|Warm latency|Per-stage tokenizer/forward/decoder/total p50/p95/p99; input length32/128/256/512, LayaQ1/2/4; actual questions logged as hashes|
|LLM throughput|Prefill tok/s,TTFT,decode tok/s,total response time,input/output/context counts; no encoder tok/s analogy|
|CPU/GPU|Perfetto/simpleperf/thread CPU time; GPU counters when accessible, unknown if not supported|
|Energy/battery|Macrobenchmark PowerMetric supported hardware; otherwise repeated idle-subtracted charge/current integration or external power monitor; not single-message percentage drop|
|Thermal|Thermal status/temperature signals,sustained throughput/slowing/errors, ordinary battery-saver state|
|Robustness|OOM/process death/cancellation,restart,key lock,background restrictions,queue saturation,low disk,corrupt assets|
|Quality|Per-language/category PR-AUC,recall/precision,FPR per1000 benign messages,calibration,risk-coverage,missed rare incidents|
|Cascade|LLM call fraction,cold loads,review burden,e2e errors,latency/energy per retained candidate,not just common fast path|
|Retrieval/explanation|Recall@k,adjacent context coverage,exact anchor/quote validity,entailment review,prompt injection/refusal tests|
|Privacy|Offline completion+traffic/log/temp/backup audit with synthetic fixtures,no remote fallback|

Suggested protocol:30 cold-process repetitions,10 warmups then200 representative warm units per relevant shape, sustained20-minute replay plus burst test. These are proposed run counts, not achieved results or work-time estimates. Bootstrap by conversation, not correlated question row.

### Gate order

1. Tokenizer parity: exact IDs/markers/masks/truncation/offset mapping on Unicode/quotes/code-mix fixtures.
2. Float graph parity: compare Python/eager reference, desktop graph and ARM64 graph logits/labels/probabilities; numerical tolerance chosen/documented from validation, not arbitrary claim of exactness.
3. Quantized parity and labeled quality: per-language/rare-label drift, confidence threshold crossings, omissions and abstention; recalibrate. Don't permit INT8 promotion just for smaller bytes.
4. Model safety: user-review policy, unsupported language/extraction, high-confidence negatives, contextual signals and new unseen categories.
5. Resource/privacy acceptance: no OOM/UI blocking/network evidence, bounded queues/native resource close; phone-specific p95/energy budgets.
6. End-to-end cascade versus simpler baseline on same held-out set. Promotion only conditional quality gain at acceptable review/cost tradeoff.

Initial design goals, not measured promises: compact text p95 under250ms on selected midrange at128tokens; AI worker incremental PSS budget under300MiB for narrow classifier path; optional foreground reasoning cancellable with finite output budget, unloaded outside user session. Adjust goals after measurements, never claim them as achieved. Threat-recall/false-positive tolerances need user-safety/expert input; hackathon fixtures cannot establish them. [R8]

## 20. Phased implementation and defensible differentiation

Effort below is relative engineering scope, not calendar estimate.

|Phase|Models/dependencies/Android components|Effort|Expected demonstration|Risks/gate|
|---|---|---|---|---|
|1 Minimal local signals|Supported import,encrypted original,ML Kit Latin or native OCR,short whisper tiny,ORT toxicity;optional emotion;Compose review|Medium|Airplane-mode selected screenshot/text ->narrow signals ->source correction/save|No universal harassment claim; assets/tokenizer/extraction/security integration|
|2 Ambiguous cases|One Qwen0.6B LiteRT-LM branch; Laya only separate research arm|High|User requests interpretation ->bounded context/unknowns ->review|Memory,startup,hallucination/refusal,anchor/cancellation validation|
|3 Retrieval+patterns|Adjacent context,MiniLM/E5 if needed,encrypted vectors,pure Kotlin PatternEngine|Medium-high|Find actual prior confirmed events,count distinct observations|Similarity not duplicate proof;coverage/truncation errors|
|4 Trajectory/explanation|Optional emotion head,reviewed trajectory,deterministic escalation trace,templates|Medium for traces,high for validated intensity|Same message changes with authorized context;counts+anchors,not risk number|No negative-emotion=harassment;intensity stays null until trained|
|5 Indic/code-mix/personalization|MuRIL/Indic teacher+small student,fitting data,native tokenizer packs,local corrections|Very high data/eval effort|Per-language support badges,unsupported abstention,code-mix cases|Sparse slices,cultural bias,privacy/drift;not hackathon-wide guarantee|

Essential timeline/counting can be shown in Phase1; later phases improve context/categorization, not basic ability to preserve evidence. Models don't delay secure acquisition/review.

### Differentiation hypotheses, not invention claims

|Existing work|Gap to verify|Sakshi mechanism|Android/demo|
|---|---|---|---|
|Temporal HAN/graph cyberbullying,notification accumulation|Coverage-aware survivor-owned offline traces under partial visibility|Observed counts+capture gaps+reviewed transitions,rate abstention|Cheap Kotlin; replay same incidents with missed observations and show uncertainty|
|Ensembles/selective classification/routing|Joint audit of safety errors and on-phone cost for evidence use|Same-task disagreement+abstention,logged route reasons/model identity|Bounded extra inference;show disagreement ->review,not fused certainty|
|Local RAG/embedding retrieval|Evidence-role/derivative-version constraints,not general chat memory|Same-case confirmed retrieval+exact quote/ID validation|Small index; ambiguous location sentence with two different supplied contexts|
|Emotion/sentiment classifiers|Avoiding survivor-negative-affect confound in longitudinal conduct interpretation|Separate expressed emotion and behaviour,role-aware trajectory|Compact heads; angry benign complaint vs polite restrictive contact|
|Multilingual/code-mix NLP and personalization|Measured low-resource abstention instead of universal support marketing|Language-specific calibration/support states,user corrections under private policy|Gradual packs;Malayalam preserved even when English AI refuses classification|

Combining established techniques is not automatically novel. Strongest defensible differentiator: **capture-aware, evidence-linked local pattern review that exposes missing context and abstains rather than inventing certainty**. Prior-art gap and user benefit remain hypotheses needing broader literature/product comparison and consenting specialist/user validation.

## 21. Final engineering blueprint A-I

**A. Model stack:** bundled ML Kit Latin/Devanagari for pragmatic OCR with disclosed SDK metrics; Tesseract5+tessdata_fast mal+eng for tested Malayalam/native zero-network option. Whisper tiny multilingual (`ggml-tiny.bin`), short imported audio; base only if quality/resources justify, not .en for Indic. Narrow minuva toxicity+optional GoEmotions baselines, production domain student from MiniLMv2-L6-H384 with multi-label/optional emotion/sentiment heads. Three-way Cardiff sentiment as research teacher/reference, not mandatory resident model. English all-MiniLM-L6-v2 retrieval; E5-small for validated multilingual retrieval later. Optional pinned Qwen3-0.6B LiteRT export reasoning,1.7B GGUF comparison if needed. **No default Laya.**

**B. Runtimes:** ORT Android CPU for compact models/embeddings; native tokenizer JNI; ML Kit Kotlin or Tesseract NDK; whisper.cpp JNI for STT; LiteRT-LM Kotlin for one optional generative model. llama.cpp NDK/JNI alternative, not simultaneous mandatory engine. No Python runtime/server/cloud inference.

**C. Android:** acquisition services/pickers ->bounded background extraction/model adapter ->domain router+PatternEngine ->encrypted repository ->Compose review. Heavy sessions leased sequentially off main thread; notification callback never loads model. No automatic screen/private-app access.

**D. Router:** validity/language before classifier; rules/user concern survive low toxicity; context/retrieval for context-sensitive/truncated/uncertain cases; optional foreground LLM with budget; any unsupported/error/unresolved ->human. No emotion filter/no confidence-only benign gate.

**E. Flow:** selected immutable bytes ->hash/encrypted original ->separate OCR/STT/source maps ->eligible narrow signals ->optional same-case retrieval/interpretation ->anchor validation ->pending user review ->confirmed evidence ->deterministic pattern snapshot ->explicit redacted export. Passive notification review retention is distinct from consented imports.

**F. Persistence:** originals/provenance+hash,versioned derivatives/corrections,model/schema/calibration/routing records,review decisions,confirmed event patterns,optional encrypted retrieval vectors,export manifests. No unbounded ordinary notification archive,no plaintext logs,no silent remote copy.

**G. Benchmark:** real ARM64 midrange/mainstream/flagship; pinned identical artifacts/data; float/INT8/backend/tokenizer parity,per-language rare-category quality,whole-pipeline PSS/latency/energy/thermal/privacy and cascade ablation. Linux smoke checks and publisher benchmarks are not phone validation.

**H. MVP:** text/image import,one bounded short-audio lane,encrypted original integrity metadata,narrow English toxicity+optional emotion,unknown/support states,manual correction,timeline/repetition trace,user-reviewed export. LLM/embeddings/intensity/all-language promises are optional or deferred; a working vault/review system is useful without them.

**I. Differentiator:** grounded local observed-pattern review with role/coverage/context uncertainty and human correction,not “hallucination-free AI” or legal conclusions.

**Bottom line:** implement the compact cascade first and defer Laya, as the user confirmed during review. Its port/fine-tuning experiments are future research only, not current engineering tasks. No inspected evidence justifies Laya-alone or raw-confidence routing as the primary Sakshi safety engine.

## 22. Primary source register and remaining uncertainties

Accessed 2 October 2026. Code URLs below pin Laya to the inspected commit. Model cards/runtime docs not explicitly frozen remain publisher pages as accessed; production must pin artifacts/builds independently. Published results are not independently reproduced unless labeled local experiment.

### Laya source, models, training and evaluation

- **L0:** [frozen repository](https://github.com/NandhaKishorM/laya/tree/4aa6761be8173de4ce6d92c31b3e40b6eaf59a7c).
- **L1:** [model, formatting, reward and confidence source](https://github.com/NandhaKishorM/laya/blob/4aa6761be8173de4ce6d92c31b3e40b6eaf59a7c/laya/common.py), especially75-247,472-630,664-727.
- **L2:** [loading, decode and batching](https://github.com/NandhaKishorM/laya/blob/4aa6761be8173de4ce6d92c31b3e40b6eaf59a7c/laya/agent.py), especially537-734,1180-1246,1250-1515; [Router](https://github.com/NandhaKishorM/laya/blob/4aa6761be8173de4ce6d92c31b3e40b6eaf59a7c/laya/router.py); [language heuristics](https://github.com/NandhaKishorM/laya/blob/4aa6761be8173de4ce6d92c31b3e40b6eaf59a7c/laya/lang.py).
- **L3:** [English pinned checkpoint](https://huggingface.co/convaiinnovations/laya/tree/55cf4c4ebb4ebe31b2550e8bdf3bd21b99753851), card,rl_agent_config,encoder/tokenizer configs and tokenizer.json; HF CLI metadata counted parameters/file sizes.
- **L4:** [multilingual pinned checkpoint](https://huggingface.co/convaiinnovations/laya-multilingual/tree/e4e9ddf21a7b1903b7acffd8814ad4307bf63a67); [typed-decisions card](https://huggingface.co/convaiinnovations/laya-typed-decisions); [ModernBERT card](https://huggingface.co/answerdotai/ModernBERT-large); [mmBERT card](https://huggingface.co/jhu-clsp/mmBERT-base).
- **L5:** [structured source](https://github.com/NandhaKishorM/laya/blob/4aa6761be8173de4ce6d92c31b3e40b6eaf59a7c/laya/structured.py); [schema guide](https://github.com/NandhaKishorM/laya/blob/4aa6761be8173de4ce6d92c31b3e40b6eaf59a7c/docs/structured.md); [confidence gate](https://github.com/NandhaKishorM/laya/blob/4aa6761be8173de4ce6d92c31b3e40b6eaf59a7c/laya/confidence.py); [calibration](https://github.com/NandhaKishorM/laya/blob/4aa6761be8173de4ce6d92c31b3e40b6eaf59a7c/laya/calibrate.py).
- **L6:** [evals source](https://github.com/NandhaKishorM/laya/blob/4aa6761be8173de4ce6d92c31b3e40b6eaf59a7c/laya/evals.py); [CLI](https://github.com/NandhaKishorM/laya/blob/4aa6761be8173de4ce6d92c31b3e40b6eaf59a7c/laya/evals_cli.py); [harness docs](https://github.com/NandhaKishorM/laya/blob/4aa6761be8173de4ce6d92c31b3e40b6eaf59a7c/docs/evals.md); [NaN issue830](https://github.com/NandhaKishorM/laya/issues/830).
- **L7:** [benchmarks and refreshed language table](https://github.com/NandhaKishorM/laya/blob/4aa6761be8173de4ce6d92c31b3e40b6eaf59a7c/BENCHMARKS.md); [Applications JSON](https://github.com/NandhaKishorM/laya/blob/4aa6761be8173de4ce6d92c31b3e40b6eaf59a7c/research/results/app_benchmark_results.json); [sampling/prompt source](https://github.com/NandhaKishorM/laya/blob/4aa6761be8173de4ce6d92c31b3e40b6eaf59a7c/research/scripts/bench_apps.py); [long context test](https://github.com/NandhaKishorM/laya/blob/4aa6761be8173de4ce6d92c31b3e40b6eaf59a7c/research/scripts/bench_long_context.py).
- **L8:** [complete fine-tuning notebook](https://github.com/NandhaKishorM/laya/blob/4aa6761be8173de4ce6d92c31b3e40b6eaf59a7c/notebooks/laya_finetune_typed_decisions_2xT4_kaggle.ipynb); [guide](https://github.com/NandhaKishorM/laya/blob/4aa6761be8173de4ce6d92c31b3e40b6eaf59a7c/docs/finetune.md); [synthetic dataset card](https://huggingface.co/datasets/LocalLLaMA/typed-decisions).
- **L9:** [RLCD vs soft-CE ablation741](https://github.com/NandhaKishorM/laya/issues/741); [option-count confidence394](https://github.com/NandhaKishorM/laya/issues/394). Contributor reports, not replicated here.
- **L10:** [export source](https://github.com/NandhaKishorM/laya/blob/4aa6761be8173de4ce6d92c31b3e40b6eaf59a7c/scripts/export_onnx.py); [ONNX runtime adapter](https://github.com/NandhaKishorM/laya/blob/4aa6761be8173de4ce6d92c31b3e40b6eaf59a7c/laya/onnx_agent.py); [GPU fast source](https://github.com/NandhaKishorM/laya/blob/4aa6761be8173de4ce6d92c31b3e40b6eaf59a7c/laya/fast.py).
- **L11:** [quantization issue790 and maintainer response](https://github.com/NandhaKishorM/laya/issues/790), includes conflicting root-cause/exporter interpretations; new default still lossy.
- **L12:** [dependencies](https://github.com/NandhaKishorM/laya/blob/4aa6761be8173de4ce6d92c31b3e40b6eaf59a7c/pyproject.toml); [license](https://github.com/NandhaKishorM/laya/blob/4aa6761be8173de4ce6d92c31b3e40b6eaf59a7c/LICENSE); [presets](https://github.com/NandhaKishorM/laya/blob/4aa6761be8173de4ce6d92c31b3e40b6eaf59a7c/laya/presets.py).

### Model alternatives and extraction

- **M1:** [minuva toxicity ONNX card/config](https://huggingface.co/minuva/MiniLMv2-toxic-jigsaw-onnx/tree/c035f27b6a6d68770f8069a4829f1715a48b8d51); [float classifier config](https://huggingface.co/minuva/MiniLMv2-toxic-jigsaw/blob/main/config.json). Existing6-label sigmoid model,not broad harassment.
- **M2:** [minuva emotion ONNX](https://huggingface.co/minuva/MiniLMv2-goemotions-v2-onnx/tree/4fea72b9ec71ba8d84b88e0efa2ace3dcc733bfc); [float card](https://huggingface.co/minuva/MiniLMv2-goemotions-v2).
- **M3:** [23M toxicity classifier, poor rare-threat metrics](https://huggingface.co/navodPeiris/minilm-toxic-classifier).
- **M4:** [DistilBERT SST-2](https://huggingface.co/distilbert/distilbert-base-uncased-finetuned-sst-2-english); [DistilBERT paper](https://arxiv.org/abs/1910.01108); binary/reference only.
- **M5:** [MobileBERT paper](https://aclanthology.org/2020.acl-main.195/); [real Android transformer study](https://arxiv.org/html/2306.11426v1), hardware/quantization/task-specific.
- **M6:** [ALBERT paper](https://arxiv.org/abs/1909.11942); [DeBERTa-v3-small](https://huggingface.co/microsoft/deberta-v3-small); [mBERT](https://huggingface.co/google-bert/bert-base-multilingual-cased).
- **M7:** [MuRIL card and native/transliterated evaluations](https://huggingface.co/google/muril-base-cased); [AI4Bharat IndicBERT repository](https://github.com/AI4Bharat/IndicBERT),v2 counts/licensing declarations.
- **M8:** [mmBERT architecture,small/base and tokenizer lineage](https://huggingface.co/jhu-clsp/mmBERT-base); [TinyBERT variant example](https://huggingface.co/Intel/dynamic_tinybert),QA-specific,not harassment checkpoint.
- **M9:** [all-MiniLM-L6-v2 pinned](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/tree/1110a243fdf4706b3f48f1d95db1a4f5529b4d41),pooling/normalization/context.
- **M10:** [paraphrase multilingual MiniLM](https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2),actual language list/128-token architecture.
- **M11:** [multilingual E5-small pinned](https://huggingface.co/intfloat/multilingual-e5-small/tree/614241f622f53c4eeff9890bdc4f31cfecc418b3),384D/prefix/language caveats; [authors' E5 repository](https://github.com/microsoft/unilm/tree/master/e5).
- **M12:** [Qwen3-1.7B card](https://huggingface.co/Qwen/Qwen3-1.7B); [official0.6B GGUF](https://huggingface.co/Qwen/Qwen3-0.6B-GGUF); [official1.7B GGUF](https://huggingface.co/Qwen/Qwen3-1.7B-GGUF),official listed artifacts Q8_0,not assumed Q4.
- **M13:** [Qwen3-0.6B LiteRT community pinned](https://huggingface.co/litert-community/Qwen3-0.6B/tree/a3c5d805ae362dff7f580bc25f2dfb9a5a7eaa76),exact artifact distinctions/device-CLI disclosure.
- **M14:** [Gemma3 model card](https://ai.google.dev/gemma/docs/core/model_card_3); [model input/platform distinctions](https://ai.google.dev/gemma/docs/get_started); [Google mobile measurements](https://developers.googleblog.com/en/gemma-3-on-mobile-and-web-with-google-ai-edge/); [LiteRT Gemma card](https://huggingface.co/litert-community/Gemma3-1B-IT),direct raw access401,gated,indexed publisher extracts only; no license acceptance/download here.
- **M15:** [current LiteRT-LM overview/benchmark table](https://developers.google.com/edge/litert-lm/overview),Gemma3n/Gemma4 device-specific entries,not proof for selected Qwen artifact.
- **M16:** [Qwen3.5-2B](https://huggingface.co/Qwen/Qwen3.5-2B),hybrid text/vision graph,model usage/size caveats.
- **M17:** [Cardiff three-way sentiment](https://huggingface.co/cardiffnlp/twitter-roberta-base-sentiment-latest); [whisper.cpp Android sample](https://github.com/ggml-org/whisper.cpp/tree/master/examples/whisper.android); [runtime/formats/resource table](https://github.com/ggml-org/whisper.cpp); [Tesseract fast packs](https://github.com/tesseract-ocr/tessdata_fast); [ML Kit OCR Android](https://developers.google.com/ml-kit/vision/text-recognition/v2/android); [ML Kit terms/privacy](https://developers.google.com/ml-kit/terms).

### Android runtime and benchmark contracts

- **R1:** [ORT mobile deployment](https://onnxruntime.ai/docs/tutorials/mobile/); [Java API/session ownership](https://onnxruntime.ai/docs/get-started/with-java.html).
- **R2:** [NNAPI migration/deprecation](https://developer.android.com/ndk/guides/neuralnetworks/migration-guide); [ORT NNAPI operator constraints](https://onnxruntime.ai/docs/execution-providers/NNAPI-ExecutionProvider.html).
- **R3:** [LiteRT Android APIs](https://ai.google.dev/edge/litert/android),CompiledModel/Interpreter/model compatibility distinctions.
- **R4:** [LiteRT-LM Kotlin Android guide](https://developers.google.com/edge/litert-lm/android); [Kotlin getting-started source](https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md),session lifecycle and initialize warning.
- **R5:** [Chaquopy current FAQ](https://chaquo.com/chaquopy/doc/current/faq.html); [Torch version history](https://github.com/chaquo/chaquopy/issues/1215); [tokenizers/Transformers history](https://github.com/chaquo/chaquopy/issues/607),historical issues not current-wheel proof.
- **R6:** [ExecuTorch Android](https://docs.pytorch.org/executorch/stable/using-executorch-android.html); [llama.cpp official Android guide](https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md),NDK/Bionic/Kotlin flows.
- **R7:** [MediaPipe TextEmbedder Android](https://ai.google.dev/edge/mediapipe/solutions/text/text_embedder/android),compatible model/metadata requirement.
- **R8:** [Macrobenchmark metrics](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-metrics); [PowerMetric API/support check](https://developer.android.com/reference/androidx/benchmark/macro/PowerMetric); [Macrobenchmark overview](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview).

### Calibration, datasets and prior work

- **S1:** [Guo et al.,On Calibration of Modern Neural Networks](https://proceedings.mlr.press/v70/guo17a.html),primary indexed abstract consulted; direct fetch timed out. Temperature scaling is empirical/task-dependent.
- **S2:** [Angelopoulos/Bates conformal introduction](https://arxiv.org/abs/2107.07511); [full text](https://arxiv.org/html/2107.07511v6),exchangeability/marginal guarantees and limitations.
- **S3:** [Online Selective Conformal Prediction: Errors and Solutions](https://arxiv.org/html/2503.16809v1),selection can break exchangeability,not blanket online guarantees.
- **D1:** [TFDS Wikipedia toxicity source](https://github.com/tensorflow/datasets/blob/master/tensorflow_datasets/text/wikipedia_toxicity_subtypes.py); [Civil Comments](https://www.tensorflow.org/datasets/catalog/civil_comments); [publisher Civil Comments card](https://huggingface.co/datasets/google/civil_comments),CC0 release statements.
- **D2:** [Google GoEmotions repository](https://github.com/google-research/google-research/tree/master/goemotions); [paper](https://aclanthology.org/2020.acl-main.372/); [dataset card](https://huggingface.co/datasets/google-research-datasets/go_emotions).
- **D3:** [HateXplain card](https://huggingface.co/datasets/Hate-speech-CNERG/hatexplain),metadata/prose license conflict; [author repository](https://github.com/hate-alert/HateXplain); [ToxicChat](https://huggingface.co/datasets/lmsys/toxic-chat),NC license and collection limits.
- **D4:** [SemEval2018 Affect in Tweets paper](https://aclanthology.org/S18-1001/); [dataset HF configuration](https://huggingface.co/datasets/SemEvalWorkshop/sem_eval_2018_task_1),intensity task versus exposed classification columns.
- **D5:** [original DravidianCodeMix Zenodo release](https://zenodo.org/records/4750858),CC-BY4.0; [authors' repo](https://github.com/bharathichezhiyan/DravidianCodeMix-Dataset); [paper](https://link.springer.com/article/10.1007/s10579-022-09583-7).
- **D6:** [HASOC2021 task](https://hasocfire.github.io/hasoc/2021/call_for_participation.html); [ICHCL context](https://hasocfire.github.io/hasoc/2021/ichcl/index.html); [2022 contextual Hinglish labels](https://hasocfire.github.io/hasoc/2022/ichcl.html); [2024 English/Bangla task](https://hasocfire.github.io/hasoc/2024/dataset.html). Exact release licenses unresolved.
- **D7:** [HateCheck paper](https://aclanthology.org/2021.acl-long.4/),29 diagnostic functionalities,constructed examples not prevalence dataset.
- **D8:** [temporal hierarchical-attention cyberbullying research](https://par.nsf.gov/biblio/10301308-modeling-temporal-patterns-cyberbullying-detection-hierarchical-attention-networks); [author manuscript](https://ysilva.cs.luc.edu/BullyBlocker/documents/modelingtemppatternscbd.pdf); [HENIN graph/context paper](https://aclanthology.org/2020.emnlp-main.200/). Primary abstract/indexed source evidence,not Android implementation benchmark.
- **D9:** [fastText LID models/licensing](https://fasttext.cc/docs/en/language-identification.html); typed-decisions/Indic datasets also linked in L8/M7.

### Open questions and explicit limits

1. Actual Laya FP32/quantized graph operation/tokenizer compatibility and memory on target ARM64 Android builds; no inference run here.
2. Current complete Chaquopy dependency build feasibility; not inferred from old issue states.
3. Per-language/code-mixed/category clinical/user-safety quality for any recommended candidate; no representative Sakshi dataset collected/evaluated.
4. Full Laya original training provenance,tokenizer upstream terms,and unresolved dataset license conflicts.
5. Whether an LLM improves hard-case quality enough to offset load/RAM/energy/review costs; matched conditional evaluation needed.
6. Capture availability,foreground/background,key-access and backup behavior across actual devices; previous property inspections aren't workload validation.
7. Explainability faithfulness and user benefit; validated anchors don't establish interpretation truth.

Completed deliverable checks: both JSON/Python examples parsed; all50 source IDs/ranges resolve in the register;22 report sections present; Markdown whitespace checked;86 HTML IDs unique and local links resolve; Chromium tests passed at1440px/390px in dark/light themes, including filter/theme/details/anchors, no page/SVG text overflow, no page exceptions and no remote asset requests. Screenshots reviewed. The stalled Playwright dependency download was stopped and replaced with native Chromium DevTools checks. These checks do not certify Kotlin pseudocode,model exports,Android runtimes,legal interpretation or safety performance. No commits/pushes/public sharing performed by this investigation. User-ended visual review was not reopened.
