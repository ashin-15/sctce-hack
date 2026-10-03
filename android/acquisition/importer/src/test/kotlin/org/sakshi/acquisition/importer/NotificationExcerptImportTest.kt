package org.sakshi.acquisition.importer

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.sakshi.core.model.Direction
import org.sakshi.core.model.IdentityBasis
import org.sakshi.core.model.TextStatus
import org.sakshi.core.vault.AccessClass
import org.sakshi.core.vault.AcquisitionKind
import org.sakshi.core.vault.NotificationClaims

/** All values are synthetic. */
class NotificationExcerptImportTest : ImporterTestBase() {
    private fun claims(summaryOnly: Boolean = false, status: TextStatus = TextStatus.AVAILABLE) = NotificationClaims(
        sourceAppClaim = "org.synthetic.chat",
        conversationScopeClaim = "scope-1",
        conversationTitleClaim = "Synthetic group",
        isGroupClaim = true,
        senderLabelClaim = "Sample Sender",
        identityBasis = IdentityBasis.APP_SCOPED_HINT,
        direction = Direction.INCOMING,
        textStatus = status,
        summaryOnly = summaryOnly,
        sourceClaimTimeMs = 1_700_000_000_000L,
        sourceClaimTimeBasis = "message_timestamp",
        notificationPostTimeMs = 1_700_000_000_500L,
        collectorWallMs = 1_700_000_900_000L,
        collectorElapsedRealtimeMs = 12_345L,
        collectorSessionId = "session-1",
        removalReasonCode = null,
        removalObservedWallMs = null,
        corroboratingObservations = 1,
        observedAsActiveSnapshot = false,
    )

    private fun commit(text: String, claims: NotificationClaims = claims()): ItemOutcome =
        runBlocking { importer().commitNotificationExcerpt(caseId, NotificationExcerptImport(text, claims)) }

    @Test
    fun storesExactBytesAccessClassOriginAndClaims() = runBlocking<Unit> {
        val text = "synthetic ഇത് यह परीक्षण 😀 end\n"
        val saved = assertIs<ItemOutcome.Saved>(commit(text))
        assertContentEquals(text.toByteArray(Charsets.UTF_8), original(saved.evidenceId))
        assertEquals("text/plain; charset=utf-8", saved.declaredMime)
        assertEquals(AnalysisState.READY_FOR_TEXT_ANALYSIS, saved.analysisState)

        val details = assertNotNull(vault.evidence.details(saved.evidenceId))
        assertEquals(AcquisitionKind.NOTIFICATION_EXCERPT, details.acquisitionKind)
        assertEquals(AccessClass.NOTIFICATION_OBSERVATION, details.accessClass)
        assertEquals("notification_listener", details.importerMechanism)
        assertEquals("org.synthetic.chat", details.claimedOrigin)

        val stored = assertNotNull(NotificationClaims.decode(details.captureClaimsJson))
        assertEquals(claims(), stored)
        assertEquals(IdentityBasis.APP_SCOPED_HINT, stored.identityBasis)
        assertEquals(1_700_000_000_000L, stored.sourceClaimTimeMs)
        assertEquals(1_700_000_900_000L, stored.collectorWallMs)
    }

    @Test
    fun truncatedExcerptIsStillAnalysable() {
        val saved = assertIs<ItemOutcome.Saved>(commit("cut off text", claims(status = TextStatus.TRUNCATED)))
        assertEquals(AnalysisState.READY_FOR_TEXT_ANALYSIS, saved.analysisState)
        val stored = NotificationClaims.decode(runBlocking { vault.evidence.details(saved.evidenceId) }?.captureClaimsJson)
        assertEquals(TextStatus.TRUNCATED, stored?.textStatus)
    }

    @Test
    fun summaryOnlyExcerptIsPreservedButNotAnalysable() {
        val saved = assertIs<ItemOutcome.Saved>(commit("3 new messages", claims(summaryOnly = true, status = TextStatus.EXTRACTION_UNCERTAIN)))
        assertEquals(AnalysisState.PRESERVED_NOT_ANALYSED, saved.analysisState)
        assertEquals(1, rowCount())
        val stored = NotificationClaims.decode(runBlocking { vault.evidence.details(saved.evidenceId) }?.captureClaimsJson)
        assertEquals(true, stored?.summaryOnly)
    }

    @Test
    fun theSameExcerptTwiceIsReportedAsDuplicateBytes() {
        val first = assertIs<ItemOutcome.Saved>(commit("same words"))
        assertTrue(first.duplicateOf.isEmpty())
        val second = assertIs<ItemOutcome.Saved>(commit("same words"))
        assertEquals(listOf(first.evidenceId), second.duplicateOf)
        assertEquals(2, rowCount())
    }

    @Test
    fun emptyAndOverlongTextAreSkippedWithoutStoringAnything() {
        assertEquals(ItemOutcome.Skipped(0, Rejection.EMPTY_TEXT), commit(""))
        val limits = ImportLimits(maxTextChars = 5)
        val outcome = runBlocking {
            importer(limits).commitNotificationExcerpt(caseId, NotificationExcerptImport("123456", claims()))
        }
        assertEquals(ItemOutcome.Skipped(0, Rejection.TEXT_TOO_LONG), outcome)
        assertEquals(0, rowCount())
        assertNoBlobs()
    }
}
