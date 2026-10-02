# Sakshi: evidence-backed local AI architecture for Android

**Research date:** 2 October 2026

**Status:** Research and implementation recommendation. No Sakshi model, Android inference, harassment evaluation, or battery benchmark was executed in this investigation. Published measurements belong to their cited authors, not to Sakshi.

**Decision:** Use specialist extraction, rules, one calibrated multilingual encoder, a deterministic temporal engine, and one optional foreground LLM. Add semantic retrieval and a second decision model only if ablations demonstrate value. There is enough evidence to choose an architecture and a first benchmark shortlist, but not to declare a universally optimal model.

## 1. Executive recommendation

```text
Opted-in notification observation OR user-selected evidence
  -> provenance, bounded parsing, repost/summary handling
  -> image OCR / audio STT / video frames + STT, only when actual media exists
  -> rules + one calibrated multi-label text encoder
  -> transient ordinary observation / encrypted candidate review / unknown
  -> temporal engine over consented, appropriately retained events
  -> optional bounded context retrieval
  -> one local LLM only for ambiguity, contextual review, explanation, summary
  -> validate evidence anchors + human confirmation
  -> encrypted confirmed evidence + explicit reviewed export
```

The temporal branch runs **before the benign-stop decision** for whatever metadata the user has explicitly authorized retaining. An individually ordinary message can contribute to repeated unwanted contact. Do not silently store all ordinary messages to support future AI.

### Initial stack

| Role | Initial candidate | Reason | Release condition |
|---|---|---|---|
| Cheap rules | Kotlin parsing, exact protected indicators, bounded patterns | No model allocation; transparent PII/contact indicators and dedup logic | Test false positives and notification reposts; rules do not determine guilt |
| Routine triage | Fine-tuned `microsoft/Multilingual-MiniLM-L12-H384` encoder with multi-label head, approximately 117M including embeddings | Smaller than Laya; multilingual pretraining; explicit fine-tuning path; CPU deployment candidate [C1] | Exact tokenizer/export parity, held-out calibration, per-language recall, Android latency and memory |
| Decision-model challenger | Laya multilingual, 322M, **not an obligatory extra stage** | Typed decisions in a single forward pass; can answer policy questions without generated JSON [L1] | Domain fine-tuning, calibration, FP32/FP16/export parity and phone profiling |
| CPU-first optional LLM | **Qwen3.5-2B text decoder, Q4_K_M**, non-thinking, 2K-4K deployed context | Apache-2.0; broad publisher language coverage; an Arm phone benchmark documents latency, memory and a quantization accuracy tradeoff [Q3][B3] | Compare with encoder-only; demonstrate better contextual accuracy and grounded summaries |
| Accelerated challenger | **Gemma 4 E2B instruction-tuned mobile bundle via LiteRT-LM**, text-first | Published CPU/GPU phone results; mobile mixed quantization; Kotlin APIs; Apache-2.0 [G1][B1][R1] | Verify app total memory, cold starts, refusal behavior, multilingual quality and integration |
| Compatibility fallback | Qwen2.5-1.5B-Instruct, Q4/Q5 GGUF or tested LiteRT bundle | Mature text architecture, non-thinking, published Android CPU/GPU rates, small GQA KV cache [Q1][B0] | Only ship if task scores justify it; do not assume older means less reliable |
| Optional retrieval | Start with case/time/sender filters and lexical search; compare multilingual-E5-small INT8 later | Avoid extra model initially; E5 is 117.7M with 384-D vectors and 512-token passages [E1] | Retrieval improves context recall without crossing case boundaries or harming energy/privacy |
| Media extraction | Bundled ML Kit Latin/Devanagari OCR; selected Tesseract Indic packs; whisper.cpp multilingual tiny/base | Reuse the existing multimodal design, preserving spatial/temporal source links | Validate per language; Malayalam is not supported by the selected ML Kit recognizers |

A pretrained encoder is **not already a harassment detector**. If a suitable labeled, licensed dataset and calibrated head are not available, ship manual review and rule-generated candidates rather than claiming automated safety coverage. Laya also needs domain validation; it is not a pretrained Sakshi-specific detector.

**Deployment policy:** classifier/temporal/template features remain usable without any LLM. Do not bundle several LLMs by default. Offer one user-selected, validated model pack per device tier. No cloud fallback, automatic message sending, legal conclusions, or always-on high-compute inference.

## 2. Method and evidence strength

Primary model cards/configs, official Android/runtime documentation, and model/runtime publisher measurements were used. Public Hugging Face metadata was checked read-only through `hf models info` and Hub API/config requests. No weights were downloaded; gated configurations were not accessed or bypassed. No device was accessed in this session.

Labels throughout this report:

- **Published measurement:** a numerical result with the source device/backend and conditions retained.
- **Verified metadata:** parameter/context/license/API information, not proof of task performance.
- **Engineering estimate:** derived arithmetic or a planning envelope, not measured RAM or latency.
- **Proposal:** a policy, routing rule, acceptance target or benchmark plan.
- **Unknown:** no directly applicable evidence found in the sources reviewed.

No reviewed source provides a complete comparison of these models on Sakshi harassment labels, all target languages, notification acquisition, sustained energy and integrated Android app memory. Therefore all recommendations are conditional on the validation in section 12. Desktop throughput, generic leaderboards and cloud cascade savings are not Android safety benchmarks.

The workspace's previous multimodal report records an earlier read-only check of an SM-S928B / SM8650 phone. That historical observation is not a benchmark and was not repeated here. A notification-intelligence report mentioned in session summaries was not present when initially read, so it was not treated as verified local content. This design preserves the stated transient ordinary / encrypted candidate / confirmed-vault retention boundary independently.

## 3. Compare all eight architectures

These are overlapping design dimensions, not mutually exclusive products. The recommended composition combines rules, classifier, routing, temporal context and specialist media extraction.

| Architecture | Best use | Latency / battery implications | RAM implications | Accuracy and evidence risks | Sakshi decision |
|---|---|---|---|---|---|
| Single LLM | Foreground contextual analysis and prose | Prefill plus generated tokens on every item; cold loads and bursts expensive | LLM weights + KV + scratch resident or repeatedly loaded | Uncalibrated self-confidence; omissions/refusals; prompt injection; poor counting; hallucinated explanations | Baseline for experiments, not notification default |
| Small classifier only | Known labels on short text | One encoder pass; no autoregressive output; actual benefit must be measured | Smallest practical neural text stack; tokenizer/vocabulary still matter | Limited context/novel categories; no fluent summary; can confidently miss euphemism | Best minimal production core with rules, temporal logic and templates |
| Classifier + LLM | Cheap labeling plus occasional contextual generation | Meaningful average savings if LLM route fraction is low | Average residency can fall; peak may be additive | Early false negatives never reach LLM; LLM can be worse on routed subset | Recommended optional generation extension |
| Rule engine + classifier + LLM | Dedup, deterministic indicators, calibrated triage and prose | Rules avoid model work on non-content and duplicates; urgent/context rules bypass early rejection | Small rules overhead; encoder + one loaded LLM dominate | Keyword-only benign stop is unsafe; quoted threats and consensual content need context | Recommended core composition |
| Embedding retrieval + classifier + LLM | Evidence-linked context across a larger retained case | Passage embedding/index upkeep costs energy; shorter LLM prompts can save prefill | Additional encoder/index; vectors and text need encryption | Similarity is not truth, sender identity or complete temporal history; semantic top-k can omit negation/boundaries | Conditional upgrade after lexical/time retrieval baseline |
| Model routing/cascade | Choose computation by uncertainty, task and resource state | Lower average cost possible; sequential escalated path slower; cold swapping can erase gains | Only lowers peak if stages unload or share resources; several hot models increase it | Confidently wrong gates, compounded recall loss, language misrouting and correlated errors | Recommended routing, but start with two neural tiers, not a long chain |
| Multimodal model | Selected-image scene context or integrated speech/image interaction | Encoders, image/audio tokens and generation increase cost | Extra modality weights, activations and preprocessing allocations | Not exact OCR/STT; sampled video misses events; no pixels/audio from a notification label | Experimental selected-evidence aid, not primary extraction |
| Specialist models for different tasks | OCR, STT, text classification, retrieval | Load only needed specialists; serialize heavy work | Peak bounded by scheduling rather than loading all specialists | More conversion/maintenance paths, but measurable outputs and source anchors | Recommended; share one text encoder across labels, not one model per label |

**Why not an LLM per harassment category?** Threat, intimidation, control and sexual harassment can overlap. A shared encoder with independent label scores is cheaper and avoids contradictory specialized binary decisions. Temporal repetition is a different computation, not merely another text class.

## 4. Map the ten tasks to computation

| Task | Primary computation | Optional context / LLM role | Required evidence and uncertainty |
|---|---|---|---|
| Harassment classification | Multi-label calibrated encoder, separate benign/unknown handling | Interpret ambiguous context, not silently override user review | Exact excerpt, source id, label definitions, quote attribution; toxicity is not synonymous with harassment |
| Threat detection | High-recall encoder plus deterministic explicit-threat indicators | Conditionality, target/action/time, quotation, sarcasm and context | Source span; observed wording versus inferred intention; inability to establish real-world capability |
| Controlling behaviour | Encoder on bounded conversation windows plus reviewed boundary/relationship context | Explain possible restrictions, coercion or monitoring | Do not infer coercion from one consensual request; preserve uncertainty and prior consent/boundary evidence |
| Sexual harassment | Multi-label encoder; explicit content detection only an input | Unwantedness, targeting, repeated requests and coercion | Adult consensual discussion, medical text and abuse reporting are hard negatives; graphic evidence may trigger LLM refusals |
| Intimidation | Shared encoder and contextual features | Distinguish implied consequence, status/power assertion and ordinary disagreement | Exact wording; avoid asserting guilt, intent or power not established in evidence |
| Doxxing indicators | Bounded regex/entity candidates for address/phone/location plus contextual classifier | Distinguish private data exposure/threat from normal contact information | Private-data appearance alone is not doxxing; ownership, consent and actual dissemination may be unknown |
| Repeated contact | Deterministic event-time counters over reviewed associations, repost-aware dedup | Summarize the counts and boundary evidence | Distinct observed contacts, window, gaps, uncertain identity, explicit unwanted-contact marker; not complete message history |
| Escalation | Temporal transitions/frequency changes across reviewed categories and evidence | Describe changes, not predict violence from an invented risk score | Multiple event ids, previous/current windows, coverage gaps; no universal severity ordering |
| Explanation | Templates from scores, rules and verified source spans | Brief bounded evidence-linked paraphrase for ambiguous cases | One or more validated evidence ids for every claim; not model chain-of-thought or post-hoc rationale presented as fact |
| Summarization | Structured/extractive timeline first, then optional LLM | Readable summary of selected reviewed evidence | Dates/counts rendered from structured data; checked citations, omissions and review state; no automatic legal conclusions |

Doxxing patterns must not fetch, enrich, verify or search for the exposed identity/location. Only analyze the evidence the user selected or the authorized notification excerpt. No external lookup is needed.

### Temporal representation

An event records observation id, source kind, text/region/time anchors, source timestamp claim, device observation time, case id, tentative conversation/sender association, dedup confidence, category vector, extraction uncertainty and user review state. Normalized text is a derivative with an offset mapping to the preserved source.

Track counts and intervals, contact after a user-reviewed stop request, category co-occurrence, and transitions such as repeated sexual requests plus a later explicit threat. Report a pattern only if its source events exist. Identical wording can occur in two real messages; a content hash alone must not collapse them. Conversely notification updates/history/group summaries must not inflate counts.

Maintain separate observational and confirmed histories. Rejected/expired candidates leave the permitted history according to retention policy. User corrections and deletions invalidate derived patterns and retrieval indexes. Do not store ordinary plaintext text or reversible embeddings merely to improve future routing.

## 5. Model inventory: metadata, languages, licenses and tuning

Named size is the publisher's label. It is not necessarily total stored tensors or runtime working memory. Unless specified otherwise, rows refer to instruction/post-trained checkpoints, not base models.

### 5.1 Small model and fallback inventory

| Model / approximate band | Parameters | Advertised context, not phone allocation | Multilingual evidence and limitations | License | Fine-tuning support / Sakshi role |
|---|---|---|---|---|---|
| Qwen2.5-0.5B-Instruct / 0.5B-1B | 0.494B Hub total | 32,768 | Family reports 29+ languages; no reviewed Sakshi Indic results [Q1] | Apache-2.0 | Transformers/PEFT-compatible architecture; compact baseline, not sole safety gate |
| Qwen3-0.6B / 0.5B-1B | Card 0.6B; Hub stored tensor total 0.752B | Card 32,768; current config 40,960; deployed LiteRT files 2,048/4,096 [Q2][B2] | 100+ language family claim; no harassment/code-mixed validation | Apache-2.0 | SFT/LoRA on source checkpoint; disable thinking; small routing/extraction benchmark |
| Qwen3.5-0.8B / 0.5B-1B | Named decoder 0.8B; full Hub total 0.873B | 262,144 native | Family reports 201 languages/dialects; size card explicitly positions it for task tuning/prototyping [Q3] | Apache-2.0 | Fine-tuning candidate; text/vision runtime support checked separately; not full-context phone use |
| Gemma 3 1B IT / approximately 1B | 1.000B Hub total | 32K, text-only at this size | Family 140+ languages; generic Indic benchmarks exist, not Sakshi recall [G3] | Gemma terms, not Apache | Documented Gemma tuning/QLoRA; compact LiteRT challenger |
| Llama 3.2 1B Instruct / 1B-2B | 1.236B Hub total | 128K original; official quantized model variants use 8K [M1][M2] | Eight supported languages include Hindi, not Malayalam/Tamil/etc. | Llama 3.2 Community License + AUP | SFT/LoRA, official QAT/QLoRA variants and ExecuTorch mobile path; compatibility baseline |
| Qwen2.5-1.5B-Instruct / 1B-2B | 1.544B Hub total; card 1.54B | 32,768, generation up to 8,192 | 29+ family language coverage; favorable small KV layout does not prove Indic quality [Q1] | Apache-2.0 | SFT/LoRA; mature fallback for text summaries |
| Qwen3-1.7B / approximately 1B-2B | Card 1.7B; Hub total 2.032B | Card 32,768; config 40,960 | 100+ family languages; mode and tokenizer efficiency affect cost [Q2] | Apache-2.0 | SFT/LoRA; CPU/mobile measurement from Liquid's comparison, not independent Sakshi validation [B4] |
| SmolLM2-1.7B-Instruct / 1B-2B | 1.711B Hub total | 8,192 config | Primarily English; not a justified Indic default [S1] | Apache-2.0 | Open training/post-training code; SFT/LoRA; English-only baseline |
| LFM2-700M / 0.5B-1B | 0.742B | 32,768 | Eight-language family list does not include required Indic languages [F1] | LFM Open License v1.0 | Narrow-task tuning encouraged; hybrid convolution/attention; small CPU specialist challenger |
| LFM2.5-1.2B-Instruct / 1B-2B | 1.170B | 32,768 | English, Arabic, Chinese, French, German, Japanese, Korean, Spanish, not Indic [F2] | LFM v1.0 commercial revenue condition | Documented TRL/Unsloth SFT-LoRA/DPO; attractive measured English/mobile challenger, not multilingual default |
| MobileLLM-600M / 0.5B-1B | 603.1M family table | 2,048 family table [F3] | English research/base model; no reviewed Indic safety evidence | FAIR Noncommercial Research License | Research fine-tuning permitted within terms; requires task post-training and verified export; not production recommendation |
| MobileLLM-1B / approximately 1B | 1.01B family table | 2,048 family table [F3] | Same limitation; mobile specialization does not establish app compatibility | FAIR Noncommercial Research License | Research comparator; exclude from default commercial deployment |

### 5.2 Contextual generation and larger models

| Model / approximate band | Parameters | Advertised context | Multilingual evidence and limitations | License | Fine-tuning support / Sakshi role |
|---|---|---|---|---|---|
| Qwen3.5-2B / 2B-4B | Named text decoder 2B; full Hub total 2.274B | 262,144 | 201 languages/dialects family claim; no task-specific Indic scores [Q3] | Apache-2.0 | Task-specific SFT/LoRA; **first CPU-first contextual benchmark candidate** |
| Llama 3.2 3B Instruct / 2B-4B | 3.213B Hub total | 128K original; quantized variants 8K; Qualcomm export 4K | Eight supported languages including Hindi; exported card's English support is narrower [M1][B5] | Llama 3.2 Community License + AUP | SFT/LoRA, QAT/QLoRA/ExecuTorch; optional Qualcomm benchmark alternative |
| SmolLM3-3B / 2B-4B | 3.075B Hub total | 65,536 trained/configured; 128K through YaRN | Body lists six native European languages; Hub tags list eight including zh/ar/ru and omit de: unresolved metadata inconsistency [S2] | Apache-2.0 | Open training recipe; SFT/LoRA candidate; English summary baseline, not Indic default |
| Phi-4-mini-instruct / 2B-4B | 3.836B Hub total; card 3.8B | 128K | Published 24-language/tag coverage is not Indic coverage; no required-language harassment evidence [P1] | MIT | SFT/LoRA, official ONNX exports; text reasoning challenger, relatively slow published phone decode |
| Phi-4-mini-flash-reasoning / 2B-4B | 3.853B Hub total | Card/blog 64K; config 262,144 is not validation | English-focused math reasoning; hybrid SambaY architecture [P2] | MIT | Open training code/custom architecture; mobile export must be verified, not presumed; exclude default |
| Qwen3-4B / approximately 4B | 4.022B Hub total | Card 32K; config 40,960 | 100+ language family; no Sakshi measurements [Q2] | Apache-2.0 | SFT/LoRA; optional high-RAM comparison only |
| Qwen3.5-4B / approximately 4B | Named decoder 4B; full Hub total 4.660B | 262,144 | 201-language family claim; generic benchmark gains do not establish quantized phone task gains [Q3] | Apache-2.0 | SFT/LoRA; selected-evidence optional upgrade, text-first |
| Gemma 3 4B IT / approximately 4B | Full Hub total 4.300B | 128K | 140+ family languages; image + text; uneven multilingual quality [G3] | Gemma terms | Documented QLoRA/SFT, quantized variants; older VLM comparison |
| Gemma 3n E2B IT / effective 2B | Effective 2B; full Hub total 5.439B | 32K | 140+ training languages; text/image/audio/video input, not proof of accurate Indic transcription [G2] | Gemma terms | Gemma/Transformers tuning with architecture-specific export; legacy mobile challenger |
| Gemma 3n E4B IT / effective 4B | Effective 4B; full Hub total 7.850B | 32K | Same language/modality caveat; nested-model execution is runtime dependent | Gemma terms | Architecture-specific tuning; larger legacy mobile comparator |
| Gemma 4 E2B IT / effective 2B-4B | 2.3B effective, 5.1B including embeddings; base Hub total 5.123B | 128K source; mobile bundle supports up to 32K, tested at 2K [G1][B1] | 140+ family languages; image/audio encoders plus text | Apache-2.0 | Official HF/QLoRA tuning; **accelerated contextual challenger**, on-demand encoders |
| Gemma 4 E4B IT / effective 4B-8B | 4.5B effective, 8B including embeddings; base Hub total 7.996B | 128K source | Same caveat; more parameters do not establish better domain utility | Apache-2.0 | Official tuning; optional high-memory foreground model |
| Qwen3-8B / approximately 8B | 8.191B Hub total | Card 32K; config 40,960 | 100+ family languages, unvalidated Sakshi multilingual recall | Apache-2.0 | SFT/LoRA; offline development teacher/quality ceiling, not default phone pack |
| Llama 3.1 8B Instruct / 4B-8B | 8.030B Hub total | 128K | Eight supported languages including Hindi; no full Indic guarantee [M3] | Llama 3.1 Community License + AUP | SFT/LoRA; development baseline, foreground-only high-RAM experiment |

**Parameter-count conflicts:** Qwen3 small-model cards and Hub stored-tensor totals differ; Qwen3.5 full checkpoint totals also include components beyond the named decoder. Gemma effective sizes deliberately exclude large lookup embeddings. These are recorded rather than normalized into a fictitious exact number. Size the exact exported artifact and inspect its tensors before release. Gemma 4 base-repository totals above are metadata cross-checks, not exact counts claimed for every IT/mobile bundle.

**Context conflicts:** marketing, config, trained length, export maximum and benchmark allocation are separate. SmolLM3's extension needs YaRN; a larger Phi config is not evidence of tested 256K; Qwen3 card/config disagreement is not a promise of useful 40K phone inference. Start Sakshi at 2K, allow 4K only after profiling. Never allocate 128K/262K merely because the checkpoint permits it.

**License implications:** Apache/MIT still require notices and upstream attribution as applicable. Gemma 3/3n use Gemma terms, whereas verified Gemma 4 cards specify Apache-2.0. Llama includes attribution/AUP and commercial-scale conditions. LFM v1.0 defines a $10M annual-revenue threshold and commercial use above it is not licensed by that agreement [F4]. MobileLLM is research-only under the reviewed license. Review exact model, runtime and derivative licenses, not only Hub tags.

### 5.3 Structured output reliability: applies to every generative row

No reviewed candidate has a published **Sakshi-specific** schema-validity, evidence-citation or factuality rate. Tool-use and IFEval scores are not such rates. Qwen2.5 advertises improved JSON; Phi/LFM/Gemma document tool formatting; this is capability metadata, not reliability certification.

Use runtime-constrained decoding where available, supported schema subsets, explicit enum/length bounds, and independent validation. llama.cpp documents GBNF and JSON-schema conversion, including unsupported features that may be skipped [R3]. Verify the native Android binding actually exposes the required sampler/grammar controls; CLI availability alone is not binding support. LiteRT-LM documents constrained tool use [R1]; verify exact Kotlin/version support for the desired schema rather than equating tool syntax with general JSON Schema. Gemini Nano structured output is marked Alpha in reviewed ML Kit documentation [F5].

A complete constrained response can be syntactically valid and still wrong. Validate cited ids belong to the input case, quotes match real spans, offsets are valid, counts/dates are sourced, and no schema field silently converts missing data to false. Truncated/cancelled output is incomplete. At most one bounded repair attempt; otherwise use an extractive/template response and mark analysis unavailable. Do not execute model-proposed tools.

Use independent label scores or separate binary questions, not one mutually exclusive category. A multi-class softmax cannot represent threat + sexual harassment + controlling behaviour simultaneously. LLM self-reported confidence is not a calibrated probability.

## 6. Android compatibility, quantization and RAM per model

### 6.1 Shared runtime interpretation

- **A:** Android CPU path through a supported GGUF architecture and NDK/JNI; actual model/runtime build must pass a phone smoke test. Vulkan/OpenCL are candidate GPU backends, not universal acceleration promises [R2].
- **L:** A published LiteRT-LM Android artifact/measurement exists; source Safetensors cannot be loaded as that artifact [B0][R1].
- **N:** Exact vendor/NPU path is published. It is limited to the chipset, graph, format and runtime. Generic GGUF is not a QNN binary.
- **X:** Conversion/backend feasibility remains unverified here.
- **G:** GGUF options FP16, Q8_0, Q6_K, Q5_K_M, Q4_K_M and Q3_K_M can be considered where the converter supports that model. Availability is not a release recommendation. INT8 ONNX/LiteRT is a separate export.

llama.cpp's current official Snapdragon guide has CPU, Adreno OpenCL and **experimental Hexagon NPU** paths [R4]. Thus saying it has no Android NPU path would be outdated. Equally, saying any supported model will run efficiently on every NPU would be false. Hybrid recurrent ops, modality encoders and mixed quantization need backend-specific tests. Do not depend on deprecated NNAPI [R6]. ONNX Runtime QNN supports Android but requires the appropriate SDK/build and compatible quantized operators/graph; desktop QNN packages are not automatically Android AARs [R5].

### 6.2 Planning envelope and published exceptions

**Estimated Q4 text process envelope** below is an engineering planning range in decimal GB, not measured PSS, an installation minimum, or an Android memory guarantee. For ordinary dense text models it approximates exported weight memory at 0.55-0.70 bytes/parameter plus 0.4-1.0 GB for short-context KV, scratch, tokenizer and integration. Large MHA KV, modality components, duplicate GPU buffers and runtime-specific allocations can exceed it. It excludes the OS/other apps and simultaneous OCR/STT. Effective-parameter Gemma models use their published specialized figures instead of this formula.

| Model | CPU / Android path | GPU / NPU evidence | Quantization options | RAM evidence or estimated Q4 envelope | Phone latency evidence |
|---|---|---|---|---|---|
| Qwen2.5-0.5B | A, L | CPU published; GPU/NPU export dependent | G; LiteRT 521 MB artifact | Estimate 0.7-1.4 GB | B0: S24 Ultra CPU decode 30 tok/s |
| Qwen3-0.6B | A, L | LiteRT OpenCL; exact MediaTek a16w8 NPU export | G; dynamic INT8, mixed INT4, a16w8 | Estimate 0.8-1.6 GB is **not sufficient for all paths**; B2 reports 585-2895 MB private footprint | B2: CPU 8.33-12.90, GPU 22.34-69.38 tok/s on listed devices |
| Qwen3.5-0.8B | A | Vulkan/OpenCL/Hexagon coverage must be tested | G, keep sensitive recurrent tensors at supported precision | Estimate 0.9-1.6 GB, text only | Unknown matched phone measurement in reviewed sources |
| Gemma 3 1B | A, L | LiteRT CPU/GPU published; NPU export dependent | G; QAT INT4 variants; tested LiteRT format | Estimate 1.0-1.7 GB; B0 bundle 1005 MB is disk, not RAM | B0: S24 Ultra CPU 33 / GPU 24 tok/s |
| Llama 3.2 1B | A; official ExecuTorch path | Qualcomm/Hexagon path exists; exact backend dependent | G; official QAT/QLoRA INT4, 8-bit embeddings | Estimate 1.1-1.9 GB | R4 has example logs, but not a matched Sakshi benchmark |
| Qwen2.5-1.5B | A, L | LiteRT CPU/GPU published | G; tested LiteRT bundle | Estimate 1.3-2.1 GB | B0: S25 Ultra CPU 34 / GPU 31 tok/s |
| Qwen3-1.7B | A | Mobile CPU published; GPU/NPU needs exact export | G | Estimate 1.3-2.4 GB; B4 reports 1306 MB for Q4_0 test | B4: S25 Ultra CPU 40 tok/s, 181 prefill tok/s |
| SmolLM2-1.7B | A | GPU backend conditional; no matched NPU benchmark reviewed | G; ONNX exports also require verification | Estimate 1.6-2.6 GB with 2K FP16 KV; MHA cache expensive | Unknown matched phone measurement |
| LFM2-700M | A with compatible hybrid runtime | Publisher CPU/GPU/NPU capability, no matched variant measurement retained | G where hybrid kernels support it | Estimate 0.8-1.6 GB | Unknown matched variant measurement |
| LFM2.5-1.2B | A; official GGUF/ONNX variants | N: NexaML mobile NPU results; GPU conditional | G; published Q4_0 CPU; vendor artifact separately | B4: CPU 719 MB, mobile NPU 0.9 GB with unspecified accounting | B4: CPU 70 / mobile NPU 82 tok/s, different phones |
| MobileLLM-600M | X: verify converter/tokenizer and instruction tuning | No verified Android accelerator benchmark retained | FP16 source; INT8/Q4 research exports, not guaranteed G | Estimate 0.7-1.4 GB **if** fully quantized supported export | Unknown |
| MobileLLM-1B | X | No verified matched accelerator benchmark retained | Same caveat | Estimate 1.0-1.7 GB **if** supported export | Unknown |
| Qwen3.5-2B | A; Arm phone text-decoder result | GPU/NPU operator coverage conditional | G; published imatrix Q4_K_M | B3: peak 2523.97 MB, decoder file 1215.36 MB; estimate 1.5-2.6 GB is not universal | B3: 22.09 tok/s, TTFT 983.29 ms, p50 6.782 s for 128/128 |
| Llama 3.2 3B | A; official ExecuTorch | N: Qualcomm mixed w4a16/w8a16, target-specific | G; official QAT/QLoRA INT4; vendor export | Estimate 2.2-3.3 GB; B5 has no comparable total app RAM | B5: 28.03 tok/s, TTFT 0.077-2.454 s on 8 Elite For Galaxy w4a16 |
| SmolLM3-3B | A with supported current converter | GPU conditional; NPU compatibility unverified here | G, respecting architecture's attention layout | Estimate 2.1-3.2 GB | Unknown matched phone measurement |
| Phi-4-mini-instruct | A, L; official ONNX | LiteRT CPU/GPU published; QNN needs converted supported graph | G; INT4 ONNX and LiteRT artifacts | Estimate 2.5-3.7 GB; B0 disk bundle 3906 MB can exceed that resident weight estimate | B0: S24 Ultra CPU 7 / GPU 10 tok/s |
| Phi-4-mini-flash | X: custom SambaY model, not ordinary Phi-4-mini | Published A100 speedup does not establish phone acceleration | FP16/BF16; INT8/GGUF quantization only after actual converter support check | Ideal Q4 estimate 2.5-3.7 GB **conditional on supported export** | Unknown Android; 10x claim is server experiment [P2] |
| Qwen3-4B | A | GPU/experimental Hexagon coverage conditional | G | Estimate 2.6-3.8 GB | Unknown matched phone measurement retained |
| Qwen3.5-4B | A with supported hybrid converter | GPU/NPU coverage conditional; vision separately | G; recurrent tensors may remain FP32 | Estimate 2.6-4.3 GB for text, not whole multimodal app | Unknown matched phone measurement retained |
| Gemma 3 4B | A; compatible multimodal runtime separately | GPU conditional; NPU model-specific | G; QAT INT4 | Text estimate 2.6-4.0 GB; vision adds allocations | Unknown matched measurement retained |
| Gemma 3n E2B | L; architecture-specific GGUF support check | LiteRT CPU/GPU published | Mobile mixed formats; FP16, INT8/Q4 require supported architecture export | B0 disk 2965 MB; full FP16 weights about 10.88 GB by arithmetic; optimized RAM unknown here | B0: S24 Ultra CPU/GPU decode both 16 tok/s |
| Gemma 3n E4B | L | LiteRT CPU/GPU published | Same caveat | B0 disk 4235 MB; full FP16 weights about 15.70 GB; optimized app RAM unknown | B0: S24 Ultra CPU/GPU decode both 9 tok/s |
| Gemma 4 E2B | L; official mobile bundle; supported GGUF route separately | LiteRT CPU/GPU; NPU variants target specific | Mobile mix of 2/4/8-bit; FP16, SFP8, Q4_0/GGUF alternatives | G1 GPU/TPU estimates: 11.4 GB BF16, 2.9 GB Q4_0, mobile 1.1 / text-only 0.84 GB; B1 CPU RSS 1733 / GPU run CPU RSS 676 MB, **not total GPU memory** | B1 S26 Ultra CPU 46.9 / GPU 52.1 tok/s; warm TTFT 1.8 / 0.3 s |
| Gemma 4 E4B | L; supported GGUF separately | LiteRT CPU/GPU; NPU conditional | Mobile mixed, FP16/SFP8/Q4_0, supported G exports | G1 estimates: 17.9 GB BF16, 4.5 GB Q4_0, mobile 2.5 / text-only 2.2 GB; not app PSS | B0 S26 Ultra CPU 18 / GPU 22 tok/s |
| Qwen3-8B | A | GPU/experimental Hexagon only after exact fit/op tests | G | Estimate 4.9-6.7 GB, before concurrent specialists | Unknown matched phone measurement retained |
| Llama 3.1 8B | A | Vendor NPU exports possible but not evaluated here | G; vendor mixed quantization separate | Estimate 4.8-6.6 GB; official quantize guide example Q4_K_M file 4.58 GiB [R7] | Unknown matched phone measurement retained |

A RAM envelope is not a reason to enable inference on a phone with the same nominal RAM. Android, the UI, database, image buffers and other applications need headroom. Memory-mapped file pages, native allocations, driver buffers and duplicated weights can dominate. Record process PSS/RSS, system pressure and accelerator allocation separately, and do not sum overlapping accounting metrics blindly.

## 7. Published mobile measurements and what they establish

### B0: Google LiteRT-LM overview [B0]

| Model / disk MB | Device | CPU prefill / decode tok/s | GPU prefill / decode tok/s |
|---|---|---|---|
| Gemma 3 1B / 1005 | S24 Ultra | 177 / 33 | 1191 / 24 |
| Gemma 3n E2B / 2965 | S24 Ultra | 111 / 16 | 816 / 16 |
| Gemma 3n E4B / 4235 | S24 Ultra | 74 / 9 | 548 / 9 |
| Phi-4-mini / 3906 | S24 Ultra | 67 / 7 | 314 / 10 |
| Qwen2.5-1.5B / 1598 | S25 Ultra | 298 / 34 | 1668 / 31 |
| Qwen2.5-0.5B / 521 | S24 Ultra | 251 / 30 | Not provided |
| Gemma 4 E4B / 3654 | S26 Ultra | 195 / 18 | 1293 / 22 |

This overview lacks all per-artifact quantization/runtime/warmup/energy details needed for a controlled cross-family comparison. It establishes feasible examples and warns against assuming GPU always increases decode. Do not rank Phi against Qwen without controlling phone, artifact and prompt.

### B1: Gemma 4 E2B mobile bundle [B1]

S26 Ultra, LiteRT-LM, 1024 prefill / 256 decode, allocated context 2048; CPU XNNPACK, four threads; initialized caches. TTFT **excludes model loading**. Model file 2583 MB. CPU: prefill 557 tok/s, decode 46.9 tok/s, TTFT 1.8 s, CPU `ru_maxrss` 1733 MB. GPU: prefill 3808 tok/s, decode 52.1 tok/s, TTFT 0.3 s, CPU `ru_maxrss` 676 MB.

Published ratios: GPU prefill about **6.84x**, decode about **1.11x**. Estimated warm time for 128 output tokens from these rates is about 4.53 s CPU versus 2.76 s GPU (`TTFT + 127/decode_rate`), **not a newly measured 128-token benchmark**. The 676 MB host RSS does not prove total GPU/unified-memory footprint is 676 MB. Model-page mobile-memory estimates and measured RSS have different scopes and should not be substituted for each other.

The mobile bundle uses 2/4/8-bit mixed weights, memory-mapped embeddings and on-demand audio/vision. It is not a uniformly Q4 model. Separate speculative-decoding results use varying tasks/token lengths; they are not directly comparable with the fixed-shape baseline. Treat speculation as another ablation with memory/energy measurements, not a reason to deploy a larger model.

### B2: Qwen3-0.6B LiteRT community measurements [B2]

Mixed INT4 bundle, context 2048, 256 prefill / 256 decode, LiteRT-LM v0.13.1, warm iteration of a two-iteration run. Samsung SM-S937U1 GPU: 1844.95 prefill, 69.38 decode, 0.150 s TTFT, 585 MB peak private footprint. Same SKU CPU: 576.59 prefill, 12.90 decode, 0.520 s TTFT, 2895 MB peak private footprint. TECNO LJ9 CPU: 231.15 / 8.33, 1.230 s, 2890 MB. GPU private footprint on vivo/TECNO was about 1.8 GB.

These are community-authored retail-device CLI runs, explicitly not an integrated app and not independently vendor-verified. The unexpectedly large CPU footprint is a concrete counterexample to estimating RAM from the 0.6B label. Other variants on the same page use INT8, INT4 block-32 or a16w8 NPU; do not combine their results as though they are one artifact.

### B3: Arm Qwen3.5-2B Q4_K_M on vivo X300 [B3]

Text decoder only; separate FP16 projector was not benchmarked. Android 16/OriginOS 6, four CPU threads, prompt/output 128/128, five warmups and twenty measured runs. Device starts each run at thermal status NONE after a 30-second settle, in fixed-performance mode.

| Metric | FP16 baseline | Optimized Q4_K_M | Published difference |
|---|---|---|---|
| Decode | 7.87 tok/s | 22.09 tok/s | 2.81x |
| TTFT | 6346.48 ms | 983.29 ms | 6.45x |
| End-to-end p50 | 22608.92 ms | 6782.29 ms | 3.33x |
| Peak memory | 3712.12 MB | 2523.97 MB | 1.47x reduction |
| Decoder file | 3600.80 MB | 1215.36 MB | 2.96x reduction |
| MMLU-redux-2.0, 5330 examples, 0-shot | 63.9024% | 62.4578% | About -1.44 percentage points |

This is the strongest reviewed CPU-first candidate evidence because it reports conditions, total-request latency and a quality comparison. It combines quantization and optimized kernels, so gains cannot be attributed to bit width alone. The card uses SME2 positioning but records a build with `armv8.6-a+dotprod+i8mm` and KleidiAI; do not infer a universal SME2-only speedup or reproduce it by globally raising Sakshi's CPU instruction baseline.

The peak-memory metric's accounting is not fully specified in the card. Fixed-performance, thermally reset runs do not establish background battery drain, sustained burst speed or S24/midrange performance. MMLU quality loss does not bound threat-recall loss.

### B4: Liquid LFM2.5 publisher measurements [F2]

1K prefill / 100 decode tokens. On S25 Ultra CPU, llama.cpp Q4_0: LFM2.5-1.2B 335 prefill / 70 decode tok/s, 719 MB; Qwen3-1.7B 181 / 40, 1306 MB. This same-phone publisher comparison supports lower memory/latency for that LFM artifact. The model card also reports ROG Phone9 Pro NPU/NexaML 4391 / 82 tok/s and 0.9 GB. Memory accounting, sustained energy and NPU quantization detail are not fully specified.

Do not compare ROG NPU against S25 CPU to claim a controlled NPU gain. LFM's supported-language list excludes Sakshi's Indic requirements, and its licensing differs from Apache. Speed alone does not make it the best primary model.

### B5: Qualcomm Llama 3.2 3B [B5]

Current publisher table lists mixed w4a16 export on Snapdragon 8 Elite For Galaxy at 28.03 decode tok/s, 4K context, TTFT 0.077-2.454 s for up-to-128-token through 4096-token prompts. A different w4 row is 13.81 tok/s on the same named chipset. These are different artifact/precision settings, not a contradictory universal Llama speed. Android deployment requires export under upstream licensing; the card says pre-exported assets cannot be distributed and Genie support is being deprecated in favor of GenieX. Verify exact release rather than copying older performance tables.

### Non-mobile claims deliberately excluded

Phi-4-mini-flash's up-to-10x decoding throughput and 2-3x latency claim was measured with vLLM on an **A100-80GB GPU**, not an Android phone [P2]. Laya's 32.8/39.5 ms rows are T4 GPU, and desktop CPU numbers are not phone timing [L1]. llama.cpp's generic quantization throughput table lacks a phone hardware disclosure and is used here only for format/size illustration [R7]. FrugalGPT's up-to-98% cloud API cost saving is not a battery/RAM claim [A1].

## 8. Quantization: compare FP16, INT8, Q8, Q6, Q5, Q4 and Q3

**INT8 is a numerical precision family; Q8_0 is a particular blockwise GGUF weight representation. They are not interchangeable artifact specifications.** Weight-only quantization, dynamic activation quantization, static QDQ and vendor w4a16 all execute differently. FP16 and BF16 both use two bytes per element but differ numerically and in hardware support; a BF16 reference is not identical to FP16.

| Option | Weight storage rule of thumb | Execution / quality implications | Sakshi policy |
|---|---|---|---|
| FP16 | 2 bytes/weight | Useful reference; some Arm kernels/backends may upcast; high memory bandwidth/storage; KV/scratch still extra | Reference parity and small specialists where supported, not default large phone LLM |
| INT8 | Ideal 1 byte/weight plus scales, mixed tensors | May be weight-only, dynamic or static activation quantization; speed requires matching kernels; calibration dataset may be needed | First encoder experiment, but calibrate **after** quantization and reject recall drift |
| Q8_0 | About 8.5 bits per block weight before whole-model mixed overhead | GGUF weight-only, activations/KV separately configured; usually closer numerical parity than lower precision, not guaranteed | LLM reference/quality ceiling and sensitive embeddings/projectors where useful |
| Q6_K | About 6.56 effective bits/weight in the guide's Llama 8B example | Smaller than Q8, larger than Q5; potentially better quality, backend kernels vary | Upgrade if Q4/Q5 quality is insufficient and memory allows |
| Q5_K_M | About 5.70 effective bits/weight in that example | Mixed tensor types; attractive compromise when Q4 errors matter | Compare directly against Q4 on worst-language/threat recall |
| Q4_K_M | About 4.89 effective bits/weight in that example | Not exactly 0.5 bytes for every tensor; imatrix can help; measured phone benefit with model-specific quality cost | First CPU LLM quantization candidate, not automatic safety approval |
| Q3_K_M | About 4.00 effective bits/weight in that example | Whole-model metadata/mixed precision can erase nominal 3-bit saving; higher error risk, especially tiny models and multilingual boundaries | Experimental only; prefer a validated smaller Q4/Q5 model over squeezing 8B into Q3 |

The illustrative **Llama 3.1 8B** files in llama.cpp's guide are FP16 14.96 GiB, Q8_0 7.95 GiB, Q6_K 6.14 GiB, Q5_K_M 5.33 GiB, Q4_K_M 4.58 GiB and Q3_K_M 3.74 GiB [R7]. These are model files, not Android app RAM. The guide's unrelated throughput numbers are not transferred to phones. Smaller quantizations need not be faster if dequantization, memory layout, kernel coverage or fallback dominates.

### 8.1 Ideal arithmetic, not model file or RAM measurements

| Parameters | FP16 GB | Ideal INT8 GB | Ideal 6-bit GB | Ideal 5-bit GB | Ideal 4-bit GB | Ideal 3-bit GB |
|---|---|---|---|---|---|---|
| 0.5B | 1.00 | 0.50 | 0.375 | 0.313 | 0.250 | 0.188 |
| 1B | 2.00 | 1.00 | 0.750 | 0.625 | 0.500 | 0.375 |
| 2B | 4.00 | 2.00 | 1.500 | 1.250 | 1.000 | 0.750 |
| 4B | 8.00 | 4.00 | 3.000 | 2.500 | 2.000 | 1.500 |
| 8B | 16.00 | 8.00 | 6.000 | 5.000 | 4.000 | 3.000 |

Formula: `parameters * bits / 8`, decimal GB. Actual quantized exports include scales, zero-points, padding, non-quantized norms/recurrent tensors, tokenizer, untied outputs and modality weights. Gemma effective size is not the parameter count to plug into this formula.

### 8.2 KV cache can overturn a small-model recommendation

For ordinary GQA/MHA attention with uniform layers and no sliding/recurrent optimization:

`KV bytes = 2 * layers * KV_heads * head_dimension * context_tokens * bytes_per_element * batch`

At batch 1 and FP16 KV:

- Qwen2.5-1.5B: 28 layers, 2 KV heads, 128 inferred head dimension gives **56 MiB at 2K**, 112 MiB at 4K.
- Qwen3-1.7B: 28 layers, 8 KV heads, 128 head dimension gives **224 MiB at 2K**, 448 MiB at 4K.
- SmolLM2-1.7B: 24 layers, 32 KV heads, 64 head dimension gives **384 MiB at 2K**, 768 MiB at 4K.

These are arithmetic estimates from configs, not measured allocations. FP32 KV doubles them. Sliding-window, NoPE, shared-KV and recurrent/hybrid models require architecture-specific accounting; do not apply the all-layer formula blindly to Qwen3.5, Gemma 4 or Phi Flash. Weight Q4 does not automatically make KV Q4. For Sakshi's initial 2K/4K windows, benchmark KV Q8 only if supported and compare quality, not just memory.

### 8.3 Laya quantization warning

The reviewed official exporter calls `onnxruntime.quantization.quantize_dynamic` over MatMul with QInt8 weights. Its docstring calls this weight-only but also says activation scales are computed per input; ordinary dynamic quantization quantizes activations for integer MatMul. Therefore it must not be treated as a proven safe weight-only export [L2]. The source reports English argmax agreement **64/96** per-tensor versus **31/96** per-channel; multilingual **83%** versus **40%**. These are export parity figures on the project's examples, not harassment accuracy.

The same source reports roughly 2x CPU speedup versus eager and 1.4-2.8x smaller artifacts, while explicitly warning not to use the export where calibrated confidence matters. Other third-party weight-only builds report better small-set parity, but those are not proof of Android kernel compatibility or safety. Use a pinned FP32/FP16 reference, test a genuinely supported weight-only/QAT approach, then refit temperature/thresholds on the exact production artifact. Do not route by probabilities copied from the original checkpoint after quantization.

## 9. Laya's correct place in Sakshi

The current repository describes three non-autoregressive decision checkpoints: English ModernBERT-large 421M, multilingual mmBERT-base 322M, and typed-decisions ModernBERT-large 421M. Outputs are `choice`, `score`, and `noul` yes/no decisions, not generated explanations. Defaults are 512/1024-token inputs; multilingual can be configured up to 8192 with quality/latency limits [L1].

English ideal FP16 weights are about 842 MB; multilingual about 644 MB, before tokenizer, activations, runtime and decision head/export layout. Published T4 one-question latency is 39.5/32.8 ms, **not Android latency**. There is no verified production Kotlin/Android integration or phone energy measurement in the sources reviewed. ONNX export feasibility is not a working Android runtime claim.

The repository now explicitly says both shipped checkpoints are overconfident and multilingual has no fitted temperatures. It describes RLCD domain fine-tuning and temperature calibration, while noting a notebook used training items for calibration: Sakshi must use a separate calibration partition and independent test set [L1]. Benchmark gains on typed routing/tickets do not establish harassment utility.

**Preferred experiment:** compare one fine-tuned 117M encoder against one fine-tuned Laya multilingual, each calibrated and exported safely. Ship whichever meets worst-language high-risk recall at lower phone cost. Use Laya as a separate conditional stage only if it catches a useful fraction of cases the cheap encoder misses, with confidence measured on that routed subset. Do not keep English + multilingual Laya checkpoints hot just because its desktop router does: a phone can use one multilingual model and abstain on unsupported scripts/code-mixing. Romanized Malayalam is not reliably identified by script detection.

## 10. Routing: gains, failure modes and a concrete policy

### 10.1 Corrected routing policy

```text
1. Preserve acquisition/extraction status and source uncertainty.
2. Apply package consent, size bounds, summary/repost handling and parsing.
3. Update permitted temporal observation features; never fabricate absent events.
4. If text is missing/redacted/truncated beyond interpretability -> UNKNOWN.
5. If explicit high-risk indicator, boundary violation, unusual repetition,
   unsupported language, OCR/STT uncertainty or context dependence -> bypass benign stop.
6. Run one calibrated multi-label encoder on available text and bounded context.
7. High-confidence ordinary, no temporal trigger, in validated language/domain:
   stop heavy inference; expire ordinary content according to retention policy.
8. Suspected incident -> encrypted review candidate; expose source, score and uncertainty.
9. Optionally use Laya only if its held-out incremental benefit warrants another stage.
10. Ambiguous/context-heavy or user-requested explanation/summary:
    gather bounded case/time/adjacent/boundary evidence, then one foreground local LLM.
11. Resource limit/unavailable model -> abstain + manual/extractive review, not 'benign'.
12. Validate anchors; human confirms/edits/rejects before confirmed vault/export.
```

No universal numeric threshold is defensible before calibration. For each language/script/source channel and category, select benign-stop thresholds to bound **missed incident rate**, not to maximize overall accuracy. Low-confidence and out-of-distribution samples must not be treated as benign. High-confidence model outputs can still be wrong; audit benign-stop false negatives offline using consented research data, not hidden uploads from users.

Do not require an LLM before displaying an explicit threat candidate. A user manually selecting evidence bypasses the benign discard path and can always preserve it, even if all models say ordinary. The routing decision controls compute, not the user's ability to save evidence.

### 10.2 Expected latency and energy

Let `T_R` be rules/temporal time, `T_C` classifier time, `q_D` the unconditional fraction routed to Laya, `q_L` to the LLM, and `q_H` to retrieval. Let `T_load` be cold model setup amortized per event that actually loads it.

`E[T] = T_R + T_C + q_D*T_D + q_H*T_H + q_L*(T_load + T_prefill + T_decode)`

If fractions are conditional, multiply them: 25% reach Laya and 40% of those reach LLM means **10%** total reach LLM, not 40%. Costs for a batch, queue delay and synchronous wait require separate accounting. Sequential escalated-path latency includes all preceding stages; routing primarily improves the ordinary fast path and total work, not necessarily the worst-case response.

**Illustrative scenario, not measurement:** assume rules/temporal 10 ms, encoder 80 ms, Laya 500 ms, q_D=0.25, q_L=0.10, no retrieval/cold-load cost. Use Arm's published 6782.29 ms LLM request only as an example service time. Expected compute time is **893.23 ms**, about **86.8% lower** than 6782.29 ms for every item. Without Laya it is **768.23 ms**. The fully escalated sequential path is **7372.29 ms**, slower than the LLM-only service time. A ten-second cold load on each routed item adds 1000 ms to the average in this scenario. These mixed-source/hypothetical numbers are not Sakshi predictions.

Energy has the same route-weighted accounting structure, but milliseconds are not joules. If an illustrative baseline costs 10 J/event, rules+encoder 0.2 J and Laya 0.5 J for 25%, while LLM runs on 10%, expected energy would be 1.325 J, an 86.75% reduction. **Those joule values are invented scenario inputs, not measurements.** GPU/NPU can draw more power while finishing faster; idle residency, cold loads and DVFS alter energy. No reviewed source justifies a specific Sakshi battery percentage saving.

### 10.3 Peak RAM versus average residency

- Loading classifier, Laya, embeddings, ASR and LLM simultaneously adds weights/buffers. A cascade does not magically lower peak RAM.
- Serial unload/load can reduce peak model residency to roughly the maximum stage plus persistent app overhead, but allocator/driver memory may remain cached and must be measured.
- Keep only the small chosen triage encoder resident during active observation if justified by energy. Load the LLM for foreground review, reuse within that session, close on background/resource pressure according to a tested policy.
- Serialize OCR/STT/LLM large allocations. Do not load a VLM encoder for text-only prompts. One model operation at a time until concurrency is justified.
- Use bounded queues, backpressure, dedup before model invocation and a circuit breaker on repeated load/OOM failures. Mark unprocessed observations honestly, without unlimited background retries.

### 10.4 Accuracy is not guaranteed to improve

If a cheap gate retains 97% of truly concerning items, a second gate retains 98% of those, and the final model detects 95% of the survivors, total recall is `0.97*0.98*0.95 = 90.3%`, not 95%. This is conditional-recall arithmetic, **not an independence assumption or measured rate**. The final model cannot recover events discarded upstream.

A cascade can improve specificity/explanations when expensive context resolves ambiguity, but can harm recall and introduce correlated errors. Evaluate the routed subset, missed confident-benign subset, high-risk bypasses and overall system. Do not estimate end-to-end performance from each stage's generic benchmark accuracy. FrugalGPT supports the plausibility of learned cascades in other tasks, not proof of a harassment/mobile gain [A1].

### Verdict on the user's example

- **Latency:** meaningful average savings are plausible and supported by avoiding expensive generation, but route rate and cold starts must be measured.
- **Battery:** plausible savings, **not quantified** by available evidence; measure joules/event and idle overhead.
- **RAM:** lower average LLM residency is achievable; lower peak requires scheduling/unloading and verified runtime release.
- **Accuracy:** conditional, not automatic; domain calibration, high-risk bypass and temporal-before-stop are essential.
- **Laya stage:** optional challenger, not a default tax on every suspicious message.

## 11. Retrieval, multimodal and device-tier design

### 11.1 Retrieval does not replace a pattern engine

Start with case-scoped structured queries, most recent adjacent events, user-reviewed boundary messages and lexical search. Always include coverage/gap indicators. Add semantic retrieval only if measured context-recall gains exceed its model/index cost.

E5-small: 117.7M, 384 dimensions, 512-token passages, MIT, 100-language pretraining coverage with low-resource degradation warning; prefix queries/passages exactly as documented [E1]. EmbeddingGemma: 300M, 2K inputs, 768 dimensions reducible to 512/256/128, 100+ languages, Gemma terms; on-device focus but not proven Sakshi retrieval quality [E2]. Both need exact tokenizer/pooling/normalization/export tests. Embedding FP16/INT8/Q4 affects retrieved neighbors and must be evaluated separately from LLM quantization.

10,000 vectors of 384 float32 values require about **15.36 MB** before metadata/index/text overhead; 768-D about 30.72 MB. These are arithmetic storage examples, not resident model RAM. Small cases can use exact cosine scans and do not require a vector database/server. Consider sharing an encoder with a classification head only if retrieval and classification both remain good after fine-tuning; shared weights do not guarantee compatible embedding geometry.

Filter case/consent/retention first, then union recent, lexical and semantic candidates, attach neighboring records and boundary evidence, dedup and bound the prompt. Never rely solely on semantic top-k to establish repetition or absence of a threat. A count query should compute over retained events directly. Encrypt embeddings and indexes; they can expose sensitive semantics and are not anonymized evidence.

### 11.2 Multimodal choices

Use exact OCR/STT derivatives as the primary bridge into text analysis. A VLM may describe visible context on a selected image but cannot replace glyph-faithful OCR, identify intent/identity from appearance, or infer content from a notification's 'Photo' label. Gemma 4/Gemma 3n supply multimodal capabilities; Qwen3.5 includes a vision encoder. Source-model capability does not establish that the chosen text-only export or Android binding supports every modality.

Multimodal benchmarks must include encoder/projector weights, decoding/resizing/PCM, input tokens and image/audio activation peaks. Compare task-specific OCR character error rate, ASR word/character error rate and evidence-span alignment. Preserve originals and transformations; uncertain extraction triggers review, not confident text classification. No always-on microphone/video analysis, protected-media acquisition or cloud fallback.

LFM2.5 also advertises VL-1.6B and Audio-1.5B specialist variants [F2]; those variants were not independently profiled here, and their smaller names are not enough to displace the validated specialist pipeline. Defer them until a modality-specific requirement and Android benchmark exists.

### 11.3 Proposal: capability tiers, not RAM-only promises

| Phone class | Proposed default | Optional foreground generation | Constraint |
|---|---|---|---|
| Around 4-6 GB, limited free memory/low-RAM flag | Rules + reviewed timeline + one tested small encoder; sequential OCR/STT | Off by default; only a validated sub-1B pack if measured headroom permits | Do not sacrifice acquisition/review for LLM availability |
| Around 8 GB modern midrange | Same core | Qwen2.5-1.5B or Qwen3.5-2B Q4 only after an actual short-context smoke/pressure test | 2K first; conservative memory ceiling and cold-load cancellation |
| Around 12-16 GB flagship | Same core | Bake off Qwen3.5-2B CPU versus Gemma 4 E2B CPU/GPU; 4B upgrade only for measured quality need | Total app/driver memory and sustained thermals, not headline RAM |
| Supported AICore devices | Same independent core | Optional ML Kit/Gemini Nano prompt or summarization path | Device/feature/language checks, API terms, availability, refusal and update-version drift |

Gemini Nano/AICore avoids a separately downloaded model when already present and uses Android system-managed on-device inference [F5]. It is not a universally available, freely fine-tunable 1B model: parameters/quantization/memory depend on system model and are not exposed as one stable app-controlled checkpoint. Feature-specific and Prompt API device lists differ. Current ML Kit docs mark relevant APIs Beta/Alpha. Check availability rather than hardcoding historic Pixel-only or all-Android claims. Filters that reject graphic/violent evidence can undermine Sakshi analysis; preserve manual/template behavior if the API refuses. No local refusal triggers automatic cloud upload.

## 12. Evaluation and release gates

### 12.1 Dataset and quality plan

Build a multi-label, context-aware evaluation set with licensed/consented data and clearly identified synthetic challenge cases. Record source, language, script, context availability, labels, annotator guidance, license and consent. Split by conversation/person/source and time where applicable, not random overlapping message windows. Separate train, development, calibration and final test sets. Distillation/LLM-generated labels are weak labels, never ground-truth facts or real evidence.

Required language rows: English, Malayalam, Hindi, Tamil, Telugu, Kannada, Bengali, Marathi, Hinglish, Romanized Indic and code-mixed/slang. Report native-script versus Romanized results separately. Include negation, indirect threats, reclaimed slurs, quoted/forwarded abuse, consensual sexual content, medical discussion, household logistics, stop-contact boundaries, OCR damage, clipped notifications, dialect, emoji, misspelling and benign PII. Include message sequences where each message looks ordinary but repeated unwanted contact forms a pattern.

| Evaluation layer | Metrics |
|---|---|
| Labels | Per-category precision/recall/PR-AUC, macro-F1, confusion analysis, severe-threat FN rate; multi-label not accuracy alone |
| Calibration | ECE/Brier/reliability curves per language/category/source; selective risk at benign-stop coverage; threshold stability after quantization |
| Routing | Total high-risk recall, rejection FN, escalation fraction, recall on routed subset, unsupported-language abstention, confident-error audit |
| Temporal | Duplicate-sensitive count accuracy, pattern precision/recall/time-to-detection, category transitions, gap reporting and deletion invalidation |
| Explanations/summaries | Schema validity, citation validity, source-span match, claim support, omitted material, date/count correctness, uncertainty and refusal rates |
| Retrieval | Recall@k for required context, boundary-message inclusion, cross-case leakage, quantization neighbor drift |
| OCR/STT | Per-language CER/WER plus downstream label error caused by extraction |

Do not invent per-language performance for this report. It is currently **unknown for every shortlisted model on Sakshi's target tasks**. Generic Gemma IndicGenBench, Qwen multilingual MMLU and MiniLM XNLI/Hindi results are candidate-selection evidence, not a substitute for this table.

### 12.2 Controlled Android benchmark protocol

Use a consented test app and synthetic/non-sensitive fixtures on at least a 6 GB midrange, an 8 GB midrange, and a 12 GB flagship, ideally spanning Qualcomm and MediaTek. Prioritize the actual target phone before extending the matrix. Do not install huge packs, enable notification access or inspect personal evidence without a specific user-approved test step.

Freeze model/revision/checksum, tokenizer, quantization recipe, runtime commit/version, ABI, Android build, SoC/RAM, backend and thread count. For each candidate:

1. Reference FP16/FP32 task evaluation, then exact mobile-export parity. Quantize from high precision, not an already quantized file.
2. Cold load/first request, repeated warm requests, ten-to-twenty-minute bursts and ordinary sparse arrivals. Separate filesystem/shader/tokenizer/model caches.
3. Input shapes 128/512/1024/2048 tokens and outputs 32/128/256; context allocation 2K then 4K. Actual supported-language token counts, not English character equivalence.
4. CPU 2/4 threads, supported GPU and exact NPU artifact. Log actual delegate/operator placement and fallbacks. Unsupported graph/backend is a failure, not a 'zero latency' result.
5. End-to-end ingest/extraction-to-review p50/p95, model-load time, TTFT, prefill/decode rates, classifier request latency, queue wait and pattern update time.
6. Java/native PSS/RSS, memory-mapped weights, peak during load/decode/OCR/STT, GPU/vendor memory where exposed, system available memory and low-memory kills. Capture lifetime cleanup.
7. Energy per event/session and idle collection overhead, screen/radio state controlled, device thermal/battery state recorded. Use supported power profiling/Perfetto or external measurement where available; percentage battery deltas alone are too coarse.
8. Screen-off/background, low battery, thermal pressure, cancellation, listener revocation, missing model, corrupted artifact, OOM and app/process death. No plaintext fallback or infinite retry.
9. Airplane-mode functional test after explicit model download; inspect unintended evidence networking/telemetry boundaries without asserting SDK-wide zero-network behavior.
10. Source-linked quality validation on the **same** quantized production artifact and routing configuration.

Use thermal status/headroom cautiously: some devices can report NONE despite throttling; unsupported headroom can return NaN. Fixed-performance mode is useful for reproducibility but must be accompanied by normal-governor sustained tests [R8]. Do not use prior warm benchmark numbers as background power expectations.

### 12.3 Ablation matrix

Run identical test cases against rules+templates; encoder only; Laya only; single LLM; encoder+LLM; rules+encoder+LLM; encoder+Laya+LLM; lexical-history+encoder+LLM; semantic retrieval addition; specialist OCR/STT versus selected VLM. Also test temporal-engine disabled, high-risk-bypass disabled, cold-load versus session reuse, and FP16/Q8/Q6/Q5/Q4/Q3 on shortlisted LLMs. The point is to justify each stage, not to keep every experiment in the final app.

Optimize the Pareto frontier of **worst-language high-risk recall, evidence-grounded utility, joules/event, p95 latency and peak memory**. An English-only 70 tok/s model should not win over a slower multilingual model merely by averaging away poor Malayalam results.

### 12.4 Proposed gates, explicitly not achieved results

- Benign-stop gate: choose thresholds from a predeclared missed-incident budget. A possible pilot target is >=98% severe-threat recall at the gating stage with a confidence interval, not a bare point estimate. With zero misses, roughly 150 positive independent examples are needed just for a one-sided 95% upper miss-rate bound near 2%; fewer positives do not establish that target. Grouped/conversation dependence can require more.
- Routing: non-inferior high-risk recall versus the ungated baseline within a predeclared margin; uncertainty/abstention retained. No arbitrary '90% confidence' default.
- Grounding: zero invalid source ids in accepted/exported output via validation; human audit still required for semantic support. Invalid/incomplete responses fall back.
- Performance: initial goals of <=300 ms p95 warm triage and <=10 s p95 short foreground review are **product targets**, not current capabilities. Adjust with target-phone evidence rather than hiding failures.
- Memory: set tier-specific ceilings after measuring acquisition/UI/specialist peaks; pass burst and pressure tests without losing already saved evidence. A nominal 8 GB phone is not itself an acceptance test.
- Energy: recommend cascade release only after a same-device workload test shows a useful reduction at matched recall; a proposed >=30% reduction is a go/no-go target, not a claim.
- Any unsupported language, missing evidence, failed model or interrupted job remains unknown/pending and manually reviewable.

## 13. Output, security and lifecycle contract

Use typed data, not unbounded prose, as the system of record. One analysis item contains category suggestions, review status, evidence ids/spans, observed/inferred/pattern/unknown distinction, uncertainty reasons, model/runtime/artifact version and permitted optional explanation. Model output must not create evidence records or source timestamps.

Treat evidence as untrusted input, including text saying 'ignore instructions'. Separate it from fixed task instructions; no browsing, execution, external fetching or autonomous actions. Keep prompt length/output length bounded. Use deterministic rendering for counts/dates and extractive templates when generation fails. Avoid storing model hidden reasoning as evidence.

Keep candidate text, accepted evidence, OCR/transcripts, embeddings, indexes and prompts in encrypted app-private storage with appropriate Keystore handling. Models are public artifacts but their inputs/caches are sensitive. Do not log tokens/evidence, leave plaintext prompt cache files, include evidence in crash reports or backups, or persist decrypted temp files to support memory mapping. Test native/runtime temporary/cache behavior explicitly. Expire ordinary observations and rejected candidates; persist confirmed evidence only under the user's choices. Retain original received artifacts separately from transformations and analysis. Source hash proves integrity checking, not authenticity or legal admissibility.

No LLM invocation in an Android notification callback. Copy bounded authorized fields, dispatch to a limited worker, and return. WorkManager/deferred scheduling must respect background restrictions and the app's consent/key-access contract. Foreground heavy review is user-visible and cancellable; resource constraints do not justify covert always-on services.

## 14. Final decision and implementation order

1. **Core MVP:** supported acquisition, immutable originals, local OCR/STT, source-linked manual review, deterministic timeline and templates. No LLM required to preserve/report evidence.
2. **Routine AI:** fine-tune and calibrate one multilingual encoder; compare safe-export Laya as a replacement. Implement abstention and temporal-before-stop.
3. **Optional generation bake-off:** Qwen3.5-2B Q4_K_M CPU versus Gemma 4 E2B mobile LiteRT-LM CPU/GPU, with Qwen2.5-1.5B fallback. Include LFM2.5-1.2B for English/mobile efficiency and Phi/Llama as controls only where justified.
4. **Choose one production LLM pack** by measured contextual recall, supported summaries, worst-language errors, peak memory and energy. Do not name a universal winner before this bake-off.
5. **Retrieval next:** lexical/time/adjacent context first; E5-small versus EmbeddingGemma only if case size and measured missed-context errors justify it.
6. **Larger models/VLM later:** 4B-8B and selected multimodal review only after useful quality improvement outweighs memory/latency/energy costs. No default always-resident model zoo.

**Bottom line:** the optimal defensible design is a **small calibrated decision core with deterministic temporal reasoning, specialist extraction and optional bounded generation**, not a single LLM and not an unconditional five-stage cascade. Published measurements justify mobile feasibility and the first benchmark candidates; Sakshi-specific accuracy, battery gains and deployment thresholds remain open empirical questions.

## 15. Source register and reproducibility notes

All sources were consulted on 2 October 2026. Live cards change. Model revisions below pin selected metadata/benchmark pages; an actual release must pin exported file checksums and runtime revisions too.

### Models, specialists and licensing

- [Q1] Qwen2.5-1.5B-Instruct official card: https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct ; 0.5B official metadata: https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct
- [Q2] Qwen3 official cards/configs: https://huggingface.co/Qwen/Qwen3-0.6B ; https://huggingface.co/Qwen/Qwen3-1.7B ; https://huggingface.co/Qwen/Qwen3-4B ; https://huggingface.co/Qwen/Qwen3-8B
- [Q3] Qwen3.5 official cards/configs: https://huggingface.co/Qwen/Qwen3.5-0.8B ; https://huggingface.co/Qwen/Qwen3.5-2B ; https://huggingface.co/Qwen/Qwen3.5-4B
- [G1] Gemma 4 official model card and memory estimates: https://ai.google.dev/gemma/docs/core/model_card_4 ; https://ai.google.dev/gemma/docs/core
- [G2] Gemma 3n official model card: https://ai.google.dev/gemma/docs/gemma-3n/model_card ; public metadata at https://huggingface.co/google/gemma-3n-E2B-it and https://huggingface.co/google/gemma-3n-E4B-it
- [G3] Gemma 3 official model card: https://ai.google.dev/gemma/docs/core/model_card_3
- [M1] Llama 3.2 official card/license: https://huggingface.co/meta-llama/Llama-3.2-3B-Instruct ; 1B public metadata: https://huggingface.co/meta-llama/Llama-3.2-1B-Instruct
- [M2] Llama official mobile quantized card: https://huggingface.co/meta-llama/Llama-3.2-1B-Instruct-QLORA_INT4_EO8
- [M3] Llama 3.1 8B public card/metadata: https://huggingface.co/meta-llama/Llama-3.1-8B-Instruct
- [P1] Phi-4-mini official card: https://huggingface.co/microsoft/Phi-4-mini-instruct
- [P2] Phi Flash official card and hardware-disclosed launch: https://huggingface.co/microsoft/Phi-4-mini-flash-reasoning ; https://azure.microsoft.com/en-us/blog/reasoning-reimagined-introducing-phi-4-mini-flash-reasoning/
- [S1] SmolLM2 official card: https://huggingface.co/HuggingFaceTB/SmolLM2-1.7B-Instruct
- [S2] SmolLM3 official card: https://huggingface.co/HuggingFaceTB/SmolLM3-3B
- [F1] LFM2 official family table: https://huggingface.co/LiquidAI/LFM2-1.2B
- [F2] LFM2.5 official card with mobile CPU/NPU measurements: https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct
- [F3] MobileLLM official card/license and family table: https://huggingface.co/facebook/MobileLLM-1B
- [F4] LFM license source: https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct/raw/main/LICENSE
- [F5] Current ML Kit GenAI overview, device support and API status: https://developers.google.com/ml-kit/genai
- [C1] Multilingual MiniLM official card, 21M transformer + 96M embeddings, tokenizer warning and fine-tuning: https://huggingface.co/microsoft/Multilingual-MiniLM-L12-H384
- [E1] Multilingual-E5-small official card: https://huggingface.co/intfloat/multilingual-e5-small
- [E2] EmbeddingGemma official card: https://ai.google.dev/gemma/docs/embeddinggemma/model_card
- [L1] Actual Laya repository, decisions, checkpoints, calibration, tuning and benchmark caveats: https://github.com/NandhaKishorM/laya
- [L2] Actual Laya exporter code: https://raw.githubusercontent.com/NandhaKishorM/laya/main/scripts/export_onnx.py

### Android measurements and runtimes

- [B0] Google LiteRT-LM overview/model performance table: https://ai.google.dev/edge/litert-lm/overview
- [B1] Gemma 4 E2B LiteRT-LM artifact, fixed-shape measurements and memory accounting: https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm
- [B2] Qwen3-0.6B LiteRT community variants and disclosed retail-device CLI results: https://huggingface.co/litert-community/Qwen3-0.6B
- [B3] Arm Qwen3.5-2B text-decoder optimization and phone benchmark: https://huggingface.co/Arm/qwen3-5-2b-q4-k-m-ggml-llama-cpp-vivo-x300
- [B4] LFM mobile comparison is the official card in [F2], not a separate independent experiment.
- [B5] Qualcomm Llama export and current precision-specific throughput: https://huggingface.co/qualcomm/Llama-v3.2-3B-Instruct
- [R1] Official LiteRT-LM Kotlin/Android API, load-time warning and acceleration setup: https://ai.google.dev/edge/litert-lm/android
- [R2] llama.cpp Android binding/NDK guidance: https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md ; supported backends: https://raw.githubusercontent.com/ggml-org/llama.cpp/master/README.md
- [R3] llama.cpp GBNF/schema support and limitations: https://raw.githubusercontent.com/ggml-org/llama.cpp/master/grammars/README.md
- [R4] Current Snapdragon CPU/OpenCL/experimental Hexagon guide: https://raw.githubusercontent.com/ggml-org/llama.cpp/master/docs/backend/snapdragon/README.md
- [R5] ONNX Runtime QNN Android graph/build requirements: https://onnxruntime.ai/docs/execution-providers/QNN-ExecutionProvider.html
- [R6] Android NNAPI deprecation/migration: https://developer.android.com/ndk/guides/neuralnetworks/migration-guide
- [R7] llama.cpp quantization formats, mixed tensor handling, files and multimodal projectors: https://raw.githubusercontent.com/ggml-org/llama.cpp/master/tools/quantize/README.md
- [R8] Android thermal API limitations/headroom: https://developer.android.com/games/optimize/adpf/thermal
- [A1] FrugalGPT paper, cloud API cascade research only: https://arxiv.org/abs/2305.05176

### Selected inspected revisions

| Repository | Revision |
|---|---|
| Qwen/Qwen3-1.7B | `70d244cc86ccca08cf5af4e1e306ecf908b1ad5e` |
| Qwen/Qwen3.5-2B | `15852e8c16360a2fea060d615a32b45270f8a8fc` |
| Qwen/Qwen3.5-4B | `851bf6e806efd8d0a36b00ddf55e13ccb7b8cd0a` |
| LiquidAI/LFM2.5-1.2B-Instruct | `0f604ada3f766f9f257460c4c9f0b5d6f69d431b` |
| microsoft/Phi-4-mini-instruct | `cfbefacb99257ffa30c83adab238a50856ac3083` |
| HuggingFaceTB/SmolLM3-3B | `a07cc9a04f16550a088caea529712d1d335b0ac1` |
| convaiinnovations/laya | `55cf4c4ebb4ebe31b2550e8bdf3bd21b99753851` |
| intfloat/multilingual-e5-small | `614241f622f53c4eeff9890bdc4f31cfecc418b3` |
| litert-community/gemma-4-E2B-it-litert-lm | `b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1` |
| Arm/qwen3-5-2b-q4-k-m-ggml-llama-cpp-vivo-x300 | `4120eaf9e06b2d162516fc332a45c3ebd241c1d5` |

**Open questions before implementation:** exact target phone tiers; consented domain data and licenses; worst-language threat recall; safe Laya quantization; total accelerator memory; app-level cold-load/sustained energy; backend graph coverage; hallucination/refusal rates on real selected evidence; measured marginal benefit of Laya/retrieval/VLM. Each has a corresponding benchmark or review gate above, rather than an unsupported affirmative answer.
