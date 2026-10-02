package org.sakshi.core.vault

import java.time.Instant
import org.sakshi.core.database.SenderClaimRow
import org.sakshi.core.model.ScopeId
import org.sakshi.core.model.Timestamp

/**
 * A sender name as a source gave it, among the newest revisions that have no confirmed person. Two claims with the
 * same name from different apps or conversations are separate. [selector] is what the assign operations take.
 */
public data class SenderClaim(
    val selector: SenderSelector,
    val messageCount: Int,
    /** How many of the [messageCount] messages are marked as the person's own outgoing messages. */
    val outgoingCount: Int,
    val earliestObservedAt: Instant,
)

internal fun SenderClaimRow.toClaim(): SenderClaim = SenderClaim(
    SenderSelector(displayLabel, sourceApp, conversationScopeId?.let(::ScopeId)),
    messageCount,
    outgoingCount,
    Timestamp(earliestObservedAt).instant,
)
