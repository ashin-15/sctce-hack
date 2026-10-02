package org.sakshi.export.report

import android.os.Debug
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.sakshi.core.model.EpistemicStatus
import org.sakshi.export.bundle.GeneratorInfo

/** Renders a large synthetic report straight from a model to measure time and memory. */
@RunWith(AndroidJUnit4::class)
class LargeReportDeviceTest : DeviceTestBase() {
    private val texts = listOf(
        "Please reply to me. I will keep messaging until you answer.",
        "ദയവായി മറുപടി തരൂ.",
        "कृपया जवाब दीजिए।",
    )

    private fun model(count: Int): ReportModel {
        val blocks = (1..count).map { n ->
            EventBlock(
                eventId = "synthetic-$n",
                revision = 1,
                heading = ReportText.fill(ReportText.RECORD_HEADING, n, "Message"),
                details = listOf(ReportText.fill(ReportText.RECORD_ID, "synthetic-$n", 1)),
                observed = listOf(
                    ObservedPart(texts[n % texts.size].repeat(3), "synthetic-artifact-$n", "ab".repeat(32), "the whole saved item", "saved copy"),
                ),
                userStatements = emptyList(),
                inferred = listOf(TagPart(EpistemicStatus.USER_REPORTED, "Ordinary", "tagged by you", "synthetic-1", "no score applies", "accepted by you")),
                unreviewed = emptyList(),
            )
        }
        return ReportModel(
            title = "synthetic-large-report",
            reportVersion = 1,
            generatedAt = Instant.parse("2026-10-04T00:00:00Z"),
            generator = GeneratorInfo("synthetic-app-1", emptyList(), emptyList()),
            scope = ScopeStatement(listOf("$count of $count records")),
            timeline = blocks.map { TimelineRow(it.eventId, "1 Oct 2026, 10:00", "as written in the export, to the minute", "Person A", "name as it appears in the export, not confirmed", "incoming, received by you", "export you selected") },
            events = blocks,
            patterns = emptyList(),
            unknowns = listOf(ReportText.UNKNOWN_SENDER),
            integrity = IntegrityAppendix(null, null, null, "00".repeat(32), ReportText.LIMITS, ReportText.NOT_EVIDENCE),
        )
    }

    @Test
    fun fiveHundredEventsRenderWithinMemory() {
        val file = File(scratchDirectory(), "large.pdf")
        val before = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
        val start = System.nanoTime()
        val summary = FileOutputStream(file).use { ReportPdfRenderer().render(model(500), it) }
        val ms = (System.nanoTime() - start) / 1_000_000L
        val memory = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
        val after = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
        assertTrue(summary.pageCount > 20)
        assertTrue(file.length() > 0)
        assertEquals("%PDF", String(file.readBytes().copyOf(4)))
        record(
            "render_500_events",
            *deviceState(context),
            "events" to 500,
            "pages" to summary.pageCount,
            "pdf_bytes" to file.length(),
            "render_ms" to ms,
            "pss_kb" to memory.totalPss,
            "heap_delta_kb" to (after - before) / 1024,
        )
    }
}
