package org.sakshi.acquisition.notifications

/**
 * What the platform said when a notification went away. This is lifecycle metadata about the notification only. It
 * says nothing about the source message: a removal can mean a dismissal, a read on another device, a replaced
 * summary or an app cancel, and it is never recorded or shown as "message deleted".
 */
public data class RemovalLifecycle(val platformReasonCode: Int, val observedWallMs: Long)

/** One candidate in the inbox. It becomes evidence only through an explicit user action. */
public data class ObservedCandidate(
    val id: String,
    val message: ObservedMessage,
    /** Other observations, such as a child notification, that carried the same text and were attached instead of counted again. */
    val corroboratingObservations: Int = 0,
    val removal: RemovalLifecycle? = null,
) {
    /** Rough memory use in bytes for the inbox bound: UTF-16 characters of every string plus a fixed overhead. */
    internal fun approximateBytes(): Int {
        val chars = listOfNotNull(
            id,
            message.text,
            message.senderLabel,
            message.sourceAppClaim,
            message.conversation.notificationKey,
            message.conversation.scopeId,
            message.conversation.groupKey,
            message.conversation.shortcutId,
            message.conversation.conversationTitle,
            message.collector.sessionId,
        ).sumOf { it.length }
        return chars * 2 + OVERHEAD_BYTES
    }

    private companion object {
        const val OVERHEAD_BYTES = 256
    }
}
