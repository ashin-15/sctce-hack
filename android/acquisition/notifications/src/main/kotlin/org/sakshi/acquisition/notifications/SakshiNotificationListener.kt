package org.sakshi.acquisition.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * The system-bound listener. It is declared disabled in the manifest and is switched on only by
 * [NotificationObservation.enable] after the person's affirmative choice. It is read-only and thin: each callback hands
 * the notification to the intake, which checks consent and the package allowlist before any extras are read, takes a
 * bounded snapshot and does a non-blocking send. No disk, crypto or analysis happens here.
 */
public class SakshiNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val observation = NotificationObservation.from(applicationContext)
        observation.intake.onPosted(sbn.toSource())
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        val observation = NotificationObservation.from(applicationContext)
        observation.intake.onRemoved(sbn.toSource(), reason)
    }

    override fun onListenerConnected() {
        val observation = NotificationObservation.from(applicationContext)
        observation.onListenerConnected(::readActiveSources)
    }

    /** A denied or unavailable platform snapshot is unknown, never an invented empty successful batch. */
    private fun readActiveSources(): List<NotificationSource>? = try {
        activeNotifications?.map { it.toSource() }
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalStateException) {
        null
    }

    override fun onListenerDisconnected() {
        NotificationObservation.from(applicationContext).onListenerDisconnected()
    }

    override fun onDestroy() {
        NotificationObservation.from(applicationContext).onListenerDisconnected()
        super.onDestroy()
    }

    private fun StatusBarNotification.toSource(): NotificationSource =
        PlatformNotificationSource(packageName, key, groupKey, postTime, notification)
}
