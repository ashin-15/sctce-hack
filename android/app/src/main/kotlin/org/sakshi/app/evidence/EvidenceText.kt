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

/** The one or two word analysis state shown as a chip. [canAnalyse] separates "ready" from "kept as received". */
@Composable
fun analysisChipText(label: AnalysisLabel, canAnalyse: Boolean): String = stringResource(
    when (label) {
        AnalysisLabel.WAITING_FOR_TEXT -> if (canAnalyse) R.string.chip_ready else R.string.chip_stored
        AnalysisLabel.NOT_ANALYSED -> R.string.chip_stored
        AnalysisLabel.ANALYSED -> R.string.chip_analysed
        AnalysisLabel.ANALYSED_IN_PART -> R.string.chip_analysed_part
    },
)

@Composable
fun integrityChipText(status: IntegrityStatus): String = stringResource(
    when (status) {
        IntegrityStatus.CHECKING -> R.string.chip_integrity_checking
        IntegrityStatus.INTACT -> R.string.chip_integrity_intact
        IntegrityStatus.CHANGED -> R.string.chip_integrity_changed
        IntegrityStatus.FILE_MISSING -> R.string.chip_integrity_missing
        IntegrityStatus.NOT_AUTHENTICATED -> R.string.chip_integrity_not_verified
        IntegrityStatus.KEY_UNAVAILABLE -> R.string.chip_integrity_key_unavailable
        IntegrityStatus.NOT_COMPLETED -> R.string.chip_integrity_failed
    },
)

/** The one-sentence explanation of a result that is neither intact nor still checking, or null for those two. */
@Composable
fun integritySentence(status: IntegrityStatus): String? {
    val sentence = when (status) {
        IntegrityStatus.CHECKING, IntegrityStatus.INTACT -> return null
        IntegrityStatus.CHANGED -> R.string.integrity_changed
        IntegrityStatus.FILE_MISSING -> R.string.integrity_missing
        IntegrityStatus.NOT_AUTHENTICATED -> R.string.integrity_not_authenticated
        IntegrityStatus.KEY_UNAVAILABLE -> R.string.integrity_key_unavailable
        IntegrityStatus.NOT_COMPLETED -> R.string.integrity_not_completed
    }
    return stringResource(sentence)
}
