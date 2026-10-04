package org.sakshi.app.evidence

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.semantics
import java.time.ZoneId
import org.sakshi.app.R
import org.sakshi.app.ui.components.IconTile
import org.sakshi.app.ui.components.MenuAction
import org.sakshi.app.ui.components.NoteKind
import org.sakshi.app.ui.components.OverflowMenuButton
import org.sakshi.app.ui.components.SakshiCard
import org.sakshi.app.ui.components.SecondaryButton
import org.sakshi.app.ui.components.StatusChip
import org.sakshi.app.ui.components.StatusNote
import org.sakshi.app.ui.components.SupportingText
import org.sakshi.app.ui.components.Tone
import org.sakshi.app.ui.formatByteSize
import org.sakshi.app.ui.formatCreatedDate
import org.sakshi.app.ui.theme.Spacing

/** One saved item: a kind icon, its type word, size and date, status chips, and the last integrity result. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EvidenceItem(
    row: EvidenceRow,
    integrity: IntegrityStatus?,
    onCheck: () -> Unit,
    onDelete: () -> Unit,
    onAnalyse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val received = formatCreatedDate(row.receivedAt, locale, ZoneId.systemDefault())
        ?: stringResource(R.string.detail_date_unknown)
    val kind = kindText(row.kind)
    SakshiCard(modifier) {
        Row(verticalAlignment = Alignment.Top) {
            Row(
                Modifier.weight(1f).heightIn(min = Spacing.touchTarget)
                    .padding(start = Spacing.lg, top = Spacing.md, bottom = Spacing.md)
                    .semantics(mergeDescendants = true) { },
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                IconTile(kindIcon(row.kind))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(kind, style = MaterialTheme.typography.titleSmall)
                    SupportingText(stringResource(R.string.evidence_size_date, formatByteSize(row.byteSize, locale), received))
                    FlowRow(
                        modifier = Modifier.padding(top = Spacing.xs),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        StatusChip(analysisChipText(row.analysis, row.canAnalyse), tone = analysisTone(row.analysis))
                        if (integrity != null) StatusChip(integrityChipText(integrity), tone = integrityTone(integrity))
                    }
                    integrity?.let { status -> integritySentence(status)?.let { StatusNote(integrityKind(status), it) } }
                    if (row.canAnalyse) {
                        val label = when (row.kind) {
                            EvidenceKind.IMAGE -> R.string.evidence_read_image
                            EvidenceKind.AUDIO -> R.string.evidence_read_audio
                            else -> R.string.evidence_analyse
                        }
                        SecondaryButton(stringResource(label), onAnalyse, Modifier.padding(top = Spacing.xs))
                    }
                }
            }
            OverflowMenuButton(
                contentDescription = stringResource(R.string.evidence_options, kind, received),
                items = listOf(
                    MenuAction(stringResource(R.string.evidence_check)) { onCheck() },
                    MenuAction(stringResource(R.string.evidence_delete), destructive = true) { onDelete() },
                ),
                modifier = Modifier.padding(horizontal = Spacing.xs),
            )
        }
    }
}

@Composable
private fun kindIcon(kind: EvidenceKind): ImageVector = when (kind) {
    EvidenceKind.TEXT, EvidenceKind.TEXT_FILE, EvidenceKind.PDF, EvidenceKind.FILE -> ImageVector.vectorResource(R.drawable.ic_document)
    EvidenceKind.NOTE -> Icons.Default.Create
    EvidenceKind.IMAGE -> ImageVector.vectorResource(R.drawable.ic_image)
    EvidenceKind.AUDIO -> ImageVector.vectorResource(R.drawable.ic_mic)
    EvidenceKind.VIDEO -> Icons.Default.PlayArrow
    EvidenceKind.ARCHIVE -> ImageVector.vectorResource(R.drawable.ic_folder)
}

private fun analysisTone(label: AnalysisLabel): Tone = when (label) {
    AnalysisLabel.ANALYSED -> Tone.Success
    AnalysisLabel.ANALYSED_IN_PART -> Tone.Warning
    AnalysisLabel.WAITING_FOR_TEXT, AnalysisLabel.NOT_ANALYSED -> Tone.Neutral
}

private fun integrityTone(status: IntegrityStatus): Tone = when (status) {
    IntegrityStatus.INTACT -> Tone.Success
    IntegrityStatus.CHECKING -> Tone.Neutral
    IntegrityStatus.CHANGED, IntegrityStatus.FILE_MISSING, IntegrityStatus.NOT_AUTHENTICATED, IntegrityStatus.KEY_UNAVAILABLE -> Tone.Warning
    IntegrityStatus.NOT_COMPLETED -> Tone.Critical
}

/** A mismatch needs a calm look; an unfinished check is a problem. Intact and in-progress results show no sentence. */
private fun integrityKind(status: IntegrityStatus): NoteKind = when (status) {
    IntegrityStatus.CHECKING, IntegrityStatus.INTACT -> NoteKind.Info
    IntegrityStatus.CHANGED, IntegrityStatus.FILE_MISSING, IntegrityStatus.NOT_AUTHENTICATED, IntegrityStatus.KEY_UNAVAILABLE -> NoteKind.Caution
    IntegrityStatus.NOT_COMPLETED -> NoteKind.Problem
}
