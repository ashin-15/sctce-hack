package org.sakshi.app.session

import java.io.Closeable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.sakshi.core.vault.Vault
import org.sakshi.core.vault.VaultKeyException

/**
 * Owns the lock state and the open vault. Every method must be called from the thread that [scope] runs on
 * (the main thread in the app); opening itself happens inside [VaultOpener].
 */
class SessionController(
    private val opener: VaultOpener,
    private val deviceSecurity: DeviceSecurity,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow<SessionState>(SessionState.Locked)
    val state: StateFlow<SessionState> = mutableState.asStateFlow()

    /** Incremented by [lock]; an open that finishes under an older value is discarded. */
    private var epoch = 0
    private var lastOpen: Job? = null

    /** Re-reads the device lock status unless the session is unlocked or unlocking. */
    fun refresh() {
        when (mutableState.value) {
            is SessionState.Unlocked, SessionState.Unlocking -> Unit
            else -> mutableState.value =
                if (deviceSecurity.isDeviceSecure()) SessionState.Locked else SessionState.NoDeviceLock
        }
    }

    /** Called after the user authenticated. Opens the vault; repeated calls while opening or unlocked are ignored. */
    fun onAuthenticated() {
        if (mutableState.value is SessionState.Unlocked || mutableState.value == SessionState.Unlocking) return
        mutableState.value = SessionState.Unlocking
        val ticket = epoch
        val previous = lastOpen
        lastOpen = scope.launch {
            // An earlier open that was abandoned by lock() must finish and close before another starts.
            previous?.join()
            val outcome = try {
                Outcome.Opened(opener.open())
            } catch (e: CancellationException) {
                throw e
            } catch (e: VaultKeyException.NotAuthenticated) {
                Outcome.Rejected(SessionState.Locked)
            } catch (e: VaultKeyException.Invalidated) {
                Outcome.Rejected(SessionState.KeyInvalidated)
            } catch (e: VaultKeyException.Unavailable) {
                Outcome.Rejected(SessionState.Failed(FailureReason.KEY_UNAVAILABLE))
            } catch (e: Exception) {
                Outcome.Rejected(SessionState.Failed(FailureReason.STORAGE_ERROR))
            }
            finish(ticket, outcome)
        }
    }

    /** Closes the vault, if any, and returns to [SessionState.Locked]. Safe to call repeatedly. */
    fun lock() {
        epoch++
        (mutableState.value as? SessionState.Unlocked)?.vault.closeQuietly()
        mutableState.value = SessionState.Locked
    }

    private fun finish(ticket: Int, outcome: Outcome) {
        when (outcome) {
            is Outcome.Opened ->
                if (ticket == epoch) mutableState.value = SessionState.Unlocked(outcome.vault) else outcome.vault.closeQuietly()
            is Outcome.Rejected -> if (ticket == epoch) mutableState.value = outcome.next
        }
    }

    private fun Closeable?.closeQuietly() {
        try {
            this?.close()
        } catch (e: Exception) {
            // The vault is being discarded; a failed close leaves nothing the caller can act on.
        }
    }

    private sealed interface Outcome {
        class Opened(val vault: Vault) : Outcome

        class Rejected(val next: SessionState) : Outcome
    }
}
