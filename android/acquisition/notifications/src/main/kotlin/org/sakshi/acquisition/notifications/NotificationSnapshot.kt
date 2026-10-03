package org.sakshi.acquisition.notifications

/** Where a snapshot came from. [ACTIVE_SNAPSHOT] is a re-delivery of what is already shown, not a new arrival. */
public enum class SnapshotOrigin { LIVE, ACTIVE_SNAPSHOT, REMOVAL }

/** The lock screen visibility the source app declared. Secret notifications are never read for text. */
public enum class NotificationVisibility { PUBLIC, PRIVATE, SECRET, UNKNOWN }

/** Fields whose text was cut by a bound, or whose entries were left out. Cut text is never presented as complete. */
public enum class SnapshotField { TITLE, TEXT, BIG_TEXT, SUB_TEXT, CONVERSATION_TITLE, MESSAGES_OMITTED }

/**
 * One MessagingStyle message as the source app published it. [senderLabel] is a label, not an identity.
 * [fromCurrentUserHint] is true when the app gave no sender, which Android documents as the current user; such a
 * message is never attributed to the other party.
 */
public data class SnapshotMessage(
    val senderLabel: String?,
    val text: String,
    val timestampMs: Long?,
    val fromCurrentUserHint: Boolean,
    val textTruncated: Boolean,
) {
    init {
        require(text.length <= NotificationBounds.MAX_FIELD_CHARS) { "Message text exceeds the field bound" }
        require((senderLabel?.length ?: 0) <= NotificationBounds.MAX_FIELD_CHARS) { "Sender label exceeds the field bound" }
    }
}

/**
 * The bounded, typed copy of what a notification callback may take. Holds plain strings and numbers only: no
 * Bundle, no icon, no action, no intent, no remote view and no URI. See [NotificationBounds] for every bound.
 *
 * Times are kept apart on purpose: [postTimeMs] is the system posting time, [whenMs] is the publisher's display
 * time, each message carries the publisher's own claim, and the collector times are [observedWallMs] and
 * [elapsedRealtimeMs]. They are never compared with one another (megaplan 17.2).
 */
public data class NotificationSnapshot(
    val origin: SnapshotOrigin,
    val packageName: String,
    val notificationKey: String,
    val groupKey: String?,
    val isGroupSummary: Boolean,
    val category: String?,
    val shortcutId: String?,
    val postTimeMs: Long,
    val whenMs: Long?,
    val isGroupConversation: Boolean?,
    val conversationTitle: String?,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val subText: String?,
    val messages: List<SnapshotMessage>,
    val historicMessages: List<SnapshotMessage>,
    val truncatedFields: Set<SnapshotField>,
    val visibility: NotificationVisibility,
    val removalReasonCode: Int?,
    val deviceLocked: Boolean,
    val collectorSessionId: String,
    val observedWallMs: Long,
    val elapsedRealtimeMs: Long,
) {
    init {
        require(messages.size <= NotificationBounds.MAX_MESSAGES) { "Too many messages in a snapshot" }
        require(historicMessages.size <= NotificationBounds.MAX_HISTORIC_MESSAGES) { "Too many historic messages" }
        listOf(title, text, bigText, subText, conversationTitle).forEach {
            require((it?.length ?: 0) <= NotificationBounds.MAX_FIELD_CHARS) { "A text field exceeds the field bound" }
        }
        require(origin != SnapshotOrigin.REMOVAL || removalReasonCode != null) { "A removal needs its platform reason code" }
    }
}

/** A clipped text value and whether anything was cut. */
public data class ClippedText(val value: String, val truncated: Boolean)

/** The remaining total text allowance of one snapshot. One instance is used for all fields of one notification. */
public class TextBudget(private var remaining: Int = NotificationBounds.MAX_TOTAL_TEXT_CHARS) {
    /**
     * Copies [value] as plain text, cut to the field bound and to what remains of the total allowance. A cut never
     * splits a surrogate pair. Returns null for a null or empty input.
     */
    public fun clip(value: CharSequence?): ClippedText? {
        if (value.isNullOrEmpty()) return null
        val limit = minOf(NotificationBounds.MAX_FIELD_CHARS, remaining)
        val plain = value.toString()
        if (plain.length <= limit) {
            remaining -= plain.length
            return ClippedText(plain, truncated = false)
        }
        var end = limit
        if (end > 0 && Character.isHighSurrogate(plain[end - 1])) end -= 1
        remaining -= end
        return ClippedText(plain.substring(0, end), truncated = true)
    }

    /** True when no text allowance is left. */
    public val isExhausted: Boolean get() = remaining <= 0
}

/** Bounding of a message list. Pure so that the cap behaviour is testable without Android. */
public object SnapshotBounds {
    /**
     * Keeps the newest [limit] of [raw] (the platform lists oldest first), clips each text against [budget] starting
     * with the newest, and returns them oldest first again. The flag says that entries were left out.
     */
    public fun boundMessages(raw: List<SnapshotMessage>, limit: Int, budget: TextBudget): Pair<List<SnapshotMessage>, Boolean> {
        val newest = raw.takeLast(limit)
        var omitted = raw.size > newest.size
        val kept = ArrayList<SnapshotMessage>(newest.size)
        for (message in newest.asReversed()) {
            val clipped = budget.clip(message.text)
            if (clipped == null) {
                omitted = true
                continue
            }
            val sender = budget.clip(message.senderLabel)?.value
            kept += message.copy(
                text = clipped.value,
                senderLabel = sender,
                textTruncated = message.textTruncated || clipped.truncated,
            )
        }
        return kept.asReversed() to omitted
    }
}
