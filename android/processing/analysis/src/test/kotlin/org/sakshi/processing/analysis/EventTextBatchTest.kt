package org.sakshi.processing.analysis

import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.sakshi.core.model.ReferenceId
import org.sakshi.processing.text.DateOrder

/** Counts reads so a test can show each derivative is read once. */
private class CountingDerivatives(private val inner: TextDerivatives) : TextDerivatives by inner {
    val reads = mutableListOf<String>()

    override suspend fun text(derivativeId: String): String? {
        reads += derivativeId
        return inner.text(derivativeId)
    }
}

class EventTextBatchTest : AnalysisTestBase() {
    private val options = ExportOptions(DateOrder.DAY_MONTH, ZoneId.of("Asia/Kolkata"), SyntheticExports.OWNER)

    private fun emojiExport(records: Int): String = buildString {
        for (i in 0 until records) {
            val body = if (i % 7 == 0) "synthetic 😀 message 👍 $i" else "synthetic message $i"
            append("%02d/10/2026, 10:%02d - %s: %s\n".format(1 + i % 28, i % 60, if (i % 3 == 0) SyntheticExports.OWNER else SyntheticExports.OTHER, body))
        }
    }

    private suspend fun analysedEvents(text: String) = run {
        assertIs<AnalysisOutcome.Analysed>(analysis.analyse(importText(text), options))
        events()
    }

    @Test
    fun bodiesOfEqualsTheSingleCallsWithOneReadPerDerivative() = runBlocking<Unit> {
        val stored = analysedEvents(emojiExport(2_000))
        assertEquals(2_000, stored.size)
        val counting = CountingDerivatives(VaultTextDerivatives(vault.derivatives))

        val batched = EventText(counting).bodiesOf(stored)

        val single = EventText(vault)
        assertEquals(stored.associate { it.eventId to single.bodyOf(it) }, batched)
        assertTrue(batched.values.any { it?.contains("😀") == true })
        assertEquals(stored.flatMap { it.evidenceReferences }.map { it.artifactId.value }.distinct(), counting.reads)
        assertEquals(1, counting.reads.size)
    }

    @Test
    fun bodiesOfGivesNullWhereBodyOfDoes() = runBlocking<Unit> {
        val stored = analysedEvents(SyntheticExports.EIGHT_MESSAGES)
        val outOfRange = stored[0].let { event ->
            event.copy(evidenceReferences = event.evidenceReferences.map { it.copy(locator = org.sakshi.core.model.Locator.Text(0, 9_999_999)) })
        }
        val noReference = stored[1].copy(evidenceReferences = emptyList())
        val whole = stored[2].copy(evidenceReferences = stored[2].evidenceReferences.map { it.copy(locator = org.sakshi.core.model.Locator.WholeArtifact) })

        val batched = EventText(vault).bodiesOf(listOf(outOfRange, noReference, whole, stored[3]))

        assertNull(batched.getValue(outOfRange.eventId))
        assertNull(batched.getValue(noReference.eventId))
        assertNull(batched.getValue(whole.eventId))
        assertEquals(EventText(vault).bodyOf(stored[3]), batched.getValue(stored[3].eventId))
        assertEquals(emptyMap(), EventText(vault).bodiesOf(emptyList()))
    }

    @Test
    fun quotesOfCoversEveryReferenceWithOneRead() = runBlocking<Unit> {
        val event = analysedEvents(SyntheticExports.EIGHT_MESSAGES).first()
        val extra = event.evidenceReferences.first().copy(referenceId = ReferenceId("synthetic-extra"))
        val withTwo = event.copy(evidenceReferences = event.evidenceReferences + extra)
        val counting = CountingDerivatives(VaultTextDerivatives(vault.derivatives))

        val quotes = EventText(counting).quotesOf(withTwo)

        val single = EventText(vault)
        assertEquals(withTwo.evidenceReferences.associate { it.referenceId to single.quote(withTwo, it) }, quotes)
        assertEquals(2, quotes.size)
        assertEquals(1, counting.reads.size)
    }

    @Test
    fun theNewInterfaceLetsATestSubstituteTheAnalyser() = runBlocking<Unit> {
        val analyser: TextAnalyser = analysis
        assertIs<AnalysisOutcome.NotAnalysable>(analyser.analyse("synthetic-none"))
    }
}
