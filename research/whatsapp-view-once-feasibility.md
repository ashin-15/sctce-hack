# WhatsApp View Once: supported acquisition boundaries for Sakshi

**Research date:** 2 October 2026

**Scope:** WhatsApp Android recipient workflows for View Once photos, videos, and voice messages. Technical feasibility, provider restrictions, distribution policy, evidence provenance, and a small local-first implementation.

**Status:** Source-backed design investigation, not Android-device certification, legal advice, or WhatsApp/Google Play approval.

## Executive decision

**Build an incident-preservation workflow, not a View Once extractor.** Sakshi can preserve opted-in notification observations when WhatsApp exposes them, user statements, permitted contextual artifacts, and independently user-selected recordings/files. No documented public Android interface gives Sakshi the underlying View Once item, its original media URI, or a right to reopen it after consumption.

**Important modality exception:** WhatsApp's current official **About view once** article explicitly says: **“It's possible for iOS or Android users to record audio of view once messages using the native screen recorder.”** [WA1] Therefore, “all View Once audio recording is impossible” is incorrect. A user-directed stock-recorder audio workflow followed by explicit import is a documented possibility, not a proven automatic third-party Sakshi capture capability. It remains subject to stock-device testing, recording/privacy rights, incidental-data minimization, and no protection overrides.

For photos/video, WhatsApp documents screenshot and screen-recording restrictions on visual media. [WA2] Android secure-window restrictions must be respected. For voice messages, native audio recording and third-party playback capture have different authority: Android allows source apps to restrict non-system audio capture. [AN6] Do not generalize either visual blocking or native-recorder success to every audio pathway.

Recommended MVP:

```text
Optional, disclosed NotificationListenerService observation
  -> conservative media hint or Unknown
  -> optional neutral alert controlled by the user
  -> user chooses whether to view/listen in WhatsApp
  -> user statement + permitted context
     OR stock-recorder audio artifact, only if supported and lawful
  -> explicit Sharesheet / SAF / Photo Picker import and Save
  -> immutable artifact + encrypted local provenance
  -> local analysis of actually available inputs
  -> user-reviewed report with unavailable-content limitations
```

There is **no automatic capture/import step for protected View Once visuals**. There is **no background auto-play, recorder activation, private-file lookup, or replay/recovery step** for any modality.

## Method, certainty, and limits

- **Verified documentation:** A relevant passage was inspected in a primary publisher's rendered page or fetched body. This establishes the documented behavior, not every device's implementation.
- **Technical inference:** A conclusion follows from Android API contracts and WhatsApp's documented restrictions. Negative API findings mean no supported acquisition contract was identified, not a mathematical proof about every OS implementation.
- **Conditional:** A supported surface exists, but app settings, permissions, source capture policy, OEM behavior, or legal authorization decide whether an individual artifact is obtainable.
- **Unknown / untested:** Actual WhatsApp notification extras, View Once-specific labels, stock audio recording results, lifecycle edge cases, and exported placeholders have not been tested here.

The investigation started from official WhatsApp Help and announcements, then checked Android API documentation and Google Play policies. Plain-text fetches and raw HTML retrieval of the Help Center exposed only page titles/bootstrap data. A read-only Chromium session using Playwright successfully rendered the official pages. The decisive evidence below comes from those current rendered official pages, not from third-party reproductions.

`adb devices -l` returned **no attached devices**. No WhatsApp Android build, phone, account, actual View Once item, notification fixture, screenshot, or native audio recording was tested. Desktop rendering of documentation is not an Android E2E test. No exploitation, private database inspection, undocumented provider probing, modified client, or protection bypass was investigated.

Existing project context: `research/android-evidence-acquisition-specification.md`. This focused report refines its conservative acquisition boundary, especially the distinction between **no original View Once voice-file extraction** and **documented manual native-recorder audio possibility**.

## A. Capability matrix

**Legend:** Supported = within a documented public surface; Conditional = implementation/payload/rights gates remain; No contract = no supported route identified for the requested item; Excluded = prohibited by Sakshi design or would defeat protections. Every row applies to Android recipient use unless specified.

| Capability / explicit question | View Once photo | View Once video | View Once voice | Evidence and Sakshi decision |
|---|---|---|---|---|
| Automatically obtain actual pixels/audio on receipt? | No supported protected-pixel route | No supported protected-pixel route; audio track is not automatically supplied | No verified automatic Sakshi route; generic third-party playback capture is conditional, not an item API | NLS is notification access, not attachment extraction. No silent capture or playback. [WA1][WA2][AN1][AN6] |
| Detect a WhatsApp notification arrived? | Conditional | Conditional | Conditional | NLS can observe a posted notification with user grant; not every source message generates an observable notification. [AN1][WA6] |
| Identify specifically that it was View Once? | Unknown until fixtures | Unknown until fixtures | Unknown until fixtures | No published stable View Once notification schema identified. “Photo,” “Video,” or “Voice message” alone is not proof of View Once. [WA6][AN3] |
| Obtain sender metadata? | Conditional exposed display identity | Same | Same | Title/Person/message fields may expose a name or number. Locked chats hide name/content. No verified identity or universal account mapping. [WA6][AN3] |
| Obtain timestamp metadata? | Notification post time; conditional app-supplied time | Same | Same | Keep post time, callback time, app `when`, structured message time, and user-reported time distinct. None is guaranteed sender time or trusted legal timestamp. [AN2][AN3] |
| Obtain photo/video/voice type metadata? | Conditional label/MIME hint | Conditional label/MIME hint | Conditional label/MIME hint | Preserve exact field and parser uncertainty. Icons/avatar/summary text are not original payloads. [AN3] |
| Obtain underlying View Once media URI? | No public contract | No public contract | No public contract | Optional notification `dataUri` is publisher-supplied and permission-gated; its existence in Android does not establish WhatsApp publishes a protected item's URI. [AN3][AN10] |
| Receive the View Once item through Android share? | Not supported by WhatsApp | Not supported by WhatsApp | Not supported by WhatsApp | WhatsApp prohibits forward/copy/save/star/share for sent or received View Once items. Adding an `ACTION_SEND` receiver does not change that. [WA1][AN8] |
| Retrieve via SAF / Photo Picker / MediaStore? | No View Once item contract | No View Once item contract | No View Once item contract | Pickers select exposed files, not WhatsApp private items. A separately produced lawful artifact can be selected. [WA1][WA2][AN9][AN11][AN12] |
| Native screenshot of content? | Documented blocked | Documented blocked | Screenshot is not audio acquisition; View Once media screenshot restriction still applies | No “try another screenshot API” fallback. [WA1][WA2][AN4][AN7] |
| Native screen recording of visual content? | Documented blocked | Documented blocked | Do not infer audio blocking from visual blocking | Secure pixels and playback audio are separate paths. [WA2][AN4][AN6] |
| User-operated native recording of audio while available? | Official generic audio caveat, not proof of useful sound in a photo | Generic audio caveat; actual video sound capture untested | Explicit documented possibility; device/result conditional | Official wording is audio of “view once messages,” not a guaranteed original voice-file export. Offer only supported stock behavior, without overrides. [WA1] |
| Sakshi MediaProjection pixels/audio? | Secure pixels not available | Secure pixels not available; audio separately conditional and unverified | Audio conditional on third-party capture eligibility; native recording does not prove it | Do not ship a View Once projection collector in MVP. [AN4][AN5][AN6] |
| Accessibility extraction? | No original media API | No original media API | No voice-file/audio API | UI nodes may expose labels, not underlying payload. Screenshot APIs reject secure windows. Broad scraping excluded. [AN7][AN15][PL2] |
| Access after opening/exiting viewer? | No supported reopen/recovery | No supported reopen/recovery | No supported reopen/recovery | May preserve a statement or already acquired independent artifact, not retrieve consumed content. [WA1][WA2] |
| Official Android mechanism granting the View Once item? | None identified | None identified | None identified; native audio recorder is a generic user-operated exception, not item access | Permissions granted to Sakshi do not compel WhatsApp to export or replay. [AN1][AN4][AN6][AN10][AN12] |
| Preserve evidence of an incident without payload? | Supported user statement; conditional notifications/context | Same | Same | Label provenance and unavailable content; no fabricated OCR/STT or inferred threat from a media label. |
| Analyze actual content locally? | Only from a legitimately selected independent artifact, not automatically from View Once | Same | From an actually imported supported recording; not from metadata | Preserve input bytes; OCR/STT and findings are derivatives with user review. |
| Report to WhatsApp? | Documented in-viewer reporting | Documented in-viewer reporting | Officially reportable; exact current voice UI needs phone test | Reported media/voice is provided to WhatsApp, not Sakshi. No readback/receipt-to-payload API. [WA1][WA3][WA4] |
| Preserve with WhatsApp backup? | Unopened-at-backup condition only | Same | Same | Opened items not included/cannot be restored under documented rule. Not an export/replay workaround. [WA1] |

### Direct answers in plain language

1. **Actual pixels/audio automatically:** Protected photo/video pixels: no supported Sakshi route. View Once audio: do not assert impossibility; stock native recording is officially acknowledged, but an automatic Sakshi path is not established. Third-party playback capture would need explicit session consent, eligible source audio policy, phone tests and policy review; it is not part of the MVP.
2. **Arrival detection:** Can observe WhatsApp notification delivery conditionally. Cannot reliably certify every View Once arrival from generic labels or absence of a notification.
3. **Sender/timestamp/type:** Can retain exposed notification fields conditionally. Post time is observable; sender/type/source-message time may be absent or ambiguous.
4. **Underlying URI:** No documented View Once URI/export surface identified. A notification launch `PendingIntent` is not a media URI.
5. **Share:** Cannot share the received View Once item to Sakshi through WhatsApp's supported UI. Can share a separate permitted context artifact or user-produced recording as its own file.
6. **After opening:** Cannot reopen/recover through a supported Sakshi mechanism. A recording made while audio was available can remain independently; it is not post-consumption retrieval.
7. **Official mechanism:** No item-specific Android permission/API grants protected media. Generic native audio recording is a documented possible user workflow, not underlying item access.
8. **Legitimate preservation:** User statements, available contextual messages/profile details, previously observed opted-in notification data, actual supported audio recordings, and WhatsApp's own reporting path. Legal authority is separate from Android ability.
9. **Not automatable:** Complete View Once identification, protected visual capture, original-file extraction, replays/recovery, guaranteed expiry detection, guaranteed sender identity, and guaranteed report-payload retrieval.
10. **Bypass approaches:** Any attempt to defeat secure-window restrictions, source audio restrictions, the one-view lifecycle, share/export restrictions, or private-app access boundaries. User consent alone does not remove those boundaries.

## B. Official-source evidence and lifecycle

### B1. Decisive current Help Center passages

All quotations in this table were inspected in official rendered pages on the research date. Typography is reproduced without adding interpretations to the quoted text.

| ID / source | Official passage | What it establishes / does not establish |
|---|---|---|
| [WA1], About view once | “Once you receive a view once photo, video, or voice message, you’ll need to open it within 14 days of it being sent. Otherwise, it’ll expire from the chat.” | Covers all three modalities. Clock is described as from **sending**, not notification arrival. No Sakshi TTL/status API. |
| [WA1] | “You can’t forward, copy, save, star, or share photos, voice messages, or videos that were sent or received with view once media enabled.” | Prohibits ordinary item export/share operations for all modalities. Does not mean a separate user statement cannot be saved. |
| [WA1] | “View once media and voice messages can be restored from backups if the message is unopened at the time of back up. If the photo, voice message, or video was opened, it won’t be included in the backup and can’t be restored.” | Backup is not categorically excluded for unopened items. Does not promise backup creation, repeated viewing, expired restoration, or Sakshi access. |
| [WA1] | “It's possible for iOS or Android users to record audio of view once messages using the native screen recorder.” | Critical native-audio exception. Does not identify non-system app playback-capture policy, audio route, codec, phone builds, or guaranteed success. |
| [WA1] | “If a recipient chooses to report view once media, or voice messages, the media, or voice messages will be provided to WhatsApp.” | An explicit report can disclose content to WhatsApp. No downloadable report copy for Sakshi. |
| [WA1] | “Encrypted media may be stored for a few weeks on WhatsApp’s servers after you’ve sent it.” | Disappearance from the chat is not proof of instantaneous erasure of every copy. Server retention is not retrieval permission or a recovery route. |
| [WA2], How to send and open view once media and voice messages, Android | “View once photos and videos won’t be saved to the recipient’s Photos or Gallery. They won't be able to forward, share, or copy them. Recipients also won’t be able to take a screenshot or screen recording of your view once media.” | Explicit visual-media restriction. Must not stretch this into a claim that all audio recording is blocked. |
| [WA2] | “You’ll see an Opened receipt in the chat for media or voice messages you’ve already viewed. View once media won’t be saved to your device. Once you exit the media viewer, you can’t view the media again.” | User-facing opening/exiting behavior, not a forensic claim about RAM, temporary buffers, or physical secure deletion. |
| [WA3], How to block and report someone, Android | “Report a view once photo or video” followed by opening it and selecting Report | Official in-viewer route for visuals. The photo/video instructions alone do not establish exact voice-player menu behavior. |
| [WA4], About reporting and blocking | “The only way to report someone is to follow the in-app reporting process.” | Sakshi should guide, not submit through an invented endpoint or claim integration. |
| [WA6], How to manage your notifications, Android | “When a chat is locked, WhatsApp hides the contact name and message content from all notifications to keep your conversations private.” | A concrete documented metadata failure case, including lock screen, panel and banners. Do not defeat it. |

### B2. Behavior by modality and state

| Topic | Photos | Videos | Voice messages | Implementation implication |
|---|---|---|---|---|
| Sending | Sender selects View Once for each item; sender cannot view again after sending | Same | Same | Sender's separate original file is not recipient access. [WA1] |
| Receiving unopened | Available to the recipient in WhatsApp within documented 14-day window from sending | Same | Same | Do not start a trusted deadline at Sakshi notification time. [WA1] |
| Opening | User taps item and views in viewer | User taps item and views/plays | User taps item and listens | Do not automatically open; doing so can consume an item and affect receipts. [WA2] |
| Closing / consumed | Can't view again after exiting; chat shows Opened | Same | Can't retrieve/reopen after consumption | Pause, seeking, interrupted playback, accidental close and completion edge cases require stock-client tests. Official announcement says voice can only be played one time; do not promise replay within a session. [WA1][WA2][WA10] |
| Not opened | Expires after 14 days from sending | Same | Same | Not the ordinary disappearing-message 24-hour/7-day/90-day setting. No listener-based expiry proof. [WA1] |
| Gallery / save | Not saved to recipient gallery; no item Save | Same | No item Save | “Not saved” is product behavior, not proof no temporary processing exists anywhere. [WA1][WA2] |
| Screenshots | Blocked | Blocked | Visual screenshot does not preserve sound | A screenshot of permitted chat context is different from protected viewer capture. [WA1][WA2] |
| Visual screen recording | Blocked | Blocked | UI video does not imply sound access | Honor protection regardless of user granting projection. [WA2][AN4] |
| Audio recording | Photo may have no relevant sound | Actual sound result unverified | Native recorder audio explicitly possible | Need stock-device test. Never assume system/non-system capture equivalence. [WA1][AN6] |
| Sharing / forwarding / copying | Can't forward/copy/save/star/share | Same | Same | No Sharesheet extraction, clipboard extraction, or share receiver trick. [WA1] |
| Backup | Restore possible when unopened at backup; opened excluded | Same | Same | Do not prescribe restore cycles, reinstalling, backup extraction or replay. [WA1] |
| Report | User can report in viewer; content goes to WhatsApp | Same | Report can provide voice to WhatsApp | Not local-first disclosure; make recipients/data flow explicit. [WA1][WA3][WA4] |
| Read/open receipts | View Once help conditions sender Opened receipt on recipient read receipts | Same | Same wording in specific View Once help | General read-receipt help separately says group read receipts and voice play receipts cannot be disabled. Treat receipt edge cases as unverified, not a privacy guarantee. [WA1][WA2][WA9] |
| Push notifications | No stable View Once notification payload documented | Same | Same | Do not confuse the chat's one-time icon with a guaranteed notification extra. [WA6][AN1][AN3] |

### B3. Conflicts and interpretation

- **Visual blocking versus native audio possibility:** The current official articles contain both. They are not equivalent claims about the same output channel. Adopt modality-specific wording; do not promise blacked-out video necessarily means no audio.
- **“Can't save” versus recorded audio:** No direct item Save/share operation is supported, while a generic native recorder can produce a separate audio artifact. That artifact is not the original WhatsApp file.
- **“Disappears” versus unopened backup/server retention:** WhatsApp documents unopened backup restore and encrypted server retention. Do not promise every copy vanishes immediately or infer Sakshi recovery permission.
- **Receipt wording:** Specific View Once help uses read-receipt conditions for media/voice; general help has group/play-receipt exceptions. Do not assure users that viewing/listening leaves no signal to the sender.
- **Historical sources:** The 2021 visual announcement predates screenshot blocking; the 2022 announcement says blocking was being tested then. Current Help establishes present documented restrictions, not old launch coverage. [WA8] Contemporary secondary reporting on native audio was corroborated by [WA1]; no secondary source is needed for the decisive claim.
- **Physical recapture:** Official Help acknowledges other-device photography/video as a privacy limitation. [WA1][WA2] This is not permission from WhatsApp to record or redistribute, nor an Android inter-app acquisition API. Sakshi should not market it as a protection workaround.

## C. Android technical feasibility

### C1. NotificationListenerService: observation, not message interception

A user-enabled NLS receives notifications that applications post, update, remove, and rank. It does not receive WhatsApp protocol messages, decrypt attachments, open viewers, or query source message history. The service declares `BIND_NOTIFICATION_LISTENER_SERVICE` for system binding; this is not a broad media-reading runtime permission. Wait for `onListenerConnected` before active-notification operations. [AN1]

Capture a bounded, typed snapshot of publicly exposed fields, not the whole Parcelable or executable notification actions:

- Package/profile, notification key/id/tag/group key, observed post time and collector time.
- Notification title/text/big text or structured `MessagingStyle.Message` data when present.
- App-supplied message timestamp, `Person`, MIME/label hints, separately labeled as publisher claims.
- Parsing version, raw exposed label, confidence/unknown reason and source build when obtainable without unnecessary broad visibility.

The notification key identifies a **notification record**, not a WhatsApp message. One repost can include several messages, historical entries, summaries, or remote-input history; duplicate suppression must not create false event counts. Removal does not prove the message was read, deleted, expired, or consumed. Active snapshots after reconnect are outstanding notifications, not a missing-history recovery journal.

WhatsApp controls previews and posting; OS/settings/profile/OEM restrictions apply. Locked chats deliberately omit identity/content. Notification permission denial, chat foreground state, muted groups, summary updates, listener revocation, OEM background limits and force-stop all need separate tests. A silent notification may still exist; DND alone is not proof of non-delivery. Android 15 can redact OTP-bearing notifications from untrusted listeners, but this is not documentation of universal View Once-specific redaction. [WA6][AN1][AN14]

For View Once detection, default `viewOnceStatus = unknown`. A generic attachment label yields `mediaHint`, not `viewOnce = true`. Promote only a tested, explicit publisher field/text indicator, with source/locale/parser version and a user correction path. It still proves an exposed label, not content or authenticated sender intent.

**URI trap:** Android's `MessagingStyle.Message` allows an optional data URI and says listeners need permission to access it. [AN3] This is an app-published interface, not a requirement that WhatsApp expose View Once data. A person icon, large icon, content intent, playback action, or `content://` string does not establish an authorized original item. Do not dereference/probe hidden protected-media locations. Modern storage permissions still apply despite legacy reference wording about MediaStore access.

### C2. MediaProjection and secure pixels

MediaProjection is a public API for a **user-approved capture session**, not a grant to override the source application's secure content. Android `FLAG_SECURE` treats window contents as secure and prevents them appearing in screenshots/non-secure displays. Current reference also covers sensitive views marked secure during projection. [AN4]

WhatsApp publicly documents the visual screenshot/recording block, but the exact flag/surface implementation in a specific WhatsApp Android build was **not** inspected. `FLAG_SECURE` explains Android's supported enforcement mechanism; do not claim a verified internal implementation for every client version.

For a compliant third-party recorder, protected pixels may be blank/omitted or capture may fail; exact UX/output needs device testing. Consent to capture is not consent from the source provider to expose secure pixels. No “screen record before opening,” alternate-display, OEM-gap, or other fallback should be shipped to defeat a restriction.

Android 14+ hardens per-session consent and single-use projection tokens and requires the appropriate mediaProjection foreground-service declaration for apps targeting that version. Sessions must handle revocation/`onStop`, release resources, and show truthful coverage. Android 15 QPR1+ adds prominent recording controls and stopping on lock. [AN5] No always-on recorder belongs in this feature.

Google documents old-device limitations in secure-activity guidance; this is not an authorization to target failures, nor a measured WhatsApp capture rate. A system recorder exemption from some Android 15 screenshare protections does not override `FLAG_SECURE` generally. [AN4][AN14]

### C3. Playback audio: source policy and system privilege matter

Android 10+ AudioPlaybackCapture can capture eligible playback into an `AudioRecord` configured with a projection-backed `AudioPlaybackCaptureConfiguration`. A non-system capturer needs `RECORD_AUDIO`, user-approved projection, the same user profile, eligible usage (`USAGE_MEDIA`, `USAGE_GAME`, or `USAGE_UNKNOWN`), and an effective source capture policy that permits third-party capture. Android applies the most restrictive app/player policy. Voice-communication audio is not made eligible simply by microphone/projection consent. [AN6]

**Do not equate a WhatsApp voice note with a telephone/VoIP call**, and do not assume its usage attribute or per-player capture policy without a stock-device test. A video's audio track is similarly separate from its protected pixels.

Android documentation explicitly distinguishes system capture from ordinary apps and describes system-only policies. [AN6] Hence the official native-recorder possibility [WA1] can coexist with unsuccessful third-party Sakshi capture. There is no evidence here that WhatsApp's particular View Once player uses `ALLOW_CAPTURE_BY_ALL` on current phones.

MVP decision: **no Sakshi playback-capture component for View Once**. Let the user independently operate the supported stock recorder if lawful and desired, then explicitly import the resulting artifact. If native recording fails or records silence, stop and offer a statement. Do not switch to microphone/speaker recapture, privileged interfaces, or altered settings to overcome it. Sakshi cannot generate a transcript from silence or metadata.

A future third-party audio feature, if separately requested, would require source-device verification, session-specific consent, visible recording, source-policy compliance, legal/distribution review, and a non-circumvention decision. Merely knowing a generic API exists is not sufficient to advertise support.

### C4. Accessibility APIs

Accessible UI nodes may contain exposed labels, sender text, or Opened state; they are not a media-file, frame-buffer or audio-stream API. Content availability depends on app UI implementation and sensitivity filtering. `FLAG_SECURE` itself should not be misdescribed as disabling all accessibility text: pixel protection and node protection are different mechanisms.

Android's accessibility screenshot APIs include display capture and window capture; the API 34 secure-window error is `ERROR_TAKE_SCREENSHOT_SECURE_WINDOW`. A screenshot-capable service does not gain a secure-pixel exception. [AN7] Android 16 sensitive-view protections can deny access to non-accessibility tools. Sakshi must not falsely declare itself a disability accessibility tool. [AN15]

Google Play allows some non-tool accessibility uses with declarations, approval and dedicated prominent disclosure/consent; it is not categorically banned for all non-disability apps. It does not authorize broad silent scraping or autonomous navigation. [PL2] Auto-opening View Once with clicks would also consume evidence and create user-safety/receipt risk.

MVP decision: do not request accessibility for this feature. No auto-scroll, auto-play, screenshot fallback or unattended UI harvesting.

### C5. Sharesheet, SAF, Photo Picker, MediaStore and providers

| Surface | Actual authority | View Once boundary |
|---|---|---|
| `ACTION_SEND` / `ACTION_SEND_MULTIPLE` | Receive text/stream URIs the source app chooses to share and grants access to | WhatsApp disallows sharing the View Once item. Receiver declarations cannot create a source-side action. Separate lawful recorder/context files may be shared. [WA1][AN8] |
| Storage Access Framework | User selects an exposed document from a provider; permission is scoped to selected result | Cannot enumerate private WhatsApp contents or force a provider to publish a View Once item. Audio/video recording files can be selected if exposed. Persist only grants actually offered. [AN9] |
| Photo Picker | User selects exposed images/video, not arbitrary audio or private message objects | Appropriate for allowed contextual screenshots or video-container recordings. For audio-only files use SAF/share. [AN11] |
| MediaStore | Shared media collections under scoped-storage/permission rules | Not an index of WhatsApp's protected message attachments. “Not in Gallery” alone is not proof of a specific internal path; no supported View Once collection identified. [WA2][AN11] |
| ContentProvider / FileProvider | Exported access/permissions or an owner-issued per-URI grant | No public View Once provider contract identified. Knowing/guessing a URI is not permission. [AN10] |
| `ContentResolver` | Opens an authorized provider result; does not create authority | Import only actual user-selected readable artifacts. Permission denial is a hard boundary, not an invitation to probe another path. |
| Broad/all-files storage | Limited shared-storage scope, not other apps' private data | `MANAGE_EXTERNAL_STORAGE` does not expose other apps' app-specific directories. `/Android/media` shared storage is distinct from private/app-specific data. No broad storage request justified for the MVP. [AN12] |

Intent behavior is source-driven: an `ACTION_VIEW` intent, notification `PendingIntent`, package launch, deep link, or WhatsApp chooser selection is not evidence that an original `EXTRA_STREAM`/URI was granted. Sakshi must not intercept another app's intents or automatically invoke content intents to open an item. The user may navigate normally; Sakshi retains no protected payload merely because it can launch WhatsApp.

### C6. App sandbox and backups

AOSP documents per-app UID/process isolation, kernel-enforced boundaries and SELinux protections, including native code. NLS/storage/accessibility grants do not merge WhatsApp's UID with Sakshi's. [AN13] User ownership of a phone is not an SDK permission to read another app's private files, memory, caches or cryptographic material.

WhatsApp backup restoration is WhatsApp's account/app workflow, not cross-app file access. [WA1] Sakshi must not request WhatsApp backup keys, backup extraction, authentication/session material, or private storage access. Unopened-backup restoration is not a safe preservation/replay promise. No restoring/reinstalling is needed for Sakshi's supported workflows.

## D. Allowed acquisition workflows

“Allowed” here means compatible with the documented surfaces and Sakshi's non-circumvention design. It is **not universal legal authorization**, a provider partnership, or automatic Play approval.

### D1. Notification observation to user-reviewed incident

1. Explain in-app that Android notification access is broad, that Sakshi will immediately discard non-allowlisted packages, and that collection can preserve exposed data after it disappears in the source app. Obtain affirmative, scoped opt-in before linking to system notification-access settings.
2. Observe only granted notification fields from the user-selected WhatsApp app/profile. Do not collect executable actions, private files or sessions.
3. Store exact exposed label and time fields; mark `viewOnceStatus` unknown unless a tested explicit indicator exists. Make duplicates/group summaries visible to parsing logic.
4. Optional neutral alert: **“A message-related notification was observed. You can add an incident note.”** An explicit verified View Once hint can use “The notification indicated View Once,” not “content captured.” No sensitive preview by default.
5. User decides whether/when to view or listen in WhatsApp. Warn that opening can consume the item and may affect receipts; do not pressure them to view distressing content.
6. User enters an account of what happened, optionally linking the observation and allowed context. Show separate user-statement provenance and unknown content status.
7. Local classification may analyze the statement/exposed ordinary text, with attribution. Do not label it an OCR/transcript of unacquired media.

This flow remains useful if the user never opens the item. Missing notification coverage is displayed, not filled with guesses.

### D2. Manual incident statement with no notification access

Available without NLS. Record user-described sender, approximate incident time, modality, whether they saw/heard it, remembered wording, context and uncertainty. Preserve the original statement and subsequent corrections as separate versions. Offer typing first; a user-recorded narration in Sakshi is **the user's statement**, not the WhatsApp voice message. Avoid requesting microphone permission unless that narration feature is actually implemented and selected.

A later note is not contemporaneous capture of the original payload. The report must say when the statement was created and which details are recalled/user-asserted.

### D3. Contextual artifacts through supported import

If currently permitted by WhatsApp and the OS, the user can preserve relevant **ordinary** messages, profile details, visible chat context or a View Once placeholder/Open state, then share/select the artifact. These are contextual records, not the protected content. Current WhatsApp reporting help itself suggests screenshots of relevant messages/calls/profile details for preserving a record. [WA4]

Do not instruct screenshotting the protected viewer. Do not assume a screenshot of context is available in every privacy mode. Ordinary chat export can supply a text file and recent media, but does not promise View Once payload inclusion or a particular placeholder format. The current [WA7] page is now titled **About restricted chat**: it says the latest WhatsApp replaces Advanced Chat Privacy with Restricted chat, retaining export/automatic-gallery/Meta AI restrictions and adding a linked-device restriction. Existing Advanced Chat Privacy chats do not upgrade automatically. Either applicable privacy mode can block export; no downgrade/alternate-session workaround is supported. [WA5][WA7] Accept only what the supported UI actually gives the user and describe omissions.

Save the exact bytes received by Sakshi, hash them, and link any claim about which conversation/item they represent as user-confirmed provenance. No fake authenticity badge.

### D4. User-operated native audio recording, then import

**Documented possibility, not phone-certified here.** [WA1]

- Present this only as an optional audio-specific route, with a clear recording/privacy-law and incidental-data notice. Do not advertise guaranteed recording, original-file preservation or sender invisibility.
- The user independently chooses and operates the phone's normal native recorder while content is legitimately available. Sakshi does not auto-open the message or start the recorder.
- Respect stock failures/restrictions. No changed client, protected-setting override, fallback recapture or third-party capture workaround.
- User reviews the result and explicitly shares/selects it into Sakshi. Use SAF/share for an audio file; SAF/share/Photo Picker for a recorder video container. System recorder output format and availability vary.
- Preserve received recording bytes as **user-imported native recording**, not “original WhatsApp audio.” Record which parts of the source attribution are user assertions.
- Only after actual audible data is imported, create local STT/audio extraction as a derivative. Keep container, extracted audio, transcript and edits separate, with hashes/versions and uncertainty.
- If audio is absent/truncated/inaudible, state that and return to a user statement. Do not infer missing speech.

An already acquired recording can still be analyzed after the WhatsApp item is consumed. This is analysis of a separate preserved artifact, not recovery from WhatsApp.

### D5. WhatsApp's reporting path

Give user-controlled instructions to WhatsApp's in-app report flow, including the documented photo/video viewer option. [WA3] Explain that reported View Once media/voice can be provided to WhatsApp and ordinary reporting can include up to five recent messages, source IDs/time/type and some call metadata. [WA1][WA3][WA4] It is a deliberate disclosure outside Sakshi's local processing.

Do not auto-report, misuse reporting to obtain a copy, delete/block without user action, or promise an outcome. Reporting is not a local evidence vault or law-enforcement complaint. User can record “I reported it” and any acknowledgment they legitimately have; Sakshi cannot verify the server submission from that assertion. Exact voice-specific reporting menu and post-consumption availability require phone testing; do not delay a potentially available report based on an invented retention window.

### D6. Independent lawful files and external-recording caveat

Official Help acknowledges other-device recapture as a privacy limitation. [WA1][WA2] It is not a supported Android original-item transfer, an endorsement to defeat protections, or legal clearance. Do not provide a View Once external-capture mode or market “use another device” as a preservation workaround.

A general-purpose user-selected importer may accept a lawfully held independent file, including a copy separately provided through ordinary permitted sharing. Preserve its acquisition description, transformations and claimed relation to the incident. Do not automatically certify that it matches the View Once original. No automated surveillance or physical recapture instructions are needed for the MVP.

## E. Unsupported workflows and bypass boundaries

| Proposal | Classification | Why excluded / supported alternative |
|---|---|---|
| “NLS downloads the View Once attachment” | Unsupported assumption | Notification metadata is not a guaranteed attachment URI or media file. Preserve exposed fields or a statement. |
| “Read the circled 1 from every notification” | Unverified assumption | Chat icon does not establish notification data schema. Test explicit indicators and retain Unknown. |
| “Projection with user consent captures everything” | Protection bypass if used against secure visual content | Capture consent does not override secure windows. Import permitted context or supported independent audio artifact. |
| “Accessibility screenshot avoids screenshot blocking” | Protection bypass | Secure-window screenshot failure is documented. No fallback collector. |
| “OCR/STT from notifications identifies the hidden threat” | Unsupported / fabricated evidence | No pixels/audio acquired. Analyze actual exposed text or attributed user statement only. |
| “Start playback/recording whenever a voice hint appears” | Excluded automation / safety risk | No verified voice-capture contract; may consume item, reveal receipt and capture unrelated speech. User-directed stock recording only. |
| “Native recorder works, therefore Sakshi playback capture works” | Unsupported privilege inference | System and third-party capture policies differ. Do not claim support without verification. |
| “If audio capture is denied, use another path to force recording” | Circumvention | Respect source/OS capture policy. Fall back to a statement, not another capture mechanism. |
| “Request all-files access and watch for transient View Once files” | Excluded protection/private-storage harvesting | No supported View Once storage surface. Use chosen exposed documents. No private-path discovery. |
| “Guess provider URI or invoke hidden intent” | Unauthorized/undocumented access | Owner grant and supported contract required. A URI string is not authority. |
| “Open before expiry automatically” | Excluded automation | Can consume evidence and affect sender signals. Let the user decide; no false deadline from notification time. |
| “Read again after exit/expiration” | No supported retrieval | Statements and previously acquired artifacts may remain; consumed media cannot be recovered through Sakshi. |
| “Backups guarantee View Once preservation/replay” | Unsupported and potential lifecycle bypass | Unopened-at-backup condition does not grant extraction/replay. No restore cycles or key requests. |
| “Report it to retrieve the original for Sakshi” | Unsupported / reporting misuse | Report discloses to WhatsApp, not to a third-party importer. Use genuine user-controlled report flow. |
| “Modified/older client or alternate session solves access” | Circumvention | Do not recommend downgrading, modified clients or session/device harvesting. |
| “Capture on builds where the protection fails” | Circumvention-oriented design | Document a limitation responsibly; do not exploit failures as a feature. |

No techniques for these excluded routes were researched or supplied. Root, encryption bypass, hidden databases, credentials, malware, interception and protected-content extraction are out of scope.

## F. Privacy, security and policy risks

### F1. Four separate authorization gates

1. **Android authority:** The actual API grant must authorize the precise data/session/URI.
2. **Provider controls/terms:** WhatsApp's sharing, secure-content and lifecycle restrictions remain. Terms prohibit unauthorized access/collection, security interference, reverse engineering and misuse of reporting channels. [WA11]
3. **Distribution policy:** Google Play requires justified permissions, disclosed expected use, consent and secure handling; notification theft/unexpected transmission can violate spyware rules. Accessibility has separate declarations and restrictions. [PL1][PL2][PL3][PL4]
4. **Applicable law/rights:** Ownership of the phone, receipt of a message, or OS permission is not blanket permission to record others, retain intimate/third-party material, disclose it or submit it as evidence. Recording, privacy, copyright, minors and evidence handling need market-specific legal review. No legal exception or guaranteed admissibility is asserted here.

A WhatsApp warning that audio recording is possible establishes **technical possibility**, not blanket authorization to record every sender. Conversely, consent and a safety purpose do not license a protection bypass. Native-recorder import can be a policy-compatible workflow only when its actual use satisfies all applicable gates.

### F2. Risk register

| Risk | Failure mode | Required mitigation |
|---|---|---|
| Broad notification grant | Other apps' messages, OTPs, financial/medical data exposed to listener | Explain broad grant; immediately discard non-allowlisted packages; bounded typed collection; no whole-object logs or telemetry; user pause/revoke/delete. |
| Persistence surprises | User expects ephemeral data; Sakshi retains label/name or context | Explain retention before opt-in; show scope/retention settings; no covert deletion-recovery marketing. |
| Detection error | Generic media mislabeled View Once or harassment | Unknown default; explicit tested markers only; retain exact labels; user corrections; never classify unseen content. |
| Coverage gaps | Foreground chats/locked chats/OEM limits/listener gaps appear safe | Coverage epochs and unavailable states; absence is not evidence of no incident. |
| Identity/time ambiguity | Contact rename, group title, clock drift or repost becomes legal certainty | Separate display identity, user association and clocks; no authenticated sender/time badge. |
| User safety | Alerts expose help-seeking; viewing triggers receipts/distress | Neutral alerts off by default; user-controlled viewing; no auto-play/report/block/delete; warn about possible receipts without guaranteeing invisibility. |
| Recorder overscope | Other notifications, contacts, ambient speech or unrelated app data captured | Separate user-operated recorder; review before Save/export; minimal relevant derivative with preserved imported container; communicate original remains outside Sakshi too. |
| Media authenticity | Recording/screenshot treated as original authenticated evidence | Artifact type and user-asserted source attribution; hash only received bytes; preserve provenance and unknown transformations. |
| Vault compromise | Sensitive evidence visible at rest or in logs/backups | Keystore-backed authenticated encryption, private storage, no plaintext temporary/log copies, protected Sakshi UI, controlled exports and tested backup exclusions. [AN16][AN17] |
| Background encryption | Auth-required key unavailable in callback/locked state | Define a tested ingestion/unlock design; do not assume biometric key works in background. No plaintext fallback. Record gaps if secure persistence unavailable. |
| Key loss | Uninstall/reset/invalidation destroys ability to decrypt | Disclose recovery limits; user-controlled export where appropriate; don't imply Keystore creates recoverable cloud backup. |
| Native recorder gallery/cloud | Recording exists in system shared storage and might auto-sync before import | Warn before recording; review device sync behavior; importing to encrypted vault does not remove source/cloud copies. No automatic external deletion. |
| Report disclosure | View Once content and recent messages leave device for WhatsApp | Explicit separate user-controlled action and disclosure; no report automation or inferred upload consent. |
| AI hallucination | Model invents visual/audio details or legal conclusions | Analyze only available inputs; evidence links, observed/user-reported/inferred/pattern/unknown labels, user review, no claims of guilt. |
| Malicious imported files/text | Parser/decoder abuse, zip traversal, prompt injection | Bounded safe import, verify content type/size, app-generated paths, no script execution, isolated parsers and treat evidence text as data, not AI instructions. |
| Excess permissions | Broad storage/accessibility/microphone requested without feature need | NLS optional; narrow share/SAF/picker import. No accessibility/all-files/playback recorder permissions in View Once MVP. [PL1] |

Keystore protects key material and can bind it to hardware; it is not proof the app or unlocked device cannot disclose plaintext. [AN16] Exclude evidence, notification snapshots, transcripts and reports from unwanted backup/D2D/cross-platform migration using the applicable Android rules and target-device tests. `allowBackup=false` alone is not a universal OEM D2D guarantee. [AN17]

## G. Hackathon-feasible implementation

### G1. Scope and user-facing contract

**Core demo:** optional observation, user incident statement, allowed context import, encrypted artifact storage, local review and explicit report export. **Optional live audio demo:** only on a tested stock phone using the native recorder and user-selected import. No claim of recording support if the device branch has not been demonstrated.

User-facing copy:

> Sakshi cannot retrieve the original View Once photo, video, or voice file or reopen it after viewing. If enabled, it can retain information exposed in notifications. You can add your own account and import evidence you are permitted to preserve. WhatsApp documents that native screen recorders may record View Once audio; availability varies. Imported recordings are separate artifacts, not the original WhatsApp file.

Keep notification-free manual mode fully functional. No cloud AI, WhatsApp login/session integration, backend, accessibility collector, playback recorder, all-files access, or original View Once importer is necessary.

### G2. Minimal module responsibilities

| Module | Responsibilities | Boundary |
|---|---|---|
| Consent / coverage | Opt-in, selected package/profile, pause/revoke, retention and coverage status | System access is broad; app collection must be narrow. |
| Notification observer | Short NLS callback; bounded exposed fields; safe encrypted persistence; summaries/reposts marked | No auto-open, URI probing, hidden file reads or all-message claim. |
| Hint parser | Structured messages first; versioned locale-aware hints; Unknown default | No generic Photo-to-View Once conversion, no unseen-content inference. |
| Incident editor | User describes receipt/view/listen/report; uncertainty; original statement plus revisions | User assertion is not app-observed original content. |
| Import reviewer | Sharesheet, SAF, Photo Picker as appropriate; preview/Save; content/size checks | Only actual grants and selected files; no background enumeration. |
| Vault / provenance | Preserve bytes, SHA-256, Keystore-backed AES-GCM with unique nonces and authenticated metadata; separate derivatives | No plaintext fallback; hash is not authenticity/admissibility proof. |
| Local processing | OCR for available visuals; STT for imported audible audio; conservative text review | No processing jobs for unavailable payloads; no model-generated surrogate media/transcript. |
| Timeline / report | Link observations, statements and imports; mark counts/dedup uncertainty; explicit reviewed export | Patterns describe available evidence, not hidden content or legal guilt. |

This is a design, not Kotlin implementation in this research repository. Library/model selection is intentionally deferred; do not assume a mobile classifier/STT dependency exists or is accurate on the target languages.

### G3. Provenance contract

Use typed, separable records:

- `NotificationObservation`: id, package/profile, notification key, group/summary state, post time, collection wall/monotonic time, app-supplied time, minimal exposed label/Person fields, parsed hint/unknown reason, parser version, consent/coverage epoch and protected serialized snapshot hash.
- `IncidentStatement`: id, immutable original text, statement-created time, user-described incident time/uncertainty, claimed sender/modality/View Once/open/report states, revisions, linked observation ids. No promotion of user assertion to system observation.
- `ImportedArtifact`: id, importer mechanism, actual media/container MIME, bytes/size/SHA-256, received time, acquisition category (`context_screenshot`, `ordinary_export`, `native_recording`, `independent_file`), user-asserted acquisition/source details and relevant consent.
- `DerivedArtifact`: parent artifact id/hash, OCR/STT/model identity/version, language, creation time, extraction boundaries, uncertainty and user edits. Keep exact received container distinct from extracted audio and transcript.
- `Finding`: evidence ids, claim type, input availability, human review status and limitations. No visual/audio finding with no available media input.

`viewOnceStatus` should distinguish `unknown`, `notification_explicit_hint`, and `user_reported`. `contentAvailability` should distinguish `not_acquired`, `context_only`, and `independent_recording_or_copy`. Never assign `original_view_once_media` merely because the user says a recording depicts that item.

Do not create an original-file hash for an unavailable item. Hash a canonical notification snapshot only as that snapshot, not as the photo/voice. Amend associations without silently rewriting original artifacts. A local hash helps detect changes to stored bytes; it does not prove who authored them, when WhatsApp sent them, or court admissibility.

Example report wording:

> A notification associated with WhatsApp was observed at [post time]. Its exposed label was [label]. View Once status was [unknown / user-reported / explicit notification hint]. The original media was not acquired. The user entered the following account at [statement time]. [If present:] A separate user-imported native recording is attached; its claimed relation to the WhatsApp item is user-provided. Transcript and AI findings are derivatives and have been reviewed by the user.

### G4. Real-device verification protocol

Use two consenting test accounts and harmless synthetic fixtures. Record stock Android/OEM build, WhatsApp version (personal and Business tested separately if supported), Sakshi version/target SDK, locale, notification/privacy settings, profile and native-recorder details. No hostile content, altered clients or disabled security controls.

| Test | Safe procedure / question | Acceptance / evidence to record |
|---|---|---|
| Photo/video/voice receipt | Send one harmless View Once item of each modality | Capture only public NLS fixture fields; record whether any notification exists and whether type/View Once is explicit. |
| Ordinary-media control | Send equivalent ordinary items | Generic labels must not become View Once positives; no content classification from labels. |
| Foreground/background/locked | Receive while WhatsApp active, background and phone locked | Measure message-to-notification and posted-notification-to-listener coverage separately. |
| Locked chat / preview off | Use standard privacy settings | Respect redaction; missing sender/text stays unknown. Lock screen UI redaction alone is not assumed to describe listener extras. |
| Muted/group/burst/update | Use normal settings and multiple same-content items | Avoid summary/historic-entry count inflation; record ambiguity rather than false precision. |
| Permission revoke/reconnect | Revoke listener, then restore normally | No new collection after revocation; no fictional missed-history recovery. Outstanding snapshot marked as such. |
| Standard visual screenshot/recording restriction | One normal stock attempt on synthetic protected visuals, without any override | Document blocked/blank/result behavior only; no alternate attempt to defeat it, no promise of capture. |
| Native voice audio recording | User operates normal built-in recorder with one harmless voice item | Verify audible output, truncation/routing and whether UI pixels are blank. Record supported/not-supported for that exact configuration only. |
| Video audio nuance | If testing audio scope, same stock recorder with harmless video | Do not infer voice or video-audio success from each other; no claim of video-pixel capture. |
| Recording import | Select real recorder output using supported share/SAF/picker | Exact received-byte hash, artifact source label and retained original container; local STT only if actual audio available. |
| Failed/silent recording | Import synthetic silent/partial recording | Visible unavailable/partial transcript state, no invented words or fallback capture. |
| Viewer exit / interrupted play | Normal viewing/listening and exit with harmless item | Record observed replay/interrupt UX; Sakshi does not try recovery or manipulate lifecycle. |
| Share/export restriction | Inspect normal View Once UI and selected permitted chat export | No unsupported item share; actual placeholder/media omissions described. Advanced Chat Privacy denial respected. |
| Reporting | Inspect available native UI; actual submission only with legitimate reason and explicit tester authorization | Verify voice vs photo/video menus, disclosure and side effects; never file groundless reports just to obtain content. |
| Unopened expiry/backup | Long-running future test, not a rushed hackathon simulation | Official rule cited in meantime; do not change system clocks, restore consumed items or wipe/reinstall to fake validation. |
| Vault / backup / offline | Synthetic artifacts, disk/key errors, offline mode and supported backup transports | No plaintext/log/cloud fallback; hashes stable; encryption failure explicit; unwanted transfer exclusions verified. |
| Export | User reviews redacted report and consciously chooses destination | No automatic upload/share; provenance/unavailable-content statements intact. |

Measure false View Once positives, unknown rates, sender/type parsing errors, notification repost duplication, missing-notification cases, callback latency, encrypted-store failures and native-audio outcomes **with a denominator and exact configuration**. “Listener connected” is not 100% message coverage. Pure notification observation cannot measure missed source messages without ground truth.

No listed phone test was run in this investigation. The hackathon demo should use genuinely tested stock-device capabilities; synthetic UI fixtures are labeled synthetic and cannot certify WhatsApp acquisition support.

### G5. Acceptance requirements

- **VO-01:** No protected original-item extraction or replay interface exists in the feature.
- **VO-02:** Generic media labels do not produce a confirmed View Once badge or media-content finding.
- **VO-03:** Notification snapshots, user statements, contextual artifacts, independent recordings and derivatives have distinct provenance and clocks.
- **VO-04:** No NLS auto-open, auto-play, native-recorder activation, unattended capture, auto-report, block or deletion.
- **VO-05:** Native audio possibility is described accurately and conditionally; no “all recording impossible” claim and no third-party capture guarantee.
- **VO-06:** Capture/URI/export denials end that route; no protection-override fallback.
- **VO-07:** Transcript/OCR jobs run only on available actual artifacts; silence and unavailable content remain explicit.
- **VO-08:** Data is encrypted locally, not logged/uploaded; backup/migration exclusions and key/error handling are tested before evidence use.
- **VO-09:** Revocation stops new observation; manual mode remains available; coverage/identity/time ambiguity is visible.
- **VO-10:** Every report shows acquisition source, human review, limitations and that hashes do not establish authenticity/admissibility.
- **VO-11:** Any claimed supported native audio configuration has a real stock-phone test; untested configurations are not advertised as certified.
- **VO-12:** Distribution/legal review considers actual collection/recording/sharing flow; no provider affiliation or blanket legal clearance implied.

## H. Claims Sakshi must NOT make

1. “Sakshi automatically captures every WhatsApp View Once photo, video or voice message.”
2. “Notification access gives us the original media, its decryption keys or its underlying URI.”
3. “Every Photo/Video/Voice message notification is a View Once item.”
4. “We reliably detect all View Once arrivals, including hidden/locked/muted/foreground chats.”
5. “Granting screen recording or accessibility overrides WhatsApp's protection.”
6. “All View Once recording, including native audio recording, is impossible.” Official Help explicitly acknowledges native audio recording.
7. “Native audio recording proves Sakshi's third-party playback-capture API works.”
8. “We can share, forward, copy, star, save or export the received View Once item through supported WhatsApp UI.”
9. “View Once media is always excluded from backups.” Unopened-at-backup restoration is documented.
10. “A backup lets us recover/replay consumed or expired View Once media.”
11. “Opened means every copy has been securely erased instantly from device, servers and backups.”
12. “We can access the content again after the user closes the viewer.”
13. “WhatsApp's report feature sends us the original or guarantees preservation, action or police reporting.”
14. “A media label tells our AI what the image showed or what the speaker said.”
15. “A user statement is a transcript/OCR of the original media.”
16. “A native recording/screenshot is the original authenticated WhatsApp file.”
17. “A SHA-256 hash proves sender identity, authenticity, guilt, source send time or court admissibility.”
18. “Notification posting time is the message's trusted sent/arrival/expiry time.”
19. “Viewing/listening/recording is guaranteed invisible to the sender.”
20. “User consent, receiving the message, a safety purpose or phone ownership makes every recording/extraction legal.”
21. “WhatsApp/Google approves or endorses Sakshi's View Once integration.”
22. “A second-device capture is an Android-supported original-item export or a guaranteed lawful workaround.”
23. “All-files access exposes all of WhatsApp storage.”
24. “A successful result on one OEM/version proves universal Android support.”
25. “Importing into the vault removes all recorder/gallery/cloud copies.”
26. “No notification or no captured content means no harassment occurred.”
27. “Repeated View Once labels prove harassment, escalation, intent to evade evidence, or guilt.”
28. “The current Help Center was verified only by a title/search snippet.” It was rendered and inspected; source-access limitations should accurately describe the successful recovery too.

**Permissible product promise:** “Sakshi helps you preserve information exposed in opted-in notifications, your own incident account, and evidence you explicitly import. It cannot retrieve or reopen original View Once items. Supported native audio recordings may be imported as separate artifacts; availability and permissions vary.”

## Source register

All sources consulted on **2 October 2026**. **Rendered** means primary page body inspected with read-only Chromium. **Full/relevant body** means fetched text and relevant passages inspected, including overflow where necessary. Source dates are publication dates where given, not device certification dates. URLs are primary documentation unless labeled contextual.

### WhatsApp / Meta

- **[WA1] Rendered, decisive:** WhatsApp Help, **About view once**. Three modalities, 14 days from sending, forward/copy/save/star/share restrictions, unopened backup restoration, reported-content disclosure, encrypted server retention and explicit native audio recording possibility. https://faq.whatsapp.com/1077018839582332/
- **[WA2] Rendered, Android:** WhatsApp Help, **How to send and open view once media and voice messages**. Visual screenshot/screen-recording/gallery restrictions, tap/view/listen/exit flow and Opened state. https://faq.whatsapp.com/578442220724722/?locale=en_US&cms_platform=android
- **[WA3] Rendered, Android:** WhatsApp Help, **How to block and report someone**. View Once photo/video reporting, recent-message/call/ID/time/type disclosures and report/block/delete UI effects. https://faq.whatsapp.com/1142481766359885/?locale=en_US&cms_platform=android
- **[WA4] Rendered, final URL:** WhatsApp Help, **About reporting and blocking on WhatsApp**. In-app reporting only, up to five recent messages, no guaranteed enforcement, contextual preservation guidance. Initial locale URL navigated to web-platform URL; final source inspected. https://faq.whatsapp.com/414631957536067/?cms_platform=web&locale=en_US
- **[WA5] Rendered, Android:** WhatsApp Help, **How to export your chat history**. Text export and most-recent media attachments via Sharesheet; not a backup; no verified View Once placeholder schema. https://faq.whatsapp.com/1180414079177245/?cms_platform=android
- **[WA6] Rendered, Android:** WhatsApp Help, **How to manage your notifications**. Notification settings and locked-chat name/content hiding across notification surfaces; no View Once extras schema. https://faq.whatsapp.com/797069521522888/?helpref=faq_content&cms_platform=android
- **[WA7] Rendered, Android, current title:** WhatsApp Help, **About restricted chat** at the former Advanced Chat Privacy URL. Latest-client Restricted chat retains export/automatic-gallery/Meta AI restrictions and adds linked-device restrictions; legacy Advanced Chat Privacy chats do not upgrade automatically. Version/business-context caveats apply. https://faq.whatsapp.com/715385484388016/?cms_platform=android
- **[WA8] Full, historical:** WhatsApp Blog, **View Once Photos and Videos on WhatsApp**, 3 August 2021. One-time icon, disappearance and Opened state. https://blog.whatsapp.com/view-once-photos-and-videos-on-whatsapp?lang=en ; Meta, **New Privacy Features on WhatsApp**, 9 August 2022, screenshot-blocking announcement described testing then. https://about.fb.com/news/2022/08/new-privacy-features-on-whatsapp/
- **[WA9] Rendered, Android:** WhatsApp Help, **How to check read receipts**. General group/read/play receipt exceptions; don't silently generalize to every View Once edge case. https://faq.whatsapp.com/665923838265756/?locale=en_US&cms_platform=android
- **[WA10] Full, historical:** WhatsApp Blog, **Voice messages just got more private**, 7 December 2023. Voice can only be played one time; E2EE. https://blog.whatsapp.com/voice-messages-just-got-more-private ; Meta parallel announcement: https://about.fb.com/news/2023/12/whatsapp-view-once-voice-messages/
- **[WA11] Full/relevant terms:** WhatsApp **Terms of Service**, fetched page displays effective date 4 January 2021. Unauthorized access/collection, interference, reverse engineering and reporting misuse restrictions. Different regional terms may apply; no universal legal conclusion. https://www.whatsapp.com/legal/terms-of-service

### Android / AOSP

- **[AN1] Full/relevant body:** NotificationListenerService. Binding, callbacks, connection and low-RAM/work-profile limits. https://developer.android.com/reference/android/service/notification/NotificationListenerService
- **[AN2] Full/relevant body:** StatusBarNotification. Key, package/profile and post time distinct from `Notification.when`. https://developer.android.com/reference/android/service/notification/StatusBarNotification
- **[AN3] Full/relevant body:** Notification.MessagingStyle.Message. Exposed text/Person/time/MIME/URI and URI permissions; no WhatsApp-specific publishing guarantee. https://developer.android.com/reference/android/app/Notification.MessagingStyle.Message
- **[AN4] Full/relevant body:** WindowManager.LayoutParams, `FLAG_SECURE`. Secure screenshots/non-secure display and projection-sensitive view behavior. https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_SECURE ; secure-activity limitations: https://developer.android.com/security/fraud-prevention/activities
- **[AN5] Full/relevant body:** Media projection. Per-session consent, tokens, Android 14 foreground service and app-sharing behavior, stop/chip/lock handling. https://developer.android.com/media/grow/media-projection
- **[AN6] Full/relevant body:** Capture video and audio playback. AudioPlaybackCapture eligibility, same profile, usage, most restrictive policy, system/non-system distinction. https://developer.android.com/media/platform/av-capture
- **[AN7] Full/relevant body:** AccessibilityService. Window/node access, screenshots and `ERROR_TAKE_SCREENSHOT_SECURE_WINDOW` (API 34). https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
- **[AN8] Full/relevant body:** Receive simple data from other apps. ACTION_SEND/MULTIPLE, selected share target and stream/text input. https://developer.android.com/training/sharing/receive
- **[AN9] Full/relevant body:** Access documents and other files. SAF selection, scoped grants/persistence and limitations; no new permission from URI conversion. https://developer.android.com/training/data-storage/shared/documents-files
- **[AN10] Full/relevant body:** Restrict interactions with other apps. ContentProvider read/write permissions and per-URI grants. https://developer.android.com/training/permissions/restrict-interactions
- **[AN11] Full/relevant body:** Access media files from shared storage. MediaStore, modern permissions and Photo Picker recommendation/linked guidance. https://developer.android.com/training/data-storage/shared/media
- **[AN12] Full/relevant body:** Manage all files on a storage device. Shared storage scope and inaccessible other-app app-specific directories. https://developer.android.com/training/data-storage/manage-all-files
- **[AN13] Full/relevant body:** AOSP Application Sandbox. UID/process/kernel/native/SELinux boundaries. https://source.android.com/docs/security/app-sandbox
- **[AN14] Full/relevant body:** Android 15 all-app changes. OTP listener redaction, private-space stoppage, screenshare protections and default-recorder distinction. https://developer.android.com/about/versions/15/behavior-changes-all
- **[AN15] Full/relevant body:** Android security blog, **Enhancing Android security: Stop malware from snooping on your app data**. Android 16 sensitive accessibility views and non-tool restrictions; deceptive tool declarations rejected. https://developer.android.com/blog/posts/enhancing-android-security-stop-malware-from-snooping-on-your-app-data
- **[AN16] Full/relevant body:** Android Keystore system. Non-exportable keys, hardware/auth restrictions and compromised-process limitations. https://developer.android.com/privacy-and-security/keystore
- **[AN17] Full/relevant body:** Auto Backup. Default participation, cloud/D2D differences, exclusions, target rules and cross-platform transport guidance. https://developer.android.com/identity/data/autobackup

### Google Play policy

- **[PL1] Full/relevant body:** Permissions and APIs that Access Sensitive Information. Necessity, incremental consent, expected purposes and restricted API obligations. Future January 2027 changes shown on page are not treated as already effective. https://support.google.com/googleplay/android-developer/answer/16558241?hl=en
- **[PL2] Full:** Use of the AccessibilityService API. Tool qualification, non-tool declarations/disclosure/approval and automation limits. https://support.google.com/googleplay/android-developer/answer/10964491
- **[PL3] Full:** Understanding Google Play's spyware policy. Notification theft, unexpected data access/transmission and disclosed compliant purpose. https://support.google.com/googleplay/android-developer/answer/14745000?hl=en-GB
- **[PL4] Full/relevant body:** User Data. Personal/sensitive data, secure handling, prominent consent and responsibility for third-party code/AI. https://support.google.com/googleplay/android-developer/answer/10144311?hl=en

### Contextual corroboration, not decision authority

Native-audio caveat was noticed in Android Authority's 7 December 2023 launch report, then independently verified in the **current official [WA1] rendered page**. Older expiration/backup reporting was similarly superseded by [WA1]. These secondary reports are not necessary to justify the capability matrix:

- https://www.androidauthority.com/whatsapp-voice-messages-3392406/
- https://indianexpress.com/article/technology/tech-news-technology/whatsapp-view-once-audio-message-9059662/
- https://timesofindia.indiatimes.com/technology/tech-tips/how-to-send-and-open-view-once-media-and-voice-messages-on-whatsapp/articleshow/109289305.cms

## Final implementation implications

1. **Ship metadata + statements + permitted imports**, with explicit unavailable-content states.
2. **Include the official native-audio nuance**, but certify only stock-phone configurations actually tested; user recording/import is not original extraction.
3. **Exclude automatic protected-media capture/recovery** and all protection-override fallbacks.
4. **Treat notification View Once identity and sender/type fields as unverified until fixtures**, not a product guarantee.
5. **Keep media bytes, user statements, context and derivatives separate**, and prohibit AI claims about absent inputs.
6. **Make recording/report/export disclosure and user-safety decisions explicit**, without promising legal admissibility or invisibility.
7. **Recheck current Help/API/policy and real devices before release.** The core MVP still works if no notification is received and the stock audio recording branch is unavailable.
