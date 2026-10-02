package org.sakshi.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import org.sakshi.app.cases.CaseListScreen
import org.sakshi.app.cases.CaseListViewModel
import org.sakshi.app.lock.BiometricGate
import org.sakshi.app.lock.LockScreen
import org.sakshi.app.onboarding.OnboardingScreen
import org.sakshi.app.session.SessionState
import org.sakshi.app.ui.SakshiTheme

/** Hosts the screens. FragmentActivity because BiometricPrompt needs one. */
class MainActivity : FragmentActivity() {
    private val container: AppContainer get() = (application as SakshiApplication).container

    private var unlockNotCompleted by mutableStateOf(false)
    private lateinit var gate: BiometricGate

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        setContent {
            SakshiTheme {
                Surface { Host() }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        container.session.refresh()
    }

    override fun onStop() {
        // onStop also runs on rotation; the session must survive that.
        if (!isChangingConfigurations) container.session.lock()
        super.onStop()
    }

    @Composable
    private fun Host() {
        val session by container.session.state.collectAsState()
        var acknowledged by remember { mutableStateOf(container.onboarding.isAcknowledged()) }
        // A view model holds case titles, so none may outlive the unlocked session.
        LaunchedEffect(session is SessionState.Unlocked) {
            if (session !is SessionState.Unlocked) viewModelStore.clear()
        }
        when (val screen = screenFor(acknowledged, session)) {
            Screen.Onboarding -> OnboardingScreen(
                onAcknowledge = {
                    container.onboarding.acknowledge()
                    acknowledged = true
                },
                modifier = Modifier.safeDrawingPadding(),
            )
            is Screen.Lock -> LockScreen(
                state = screen.state,
                notCompleted = unlockNotCompleted,
                onUnlock = {
                    unlockNotCompleted = false
                    gate.authenticate()
                },
                onRetry = container.session::refresh,
                modifier = Modifier.safeDrawingPadding(),
            )
            is Screen.Cases -> {
                val model = remember(screen.vault) {
                    ViewModelProvider(this@MainActivity, CaseListViewModel.factory(screen.vault.cases))[CaseListViewModel::class.java]
                }
                val state by model.uiState.collectAsState()
                CaseListScreen(
                    state = state,
                    onCreate = model::create,
                    onRename = model::rename,
                    onArchive = model::archive,
                    onUnarchive = model::unarchive,
                    onDelete = model::delete,
                    onMessageShown = model::messageShown,
                    onLock = container.session::lock,
                )
            }
        }
    }
}
