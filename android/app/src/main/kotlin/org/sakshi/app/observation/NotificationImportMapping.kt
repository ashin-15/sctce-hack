package org.sakshi.app.observation

import org.sakshi.acquisition.importer.NotificationExcerptImport
import org.sakshi.acquisition.notifications.ObservedCandidate
import org.sakshi.acquisition.notifications.toHandoff
import org.sakshi.core.vault.NotificationClaims

/** Preserve source and collector claims independently when the person chooses to keep an excerpt. */
internal fun ObservedCandidate.toImport(): NotificationExcerptImport {
    val handoff = toHandoff()
    return NotificationExcerptImport(
        text = handoff.text,
        claims = NotificationClaims(
            sourceAppClaim = handoff.sourceAppClaim,
            conversationScopeClaim = handoff.conversationScopeClaim,
            conversationTitleClaim = handoff.conversationTitleClaim,
            isGroupClaim = handoff.isGroupClaim,
            senderLabelClaim = handoff.senderLabelClaim,
            identityBasis = handoff.identityBasis,
            direction = handoff.direction,
            textStatus = handoff.textStatus,
            summaryOnly = handoff.summaryOnly,
            sourceClaimTimeMs = handoff.sourceClaimTime.epochMs,
            sourceClaimTimeBasis = handoff.sourceClaimTime.basis.name.lowercase(),
            notificationPostTimeMs = handoff.notificationPostTimeMs,
            collectorWallMs = handoff.collectorTime.wallMs,
            collectorElapsedRealtimeMs = handoff.collectorTime.elapsedRealtimeMs,
            collectorSessionId = handoff.collectorTime.sessionId,
            removalReasonCode = handoff.removal?.platformReasonCode,
            removalObservedWallMs = handoff.removal?.observedWallMs,
            corroboratingObservations = handoff.corroboratingObservations,
            observedAsActiveSnapshot = handoff.observedAsActiveSnapshot,
        ),
    )
}
