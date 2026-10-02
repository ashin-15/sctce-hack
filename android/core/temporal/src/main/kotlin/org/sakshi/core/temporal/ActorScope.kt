package org.sakshi.core.temporal

import org.sakshi.core.model.ActorId
import org.sakshi.core.model.AssociationReview
import org.sakshi.core.model.Event
import org.sakshi.core.model.ScopeId

/**
 * Whose observations a pattern is about. Patterns are computed per scope and scopes are never merged.
 * [key] is a stable string used for ordering and pattern keys.
 */
public sealed interface ActorScope {
    public val key: String

    /** A sender whose association with [actorId] was confirmed by the user. */
    public data class Confirmed(val actorId: ActorId) : ActorScope {
        override val key: String
            get() = "confirmed:${encode(actorId.value)}"
    }

    /**
     * A sender without a confirmed association. Two scopes with the same [displayLabel] but a different
     * app or conversation are different scopes.
     */
    public data class Unresolved(
        val sourceApp: String?,
        val conversationScopeId: ScopeId?,
        val displayLabel: String?,
    ) : ActorScope {
        override val key: String
            get() =
                "unresolved:${encode(sourceApp)}|${encode(conversationScopeId?.value)}|${encode(displayLabel)}"
    }
}

private fun encode(part: String?): String = if (part == null) "-" else "${part.length}:$part"

internal fun Event.actorScope(): ActorScope {
    val actorId = sender.actorId
    return if (actorId != null && sender.associationReview == AssociationReview.CONFIRMED) {
        ActorScope.Confirmed(actorId)
    } else {
        ActorScope.Unresolved(source.sourceApp, source.conversationScopeId, sender.displayLabel)
    }
}
