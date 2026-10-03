package org.sakshi.acquisition.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The session-only list of candidates, held in memory and bounded by [maxRecords] and [maxBytes] (megaplan 13.2:
 * 100 records and 2 MiB). When a bound is exceeded the oldest candidates are evicted and counted in [droppedCount],
 * which feeds the coverage detail so that the person is told some candidates were dropped.
 *
 * Nothing here is written to disk. [clear] runs on session lock, stop and revoke. A candidate becomes evidence only
 * through an explicit user action: [take] hands immutable records to the caller, which imports them.
 * The class is thread-safe.
 */
public class CandidateInbox(
    private val maxRecords: Int = NotificationBounds.INBOX_MAX_RECORDS,
    private val maxBytes: Int = NotificationBounds.INBOX_MAX_BYTES,
    private val onDropped: (Int) -> Unit = {},
) {
    private val lock = Any()
    private val records = ArrayList<ObservedCandidate>()
    private val state = MutableStateFlow<List<ObservedCandidate>>(emptyList())
    private val dropped = MutableStateFlow(0)

    /** The current candidates, oldest first. Each emission is an immutable copy. */
    public val candidates: StateFlow<List<ObservedCandidate>> = state.asStateFlow()

    /** Candidates evicted for space since the inbox was created or last cleared. Zero does not mean nothing was missed. */
    public val droppedCount: StateFlow<Int> = dropped.asStateFlow()

    /** Approximate memory use of the held candidates in bytes. */
    public val approximateBytes: Int get() = synchronized(lock) { records.sumOf { it.approximateBytes() } }

    /** Adds new candidates, replacing any earlier summary-only candidate a new one supersedes, then enforces the bounds. */
    public fun add(diffed: List<DiffedCandidate>) {
        if (diffed.isEmpty()) return
        var evicted = 0
        synchronized(lock) {
            diffed.forEach { item ->
                item.supersedes?.let { replaced ->
                    val index = records.indexOfFirst { it.id == replaced }
                    if (index >= 0) {
                        val old = records.removeAt(index)
                        records += item.candidate.copy(corroboratingObservations = old.corroboratingObservations + 1)
                        return@forEach
                    }
                }
                records += item.candidate
            }
            var bytes = records.sumOf { it.approximateBytes() }
            while (records.isNotEmpty() && (records.size > maxRecords || bytes > maxBytes)) {
                bytes -= records.removeAt(0).approximateBytes()
                evicted += 1
            }
            publish(evicted)
        }
        if (evicted > 0) onDropped(evicted)
    }

    /** Records that the notification [update] names was removed. The candidate stays; only lifecycle metadata is added. */
    public fun applyLifecycle(update: LifecycleUpdate): Unit = synchronized(lock) {
        var changed = false
        records.indices.forEach { index ->
            val candidate = records[index]
            if (candidate.message.conversation.notificationKey == update.notificationKey) {
                records[index] = candidate.copy(removal = update.removal)
                changed = true
            }
        }
        if (changed) publish(0)
    }

    /** Removes and returns the candidates with [ids] for the caller to import. Unknown ids are ignored. */
    public fun take(ids: Set<String>): List<ObservedCandidate> = synchronized(lock) {
        val taken = records.filter { it.id in ids }
        if (taken.isNotEmpty()) {
            records.removeAll { it.id in ids }
            publish(0)
        }
        taken
    }

    /** Returns candidates taken earlier, for example when the import failed. They go to the end and the bounds apply. */
    public fun restore(candidates: List<ObservedCandidate>) {
        add(candidates.map { DiffedCandidate(it, supersedes = null) })
    }

    /** Removes the candidates with [ids] without importing them. */
    public fun discard(ids: Set<String>) {
        take(ids)
    }

    /** Removes everything and resets the dropped counter. */
    public fun clear(): Unit = synchronized(lock) {
        records.clear()
        state.value = emptyList()
        dropped.value = 0
    }

    private fun publish(evicted: Int) {
        state.value = records.toList()
        if (evicted > 0) dropped.value += evicted
    }
}
