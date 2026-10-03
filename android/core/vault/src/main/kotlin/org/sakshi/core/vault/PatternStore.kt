package org.sakshi.core.vault

import androidx.room.withTransaction
import java.time.DateTimeException
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.sakshi.core.database.ReviewAction
import org.sakshi.core.database.ReviewDecisionEntity
import org.sakshi.core.database.ReviewTargetType
import org.sakshi.core.database.SakshiDatabase
import org.sakshi.core.database.SakshiSchema
import org.sakshi.core.database.AssessmentStatus as StoredAssessment
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.Event
import org.sakshi.core.temporal.CoverageGap
import org.sakshi.core.temporal.EvidenceView
import org.sakshi.core.temporal.GapReason
import org.sakshi.core.temporal.PatternRecord

/**
 * What a person has said about one stored pattern description. [ACCEPTED] means only that the person agrees the
 * description matches their evidence; it is not a finding, a verification or a legal statement.
 */
public enum class PatternReview {
    NOT_REVIEWED,
    ACCEPTED,
    REJECTED,
    MARKED_UNKNOWN,
}

/** A response a person can give to a stored pattern description. */
public enum class PatternReviewAction {
    ACCEPT,
    REJECT,
    MARK_UNKNOWN,

    /** Takes back an earlier response: the description is [PatternReview.NOT_REVIEWED] again. */
    WITHDRAW,
}

/** Outcome of [PatternStore.review]. Anything but [Recorded] means nothing was written. */
public sealed interface PatternReviewResult {
    /** One decision row and one audit row were written; [review] is the pattern's review state now. */
    public data class Recorded(val review: PatternReview) : PatternReviewResult

    /** No stored pattern has this id (it may have been replaced by a recompute). */
    public data object NotFound : PatternReviewResult

    /** The description is out of date, so a response to it would not be about what the person now has. */
    public data object Stale : PatternReviewResult

    /** A reason code was given with an action other than reject, or is not one of [ReviewReason]. */
    public data object ReasonNotAllowed : PatternReviewResult
}

/**
 * A pattern description kept in the vault. [id] is derived from the content (see [PatternCodec]): the same
 * description recomputed from the same inputs has the same id, and any change in what it rests on gives a new one,
 * which is how [review] stays tied to exactly what the person looked at. [stale] is true when something it rests on
 * may have changed since it was computed; recompute before relying on it. [record] keeps the engine's own
 * assessment status even when [stale]. [interpretation] is the sentence rendered when the description was generated,
 * if the caller supplied one. [reviewReason] is the [ReviewReason] constant the person gave when their newest decision
 * is a rejection; it is null when no reason was given, when the newest decision is not a rejection (accepting,
 * marking unknown or withdrawing clears it), or when the stored code is not in the vocabulary.
 */
public data class StoredPattern(
    val id: String,
    val record: PatternRecord,
    val stale: Boolean,
    val generatedAt: Instant,
    val interpretation: String?,
    val review: PatternReview,
    val reviewReason: String? = null,
)

/** What the temporal engine needs for one case, read in the same transaction that stores the result. */
public class PatternInputs(
    public val caseId: CaseId,
    public val view: EvidenceView,
    /** The vault clock when the inputs were read: the knowledge cutoff of the run. */
    public val now: Instant,
    /** Latest revision of each event available at [now]. */
    public val events: List<Event>,
    public val gaps: List<CoverageGap>,
    /** The person's own aliases for confirmed actors, by id. Never verified identities. */
    public val actorLabels: Map<ActorId, String>,
)

/** One description to store: the engine's record and, optionally, the interpretation sentence rendered for it. */
public data class ComputedPattern(val record: PatternRecord, val interpretation: String? = null)

/** A stored coverage gap as the temporal engine takes it. A gap that names an unknown reason is treated as `UNKNOWN`. */
public fun StoredCoverageGap.toCoverageGap(): CoverageGap = CoverageGap(
    id = id,
    caseId = caseId,
    actorId = null,
    start = startAt?.instant,
    end = endAt?.instant,
    reason = GapReason.entries.firstOrNull { it.name.equals(reason, ignoreCase = true) } ?: GapReason.UNKNOWN,
)

/**
 * Stored pattern descriptions of a case, their staleness and the person's review of them.
 *
 * Nothing here computes a pattern: the caller passes the engine run to [recompute], which reads the inputs and
 * replaces the stored rows in ONE transaction. Every vault write that can change what the engine would compute for
 * the case (saved events, coverage gaps, actor renames, evidence deletion) marks the case's non-stale patterns stale
 * in the write's own transaction, so a description is never marked current when its inputs changed after it was read.
 * Stored descriptions describe observed evidence; they never state a legal conclusion.
 */
public class PatternStore(
    private val database: SakshiDatabase,
    private val events: EventStore,
    private val actors: ActorRegistry,
    private val audit: AuditLog,
    private val clock: () -> Instant,
    private val ids: () -> String = { UUID.randomUUID().toString() },
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /**
     * Reads the engine inputs for the case at the vault clock's now, calls [compute], and replaces the case's stored
     * patterns for [view] in the same transaction: rows whose id is in the result are kept (and no longer stale),
     * rows that are not are deleted, new ones are inserted with their support rows. Descriptions with the same id
     * are stored once. Patterns of the other view are not touched. The result is in the order [compute] gave.
     * Decisions about replaced descriptions stay in the database, and apply again if an identical description returns.
     *
     * @throws IllegalArgumentException for an unknown case, or a record of another case or view.
     */
    public suspend fun recompute(
        caseId: CaseId,
        view: EvidenceView,
        compute: suspend (PatternInputs) -> List<ComputedPattern>,
    ): List<StoredPattern> = withContext(dispatcher) {
        database.withTransaction {
            requireNotNull(database.caseDao().get(caseId.value)) { "Unknown case" }
            val now = clock()
            val inputs = PatternInputs(
                caseId = caseId,
                view = view,
                now = now,
                events = events.loadLatest(caseId, now),
                gaps = events.coverageGaps(caseId).map { it.toCoverageGap() },
                actorLabels = actors.list(caseId).associate { it.id to it.displayLabel },
            )
            val encoded = compute(inputs).map { computed ->
                val record = computed.record
                require(record.caseId == caseId && record.view == view) { "Record is for another case or view" }
                PatternCodec.encode(record, computed.interpretation, now)
            }.distinctBy { it.id }
            replace(caseId, view, encoded)
            val stored = read(caseId, view).associateBy { it.id }
            encoded.mapNotNull { stored[it.id] }
        }
    }

    /** The case's stored patterns for [view], by type and window. Rows this version cannot read are left out. */
    public suspend fun list(caseId: CaseId, view: EvidenceView): List<StoredPattern> =
        withContext(dispatcher) { database.withTransaction { read(caseId, view) } }

    /** Emits [list] now and after every change to the case's patterns, their support or decisions. */
    public fun observe(caseId: CaseId, view: EvidenceView): Flow<List<StoredPattern>> =
        database.invalidationTracker
            .createFlow(SakshiSchema.PATTERN, SakshiSchema.PATTERN_SUPPORT, SakshiSchema.REVIEW_DECISION)
            .map { list(caseId, view) }
            .distinctUntilChanged()

    /**
     * Records the person's response to the stored description [patternId] as one insert-only `review_decision`
     * row and one `pattern.reviewed` audit row (ids and enum strings only; the note is never audited), in one
     * transaction. [reasonCode] is allowed only with [PatternReviewAction.REJECT] and must be one of [ReviewReason].
     * The description's state is derived from its newest decision, so a decision applies only to the exact
     * description it was given to.
     */
    public suspend fun review(
        patternId: String,
        action: PatternReviewAction,
        reasonCode: String? = null,
        note: String? = null,
    ): PatternReviewResult {
        if (reasonCode != null && (action != PatternReviewAction.REJECT || reasonCode !in ReviewReason.all)) {
            return PatternReviewResult.ReasonNotAllowed
        }
        return withContext(dispatcher) {
            database.withTransaction {
                val row = database.patternDao().get(patternId) ?: return@withTransaction PatternReviewResult.NotFound
                if (row.assessmentStatus == StoredAssessment.STALE) return@withTransaction PatternReviewResult.Stale
                val at = clock()
                database.findingDao().insertDecision(
                    ReviewDecisionEntity(
                        id = ids(),
                        caseId = row.caseId,
                        targetType = ReviewTargetType.PATTERN,
                        targetId = row.id,
                        targetRevision = null,
                        action = storedAction(action),
                        reasonCode = reasonCode,
                        editedValueJson = if (action == PatternReviewAction.WITHDRAW) WITHDRAWN_VALUE else null,
                        note = note?.takeIf { it.isNotBlank() },
                        decidedAt = at.toString(),
                        decidedAtEpochMs = at.toEpochMilli(),
                    ),
                )
                val review = reviewOf(action)
                audit.append(
                    AuditActions.PATTERN_REVIEWED,
                    SUBJECT_TYPE,
                    row.id,
                    jsonObjectOf(
                        *listOfNotNull(
                            "pattern_id" to row.id,
                            "case_id" to row.caseId,
                            "action" to storedAction(action),
                            "review" to review.name.lowercase(),
                            reasonCode?.let { "reason_code" to it },
                        ).toTypedArray(),
                    ),
                )
                PatternReviewResult.Recorded(review)
            }
        }
    }

    private suspend fun replace(caseId: CaseId, view: EvidenceView, encoded: List<EncodedPattern>) {
        val dao = database.patternDao()
        val existing = dao.getForCaseView(caseId.value, PatternCodec.viewName(view)).associateBy { it.id }
        val wanted = encoded.map { it.id }.toSet()
        (existing.keys - wanted).chunked(CHUNK).forEach { dao.deleteByIds(it) }
        for (pattern in encoded) {
            val kept = existing[pattern.id]
            when {
                kept == null -> dao.insertWithSupport(pattern.entity, pattern.support)
                kept.assessmentStatus != pattern.entity.assessmentStatus ->
                    dao.updateAssessment(pattern.id, pattern.entity.assessmentStatus)
            }
        }
    }

    private suspend fun read(caseId: CaseId, view: EvidenceView): List<StoredPattern> {
        val dao = database.patternDao()
        val name = PatternCodec.viewName(view)
        val rows = dao.getForCaseView(caseId.value, name)
        if (rows.isEmpty()) return emptyList()
        val support = dao.getSupportForCaseView(caseId.value, name).groupBy { it.patternId }
        val decisions = database.findingDao().getLatestDecisionsForCase(caseId.value, ReviewTargetType.PATTERN)
            .associateBy { it.targetId }
        return rows.mapNotNull { row ->
            try {
                StoredPattern(
                    id = row.id,
                    record = PatternCodec.decode(row, support[row.id].orEmpty()),
                    stale = row.assessmentStatus == StoredAssessment.STALE,
                    generatedAt = Instant.parse(row.generatedAt),
                    interpretation = row.interpretationText,
                    review = decisions[row.id]?.let { DecisionParser.patternReview(it.action) } ?: PatternReview.NOT_REVIEWED,
                    reviewReason = decisions[row.id]
                        ?.takeIf { it.action == ReviewAction.REJECT }
                        ?.reasonCode
                        ?.takeIf { it in ReviewReason.all },
                )
            } catch (_: IllegalStateException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            } catch (_: NoSuchElementException) {
                null
            } catch (_: DateTimeException) {
                null
            }
        }
    }

    private fun storedAction(action: PatternReviewAction): String = when (action) {
        PatternReviewAction.ACCEPT -> ReviewAction.ACCEPT
        PatternReviewAction.REJECT -> ReviewAction.REJECT
        PatternReviewAction.MARK_UNKNOWN -> ReviewAction.MARK_UNKNOWN
        PatternReviewAction.WITHDRAW -> ReviewAction.EDIT
    }

    private fun reviewOf(action: PatternReviewAction): PatternReview = when (action) {
        PatternReviewAction.ACCEPT -> PatternReview.ACCEPTED
        PatternReviewAction.REJECT -> PatternReview.REJECTED
        PatternReviewAction.MARK_UNKNOWN -> PatternReview.MARKED_UNKNOWN
        PatternReviewAction.WITHDRAW -> PatternReview.NOT_REVIEWED
    }

    private companion object {
        const val SUBJECT_TYPE = "pattern"
        const val CHUNK = 400
        val WITHDRAWN_VALUE: String = jsonObjectOf("review" to "not_reviewed").toString()
    }
}
