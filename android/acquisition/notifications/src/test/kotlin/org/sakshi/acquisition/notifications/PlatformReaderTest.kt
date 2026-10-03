package org.sakshi.acquisition.notifications

import android.app.Notification
import android.app.Person
import android.content.Context
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.sakshi.core.model.IdentityBasis

/**
 * Real platform notification objects, built the way a messaging app builds them, run through the snapshot reader, the
 * normaliser, the differ and the inbox. All content is synthetic. These establish parser behaviour only, not the
 * payload of any real messenger.
 */
@RunWith(RobolectricTestRunner::class)
class PlatformReaderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val one = SyntheticMessage("synthetic hello", T0, "Synthetic Sender One")
    private val two = SyntheticMessage("synthetic second", T0 + 60_000, "Synthetic Sender One")

    /** The bundle layout the platform message parser reads: text, time and sender person. */
    private fun messageBundles(texts: List<String>): Array<Bundle> {
        val person = Person.Builder().setName("Synthetic Sender One").build()
        return texts.mapIndexed { index, text ->
            Bundle().apply {
                putCharSequence("text", text)
                putLong("time", T0 + index + 1)
                putParcelable("sender_person", person)
            }
        }.toTypedArray()
    }

    private fun read(notification: Notification, key: String = "synthetic-key-1", groupKey: String? = null): NotificationSnapshot =
        SyntheticNotifications.source(notification, key, groupKey).read(SyntheticNotifications.request())

    private fun candidates(notification: Notification): List<ObservedMessage> =
        NotificationNormalizer.normalize(read(notification)).messages

    @Test
    fun `one to one messaging style gives typed messages and one candidate per message`() {
        val snapshot = read(SyntheticNotifications.messaging(context, listOf(one, two)))
        assertEquals(listOf("synthetic hello", "synthetic second"), snapshot.messages.map { it.text })
        assertEquals(listOf(T0, T0 + 60_000), snapshot.messages.map { it.timestampMs })
        assertEquals("Synthetic Sender One", snapshot.messages.first().senderLabel)
        assertFalse(snapshot.messages.first().fromCurrentUserHint)
        assertEquals("synthetic-shortcut", snapshot.shortcutId)
        assertEquals(NotificationVisibility.PRIVATE, snapshot.visibility)
        assertEquals(false, snapshot.isGroupConversation)
        val result = NotificationNormalizer.normalize(snapshot)
        assertEquals(2, result.messages.size)
        assertTrue(result.messages.all { it.identityBasis == IdentityBasis.APP_SCOPED_HINT })
    }

    @Test
    fun `group messaging keeps the group claim and the conversation title as claims`() {
        val snapshot = read(
            SyntheticNotifications.messaging(
                context,
                listOf(one, SyntheticMessage("synthetic other", T0 + 1, "Synthetic Sender Two")),
                group = true,
                conversationTitle = "Synthetic Group Title",
            ),
        )
        assertEquals(true, snapshot.isGroupConversation)
        assertEquals("Synthetic Group Title", snapshot.conversationTitle)
        assertEquals(listOf("Synthetic Sender One", "Synthetic Sender Two"), snapshot.messages.map { it.senderLabel })
        val scope = NotificationNormalizer.normalize(snapshot).messages.first().conversation
        assertEquals(true, scope.isGroup)
        assertEquals("Synthetic Group Title", scope.conversationTitle)
    }

    @Test
    fun `current user messages and historic messages are context`() {
        val snapshot = read(
            SyntheticNotifications.messaging(
                context,
                listOf(SyntheticMessage("synthetic mine", T0, null), two),
                historic = listOf(SyntheticMessage("synthetic older", T0 - 1, "Synthetic Sender One")),
            ),
        )
        assertTrue(snapshot.messages.first().fromCurrentUserHint)
        assertEquals(listOf("synthetic older"), snapshot.historicMessages.map { it.text })
        assertEquals(listOf("synthetic second"), NotificationNormalizer.normalize(snapshot).messages.map { it.text })
    }

    @Test
    fun `fixture - an update of the same key with one more message yields only the new one`() {
        val differ = differ()
        val first = SyntheticNotifications.messaging(context, listOf(one))
        val updated = SyntheticNotifications.messaging(context, listOf(one, two))
        assertEquals(listOf("synthetic hello"), differ.process(read(first)).texts())
        assertEquals(listOf("synthetic second"), differ.process(read(updated)).texts())
        assertTrue(differ.process(read(updated)).newCandidates.isEmpty())
    }

    @Test
    fun `fixture - a summary with children does not double the message`() {
        val differ = differ()
        val child = SyntheticNotifications.messaging(context, listOf(one), groupKey = "synthetic-group")
        val summary = SyntheticNotifications.plain(
            context, title = "Synthetic Sender One", text = "synthetic hello", summary = true, groupKey = "synthetic-group",
        )
        val inbox = CandidateInbox()
        val childResult = differ.process(read(child, key = "child", groupKey = "synthetic-group"))
        val summaryResult = differ.process(read(summary, key = "summary", groupKey = "synthetic-group"))
        inbox.add(childResult.newCandidates)
        inbox.add(summaryResult.newCandidates)
        assertEquals(1, inbox.candidates.value.size)
        assertEquals(1, summaryResult.suppressed[SuppressionLayer.GROUP_SUMMARY_OVERLAP])
    }

    @Test
    fun `a count notice is a summary and never a message`() {
        val notification = SyntheticNotifications.plain(context, title = "Synthetic App", text = "3 new messages", summary = true)
        val result = NotificationNormalizer.normalize(read(notification))
        assertTrue(result.messages.isEmpty())
        assertEquals(WithheldReason.SUMMARY_COUNT_NOTICE, result.withheld)
    }

    @Test
    fun `a summary that carries an excerpt is summary only`() {
        val notification = SyntheticNotifications.plain(context, title = "Synthetic Sender One", text = "an excerpt", summary = true)
        assertEquals(ObservedTextStatus.SUMMARY_ONLY, candidates(notification).single().textStatus)
    }

    @Test
    fun `text the platform builder already shortened is passed through and not claimed as cut by this lane`() {
        // The platform builder itself limits a char sequence, so a text longer than our bound cannot be built here.
        val long = "synthetic ".repeat(500)
        val snapshot = read(SyntheticNotifications.plain(context, title = "Synthetic", text = "short", bigText = long))
        val length = checkNotNull(snapshot.bigText).length
        assertTrue(length in 1..NotificationBounds.MAX_FIELD_CHARS)
        assertFalse(SnapshotField.BIG_TEXT in snapshot.truncatedFields)
    }

    @Test
    fun `an oversized text field in the extras is cut to the bound and reported as truncated`() {
        val long = "synthetic ".repeat(500)
        val notification = SyntheticNotifications.plain(context, title = "Synthetic", text = "short")
        notification.extras.putCharSequence(Notification.EXTRA_BIG_TEXT, long)
        val snapshot = read(notification)
        assertEquals(NotificationBounds.MAX_FIELD_CHARS, snapshot.bigText?.length)
        assertTrue(SnapshotField.BIG_TEXT in snapshot.truncatedFields)
        val candidate = NotificationNormalizer.normalize(snapshot).messages.single()
        assertEquals(ObservedTextStatus.TRUNCATED, candidate.textStatus)
    }

    @Test
    fun `more messages than the cap keep the newest and flag the omission`() {
        val notification = SyntheticNotifications.messaging(context, listOf(one))
        notification.extras.putParcelableArray(Notification.EXTRA_MESSAGES, messageBundles((1..40).map { "synthetic m$it" }))
        val snapshot = read(notification)
        assertEquals(NotificationBounds.MAX_MESSAGES, snapshot.messages.size)
        assertEquals("synthetic m16", snapshot.messages.first().text)
        assertEquals("synthetic m40", snapshot.messages.last().text)
        assertTrue(SnapshotField.MESSAGES_OMITTED in snapshot.truncatedFields)
    }

    @Test
    fun `emoji Malayalam and Devanagari text is kept exactly`() {
        val texts = listOf("😀 synthetic", "മലയാളം synthetic", "हिन्दी synthetic")
        val messages = texts.mapIndexed { index, text -> SyntheticMessage(text, T0 + index, "Synthetic Sender One") }
        assertEquals(texts, candidates(SyntheticNotifications.messaging(context, messages)).map { it.text })
    }

    @Test
    fun `a secret notification is never read for text`() {
        val secret = SyntheticNotifications.messaging(context, listOf(one), visibility = Notification.VISIBILITY_SECRET)
        val snapshot = read(secret)
        assertEquals(NotificationVisibility.SECRET, snapshot.visibility)
        assertTrue(snapshot.messages.isEmpty())
        assertNull(snapshot.title)
        assertEquals(WithheldReason.SECRET_VISIBILITY, NotificationNormalizer.normalize(snapshot).withheld)
    }

    @Test
    fun `plain text uses the title as a weak sender and keeps the claimed when apart from collector time`() {
        val notification = SyntheticNotifications.plain(context, title = "Synthetic Title", text = "synthetic body", whenMs = T0 - 10_000)
        val candidate = candidates(notification).single()
        assertEquals(SenderLabelSource.NOTIFICATION_TITLE, candidate.senderLabelSource)
        assertEquals(T0 - 10_000, candidate.sourceClaimTime.epochMs)
        assertEquals(T0 + 5_000, candidate.collector.wallMs)
        assertEquals(T0, candidate.notificationPostTimeMs)
    }

    @Test
    fun `a notification with no standard text gives no candidate`() {
        val result = NotificationNormalizer.normalize(read(SyntheticNotifications.plain(context, title = null, text = null)))
        assertEquals(WithheldReason.NO_TEXT, result.withheld)
    }

    @Test
    fun `the snapshot is a copy that holds no platform object`() {
        val snapshot = read(SyntheticNotifications.messaging(context, listOf(one)))
        val types = NotificationSnapshot::class.java.declaredFields.map { it.type.name }
        assertTrue(types.none { it.startsWith("android.") }, types.toString())
    }
}
