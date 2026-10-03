# MediaProjection evidence acquisition

Status: Frozen for screenshot and burst release. Approved by the user through "Do the recommended decisions" on 2026-10-03.

## Goal and scope

Let a user explicitly capture ordinary visible messages and other screen evidence, preserve the captured bytes locally, and review them before adding them to a case. This records a rendered screen observation. It does not retrieve original attachments, full conversation history, hidden messages, or private application databases.

User request: implement the MediaProjection plan and required app integration. Selected scope: screenshots and bounded bursts; temporary encrypted drafts with unlock/review before permanent save. Video and audio are deferred.

## Implementation status

The projection module is included in the Android build and integrated into the app with a user-started foreground service, fresh Android consent for each session, screenshot and bounded-burst capture, encrypted in-memory-key drafts, and an unlock/review/save flow. Build, lint, manifest, and JVM evidence is recorded in `research/verification/android-mediaprojection-2026-10-03/`. The user-controlled OS consent-to-capture flow has not been exercised on-device; do not treat implementation or installation as acceptance.

Dark/empty frames are classified only as blank or unavailable. OCR layout parsing is an unreviewed proposal; sender, direction, and message time remain unknown until user review. Captured rendered pixels are not authenticated as a particular app or conversation.

## Functional requirements

- FR-001: Start only from an explicit foreground user action. Show what will be captured, storage policy, limits, stop controls, and limitations before Android consent. Every new session needs new Android consent.
- FR-002: Offer one screenshot and a bounded screenshot burst. Let the user choose an app window through the Android picker where supported. Full-display selection needs a clear disclosure that unrelated visible content may be included. On older Android versions disclose display-wide capture before proceeding.
- FR-003: Capture only system-permitted visual output. Never bypass protected content, ephemeral-content restrictions, work-profile policy, encryption, or sandbox boundaries. Provide Share/Import/manual-note alternatives.
- FR-004: Keep capture visible and stoppable through foreground-service controls and app UI. Stop on user request, system revocation, screen lock, timeout, resource limit, or unrecoverable failure. Never start at boot or restart a session without consent.
- FR-005: Preserve exact retained PNG bytes and SHA-256, timestamps, session/frame identifiers, dimensions, transformations, and capture limitations. Do not represent capture time as message-send time or user-declared app/contact as verified attribution.
- FR-006: Provide encrypted temporary staging while the main vault remains locked; unlock to review thumbnails and select a case. Permanently save only according to the retention choice below. Discard must destroy the temporary session key and remove files.
- FR-007: Keep screenshots and OCR/model outputs separate. Existing bundled Latin OCR and local analysis produce evidence-linked suggestions. Malayalam/Devanagari OCR remains deferred; text imports in those languages remain supported. Unknown sender, direction, and message time remain unknown until reviewed.
- FR-008: Save selected evidence through the existing encrypted repository, then run local analysis and human review. Failure of OCR or analysis must not lose a successfully saved image. Export remains explicit.
- FR-009: State duplicate suppression and capture gaps. Never infer repeated harassment solely from overlapping screenshots or repeated capture of the same message.
- FR-010: Preserve notification, Accessibility, Sharesheet, picker, vault-lock, review, and report workflows. Projection is a separate collector with separate consent.
- FR-011: Design a bounded screen-video extension with optional supported internal audio. Ship it only if selected and after its separate acceptance gates. No implicit microphone or call recording.

## Proposed user workflow

Home collection controls -> Screen evidence -> choose screenshot/burst and target case -> disclosure and optional notification-control setup -> Android capture picker -> visible capture session -> user opens/scrolls the ordinary content -> capture finishes or user stops -> return and unlock -> inspect/select drafts -> Save to case -> local OCR/analysis -> user reviews findings.

A snapshot uses a disclosed short countdown so the user can show the desired screen. Burst sampling occurs at a bounded cadence while the user scrolls manually. No Accessibility gestures, automatic scrolling, overlays, or remote control are needed. Returning to Sakshi stops acquisition before review.

## Decision log

- D-001: User selected screenshots and bounded bursts. Silent video and internal audio deferred. Scope FR-002/011.
- D-002: User selected encrypted temporary drafts followed by unlock/review and explicit permanent saving. Scope FR-006/008.
- D-003: Adopt proposed session-only in-memory AES-GCM key. Process death, lock, revocation and expiry discard uncommitted captures; process death loses the key. Durable draft recovery deferred.
- D-004: Adopt fail-closed if Sakshi notifications are disabled so a visible stop control is available.
- D-005: Adopt proposed screenshot bounds: session <=120 seconds, <=20 retained frames, <=1 sampled frame/second, <=4 megapixels, <=16 MiB/frame, <=128 MiB/session, review expiry <=5 minutes after stop. These are engineering caps, not measured performance claims.

Video and audio are deferred beyond this release and require new specification approval.

## Definition of done

The frozen scope passes all applicable criteria in ACCEPTANCE.md with real Android acquisition evidence, reproducible device/resource measurements, and no privacy or collection regressions. Source code and passing JVM tests alone do not establish device readiness.
