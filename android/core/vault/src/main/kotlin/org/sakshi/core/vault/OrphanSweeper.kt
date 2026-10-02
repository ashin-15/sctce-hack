package org.sakshi.core.vault

import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.sakshi.core.database.SakshiDatabase
import org.sakshi.core.database.SakshiSchema

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
    public suspend fun sweep(): SweepReport = withContext(dispatcher) {
        val temporary = blobs.sweepTemporaryFiles()
        val rows = recordedBlobs()
        val known = rows.values.toSet()
        val orphans = blobs.listBlobs().filter { it !in known }.count { blobs.delete(it) }
        val missing = rows.filterValues { !blobs.exists(it) }.keys.sorted()
        SweepReport(orphans, temporary, missing)
    }

    private fun recordedBlobs(): Map<String, String> =
        database.query(SimpleSQLiteQuery("SELECT evidence_id, path FROM ${SakshiSchema.EVIDENCE_BLOB}")).use { cursor ->
            buildMap { while (cursor.moveToNext()) put(cursor.getString(0), cursor.getString(1)) }
        }
}
