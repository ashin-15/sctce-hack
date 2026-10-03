package org.sakshi.app.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.sakshi.app.onboarding.OnboardingStore
import org.sakshi.core.vault.VaultDestroyResult

/** Where "Delete everything" is. Anything other than [Idle] takes over the whole app until it is dealt with. */
sealed interface DeletionState {
    data object Idle : DeletionState

    data object Running : DeletionState

    /** Something could not be removed, or the app was closed half way. The key is already gone; trying again finishes it. */
    data object Incomplete : DeletionState

    /** Deleting stopped with an error. Nothing is claimed as deleted. */
    data object Failed : DeletionState

    data object Done : DeletionState
}

/**
 * Deletes everything Sakshi saved. Application-wide, not tied to a screen or to the unlocked session, because the
 * session ends part way through and the work must not stop when the person leaves or the screen is recreated.
 * [scope] is the application scope for that reason: a view model scope would cancel the deletion half way.
 *
 * Every method must be called from the thread that [scope] runs on (the main thread in the app).
 */
class DeletionController(
    private val session: SessionController,
    private val onboarding: OnboardingStore,
    private val marker: DeletionMarker,
    private val destroyer: VaultDestroyer,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow<DeletionState>(if (marker.isPending()) DeletionState.Incomplete else DeletionState.Idle)
    val state: StateFlow<DeletionState> = mutableState.asStateFlow()

    /**
     * Starts the deletion. [release] runs first, on the calling thread, and must stop everything that holds saved data
     * (services, export files, the text recognition engine). The session is then ended, which closes the open
     * database, and the deletion itself runs in the background. Ignored unless the state is [DeletionState.Idle].
     */
    fun start(release: () -> Unit) {
        if (mutableState.value != DeletionState.Idle) return
        marker.setPending()
        mutableState.value = DeletionState.Running
        try {
            release()
        } catch (e: Exception) {
            session.lock()
            mutableState.value = DeletionState.Failed
            return
        }
        session.lock()
        run()
    }

    /** Tries again after [DeletionState.Incomplete] or [DeletionState.Failed]. */
    fun retry() {
        val current = mutableState.value
        if (current != DeletionState.Incomplete && current != DeletionState.Failed) return
        mutableState.value = DeletionState.Running
        session.lock()
        run()
    }

    /** The person has read that everything was deleted; the app goes back to its first-run state. */
    fun finish() {
        if (mutableState.value == DeletionState.Done) mutableState.value = DeletionState.Idle
    }

    private fun run() {
        scope.launch {
            val next = try {
                when (destroyer.destroy()) {
                    VaultDestroyResult.Destroyed -> {
                        onboarding.reset()
                        marker.clear()
                        DeletionState.Done
                    }
                    is VaultDestroyResult.Incomplete -> DeletionState.Incomplete
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DeletionState.Failed
            }
            mutableState.value = next
        }
    }
}
