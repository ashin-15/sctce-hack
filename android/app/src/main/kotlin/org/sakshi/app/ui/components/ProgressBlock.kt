package org.sakshi.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import org.sakshi.app.R
import org.sakshi.app.ui.theme.Spacing

/** How many times a long job is announced to accessibility services: at each quarter and at the end. */
private const val ANNOUNCE_STEPS = 4

/** Which announcement step [done] of [total] has reached. Changes at most [ANNOUNCE_STEPS] + 1 times. */
internal fun announceStep(done: Int, total: Int): Int = if (total <= 0) 0 else (done.coerceIn(0, total) * ANNOUNCE_STEPS) / total

/**
 * Progress for work that takes a while: [label] ("Saving 2 of 5"), a bar, and a cancel button. The label is a polite
 * live region whose spoken text only changes at each quarter, so TalkBack is not interrupted for every item. When the
 * amount of work is not known, [indeterminate] shows a bar that only moves and [done] and [total] are ignored. [showCancel]
 * false hides the button while a cancellation is already on its way.
 */
@Composable
fun ProgressBlock(
    done: Int,
    total: Int,
    label: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    indeterminate: Boolean = false,
    showCancel: Boolean = true,
) {
    val step = announceStep(done, total)
    var announced by remember { mutableStateOf(label) }
    LaunchedEffect(step) { announced = label }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Text(
            label,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
                contentDescription = announced
            },
        )
        if (indeterminate) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        } else {
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else done.toFloat() / total },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
        if (showCancel) SecondaryButton(stringResource(R.string.dialog_cancel), onCancel, Modifier.fillMaxWidth())
    }
}
