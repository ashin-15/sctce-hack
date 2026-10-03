package org.sakshi.core.vault

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.sakshi.core.model.Direction
import org.sakshi.core.model.IdentityBasis
import org.sakshi.core.model.TextStatus

/** All values are synthetic. */
class NotificationClaimsTest : VaultTestBase() {
    private val claims = NotificationClaims(
        sourceAppClaim = "org.synthetic.chat",
        conversationScopeClaim = "scope-1",
        conversationTitleClaim = null,
        isGroupClaim = false,
        senderLabelClaim = "Sample Sender",
        identityBasis = IdentityBasis.APP_SCOPED_HINT,
        direction = Direction.INCOMING,
        textStatus = TextStatus.TRUNCATED,
        summaryOnly = false,
        sourceClaimTimeMs = 1_000L,
        sourceClaimTimeBasis = "message_timestamp",
        notificationPostTimeMs = 2_000L,
        collectorWallMs = 9_000L,
        collectorElapsedRealtimeMs = 500L,
        collectorSessionId = "session-1",
        removalReasonCode = 8,
        removalObservedWallMs = 9_500L,
        corroboratingObservations = 2,
        observedAsActiveSnapshot = true,
    )

    @Test
    fun vocabularyKnowsTheNotificationKindAndAccessClass() {
        assertEquals("notification_excerpt", AcquisitionKind.NOTIFICATION_EXCERPT)
        assertTrue(AcquisitionKind.NOTIFICATION_EXCERPT in AcquisitionKind.all)
        assertTrue(AccessClass.NOTIFICATION_OBSERVATION in AccessClass.all)
    }

    @Test
    fun claimsRoundTripThroughJsonAndKeepBothTimes() {
        val json = claims.toJson()
        assertTrue(json.contains("\"${NotificationClaims.ROOT_KEY}\""))
        assertEquals(claims, NotificationClaims.decode(json))
    }

    @Test
    fun damagedOrForeignJsonDecodesToNull() {
        assertNull(NotificationClaims.decode(null))
        assertNull(NotificationClaims.decode("not json"))
        assertNull(NotificationClaims.decode("{\"other\":{}}"))
        assertNull(NotificationClaims.decode("{\"notification_claims\":{\"direction\":\"sideways\"}}"))
    }

    @Test
    fun vaultStoresTheKindAndTheClaimsWithoutSchemaChange() = runBlocking<Unit> {
        val caseId = cases.create("Synthetic").id
        val request = ImportRequest(
            caseId, AcquisitionKind.NOTIFICATION_EXCERPT, AccessClass.NOTIFICATION_OBSERVATION, "notification_listener",
            "text/plain", claims.sourceAppClaim, null, null, 10_000L, claims.toJson(),
        )
        val id = evidence.import(request, ByteArrayInputStream("synthetic".toByteArray())).id
        val details = assertNotNull(evidence.details(id))
        assertEquals(AcquisitionKind.NOTIFICATION_EXCERPT, details.acquisitionKind)
        assertEquals(AccessClass.NOTIFICATION_OBSERVATION, details.accessClass)
        assertEquals(claims, NotificationClaims.decode(details.captureClaimsJson))
    }
}
