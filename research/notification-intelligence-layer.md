# Notification Intelligence Layer - Sakshi

**Research date:** 2 October 2026

**Status:** Source-backed technical design, not implemented or certified on an Android device.

**Scope:** An ordinary, non-rooted Android app observing the device owner's notifications after explicit opt-in. No privileged/OEM role, database access, screen capture, notification actions or default cloud processing.

## 1. Decision and product promise

**Build an optional, event-driven signal layer, not a notification archive or universal chat monitor.** `NotificationListenerService` (NLS) can observe notifications that a source app actually publishes and Android allows the listener to receive. Extract bounded text and metadata, suppress duplicate representations, run cheap local checks, and optionally run validated local inference. Retain only selected incident candidates under a separately consented, expiring review policy. The user decides what becomes evidence. [A1-A5]

The useful claim is:

> With notification access enabled, Sakshi can suggest possible incidents from available notification excerpts in selected apps. Some messages, names and context will be unavailable. Suggestions are not legal findings, complete chat history, or emergency protection.

**Do not promise:** every message captured; authenticated human sender; original attachments; message-deletion detection; recovered deleted chats; complete notification history; real-time guarantees; calibrated harassment probabilities without task-specific evaluation.

This complements the existing Android acquisition and multimodal specifications. It does not change their boundaries or replace user-mediated imports.

### Evidence status

- **Verified API fact:** Explicitly supported by the inspected Android references and behavior-change documentation.
- **Verified publisher statement:** App/model publisher documents a feature, restriction or benchmark. Not independent validation of every shipped Android build.
- **Proposed design:** Sakshi architecture, policy, thresholds and resource budgets below. Not measured performance.
- **Open / device validation:** Actual callback payloads, preview behavior, OEM delivery and model performance require an instrumented prototype with consenting test accounts and synthetic fixtures.

No Android app was built, installed or exercised for this investigation; no personal notification data was accessed. The repository currently contains research artifacts, not an Android implementation. Existing reports describe a potential Android 16 reference phone, but their device inspection is not notification validation.

## 2. Verified Android baseline

### 2.1 Service and authorization

NLS has existed since API 18. Android calls it for notification posting, removal and ranking changes. Updating a notification can trigger posting again; this is not a message-received protocol callback. [A1]

Declare the service using the pattern in the **current Android reference**:

```xml
<service
    android:name=".notifications.SakshiNotificationListener"
    android:exported="false"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
    <intent-filter>
        <action android:name="android.service.notification.NotificationListenerService" />
    </intent-filter>
</service>
```

This is a proposed manifest fragment, not an installed service. `BIND_NOTIFICATION_LISTENER_SERVICE` protects system binding; it is not a dangerous permission Sakshi obtains with a runtime popup. After an in-app explanation and consent, open notification-access Settings using the supported `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS` flow. Check the exact component's access with `NotificationManager.isNotificationListenerAccessGranted` on API 27+, and track connection separately. Access enabled is not proof the service is currently receiving callbacks. Handle a missing Settings Activity without crashing or trying an undocumented alternative. [A1][A13]

`POST_NOTIFICATIONS` on Android 13+ controls an app's own notification posting. Sakshi's grant affects its review alerts, not its authority to read other apps. The source app's posting permission, channels and preferences affect whether there is an ordinary notification to observe. Exempt notifications exist, so don't reduce the entire OS to one permission boolean. [A7]

The OS authorization is sensitive and broad. Sakshi's app/conversation allowlist is an **application processing policy**, not an OS-granted per-chat permission. Some Android builds expose listener filters; their availability and choices do not establish a universal per-chat authorization surface.

### 2.2 Lifecycle and platform versions

| Capability / restriction | Documented boundary | Design implication |
|---|---|---|
| Callbacks | API 24+ callbacks run on the main thread | Only bounded snapshotting and nonblocking handoff in callbacks; no model/tokenizer/disk work there. |
| Connected state | Wait for `onListenerConnected()` before listener operations | Query outstanding notifications only after connection. |
| Disconnection | `requestRebind(ComponentName)` is safe before connection/after disconnect | Recovery is bounded and conditional on consent/access; no reconnect loop or bypass. |
| Active snapshot | `getActiveNotifications()` returns outstanding notifications | Can reconcile currently active items, not replay missed notifications. |
| Removal | Payload is "light" and can omit heavyweight fields | Preserve selected posting-time fields; removal is lifecycle metadata only. |
| Low-RAM | NLS cannot obtain access/be bound on low-RAM Android Q/10 and below | Unsupported-state UI and manual/import fallback. This restriction is not documented as applying to every later low-RAM version. |
| Managed profiles | Listeners running in a work profile are ignored; device policy can block work-origin notifications | Never promise cross-profile coverage; keep profile namespaces separate. |
| Filter types | API 31 metadata supports conversations, alerting, silent, ongoing; absent default means all types | Do not copy the example that disables silent/ongoing. Silent messages can be relevant. Honor user choices. |
| Source posting | Android 13+ permission and channel/app settings | Missing posts cannot be analyzed or recovered by NLS. |
| Sensitive content | Android 15 blocks untrusted listeners from unredacted notifications where an OTP is detected | Accept redaction. Do not seek a trusted role or companion association to evade this. |
| Private space | On Android 15, locked private-space apps stop, including notification activity | Missing observations are expected; do not inspect hidden profiles. |

Sources: [A1][A6][A7]. Proposed MVP minimum API: **30**, so platform message-array parsing and modern conversation APIs are available. This is a scope choice, not a requirement of NLS itself. If older Android support is needed, use a separately tested AndroidX compatibility adapter and explicit API guards. Do not call `Message.getMessagesFromBundleArray()` on API 24-29: that public helper was added in **API 30**, although MessagingStyle itself is API 24 and Person is API 28. [A4]

The current API reference exposes API 37-family entries. The inspected Android 17 all-apps change explains SMS/WebOTP delivery/provider restrictions, not a blanket ban on ordinary E2EE notification excerpts. An existing project report flags an unresolved "restricted message access" summary entry. Do not infer that all messenger notifications are accessible or blocked from that headline. Recheck release/QPR behavior on the actual deployment build. [A14]

## 3. Extraction capabilities and limitations

### 3.1 Text, identity, time, grouping and rich content

| Investigated surface | What Android can provide | Limit / correct interpretation |
|---|---|---|
| Plain notification text | `Notification.extras`: `EXTRA_TITLE`, `EXTRA_TEXT`, `EXTRA_BIG_TEXT`, `EXTRA_TEXT_LINES`; optionally subtext/summary | Publisher-provided excerpts and labels, possibly absent, generic, truncated or localized. Title is not universally sender. |
| MessagingStyle | `EXTRA_MESSAGES`, parsed with public `Message.getMessagesFromBundleArray()` on API 30+ | Structured list only when publisher includes it. One callback can contain many messages and repeated previous entries. |
| Historic messages | `EXTRA_HISTORIC_MESSAGES` | Publisher-selected context, not Android history or a full conversation. Never count automatically as newly received content. |
| Sender | `Message.getSenderPerson()`, Person name/key/URI, or generic title as a weak fallback | Display metadata, not authenticated identity. Null sender is documented for current-user messages; do not attribute those to an attacker. |
| Message timestamp | `Message.getTimestamp()` | Publisher claims the arrival time. Not independently verified server-send time. Record provenance and clock uncertainty. |
| Posting time | `StatusBarNotification.getPostTime()` | System wall-clock time of notification posting, distinct from source message time and `Notification.when`. Delays/reposts matter. |
| Display time | `Notification.when` | Publisher-controlled display/event time, sometimes conversation-level or arbitrary. |
| Collector time | Sakshi wall clock plus `SystemClock.elapsedRealtime()` and collector session | Useful for ordering/durations within a session; wall clock can change and monotonic time resets across boot. |
| Conversation context | MessagingStyle conversation title/group flag; shortcut ID; ranking/conversation-channel hints on supported APIs | App/profile-scoped hints. Group title is not sender, shortcut is not a universal account/chat ID. No complete participant list. |
| Android grouping | `sbn.groupKey`, notification group/sort keys, `FLAG_GROUP_SUMMARY` | Notification UI grouping, not chat membership. Summaries may span chats/accounts. |
| Multiple messages | Structured message arrays / text lines | Diff included entries conservatively. Neither callbacks nor displayed unread counts equal received-message counts. |
| Collapsed presentation | Extras can contain expanded text/messages although UI is collapsed | UI collapse alone does not imply payload loss. Expansion does not fetch missing history for Sakshi. |
| Custom layout | Some apps publish foreign `RemoteViews` without sufficient standard extras | Abstain if standard fields are insufficient; no inflating/scraping arbitrary foreign views or screen OCR. |
| Media label / MIME | Text such as "Photo"; optional message MIME and `dataUri` | Type hint, not image/audio/video bytes; absent URI/grant is normal. |
| Icons / previews | Avatar, large icon, optional picture representations | Not necessarily attachment or original quality. Baseline drops all image fields. |
| MediaStyle | Playback notification and media-session token | Music/video/call UI is not an incoming voice-note transcription or attachment interface. |
| Inline reply history | `EXTRA_REMOTE_INPUT_HISTORY` when supplied | Optional local outgoing context, not incoming harassment and not an outgoing-message journal. |
| Notification actions | Reply/open/read/cancel actions, PendingIntents | Executable authority, not evidence. Do not execute, enumerate for ingestion, or retain them. |

Contracts: [A2-A5][A8]. Copy only typed bounded values from standard fields. Flatten CharSequence into plain text without retaining spans/foreign objects; preserve text as exposed within declared caps. Handle wrong types and malformed bundles at the parser boundary without logging contents. Do not serialize the full Notification, Bundle, Parcelable, icons, actions or PendingIntents.

### 3.2 Privacy, encryption, deletion and history

**Three distinct mechanisms must not be conflated:**

1. **System presentation:** `visibility` and `publicVersion` control exposure by SystemUI in insecure situations such as a secure lock screen or screensharing. This alone is not a universal promise that an authorized listener receives the identical redacted version shown on screen. [A2][A6][A8]
2. **Publisher omission:** The messaging app may construct only "New message," omit sender/body, or replace the entire payload while app/chat locked. Sakshi cannot reconstruct what was not published. [P1-P3]
3. **System listener redaction:** Android 15 explicitly restricts untrusted NLS access to unredacted OTP-detected notifications. Permission does not override it. [A6]

**Sakshi policy should be stricter than mere technical accessibility:** Drop `VISIBILITY_SECRET`; for private/public-version notifications, default to no private-body analysis while the device is locked. Check keyguard state before extraction and again before inference/persistence. Do not switch to `publicVersion` text as if it were an ordinary message. If lock state cannot be determined, fail closed under the default policy. Users can explicitly enable "analyze available previews while device locked," with separate disclosure; that still respects app omission and system redaction. `VISIBILITY_PRIVATE` is common, so this policy intentionally reduces locked-device coverage. It does not assert Android necessarily blocks all such listener text.

There is no portable redaction inference from a single localized placeholder string. Versioned placeholder recognition can provide `generic_or_redaction_suspected`, not a guaranteed OS-redacted bit. Never use reflection, hidden resources or guessed extras as authority. Missing text means **unknown**, not "safe" and not "threat detected."

**Encrypted notification content:** NLS receives the notification representation, not encrypted messenger transport or its keys. An E2EE endpoint can deliberately post a plaintext excerpt after decryption. That is not breaking E2EE and does not imply that all E2EE apps do so. Opaque/ciphertext placeholders or generic locked notifications are not analyzable text; do not decrypt, probe app state or reconstruct them. Sakshi encryption protects its own retained observations separately.

**Removed/deleted notifications:** `onNotificationRemoved(..., reason)` can report dismissal, app cancellation, group optimization, timeout or other notification lifecycle reasons. Even `REASON_APP_CANCEL` does not identify source-message deletion. Reading on another device or replacing a summary may look similar. A retained excerpt can survive later source deletion only because it was previously captured under an explicit retention policy. That is **prior observation**, not deleted-message recovery. Do not change a record to "message deleted" or "sender concealed evidence." [A1]

**Notification history:** AOSP documents an optional OEM Settings screen, defaulting to 24-hour retention, which users can turn off. NLS access does not grant an ordinary app a documented API for querying that system history database. `getActiveNotifications()` is not history; snoozed-notification access is not history either. Offer instructions for the user to inspect their own history and deliberately import permitted context, but do not automate Settings extraction or query internal databases. [A1][A9]

## 4. Suppression, app differences and coverage

### 4.1 Posting versus alerting versus listener visibility

Separate these conditions:

```text
Source message exists
  -> source app receives it
  -> source app chooses to publish/update a notification
  -> Android accepts and exposes it to this listener
  -> Sakshi is connected and policy permits processing
  -> parser/filter/inference successfully processes available fields
```

Failure at any step means unknown coverage. NLS cannot observe messages for which no accessible notification exists.

- DND concerns interruptions such as sound/vibration; `matchesInterruptionFilter()` is ranking metadata. An unalerted notification may still be posted and observed. Do not discard all DND/silent-channel events. [A1]
- Listener filter settings can exclude types/apps. Respect them; Sakshi's rules cannot re-enable unseen events.
- Disabled source notifications, channel blocking, app-side muted-chat logic, foreground chat behavior, batched delivery, connectivity, source background restrictions and OEM app sleep can suppress/delay posts. Exact muted behavior is app-specific: silence is not necessarily no post.
- Android/OEM presentation changes, cooldowns or summaries need fixture tests; do not assume presentation text is unchanged or every suppressed alert produces a callback.
- Source and Sakshi background failures are different. Fixing source delivery does not guarantee listener uptime.
- Revocation, force-stop, reboot, profile changes or process death can create gaps. No busy polling or persistent wake lock can establish complete capture.

### 4.2 App compatibility register

| App | Primary-source evidence reviewed | Sakshi implication / unresolved fixture questions |
|---|---|---|
| WhatsApp | Meta documents Chat Lock hiding conversation contents in notifications. WhatsApp Android help fetch returned an empty body; it was not used to establish a full Android schema. [P1] | Locked-chat body unavailable is expected. Test ordinary vs locked chats, reactions, one/group chats, account variants, summaries, locale, media and updates. Do not infer View Once from "Photo"/"Voice message." |
| Signal | Official indexed support describes Android Show modes: name and message, name only, no name or message; locked Signal replaces content with "Locked message." Full support-page fetch returned HTTP 403. [P2] | Respect chosen preview mode. Test repetition, reactions, locks, disappearing-message context and inline replies. Do not apply macOS-specific disappearing-message rules to Android. |
| Telegram | Official protocol schema includes `show_previews`, `silent`, `mute_until`; this is client/settings semantics, not a NLS Android payload contract. [P3] | Test previews, app lock, secret vs cloud chats, accounts, topics, groups/channels and summaries. Do not connect an authenticated Telegram client just to fill notification gaps. |
| Instagram / Messenger | No stable Android notification field schema was verified in this investigation | Generic standard-fields adapter only. Treat requests, reactions, calls, promotions and hidden previews as distinct unknown cases. No assumed guaranteed text/sender/messaging style. |
| SMS/default messenger | Same NLS boundary; Android 17 SMS OTP restrictions are a separate documented change [A14] | Not a backdoor to restricted SMS permissions or OTP collection. Exclude authentication/financial channels and generic OTP notices. |

**Adapters must be parsers, not access workarounds.** Maintain source-package, version/build, Android/API, OEM, locale and fixture metadata. Prefer generic structured parsing; app-specific sender-prefix heuristics require versioned tests and low-confidence attribution. Unknown builds fall back to standard fields rather than brittle regex certainty. Do not merge accounts/profiles just because display names match.

## 5. Proposed event-driven architecture

```text
System notification callback
  -> consent / package / profile / lock-policy gate
  -> bounded typed snapshot
  -> normalized observation and item candidates
  -> notification-update diff + conservative dedup
  -> cheap local filter and optional transient frequency counters
  -> optional small classifier
  -> optional Laya decision, only after phone validation
  -> incident candidate / incremental temporal pattern
  -> encrypted expiring review record, only with retention opt-in
  -> user confirm / correct / reject
  -> encrypted evidence vault
```

Nonselected packages are rejected **before reading their extras**. Ordinary text exists only transiently in RAM and is not written to a "raw events" table, WorkManager input, logs or crash reports. Candidate-retention consent is separate from notification access. Without that consent, review is session-only and lost on process death.

### 5.1 Components and responsibilities

| Proposed component | Owns | Must not do |
|---|---|---|
| `SakshiNotificationListener` | Connection state; early policy gate; bounded snapshots; removal/ranking metadata | Disk/model work in callback; notification action execution; screen/media/database access |
| `NotificationNormalizer` | Standard fields; structured item provenance; generic fallback; missingness | Fabricate sender/time/full body; turn historic items into new messages |
| `ObservationDiffer` | Per-notification generation/state, ordered-list diff, update reasons | Equate notification keys with message IDs or remove equal repeated messages globally |
| `LocalSignalFilter` | Exclusions, generic/media-only abstention, low-cost cues and optional RAM counters | Archive all text; declare harassment from keywords/frequency alone |
| `LocalDecisionEngine` | Disabled/rules-only/classifier/Laya modes; model version; abstention | Network fallback; self-authorize new collection or retention |
| `PatternReducer` | Incremental candidate/confirmed-event time windows, conservative links and coverage | Repeated full-archive scans; cross-account identity guesses; guilt/legal conclusions |
| `CandidateInbox` | Selective encrypted short retention; quotas; review state | Unbounded automatic archive or silent full-chat backfill |
| `EvidenceVault` | User-confirmed immutable observation; separate corrections/AI findings; export preview | Overwrite source observation; export by default |
| `CoverageState` | Access/connection/pause/key/model/overflow states without content | Show "all clear" from no callbacks or claim complete coverage |

### 5.2 Callback and worker contract

1. Read an immutable policy snapshot: enabled, allowed packages/profiles, collection generation and lock policy. Reject Sakshi's own notifications and unselected sources before extras parsing.
2. Obtain minimal metadata and bounded fields; if input is malformed, record only a content-free error counter. No arbitrary Parcelable deserialization or foreign UI traversal beyond public typed message parsing.
3. `trySend` a snapshot to one bounded in-process channel, with a single consumer preserving order. Never launch an unbounded coroutine per callback. Do not conflate an entire conversation to its latest value: intermediate threatening text may vanish from a later summary.
4. Parser/differ/filter run off main. An overflow increments a coverage-gap counter and drops according to a documented policy; it is not hidden. Proposed policy: reject newest when full rather than block the UI thread; record lost observation count, not message count.
5. Small-model work is optional, serialized and budgeted. A model unavailable, unsupported-language, truncated-input or budget-exhausted result becomes `unknown/deferred`, never `benign`.
6. Recheck consent generation, source selection and lock policy before inference and before writing. Pause/revoke must prevent already-queued work from persisting. Cancel current work, clear queues/cache and release model resources; native inference may finish computationally, but its stale result must be discarded.
7. Candidate persistence is atomic and idempotent. Only after an allowed candidate is encrypted and committed may durable deferred work be scheduled. WorkManager `Data` contains opaque job IDs, never notification text, names, embeddings or file contents.
8. Use one serialized drain/recovery mechanism for deferred candidates, with a transactional job state/lease. Coalescing must not lose a candidate arriving while a worker exits; pending encrypted rows are discoverable on next connection/app open. WorkManager is deferrable, not a real-time response contract. Prefer normal constrained work; do not expedite every notification. [A10]

Process death before selective persistence loses RAM observations. A journal of every notification would reduce that loss but contradict the privacy objective. Accept and disclose the tradeoff; do not quietly introduce an all-content staging database.

### 5.3 Connection and recovery state machine

```text
OFF -> CONSENTED_WAITING -> CONNECTED
CONNECTED -> PAUSED | DISCONNECTED | KEY_UNAVAILABLE
DISCONNECTED -> CONSENTED_WAITING -> CONNECTED
Any state -> OFF when access/collection consent is revoked
```

States are product states, not guarantees from a service base class. Keep access, connection and analysis health as separate dimensions. `KEY_UNAVAILABLE` may leave the service connected while collection/retention is disabled.

- On connection, optionally perform **one** active snapshot reconciliation only if the user opted to include already-active notifications. First enable/resume defaults to new observations only; seed dedup metadata without analyzing old content. Do not ingest an entire existing inbox silently.
- A reconciled item has origin `active_snapshot`, not `live_received`. Do not count it as a new event unless conservative diff evidence supports that. Reconciliation cannot establish what happened in the gap.
- On disconnect, mark coverage unknown. Rebind only while consent and access remain valid, with bounded backoff. Never auto-open permissions screens or ask for blanket battery exemptions as a default.
- On unclean process restart, the gap start may be unknown; persist minimal last-health state so UI can report that uncertainty, not an invented exact gap.
- Removal updates selected-record lifecycle metadata and RAM dedup state. It does not erase user-confirmed evidence or infer chat deletion. Honor retention settings independently.

## 6. Normalized event schema

The canonical unit is a **notification observation** with zero or more included item representations. An incident links to selected item spans in that observation. The following is a proposed schema, not a synthetic real incident:

```yaml
schema_version: 1
observation_id: random UUID
collector_session_id: random per-start identifier
collector_sequence: increasing within session
origin: live_callback | active_snapshot | removal_callback
source:
  package_name: Android-reported posting package
  profile_scope: app-local opaque profile identifier
  app_version: known version or null
  notification_key: Android notification key
  notification_generation: app-local generation
  notification_id: integer
  notification_tag: nullable string
  channel_id: nullable string
  group_key: nullable string
  is_group_summary: boolean
  category: nullable string
clocks:
  observed_wall_ms: collector wall clock
  observed_elapsed_ms: monotonic clock within session
  notification_post_ms: system post time
  publisher_when_ms: nullable publisher display time
conversation_hint:
  shortcut_id: nullable app-scoped string
  title: nullable publisher label
  is_group: true | false | unknown
  account_hint: nullable unverified label
  link_basis: shortcut | channel | notification_scope | none
  attribution: publisher_claim | heuristic | user_confirmed | unknown
privacy:
  visibility: public | private | secret | unknown
  public_version_present: boolean
  lock_state: locked | unlocked | unknown
  content_availability: text | generic | media_only | missing | opaque
  redaction_status: suspected | unknown
items:
  - local_item_id: random UUID
    representation: messaging_style | historic | plain_text | text_line
    source_path: extras.android.messages[0].text or other actual path
    exposed_text: bounded original plain text
    text_truncated: boolean
    original_length_if_known: nullable integer
    claimed_message_time_ms: nullable publisher time
    sender_name: nullable publisher label
    sender_key: nullable app-scoped key
    sender_uri_hint: nullable publisher string, never resolved
    direction: incoming_hint | current_user_hint | unknown
    role: candidate_new | repeated_context | historic | summary | unknown
    media_mime_hint: nullable string
    media_label: nullable exposed label
    comparison_fingerprint: keyed digest
    duplicate_status: distinct_representation | probable_repeat | ambiguous
quality:
  parser_id: versioned identifier
  missing_fields: list
  coverage_gap_ids: list
  truncated_fields: list
  count_semantics: observed_representations_not_total_messages
policy:
  consent_generation: integer
  retention_mode: session_only | expiring_candidates
  retention_expires_at: nullable timestamp
analysis:
  status: not_run | inferred | abstained | deferred
  engine_id: nullable model/rules version and artifact digest
  language_hint: nullable language/script with uncertainty
  calibration_id: nullable identifier
  categories: zero or more interpreted categories
  scores: nullable task-validated scores
  evidence_anchors: item ID, source path, UTF-16 start/end offsets
  uncertainty_reasons: list
review:
  state: transient | pending | confirmed | rejected | expired
  user_correction_id: nullable separate record ID
lifecycle:
  removed_at: nullable collector time
  removal_reason: nullable Android reason
integrity:
  canonical_format_version: integer
  sha256: digest of canonical captured observation
```

### Required schema invariants

- All optional publisher fields may be absent; absence is represented, not backfilled by invented values. A missing sender/shortcut does not prevent an unattributed text candidate.
- Profile/package/account/conversation scopes remain separated. Identity linkage requires explicit user review if app account identity is missing or ambiguous.
- UTF-16 offsets anchor Kotlin/Android exposed text; inference normalization must retain a mapping to those offsets. No quote may be generated that cannot be sliced from retained observation text.
- Retain exact **selected exposed text within caps**, not full unrelated sibling-message arrays. Persisting a candidate projects only needed item fields; source paths and inclusion/omission metadata preserve provenance without serializing everyone else's messages.
- Generic titles/counts are not body text. Historic, summary and current-user hints are contextual and cannot silently increase incoming incident counts.
- Removal records contain no newly extracted body and are attached only to already-selected records where policy allows.
- Persistent metadata, sender labels, fingerprints, scores and embeddings are sensitive too. Store them inside encryption; no plaintext FTS index or content-derived unkeyed hash index.
- `redaction_status` is not a claim of reliable platform detection. No new protected-content API was established here.
- Canonical hashing proves later bytes match the capture, not source authenticity, completeness, human identity or court admissibility. Hashes of short text can expose content through guessing; never publish them indiscriminately.

## 7. Deduplication without erasing repetition

Harassment often includes repeated identical text. Dedup must suppress redundant **representations**, not erase real repeated contact.

### Layer 1: notification update equivalence

Within `(profile, package, notification key, generation)`, fingerprint analysis-relevant bounded fields with a per-install HMAC key. Exclude collector time, ranking-only changes and other fields that change without new content. Retain last fingerprint and ordered message-state fingerprints in a TTL RAM cache. Equal content-bearing snapshots are probable reposts and need no new inference. Ranking-only changes update metadata without becoming incidents.

Generation changes on observed removal/repost or explicit state reset. Missing removal callbacks mean key reuse cannot be perfectly identified. Keep uncertainty rather than claiming exact correspondence.

### Layer 2: structured item diff

Prefer app/profile-scoped conversation shortcut plus sender key, claimed timestamp, **exact exposed text**, item role and list order. Compare ordered lists as multisets with occurrence positions so two identical entries in one array remain two representations. Separate `EXTRA_HISTORIC_MESSAGES`; do not globally dedup by normalized text. When timestamp/sender is missing, use notification scope and prior sequence overlap, marking results ambiguous.

A new timestamp can distinguish equal texts, but two real messages may share coarse timestamps. A stable notification key can include multiple messages. Therefore this is an attribution heuristic, not guaranteed message identity.

### Layer 3: summary/child overlap

Prefer structured child observations for inference. Summaries normally contribute coverage metadata only. If the summary is the only exposed text-bearing surface, classify its excerpt with `summary_only` uncertainty and no asserted per-chat count. An exact overlap may attach another source observation to a candidate, not create another incident. Missing conversation scope must never merge different chats by equal text alone.

### Layer 4: storage and replay

Assign candidate/job IDs before persistence and enforce idempotent selected-record inserts. Reconnect snapshots and retried workers cannot create duplicate records for the same stored observation/job. A bounded encrypted dedup state can be kept only for selected records; do not persist a searchable fingerprint history of every notification. After RAM expiry/restart, unknown duplicates are marked and not silently counted as new-message truth.

**Acceptance fixtures:** A -> A ranking update yields one analyzed representation; `[A] -> [A,B]` yields only B as newly included; `[A,A]` preserves multiplicity; equal text with different times remains eligible; summary+child does not double a finding; removal+repost is not automatically the same message; reconnect snapshot is not a fresh-arrival claim; historic/self/remote-input context is never blamed on the other party.

## 8. Filtering, inference and incident selection

### 8.1 Cheap local stages

1. **Policy exclusion:** Reject unselected packages/profiles, paused sessions, locked-policy violations and excluded channels before body work. Exclude auth/OTP, banking, transactional, promotional, player/download and unrelated system content when identifiable. These checks are best-effort, not a guarantee of identifying every secret in selected messenger text.
2. **Structure and availability:** Tag summaries, historic/self context, generic notices and media-only observations. `Photo`, `New message`, unread count or missing preview never becomes a text-harassment classification.
3. **Transient normalization:** Bound input, Unicode-aware tokenize, language/script hints, case handling and cheap matching. Preserve original text and offset mapping for candidates. Do not strip emojis, negation or quoted content indiscriminately; they can affect interpretation.
4. **High-recall triage:** Explicit threat/coercion/stalking/contact cues, user's chosen watched conversation, and optional RAM repetition counters select inputs for review/classification. These are fallible cues, not legal tests. A watched-conversation setting selects triage context; it must not automatically save every notification from that conversation. Avoid pure abusive-word blacklists that misread friendly slang, quotation, reclaimed terms or self-expression.
5. **Abstention and privacy projection:** Unknown language, uncertain sender or clipped context is visible. Unsupported text can become a limited review suggestion under the user's policy, not a confident category. Discard noncandidate text promptly.

Filtering trades recall for battery/privacy. Evaluate missed incidents **at the gate**, not only model accuracy on already-filtered texts. No model can fix false negatives discarded upstream. Do not sample/store ordinary notifications in production for model improvement; evaluate with separately consented research data and synthetic parser fixtures.

### 8.2 Optional classifier

Start rules-only and manual review. A small Android-tested text classifier is the first optional inference stage; choose LiteRT or ONNX Runtime only after exact model/tokenizer/runtime compatibility checks, not by assuming the repo already has these dependencies.

Use labels such as `possible_threat`, `possible_coercion`, `possible_sexual_harassment`, `possible_repeated_unwanted_contact`, `other`, `insufficient_context`. Repeated contact requires multiple distinct observations and preferably a user-defined unwanted-contact context; text classification alone cannot establish it. Include per-label support spans only when actually extracted/validated, not synthetic explanations.

Measure precision/recall, false alerts per user-day, missed incidents, calibration/ECE/Brier, abstention and per-language coverage. Conversation/person/source-group-safe train/calibration/test splits are mandatory. Include English, Malayalam, Hindi, Tamil, Telugu, Kannada, Bengali, Marathi, Hinglish, Romanized Indic, slang and code mixing. Script routing alone fails for Romanized/code-mixed text. Unsupported-language scores must not be interpreted as benign.

### 8.3 Optional Laya decision: researched but not baseline-ready

The actual specified repository is **a typed decision engine, not a tiny harassment classifier and not a generative chat model**. Publisher sources describe:

| Verified publisher/source fact | Sakshi consequence |
|---|---|
| English Laya: ModernBERT-large, about 421M parameters; multilingual: mmBERT-base plus decision head, about 322M | Substantial phone storage/RAM/compute. Do not preload both in a passive listener. |
| `choice`, bounded ordinal `score`, and `noul` yes/no probability questions; fixed-option schema projection | Suitable research role: recommend `review / insufficient_context / no_supported_signal` using available excerpts, not generated narrative. |
| Multilingual architecture: bidirectional encoder plus two transformer decision layers, option-marker scoring and act/escalate head | Native port must reproduce head/question packing and decoding, not just load a generic BERT classifier. |
| Multilingual model card: ships uncalibrated; overconfidence; near-chance zero-shot typed decisions; weak language/score cases | Fine-tune and recalibrate Sakshi-specific decisions on held-out data. Claimed 100+ languages is not proof of Indic harassment detection. |
| Publisher timing around 33 ms is GPU-specific; model card includes T4 throughput | Not Android latency, power or RAM evidence. |
| Python package and ONNX exporter exist; inspected exporter uses opset 18, five inputs and logits/act_logits outputs | Android integration is plausible research work, not a verified turnkey Kotlin API. Exact tokenizer/marker layout/masks/postprocessing/calibration must match. |
| Current exporter defaults dynamic INT8 to per-tensor and documents serious choice-agreement loss, even there | Do not treat `--quantize` as an accuracy-safe mobile conversion. Recalibration alone does not repair changed class ranking. |
| README's older changelog mentions per-channel INT8; current source explicitly warns against it | Prefer current implementation over stale changelog; pin revision before evaluation. |
| Repository and multilingual model card identify Apache-2.0 | Verify pinned code/checkpoint/tokenizer/backbone assets and license notices before redistribution; not a blanket license for training data. |

Sources: [L1-L5]. Approximate lower-bound arithmetic, **not measured artifact sizes**: 322M weights are about 644 MB at FP16 or 322 MB at one byte each; 421M are about 842 MB or 421 MB respectively, in decimal units. Dynamic INT8 does not necessarily quantize embeddings/all tensors; actual files, tokenizer, activations, runtime memory and app overhead are larger/different. No Android working-set or runtime benchmark was established.

The fine-tuning documentation describes RLCD with soft targets, policy-gradient and cross-entropy terms, calibration and held-out evaluation. It reports typed-workflow benchmarks, not a harassment dataset. Teacher probability distributions are not validated harassment truth. One existing README note warns that a notebook calibration path uses training items, whereas the fine-tuning guide describes a held-out slice; verify the chosen training script rather than assuming all recipes are leakage-safe. [L1][L4]

**Proposed Laya adapter:** A narrow optional stage receives selected excerpts plus minimal confirmed context, and emits fixed choice/probability fields and model/calibration identifiers. A candidate can reach review without Laya. Laya cannot authorize collection, trigger notification actions, save all context, contact anyone or make a legal finding. An act/escalate output means "defer to user review," not automatic emergency escalation.

Enable only after: pinned artifacts/licenses; Android export parity on held-out fixtures; quantization drift analysis; per-language calibration; realistic cold/warm PSS/latency/thermal/energy measurements; acceptable gated pipeline recall. Use one checkpoint at a time and user-initiated deferred analysis first. No server inference, automatic checkpoint download at notification receipt, Python runtime assumption, NPU assumption or local LLM fallback.

### 8.4 Temporal pattern reducer

Update bounded windows incrementally per conservative conversation/profile scope. Track selected candidate/confirmed observation frequency, category transitions, repeated observed contact after a **user-entered** boundary, and possible escalation. Distinguish observations from interpretations.

- **Observed:** Retained excerpt and available sender/time claims.
- **Inferred:** Classifier suggests possible coercion from that excerpt.
- **Pattern:** Several independently retained representations support a repetition suggestion, with count/identity uncertainty shown.
- **Unknown:** Complete conversation, sender identity, unseen events or legality.

Privacy-first defaults retain only selected candidates/confirmed events. This misses benign-looking buildup and underestimates conversation frequency. Optional RAM-only per-conversation buckets can assist current-session triage without text retention; these counters remain sensitive and expire/reset. Persisting longer-term metadata counters requires a separate disclosed opt-in and encryption. No statement like "20 messages received" follows from 20 callbacks.

Example safe wording: "Three retained notification excerpts, observed across two days, may show repeated pressure. Sender labels and conversation linkage need review; notification coverage is incomplete." Not "This person is stalking you" or a safety guarantee.

## 9. Storage, retention and security

### 9.1 Three storage classes

| Class | Default handling | Consequence |
|---|---|---|
| Noncandidate notifications | Bounded RAM only; promptly discard; no durable content/index/fingerprint history | Lost on process death; cannot retrospectively find missed candidates. |
| Candidate inbox | Session-only unless user separately enables selective encrypted retention; proposed 24-hour expiry, 100-record/2-MiB cap | Only selected excerpts and needed provenance saved; not all sibling messages or summaries. |
| Confirmed evidence | User explicitly saves, assigns incident and chooses retention; encrypted immutable observation plus separate annotations | Remains until chosen expiry/deletion; source-app cancellation does not silently destroy preserved evidence. |

All numeric policies are proposed starting values requiring validation and user-safety review. At cap, stop additional persistence with a visible health warning, or apply a clearly chosen expiry policy; never silently evict confirmed evidence. Reads/exports exclude expired records immediately. Physical cleanup is opportunistic/deferred, so 24 hours is a logical TTL, not an exact guarantee that flash bytes disappear at that moment. If exact cryptographic expiry is required, it needs a separate reviewed key-lifecycle design.

Candidate state: `pending -> confirmed | rejected | expired`. Confirmation creates a vault record atomically and avoids a second persistent candidate copy. Rejection/expiry remove candidate content and its analysis/index/job references. Retain only content-free aggregate diagnostics where consent permits. Deletion semantics must include journals, caches, derivatives and job references, without promising physical secure erase on flash.

### 9.2 Cryptography and background-access tradeoff

Use app-private credential-encrypted/no-backup storage and AES-GCM authenticated encryption with a fresh unique nonce per operation, versioned envelopes, and authenticated record ID/schema/key-version metadata. Keystore-backed keys must never be logged or exported. Encrypt **metadata and indexes**, not just body strings. Room/SQLite alone is not encryption; any record-envelope approach must prevent plaintext columns/FTS/WAL from leaking labels, excerpts or conversation IDs. [A11][A12]

A SHA-256 over versioned canonical captured fields can support integrity checks. AEAD protects local record integrity; a digest does not prove external authenticity, sender identity or legal admissibility. Preserve selected source observations separately from normalization, inference and user correction. A bounded observation is not the original messenger message.

**Choose an honest key mode:**

- **Default strict session mode:** Authentication-gated vault key, unlocked/review session only; if key unavailable, do not queue plaintext for disk and do not prompt biometrics from a background callback. Locked-policy observation is dropped. Most private default, lower passive coverage.
- **Explicit background candidate mode:** Separate non-auth-per-operation Keystore ingress key encrypts only expiring selected candidates after consent. Confirmed vault uses stronger review access controls. This supports passive retention but permits the authorized app process to decrypt ingress records; a UI biometric gate alone does not cryptographically protect them from app compromise. Disclose that tradeoff. Default lock-screen processing policy still applies unless separately changed.

Authentication-required symmetric keys cannot magically write while their authorization is unavailable. Public-key write-only intake with auth-gated private-key review is a possible later design, not an implicit property of AES-GCM/Keystore. Avoid adding that complexity to the MVP without security review. Keystore reduces key extraction risk but cannot protect plaintext/inference from an already-compromised app/OS. [A11]

Disable/exclude sensitive data from cloud backups **and device transfers**. `allowBackup=false` alone is not sufficient on every OEM targeting Android 12+. Apply and test appropriate backup/data-extraction exclusions, including supported cross-platform transfer rules, plus no-backup placement. Lost/deleted/inaccessible keys must yield `unavailable`, never plaintext fallback or fabricated empty history. Recovery/export requires explicit design and consent; keys are not assumed to migrate. [A12]

For an offline MVP, bundle validated rules/model assets and omit `INTERNET` from the release build if dependencies permit. If later model provisioning requires network, isolate that path, verify pinned artifacts/digests and audit SDK telemetry; never include notification content in requests. Local inference does not automatically make a network-capable SDK private.

## 10. User controls and user-safety design

Before Settings, disclose: broad OS access; selected-app processing; RAM text analysis; selective storage mode; coverage limits; no cloud default; private notification differences; key/retention tradeoffs. Require affirmative choice, not a preselected "enable protection" switch. Notification access does not imply permission to retain evidence. [P4]

Required controls:

- Off by default; manual/import workflows remain fully usable.
- App/profile allowlist; optional channel exclusions and conservative conversation selection when a stable hint exists. Explain weak identity/account linkage.
- Pause now, resume policy, and a separate system-access revoke shortcut. No automatic replay during pause/resume.
- Session-only versus expiring candidate retention; retention/quota settings; clear pending-data management.
- Lock-screen processing off by default; separate option for available previews; no pressure to weaken the source app's privacy settings.
- Rules-only/local model mode, supported-language disclosure, inference budget and no-cloud guarantee for the chosen build.
- Review: exact excerpt, source/path/time claims, missingness, interpreted label, why selected, confidence limitations; confirm/edit/reject.
- Corrections are separate user assertions; do not rewrite source names, clocks or text.
- Neutral optional review notifications with no sender/body/category on the lock screen. Do not make harassment content visible through Sakshi itself. Respect Sakshi's posting permission and quiet hours.
- Never reply, dismiss, mark read, snooze, open source conversations, contact alleged senders, report to authorities, or share an incident automatically.
- Explicit reviewed export with redaction preview; no automatic attachments, unrelated context, fingerprints or personal metadata. Other notification listeners may see Sakshi's own alert, so keep it generic even when unlocked.
- Access/analysis health shows `connected`, `paused`, `unavailable`, `deferred` or `coverage unknown`, not a green "you are safe." Surface known gaps without collecting all notifications to prove uptime.
- Consider coercive access to the phone, shared-device risks and unsafe reminders in usability testing. No disguised covert-monitoring mode.

## 11. Battery, memory and scheduling strategy

NLS is system-bound and callback-driven. It does not require constant screen polling, microphone capture, database scanning or an always-on model loop. A foreground service is not the default mechanism for maintaining the listener; do not assume an FGS or exemption cures OEM/source delivery problems. [A1]

**Proposed initial budgets, not measurements or platform limits:**

| Resource | Starting policy | Failure behavior |
|---|---|---|
| Callback | Target p95 under 5 ms on reference phone | Measure; move expensive parsing/tokenizer/crypto/I/O off main. |
| Snapshot | Up to 25 current + 25 historic entries; 2,048 UTF-16 code units/field, 8,192 total text units; no bitmaps/media/actions | Preserve cap flags; truncated/omitted context cannot earn a complete-content claim. These are Sakshi limits, not guaranteed Android list sizes. |
| Queue | 64 bounded snapshots, roughly 2 MiB soft byte budget including overhead monitoring | Nonblocking reject newest + content-free overflow gap; no unbounded growth. |
| Dedup state | 256 notification scopes, 30-minute RAM TTL | Eviction/restart introduces duplicate uncertainty; not evidence loss concealed as certainty. |
| Burst handling | Brief consumer-side batch, target up to 500 ms added wait; ordered processing | Do not overwrite intermediate snapshots; no guaranteed alert latency. |
| Optional small classifier | Single instance/one inference at a time; proposed 10/minute budget | Budget exhaustion produces deferred/unknown candidate state; rules-only continues. |
| Laya | Disabled for live passive path until validation; user-started candidate analysis first | Skip rather than auto-download, overload device or call cloud. |
| Model residency | Load on demand, release after short idle/trim-memory; proposed 60-second idle timeout | Avoid load/unload thrash in bursts; measure cold-start cost. |
| Deferred work | Coalesce encrypted candidate IDs; normal WorkManager battery/storage constraints | Work may be delayed/stopped; maintain retry state, not real-time claims. |

No exact alarms, custom permanent wake locks, periodic active-notification polling, hot LLM, always-running OCR or battery-optimization exemption by default. Charging/battery/thermal state gates optional heavier analysis; fast rules need no network. Model artifact download, if added, is a separate explicit provisioning workflow, not notification-triggered inference behavior. [A10]

**Measure the full path:** idle baseline versus listener-only versus filtered/classifier/Laya workload; callback and enqueue p50/p95/p99; observed-to-candidate time; dropped snapshots; process PSS/native heap; cold/warm startup; model reloads; CPU time/wakeups; thermal/battery effects over representative idle/day/burst tests. Use identical synthetic workload and reference hardware/settings; compare source-app delivery separately. Do not extrapolate desktop milliseconds or a few-minute battery percentage into a daily phone-power guarantee.

## 12. Failure-mode register

| Failure | What must be visible / preserved | Correct response |
|---|---|---|
| Source never posts / channel blocked / foreground suppression | No claim of total source traffic | Unknown coverage; manual/share import fallback. |
| Hidden preview / locked chat / generic notice / opaque content | Availability, not fabricated text | Abstain; no probing or settings weakening. |
| Android sensitive redaction | Suspected/generic status when identifiable | Drop from threat inference; do not circumvent. |
| Device locked under strict policy | Collection-policy gap, no content | Skip private previews; explain reduced coverage. |
| Access revoked / disconnect / force-stop / reboot | Access and connection separately; possibly unknown gap start | Stop, cancel stale work; reconnect only normally with consent. |
| Private/work profile unavailable | Namespace and policy boundary | Unsupported coverage, not cross-profile scraping. |
| Summary/repost/reaction/call duplication | Item roles, duplicate uncertainty | No fake message totals or escalation from callback count. |
| Sender collision / ambiguous accounts | Attribution quality and user assertions | No cross-chat/person merge without review. |
| Clock jump / delayed post | Multiple clocks, session, claimed time | Durations use appropriate monotonic scope; uncertain chronology stays uncertain. |
| Truncation / parser change / malformed extras | Parser ID/caps/missingness; no sensitive logs | Bound work; generic fallback or abstain; validate new fixtures. |
| Queue flood / adversarial notification spam | Dropped-observation counter and health state | Per-source quotas/bounded queue; no hidden unbounded archive. |
| Unsupported language / slang / quote / missing context | Abstention and category uncertainty | Review or discard by explicit policy, not confident benign result. |
| Model absent / OOM / thermal/quota limit | Engine status and deferred work | Rules-only/manual review; no remote fallback. |
| False positive | Exact excerpt and user correction/rejection | No automatic action; measure alert burden. |
| False negative / missed buildup | Coverage and filter/model limitations | Do not show "all clear"; improve offline evaluation without background data hoarding. |
| Key unavailable/lost / storage full / corrupted ciphertext | Record unavailable vs none; pending-save failure | Fail closed; no plaintext fallback; no silent overwrite of confirmed evidence. |
| Expired/rejected candidate / stale WorkManager job | Idempotent lifecycle and policy generation | Worker drops stale IDs; cannot resurrect removed data. |
| Backup / telemetry / export leakage | Build/data-flow review, network tests | Block defaults; require explicit export/provisioning design. |
| Device/app compromise | Threat-model limitation | Keystore is not complete protection; user-safety review and minimal exposure. |

## 13. Validation plan and acceptance criteria

Platform behavior remains open until a **real Android prototype** passes these tests. Synthetic tests establish parser/service behavior only, not the actual schema of a production messenger.

### Stage A: controlled notification producer

Build a minimal separate test publisher and NLS collector using synthetic text only. Publish standard text, BigText, InboxStyle, MessagingStyle current/historic entries, identical repeated messages, same-ID updates, child/summary pairs, custom layouts with missing extras, media-only labels, null senders, remote-input history, privacy visibility/public versions, cancellations and malformed/oversized extras. Synthetic artifact provenance must be explicit.

Verify actual collector output against known producer inputs; log only synthetic IDs and content-free metrics. Test connect/revoke/pause during queued/native work, snapshots, process death, force-stop, reboot and cleanup/retry races. Do not read actual personal notifications just to exercise the listener.

### Stage B: consented app fixtures

Test account conversations controlled by consenting participants, with benign synthetic examples and non-graphic test threat/coercion examples clearly marked synthetic. Test WhatsApp, Signal, Telegram and selected Meta apps on specific installed versions. Record Android/API/QPR, target SDK, OEM, app build, locale, profile, settings and fixture IDs, not personal identifiers.

Cross-product cases: screen unlocked/locked, app/chat lock, previews on/off, source app foreground/background, muted chat vs silent channel vs disabled channel, DND, notification permission, batch bursts, account variants, reactions/calls/media, network delay, Android 15 sensitive redaction, reconnect after missed events and device battery saver. Use benign dummy OTP fixtures, never actual login codes. Compare callback observations to test sender logs without claiming that notification count equals message count.

Run at least API 30, 33, 35, 36 and current deployment API 37/QPR where devices are available, plus at least one Pixel/AOSP-like and one relevant OEM. Android 10 low-RAM/work-profile restrictions can be verified on supported test hardware when that audience is targeted. An emulator is useful but does not certify OEM background behavior.

### Stage C: privacy and security acceptance

- Zero retained content/labels/fingerprints from nonselected sources; verify package gating precedes extras extraction.
- Default session mode has no notification body in files, preferences, SQLite/WAL, WorkManager input, logs, crash dumps/analytics payloads or backups.
- Expiring mode stores only selected candidates and necessary projected fields; no full message-array sibling leakage.
- Pause/revoke races cannot create later candidate/vault writes; expired/rejected data cannot return via deferred jobs.
- No notification actions, URI opens, screen/media/database capture or outbound evidence network requests.
- AES-GCM tampering fails closed; keys are unavailable when expected; metadata and indexes are not readable in plaintext; key loss is not "no incidents."
- Cloud/device/cross-platform transfer tests confirm exclusions on supported OEMs. Export preview matches actual sanitized output.
- Lock-screen Sakshi alerts contain no sender/body/harassment category; all notification-access disclosures are accurate.

### Stage D: semantic and inference acceptance

- Structured extraction produces exact field-path/UTF-16 anchors; all quotes slice from preserved selected text.
- Dedup fixtures from section 7 pass, including equal repeated texts and summary overlap.
- Removal never becomes source-message deletion, historic/self context never becomes incoming blame, metadata-only media never yields invented OCR/STT.
- Gate-level recall and false negatives are measured on unseen conversation groups; model accuracy is measured independently and end-to-end.
- Publish per-language/category precision/recall, calibration, abstention, false alerts/user-day and sample sizes; no single overall accuracy hides unsupported Malayalam or Romanized Indic behavior.
- Small classifier/Laya export parity and quantization changes are evaluated before phone enablement; pin exact model, tokenizer, runtime, questions and calibration. Synthetic fixtures are not represented as real-world evaluation.

### Stage E: battery/reliability acceptance

Measure the budgets in section 11, including queue floods and long idle sessions. Check no continuous polling/wake locks/model compute; demonstrate source suppression/disconnect gaps explicitly. A successful synthetic callback demo is not an uptime or emergency-monitoring guarantee.

## 14. Implementation order

1. **Foundation:** Consent/access state, read-only NLS, app/profile gating, bounded standard-field snapshots, session-only review and manual import fallback.
2. **Semantics:** Versioned parser/schema, distinct clocks, ordered dedup, summaries/self/historic roles and coverage UI, validated with synthetic producer then real apps.
3. **Selective preservation:** Keystore/encrypted candidate inbox with separate opt-in, expiry/quota, race-safe pause/revoke, user-confirmed vault and explicit export.
4. **Cheap intelligence:** Transparent high-recall local rules and incremental selected-evidence patterns; measure gate errors and alert burden.
5. **Optional small model:** Leakage-safe data, per-language calibration, Android parity/resource tests and limited execution budgets.
6. **Optional Laya:** Only if it demonstrably improves user-reviewed decisions enough to justify storage/RAM/energy and integration effort. Keep disabled otherwise.

**Concrete MVP:** One or two consenting source apps, API 30+, structured/generic text only, no notification bitmaps or attachments, no cloud/network permission if feasible, session review plus explicitly selected encrypted candidate retention, rules-only suggestions and user confirmation. A complete passive archive, universal app adapter suite, LLM summaries and live Laya inference are deliberately outside the minimum build.

## 15. Primary-source register and research limitations

Sources were consulted on 2 October 2026. Android references contain large navigation bodies; the relevant API/member sections were inspected, not merely page titles. Mutable `main` model/code pages describe the inspected state, not immutable guarantees. Pin code/checkpoint revisions and source digests in implementation evaluation.

| ID | Source | Evidence and access quality |
|---|---|---|
| A1 | [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService) | Full relevant sections: manifest, main-thread callbacks, filters, lifecycle, active snapshots, light removal, low-RAM and work profiles. |
| A2 | [Notification](https://developer.android.com/reference/android/app/Notification) | Relevant extras, grouping, visibility/public-version and `when` sections. |
| A3 | [StatusBarNotification](https://developer.android.com/reference/android/service/notification/StatusBarNotification) | Relevant key/group/profile/source/post-time contract. |
| A4 | [MessagingStyle.Message](https://developer.android.com/reference/android/app/Notification.MessagingStyle.Message) | API-level-specific public parser, sender/null-self contract, text/time and URI permissions. |
| A5 | [Conversations](https://developer.android.com/develop/ui/views/notifications/conversations) | Publisher conversation/shortcut participation, not full cross-app chat access. |
| A6 | [Android 15: all-apps changes](https://developer.android.com/about/versions/15/behavior-changes-all) | OTP listener redaction, separate screenshare protections and locked private-space behavior. |
| A7 | [Notification runtime permission](https://developer.android.com/develop/ui/views/notifications/notification-permission) | Posting authorization and exemptions; not NLS read authorization. |
| A8 | [Build a notification](https://developer.android.com/develop/ui/views/notifications/build-notification) | Publisher content, expanded styles, lock-screen presentation controls. |
| A9 | [AOSP notification history](https://source.android.com/docs/core/display/notification-history) | Optional OEM Settings implementation, default 24-hour retention and user disablement. Not an app history API. |
| A10 | [Define WorkRequests](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work) | Constraints, scheduling, expedited quotas and older-platform FGS behavior. |
| A11 | [Android Keystore](https://developer.android.com/privacy-and-security/keystore) | Nonextractable keys, authentication constraints, hardware variation and app/OS compromise limits. |
| A12 | [Auto Backup](https://developer.android.com/identity/data/autobackup) | Default backup, no-backup storage, OEM `allowBackup` caveat and versioned transfer rules. |
| A13 | [NotificationManager](https://developer.android.com/reference/android/app/NotificationManager) and [Settings](https://developer.android.com/reference/android/provider/Settings) | Relevant members independently inspected: access-status check API 27+ for a service belonging to the caller; Settings entry API 22+, with documented possibility of no matching Activity. |
| A14 | [Android 17: all-apps changes](https://developer.android.com/about/versions/17/behavior-changes-all) | Inspected SMS/WebOTP restriction text; not proof of an E2EE notification-wide ban. |
| P1 | [Meta: WhatsApp Chat Lock](https://about.fb.com/news/2023/05/whatsapp-chat-lock/) | Full primary article inspected; hides chat contents in notifications. WhatsApp Android FAQ fetch was empty, so exact fields remain unverified. |
| P2 | [Signal notification options](https://support.signal.org/hc/en-us/articles/360043273491-In-App-Notification-Options) and [notification troubleshooting](https://support.signal.org/hc/en-us/articles/360007318711-Troubleshooting-Notifications) | Indexed official excerpts inspected. Full-page fetches returned 403; Android/iOS/macOS distinctions must be maintained. No current installed-build certification. |
| P3 | [Telegram peerNotifySettings](https://core.telegram.org/constructor/peerNotifySettings) | Full relevant preview/mute settings schema; not an Android Notification extras schema. |
| P4 | [Google Play User Data](https://support.google.com/googleplay/android-developer/answer/10144311) | Full policy sections inspected: limited use, third-party/AI responsibility, prominent in-app disclosure and affirmative consent. Not a Play-approval determination. |
| L1 | [Specified Laya repository](https://github.com/NandhaKishorM/laya) | README architecture/checkpoints/types/runtime/training/calibration notes; project license identified. |
| L2 | [Laya schema-driven decisions](https://nandhakishorm.github.io/laya/structured/) | Supported fixed-choice/bounded-score/boolean subset; no arbitrary free-text output. |
| L3 | [Multilingual checkpoint model card](https://huggingface.co/convaiinnovations/laya-multilingual/raw/main/README.md) | Full card inspected: architecture, license declaration, GPU benchmarks and explicit calibration/language/zero-shot limitations. Publisher measurements, not Sakshi results. |
| L4 | [Laya fine-tuning guide](https://nandhakishorm.github.io/laya/finetune/) | RLCD/soft targets, calibration/evaluation workflow. Harassment task suitability unestablished. |
| L5 | [Current ONNX export source](https://github.com/NandhaKishorM/laya/blob/main/scripts/export_onnx.py) | Actual source inspected: opset/input/output contract, per-tensor default, documented per-channel collapse and remaining quantization accuracy loss. No Android parity run. |

### Remaining open questions

- Real fields exposed by each chosen app/version under its current privacy modes and OEM builds.
- Lock-state transition race behavior and platform/QPR redaction details for an ordinary listener.
- Channel/account/conversation-hint stability; callback coverage when foreground, muted, bursty or reconnecting.
- Whether the default privacy/retention policy yields acceptable end-to-end recall without collecting unnecessary content.
- Which small classifier, if any, meets multilingual task/phone budgets; whether Laya adds measurable value.
- Exact key-mode UX and evidence recovery requirements; acceptable background-ingress threat model.
- Play/distribution eligibility and jurisdiction-specific legal/user-safety review for the actual data flow.

Resolve platform/app questions with consented device fixtures, not private-data extraction or assumptions. Resolve inference questions with held-out evaluation and Android measurements, not desktop claims. The implementation consequence is a bounded, optional, auditable observation layer with explicit unknowns and user-controlled evidence preservation.
