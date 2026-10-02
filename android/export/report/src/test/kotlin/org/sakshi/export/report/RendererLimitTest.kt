package org.sakshi.export.report

import java.io.ByteArrayOutputStream
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.sakshi.core.model.EpistemicStatus
import org.sakshi.export.bundle.GeneratorInfo

/** The limit is checked before any platform PDF call, so it can run on the JVM. Rendering itself is a device test. */
@RunWith(RobolectricTestRunner::class)
class RendererLimitTest {
    private fun model(count: Int): ReportModel {
        val block = EventBlock("synthetic-1", 1, "Record 1: Message", emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        return ReportModel(
            title = "synthetic",
            reportVersion = 1,
            generatedAt = Instant.parse("2026-10-04T00:00:00Z"),
            generator = GeneratorInfo("synthetic-app-1", emptyList(), emptyList()),
            scope = ScopeStatement(emptyList()),
            timeline = emptyList(),
            events = List(count) { block },
            patterns = emptyList(),
            unknowns = emptyList(),
            integrity = IntegrityAppendix(null, null, null, "00".repeat(32), ReportText.LIMITS, ReportText.NOT_EVIDENCE),
        )
    }

    @Test
    fun moreThanTwoThousandEventBlocksAreRefused() {
        assertFailsWith<IllegalArgumentException> {
            ReportPdfRenderer().render(model(ReportPdfRenderer.MAX_EVENT_BLOCKS + 1), ByteArrayOutputStream())
        }
    }

    @Test
    fun statusWordsAreFixedByPartType() {
        val observed = ObservedPart("q", "a", null, "l", "r")
        val statement = UserStatementPart("t", "a", "w", "l")
        assertEquals(EpistemicStatus.OBSERVED, observed.status)
        assertEquals(EpistemicStatus.USER_REPORTED, statement.status)
    }
}
