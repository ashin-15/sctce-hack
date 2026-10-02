package org.sakshi.app.lock

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import org.sakshi.app.R
import org.sakshi.app.session.FailureReason
import org.sakshi.app.session.SessionState

/** Shown for every state except [SessionState.Unlocked]. */
@Composable
fun LockScreen(
    state: SessionState,
    notCompleted: Boolean,
    onUnlock: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state) {
            SessionState.Locked -> Message(R.string.lock_title, R.string.lock_body) {
                if (notCompleted) Text(stringResource(R.string.lock_not_completed), style = MaterialTheme.typography.bodyMedium)
                ActionButton(R.string.lock_unlock, onUnlock)
            }
            SessionState.Unlocking -> Message(R.string.lock_title, R.string.lock_unlocking) { CircularProgressIndicator() }
            SessionState.NoDeviceLock -> Message(R.string.no_lock_title, R.string.no_lock_body) {
                val context = LocalContext.current
                ActionButton(R.string.no_lock_open_settings) {
                    context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            SessionState.KeyInvalidated -> Message(R.string.invalidated_title, R.string.invalidated_body) {}
            is SessionState.Failed -> Message(R.string.failed_title, failureBody(state.reason)) {
                ActionButton(R.string.failed_retry, onRetry)
            }
            is SessionState.Unlocked -> Unit
        }
    }
}

private fun failureBody(reason: FailureReason): Int = when (reason) {
    FailureReason.KEY_UNAVAILABLE -> R.string.failed_key_body
    FailureReason.STORAGE_ERROR -> R.string.failed_storage_body
}

@Composable
private fun Message(title: Int, body: Int, extra: @Composable () -> Unit) {
    Text(stringResource(title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
    Text(stringResource(body), style = MaterialTheme.typography.bodyLarge)
    extra()
}

@Composable
private fun ActionButton(label: Int, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(label)) }
}
