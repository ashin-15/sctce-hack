package org.sakshi.app.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.sakshi.app.support.AnalysisTestBase
import org.sakshi.app.support.SyntheticChats
import org.sakshi.core.database.EvidenceListItem
import org.sakshi.core.database.SupportState
import org.sakshi.core.vault.AcquisitionKind
import org.sakshi.processing.analysis.AnalysisOutcome
import org.sakshi.processing.analysis.NotAnalysableReason

class AnalysisQueueTest : AnalysisTestBase() {
    private fun queueOver(analyser: suspend (String) -> AnalysisOutcome = { analysis.analyse(it, null) }) =
        AnalysisQueue(vault.unanalysedText(), analyser, CoroutineScope(Job() + Dispatchers.Unconfined))

    private fun AnalysisQueue.awaitIdle(tried: Int): AutoAnalysisState =
        await(state) { it.current == null && it.waiting == 0 && it.analysed + it.needsAnswers + it.notAnalysed == tried }

    @Test
    fun savedTextIsAnalysedWithoutAnyoneStartingIt() {
        val caseId = newCase()
        importText(caseId, "I will hurt you if you reply")
        importText(caseId, "see you at lunch")
        queueOver().use { queue ->
            queue.start()
            assertEquals(2, queue.awaitIdle(2).analysed)
        }
        assertEquals(2, events(caseId).size)
    }

    @Test
    fun textImportedWhileTheQueueRunsIsPickedUp() {
        val caseId = newCase()
        queueOver().use { queue ->
            queue.start()
            importText(caseId, "a later synthetic message")
            assertEquals(1, queue.awaitIdle(1).analysed)
        }
        assertEquals(1, events(caseId).size)
    }

    @Test
    fun anExportWaitsForThePersonAndIsNotTriedAgainInTheSession() {
        val caseId = newCase()
        importText(caseId, SyntheticChats.EIGHT_MESSAGES)
        val seen = mutableListOf<String>()
        queueOver { id -> seen += id; analysis.analyse(id, null) }.use { queue ->
            queue.start()
            assertEquals(1, queue.awaitIdle(1).needsAnswers)
            importText(caseId, "another synthetic message")
            assertEquals(1, queue.awaitIdle(2).analysed)
        }
        assertEquals(2, seen.size)
        assertEquals(1, events(caseId).size)
    }

    @Test
    fun aFailureIsCountedAndTheNextItemStillRuns() {
        val caseId = newCase()
        val first = importText(caseId, "first synthetic message")
        importText(caseId, "second synthetic message")
        queueOver { id -> if (id == first) error("synthetic failure") else analysis.analyse(id, null) }.use { queue ->
            queue.start()
            val state = queue.awaitIdle(2)
            assertEquals(1, state.notAnalysed)
            assertEquals(1, state.analysed)
        }
    }

    @Test
    fun aPausedQueueStartsNothingUntilItIsResumed() {
        val caseId = newCase()
        importText(caseId, "synthetic message kept waiting")
        queueOver().use { queue ->
            queue.setPaused(true)
            queue.start()
            assertEquals(1, await(queue.state) { it.waiting == 1 }.waiting)
            assertTrue(events(caseId).isEmpty())
            queue.setPaused(false)
            assertEquals(1, queue.awaitIdle(1).analysed)
        }
    }

    @Test
    fun closingStopsARunAndLeavesTheItemForTheNextSession() {
        val caseId = newCase()
        importText(caseId, "synthetic message interrupted by the lock")
        val entered = CompletableDeferred<Unit>()
        val queue = queueOver { entered.complete(Unit); CompletableDeferred<AnalysisOutcome>().await() }
        queue.start()
        runBlocking { entered.await() }
        queue.close()
        assertTrue(events(caseId).isEmpty())

        queueOver().use { next ->
            next.start()
            assertEquals(1, next.awaitIdle(1).analysed)
        }
        assertEquals(1, events(caseId).size)
    }

    @Test
    fun aRunThePersonStartsWaitsForTheAutomaticRunOfTheMoment() {
        val candidates = MutableStateFlow(listOf("synthetic-id"))
        val release = CompletableDeferred<AnalysisOutcome>()
        val entered = CompletableDeferred<Unit>()
        val queue = AnalysisQueue(candidates, { entered.complete(Unit); release.await() }, CoroutineScope(Job() + Dispatchers.Unconfined))
        queue.use {
            queue.start()
            runBlocking { entered.await() }
            var manualRan = false
            val manual = scope.launch { queue.exclusive { manualRan = true } }
            assertFalse(manualRan)
            release.complete(AnalysisOutcome.NotAnalysable(NotAnalysableReason.ALREADY_ANALYSED))
            runBlocking { manual.join() }
            assertTrue(manualRan)
        }
    }

    @Test
    fun onlyUnanalysedTextWaitsForAutomaticAnalysis() {
        fun item(kind: String = AcquisitionKind.SHARED_TEXT, mime: String? = null, state: String = SupportState.SAVED) =
            EvidenceListItem("synthetic", "2026-10-03T00:00:00Z", kind, mime, 10L, state)
        assertTrue(waitsForAutomaticAnalysis(item()))
        assertFalse(waitsForAutomaticAnalysis(item(mime = "image/png")))
        assertFalse(waitsForAutomaticAnalysis(item(mime = "audio/ogg")))
        assertFalse(waitsForAutomaticAnalysis(item(kind = AcquisitionKind.MANUAL_NOTE)))
        assertFalse(waitsForAutomaticAnalysis(item(state = SupportState.ANALYZED)))
        assertFalse(waitsForAutomaticAnalysis(item(state = SupportState.PARTIAL)))
    }

    @Test
    fun archivedCasesAreLeftAlone() {
        val archived = newCase("synthetic archived case")
        importText(archived, "synthetic message in an archived case")
        runBlocking { vault.cases.archive(archived) }
        val active = newCase()
        importText(active, "synthetic message in an active case")
        queueOver().use { queue ->
            queue.start()
            assertEquals(1, queue.awaitIdle(1).analysed)
        }
        assertEquals(1, events(active).size)
        assertTrue(events(archived).isEmpty())
    }
}
