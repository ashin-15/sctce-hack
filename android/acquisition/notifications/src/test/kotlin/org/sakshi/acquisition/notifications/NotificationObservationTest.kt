package org.sakshi.acquisition.notifications

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NotificationObservationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val component = ComponentName(context, SakshiNotificationListener::class.java)
    private val scheduler = TestCoroutineScheduler()
    private val environment = FakeEnvironment()
    private val settingsFile = File(createTempDirectory("sakshi-observation").toFile(), "settings.properties")

    private fun observation(env: IntakeEnvironment = environment): NotificationObservation =
        NotificationObservation(context, NotificationSettings(settingsFile), env, MutableClock(), StandardTestDispatcher(scheduler))

    private fun grantAccess(granted: Boolean) {
        shadowOf(context.getSystemService(NotificationManager::class.java)).setNotificationListenerAccessGranted(component, granted)
    }

    private fun componentState(): Int = context.packageManager.getComponentEnabledSetting(component)

    private fun post(observation: NotificationObservation, text: String, key: String = "synthetic-key-1") {
        val notification = SyntheticNotifications.messaging(context, listOf(SyntheticMessage(text, T0, "Synthetic Sender One")))
        observation.intake.onPosted(SyntheticNotifications.source(notification, key))
        scheduler.runCurrent()
    }

    @Test
    fun `the lane is off by default and observes nothing`() {
        val observation = observation()
        assertFalse(observation.settingsState.value.enabled)
        assertTrue(observation.settingsState.value.allowlist.isEmpty)
        assertEquals(CoverageState.UNAVAILABLE, observation.coverage.value)
        assertTrue(componentState() != PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
        post(observation, "synthetic text")
        assertTrue(observation.inbox.candidates.value.isEmpty())
    }

    @Test
    fun `the access intent is the system notification listener settings screen`() {
        assertEquals(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, observation().accessSettingsIntent().action)
    }

    @Test
    fun `access granted and connected are separate coverage facts`() {
        val observation = observation()
        observation.enable()
        assertEquals(NotificationAvailability.AVAILABLE, observation.availability())
        assertFalse(observation.isAccessGranted())
        assertEquals(CoverageState.UNAVAILABLE, observation.coverage.value)
        assertEquals(UnavailableReason.ACCESS_NOT_GRANTED, observation.coverageDetail.value.unavailableReason)
        grantAccess(true)
        observation.beginSession()
        assertTrue(observation.isAccessGranted())
        assertEquals(CoverageState.ACCESS_GRANTED_NOT_CONNECTED, observation.coverage.value)
        observation.onListenerConnected()
        assertEquals(CoverageState.COVERAGE_UNKNOWN, observation.coverage.value)
        observation.intake.onActiveSnapshot(emptyList())
        scheduler.runCurrent()
        assertEquals(CoverageState.CONNECTED, observation.coverage.value)
    }

    @Test
    fun `enable switches the component on and disable switches it off and clears`() {
        val observation = observation()
        grantAccess(true)
        observation.setAllowlist(PackageAllowlist.of(listOf(APP)))
        observation.enable()
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, componentState())
        observation.onListenerConnected()
        observation.intake.onActiveSnapshot(emptyList())
        post(observation, "synthetic text")
        assertEquals(1, observation.inbox.candidates.value.size)
        observation.disable()
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, componentState())
        assertTrue(observation.inbox.candidates.value.isEmpty())
        assertEquals(CoverageState.UNAVAILABLE, observation.coverage.value)
        assertFalse(NotificationSettings(settingsFile).state.value.enabled)
    }

    @Test
    fun `pause and resume are reflected in coverage and nothing is replayed`() {
        val observation = observation()
        grantAccess(true)
        observation.setAllowlist(PackageAllowlist.of(listOf(APP)))
        observation.enable()
        observation.onListenerConnected()
        observation.intake.onActiveSnapshot(emptyList())
        scheduler.runCurrent()
        observation.pause()
        assertEquals(CoverageState.PAUSED, observation.coverage.value)
        post(observation, "synthetic while paused")
        assertTrue(observation.inbox.candidates.value.isEmpty())
        observation.resume()
        assertEquals(CoverageState.CONNECTED, observation.coverage.value)
        assertTrue(observation.inbox.candidates.value.isEmpty())
        post(observation, "synthetic after resume")
        assertEquals(listOf("synthetic after resume"), observation.inbox.candidates.value.map { it.message.text })
    }

    @Test
    fun `stop and clear leaves nothing reachable and a new session starts empty`() {
        val observation = observation()
        grantAccess(true)
        observation.setAllowlist(PackageAllowlist.of(listOf(APP)))
        observation.enable()
        observation.onListenerConnected()
        post(observation, "synthetic text")
        assertEquals(1, observation.inbox.candidates.value.size)
        observation.stopAndClear()
        assertTrue(observation.inbox.candidates.value.isEmpty())
        assertEquals(CoverageState.UNAVAILABLE, observation.coverage.value)
        assertEquals(UnavailableReason.SESSION_STOPPED, observation.coverageDetail.value.unavailableReason)
        post(observation, "synthetic after stop")
        assertTrue(observation.inbox.candidates.value.isEmpty())
        observation.beginSession()
        post(observation, "synthetic new session")
        assertEquals(1, observation.inbox.candidates.value.size)
    }

    @Test
    fun `revoking access in system settings stops capture and clears`() {
        val observation = observation()
        grantAccess(true)
        observation.setAllowlist(PackageAllowlist.of(listOf(APP)))
        observation.enable()
        observation.onListenerConnected()
        post(observation, "synthetic text")
        grantAccess(false)
        observation.onListenerDisconnected()
        assertTrue(observation.inbox.candidates.value.isEmpty())
        assertEquals(CoverageState.UNAVAILABLE, observation.coverage.value)
        post(observation, "synthetic after revoke")
        assertTrue(observation.inbox.candidates.value.isEmpty())
    }

    @Test
    fun `a process that starts with the lane already on reports coverage unknown until reconciled`() {
        NotificationSettings(settingsFile).update { it.copy(enabled = true, allowlist = PackageAllowlist.of(listOf(APP))) }
        grantAccess(true)
        val observation = observation()
        assertEquals(CoverageState.COVERAGE_UNKNOWN, observation.coverage.value)
        assertTrue(observation.coverageDetail.value.gaps.any { it.reason == CoverageGapReason.PROCESS_RESTART && it.startAt == null })
        observation.onListenerConnected()
        observation.intake.onActiveSnapshot(emptyList())
        scheduler.runCurrent()
        assertEquals(CoverageState.CONNECTED, observation.coverage.value)
    }

    @Test
    @Config(sdk = [29])
    fun `below Android 11 the lane is unavailable and every entry point does nothing`() {
        val observation = observation(PlatformEnvironment(context))
        assertEquals(NotificationAvailability.UNAVAILABLE_ANDROID_VERSION, observation.availability())
        assertEquals(NotificationAvailability.UNAVAILABLE_ANDROID_VERSION, observation.enable())
        assertFalse(observation.isAccessGranted())
        assertFalse(observation.settingsState.value.enabled)
        assertTrue(componentState() != PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
        observation.pause()
        observation.resume()
        observation.beginSession()
        observation.onListenerConnected()
        grantAccess(true)
        post(observation, "synthetic text")
        assertTrue(observation.inbox.candidates.value.isEmpty())
        assertEquals(CoverageState.UNAVAILABLE, observation.coverage.value)
        assertEquals(UnavailableReason.ANDROID_VERSION, observation.coverageDetail.value.unavailableReason)
    }

    @Test
    @Config(sdk = [30])
    fun `Android 11 is the first available version`() {
        assertEquals(NotificationAvailability.AVAILABLE, observation(PlatformEnvironment(context)).availability())
    }
}
