package org.sakshi.app.report

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.sakshi.app.R
import org.sakshi.app.ui.UiText
import org.sakshi.app.ui.formatCreatedDate
import org.sakshi.app.ui.plural
import org.sakshi.app.ui.res
import org.sakshi.core.vault.ExportDrift

/** The newest export of a case and how many of its messages or notes differ now. Numbers and a time only, no text from the case. */
data class EarlierExport(val version: Int, val createdAt: Instant, val changed: Int, val removed: Int) {
    val upToDate: Boolean get() = changed == 0 && removed == 0
}

/** Words for the notice about an earlier export on the selection screen. Zero counts are left out of the sentence. */
object ExportHistoryMapping {
    fun earlierExport(drift: ExportDrift?): EarlierExport? = drift?.let {
        EarlierExport(it.snapshot.version, Instant.parse(it.snapshot.createdAt), it.changedEvents, it.removedEvents)
    }

    fun notice(earlier: EarlierExport, zone: ZoneId, locale: Locale): UiText {
        val exported = formatCreatedDate(earlier.createdAt.toString(), locale, zone) ?: earlier.createdAt.toString()
        val intro = res(R.string.report_history_intro, earlier.version.toString(), exported)
        if (earlier.upToDate) return res(R.string.report_history_join_two, intro, res(R.string.report_history_unchanged))
        val changes = when {
            earlier.removed == 0 -> res(R.string.report_history_since, plural(R.plurals.report_history_changed, earlier.changed, earlier.changed))
            earlier.changed == 0 -> res(R.string.report_history_since, plural(R.plurals.report_history_removed, earlier.removed, earlier.removed))
            else -> res(
                R.string.report_history_since_both,
                plural(R.plurals.report_history_changed, earlier.changed, earlier.changed),
                plural(R.plurals.report_history_removed_short, earlier.removed, earlier.removed),
            )
        }
        return res(R.string.report_history_join_three, intro, changes, res(R.string.report_history_kept, earlier.version + 1))
    }
}
