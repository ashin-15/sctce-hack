package org.sakshi.core.vault

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.CategoryReviewStatus
import org.sakshi.core.model.EventId
import org.sakshi.core.model.Timestamp
import org.sakshi.core.temporal.EvidenceView
import org.sakshi.core.temporal.GapReason
import org.sakshi.core.temporal.PatternType
import org.sakshi.core.temporal.fixtures.SyntheticTimelines

/** Stored descriptions: replacement semantics of recompute, reading, isolation between cases and views. */
class PatternStoreTest : PatternTestBase() {
    private val a = SyntheticTimelines.a()
    private val b = SyntheticTimelines.b()

    @Test
    fun recomputeStoresTheEngineRecordsInEngineOrderAndReadsThemBack() = runBlocking<Unit> {
        load(a, "synthetic-actor-a")

        val stored = recompute(a.caseId)

        assertEquals(setOf(PatternType.REPEATED_CONTACT, PatternType.RECURRENCE_AFTER_BOUNDARY), stored.map { it.record.type }.toSet())
        assertEquals(stored.map { it.id }, recompute(a.caseId).map { it.id })
        assertEquals(stored.toSet(), patterns.list(a.caseId, EvidenceView.CONFIRMED_ONLY).toSet())
        assertTrue(stored.all { !it.stale && it.review == PatternReview.NOT_REVIEWED && it.interpretation != null })
        assertEquals(stored.size, count("pattern"))
        assertEquals(stored.sumOf { it.record.supportingEvents.size + it.record.contextEvents.size }, count("pattern_support"))
        assertEquals(emptyList(), patterns.list(a.caseId, EvidenceView.CANDIDATE_PREVIEW))
    }

    @Test
    fun recomputeGivesTheInputsTheEngineNeedsFromTheSameTransaction() = runBlocking<Unit> {
        load(SyntheticTimelines.d(), "synthetic-actor-d")
        val caseId = SyntheticTimelines.d().caseId
        store.addCoverageGap(caseId, Timestamp("2026-10-01T05:30:00Z"), Timestamp("2026-10-02T03:30:00Z"), "listener_disconnected")
        var seen: PatternInputs? = null

        patterns.recompute(caseId, EvidenceView.CONFIRMED_ONLY) { inputs ->
            seen = inputs
            emptyList()
        }

        val inputs = checkNotNull(seen)
        assertEquals(patternNow, inputs.now)
        assertEquals(SyntheticTimelines.d().events.map { it.eventId }.toSet(), inputs.events.map { it.eventId }.toSet())
        assertEquals(1, inputs.gaps.size)
        assertEquals(GapReason.LISTENER_DISCONNECTED, inputs.gaps.single().reason)
        assertEquals("synthetic-label-synthetic-actor-d", inputs.actorLabels.getValue(ActorId("synthetic-actor-d")))
    }

    @Test
    fun recomputeKeepsIdenticalRowsAndClearsTheirStaleness() = runBlocking<Unit> {
        load(a, "synthetic-actor-a")
        val first = recompute(a.caseId)
        actors.rename(ActorId("synthetic-actor-a"), "synthetic-renamed")
        assertEquals(first.size, staleCount(a.caseId))
        patternNow = patternNow.plusSeconds(3600)

        val second = recompute(a.caseId)

        assertEquals(first.map { it.id }, second.map { it.id })
        assertEquals(0, staleCount(a.caseId))
        // Kept rows are the original rows: their generation time and text are not rewritten.
        assertEquals(first.map { it.generatedAt }, second.map { it.generatedAt })
        assertEquals(first.size, count("pattern"))
    }

    @Test
    fun recomputeDeletesVanishedRowsInsertsNewOnesAndLeavesNoOrphanSupport() = runBlocking<Unit> {
        load(b, "synthetic-actor-b")
        val before = recompute(b.caseId)
        assertTrue(before.any { it.record.type == PatternType.WORDING_TRANSITION })

        assertApplied(review.reviewCategory(EventId("synthetic-b3"), 0, CategoryReviewStatus.REJECTED), "synthetic-b3")
        val after = recompute(b.caseId)

        assertTrue(after.none { it.record.type == PatternType.WORDING_TRANSITION })
        val vanished = before.map { it.id } - after.map { it.id }.toSet()
        assertTrue(vanished.isNotEmpty())
        assertEquals(after.size, count("pattern"))
        assertEquals(0, orphanSupportRows())
        assertEquals(after.sumOf { it.record.supportingEvents.size + it.record.contextEvents.size }, count("pattern_support"))
    }

    @Test
    fun rejectingOneFindingMakesTheWordingTransitionStaleAndThenAbsent() = runBlocking<Unit> {
        load(b, "synthetic-actor-b")
        val transition = recompute(b.caseId).single { it.record.type == PatternType.WORDING_TRANSITION }
        assertEquals(PatternReviewResult.Recorded(PatternReview.ACCEPTED), patterns.review(transition.id, PatternReviewAction.ACCEPT))

        assertApplied(review.reviewCategory(EventId("synthetic-b3"), 0, CategoryReviewStatus.REJECTED, ReviewReason.SIGNAL_ABSENT), "synthetic-b3")

        val stale = patterns.list(b.caseId, EvidenceView.CONFIRMED_ONLY)
        assertTrue(stale.all { it.stale })
        assertEquals(PatternReview.ACCEPTED, stale.single { it.id == transition.id }.review)
        assertEquals(PatternReviewResult.Stale, patterns.review(transition.id, PatternReviewAction.REJECT))

        val fresh = recompute(b.caseId)
        assertTrue(fresh.none { it.record.type == PatternType.WORDING_TRANSITION })
        assertTrue(fresh.none { it.id == transition.id })
    }

    @Test
    fun anUnchangedCaseGivesTheSameIdsAcrossRecomputes() = runBlocking<Unit> {
        load(SyntheticTimelines.c(), "synthetic-actor-c")
        val caseId = SyntheticTimelines.c().caseId
        val first = recompute(caseId)
        patternNow = patternNow.plusSeconds(60)
        val second = recompute(caseId)
        assertTrue(first.any { it.record.type == PatternType.DENSITY_CHANGE })
        assertEquals(first.map { it.id }, second.map { it.id })
        // The clock moved but the description did not, so the stored rows are the first ones.
        assertEquals(first.map { it.record }, second.map { it.record })
    }

    @Test
    fun aFailingComputeChangesNothing() = runBlocking<Unit> {
        load(a, "synthetic-actor-a")
        val stored = recompute(a.caseId)
        actors.rename(ActorId("synthetic-actor-a"), "synthetic-renamed")

        assertFailsWith<IllegalStateException> {
            patterns.recompute(a.caseId, EvidenceView.CONFIRMED_ONLY) { error("synthetic failure") }
        }

        val after = patterns.list(a.caseId, EvidenceView.CONFIRMED_ONLY)
        assertEquals(stored.map { it.id }.toSet(), after.map { it.id }.toSet())
        assertTrue(after.all { it.stale })
    }

    @Test
    fun recordsOfAnotherCaseOrViewAndUnknownCasesAreRefused() = runBlocking<Unit> {
        load(a, "synthetic-actor-a")
        load(b, "synthetic-actor-b")
        val foreign = recompute(b.caseId).first().record

        assertFailsWith<IllegalArgumentException> {
            patterns.recompute(a.caseId, EvidenceView.CONFIRMED_ONLY) { listOf(ComputedPattern(foreign)) }
        }
        assertFailsWith<IllegalArgumentException> {
            patterns.recompute(b.caseId, EvidenceView.CANDIDATE_PREVIEW) { listOf(ComputedPattern(foreign)) }
        }
        assertFailsWith<IllegalArgumentException> {
            patterns.recompute(CaseId("synthetic-missing"), EvidenceView.CONFIRMED_ONLY) { emptyList() }
        }
    }

    @Test
    fun identicalDescriptionsFromComputeAreStoredOnce() = runBlocking<Unit> {
        load(a, "synthetic-actor-a")
        val records = patterns.recompute(a.caseId, EvidenceView.CONFIRMED_ONLY) { engine(it).map(::ComputedPattern) }.map { it.record }

        val again = patterns.recompute(a.caseId, EvidenceView.CONFIRMED_ONLY) { records.map { r -> ComputedPattern(r) } + records.map { r -> ComputedPattern(r) } }

        assertEquals(records.size, again.size)
        assertEquals(records.size, count("pattern"))
    }

    @Test
    fun twoCasesNeverSeeEachOthersPatterns() = runBlocking<Unit> {
        load(a, "synthetic-actor-a")
        load(b, "synthetic-actor-b")
        val forA = recompute(a.caseId)
        val forB = recompute(b.caseId)

        assertTrue(forA.map { it.id }.intersect(forB.map { it.id }.toSet()).isEmpty())
        assertTrue(patterns.list(a.caseId, EvidenceView.CONFIRMED_ONLY).all { it.record.caseId == a.caseId })
        assertTrue(patterns.list(b.caseId, EvidenceView.CONFIRMED_ONLY).all { it.record.caseId == b.caseId })

        // A write to case A leaves case B's descriptions current, and recomputing A keeps B's rows.
        actors.rename(ActorId("synthetic-actor-a"), "synthetic-renamed")
        assertEquals(0, staleCount(b.caseId))
        recompute(a.caseId)
        assertEquals(forB.map { it.id }.toSet(), patterns.list(b.caseId, EvidenceView.CONFIRMED_ONLY).map { it.id }.toSet())
    }

    @Test
    fun viewsAreStoredSeparatelyButStalenessIsCaseWide() = runBlocking<Unit> {
        load(a, "synthetic-actor-a")
        val confirmed = recompute(a.caseId, EvidenceView.CONFIRMED_ONLY)
        val preview = recompute(a.caseId, EvidenceView.CANDIDATE_PREVIEW)
        assertTrue(preview.all { it.record.view == EvidenceView.CANDIDATE_PREVIEW })
        assertTrue(confirmed.map { it.id }.intersect(preview.map { it.id }.toSet()).isEmpty())

        actors.rename(ActorId("synthetic-actor-a"), "synthetic-renamed")
        assertEquals(confirmed.size + preview.size, staleCount(a.caseId))

        recompute(a.caseId, EvidenceView.CONFIRMED_ONLY)
        assertEquals(preview.size, staleCount(a.caseId))
        assertTrue(patterns.list(a.caseId, EvidenceView.CANDIDATE_PREVIEW).all { it.stale })
    }

    @Test
    fun observeEmitsTheStoredPatternsAndTheirChanges() = runBlocking<Unit> {
        load(a, "synthetic-actor-a")
        assertEquals(emptyList(), patterns.observe(a.caseId, EvidenceView.CONFIRMED_ONLY).first())
        val stored = recompute(a.caseId)
        assertEquals(stored.map { it.id }.toSet(), patterns.observe(a.caseId, EvidenceView.CONFIRMED_ONLY).first().map { it.id }.toSet())
        actors.rename(ActorId("synthetic-actor-a"), "synthetic-renamed")
        assertTrue(patterns.observe(a.caseId, EvidenceView.CONFIRMED_ONLY).first().all { it.stale })
    }

    private suspend fun orphanSupportRows(): Int = withContext(Dispatchers.IO) {
        db.query(
            "SELECT COUNT(*) FROM pattern_support WHERE pattern_id NOT IN (SELECT id FROM pattern) " +
                "OR event_id NOT IN (SELECT id FROM event)",
            null,
        ).use {
            assertTrue(it.moveToFirst())
            it.getInt(0)
        }
    }
}
