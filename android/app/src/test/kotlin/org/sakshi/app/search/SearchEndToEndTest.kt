package org.sakshi.app.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.sakshi.app.support.AnalysisTestBase
import org.sakshi.app.support.SyntheticChats
import org.sakshi.core.model.CaseId
import org.sakshi.processing.analysis.EventText

/** Search over text that went through the real import and analysis, as a person would produce it. Synthetic data. */
class SearchEndToEndTest : AnalysisTestBase() {
    private fun analysedCase(): String {
        val caseId = newCase("synthetic-search")
        analyseExport(importText(caseId, SyntheticChats.EIGHT_MESSAGES))
        return caseId
    }

    @Test
    fun aMatchInAnAnalysedExportOpensTheMessageItCameFrom() = runBlocking<Unit> {
        val caseId = analysedCase()
        val hit = vault.search.search(CaseId(caseId), "idiot").hits.single()
        val eventId = assertNotNull(hit.eventId, "the hit must lead to the message event")
        val event = events(caseId).single { it.eventId.value == eventId }
        assertTrue("idiot" in EventText(vault).bodyOf(event).orEmpty())
        assertEquals(SyntheticChats.OTHER, hit.actorLabel)
        assertEquals(event.timestamp.earliest?.iso, hit.timestamp)
    }

    @Test
    fun matchesInTwoMessagesOfOneExportAreTwoHitsWithDistinctKeys() = runBlocking<Unit> {
        val caseId = analysedCase()
        val hits = vault.search.search(CaseId(caseId), "you").hits
        assertEquals(2, hits.mapNotNull { it.eventId }.distinct().size, hits.toString())
        assertEquals(hits.size, hits.map { it.key }.distinct().size)
    }
}
