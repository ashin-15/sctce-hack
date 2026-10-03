package org.sakshi.core.vault

import androidx.room.withTransaction
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.sakshi.core.database.CoverageGapEntity
import org.sakshi.core.database.EventEntity
import org.sakshi.core.database.EventRevisionEntity
import org.sakshi.core.database.FindingAnchorEntity
import org.sakshi.core.database.SakshiDatabase
import org.sakshi.core.database.SakshiSchema
import org.sakshi.core.model.ArtifactId
import org.sakshi.core.model.AssociationReview
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.Direction
import org.sakshi.core.model.Event
import org.sakshi.core.model.EventId
import org.sakshi.core.model.EventValidator
import org.sakshi.core.model.ReferenceId
import org.sakshi.core.model.Timestamp
import org.sakshi.core.model.Violation

/** Outcome of [EventStore.save]. Anything but [Saved] means nothing was written. */
public sealed interface SaveResult {
    public data class Saved(val eventId: EventId, val revision: Int) : SaveResult

    /** The event breaks an invariant of the event model. */
    public data class Invalid(val violations: List<Violation>) : SaveResult

    public data object UnknownCase : SaveResult

    /** The revision is not the next one for the event, or the event id belongs to another case. */
    public data class RevisionConflict(val expected: Int) : SaveResult

    /** The event holds [fields] that the current tables cannot store without loss, so it was refused whole. */
    public data class Unsupported(val fields: List<String>) : SaveResult
}

/** A period the selected evidence does not cover. A null bound means unknown. */
public data class StoredCoverageGap(
    val id: ReferenceId,
    val caseId: CaseId,
    val startAt: Timestamp?,
    val endAt: Timestamp?,
    val reason: String,
)

/**
 * Lossless storage of schema events. Revisions are insert-only: a new revision adds rows and never touches the
 * earlier ones, so every revision loads back exactly as it was saved.
 *
 * Order of lists is kept without an order column: evidence references, categories and relationships get row keys
 * that sort in list order (see [RowKeys]), and a category's reference ids are the insertion order of its
 * `finding_anchor` rows (`ORDER BY rowid`; rows are insert-only and SQLite's VACUUM renumbers rowids but keeps
 * their relative order).
 *
 * Every write that can change what the temporal engine computes for a case (a saved event or batch, a coverage gap)
 * marks the case's stored patterns stale in the same transaction; see [PatternStore].
 */
public class EventStore(
    private val database: SakshiDatabase,
    private val audit: AuditLog,
    private val clock: () -> Instant,
    private val ids: () -> String,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /** Tests only: told how many events each [observeLatest] emission had to rebuild. */
    internal var assembledObserver: ((Int) -> Unit)? = null

    /**
     * Stores a new event (revision 1) or the next revision of an existing event, in one transaction with one
     * `event.saved` audit row. The event is validated first; [artifactLengths] gives the code point length of
     * text artifacts so text locators can be range checked.
     */
    public suspend fun save(event: Event, artifactLengths: (ArtifactId) -> Int? = { null }): SaveResult =
        withContext(dispatcher) {
            database.withTransaction {
                saveInTransaction(event, artifactLengths).also {
                    if (it is SaveResult.Saved) database.patternDao().markStaleForCase(event.caseId.value)
                }
            }
        }

    /**
     * Saves [events] in list order in ONE transaction with ONE `events.saved` audit row. Every event is checked
     * first, in order, against the stored data and the events before it, so an event may name earlier events of
     * the batch in relationships and as canonical event, and a batch may hold consecutive revisions of one event.
     * Events must all belong to the case of the first event. If any event is refused nothing is written and each
     * refusal is listed. Saving a batch gives the same stored events as saving them one by one.
     */
    public suspend fun saveAll(events: List<Event>, artifactLengths: (ArtifactId) -> Int? = { null }): BatchSaveResult {
        if (events.isEmpty()) return BatchSaveResult.Saved(0)
        return withContext(dispatcher) {
            database.withTransaction {
                saveAllInTransaction(events, artifactLengths).also {
                    if (it is BatchSaveResult.Saved) database.patternDao().markStaleForCase(events.first().caseId.value)
                }
            }
        }
    }

    /** Every stored revision of every event of the case, by event id and revision, rebuilt exactly as saved. */
    public suspend fun loadAll(caseId: CaseId): List<Event> = withContext(dispatcher) {
        database.withTransaction { assemble(caseId, database.eventDao().getRevisionsForCase(caseId.value)) }
    }

    /** Latest revision of each event whose `available_at` is at or before [cutoff], in time order. */
    public suspend fun loadLatest(caseId: CaseId, cutoff: Instant): List<Event> =
        latest(caseId, cutoff.toEpochMilli())

    /** One stored revision, or null if the event or revision does not exist. */
    public suspend fun load(eventId: EventId, revision: Int): Event? = withContext(dispatcher) {
        database.withTransaction {
            val dao = database.eventDao()
            val findingDao = database.findingDao()
            val event = dao.getEvent(eventId.value) ?: return@withTransaction null
            val row = dao.getRevision(eventId.value, revision) ?: return@withTransaction null
            val findings = findingDao.getForEventRevision(eventId.value, revision).map { finding ->
                FindingRows(finding, findingDao.getFindingAnchorsInOrder(finding.id).map { it.anchorId })
            }
            EventRowReader.assemble(
                event.caseId,
                RevisionRows(
                    row,
                    dao.getAnchors(eventId.value, revision),
                    findings,
                    dao.getLinksFrom(eventId.value, RowKeys.prefix(eventId.value, revision)),
                    dao.getBoundary(RowKeys.boundary(eventId.value, revision)),
                ),
            )
        }
    }

    /** The newest stored revision of the event, or null if it does not exist. */
    public suspend fun loadLatest(eventId: EventId): Event? = withContext(dispatcher) {
        database.withTransaction {
            val revision = database.eventDao().getLatestRevisionNumber(eventId.value) ?: return@withTransaction null
            load(eventId, revision)
        }
    }

    /** Every stored revision of the event in revision order, or an empty list if it does not exist. */
    public suspend fun revisions(eventId: EventId): List<Event> = withContext(dispatcher) {
        database.withTransaction {
            val caseId = database.eventDao().getEvent(eventId.value)?.caseId ?: return@withTransaction emptyList()
            RevisionAssembler(database).assemble(caseId, database.eventDao().getRevisions(eventId.value))
        }
    }

    /** Emits the newest revision of the event (null while it does not exist) now and after every change. */
    public fun observeEvent(eventId: EventId): Flow<Event?> =
        database.invalidationTracker.createFlow(*EVENT_TABLES).map { loadLatest(eventId) }.distinctUntilChanged()

    /**
     * Emits the latest revision of each event of the case, whatever its `available_at`, after every change.
     *
     * Each emission costs one query for the newest revision numbers, plus a fixed number of batched queries for the
     * events whose newest revision is new to this collection. The rest come from a cache kept for the life of the
     * collection and keyed by event id, revision and anchor count: a revision's rows never change, and the anchor
     * count notices the one exception, evidence deletion removing anchors. Nothing is cached once collection ends.
     */
    public fun observeLatest(caseId: CaseId): Flow<List<Event>> = flow {
        val cache = HashMap<RevisionKey, Event>()
        emitAll(database.invalidationTracker.createFlow(*EVENT_TABLES).map { latestCached(caseId, cache) })
    }

    /**
     * Sender claims among the newest revisions of the case: distinct sender label, app and conversation with no
     * confirmed person, with their message count and earliest observed time, ordered by label, app, conversation.
     * Grouped by the database, not in memory.
     */
    public suspend fun senderClaims(caseId: CaseId): List<SenderClaim> = withContext(dispatcher) {
        database.eventDao().getSenderClaims(
            caseId.value,
            Codecs.associationReview.name(AssociationReview.CONFIRMED),
            Codecs.direction.name(Direction.OUTGOING),
        ).map { it.toClaim() }
    }

    /** Emits [senderClaims] now and after every change to the case's events. */
    public fun observeSenderClaims(caseId: CaseId): Flow<List<SenderClaim>> =
        database.invalidationTracker.createFlow(SakshiSchema.EVENT, SakshiSchema.EVENT_REVISION)
            .map { senderClaims(caseId) }
            .distinctUntilChanged()

    /**
     * Records a period the case's evidence does not cover and returns its id, which events cite in
     * `gap_reference_ids`. The reason is stored as given and never audited.
     *
     * @throws IllegalArgumentException for an unknown case, a blank reason or bounds in the wrong order.
     */
    public suspend fun addCoverageGap(caseId: CaseId, startAt: Timestamp?, endAt: Timestamp?, reason: String): ReferenceId {
        require(reason.isNotBlank()) { "Reason must not be blank" }
        require(startAt == null || endAt == null || startAt.instant <= endAt.instant) { "Gap ends before it starts" }
        val entity = CoverageGapEntity(
            id = ids(),
            caseId = caseId.value,
            sourceScopeId = null,
            startAt = startAt?.iso,
            startAtEpochMs = startAt?.instant?.toEpochMilli(),
            endAt = endAt?.iso,
            endAtEpochMs = endAt?.instant?.toEpochMilli(),
            reason = reason,
        )
        withContext(dispatcher) {
            database.withTransaction {
                requireNotNull(database.caseDao().get(caseId.value)) { "Unknown case" }
                database.eventDao().insertCoverageGap(entity)
                database.patternDao().markStaleForCase(caseId.value)
                audit.append(
                    AuditActions.COVERAGE_GAP_ADDED,
                    "coverage_gap",
                    entity.id,
                    jsonObjectOf("gap_id" to entity.id, "case_id" to entity.caseId),
                )
            }
        }
        return ReferenceId(entity.id)
    }

    /** The case's coverage gaps, earliest start first. */
    public suspend fun coverageGaps(caseId: CaseId): List<StoredCoverageGap> = withContext(dispatcher) {
        database.eventDao().getCoverageGaps(caseId.value).map {
            StoredCoverageGap(
                ReferenceId(it.id),
                CaseId(it.caseId),
                it.startAt?.let(::Timestamp),
                it.endAt?.let(::Timestamp),
                it.reason,
            )
        }
    }

    private suspend fun latestCached(caseId: CaseId, cache: MutableMap<RevisionKey, Event>): List<Event> =
        withContext(dispatcher) {
            database.withTransaction {
                val heads = database.eventDao().getLatestHeads(caseId.value)
                val keys = heads.map { RevisionKey(it.eventId, it.revision, it.anchorCount) }
                val missing = keys.filter { it !in cache }
                if (missing.isNotEmpty()) {
                    val built = RevisionAssembler(database).assembleKeys(caseId.value, missing.map { it.eventId to it.revision })
                    assembledObserver?.invoke(built.size)
                    val byId = built.associateBy { it.eventId.value }
                    missing.forEach { key -> byId[key.eventId]?.let { cache[key] = it } }
                }
                cache.keys.retainAll(keys.toSet())
                keys.mapNotNull { cache[it] }
            }
        }

    private suspend fun latest(caseId: CaseId, cutoffEpochMs: Long): List<Event> = withContext(dispatcher) {
        database.withTransaction {
            assemble(caseId, database.eventDao().getLatestRevisions(caseId.value, cutoffEpochMs))
        }
    }

    private suspend fun saveAllInTransaction(events: List<Event>, artifactLengths: (ArtifactId) -> Int?): BatchSaveResult {
        val caseId = events.first().caseId
        if (database.caseDao().get(caseId.value) == null) return BatchSaveResult.UnknownCase
        val batch = EventBatch.load(database.eventDao(), database.evidenceDao(), events, artifactLengths)
        val failures = events.mapIndexedNotNull { index, event -> batch.accept(index, event) }
        if (failures.isNotEmpty()) return BatchSaveResult.Invalid(failures)

        val createdAt = clock().toString()
        val rows = events.map { event ->
            val stored = event.evidenceReferences.map { it.artifactId.value }.filter { it in batch.evidenceIds }
            EventRowWriter.rows(event, stored.associateWith { it }, createdAt)
        }
        val firstRevisions = events.filter { it.revision == 1 }.map { EventEntity(it.eventId.value, it.caseId.value) }
        val dao = database.eventDao()
        dao.insertEvents(firstRevisions)
        dao.insertRevisions(rows.map { it.revision })
        dao.insertAnchors(rows.flatMap { it.anchors })
        database.findingDao().insertFindings(rows.flatMap { r -> r.findings.map { it.finding } })
        database.findingDao().insertFindingAnchors(
            rows.flatMap { r -> r.findings.flatMap { f -> f.anchorIds.map { FindingAnchorEntity(f.finding.id, it) } } },
        )
        dao.insertLinks(rows.flatMap { it.links })
        dao.insertBoundaries(rows.mapNotNull { it.boundary })
        audit.append(
            AuditActions.EVENTS_SAVED,
            SUBJECT_TYPE,
            events.first().eventId.value,
            jsonObjectOf(
                "case_id" to caseId.value,
                "count" to events.size,
                "first_event_id" to events.first().eventId.value,
                "last_event_id" to events.last().eventId.value,
            ),
        )
        return BatchSaveResult.Saved(events.size)
    }

    private suspend fun saveInTransaction(event: Event, artifactLengths: (ArtifactId) -> Int?): SaveResult {
        val dao = database.eventDao()
        if (database.caseDao().get(event.caseId.value) == null) return SaveResult.UnknownCase
        val unsupported = EventRowWriter.unsupportedFields(event)
        if (unsupported.isNotEmpty()) return SaveResult.Unsupported(unsupported)
        val violations = EventValidator.validate(event, StoredCaseContext.load(dao, event, artifactLengths))
        if (violations.isNotEmpty()) return SaveResult.Invalid(violations)

        val eventId = event.eventId.value
        val existing = dao.getEvent(eventId)
        val expected = (dao.getLatestRevisionNumber(eventId) ?: 0) + 1
        if (event.revision != expected || (existing != null && existing.caseId != event.caseId.value)) {
            return SaveResult.RevisionConflict(expected)
        }

        val rows = EventRowWriter.rows(event, storedEvidenceIds(event), clock().toString())
        if (existing == null) {
            dao.insertEventWithRevision(EventEntity(eventId, event.caseId.value), rows.revision, rows.anchors)
        } else {
            dao.addRevision(rows.revision, rows.anchors)
        }
        rows.findings.forEach { database.findingDao().insertWithAnchors(it.finding, it.anchorIds) }
        rows.links.forEach { dao.insertLink(it) }
        rows.boundary?.let { dao.insertBoundary(it) }
        audit.append(
            AuditActions.EVENT_SAVED,
            SUBJECT_TYPE,
            eventId,
            jsonObjectOf(
                "event_id" to eventId,
                "case_id" to event.caseId.value,
                "revision" to event.revision,
                "kind" to Codecs.eventKind.name(event.eventKind),
            ),
        )
        return SaveResult.Saved(event.eventId, event.revision)
    }

    /** Artifact ids that are evidence rows of the event's own case. */
    private suspend fun storedEvidenceIds(event: Event): Map<String, String> = buildMap {
        for (reference in event.evidenceReferences) {
            val artifact = reference.artifactId.value
            val evidence = database.evidenceDao().get(artifact)
            if (evidence != null && evidence.caseId == event.caseId.value) put(artifact, evidence.id)
        }
    }

    private suspend fun assemble(caseId: CaseId, revisions: List<EventRevisionEntity>): List<Event> {
        if (revisions.isEmpty()) return emptyList()
        val dao = database.eventDao()
        val findingDao = database.findingDao()
        val anchors = dao.getAnchorsForCase(caseId.value).groupBy { it.eventId to it.eventRevision }
        val findings = findingDao.getForCase(caseId.value).groupBy { it.eventId to it.eventRevision }
        val findingAnchors = findingDao.getFindingAnchorsForCase(caseId.value).groupBy { it.findingId }
        val links = dao.getLinksForCase(caseId.value).groupBy { it.fromEvent }
        val boundaries = dao.getBoundaries(caseId.value).associateBy { it.id }
        return revisions.map { row ->
            val key = row.eventId to row.revision
            val prefix = RowKeys.prefix(row.eventId, row.revision)
            EventRowReader.assemble(
                caseId.value,
                RevisionRows(
                    row,
                    anchors[key].orEmpty(),
                    findings[key].orEmpty().map { finding ->
                        FindingRows(finding, findingAnchors[finding.id].orEmpty().map { it.anchorId })
                    },
                    links[row.eventId].orEmpty().filter { it.id.startsWith(prefix) },
                    boundaries[RowKeys.boundary(row.eventId, row.revision)],
                ),
            )
        }
    }

    /** Identity of a cached event: its revision rows never change, but evidence deletion can remove anchors. */
    private data class RevisionKey(val eventId: String, val revision: Int, val anchorCount: Int)

    private companion object {
        const val SUBJECT_TYPE = "event"
        val EVENT_TABLES: Array<String> = arrayOf(
            SakshiSchema.EVENT,
            SakshiSchema.EVENT_REVISION,
            SakshiSchema.EVIDENCE_ANCHOR,
            SakshiSchema.FINDING,
            SakshiSchema.FINDING_ANCHOR,
            SakshiSchema.EVENT_LINK,
            SakshiSchema.BOUNDARY,
        )
    }
}
