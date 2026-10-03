package org.sakshi.processing.analysis

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import org.sakshi.core.database.SupportState
import org.sakshi.core.model.CoverageContext
import org.sakshi.core.model.Direction
import org.sakshi.core.model.IdentityBasis
import org.sakshi.core.model.OutgoingCoverage
import org.sakshi.core.model.SourceKind
import org.sakshi.core.model.TextStatus
import org.sakshi.core.model.TimeBasis
import org.sakshi.core.vault.AccessClass
import org.sakshi.core.vault.AcquisitionKind
import org.sakshi.core.vault.ImportRequest
import org.sakshi.core.vault.NotificationClaims

/** All values are synthetic. */
class NotificationExcerptAnalysisTest : AnalysisTestBase() {
    private val sourceMs = 1_700_000_000_000L
    private val collectorMs = 1_700_000_900_000L

    private fun claims(
        status: TextStatus = TextStatus.AVAILABLE,
        summaryOnly: Boolean = false,
        direction: Direction = Direction.INCOMING,
        sourceTime: Long? = sourceMs,
        sender: String? = "Sample Sender",
    ) = NotificationClaims(
        "org.synthetic.chat", "scope-1", null, false, sender,
        if (sender == null) IdentityBasis.UNKNOWN else IdentityBasis.APP_SCOPED_HINT,
        direction, status, summaryOnly, sourceTime, "message_timestamp", sourceMs, collectorMs, 4_242L, "session-1",
        null, null, 0, false,
    )

    private fun import(text: String, claims: NotificationClaims, withClaims: Boolean = true): String = runBlocking {
        val request = ImportRequest(
            caseId, AcquisitionKind.NOTIFICATION_EXCERPT, AccessClass.NOTIFICATION_OBSERVATION, "notification_listener",
            "text/plain; charset=utf-8", claims.sourceAppClaim, null, null, 1_000_000L,
            if (withClaims) claims.toJson() else null,
        )
        vault.evidence.import(request, text.byteInputStream()).id
    }

    @Test
    fun completeExcerptBecomesOneSchemaValidNotificationEvent() = runBlocking<Unit> {
        val id = import("you are worthless", claims())
        val outcome = assertIs<AnalysisOutcome.Analysed>(analysis.analyse(id))
        assertEquals(InputKind.PLAIN_TEXT, outcome.kind)
        val event = events().single()
        assertSchemaValid(listOf(event))

        assertEquals(SourceKind.NOTIFICATION_EXCERPT, event.source.kind)
        assertEquals("org.synthetic.chat", event.source.sourceApp)
        assertEquals("scope-1", event.source.conversationScopeId?.value)
        assertEquals("Sample Sender", event.sender.displayLabel)
        assertEquals(IdentityBasis.APP_SCOPED_HINT, event.sender.identityBasis)
        assertNull(event.sender.actorId)
        assertEquals(Direction.INCOMING, event.direction)
        assertEquals(TimeBasis.SOURCE_CLAIM, event.timestamp.basis)
        assertEquals(Instant.ofEpochMilli(sourceMs).toString(), event.timestamp.earliest?.iso)
        assertEquals(event.timestamp.earliest, event.timestamp.latest)
        assertEquals("session-1", event.timestamp.collectorSessionId?.value)
        assertEquals(4_242L, event.timestamp.monotonicMs)
        assertEquals(CoverageContext.NOTIFICATION_PARTIAL, event.coverage.context)
        assertEquals(TextStatus.AVAILABLE, event.coverage.textStatus)
        assertEquals(OutgoingCoverage.UNKNOWN, event.coverage.outgoingCoverage)
        assertEquals(SupportState.ANALYZED, vault.evidence.details(id)?.supportState)
        assertEquals("you are worthless", EventText(vault).bodyOf(event))
    }

    @Test
    fun truncatedExcerptKeepsTheTruncatedTextStatus() = runBlocking<Unit> {
        val id = import("synthetic cut tex", claims(status = TextStatus.TRUNCATED))
        assertIs<AnalysisOutcome.Analysed>(analysis.analyse(id))
        val event = events().single()
        assertSchemaValid(listOf(event))
        assertEquals(TextStatus.TRUNCATED, event.coverage.textStatus)
    }

    @Test
    fun outgoingClaimIsNeverCarriedAndMissingSenderStaysUnknown() = runBlocking<Unit> {
        val id = import("synthetic", claims(direction = Direction.OUTGOING, sender = null))
        assertIs<AnalysisOutcome.Analysed>(analysis.analyse(id))
        val event = events().single()
        assertSchemaValid(listOf(event))
        assertEquals(Direction.UNKNOWN, event.direction)
        assertEquals(IdentityBasis.UNKNOWN, event.sender.identityBasis)
        assertEquals(OutgoingCoverage.UNKNOWN, event.coverage.outgoingCoverage)
    }

    @Test
    fun missingSourceClaimTimeGivesUnknownBasisButKeepsCollectorSession() = runBlocking<Unit> {
        val id = import("synthetic", claims(sourceTime = null))
        assertIs<AnalysisOutcome.Analysed>(analysis.analyse(id))
        val event = events().single()
        assertSchemaValid(listOf(event))
        assertEquals(TimeBasis.UNKNOWN, event.timestamp.basis)
        assertNull(event.timestamp.earliest)
        assertEquals("session-1", event.timestamp.collectorSessionId?.value)
    }

    @Test
    fun exportShapedExcerptStaysOneEvent() = runBlocking<Unit> {
        val text = "12/03/2026, 10:00 - A: you idiot\n12/03/2026, 10:01 - B: no"
        val id = import(text, claims())
        val outcome = assertIs<AnalysisOutcome.Analysed>(analysis.analyse(id))
        assertEquals(1, outcome.eventCount)
        assertEquals(SourceKind.NOTIFICATION_EXCERPT, events().single().source.kind)
    }

    @Test
    fun summaryOnlyExcerptIsRefusedAndWritesNoEvent() = runBlocking<Unit> {
        val id = import("3 new messages", claims(summaryOnly = true, status = TextStatus.EXTRACTION_UNCERTAIN))
        val outcome = assertIs<AnalysisOutcome.NotAnalysable>(analysis.analyse(id))
        assertEquals(NotAnalysableReason.PRESERVE_ONLY_TYPE, outcome.reason)
        assertEquals(emptyList(), events())
    }

    @Test
    fun excerptWithoutReadableClaimsIsRefused() = runBlocking<Unit> {
        val id = import("synthetic", claims(), withClaims = false)
        val outcome = assertIs<AnalysisOutcome.NotAnalysable>(analysis.analyse(id))
        assertEquals(NotAnalysableReason.UNREADABLE, outcome.reason)
    }
}
