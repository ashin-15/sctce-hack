package org.sakshi.app.session

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.IOException
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.sakshi.core.crypto.SoftwareKeyWrapper
import org.sakshi.core.vault.Vault
import org.sakshi.core.vault.VaultKeyException

private class FakeDeviceSecurity(var secure: Boolean = true) : DeviceSecurity {
    override fun isDeviceSecure(): Boolean = secure
}

private class FakeOpener(private val behaviour: suspend () -> Vault) : VaultOpener {
    var calls = 0
        private set

    override suspend fun open(): Vault {
        calls++
        return behaviour()
    }
}

/** The controller runs on [Dispatchers.Unconfined], so each launch proceeds inline until it suspends. */
@RunWith(RobolectricTestRunner::class)
class SessionControllerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val job = Job()
    private val security = FakeDeviceSecurity()
    private val opened = mutableListOf<Vault>()
    private var nextId = 0

    @AfterTest
    fun cleanUp() {
        job.cancel()
        opened.forEach { runCatching { it.close() } }
    }

    private fun newVault(): Vault = Vault.openForTests(
        context,
        SoftwareKeyWrapper(ByteArray(32) { it.toByte() }),
        { Instant.parse("2026-10-02T10:00:00Z") },
        { "id-${nextId++}" },
    ).also(opened::add)

    private fun controller(opener: VaultOpener) =
        SessionController(opener, security, CoroutineScope(job + Dispatchers.Unconfined))

    private fun isClosed(vault: Vault): Boolean = runCatching { runBlocking { vault.cases.create("synthetic probe") } }.isFailure

    @Test
    fun refreshReportsMissingDeviceLock() {
        val controller = controller(FakeOpener { error("unused") })
        security.secure = false
        controller.refresh()
        assertEquals(SessionState.NoDeviceLock, controller.state.value)
        security.secure = true
        controller.refresh()
        assertEquals(SessionState.Locked, controller.state.value)
    }

    @Test
    fun successfulAuthenticationUnlocks() {
        val vault = newVault()
        val gate = CompletableDeferred<Unit>()
        val controller = controller(FakeOpener { gate.await(); vault })
        controller.refresh()
        controller.onAuthenticated()
        assertEquals(SessionState.Unlocking, controller.state.value)
        gate.complete(Unit)
        assertEquals(vault, assertIs<SessionState.Unlocked>(controller.state.value).vault)
    }

    @Test
    fun exceptionsMapToStates() {
        val expectations = listOf<Pair<Exception, SessionState>>(
            VaultKeyException.NotAuthenticated(null) to SessionState.Locked,
            VaultKeyException.Invalidated(null) to SessionState.KeyInvalidated,
            VaultKeyException.Unavailable(null) to SessionState.Failed(FailureReason.KEY_UNAVAILABLE),
            IOException("synthetic") to SessionState.Failed(FailureReason.STORAGE_ERROR),
            IllegalStateException("synthetic") to SessionState.Failed(FailureReason.STORAGE_ERROR),
        )
        expectations.forEach { (failure, expected) ->
            val controller = controller(FakeOpener { throw failure })
            controller.onAuthenticated()
            assertEquals(expected, controller.state.value, failure.toString())
        }
    }

    @Test
    fun lockClosesTheVaultAndIsIdempotent() {
        val vault = newVault()
        val controller = controller(FakeOpener { vault })
        controller.onAuthenticated()
        assertIs<SessionState.Unlocked>(controller.state.value)
        controller.lock()
        assertEquals(SessionState.Locked, controller.state.value)
        assertTrue(isClosed(vault))
        controller.lock()
        assertEquals(SessionState.Locked, controller.state.value)
    }

    @Test
    fun concurrentAuthenticationsOpenOneVault() {
        val gate = CompletableDeferred<Unit>()
        val vault = newVault()
        val opener = FakeOpener { gate.await(); vault }
        val controller = controller(opener)
        controller.onAuthenticated()
        controller.onAuthenticated()
        gate.complete(Unit)
        controller.onAuthenticated()
        assertEquals(1, opener.calls)
        assertIs<SessionState.Unlocked>(controller.state.value)
    }

    @Test
    fun lockWhileUnlockingClosesTheVaultThatFinishesOpening() {
        val vault = newVault()
        val gate = CompletableDeferred<Unit>()
        val controller = controller(FakeOpener { gate.await(); vault })
        controller.onAuthenticated()
        assertEquals(SessionState.Unlocking, controller.state.value)
        controller.lock()
        assertEquals(SessionState.Locked, controller.state.value)
        gate.complete(Unit)
        assertEquals(SessionState.Locked, controller.state.value)
        assertTrue(isClosed(vault))
    }

    @Test
    fun authenticatingAfterLockDuringOpenWaitsForTheEarlierOpen() {
        val first = newVault()
        val second = newVault()
        val gate = CompletableDeferred<Unit>()
        val queue = ArrayDeque(listOf(first, second))
        val opener = FakeOpener {
            val mine = queue.removeFirst()
            if (mine === first) gate.await()
            mine
        }
        val controller = controller(opener)
        controller.onAuthenticated()
        controller.lock()
        controller.onAuthenticated()
        assertEquals(1, opener.calls)
        gate.complete(Unit)
        assertEquals(2, opener.calls)
        assertTrue(isClosed(first))
        assertEquals(second, assertIs<SessionState.Unlocked>(controller.state.value).vault)
    }

    @Test
    fun failedOpenAfterLockDoesNotOverrideLocked() {
        val gate = CompletableDeferred<Unit>()
        val controller = controller(FakeOpener { gate.await(); throw IOException("synthetic") })
        controller.onAuthenticated()
        controller.lock()
        gate.complete(Unit)
        assertEquals(SessionState.Locked, controller.state.value)
    }
}
