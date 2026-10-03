package org.sakshi.app

import android.content.Context
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.sakshi.app.importing.ImportCoordinator
import org.sakshi.app.importing.PickerGrace
import org.sakshi.app.importing.ShareIntake
import org.sakshi.app.onboarding.OnboardingStore
import org.sakshi.app.onboarding.SharedPreferencesOnboardingStore
import org.sakshi.app.session.AndroidDeviceSecurity
import org.sakshi.app.session.DeletionController
import org.sakshi.app.session.KeystoreVaultDestroyer
import org.sakshi.app.session.KeystoreVaultOpener
import org.sakshi.app.session.SessionController
import org.sakshi.app.session.SharedPreferencesDeletionMarker

data class AppDispatchers(val main: CoroutineDispatcher, val io: CoroutineDispatcher)

/** Manual dependency wiring. Tests build it from fakes through the primary constructor. */
class AppContainer(
    val session: SessionController,
    val onboarding: OnboardingStore,
    val dispatchers: AppDispatchers,
    val importCoordinator: ImportCoordinator,
    val pickerGrace: PickerGrace,
    val shareIntake: ShareIntake,
    val deletion: DeletionController,
) {
    companion object {
        fun create(context: Context, scope: CoroutineScope = defaultScope()): AppContainer {
            val session = SessionController(KeystoreVaultOpener(context), AndroidDeviceSecurity(context), scope)
            val dispatchers = AppDispatchers(Dispatchers.Main, Dispatchers.IO)
            val coordinator = ImportCoordinator(Instant::now)
            val onboarding = SharedPreferencesOnboardingStore.create(context)
            // The application scope, so a deletion that has started is never cancelled by leaving a screen.
            val deletion = DeletionController(
                session,
                onboarding,
                SharedPreferencesDeletionMarker.create(context),
                KeystoreVaultDestroyer(context),
                scope,
            )
            return AppContainer(
                session = session,
                onboarding = onboarding,
                dispatchers = dispatchers,
                importCoordinator = coordinator,
                pickerGrace = PickerGrace(scope, session::lock),
                shareIntake = ShareIntake(coordinator, context.contentResolver, scope, dispatchers.io),
                deletion = deletion,
            )
        }

        private fun defaultScope() = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
}
