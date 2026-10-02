package org.sakshi.app.importing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.sakshi.app.R
import org.sakshi.app.ui.components.ProgressBlock
import org.sakshi.app.ui.components.SakshiScaffold
import org.sakshi.app.ui.components.SupportingText
import org.sakshi.app.ui.theme.Spacing

@Composable
fun ProgressContent(state: ImportUiState.Saving, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    SakshiScaffold(title = null, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize().padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
        ) {
            ProgressBlock(
                done = state.done,
                total = state.total,
                label = stringResource(R.string.import_saving_progress, state.done, state.total),
                onCancel = onCancel,
            )
            SupportingText(stringResource(R.string.import_saving_hint))
        }
    }
}
