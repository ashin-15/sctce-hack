package org.sakshi.acquisition.notifications

import org.sakshi.core.model.IdentityBasis

/**
 * How complete the observed text is. [SUMMARY_ONLY] means the only text-bearing surface was a group summary; it is a
 * lower-confidence excerpt and never a statement about how many messages arrived.
 */
public enum class ObservedTextStatus { COMPLETE, TRUNCATED, SUMMARY_ONLY }

/**
 * Direction as far as the notification shows it. A message with a named sender is an incoming hint. Nothing else is
 * claimed: this lane makes no statement about outgoing messages (coverage of outgoing messages is always unknown).
 */
public enum class ObservedDirection { INCOMING_HINT, UNKNOWN }

/** Where the sender label came from. A notification title is only a weak fallback and is not always a sender. */
public enum class SenderLabelSource { MESSAGE_SENDER, NOTIFICATION_TITLE, NONE }

/** Which publisher field a source claim time came from. */
public enum class SourceClaimTimeBasis { MESSAGE_TIMESTAMP, NOTIFICATION_WHEN, NONE }

/** The time the source app claims for the content. It is a claim, never verified, and not comparable with collector time. */
public data class SourceClaimTime(val epochMs: Long?, val basis: SourceClaimTimeBasis)

/** When and in which collector session Sakshi observed the notification. */
public data class CollectorTime(val wallMs: Long, val elapsedRealtimeMs: Long, val sessionId: String)

/**
 * The conversation as the notification hints at it. All parts are app-scoped claims. [scopeId] only separates
 * de-duplication state and is not a chat identity.
 */
public data class ConversationScopeClaim(
    val packageName: String,
    val scopeId: String,
    val notificationKey: String,
    val groupKey: String?,
    val shortcutId: String?,
    val conversationTitle: String?,
    val isGroup: Boolean?,
)

/**
 * One candidate observation of message text. Every field about the source is a claim made by the publishing app.
 * The sender is an app-scoped hint ([IdentityBasis.APP_SCOPED_HINT]) and never a verified identity.
 */
public data class ObservedMessage(
    val sourceAppClaim: String,
    val conversation: ConversationScopeClaim,
    val senderLabel: String?,
    val senderLabelSource: SenderLabelSource,
    val identityBasis: IdentityBasis,
    val text: String,
    val textStatus: ObservedTextStatus,
    val direction: ObservedDirection,
    val sourceClaimTime: SourceClaimTime,
    val notificationPostTimeMs: Long,
    val collector: CollectorTime,
    val origin: SnapshotOrigin,
)

/** Why a snapshot produced no candidate. It is coverage information, never a finding. */
public enum class WithheldReason {
    REMOVAL_ONLY,
    SECRET_VISIBILITY,
    SUMMARY_COUNT_NOTICE,
    MEDIA_LABEL_ONLY,
    NO_TEXT,
}

/** The candidates of one snapshot, or the reason there are none. */
public data class NormalizationResult(val messages: List<ObservedMessage>, val withheld: WithheldReason?)
