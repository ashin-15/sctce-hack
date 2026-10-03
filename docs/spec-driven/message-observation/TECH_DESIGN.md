# Technical design

## Verified baseline

- `android/acquisition/notifications`: system-bound listener, source allowlist before extras access, bounded snapshot queue, deduplication, memory inbox and coverage intervals already implemented.
- `EvidenceImporter.commitNotificationExcerpt` and `NotificationClaims`: existing encrypted preservation and separate source/collector clocks.
- Notification module was absent from app dependencies/navigation/lifecycle.
- AgentHita service uses window events, `rootInActiveWindow`, app-specific resource IDs, conversation heuristics and deduplication. It also has debug logs, remote config/telemetry and guardian alerts that are unsuitable for direct Sakshi integration. XML lists WhatsApp/Business, Instagram and several messaging packages; it is not a general chat-history API.
- Stitch exports exist locally under `stitch_design_specification_project`. Stitch MCP is not connected in this session. The posted credential is not written into any deliverable.

## Contracts and lifetimes

Notification screen maps candidate handoff to existing NotificationClaims field by field. It removes a candidate only after a successful import. No restore-on-failure path can repopulate an inbox after lock. Summary-only notification text is preserved without analysis.

App lifetime `ObservationLifecycle` coordinates unlocked sessions, explicit background opt-in, neutral cue alerts and SCREEN_OFF. Background observation is memory-only and independent of the locked vault. Default is off. SCREEN_OFF clears both collectors and locks the vault; a process restart never restores drafts. No foreground-service survival guarantee. Notifications arriving while the lane is inactive are not historical recoveries. Notification listener access and POST_NOTIFICATIONS are independent grants.

`acquisition/accessibility`: service disabled by default, enabled only through the in-app disclosure, then manually granted by Android settings. `startSession` requires grant, connected service, unlocked device and explicit packages. Five-minute timeout, 40 snapshots, 300 nodes, depth 20 and bounded text. Reads only visible non-password/non-editable nodes; API34 sensitive nodes and recognisable ephemeral-mode markers stop and clear. Nodes and inaccessible content are never reconstructed. Markers are heuristic, not proof of exhaustive mode exclusion. Stop, expiry and return retain bounded drafts for authenticated review; screen lock/interruption/disconnection/revocation clears. Vault closes normally when switching apps.

A saved visible snapshot uses `visible_text_snapshot`, `user_mediated`, `accessibility_visible_text`, exact UTF-8 text and capture claims JSON. Analysis always builds one extraction-uncertain event with unknown identity/direction/time, even if text resembles a chat export. Exact cue spans remain evidence-linked. Notification and Accessibility observations are not merged automatically: equal text does not prove the same real message.

Cue alerts use existing unreviewed deterministic phrase lists, local CPU processing, no confidence or danger score, neutral content, secret notification visibility, a 30-second cooldown and explicit POST_NOTIFICATIONS permission. Sensitive content is never inserted into a notification. Model provisioning is unchanged. No automatic export or external contact.

## Primary sources checked 3 October 2026

- [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService): bound system service, connection before active notification queries, callbacks are observation, not complete history.
- [Android 15 privacy changes](https://developer.android.com/about/versions/15/behavior-changes-all): OTP notification redaction for untrusted listeners. No exemption or bypass requested.
- [AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService): retrieve-window-content capability and explicit service enablement.
- [Sensitive accessibility data](https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo#isAccessibilityDataSensitive()): API34 node sensitivity signal.
- [Google Play Accessibility API policy](https://support.google.com/googleplay/android-developer/answer/10964491): non-accessibility-tool disclosure, affirmative consent, declaration/review and automation restrictions. OS consent alone does not establish Play approval.

## Compatibility and release limits

Notification previews vary by app configuration, muting/group summaries, lock/privacy settings, profile and Android version. WhatsApp/Business, Instagram, Telegram and Signal are selected package candidates, not validated capture guarantees. User-shared exports/images/text remain the reliable explicit import fallback. Accessibility generic snapshots may include UI labels and omit nodes. Ephemeral/protected paths must remain excluded; validate uncertain behaviour with synthetic chat fixtures and real Android devices, never by bypassing controls.

Narrow policy exception permits the new service only in its dedicated module. Manifest exported component allowlist expands only for the two OS-bound services, both require system binder permissions. No network permissions. No evidence migration: acquisition kinds are stored as strings; schema event source remains selected_text.
