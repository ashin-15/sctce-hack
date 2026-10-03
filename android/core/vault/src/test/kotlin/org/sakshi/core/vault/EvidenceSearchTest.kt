package org.sakshi.core.vault

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.sakshi.core.database.ActorEntity
import org.sakshi.core.database.EventEntity
import org.sakshi.core.database.EventRevisionEntity
import org.sakshi.core.database.EvidenceAnchorEntity
import org.sakshi.core.model.CaseId

/** Unit tests for case-scoped lexical search over derivatives and event correlation behaviour. */
class EvidenceSearchTest : VaultTestBase() {
    private lateinit var derivatives: DerivativeStore
    private lateinit var search: VaultEvidenceSearch

    @Before
    fun openSearch() {
        derivatives = DerivativeStore(db, audit, clock, ids, Dispatchers.IO)
        search = VaultEvidenceSearch(db, Dispatchers.IO)
    }

    private suspend fun searchHits(caseId: CaseId, query: String): List<SearchHit> = search.search(caseId, query).hits

    private suspend fun importEvidence(caseId: String): String =
        evidence.import(request(caseId), ByteArrayInputStream("content".toByteArray())).id

    @Test
    fun blankOrEmptyQueryReturnsEmptyList() = runBlocking<Unit> {
        val caseId = cases.create("Case Alpha").id
        val evId = importEvidence(caseId)
        derivatives.save(evId, DerivativeKind.OCR, "Some text content here", "tool", "1")

        val emptyResult = searchHits(CaseId(caseId), "")
        val spaceResult = searchHits(CaseId(caseId), "   ")
        val newlineResult = searchHits(CaseId(caseId), "\t\n  ")

        assertTrue(emptyResult.isEmpty())
        assertTrue(spaceResult.isEmpty())
        assertTrue(newlineResult.isEmpty())
    }

    @Test
    fun exactQueryReturnsExpectedHitsAndSnippets() = runBlocking<Unit> {
        val caseId = cases.create("Case Alpha").id
        val evId = importEvidence(caseId)
        val text = "Please do not call me anymore. Stop it."
        val saved = derivatives.save(evId, DerivativeKind.OCR, text, "tool", "1")

        val hits = searchHits(CaseId(caseId), "call me")

        assertEquals(1, hits.size)
        val hit = hits[0]
        assertEquals(evId, hit.evidenceId)
        assertEquals(saved.id, hit.derivativeId)
        assertEquals(DerivativeKind.OCR, hit.derivativeKind)
        assertNull(hit.eventId)
        assertNull(hit.actorLabel)
        assertNull(hit.timestamp)

        assertEquals(1, hit.matches.size)
        val match = hit.matches[0]
        assertEquals(text, match.snippet)
        assertEquals(14, match.matchStartInSnippet)
        assertEquals(21, match.matchEndInSnippet)
        assertEquals("call me", match.snippet.substring(match.matchStartInSnippet, match.matchEndInSnippet))
    }

    @Test
    fun caseInsensitiveQueryMatchesProperly() = runBlocking<Unit> {
        val caseId = cases.create("Case Alpha").id
        val evId = importEvidence(caseId)
        val text = "The Threatening Message was received at midnight."
        derivatives.save(evId, DerivativeKind.PARSED_TEXT, text, "parser", "1")

        val lowerHits = searchHits(CaseId(caseId), "threatening")
        val upperHits = searchHits(CaseId(caseId), "THREATENING")
        val mixedHits = searchHits(CaseId(caseId), "ThReAtEnInG")

        assertEquals(1, lowerHits.size)
        assertEquals(1, upperHits.size)
        assertEquals(1, mixedHits.size)

        for (hits in listOf(lowerHits, upperHits, mixedHits)) {
            val match = hits[0].matches[0]
            val matchedText = match.snippet.substring(match.matchStartInSnippet, match.matchEndInSnippet)
            assertEquals("Threatening", matchedText)
        }
    }

    @Test
    fun snippetBoundariesAtStartAndEndOfDerivativeText() = runBlocking<Unit> {
        val caseId = cases.create("Case Alpha").id
        val evId = importEvidence(caseId)
        val text = "START message body content until the END"
        derivatives.save(evId, DerivativeKind.OCR, text, "tool", "1")

        // Search at start
        val startHits = searchHits(CaseId(caseId), "START")
        assertEquals(1, startHits.size)
        val startMatch = startHits[0].matches[0]
        assertEquals(0, startMatch.matchStartInSnippet)
        assertEquals(5, startMatch.matchEndInSnippet)
        assertFalse(startMatch.snippet.startsWith("\u2026"))
        assertEquals("START", startMatch.snippet.substring(startMatch.matchStartInSnippet, startMatch.matchEndInSnippet))

        // Search at end
        val endHits = searchHits(CaseId(caseId), "END")
        assertEquals(1, endHits.size)
        val endMatch = endHits[0].matches[0]
        assertEquals(endMatch.snippet.length, endMatch.matchEndInSnippet)
        assertFalse(endMatch.snippet.endsWith("\u2026"))
        assertEquals("END", endMatch.snippet.substring(endMatch.matchStartInSnippet, endMatch.matchEndInSnippet))
    }

    @Test
    fun multipleMatchesInSingleDerivative() = runBlocking<Unit> {
        val caseId = cases.create("Case Alpha").id
        val evId = importEvidence(caseId)
        val text = "I said stop messaging me right now, or I will report you! stop messaging me!"
        val saved = derivatives.save(evId, DerivativeKind.OCR, text, "tool", "1")

        val hits = searchHits(CaseId(caseId), "stop messaging")

        assertEquals(1, hits.size)
        val hit = hits[0]
        assertEquals(saved.id, hit.derivativeId)
        assertEquals(2, hit.matches.size)

        val first = hit.matches[0]
        val second = hit.matches[1]

        assertEquals("stop messaging", first.snippet.substring(first.matchStartInSnippet, first.matchEndInSnippet))
        assertEquals("stop messaging", second.snippet.substring(second.matchStartInSnippet, second.matchEndInSnippet))
        assertTrue(first.matchStartInSnippet < second.matchStartInSnippet)
    }

    @Test
    fun crossCaseIsolation() = runBlocking<Unit> {
        val caseA = cases.create("Case Alpha").id
        val caseB = cases.create("Case Beta").id

        val evA = importEvidence(caseA)
        val evB = importEvidence(caseB)

        derivatives.save(evA, DerivativeKind.OCR, "Confidential case alpha evidence text only", "tool", "1")
        derivatives.save(evB, DerivativeKind.OCR, "Secret case beta private record only", "tool", "1")

        // Search in Case A for text existing strictly in Case B -> must return empty
        val aForBeta = searchHits(CaseId(caseA), "beta")
        val aForSecret = searchHits(CaseId(caseA), "Secret")
        val aForRecord = searchHits(CaseId(caseA), "record")

        assertTrue(aForBeta.isEmpty())
        assertTrue(aForSecret.isEmpty())
        assertTrue(aForRecord.isEmpty())

        // Search in Case B for text existing strictly in Case A -> must return empty
        val bForAlpha = searchHits(CaseId(caseB), "alpha")
        val bForConfidential = searchHits(CaseId(caseB), "Confidential")
        val bForEvidence = searchHits(CaseId(caseB), "evidence")

        assertTrue(bForAlpha.isEmpty())
        assertTrue(bForConfidential.isEmpty())
        assertTrue(bForEvidence.isEmpty())

        // Positive search in own cases
        val aHits = searchHits(CaseId(caseA), "alpha")
        assertEquals(1, aHits.size)
        assertEquals(evA, aHits[0].evidenceId)

        val bHits = searchHits(CaseId(caseB), "beta")
        assertEquals(1, bHits.size)
        assertEquals(evB, bHits[0].evidenceId)
    }

    @Test
    fun eventAndActorLabelPopulatedWhenAnchorLinksDerivative() = runBlocking<Unit> {
        val caseId = cases.create("Case Alpha").id
        val evId = importEvidence(caseId)
        val text = "Why are you ignoring my repeated messages?"
        val deriv = derivatives.save(evId, DerivativeKind.OCR, text, "tool", "1")

        // Insert Actor, Event, EventRevision, and EvidenceAnchor
        val actorId = "actor-stalker-1"
        val eventId = "event-threat-1"
        db.eventDao().insertActor(
            ActorEntity(
                id = actorId,
                caseId = caseId,
                displayLabel = "Threatening Stalker",
                identityBasis = "user_asserted",
                associationReview = "confirmed",
            ),
        )

        db.eventDao().insertEvent(EventEntity(eventId, caseId))

        db.eventDao().insertRevision(
            EventRevisionEntity(
                eventId = eventId,
                revision = 1,
                kind = "message_observation",
                observedAt = "2026-10-01T09:00:00Z",
                availableAt = "2026-10-01T09:00:00Z",
                availableAtEpochMs = 1790845200000L,
                tsEarliest = "2026-10-01T08:55:00Z",
                tsEarliestEpochMs = 1790844900000L,
                tsLatest = null,
                tsLatestEpochMs = null,
                tsBasis = "source_claim",
                tsPrecision = "minute",
                tsTimezone = null,
                collectorSessionId = null,
                monotonicMs = null,
                actorId = actorId,
                sourceScopeId = null,
                senderDisplayLabel = "Stalker Label",
                senderIdentityBasis = "user_asserted",
                senderAssociationReview = "confirmed",
                sourceKind = "selected_text",
                sourceApp = "com.whatsapp",
                profileScopeId = null,
                conversationScopeId = null,
                sourceRecordId = null,
                parserVersion = "parser-v1",
                direction = "incoming",
                reviewPriority = "high",
                severityBasis = "unknown",
                severityReferenceIdsJson = "[]",
                confirmationStatus = "confirmed",
                confirmationScope = "full",
                reviewedAt = null,
                dedupStatus = "distinct_observation",
                canonicalEventId = null,
                dedupMethod = "dedup-v1",
                coverageContext = "unknown",
                textStatus = "available",
                outgoingCoverage = "unknown",
                gapReferenceIdsJson = "[]",
                unwantedContact = "yes",
                retentionMode = "encrypted_candidate",
                expiresAt = null,
                consentGeneration = 0,
            ),
        )

        db.eventDao().insertAnchors(
            listOf(
                EvidenceAnchorEntity(
                    id = "anchor-1",
                    eventId = eventId,
                    eventRevision = 1,
                    referenceId = "ref-1",
                    artifactId = deriv.id,
                    evidenceId = null,
                    derivativeId = null,
                    sha256 = null,
                    representation = "ocr_text",
                    locatorKind = "text",
                    startCp = 0,
                    endCp = text.length,
                    startMs = null,
                    endMs = null,
                    pageIndex = null,
                    regionId = null,
                ),
            ),
        )

        val hits = searchHits(CaseId(caseId), "repeated messages")

        assertEquals(1, hits.size)
        val hit = hits[0]
        assertEquals(eventId, hit.eventId)
        assertEquals("Threatening Stalker", hit.actorLabel)
        assertEquals("2026-10-01T08:55:00Z", hit.timestamp)
        assertEquals(1, hit.matches.size)
        assertEquals("repeated messages", hit.matches[0].snippet.substring(hit.matches[0].matchStartInSnippet, hit.matches[0].matchEndInSnippet))
    }

    @Test
    fun longContextSnippetTruncationAndWhitespaceCleaning() = runBlocking<Unit> {
        val caseId = cases.create("Case Alpha").id
        val evId = importEvidence(caseId)
        val leading = "Leading filler text repeated several times to exceed context window length. "
        val trailing = " Trailing filler text repeated several times to exceed context window length."
        val text = leading.repeat(2) + "\n\n  TargetWord  \n\n" + trailing.repeat(2)
        derivatives.save(evId, DerivativeKind.OCR, text, "tool", "1")

        val hits = searchHits(CaseId(caseId), "TargetWord")

        assertEquals(1, hits.size)
        val match = hits[0].matches[0]
        assertTrue(match.snippet.startsWith("\u2026"), "Expected snippet to start with ellipsis")
        assertTrue(match.snippet.endsWith("\u2026"), "Expected snippet to end with ellipsis")
        assertFalse(match.snippet.contains("\n"), "Snippet should have clean single-line spaces")
        assertFalse(match.snippet.contains("  "), "Snippet should not contain multiple consecutive spaces")
        assertEquals("TargetWord", match.snippet.substring(match.matchStartInSnippet, match.matchEndInSnippet))
    }

    @Test
    fun vaultSearchExposedOnVaultSession() = runBlocking<Unit> {
        val vault = Vault.openForTests(context, testWrapper(), clock, ids, Dispatchers.IO)
        try {
            assertNotNull(vault.search)
            val case = vault.cases.create("Integrated Case")
            val imported = vault.evidence.import(request(case.id), ByteArrayInputStream("content".toByteArray()))
            vault.derivatives.save(imported.id, DerivativeKind.OCR, "Integrated search content here", "tool", "1")

            val hits = vault.search.search(CaseId(case.id), "search content").hits
            assertEquals(1, hits.size)
            assertEquals("search content", hits[0].matches[0].snippet.substring(hits[0].matches[0].matchStartInSnippet, hits[0].matches[0].matchEndInSnippet))
        } finally {
            vault.close()
        }
    }

    @Test
    fun onlyTheNewestRevisionOfADerivativeIsSearched() = runBlocking<Unit> {
        val caseId = cases.create("Case Alpha").id
        val evId = importEvidence(caseId)
        val first = derivatives.save(evId, DerivativeKind.USER_EDIT, "synthetic old wording", "tool", "1")
        val second = derivatives.save(evId, DerivativeKind.USER_EDIT, "synthetic new wording", "tool", "1", parentDerivativeId = first.id)
        assertTrue(searchHits(CaseId(caseId), "old").isEmpty(), "a superseded revision must not be found")
        assertEquals(listOf(second.id), searchHits(CaseId(caseId), "wording").map { it.derivativeId })
    }

    @Test
    fun wildcardCharactersAndNonAsciiCaseAreMatchedLiterally() = runBlocking<Unit> {
        val caseId = cases.create("Case Alpha").id
        val evId = importEvidence(caseId)
        derivatives.save(evId, DerivativeKind.OCR, "synthetic ÉCOLE note, pay 50% now", "tool", "1")
        assertEquals(1, searchHits(CaseId(caseId), "école").size, "case folding beyond ASCII")
        assertEquals(1, searchHits(CaseId(caseId), "50%").size)
        assertTrue(searchHits(CaseId(caseId), "5_%").isEmpty(), "SQL wildcards are not wildcards here")
        assertTrue(searchHits(CaseId(caseId), "%").single().matches.size == 1)
    }

    @Test
    fun codePointOffsetsKeepEventAttributionRightAfterEmojiAndSnippetsNeverSplitPairs() = runBlocking<Unit> {
        val caseId = cases.create("Case Alpha").id
        val evId = importEvidence(caseId)
        val text = "\uD83D\uDE00".repeat(40) + " target"
        val deriv = derivatives.save(evId, DerivativeKind.PARSED_TEXT, text, "tool", "1")
        val targetStartCp = 41
        insertEventWithAnchor(caseId, "synthetic-event-cp", deriv.id, targetStartCp, targetStartCp + 6, tsEarliest = null)
        insertEventWithAnchor(caseId, "synthetic-event-emoji", deriv.id, 0, 40, tsEarliest = null)

        val hit = searchHits(CaseId(caseId), "target").single()
        assertEquals("synthetic-event-cp", hit.eventId)
        assertNull(hit.timestamp, "an unknown message time stays unknown; import time is not shown instead")
        val snippet = hit.matches.single().snippet
        assertTrue(snippet.indices.none { Character.isLowSurrogate(snippet[it]) && (it == 0 || !Character.isHighSurrogate(snippet[it - 1])) })
        assertTrue(snippet.indices.none { Character.isHighSurrogate(snippet[it]) && (it == snippet.lastIndex || !Character.isLowSurrogate(snippet[it + 1])) })
    }

    @Test
    fun matchCountIsCappedAndTheCapIsReported() = runBlocking<Unit> {
        val caseId = cases.create("Case Alpha").id
        val evId = importEvidence(caseId)
        derivatives.save(evId, DerivativeKind.OCR, "ab ".repeat(20), "tool", "1")
        val capped = VaultEvidenceSearch(db, Dispatchers.IO, maxMatches = 5).search(CaseId(caseId), "ab")
        assertTrue(capped.limitReached)
        assertEquals(5, capped.hits.sumOf { it.matches.size })
        assertFalse(search.search(CaseId(caseId), "ab").limitReached)
    }

    private suspend fun insertEventWithAnchor(caseId: String, eventId: String, derivativeId: String, start: Int, end: Int, tsEarliest: String?) {
        db.eventDao().insertEvent(EventEntity(eventId, caseId))
        db.eventDao().insertRevision(
            EventRevisionEntity(
                eventId = eventId, revision = 1, kind = "message_observation",
                observedAt = "2026-10-01T09:00:00Z", availableAt = "2026-10-01T09:00:00Z", availableAtEpochMs = 1790845200000L,
                tsEarliest = tsEarliest, tsEarliestEpochMs = null, tsLatest = null, tsLatestEpochMs = null,
                tsBasis = "unknown", tsPrecision = "unknown", tsTimezone = null, collectorSessionId = null, monotonicMs = null,
                actorId = null, sourceScopeId = null, senderDisplayLabel = null, senderIdentityBasis = "unknown",
                senderAssociationReview = "unreviewed", sourceKind = "selected_text", sourceApp = null, profileScopeId = null,
                conversationScopeId = null, sourceRecordId = null, parserVersion = "synthetic-parser-1", direction = "unknown",
                reviewPriority = "ordinary", severityBasis = "unknown", severityReferenceIdsJson = "[]",
                confirmationStatus = "pending", confirmationScope = "not_reviewed", reviewedAt = null,
                dedupStatus = "distinct_observation", canonicalEventId = null, dedupMethod = "synthetic-dedup-1",
                coverageContext = "unknown", textStatus = "available", outgoingCoverage = "unknown", gapReferenceIdsJson = "[]",
                unwantedContact = "unknown", retentionMode = "encrypted_candidate", expiresAt = null, consentGeneration = 0,
            ),
        )
        db.eventDao().insertAnchors(
            listOf(
                EvidenceAnchorEntity(
                    id = "anchor-$eventId", eventId = eventId, eventRevision = 1, referenceId = "body", artifactId = derivativeId,
                    evidenceId = null, derivativeId = null, sha256 = null, representation = "preserved_import", locatorKind = "text",
                    startCp = start, endCp = end, startMs = null, endMs = null, pageIndex = null, regionId = null,
                ),
            ),
        )
    }
}
