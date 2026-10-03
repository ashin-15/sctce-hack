package org.sakshi.app.deletion

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.sakshi.app.R
import org.sakshi.app.session.DeletionState
import org.sakshi.app.ui.components.DestructiveButton
import org.sakshi.app.ui.components.PrimaryButton
import org.sakshi.app.ui.components.SakshiScaffold
import org.sakshi.app.ui.components.SakshiTextField
import org.sakshi.app.ui.components.ScreenTitle
import org.sakshi.app.ui.components.SectionHeader
import org.sakshi.app.ui.components.SupportingText
import org.sakshi.app.ui.theme.Spacing

/** True when [typed] is the confirmation [word], ignoring case and spaces around it. */
fun confirmWordTyped(typed: String, word: String): Boolean = typed.trim().equals(word, ignoreCase = true)

/**
 * Says what "Delete everything" removes and what it does not, and asks for a typed word before the button works.
 * The word is held with `remember`, not saved instance state, and is gone when the screen is left.
 */
@Composable
fun DeleteEverythingScreen(onConfirm: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onBack)
    val word = stringResource(R.string.delete_all_confirm_word)
    var typed by remember { mutableStateOf("") }
    SakshiScaffold(
        title = stringResource(R.string.delete_all_title),
        modifier = modifier,
        onBack = onBack,
        bottomBar = {
            DestructiveButton(
                stringResource(R.string.delete_all_button),
                onConfirm,
                Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.lg),
                enabled = confirmWordTyped(typed, word),
            )
        },
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                SectionHeader(stringResource(R.string.delete_all_removed_heading))
                listOf(
                    R.string.delete_all_removed_cases,
                    R.string.delete_all_removed_files,
                    R.string.delete_all_removed_text,
                    R.string.delete_all_removed_answers,
                    R.string.delete_all_removed_activity,
                    R.string.delete_all_removed_keys,
                ).forEach { Text(stringResource(it), style = MaterialTheme.typography.bodyLarge) }
            }
            Text(stringResource(R.string.delete_all_not_affected), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.delete_all_permanent), style = MaterialTheme.typography.titleMedium)
            SupportingText(stringResource(R.string.delete_all_limit))
            SupportingText(stringResource(R.string.delete_all_slow))
            Text(stringResource(R.string.delete_all_type_prompt, word), style = MaterialTheme.typography.bodyLarge)
            SakshiTextField(typed, { typed = it }, stringResource(R.string.delete_all_field_label))
        }
    }
}

/**
 * What the whole app shows while a deletion is anything other than idle: progress with no way out, the outcome of a
 * partial or failed deletion with a way to try again, and the final confirmation. Never shows file names or paths.
 */
@Composable
fun DeletionProgressScreen(state: DeletionState, onRetry: () -> Unit, onFinish: () -> Unit, modifier: Modifier = Modifier) {
    // The key is already being destroyed; leaving would only hide what is happening.
    BackHandler(enabled = state == DeletionState.Running) { }
    SakshiScaffold(title = null, modifier = modifier) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter, vertical = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            when (state) {
                DeletionState.Running -> {
                    ScreenTitle(stringResource(R.string.delete_all_running_title))
                    SupportingText(stringResource(R.string.delete_all_running_body))
                    CircularProgressIndicator()
                }
                DeletionState.Incomplete ->
                    Outcome(R.string.delete_all_incomplete_title, R.string.delete_all_incomplete_body, R.string.delete_all_retry, onRetry)
                DeletionState.Failed ->
                    Outcome(R.string.delete_all_failed_title, R.string.delete_all_failed_body, R.string.delete_all_retry, onRetry)
                DeletionState.Done ->
                    Outcome(R.string.delete_all_done_title, R.string.delete_all_done_body, R.string.delete_all_done_continue, onFinish)
                DeletionState.Idle -> Unit
            }
        }
    }
}

@Composable
private fun Outcome(title: Int, body: Int, action: Int, onAction: () -> Unit) {
    ScreenTitle(stringResource(title))
    Text(stringResource(body), style = MaterialTheme.typography.bodyLarge)
    PrimaryButton(stringResource(action), onAction)
}
