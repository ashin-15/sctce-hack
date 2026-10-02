# Sakshi: consent-based Android multimodal evidence pipelines

**Research date:** 2 October 2026

**Scope:** Passive notification signals and user-mediated image, screenshot, audio/voice-note, video, PDF/document and exported-conversation imports; Android preprocessing; local OCR/STT/inference; resource and quality evaluation; hackathon scope.

**Status:** Source-backed research and implementation design, not implemented Android support, measured Sakshi benchmarks, provider approval or legal clearance.

## 1. Executive recommendation

Use **two acquisition lanes feeding one immutable, encrypted evidence store**:

```text
A. Opted-in notification observations
   -> exposed text + metadata + conservative media hints
   -> lightweight parsing and deferred local text review

B. User-selected share / picker / supported export
   -> validate and preserve the exact received artifact
   -> bounded decoding / page or frame selection / audio conversion
   -> local OCR or STT

Both -> evidence-linked text/regions/time ranges
     -> optional classifier or retrieval
     -> user correction and confirmation
     -> timeline and explicitly reviewed export
```

**Do not build passive original-media extraction.** A notification saying “Photo” or “Voice message” is a signal, not the underlying pixels/audio. Use ordinary supported sharing or user-selected files. Respect private storage, secure windows, export restrictions, disappearing content and View Once protections.

**Smallest useful hackathon:** user-selected JPEG/PNG screenshots/images with OCR; a short imported audio/voice-note file with offline transcription; text/manual notes; original-file preservation for supported video/PDF files; explicit reviewed report. One tested text-conversation export parser is a next-step addition. Add bounded PDF extraction and sparse video OCR/audio transcription by reusing those primitives, not by adding a large vision-language model.

Recommended first AI stack:

- **Bundled ML Kit Text Recognition v2** for Latin and, if needed, Devanagari. Models are immediately available offline. Native Malayalam script is **not** covered by these recognizers. [O1][O2]
- **Tesseract 5 + selected tessdata_fast packs**, especially `mal`, as a Malayalam/other Indic OCR candidate requiring Android JNI integration and accuracy tests. [O4][O5]
- **whisper.cpp tiny or base multilingual**, one at a time, for a short imported speech file. Its official Android sample recommends tiny/base. [S1][S2] Use an English-only variant only for an explicitly English-only mode.
- **Platform media APIs**, or a pinned compatible Media3 Inspector build, for selected-file decoding/frame extraction. Demuxing encoded audio is not decoding PCM. [A6][A7][A8]
- **Platform PdfRenderer**, embedded text extraction on API 35+, page OCR fallback when appropriate. No PDF parsing model is required for basic evidence handling. [A9]
- **No LLM/VLM/embedding dependency required for the core demo.** First establish reliable extraction, provenance and review. Optional text inference can use one selected ONNX Runtime/LiteRT model after task-specific validation.

## 2. Research method, evidence strength and observed environment

Primary Android/Google documentation, official engine repositories and model publishers were prioritized. Published model sizes and runtime descriptions were checked against fetched bodies and read-only Hugging Face/GitHub metadata. References below distinguish **documented API capability**, **published measurements/metadata**, **design inference** and **unmeasured targets**.

A connected device reported **SM-S928B, Android 16 / API 36, SoC SM8650, arm64-v8a** through read-only platform properties. The device serial is intentionally not recorded. No app/model was installed, no personal media/notification was read, and no OCR, ASR, decoder, PDF or battery workload ran on that phone. This establishes a potential test platform, **not performance or acquisition support**.

At task start, the inspected workspace contained research files and an Android project archive; no active Sakshi application build was verified here. The archive was not inspected or modified in this investigation. No dependencies are presumed installed. Desktop tools/model metadata do not establish Android compatibility, especially for native libraries and quantized exports.

Related reports:

- `research/android-evidence-acquisition-specification.md`: detailed notification, import, app and policy boundaries.
- `research/whatsapp-view-once-feasibility.md`: current rendered official WhatsApp evidence, including the documented native-recorder audio exception and no original-item extraction contract.

Older general research tables are not treated as current benchmarks. This report corrects common conflations: ML Kit inference versus telemetry, mobile-model labels versus phone timing, demuxing versus decoding, speaker labels versus identity, and NNAPI availability versus modern acceleration recommendations.

## 3. Category A: automatic/passive signals after explicit opt-in

“Passive” means observation of an authorized public notification surface, not silent consent or unrestricted monitoring.

### 3.1 Capability contract

| Signal | Official Android surface | What Sakshi can preserve | What it cannot establish |
|---|---|---|---|
| Notification text | NLS; standard extras; MessagingStyle messages | Exact exposed excerpt/label with source field, snapshot and parser version | Full conversation, untruncated message, attachments or all outgoing text |
| Notification metadata | StatusBarNotification / Notification | Posting package/profile, key/id/tag/channel/group/summary state, callback time | Verified sender identity, account identity or a source message id |
| Media-message indicator | Exposed text/MIME/style/optional image field | “Photo,” “Video,” “Voice message” or MIME hint if actually supplied | Attachment availability, View Once status from a generic label, image contents or spoken words |
| Timestamps | `sbn.postTime`; app `when`; structured message timestamp | Platform posting time and publisher-supplied time separately | Trusted sent/read/expiry time; source-delivery latency without ground truth |
| Conversation metadata | MessagingStyle title/group/Person; shortcut/channel/ranking when published | Optional display title, sender hint and app-scoped conversation association | Full membership, stable cross-app chat id, authenticated person, every account/profile |
| Rich image preview | Publisher-provided permitted bitmap/icon | Optional exposed preview, with explicit retention scope and its own provenance | Original full-resolution image; distinction between avatar and attachment without tested semantics |
| Optional message data URI | MessagingStyle `dataUri` plus legitimate grant | An explicitly published authorized representation, only if source context permits it | A universal attachment contract or entitlement to protected/private content |

Sources: [A1][A2]. The MVP retains text and metadata, not automatic rich-preview or URI media ingestion. If expanded later, public unprotected data still needs disclosed purpose, authorization, byte limits and provenance. A guessed URI or hidden provider path is not an allowed fallback.

### 3.2 Collection and processing flow

```text
App-posted notification
 -> user-enabled NotificationListenerService
 -> discard non-allowlisted packages/profiles immediately
 -> bounded typed snapshot of standard exposed fields
 -> secure persistence / explicit failure or coverage gap
 -> MessagingStyle-first parsing; generic-label fallback
 -> dedup/repost/summary classification
 -> local text review only on text actually present
 -> user-confirmed event with coverage and source uncertainty
```

- NLS system binding uses `BIND_NOTIFICATION_LISTENER_SERVICE`; user grants notification access in Settings. `POST_NOTIFICATIONS` concerns an app's own alerts, not reading other apps. [A1]
- Wait for `onListenerConnected`; on API 24+ callbacks run on the main thread. No OCR, STT, LLM, archive parsing or network fetch in a callback. Use a bounded queue and a documented encrypted-ingestion/key-access design; failures must not silently fall back to plaintext. [A1]
- Notification key identifies a notification record, not a WhatsApp/Telegram message. Summaries, repeated historic entries, bursts, reactions and inline replies require conservative handling. Hash-based artifact dedup is not proof two identical messages are the same event.
- Do not click/open/play, invoke reply actions, dismiss or mark read. Those are real-world actions outside evidence observation.
- Reconnection can read outstanding notifications, not recover a complete missed-history journal. Removal does not prove deletion, reading, consumption or expiry.
- App privacy controls, muted/foreground behavior, absent posting, OS redaction, managed profiles, OEM background restrictions and listener revocation create missing coverage. Android 15 OTP redaction and private-space stoppage are documented, but do not imply universal encrypted-messenger preview blocking. [A10]
- Preserve `unknown` when sender/type/conversation state is absent. A group title is not a sender; display-name matching is not identity verification.
- Keep manual/import mode usable without notification access. Explain the OS grant is broad even though Sakshi filters collection; it is not an OS-granted per-chat permission.

**Output:** `NotificationObservation`, exposed text spans, optional media hints and tentative user-reviewed associations. No hidden-media OCR/transcript is generated from these signals.

## 4. Category B: user-mediated acquisition surfaces

### 4.1 Acquisition matrix

| Mechanism | Appropriate artifacts | User action and Android authority | Limits and implementation decision |
|---|---|---|---|
| Android Sharesheet | Selected text, images, screenshots, ordinary shareable audio/video, PDFs, exports | Source app constructs share; user selects Sakshi; receiving activity accepts supported MIME/action; URI grant if streams | Source might share a link/text instead of media. A receiving activity cannot force export or grant private access. Preview + explicit Save. [A3] |
| `ACTION_SEND` | One stream and/or text | `EXTRA_STREAM`, `EXTRA_TEXT`, ClipData and actual resolver access | Treat MIME/name/caller/referrer as claims; intent can be forged. No automatic trusted-origin badge. |
| `ACTION_SEND_MULTIPLE` | Selected batch or multi-file export | Stream list/ClipData with grants; user selects which items to retain | Bound count, total bytes and per-item limits; report partial failures; keep relationships without assuming order/completeness. |
| Storage Access Framework | Audio, PDF, TXT/JSON/CSV/HTML export, images/video, supported archives | `ACTION_OPEN_DOCUMENT`; user selects document; result URI grant; persist only when offered | No broad storage permission ordinarily required. Provider can be remote, revocable, slow or non-seekable. Own encrypted copy is preferred. [A4] |
| Generic file/content picker | Same files where a provider exposes them | `ACTION_GET_CONTENT`, optionally multiple selection | Usually an import/transient path, not a promised persistent document grant. “File picker” is not a special cross-app privilege. |
| Photo Picker | Selected images/screenshots/video | `PickVisualMedia` / `PickMultipleVisualMedia`; system grants selected access | Not audio/PDF. Can include cloud-provider media. Explicit HDR transcoding capability can return a changed representation; not enabled by default. Record import path. [A5] |
| Imported screenshot | User-produced screenshot of permitted content | Ordinary file via share/picker | Not complete history or authenticated origin. Secure/protected screens may prohibit capture; no fallback to defeat it. |
| Imported voice note/audio | Source permits external sharing, or user holds a lawful file | Share/SAF, readable stream | Source codec/container varies; no passive cross-app microphone/voice-note intercept. No `RECORD_AUDIO` permission needed merely to decode a selected file. |
| Imported video | Permitted file, ordinary export attachment, user-created recording | Photo Picker/share/SAF | Need container/codec support and bounds. A system recording is not the sender's original. |
| Imported PDF/document | User-selected lawful file | SAF/share | Password, corruption, unsupported encoding and scanned pages are explicit support states. Do not crack passwords or strip protections to gain access. |
| Exported conversation | Official user-facing export/archive where currently available | User generates export in source app, then selects it | Not universal. Missing/expired/private/protected content stays missing. Export locale/schema/version needs fixtures. |

**No broad `READ_MEDIA_*`, legacy broad storage, all-files, accessibility or projection permission is needed for the selected-file MVP.** Request only implemented necessary capabilities. Optional manual narration capture would have its own microphone permission/disclosure and is a user statement, not an acquired messenger voice note. [P1][P2]

### 4.2 Shared import sequence

1. Present selected items, source claims, retention/processing purpose and destination case. Do not auto-save a forged inbound intent.
2. Accept only supported `content://`/other explicitly approved sources; never auto-fetch a shared web URL or use intent-provided paths to read arbitrary private files.
3. Read actual granted streams off the UI thread. Enforce count/byte/time limits while streaming even if provider size is unknown or false. Use app-generated ids/paths; never trust a supplied filename.
4. Copy and hash the **exact bytes delivered to Sakshi**, encrypted in app-private storage. Commit atomically only after complete authenticated save. Keep metadata separately; do not mutate source files.
5. Process from the owned copy, not a transient URI expected to survive later WorkManager execution. Persist legitimate grants only when needed and offered; revoked/moved files must be handled.
6. Distinguish `saved`, `analysis_pending`, `analyzed`, `partial`, `unsupported`, `unavailable` and `failed`. Cancellation preserves a successfully saved original but does not mark incomplete results complete.
7. Derivatives reference parent hashes and transforms. User edits are new versions. Export only after review, redaction selection and conscious destination choice.

**Provider transformations:** Sharesheet resizing/re-encoding, cloud-provider responses and requested picker HDR transcoding can change what is delivered. “Original” in this report means **the immutable received artifact**, not automatically the source camera/WhatsApp-original file. The importer cannot reconstruct an unavailable source original. [A5]

### 4.3 Application-boundary policy

Respect the source's ordinary share/export options and Android grants. Do not enumerate another app's private database/caches, scrape visible history with accessibility, watch transient protected files, restore backups to defeat expiry, obtain sessions/keys, or capture secure content.

WhatsApp View Once requires modality-specific wording: its official Help acknowledges possible **native screen-recorder audio**, but does not provide Sakshi an original-item share/URI or recovery API. A legitimately produced, user-selected recording can follow the independent-artifact audio pipeline; do not start it automatically or claim protected photo/video pixels are capturable. See the focused View Once report for verified official passages.

Ordinary conversation exports may be blocked by source privacy settings. Current WhatsApp Help inspected in the companion report describes Restricted chat replacing Advanced Chat Privacy on latest clients, retaining export restrictions. Do not present alternative clients/downgrades as ways around that choice. Source-specific exports such as Telegram Desktop archives are user-mediated inputs, not Android private-history access.

## 5. Evidence Source -> Acquisition -> Preprocessing -> Local AI -> Output

### 5.1 Complete pipeline matrix

| Evidence source / modality | Acquisition | Preprocessing on received copy | Local component / AI | Output and evidence anchor |
|---|---|---|---|---|
| Passive notification text/metadata | Opted-in NLS | Bounded extraction, field provenance, summaries/reposts, conservative conversation hints | Deterministic parser; optional validated tiny text classifier | Observation + exact text field offsets + independent clocks; no attachment-content claim |
| Ordinary photo/image | Share/Photo Picker/SAF | Header/dimension check; EXIF orientation; bounded bitmap; optional crop/deskew/color normalization as derivatives | ML Kit Latin/Devanagari; Tesseract selected Indic pack; PaddleOCR alternative | OCR spans, polygons, language/script, model score, source coordinate map, user corrections |
| Screenshot/long screenshot | Share/Photo Picker/SAF | Orientation; overlapping tiles; UI region candidates; no destructive autocrop; optional dedup/alignment | Same OCR; conservative layout parser; manual role/time correction | OCR + visual regions + quoted/context boundaries; tentative chat associations, not authenticated messages |
| Ordinary audio/voice note | Share/SAF/readable export attachment | Container probe -> encoded track demux -> decode PCM -> channel-aware mix/resample -> optional VAD/noise derivative | whisper.cpp tiny/base; Vosk selected small model alternative | Original-language transcript with source time ranges, uncertainty, silence/inaudible states, manual speaker labels |
| Ordinary video | Share/Photo Picker/SAF | Metadata; bounded sparse/selected decoded frames; actual PTS where available; audio track decode and source-offset map | Same OCR on sampled frames + same STT on actual audio; optional scene/image embedding later | Frame/text anchors and speech timeline; explicit sampling gaps, no full-scene understanding guarantee |
| Born-digital PDF | SAF/share | Validate/open isolated; native text extraction if available; render verification; page layout/reading-order checks | PdfRenderer text APIs API 35+; optional verified parser on older systems; OCR if text unavailable/unreliable | Page/text/bounds anchors and structured candidate fields; hidden-text/render mismatch flagged |
| Scanned/image PDF | SAF/share | Sequential page render; capped resolution; tile/deskew derivative; page transform | OCR stack by supported script | Per-page OCR + polygons/page coordinates; empty page versus unsupported-script states |
| TXT/JSON/CSV/HTML conversation export | Source's official export then SAF/share | Charset/schema detection; streaming bounded parse; locale/date ambiguity; sanitize HTML; no script execution | Deterministic versioned adapter; optional text review/embedding | Messages as export claims, source byte/record spans, uncertain time/identity, missing attachment references |
| Archive export attachments | User selects supported export archive | Preserve archive; bounded entry parsing; no traversal/symlinks/nested expansion; parse manifest/relationships | Reuse modality pipelines on selected entries | Child artifacts anchored to archive hash/entry and parser schema; not complete-history guarantee |

Each pipeline retains its original received bytes, derived data and user statements separately. A local model output is a suggestion attached to actual available evidence, never a substitute for missing content.

### 5.2 Images: acquisition, metadata, OCR and redaction

**Acquisition:** JPEG/PNG first; WebP/HEIF/AVIF only after device decoder tests. Source apps may intentionally remove EXIF or re-encode. Photo Picker is the narrow visual selection path; SAF/share covers files. No automatic gallery scanning is required. [A3][A4][A5]

**Metadata:** record declared/detected MIME, size, dimensions, EXIF orientation, embedded capture time/GPS/device fields only when present and relevant, and importer/provider claims. Keep sensitive GPS in the encrypted record and exclude it from exports by default. EXIF dates are mutable and not trusted incident/send timestamps. Screenshots often lack useful source-time metadata. AndroidX ExifInterface supports metadata handling, but support/read/write behavior varies by format. Do not use `saveAttributes` on the source evidence. [A11]

**Preprocessing:**

- Inspect dimensions before allocation; reject decompression bombs and excessive pixel counts. Decode scaled/tiled derivatives rather than every 12-MP source at full size. A 4000x3000 RGBA bitmap alone uses 48,000,000 bytes (45.78 MiB), excluding extra copies and engine memory. [A12]
- Apply EXIF rotation/mirroring exactly once and store the transform. Respect alpha, color space and gain-map/HDR metadata; tone mapping is a derivative, not original replacement.
- For screen text, retain glyph resolution. ML Kit recommends characters ideally at least 16x16 pixels, generally no gain above 24x24. Arbitrarily shrinking a long screenshot to one thumbnail can erase evidence text. [O1]
- Try original/normalized ROI only when needed; deskew, contrast, grayscale or thresholding may help some engines and harm Indic glyphs or colored overlays. Compare derivatives rather than overwrite. Tesseract specifically documents deskew, dark-background and segmentation considerations. [O6]
- Record crops/tiles with overlap and a coordinate transform back to the received image. Deduplicate overlapping OCR spans without deleting genuine repeated text.

**OCR choice:** ML Kit bundled models simplify offline Latin/Devanagari extraction; Tesseract gives an explicit Malayalam model and other language packs; PaddleOCR offers detector + recognizer alternatives with current Android deployment documentation. None guarantees correct code-mixed/slang/chat-layout extraction.

**Output:** preserve raw recognized text, line/word regions, script/language hints, engine score if provided, engine/model version, preprocessing and user-edited version. Engine confidence is not a calibrated probability of factual correctness or harassment.

**Screenshot semantics:** OCR alone cannot determine real author, sender/receiver roles, timezone or chronology. Headers, quoted messages, date dividers, forwarded content, reactions and duplicate overlapping screenshots need UI-aware heuristics plus human confirmation. Store the visible wording/region first; do not silently normalize a quoted threat into a message by the screenshot sender.

**Redaction:** user-reviewed suggestions for phone numbers/names/addresses/faces can be generated from text regions or a separately validated detector. Use opaque raster replacement/cropping in a **new export derivative**, not reversible blur, display-only overlays or annotations. Strip sensitive EXIF/XMP, embedded thumbnails, gain maps or auxiliary representations from the export as appropriate, and test the delivered file. Remove corresponding text/transcript/manifest entries from the export too. Keep the private original intact; never auto-share it alongside a redacted preview.

### 5.3 Audio and voice notes: decoding, STT, speaker and noise limits

**Acquisition:** ordinary user-shareable audio from a messenger, a supported export attachment, or an independently selected lawful recording. SAF is appropriate for audio-only files; Photo Picker is not. Source codecs might include AAC/M4A/MP4, Opus/Ogg, AMR, MP3 or WAV. A filename such as `.opus` does not settle container support. Inspect the actual file and target decoder. [A6][A8]

**Decoding path:**

```text
Immutable selected audio/container
 -> choose supported audio track and inspect format
 -> MediaExtractor demuxes encoded samples and source timestamps
 -> MediaCodec decodes to PCM; handle output-format changes
 -> preserve channel/rate/offset metadata
 -> resample to model rate; choose channel/mix policy
 -> bounded float/PCM windows + original-time mapping
 -> VAD and optional denoised derivative
 -> local ASR
```

`MediaExtractor.readSampleData` returns **encoded** samples, not speech-ready PCM. Decoding must respect codec output sample rate, channel count, PCM encoding, encoder delay/padding and presentation timestamps. No decryption/DRM workaround; unsupported encrypted formats remain unavailable. [A6][A7]

Use platform/Media3 support first; do not introduce a large FFmpeg distribution merely to support every container. Any later native codec dependency requires maintenance/security, license, ABI and 16-KB page-size review. Existing Python/FFmpeg examples in whisper.cpp are not the Android acquisition/decoder contract.

whisper.cpp's CLI example expects 16-bit WAV and illustrates 16 kHz mono input; native integration uses model-compatible PCM/float input. Implement real resampling with filtering, not playback-rate changes. Preserve original channels; a stereo channel may carry a distinct speaker or negative-polarity audio. Test downmix against separate-channel processing. [S1]

**Noise handling:** detect silence, clipping, very low level and candidate speech; use conservative VAD with padding. VAD can miss quiet speech and nonverbal events, so keep original timeline and all omitted ranges. Optional denoising must create a labeled derivative; it can remove or fabricate perceptually important cues. No “enhanced words” are promoted to exact original speech. Compare original versus denoised transcript and let the user inspect.

**Transcription:** prefer original-language ASR, not automatic English translation. Save words/segments with model-estimated timing and uncertainty; those timings are not exact legal timestamps. Chunk long audio with overlap and reconcile overlap while preserving absolute source offsets. Whisper's official model card explicitly warns about hallucinated/unspoken words, repetition, uneven accents/languages and high-risk decision contexts. [S3] Abstain on silence/low-quality input, show “inaudible/uncertain,” and require user review of important phrases.

**Speaker information:**

- Container tags, user-described sender and “Speaker A/B” are three different things. None authenticates who spoke.
- Basic whisper.cpp ASR is not robust identity verification or universal diarization. Speaker-turn marker models and stereo heuristics are specialized and must not become person-identification claims.
- Vosk documents optional speaker identification support and a separate model, but speaker embeddings require extra evaluation/consent and can be sensitive biometric data. Do not ship voice identification in the MVP. [S4][S5]
- Prefer user-labeled speakers with explicit `user_asserted` provenance. For multiple/overlapping speakers, permit uncertainty instead of arbitrary attribution.
- Do not infer gender, intent, emotion, dangerousness, credibility or guilt from voice. MediaPipe Audio Classifier is a sound-category tool, **not an STT/diarization engine or harassment detector**. [R4]

**Output:** timestamped transcript linked to the original-file hash and audio intervals; optional speech/noise quality markers; review status; user speaker labels. Original, resampled PCM, denoised audio, raw transcript, corrected transcript and translated text remain separate.

### 5.4 Video: metadata, frames, OCR, audio and synchronization

**Acquisition:** user-selected ordinary MP4 or other tested container. Preserve exact delivered bytes; HDR-compatible picker behavior may intentionally produce a transcoded representation if requested. Do not claim the received file is source-identical. [A5]

**Metadata:** duration, codec/MIME for each track, coded/display width/height, rotation, bitrate/frame-rate hints, audio tracks/channels/rate, color/HDR fields and embedded date if available. All source metadata is mutable; filename/file creation time is not incident time. Variable frame rate makes `frameIndex / nominalFps` unreliable.

**Frames:**

- Start with user-selected positions and sparse samples, e.g. a **design default** of one candidate frame every two seconds for a <=60-second clip, capped at 30. This is not guaranteed coverage of brief text or important events.
- `MediaMetadataRetriever.getScaledFrameAtTime` is a low-dependency option (API 27+). Requested time is in microseconds; sync-frame requests can return a nearby keyframe, not the exact requested moment. If actual timestamp is not exposed, label it approximate/requested. [A8]
- Current Media3 Inspector `FrameExtractor` provides decoded frames at requested positions and transformations asynchronously; its instances require access from a single application thread. Pin a release containing the actual API and test ABI/decoder compatibility instead of assuming newest documentation matches the app's dependency. [A7]
- Sequential MediaExtractor/MediaCodec decoding can provide actual PTS for exact extraction/interval coverage, at greater implementation and processing cost. Sparse random seeks may still decode intervening GOPs. [A6]
- Apply display rotation/aspect/color handling, preserve requested versus actual PTS, and map OCR polygons to frame coordinates. A 4K RGBA bitmap is 31.64 MiB before working copies, so never cache every full frame.
- Optional scene-change/thumbnail/perceptual-hash selection reduces duplicate processing, but may miss short overlays. User-selected extra frames and explicit coverage gaps are essential. Frame sampling is not a complete video review.

**OCR:** reuse image pipeline on sampled frames; group repeated text into intervals only if timing/coverage supports the claim. A duplicate caption is not necessarily a new harassment event. Do not infer unexamined frames or visual actions from transcript text alone.

**Audio:** extract each selected audio track and decode through the audio pipeline. Encoded sample extraction or remuxing is not PCM decoding. A silent video/no audio track yields “no audio track/transcript unavailable,” not fabricated speech. Maintain the source video's track start offset, PTS and encoder delay so ASR times map back to the video timeline.

**Output:** selected frames + actual/approximate time anchors + frame OCR; original-language speech segments with synchronized time ranges; metadata and coverage manifest. A multimodal description model is optional later and must not replace verifiable OCR/STT or make claims about sampled-out intervals.

### 5.5 PDFs and documents: text, OCR and structure

**Born-digital PDF:** prefer a native text layer, then cross-check render/reading order. `PdfRenderer.Page.getTextContents()` is documented from **API 35**, returning text contents ordered left-to-right/top-to-bottom, with localization not changing that ordering. This is not a guarantee of semantic multi-column/Indic reading order. Older-platform extension/backport support must be checked against the concrete API/library, not assumed from another related class. [A9]

**Scanned PDF:** page render -> image OCR. A mixed PDF may require native extraction on some regions/pages and OCR elsewhere; don't deduplicate legitimate repeated text automatically. Invisible/incorrect text layers, tables, signatures, stamps, handwriting and rotated columns need review.

**Platform constraints:** PdfRenderer needs a **seekable file descriptor**, should be constructed on a worker thread, and keeps one page open at a time. Official docs recommend a separate isolated, minimally privileged process for untrusted PDFs. New password handling is version-dependent; require a legitimate user-supplied password only where supported, otherwise mark unsupported. Do not crack or override protection. [A9]

**Preprocessing:** cap bytes, page count, render pixels and selected analysis pages. Render/close sequentially, reuse bounded bitmaps, preserve page-space transforms. Keep original PDF and page rasters separate; raster OCR loses vector/search/signature properties. A different rendering does not establish document authenticity.

**Structured parsing:** extract page/line/field candidates anchored to exact content; user validates tables/dates/entities. Basic PDFs do not require PaddleOCR-VL, an LLM or a layout model. For TXT/JSON/CSV, use bounded explicit charset/schema parsers. For HTML, extract text in a safe parser; never execute scripts, load remote images or open untrusted URLs automatically. Reject XML external entities/macros/active attachments. DOCX/office support is a separate format/license/test task, not silently advertised.

**PDF redaction:** painting a rectangle over a PDF can leave selectable text, embedded objects and incremental-save history. MVP exports should be freshly rendered/redacted page images or an explicitly constructed sanitized report with no original embedded. Validate search/copy, embedded files/metadata and auxiliary text before sharing. This is a redacted derivative, not an unchanged original PDF or guaranteed forensic sanitization.

### 5.6 Exported conversations and attachments

Parse only a **real user-generated supported export fixture** for the source/version/locale. WhatsApp text exports, Telegram HTML/JSON, and other account archives have different semantics and may be unavailable under source privacy settings. No universal messenger parser is promised.

Store export root hash, exact source record/byte spans, display-name/handle claims, date-format/timezone ambiguity and attachment references. Multiline text, quote/reply content, renamed users, edited messages, missing files and locale-dependent dates need fixtures. Do not infer the payload of a missing media reference.

For archives, enforce entry count, total expanded bytes, compression ratio, nesting and parser-time limits. Do not trust paths or extract outside app-generated locations; reject traversal/symlink surprises. Preserve the original archive, create selected child artifacts with archive/entry provenance, and associate attachments only through validated parser evidence and user review. Structured records are parser interpretations, not source-authenticated live messages.

## 6. Local component investigation and selection

### 6.1 Runtime/component matrix

| Component | Role / Android route | Language / format / acceleration | Recommendation and limits |
|---|---|---|---|
| ML Kit Text Recognition v2 | Android SDK, image/PDF-page/video-frame OCR | Bundled or Play-services-downloaded models; Latin, Devanagari, Chinese, Japanese, Korean; SDK selects supported internal execution | First Latin/Devanagari OCR choice. Native Malayalam/Tamil/Telugu/Kannada/Bengali scripts not covered. No guaranteed NPU backend control. [O1][O2] |
| Tesseract 5 | C++ OCR + Leptonica, Android NDK/JNI or carefully reviewed wrapper | Explicit language/script packs; tessdata_fast integer LSTM versus larger/slower best; CPU-first | Malayalam/Indic candidate. Separate layout/preprocessing required; JNI/ABI and pack quality test. Experimental OpenCL is not a universal Android GPU/NPU route. [O4][O5][O6] |
| PaddleOCR | Detector + crop/recognizer + postprocessing; current Android SDK/demo uses ONNX Runtime and OpenCV | Current docs list PP-OCRv6 tiny/small and PP-OCRv5_mobile in Android demo; language-specific v5 recognizers require matching dictionary | Strong alternative, not automatic MVP default. Inspect exported ONNX weights, dictionary and SDK release. “106 languages” is a v5 family claim, not one default model or universal Indic coverage. [O7][O8] |
| whisper.cpp | Native C/C++ Android example/JNI, local ASR | tiny/base multilingual or `.en`; ARM CPU; quantization; upstream Vulkan option subject to Android build/driver testing | Preferred short-file STT candidate. Published memory is not app PSS. No Apple ANE/Core ML speed claim applies to Android. [S1][S2][S3] |
| Vosk | Android AAR/native Kaldi API, file PCM streaming | Per-language small models, CPU route; optional separate speaker model | Alternative for constrained selected languages, not multilingual/code-mixed parity. Listed Telugu small-model WER is poor; presence is not quality. Model licenses vary. [S4][S5] |
| ONNX Runtime | Android Java/C++ runtime for compatible classifiers, embeddings or OCR | CPU default, XNNPACK appropriate models, NNAPI legacy, Qualcomm QNN specific hardware/build path | Useful shared inference engine if a real compatible ONNX model is selected. Export/tokenization/ops/quantization must be tested. AAR does not guarantee universal NPU offload. [R1][R2] |
| LiteRT | Modern Google AI Edge Android runtime; CompiledModel or compatible Interpreter path | CPU/GPU/NPU with backend/model/device constraints | Candidate for exported mobile classifier/vision/embedding. Current docs prefer CompiledModel; don't treat all old TFLite delegate samples as drop-in current API. [R3] |
| MediaPipe Tasks | Android compatible model/task wrapper, e.g. Text Embedder / Audio Classifier | Task-specific model metadata/tokenizer/tensor requirements; delegates depend on task | Optional retrieval/quality markers. Not generic automatic OCR/STT; audio categories are not transcripts. [R4][R5] |
| llama.cpp | Android NDK/JNI, GGUF text inference; multimodal `libmtmd` for supported models | CPU with supported ARM kernels; GPU backend requires actual build; compatible text model plus vision/audio projector | Defer LLM summary/VLM. Ordinary text-only Android example does not prove multimedia JNI plumbing is implemented. Models retain their own licenses. [R6] |
| MiniLM embeddings | Selected ONNX arm64 INT8 file + correct tokenizer/pooling | English all-MiniLM L6 or larger multilingual L12; 384-D vectors | Optional similarity/search after extraction. Not a threat classifier, factuality score, cross-person identity match or dedup proof. [E1][E2] |
| SmolVLM-256M | Publisher image+text English model; compatible GGUF + projector candidate | Model card claims one-image inference under 1 GB GPU RAM; hardware/setup-specific, not Android total RAM | Optional image-description experiment only. Not multilingual evidentiary OCR or deterministic harassment understanding. [V1][R6] |
| Android SpeechRecognizer | System recognition service; on-device factory API 31+ if available | Default service may stream audio to servers; provider/language/input support varies | Not baseline offline arbitrary-file STT. Never silently fall back to remote service. [A15] |

### 6.2 Privacy and licensing nuances

ML Kit Terms explicitly say input images/video/text and resulting outputs are processed on-device and **not sent to Google servers**. The same terms say the SDK may contact Google for model/compatibility updates and send API performance/utilization metrics. Its data-disclosure page lists device/app/version/identifiers, latency/configuration and input/output **size** metadata. [O3]

Therefore:

- “Evidence OCR runs locally” is supported; “ML Kit never communicates or collects anything” is not.
- Prefer bundled OCR for cold-start offline readiness, disclose SDK metrics and audit the real network behavior. A stricter no-telemetry build should consider bundled open-source OCR instead and verify all integrated libraries.
- Selecting a cloud file provider can trigger a download even though Sakshi does not upload evidence. Model installation/update is separate from evidence processing consent. Do not auto-download models/remote shared links without a disclosed control.

Tesseract engine/data are Apache-2.0; verify specific wrapper/Leptonica dependencies and notices. PaddleOCR repository identifies Apache-2.0; current deployment also depends on ORT/OpenCV and exact model distributions. whisper.cpp and llama.cpp are MIT runtimes; Whisper released code/model weights are MIT according to upstream. Vosk engine is Apache-2.0 but its model table includes Apache, LGPL, AGPL and non-commercial variants. MiniLM and SmolVLM inspected cards identify Apache-2.0. ML Kit SDK is governed by Google terms, not assumed open source. No runtime license grants rights over evidence or every model loaded by it.

### 6.3 Multilingual routing

| Target input | ML Kit OCR v2 | Tesseract pack candidate | STT consideration |
|---|---|---|---|
| English | Latin, documented | `eng` | whisper tiny/base or English-specific variant only in English mode; Vosk English candidate |
| Malayalam native script | Not one of supported scripts | `mal` | Multilingual Whisper candidate; no verified small Malayalam Vosk entry in inspected catalog; quality unmeasured |
| Hindi | Devanagari, documented | `hin` | Multilingual Whisper or small Hindi Vosk; source WER is task-specific |
| Marathi | Devanagari, listed | `mar` | Multilingual Whisper candidate; don't infer Marathi quality from Hindi |
| Tamil | Not one of supported scripts | `tam` | Multilingual Whisper candidate; Paddle v5 Tamil OCR family available, Android export test needed |
| Telugu | Not one of supported scripts | `tel` | Multilingual Whisper; small Telugu Vosk is listed but publisher reports 87.9 WER on Fleurs, unsuitable as a quality claim |
| Kannada | Not one of supported scripts | `kan` | Multilingual Whisper candidate; quality unknown |
| Bengali | Not one of supported scripts | `ben` | Multilingual Whisper candidate; quality unknown |
| Hinglish / Romanized Indic | Latin characters can be recognized | `eng` plus evaluated script-specific routing as needed | Spelling/slang/code-switch semantics and language recognition differ from script coverage; no native-language parity claim |
| Mixed-script screenshot / code-mixed speech | Segment/run appropriate actual OCR scripts; manual correction | Selected multiple packs/ROIs, not all packs loaded blindly | Language routing can misfire; save raw ASR and uncertain sections; never silently translate/rewrite insults |

Language pack/model presence means potential coverage, not measured Sakshi performance. Report CER/WER and key-phrase fidelity separately per language and code-mixed condition. Unsupported script should yield user correction/manual note, not confidently empty or hallucinated text.

## 7. Benchmarks: published evidence versus targets

### 7.1 Published resource facts and measurements

**Units matter:** source labels such as MB/Mb are reproduced as publisher approximations. Binary MiB calculations are identified explicitly. Neither model-file size nor publisher “memory” is peak Android process PSS. Compressed model downloads and unpacked installed assets also differ; measure both rather than treat Vosk's catalog size as the full installed footprint.

| Component / artifact | Verified published size/resource | Published performance evidence | What remains unknown for Sakshi |
|---|---|---|---|
| ML Kit bundled OCR | About **4 MB per script per architecture** app-size increase; unbundled about **260 KB per script architecture**, plus externally managed model | Official page says Latin real-time on most devices, others slower; no numeric phone PSS/energy bound | Import/decode/OCR latency, whole-app RAM/CPU, supported hardware execution and battery on selected phone. [O1] |
| Tesseract fast English | **4,113,088 B / 3.92 MiB** pack | Official qualitative fast/best trade-off, not this Android timing | Runtime/JNI/Leptonica overhead, actual peak RAM, screenshot accuracy. [O4][O5] |
| Tesseract fast Malayalam | **5,275,996 B / 5.03 MiB** pack | No relevant phone CER/latency found in inspected sources | Malayalam/code-mixed quality and speed; engine libraries add storage. |
| Tesseract fast Hindi | **1,122,751 B / 1.07 MiB** pack | Pack size only | Not a RAM or quality claim. |
| Paddle PP-OCRv6 recognizer | Tiny **4.4 MB**, small **20.4 MB**, medium **73.3 MB** in module table | v6 table shows no numeric CPU/GPU timings; internal accuracy sets differ from older models | Detector/dictionary/ONNX conversion/runtime overhead and actual Android timing. [O7][O8] |
| Paddle PP-OCRv5 mobile recognizer | **16 MB** general recognizer; some language packs **7.5-14 MB** | Example listed CPU **21.20/5.32 ms**, GPU **5.43/1.46 ms**, but tested on **Xeon Gold 6271C / Tesla T4**, inference only | Not a phone result, not full screenshot detection+all-crops+postprocess time. |
| whisper.cpp tiny | **75 MiB disk**, approximately **273 MB memory** in current README | Android example recommends tiny/base; upstream benchmark tool mainly measures model stages | Whole-file RTF, Java/native/codec buffers, warm/cold latency and power. [S1][S2] |
| whisper.cpp base | **142 MiB disk**, approximately **388 MB memory** | Same | Multilingual/quantized quality and device memory need measurement. |
| whisper.cpp small | **466 MiB disk**, approximately **852 MB memory** | Larger candidate | Not default hackathon model; combined app memory/thermal cost. |
| Vosk small | Typically about **50 MB file / 300 MB runtime**, publisher estimate; English small **40M**, Hindi small **42M**, Telugu small **58M** | English small WER **9.85** Librispeech clean / **10.38** TEDLIUM; Hindi **20.89/24.72** IITM/MUCS; Telugu **87.9** Fleurs | Different datasets/languages aren't comparable Sakshi quality scores; no target-phone energy/RTF. [S4] |
| English MiniLM L6 | **22,713,728 total tensor parameters** (mostly F32); official `onnx/model_qint8_arm64.onnx` **23,026,053 B / 21.96 MiB** | No measured Android latency here | Tokenizer, pooling, activation/runtime overhead; English only; correct truncation. [E1] |
| Multilingual MiniLM L12 | **117,654,272 total tensor parameters**; official arm64 INT8 ONNX **118,412,398 B / 112.93 MiB** | 50-language card, not universal Indic benchmark | Tokenizer adds MB; CPU/ops/quality per target language unmeasured. [E2] |
| MediaPipe Text Embedder | Compatible USE or mixed-precision EmbeddingGemma task; model assets separate | **Current fetched page** reports Samsung **S26 Ultra CPU, 4 threads**, USE **10 ms**, EmbeddingGemma 300M **200 ms** whole-pipeline averages | Not SM-S928B/S24-series result; sequence/warmness/energy not fully specified there. Older indexed Pixel 6/18.21 ms snippet is superseded for this source snapshot. [R5] |
| SmolVLM-256M | HF metadata **256,484,928 BF16 parameters**; weights alone about **489.2 MiB** by 2-byte arithmetic | Card says one-image inference **under 1 GB GPU RAM** | Not total Android PSS, not quantized GGUF+projector size, not phone latency/battery or Indic evidence OCR. [V1] |

Tesseract sizes were verified through official repository metadata at revision `87416418657359cb625c412a48b6e1d6d41c29bd`. HF model metadata and file sizes were queried read-only without downloading weights or running hosted inference. See pinned source revisions in the register.

**No published number above establishes an end-to-end Sakshi benchmark.** In particular, Whisper's model-stage microbenchmark, Paddle's desktop recognition timing, and a model card's GPU RAM figure are not mobile cold-start/file-processing performance.

### 7.2 Expected resource drivers by modality

| Pipeline | RAM/CPU expectation supported by architecture | GPU/NPU opportunity | Storage/battery/latency implications |
|---|---|---|---|
| Passive text observation | Bounded text snapshots/queue; short callback; deferred parser rather than constant inference | Usually unnecessary for metadata | Lowest-compute lane; notification rate, wakeups and retention still matter. No guaranteed zero battery cost. |
| Image/screenshot OCR | Decoded bitmap(s), tiles and recognizer dominate; CPU work grows with pixels/regions | ML Kit internal choice; Paddle/ORT/LiteRT only supported graph/backend | Cold load plus decode/preprocess/OCR; repeated variants cost energy. Tile rather than giant allocation. |
| Audio ASR | Model buffers plus bounded PCM windows; sustained CPU-intensive inference; more threads can heat/throttle | whisper.cpp Vulkan candidate; separately converted models/backend paths need validation; no universal mobile NPU promise | Time scales with speech duration/decode strategy; model storage dominates small clips. VAD helps compute but must preserve omissions. |
| Video OCR+ASR | Decoder surfaces, frame images and ASR buffers; sparse seeks can decode multiple frames | Hardware media codec acceleration is not AI NPU acceleration; optional OCR GPU | Highest preprocessing cost; schedule frame OCR and ASR sequentially; bitrate controls file storage; sampling trades coverage for energy. |
| PDF OCR | One page bitmap + engine; text-layer path can avoid page OCR | Same OCR backend; usually CPU rendering/parsing | Cost grows with pages/pixels, not merely PDF byte size. No all-pages eager cache. |
| Text export parsing / retrieval | Bounded records; model activations only when optional retrieval/classifier loaded | CPU first; embedding GPU/NPU optional | Most benefit comes from streaming parse and short chunks. Vector index itself can be small, raw texts remain sensitive. |
| VLM / generative summary | Text/vision encoder, projector, KV cache, context and image patches, plus app buffers | CPU/GPU with compatible build; NPU not automatic | High startup/thermal cost and hallucination risk. Defer from minimum pipeline. |

### 7.3 Auditable memory/storage arithmetic

These are calculations, not measurements:

- 4000x3000 RGBA image: **45.78 MiB** for one pixel buffer. Three full-size copies already approach **137.3 MiB**, before OCR/model/UI allocations.
- 1920x1080 RGBA frame: **7.91 MiB**. Thirty resident frames would use about **237.3 MiB** pixel memory alone. Process sequentially instead.
- 3840x2160 RGBA frame: **31.64 MiB**. Hardware/decoder textures and intermediate copies add more.
- 16 kHz mono PCM16: **32,000 B/s**, one minute **1.83 MiB**. Float32 model input is twice that: a 30-second window is **1.83 MiB**. Model state, not PCM alone, is the large ASR cost.
- 384-dimensional float32 vector: **1,536 B**. Ten thousand raw vectors: **14.65 MiB**, excluding metadata/index overhead. Embeddings still contain sensitive derived information.
- 10 Mbps video for 60 seconds: **75 MB decimal** before container overhead; source storage can exceed every AI model budget.
- Ideal 4-bit 1B weights are about **500 MB decimal** before quantization blocks, embeddings, vision projector, tokenizer, KV cache and activations. “500 MB model” is not “500 MB app RAM.”

### 7.4 Unmeasured implementation targets, not capability claims

The following are **proposed demo envelopes and acceptance targets**, selected to bound effort and resource use. They are not sourced latency predictions and must be replaced by measured values:

| Job / workload | Initial envelope | Proposed target / failure behavior |
|---|---|---|
| Screenshot/image | JPEG/PNG; <=20 MiB file; <=12 MP input; tile long screenshots | Warm Latin OCR <=2 s for a typical one-screen fixture; peak full-app PSS target <=512 MiB; show progress/cancel and explicit partial OCR when bounds reached |
| Malayalam OCR candidate | Selected `mal` pack; same bounded images | Record actual CER/latency before advertising support; <=5 s typical-page target is only a UX goal, not assumed attainable |
| Audio/voice note | <=60 s analyzed; <=20 MiB supported file; 16 kHz model input; one tiny/base engine | Aim RTF <=1 for tested short English fixtures on reference high-end phone; report actual multilingual RTF separately; full-app PSS target <=700 MiB for base |
| Video | <=60 s, <=100 MiB, tested MP4 H.264/AAC; preserve original; <=30 candidate frames | Reuse OCR/STT sequentially; progress/coverage, no total time guarantee until measured; allow selecting fewer frames/time interval |
| PDF | <=10 MiB, <=20 pages preserved; initially <=5 user-selected pages analyzed | Sequential native text/OCR; per-page timing and partial-page manifest; no fixed time for arbitrary PDF complexity |
| Text export | <=5 MiB / <=10,000 parsed records initially | Streaming parser with bounded line/record and explicit unsupported schema/date ambiguity; no full-history completeness badge |
| Batch | <=10 chosen items; one heavy model job at a time | Queue, cancellation, disk check, thermal pause; no concurrent ASR+OCR+VLM model residency |

If a legitimate selected file exceeds analysis bounds, preserve it only when storage/save policy permits and clearly distinguish **preserved but not analyzed**. Do not silently truncate the original. If safe preservation itself exceeds a bound, stop before committing and ask the user to select a supported artifact or revise scope.

No reliable universal battery percentage or CPU utilization was found for these exact pipelines. Measure incremental energy per workload and sustained throughput instead of inventing a percentage. Example physics only: a hypothetical extra 3 W for 60 s is 180 J / 0.05 Wh; translating that to battery percentage requires actual usable capacity and other loads. This is not a Sakshi energy estimate.

### 7.5 Real-device benchmark protocol

1. **Reference platforms:** use the observed SM-S928B/API36/arm64 for initial integration, plus at least one lower-RAM/older API device before broader claims. A high-end reference does not certify low-end/offline/OEM behavior.
2. **Build/identity:** record app/runtime versions, model/source hashes, ABI, Android/OEM build, backend, quantization, token/context/input settings and thread count. Run a release/profileable build, not debug timing alone. Verify native libraries on 16-KB page-size devices. [A10]
3. **Fixtures:** harmless consenting/synthetic images/screenshots; rotated/dark/long/compressed/Indic text; clear/noisy/quiet/silent/overlapping speech; MP4/variable-rate/no-audio/HDR fixtures; scanned/mixed/corrupt/multicolumn PDFs; real public export schemas with locale variants. Label synthetic data honestly.
4. **Latency:** instrument import/hash/encrypt, first model load, preprocessing, inference, postprocessing and review-ready output separately. Report cold/warm median/p95, failure rates, bytes/pixels/pages/audio duration and video sampling plan. ASR RTF = wall processing time / **source audio duration**, not only retained VAD duration. Also report inference-only timing if useful.
5. **Memory:** sample Java/native heap, decoder/bitmap buffers and process PSS/RSS/peak over the full job. Model file size is not a proxy; mmap/shared pages and GPU allocations need careful interpretation. Record baseline and high-water mark, including model initialization.
6. **CPU/backend:** sweep 1/2/4 inference threads where supported. Record CPU time, wall time, backend graph partitioning/CPU fallback, decoder hardware/software choice and numerical/quality differences. Increasing threads is not assumed faster/more efficient.
7. **Sustained behavior:** after warm-up, repeated batches and a sustained run reveal thermal throttling. Record thermal status, battery level/charging, ambient/device temperature if safely available, background app state and cooling intervals. Cancellation must close codecs/pages/models and release buffers.
8. **Energy:** use system tracing, batterystats and supported power measurements with idle baseline/control workloads. Android Power Profiler ODPM is documented for supported Pixel 6+ hardware and device-level rails, not assumed available on this Samsung. Do not treat coarse battery percent as per-job energy. Battery Historian is no longer actively maintained. [B1][B2]
9. **Offline/privacy:** pre-provision required models, then test with network unavailable. Confirm no evidence upload or remote recognizer fallback. Distinguish SDK metrics/model-update traffic and a cloud-provider download from sensitive-input transmission.
10. **Quality:** OCR CER/WER + critical name/number/negation fidelity, script and layout attribution; ASR WER/CER by language/accent/noise/code-switch plus false words on silence; video text recall with actual-time errors/sampling gaps; PDF reading order/field fidelity; export parse coverage/date/sender mistakes; classifier false positives/negatives/calibration/abstention. No legal-guilt labels.
11. **Repeatability:** use at least five cold runs, warm-up and repeated warm runs, plus a sustained batch; publish exact counts and ranges. Seeds/content/settings and revision manifests accompany results. Do not hide crashes or silently exclude difficult samples.
12. **Source-specific acquisition:** test actual app share/picker/notification fixtures, redaction settings, grants that expire/revoke, unknown-size streams, multiple imports and client export restrictions separately from engine benchmarks.

**Result of this investigation:** documentation/metadata resource evidence and a benchmark plan only. No on-phone workload measurements were produced.

## 8. CPU, GPU and NPU feasibility

- **CPU baseline:** ARM64 builds, conservative thread counts, bounded models, one heavy job, UI-thread isolation. CPU is the most portable inference baseline; numerical equivalence/quality still needs tests.
- **GPU:** whisper.cpp and llama.cpp document Vulkan-related support, but Android build, shader tooling, drivers, actual model graph and memory budget decide success. There is no guaranteed speedup. ML Kit internal accelerator choice is not a public per-model NPU scheduling contract. [S1][R6]
- **Media codec hardware:** hardware-assisted H.264/AAC decoding can reduce preprocessing cost; it is distinct from accelerating OCR/ASR on GPU/NPU. Test codec/profile/container and color output per device. [A6]
- **ORT:** mobile docs recommend CPU for quantized models and CPU/XNNPACK starting points; unsupported-op graph partitioning can slow accelerator paths. QNN can use Qualcomm HTP/NPU on supported Snapdragon platforms but requires appropriate Android build, SDK/backend libraries and compatible graph/quantization. Windows prebuilt QNN packages are not Android AAR proof. [R1][R2]
- **NNAPI:** official Android docs say it was deprecated in Android 15 and future device behavior may often use CPU. Do not make it Sakshi's new universal NPU strategy even though legacy ORT docs list it. [R7]
- **LiteRT:** current Android guide describes CompiledModel as the modern accelerated CPU/GPU/NPU route; Interpreter remains a compatibility path. Vendor/compiler/runtime/model support and release API availability still require tests. [R3]
- **MediaPipe:** select an actual supported task/model/delegate. Do not infer that every task supports GPU or that a text classifier accepts arbitrary ONNX/GGUF weights.
- **Quantization:** save verified weight/storage cost, then compare fidelity/calibration. An INT8 artifact does not guarantee INT8 activations, full NPU placement or zero accuracy loss. Do not quantize evidence originals; quantization concerns model computation.

## 9. Integrity, secure random access, consent and export design

### 9.1 Immutable artifact and derivation graph

For each import: id, source/acquisition category, consent purpose, received time, actual byte count, detected/claimed MIME, SHA-256, storage-encryption reference, claimed origin, provider transformation/unknown status and parsing support state.

For each derivative: parent id/hash, transform/model/runtime/version, dimensions/sample rate/language, image/page coordinate transform or audio/video source-time mapping, extraction coverage, raw scores and review status.

For each claim: exact evidence ids plus page/region/record/time spans, `observed`, `user_reported`, `inferred`, `pattern` or `unknown` status. A finding cannot cite an absent input. Grouping multiple observations is a reviewable association, not identity/authenticity proof.

Hash the **received bytes**, not an unavailable source-original attachment. A stored hash supports integrity checks after acquisition; it does not establish authenticity, guilt, sender identity, trusted incident time or guaranteed court admissibility.

### 9.2 Encrypted storage is not enough if the decoder needs plaintext files

Use app-private encrypted storage, Keystore-backed key protection and authenticated encryption; encrypt snapshots, OCR/STT, indexes and reports too. Unique nonces/authenticated metadata and fail-closed writes are required. Do not log evidence/keys, use analytics/crash attachments containing evidence, or silently upload. [A16]

Many media/PDF libraries require random access or a seekable FD. A naive single whole-file AES-GCM stream cannot be randomly decrypted safely, and writing a decrypted `temp.pdf`/audio cache would undermine the encrypted-vault promise.

Supported design candidates:

- **MediaDataSource (API 23+)** permits position-based media reads; MediaExtractor and retriever integrations can use an app-controlled source. Implement reads over a bounded, authenticated encrypted representation. Test library/thread behavior. [A13]
- **StorageManager.openProxyFileDescriptor (API 26+)** explicitly returns a seekable proxy FD. Android documentation gives on-demand decryption without persisting cleartext as an example. This can satisfy PDF/native consumers when a correctly implemented callback supplies authenticated data. [A14]
- For large originals, use a **reviewed chunked authenticated-encryption design** with a versioned authenticated manifest, per-chunk unique nonces, index/length binding, bounded decrypted buffers and integrity errors propagated to decoders. Do not invent unauthenticated random-access encryption or expose unauthenticated plaintext from a not-yet-verified whole-file GCM stream.
- A small image can be decrypted into bounded RAM and decoded directly. Models are non-evidence assets and may be mapped from a pinned app-private file separately.

The actual adapters, isolated-process FD transfer, cancellation, seek behavior, callback thread/deadlock avoidance and performance must be prototyped. This is a design recommendation, not implemented proof. If secure random access is not ready, **defer the affected PDF/video analysis rather than silently write plaintext scratch**. User can still submit a screenshot or supported image/audio artifact within the verified lane.

### 9.3 Consent boundaries

Separate consent for: notification observation, selected artifact retention, local analysis, optional SDK/model provisioning/metrics disclosure, optional user narration, and export destination. A share-selection gesture is not consent to cloud AI, background scanning or auto-reporting. Explain previously saved artifacts remain retained until the user deliberately manages them; revoking notification access stops new observation.

Local processing is not blanket legal authority to record/retain/disclose intimate or third-party data. Source restrictions, user entitlement, recording/privacy laws and distribution policies are distinct gates. Do not contact the alleged harasser to obtain permission automatically. The product is evidence organization and review, not autonomous legal conclusions.

### 9.4 Backup, redaction and derived-data leakage

Exclude evidence, derivatives and sensitive indexes from unwanted cloud backup, D2D and cross-platform migration using actual target rules and device tests. `allowBackup=false` alone is not an OEM-universal D2D guarantee. Keystore key loss/uninstall/invalidation can prevent recovery; disclose limitations. [A17]

Redacted exports must not retain original bytes, hidden PDF text, EXIF/GPS, unredacted transcripts, sidecar manifests, attachment thumbnails, links or embeddings that disclose omitted details. Text search and preview must obey export scope. Do not embed a complete original as “supporting evidence” in a public report without explicit selection.

Sakshi encryption does not remove the user's original gallery/recorder/cloud-provider copies. Never delete those outside artifacts automatically. Sanitization/processing failures and unsupported redaction should be visible.

### 9.5 Work scheduling

Save/hash/encrypt selected imports promptly while grants are valid, then schedule analysis by **artifact id**, not transient URI. Keep one heavy inference job at a time. Use durable bounded jobs with progress/cancellation and thermal/battery controls; manual job start first. Never run always-on OCR/STT of screens/microphone.

WorkManager's long-running workers use foreground services and on Android 16 can exhaust job quota. A legitimate user-visible FGS path has its own declaration/type/start/runtime limits; it is not an exemption for continuous monitoring. Chunk/resume long work, close resources when stopped and test target API policies. [A18]

## 10. Hackathon minimum and expansion order

### 10.1 Recommended scope tiers

| Tier | Capability | Minimum concrete support | Why / dependency |
|---|---|---|---|
| **P0 required** | Narrow selected imports + provenance | `ACTION_SEND`/MULTIPLE, SAF audio/doc/text, Photo Picker visual; explicit Save; encrypted originals/hashes; manual notes | Acquisition/integrity first; manual mode useful without NLS |
| **P0 required** | Image/screenshot OCR and review | JPEG/PNG; one-screen and bounded long-screenshot tiles; bundled Latin OCR; visible original/regions/corrections | High-value demonstrable local AI with small model footprint |
| **P0 required** | Short audio/voice-note transcription | One tested audio container/codec lane; <=60 s; whisper.cpp tiny/base; original-language transcript with intervals, uncertainty and review | Reuses same text review; no live capture/microphone interception |
| **P0 required** | Local report/timeline | Exact evidence links, user confirmation, manual identity/time corrections and explicit redacted export | Demonstrates evidence organization, not speculative guilt |
| **P0 preserve-only** | Supported PDF/video originals | Selected-file encrypted save + metadata/appropriate preview if securely supported | Honest preservation even before full analysis; don't claim analyzed |
| **P1 add next** | PDF page extraction | API35+ native text on demo device; <=5 selected rendered pages OCR; secure seekable adapter; older platforms OCR fallback after validation | Reuses OCR, no VLM/layout-heavy stack |
| **P1 add next** | Sparse video OCR + audio STT | Tested MP4 H.264/AAC <=60 s; <=30 selected/sparse frames; same OCR/STT; synchronized offsets and gap manifest | Reuses engines, but adds decoder/secure random-access complexity |
| **P1 add next** | Malayalam screenshot OCR | Tesseract `mal` + required JNI; actual Malayalam/code-mixed fixtures and correction UI | Needed for meaningful native Malayalam support; ML Kit alone is insufficient |
| **P1 add next** | One exported conversation parser | One source/version/locale fixture; preserve raw export and optional selected attachments | Do not implement universal parsers from sample strings |
| **P1 optional** | NLS observations | Opt-in narrow package filtering; real source settings/summary/locked-chat fixtures | Complementary metadata, not required for useful import demo |
| **P2 defer** | Semantic retrieval/classifier | One verified ONNX/LiteRT model and tokenizer, calibrated task-specific labels | Embedding similarity is not harassment probability |
| **P2 defer** | VLM/LLM summary/diarization | Separate evaluated/model/consent/backend work | Larger resource and hallucination/identity risk; not needed for modalities |

If a four-modality live-processing demo is required, promote the two P1 PDF/video rows **only after** secure decoder/FD integration and real fixture tests. Do not trade evidence integrity for an unverified “supports everything” badge. The minimum useful hackathon does not need universal native-script support, a backend, always-on monitor or VLM.

### 10.2 Stack to avoid multiplying dependencies

Use **arm64 Android API26+ as a proposed initial implementation floor** if the seekable proxy-FD path is adopted, not as a claim every component needs API26. ML Kit's current setup body requires API23, scaled retriever frames need API27, and native PDF text extraction needs API35. Implement lower-version fallbacks or clearly disable individual analysis branches. Test on the actual API36 reference phone, then a second lower-tier device before expanding the supported range.

- Kotlin + Compose + platform/AndroidX import primitives.
- One image OCR engine initially: bundled ML Kit Latin; add Devanagari only if used. If strict no-SDK-telemetry or Malayalam-first delivery is required, evaluate the Tesseract lane instead of assuming equivalence.
- One ASR runtime/model initially: whisper.cpp tiny or base. Do not bundle both Whisper and Vosk merely for a comparison in production.
- Platform decoding (`MediaExtractor`/`MediaCodec`, retriever) or a consistent pinned Media3 version; no default large FFmpeg package.
- Platform PDF handling on the actual API level; add older-platform text parser only when its Android support/license/maintenance is verified. OCR fallback can remain simpler.
- Shared local text review/statement/timeline layer; classifier is optional until trained/calibrated. No unsupported assumption that Laya or an arbitrary desktop checkpoint already exports/runs on Android.
- One reviewed encrypted store and derivation/provenance graph. Models packaged/pinned separately with license/checksum manifests; evidence never becomes model-update or training data without separate consent.

### 10.3 Acceptance criteria

1. **ACQ-01:** Both passive observations and user-selected artifacts have distinct source/consent/coverage semantics; no passive protected-media extraction.
2. **ACQ-02:** Share/file URI denial, revocation, unsupported codecs/scripts, unreadable/password PDFs and oversize inputs fail visibly without bypasses or remote fallback.
3. **INT-01:** Received original hash remains stable through decoding, OCR, STT, cropping, denoising and redaction. Derivatives retain transforms and parent references.
4. **INT-02:** No plaintext persistent processing cache or evidence log; secure random-access pipeline is tested before PDF/video analysis support is claimed.
5. **AI-01:** OCR/STT/structured output links to source regions/pages/records/intervals, with raw extraction separate from user correction.
6. **AI-02:** Silence, unavailable payload, unsupported script and unknown speaker remain explicit; no hallucinated fill or authenticated-identity claim.
7. **VID-01:** Video frame sampling/time approximations and audio offsets are visible; no complete-review claim from sparse frames.
8. **DOC-01:** Native PDF text/visual rendering disagreements and reading-order/schema/date uncertainties are reviewable.
9. **PRIV-01:** No broad storage/accessibility/projection/microphone permission in import-only core; no auto-open/reply/report/share or background gallery scan.
10. **PRIV-02:** SDK metrics, model provisioning, provider downloads, retention and export are disclosed distinctly; no evidence cloud AI by default.
11. **BENCH-01:** Every numeric phone-support/performance claim has a release-build fixture benchmark with exact runtime/model/backend/configuration. Source tables alone do not qualify.
12. **BENCH-02:** Per-language quality, uncertainty, critical-term errors, thermal behavior, peak PSS and failure counts are published for tested scope; untested scope is labeled.
13. **EXPORT-01:** User reviews destination and sanitized representation; exports exclude originals/unredacted sidecars unless separately selected.
14. **SAFE-01:** Reports describe observed/user-reported/inferred patterns and gaps; no legal guilt/authenticity/admissibility or “all messages protected” promise.

## 11. Source register and reproducibility

All consulted on **2 October 2026**. **Full/relevant body** indicates primary fetched text and relevant passages inspected, including overflow; **metadata** indicates read-only official API/CLI file/model metadata; **related report** references primary evidence inspected earlier in this same research session. Mutable documentation is a research snapshot, not a recommended floating dependency.

### Android acquisition and preprocessing

- **[A1] Full/relevant body:** NotificationListenerService: grant/binding, connection, callbacks, low-RAM/profile limits. https://developer.android.com/reference/android/service/notification/NotificationListenerService
- **[A2] Full/relevant body + same-date related report:** MessagingStyle.Message fields, optional MIME/URI and grant requirements; StatusBarNotification clocks and notification/conversation context detailed in acquisition report. https://developer.android.com/reference/android/app/Notification.MessagingStyle.Message ; https://developer.android.com/reference/android/service/notification/StatusBarNotification
- **[A3] Full/relevant body:** Receive simple data from other apps, ACTION_SEND/MULTIPLE, selected share target and text/stream handling. https://developer.android.com/training/sharing/receive
- **[A4] Full/relevant body:** SAF document/file selection, scoped/persisted grants, provider operations and Android/data/obb restrictions. https://developer.android.com/training/data-storage/shared/documents-files
- **[A5] Full/relevant body:** Photo Picker, visual-only selection, cloud media, lifetime/persistence and opt-in HDR transcoding. https://developer.android.com/training/data-storage/shared/photo-picker
- **[A6] Full/relevant body:** MediaExtractor encoded samples and times, MediaCodec decode/PCM behavior, codec/container support. https://developer.android.com/reference/android/media/MediaExtractor ; https://developer.android.com/reference/android/media/MediaCodec ; https://developer.android.com/media/platform/supported-formats
- **[A7] Full/relevant body:** Media3 Inspector frame extraction and encoded sample extraction. Initial guessed long-form paths returned 404; correct linked paths inspected. https://developer.android.com/media/media3/inspector/extract-frames ; https://developer.android.com/media/media3/inspector/extract-samples
- **[A8] Full/relevant body:** MediaMetadataRetriever metadata/frame extraction, nearest/sync selection and scaled frame methods. https://developer.android.com/reference/android/media/MediaMetadataRetriever
- **[A9] Full/relevant body:** PdfRenderer constructor requirements/security/isolation and Page API35 text extraction/order. https://developer.android.com/reference/android/graphics/pdf/PdfRenderer ; https://developer.android.com/reference/android/graphics/pdf/PdfRenderer.Page
- **[A10] Full/relevant body:** Android15 behavior changes, OTP/listener/screenshare/private-space limits and native 16-KB page-size considerations. https://developer.android.com/about/versions/15/behavior-changes-all
- **[A11] Full/relevant body:** AndroidX ExifInterface metadata/format behavior. https://developer.android.com/reference/androidx/exifinterface/media/ExifInterface
- **[A12] Full/relevant body:** Load large bitmaps efficiently, bounds/sampling/memory guidance. https://developer.android.com/topic/performance/graphics/load-bitmap
- **[A13] Full/relevant body:** MediaDataSource position-based reads and API23/thread contract; MediaExtractor accepts it. https://developer.android.com/reference/android/media/MediaDataSource
- **[A14] Full/relevant body:** StorageManager.openProxyFileDescriptor API26, seekable callbacks and explicit on-demand-decryption-without-cleartext-file example. https://developer.android.com/reference/android/os/storage/StorageManager#openProxyFileDescriptor(int,android.os.ProxyFileDescriptorCallback,android.os.Handler)
- **[A15] Full/relevant body:** SpeechRecognizer default remote-streaming warning, non-continuous intent and API31 on-device factory/availability. https://developer.android.com/reference/android/speech/SpeechRecognizer
- **[A16] Full/relevant body:** Android Keystore key/non-exportability/auth/hardware boundaries. https://developer.android.com/privacy-and-security/keystore
- **[A17] Full/relevant body:** Auto Backup, exclusions, cloud/D2D/cross-platform transfer distinctions. https://developer.android.com/identity/data/autobackup
- **[A18] Full/relevant body:** Long-running WorkManager, foreground service and Android16 job-quota warning; FGS obligations. https://developer.android.com/topic/libraries/architecture/workmanager/advanced/long-running ; https://developer.android.com/guide/components/foreground-services

### OCR

- **[O1] Full/relevant body:** ML Kit OCR Android setup/bundling/app size/character-resolution guidance. **Body requires API23**, despite auto-generated page summary saying API21; use body/library requirements, not summary. https://developers.google.com/ml-kit/vision/text-recognition/v2/android
- **[O2] Full:** ML Kit supported/experimental/mapped languages and script scope, Hindi/Marathi versus unavailable native Indic scripts. https://developers.google.com/ml-kit/vision/text-recognition/v2/languages
- **[O3] Full/relevant body:** ML Kit Terms & Privacy confirms local inputs/outputs and metrics/update traffic; Android data disclosures specify diagnostic categories. https://developers.google.com/ml-kit/terms ; https://developers.google.com/ml-kit/android-data-disclosure
- **[O4] Full:** Tesseract 5 manual, Apache license, Android compilation and language data choices. https://tesseract-ocr.github.io/tessdoc/Home.html
- **[O5] Full + official metadata:** Tesseract fast/best LSTM data trade-off and language packs; three byte sizes inspected through GitHub API. https://tesseract-ocr.github.io/tessdoc/Data-Files.html ; https://github.com/tesseract-ocr/tessdata_fast/tree/87416418657359cb625c412a48b6e1d6d41c29bd
- **[O6] Full:** Tesseract image quality, inverted images, deskew, segmentation and table limitations. https://tesseract-ocr.github.io/tessdoc/ImproveQuality.html
- **[O7] Full/relevant body:** Current PaddleOCR Android deployment: PP-OCRv6 SDK/demo, ORT/OpenCV/Compose/AAR, API26 setup and v5-mobile support. Initial old on-device path failed; linked Android page inspected. https://www.paddleocr.ai/main/en/version3.x/inference_deployment/cross_platform/android_deployment.html
- **[O8] Full/relevant body + repository license metadata:** Paddle text-recognition storage tables, inference-only timing, Tesla T4/Xeon environment and incomparable v6 evaluation set note; v5 multilingual family/dictionaries. https://www.paddleocr.ai/main/en/version3.x/module_usage/text_recognition.html ; https://www.paddleocr.ai/main/en/version3.x/algorithm/PP-OCRv5/PP-OCRv5_multi_languages.html ; https://github.com/PaddlePaddle/PaddleOCR

### Speech

- **[S1] Full/relevant body + MIT metadata:** whisper.cpp Android/platform/backend support, current memory table, CLI audio assumptions, quantization and benchmarks. https://github.com/ggml-org/whisper.cpp
- **[S2] Full:** Official whisper.cpp Android sample explicitly recommends tiny/base. https://raw.githubusercontent.com/ggml-org/whisper.cpp/master/examples/whisper.android/README.md
- **[S3] Full:** Official Whisper model card, model parameters, original-language ASR, hallucination/uneven-language/high-risk and speaker-task limits; upstream license. https://raw.githubusercontent.com/openai/whisper/main/model-card.md ; https://github.com/openai/whisper
- **[S4] Full/relevant body:** Vosk model catalog, small memory/file estimates, exact English/Hindi/Telugu entries and per-model licenses. https://alphacephei.com/vosk/models
- **[S5] Full:** Vosk Android demo, local speech and optional speaker identification; sample is not all-model mobile certification. https://alphacephei.com/vosk/android

### Runtimes and optional models

- **[R1] Full/relevant body:** ORT mobile flow, CPU/XNNPACK/legacy NNAPI, partitioning, quantization and binary/memory/power evaluation. https://onnxruntime.ai/docs/tutorials/mobile/
- **[R2] Full/relevant body:** Qualcomm QNN, supported Android hardware/build, HTP/GPU backend and Windows-only prebuilt package distinction. https://onnxruntime.ai/docs/execution-providers/QNN-ExecutionProvider.html
- **[R3] Full/relevant body:** Current LiteRT Android CompiledModel/Interpreter distinctions, version/minSdk/runtime support. https://developers.google.com/edge/litert/android
- **[R4] Full/relevant body:** MediaPipe Audio Classifier category task, not transcription. https://developers.google.com/edge/mediapipe/solutions/audio/audio_classifier
- **[R5] Full/relevant body:** MediaPipe Text Embedder, compatible model/input/formatting/512-token Gemma task and current Samsung S26 Ultra CPU4 benchmark. https://developers.google.com/edge/mediapipe/solutions/text/text_embedder
- **[R6] Full raw official docs + MIT metadata:** llama.cpp Android NDK/JNI CPU portability and current `libmtmd` image/audio/video support/models/projector requirements. GitHub rendered pages initially returned 503; official raw docs inspected after resolving via GitHub API. https://raw.githubusercontent.com/ggml-org/llama.cpp/master/docs/android.md ; https://raw.githubusercontent.com/ggml-org/llama.cpp/master/docs/multimodal.md
- **[R7] Full/relevant body:** NNAPI deprecation in Android15 and migration warning. https://developer.android.com/ndk/guides/neuralnetworks
- **[E1] Full model card + HF metadata:** English all-MiniLM-L6-v2, 384-D/256-word-piece intended limit, pooling and official arm64 quantized file. Revision `1110a243fdf4706b3f48f1d95db1a4f5529b4d41`. https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2
- **[E2] Full model card + HF metadata:** multilingual MiniLM L12, 50-language card, 384-D/128-token architecture limit and official arm64 quantized file. Card's example comment says max pooling while shown code/architecture uses mean pooling; use verified model config/parity tests, not that comment. Revision `e8f8c211226b894fcb81acc59f3b34ba3efd5f42`. https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2
- **[V1] Full/relevant card + HF metadata:** SmolVLM-256M English/APACHE-2.0, image-patch handling and publisher <1 GB GPU RAM claim. Revision `7e3e67edbbed1bf9888184d9df282b700a323964`. https://huggingface.co/HuggingFaceTB/SmolVLM-256M-Instruct

### Measurement and policy

- **[B1] Full/relevant body:** Android Studio Power Profiler, ODPM supported-Pixel/device-level/noise limitations. https://developer.android.com/studio/profile/power-profiler
- **[B2] Full/relevant body:** Batterystats/Battery Historian guide explicitly warns Historian is no longer actively maintained; recommends tracing/power profiling alternatives. https://developer.android.com/topic/performance/power/setup-battery-historian
- **[P1] Full/relevant body:** Google Play sensitive-permission/API necessity, scope and consent. Future January2027 preview changes are not treated as already effective. https://support.google.com/googleplay/android-developer/answer/16558241?hl=en
- **[P2] Full/relevant body:** Google Play User Data, sensitive data, transparency/security and third-party AI/SDK responsibilities. https://support.google.com/googleplay/android-developer/answer/10144311?hl=en

## 12. Concrete implementation implications

1. Build the **share/SAF/Photo Picker importer and encrypted derivation graph** before adding models or passive collection.
2. Implement **screenshots + short imported audio** first, with source-linked corrections and original-byte preservation.
3. Add **native PDF text/page OCR and sparse video OCR+ASR** by reusing extraction primitives, with secure random-access integration and explicit sampling limits.
4. Treat **Malayalam OCR as a distinct validated engine task**, not a language switch in ML Kit; multilingual ASR/classifier quality is also a separate evaluation.
5. Benchmark **one engine/model/backend at a time** on the observed stock phone; retain lower-end-device and thermal/energy tests before general support claims.
6. Add optional notifications, one real export parser and retrieval/classification only after conservative source semantics and actual fixtures are established.
7. Defer VLMs, autonomous capture, voice identity and legal conclusions. The core promise is local organization and review of evidence the user legitimately selected or the OS actually exposed, not omniscient access or guaranteed proof.
