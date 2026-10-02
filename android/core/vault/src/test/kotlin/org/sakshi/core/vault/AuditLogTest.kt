package org.sakshi.core.vault

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking
import org.sakshi.core.integrity.HashChain

class AuditLogTest : VaultTestBase() {
    @Test
    fun emptyLogHasGenesisHeadAndVerifies() = runBlocking<Unit> {
        assertContentEquals(HashChain.genesis(), audit.head())
        assertEquals(0, assertIs<AuditVerification.Valid>(audit.verify()).count)
    }

    @Test
    fun severalAppendsFormAValidChainAndTheHeadAdvances() = runBlocking<Unit> {
        val heads = mutableListOf(audit.head())
        repeat(4) { index ->
            audit.append(AuditActions.CASE_CREATED, "case", "synthetic-$index", jsonObjectOf("n" to index))
            heads += audit.head()
        }
        assertEquals(heads.size, heads.map { it.toList() }.toSet().size)
        val result = assertIs<AuditVerification.Valid>(audit.verify())
        assertEquals(4, result.count)
        assertContentEquals(audit.head(), result.head)
    }

    @Test
    fun sameActionOnDifferentSubjectsGivesDifferentRecords() = runBlocking<Unit> {
        audit.append(AuditActions.CASE_CREATED, "case", "synthetic-a")
        val afterFirst = audit.head()
        audit.append(AuditActions.CASE_CREATED, "case", "synthetic-b")
        assertFalse(afterFirst.contentEquals(audit.head()))
    }
}
