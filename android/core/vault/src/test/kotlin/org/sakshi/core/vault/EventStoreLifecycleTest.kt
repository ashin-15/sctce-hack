package org.sakshi.core.vault

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.sakshi.core.model.AssociationReview
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.Event
import org.sakshi.core.model.EventId
import org.sakshi.core.model.IdentityBasis
import org.sakshi.core.model.Timestamp

class EventStoreLifecycleTest : EventStoreTestBase() {
    private val caseId = CaseId(EventFixtures.CASE)

    @Test
    fun deletingACaseRemovesEveryEventRow() = runBlocking<Unit> {
        standardCase()
        saveOk(EventFixtures.plain("synthetic-target"))
        saveOk(EventFixtures.maximal("synthetic-maximal", "synthetic-target", "synthetic-target"))
        assertTrue(rowCounts().take(7).all { it > 0 })

        cases.delete(EventFixtures.CASE)

        assertEquals(List(7) { 0 }, rowCounts().take(7))
        assertEquals(emptyList(), store.loadAll(caseId))
        assertNull(store.load(EventId("synthetic-maximal"), 1))
    }

    @Test
    fun deletingEvidenceRemovesEventsThatRestOnlyOnItAndTrimsTheOthers() = runBlocking<Unit> {
        standardCase()
        val stored = evidence.import(request(EventFixtures.CASE), ByteArrayInputStream(ByteArray(40)))
        val onlyOnIt = EventFixtures.plain("synthetic-only").copy(
            evidenceReferences = listOf(EventFixtures.ref("r1", artifact = stored.id)),
        )
        val shared = EventFixtures.maximal("synthetic-shared", "synthetic-only", "synthetic-only").copy(
            evidenceReferences = EventFixtures.maximal("x", "y", "y").evidenceReferences.map {
                if (it.referenceId.value == "ref-a") it.copy(artifactId = org.sakshi.core.model.ArtifactId(stored.id)) else it
            },
        )
        saveOk(onlyOnIt)
        saveOk(shared)

        evidence.delete(stored.id)

        // deleteWithDependants: an event whose every anchor pointed at the evidence is removed...
        assertNull(store.load(EventId("synthetic-only"), 1))
        // ...while an event with other anchors stays, minus the removed reference, and a category left with no
        // anchor at all disappears. Id lists kept as JSON on the revision are not edited.
        val trimmed = store.load(EventId("synthetic-shared"), 1)!!
        assertEquals(listOf("ref-z", "ref-m", "ref-b", "ref-p", "ref-q"), trimmed.evidenceReferences.map { it.referenceId.value })
        assertEquals(
            listOf(listOf("ref-b", "ref-z"), listOf("ref-m")),
            trimmed.categories.map { c -> c.evidenceReferenceIds.map { it.value } }.filter { it.isNotEmpty() },
        )
        assertEquals(listOf("ref-m", "ref-z"), trimmed.severity.evidenceReferenceIds.map { it.value })
        assertEquals(listOf(EventId("synthetic-shared")), store.loadAll(caseId).map { it.eventId })
    }

    @Test
    fun eachSaveWritesExactlyOneAuditRowWithoutAnyText() = runBlocking<Unit> {
        standardCase()
        saveOk(EventFixtures.plain("synthetic-target"))
        val maximal = EventFixtures.maximal("synthetic-maximal", "synthetic-target", "synthetic-target")
        saveOk(maximal)
        saveOk(EventFixtures.sparse("synthetic-sparse", "synthetic-target"))
        store.save(EventFixtures.plain("synthetic-rejected", caseId = "synthetic-missing"))

        val saved = recording.calls.filter { it.startsWith("event.saved|") }
        assertEquals(3, saved.size)
        assertEquals(
            "event.saved|event|synthetic-maximal|" +
                "{\"event_id\":\"synthetic-maximal\",\"case_id\":\"${EventFixtures.CASE}\",\"revision\":1,\"kind\":\"user_boundary\"}",
            saved[1],
        )
        val everything = recording.calls.joinToString("\n")
        listOf("അജ്ഞാത", "Zoë", "quoted", "ref-z", "synthetic-artifact").forEach { assertFalse(everything.contains(it), it) }
        assertEquals(3, audit.records().count { it.action == "event.saved" })
        assertIs<AuditVerification.Valid>(audit.verify())
    }

    @Test
    fun observeLatestEmitsAgainAfterASave() = runBlocking<Unit> {
        standardCase()
        val seen = mutableListOf<List<Event>>()
        val collector = launch(Dispatchers.IO) { store.observeLatest(caseId).collect { seen += it } }
        withTimeout(WAIT_MS) { while (seen.isEmpty()) delay(POLL_MS) }
        assertEquals(emptyList(), seen.first())

        val event = EventFixtures.plain("synthetic-observed")
        saveOk(event)
        withTimeout(WAIT_MS) { while (seen.none { it.contains(event) }) delay(POLL_MS) }
        collector.cancel()
    }

    @Test
    fun coverageGapsAreStoredAndCitedByTheirIds() = runBlocking<Unit> {
        standardCase()
        val gap = store.addCoverageGap(caseId, Timestamp("2026-10-01T10:00:00Z"), null, "synthetic-reason")
        store.addCoverageGap(caseId, null, null, "second")
        val stored = store.coverageGaps(caseId)
        assertEquals(2, stored.size)
        assertTrue(stored.any { it.id == gap && it.startAt == Timestamp("2026-10-01T10:00:00Z") && it.endAt == null })
        assertFailsWith<IllegalArgumentException> { store.addCoverageGap(CaseId("synthetic-missing"), null, null, "r") }
        assertFailsWith<IllegalArgumentException> { store.addCoverageGap(caseId, null, null, " ") }
        assertFailsWith<IllegalArgumentException> {
            store.addCoverageGap(caseId, Timestamp("2026-10-02T00:00:00Z"), Timestamp("2026-10-01T00:00:00Z"), "r")
        }
        saveOk(EventFixtures.plain("synthetic-gapped").copy(coverage = EventFixtures.plain("x").coverage.copy(gapReferenceIds = listOf(gap))))
        assertEquals(listOf(gap), store.load(EventId("synthetic-gapped"), 1)!!.coverage.gapReferenceIds)
        assertEquals(2, audit.records().count { it.action == "coverage_gap.added" })
    }

    @Test
    fun actorRegistryCreatesListsAndRefusesBadInput() = runBlocking<Unit> {
        insertCase(EventFixtures.CASE)
        val generated = actors.create(caseId, "Label ഒന്ന്", IdentityBasis.UNKNOWN, AssociationReview.UNREVIEWED)
        insertActor(EventFixtures.CASE, "synthetic-fixed")
        val listed = actors.list(caseId)
        assertEquals(setOf(generated.value, "synthetic-fixed"), listed.map { it.id.value }.toSet())
        assertEquals("Label ഒന്ന്", listed.first { it.id == generated }.displayLabel)
        assertFailsWith<IllegalArgumentException> { actors.create(CaseId("synthetic-missing"), "x", IdentityBasis.UNKNOWN, AssociationReview.UNKNOWN) }
        assertFailsWith<IllegalArgumentException> { actors.create(caseId, " ", IdentityBasis.UNKNOWN, AssociationReview.UNKNOWN) }
        assertFalse(recording.calls.filter { it.startsWith("actor.created") }.any { it.contains("Label") })
        assertEquals(emptyList(), withContext(Dispatchers.IO) { actors.list(CaseId("synthetic-other")) })
    }

    private companion object {
        const val WAIT_MS = 10_000L
        const val POLL_MS = 20L
    }
}
