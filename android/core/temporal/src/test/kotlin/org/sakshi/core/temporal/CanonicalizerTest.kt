package org.sakshi.core.temporal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.sakshi.core.model.CategoryLabel
import org.sakshi.core.model.DedupStatus
import org.sakshi.core.model.RelationshipReviewStatus

class CanonicalizerTest {
    private fun total(vararg events: org.sakshi.core.model.Event): CountBounds =
        repeated(caseInput(events.toList()).analyse().single(PatternType.REPEATED_CONTACT)).total

    @Test
    fun possibleDuplicatesGiveCountRange() {
        val events =
            listOf(msg("synthetic-m1", "10:00:00"), msg("synthetic-m2", "10:10:00"), msg("synthetic-m3", "10:20:00"),
                msg("synthetic-m4", "10:30:00"), msg("synthetic-m5", "10:40:00") {
                    dedup(DedupStatus.POSSIBLE_DUPLICATE, "synthetic-m4")
                })
        val result = caseInput(events).analyse()
        val record = result.single(PatternType.REPEATED_CONTACT)
        assertEquals(CountBounds(4, 5), repeated(record).total)
        assertTrue(Limitation.DUPLICATE_UNCERTAINTY in record.limitations)
        assertTrue(PatternExplanation.render(record, ::label).observed.startsWith("4 to 5 distinct"))
    }

    @Test
    fun confirmedRelationshipMergesAndRejectedDoesNot() {
        fun withReview(review: RelationshipReviewStatus) =
            total(
                msg("synthetic-m1", "10:00:00"),
                msg("synthetic-m2", "10:00:30") { relate("synthetic-m1", review = review) },
                msg("synthetic-m3", "10:30:00"),
            )
        assertEquals(CountBounds(2, 2), withReview(RelationshipReviewStatus.CONFIRMED))
        assertEquals(CountBounds(3, 3), withReview(RelationshipReviewStatus.REJECTED))
        assertEquals(CountBounds(2, 3), withReview(RelationshipReviewStatus.UNREVIEWED))
        assertEquals(CountBounds(2, 3), withReview(RelationshipReviewStatus.UNKNOWN))
    }

    @Test
    fun dedupCycleTerminatesAndCountsOnce() {
        val bounds =
            total(
                msg("synthetic-m1", "10:00:00") { dedup(DedupStatus.SAME_REPRESENTATION, "synthetic-m2") },
                msg("synthetic-m2", "10:00:10") { dedup(DedupStatus.SAME_REPRESENTATION, "synthetic-m1") },
                msg("synthetic-m3", "10:30:00"),
            )
        assertEquals(CountBounds(2, 2), bounds)
    }

    @Test
    fun chainedSameRepresentationIsOneContact() {
        val bounds =
            total(
                msg("synthetic-m1", "10:00:00"),
                msg("synthetic-m2", "10:00:10") { dedup(DedupStatus.SAME_REPRESENTATION, "synthetic-m1") },
                msg("synthetic-m3", "10:00:20") { dedup(DedupStatus.SAME_REPRESENTATION, "synthetic-m2") },
                msg("synthetic-m4", "11:00:00"),
            )
        assertEquals(CountBounds(2, 2), bounds)
    }

    @Test
    fun missingCanonicalTargetStillCountsOnce() {
        val bounds =
            total(
                msg("synthetic-m1", "10:00:00") { dedup(DedupStatus.SAME_REPRESENTATION, "synthetic-absent") },
                msg("synthetic-m2", "10:00:10") { dedup(DedupStatus.SAME_REPRESENTATION, "synthetic-absent") },
                msg("synthetic-m3", "11:00:00"),
            )
        assertEquals(CountBounds(2, 2), bounds)
    }

    @Test
    fun equalHashTimeAndLabelNeverMerge() {
        val hash = "a".repeat(64)
        val bounds =
            total(
                msg("synthetic-m1", "10:00:00") { sha256 = hash },
                msg("synthetic-m2", "10:00:00") { sha256 = hash },
                msg("synthetic-m3", "10:00:00") { sha256 = hash },
            )
        assertEquals(CountBounds(3, 3), bounds)
    }

    @Test
    fun manyLabelsOnOneEventStillOneContact() {
        val bounds =
            total(
                msg("synthetic-m1", "10:00:00") {
                    category(CategoryLabel.VERBAL_ABUSE)
                    category(CategoryLabel.INTIMIDATION)
                    category(CategoryLabel.EXPLICIT_THREAT)
                },
                msg("synthetic-m2", "11:00:00"),
            )
        assertEquals(CountBounds(2, 2), bounds)
    }

    @Test
    fun representativeIsEarliestMember() {
        val result =
            caseInput(
                listOf(
                    msg("synthetic-m1", "10:05:00") { dedup(DedupStatus.SAME_REPRESENTATION, "synthetic-m2") },
                    msg("synthetic-m2", "10:00:00"),
                    msg("synthetic-m3", "11:00:00"),
                ),
            ).analyse()
        val record = result.single(PatternType.REPEATED_CONTACT)
        assertEquals(
            listOf("synthetic-m2", "synthetic-m3"),
            record.supportingEvents.map { it.eventId.value },
        )
        assertEquals(
            listOf("synthetic-m1" to SupportRole.POSSIBLE_DUPLICATE),
            record.contextEvents.map { it.eventId.value to it.role },
        )
    }
}
