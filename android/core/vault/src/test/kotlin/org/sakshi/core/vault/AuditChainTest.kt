package org.sakshi.core.vault

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.sakshi.core.database.AuditRecordEntity
import org.sakshi.core.integrity.HashChain
import org.sakshi.core.integrity.Sha256

class AuditChainTest {
    private fun chain(count: Int): List<AuditRecordEntity> {
        var previous = HashChain.genesis()
        return (1..count).map { index ->
            val digest = Sha256.digest("synthetic-payload-$index".toByteArray())
            val next = HashChain.next(previous, digest)
            AuditRecordEntity(
                seq = index.toLong(),
                at = "2026-10-02T10:00:00Z",
                atEpochMs = 1L,
                action = "synthetic.action",
                subjectType = "synthetic",
                subjectId = "synthetic-$index",
                payloadSha256 = Sha256.hex(digest),
                prevHash = previous,
                thisHash = next,
            ).also { previous = next }
        }
    }

    @Test
    fun emptyChainIsValidWithGenesisHead() {
        val result = assertIs<AuditVerification.Valid>(AuditChain.verify(emptyList()))
        assertEquals(0, result.count)
        assertContentEquals(HashChain.genesis(), result.head)
    }

    @Test
    fun intactChainIsValid() {
        val rows = chain(5)
        val result = assertIs<AuditVerification.Valid>(AuditChain.verify(rows))
        assertEquals(5, result.count)
        assertContentEquals(rows.last().thisHash, result.head)
    }

    @Test
    fun changedPayloadDigestIsBroken() {
        val rows = chain(4).toMutableList()
        rows[1] = rows[1].copy(payloadSha256 = Sha256.hex(Sha256.digest("forged".toByteArray())))
        assertEquals(AuditVerification.Broken(2), AuditChain.verify(rows))
    }

    @Test
    fun malformedDigestIsBroken() {
        val rows = chain(3).toMutableList()
        rows[2] = rows[2].copy(payloadSha256 = "not-hex")
        assertEquals(AuditVerification.Broken(3), AuditChain.verify(rows))
    }

    @Test
    fun swappedRowsAreBroken() {
        val rows = chain(4).toMutableList()
        val first = rows[1]
        rows[1] = rows[2]
        rows[2] = first
        assertEquals(AuditVerification.Broken(3), AuditChain.verify(rows))
    }

    @Test
    fun droppedRowIsBroken() {
        val rows = chain(4).toMutableList()
        rows.removeAt(1)
        assertEquals(AuditVerification.Broken(3), AuditChain.verify(rows))
    }

    @Test
    fun droppingTheFirstRowIsBroken() {
        assertEquals(AuditVerification.Broken(2), AuditChain.verify(chain(3).drop(1)))
    }
}
