package org.sakshi.app.lock

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import org.sakshi.app.R
import org.sakshi.app.session.FailureReason
import org.sakshi.app.session.SessionState
import org.sakshi.app.ui.components.NoteKind
import org.sakshi.app.ui.components.PrimaryButton
import org.sakshi.app.ui.components.SakshiScaffold
import org.sakshi.app.ui.components.StatusNote
import org.sakshi.app.ui.theme.Spacing

/** Shown for every state except [SessionState.Unlocked]. Centred and minimal; the app name is small. */
@Composable
fun LockScreen(
    state: SessionState,
    notCompleted: Boolean,
    sharePending: Boolean,
    onUnlock: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SakshiScaffold(title = null, modifier = modifier) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when (state) {
                    SessionState.Locked -> Message(R.string.lock_title, R.string.lock_body) {
                        if (sharePending) StatusNote(NoteKind.Info, stringResource(R.string.lock_share_pending))
                        if (notCompleted) StatusNote(NoteKind.Info, stringResource(R.string.lock_not_completed))
                        PrimaryButton(stringResource(R.string.lock_unlock), onUnlock)
                    }
                    SessionState.Unlocking -> Message(R.string.lock_title, R.string.lock_unlocking) { CircularProgressIndicator() }
                    SessionState.NoDeviceLock -> Message(R.string.no_lock_title, R.string.no_lock_body) {
                        val context = LocalContext.current
                        PrimaryButton(stringResource(R.string.no_lock_open_settings), {
                            context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        })
                    }
                    SessionState.KeyInvalidated -> Message(R.string.invalidated_title, R.string.invalidated_body) {}
                    is SessionState.Failed -> Message(R.string.failed_title, failureBody(state.reason)) {
                        PrimaryButton(stringResource(R.string.failed_retry), onRetry)
                    }
                    is SessionState.Unlocked -> Unit
                }
            }
        }
    }
}

private fun failureBody(reason: FailureReason): Int = when (reason) {
    FailureReason.KEY_UNAVAILABLE -> R.string.failed_key_body
    FailureReason.STORAGE_ERROR -> R.string.failed_storage_body
}

@Composable
private fun Message(title: Int, body: Int, extra: @Composable () -> Unit) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() },
    )
    Text(stringResource(body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    extra()
}
