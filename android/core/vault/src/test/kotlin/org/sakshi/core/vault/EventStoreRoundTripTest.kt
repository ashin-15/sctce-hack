package org.sakshi.core.vault

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.ArtifactId
import org.sakshi.core.model.EventId
import org.sakshi.core.model.EventSchemaAdapter
import org.sakshi.core.model.Locator
import org.sakshi.core.model.ReferenceId
import org.sakshi.core.model.ScopeId

class EventStoreRoundTripTest : EventStoreTestBase() {
    private fun fixtureFile(): String =
        java.io.File(checkNotNull(System.getProperty("sakshi.fixtures")) { "sakshi.fixtures not set" }, "event-valid.json").readText()

    @Test
    fun embeddedCopyOfTheFixtureMatchesTheFile() {
        assertEquals(
            kotlinx.serialization.json.Json.parseToJsonElement(fixtureFile()),
            kotlinx.serialization.json.Json.parseToJsonElement(EventFixtures.DEMO_EVENT_JSON),
        )
    }

    @Test
    fun schemaFixtureRoundTripsExactlyAndStillSerialisesToTheOriginalJson() = runBlocking<Unit> {
        val original = EventSchemaAdapter.fromJson(fixtureFile())
        insertCase(original.caseId.value)
        insertActor(original.caseId.value, "demo-actor-a")

        assertRoundTrip(original)

        val reloaded = assertNotNull(store.load(original.eventId, 1))
        val originalJson = kotlinx.serialization.json.Json.parseToJsonElement(fixtureFile())
        assertEquals(originalJson, EventSchemaAdapter.toJsonElement(reloaded))
    }

    @Test
    fun maximalEventRoundTripsWithEveryStorableLocatorAndListOrder() = runBlocking<Unit> {
        standardCase()
        saveOk(EventFixtures.plain("synthetic-target-a"))
        saveOk(EventFixtures.plain("synthetic-target-b"))
        val event = EventFixtures.maximal("synthetic-maximal", "synthetic-target-a", "synthetic-target-b")

        assertRoundTrip(event)

        val loaded = assertNotNull(store.load(event.eventId, 1))
        assertEquals(listOf("ref-z", "ref-a", "ref-m", "ref-b", "ref-p", "ref-q"), loaded.evidenceReferences.map { it.referenceId.value })
        assertEquals(
            listOf(listOf("ref-b", "ref-z", "ref-a"), listOf("ref-m", "ref-a"), emptyList()),
            loaded.categories.map { c -> c.evidenceReferenceIds.map { it.value } },
        )
        assertEquals(listOf("synthetic-target-b", "synthetic-target-a"), loaded.relationshipToPreviousEvents.map { it.targetEventId.value })
        assertEquals(listOf("gap-2", "gap-1"), loaded.coverage.gapReferenceIds.map { it.value })
        assertEquals(listOf("ref-m", "ref-z"), loaded.severity.evidenceReferenceIds.map { it.value })
        assertIs<Locator.Text>(loaded.evidenceReferences[0].locator)
        assertIs<Locator.AudioTime>(loaded.evidenceReferences[1].locator)
        assertEquals(Locator.WholeArtifact, loaded.evidenceReferences[2].locator)
        assertEquals(Locator.ImageOrPageRegion(2, ScopeId("synthetic-region-1")), loaded.evidenceReferences[4].locator)
        assertEquals(Locator.ImageOrPageRegion(null, ScopeId("synthetic-region-2")), loaded.evidenceReferences[5].locator)
        assertEquals(listOf(true, true, false, false, true, false), loaded.evidenceReferences.map { it.sha256 != null })
    }

    @Test
    fun sparseEventWithUnknownTimeNullActorAndMalayalamLabelRoundTrips() = runBlocking<Unit> {
        standardCase()
        saveOk(EventFixtures.plain("synthetic-canonical"))
        val event = EventFixtures.sparse("synthetic-sparse", "synthetic-canonical")

        assertRoundTrip(event)

        assertEquals("അജ്ഞാത അയച്ചയാൾ", assertNotNull(store.load(event.eventId, 1)).sender.displayLabel)
        assertNull(store.load(event.eventId, 1)!!.timestamp.earliest)
    }

    @Test
    fun noneMarkerBoundaryWithAnActorOrStatusIsStoredNotDropped() = runBlocking<Unit> {
        standardCase()
        val base = EventFixtures.plain("synthetic-none-actor")
        assertRoundTrip(base.copy(boundary = base.boundary.copy(actorId = ActorId(EventFixtures.ACTOR))))
    }

    @Test
    fun referenceWithoutDigestAndWithoutStoredEvidenceRoundTrips() = runBlocking<Unit> {
        standardCase()
        val base = EventFixtures.plain("synthetic-no-digest")
        assertRoundTrip(base.copy(evidenceReferences = listOf(EventFixtures.ref("r1", sha256 = null))))
        val anchors = db.eventDao().getAnchors("synthetic-no-digest", 1)
        assertNull(anchors.single().evidenceId)
        assertNull(anchors.single().sha256)
    }

    @Test
    fun anchorLinksToStoredEvidenceOfTheSameCaseOnly() = runBlocking<Unit> {
        standardCase()
        insertCase("synthetic-case-2")
        val own = evidence.import(request(EventFixtures.CASE), java.io.ByteArrayInputStream(ByteArray(10)))
        val foreign = evidence.import(request("synthetic-case-2"), java.io.ByteArrayInputStream(ByteArray(10)))
        val event = EventFixtures.plain("synthetic-linked").copy(
            evidenceReferences = listOf(
                EventFixtures.ref("r-own", artifact = own.id),
                EventFixtures.ref("r-foreign", artifact = foreign.id),
                EventFixtures.ref("r-none"),
            ),
        )
        assertRoundTrip(event)
        assertEquals(
            listOf(own.id, null, null),
            db.eventDao().getAnchors("synthetic-linked", 1).map { it.evidenceId },
        )
    }

    @Test
    fun duplicateReferenceIdsInOneCategoryAreRefusedWhole() = runBlocking<Unit> {
        standardCase()
        val base = EventFixtures.maximal("synthetic-dup", "t", "t")
        val duplicated = base.categories[0].copy(evidenceReferenceIds = listOf(ReferenceId("ref-b"), ReferenceId("ref-b")))
        val result = store.save(EventFixtures.plain("synthetic-dup").copy(
            evidenceReferences = base.evidenceReferences,
            categories = listOf(duplicated),
        ))
        assertEquals(SaveResult.Unsupported(listOf("categories.evidence_reference_ids")), result)
    }

    @Test
    fun enumStringsComeFromTheSerialNames() {
        assertEquals("urgent_review", Codecs.reviewPriority.name(org.sakshi.core.model.ReviewPriority.URGENT_REVIEW))
        assertEquals(org.sakshi.core.model.TimePrecision.MINUTE, Codecs.timePrecision.parse("minute"))
        assertEquals(ArtifactId("x"), ArtifactId("x"))
    }
}
