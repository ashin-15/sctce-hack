# Sakshi offline benchmark harness (partial implementation)

This repository is a runnable starter and an honest execution ledger, NOT a completed evaluation of every requested model. `benchmark.md` lists milestones and gaps. `results/candidate_inventory.csv` tracks the full requested families, STT VAD variants, LLM quant/runtime/output-mode plans and classifier export plans. `pending_adapter` is NOT a legitimate unsupported-model skip. No victim data is used. The 600 text records are orthographic variants of 14 semantic templates across six language categories, not 600 independent semantic examples; production model selection needs a much more diverse adjudicated dataset.

## Setup

From the repository root, Python 3.12 is recommended:

```powershell
$envDir = Join-Path $env:LOCALAPPDATA 'SakshiBench\venv'
uv venv --python 3.12 $envDir
$python = Join-Path $envDir 'Scripts\python.exe'
uv pip sync --python $python --link-mode copy --require-hashes requirements-lock.txt
```

Use a new environment directory outside OneDrive: this host repeatedly denied package replacement inside its synced workspace, even in copy mode. Windows commands below use PowerShell's `$python` variable from setup. On Linux/macOS create a separate virtual environment and provide Latin/Devanagari/Malayalam TrueType fonts; the current font discovery needs adaptation for non-Windows systems. Core screenshot rendering requires HarfBuzz/FreeType from optional requirements. Binary models, audio and screenshots are deliberately not committed.

## Preparation (network permitted only here)

```powershell
& $python -m bench prepare-audio
& $python -m bench prepare-whisper
& $python -m bench data
& $python -m bench validate-data
& $python -m bench registry
```

Run preparations sequentially: the download ledger is not concurrency-safe. FLEURS CC-BY-4.0 attribution and transformations are recorded in `data/audio_provenance.json`; manifests retain source transcripts/IDs and hashes. Download cap is 5,000,000,000 bytes for explicitly prepared model/data assets, excluding Python dependencies. Three FLEURS test parquet files are downloaded; sixty paired WAV fixtures concatenate full utterances with no word truncation. Natural code-mixed/harassment speech is not covered. SHA-256 pins the actual retrieved data; the public parquet ref itself is mutable and is not yet resolved to a commit. Preparation refuses a parquet hash that differs from committed provenance before regenerating ground truth. Whisper preparation pins the verified upstream commit rather than tracking main.

## One command per implemented component (inference offline)

```powershell
& $python -m bench classifier
& $python -m bench language
& $python -m bench ocr
& $python -m bench stt
& $python -m bench stt --vad
& $python -m bench extraction
& $python -m bench linking
& $python -m bench storage
& $python -m bench integrity
& $python -m bench pdf
& $python -m bench ingest
& $python -m bench stacks
& $python -m bench report
& $python -m unittest discover -s bench/tests -v
```

`--candidate NAME` selects an implemented candidate. Unimplemented names produce a nonzero exit and a pending entry, never fabricated results. `--limit N` performs an explicitly tagged smoke subset; default is the full applicable held-out set. `--ram-cap-gb 4` is a post-observation RSS guard on the laptop, NOT an OS-enforced cap or Android simulation. Native Android battery-saver/kill/restart tests need a native app and real hardware. Commands append new timestamped runs; report generation selects the latest full run, keeping subsets separate.

## Current adapters

- OCR: bundled RapidOCR PP-OCRv4 ONNX; Latin/romanized test subset only. Hindi/Malayalam are excluded because these particular weights do not support those scripts. This says nothing about other Paddle/RapidOCR multilingual models. OCR compares full visible text including UI/sender/timestamp; extraction uses sender/timestamp substring presence and is only an optimistic proxy, not exact structured parsing.
- STT: extra faster-whisper **base** CPU int8 candidate with/without packaged Silero VAD. It does not stand in for whisper.cpp or the requested small/large models. Greedy beam=1, temperature=0, four CPU threads, known language passed to all clips.
- Classifier: char TF-IDF logistic regression, authored multilingual rules, max-score ensemble. Same seed and training split; thresholds/operating points selected only on validation. Report test precision/recall at validation-selected targets; never imply test target attainment. `none` is the complement of six harassment labels, not an independent positive head. Multilabel ECE is pooled positive-probability calibration. Rules were authored with knowledge of the synthetic generation templates, so this is a biased fixture sanity check.
- Language: authored script/lexicon heuristic extra baseline; fastText/lingua/CLD3 and normalization comparisons pending.
- Extraction: source-bound template, not an LLM. Date/platform/sender are already provided in the fixture input; field F1 is therefore optimistic. Quote verification is exact case-sensitive substring comparison. Human summary review is pending.
- Linking: sender/time rules with pairwise grouping precision/recall; current case IDs are arbitrary synthetic fixtures. Escalation trends and realistic case annotations pending.
- Storage/crypto: laptop SQLite with fresh per-row AES-256-GCM nonces and row-ID AAD, Argon2id (64 MiB, t=3, lanes=1), SHA-256 chain, domain-separated Merkle tree, indexed HMAC and Ed25519. Fixture keys are ephemeral and never written. Chains/Merkle require a trusted root kept outside mutable storage; do not provide authentication by themselves. The current Merkle v2 root additionally binds the entry count to prevent duplicate-padding ambiguity; earlier M3a timing rows used the legacy prototype and are retained as historical measurements, not an endorsement of its security. HMAC/signatures tested against altered/reordered/truncated fixtures; external trusted expected count/root is required. This is NOT production Keystore integration or a production complaint store.
- PDF: ReportLab with Indic-capable font and shaping enabled; fifty-incident timing/file size. Font rendering correctness requires native-speaker visual review.
- Ingest: one multiline WhatsApp-style .txt fixture. Native permissions, ZIP, locale variants and attachment handling remain pending.

## Measurement and scoring

Each candidate runs alone in a fresh Python child process; one full warmup, then five full timed batches. Initialization/training time is logged separately as a single load observation, not a median/p95 load benchmark. Peak memory covers load/warmup/trials and retained allocations. Windows uses peak working set plus 5 ms sampled RSS; other platforms currently sample RSS. Per-run hardware/OS/thermal metadata, dependency versions, source/data/model hashes, commit and dirty status are preserved. Thermal and energy are unknown unless a sensor measurement exists. CSV median/p95 are from five **batches**, not per-item tail latency; p95 has low statistical confidence. In some adapters per-language latency is pooled batch throughput, not an isolated language timing. Model bytes exclude runtime/app/dependency bytes. Quantization export/drop measurements are pending.

Workers block Python socket connections and set Hugging Face offline environment flags. Local paths are mandatory for Whisper; bundled RapidOCR assets are checked. This guards Python inference, not OS-level networking. No external inference subprocess or cloud endpoint is used. Configure an OS firewall for a stronger audit.

Weighted scoring uses exactly 35/20/15/10/10/5/5 percent for accuracy/latency/RAM/size/integration/battery-thermal/licence-openness, min-max within a slot after hard gates. RAM over 2.5 GB, licence prohibition, or online inference disqualifies. Unknown gates or missing dimensions withhold the score; they are NOT filled with zero or renormalized away. No defensible full weighted shortlist/stack ranking can be emitted without energy, integration, licence and native measurements. Pareto plots are descriptive laptop proxies only. Stack A–E stay pending until their actual components are implemented and measured; a synthetic proxy pipeline must not be relabelled Stack E.
