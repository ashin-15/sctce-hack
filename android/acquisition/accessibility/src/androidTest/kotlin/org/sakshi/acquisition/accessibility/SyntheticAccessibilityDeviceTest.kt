package org.sakshi.acquisition.accessibility

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** Actual Android event/window capture against an isolated synthetic fixture, not third-party app validation. */
class SyntheticAccessibilityDeviceTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val capture = AccessibleCapture.from(context)
    private var scenario: ActivityScenario<SyntheticVisibleTextActivity>? = null

    @Before fun setUp() {
        assumeTrue("Fixture must be running in its isolated test APK", context.packageName == FIXTURE_PACKAGE)
        assumeTrue("Device must be unlocked", context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == false)
        capture.enable()
        val granted = runBlocking { withTimeoutOrNull(3_000L) {
            while (!capture.isAccessGranted()) kotlinx.coroutines.delay(100)
            true
        } }
        assumeTrue(
            "Grant Android Accessibility access to $FIXTURE_PACKAGE/${SakshiVisibleTextService::class.java.name} in the test harness first. " +
                "Do not change grants for org.sakshi.app or other services.",
            granted == true,
        )
        val connected = runBlocking { withTimeoutOrNull(20_000L) { capture.status.first { it.connected } } }
        assertNotNull("Test Accessibility service did not connect", connected)
        capture.stopAndClear()
        assertTrue(capture.startSession(setOf(FIXTURE_PACKAGE)))
    }

    @After fun tearDown() {
        scenario?.close()
        capture.revoke()
    }

    @Test fun actualVisibleWindowCaptureExcludesEditableAndPasswordAndSupportsReviewAndClear() {
        launch("ordinary")
        val observed = runBlocking { withTimeoutOrNull(20_000L) { capture.candidates.first { it.isNotEmpty() } } }
        assertNotNull("No visible synthetic snapshot was observed", observed)
        val candidate = checkNotNull(observed).first()
        assertTrue(candidate.text.contains("SYNTHETIC_VISIBLE_TEXT"))
        assertFalse(candidate.text.contains("SYNTHETIC_EDITABLE_SECRET"))
        assertFalse(candidate.text.contains("SYNTHETIC_PASSWORD_SECRET"))
        assertEquals(FIXTURE_PACKAGE, candidate.packageName)
        assertEquals("UNKNOWN", candidate.direction)
        assertEquals("UNKNOWN", candidate.messageBoundaries)
        assertEquals(null, candidate.sender)
        assertTrue(candidate.collectorElapsedRealtimeMs > 0)
        capture.stopSession()
        assertFalse(capture.status.value.active)
        assertEquals(observed, capture.candidates.value)
        capture.stopAndClear()
        assertTrue(capture.candidates.value.isEmpty())
        capture.revoke()
        val disconnected = runBlocking { withTimeoutOrNull(20_000L) { capture.status.first { !it.connected } } }
        assertNotNull("Revoked test service did not disconnect", disconnected)
    }

    @Test fun restrictedSyntheticWindowStopsAndClearsWithoutRetainingText() {
        launch("restricted")
        val stopped = runBlocking { withTimeoutOrNull(20_000L) { capture.status.first { !it.active } } }
        assertNotNull("Restricted-content marker did not stop capture", stopped)
        assertTrue(capture.candidates.value.isEmpty())
        assertTrue(checkNotNull(stopped).notice.contains("View Once"))
    }

    @Test fun frameworkSensitiveNodeStopsAndClearsOnSupportedAndroid() {
        assumeTrue("Accessibility sensitive flags require API 34+", Build.VERSION.SDK_INT >= 34)
        launch("sensitive")
        val stopped = runBlocking { withTimeoutOrNull(20_000L) { capture.status.first { !it.active } } }
        assertNotNull("Sensitive synthetic window did not stop capture", stopped)
        assertTrue(capture.candidates.value.isEmpty())
        assertTrue(checkNotNull(stopped).notice.contains("sensitive"))
    }

    private fun launch(mode: String) {
        scenario = ActivityScenario.launch(Intent(context, SyntheticVisibleTextActivity::class.java)
            .putExtra("mode", mode).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private companion object { const val FIXTURE_PACKAGE = "org.sakshi.acquisition.accessibility.test" }
}
