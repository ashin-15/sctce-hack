package org.sakshi.acquisition.notifications

import java.util.UUID

/** The de-duplication layer that suppressed content, or the reason it was only used to seed state. */
public enum class SuppressionLayer {
    /** Layer 1: the same notification was re-posted or updated with the same content. */
    UPDATE_EQUIVALENCE,

    /** Layer 2: the message was already included in an earlier snapshot of the same conversation. */
    ALREADY_SEEN_IN_CONVERSATION,

    /** Layer 3: a group summary overlapped child notifications. */
    GROUP_SUMMARY_OVERLAP,

    /** Layer 4: an active snapshot re-delivered something already seen. */
    ACTIVE_SNAPSHOT_REDELIVERY,

    /** Layer 4: an active snapshot was used to seed state only, and nothing in it was reported as new. */
    ACTIVE_SNAPSHOT_SEEDED,
}

/** What to do with messages found only in an active snapshot (report section 5.3: new observations only by default). */
public enum class ActiveSnapshotPolicy { SEED_ONLY, REPORT_AS_ACTIVE_SNAPSHOT }

/** A new candidate. [supersedes] names an earlier summary-only candidate that this one replaces rather than doubles. */
public data class DiffedCandidate(val candidate: ObservedCandidate, val supersedes: String?)

/** The lifecycle fact of a removal callback, to be attached to candidates of that notification. */
public data class LifecycleUpdate(val notificationKey: String, val removal: RemovalLifecycle)

/** The outcome of one snapshot: only genuinely new candidates, plus how many items each layer suppressed. */
public data class DiffResult(
    val newCandidates: List<DiffedCandidate>,
    val suppressed: Map<SuppressionLayer, Int>,
    val lifecycle: LifecycleUpdate?,
    val withheld: WithheldReason?,
)

/**
 * Suppresses redundant representations of the same notification content while keeping real repetition. Harassment is
 * often the same text again, so text equality alone never merges two contacts: two entries are the same only when
 * sender, text and the publisher's claimed time agree and the earlier one was seen in the same conversation scope.
 *
 * State is RAM only and bounded by [NotificationBounds.DEDUP_MAX_SCOPES] and [NotificationBounds.DEDUP_TTL_MS].
 * Forgetting a scope can only cause a repeat to be reported again, never a message to be lost. The class is thread-safe.
 */
public class ObservationDiffer(
    private val activePolicy: () -> ActiveSnapshotPolicy = { ActiveSnapshotPolicy.SEED_ONLY },
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private data class Signature(val sender: String?, val text: String, val timeMs: Long?)

    private class KeyState(var lastSignatures: List<Signature>? = null)

    private class ScopeState(var lastSeenElapsedMs: Long) {
        val seen = HashMap<Signature, Int>()
        val keys = HashMap<String, KeyState>()
    }

    private class SummaryEmission(val candidateId: String, val sender: String?, val text: String)

    private class GroupState(var lastSeenElapsedMs: Long) {
        var childSeen = false
        val summaryEmissions = ArrayList<SummaryEmission>()
    }

    private val lock = Any()
    private val scopes = LinkedHashMap<String, ScopeState>(16, 0.75f, true)
    private val groups = LinkedHashMap<String, GroupState>(16, 0.75f, true)

    /** Number of conversation scopes held, for tests of the bound. */
    internal val trackedScopeCount: Int get() = synchronized(lock) { scopes.size }

    /** Forgets everything. Used when the session is stopped. */
    public fun reset(): Unit = synchronized(lock) {
        scopes.clear()
        groups.clear()
    }

    public fun process(snapshot: NotificationSnapshot): DiffResult = synchronized(lock) {
        expire(snapshot.elapsedRealtimeMs)
        if (snapshot.origin == SnapshotOrigin.REMOVAL) return@synchronized removal(snapshot)

        val normalized = NotificationNormalizer.normalize(snapshot)
        if (normalized.messages.isEmpty()) return@synchronized DiffResult(emptyList(), emptyMap(), null, normalized.withheld)

        val suppressed = LinkedHashMap<SuppressionLayer, Int>()
        val messages = normalized.messages
        val scopeId = messages.first().conversation.scopeId
        val scope = scopes.getOrPut(scopeId) { ScopeState(snapshot.elapsedRealtimeMs) }
        scope.lastSeenElapsedMs = snapshot.elapsedRealtimeMs
        trimScopes()
        val keyState = scope.keys.getOrPut(snapshot.notificationKey) { KeyState() }
        val signatures = messages.map(::signatureOf)
        val group = groupOf(snapshot)
        group?.lastSeenElapsedMs = snapshot.elapsedRealtimeMs

        // Layer 3, first half: a summary that arrives after its children adds no candidate.
        if (snapshot.isGroupSummary && group != null && group.childSeen) {
            markSeen(scope, keyState, signatures)
            suppressed.add(SuppressionLayer.GROUP_SUMMARY_OVERLAP, messages.size)
            return@synchronized DiffResult(emptyList(), suppressed, null, null)
        }
        if (!snapshot.isGroupSummary && group != null) group.childSeen = true

        // Layer 1: the same notification again with the same content.
        if (keyState.lastSignatures == signatures) {
            suppressed.add(SuppressionLayer.UPDATE_EQUIVALENCE, messages.size)
            return@synchronized DiffResult(emptyList(), suppressed, null, null)
        }

        // Layers 2 and 4: occurrence-aware multiset difference against everything seen in this conversation scope.
        val occurrence = HashMap<Signature, Int>()
        val fresh = ArrayList<ObservedMessage>()
        messages.forEachIndexed { index, message ->
            val signature = signatures[index]
            val position = occurrence[signature] ?: 0
            occurrence[signature] = position + 1
            if (position >= (scope.seen[signature] ?: 0)) {
                fresh += message
            } else {
                val layer = if (snapshot.origin == SnapshotOrigin.ACTIVE_SNAPSHOT) {
                    SuppressionLayer.ACTIVE_SNAPSHOT_REDELIVERY
                } else {
                    SuppressionLayer.ALREADY_SEEN_IN_CONVERSATION
                }
                suppressed.add(layer, 1)
            }
        }
        markSeen(scope, keyState, signatures)

        if (snapshot.origin == SnapshotOrigin.ACTIVE_SNAPSHOT && activePolicy() == ActiveSnapshotPolicy.SEED_ONLY) {
            suppressed.add(SuppressionLayer.ACTIVE_SNAPSHOT_SEEDED, fresh.size)
            return@synchronized DiffResult(emptyList(), suppressed, null, null)
        }

        // Layer 3, second half: a child that matches an earlier summary-only candidate replaces it.
        val emitted = fresh.map { message ->
            val replaced = if (!snapshot.isGroupSummary && group != null) takeSummaryMatch(group, message) else null
            val candidate = ObservedCandidate(newId(), message)
            if (snapshot.isGroupSummary && group != null) {
                group.summaryEmissions += SummaryEmission(candidate.id, message.senderLabel, message.text)
            }
            DiffedCandidate(candidate, replaced)
        }
        DiffResult(emitted, suppressed, null, null)
    }

    private fun removal(snapshot: NotificationSnapshot): DiffResult {
        val code = checkNotNull(snapshot.removalReasonCode)
        val scope = scopes.values.firstOrNull { snapshot.notificationKey in it.keys }
        if (scope != null) {
            // Generation change: the next post under this key is not assumed to be the same content. Entries with no
            // claimed time cannot be told apart from a new message, so they are forgotten and may be reported again.
            scope.keys[snapshot.notificationKey]?.lastSignatures = null
            scope.seen.keys.removeAll { it.timeMs == null }
        }
        val update = LifecycleUpdate(snapshot.notificationKey, RemovalLifecycle(code, snapshot.observedWallMs))
        return DiffResult(emptyList(), emptyMap(), update, WithheldReason.REMOVAL_ONLY)
    }

    private fun markSeen(scope: ScopeState, keyState: KeyState, signatures: List<Signature>) {
        signatures.groupingBy { it }.eachCount().forEach { (signature, count) ->
            scope.seen[signature] = maxOf(scope.seen[signature] ?: 0, count)
        }
        keyState.lastSignatures = signatures
    }

    private fun groupOf(snapshot: NotificationSnapshot): GroupState? {
        val groupKey = snapshot.groupKey ?: return null
        return groups.getOrPut("${snapshot.packageName}|$groupKey") { GroupState(snapshot.elapsedRealtimeMs) }
    }

    private fun takeSummaryMatch(group: GroupState, message: ObservedMessage): String? {
        val match = group.summaryEmissions.firstOrNull {
            it.text == message.text && (it.sender == null || message.senderLabel == null || it.sender == message.senderLabel)
        } ?: return null
        group.summaryEmissions.remove(match)
        return match.candidateId
    }

    private fun signatureOf(message: ObservedMessage): Signature =
        Signature(message.senderLabel, message.text, message.sourceClaimTime.epochMs)

    private fun expire(nowElapsedMs: Long) {
        scopes.values.removeAll { nowElapsedMs - it.lastSeenElapsedMs > NotificationBounds.DEDUP_TTL_MS }
        groups.values.removeAll { nowElapsedMs - it.lastSeenElapsedMs > NotificationBounds.DEDUP_TTL_MS }
    }

    private fun trimScopes() {
        while (scopes.size > NotificationBounds.DEDUP_MAX_SCOPES) scopes.remove(scopes.keys.first())
        while (groups.size > NotificationBounds.DEDUP_MAX_SCOPES) groups.remove(groups.keys.first())
    }

    private fun MutableMap<SuppressionLayer, Int>.add(layer: SuppressionLayer, count: Int) {
        if (count > 0) merge(layer, count, Int::plus)
    }
}
