package org.sakshi.core.vault

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.sakshi.core.database.CaseEntity
import org.sakshi.core.database.CaseStatus
import org.sakshi.core.database.SakshiDatabase
import org.sakshi.core.database.SakshiSchema

/** A case with the number of evidence items it holds. */
public data class CaseSummary(
    val id: String,
    val title: String,
    val status: String,
    val createdAt: String,
    val evidenceCount: Int,
)

/** Creates, edits and deletes cases. Titles are validated and never written to the audit log. */
public class CaseRepository(
    private val database: SakshiDatabase,
    private val blobs: BlobStore,
    private val audit: AuditLog,
    private val clock: () -> Instant,
    private val ids: () -> String = { UUID.randomUUID().toString() },
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /** @throws IllegalArgumentException if the trimmed title is not 1..[MAX_TITLE_LENGTH] characters. */
    public suspend fun create(title: String): CaseSummary {
        val clean = validTitle(title)
        val entity = CaseEntity(ids(), clean, clock().toString(), CaseStatus.ACTIVE)
        database.withTransaction {
            database.caseDao().insert(entity)
            audit.append(AuditActions.CASE_CREATED, SUBJECT_TYPE, entity.id)
        }
        return CaseSummary(entity.id, entity.title, entity.status, entity.createdAt, 0)
    }

    public suspend fun rename(caseId: String, title: String) {
        val clean = validTitle(title)
        update(caseId, AuditActions.CASE_RENAMED) { it.copy(title = clean) }
    }

    public suspend fun archive(caseId: String) {
        update(caseId, AuditActions.CASE_ARCHIVED) { it.copy(status = CaseStatus.ARCHIVED) }
    }

    public suspend fun unarchive(caseId: String) {
        update(caseId, AuditActions.CASE_ARCHIVED) { it.copy(status = CaseStatus.ACTIVE) }
    }

    /**
     * Deletes the case and everything it owns. Rows go first, in one transaction with the audit record, so the
     * wrapped keys are destroyed even if a file cannot be removed; leftover files are found by [OrphanSweeper].
     */
    public suspend fun delete(caseId: String) {
        val paths = database.withTransaction {
            val found = requireNotNull(database.caseDao().get(caseId)) { "Unknown case" }
            val evidenceDao = database.evidenceDao()
            val collected = evidenceDao.observeForCase(found.id).first().mapNotNull { evidenceDao.getBlob(it.id)?.path }
            database.caseDao().delete(found.id)
            audit.append(
                AuditActions.CASE_DELETED,
                SUBJECT_TYPE,
                found.id,
                jsonObjectOf("evidence_count" to collected.size),
            )
            collected
        }
        withContext(dispatcher) { paths.forEach(blobs::delete) }
    }

    /** Emits every case, newest first, whenever cases or evidence change. */
    public fun observe(): Flow<List<CaseSummary>> =
        database.invalidationTracker.createFlow(SakshiSchema.CASE_FILE, SakshiSchema.EVIDENCE)
            .map { withContext(dispatcher) { summaries() } }

    private suspend fun update(caseId: String, action: String, change: (CaseEntity) -> CaseEntity) {
        database.withTransaction {
            val current = requireNotNull(database.caseDao().get(caseId)) { "Unknown case" }
            val changed = change(current)
            database.caseDao().update(changed)
            audit.append(action, SUBJECT_TYPE, caseId, jsonObjectOf("status" to changed.status))
        }
    }

    private fun summaries(): List<CaseSummary> =
        database.query(SimpleSQLiteQuery(SUMMARY_SQL)).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(CaseSummary(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getInt(4)))
                }
            }
        }

    private fun validTitle(title: String): String {
        val clean = title.trim()
        require(clean.length in 1..MAX_TITLE_LENGTH) { "Title must be 1..$MAX_TITLE_LENGTH characters" }
        return clean
    }

    public companion object {
        public const val MAX_TITLE_LENGTH: Int = 120
        private const val SUBJECT_TYPE = "case"
        private val SUMMARY_SQL =
            "SELECT c.id, c.title, c.status, c.created_at, " +
                "(SELECT COUNT(*) FROM ${SakshiSchema.EVIDENCE} e WHERE e.case_id = c.id) " +
                "FROM ${SakshiSchema.CASE_FILE} c ORDER BY c.created_at DESC, c.id"
    }
}
