package org.sakshi.acquisition.notifications

/** What the collector knows when it asks a [NotificationSource] to read its text. None of it comes from the notification. */
public data class SnapshotRequest(
    val origin: SnapshotOrigin,
    val deviceLocked: Boolean,
    val collectorSessionId: String,
    val observedWallMs: Long,
    val elapsedRealtimeMs: Long,
)

/**
 * One platform notification as the intake sees it. The members before [read] are cheap platform fields that are not
 * part of the notification extras, so the allowlist and the lock gate can decide on them alone. [read] is the only
 * call that touches extras, and the intake calls it only after every gate passed.
 */
public interface NotificationSource {
    public val packageName: String
    public val notificationKey: String
    public val groupKey: String?
    public val postTimeMs: Long

    /** Builds the bounded snapshot. Must not throw for malformed extras: it returns what could be read. */
    public fun read(request: SnapshotRequest): NotificationSnapshot
}
