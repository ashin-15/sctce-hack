package org.sakshi.app.report

import org.sakshi.app.R
import org.sakshi.app.ui.UiText
import org.sakshi.app.ui.res
import org.sakshi.export.report.ExportFailure
import org.sakshi.export.report.RefusalReason

/** Plain sentences for every reason a report or an export file cannot be made. None carries text from the case. */
object ReportMessages {
    fun refusal(reason: RefusalReason): UiText = res(
        when (reason) {
            RefusalReason.EMPTY_SELECTION -> R.string.report_refused_empty
            RefusalReason.UNCONFIRMED_EVENT -> R.string.report_refused_unconfirmed
            RefusalReason.UNKNOWN_EVENT -> R.string.report_refused_unknown_event
            RefusalReason.CASE_MISSING -> R.string.report_refused_case_missing
            RefusalReason.UNKNOWN_EVIDENCE -> R.string.report_refused_unknown_evidence
        },
    )

    fun failure(reason: ExportFailure): UiText = res(
        when (reason) {
            ExportFailure.VERIFICATION_FAILED -> R.string.export_failed_verification
            ExportFailure.CONTENT_REJECTED -> R.string.export_failed_content
            ExportFailure.ORIGINAL_UNAVAILABLE -> R.string.export_failed_original
            ExportFailure.ORIGINAL_HASH_MISMATCH -> R.string.export_failed_original_changed
            ExportFailure.RENDER_FAILED -> R.string.export_failed_render
            ExportFailure.SIGNING_FAILED -> R.string.export_failed_key
            ExportFailure.IO_ERROR -> R.string.export_failed_storage
        },
    )

    val cancelled: UiText get() = res(R.string.export_cancelled)

    val unexpected: UiText get() = res(R.string.export_failed_unexpected)
}
