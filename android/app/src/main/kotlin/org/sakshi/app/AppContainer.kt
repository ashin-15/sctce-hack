package org.sakshi.app

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.sakshi.app.onboarding.OnboardingStore
import org.sakshi.app.onboarding.SharedPreferencesOnboardingStore
import org.sakshi.app.session.AndroidDeviceSecurity
import org.sakshi.app.session.KeystoreVaultOpener
import org.sakshi.app.session.SessionController

data class AppDispatchers(val main: CoroutineDispatcher, val io: CoroutineDispatcher)

/** Manual dependency wiring. Tests build it from fakes through the primary constructor. */
class AppContainer(
    val session: SessionController,
    val onboarding: OnboardingStore,
    val dispatchers: AppDispatchers,
) {
    companion object {
        fun create(context: Context, scope: CoroutineScope = defaultScope()): AppContainer {
            val session = SessionController(KeystoreVaultOpener(context), AndroidDeviceSecurity(context), scope)
            return AppContainer(session, SharedPreferencesOnboardingStore.create(context), AppDispatchers(Dispatchers.Main, Dispatchers.IO))
        }

        private fun defaultScope() = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
}
