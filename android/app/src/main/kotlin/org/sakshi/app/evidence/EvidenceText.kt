package org.sakshi.app.evidence

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.sakshi.app.R

@Composable
fun kindText(kind: EvidenceKind): String = stringResource(
    when (kind) {
        EvidenceKind.TEXT -> R.string.kind_text
        EvidenceKind.TEXT_FILE -> R.string.kind_text_file
        EvidenceKind.IMAGE -> R.string.kind_image
        EvidenceKind.AUDIO -> R.string.kind_audio
        EvidenceKind.VIDEO -> R.string.kind_video
        EvidenceKind.PDF -> R.string.kind_pdf
        EvidenceKind.ARCHIVE -> R.string.kind_archive
        EvidenceKind.NOTE -> R.string.kind_note
        EvidenceKind.FILE -> R.string.kind_file
    },
)

@Composable
fun analysisText(label: AnalysisLabel): String = stringResource(
    when (label) {
        AnalysisLabel.WAITING_FOR_TEXT -> R.string.analysis_waiting
        AnalysisLabel.NOT_ANALYSED -> R.string.analysis_not_analysed
        AnalysisLabel.ANALYSED -> R.string.analysis_done_label
        AnalysisLabel.ANALYSED_IN_PART -> R.string.analysis_done_in_part_label
    },
)

@Composable
fun integrityText(status: IntegrityStatus): String = stringResource(
    when (status) {
        IntegrityStatus.CHECKING -> R.string.integrity_checking
        IntegrityStatus.INTACT -> R.string.integrity_intact
        IntegrityStatus.CHANGED -> R.string.integrity_changed
        IntegrityStatus.FILE_MISSING -> R.string.integrity_missing
        IntegrityStatus.NOT_AUTHENTICATED -> R.string.integrity_not_authenticated
        IntegrityStatus.KEY_UNAVAILABLE -> R.string.integrity_key_unavailable
        IntegrityStatus.NOT_COMPLETED -> R.string.integrity_not_completed
    },
)
