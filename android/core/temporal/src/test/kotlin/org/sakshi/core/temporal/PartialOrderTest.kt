package org.sakshi.core.temporal

import java.time.Duration
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.sakshi.core.model.BoundaryMarker
import org.sakshi.core.model.CommunicationStatus
import org.sakshi.core.temporal.fixtures.SyntheticTimelines

class PartialOrderTest {
    private fun stop(time: String) =
        msg("synthetic-stop", time) {
            boundary(BoundaryMarker.DO_NOT_CONTACT, "synthetic-t-a", CommunicationStatus.SUPPORTED_BY_SELECTED_EVIDENCE)
        }

    @Test
    fun overlappingIntervalsHaveUnknownOrderAndOnlyRaiseUpperBound() {
        val events =
            listOf(
                stop("10:00:00"),
                msg("synthetic-m1", "10:05:00"),
                msg("synthetic-m2", "10:06:00"),
                msg("synthetic-m3", "10:07:00"),
                msg("synthetic-m4", "10:00:00") { between("2026-10-01T09:59:00Z", "2026-10-01T10:01:00Z") },
            )
        val record = caseInput(events).analyse().single(PatternType.RECURRENCE_AFTER_BOUNDARY)
        assertEquals(CountBounds(3, 4), recurrence(record).afterBoundary)
        assertTrue(Limitation.TIME_UNCERTAIN in record.limitations)
        assertEquals(AssessmentStatus.SUPPORTED_DESCRIPTION, record.status)
    }

    @Test
    fun timelineMarksOnlyDefiniteOrder() {
        val result =
            caseInput(
                listOf(
                    msg("synthetic-m1", "10:00:00") { between("2026-10-01T10:00:00Z", "2026-10-01T10:10:00Z") },
                    msg("synthetic-m2", "10:00:00") { between("2026-10-01T10:05:00Z", "2026-10-01T10:06:00Z") },
                    msg("synthetic-m3", "11:00:00"),
                    msg("synthetic-m4", "10:00:00") { untimed() },
                ),
            ).analyse()
        assertEquals(listOf("synthetic-m1", "synthetic-m2", "synthetic-m3", "synthetic-m4"), result.timeline.map { it.eventId.value })
        assertEquals(listOf(false, false, true, false), result.timeline.map { it.orderCertainAfterPrevious })
    }

    @Test
    fun untimedEventsCountInTotalsButTakePartInNoWindow() {
        val record =
            caseInput(
                listOf(msg("synthetic-m1", "10:00:00"), msg("synthetic-m2", "10:05:00"), msg("synthetic-m3", "10:00:00") { untimed() }),
            ).analyse().single(PatternType.REPEATED_CONTACT)
        val m = repeated(record)
        assertEquals(CountBounds(3, 3), m.total)
        assertEquals(2, m.timedContacts)
        assertEquals(CountBounds(2, 2), m.windowMaxima.first().bounds)
        assertEquals(1, m.episodes)
        assertTrue(Limitation.TIME_UNCERTAIN in record.limitations)
    }

    @Test
    fun constantShiftLeavesCountsAndStatusesUnchanged() {
        for ((name, input) in SyntheticTimelines.all()) {
            val shifted = input.moved(Duration.ofDays(7), ZoneOffset.UTC)
            assertEquals(input.analyse().shape(), shifted.analyse().shape(), name)
        }
    }

    @Test
    fun differentOffsetsForTheSameInstantsGiveIdenticalResults() {
        for ((name, input) in SyntheticTimelines.all()) {
            val reoffset = input.moved(Duration.ZERO, ZoneOffset.ofHours(-8))
            assertEquals(input.analyse(), reoffset.analyse(), name)
        }
    }

    @Test
    fun dstChangeDoesNotAlterInstantsOrSpans() {
        val zone = ZoneId.of("America/New_York")
        val config = PatternConfig(zone = zone)
        val events =
            listOf(
                msg("synthetic-m1", "04:30:00") { at("2026-11-01T04:30:00Z") },
                msg("synthetic-m2", "06:30:00") { at("2026-11-01T06:30:00Z") },
            )
        val m = repeated(caseInput(events, config = config, cutoff = instant("2026-11-02T00:00:00Z")).analyse().single(PatternType.REPEATED_CONTACT))
        assertEquals(Duration.ofHours(2), m.span)
        assertEquals(1, m.uniqueDays)
        assertEquals(2, m.episodes)
        assertFalse(m.firstAt == null)
    }
}
