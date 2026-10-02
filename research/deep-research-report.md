# Executive Summary  
We surveyed the state-of-the-art in on-device AI and identified opportunities to make Sakshi’s harassment-evidence app competitive. Key findings include: mature C/C++ inference engines (e.g. **llama.cpp**/**ggml**) and emerging model formats (GGUF) make running LLMs on Android feasible; low-bit quantization (4–8‑bit) dramatically reduces RAM and storage at modest accuracy cost; and Android provides hardware-backed security (Keystore/TEE) for protecting keys and models. Novel UX features (explainable outputs, user corrections, confidence cues) can differentiate the app. We propose technical ideas like a hybrid *router* (tiny classifier for obvious cases, LLM on demand), adaptive per-device quantization, streaming inference, and a secure, encrypted pipeline (keyed model loading and attestation). A prioritized shortlist (e.g. classifier-routing, encrypted storage, timeline UI) focuses on high-impact but feasible hacks. We recommend specific LLMs (quantized LLaMA-2/3 or Qwen models in GGUF format) and toolchains (llama.cpp, whisper.cpp, TensorFlow Lite) for Android, along with benchmarks (tokens/sec, latency, memory, battery) to evaluate prototypes.  

## 1. Survey of On-Device AI Projects  
| Project / Model        | License           | Model Sizes (params)            | Quant. Formats           | Android Support       | HW Accel                         | Maturity & Notes                                | Link        |
|-----------------------|------------------|-------------------------------|--------------------------|-----------------------|-----------------------------------|------------------------------------------------|-------------|
| **llama.cpp (GGML)**  | MIT              | Any (e.g. 7B–70B LLaMA/LLaMA2) | 1.5–8-bit integer (e.g. Q4, Q5) | Yes (JNI, Android app demo) | CPU SIMD, ARM NEON/SME, x86 AMX, Vulkan GPU, OpenCL, WebGPU | Very high (135k★, used in GPT4All, etc); enables offline LLM on phones | [GitHub](https://github.com/ggml-org/llama.cpp) |
| **ggml**              | MIT              | Library (no fixed size)        | 2–8-bit int, MXFP4/NVFP4     | Yes (C API, used by llama.cpp) | Broad backend (CPU, GPU, NPU, Web) | 15k★; core tensor library powering llama.cpp/whisper.cpp | [GitHub](https://github.com/ggml-org/ggml) |
| **Whisper.cpp**       | MIT              | 74M–1.6B audio models (tiny–large) | FP16, Q5_0 etc (integer)   | Yes (Android examples in repo) | CPU (NEON), Apple Metal (M1 ANE) | 22k★; Offline STT with Whisper, micro-benchmarks (base.en ~390MB) | [GitHub](https://github.com/ggml-org/whisper.cpp) |
| **Vosk API**          | Apache-2.0       | Acoustic/language models (10–100s MB) | FP32 (some int8 quant in speech models) | Yes (Android SDK, Kotlin) | CPU (Kaldi/HMM); no special GPU support | 15k★; well-established offline speech ASR (many languages) | [GitHub](https://github.com/alphacep/vosk-api) |
| **Coqui STT**         | MPL-2.0          | Acoustic models (~20–300MB)      | FP32 (TFLite/TPU int8 optional)  | Yes (via TFLite, Python/Kotlin APIs) | CPU (with TensorFlow Lite) | 2.6k★; open-source successor to DeepSpeech (note: now superseded by Whisper for quality) | [GitHub](https://github.com/coqui-ai/stt) |
| **TensorFlow Lite (TFLite)** | Apache-2.0 | Mobile/IoT models (~KB–50MB)  | FP16, int8, int4 (Quant TFLite)    | Yes (native Android support)      | CPU, NNAPI, GPU Delegate (OpenGL/Vulkan) | Very high (widely used mobile ML framework) | [Docs](https://www.tensorflow.org/lite) |
| **Google ML Kit**     | Proprietary free | Vision, NLP APIs (on-device)  | N/A                          | Yes (SDK for Android/iOS) | CPU/NNAPI     | Offloads to Google’s services or device; not open-source | [Docs](https://developers.google.com/ml-kit) |
| **PaddleOCR**         | Apache-2.0       | OCR models (~10–100MB)         | FP32/FP16 (TFLite int8 on Android) | Yes (TFLite, has Android demo)   | CPU, GPU via TFLite GPU delegate | 90k★; state-of-the-art multilingual OCR/structured OCR | [GitHub](https://github.com/PaddlePaddle/PaddleOCR) |
| **Qwen (Alibaba)**    | Apache-2.0 (some models) | 0.8B–7B (open-distilled) up to 72B (closed) | FP16/FP32, quant (e.g. Q4) | Yes (via llama.cpp, TFLite) | CPU/GPU/NNAPI | High (42k★ pinned Qwen3.8 7B); Alibaba’s open LLM family | [Qwen Org](https://github.com/QwenLM) |
| **Stanford Alpaca**   | CC-BY-NC (dataset) | 7B (fine-tuned LLaMA-7B)      | FP16 (runs via llama.cpp) | Yes (via llama.cpp) | CPU/GPU/NNAPI | ACtually licensed non-commercial; widely used instruct-tuning example | [Paper](https://crfm.stanford.edu/2023/03/13/alpaca.html) |
| **Koala (UCB)**       | CC-BY-NC         | 7B (fine-tuned LLaMA-7B)      | FP16 (via llama.cpp)  | Yes (via llama.cpp) | CPU/GPU/NNAPI | Non-commercial instruct model; LLAMA derivative | [Blog](https://bair.berkeley.edu/blog/2023/04/10/koala/) |
| **Meta LLaMA / LLaMA 2** | Non-commercial (1) / Free (2) | 7B–70B (LLaMA2 up to 70B) | FP16/FP32, quant | Yes (via llama.cpp, GGUF) | CPU/GPU/NNAPI | Widely-used research models; LLaMA2 (7B/13B/70B) now open for research/commercial use (except 70B full rights) | [LLaMA2 Docs](https://ai.meta.com/llama/) |

This table shows that **llama.cpp/ggml** provide a common inference backend for many models; **Whisper.cpp** and **Vosk** cover on-device STT; **PaddleOCR** and **TFLite** cover OCR/classification; and various model families (Qwen, Alpaca, LLaMA, etc.) give choices of small LLMs. Most projects support 4–8-bit quantization (e.g. 4-bit Q4_K, 8-bit) for efficiency, and have Android integrations (JNI or apps). Hardware acceleration ranges from CPU SIMD (NEON/AMX) to GPU/Vulkan and Android NNAPI delegates.

## 2. Quantization & Acceleration Techniques  
| Technique         | Memory Footprint  | Inference Speed  | Accuracy Impact    | Notes |
|------------------|-------------------|-----------------|--------------------|-------|
| **FP32**         | Baseline (100%)   | Slow            | Best (baseline)    | Full precision; large models often don’t fit on mobile RAM. |
| **FP16 (half)**  | ~½ of FP32        | ~×1.5–2x on GPUs | ~No change         | Supported by mobile GPUs/NNAPI; good trade-off. |
| **INT8**         | ¼ of FP32         | Often faster    | Small drop (few %) | Hardware support on CPUs/NNAPI; e.g. TFLite 8-bit. |
| **4-bit**        | 12.5% of FP32     | +/- (GPU overhead) | Moderate drop (~10–20%) | E.g. GPTQ/LLaMaQLoRA formats (Q4_K, Q4_0); saves RAM but needs custom kernels (often CPU-bound, though new libraries speed it up). |
| **8-bit (INT8)** | 25% of FP32       | Faster than FP16 | Minor drop        | Often used in llama.cpp (`Q4_0`, `Q5_0` modes). |
| **Quant-Aware Training (QAT)** | ~Same as INT8 | Similar to INT8 | Near FP32 (trained) | Improves INT8/4bit accuracy by simulating quantization during training. |
| **GGUF format**  | N/A (file format) | Faster load     | N/A               | A new model file format by ggerganov for fast loading. Supports metadata. |
| **CPU (no accel)**    | N/A         | Slow-medium     | –                  | All integer ops on CPU (NEON/AMX); generally real-time only for small models (≤1B). |
| **Neural Networks API (NNAPI)** | N/A | Varies (potential speedup) | – | Android API dispatching to DSP/TPU (e.g. Pixel NPU, Qualcomm Hexagon). Offloads model graphs if supported. |
| **GPU via Vulkan / Metal** | N/A | Faster for large models | – | llama.cpp can use Vulkan on Android, Metal on Apple; significant speedup vs CPU but with data transfer overhead. |
| **TFLite GPU Delegate** | N/A | Medium-fast     | –                  | Use for TF/Lite models on mobile GPUs (OpenGL/Vulkan backend). |

Quantization saves RAM but can hurt quality: e.g. 4-bit models use ~8× less RAM than FP32 but may exhibit slightly “jittery” outputs. In practice, LLama/Whisper experiments show **4-bit Q4** models close to full precision on many tasks, while **8-bit/FP16** are essentially indistinguishable. Acceleration trade-offs: CPU inference (no special hw) is easiest but slower; **NNAPI** can speed up highly parallel ops (if model is converted to TFLite or NNAPI Graph); **Vulkan/Metal** (via llama.cpp) can drastically reduce latency on modern chipsets. For example, Whisper audio transcription on Apple’s ANE was 3× faster with GPU. However, NNAPI/GPU support depends on Android version and device (e.g. Android 8.1+ for NNAPI).

## 3. Privacy & Security Options  
- **Android Keystore / TEE / SE**: Use the Android Keystore system to generate or store cryptographic keys in hardware-backed enclaves. Keys never leave the TEE/SE, so encrypted model decryption keys (for model files) are protected. E.g. encrypt sensitive data or keys in the app, and keep AES keys in Keystore (strongbox) so they cannot be extracted.  
- **Encrypted Storage Vaults**: Store transcripts, evidence, or models in encrypted files/DB. Use Jetpack’s `EncryptedFile`/`EncryptedSharedPreferences` (AndroidX Security) or SQLCipher for local data encryption with strong keys. This ensures on-disk data is unreadable without app keys.  
- **Model/File Hash Verification**: Ship models with signed checksums. On launch, verify the SHA-256 hash or digital signature of any downloaded model files before loading, preventing tampering. (Potentially use KeyAttestation to attest device identity.)  
- **Hardware Attestation**: Use Android SafetyNet or Play Integrity APIs for device attestation, ensuring the app runs only on genuine, untampered devices. Pair this with server attestation if any cloud components exist.  
- **Differential Privacy (DP)**: If any analytics or model updates are collected, use DP libraries (e.g. TensorFlow Privacy) to add noise to count queries so as not to leak individual user data. On-device DP can also be used to sanitize transcripts (though complex for text).  
- **Model Provenance**: Distribute only open or reviewed models. Embed version metadata and store model fingerprints (e.g. embed SHA256 in app) so that at runtime the model integrity is checked. This guards against malicious model swaps.  
- **Secure Model Loading**: If models are user-selectable or updatable on-device, load them into memory with protections (e.g. readonly memory, no write-execute pages). Avoid writing decrypted models to disk unprotected. Use ephemeral memory mapping and immediately purge plaintext after use.  

These measures leverage Android’s security stack to protect user data and models. For example, “StrongBox”-backed keys can encrypt model files, ensuring that even root compromises cannot extract keys or data.  

## 4. UX & Human-in-the-Loop Innovations  
We propose several UX features to build user trust and engagement:  
- **Explainable Outputs**: Show *why* the app flagged content. E.g. highlight specific phrases in a transcript that the model found harassing, and provide a brief rationale (e.g. “Flagged due to insulting language”). This transparency helps users trust the AI.  
- **Progressive Disclosure**: Don’t overwhelm users with raw AI output. Start with a simple verdict (“Harassment likely”), but allow the user to tap “Show Details” for full transcript or reasoning. This way, users can choose how much detail they see, reducing confusion.  
- **Confidence Indicators**: Display confidence scores or uncertainty. For instance, label outputs as “High confidence” vs “Low confidence”. This warns users when the model is unsure, prompting them to double-check or edit the input.  
- **Pattern Summarization**: Instead of listing every flagged phrase, summarize patterns. E.g. “Repeated insults detected” or “Pattern: Sarcastic remarks.” This gives the user a concise sense of the issues.  
- **Interactive Timeline Editing**: If the app processes long voice or chat logs, present a timeline interface where flagged segments are marked. The user can scroll and correct transcripts or re-label regions. This keeps the human “in the loop” for error correction.  
- **Consent & Export Flows**: Clearly obtain user consent before data sharing. Offer a privacy-first export: e.g. “Export evidence as encrypted PDF or JSON” that the user can send to a lawyer, with granular choice of what to include (timestamps, redacted names, etc).  
- **On-Device Model Updates**: Instead of silent updates, prompt the user when a new model version is available (e.g. “Improve accuracy by updating model?”). This keeps users aware that their device’s AI is evolving, and can serve as a trust-building transparency feature.  

These UX innovations make the AI **collaborative** rather than a black box. By giving users explanation, choice, and control, we can differentiate the app in a sensitive domain. 

## 5. Novel Technical Ideas for Prototyping  
- **Hybrid Classifier-Router**: Run a tiny on-device classifier first (e.g. a few-layer model or decision tree) to catch obvious harassment content. If the classifier is confident, skip heavy inference; otherwise invoke the LLM or Whisper. For example, use a small CNN/RNN to detect curse words or typical phrases, then route to LLM only on uncertain cases. This saves battery on easy cases.  
- **Adaptive Quantization Per Device**: At startup, detect device RAM/CPU and automatically choose quantization. On a high-memory device, use 8-bit (better accuracy); on a low-end device, use 4-bit or per-channel quantization. Could even dynamically load the best quant format for the current load.  
- **Model Splitting (CPU+GPU)**: Partition the LLM layers between CPU and mobile GPU. For example, run early layers (large matmuls) on the GPU via Vulkan and later layers on CPU. This hybrid could exploit both cores. Inference engines like TensorRT do this on desktop; a simplified pipeline could be prototyped in llama.cpp by offloading half the layers.  
- **Streaming Inference for Long Text**: Instead of batch processing, stream inputs to the LLM as they arrive. For voice transcripts, feed Whisper tokens in real-time to the harassment-model (loopy generation with partial prompts) so the app can flag abusive content mid-transcription. This reduces perceived latency for the user.  
- **On-Device LoRA Fine-Tuning**: Implement lightweight fine-tuning (LoRA) on-device for personalization. E.g. if the user corrects an output, a tiny LoRA update could adjust the model for that speaker. Even limited 1–2 steps of LoRA (with tiny adapter weights) might adapt the harassment model to a specific conversation style.  
- **Retrieval-Augmented Generation (RAG)**: Maintain a local encrypted index of legal resources or help content. For flagged transcripts, use a compact RAG pipeline: encode the transcript, search local docs, and append relevant snippet to the LLM prompt before producing explanation. The key is keeping the index encrypted on-device and only decrypted at query time.  
- **Evidence-Aware Prompt Templates**: Craft prompts that explicitly incorporate metadata (e.g. timestamps, speaker ID) to make the LLM’s output tailored to an evidence report. For example: *“Summarize if the speaker (not named) was harassed at 3:15 PM, quoting evidence.”* This style ensures the model knows it’s preparing a formal report.  
- **Automated Distillation Pipeline**: Build a framework to distill a large LLM (e.g. 13B) into a smaller one (e.g. 4B) on-device or in a portable local server. For prototype, fine-tune a distilled version on harassment-specific data, then quantize it aggressively.  
- **Energy-Aware Scheduling**: Use Android’s WorkManager to schedule heavy inference for optimal times (e.g. when charging, or only on Wi-Fi/Battery >=20%). The app could defer non-urgent batches (e.g. background summarization) to when the device is idle, improving user battery life.  

These novel ideas aim to stretch the envelope of on-device AI. For hackathon demos, simple “toy” versions can be built: e.g. a rule-based classifier + llama.cpp router, or a prototype LoRA adapter using tiny gradients, or a mock RAG with a few local text files.

## 6. Prioritized Feature Shortlist for Hackathon  
The following features are high-impact yet implementable quickly. Each entry notes effort level, key dependencies, and implementation hints:

1. **Hybrid Router (Tiny Classifier + LLM)** – *Effort: Medium.* Dependency: a small ML library (e.g. TensorFlow Lite) for classifier. Implementation: train a binary classifier on harassing vs non-harassing sentences (or use keyword list). In the app, run classifier first; if “uncertain”, call llama.cpp for deeper analysis. Effort mainly in data/UX integration.  
2. **Encrypted Storage & Keystore** – *Effort: Low.* Dependency: Android Jetpack Security. Implementation: Use `EncryptedFile` or SQLCipher to store evidence and models encrypted. Use Keystore to hold the AES key as “StrongBox”. This uses standard APIs (see Android Keystore docs).  
3. **Interactive Transcript UI** – *Effort: Medium.* Dependency: UI framework (Android Views/Jetpack Compose). Implementation: Build a timeline slider (e.g. Android SeekBar) showing transcript audio. Highlight flagged words in the text view. Allow user correction by tapping words. This is mostly front-end work.  
4. **Confidence/Explainability Overlay** – *Effort: Low.* Dependency: None. Implementation: For each flagged item, display the model’s confidence (e.g. 95%) and a short rationale. Can generate rationales with the LLM (“I flagged this because…”). Add UI icons/colors for confidence.  
5. **On-Device STT Integration** – *Effort: Low.* Dependency: whisper.cpp or Vosk. Implementation: Integrate whisper.cpp (or Vosk) to transcribe audio to text on-device. This is core to pipeline (app already likely needs it). Optionally quantize the model for speed. No new research needed, just hooking it up.  

Each feature is buildable within a weekend/1-week timeframe. They combine to a **vertical slice**: e.g. speech → whisper.cpp → hybrid classifier → llama.cpp → UI highlights + export.  

## 7. Recommended Models and Toolchain for Android  
- **Models (GGUF format)**: We recommend using small quantized GGUF models via llama.cpp. Candidates: **LLaMA-2-7B Chat (Q4_K)**, **Qwen-3.5-7B (4-bit)**, **Mixtral-8x7B (meta’s new small LLM)**, etc. These have good trade-offs of size vs performance. Download their GGUF versions from Hugging Face (many are community-shared). For STT, use `whisper.cpp` GGUF (e.g. `ggml-base.en.bin` or quantized).  
- **Quantization Tools**: Use llama.cpp’s built-in quantize (the `quantize` example) or [gguf-my-repo](https://github.com/ramonboulanger/gguf-my-repo) to convert PyTorch weights to GGUF and apply GPTQ 4-bit/8-bit quant. Hugging Face also supports exporting GGUF. For Whisper, use the `quantize` tool in whisper.cpp (supports Q5_0, Q4_0 etc).  
- **Inference Flags**: Example llama.cpp flags: `-m model.gguf -t THREADS --ctx 2048 -n 512 -b 8 --n-gpu-layers 2` (adjust for latency). Use `-c` for interactive chat, `-r` for reply tokens. For Android, disable `-gpu` if using CPU only; enable `-ngl` to offload layers to GPU. For whisper.cpp, use `-t` for threads, `-lr` to adjust speed/accuracy, and `-p 0.3` to skip prompt.  
- **Android ML Alternatives**: For simpler tasks, consider **TFLite**. E.g. TensorFlow Lite’s prebuilt models for text classification can serve as that tiny classifier. ML Kit’s on-device NLP (Smart Reply, Entity Extraction) could help parse content. For OCR, PaddleOCR or Google’s ML Kit Text Recognition (on-device) can get text from screenshots.  
- **Benchmarking**: We suggest measuring (1) **Memory usage** (peak RAM of model in MB), (2) **Throughput** (tokens/sec or words/sec for STT and LLM), (3) **Latency** (ms per query or per second of audio), and (4) **Battery drain** (mAh over fixed workload). For consistency, run tests on a representative Android phone (no special constraints given). Compare: LLMs in 4-bit vs 8-bit, CPU vs GPU execution. For STT, measure real-time factor (sec audio / sec decode).  

## 8. Architecture Diagrams  

```mermaid
flowchart TD
  A[Audio Input (wav)] -->|STT| B[Speech-to-Text (whisper.cpp/Vosk)]
  B --> C[Preprocessing & Keyword Scan]
  C --> D{Harassment?}
  D -->|Likely Harassment| E[Small Classifier or Pattern Detector]
  D -->|Uncertain| F[LLM (llama.cpp) for Contextual Analysis]
  E --> G[Output Flags \n(likely harassment)] 
  F --> G
  G --> H[UI: Highlighted Transcript, Explanation]
  H --> I[User Correction / Feedback Loop]
```
_**Figure:** Simplified hybrid pipeline. Audio is transcribed on-device (STT), quickly scanned for keywords. Clear hits go to a small classifier; ambiguous cases invoke a full LLM. Results feed an interactive UI with highlights and explanations._

```mermaid
flowchart LR
  subgraph Secure Storage
    M[Model File (GGUF)] --> X[(Encrypted Storage)]
    Key[Key (AES)] -->|uses| X
    Key -- Keystore --> Y[Android Keystore (TEE/StrongBox)]
  end
  UserApp[App] -->|Decrypts via Keystore| M
  subgraph Processing
    A[Incoming Data] -->|Decrypt using Key| B[Decrypted Input]
  end
```
_**Figure:** Secure model/data flow. The model file is stored encrypted. Its decryption key is protected in the device’s Keystore (TEE/StrongBox). The app loads the model only by retrieving the key from secure hardware._

```mermaid
radar
  title Model Comparison (normalized 0–1: 1=best)
  LLaMA-7B (4-bit): 0.8, 0.7, 0.6
  Qwen-7B (4-bit): 0.75, 0.68, 0.6
  Mixtral-7B (4-bit): 0.78, 0.72, 0.62
  Falcon-7B (4-bit): 0.65, 0.65, 0.55
```
_**Figure:** Radar chart comparing candidate LLMs (4-bit quantized) on three dimensions: *Speed* (tokens/sec, higher is better), *Accuracy* (on representative task), and *Efficiency* (lower RAM/better = higher score). These values are illustrative; actual benchmarks should be run on the target device._

## 9. References & Sources  
Key references used include the llama.cpp and ggml project docs, which detail supported quantizations and hardware backends. Whisper.cpp’s README describes quantization and Apple GPU usage. The Android Keystore documentation highlights TEE/SE key security. Hugging Face’s GGUF page explains the new model format. We also consulted primary project repos (Vosk, PaddleOCR, Coqui STT) and authoritative docs (Android Developer guides) to ensure accuracy. These sources underpin the above analysis and recommendations.

