package org.sakshi.app

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import org.sakshi.app.deletion.DeletionProgressScreen
import org.sakshi.app.lock.BiometricGate
import org.sakshi.app.importing.ConsumedIntentTracker
import org.sakshi.app.lock.LockScreen
import org.sakshi.app.onboarding.OnboardingScreen
import org.sakshi.app.session.SessionState
import org.sakshi.app.ui.theme.SakshiTheme

/** Hosts the screens. FragmentActivity because BiometricPrompt needs one. */
class MainActivity : FragmentActivity() {
    private val container: AppContainer get() = (application as SakshiApplication).container

    private var unlockNotCompleted by mutableStateOf(false)
    private lateinit var gate: BiometricGate
    private val consumedShares = ConsumedIntentTracker()
    private var sessionOwner: SessionViewModelStoreOwner? = null
    private var sessionServices: SessionServices? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumedShares.lastId = savedInstanceState?.getString(STATE_CONSUMED_SHARE)
        // Keeps case titles out of screenshots, screen recordings and the recents thumbnail.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        gate = BiometricGate(
            activity = this,
            onAuthenticated = {
                unlockNotCompleted = false
                container.session.onAuthenticated()
            },
            onNotCompleted = { unlockNotCompleted = true },
        )
        acceptShare(intent, recreated = savedInstanceState != null)
        setContent {
            SakshiTheme {
                Surface { Host() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptShare(intent, recreated = false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Only the random id of the last forwarded share; nothing from the share itself.
        consumedShares.lastId?.let { outState.putString(STATE_CONSUMED_SHARE, it) }
    }

    private fun acceptShare(intent: Intent, recreated: Boolean) {
        if (consumedShares.shouldProcess(intent.getStringExtra(EXTRA_SHARE_ID), recreated)) {
            container.shareIntake.accept(intent)
        }
    }

    override fun onStart() {
        super.onStart()
        (application as SakshiApplication).observationLifecycle.onForeground()
        container.pickerGrace.onStarted()
        container.session.refresh()
    }

    override fun onStop() {
        // onStop also runs on rotation, and while a system picker the user opened is showing; the session survives both.
        if (!isChangingConfigurations && !container.pickerGrace.coverStop()) container.session.lock()
        super.onStop()
    }

    @Composable
    private fun Host() {
        val session by container.session.state.collectAsState()
        val sharePending by container.importCoordinator.hasPending.collectAsState()
        val deletion by container.deletion.state.collectAsState()
        // Read again whenever the deletion state changes, because a finished deletion resets it.
        var acknowledged by remember(deletion) { mutableStateOf(container.onboarding.isAcknowledged()) }
        val unlocked = session is SessionState.Unlocked
        // A view model holds case titles and evidence, so none may outlive the unlocked session.
        LaunchedEffect(unlocked) {
            if (!unlocked) {
                container.importCoordinator.sessionLocked()
                sessionOwner?.clear()
                sessionOwner = null
            }
        }
        LaunchedEffect(session) { container.importCoordinator.dropIfExpired() }
        when (val screen = screenFor(acknowledged, session, deletion)) {
            is Screen.Deletion -> DeletionProgressScreen(screen.state, onRetry = container.deletion::retry, onFinish = container.deletion::finish)
            Screen.Onboarding -> OnboardingScreen(
                onAcknowledge = {
                    container.onboarding.acknowledge()
                    acknowledged = true
                },
            )
            is Screen.Lock -> LockScreen(
                state = screen.state,
                notCompleted = unlockNotCompleted,
                sharePending = sharePending,
                onUnlock = {
                    unlockNotCompleted = false
                    gate.authenticate()
                },
                onRetry = container.session::refresh,
            )
            is Screen.Session -> {
                val owner = remember(screen.vault) {
                    sessionOwner?.clear()
                    SessionViewModelStoreOwner().also { sessionOwner = it }
                }
                val services = remember(screen.vault) {
                    SessionServices(screen.vault, applicationContext, container.dispatchers.io)
                }
                DisposableEffect(services) {
                    sessionServices = services
                    onDispose {
                        if (sessionServices === services) sessionServices = null
                        services.close()
                    }
                }
                SessionHost(services = services, container = container, owner = owner)
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        sessionServices?.onTrimMemory(level)
    }

    override fun onDestroy() {
        sessionOwner?.clear()
        sessionOwner = null
        super.onDestroy()
    }

    private class SessionViewModelStoreOwner : ViewModelStoreOwner {
        override val viewModelStore: ViewModelStore = ViewModelStore()
        fun clear() = viewModelStore.clear()
    }

    private companion object {
        const val STATE_CONSUMED_SHARE = "consumed_share_id"
    }
}
