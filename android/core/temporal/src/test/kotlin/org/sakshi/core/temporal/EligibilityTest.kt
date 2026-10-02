package org.sakshi.core.temporal

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.sakshi.core.model.ConfirmationStatus
import org.sakshi.core.model.Event

class EligibilityTest {
    private val cutoff: Instant = Instant.parse("2026-10-01T12:00:00Z")

    private fun base(): List<Event> =
        listOf(msg("synthetic-m1", "10:00:00"), msg("synthetic-m2", "10:10:00"))

    private fun count(events: List<Event>, view: EvidenceView = EvidenceView.CONFIRMED_ONLY): Int? =
        caseInput(events, view = view, cutoff = cutoff).analyse().of(PatternType.REPEATED_CONTACT)
            .singleOrNull()?.let { repeated(it).total.lower }

    @Test
    fun laterRevisionIsInvisibleBeforeItsAvailableAt() {
        val second = msg("synthetic-m2", "10:50:00") { revision = 2; availableAt = "2026-10-01T13:00:00Z"; observedAt = "2026-10-01T10:50:00Z" }
        val before = caseInput(base() + second, cutoff = cutoff).analyse()
        assertEquals(base().map { it.eventId.value }, before.timeline.map { it.eventId.value })
        assertEquals(listOf(1, 1), before.timeline.map { it.revision })
        assertEquals(instant("2026-10-01T10:10:00Z"), before.timeline.last().earliest)

        val after = caseInput(base() + second, cutoff = Instant.parse("2026-10-01T14:00:00Z")).analyse()
        assertEquals(listOf(1, 2), after.timeline.map { it.revision })
        assertEquals(instant("2026-10-01T10:50:00Z"), after.timeline.last().earliest)
    }

    @Test
    fun reviewAfterCutoffIsPending() {
        val late = msg("synthetic-m2", "10:10:00") { reviewedAt = "2026-10-01T13:00:00Z" }
        val events = listOf(msg("synthetic-m1", "10:00:00"), late)
        assertEquals(null, count(events))
        val preview = caseInput(events, view = EvidenceView.CANDIDATE_PREVIEW, cutoff = cutoff).analyse()
        val record = preview.single(PatternType.REPEATED_CONTACT)
        assertEquals(AssessmentStatus.CANDIDATE, record.status)
        assertTrue(Limitation.UNCONFIRMED_EVIDENCE in record.limitations)
        assertTrue(record.dependsOn(late.eventId))
    }

    @Test
    fun confirmedWithoutAnyReviewTimeIsPending() {
        val unreviewed = msg("synthetic-m2", "10:10:00") { omitReviewTime = true }
        assertEquals(null, count(listOf(msg("synthetic-m1", "10:00:00"), unreviewed)))
        assertEquals(2, count(listOf(msg("synthetic-m1", "10:00:00"), unreviewed), EvidenceView.CANDIDATE_PREVIEW))
    }

    @Test
    fun rejectedAndExpiredAreExcludedInBothViews() {
        val rejected = msg("synthetic-m2", "10:10:00") { confirmation = ConfirmationStatus.REJECTED }
        val expired = msg("synthetic-m3", "10:20:00") { confirmation = ConfirmationStatus.EXPIRED }
        val events = base() + rejected + expired
        for (view in EvidenceView.entries) {
            val result = caseInput(events, view = view, cutoff = cutoff).analyse()
            assertFalse(result.timeline.any { it.eventId == rejected.eventId || it.eventId == expired.eventId })
        }
    }

    @Test
    fun expiredEncryptedCandidateIsExcluded() {
        val stale = msg("synthetic-m3", "10:20:00") { pending(); expiresAt = "2026-10-01T11:00:00Z" }
        val fresh = msg("synthetic-m4", "10:30:00") { pending(); expiresAt = "2026-10-02T11:00:00Z" }
        val result = caseInput(base() + stale + fresh, view = EvidenceView.CANDIDATE_PREVIEW, cutoff = cutoff).analyse()
        assertEquals(listOf("synthetic-m1", "synthetic-m2", "synthetic-m4"), result.timeline.map { it.eventId.value })
    }

    @Test
    fun crossCaseEventsNeverContribute() {
        val other = msg("synthetic-x1", "10:20:00") { caseId = "synthetic-other-case" }
        val result = caseInput(base() + other, cutoff = cutoff).analyse()
        assertFalse(result.timeline.any { it.eventId == other.eventId })
        assertFalse(result.patterns.any { it.dependsOn(other.eventId) })
        assertEquals(2, repeated(result.single(PatternType.REPEATED_CONTACT)).total.lower)
    }

    @Test
    fun crossCaseGapsAreIgnored() {
        val gap =
            CoverageGap(
                org.sakshi.core.model.ReferenceId("synthetic-g1"),
                org.sakshi.core.model.CaseId("synthetic-other-case"),
                null, null, null, GapReason.UNKNOWN,
            )
        val result = caseInput(base(), gaps = listOf(gap), cutoff = cutoff).analyse()
        assertTrue(result.gaps.isEmpty())
        assertTrue(result.single(PatternType.REPEATED_CONTACT).gapIds.isEmpty())
    }

    @Test
    fun nonContactKindsAreNotCounted() {
        val events =
            base() +
                msg("synthetic-n1", "10:20:00") { kind = org.sakshi.core.model.EventKind.USER_NOTE } +
                msg("synthetic-n2", "10:21:00") { kind = org.sakshi.core.model.EventKind.NOTIFICATION_LIFECYCLE } +
                msg("synthetic-n3", "10:22:00") { direction = org.sakshi.core.model.Direction.SYSTEM } +
                msg("synthetic-n4", "10:23:00") { direction = org.sakshi.core.model.Direction.UNKNOWN } +
                msg("synthetic-n5", "10:24:00") { dedup(org.sakshi.core.model.DedupStatus.LIFECYCLE_ONLY, null) } +
                msg("synthetic-n6", "10:25:00") { kind = org.sakshi.core.model.EventKind.REPORTED_EXTERNAL_EVENT }
        val result = caseInput(events, cutoff = cutoff).analyse()
        assertEquals(8, result.timeline.size)
        assertEquals(2, result.contactEntries().size)
        assertEquals(CountBounds(2, 2), repeated(result.single(PatternType.REPEATED_CONTACT)).total)
    }
}
