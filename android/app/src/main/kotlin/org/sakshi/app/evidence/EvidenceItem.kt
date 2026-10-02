package org.sakshi.app.evidence

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import java.time.ZoneId
import org.sakshi.app.R
import org.sakshi.app.ui.components.MenuAction
import org.sakshi.app.ui.components.NoteKind
import org.sakshi.app.ui.components.OverflowMenuButton
import org.sakshi.app.ui.components.SakshiCard
import org.sakshi.app.ui.components.SecondaryButton
import org.sakshi.app.ui.components.StatusNote
import org.sakshi.app.ui.components.SupportingText
import org.sakshi.app.ui.formatByteSize
import org.sakshi.app.ui.formatCreatedDate
import org.sakshi.app.ui.theme.Spacing

/** One saved item: its type word, size and date, what this version does with it, and the last integrity result. */
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
            Column(
                Modifier.weight(1f).heightIn(min = Spacing.touchTarget)
                    .padding(start = Spacing.lg, top = Spacing.md, bottom = Spacing.md)
                    .semantics(mergeDescendants = true) { },
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(kind, style = MaterialTheme.typography.titleSmall)
                SupportingText(stringResource(R.string.evidence_size_date, formatByteSize(row.byteSize, locale), received))
                StatusNote(NoteKind.Info, analysisText(row.analysis), Modifier.padding(top = Spacing.xs))
                if (integrity != null) StatusNote(integrityKind(integrity), integrityText(integrity))
                if (row.canAnalyse) SecondaryButton(stringResource(R.string.evidence_analyse), onAnalyse, Modifier.padding(top = Spacing.xs))
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

/** Intact and in-progress results are plain notes; a mismatch needs a calm look; an unfinished check is a problem. */
private fun integrityKind(status: IntegrityStatus): NoteKind = when (status) {
    IntegrityStatus.CHECKING, IntegrityStatus.INTACT -> NoteKind.Info
    IntegrityStatus.CHANGED, IntegrityStatus.FILE_MISSING, IntegrityStatus.NOT_AUTHENTICATED, IntegrityStatus.KEY_UNAVAILABLE -> NoteKind.Caution
    IntegrityStatus.NOT_COMPLETED -> NoteKind.Problem
}
