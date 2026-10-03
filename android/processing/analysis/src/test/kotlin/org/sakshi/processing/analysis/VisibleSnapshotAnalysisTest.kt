package org.sakshi.processing.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import org.sakshi.core.model.Direction
import org.sakshi.core.model.TextStatus
import org.sakshi.core.model.TimeBasis
import org.sakshi.core.vault.AcquisitionKind

class VisibleSnapshotAnalysisTest : AnalysisTestBase() {
    @Test
    fun aChatShapedSnapshotNeverInventsMessageBoundariesOrAttribution() = runBlocking<Unit> {
        val text = "03/10/2026, 10:00 - Alex: I will hurt you\n03/10/2026, 10:01 - Sam: stop\n03/10/2026, 10:02 - Alex: worthless"
        val id = importBytes(text.toByteArray(), kind = AcquisitionKind.VISIBLE_TEXT_SNAPSHOT)
        val result = assertIs<AnalysisOutcome.Analysed>(analysis.analyse(id))
        assertEquals(InputKind.PLAIN_TEXT, result.kind)
        assertEquals(1, result.eventCount)
        val event = events().single()
        assertEquals(Direction.UNKNOWN, event.direction)
        assertEquals(TimeBasis.UNKNOWN, event.timestamp.basis)
        assertNull(event.sender.displayLabel)
        assertEquals(TextStatus.EXTRACTION_UNCERTAIN, event.coverage.textStatus)
        assertEquals(text, EventText(vault).bodyOf(event))
        assertSchemaValid(listOf(event))
    }
}
