package org.sakshi.processing.analysis

import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.sakshi.core.database.SupportState
import org.sakshi.core.model.CategoryLabel
import org.sakshi.core.model.Direction
import org.sakshi.core.model.Event
import org.sakshi.core.model.OutgoingCoverage
import org.sakshi.core.model.SourceKind
import org.sakshi.core.model.TextStatus
import org.sakshi.core.model.TimeBasis
import org.sakshi.core.model.TimePrecision
import org.sakshi.processing.text.DateOrder
import org.sakshi.processing.text.WhatsAppExportParser

class ExportAnalysisTest : AnalysisTestBase() {
    private val kolkata: ZoneId = ZoneId.of("Asia/Kolkata")
    private val options = ExportOptions(DateOrder.DAY_MONTH, kolkata, SyntheticExports.OWNER)

    private fun bodies(list: List<Event>): List<String?> = runBlocking {
        val text = EventText(vault)
        list.map { text.bodyOf(it) }
    }

    @Test
    fun withoutOptionsOnlyTheDerivativeIsWritten() = runBlocking<Unit> {
        val evidenceId = importText(SyntheticExports.EIGHT_MESSAGES)
        val outcome = assertIs<AnalysisOutcome.NeedsExportOptions>(analysis.analyse(evidenceId))
        assertEquals(listOf(SyntheticExports.OTHER, SyntheticExports.OWNER), outcome.senders)
        assertEquals(DateOrder.DAY_MONTH, outcome.detectedDateOrder)
        assertEquals(8, outcome.recordCount)
        assertEquals(listOf("24/09/2026", "25/09/2026", "26/09/2026"), outcome.sampleDates)
        assertTrue(events().isEmpty())
        assertEquals(1, vault.derivatives.listForEvidence(evidenceId).size)
        assertEquals(SupportState.SAVED, vault.evidence.details(evidenceId)?.supportState)
    }

    @Test
    fun withOptionsEveryMessageBecomesAnEvent() = runBlocking<Unit> {
        val evidenceId = importText(SyntheticExports.EIGHT_MESSAGES)
        assertIs<AnalysisOutcome.NeedsExportOptions>(analysis.analyse(evidenceId))
        val outcome = assertIs<AnalysisOutcome.Analysed>(analysis.analyse(evidenceId, options))

        assertEquals(InputKind.WHATSAPP_EXPORT, outcome.kind)
        assertEquals(8, outcome.eventCount)
        assertEquals(2, outcome.suggestionCount)
        assertEquals(setOf(AnalysisWarning.SYSTEM_LINES_SKIPPED), outcome.warnings)
        assertEquals(1, vault.derivatives.listForEvidence(evidenceId).size)
        assertEquals(SupportState.ANALYZED, vault.evidence.details(evidenceId)?.supportState)

        val stored = events()
        assertSchemaValid(stored)
        val byBody = stored.zip(bodies(stored)).associate { (event, body) -> body to event }
        val insult = byBody.getValue("you are an idiot\nand this line continues\non a third line")
        assertEquals(listOf(CategoryLabel.VERBAL_ABUSE), insult.categories.map { it.label })
        val threat = byBody.getValue("I will hurt you if you reply")
        assertEquals(listOf(CategoryLabel.EXPLICIT_THREAT), threat.categories.map { it.label })
        assertEquals(2, stored.count { it.categories.isNotEmpty() })

        val text = EventText(vault)
        assertEquals("idiot", text.quote(insult, insult.evidenceReferences[1]))
        assertEquals("hurt you", text.quote(threat, threat.evidenceReferences[1]))
    }

    @Test
    fun directionsTimesAndSourceFollowTheRecords() = runBlocking<Unit> {
        val evidenceId = importText(SyntheticExports.EIGHT_MESSAGES)
        analysis.analyse(evidenceId, options)
        val stored = events()
        val byBody = stored.zip(bodies(stored)).associate { (event, body) -> body to event }

        val owner = byBody.getValue("hi, please stop sending these")
        assertEquals(Direction.OUTGOING, owner.direction)
        val other = byBody.getValue("hello there")
        assertEquals(Direction.INCOMING, other.direction)
        assertEquals(SyntheticExports.OTHER, other.sender.displayLabel)
        assertNull(other.sender.actorId)

        val insult = byBody.getValue("you are an idiot\nand this line continues\non a third line")
        assertEquals(TimeBasis.SOURCE_CLAIM, insult.timestamp.basis)
        assertEquals(TimePrecision.MINUTE, insult.timestamp.precision)
        assertEquals("Asia/Kolkata", insult.timestamp.sourceTimezone)
        assertEquals(Instant.parse("2026-09-25T02:45:00Z"), insult.timestamp.earliest?.instant)
        assertEquals(Instant.parse("2026-09-25T02:45:59.999Z"), insult.timestamp.latest?.instant)

        assertEquals(SourceKind.SELECTED_EXPORT, insult.source.kind)
        assertEquals("whatsapp-export-claim", insult.source.sourceApp)
        assertEquals(WhatsAppExportParser.VERSION, insult.source.parserVersion.value)
        assertEquals(OutgoingCoverage.INCLUDED_FOR_SELECTED_RANGE, insult.coverage.outgoingCoverage)
        assertEquals(1, stored.map { it.source.conversationScopeId }.distinct().size)
        assertEquals(8, stored.map { it.source.sourceRecordId }.distinct().size)
    }

    @Test
    fun mediaPlaceholderHasAbsentTextAndNoCategories() = runBlocking<Unit> {
        analysis.analyse(importText(SyntheticExports.EIGHT_MESSAGES), options)
        val media = events().single { it.coverage.textStatus == TextStatus.ABSENT }
        assertTrue(media.categories.isEmpty())
        assertEquals(1, media.evidenceReferences.size)
        assertEquals("<Media omitted>", EventText(vault).bodyOf(media))
    }

    @Test
    fun withoutAnOwnerClaimDirectionIsUnknown() = runBlocking<Unit> {
        analysis.analyse(importText(SyntheticExports.EIGHT_MESSAGES), options.copy(ownerSenderClaim = null))
        assertEquals(setOf(Direction.UNKNOWN), events().map { it.direction }.toSet())
        assertSchemaValid(events())
    }

    @Test
    fun ambiguousFileTakesTheGivenOrder() = runBlocking<Unit> {
        val evidenceId = importText(SyntheticExports.AMBIGUOUS)
        val needs = assertIs<AnalysisOutcome.NeedsExportOptions>(analysis.analyse(evidenceId))
        assertEquals(DateOrder.AMBIGUOUS, needs.detectedDateOrder)
        val outcome = assertIs<AnalysisOutcome.Analysed>(
            analysis.analyse(evidenceId, ExportOptions(DateOrder.MONTH_DAY, ZoneId.of("UTC"), null)),
        )
        assertTrue(AnalysisWarning.DATE_ORDER_OVERRIDDEN !in outcome.warnings)
        val first = events().first { it.timestamp.earliest?.instant == Instant.parse("2026-01-02T10:00:00Z") }
        assertEquals(Direction.UNKNOWN, first.direction)
    }

    @Test
    fun detectedOrderBeatsAContradictingChoiceWithAWarning() = runBlocking<Unit> {
        val evidenceId = importText(SyntheticExports.EIGHT_MESSAGES)
        val wrong = options.copy(dateOrder = DateOrder.MONTH_DAY)
        val outcome = assertIs<AnalysisOutcome.Analysed>(analysis.analyse(evidenceId, wrong))
        assertTrue(AnalysisWarning.DATE_ORDER_OVERRIDDEN in outcome.warnings)
        assertTrue(events().all { it.timestamp.basis == TimeBasis.SOURCE_CLAIM })
        assertTrue(events().any { it.timestamp.earliest?.instant == Instant.parse("2026-09-25T02:45:00Z") })
    }

    @Test
    fun unresolvableDateGivesUnknownTimeAndPartialState() = runBlocking<Unit> {
        val text = "31/02/2026, 10:00 - a: first\n30/09/2026, 10:00 - b: second\n"
        val evidenceId = importText(text)
        val outcome = assertIs<AnalysisOutcome.Analysed>(
            analysis.analyse(evidenceId, ExportOptions(DateOrder.DAY_MONTH, ZoneId.of("UTC"), null)),
        )
        assertEquals(setOf(AnalysisWarning.UNRESOLVED_TIMES), outcome.warnings)
        assertEquals(SupportState.PARTIAL, vault.evidence.details(evidenceId)?.supportState)
        val unknown = events().single { it.timestamp.basis == TimeBasis.UNKNOWN }
        assertNull(unknown.timestamp.earliest)
        assertEquals(TimePrecision.UNKNOWN, unknown.timestamp.precision)
        assertSchemaValid(events())
    }

    @Test
    fun recordLimitStopsAndWarns() = runBlocking<Unit> {
        val limited = TextAnalysis(vault, RulesEngineFactory.default(), clock, ids, AnalysisLimits(maxRecords = 3))
        val evidenceId = importText(SyntheticExports.large(10))
        val outcome = assertIs<AnalysisOutcome.Analysed>(limited.analyse(evidenceId, options))
        assertEquals(3, outcome.eventCount)
        assertTrue(AnalysisWarning.RECORD_LIMIT_REACHED in outcome.warnings)
        assertEquals(SupportState.PARTIAL, vault.evidence.details(evidenceId)?.supportState)
    }
}
