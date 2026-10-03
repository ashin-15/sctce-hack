package org.sakshi.processing.llm.engine

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public class InferenceLock public constructor(
    private val mutex: Mutex = Mutex(),
) {
    public suspend fun <T> withLock(action: suspend () -> T): T = mutex.withLock { action() }

    public val isLocked: Boolean
        get() = mutex.isLocked

    public companion object {
        private val globalMutex: Mutex = Mutex()

        public suspend fun <T> withLock(action: suspend () -> T): T = globalMutex.withLock { action() }

        public val isLocked: Boolean
            get() = globalMutex.isLocked
    }
}
