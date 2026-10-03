package org.sakshi.core.vault

import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.Before
import org.sakshi.core.model.CaseId
import org.sakshi.core.temporal.EvidenceView
import org.sakshi.core.temporal.PatternConfig
import org.sakshi.core.temporal.PatternRecord
import org.sakshi.core.temporal.TemporalEngine
import org.sakshi.core.temporal.TemporalInput
import org.sakshi.core.temporal.fixtures.SyntheticTimelines

/** Stored-pattern tests over the synthetic timelines A to F. All data is synthetic. */
abstract class PatternTestBase : ReviewTestBase() {
    protected lateinit var patterns: PatternStore

    /** The pattern store's clock; events and decisions use the fixed vault clock. */
    protected var patternNow: Instant = Instant.parse("2026-10-02T11:00:00Z")

    @Before
    fun openPatterns() {
        patterns = PatternStore(db, store, actors, recording, { patternNow }, ids, Dispatchers.IO)
    }

    /** Creates the timeline's case and actors and stores its events in one batch. */
    protected suspend fun load(input: TemporalInput, vararg actorIds: String) {
        insertCase(input.caseId.value)
        actorIds.forEach { insertActor(input.caseId.value, it) }
        assertEquals(input.events.size, (store.saveAll(input.events) as BatchSaveResult.Saved).count)
    }

    /** Runs the real engine, in the synthetic zone, over what the store read. */
    protected suspend fun recompute(caseId: CaseId, view: EvidenceView = EvidenceView.CONFIRMED_ONLY): List<StoredPattern> =
        patterns.recompute(caseId, view) { inputs -> engine(inputs).map { ComputedPattern(it, "synthetic interpretation of ${it.type}") } }

    protected fun engine(inputs: PatternInputs): List<PatternRecord> =
        TemporalEngine.analyse(
            TemporalInput(inputs.caseId, inputs.events, inputs.gaps, inputs.now, inputs.view, PatternConfig(zone = SyntheticTimelines.zone)),
        ).patterns

    protected suspend fun staleCount(caseId: CaseId): Int = withContext(Dispatchers.IO) {
        db.query("SELECT COUNT(*) FROM pattern WHERE case_id = ? AND assessment_status = 'stale'", arrayOf(caseId.value)).use {
            assertTrue(it.moveToFirst())
            it.getInt(0)
        }
    }

    protected suspend fun count(table: String): Int = withContext(Dispatchers.IO) {
        db.query("SELECT COUNT(*) FROM $table", null).use {
            assertTrue(it.moveToFirst())
            it.getInt(0)
        }
    }

    /** Stores [input]'s patterns, runs [change], and checks every pattern went stale and a recompute clears it. */
    protected suspend fun assertWriteMarksStale(input: TemporalInput, vararg actorIds: String, change: suspend () -> Unit) {
        load(input, *actorIds)
        val before = recompute(input.caseId)
        assertTrue(before.isNotEmpty())
        assertTrue(before.none { it.stale })

        change()

        val after = patterns.list(input.caseId, EvidenceView.CONFIRMED_ONLY)
        assertTrue(after.isNotEmpty())
        assertTrue(after.all { it.stale }, "every pattern of the case must be stale after the write")
        recompute(input.caseId)
        assertEquals(0, staleCount(input.caseId))
    }
}
