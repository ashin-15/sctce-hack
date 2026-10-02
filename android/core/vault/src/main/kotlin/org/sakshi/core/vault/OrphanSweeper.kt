package org.sakshi.core.vault

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.sakshi.core.database.SakshiDatabase

/** Result of [OrphanSweeper.sweep]. [missingFileEvidenceIds] are reported, never repaired or deleted. */
public class SweepReport(
    public val orphanFilesDeleted: Int,
    public val temporaryFilesDeleted: Int,
    public val missingFileEvidenceIds: List<String>,
)

/**
 * Reconciles blob files with their database rows.
 *
 * Run it only while no import is in flight (the vault does so at start-up): a blob that has been renamed into
 * place but not yet recorded looks like an orphan.
 */
public class OrphanSweeper(
    private val database: SakshiDatabase,
    private val blobs: BlobStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    public suspend fun sweep(): SweepReport {
        val rows = database.evidenceDao().getAllBlobPaths().associate { it.evidenceId to it.path }
        return withContext(dispatcher) { reconcile(rows) }
    }

    private fun reconcile(rows: Map<String, String>): SweepReport {
        val temporary = blobs.sweepTemporaryFiles()
        val known = rows.values.toSet()
        val orphans = blobs.listBlobs().filter { it !in known }.count { blobs.delete(it) }
        val missing = rows.filterValues { !blobs.exists(it) }.keys.sorted()
        return SweepReport(orphans, temporary, missing)
    }
}
