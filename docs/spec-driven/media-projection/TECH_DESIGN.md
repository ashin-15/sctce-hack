# MediaProjection technical design

Status: Frozen for screenshot and burst release. Based on approved PRD dated 2026-10-03.

## Platform contract

Use the Android consent activity, a non-exported `mediaProjection` foreground service, and fresh one-use consent per session. Start the service after consent, promote it to foreground, then obtain MediaProjection. Register its callback before creating one VirtualDisplay. Resize that display/surface rather than creating a second display with the same consent. Android 14 adds app-window selection; older devices need explicit display-wide disclosure. On Android 15 QPR1+ the system provides a stop chip and stops projection on lock. Implement independent screen-off handling for older devices. See [Android MediaProjection](https://developer.android.com/media/grow/media-projection) and [foreground-service contract](https://developer.android.com/develop/background-work/services/fgs/service-types#media-projection).

`FLAG_SECURE` protects windows from screenshots/non-secure displays; retain Sakshi's existing flag. A blank or dark output has multiple possible causes. Never label it proof of protected content, Private Space, or View Once. See [Android secure activities](https://developer.android.com/security/fraud-prevention/activities).

Android does not require POST_NOTIFICATIONS to start a foreground service. Sakshi's proposed requirement for accessible notification controls is a product choice D-004. See [notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission).

## Module changes and responsibilities

1. Include `:acquisition:projection` in settings; add app dependency. Keep the service, capture state machine, frame conversion, and staging in the acquisition module. Add only the required FGS permissions/service to the app's verified merged manifest policy.
2. Add `ProjectionCaptureService`, `ProjectionCaptureController`, `ProjectionCaptureState`, `ProjectionLimits`, `ProjectionDraftStore`, and capture metadata codec. No exported receiver, overlay, accessibility requirement, network client, boot receiver, or persistent consent-token storage.
3. Refactor `ProjectionSession`/`MediaProjectionFrameSource` around event-driven frames, explicit cancellation, and one serialized owner. Remove the misleading secure-frame evidence outcome. Separate collection/staging from vault saving and OCR.
4. Wire ActivityResult consent in app SessionHost/AppContainer. Add Compose disclosure, active-session status, review gallery, and save/discard actions in the existing collection UI using Sakshi tokens.
5. Add a narrow app review/save coordinator using EvidenceRepository and the existing image analysis pipeline. Update provenance/rendering/report labels without changing evidence authenticity claims.

## Proposed interfaces

```kotlin
// Public API design; signatures are not production code.
interface ProjectionCaptureController {
    val state: StateFlow<ProjectionCaptureState>
    suspend fun prepare(request: CaptureRequest): PreparationResult
    fun startWithConsent(resultCode: Int, data: Intent, requestId: String)
    fun stop(reason: StopReason)
    suspend fun discard(sessionId: String)
}
interface ProjectionDraftStore {
    suspend fun stage(frame: EncodedFrame, metadata: CaptureMetadata): DraftRef
    suspend fun openForReview(ref: DraftRef, authorization: ReviewAuthorization): InputStream
    suspend fun discardSession(sessionId: String)
}
// The app coordinator owns the unlocked vault and case selection.
// Save(sessionId, draftIds, caseId, operationId) returns a per-frame receipt.
```

Service lifetime owns projection/VirtualDisplay/ImageReader. A process-scoped controller owns temporary draft keys through a bounded review period after acquisition stops. UI observes redacted state only while locked. `START_NOT_STICKY`, no persisted grant, and no automatic restarts. Requests and service actions include a session generation ID; stale actions/results cannot operate on a new session. Mutable buffers, bitmap references, intent grants, and keys never enter saved state, logs, notifications, analytics, or report metadata.

## State and concurrency

Idle -> Preparing -> AwaitingConsent -> StartingForeground -> Capturing -> Stopping -> AwaitingUnlock -> Reviewing -> Saving -> Saved/Discarded -> Idle.

Denial returns Idle without frames. Failure closes all initialized resources and shows a redacted cause. A single actor/mutex serializes state transitions; an independent monotonic watchdog enforces limits. Stop atomically invalidates the session generation before cancelling acquisition, so late callbacks/writes cannot append frames. Close is idempotent across notification stop, OS onStop, cancellation, and partial startup failure.

Register callbacks on a dedicated HandlerThread; use bounded worker/IO dispatchers for conversion/encryption. ImageReader keeps at most two buffers, pending encoding queue at most one, and every Image/bitmap/stream is closed or released in finally blocks. If no frame is ready, await the listener until a bounded first-frame deadline; do not terminate a burst on a transient null. Proposed first-frame deadline: 5 seconds, pending D-005 acceptance.

Resize pauses writes, detaches the old surface, resizes the existing VirtualDisplay, attaches a new reader surface, then drains/releases old resources on the same owner thread. Tag frames with a geometry generation and discard stale geometry. Handle API 34+ onCapturedContentResize; older versions use guarded display/configuration changes. Pause sampling when API-supported selected-content visibility is false; record the gap rather than claiming continuity.

## Frame selection and capture fidelity

Use ImageReader RGBA8888 with validated width/height/pixelStride/rowStride and overflow-safe allocations. Preserve image crop bounds, orientation, and exact effective capture dimensions. Reject a configuration exceeding agreed native-size limits or ask the user for a clearly labelled scaled capture; never silently scale or crop an original. Derivatives may be cropped/redacted later without overwriting the saved screen capture.

Encode lossless PNG once, compute SHA-256 on those exact bytes, then encrypt/stage. Keep exact-byte duplicate suppression within a session only. Perceptual hash can propose duplicates for review; it must not discard evidence because small edits, timestamps, or one-character threats can have nearly identical hashes. Explicit single screenshots are retained even when visually similar. Dropped/not-ready/duplicate/skipped frames have reason counts and gap intervals. The sampler never promises to capture every message.

Blank/dark frames get a neutral `blank_or_unavailable` observation and review warning. No automatic vault evidence claiming OS protection. Unexpected content cannot be reliably filtered by MediaProjection into messaging-only pixels, and a selected app/package cannot be treated as independently verified from user claims. Full-display scope can include keyboards, private inputs, notifications, and unrelated apps. Do not promise to exclude passwords or detect ephemeral content from pixels. User-facing disclosure and OS scope selection are essential; suspected restricted content is not pursued or bypassed.

## Encrypted temporary drafts - proposed review-first branch

Generate a random session AES-256 key held in memory only. Encrypt every draft with AES-GCM, unique random nonce per object, authenticated version/session/frame/dimensions/hash metadata, and bounded writes into app-private no-backup storage. Metadata and thumbnails are encrypted too. Publish a DraftRef only after a complete ciphertext write and atomic rename. Never write plaintext PNGs, OCR, or videos to cache, MediaStore, external storage, or debug fixtures.

The main vault still locks on background as it does today; do not extend picker grace across capture. Review access requires a currently authenticated vault session and current generation. Keep only minimal in-memory frame/key lifetime. Screen lock or OS revocation cancels and discards unsaved drafts under this proposed policy. User Stop/timeout returns drafts for review for at most five minutes. Process death loses the in-memory key; startup deletes orphan ciphertext and explicitly says unsaved captures could not be recovered. Deletion removes files and keys; flash physical erasure and JVM memory zeroization cannot be guaranteed. Never claim guaranteed erasure.

If durable drafts or auto-retention are requested later, revise the key design before implementation: an authenticated vault stays locked, and collection needs a separate constrained encryption ingress/public-key wrapping scheme. Define who can decrypt, device-lock accessibility, retention/backup/recovery, crash behavior, and user disclosure. Do not introduce a persistent non-authenticated decryption key as a silent workaround.

## Permanent storage and provenance

After unlock and explicit selection, stream-decrypt a draft directly into existing EvidenceRepository import, using `SELECTED_VISUAL_MEDIA`, `USER_MEDIATED`, mechanism `media_projection`, declared MIME image/png, and a screen-observation origin label. Hash validation must match exact captured bytes. Verify import/authentication success before removing that draft. Return partial success receipts; retain unimported drafts until expiry and allow retry. Use operation/session/frame identity so a repeated save after activity recreation cannot create duplicate rows; define transactional import-marker behavior and reconcile interrupted saves.

CaptureMetadata v1: session UUID, request/frame ordinal, UTC observation timestamp, elapsedRealtime-relative timestamp and boot/session association, actual dimensions/density/rotation/crop/scaling, capture mode, scope claim with its claim source, user-supplied app/contact claim, SHA-256, collector/schema/app versions, geometry generation, duplicate/drop/gap counters, stop reason. Never store a consent Intent, Binder token, private URI, inferred package as verified source, message-send timestamp as capture time, or legal/admissibility conclusions.

Use the existing capture-metadata field if compatible; inspect its versioning limits and migrations before freezing any Room changes. A new import-idempotency index/receipt table, if needed, requires upgrade, rollback, and interrupted-import tests. Old captures must remain readable. Reports identify this as a screen observation and keep user claims/OCR/inference distinct from captured pixels.

## OCR, messages, patterns, and alerts

Permanent storage precedes expensive OCR/classification. Work is cancellable and serialized/bounded. Reuse bundled offline Latin OCR; keep text lines, coordinate boxes, source image dimensions, engine/version, and extraction warnings as derivatives linked to the original image. OCR failure produces a retryable status while the image stays intact. Other script OCR is deferred, without denying image retention.

MVP uses the existing uncertain image-event path. ChatVisualParser layout guesses are optional proposals, with unknown attribution/time/direction and preserved raw OCR anchors. Add reviewed message segmentation only after fixtures cover multiple layouts/themes/fonts/group/RTL and source mapping is correct. Confidence numbers are engine/layout estimates, not calibrated truth probabilities. Overlapping screenshots do not count as independent repeated harassment; temporal engine uses accepted event identity/grouping. Threat suggestions appear in review after local analysis. No new always-on threat scanning. Background neutral alerts require a separate approved consent/quality path, not inferred from capture permission.

## Optional screen-video/internal-audio extension

D-001 blocks release inclusion and resource limits. Configure a MediaCodec AVC encoder surface and one VirtualDisplay for a video-mode session. Prefer separate screenshot and video modes initially, avoiding a second VirtualDisplay or token reuse. Drain codec output and timestamps into bounded encrypted segments; derive review thumbnails/frames afterwards through local decode. Do not create a plaintext temporary MP4. If using MediaMuxer with seekable MP4 output, it needs an explicitly reviewed encrypted seekable storage design; otherwise use a versioned encrypted encoded-segment container and generate a review/export MP4 only through an authorized streaming/seekable export path. Record codec/profile/fps/resolution/sample timestamps/gaps and preserve captured segments as originals. Capture failure finalizes complete segments, discards partial segments, and reports the interruption.

Audio is opt-in, separate from visual permission. Supported internal playback on API 29+ requires RECORD_AUDIO, OS capture consent, same user profile, and source-app capture policy/eligible usage. Use AudioPlaybackCaptureConfiguration + AudioRecord, constrain UIDs/usages, timestamp and encrypt packets, synchronize tracks, and label unavailable audio truthfully. No assumption that messaging voice notes, calls, DRM, or arbitrary app audio are capturable. Never substitute microphone audio silently. See [Android playback capture](https://developer.android.com/media/platform/av-capture).

Video review provides playback, segment/time selection, and explicit storage/export. STT runs only on supported retained audio, uses provisioned local models, preserves original tracks, and distinguishes transcript from evidence. Agree video duration/bytes/codec/RAM/battery budgets separately; screenshot budgets do not imply video budgets.

## Failures, compatibility, release

Cover consent denial/cancellation, disabled controls, expired result, concurrent starts, FGS start failure, screen-off, OS chip stop, competing projection, process death, geometry changes, empty frames, low space, oversized image, write corruption, wrong key/tag/hash, failed authentication, case deletion, repeated saves, expiry during review/save, and OCR/model absence. Show neutral actionable errors with Share/Import alternatives. No collector retries without user action after consent becomes invalid.

Current app minSdk 26, target/compile 36. Validate API 26/29/33/34/35-QPR1/36 behavior where relevant; use emulators for compatibility and actual phones for protected-output, selected-app/system-UI exclusion, stop/lifecycle, resource measurements, and battery tests. Do not label emulator/laptop measurements Android device acceptance.

Add a feature gate default-off until acceptance. Ship independently of Accessibility/notifications, with capture controls hidden if disabled. Rollback stops service, destroys temporary drafts, removes new capability UI, and retains previously saved evidence/metadata readability. Log only IDs, dimensions, counts, durations, error codes, and redacted outcomes; never pixels, recognized text, contact names, or grants.
