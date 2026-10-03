package org.sakshi.acquisition.notifications

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Every string in these tests is obviously synthetic. */
const val APP: String = "synthetic.chat.app"
const val SESSION: String = "synthetic-session"
const val T0: Long = 1_800_000_000_000L

/** A message with a named sender. */
fun msg(text: String, sender: String? = "Synthetic Sender One", time: Long? = T0, truncated: Boolean = false): SnapshotMessage =
    SnapshotMessage(
        senderLabel = sender,
        text = text,
        timestampMs = time,
        fromCurrentUserHint = sender == null,
        textTruncated = truncated,
    )

/** A snapshot with every field defaulted, so a test names only what it cares about. */
fun snap(
    key: String = "key-1",
    packageName: String = APP,
    origin: SnapshotOrigin = SnapshotOrigin.LIVE,
    messages: List<SnapshotMessage> = emptyList(),
    historic: List<SnapshotMessage> = emptyList(),
    title: String? = null,
    text: String? = null,
    bigText: String? = null,
    shortcut: String? = "shortcut-1",
    groupKey: String? = null,
    summary: Boolean = false,
    whenMs: Long? = null,
    visibility: NotificationVisibility = NotificationVisibility.PRIVATE,
    truncatedFields: Set<SnapshotField> = emptySet(),
    removalReason: Int? = null,
    locked: Boolean = false,
    elapsed: Long = 1_000L,
    wall: Long = T0 + 5_000L,
): NotificationSnapshot = NotificationSnapshot(
    origin = origin,
    packageName = packageName,
    notificationKey = key,
    groupKey = groupKey,
    isGroupSummary = summary,
    category = "msg",
    shortcutId = shortcut,
    postTimeMs = wall - 1,
    whenMs = whenMs,
    isGroupConversation = false,
    conversationTitle = null,
    title = title,
    text = text,
    bigText = bigText,
    subText = null,
    messages = messages,
    historicMessages = historic,
    truncatedFields = truncatedFields,
    visibility = visibility,
    removalReasonCode = removalReason,
    deviceLocked = locked,
    collectorSessionId = SESSION,
    observedWallMs = wall,
    elapsedRealtimeMs = elapsed,
)

/** A differ whose candidate ids are predictable: c1, c2, ... */
fun differ(policy: ActiveSnapshotPolicy = ActiveSnapshotPolicy.SEED_ONLY): ObservationDiffer {
    var n = 0
    return ObservationDiffer(activePolicy = { policy }, newId = { "c${++n}" })
}

/** The texts of the new candidates of a result. */
fun DiffResult.texts(): List<String> = newCandidates.map { it.candidate.message.text }

/** A clock a test can move. */
class MutableClock(var now: Instant = Instant.parse("2026-10-03T10:00:00Z")) : Clock() {
    override fun getZone() = ZoneOffset.UTC

    override fun withZone(zone: java.time.ZoneId?): Clock = this

    override fun instant(): Instant = now
}
