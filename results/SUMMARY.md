# Slot summaries — laptop proxies only

Latest full applicable-data run for each candidate/VAD setting. Five batches after one warmup; committed metadata/trials preserve versions and hashes. See component CSVs for all languages and p95.

Weighted ranking is withheld when mandatory dimensions or hard gates are unknown. No battery, thermal-throttling or native Android measurements exist. Model bytes exclude runtimes/app dependencies. Not all candidate families have adapters.

## classifier

| Candidate | Quality metric / value | Latency metric / value | Peak MB | Model MB | Weighted score |
|---|---|---|---:|---:|---|
| rules | macro_f1: 0.9850552306692656 | ms_per_message: 0.04837999844716655 | 129.86 | 0.002 | pending: missing integration_effort, battery_thermal, licence_openness |
| rules-tfidf-ensemble | macro_f1: 0.9938271604938271 | ms_per_message: 0.22049444313678476 | 145.43 | 1.374 | pending: missing integration_effort, battery_thermal, licence_openness |
| tfidf-logreg | macro_f1: 0.40631313131313135 | ms_per_message: 0.2143455565803581 | 146.26 | 1.374 | pending: missing integration_effort, battery_thermal, licence_openness |

Top 3: withheld unless complete score inputs and gates exist. Planned options: tfidf-logreg, fastText, CNN, BiLSTM, DistilmBERT, multilingual-MiniLM, mDeBERTa-v3-small, MuRIL, IndicBERT-v2, XLM-R-base, LaBSE-logreg, multilingual-e5-small-logreg, EmbeddingGemma-logreg, rules, rules-tfidf-ensemble, zero-shot-Qwen2.5-0.5B, zero-shot-Qwen2.5-1.5B, zero-shot-Qwen2.5-3B, zero-shot-Qwen3-0.6B, zero-shot-Qwen3-1.7B, zero-shot-Qwen3-4B, zero-shot-Llama3.2-1B, zero-shot-Llama3.2-3B, zero-shot-Gemma3-1B, zero-shot-Gemma3-4B, zero-shot-Gemma3n-E2B, zero-shot-Gemma3n-E4B, zero-shot-Phi3.5-mini, zero-shot-Phi4-mini, zero-shot-SmolLM2-1.7B, zero-shot-SmolLM3-3B, zero-shot-Sarvam1-2B, zero-shot-Airavata

## extraction

| Candidate | Quality metric / value | Latency metric / value | Peak MB | Model MB | Weighted score |
|---|---|---|---:|---:|---|
| template-extractive | field_f1: 0.9935064935064936 | ms_per_thread: 0.1968500270907368 | 121.31 | 0.000 | pending: missing integration_effort, battery_thermal, licence_openness |

Top 3: withheld unless complete score inputs and gates exist. Planned options: Qwen2.5-0.5B, Qwen2.5-1.5B, Qwen2.5-3B, Qwen3-0.6B, Qwen3-1.7B, Qwen3-4B, Llama3.2-1B, Llama3.2-3B, Gemma3-1B, Gemma3-4B, Gemma3n-E2B, Gemma3n-E4B, Phi3.5-mini, Phi4-mini, SmolLM2-1.7B, SmolLM3-3B, Sarvam1-2B, Airavata, template-extractive

## ingest

| Candidate | Quality metric / value | Latency metric / value | Peak MB | Model MB | Weighted score |
|---|---|---|---:|---:|---|
| whatsapp-export | not measured: unknown | ms_per_export_fixture: 0.005621000193059444 | 120.73 | 0.000 | pending: missing accuracy, integration_effort, battery_thermal, licence_openness |

Top 3: withheld unless complete score inputs and gates exist. Planned options: share-sheet, media-picker, whatsapp-export, MediaProjection, notification-listener

## integrity

| Candidate | Quality metric / value | Latency metric / value | Peak MB | Model MB | Weighted score |
|---|---|---|---:|---:|---|
| Ed25519 | tamper_detected: 1.0 | verify_10000_seconds: 18.550598700065166 | 124.26 | 0.000 | pending: missing integration_effort, battery_thermal, licence_openness |
| HMAC | tamper_detected: 1.0 | verify_10000_seconds: 0.08221769984811544 | 123.79 | 0.000 | pending: missing integration_effort, battery_thermal, licence_openness |
| Merkle-tree | tamper_detected: 1.0 | verify_10000_seconds: 0.04065999994054437 | 124.49 | 0.000 | pending: missing integration_effort, battery_thermal, licence_openness |
| SHA256-chain | tamper_detected: 1.0 | verify_10000_seconds: 0.017514199949800968 | 122.70 | 0.000 | pending: missing integration_effort, battery_thermal, licence_openness |

Top 3: withheld unless complete score inputs and gates exist. Planned options: SHA256-chain, Merkle-tree, HMAC, Ed25519

## key

| Candidate | Quality metric / value | Latency metric / value | Peak MB | Model MB | Weighted score |
|---|---|---|---:|---:|---|
| Argon2id-laptop | not measured: unknown | unlock_seconds: 0.27287539979442954 | 188.52 | 0.000 | pending: missing accuracy, integration_effort, battery_thermal, licence_openness |

Top 3: withheld unless complete score inputs and gates exist. Planned options: Keystore-Argon2id, Argon2id-laptop

## language

| Candidate | Quality metric / value | Latency metric / value | Peak MB | Model MB | Weighted score |
|---|---|---|---:|---:|---|
| script-heuristic | accuracy: 0.8444444444444444 | ms_per_message: 0.01292111248605781 | 123.81 | 0.000 | pending: missing integration_effort, battery_thermal, licence_openness |

Top 3: withheld unless complete score inputs and gates exist. Planned options: fastText-lid, lingua, CLD3, script-heuristic

## linking

| Candidate | Quality metric / value | Latency metric / value | Peak MB | Model MB | Weighted score |
|---|---|---|---:|---:|---|
| sender-time-rules | grouping_recall: 0.30666666666666664 | ms_per_1000_messages: 3.341999836266041 | 121.92 | 0.000 | pending: missing integration_effort, battery_thermal, licence_openness |

Top 3: withheld unless complete score inputs and gates exist. Planned options: sender-time-rules, multilingual-e5-small-clustering, MiniLM-clustering, LaBSE-clustering, LLM-grouping

## normalization

| Candidate | Quality metric / value | Latency metric / value | Peak MB | Model MB | Weighted score |
|---|---|---|---:|---:|---|
| No measurements | unknown | unknown | unknown | unknown | pending |

Top 3: withheld unless complete score inputs and gates exist. Planned options: IndicXlit, rule-transliteration, none

## ocr

| Candidate | Quality metric / value | Latency metric / value | Peak MB | Model MB | Weighted score |
|---|---|---|---:|---:|---|
| No measurements | unknown | unknown | unknown | unknown | pending |

Top 3: withheld unless complete score inputs and gates exist. Planned options: MLKit-Latin, MLKit-Devanagari, Tesseract-eng, Tesseract-hin, Tesseract-mal, PaddleOCR, RapidOCR-ONNX, EasyOCR, docTR, Surya, SmolVLM, Qwen2.5-VL-small, Florence2, MLKit-Tesseract-hybrid, MLKit-Paddle-hybrid

## pdf

| Candidate | Quality metric / value | Latency metric / value | Peak MB | Model MB | Weighted score |
|---|---|---|---:|---:|---|
| ReportLab | not measured: unknown | render_50_incidents_seconds: 0.12322129961103201 | 150.54 | 0.000 | pending: missing accuracy, integration_effort, battery_thermal, licence_openness |

Top 3: withheld unless complete score inputs and gates exist. Planned options: Android-PdfDocument, OpenPDF, PDFBox-Android, WebView-PDF, ReportLab, WeasyPrint

## storage

| Candidate | Quality metric / value | Latency metric / value | Peak MB | Model MB | Weighted score |
|---|---|---|---:|---:|---|
| SQLite-AES-GCM | not measured: unknown | insert_10000_seconds: 1.0085207996889949 | 128.33 | 0.000 | pending: missing accuracy, integration_effort, battery_thermal, licence_openness |

Top 3: withheld unless complete score inputs and gates exist. Planned options: Room-SQLCipher, SQLite-AES-GCM, Realm-encrypted

## stt

| Candidate | Quality metric / value | Latency metric / value | Peak MB | Model MB | Weighted score |
|---|---|---|---:|---:|---|
| No measurements | unknown | unknown | unknown | unknown | pending |

Top 3: withheld unless complete score inputs and gates exist. Planned options: whisper.cpp-tiny-q5_1, whisper.cpp-tiny-q8_0, whisper.cpp-base-q5_1, whisper.cpp-base-q8_0, whisper.cpp-small-q5_1, whisper.cpp-small-q8_0, faster-whisper-small, faster-whisper-large-v3-turbo, IndicWhisper, IndicConformer, Vosk-hindi, Vosk-english, sherpa-onnx, Meta-MMS, Android-offline-SpeechRecognizer, faster-whisper-base
