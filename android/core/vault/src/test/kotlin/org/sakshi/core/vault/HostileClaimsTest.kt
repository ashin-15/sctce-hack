package org.sakshi.core.vault

import java.io.ByteArrayInputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** Names, origins and authorities supplied by a source are claims: stored as received, never used to build a path. */
class HostileClaimsTest : VaultTestBase() {
    private val hostile = listOf(
        "../../databases/sakshi.db",
        "/etc/passwd",
        "..\\..\\windows\\system32",
        "evil\u0000.txt",
        "line one\nline two\r\nX-Injected: yes",
        "‮fdp.exe",
        "a".repeat(100_000),
        "con",
        "sub/dir/name.png",
        "~/.ssh/id_rsa",
        "file:///data/data/org.sakshi.app/x",
    )

    private fun filesUnderAppData(): Set<String> =
        context.dataDir.walkTopDown().filter { it.isFile }.map { it.absolutePath }.toSet()

    @Test
    fun claimsAreStoredAsReceivedAndNeverInfluenceAnyPath() = runBlocking<Unit> {
        val caseId = cases.create("Synthetic").id
        val before = filesUnderAppData()
        val ids = hostile.map { claim ->
            val request = ImportRequest(
                caseId, AcquisitionKind.SHARED_STREAM, AccessClass.USER_MEDIATED, "synthetic-test", "application/octet-stream",
                claim, claim, claim, 1_000_000L,
            )
            evidence.import(request, ByteArrayInputStream(ByteArray(32) { claim.length.toByte() })).id
        }

        ids.zip(hostile).forEach { (id, claim) ->
            val details = assertNotNull(evidence.details(id))
            assertEquals(claim, details.displayNameClaim)
            assertEquals(claim, details.claimedOrigin)
            assertEquals(claim, details.uriAuthorityClaim)
        }
        val created = filesUnderAppData() - before
        val pattern = Regex("[0-9a-f]{32}\\.skb")
        assertEquals(hostile.size, created.size)
        for (path in created) {
            val file = File(path)
            assertTrue(pattern.matches(file.name), "unexpected file name ${file.name}")
            assertEquals(blobDirectory.absoluteFile, file.parentFile?.absoluteFile, "file created outside the blob directory")
        }
    }

    @Test
    fun noClaimAppearsInAnyStoredFileName() = runBlocking<Unit> {
        val caseId = cases.create("Synthetic").id
        val request = ImportRequest(
            caseId, AcquisitionKind.SHARED_STREAM, AccessClass.USER_MEDIATED, "synthetic-test", null,
            null, "synthetic-visible-name.png", "synthetic.authority", 1_000L,
        )
        evidence.import(request, ByteArrayInputStream(ByteArray(8)))
        val allNames = context.dataDir.walkTopDown().map { it.name }.toList()
        assertTrue(allNames.none { it.contains("synthetic-visible-name") }, "a claimed name was used as a file name")
    }
}
