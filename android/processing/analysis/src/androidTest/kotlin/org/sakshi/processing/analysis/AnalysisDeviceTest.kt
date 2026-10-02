package org.sakshi.processing.analysis

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.Direction
import org.sakshi.core.model.Event
import org.sakshi.core.temporal.EvidenceView
import org.sakshi.core.vault.AccessClass
import org.sakshi.core.vault.AcquisitionKind
import org.sakshi.core.vault.ImportRequest
import org.sakshi.core.vault.KeystoreKeyWrapper
import org.sakshi.core.vault.Vault
import org.sakshi.processing.text.DateOrder

/** Synthetic data only, in the real encrypted vault of the test package's own sandbox. */
class AnalysisDeviceTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val wrapper = KeystoreKeyWrapper(alias = "synthetic-analysis-${UUID.randomUUID()}", requireUserAuthentication = false)
    private var vault: Vault? = null
    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")
    private val options = ExportOptions(DateOrder.DAY_MONTH, zone, SyntheticExports.OWNER)

    @After
    fun cleanUp() {
        runCatching { vault?.close() }
        runCatching { wrapper.delete() }
        File(context.noBackupFilesDir, "vault").deleteRecursively()
    }

    private fun analyseExport(records: Int): Triple<AnalysisOutcome.Analysed, Long, CaseId> = runBlocking {
        val opened = Vault.open(context, wrapper).also { vault = it }
        val case = opened.cases.create("synthetic-bench-$records")
        val bytes = SyntheticExports.large(records).toByteArray(Charsets.UTF_8)
        val request = ImportRequest(
            case.id, AcquisitionKind.SHARED_TEXT, AccessClass.USER_MEDIATED, "synthetic-device-test",
            "text/plain", null, "synthetic.txt", null, MAX_BYTES,
        )
        val evidenceId = opened.evidence.import(request, ByteArrayInputStream(bytes)).id
        val analysis = TextAnalysis(opened, RulesEngineFactory.default(), Instant::now, { UUID.randomUUID().toString() })
        val started = System.nanoTime()
        val outcome = assertIs<AnalysisOutcome.Analysed>(analysis.analyse(evidenceId, options))
        Triple(outcome, (System.nanoTime() - started) / NANOS_PER_MS, CaseId(case.id))
    }

    @Test
    fun analysesOneThousandMessages() {
        val (outcome, millis, _) = analyseExport(1_000)
        assertEquals(1_000, outcome.eventCount)
        assertEquals(20, outcome.suggestionCount)
        record("analyse_1000", "total_ms" to millis, "events" to outcome.eventCount, *deviceState(context))
    }

    @Test
    fun analysesTenThousandMessagesAndComputesPatterns() = runBlocking<Unit> {
        val (outcome, millis, caseId) = analyseExport(10_000)
        assertEquals(10_000, outcome.eventCount)
        assertEquals(200, outcome.suggestionCount)
        record("analyse_10000", "total_ms" to millis, "events" to outcome.eventCount, *deviceState(context))

        val patterns = CasePatterns(checkNotNull(vault), Instant::now)
        val started = System.nanoTime()
        val view = patterns.compute(caseId, EvidenceView.CONFIRMED_ONLY, zone)
        val patternMillis = (System.nanoTime() - started) / NANOS_PER_MS
        assertEquals(10_000, view.result.timeline.size)
        assertTrue(view.explanations.size == view.result.patterns.size)
        record(
            "patterns_10000",
            "compute_ms" to patternMillis,
            "patterns" to view.result.patterns.size,
            *deviceState(context),
        )

        val supportStarted = System.nanoTime()
        val withSupport = patterns.compute(caseId, EvidenceView.CONFIRMED_ONLY, zone, withSupportingEvents = true)
        val supportMillis = (System.nanoTime() - supportStarted) / NANOS_PER_MS
        assertEquals(view.explanations, withSupport.explanations)
        assertTrue(withSupport.supportingEvents.isNotEmpty())
        record(
            "patterns_with_support_10000",
            "compute_ms" to supportMillis,
            "supporting_events" to withSupport.supportingEvents.size,
            *deviceState(context),
        )
        observeFirstAndIncremental(checkNotNull(vault), caseId)
    }

    private suspend fun observeFirstAndIncremental(opened: Vault, caseId: CaseId) {
        val emissions = Channel<List<Event>>(Channel.UNLIMITED)
        val job = CoroutineScope(Dispatchers.Default).launch {
            try {
                opened.events.observeLatest(caseId).collect { emissions.send(it) }
            } catch (failure: IllegalStateException) {
                emissions.close(failure)
            }
        }
        try {
            val started = System.nanoTime()
            val first = withTimeout(OBSERVE_TIMEOUT_MS) { emissions.receive() }
            val firstMillis = (System.nanoTime() - started) / NANOS_PER_MS
            record("observe_progress", "step" to "first_received", "ms" to firstMillis)
            assertEquals(10_000, first.size)
            val target = first.first { it.direction != Direction.OUTGOING }.eventId
            val changeStarted = System.nanoTime()
            opened.review.setDirection(listOf(target), Direction.OUTGOING)
            record("observe_progress", "step" to "review_done", "ms" to (System.nanoTime() - changeStarted) / NANOS_PER_MS)
            var next = withTimeout(OBSERVE_TIMEOUT_MS) { emissions.receive() }
            while (next.first { it.eventId == target }.direction != Direction.OUTGOING) {
                next = withTimeout(OBSERVE_TIMEOUT_MS) { emissions.receive() }
            }
            val incrementalMillis = (System.nanoTime() - changeStarted) / NANOS_PER_MS
            assertEquals(10_000, next.size)
            record(
                "observe_latest_10000",
                "first_emission_ms" to firstMillis,
                "change_to_next_emission_ms" to incrementalMillis,
                *deviceState(context),
            )
        } finally {
            job.cancel()
        }
    }

    private companion object {
        const val MAX_BYTES: Long = 20_000_000L
        const val OBSERVE_TIMEOUT_MS: Long = 120_000L
        const val NANOS_PER_MS: Long = 1_000_000L
    }
}
