package org.sakshi.app.session

import org.sakshi.core.vault.Vault

/** Why the vault could not be opened. Deliberately not an exception message. */
enum class FailureReason { KEY_UNAVAILABLE, STORAGE_ERROR }

sealed interface SessionState {
    /** The phone has no PIN, pattern or password, so the device key cannot be used. */
    data object NoDeviceLock : SessionState

    data object Locked : SessionState

    data object Unlocking : SessionState

    class Unlocked(val vault: Vault) : SessionState

    /** The Keystore key was permanently invalidated. */
    data object KeyInvalidated : SessionState

    data class Failed(val reason: FailureReason) : SessionState
}
