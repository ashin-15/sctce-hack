package org.sakshi.app.deletion

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.sakshi.app.onboarding.OnboardingStore
import org.sakshi.app.session.DeletionController
import org.sakshi.app.session.DeletionMarker
import org.sakshi.app.session.DeletionState
import org.sakshi.app.session.DeviceSecurity
import org.sakshi.app.session.SessionController
import org.sakshi.app.session.SessionState
import org.sakshi.app.session.SharedPreferencesDeletionMarker
import org.sakshi.app.session.VaultDestroyer
import org.sakshi.app.session.VaultOpener
import org.sakshi.app.support.VaultTestBase
import org.sakshi.core.crypto.SoftwareKeyWrapper
import org.sakshi.core.vault.Vault
import org.sakshi.core.vault.VaultDestroyResult

private class FakeOnboarding(var acknowledged: Boolean = true) : OnboardingStore {
    override fun isAcknowledged(): Boolean = acknowledged

    override fun acknowledge() {
        acknowledged = true
    }

    override fun reset() {
        acknowledged = false
    }
}

private class FakeMarker(var pending: Boolean = false) : DeletionMarker {
    override fun isPending(): Boolean = pending

    override fun setPending() {
        pending = true
    }

    override fun clear() {
        pending = false
    }
}

/** Answers with the next scripted outcome (the last one repeats); an outcome may wait on a gate to hold the deletion half way. */
private class ScriptedDestroyer(private vararg val outcomes: suspend () -> VaultDestroyResult) : VaultDestroyer {
    var calls = 0
        private set

    override suspend fun destroy(): VaultDestroyResult = outcomes[minOf(calls++, outcomes.size - 1)]()
}

private val SECURE_DEVICE = object : DeviceSecurity {
    override fun isDeviceSecure(): Boolean = true
}

private val neverOpens = object : VaultOpener {
    override suspend fun open(): Vault = error("unused")
}

private val INCOMPLETE = VaultDestroyResult.Incomplete(listOf("synthetic-leftover-file"), keyStoreKeyRemains = false)

class DeletionControllerTest : VaultTestBase() {
    private val appScope = CoroutineScope(Job() + Dispatchers.Unconfined)
    private val onboarding = FakeOnboarding()
    private val marker = FakeMarker()

    @AfterTest
    fun stopScope() = appScope.coroutineContext[Job]!!.cancel()

    private fun unlockedSession(): SessionController {
        val opener = object : VaultOpener {
            override suspend fun open(): Vault = vault
        }
        val session = SessionController(opener, SECURE_DEVICE, appScope)
        session.refresh()
        session.onAuthenticated()
        assertIs<SessionState.Unlocked>(session.state.value)
        return session
    }

    private fun controller(session: SessionController, destroyer: VaultDestroyer, marker: DeletionMarker = this.marker) =
        DeletionController(session, onboarding, marker, destroyer, appScope)

    private fun isClosed(): Boolean = runCatching { runBlocking { vault.cases.create("synthetic probe") } }.isFailure

    @Test
    fun successReleasesEverythingEndsTheSessionResetsFirstRunAndShowsDone() {
        val session = unlockedSession()
        val gate = CompletableDeferred<Unit>()
        val destroyer = ScriptedDestroyer({ gate.await(); VaultDestroyResult.Destroyed })
        val controller = controller(session, destroyer)
        val order = mutableListOf<String>()

        controller.start { order += "release:closed=${isClosed()}" }

        assertEquals(DeletionState.Running, controller.state.value)
        assertEquals(listOf("release:closed=false"), order, "the release step runs while everything is still open")
        assertEquals(SessionState.Locked, session.state.value)
        assertTrue(isClosed(), "the session no longer holds the data")
        assertTrue(marker.pending, "an unfinished deletion is remembered")
        assertTrue(onboarding.acknowledged, "first-run state is reset only once the deletion succeeded")

        gate.complete(Unit)

        assertEquals(DeletionState.Done, controller.state.value)
        assertFalse(onboarding.acknowledged)
        assertFalse(marker.pending)
        assertEquals(1, destroyer.calls)
        controller.finish()
        assertEquals(DeletionState.Idle, controller.state.value)
    }

    @Test
    fun anIncompleteDeletionStaysOutOfTheSessionAndOffersRetryUntilItSucceeds() {
        val session = unlockedSession()
        val destroyer = ScriptedDestroyer({ INCOMPLETE }, { INCOMPLETE }, { VaultDestroyResult.Destroyed })
        val controller = controller(session, destroyer)

        controller.start { }
        assertEquals(DeletionState.Incomplete, controller.state.value)
        assertEquals(SessionState.Locked, session.state.value)
        assertTrue(onboarding.acknowledged)
        assertTrue(marker.pending)

        controller.retry()
        assertEquals(DeletionState.Incomplete, controller.state.value)
        controller.retry()
        assertEquals(DeletionState.Done, controller.state.value)
        assertEquals(3, destroyer.calls)
        assertFalse(onboarding.acknowledged)
        assertFalse(marker.pending)
    }

    @Test
    fun aRemainingKeyIsTheSameIncompleteOutcome() {
        val controller = controller(unlockedSession(), ScriptedDestroyer({ VaultDestroyResult.Incomplete(emptyList(), keyStoreKeyRemains = true) }))
        controller.start { }
        assertEquals(DeletionState.Incomplete, controller.state.value)
    }

    @Test
    fun anExceptionIsAFailureWithRetryAndNeverSuccess() {
        val destroyer = ScriptedDestroyer({ throw IOException("synthetic failure") }, { VaultDestroyResult.Destroyed })
        val controller = controller(unlockedSession(), destroyer)

        controller.start { }
        assertEquals(DeletionState.Failed, controller.state.value)
        assertTrue(onboarding.acknowledged)
        assertTrue(marker.pending)

        controller.retry()
        assertEquals(DeletionState.Done, controller.state.value)
    }

    @Test
    fun aReleaseStepThatFailsStopsBeforeAnythingIsDestroyed() {
        val session = unlockedSession()
        val destroyer = ScriptedDestroyer({ VaultDestroyResult.Destroyed })
        val controller = controller(session, destroyer)

        controller.start { throw IllegalStateException("synthetic failure") }

        assertEquals(DeletionState.Failed, controller.state.value)
        assertEquals(0, destroyer.calls)
        assertEquals(SessionState.Locked, session.state.value)
        controller.retry()
        assertEquals(DeletionState.Done, controller.state.value)
    }

    @Test
    fun startAndRetryAreIgnoredOutsideTheirStates() {
        val gate = CompletableDeferred<Unit>()
        val destroyer = ScriptedDestroyer({ gate.await(); VaultDestroyResult.Destroyed })
        val controller = controller(unlockedSession(), destroyer)

        controller.retry()
        controller.finish()
        assertEquals(DeletionState.Idle, controller.state.value)
        controller.start { }
        controller.start { error("a second start must not run") }
        controller.retry()
        assertEquals(1, destroyer.calls)
        gate.complete(Unit)
        controller.start { error("nothing starts while the outcome is showing") }
        assertEquals(DeletionState.Done, controller.state.value)
    }

    @Test
    fun anAppClosedHalfWayOffersToFinishOnTheNextLaunch() {
        val pending = FakeMarker(pending = true)
        val destroyer = ScriptedDestroyer({ VaultDestroyResult.Destroyed })
        val controller = controller(SessionController(neverOpens, SECURE_DEVICE, appScope), destroyer, pending)

        assertEquals(DeletionState.Incomplete, controller.state.value)
        controller.retry()
        assertEquals(DeletionState.Done, controller.state.value)
        assertFalse(pending.pending)
    }

    @Test
    fun theRealDestroyRemovesTheSavedFilesAndAndLeavesNoVaultDirectory() {
        val caseId = newCase("synthetic case")
        importText(caseId)
        val root = File(context.noBackupFilesDir, "vault")
        assertTrue(root.exists())
        val appContext: Context = ApplicationProvider.getApplicationContext()
        val destroyer = VaultDestroyer { Vault.destroy(appContext, SoftwareKeyWrapper(ByteArray(32) { it.toByte() })) }
        val controller = controller(unlockedSession(), destroyer)

        controller.start { }

        assertEquals(DeletionState.Done, controller.state.value)
        assertFalse(root.exists())
    }

    @Test
    fun theMarkerSurvivesARestartUntilCleared() {
        val first = SharedPreferencesDeletionMarker.create(context)
        assertFalse(first.isPending())
        first.setPending()
        assertTrue(SharedPreferencesDeletionMarker.create(context).isPending())
        first.clear()
        assertFalse(SharedPreferencesDeletionMarker.create(context).isPending())
    }

    private fun importText(caseId: String) {
        val uri = serve("synthetic.txt", "synthetic text".toByteArray())
        val batch = org.sakshi.acquisition.importer.PendingBatch(
            org.sakshi.acquisition.importer.ImportMechanism.DOCUMENT_PICKER,
            listOf(streamItem(0, uri, "text/plain")),
            referrerClaim = null,
        )
        runBlocking { importer.commit(caseId, batch, setOf(0)) }
    }
}
