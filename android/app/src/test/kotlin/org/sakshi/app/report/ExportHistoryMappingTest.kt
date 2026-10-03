package org.sakshi.app.report

import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.ZoneId
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.sakshi.app.support.ForbiddenWords
import org.sakshi.app.ui.resolve
import org.sakshi.core.vault.ExportDependencies
import org.sakshi.core.vault.ExportDrift
import org.sakshi.core.vault.ExportedSnapshot

@RunWith(RobolectricTestRunner::class)
class ExportHistoryMappingTest {
    private val resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources
    private val created = Instant.parse("2026-09-28T20:30:00Z")

    private fun text(changed: Int, removed: Int, version: Int = 2): String =
        ExportHistoryMapping.notice(EarlierExport(version, created, changed, removed), ZoneId.of("Asia/Kolkata"), Locale.UK).resolve(resources)

    @Test
    fun neverExportedShowsNothing() {
        assertNull(ExportHistoryMapping.earlierExport(null))
    }

    @Test
    fun aDriftBecomesNumbersOnly() {
        val snapshot = ExportedSnapshot("synthetic-1", 3, created.toString(), "m", "s", "k", null, ExportDependencies(emptyMap(), "c"))
        val earlier = ExportHistoryMapping.earlierExport(ExportDrift(snapshot, 4, 1))!!
        assertEquals(EarlierExport(3, created, 4, 1), earlier)
        assertFalse(earlier.upToDate)
        assertTrue(ExportHistoryMapping.earlierExport(ExportDrift(snapshot, 0, 0))!!.upToDate)
    }

    @Test
    fun upToDate() {
        assertEquals("Report version 2 was exported on ${date()}. Nothing in it has changed since.", text(0, 0))
    }

    @Test
    fun changedOnly() {
        assertEquals(
            "Report version 2 was exported on ${date()}. Since then, 3 of its messages or notes were changed. " +
                "That file stays as it was. A new export will be version 3.",
            text(3, 0),
        )
        assertTrue(text(1, 0).contains("1 of its messages or notes was changed."))
    }

    @Test
    fun removedOnly() {
        assertEquals(
            "Report version 2 was exported on ${date()}. Since then, 2 of its messages or notes are no longer in this case. " +
                "That file stays as it was. A new export will be version 3.",
            text(0, 2),
        )
        assertTrue(text(0, 1).contains("1 of its messages or notes is no longer in this case."))
    }

    @Test
    fun both() {
        assertEquals(
            "Report version 4 was exported on ${date()}. Since then, 1 of its messages or notes was changed and 2 are no longer in this case. " +
                "That file stays as it was. A new export will be version 5.",
            text(1, 2, version = 4),
        )
    }

    @Test
    fun noZeroCountIsPrintedAndNoDeveloperWordsAreUsed() {
        listOf(text(0, 0), text(2, 0), text(0, 2), text(2, 2)).forEach {
            assertFalse(it.contains("0 "), it)
            assertEquals(emptyList(), ForbiddenWords.found(it), it)
            assertFalse(ForbiddenWords.hasDash(it), it)
        }
    }

    private fun date(): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.UK)
            .withZone(ZoneId.of("Asia/Kolkata")).format(created)
}
