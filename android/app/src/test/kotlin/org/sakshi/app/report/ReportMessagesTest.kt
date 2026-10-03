package org.sakshi.app.report

import androidx.test.core.app.ApplicationProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.sakshi.app.support.ForbiddenWords
import org.sakshi.app.ui.resolve
import org.sakshi.export.report.ExportFailure
import org.sakshi.export.report.RefusalReason

@RunWith(RobolectricTestRunner::class)
class ReportMessagesTest {
    private val resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources

    private fun assertDistinctAndClean(texts: List<String>) {
        assertTrue(texts.all { it.isNotBlank() })
        assertEquals(texts.size, texts.toSet().size)
        texts.forEach {
            assertEquals(emptyList(), ForbiddenWords.found(it), it)
            assertTrue(!ForbiddenWords.hasDash(it), it)
        }
    }

    @Test
    fun everyRefusalReasonHasAPlainSentence() {
        assertDistinctAndClean(RefusalReason.entries.map { ReportMessages.refusal(it).resolve(resources) })
    }

    @Test
    fun aChangedCaseTellsThePersonToPreviewAgain() {
        val text = ReportMessages.refusal(RefusalReason.CHANGED_SINCE_PREVIEW).resolve(resources)
        assertEquals(
            "Something in this case changed after the preview was made, so the file was not created. Go back and preview the report again.",
            text,
        )
    }

    @Test
    fun everyExportFailureHasAPlainSentence() {
        assertDistinctAndClean(ExportFailure.entries.map { ReportMessages.failure(it).resolve(resources) })
    }

    @Test
    fun everyBlockReasonHasAPlainSentence() {
        assertDistinctAndClean(BlockReason.entries.map { ReportRows.blockText(it).resolve(resources) })
    }

    @Test
    fun theSentencesNameTheCauseInWords() {
        assertTrue(ReportMessages.failure(ExportFailure.SIGNING_FAILED).resolve(resources).contains("signing key"))
        assertTrue(ReportMessages.failure(ExportFailure.VERIFICATION_FAILED).resolve(resources).contains("not kept"))
        assertTrue(ReportMessages.failure(ExportFailure.ORIGINAL_UNAVAILABLE).resolve(resources).contains("original"))
        assertTrue(ReportMessages.failure(ExportFailure.IO_ERROR).resolve(resources).contains("storage"))
        assertTrue(ReportMessages.cancelled.resolve(resources).contains("cancelled"))
    }
}
