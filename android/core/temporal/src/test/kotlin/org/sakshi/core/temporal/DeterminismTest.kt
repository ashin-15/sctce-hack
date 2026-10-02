package org.sakshi.core.temporal

import java.time.Instant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import org.sakshi.core.temporal.fixtures.SyntheticTimelines

class DeterminismTest {
    @Test
    fun shufflingInputOrderGivesAnEqualResult() {
        for ((name, input) in SyntheticTimelines.all(EvidenceView.CANDIDATE_PREVIEW)) {
            val expected = input.analyse()
            for (seed in 1..5) {
                val random = Random(seed)
                val shuffled = input.copy(events = input.events.shuffled(random), gaps = input.gaps.shuffled(random))
                assertEquals(expected, shuffled.analyse(), "$name seed $seed")
            }
            assertEquals(expected, input.copy(events = input.events.reversed(), gaps = input.gaps.reversed()).analyse(), name)
        }
    }

    @Test
    fun shufflingTangledDuplicatesGivesAnEqualResult() {
        val events =
            listOf(
                msg("synthetic-m1", "10:00:00"),
                msg("synthetic-m2", "10:00:10") { dedup(org.sakshi.core.model.DedupStatus.POSSIBLE_DUPLICATE, "synthetic-m1") },
                msg("synthetic-m3", "10:00:20") { relate("synthetic-m2") },
                msg("synthetic-m4", "10:00:30") { dedup(org.sakshi.core.model.DedupStatus.SAME_REPRESENTATION, "synthetic-m3") },
                msg("synthetic-m5", "10:30:00"),
                msg("synthetic-m6", "10:30:00"),
            )
        val expected = caseInput(events).analyse()
        for (seed in 1..8) {
            assertEquals(expected, caseInput(events.shuffled(Random(seed))).analyse(), "seed $seed")
        }
    }

    @Test
    fun runningTwiceGivesTheSameResult() {
        for ((name, input) in SyntheticTimelines.all()) {
            assertEquals(input.analyse(), input.analyse(), name)
        }
    }

    @Test(timeout = 60_000)
    fun tenThousandEventsInOneScopeComplete() {
        val start = Instant.parse("2026-10-01T00:00:00Z")
        val events = (0 until 10_000).map { index ->
            msg("synthetic-big-$index", "00:00:00") { at(start.plusSeconds(60L * index).toString()) }
        }
        val input = caseInput(events, cutoff = Instant.parse("2026-10-20T00:00:00Z"))
        val result = input.analyse()
        assertEquals(10_000, result.contactEntries().size)
        assertEquals(CountBounds(10_000, 10_000), repeated(result.single(PatternType.REPEATED_CONTACT)).total)
        assertEquals(result, input.copy(events = events.reversed()).analyse())
    }
}
