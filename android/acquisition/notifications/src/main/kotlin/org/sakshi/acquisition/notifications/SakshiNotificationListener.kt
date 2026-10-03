package org.sakshi.acquisition.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * The NotificationListenerService that Android binds when notification access is granted. Declared
 * disabled in the manifest; [NotificationObservation.enable] flips the component on when the person
 * makes an affirmative choice in the app.
 *
 * This is a skeleton for phase 12. The service receives callbacks but does not process them yet.
 * It will be wired to [NotificationNormalizer], [ObservationDiffer] and [CandidateInbox] once the
 * app screens for the notification lane exist.
 */
public class SakshiNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Phase 12 stub: will forward to the normaliser and differ pipeline.
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        // Phase 12 stub: will record removal lifecycle for candidates.
    }

    override fun onListenerConnected() {
        // Phase 12 stub: will seed de-duplication state from active notifications
        // if settings.includeActiveOnConnect is true.
    }

    override fun onListenerDisconnected() {
        // Phase 12 stub: will transition coverage to COVERAGE_UNKNOWN.
    }
}
