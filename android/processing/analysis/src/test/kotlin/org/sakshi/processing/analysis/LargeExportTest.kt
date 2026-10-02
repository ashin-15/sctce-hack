package org.sakshi.processing.analysis

import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.sakshi.processing.text.DateOrder

class LargeExportTest : AnalysisTestBase() {
    @Test
    fun tenThousandRecordsBecomeTenThousandSchemaValidEvents() = runBlocking<Unit> {
        val id = importText(SyntheticExports.large(10_000))
        val options = ExportOptions(DateOrder.DAY_MONTH, ZoneId.of("Asia/Kolkata"), SyntheticExports.OWNER)
        val outcome = withTimeout(TIMEOUT_MS) { analysis.analyse(id, options) }
        val analysed = assertIs<AnalysisOutcome.Analysed>(outcome)
        assertEquals(10_000, analysed.eventCount)
        assertEquals(200, analysed.suggestionCount)
        assertEquals(emptySet(), analysed.warnings)
        val stored = events()
        assertEquals(10_000, stored.size)
        assertSchemaValid(stored.take(300))
    }

    private companion object {
        const val TIMEOUT_MS: Long = 300_000
    }
}
