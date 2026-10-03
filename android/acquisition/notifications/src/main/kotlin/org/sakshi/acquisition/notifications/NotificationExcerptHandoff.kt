package org.sakshi.acquisition.notifications

import org.sakshi.core.model.Direction
import org.sakshi.core.model.IdentityBasis
import org.sakshi.core.model.OutgoingCoverage
import org.sakshi.core.model.SourceKind
import org.sakshi.core.model.TextStatus

/**
 * The text and provenance a caller hands to the importer after the person chose to keep a candidate. This module does
 * not call the importer. Everything about the source is a claim by the publishing app; [sourceClaimTime] and
 * [collectorTime] stay separate and must not be compared with each other (megaplan 17.2).
 */
public data class NotificationExcerptHandoff(
    val text: String,
    val sourceKind: SourceKind,
    val acquisitionKind: String,
    val accessClass: String,
    val sourceAppClaim: String,
    val conversationScopeClaim: String,
    val conversationTitleClaim: String?,
    val isGroupClaim: Boolean?,
    val senderLabelClaim: String?,
    val senderLabelSource: SenderLabelSource,
    val identityBasis: IdentityBasis,
    val direction: Direction,
    val outgoingCoverage: OutgoingCoverage,
    val textStatus: TextStatus,
    val summaryOnly: Boolean,
    val sourceClaimTime: SourceClaimTime,
    val notificationPostTimeMs: Long,
    val collectorTime: CollectorTime,
    val removal: RemovalLifecycle?,
    val corroboratingObservations: Int,
    val observedAsActiveSnapshot: Boolean,
) {
    public companion object {
        /** Value for `evidence.acquisition_kind`. The importer's vocabulary needs this entry (see the hand-off notes). */
        public const val ACQUISITION_KIND: String = "notification_excerpt"

        /** Value for `evidence.access_class`, already present in the vault vocabulary. */
        public const val ACCESS_CLASS: String = "notification_observation"
    }
}

/**
 * Turns a candidate into the hand-off. Summary-only text is marked uncertain, cut text is marked truncated, and a
 * sender is only ever an app-scoped hint. Outgoing coverage is always unknown for this lane.
 */
public fun ObservedCandidate.toHandoff(): NotificationExcerptHandoff {
    val observed = message
    return NotificationExcerptHandoff(
        text = observed.text,
        sourceKind = SourceKind.NOTIFICATION_EXCERPT,
        acquisitionKind = NotificationExcerptHandoff.ACQUISITION_KIND,
        accessClass = NotificationExcerptHandoff.ACCESS_CLASS,
        sourceAppClaim = observed.sourceAppClaim,
        conversationScopeClaim = observed.conversation.scopeId,
        conversationTitleClaim = observed.conversation.conversationTitle,
        isGroupClaim = observed.conversation.isGroup,
        senderLabelClaim = observed.senderLabel,
        senderLabelSource = observed.senderLabelSource,
        identityBasis = if (observed.senderLabel == null) IdentityBasis.UNKNOWN else IdentityBasis.APP_SCOPED_HINT,
        direction = if (observed.direction == ObservedDirection.INCOMING_HINT) Direction.INCOMING else Direction.UNKNOWN,
        outgoingCoverage = OutgoingCoverage.UNKNOWN,
        textStatus = when (observed.textStatus) {
            ObservedTextStatus.COMPLETE -> TextStatus.AVAILABLE
            ObservedTextStatus.TRUNCATED -> TextStatus.TRUNCATED
            ObservedTextStatus.SUMMARY_ONLY -> TextStatus.EXTRACTION_UNCERTAIN
        },
        summaryOnly = observed.textStatus == ObservedTextStatus.SUMMARY_ONLY,
        sourceClaimTime = observed.sourceClaimTime,
        notificationPostTimeMs = observed.notificationPostTimeMs,
        collectorTime = observed.collector,
        removal = removal,
        corroboratingObservations = corroboratingObservations,
        observedAsActiveSnapshot = observed.origin == SnapshotOrigin.ACTIVE_SNAPSHOT,
    )
}
