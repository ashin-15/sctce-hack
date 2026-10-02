package org.sakshi.export.bundle

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WriterRefusalTest {
    @get:Rule
    val tmp: TemporaryFolder = TemporaryFolder()

    private val signer: SoftwareP256Signer = SoftwareP256Signer.generate()

    private fun refuse(content: BundleContent, target: Path = tmp.newFolder().toPath()): IllegalArgumentException {
        val error = assertFailsWith<IllegalArgumentException> { BundleWriter.write(content, target, signer) }
        Files.newDirectoryStream(target).use { assertTrue(!it.iterator().hasNext(), "nothing may be written") }
        return error
    }

    @Test
    fun pendingEvent() {
        val message = refuse(sampleContent(events = listOf(event("synthetic-event-1"), event("synthetic-event-2", confirmation = "pending")))).message
        assertTrue(message.orEmpty().contains("not user-confirmed"), message)
    }

    @Test
    fun staleOrUnknownPatterns() {
        assertTrue(refuse(sampleContent(patterns = listOf(samplePattern(status = "stale")))).message.orEmpty().contains("stale"))
        assertTrue(refuse(sampleContent(patterns = listOf(samplePattern(revision = 7)))).message.orEmpty().contains("not in the bundle"))
    }

    @Test
    fun findingWithMissingEventOrAnchor() {
        refuse(sampleContent(findings = listOf(sampleFinding(eventId = "synthetic-missing"))))
        refuse(sampleContent(findings = listOf(sampleFinding().copy(anchorReferenceIds = listOf("nope")))))
    }

    @Test
    fun emptySelection() {
        val content = sampleContent(
            events = emptyList(),
            findings = emptyList(),
            patterns = emptyList(),
            provenance = Provenance(emptyList(), emptyList()),
            originals = emptyList(),
            derivatives = emptyList(),
            report = null,
        )
        assertTrue(refuse(content).message.orEmpty().contains("empty"))
    }

    @Test
    fun duplicateOpaqueId() {
        val content = sampleContent(derivatives = listOf(patternFile(ORIGINAL_ID, 2048, 2)))
        assertTrue(refuse(content).message.orEmpty().contains("Duplicate opaque id"))
    }

    @Test
    fun badOpaqueIds() {
        listOf("../x", "a/b", "", "a".repeat(129), ".", "..", "sp ace").forEach { id ->
            val content = sampleContent(originals = listOf(patternFile(id, 10, 1)))
            assertTrue(refuse(content).message.orEmpty().contains("Bad opaque id"), id)
        }
    }

    @Test
    fun nonEmptyTargetDirectory() {
        val target = tmp.newFolder().toPath()
        Files.writeString(target.resolve("keep.txt"), "x")
        val error = assertFailsWith<IllegalArgumentException> { BundleWriter.write(sampleContent(), target, signer) }
        assertTrue(error.message.orEmpty().contains("not empty"))
        assertEquals(listOf("keep.txt"), Files.list(target).use { s -> s.map { it.fileName.toString() }.toList() })
    }

    @Test
    fun includedNodeWithWrongHash() {
        val graph = sampleProvenance()
        val wrong = graph.copy(nodes = graph.nodes.map { if (it.id == ORIGINAL_ID) it.copy(sha256 = "22".repeat(32)) else it })
        assertTrue(refuse(sampleContent(provenance = wrong)).message.orEmpty().contains("hash differs"))
    }

    @Test
    fun declaredLengthMismatch() {
        val liar = BundleFile(ORIGINAL_ID, ORIGINAL_LENGTH + 5) { PatternStream(ORIGINAL_LENGTH, 1) }
        assertTrue(refuse(sampleContent(originals = listOf(liar))).message.orEmpty().contains("declared length"))
    }
}
