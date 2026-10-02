# Android Evidence Acquisition Specification - Sakshi

**Research date:** 2 October 2026

**Status:** Source-backed design specification, not an implemented or device-certified acquisition system.

**Audience:** Android engineers, product/security reviewers, and Play release owners.

**Operating assumption:** An ordinary, non-rooted Android application, installed by and used for the device/account owner. No privileged OEM integration, private database extraction, covert monitoring, or protection bypasses.

## 1. Engineering decision

Sakshi can implement **consented capture of exposed notification observations** and **explicit imports of user-selected text, screenshots, recordings, ordinary media, and supported exports**. It cannot implement a universal reader of messaging apps, reliably capture every new message, recover uncaptured deleted content, or extract protected View Once payloads through these mechanisms.

An Android API being supported does not mean that WhatsApp, Instagram, Telegram, Signal, or Messenger publishes a stable schema or complete content through it. The reliable acquisition unit is an **observation or imported artifact**, not necessarily a complete message.

| Decision | Acquisition path | Product boundary |
|---|---|---|
| Implement first | Android Sharesheet; SAF; Photo Picker; manual incident entry | User chooses evidence; copy only the selected material; preserve provenance limitations. |
| Implement with explicit opt-in | NotificationListenerService (NLS) | Best-effort observations from approved source apps, not historical chats or guaranteed real-time messages. |
| Optional, later | Short, user-started MediaProjection sessions | Unprotected visible UI pixels only; stop on OS revocation; no original attachment or protected-content promise. |
| Do not use for baseline | AccessibilityService | Supported API, but UI-dependent, highly sensitive, increasingly restricted, and policy-reviewed. No silent scraping or autonomous chat navigation. |
| Separate policy-gated feature | Direct SMS/MMS access | Android permissions and Play eligibility are separate gates. Do not use NLS/accessibility as a workaround for SMS restrictions. |
| Out of scope | Telegram authenticated client; Meta business messaging integrations | Legitimate APIs exist in limited contexts, but these are separate online integrations, not access to another app's Android sandbox. |
| Reject | Private databases, backup/key extraction, protected media capture, covert linked sessions | Unsupported access or privacy/security circumvention. |

**Critical distinctions:**

- A notification saying “Photo” is evidence of that label, not the image.
- A notification thumbnail is a preview, not the original attachment.
- A notification being removed is not evidence that a chat message was deleted.
- A stored notification excerpt surviving later deletion is prior capture, not deleted-message recovery.
- A notification timestamp is not automatically a sender's message timestamp.
- An imported archive is a user-provided snapshot, not proof of completeness or authenticity.
- “No observation” means unknown coverage, not “no message” or “no harassment.”

## 2. Classification and evidence standard

### 2.1 Required access classes

| Class | Meaning | Examples | Sakshi disposition |
|---|---|---|---|
| **A - officially supported Android access** | Public Android API operating within its documented authorization boundary | NLS callbacks; Telephony provider with valid permissions; app-owned private storage | Eligible, subject to consent, policy and runtime checks. |
| **B - user-mediated access** | User deliberately selects, shares, exports or captures an artifact | ACTION_SEND; ACTION_OPEN_DOCUMENT; Photo Picker; allowed screenshot import | Preferred acquisition path. Usually uses an A-class Android API underneath. |
| **C - opportunistic/conditional access** | Supported surface, but availability depends on publishing app/settings/version/profile/grants | Notification message text, sender, MIME hints, preview image, visible accessibility nodes | Never promise completeness; test and degrade gracefully. |
| **D - unsupported access** | No documented entitlement or supported acquisition contract for the requested data | Ordinary app querying another messenger's private history; universal deleted-message recovery | Do not implement; offer B-class alternatives. |
| **E - privacy/security circumvention** | Defeating explicit controls or obtaining data without legitimate authorization | Sandbox/key extraction; protected View Once bypass; concealed account/device monitoring | Prohibited. No implementation or bypass research. |

Classes are composable: **A/C** means the Android mechanism is official but the requested payload is conditional; **A/B** means an official mechanism with per-item user selection. A D-class request becomes E if pursued by defeating protections.

### 2.2 What “possible” and “reliable” mean

- **Conditional:** The surface can expose the item, but app-specific behavior has not been certified.
- **Yes, selected:** The selected and readable artifact can be imported; this says nothing about unselected or unavailable data.
- **No:** Not available through the specified supported path.
- **H-artifact:** High feasibility of preserving a readable selected artifact; no authenticity/completeness guarantee.
- **M-observation:** Reasonable event delivery when the source actually posts and the listener is connected; not a measured capture rate.
- **L-content:** Highly conditional text/identity/media semantics or lifecycle; device testing required.
- **None:** No supported recovery/acquisition pathway established.

Reliability grades are engineering judgments, not benchmark results. Every matrix row inherits the profile, lifecycle, consent and policy restrictions below. App notification layouts and original-media URI grants are **not independently verified on a phone in this investigation**.

### 2.3 Research limitations

Primary Android documentation and Play policy were inspected, together with official app help, vendor announcements and Telegram API documentation. Some WhatsApp/Instagram pages returned empty fetch bodies; some Signal help pages returned HTTP 403. Their indexed primary-source passages were available through search and are explicitly identified in the source register. This is weaker than inspecting a full page. Do not substitute third-party “recovery” articles for evidence.

An important conflicting source was found: unofficial Signal documentation claimed a plaintext export. The official Android repository does contain export underpinnings in a March 2026 commit, but that commit includes **internal backup playground UI**, not proof that a stable public export option is shipped. Therefore Signal historical import remains **conditional/unconfirmed**, not “impossible forever” and not “supported today on every Android release.” [APP13]

Android 17's summary page mentions “restricted message access,” while the inspected detailed all-apps page did not explain that entry. Do not generalize that headline into a claim that all E2EE notifications are blocked. The detailed, verified Android 17 changes concern SMS OTP availability; the unresolved entry remains a validation question. [AV5][AV6][AV7]

## 3. Legal authorization, terms and distribution are separate gates

This document establishes technical support boundaries and policy risks, not jurisdiction-wide legal permission. No deployment jurisdiction, age range, enterprise use or recording-law context was supplied.

Before release, all four gates must pass:

1. **Platform authority:** Android granted the documented permission or URI/session access.
2. **User authority:** The user is entitled to access the device/account/material. Permission from a phone holder is not automatically permission to monitor another person's account or an employer-managed profile.
3. **Permitted processing:** Determine applicable privacy, interception/recording, child-protection, intimate-content, retention and disclosure rules. Counterparties' data and sensitive content require review; do not assume every sender must consent, or that owner consent alone settles all law.
4. **Distribution and service terms:** Google Play policy and relevant provider terms/API rules still apply. A successful sideload test is not Play approval or legal clearance.

For an India-first release, the DPDP Act's personal/domestic exclusion and lawful-purpose framework are relevant, but do not automatically exempt a vendor's telemetry, support uploads, cloud processing or institutional deployment. Confirm current commencement notifications and final rules rather than relying on a draft explanatory note. The IT Act section 43 addresses access/copying without permission; section 66 adds the dishonest/fraudulent criminal context. No conclusion that this specific product is lawful follows from those provisions alone. [LEGAL1][LEGAL2]

For EU deployment, GDPR Article 2's household exclusion, Article 6 lawful basis, Article 9 sensitive data and Recital 18's qualification concerning providers require analysis. “Local-only” and “household use” are not universal vendor exemptions. Legal-claims provisions may be relevant to evidence retention, but must not be asserted as blanket permission. [LEGAL3]

**Release requirement:** Jurisdiction-specific counsel review of the actual data flows, disclosures, target users and export/recording features. Do not request the alleged harasser's consent automatically or contact them; that would be a separate user-safety/product decision.

## 4. Mechanism specifications

### M1. NotificationListenerService / Android notification access

**Exposed:** Notifications posted, updated, removed and ranked for users/profiles the system allows the listener to see. `StatusBarNotification` supplies package, ID/tag, key, user, posting time and grouping information. `Notification` supplies extras, category, flags, channel ID on API 26+, optional shortcut/conversation metadata, style, icons/previews, actions and other publisher-selected fields. `RankingMap` can expose ranking/channel/conversation information on supported versions. [AND1][AND2][AND3][AND5]

**Not exposed:** Messaging databases; full chat history; source encryption keys; reliable sent/read/delivery states; reliable account identity; all group participants; uncaptured message edits/deletions; original attachments absent a valid readable grant; private app files; protected View Once payloads. E2EE does not prevent an endpoint app from deliberately publishing plaintext notification previews, but it provides no entitlement to private app state. [AND1][AND4][AND14]

**Authorization:** Declare a service guarded by `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` and the `android.service.notification.NotificationListenerService` intent action. This is the service's binding protection, **not a dangerous runtime permission requested with a popup**. The user enables the listener in system notification-access settings. Check enabled status on return and actual connection separately. Sakshi's `POST_NOTIFICATIONS` controls Sakshi's own outgoing notifications, not permission to read other apps' notifications. The source app's posting permission/settings determine whether ordinary notifications exist at all. [AND1][AV1]

**Runtime/background:** System binds the service. Wait for `onListenerConnected()` before querying; `requestRebind()` is the documented reconnection operation. After disconnect, do not assume events are received. On API 24+, callbacks run on the main thread: extract a bounded snapshot and hand off quickly; no OCR/STT/LLM in callbacks. On reconnect, `getActiveNotifications()` gives outstanding notifications, not a journal of missed posts. Removal payloads can be “light” and omit heavy fields. Persist required exposed fields at posting time. This path is not an ordinary permanently running polling service and does not inherently require an always-on FGS, but process, source delivery and OS/OEM behavior still affect coverage. [AND1]

**Version differences:** NLS API 18; notification instance key API 20; MessagingStyle/RemoteInput API 24; channels API 26; Person API 28; conversation features API 30; listener filter metadata API 31; source `POST_NOTIFICATIONS` API 33; OTP redaction for untrusted listeners Android 15. Low-RAM devices on Android 10/Q and below cannot obtain/bind notification listeners. Listeners in a work profile are ignored, and policy can block work-origin notifications. Locked Android 15 private space stops its apps, including notifications. [AND1][AND4][AND5][AV1][AV3]

**OEM risks:** Source push delivery may be delayed or absent under battery/app-sleep policies; Sakshi can independently be killed/disconnected. OEM notification settings, cloned apps and profiles alter visibility. Samsung sleeping-app behavior is documented by Signal; Huawei/Xiaomi/Oppo-style app-launch/background controls are listed in Signal troubleshooting. These sources demonstrate source-app risk, not a measured Sakshi listener failure rate. [APP11][APP12]

**Policy/privacy:** Prominent in-app explanation before access, affirmative opt-in, clear source-app selection, pause/revoke controls, retention settings, no advertising or remote AI ingestion of evidence. Android may deliver more apps' notifications than Sakshi wants; discard unselected packages **before** content parsing/persistence. NLS cannot be represented as an OS-granted per-chat permission. Play Protect can block internet-sideloaded apps using sensitive permissions in select markets. Do not instruct users to disable protections; use legitimate testing/distribution and appeals. [PLAY1][PLAY3][PLAY5]

**Reliability / suitability:** M-observation, L-content; suitable as a best-effort complement to imports, never as a complete safety monitor. Silent notifications can still be useful; do not blindly copy documentation filter examples excluding `silent` or `ongoing`. Respect the user's listener filter choices and distinguish notification channel silence from no notification. DND suppressing alerts is not necessarily suppressing NLS events. Validate source app settings instead of inferring posting from sound.

#### M1.1 Notification extraction contract

| Field/surface | What can be recorded | Required interpretation / limitation |
|---|---|---|
| `sbn.packageName`, `user`, `id`, `tag`, `key` | Android-reported source package/profile and notification record | Notification key is not a chat message ID. Package identifies the poster, not a verified human sender. Profiles/accounts must remain separated. |
| `sbn.postTime` | Platform notification posting time in wall-clock milliseconds | May differ from `Notification.when`; not message send time; delivery can be delayed and notification records updated/reused. |
| `Notification.when` | Publisher-supplied display time | May be absent, arbitrary, conversation-level or regenerated. Preserve as claimed time. |
| `EXTRA_MESSAGES` | Included MessagingStyle messages: text, timestamp, Person/sender, optional MIME/URI | Multiple messages can arrive in one notification; repeated older messages can be republished. Null sender may mean current user, not anonymous attacker. |
| `EXTRA_HISTORIC_MESSAGES` | Optional publisher-provided contextual messages | A small contextual list, **not database history**; keep historical/context origin separate from new observations. |
| `EXTRA_TITLE`, `EXTRA_TEXT`, `EXTRA_BIG_TEXT`, `EXTRA_TEXT_LINES`, subtext/summary | Renderable labels and excerpt strings supplied by the app | Titles may be group names, counts or account labels; body can contain sender prefixes, reactions or generic “new message.” No universal sender parser. |
| Conversation title/group boolean/Person list/shortcut | Optional conversation context and identity hints | Group title is not sender. Person URI/key need not be phone/handle, globally unique or authenticated. Shortcut is local/app-scoped. |
| `groupKey`, `FLAG_GROUP_SUMMARY`, sort metadata | Notification display group and summary/child status | Android grouping is not messaging group membership. A summary can span multiple conversations/accounts. |
| Icons / BigPicture extras | Avatar, illustration, preview bitmap or optional picture icon | Do not call an avatar an attachment. Preview pixels may be resized/reencoded; preserve as a derivative representation. |
| Message `dataMimeType` / `dataUri` | Explicit MIME hint and URI if published | URI does not guarantee original data, permission or continued availability. Only open via normal authorized resolver access; treat denial as unavailable. |
| `actions` / `RemoteInput` | Reply/action availability and descriptors | An action is executable authority, not evidence text. Do not invoke in the acquisition pipeline. |
| `EXTRA_REMOTE_INPUT_HISTORY` | Optional recently entered inline replies echoed by publisher | Not a full outgoing-message log; label outgoing/local context separately. |
| Removal reason / ranking changes | Notification lifecycle observations | App cancel/user dismiss/read elsewhere/replacement/timeout can look similar. No chat deletion or escalation conclusion. |

Source contracts: [AND1][AND2][AND3][AND4][AND5].

#### M1.2 Group chats, grouping, rich notifications and inline replies

- Parse structured MessagingStyle first; retain the enclosing notification snapshot and exact field path for every extracted span. Fall back to generic extras without forcing sender/chat identity.
- A single posted/update callback can represent several messages, a summary, a reaction, a call or a state change. A burst of ten messages may produce fewer callbacks; repeated posts may contain the same text. Never equate callbacks with message counts.
- Keep group sender, group title, source account, notification grouping and conversation shortcut distinct. Treat localized `name: text` splitting as a versioned heuristic. A colon in content or sender name is not a safe delimiter.
- Conversation notifications and bubbles are UI integration features, not a cross-app conversation database. Android's dedicated conversation treatment depends on publisher style/shortcut participation. Ordinary non-conversation notifications may still contain messages. [AND5]
- A custom `RemoteViews` notification may lack equivalent structured extras. Do not inflate or introspect arbitrary foreign layouts as a reliable ingestion contract; record available standard fields or abstain.
- MediaStyle/playback metadata and media-session tokens may describe a player or call, not an incoming voice note. NLS does not expose an audio waveform or video file merely because a media notification exists.
- Inline reply uses `RemoteInput`/a publisher PendingIntent to act on the conversation. It can send a real message or change state. Sakshi acquisition must be read-only: do not send replies, mark read, dismiss, snooze, click or cancel notifications. A separately requested communication feature would require its own specification and consent. [AND3]
- Android lock-screen `VISIBILITY_PRIVATE`/public-version behavior is not universally the same as the app omitting text from the underlying notification. Do not assume lock-screen preview settings alone prove NLS can or cannot see a field. App-side omission and Android 15 OTP redaction are distinct mechanisms. [AV3]

### M2. Android Sharesheet / ACTION_SEND / ACTION_SEND_MULTIPLE

**Exposed:** Whatever the sending app deliberately supplies: `EXTRA_TEXT`, optional subject/HTML, stream URI(s), ClipData, MIME type and grant flags. This can be copied text, media, PDFs, screenshots or exports. Ordinary WhatsApp export is an example. Other apps may share only a link or may not expose an external share option for a particular message. [AND6][AND7][APP1]

**Not exposed:** Adjacent messages, original chat identifiers, verified sender, original timestamps, source-app database, inaccessible attachments, whole chat just because one item is shared. Share intent caller/referrer/provider authority is not cryptographic proof of origin. Users can share a screenshot through a gallery or edit a text export.

**Permissions:** No broad storage/media permission is needed for a readable per-URI grant. The sender must provide permission; inspect `FLAG_GRANT_READ_URI_PERMISSION`, ClipData and stream entries and handle access denial. Exported receiving activity must explicitly set `android:exported` for current targets. Treat all inbound intents as untrusted; a direct intent can be forged without a genuine Sharesheet gesture. Require preview/case selection and explicit save. [AND6][AND7][AND13]

**Runtime/background:** Handle both initial intent and `onNewIntent`; deduplicate delivery without merging distinct artifacts. Grants can be transient. After Save, copy selected content immediately into encrypted app-owned storage while access is valid; hashing/copying must be streamed and bounded off the main thread. Do not enqueue a transient URI for much later processing and assume it survives. Heavy analysis happens on the owned copy.

**Versions/OEM:** Sharesheet support predates modern Android; typed Parcelable handling must follow SDK-level APIs; Android 12+ exported-component rules matter. OEM sharesheets/MIME choices differ; chat exports may be multiple files or an archive. Provider streams may fail, report unknown sizes, transcode or require a network download.

**Policy/privacy:** Explain local retention, preview selected material, do not silently forward to cloud or another app. Avoid advertising/analytics SDK access. Explicit sharing is preferred but still does not legalize prohibited material/unauthorized account data.

**Reliability / suitability:** H-artifact after readable copy; source completeness varies. Primary Sakshi ingestion route. Label imports as `user_shared`, with origin `claimed` unless independently supported. Copy is the original **received artifact**, not necessarily the sender's original camera/audio file.

### M3. Storage Access Framework (SAF)

**Exposed:** User-selected documents from a participating `DocumentsProvider`: local files, Downloads, removable storage or cloud providers. Metadata can include display name, size, MIME and provider modification time, if supplied. `ACTION_OPEN_DOCUMENT` enables persistent access when the result permits it; `ACTION_GET_CONTENT` is generally an import/transient route. `ACTION_OPEN_DOCUMENT_TREE` grants a selected hierarchy, not an entire device. [AND8]

**Not exposed:** Files a provider does not offer; messenger private directories/databases; Android 11+ restricted `Android/data` and `Android/obb`; deleted files; protected payloads. Selecting a folder is not permission to defeat underlying app restrictions. A `DocumentsProvider` is an intentional sharing surface, not a database extractor. [AND8][AND9]

**Permissions:** System picker selection/URI grant, normally no `READ_EXTERNAL_STORAGE` or `READ_MEDIA_*`. Call `takePersistableUriPermission()` only when a persistable grant is actually returned and only for permitted flags. Persistable access can be revoked, and files can move/disappear. Do not request tree-wide access when per-file selection suffices.

**Runtime/background:** User must interact with picker. A grant can enable later reads but does not guarantee offline bytes. A cloud provider may fetch remotely; disclose that Sakshi is not uploading evidence but import can involve the selected provider/network. Use `EXTRA_LOCAL_ONLY` where supported and still handle unavailable content. Stream into private storage; re-openability and seekability are not guaranteed.

**Version/OEM:** SAF API 19; tree selection API 21. Android 11/current-target restrictions prevent root internal storage/reliable SD roots/Download tree grants, and selection under Android/data/obb. Individual ordinary Download files remain selectable. OEM pickers/providers vary, including filtering and missing cloud availability. [AND8][AND9]

**Policy/privacy:** Minimal scope, selected-file confirmation, no background directory crawl. A provider can expose more files under a tree than the user intended to add as evidence; use explicit per-item review.

**Reliability / suitability:** H-artifact for successful local copies, lower for remote/revoked inputs. Preferred for archives, audio, PDF and selected ordinary files.

### M4. Photo Picker / MediaStore / scoped shared storage

**Exposed:** Photo Picker grants selected images/videos that actually exist in the media library; it is not an audio picker. MediaStore can enumerate permitted shared images/video/audio and their metadata. An app-saved ordinary attachment in shared storage may be available; a private cache or unsaved streaming media is not. [AND10][AND11]

**Not exposed:** Chat text or context; verified sender; original message time; private databases/cache; deleted media; protected View Once originals. EXIF/filename/library timestamps are not message timestamps. An image in `Android/media` may be shared storage, but that does not make it proof of a particular chat/source. [AND11][AND12]

**Permissions:** Prefer Photo Picker without broad permissions. Broad shared-media access varies by version: older `READ_EXTERNAL_STORAGE`, Android 13 `READ_MEDIA_IMAGES`/`READ_MEDIA_VIDEO`/`READ_MEDIA_AUDIO`, Android 14 selected visual access when applicable. None grants another app's private files. SAF/share audio instead of broad audio inventory. [AND10][AND11][AV2]

**Runtime/background:** Explicit picker selection or permitted MediaStore query. A ContentObserver indicates a shared collection change, not a new message or its sender. Do not turn it into filesystem-based messaging surveillance. Cloud media can need downloads; grants/libraries change; copy selected bytes while authorized.

**Version/OEM:** Android 10 introduced scoped storage; Android 11 enforces it for targets 30+. Photo Picker availability depends on Android release, system modules/backport and device; AndroidX contracts can fall back to ACTION_OPEN_DOCUMENT. Do not assume API 33-only support. Selected visual access on Android 14 makes broad inventory assumptions unsafe. [AND9][AND10][AV2]

**Policy/privacy:** Play restricts broad photo/video and all-files permissions where pickers suffice. `MANAGE_EXTERNAL_STORAGE` is not a messenger permission and still excludes other apps' external app-specific directories. No all-files permission in Sakshi MVP. [AND12][PLAY1]

**Reliability / suitability:** H-artifact for selected readable material; low chat attribution. Photo Picker recommended; broad MediaStore monitoring not appropriate for baseline.

### M5. ContentProvider / FileProvider / cross-app IPC

**Exposed:** Only a publisher's intentionally exported/authorized data or granted URI. `ContentResolver` enforces provider/grant permissions. A FileProvider grant usually authorizes a specific file, not the application's database or directory inventory. [AND7][AND13]

**Not exposed:** Arbitrary data merely because a provider authority exists, a URI can be guessed, the package is installed or a component is exported. Signature-protected IPC is not available to arbitrary third-party apps. Package visibility/`QUERY_ALL_PACKAGES` grants discovery, not content. [AND13][AND14]

**Permissions/runtime:** Provider-specific permission, signature/role access or a valid URI grant. Query/open only documented/actually granted resources; handle SecurityException, revocation, process death and missing providers. Never probe undocumented private authorities/paths as an extraction strategy.

**Versions/OEM/background:** IPC applies throughout Android, with stricter component defaults and package visibility on newer targets. Provider lifetime, URI-grant duration and OEM file-provider implementations vary; no background completeness contract.

**Policy/privacy:** A misconfigured exported provider is not a license to harvest private messages. Such a finding belongs in responsible defensive disclosure, not acquisition logic.

**Reliability / suitability:** High for a documented/granted object; no general messenger API. Suitable only as the plumbing for shares/imports or an explicitly documented provider integration.

### M6. AccessibilityService

**Exposed:** Selected accessibility events and, if requested/configured, node text/content descriptions and visible window hierarchy that the app makes accessible. May expose ordinary chat bubbles, names or labels on the current UI; a displayed picture usually provides semantics/pixels, not the original file. Screenshot APIs exist on newer releases under their own capabilities and restrictions. [AND15][AV4]

**Not exposed:** Private database; reliably offscreen/virtualized historical messages; full attachments/audio; password/locked/hidden/sensitive nodes; reliable message IDs/timestamps; protected screens. Node trees can be absent, incomplete, reordered or purely descriptive. `FLAG_SECURE` is a screen-capture control, not a blanket accessibility-node policy; conversely accessible labels do not authorize retaining protected ephemeral media. [AND15][AND16][AV4]

**Permissions:** User enables service in Accessibility settings; `BIND_ACCESSIBILITY_SERVICE` protects binding; retrieval capabilities configured in metadata. Not a normal dangerous-permission request. Some installation paths encounter restricted settings/Play Protect controls. [AND15][PLAY5][PLAY6]

**Runtime/background:** System-bound service can receive configured UI events while other apps are foreground. It does not mean arbitrary background chat history is accessible. Broad event collection is invasive; restrict packages/types and only process during a clearly initiated session if ever adopted. Do not auto-scroll, open private chats, enter credentials or automate sending. UI events are not a stable export API.

**Versions:** API 14-era service capabilities expanded over time; screenshot capture API 30 and window capture on newer releases must be gated. Sensitive accessibility metadata exists in API 34, and Android 16 vendor guidance explains exclusion of non-accessibility-tool services from sensitive views, including interactions. Some touch-obscuration-protected views are treated as sensitive. Do not claim older accessibility scrapers remain effective on current Android. [AV4]

**OEM risks:** Different layouts/fonts/WebViews/Compose accessibility semantics, app versions, process restrictions and service restarts break parsers; no six-app reliability certification established.

**Play policy:** Non-disability tools can use Accessibility subject to declaration, approval, dedicated prominent disclosure/consent and other policy limits; it is not categorically prohibited. Sakshi is not automatically eligible for `isAccessibilityTool=true`. Current guidance forbids autonomous initiation/planning/execution of actions by non-tool automation; deterministic rule-based automation is distinguished, but remains subject to policies. Do not falsely declare a disability tool to gain sensitive-node access. [PLAY2][PLAY5][AV4]

**Privacy / suitability:** Very high access breadth and coercion risk. Not appropriate for Sakshi's baseline evidence capture or a substitute for NLS/SMS permissions. Consider only a separately reviewed, bounded assistive/user-driven feature with demonstrated necessity; no silent broad app scraping.

### M7. MediaProjection / user-driven screen capture

**Exposed:** Capturable rendered pixels of the selected app/window/display during an approved session. Screenshots/recordings can contain ordinary visible chat text, displayed names and UI dates; OCR produces derivatives. It does not expose machine-readable chat records or original attachments. [AND17]

**Not exposed:** Offscreen messages without user navigation; locked/protected content; FLAG_SECURE windows; app-sensitive fields; original media resolution/encoding; arbitrary app files. Capture can produce black/blank/redacted regions. A picture of a video is not the video original; recording playback is a rendered recording, not the incoming file. [AND16][AND17][AV3]

**Permissions:** OS screen-capture consent; applicable `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PROJECTION` declarations/type for targets 34+. Start only from a user-initiated flow; follow foreground-service start restrictions. Screen pixels do not need camera permission. Internal audio needs separate RECORD_AUDIO and playback-capture eligibility. [AND17][AND18]

**Runtime/background:** Approved projection runs during a visible FGS session; release display/surface/audio resources on `onStop()`, session interruption and errors. No silent restart, cached-token reuse or boot-start capture. Android 14 enforces single-use session/token behavior for current targets; register required callbacks. Android 15-target FGS rules also restrict starting mediaProjection from BOOT_COMPLETED. [AND17][AV3]

**Versions:** MediaProjection API 21; audio playback capture API 29; Android 14 app/window selection and session hardening; app sharing rollout includes Android 14 QPR2. Android 15 hides sensitive screenshare content; 15 QPR1 adds prominent status chip and stops projection at screen lock. Android 17 audio/background changes must be rechecked before audio capture ships. [AND17][AND18][AV3][AV5]

**Audio boundary:** Playback capture requires same profile, RECORD_AUDIO, approved projection, allowed audio usage (MEDIA/GAME/UNKNOWN) and the source's capture policy. VOICE_COMMUNICATION is not generally eligible. Voice note playback may or may not use eligible settings. Do not promise voice-note/call recording across apps. Microphone capture is a separate consent/recording-law workflow and does not recover the file. [AND18]

**OEM/policy/privacy:** Display geometry/rotation, codecs, app support and OEM security behavior vary. Full-display capture can expose unrelated notifications/passwords; prefer single-app selection, short sessions, local preview and explicit Save. Any Play foreground-service declarations must match real functionality. User safety may be affected by screenshot/capture notices sent by the source app; never promise undetectability.

**Reliability / suitability:** Reasonable for permitted visible pixels, poor original-media fidelity/semantic completeness. Optional later; manual system screenshots/recordings followed by B-class import are simpler MVP routes. Never route blocked View Once through projection/accessibility/OCR as a fallback.

### M8. App sandbox, app-private storage and backups

**Exposed:** Sakshi's own files/databases and data another app deliberately shares. Android unique UID/process isolation and SELinux protect other apps. Native code does not escape the sandbox. Internal credential-encrypted app storage is unavailable before first unlock after boot. [AND14]

**Not exposed:** Other messengers' internal databases, keys, preferences, private cache or system backups. Notification, accessibility, storage, contacts or all-files permissions do not grant these. Android backup restores app data under its own package/signing identity; it is not a cross-app history query. [AND12][AND14][AND19]

**Permissions/runtime/background:** No storage permission to use own internal storage. Preserve selected incoming bytes there, encrypt at rest, isolate keys through Android Keystore and secure indexes/caches. Before first unlock or if the vault key is unavailable, fail closed: no unencrypted device-protected evidence spool. Record coverage unavailability without sensitive payload. User-authentication-bound keys may prevent locked-background capture; choose/document the lock/capture tradeoff and key-access design rather than pretending both guarantees are automatic.

**Version/OEM:** Scoped storage further restricts external app data; Android 11 denies other external app-specific directories. Android Auto Backup defaults can upload app files/databases/preferences. Android 12+ `allowBackup=false` alone may not disable OEM device-to-device transfer; configure both legacy rules and `dataExtractionRules` exclusions for cloud/D2D. Documentation also describes cross-platform transfers from Android 16 QPR2. Test the actual target device and backup transport. [AND9][AND19]

**Backup policy:** Exclude evidence, derived text, metadata, indexes and keys from automatic backup/transfer by default. Use no-backup storage plus explicit exclusion rules where applicable; verify all domains. Do not promise recovery after uninstall/key loss. A separately approved encrypted user export/recovery workflow is not silent backup. Keystore-bound keys are not portable merely because encrypted blobs migrate.

**Source app backups:** WhatsApp backup, Signal encrypted backups and Messenger secure storage are restore mechanisms, not Sakshi-readable chat APIs. Do not obtain credentials, recovery keys or private backup files to turn them into extraction routes. A **publicly supported user-produced export**, if actually available, is a different B-class artifact. [APP7][APP10][APP13]

**Reliability / suitability:** Suitable and required for owned evidence, not an acquisition route into other apps. Root/device compromise, uninstall, key loss and OEM migration remain risks; hashes prove subsequent byte integrity, not source truth, legal admissibility or trusted capture time.

### M9. Direct SMS / MMS / Telephony provider

**Exposed with authorization:** SMS_RECEIVED_ACTION can supply incoming PDUs/body/originating address/service-center timestamp; Telephony.Sms can expose stored body, ADDRESS, DATE, DATE_SENT, thread/type and related fields. MMS uses distinct provider parts and permission/broadcast handling. Only the default SMS app writes the SMS provider and receives SMS_DELIVER_ACTION; permitted non-default apps can read/receive under the applicable rules. [AND20][AND21]

**Not exposed:** RCS or Google Messages app-private data by virtue of READ_SMS; WhatsApp/Signal/Telegram chats; attachments in plain SMS; deleted rows absent a prior artifact; confirmed human sender identity. SMS originating address can be spoofed and timestamps differ by network/device.

**Permissions:** READ_SMS and/or RECEIVE_SMS, plus MMS-related permissions only for an approved feature. They are dangerous/hard-restricted in current Android permission contracts; installer/role eligibility and runtime user consent both matter. A permission dialog alone is insufficient. Default-handler role adoption must be genuine, not a pretext. [AND22][PLAY1][PLAY4]

**Runtime/background:** Incoming broadcasts are a supported event path, not a requirement for always-on polling. Assemble multipart SMS carefully; distinguish PDU service-center time, receiver time, provider DATE and DATE_SENT; preserve subscription context only if available/needed. Queue bounded work, no heavy analysis in receiver. Provider history is only the retained store, not all lifetime messages. Revocation/role changes immediately stop further reads.

**Versions/OEM:** Default SMS distinction since Android 4.4; runtime permission model Android 6; hard-restricted permissions Android 10-era; telephony feature/multi-SIM/client differences. Existing Retriever-hash OTP messages have a three-hour delay for most readers. Android 17 extends detailed protections to WebOTP for all targets and ordinary OTP-bearing SMS for target 37+, with documented role/privileged exceptions. Do not downgrade target to evade protections. [AND20][AV5][AV6]

**Play policy:** Default handler or approved documented exception is generally necessary. “Physical safety/emergency alerts” allows SEND_SMS in the listed exception, **not unrestricted READ_SMS evidence mining**. Notification/accessibility/other APIs must not be used to derive SMS-permission data as a workaround. An NLS SMS prototype may technically work but is not thereby policy-eligible. Keep automatic SMS capture disabled for Play MVP until eligibility is established. [PLAY1][PLAY4]

**Privacy / reliability / suitability:** Broad inbox access includes OTPs/financial/health data; unacceptable as a casual permission request. Authorized SMS text is relatively structured, but source authenticity/loss remain uncertain. Prefer explicit user-selected SMS screenshots/copied text/exports; these are not an automatic inbox substitute, and their precise release policy treatment still needs review. MMS is not SMS; RCS is a separate unsupported/private-client surface unless a documented integration exists.

## 5. Version and OEM engineering gates

| Android band | Acquisition-relevant changes | Mandatory engineering response |
|---|---|---|
| 4.3 / API 18 | NLS introduced | Historical API availability only; not a recommended production minimum. |
| 4.4-5 / API 19-22 | SAF; default SMS distinction; tree selection/projection API 21 | Treat picker grants as bounded access; do not use legacy assumptions to access private apps. |
| 6-8 / API 23-27 | Runtime dangerous permissions; Auto Backup; API 24 MessagingStyle/RemoteInput; API 26 channels and background restrictions | Runtime consent where required; no model work in main-thread callbacks; explicit backup exclusions. |
| 9-10 / API 28-29 | Person; stronger sandbox; scoped storage begins; audio playback capture; restricted SMS permissions | Sender hints not identities; audio eligibility checks; reject filesystem-wide acquisition design. |
| 11 / API 30 | Scoped storage enforcement for targets 30+; SAF restrictions; conversation shortcuts; screenshot accessibility API | Imports cannot traverse Android/data; conversation UI is not message storage. |
| 12 / API 31-32 | FGS background-start restrictions; explicit exported components; listener filter metadata; backup/D2D rule changes | Safe receiving activities; avoid excluding silent evidence; test migration exclusions. |
| 13 / API 33 | Source POST_NOTIFICATIONS; granular media permissions; restricted settings depending on installer | No source notification means no NLS capture; no permission coercion; prefer selected imports. |
| 14 / API 34, QPRs | Selected visual access; projection session/token/FGS hardening; app sharing; sensitive accessibility APIs | Handle partial grants; single-session consent; prefer single-app capture; no protected-node assumptions. |
| 15 / API 35, QPR1 | OTP notification redaction; sensitive screenshare protections; private space; chip/lock-stop; boot FGS restrictions | Redacted stays unavailable; no invented content; stop/release projection; record profile/coverage limits. |
| 16 / API 36, QPRs | Sensitive-node security guidance; tighter job quota; documented backup cross-platform additions | Do not falsely declare accessibility tool; deferred analysis can be delayed even with FGS; re-test backup exclusions. |
| 17 / API 37, QPRs | Detailed SMS OTP three-hour filtering changes; summary's unexplained restricted-message entry | Separate all-target versus target-specific OTP rules; verify restricted-store behavior on actual builds. No unsupported universal E2EE claim. |

Sources: [AND1][AND4][AND8][AND9][AND17][AND19][AV1][AV2][AV3][AV4][AV5][AV6][AV7][AV8].

**Recommended engineering baseline:** Choose and document a minimum SDK based on actual target phones; API 26+ is a reasonable design starting point, not a validated market decision. Compile/target a current release required by distribution policy; use version-gated API access. Test current Android 15-17 as available, plus representative older phones, rather than extrapolating from an emulator.

**OEM test families:** Pixel/reference Android; Samsung One UI; Xiaomi/Redmi; Oppo/Realme/OnePlus; Vivo; Huawei where relevant. This is a risk-based test list, not a claim all OEMs are broken. Record model/build/patch, app version, user/profile, source notifications/channel settings, Sakshi access status, battery mode and measured event delivery. Both source-app delays and collector outages matter. Imports depend more on provider behavior than background delivery. [APP11][APP12]

## 6. App-specific acquisition contracts

### 6.1 WhatsApp (personal app and Business app must be tested separately)

- **Notifications:** NLS reads published notification data, not WhatsApp protocol messages. Exact sender/text/group/MIME fields are conditional; there is no cited stable third-party notification schema. OS/app notification settings and foreground behavior affect posting. Do not claim every muted group is either always captured or always invisible. [AND1][APP3]
- **Ordinary imports:** Official chat export creates a text artifact and can include recent media as attachments through the Sharesheet. Preserve text/archive/attachments and associate by documented parser output with uncertainty. Locale-dependent dates, display names, multiline text and omitted media require tests. Historical 10,000/40,000-message limits circulate in old sources; do not hardcode these as current completeness guarantees. Current primary indexed instructions establish recent-media selection, not unlimited history. [APP1]
- **Advanced Chat Privacy:** Can prevent export and automatic gallery media saving. The official help warns older app versions can differ. Do not recommend downgrading, alternate clients or another route to defeat that restriction. [APP2]
- **View Once photos/video/voice:** No legitimate Android API for extracting protected payload established. Screenshot blocking is documented by Meta; current help covers View Once media/voice. Whether a particular notification identifies “View Once” or merely “Photo/New message” is unknown until device testing. Preserve only exposed labels or a clearly labeled user description. Do not open the content automatically, infer image contents, or call generic photo labels proof of View Once. [APP4][APP5]
- **Disappearing ordinary text:** An excerpt might be exposed before expiry, but notification access exposes no reliable TTL/deletion state. A previously collected excerpt is a notification observation, not recovery. Retention must be prominently explained and user-controlled.
- **Business API:** WhatsApp Business Platform is a business integration; it is not permission to retrieve arbitrary personal-account chats stored by the consumer Android app. Exclude from offline MVP. [API1]

### 6.2 Instagram

- **Notifications:** DM text/name may be exposed or replaced by a generic event; requests, group messages, reactions, shared posts/reels and account activity may not represent a new text message. Mute/push preferences alter observation. Exact extras schema and attachment URI behavior remain unverified. [AND1][APP6]
- **Imports/history:** Accounts Center offers selected information/date range/format/media quality exports to device. The existence of an export does not establish that every DM, unsent item or ephemeral attachment is present. Obtain a user-produced fixture, detect actual message structure, report selected scope and missing/expired attachments. Public post/link sharing is not necessarily sharing a private DM image. [APP7]
- **Ephemeral media:** Meta announced prevention of screenshots/recording of View Once/Allow Replay media for Instagram and Messenger, including web restrictions for Instagram. This contradicts older articles promising “screenshots work but notify.” Treat current protected payloads as unavailable and validate source-client behavior without bypasses. [APP8]
- **Official messaging API:** Limited to eligible professional/business/creator inbox contexts and authorization; not universal personal DM access, not an Android sandbox API. [API2]

### 6.3 Telegram

- **Notifications:** Cloud groups/channels/topics/accounts and secret chats are distinct contexts; published text/sender/media hints depend on privacy/settings/client. Do not infer all clients or secret chats publish previews. Notification grouping is not a chat/topic ID contract. [AND1][APP9]
- **Ordinary history/import:** Telegram documents JSON/HTML export with media in Telegram Desktop. Users can transfer the export to Android and select it in SAF. This is an Android import of a Desktop-produced artifact, not proof of export in every Android mobile UI. [APP9]
- **Secret chats:** Device-specific and outside Telegram cloud; a new logged-in Sakshi client cannot read an existing secret chat established by the official client. No supported acquisition of that private local history. [APP14]
- **Content protection:** Current API documentation covers groups/channels, bot messages and private-chat protection. Screenshots, copying, forwarding and downloads must respect protection. Do not assume all cloud chats are exportable. [APP15]
- **Real API exception:** Telegram API/TDLib can implement a separately user-authorized client and access the account's permitted cloud history/updates/media. This is officially supported **Telegram service access**, not a cross-app Android read permission. Requires login/session security, network, terms/license review, rate limits, scoped user selection and no protected-content override. Bots see only authorized interactions/membership/privacy-mode scope, not an arbitrary user's inbox/history. Defer from MVP. [API3][API4]

### 6.4 Signal

- **Notifications:** Official settings offer Name/Content/Actions, Name only or No Name/Content. Therefore text/sender can be intentionally absent. Android and Desktop settings differ: do not apply documented macOS disappearing-message “New Message” behavior to Android universally. [APP10]
- **Media imports:** Official Android help documents viewing/saving ordinary shared media/files/audio outside Signal. A saved allowed file can then be selected in SAF/Photo Picker; this is not automatic attachment extraction. [APP16]
- **Screen capture:** Screen Security can prevent screenshots on the user's Android device. Do not use accessibility as a fallback to defeat that choice. [APP17]
- **View Once:** Photos/video are removed from conversation history after viewing or expiry. Signal Secure Backups exclude View Once and messages scheduled to disappear within 24 hours. An encrypted backup is not an Android cross-app read surface. [APP18][APP19]
- **History/export uncertainty:** Official-source plaintext-export underpinnings exist, but stable public Android availability was not established here. Support a user-produced public export only after UI/release and schema validation. Do not claim an internal/source feature is generally released, and do not decrypt private databases/backups to compensate. [APP13]
- **SMS:** Signal is not the SMS inbox acquisition contract; test system SMS through Telephony/client imports separately.

### 6.5 Facebook Messenger

- **Notifications:** Official settings can disable previews and per-chat/group alerts. Read only exposed data; multi-account/group/call/reaction/activity/bubble notifications are not a complete message feed. [APP20]
- **History:** Meta help documents download of end-to-end encrypted messages/attachments from message storage on a computer. User transfers the result and imports via SAF. Availability depends on secure storage/history/export scope; do not imply Sakshi can read secure storage directly or that all mobile menus have this export. [APP21]
- **Ephemeral images/video:** Same Meta prevention announcement as Instagram; unprotected UI capture and ordinary file sharing do not override protected media. [APP8]
- **Business platform:** Page/business messaging permissions do not provide arbitrary personal Messenger conversations. [API2]

### 6.6 SMS, MMS and Google/Samsung Messages

- NLS may technically observe a source client's notifications but is incomplete and **Play-policy gated for automatic SMS derivation**. Do not present this as an approved workaround. Source previews/groups/multi-SIM affect semantics. [AND1][PLAY1]
- Direct authorized SMS provider/receiver can supply body/address/time and retained history. An address is not authenticated identity; outgoing/history can differ from received broadcasts. No uncaptured deleted recovery. [AND20][AND21]
- Plain SMS has no image/audio/video attachment. MMS parts need separate implementation/permissions. RCS and an OEM client's private DB are not automatically included in Telephony.Sms; mark RCS as unsupported until a documented authorized surface exists.
- Supported user-selected screenshots/copied text/exports are the baseline, with the exact client export availability checked on device. Do not collect OTPs or whole SMS inventories under a generic evidence promise.

### 6.7 Similar messaging apps

Use the same contract, not a universal scraping adapter: detect posted notification only; parse standard fields conservatively; import only allowed selected artifacts; honor private databases, DRM/protected-content flags and export restrictions. Slack/Teams/Discord/business tools may have separately authorized service APIs, retention/admin policies and work-profile constraints; do not silently equate those integrations with local Android access.

## 7. Source-backed capability matrix

### 7.1 Matrix rules and API/consent shorthand

This is **90 capability-app rows: 15 capabilities x 6 app families**. Each row contains Capability x App x Android API x Possible x User consent x Reliability x Restrictions. Classes use Section 2. App-specific rows are feasibility assessments derived from Android contracts plus documented source-app controls, not measured device interoperability.

- **N:** NLS/StatusBarNotification (API 18+); structured MessagingStyle from 24+ when published. Consent **N** = separate in-app opt-in/source selection plus Android notification-access grant.
- **I:** ACTION_SEND/MULTIPLE and SAF (API 19+ for SAF); Photo Picker for images/video where available. Consent **I** = deliberate selection/share plus Sakshi preview/Save.
- **P:** MediaProjection (API 21+) or user-supplied permitted system capture; consent **P** = per-session OS approval and local Save. Optional, not an original-file route.
- **S:** Telephony provider/receiver with approved permissions/roles. Consent **S** = policy eligibility, installer/role authorization, runtime grant and explicit scoped consent.
- **Retained:** Only an existing Sakshi artifact captured before deletion, or another legitimately user-selected copy. No API to recover uncaptured content.
- **Protected:** No View Once/protected payload capture. A notification label/user report is not that payload.
- **SMS gate:** No automatic SMS NLS/alternative-API collection in Play MVP absent established policy eligibility.

#### WhatsApp

| Capability | App | Android API | Possible? / class | User consent? | Reliability | Restrictions / sources |
|---|---|---|---|---|---|---|
| detect new message | WhatsApp | N | Conditional notification event A/C | N | M-observation | Only posted events; no one-event-one-message guarantee. [AND1][APP3] |
| obtain text | WhatsApp | N; I | Conditional excerpt A/C; selected export A/B | N or I | L-content; H-artifact | Preview/export may be absent or limited; privacy can block export. [AND3][APP1][APP2] |
| obtain sender | WhatsApp | N; I | Conditional display identity A/C; export claim B | N or I | L-content | Name/number/group prefix not verified identity. [AND4][APP1] |
| obtain timestamp | WhatsApp | N; I | Post time A; claimed message/export time C/B | N or I | H for recorded field; L for send-time meaning | Keep post/when/message/import clocks separate. [AND2][AND4][APP1] |
| detect image | WhatsApp | N; I | Conditional label/MIME A/C; selected image B | N or I | L-content | Photo label/preview does not establish original or View Once. [AND3][AND4][APP4] |
| obtain image itself | WhatsApp | I; N only with valid URI | Yes selected ordinary artifact A/B; notification preview C | I; N if scoped | H-artifact; L notification media | No original guarantee from preview; no protected payload. [AND4][APP2][APP5] |
| detect audio | WhatsApp | N; I | Conditional voice/audio label A/C; selected audio B | N or I | L-content | Voice-message event not waveform or transcript. [AND4][APP4] |
| obtain audio itself | WhatsApp | I | Selected ordinary audio A/B; automatic private file D | I | H-artifact if supplied | View Once voice unavailable; readable shared/exported file required. [AND7][APP1][APP4] |
| detect video | WhatsApp | N; I | Conditional label/MIME A/C; selected video B | N or I | L-content | Generic media label can be ambiguous. [AND4][APP4] |
| obtain video itself | WhatsApp | I | Selected ordinary video A/B; protected original D/E | I | H-artifact if supplied | No notification-based full video guarantee. [AND7][APP1][APP5] |
| obtain historical chat | WhatsApp | I | Supported user export A/B; live private history D | I | H-artifact; incomplete history | Privacy/export scope/current limits; not app DB. [APP1][APP2][AND14] |
| obtain deleted messages | WhatsApp | Retained; I | Prior copy only B/C; uncaptured recovery D | Original consent; I for later copy | None for recovery | Notification cancellation is not chat deletion. [AND1][AND14] |
| observe disappearing content | WhatsApp | N; I | Exposed pre-expiry excerpt/label C; protected payload D/E | Explicit ephemeral-retention opt-in; I | L-content | No expiry guarantee or View Once extraction. [APP4][APP5][AND1] |
| access notification metadata | WhatsApp | N | Yes for delivered records A | N | M-observation | Package/key/group/channel/time, not all message metadata. [AND1][AND2] |
| receive user-shared evidence | WhatsApp | I | Yes selected allowed artifact A/B | I | H-artifact | Export/attachments/screenshots only when allowed; not exhaustive. [APP1][APP2][AND6] |

#### Instagram

| Capability | App | Android API | Possible? / class | User consent? | Reliability | Restrictions / sources |
|---|---|---|---|---|---|---|
| detect new message | Instagram | N | Conditional notification event A/C | N | M-observation | DM requests/reactions/activity/mutes affect meaning. [AND1][APP6] |
| obtain text | Instagram | N; I | Conditional excerpt A/C; selected archive/text B | N or I | L-content; H-artifact | No stable consumer DM extras contract. [AND3][APP7] |
| obtain sender | Instagram | N; I | Conditional name/handle A/C; import claim B | N or I | L-content | Group/account/title can be ambiguous. [AND4][APP7] |
| obtain timestamp | Instagram | N; I | Post time A; claimed message/archive time C/B | N or I | H field; L send-time meaning | Archive selection and clock provenance required. [AND2][APP7] |
| detect image | Instagram | N; I | Conditional event label/MIME A/C; selected image B | N or I | L-content | “Sent a photo” is not original pixels. [AND4][APP8] |
| obtain image itself | Instagram | I | Selected allowed saved/exported image A/B | I | H-artifact if supplied | External sharing may supply link only; protected ephemeral payload D/E. [AND7][APP7][APP8] |
| detect audio | Instagram | N; I | Conditional label A/C; selected audio B | N or I | L-content | Exact notification type/schema unverified. [AND1][AND4] |
| obtain audio itself | Instagram | I | Conditional selected ordinary file A/B | I | H-artifact if supplied | App must provide/save/export it; no private cache read. [AND7][AND14][APP7] |
| detect video | Instagram | N; I | Conditional label A/C; selected video B | N or I | L-content | Reel/link event not necessarily attached video. [AND4][APP7] |
| obtain video itself | Instagram | I | Conditional selected ordinary video A/B | I | H-artifact if supplied | View Once/Allow Replay capture blocked/protected; no bypass. [APP8][AND7] |
| obtain historical chat | Instagram | I | Selected account archive B; private live history D | I | Conditional archive coverage | Verify messages actually present/date range/schema; no deleted guarantee. [APP7][AND14] |
| obtain deleted messages | Instagram | Retained; I | Prior copy only B/C; unsent/uncaptured recovery D | Original consent; I | None for recovery | Archive existence does not imply deleted/unsent retention. [APP7][AND14] |
| observe disappearing content | Instagram | N | Exposed event label/excerpt C; protected payload D/E | Explicit scope/retention opt-in | L-content | No screenshot/recording fallback for protected media. [APP8][AND16] |
| access notification metadata | Instagram | N | Yes delivered records A | N | M-observation | Not account/chat-message IDs unless actually supplied. [AND1][AND2] |
| receive user-shared evidence | Instagram | I | Yes supplied allowed artifact A/B | I | H-artifact | Screenshot/text/archive; private DM external sharing not guaranteed. [AND6][APP7][APP8] |

#### Telegram

| Capability | App | Android API | Possible? / class | User consent? | Reliability | Restrictions / sources |
|---|---|---|---|---|---|---|
| detect new message | Telegram | N | Conditional notification event A/C | N | M-observation | Accounts/topics/groups/channels/secret previews vary. [AND1][APP9] |
| obtain text | Telegram | N; I | Conditional excerpt A/C; selected cloud export B | N or I | L-content; H-artifact | No protected-chat copying; separate service API not assumed. [APP9][APP15][AND4] |
| obtain sender | Telegram | N; I | Conditional sender/channel author A/C; import claim B | N or I | L-content | Channel/group title is not author; usernames need not exist. [AND4][APP9] |
| obtain timestamp | Telegram | N; I | Post time A; message/export time C/B | N or I | H field; L semantic mapping | Record post and publisher/export time separately. [AND2][AND4][APP9] |
| detect image | Telegram | N; I | Conditional MIME/label A/C; selected image B | N or I | L-content | Ordinary versus protected/secret content not inferred from generic label. [AND4][APP15] |
| obtain image itself | Telegram | I; authorized N URI | Selected ordinary file/preview A/B/C | I; N if scoped | H-artifact; L N URI | No protected download; no original promise for notification preview. [APP15][AND4] |
| detect audio | Telegram | N; I | Conditional voice/audio label A/C; selected audio B | N or I | L-content | Playback notification not incoming attachment. [AND3][AND4] |
| obtain audio itself | Telegram | I | Selected permitted saved/exported file A/B | I | H-artifact if supplied | Protection/secret-chat restrictions apply. [APP9][APP14][APP15] |
| detect video | Telegram | N; I | Conditional label/MIME A/C; selected video B | N or I | L-content | Round-video/document labels may differ by client. [AND4][APP9] |
| obtain video itself | Telegram | I | Selected permitted saved/exported file A/B | I | H-artifact if supplied | No protected-content bypass; not every clip saved/exported. [APP9][APP15] |
| obtain historical chat | Telegram | I | Desktop cloud export B; existing secret history D | I | H-artifact; scope limited | Transfer Desktop export to phone; authenticated API discussed separately. [APP9][APP14][API3] |
| obtain deleted messages | Telegram | Retained; I | Prior copy only B/C; uncaptured recovery D | Original consent; I | None for recovery | Cloud API not a deleted-message recovery service. [APP14][AND14] |
| observe disappearing content | Telegram | N | Exposed pre-expiry event/label C; protected payload D/E | Explicit scope/retention opt-in | L-content | Existing secret-chat private storage not readable. [APP14][APP15] |
| access notification metadata | Telegram | N | Yes delivered records A | N | M-observation | Preserve profile/account/topic uncertainty. [AND1][AND2] |
| receive user-shared evidence | Telegram | I | Yes selected permitted artifact A/B | I | H-artifact | Protected forwarding/copy/save restricted. [APP9][APP15][AND6] |

#### Signal

| Capability | App | Android API | Possible? / class | User consent? | Reliability | Restrictions / sources |
|---|---|---|---|---|---|---|
| detect new message | Signal | N | Conditional notification event A/C | N | M-observation | Generic or hidden content; source delivery may fail. [APP10][APP11][AND1] |
| obtain text | Signal | N; I | Conditional excerpt A/C; selected text B | N or I | L-content; H-artifact | Name-only/no-content settings intentionally omit text. [APP10][AND3] |
| obtain sender | Signal | N; I | Conditional displayed name A/C; import claim B | N or I | L-content | No Name/Content can hide sender; no identity guarantee. [APP10][AND4] |
| obtain timestamp | Signal | N; I | Post time A; publisher/UI time C/B | N or I | H field; L send-time meaning | Screen/UI timestamps and acquisition time differ. [AND2][AND4] |
| detect image | Signal | N; I | Conditional media label A/C; selected image B | N or I | L-content | View Once content not established by thumbnail/icon. [APP18][AND4] |
| obtain image itself | Signal | I; authorized N URI | Selected ordinary saved media A/B; preview C | I; N if scoped | H-artifact; L preview | Screen Security and View Once restrictions; private files D. [APP16][APP17][APP18] |
| detect audio | Signal | N; I | Conditional media label A/C; selected audio B | N or I | L-content | Exact extras app-version dependent. [AND4][APP10] |
| obtain audio itself | Signal | I | Selected ordinary saved audio A/B | I | H-artifact if supplied | Official save-media workflow; no database/backup extraction. [APP16][AND14] |
| detect video | Signal | N; I | Conditional label/MIME A/C; selected video B | N or I | L-content | Generic notification may disclose no modality. [APP10][AND4] |
| obtain video itself | Signal | I | Selected ordinary saved video A/B | I | H-artifact if supplied | View Once payload not supported. [APP16][APP18] |
| obtain historical chat | Signal | I | Selected public export if available B/C; stable availability unknown | I | Unconfirmed release/UI/schema | Source underpinnings do not prove shipped feature; private DB D. [APP13][AND14] |
| obtain deleted messages | Signal | Retained; I | Prior copy only B/C; uncaptured recovery D | Original consent; I | None for recovery | Encrypted backup is not a recovery API for Sakshi. [APP19][AND14] |
| observe disappearing content | Signal | N | Exposed pre-expiry event/excerpt C; protected payload D/E | Explicit scope/retention opt-in | L-content | No Android-specific blanket preview assertion; View Once absent from history. [APP10][APP18] |
| access notification metadata | Signal | N | Yes delivered records A | N | M-observation | Generic notification still not an exact message count. [AND1][AND2][APP10] |
| receive user-shared evidence | Signal | I | Yes selected ordinary saved/shared artifact A/B | I | H-artifact | Allowed save/import; screenshot protection respected. [APP16][APP17][AND6] |

#### Facebook Messenger

| Capability | App | Android API | Possible? / class | User consent? | Reliability | Restrictions / sources |
|---|---|---|---|---|---|---|
| detect new message | Facebook Messenger | N | Conditional notification event A/C | N | M-observation | Mute/groups/reactions/calls/activity can alter feed. [APP20][AND1] |
| obtain text | Facebook Messenger | N; I | Conditional excerpt A/C; selected archive/text B | N or I | L-content; H-artifact | Notification previews can be disabled. [APP20][APP21][AND3] |
| obtain sender | Facebook Messenger | N; I | Conditional display identity A/C; archive claim B | N or I | L-content | Group/title/account ambiguity and nonverified names. [AND4][APP20] |
| obtain timestamp | Facebook Messenger | N; I | Post time A; claimed message/archive time C/B | N or I | H field; L send-time meaning | Notification time not send time; retain import scope. [AND2][APP21] |
| detect image | Facebook Messenger | N; I | Conditional label/MIME A/C; selected image B | N or I | L-content | Event label not pixels; protected ephemeral payload unavailable. [APP8][AND4] |
| obtain image itself | Facebook Messenger | I | Selected ordinary saved/shared/archive artifact A/B | I | H-artifact if supplied | Archive only includes retained/exported attachments; protected media D/E. [APP21][APP8] |
| detect audio | Facebook Messenger | N; I | Conditional voice/audio label A/C; selected audio B | N or I | L-content | Calls/media controls do not prove incoming voice-note file. [AND3][APP20] |
| obtain audio itself | Facebook Messenger | I | Conditional selected ordinary file/archive A/B | I | H-artifact if supplied | No capture or private-file guarantee. [APP21][AND7][AND14] |
| detect video | Facebook Messenger | N; I | Conditional label/MIME A/C; selected video B | N or I | L-content | Generic attachment notifications may omit type. [AND4][APP20] |
| obtain video itself | Facebook Messenger | I | Selected ordinary shared/archive file A/B | I | H-artifact if supplied | View Once/Allow Replay protected capture not supported. [APP8][APP21] |
| obtain historical chat | Facebook Messenger | I | User computer-produced E2EE export B | I | Conditional storage/export coverage | Not direct secure-storage read or universal mobile export. [APP21] |
| obtain deleted messages | Facebook Messenger | Retained; I | Prior copy only B/C; uncaptured recovery D | Original consent; I | None for recovery | Secure-storage download not a deleted-content promise. [APP21][AND14] |
| observe disappearing content | Facebook Messenger | N | Exposed pre-expiry label/excerpt C; protected payload D/E | Explicit scope/retention opt-in | L-content | No protected screenshot/recording workaround. [APP8][AND16] |
| access notification metadata | Facebook Messenger | N | Yes delivered records A | N | M-observation | Bubble/summary record not universal message ID. [AND1][AND2] |
| receive user-shared evidence | Facebook Messenger | I | Yes selected allowed artifact A/B | I | H-artifact | Computer export transfer, screenshots, ordinary files as available. [APP21][AND6] |

#### SMS (MMS/RCS explicitly separated)

| Capability | App | Android API | Possible? / class | User consent? | Reliability | Restrictions / sources |
|---|---|---|---|---|---|---|
| detect new message | SMS | S; N | Authorized receiver A; N conditional/policy-gated C | S; N only if eligible | M authorized event | SMS gate; multi-part/OTP delays; not RCS. [AND20][AV5][PLAY1] |
| obtain text | SMS | S; I; N gated | Body with approved access A; selected text B | S or I | Structured field; H-artifact | No permission-workaround NLS scraping. [AND20][AND21][PLAY1] |
| obtain sender | SMS | S; I | Originating address A; copied/display name B/C | S or I | H address field; L identity | Contact name requires independent source; spoofing possible. [AND21] |
| obtain timestamp | SMS | S; N; I | PDU/provider/post/claimed UI fields A/C/B | S or I; N gated | H field; provenance-dependent | DATE versus DATE_SENT versus PDU/post/observer time. [AND21][AND2] |
| detect image | SMS | None for SMS; I / MMS parts | No plain SMS attachment; selected MMS hint B/C | I; S for approved MMS | None SMS; conditional MMS | MMS and RCS are not SMS body data. [AND20] |
| obtain image itself | SMS | I; authorized MMS provider | No plain SMS; selected/authorized MMS file A/B | I or approved S | H selected bytes | Separate MMS parts/permissions; no RCS private DB. [AND20][AND7] |
| detect audio | SMS | None for SMS; I / MMS | No plain SMS; selected MMS hint B/C | I; approved S | None SMS; conditional MMS | A URL is not the audio file. [AND20] |
| obtain audio itself | SMS | I; authorized MMS provider | No plain SMS; selected MMS file A/B | I or approved S | H selected bytes | Separate MMS handling; capture of playback not original. [AND20][AND7] |
| detect video | SMS | None for SMS; I / MMS | No plain SMS; selected MMS hint B/C | I; approved S | None SMS; conditional MMS | RCS notification is not SMS provider access. [AND20] |
| obtain video itself | SMS | I; authorized MMS provider | No plain SMS; selected MMS file A/B | I or approved S | H selected bytes | Not RCS/private client history. [AND20][AND14] |
| obtain historical chat | SMS | S; I | Authorized retained SMS provider A; selected export B | S or I | Structured retained store | Play eligibility; client export availability unverified; no lifetime completeness. [AND20][PLAY4] |
| obtain deleted messages | SMS | Retained; I | Prior copy only B/C; deleted row recovery D | Original consent; I | None for recovery | No forensic/private-store extraction. [AND14][AND20] |
| observe disappearing content | SMS | N; I; S where applicable | No universal SMS disappearing mechanism; client label C | Eligible N/S or I | Unestablished semantics | RCS/client expiry/private behavior not inferred from SMS API. [AND20][PLAY1] |
| access notification metadata | SMS | N | Technically delivered records A; policy gate | N plus established eligibility | M-observation | Metadata collection must not become SMS restriction workaround. [AND1][PLAY1] |
| receive user-shared evidence | SMS | I | Selected screenshot/text/client export A/B | I | H-artifact | No automated inbox substitute; review release policy/client support. [AND6][PLAY1] |

### 7.2 Separate legitimate service APIs, not Android cross-app access

| App / API | What authorized integration may expose | Cannot be assumed | Classification / Sakshi decision |
|---|---|---|---|
| WhatsApp Business Platform | Business-account messaging and permitted event/media flows | Arbitrary personal app history, all past messages, deleted/View Once payloads | Official service API, distinct from A-class Android access; explicit business consent, terms and online processing. Not MVP. [API1] |
| Instagram professional messaging | Authorized professional inbox/customer conversations with app review/scopes | All ordinary personal accounts/private DM archives | Official limited service API; explicit account authorization; not MVP. [API2] |
| Messenger Platform | Authorized Page/business messaging | Arbitrary personal Messenger E2EE history | Official limited service API; not MVP. [API2] |
| Telegram API / TDLib | Authenticated client cloud message history, updates and authorized media | Another installed client's device-specific secret chats; protected downloads/deleted recovery | Legitimate optional integration with session/terms controls; online acquisition, local analysis possible; not MVP. [API3][APP14][APP15] |
| Telegram Bot API | User-to-bot messages and permitted group/channel interactions | Every private conversation, preexisting full history, secret chats | Narrow consent/membership/privacy rules; avoid server evidence flow by default. [API4] |
| Signal | No general third-party historical message service API established here | That source code/library availability creates cross-app database permission | NLS/import only; independently validate future public export availability. [APP13][AND14] |

## 8. Required Sakshi ingestion architecture and records

### 8.1 Bounded pipeline

```text
User selects source/consents OR selects import
  -> authorization/scope check
  -> bounded exposed notification snapshot OR selected artifact copy
  -> encrypted immutable evidence + acquisition provenance
  -> versioned parsing/OCR/STT
  -> user review and correction
  -> capture-aware pattern analysis
  -> explicit selected export
```

These are design requirements, not implemented Kotlin modules.

**Acquisition adapters:** `NotificationObservation`, `SharedArtifact`, `SelectedDocument`, `SelectedVisualMedia`, `ManualIncident`. Optional later: `ScreenCaptureArtifact`, `AuthorizedSms`. Keep service APIs as separate online adapters if ever approved.

**No fallback escalation:** If text/media is unavailable, return `unavailable` and offer a permitted user import/manual description. Do not request accessibility/root/all-files permissions to bypass the missing surface.

### 8.2 Minimum evidence schema

| Record/field | Required behavior |
|---|---|
| `evidenceId`, `caseId`, `sourceKind` | App-generated immutable IDs; source kind distinguishes notification/file/capture/user statement. |
| `accessClass`, `consentRecordId`, scope/version | Preserve A/B/C meaning and applicable user selection/consent, including retention expectations. |
| Source package/profile/account claim | Android-reported package/profile for NLS; user-claimed origin for imports; never infer globally verified identity. Account nullable. |
| `notificationKey`, id/tag/channel/group/summary | Notification record identity and lifecycle; not chat-message identity. Channel nullable by API. |
| `conversationHint`, sender fields and confidence/source | Keep group/title/Person/parsed display name distinct; manual alias linking reversible. |
| Observation clocks | Wall time and elapsed-realtime for acquisition ordering within a boot/session; boot/session ID; publisher `when`; included message time; parsed export time/zone/locale. Unknown timestamps remain unknown. |
| Raw exposed text/field spans | Exact strings and field path/index as received, with parser version; no normalization overwriting source. App-produced JSON snapshot is a representation, not a byte-original chat message. |
| Artifact bytes/hash/size/MIME | Preserve bytes received from authorized stream; declared and detected MIME separate; SHA-256; identify previews/transcodes as such. |
| Availability/protection | Available/omitted/unknown/denied/expired/provider error/platform redacted when established. Do not guess redaction cause from generic text. |
| Derived records | OCR/transcript/parser output linked to artifact/version; confidence and observed/inferred/unknown distinctions. |
| Review/retention state | Save/reject/correct/export approval; separate retention choice for transient observations and case evidence. |
| Coverage record | Consent/access/connection/key/store availability epochs and gaps; known versus unknown source posting coverage. |

Use SQLite/Room transactions and encrypted blobs consistent with the eventual app architecture. Hash a canonical evidence representation with a version tag, not an arbitrary Bundle serialization or UTF-16/UTF-8 conversion assumed to be original network bytes. Do not persist foreign PendingIntent/action objects as durable evidence; record safe descriptive fields if needed.

### 8.3 Notifications and deduplication

1. Scope-filter package/profile first. Do not retain other apps' notification text for diagnostics.
2. Append an observation snapshot for posted/update events with capture clock and notification key. Identical reposts can be associated but not silently erased from lineage.
3. Extract structured included messages; tag historic/outgoing/context separately. Do not promote a summary count into individually fabricated messages.
4. Associate repeated included items using scoped sender/time/text/context hints, but keep uncertainty and duplicates reversible. Text alone is not a stable ID: “stop” sent twice may be two different events.
5. Maintain notification snapshot versions and field diffs. An edited excerpt does not prove a server-side message edit unless independently established.
6. On removal append lifecycle reason; do not delete evidence automatically or mark the message “deleted.” Follow declared retention policy and user choice.
7. On reconnect capture active records as `reconnect_snapshot`; previously dismissed/missed records remain unknown. Access enabled is not listener connected; connected is not all source messages captured.
8. Persist only through a key/store path that is available; bound memory queues and record overflow/storage/key failures. No silent cleartext fallback. Heavy workers may be delayed by Android/OEM quotas. [AND1][AV8]

### 8.4 Import hardening

- Treat URI, provider metadata, filename, MIME, text, ZIP/HTML/JSON/PDF and rendered content as attacker-controlled.
- Resolve only authorized selected content. Do not dereference imported HTTP links or remote HTML resources automatically; no evidence-driven tool execution.
- Stream-copy after user Save into an encrypted temporary artifact; finalize atomically with hash/size/provenance. Set configured size/item/time/decoded-pixel/archive-expansion limits. Reject traversal, symlinks, zip bombs and malformed archives. A `.txt` extension is not a sufficient type check.
- Preserve received archive bytes before parsing; parse a sandboxed copy. Validate attachment paths within the archive; missing attachments stay missing.
- For provider transcodes, preserve the stream received and label representation; do not claim it matches the source original. Avoid unnecessary second transcoding.
- Detect export dialect/version/locale. Ambiguous dates and sender splits require user review; do not force a guessed timezone. Forwarded/replied content is not the current sender's own words.
- Keep original and analysis separately. User correction edits interpretation, not original artifact bytes.
- No hidden telemetry, exception traces, thumbnails or temporary external files containing evidence. URI names and sender/time metadata are sensitive too.

## 9. Consent, privacy and user-safety contract

### Notification consent flow

1. Explain that Android notification access can expose broad device notifications, including unrelated personal data; Sakshi filters approved apps, stores exposed excerpts locally, cannot guarantee full capture and cannot read protected messages.
2. Let the user choose source apps, collection/retention mode and whether exposed disappearing-message excerpts may be retained. Do not covertly market “recover disappearing messages.”
3. Obtain affirmative app consent, then launch system grant settings. Returning is not proof of grant/connection; verify both.
4. Show acquisition status without displaying sensitive excerpts on Sakshi's notifications/lock screen. Make pause/revoke/clear-unsaved-buffer controls accessible; no deceptive hidden service.
5. Revocation immediately stops new capture; it does not imply deletion of prior saved case evidence. Explain retention/deletion/export independently.

**Per-chat scope caveat:** Android grants broad notification access, not selected chats. If an app offers optional chat filtering, use only actually exposed stable hints and warn that generic/grouped previews may be unclassifiable. Default to rejecting unclassifiable notifications for a strict per-chat scope, rather than collecting broadly under a narrow promise.

**Sensitive content:** Exclude OTP-focused/system/banking packages by default through positive source allowlists; Android's OTP redaction is a second protection, not a classifier substitute. Do not guarantee the app can detect every sensitive string. Keep UI/data local; no notification/evidence training upload. User incident statements are legitimate records but must be marked as user reports, not captured original media.

**Disappearing-content retention:** Supported exposed ordinary text may be collectible, but sender expectations and user-safety risks remain. Exclude known protected payloads; honor scope/retention choice; never promise retention of an undisclosed ephemeral message. If the app does not expose expiry metadata, say retention semantics are unknown. Current lack of a protective flag is not permission to target a known protected viewer.

**Exports:** Preview recipients/selected items/redactions; no automatic police/contact/email upload. Any export can reveal survivor/sender/group information. Source evidence hash/signature does not prove truth, trusted time, or court admissibility.

**Play Data safety nuance:** Local-only processing may not count as off-device “collection” for the Data safety form, but it still requires user-data/privacy compliance and in-app disclosure where applicable. SDK uploads, backup, crash content and third-party transfers change the analysis. Review actual implementation, not just an “offline” marketing label. [PLAY3][PLAY7]

## 10. Device-validation plan and release acceptance criteria

**No Android acquisition prototype, real-device experiment, Play submission, app-version interoperability benchmark, or legal review was performed here.** The following is the required validation plan, not a claim of completed tests. Test with consenting participants/test accounts and synthetic test messages clearly labeled synthetic; do not collect uninvolved people's chats.

### 10.1 Test record

For each run record phone model, Android/API/QPR/build/security patch, user/profile, source app/version/account type, Sakshi target/collector version, installation path, grants, source channel/preview settings, battery/network state and test ground truth. Store encrypted/minimized diagnostic fixtures; no public real-message dumps.

### 10.2 Required scenarios

| Test group | Scenarios | Expected evidence / pass boundary |
|---|---|---|
| Source posting | Each of six families; app foreground/background; online/offline reconnect; source posting permission denied; all channels off; muted chat; DND | Report observed notification coverage, not universal message detection. Sound silence must not be treated as no notification. |
| Structured parsing | MessagingStyle versus generic/custom; long multiline/Unicode/Indic/code-mixed text; sender with colon; null sender/outgoing history | Exact exposed text preserved; unknown sender/time stay unknown; historic/outgoing not counted as new incoming. |
| Group and account | Two groups, multiple senders, same names; group summaries/children; multiple accounts; Telegram topics; cloned apps/profile separation | No summary double-counting or automatic cross-account/person conflation; summary unknowns visible. |
| Bursts/lifecycle | Ten-message burst; rapid updates; repeated identical legitimate messages; reaction; edit; notification dismiss/read on another device | Compare visible included items and callbacks to ground truth; removal never falsely becomes chat deletion. |
| Platform privacy | Android 15 OTP/redacted notification; private space locked; work-profile/admin restriction; Signal no-content setting | No redaction bypass; mark unavailable; record what is actually exposed, with exact build/settings. |
| Collector lifecycle | Reboot before/after first unlock; force-stop/relaunch; revoke/regrant; process death; listener disconnect; battery saver/app sleep; key unavailable | Fail closed, gaps shown, active reconnect snapshots not “recovered history”; no permanent background promise. |
| Ordinary media | Shared image/audio/video/file; photo thumbnail; unreadable notification URI; expired grant; file not downloaded | Selected stream preserved; thumbnail labeled preview; denial never triggers private-path extraction. |
| Protected/ephemeral | WhatsApp View Once photo/video/voice; Instagram/Messenger View Once/Allow Replay; Signal View Once/Screen Security; Telegram protected/secret context | No blocked payload saved or alternate bypass; exposed label/user statement remains clearly distinct. Do not attempt unpermitted extraction. |
| Export | WhatsApp with/without media and Advanced Chat Privacy; Telegram Desktop JSON/HTML transfer; Instagram scoped archive; Messenger computer E2EE export; Signal public export availability | Preserve originals/scope; identify missing media; no claim source/internal feature equals shipping support. |
| Provider/import | Multi-share; wrong MIME; absent ClipData/grant; onNewIntent repeat; revoked SAF; cloud provider offline; huge/hostile archive/PDF/HTML | Preview/Save required; reject unsafe inputs; bounded resources; no remote fetch/secret logs; readable copy hash verified. |
| Screen projection | App-only/full screen; rotation; lock/user Stop; interrupted session; FLAG_SECURE; sensitive redaction; optional eligible audio | Session consent/release verified; protected blank stays unavailable; audio not promised when source excludes it. |
| SMS-specific | Eligible distribution only; multi-part/multi-SIM; permission/role loss; SMS/MMS/RCS distinction; OTP delays | Correct scope and clocks; no Play restriction workaround; MMS parts distinct; no uncaptured deleted recovery. |
| Vault/backup | Offline mode; disk full; key loss; app uninstall/reinstall; legacy cloud and D2D/cross-platform transports as applicable | No silent upload/cleartext fallback; exclusions tested; recovery limits explicit. |

### 10.3 Acceptance requirements

- **AC01:** Every acquired artifact has source kind, consent/scope, capture time, integrity representation/hash and unknown fields explicitly preserved.
- **AC02:** No acquisition via root, private database, credentials/session harvesting, backup extraction, undocumented provider probing or protection bypass.
- **AC03:** All six apps have tested notification/import fixtures or visible `unverified/unsupported` status; no green universal support badge based solely on generic Android docs.
- **AC04:** Duplicates, group summaries, historic context, outgoing replies and lifecycle callbacks cannot inflate event counts without uncertainty.
- **AC05:** Consent denial/revocation, redaction, no source notification, URI denial, key failure and disk full produce clear missing-coverage states, not guessed evidence.
- **AC06:** Imports are exact received artifacts; preview/OCR/STT/parser outputs are derivatives; original attachment availability is not inferred from a label.
- **AC07:** Protected View Once/ephemeral payloads are never an acquisition target or fallback path.
- **AC08:** No automatic SMS access ships until Play eligibility/permission/role gates pass; NLS/accessibility is not a workaround.
- **AC09:** No sensitive logs/analytics/network fallback; automatic backup/migration exclusions verified on supported devices.
- **AC10:** Export is explicit/user-reviewed; reports state acquisition scope, gaps, time/identity uncertainty and integrity limitations.
- **AC11:** Performance measurements cover callback-to-encrypted-store latency, burst drops, model-worker delay, RAM/battery and OEM outages. No completeness percentage is claimed without ground truth and denominator.
- **AC12:** Counsel and distribution reviewers assess actual data flow/markets; policy approval is not inferred from technical operation.

### 10.4 Measurements to publish

Measure source messages that generate notifications separately from generated notifications received by Sakshi. Report:

- Generated-notification callback coverage and active-snapshot catch-up coverage.
- Ground-truth message observation coverage by app/settings/modality, with missed-message reasons where known.
- Extracted sender/text/time correctness; truncation; false attachment detection; group/account misattribution.
- Duplicate inflation and missed distinct identical-message rates.
- Unknown/unavailable rates by Android/OEM/client version and protection mode.
- Import success, exact-byte hashes, missing attachments and ambiguous export dates.
- Background ingestion latency versus analysis latency and energy/storage cost.

A connected listener cannot measure all missing source notifications by itself. User-visible coverage should say “observations available from this source/settings” rather than “all conversations protected.”

## 11. Prioritized implementation implications

1. Build and test share/SAF/Photo Picker imports plus manual incident statements and immutable provenance.
2. Add a positive-allowlisted, read-only NLS collector with bounded encrypted persistence, versioned structured parsing and coverage epochs.
3. Validate on real devices before claiming support for any app-specific sender/media/group field.
4. Implement capture-aware counts and user correction flow before escalation/pattern claims.
5. Ship neither Accessibility nor MediaProjection for the initial automatic collector; consider short explicit capture later only if imports leave a demonstrated need.
6. Keep direct SMS and all SMS-notification derivation behind a release-policy gate; do not add restricted permissions as placeholders.
7. Add supported historical import adapters only against actual public export fixtures. WhatsApp privacy controls, Telegram protected/secret scope, Messenger secure storage and Signal release uncertainty must be first-class support states.
8. Recheck Android/Play/app documentation before each release. Document exact app/OEM builds tested; missing data remains unknown.

**Permissible product promise:** “Sakshi helps you preserve and review evidence you explicitly import and, if you opt in, message-related information exposed in supported Android notifications. Availability varies by app, settings and Android version.”

**Impermissible promises:** “Reads all chats,” “recovers deleted messages,” “captures View Once,” “records any app's voice/video,” “proves who sent it,” “always detects harassment,” or “guaranteed court-admissible.”

## 12. Source register

All sources consulted on 2 October 2026. API references define platform surfaces, not six-app behavior guarantees. **Full** = fetched primary page/body or inspected relevant overflow text; **Indexed** = primary publisher's indexed passages inspected, full body unavailable/not fetched; **Repo** = official repository metadata verified with GitHub CLI. Date/version stability must be rechecked before release.

### Android platform and APIs

- **[AND1] Full:** Android, NotificationListenerService: binding/grant declaration, callbacks, connection, active/snoozed records, filters, light removal payloads, work-profile and low-RAM restrictions. https://developer.android.com/reference/android/service/notification/NotificationListenerService
- **[AND2] Indexed:** Android, StatusBarNotification: key/post time/package/profile/group. https://developer.android.com/reference/android/service/notification/StatusBarNotification
- **[AND3] Indexed:** Android, Notification: extras, historic messages, previews, remote input history/actions. https://developer.android.com/reference/android/app/Notification
- **[AND4] Full:** Android, MessagingStyle.Message: text/Person/timestamp/MIME/URI; URI permissions required. The reference's legacy wording on broad MediaStore access must be read with modern scoped-storage docs, not as a current permission exemption. https://developer.android.com/reference/android/app/Notification.MessagingStyle.Message
- **[AND5] Indexed:** Android, People and conversations / MessagingStyle; UI/shortcut requirements and group participation. https://developer.android.com/develop/ui/views/notifications/conversations ; https://developer.android.com/reference/android/app/Notification.MessagingStyle
- **[AND6] Full:** Android, Receive simple data from other apps: receiving activities, ACTION_SEND/MULTIPLE, MIME and content. https://developer.android.com/training/sharing/receive
- **[AND7] Indexed:** Android, Send simple data: stream URIs, grants, FileProvider. https://developer.android.com/training/sharing/send
- **[AND8] Full:** Android, Access documents and other files: SAF, persisted grants, tree/per-file restrictions/providers. https://developer.android.com/training/data-storage/shared/documents-files
- **[AND9] Indexed:** Android, Storage updates in Android 11 / AOSP scoped storage. https://developer.android.com/about/versions/11/privacy/storage ; https://source.android.com/docs/core/storage/scoped
- **[AND10] Indexed:** Android, Photo Picker: selected visual media, availability/backport/fallback. https://developer.android.com/training/data-storage/shared/photo-picker
- **[AND11] Indexed:** Android, Access media files from shared storage: collections and permissions. https://developer.android.com/training/data-storage/shared/media
- **[AND12] Indexed:** Android, Manage all files: Android/media shared storage versus excluded app-specific data; limited policy use. https://developer.android.com/training/data-storage/manage-all-files
- **[AND13] Indexed:** Android, Restrict interactions / Content provider basics: permissions, exported access and per-URI grants. https://developer.android.com/training/permissions/restrict-interactions ; https://developer.android.com/guide/topics/providers/content-provider-basics
- **[AND14] Full:** AOSP, Application Sandbox: UID/process, native code, SELinux and per-app isolation. https://source.android.com/docs/security/app-sandbox
- **[AND15] Full service reference / Indexed node references:** Android AccessibilityService binding/capabilities and screenshot APIs (display API 30, window API 34); AccessibilityEvent/NodeInfo and API diffs: event/node retrieval is supplied by views and can be restricted. https://developer.android.com/reference/android/accessibilityservice/AccessibilityService ; https://developer.android.com/reference/android/view/accessibility/AccessibilityEvent ; https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo ; https://developer.android.com/sdk/api_diff/34/changes/android.view.accessibility.AccessibilityEvent
- **[AND16] Full:** Android, Secure sensitive activities: FLAG_SECURE capture/nonsecure-display limits and documented old-device limitations. https://developer.android.com/security/fraud-prevention/activities
- **[AND17] Full:** Android, Media projection: session consent, FGS, tokens, app-sharing, callbacks, chip/lock-stop. https://developer.android.com/media/grow/media-projection
- **[AND18] Full / Indexed reference:** Android, Capture video/audio playback / AudioPlaybackCaptureConfiguration: consent, RECORD_AUDIO, profile/usage/capture-policy restrictions. https://developer.android.com/media/platform/av-capture ; https://developer.android.com/reference/kotlin/android/media/AudioPlaybackCaptureConfiguration
- **[AND19] Full:** Android, Auto Backup: default participation, private backup storage, exclusions and version/OEM D2D/cross-platform rules. https://developer.android.com/identity/data/autobackup
- **[AND20] Indexed:** Android, Telephony / Sms.Intents / Sms: provider and default-handler versus authorized reader/receiver; OTP filtering. https://developer.android.com/reference/android/provider/Telephony ; https://developer.android.com/reference/android/provider/Telephony.Sms.Intents ; https://developer.android.com/reference/kotlin/android/provider/Telephony.Sms
- **[AND21] Indexed:** Android, TextBasedSmsColumns: ADDRESS/BODY/DATE/DATE_SENT/thread/type contracts. https://developer.android.com/reference/kotlin/android/provider/Telephony.TextBasedSmsColumns
- **[AND22] Full:** Android Manifest.permission READ_SMS/RECEIVE_SMS: dangerous and hard-restricted; installer allowlisting required. Relevant primary reference sections were fetched and inspected. Verify actual installer/role behavior on target devices before shipping. https://developer.android.com/reference/android/Manifest.permission#READ_SMS ; https://developer.android.com/reference/android/Manifest.permission#RECEIVE_SMS

### Version changes

- **[AV1] Indexed:** Android 13 notification runtime permission: sending permission and denial behavior, not NLS grant. https://developer.android.com/develop/ui/compose/notifications/notification-permission
- **[AV2] Indexed:** Android 14 selected visual media permission behavior. https://developer.android.com/about/versions/14/changes/partial-photo-video-access
- **[AV3] Full:** Android 15 all-app changes: OTP NLS redaction, screenshare, private space, force-stop, QPR1 chip; target-35 changes: BOOT_COMPLETED foreground-service restrictions including mediaProjection. https://developer.android.com/about/versions/15/behavior-changes-all ; https://developer.android.com/about/versions/15/behavior-changes-15
- **[AV4] Full:** Android security blog, sensitive accessibility views/non-tool exclusions, Android 16 guidance and deceptive-tool consequences. https://developer.android.com/blog/posts/enhancing-android-security-stop-malware-from-snooping-on-your-app-data
- **[AV5] Full:** Android 17 all-app changes: WebOTP and existing hash protections/delays, exemptions, background audio guidance links. https://developer.android.com/about/versions/17/behavior-changes-all
- **[AV6] Full:** Android 17 target changes: ordinary OTP-bearing SMS protection for target 37+. https://developer.android.com/about/versions/17/behavior-changes-17
- **[AV7] Indexed:** Android 17 summary: restricted-message headline lacks matching explanation in inspected detailed page; unresolved, not universal messenger NLS claim. https://developer.android.com/about/versions/17/summary
- **[AV8] Indexed:** Android 16 JobScheduler/WorkManager quotas and power management. https://developer.android.com/about/versions/16/behavior-changes-all ; https://developer.android.com/topic/performance/power/power-details

### App-controlled surfaces

- **[APP1] Indexed, empty full fetch:** WhatsApp Help, Android chat export: text file and recent media attachments, Sharesheet workflow. https://faq.whatsapp.com/1180414079177245/?cms_platform=android
- **[APP2] Indexed, empty full fetch:** WhatsApp Help, Advanced Chat Privacy: export/gallery saving restrictions and version caveat. https://faq.whatsapp.com/715385484388016/?cms_platform=web
- **[APP3] Indexed title, body not established:** WhatsApp Android notification management and mute controls; exact Android extras/group behavior requires testing. https://faq.whatsapp.com/797069521522888/?helpref=faq_content&cms_platform=android ; https://faq.whatsapp.com/694350718331007/?locale=en_US&cms_platform=android
- **[APP4] Indexed title, empty full fetch:** WhatsApp Help, View Once media and voice messages. Payload-extraction details are not inferred solely from this title. https://faq.whatsapp.com/578442220724722/?locale=en_US&cms_platform=android
- **[APP5] Indexed primary announcement:** Meta/WhatsApp screenshot blocking for View Once; announcement is not current-device certification. https://about.fb.com/news/2022/08/new-privacy-features-on-whatsapp/ ; https://blog.whatsapp.com/new-features-for-more-privacy-more-protection-more-control
- **[APP6] Indexed:** Instagram Help, notification/mute controls. https://www.facebook.com/help/instagram/417173528786894 ; https://www.facebook.com/help/instagram/469042960409432/?locale=en_GB
- **[APP7] Full Facebook mirror / Indexed English:** Instagram information review/export: scope, format/date/media quality/device. Does not guarantee every DM/ephemeral record. https://www.facebook.com/help/instagram/181231772500920?helpref=hc_fnav ; https://help.instagram.com/181231772500920/?helpref=related_articles
- **[APP8] Full:** Meta, October 2024 Instagram/Messenger ephemeral screenshot/recording prevention, updated article October 2025. https://about.fb.com/news/2024/10/instagram-campaign-protect-teens-sextortion-scams/
- **[APP9] Full:** Telegram, Desktop JSON/HTML export/media and notification exceptions. https://telegram.org/blog/export-and-more?setln=en
- **[APP10] Indexed, full fetch 403:** Signal, In-App Notification Options: platform-specific name/content settings. https://support.signal.org/hc/en-us/articles/360043273491-In-App-Notification-Options
- **[APP11] Indexed:** Signal, Troubleshooting Notifications: source delivery/OS/OEM battery settings. https://support.signal.org/hc/en-us/articles/360007318711-Troubleshooting-Notifications
- **[APP12] Indexed:** Signal, Samsung Notifications: background sleeping/power settings. https://support.signal.org/hc/en-us/articles/7874157231002-Samsung-Notifications
- **[APP13] Repo + indexed diff:** Official Signal Android commit `f2e4881026b828a7e8ca7fdc3b62281221f57219`, 25 March 2026, “Add underpinnings to allow for local plaintext export.” Paths include internal backup playground; release/public UI not established. https://github.com/signalapp/Signal-Android/commit/f2e4881026b828a7e8ca7fdc3b62281221f57219
- **[APP14] Indexed:** Telegram FAQ, device-specific Secret Chats/not cloud/no forwarding. https://telegram.org/faq/
- **[APP15] Full:** Telegram content-protection API, groups/channels/bots/private chats: respect copying/forwarding/screenshots/download controls. https://core.telegram.org/api/content-protection
- **[APP16] Indexed, full fetch 403:** Signal, View/save ordinary media/files/audio on Android. https://support.signal.org/hc/en-us/articles/360007317471-View-and-save-media-or-files
- **[APP17] Indexed:** Signal, Screen Security on Android screenshot/app-switcher prevention. https://support.signal.org/hc/en-us/articles/360043469312-Screen-Security
- **[APP18] Indexed, full fetch 403:** Signal, View Once Media and lifecycle/history absence. https://support.signal.org/hc/en-us/articles/360038443071-View-Once-Media
- **[APP19] Indexed, full fetch 403:** Signal Secure Backups and exclusions/recovery-key boundary. https://support.signal.org/hc/en-us/articles/9708267671322-Signal-Secure-Backups
- **[APP20] Indexed:** Messenger, disable notification previews/alerts/group notifications. https://www.facebook.com/help/android-app/330627630326605 ; https://www.facebook.com/help/messenger-app/330627630326605
- **[APP21] Full:** Meta Help, computer download of E2EE message storage/attachments. https://www.facebook.com/help/1388443508232330/

### Legitimate provider service APIs

- **[API1] Indexed:** Meta, WhatsApp Business Platform overview, business account scope. https://developers.facebook.com/docs/whatsapp/cloud-api/overview/
- **[API2] Indexed:** Meta, Messenger Platform/professional Instagram messaging overview: account/scopes/review/webhooks, not universal personal chats. https://developers.facebook.com/documentation/business-messaging/messenger-platform/overview ; https://developers.facebook.com/documentation/business-messaging/instagram-messaging/overview
- **[API3] Full:** Telegram APIs/TDLib and authenticated client versus bot scope. https://core.telegram.org/api
- **[API4] Full:** Telegram Bots FAQ: membership/privacy-mode scope. The FAQ's historical bot-to-bot restrictions conflict with newer API overview features; only scope relevant to arbitrary personal inbox access is used here. https://core.telegram.org/bots/faq

### Google Play / installation policy

- **[PLAY1] Full:** Permissions/APIs accessing sensitive information: minimum scope, media/all-files restrictions, SMS default/exception rules and no alternative-API derivation. Some preview changes effective January 2027 are not treated as already active. https://support.google.com/googleplay/android-developer/answer/16558241?hl=en
- **[PLAY2] Full:** Accessibility API use: declaration, non-tool disclosure, tool definition, autonomous versus deterministic automation. https://support.google.com/googleplay/android-developer/answer/10964491
- **[PLAY3] Full:** Spyware policy: unexpected notification/data access and transmission; consent/functionality obligations. https://support.google.com/googleplay/android-developer/answer/14745000?hl=en-GB
- **[PLAY4] Full:** SMS/Call Log permitted uses/exceptions; physical-safety exception is SEND_SMS; review required. https://support.google.com/googleplay/android-developer/answer/10208820
- **[PLAY5] Full:** Play Protect guidance: sensitive-permission internet-sideload block in select markets, non-exhaustive permitted cases and deceptive accessibility warning. https://developers.google.com/android/play-protect/warning-dev-guidance
- **[PLAY6] Indexed:** Android restricted settings guidance for installation paths on Android 13+. https://support.google.com/android/answer/12623953
- **[PLAY7] Indexed:** Data safety local-processing/reporting distinctions; not a privacy-policy exemption. https://support.google.com/googleplay/android-developer/answer/10787469?hl=en-GB

### Legal primary sources, not product clearance

- **[LEGAL1] Indexed primary Act:** India, Digital Personal Data Protection Act 2023, sections 3-6; commencement/final rules not independently validated in this task. https://www.meity.gov.in/static/uploads/2024/02/Digital-Personal-Data-Protection-Act-2023.pdf
- **[LEGAL2] Indexed primary statute:** India, IT Act 2000, sections 43/66; no authorization to circumvent. https://www.indiacode.nic.in/bitstream/123456789/13116/1/it_act_2000_updated.pdf
- **[LEGAL3] Indexed primary regulation:** GDPR, Article 2/6/9 and Recital 18; deployment-specific controller/processor/household analysis required. https://eur-lex.europa.eu/legal-content/EN/TXT/HTML/?from=EN&uri=CELEX%3A02016R0679-20160504
