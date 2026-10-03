package org.sakshi.processing.stt

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Admits one heavy model at a time (NFR-04): the speech model and a local language model must never be resident at the
 * same moment on a phone.
 *
 * To share one lock with `:processing:llm` without a module dependency, the app implements this interface once over the
 * llm module's process-wide lock, for example
 * `object : HeavyModelLock { override suspend fun <T> withExclusive(block: suspend () -> T): T = InferenceLock.withLock(block) }`,
 * and passes that instance to the [ModelSessionManager] of this module. [ProcessWide] is the default when nothing is
 * shared.
 */
public interface HeavyModelLock {
    public suspend fun <T> withExclusive(block: suspend () -> T): T

    public companion object {
        private val processMutex: Mutex = Mutex()

        /** One mutex for the whole process. Not re-entrant: a holder must not wait for the lock again. */
        public val ProcessWide: HeavyModelLock = object : HeavyModelLock {
            override suspend fun <T> withExclusive(block: suspend () -> T): T = processMutex.withLock { block() }
        }
    }
}
