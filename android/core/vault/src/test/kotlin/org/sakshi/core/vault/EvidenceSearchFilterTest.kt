package org.sakshi.core.vault

import java.io.ByteArrayInputStream
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.CategoryLabel
import org.sakshi.core.model.CategoryReviewStatus
import org.sakshi.core.model.ConfirmationStatus
import org.sakshi.core.model.Event
import org.sakshi.core.model.EventId
import org.sakshi.core.model.Locator
import org.sakshi.core.model.ReferenceId
import org.sakshi.core.model.SourceKind
import org.sakshi.core.model.ArtifactId
import org.sakshi.core.temporal.fixtures.EventBuilder
import org.sakshi.core.temporal.fixtures.syntheticEvent

/** Search filters and scopes over synthetic events, each with its own text derivative. All data is synthetic. */
class EvidenceSearchFilterTest : ReviewTestBase() {
    private lateinit var derivatives: DerivativeStore
    private lateinit var search: VaultEvidenceSearch
    private val other = "synthetic-case-2"

    @Before
    fun openSearch() {
        derivatives = DerivativeStore(db, audit, clock, ids, Dispatchers.IO)
        search = VaultEvidenceSearch(db, Dispatchers.IO)
    }

    /** Saves an event of [case] whose only reference is the whole text of a new derivative "needle <id>". */
    private suspend fun seed(
        id: String,
        case: String = EventFixtures.CASE,
        edit: (Event) -> Event = { it },
        configure: EventBuilder.() -> Unit = {},
    ): Event {
        val evidenceId = evidence.import(request(case), ByteArrayInputStream("synthetic".toByteArray())).id
        val text = "needle $id"
        val derivative = derivatives.save(evidenceId, DerivativeKind.PARSED_TEXT, text, "tool", "1")
        val built = syntheticEvent(id) {
            caseId = case
            actor = EventFixtures.ACTOR
            at("2026-10-01T10:00:00Z")
            configure()
        }
        val reference = built.evidenceReferences.single().copy(artifactId = ArtifactId(derivative.id), locator = Locator.Text(0, text.length))
        val event = edit(built.copy(evidenceReferences = listOf(reference)))
        saveOk(event)
        return event
    }

    private suspend fun found(filters: SearchFilters, case: String = EventFixtures.CASE, query: String = "needle"): List<String> =
        search.search(CaseId(case), query, filters).hits.map { it.derivativeText() }.sorted()

    private fun SearchHit.derivativeText(): String = matches.single().snippet.removePrefix("needle ")

    private fun instant(text: String): Instant = Instant.parse(text)

    private suspend fun fixture() {
        standardCase()
        insertCase(other)
    }

    @Test
    fun theOldSignatureAndDefaultFiltersGiveTheSameResultsIncludingUneventedText() = runBlocking<Unit> {
        fixture()
        seed("e1")
        val evidenceId = evidence.import(request(EventFixtures.CASE), ByteArrayInputStream("synthetic".toByteArray())).id
        derivatives.save(evidenceId, DerivativeKind.OCR, "needle loose", "tool", "1")

        val old = search.search(CaseId(EventFixtures.CASE), "needle")
        val filtered = search.search(CaseId(EventFixtures.CASE), "needle", SearchFilters())

        assertEquals(old, filtered)
        assertEquals(listOf("e1", "loose"), found(SearchFilters()))
        assertTrue(SearchFilters().isUnfiltered)
    }

    @Test
    fun dateRangeMatchesOnOverlapAndBothBoundsAreInclusive() = runBlocking<Unit> {
        fixture()
        seed("point") { at("2026-10-01T10:00:00Z") }
        seed("span") { between("2026-10-01T08:00:00Z", "2026-10-01T12:00:00Z") }
        seed("early") { at("2026-09-01T00:00:00Z") }
        seed("late") { at("2026-11-01T00:00:00Z") }

        assertEquals(listOf("point", "span"), found(SearchFilters(from = instant("2026-10-01T10:00:00Z"), until = instant("2026-10-01T10:00:00Z"))))
        assertEquals(listOf("point", "span"), found(SearchFilters(from = instant("2026-10-01T09:00:00Z"), until = instant("2026-10-02T00:00:00Z"))))
        assertEquals(listOf("span"), found(SearchFilters(from = instant("2026-10-01T10:30:00Z"), until = instant("2026-10-01T13:00:00Z"))))
        assertEquals(listOf("late"), found(SearchFilters(from = instant("2026-10-02T00:00:00Z"))))
        assertEquals(listOf("early"), found(SearchFilters(until = instant("2026-09-30T00:00:00Z"))))
    }

    @Test
    fun rangeEdgesJustOutsideAnEventDoNotMatch() = runBlocking<Unit> {
        fixture()
        seed("span") { between("2026-10-01T08:00:00Z", "2026-10-01T12:00:00Z") }

        assertEquals(listOf("span"), found(SearchFilters(from = instant("2026-10-01T12:00:00Z"))), "from equal to the latest time overlaps")
        assertTrue(found(SearchFilters(from = instant("2026-10-01T12:00:00.001Z"))).isEmpty())
        assertEquals(listOf("span"), found(SearchFilters(until = instant("2026-10-01T08:00:00Z"))), "until equal to the earliest time overlaps")
        assertTrue(found(SearchFilters(until = instant("2026-10-01T07:59:59.999Z"))).isEmpty())
    }

    @Test
    fun anEventWithAnUnknownTimeMatchesOnlyWhenNoDateFilterIsSet() = runBlocking<Unit> {
        fixture()
        seed("untimed") { untimed() }
        seed("timed") { at("2026-10-01T10:00:00Z") }

        assertEquals(listOf("timed", "untimed"), found(SearchFilters()))
        assertEquals(listOf("timed"), found(SearchFilters(from = instant("2026-01-01T00:00:00Z"))))
        assertEquals(listOf("timed"), found(SearchFilters(until = instant("2027-01-01T00:00:00Z"))))
        assertEquals(listOf("timed", "untimed"), found(SearchFilters(actorId = ActorId(EventFixtures.ACTOR))), "no date filter: unknown time is kept")
    }

    @Test
    fun actorFilterMatchesTheConfirmedPersonOnly() = runBlocking<Unit> {
        fixture()
        seed("a1") { actor = EventFixtures.ACTOR }
        seed("a2") { actor = EventFixtures.OTHER_ACTOR }
        seed("a3") { actor = null }

        assertEquals(listOf("a1"), found(SearchFilters(actorId = ActorId(EventFixtures.ACTOR))))
        assertEquals(listOf("a2"), found(SearchFilters(actorId = ActorId(EventFixtures.OTHER_ACTOR))))
        assertTrue(found(SearchFilters(actorId = ActorId("synthetic-nobody"))).isEmpty())
    }

    @Test
    fun confirmationFilterMatchesTheReviewState() = runBlocking<Unit> {
        fixture()
        seed("c1") { confirmation = ConfirmationStatus.CONFIRMED }
        seed("p1") { pending() }

        assertEquals(listOf("c1"), found(SearchFilters(confirmation = ConfirmationStatus.CONFIRMED)))
        assertEquals(listOf("p1"), found(SearchFilters(confirmation = ConfirmationStatus.PENDING)))
        assertTrue(found(SearchFilters(confirmation = ConfirmationStatus.REJECTED)).isEmpty())
    }

    @Test
    fun sourceKindFilterMatchesAnyOfTheKindsAndEmptyMeansAll() = runBlocking<Unit> {
        fixture()
        seed("k1", edit = { it.copy(source = it.source.copy(kind = SourceKind.SELECTED_TEXT)) })
        seed("k2", edit = { it.copy(source = it.source.copy(kind = SourceKind.NOTIFICATION_EXCERPT)) })
        seed("k3")

        assertEquals(listOf("k1"), found(SearchFilters(sourceKinds = setOf(SourceKind.SELECTED_TEXT))))
        assertEquals(listOf("k1", "k2"), found(SearchFilters(sourceKinds = setOf(SourceKind.SELECTED_TEXT, SourceKind.NOTIFICATION_EXCERPT))))
        assertEquals(listOf("k1", "k2", "k3"), found(SearchFilters(sourceKinds = emptySet())))
    }

    @Test
    fun textNotYetTurnedIntoEventsIsLeftOutWhenAnyEventFilterIsSet() = runBlocking<Unit> {
        fixture()
        seed("e1")
        val evidenceId = evidence.import(request(EventFixtures.CASE), ByteArrayInputStream("synthetic".toByteArray())).id
        derivatives.save(evidenceId, DerivativeKind.OCR, "needle loose", "tool", "1")

        assertEquals(listOf("e1", "loose"), found(SearchFilters()))
        val eventFilters = listOf(
            SearchFilters(from = instant("2020-01-01T00:00:00Z")),
            SearchFilters(until = instant("2030-01-01T00:00:00Z")),
            SearchFilters(actorId = ActorId(EventFixtures.ACTOR)),
            SearchFilters(confirmation = ConfirmationStatus.CONFIRMED),
            SearchFilters(sourceKinds = setOf(SourceKind.SELECTED_EXPORT)),
            SearchFilters(scope = SearchScope.ACCEPTED_FINDINGS_ONLY),
        )
        for (filters in eventFilters.dropLast(1)) {
            assertEquals(listOf("e1"), found(filters), "$filters")
        }
        assertTrue(found(eventFilters.last()).none { it == "loose" })
    }

    @Test
    fun filtersCombineWithAnd() = runBlocking<Unit> {
        fixture()
        seed("m1") { actor = EventFixtures.ACTOR; at("2026-10-01T10:00:00Z") }
        seed("m2") { actor = EventFixtures.OTHER_ACTOR; at("2026-10-01T10:00:00Z") }
        seed("m3") { actor = EventFixtures.ACTOR; at("2026-12-01T10:00:00Z") }
        seed("m4") { actor = EventFixtures.ACTOR; at("2026-10-01T10:00:00Z"); pending() }

        val combined = SearchFilters(
            from = instant("2026-10-01T00:00:00Z"),
            until = instant("2026-10-31T00:00:00Z"),
            actorId = ActorId(EventFixtures.ACTOR),
            confirmation = ConfirmationStatus.CONFIRMED,
            sourceKinds = setOf(SourceKind.SELECTED_EXPORT),
        )

        assertEquals(listOf("m1"), found(combined))
    }

    @Test
    fun acceptedScopeIncludesAcceptedAndUserTaggedAndExcludesRejectedUncertainAndUnreviewed() = runBlocking<Unit> {
        fixture()
        seed("accepted") { category(CategoryLabel.VERBAL_ABUSE, CategoryReviewStatus.UNREVIEWED) }
        seed("rejected") { category(CategoryLabel.VERBAL_ABUSE, CategoryReviewStatus.UNREVIEWED) }
        seed("uncertain") { category(CategoryLabel.VERBAL_ABUSE, CategoryReviewStatus.UNREVIEWED) }
        seed("tagged")
        seed("plain")
        seed("unreviewed") { category(CategoryLabel.VERBAL_ABUSE, CategoryReviewStatus.UNREVIEWED) }
        seed("mixed") {
            category(CategoryLabel.VERBAL_ABUSE, CategoryReviewStatus.UNREVIEWED)
            category(CategoryLabel.INTIMIDATION, CategoryReviewStatus.UNREVIEWED)
        }
        assertApplied(review.reviewCategory(EventId("accepted"), 0, CategoryReviewStatus.ACCEPTED), "accepted")
        assertApplied(review.reviewCategory(EventId("rejected"), 0, CategoryReviewStatus.REJECTED), "rejected")
        assertApplied(review.reviewCategory(EventId("uncertain"), 0, CategoryReviewStatus.UNCERTAIN), "uncertain")
        assertApplied(review.reviewCategory(EventId("mixed"), 0, CategoryReviewStatus.REJECTED), "mixed")
        assertApplied(review.reviewCategory(EventId("mixed"), 1, CategoryReviewStatus.ACCEPTED), "mixed")
        assertApplied(review.addUserTag(EventId("tagged"), CategoryLabel.INTIMIDATION, listOf(ReferenceId("ref-1"))), "tagged")

        val accepted = SearchFilters(scope = SearchScope.ACCEPTED_FINDINGS_ONLY)

        assertEquals(listOf("accepted", "mixed", "tagged"), found(accepted))
        assertEquals(
            listOf("accepted", "mixed", "plain", "rejected", "tagged", "uncertain", "unreviewed"),
            found(SearchFilters(scope = SearchScope.ALL_PRESERVED_TEXT)),
        )

        assertApplied(review.reviewCategory(EventId("accepted"), 0, CategoryReviewStatus.REJECTED), "accepted")
        assertEquals(listOf("mixed", "tagged"), found(accepted), "a later rejection removes the event again")
    }

    @Test
    fun acceptedScopeCombinesWithOtherFilters() = runBlocking<Unit> {
        fixture()
        seed("x1") { actor = EventFixtures.ACTOR; category(CategoryLabel.VERBAL_ABUSE, CategoryReviewStatus.ACCEPTED) }
        seed("x2") { actor = EventFixtures.OTHER_ACTOR; category(CategoryLabel.VERBAL_ABUSE, CategoryReviewStatus.ACCEPTED) }
        seed("x3") { actor = EventFixtures.ACTOR }

        val filters = SearchFilters(actorId = ActorId(EventFixtures.ACTOR), scope = SearchScope.ACCEPTED_FINDINGS_ONLY)

        assertEquals(listOf("x1"), found(filters))
    }

    @Test
    fun anotherCasesMatchingTextIsNeverReturnedUnderAnyFilter() = runBlocking<Unit> {
        fixture()
        seed("mine") { category(CategoryLabel.VERBAL_ABUSE, CategoryReviewStatus.ACCEPTED) }
        insertActor(other, "synthetic-actor-3")
        seed("theirs", case = other) { actor = "synthetic-actor-3"; category(CategoryLabel.VERBAL_ABUSE, CategoryReviewStatus.ACCEPTED) }
        val evidenceId = evidence.import(request(other), ByteArrayInputStream("synthetic".toByteArray())).id
        derivatives.save(evidenceId, DerivativeKind.OCR, "needle theirs-loose", "tool", "1")

        val everything = listOf(
            SearchFilters(),
            SearchFilters(from = instant("2020-01-01T00:00:00Z"), until = instant("2030-01-01T00:00:00Z")),
            SearchFilters(actorId = ActorId(EventFixtures.ACTOR)),
            SearchFilters(confirmation = ConfirmationStatus.CONFIRMED),
            SearchFilters(sourceKinds = setOf(SourceKind.SELECTED_EXPORT)),
            SearchFilters(scope = SearchScope.ACCEPTED_FINDINGS_ONLY),
            SearchFilters(scope = SearchScope.ALL_PRESERVED_TEXT),
        )
        for (filters in everything) {
            assertEquals(listOf("mine"), found(filters), "$filters")
            assertFalse(found(filters, case = other).contains("mine"), "$filters")
        }
        assertEquals(listOf("theirs"), found(SearchFilters(scope = SearchScope.ACCEPTED_FINDINGS_ONLY), case = other))
    }

    @Test
    fun theMatchLimitCountsOnlyMatchesThatPassTheFilters() = runBlocking<Unit> {
        fixture()
        repeat(4) { seed("skip$it") { actor = EventFixtures.OTHER_ACTOR } }
        seed("keep1")
        seed("keep2")
        seed("keep3")
        val capped = VaultEvidenceSearch(db, Dispatchers.IO, maxMatches = 2)
        val filters = SearchFilters(actorId = ActorId(EventFixtures.ACTOR))

        val results = capped.search(CaseId(EventFixtures.CASE), "needle", filters)

        assertTrue(results.limitReached)
        assertEquals(2, results.hits.size)
        assertTrue(results.hits.all { it.derivativeText().startsWith("keep") })
        val others = capped.search(CaseId(EventFixtures.CASE), "needle", SearchFilters(actorId = ActorId(EventFixtures.OTHER_ACTOR)))
        assertTrue(others.limitReached, "four filtered matches exceed the limit of two")
        val none = capped.search(CaseId(EventFixtures.CASE), "needle", SearchFilters(actorId = ActorId("synthetic-nobody")))
        assertFalse(none.limitReached)
        assertTrue(none.hits.isEmpty())
    }

    @Test
    fun anImplementationWithoutFilterSupportRefusesNarrowingInsteadOfIgnoringIt() = runBlocking<Unit> {
        val plain = object : EvidenceSearch {
            override suspend fun search(caseId: CaseId, query: String): SearchResults = SearchResults.EMPTY
        }

        assertEquals(SearchResults.EMPTY, plain.search(CaseId("synthetic-case-1"), "x", SearchFilters()))
        assertFailsWith<UnsupportedOperationException> {
            plain.search(CaseId("synthetic-case-1"), "x", SearchFilters(actorId = ActorId("synthetic-actor-1")))
        }
    }
}
