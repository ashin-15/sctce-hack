package org.sakshi.app

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
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
        var acknowledged by remember { mutableStateOf(container.onboarding.isAcknowledged()) }
        val unlocked = session is SessionState.Unlocked
        // A view model holds case titles and evidence, so none may outlive the unlocked session.
        LaunchedEffect(unlocked) {
            if (!unlocked) {
                container.importCoordinator.sessionLocked()
                viewModelStore.clear()
            }
        }
        LaunchedEffect(session) { container.importCoordinator.dropIfExpired() }
        when (val screen = screenFor(acknowledged, session)) {
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
            is Screen.Session -> SessionHost(
                services = remember(screen.vault) {
                    SessionServices(screen.vault, applicationContext, container.dispatchers.io)
                },
                container = container,
                owner = this@MainActivity,
            )
        }
    }

    private companion object {
        const val STATE_CONSUMED_SHARE = "consumed_share_id"
    }
}
