package org.sakshi.core.vault

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.EventId
import org.sakshi.core.model.ReferenceId
import org.sakshi.core.model.ScopeId
import org.sakshi.core.model.TimeBasis
import org.sakshi.core.temporal.ActorScope
import org.sakshi.core.temporal.AssessmentStatus
import org.sakshi.core.temporal.CountBounds
import org.sakshi.core.temporal.EventRef
import org.sakshi.core.temporal.EvidenceView
import org.sakshi.core.temporal.Limitation
import org.sakshi.core.temporal.Measurements
import org.sakshi.core.temporal.PatternConfig
import org.sakshi.core.temporal.PatternRecord
import org.sakshi.core.temporal.PatternType
import org.sakshi.core.temporal.SupportRole
import org.sakshi.core.temporal.TemporalEngine
import org.sakshi.core.temporal.TemporalInput
import org.sakshi.core.temporal.fixtures.SyntheticTimelines

/** The content id and the column mapping of stored patterns, without a database. */
class PatternCodecTest {
    private fun records(): List<PatternRecord> = SyntheticTimelines.all().values.flatMap { input ->
        TemporalEngine.analyse(input.copy(config = PatternConfig(zone = SyntheticTimelines.zone))).patterns
    }

    private fun roundTrip(record: PatternRecord): PatternRecord {
        val encoded = PatternCodec.encode(record, "synthetic interpretation", Instant.parse("2026-10-02T11:00:00Z"))
        return PatternCodec.decode(encoded.entity, encoded.support)
    }

    @Test
    fun everyEngineRecordRoundTripsExactly() {
        val all = records()
        assertEquals(PatternType.entries.toSet(), all.map { it.type }.toSet())
        assertEquals(setOf(ActorScope.Confirmed::class, ActorScope.Unresolved::class), all.map { it.actorScope::class }.toSet())
        for (record in all) assertEquals(record, roundTrip(record), record.patternKey)
    }

    @Test
    fun handBuiltRecordsWithNullsContextEventsAndAllScopeShapesRoundTrip() {
        val base = records().first { it.type == PatternType.REPEATED_CONTACT }
        val untimed = (base.measurements as Measurements.RepeatedContact).copy(firstAt = null, lastAt = null, span = null, windowMaxima = emptyList())
        val variants = listOf(
            base.copy(windowStart = null, windowEnd = null, measurements = untimed, clockBases = emptySet(), limitations = emptySet(), gapIds = emptyList()),
            base.copy(
                contextEvents = listOf(
                    EventRef(EventId("synthetic-context-1"), 3, SupportRole.WANTED_CONTEXT),
                    EventRef(base.supportingEvents.first().eventId, base.supportingEvents.first().revision, SupportRole.BOUNDARY),
                ),
                actorScope = ActorScope.Unresolved(null, null, null),
                clockBases = setOf(TimeBasis.SOURCE_CLAIM, TimeBasis.USER_REPORTED),
                limitations = setOf(Limitation.DEMO_THRESHOLD, Limitation.COVERAGE_GAP),
                gapIds = listOf(ReferenceId("synthetic-gap-1"), ReferenceId("synthetic-gap-2")),
                status = AssessmentStatus.INSUFFICIENT_CONTEXT,
            ),
            base.copy(actorScope = ActorScope.Unresolved("synthetic-app", ScopeId("synthetic-conversation"), "Synthetic Label: with | separators")),
            base.copy(actorScope = ActorScope.Confirmed(ActorId("synthetic-actor-x"))),
        )
        for (record in variants) assertEquals(record, roundTrip(record))
    }

    @Test
    fun theSameDescriptionHasTheSameIdWhateverTheCutoffOrWording() {
        val record = records().first()
        val id = PatternCodec.id(record)
        assertEquals(64, id.length)
        assertEquals(id, PatternCodec.id(record.copy()))
        assertEquals(id, PatternCodec.id(record.copy(knowledgeCutoff = record.knowledgeCutoff.plusSeconds(3600))))
        assertEquals(id, PatternCodec.encode(record, "one wording", Instant.EPOCH).id)
        assertEquals(id, PatternCodec.encode(record, "another wording", Instant.parse("2026-10-05T00:00:00Z")).id)
    }

    @Test
    fun anyChangeInWhatTheDescriptionRestsOnGivesANewId() {
        val record = records().first { it.type == PatternType.REPEATED_CONTACT && it.supportingEvents.size > 1 }
        val id = PatternCodec.id(record)
        val first = record.supportingEvents.first()
        val repeated = record.measurements as Measurements.RepeatedContact
        val changed = listOf(
            "revision" to record.copy(supportingEvents = listOf(first.copy(revision = first.revision + 1)) + record.supportingEvents.drop(1)),
            "support role" to record.copy(supportingEvents = listOf(first.copy(role = SupportRole.POSSIBLE_DUPLICATE)) + record.supportingEvents.drop(1)),
            "support list" to record.copy(supportingEvents = record.supportingEvents.drop(1)),
            "context" to record.copy(contextEvents = listOf(first)),
            "measurement" to record.copy(measurements = repeated.copy(total = CountBounds(repeated.total.lower, repeated.total.upper + 1))),
            "limitation" to record.copy(limitations = record.limitations + Limitation.TIME_UNCERTAIN),
            "gap" to record.copy(gapIds = record.gapIds + ReferenceId("synthetic-gap-9")),
            "window" to record.copy(windowEnd = record.windowEnd?.plusSeconds(1) ?: Instant.EPOCH),
            "status" to record.copy(status = if (record.status == AssessmentStatus.CANDIDATE) AssessmentStatus.NOT_OBSERVED else AssessmentStatus.CANDIDATE),
            "view" to record.copy(view = EvidenceView.CANDIDATE_PREVIEW),
            "rule version" to record.copy(ruleVersion = record.ruleVersion + "-x"),
            "key" to record.copy(patternKey = record.patternKey + "x"),
            "scope" to record.copy(actorScope = ActorScope.Confirmed(ActorId("synthetic-someone-else"))),
            "case" to record.copy(caseId = org.sakshi.core.model.CaseId("synthetic-other-case")),
            "clock" to record.copy(clockBases = record.clockBases + TimeBasis.UNKNOWN),
        )
        for ((what, other) in changed) assertNotEquals(id, PatternCodec.id(other), what)
        assertEquals(changed.size, changed.map { PatternCodec.id(it.second) }.toSet().size, "each change gives its own id")
    }

    @Test
    fun supportRolesKeepKindAndEngineRoleInEngineOrder() {
        val record = records().first { it.type == PatternType.RECURRENCE_AFTER_BOUNDARY }
        val encoded = PatternCodec.encode(record, null, Instant.EPOCH)
        assertEquals(record.supportingEvents.size, encoded.support.size)
        assertTrue(encoded.support.all { it.role.startsWith("supporting:") })
        assertTrue(encoded.support.any { it.role == "supporting:boundary" })
        assertEquals(record.supportingEvents.map { it.eventId.value }, encoded.support.map { it.eventId })
    }
}
