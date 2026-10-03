package org.sakshi.acquisition.notifications

import android.app.Notification
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import androidx.core.os.BundleCompat

/**
 * Reads the bounded snapshot from a platform [Notification]. This is the only class that touches notification extras,
 * and only text fields are read: no icon, picture, action, intent, content URI or remote view is ever opened.
 * Messages come from the public MessagingStyle parser (API 30), and the count is cut before parsing.
 *
 * The constructor takes plain platform fields so that the service can pass them from a status bar notification and
 * tests can pass them from a notification they built.
 */
public class PlatformNotificationSource(
    override val packageName: String,
    override val notificationKey: String,
    override val groupKey: String?,
    override val postTimeMs: Long,
    private val notification: Notification,
) : NotificationSource {

    override fun read(request: SnapshotRequest): NotificationSnapshot {
        val budget = TextBudget()
        val truncated = LinkedHashSet<SnapshotField>()
        val extras: Bundle? = try {
            notification.extras
        } catch (ignored: RuntimeException) {
            null
        }
        val visibility = visibilityOf(notification.visibility)
        // A secret notification is never read for text (report section 3.2).
        val readable: Bundle? = extras?.takeIf { visibility != NotificationVisibility.SECRET }

        fun field(key: String, field: SnapshotField): String? {
            if (readable == null) return null
            val clipped = budget.clip(safely { readable.getCharSequence(key) }) ?: return null
            if (clipped.truncated) truncated += field
            return clipped.value.ifEmpty { null }
        }

        val title = field(Notification.EXTRA_TITLE, SnapshotField.TITLE)
        val text = field(Notification.EXTRA_TEXT, SnapshotField.TEXT)
        val bigText = field(Notification.EXTRA_BIG_TEXT, SnapshotField.BIG_TEXT)
        val subText = field(Notification.EXTRA_SUB_TEXT, SnapshotField.SUB_TEXT)
        val conversationTitle = field(Notification.EXTRA_CONVERSATION_TITLE, SnapshotField.CONVERSATION_TITLE)

        val current = if (readable != null) {
            readMessages(readable, Notification.EXTRA_MESSAGES, NotificationBounds.MAX_MESSAGES, budget)
        } else {
            emptyList<SnapshotMessage>() to false
        }
        val historic = if (readable != null) {
            readMessages(readable, Notification.EXTRA_HISTORIC_MESSAGES, NotificationBounds.MAX_HISTORIC_MESSAGES, budget)
        } else {
            emptyList<SnapshotMessage>() to false
        }
        if (current.second || historic.second) truncated += SnapshotField.MESSAGES_OMITTED

        val isGroup = if (
            Build.VERSION.SDK_INT >= NotificationBounds.MIN_API &&
            readable != null &&
            readable.containsKey(Notification.EXTRA_IS_GROUP_CONVERSATION)
        ) {
            readable.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION)
        } else {
            null
        }
        return NotificationSnapshot(
            origin = request.origin,
            packageName = packageName,
            notificationKey = notificationKey,
            groupKey = groupKey,
            isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            category = notification.category,
            shortcutId = notification.shortcutId,
            postTimeMs = postTimeMs,
            whenMs = notification.`when`.takeIf { it > 0L },
            isGroupConversation = isGroup,
            conversationTitle = conversationTitle,
            title = title,
            text = text,
            bigText = bigText,
            subText = subText,
            messages = current.first,
            historicMessages = historic.first,
            truncatedFields = truncated,
            visibility = visibility,
            removalReasonCode = null,
            deviceLocked = request.deviceLocked,
            collectorSessionId = request.collectorSessionId,
            observedWallMs = request.observedWallMs,
            elapsedRealtimeMs = request.elapsedRealtimeMs,
        )
    }

    private fun readMessages(
        extras: Bundle,
        key: String,
        limit: Int,
        budget: TextBudget,
    ): Pair<List<SnapshotMessage>, Boolean> {
        // The public message parser and Person are API 28 and 30; the lane never runs below API 30.
        if (Build.VERSION.SDK_INT < NotificationBounds.MIN_API) return emptyList<SnapshotMessage>() to false
        val array: Array<Parcelable> = safely { BundleCompat.getParcelableArray(extras, key, Bundle::class.java) }
            ?: return emptyList<SnapshotMessage>() to false
        // Cut to the newest entries before parsing so that a huge array costs no more than the bound.
        val newest = array.takeLast(limit).toTypedArray()
        val parsed: List<Notification.MessagingStyle.Message> = try {
            Notification.MessagingStyle.Message.getMessagesFromBundleArray(newest)
        } catch (ignored: RuntimeException) {
            return emptyList<SnapshotMessage>() to true
        }
        val raw = parsed.map { message ->
            val person = message.senderPerson
            RawMessage(
                senderLabel = person?.name,
                text = message.text,
                timestampMs = message.timestamp.takeIf { it > 0L },
                fromCurrentUserHint = person == null,
            )
        }
        val (bounded, omitted) = SnapshotBounds.boundMessages(raw, limit, budget)
        return bounded to (omitted || array.size > newest.size)
    }

    private fun visibilityOf(value: Int): NotificationVisibility = when (value) {
        Notification.VISIBILITY_PUBLIC -> NotificationVisibility.PUBLIC
        Notification.VISIBILITY_PRIVATE -> NotificationVisibility.PRIVATE
        Notification.VISIBILITY_SECRET -> NotificationVisibility.SECRET
        else -> NotificationVisibility.UNKNOWN
    }

    /** A malformed extra from another app must never crash the listener, so a failing read means "absent". */
    private inline fun <T> safely(block: () -> T?): T? = try {
        block()
    } catch (ignored: RuntimeException) {
        null
    }
}
