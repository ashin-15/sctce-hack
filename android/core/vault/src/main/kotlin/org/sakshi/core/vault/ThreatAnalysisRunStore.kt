package org.sakshi.core.vault

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.sakshi.core.database.SakshiDatabase
import org.sakshi.core.database.ThreatAnalysisRunEntity

/** Read-only view of threat-analysis attempt statuses, kept separate from the immutable event and review rows. */
public class ThreatAnalysisRunStore internal constructor(
    private val database: SakshiDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    public suspend fun forEvent(eventId: String): List<ThreatAnalysisRunEntity> = withContext(dispatcher) {
        database.threatAnalysisRunDao().getForEvent(eventId)
    }
}
