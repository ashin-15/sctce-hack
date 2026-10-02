package org.sakshi.app

import org.sakshi.app.session.SessionState
import org.sakshi.core.vault.Vault

/** What the activity shows. Derived from state, never stored. */
sealed interface Screen {
    data object Onboarding : Screen

    data class Lock(val state: SessionState) : Screen

    class Cases(val vault: Vault) : Screen
}

fun screenFor(acknowledged: Boolean, session: SessionState): Screen = when {
    !acknowledged -> Screen.Onboarding
    session is SessionState.Unlocked -> Screen.Cases(session.vault)
    else -> Screen.Lock(session)
}
