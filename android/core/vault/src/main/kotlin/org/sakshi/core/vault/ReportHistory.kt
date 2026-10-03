package org.sakshi.core.vault

import androidx.room.withTransaction
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.sakshi.core.database.ReportEntity
import org.sakshi.core.database.ReportSnapshotEntity
import org.sakshi.core.database.SakshiDatabase
import org.sakshi.core.model.CaseId

/** What an export was made from: the event revisions it used and a digest of the report content the person saw. */
public data class ExportDependencies(val eventRevisions: Map<String, Int>, val contentSha256: String)

/** A stored export snapshot. Frozen once written; a later export of the case marks it superseded. */
public data class ExportedSnapshot(
    val id: String,
    val version: Int,
    val createdAt: String,
    val merkleRoot: String,
    val manifestSha256: String,
    val signerKeyId: String?,
    val supersededBy: String?,
    val dependencies: ExportDependencies,
)

/**
 * How the case differs from what [snapshot] was made from. [changedEvents] have a newer revision now (a review, a
 * correction, a new sender or boundary); [removedEvents] no longer exist, for example because their evidence was
 * deleted. Events added to the case later are not counted: they were never part of the export.
 */
public data class ExportDrift(val snapshot: ExportedSnapshot, val changedEvents: Int, val removedEvents: Int) {
    val upToDate: Boolean get() = changedEvents == 0 && removedEvents == 0
}

/**
 * Report versions of a case. Each export is one snapshot with the next version number; the one before it is marked
 * superseded, never deleted. One report container per case.
 */
public class ReportHistory(
    private val database: SakshiDatabase,
    private val events: EventStore,
    private val audit: AuditLog,
    private val clock: () -> Instant,
    private val ids: () -> String,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /** The version the next export of the case gets: one more than the newest snapshot, starting at 1. */
    public suspend fun nextVersion(caseId: CaseId): Int =
        withContext(dispatcher) { (database.reportDao().getLatestSnapshotForCase(caseId.value)?.version ?: 0) + 1 }

    /**
     * Stores the snapshot of a finished export and its `export.created` audit row in one transaction, and marks the
     * case's previous newest snapshot superseded by it.
     *
     * @throws IllegalStateException when [version] is not [nextVersion], because another export was stored first.
     */
    public suspend fun record(caseId: CaseId, export: ExportRecord): ExportedSnapshot = withContext(dispatcher) {
        database.withTransaction {
            val reports = database.reportDao()
            val previous = reports.getLatestSnapshotForCase(caseId.value)
            check(export.version == (previous?.version ?: 0) + 1) { "Report version ${export.version} is no longer the next one" }
            val report = reports.getReportForCase(caseId.value)
                ?: ReportEntity(ids(), caseId.value, REPORT_TITLE, clock().toString()).also { reports.insertReport(it) }
            val entity = ReportSnapshotEntity(
                id = export.snapshotId,
                reportId = report.id,
                version = export.version,
                createdAt = export.createdAt,
                manifestSha256 = export.manifestSha256,
                merkleRoot = export.merkleRoot,
                signature = export.signatureHex,
                signerKeyId = export.signerKeyId,
                supersededBy = null,
                dependencyJson = encode(export.dependencies),
            )
            reports.insertSnapshot(entity)
            if (previous != null) reports.markSuperseded(previous.id, entity.id)
            audit.recordExport(export.snapshotId, caseId.value, export.dependencies.eventRevisions.size, export.originalCount, export.signerKeyId)
            entity.toSnapshot()
        }
    }

    /** The case's newest export snapshot, or null when it was never exported. */
    public suspend fun latest(caseId: CaseId): ExportedSnapshot? =
        withContext(dispatcher) { database.reportDao().getLatestSnapshotForCase(caseId.value)?.toSnapshot() }

    /** How the case now differs from its newest export, or null when it was never exported. Updates with the case. */
    public fun observeDrift(caseId: CaseId): Flow<ExportDrift?> =
        combine(database.reportDao().observeLatestSnapshotForCase(caseId.value), events.observeLatest(caseId)) { stored, latest ->
            stored?.toSnapshot()?.let { snapshot ->
                val now = latest.associate { it.eventId.value to it.revision }
                val used = snapshot.dependencies.eventRevisions
                ExportDrift(
                    snapshot = snapshot,
                    changedEvents = used.count { (id, revision) -> now[id]?.let { it != revision } == true },
                    removedEvents = used.keys.count { it !in now },
                )
            }
        }

    private fun ReportSnapshotEntity.toSnapshot() =
        ExportedSnapshot(id, version, createdAt, merkleRoot, manifestSha256, signerKeyId, supersededBy, decode(dependencyJson))

    private fun encode(dependencies: ExportDependencies): String = buildJsonObject {
        put("version", DEPENDENCY_FORMAT)
        put("content_sha256", dependencies.contentSha256)
        put(
            "events",
            buildJsonArray {
                dependencies.eventRevisions.toSortedMap().forEach { (id, revision) ->
                    add(buildJsonObject {
                        put("event_id", id)
                        put("revision", revision)
                    })
                }
            },
        )
    }.toString()

    private fun decode(json: String): ExportDependencies {
        val root = Json.parseToJsonElement(json).jsonObject
        val events = root.getValue("events").jsonArray.associate {
            val entry = it.jsonObject
            entry.getValue("event_id").jsonPrimitive.content to entry.getValue("revision").jsonPrimitive.int
        }
        return ExportDependencies(events, root.getValue("content_sha256").jsonPrimitive.content)
    }

    private companion object {
        const val REPORT_TITLE: String = "Case report"
        const val DEPENDENCY_FORMAT: Int = 1
    }
}

/** What one finished export hands to [ReportHistory.record]. Ids, digests and counts only; no text from the case. */
public data class ExportRecord(
    val snapshotId: String,
    val version: Int,
    val createdAt: String,
    val merkleRoot: String,
    val manifestSha256: String,
    val signatureHex: String,
    val signerKeyId: String,
    val originalCount: Int,
    val dependencies: ExportDependencies,
)
