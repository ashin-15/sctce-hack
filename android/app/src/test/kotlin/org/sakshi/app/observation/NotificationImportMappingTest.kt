package org.sakshi.app.observation

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.sakshi.acquisition.notifications.CollectorTime
import org.sakshi.acquisition.notifications.ConversationScopeClaim
import org.sakshi.acquisition.notifications.ObservedCandidate
import org.sakshi.acquisition.notifications.ObservedDirection
import org.sakshi.acquisition.notifications.ObservedMessage
import org.sakshi.acquisition.notifications.ObservedTextStatus
import org.sakshi.acquisition.notifications.RemovalLifecycle
import org.sakshi.acquisition.notifications.SenderLabelSource
import org.sakshi.acquisition.notifications.SnapshotOrigin
import org.sakshi.acquisition.notifications.SourceClaimTime
import org.sakshi.acquisition.notifications.SourceClaimTimeBasis
import org.sakshi.core.model.Direction
import org.sakshi.core.model.IdentityBasis
import org.sakshi.core.model.TextStatus
import org.sakshi.core.vault.NotificationClaims

class NotificationImportMappingTest {
    @Test
    fun `saved excerpt retains source claims and distinct collector clocks`() {
        val mapped = candidate().toImport()
        assertEquals(" exact original text\n", mapped.text)
        val claims = mapped.claims
        assertEquals("com.example.messaging", claims.sourceAppClaim)
        assertEquals("scope-claim", claims.conversationScopeClaim)
        assertEquals("Conversation claim", claims.conversationTitleClaim)
        assertTrue(claims.isGroupClaim == true)
        assertEquals("Sender claim", claims.senderLabelClaim)
        assertEquals(IdentityBasis.APP_SCOPED_HINT, claims.identityBasis)
        assertEquals(Direction.INCOMING, claims.direction)
        assertEquals(TextStatus.AVAILABLE, claims.textStatus)
        assertFalse(claims.summaryOnly)
        assertEquals(900L, claims.sourceClaimTimeMs)
        assertEquals("message_timestamp", claims.sourceClaimTimeBasis)
        assertEquals(1000L, claims.notificationPostTimeMs)
        assertEquals(2000L, claims.collectorWallMs)
        assertEquals(30L, claims.collectorElapsedRealtimeMs)
        assertEquals("collector-session", claims.collectorSessionId)
        assertEquals(7, claims.removalReasonCode)
        assertEquals(3000L, claims.removalObservedWallMs)
        assertEquals(2, claims.corroboratingObservations)
        assertTrue(claims.observedAsActiveSnapshot)
        assertEquals(claims, NotificationClaims.decode(claims.toJson()))
    }

    @Test
    fun `summary and unknown sender are retained without invented identity or time`() {
        val original = candidate()
        val mapped = original.copy(
            message = original.message.copy(
                senderLabel = null,
                senderLabelSource = SenderLabelSource.NONE,
                textStatus = ObservedTextStatus.SUMMARY_ONLY,
                direction = ObservedDirection.UNKNOWN,
                sourceClaimTime = SourceClaimTime(null, SourceClaimTimeBasis.NONE),
                origin = SnapshotOrigin.LIVE,
            ),
            removal = null,
        ).toImport()
        assertTrue(mapped.claims.summaryOnly)
        assertEquals(TextStatus.EXTRACTION_UNCERTAIN, mapped.claims.textStatus)
        assertEquals(IdentityBasis.UNKNOWN, mapped.claims.identityBasis)
        assertEquals(Direction.UNKNOWN, mapped.claims.direction)
        assertNull(mapped.claims.senderLabelClaim)
        assertNull(mapped.claims.sourceClaimTimeMs)
        assertNull(mapped.claims.removalReasonCode)
        assertNull(mapped.claims.removalObservedWallMs)
        assertFalse(mapped.claims.observedAsActiveSnapshot)
    }

    private fun candidate() = ObservedCandidate(
        id = "candidate-id",
        message = ObservedMessage(
            sourceAppClaim = "com.example.messaging",
            conversation = ConversationScopeClaim(
                "com.example.messaging", "scope-claim", "notification-key", null, null,
                "Conversation claim", true,
            ),
            senderLabel = "Sender claim",
            senderLabelSource = SenderLabelSource.MESSAGE_SENDER,
            identityBasis = IdentityBasis.APP_SCOPED_HINT,
            text = " exact original text\n",
            textStatus = ObservedTextStatus.COMPLETE,
            direction = ObservedDirection.INCOMING_HINT,
            sourceClaimTime = SourceClaimTime(900L, SourceClaimTimeBasis.MESSAGE_TIMESTAMP),
            notificationPostTimeMs = 1000L,
            collector = CollectorTime(2000L, 30L, "collector-session"),
            origin = SnapshotOrigin.ACTIVE_SNAPSHOT,
        ),
        corroboratingObservations = 2,
        removal = RemovalLifecycle(7, 3000L),
    )
}
