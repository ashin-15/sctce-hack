package org.sakshi.processing.analysis

import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.sakshi.core.database.SupportState
import org.sakshi.processing.text.DateOrder

class CancelledAnalysisTest : AnalysisTestBase() {
    private val options = ExportOptions(DateOrder.DAY_MONTH, ZoneId.of("Asia/Kolkata"), SyntheticExports.OWNER)

    @Test
    fun analysingAgainAfterACancelBehavesLikeAFirstRun() = runBlocking<Unit> {
        val evidenceId = importText(SyntheticExports.EIGHT_MESSAGES)
        val saved = CompletableDeferred<Unit>()
        val real = VaultTextDerivatives(vault.derivatives)
        val stopsAfterSaving = object : TextDerivatives by real {
            override suspend fun saveParsedText(evidenceId: String, text: String): DerivativeText {
                real.saveParsedText(evidenceId, text)
                saved.complete(Unit)
                awaitCancellation()
            }
        }
        val interrupted = TextAnalysis(vault, stopsAfterSaving, RulesEngineFactory.default(), clock, ids, AnalysisLimits(), Dispatchers.Default)
        val run = launch { interrupted.analyse(evidenceId, options) }
        saved.await()
        run.cancelAndJoin()

        assertEquals(emptyList(), events())
        assertEquals(1, vault.derivatives.listForEvidence(evidenceId).size)
        assertEquals(SupportState.SAVED, vault.evidence.details(evidenceId)?.supportState)

        val outcome = assertIs<AnalysisOutcome.Analysed>(analysis.analyse(evidenceId, options))

        assertEquals(InputKind.WHATSAPP_EXPORT, outcome.kind)
        assertEquals(8, outcome.eventCount)
        assertEquals(2, outcome.suggestionCount)
        assertEquals(setOf(AnalysisWarning.SYSTEM_LINES_SKIPPED), outcome.warnings)
        assertEquals(1, vault.derivatives.listForEvidence(evidenceId).size)
        assertEquals(8, events().size)
        assertEquals(SupportState.ANALYZED, vault.evidence.details(evidenceId)?.supportState)
        assertEquals(AnalysisOutcome.NotAnalysable(NotAnalysableReason.ALREADY_ANALYSED), analysis.analyse(evidenceId, options))
    }
}
