package org.sakshi.acquisition.notifications

import android.app.Notification
import android.app.Person
import android.content.Context

/** One synthetic message for a builder. A null sender is the current user, as Android documents it. */
class SyntheticMessage(val text: CharSequence, val timeMs: Long, val sender: String?)

/**
 * Builds real [Notification] objects from synthetic data, the way a messaging app would publish them, so that the
 * snapshot reader is exercised against the platform classes and not against hand-made fakes.
 */
object SyntheticNotifications {
    private fun person(name: String): Person = Person.Builder().setName(name).setKey("synthetic-key-$name").build()

    fun messaging(
        context: Context,
        messages: List<SyntheticMessage>,
        historic: List<SyntheticMessage> = emptyList(),
        group: Boolean = false,
        conversationTitle: String? = null,
        summary: Boolean = false,
        groupKey: String? = null,
        shortcutId: String? = "synthetic-shortcut",
        visibility: Int = Notification.VISIBILITY_PRIVATE,
        whenMs: Long = 0L,
    ): Notification {
        val style = Notification.MessagingStyle(person("Synthetic Self"))
        conversationTitle?.let(style::setConversationTitle)
        style.setGroupConversation(group)
        historic.forEach { style.addHistoricMessage(Notification.MessagingStyle.Message(it.text, it.timeMs, it.sender?.let(::person))) }
        messages.forEach { style.addMessage(it.text, it.timeMs, it.sender?.let(::person)) }
        return base(context, summary, groupKey, shortcutId, visibility, whenMs).setStyle(style).build()
    }

    fun plain(
        context: Context,
        title: String?,
        text: String?,
        bigText: String? = null,
        summary: Boolean = false,
        groupKey: String? = null,
        visibility: Int = Notification.VISIBILITY_PRIVATE,
        whenMs: Long = 0L,
    ): Notification {
        val builder = base(context, summary, groupKey, null, visibility, whenMs)
        title?.let(builder::setContentTitle)
        text?.let(builder::setContentText)
        bigText?.let { builder.setStyle(Notification.BigTextStyle().bigText(it)) }
        return builder.build()
    }

    private fun base(
        context: Context,
        summary: Boolean,
        groupKey: String?,
        shortcutId: String?,
        visibility: Int,
        whenMs: Long,
    ): Notification.Builder {
        val builder = Notification.Builder(context, "synthetic-channel")
        builder.setCategory(Notification.CATEGORY_MESSAGE).setVisibility(visibility).setGroupSummary(summary)
        groupKey?.let(builder::setGroup)
        shortcutId?.let(builder::setShortcutId)
        if (whenMs > 0L) builder.setWhen(whenMs)
        return builder
    }

    fun source(notification: Notification, key: String = "synthetic-key-1", groupKey: String? = null, packageName: String = APP, postTimeMs: Long = T0): PlatformNotificationSource =
        PlatformNotificationSource(packageName, key, groupKey, postTimeMs, notification)

    fun request(origin: SnapshotOrigin = SnapshotOrigin.LIVE, locked: Boolean = false): SnapshotRequest =
        SnapshotRequest(origin, locked, SESSION, T0 + 5_000, 1_000)
}
