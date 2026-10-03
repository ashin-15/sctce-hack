package org.sakshi.core.vault

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.sakshi.core.database.ReviewAction
import org.sakshi.core.database.ReviewDecisionEntity
import org.sakshi.core.database.ReviewTargetType
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.EventId
import org.sakshi.core.model.Timestamp
import org.sakshi.core.model.UnwantedContact
import org.sakshi.core.temporal.EvidenceView
import org.sakshi.core.temporal.PatternType
import org.sakshi.core.temporal.fixtures.SyntheticTimelines

/**
 * A person's response to a stored description. "Accepted" means only that the person agrees the description matches
 * their evidence. The response belongs to the exact description (its content id), not to the case.
 */
class PatternReviewTest : PatternTestBase() {
    private val a = SyntheticTimelines.a()
    private val actorA = ActorId("synthetic-actor-a")

    private suspend fun stored(): List<StoredPattern> {
        load(a, "synthetic-actor-a")
        return recompute(a.caseId)
    }

    private suspend fun current(id: String): StoredPattern =
        patterns.list(a.caseId, EvidenceView.CONFIRMED_ONLY).single { it.id == id }

    @Test
    fun acceptRejectUnknownAndWithdrawFollowTheNewestDecision() = runBlocking<Unit> {
        val pattern = stored().first()
        assertEquals(PatternReview.NOT_REVIEWED, pattern.review)

        assertEquals(PatternReviewResult.Recorded(PatternReview.ACCEPTED), patterns.review(pattern.id, PatternReviewAction.ACCEPT))
        assertEquals(PatternReview.ACCEPTED, current(pattern.id).review)
        assertEquals(
            PatternReviewResult.Recorded(PatternReview.REJECTED),
            patterns.review(pattern.id, PatternReviewAction.REJECT, ReviewReason.INSUFFICIENT_CONTEXT, "synthetic note"),
        )
        assertEquals(PatternReview.REJECTED, current(pattern.id).review)
        assertEquals(PatternReviewResult.Recorded(PatternReview.MARKED_UNKNOWN), patterns.review(pattern.id, PatternReviewAction.MARK_UNKNOWN))
        assertEquals(PatternReview.MARKED_UNKNOWN, current(pattern.id).review)
        assertEquals(PatternReviewResult.Recorded(PatternReview.NOT_REVIEWED), patterns.review(pattern.id, PatternReviewAction.WITHDRAW))
        assertEquals(PatternReview.NOT_REVIEWED, current(pattern.id).review)

        assertEquals(4, count("review_decision"))
        assertEquals(
            listOf(PatternReview.ACCEPTED, PatternReview.REJECTED, PatternReview.MARKED_UNKNOWN, PatternReview.NOT_REVIEWED),
            review.decisions(pattern.id).map { (it.change as DecisionChange.PatternReviewed).to },
        )
    }

    @Test
    fun eachDecisionIsReadBackAsATypedPatternTarget() = runBlocking<Unit> {
        val pattern = stored().first()
        patterns.review(pattern.id, PatternReviewAction.REJECT, ReviewReason.SIGNAL_ABSENT, "synthetic note")

        val decision = review.decisions(pattern.id).single()

        assertEquals(DecisionTargetKind.Pattern, decision.target)
        assertEquals(DecisionChange.PatternReviewed(PatternReview.REJECTED), decision.change)
        assertEquals(ReviewTargetType.PATTERN, decision.targetType)
        assertEquals(ReviewAction.REJECT, decision.action)
        assertEquals(ReviewReason.SIGNAL_ABSENT, decision.reasonCode)
        assertNull(decision.targetRevision)
        assertEquals(a.caseId.value, decision.caseId)
        assertEquals(decision, review.latestDecision(ReviewTargetType.PATTERN, pattern.id))
        assertEquals("synthetic note", noteOf(decision.id))
        val withdrawn = patterns.review(pattern.id, PatternReviewAction.WITHDRAW)
        assertIs<PatternReviewResult.Recorded>(withdrawn)
        val last = review.decisions(pattern.id).last()
        assertEquals(ReviewAction.EDIT, last.action)
        assertEquals(DecisionChange.PatternReviewed(PatternReview.NOT_REVIEWED), last.change)
    }

    @Test
    fun aRejectionReasonIsReadBackThroughListObserveAndRecompute() = runBlocking<Unit> {
        val pattern = stored().first()
        assertNull(pattern.reviewReason)

        patterns.review(pattern.id, PatternReviewAction.REJECT, ReviewReason.SIGNAL_ABSENT, "synthetic note")

        assertEquals(ReviewReason.SIGNAL_ABSENT, current(pattern.id).reviewReason)
        assertEquals(
            ReviewReason.SIGNAL_ABSENT,
            patterns.observe(a.caseId, EvidenceView.CONFIRMED_ONLY).first().single { it.id == pattern.id }.reviewReason,
        )
        assertEquals(ReviewReason.SIGNAL_ABSENT, recompute(a.caseId).single { it.id == pattern.id }.reviewReason)
    }

    @Test
    fun aRejectionWithoutReasonHasNoReason() = runBlocking<Unit> {
        val pattern = stored().first()
        patterns.review(pattern.id, PatternReviewAction.REJECT)

        assertEquals(PatternReview.REJECTED, current(pattern.id).review)
        assertNull(current(pattern.id).reviewReason)
    }

    @Test
    fun aLaterDecisionClearsTheReason() = runBlocking<Unit> {
        val pattern = stored().first()
        patterns.review(pattern.id, PatternReviewAction.REJECT, ReviewReason.DUPLICATE)
        patterns.review(pattern.id, PatternReviewAction.ACCEPT)
        assertNull(current(pattern.id).reviewReason)

        patterns.review(pattern.id, PatternReviewAction.REJECT, ReviewReason.DUPLICATE)
        patterns.review(pattern.id, PatternReviewAction.WITHDRAW)
        assertNull(current(pattern.id).reviewReason)

        patterns.review(pattern.id, PatternReviewAction.REJECT, ReviewReason.DUPLICATE)
        patterns.review(pattern.id, PatternReviewAction.MARK_UNKNOWN)
        assertNull(current(pattern.id).reviewReason)
    }

    @Test
    fun aStoredCodeOutsideTheVocabularyReadsAsNoReason() = runBlocking<Unit> {
        val pattern = stored().first()
        withContext(Dispatchers.IO) {
            db.findingDao().insertDecision(
                ReviewDecisionEntity(
                    id = "synthetic-decision-unknown-reason",
                    caseId = a.caseId.value,
                    targetType = ReviewTargetType.PATTERN,
                    targetId = pattern.id,
                    targetRevision = null,
                    action = ReviewAction.REJECT,
                    reasonCode = "synthetic_future_reason",
                    editedValueJson = null,
                    note = null,
                    decidedAt = "2026-10-02T11:00:00Z",
                    decidedAtEpochMs = 1_790_000_000_000L,
                ),
            )
        }

        assertEquals(PatternReview.REJECTED, current(pattern.id).review)
        assertNull(current(pattern.id).reviewReason)
    }

    @Test
    fun aDecisionWritesOneAuditRowWithIdsOnlyAndTheChainStaysValid() = runBlocking<Unit> {
        val pattern = stored().first()
        val before = recording.calls.size

        patterns.review(pattern.id, PatternReviewAction.REJECT, ReviewReason.DUPLICATE, "synthetic private note")

        val added = recording.calls.drop(before)
        assertEquals(1, added.size)
        assertEquals(
            "pattern.reviewed|pattern|${pattern.id}|{\"pattern_id\":\"${pattern.id}\",\"case_id\":\"${a.caseId.value}\"," +
                "\"action\":\"reject\",\"review\":\"rejected\",\"reason_code\":\"duplicate\"}",
            added.single(),
        )
        assertFalse(added.single().contains("synthetic private note"))
        assertIs<AuditVerification.Valid>(audit.verify())
        assertEquals(1, audit.records().count { it.action == "pattern.reviewed" })
    }

    @Test
    fun refusalsWriteNothing() = runBlocking<Unit> {
        val pattern = stored().first()
        actors.rename(actorA, "synthetic-renamed")
        val decisions = count("review_decision")
        val auditRows = audit.count()

        assertEquals(PatternReviewResult.NotFound, patterns.review("synthetic-no-such-pattern", PatternReviewAction.ACCEPT))
        assertEquals(PatternReviewResult.ReasonNotAllowed, patterns.review(pattern.id, PatternReviewAction.ACCEPT, ReviewReason.DUPLICATE))
        assertEquals(PatternReviewResult.ReasonNotAllowed, patterns.review(pattern.id, PatternReviewAction.REJECT, "synthetic-not-a-reason"))
        assertEquals(PatternReviewResult.ReasonNotAllowed, patterns.review(pattern.id, PatternReviewAction.WITHDRAW, ReviewReason.DUPLICATE))
        assertEquals(PatternReviewResult.Stale, patterns.review(pattern.id, PatternReviewAction.ACCEPT))

        assertEquals(decisions, count("review_decision"))
        assertEquals(auditRows, audit.count())
    }

    @Test
    fun aDecisionStopsApplyingWhenTheDescriptionChanges() = runBlocking<Unit> {
        val before = stored()
        val repeated = before.single { it.record.type == PatternType.REPEATED_CONTACT }
        patterns.review(repeated.id, PatternReviewAction.ACCEPT)

        assertApplied(review.markWantedness(listOf(EventId("synthetic-a1")), UnwantedContact.USER_MARKED_WANTED), "synthetic-a1")
        val after = recompute(a.caseId)

        val replacement = after.single { it.record.type == PatternType.REPEATED_CONTACT }
        assertTrue(replacement.id != repeated.id)
        assertEquals(PatternReview.NOT_REVIEWED, replacement.review)
        assertNull(patternRow(repeated.id))
        // The earlier decision is history, not erased.
        assertEquals(1, review.decisions(repeated.id).size)
        assertEquals(0, review.decisions(replacement.id).size)
    }

    @Test
    fun aDecisionAppliesAgainWhenAnIdenticalDescriptionIsRecomputed() = runBlocking<Unit> {
        val before = stored()
        val repeated = before.single { it.record.type == PatternType.REPEATED_CONTACT }
        patterns.review(repeated.id, PatternReviewAction.REJECT, ReviewReason.INSUFFICIENT_CONTEXT)

        actors.rename(actorA, "synthetic-renamed")
        assertTrue(current(repeated.id).stale)
        val after = recompute(a.caseId)

        val same = after.single { it.record.type == PatternType.REPEATED_CONTACT }
        assertEquals(repeated.id, same.id)
        assertFalse(same.stale)
        assertEquals(PatternReview.REJECTED, same.review)
        assertEquals(PatternReviewResult.Recorded(PatternReview.ACCEPTED), patterns.review(same.id, PatternReviewAction.ACCEPT))
    }

    @Test
    fun aDecisionOnADeletedDescriptionReturnsWithAnIdenticalOneAfterTheChangeIsUndone() = runBlocking<Unit> {
        val repeated = stored().single { it.record.type == PatternType.REPEATED_CONTACT }
        patterns.review(repeated.id, PatternReviewAction.ACCEPT)
        recompute(a.caseId)

        // A coverage gap far outside every window changes nothing the description rests on.
        store.addCoverageGap(a.caseId, Timestamp("2026-12-01T00:00:00Z"), Timestamp("2026-12-02T00:00:00Z"), "import_selection")
        val after = recompute(a.caseId).single { it.record.type == PatternType.REPEATED_CONTACT }

        assertEquals(repeated.id, after.id)
        assertEquals(PatternReview.ACCEPTED, after.review)
    }

    @Test
    fun decisionsOfOneCaseNeverShowOnAnother() = runBlocking<Unit> {
        val forA = stored().first()
        val b = SyntheticTimelines.b()
        load(b, "synthetic-actor-b")
        recompute(b.caseId)
        patterns.review(forA.id, PatternReviewAction.ACCEPT)

        assertTrue(patterns.list(b.caseId, EvidenceView.CONFIRMED_ONLY).all { it.review == PatternReview.NOT_REVIEWED })
        assertEquals(PatternReview.ACCEPTED, current(forA.id).review)
    }

    @Test
    fun deletingTheCaseRemovesItsPatternsAndDecisions() = runBlocking<Unit> {
        val pattern = stored().first()
        patterns.review(pattern.id, PatternReviewAction.ACCEPT)

        cases.delete(a.caseId.value)

        assertEquals(0, count("pattern"))
        assertEquals(0, count("pattern_support"))
        assertEquals(0, count("review_decision"))
    }

    private suspend fun patternRow(id: String) = db.patternDao().get(id)

    private suspend fun noteOf(decisionId: String): String? = withContext(Dispatchers.IO) {
        db.query("SELECT note FROM review_decision WHERE id = ?", arrayOf(decisionId)).use {
            assertTrue(it.moveToFirst())
            it.getString(0)
        }
    }
}
