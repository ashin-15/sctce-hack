package org.sakshi.core.vault

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.sakshi.core.database.SupportState
import org.sakshi.core.integrity.Sha256

class ReadApiTest : VaultTestBase() {
    @Test
    fun auditRecordsAndCountMirrorTheStoredChain() = runBlocking<Unit> {
        assertEquals(0, audit.count())
        assertEquals(emptyList(), audit.records())
        val caseId = cases.create("Synthetic").id
        val imported = evidence.import(request(caseId), ByteArrayInputStream(ByteArray(30)))

        val records = audit.records()
        assertEquals(2, audit.count())
        assertEquals(listOf(1L, 2L), records.map { it.seq })
        assertEquals(listOf("case.created", "evidence.imported"), records.map { it.action })
        assertEquals(listOf("case", "evidence"), records.map { it.subjectType })
        assertEquals(listOf(caseId, imported.id), records.map { it.subjectId })
        records.forEach {
            assertTrue(Regex("[0-9a-f]{64}").matches(it.payloadSha256Hex))
            assertTrue(Regex("[0-9a-f]{64}").matches(it.thisHashHex))
            assertEquals("2026-10-02T10:00:00Z", it.at)
        }
        assertEquals(Sha256.hex(audit.head()), records.last().thisHashHex)
    }

    @Test
    fun detailsReturnStoredRowsWithClaimsAsReceived() = runBlocking<Unit> {
        val caseId = cases.create("Synthetic").id
        val imported = evidence.import(request(caseId, declaredMime = "image/jpeg"), ByteArrayInputStream(ByteArray(30)))

        val details = evidence.details(imported.id)!!
        assertEquals(
            EvidenceDetails(
                id = imported.id,
                caseId = caseId,
                acquisitionKind = AcquisitionKind.SHARED_STREAM,
                accessClass = AccessClass.USER_MEDIATED,
                receivedAt = "2026-10-02T10:00:00Z",
                declaredMime = "image/jpeg",
                detectedMime = null,
                byteSize = 30L,
                sha256 = imported.sha256,
                supportState = SupportState.SAVED,
                importerMechanism = "synthetic-test",
                claimedOrigin = "synthetic-origin",
                displayNameClaim = "synthetic-name.bin",
                uriAuthorityClaim = "synthetic.authority",
            ),
            details,
        )
        assertNull(evidence.details("synthetic-missing"))
    }

    @Test
    fun setSupportStateValidatesAndUpdates() = runBlocking<Unit> {
        val caseId = cases.create("Synthetic").id
        val imported = evidence.import(request(caseId), ByteArrayInputStream(ByteArray(3)))

        evidence.setSupportState(imported.id, SupportState.ANALYZED)
        assertEquals(SupportState.ANALYZED, evidence.details(imported.id)!!.supportState)
        assertFailsWith<IllegalArgumentException> { evidence.setSupportState(imported.id, "bogus") }
        assertFailsWith<IllegalArgumentException> { evidence.setSupportState("synthetic-missing", SupportState.FAILED) }
        assertEquals(SupportState.ANALYZED, evidence.details(imported.id)!!.supportState)
    }

    @Test
    fun everySupportStateConstantIsAccepted() = runBlocking<Unit> {
        val caseId = cases.create("Synthetic").id
        val imported = evidence.import(request(caseId), ByteArrayInputStream(ByteArray(3)))
        val constants = SupportState::class.java.declaredFields
            .filter { java.lang.reflect.Modifier.isStatic(it.modifiers) && it.type == String::class.java }
            .map { it.get(null) as String }
        assertTrue(constants.size >= 7)
        constants.forEach {
            evidence.setSupportState(imported.id, it)
            assertEquals(it, evidence.details(imported.id)!!.supportState)
        }
    }
}
