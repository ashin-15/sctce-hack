package org.sakshi.app.observation

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.sakshi.acquisition.accessibility.AccessibleCapture
import org.sakshi.acquisition.notifications.NotificationObservation
import org.sakshi.acquisition.notifications.ObservedTextStatus
import org.sakshi.app.MainActivity
import org.sakshi.app.R
import org.sakshi.app.session.SessionController
import org.sakshi.app.session.SessionState
import org.sakshi.processing.analysis.RulesEngineFactory

/** Application lifetime coordination. No vault or evidence text is retained here. */
class ObservationLifecycle(private val context: Context, session: SessionController) {
    private val notifications = NotificationObservation.from(context)
    private val capture = AccessibleCapture.from(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val rules = RulesEngineFactory.default()
    private var lastAlertElapsedMs = 0L
    private val alerted = linkedSetOf<String>()
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                clearDrafts()
                session.lock()
            }
        }
    }

    init {
        ContextCompat.registerReceiver(context, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
        manager?.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.capture_alert_channel), NotificationManager.IMPORTANCE_DEFAULT))
        scope.launch {
            session.state.collectLatest { state ->
                if (state is SessionState.Unlocked) notifications.beginSession()
                else {
                    if (!notifications.settingsState.value.backgroundObservationOptIn) notifications.stopAndClear()
                    if (!capture.status.value.active) capture.stopAndClear()
                }
            }
        }
        scope.launch {
            notifications.settingsState.collectLatest { settings ->
                if (!settings.cueAlertsOptIn) manager?.cancel(ALERT_ID)
            }
        }
        scope.launch {
            notifications.inbox.candidates.collectLatest { candidates ->
                val live = candidates.mapTo(mutableSetOf()) { it.id }
                alerted.retainAll(live)
                if (candidates.isEmpty()) manager?.cancel(ALERT_ID)
                if (!notifications.settingsState.value.cueAlertsOptIn) return@collectLatest
                for (candidate in candidates) {
                    if (candidate.id in alerted || candidate.message.textStatus == ObservedTextStatus.SUMMARY_ONLY) continue
                    if (rules.analyse(candidate.message.text).matches.isEmpty()) continue
                    alerted += candidate.id
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (lastAlertElapsedMs != 0L && now - lastAlertElapsedMs < 30_000L) continue
                    if (canNotify()) {
                        manager?.notify(ALERT_ID, neutralAlert())
                        lastAlertElapsedMs = now
                    }
                }
            }
        }
    }

    fun onForeground() {
        capture.stopSession()
        notifications.refreshAccess()
    }

    fun clearDrafts() {
        notifications.stopAndClear()
        capture.stopAndClear()
        manager?.cancel(ALERT_ID)
        alerted.clear()
    }

    private fun canNotify(): Boolean = manager?.areNotificationsEnabled() == true &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    private fun neutralAlert(): Notification {
        val intent = Intent(context, MainActivity::class.java).putExtra("open_observation", true)
        val pending = PendingIntent.getActivity(context, ALERT_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_sakshi_logo)
            .setContentTitle(context.getString(R.string.capture_alert_title))
            .setContentText(context.getString(R.string.capture_alert_body))
            .setVisibility(Notification.VISIBILITY_SECRET)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
    }

    private companion object {
        const val CHANNEL = "local_review_reminders"
        const val ALERT_ID = 2401
    }
}
