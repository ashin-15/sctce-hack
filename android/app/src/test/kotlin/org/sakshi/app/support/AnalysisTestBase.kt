package org.sakshi.app.support

import java.io.ByteArrayInputStream
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.BeforeTest
import kotlinx.coroutines.runBlocking
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.Event
import org.sakshi.core.vault.AccessClass
import org.sakshi.core.vault.AcquisitionKind
import org.sakshi.core.vault.ImportRequest
import org.sakshi.processing.analysis.AnalysisOutcome
import org.sakshi.processing.analysis.ExportOptions
import org.sakshi.processing.analysis.RulesEngineFactory
import org.sakshi.processing.analysis.TextAnalysis
import org.sakshi.processing.text.DateOrder

/** Synthetic chat exports. Names, dates and words are invented; none of this is real-world evidence. */
object SyntheticChats {
    const val OWNER: String = "synthetic-alex"
    const val OTHER: String = "synthetic-sam"

    /** Day-first because of the 24th, so the file fixes the order itself. */
    val EIGHT_MESSAGES: String = """
        24/09/2026, 21:03 - $OTHER: hello there
        24/09/2026, 21:05 - $OWNER: hi, please stop sending these
        25/09/2026, 08:15 - $OTHER: you are an idiot
        and this line continues
        on a third line
        25/09/2026, 08:16 - $OTHER: <Media omitted>
        25/09/2026, 08:17 - Messages and calls are end-to-end encrypted.
        25/09/2026, 08:20 - $OTHER: I will hurt you if you reply
        26/09/2026, 09:00 - $OTHER: good morning
        26/09/2026, 09:30 - $OWNER: no more messages
        26/09/2026, 10:00 - $OTHER: ok
    """.trimIndent() + "\n"

    /** Every date has both parts at most twelve, so the order cannot be read from the file. */
    val AMBIGUOUS: String = """
        01/02/2026, 10:00 - $OTHER: hello
        02/03/2026, 11:00 - $OWNER: hi
    """.trimIndent() + "\n"

    /** A stop message from the owner, then six messages from one sender in the next half hour. */
    val STOP_THEN_SIX: String = """
        24/09/2026, 21:05 - $OWNER: please stop messaging me
        24/09/2026, 21:10 - $OTHER: hello
        24/09/2026, 21:12 - $OTHER: why do you ignore me
        24/09/2026, 21:15 - $OTHER: answer me
        24/09/2026, 21:20 - $OTHER: you are an idiot
        24/09/2026, 21:25 - $OTHER: reply now
        24/09/2026, 21:30 - $OTHER: I am waiting
    """.trimIndent() + "\n"
}

val TEST_ZONE: ZoneId = ZoneId.of("Asia/Kolkata")

/** A vault plus a real text analysis over it, and helpers to put synthetic text into a case. */
abstract class AnalysisTestBase : VaultTestBase() {
    private val analysisCounter = AtomicInteger()
    protected lateinit var analysis: TextAnalysis

    @BeforeTest
    fun createAnalysis() {
        analysis = TextAnalysis(vault, RulesEngineFactory.default(), { FIXED_INSTANT }, { "synthetic-event-${analysisCounter.incrementAndGet()}" })
    }

    protected fun importText(caseId: String, text: String, kind: String = AcquisitionKind.PASTED_TEXT): String = runBlocking {
        val request = ImportRequest(caseId, kind, AccessClass.USER_MEDIATED, "synthetic-test", "text/plain", null, null, null, 20_000_000L)
        vault.evidence.import(request, ByteArrayInputStream(text.toByteArray(Charsets.UTF_8))).id
    }

    protected fun analyseExport(evidenceId: String, owner: String? = SyntheticChats.OWNER, order: DateOrder = DateOrder.DAY_MONTH) = runBlocking {
        analysis.analyse(evidenceId, ExportOptions(order, TEST_ZONE, owner)) as AnalysisOutcome.Analysed
    }

    protected fun events(caseId: String): List<Event> = runBlocking {
        vault.events.loadLatest(CaseId(caseId), java.time.Instant.ofEpochMilli(Long.MAX_VALUE))
    }
}
