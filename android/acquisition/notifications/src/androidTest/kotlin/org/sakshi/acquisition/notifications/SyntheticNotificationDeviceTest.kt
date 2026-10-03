package org.sakshi.acquisition.notifications

import android.Manifest
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Person
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Device test with a synthetic producer: the test posts its own notifications from its own package and asserts that the
 * listener observes them. It needs one manual precondition that no test can grant on its own: notification access for
 * the listener. Without it the test is skipped with a message that says how to grant it. All text is synthetic.
 */
class SyntheticNotificationDeviceTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager: NotificationManager = checkNotNull(context.getSystemService(NotificationManager::class.java))
    private val observation = NotificationObservation.from(context)

    @Before
    fun setUp() {
        assumeTrue("Android 11 (API 30) or newer is required", Build.VERSION.SDK_INT >= NotificationBounds.MIN_API)
        assumeTrue(
            "The device must be unlocked: the default policy keeps no text while locked",
            !checkNotNull(context.getSystemService(KeyguardManager::class.java)).isDeviceLocked,
        )
        assumeTrue(
            "Grant POST_NOTIFICATIONS first: adb shell pm grant ${context.packageName} ${Manifest.permission.POST_NOTIFICATIONS}",
            Build.VERSION.SDK_INT < 33 ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        )
        observation.setAllowlist(PackageAllowlist.of(listOf(context.packageName)))
        observation.enable()
        assumeTrue(
            "Notification access is not granted. Grant it once, then rerun: " +
                "adb shell cmd notification allow_listener " +
                "${context.packageName}/${SakshiNotificationListener::class.java.name}",
            observation.isAccessGranted(),
        )
    }

    @After
    fun tearDown() {
        manager.cancelAll()
        observation.disable()
    }

    @Test
    fun the_listener_observes_a_synthetic_messaging_notification_from_the_test_package() {
        val connected = runBlocking {
            withTimeoutOrNull(CONNECT_TIMEOUT_MS) { observation.coverage.first { it == CoverageState.CONNECTED } }
        }
        assertNotNull(connected, "The listener did not connect and reconcile within $CONNECT_TIMEOUT_MS ms")

        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Synthetic", NotificationManager.IMPORTANCE_LOW))
        val sender = Person.Builder().setName("Synthetic Sender One").build()
        val style = Notification.MessagingStyle(Person.Builder().setName("Synthetic Self").build())
        style.addMessage("synthetic device test message", System.currentTimeMillis(), sender)
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setStyle(style)
            .build()
        manager.notify(NOTIFICATION_ID, notification)

        val observed = runBlocking {
            withTimeoutOrNull(OBSERVE_TIMEOUT_MS) { observation.inbox.candidates.first { it.isNotEmpty() } }
        }
        assertNotNull(observed, "The listener did not observe the synthetic notification within $OBSERVE_TIMEOUT_MS ms")
        val candidate = observed.first().message
        assertEquals("synthetic device test message", candidate.text)
        assertEquals("Synthetic Sender One", candidate.senderLabel)
        assertEquals(context.packageName, candidate.sourceAppClaim)
        assertEquals(SnapshotOrigin.LIVE, candidate.origin)
    }

    private companion object {
        const val CHANNEL = "synthetic-device-test"
        const val NOTIFICATION_ID = 4201
        const val CONNECT_TIMEOUT_MS = 20_000L
        const val OBSERVE_TIMEOUT_MS = 20_000L
    }
}
