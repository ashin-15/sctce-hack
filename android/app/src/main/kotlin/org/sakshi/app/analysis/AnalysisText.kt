package org.sakshi.app.analysis

import org.sakshi.app.R
import org.sakshi.app.ui.UiText
import org.sakshi.app.ui.plural
import org.sakshi.app.ui.res
import org.sakshi.processing.analysis.AnalysisOutcome
import org.sakshi.processing.analysis.AnalysisWarning
import org.sakshi.processing.analysis.NotAnalysableReason

/** One plain sentence per reason an item was not analysed. Never an error name or a stack. */
fun refusalText(reason: NotAnalysableReason): UiText = res(
    when (reason) {
        NotAnalysableReason.NOT_TEXT -> R.string.analysis_refused_not_text
        NotAnalysableReason.TOO_LARGE -> R.string.analysis_refused_too_large
        NotAnalysableReason.PRESERVE_ONLY_TYPE -> R.string.analysis_refused_preserve_only
        NotAnalysableReason.MANUAL_NOTE -> R.string.analysis_refused_note
        NotAnalysableReason.EVIDENCE_MISSING -> R.string.analysis_refused_missing
        NotAnalysableReason.UNREADABLE -> R.string.analysis_refused_unreadable
        NotAnalysableReason.ALREADY_ANALYSED -> R.string.analysis_refused_already
    },
)

/** Each warning in words; [count] is how many records it concerns, where a count helps. */
fun warningText(warning: AnalysisWarning, count: Int): UiText = when (warning) {
    AnalysisWarning.DATE_ORDER_OVERRIDDEN -> res(R.string.analysis_warn_date_order)
    AnalysisWarning.SYSTEM_LINES_SKIPPED -> plural(R.plurals.analysis_warn_system_lines, count, count)
    AnalysisWarning.UNRESOLVED_TIMES -> plural(R.plurals.analysis_warn_times, count, count)
    AnalysisWarning.RECORD_LIMIT_REACHED -> res(R.string.analysis_warn_limit)
    AnalysisWarning.UNPARSED_PREFIX_SKIPPED -> res(R.string.analysis_warn_prefix)
    AnalysisWarning.UNSUPPORTED_LANGUAGE_PRESENT -> res(R.string.analysis_warn_language)
    AnalysisWarning.CUE_LIST_NOT_REVIEWED -> res(R.string.analysis_warn_cue_list)
}

fun warningTexts(result: AnalysisOutcome.Analysed): List<UiText> =
    AnalysisWarning.entries.filter { it in result.warnings }.map { warningText(it, result.warningCounts[it] ?: 1) }
