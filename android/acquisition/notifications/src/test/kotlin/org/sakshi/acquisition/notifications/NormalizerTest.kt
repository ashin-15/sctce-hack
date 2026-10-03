package org.sakshi.acquisition.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.sakshi.core.model.IdentityBasis

class NormalizerTest {
    @Test
    fun `one candidate per messaging style message with every claim kept apart`() {
        val result = NotificationNormalizer.normalize(
            snap(messages = listOf(msg("first", time = T0), msg("second", time = T0 + 60_000)), wall = T0 + 9_000_000, elapsed = 77),
        )
        assertNull(result.withheld)
        val (a, b) = result.messages
        assertEquals(listOf("first", "second"), result.messages.map { it.text })
        assertEquals(APP, a.sourceAppClaim)
        assertEquals("Synthetic Sender One", a.senderLabel)
        assertEquals(SenderLabelSource.MESSAGE_SENDER, a.senderLabelSource)
        assertEquals(IdentityBasis.APP_SCOPED_HINT, a.identityBasis)
        assertEquals(ObservedDirection.INCOMING_HINT, a.direction)
        assertEquals(SourceClaimTime(T0, SourceClaimTimeBasis.MESSAGE_TIMESTAMP), a.sourceClaimTime)
        assertEquals(T0 + 60_000, b.sourceClaimTime.epochMs)
        assertEquals(CollectorTime(T0 + 9_000_000, 77, SESSION), a.collector)
        assertEquals(a.collector, b.collector)
        assertEquals("$APP|shortcut|shortcut-1", a.conversation.scopeId)
        assertEquals(ObservedTextStatus.COMPLETE, a.textStatus)
    }

    @Test
    fun `messages from the current user and historic messages are context and never candidates`() {
        val result = NotificationNormalizer.normalize(
            snap(messages = listOf(msg("mine", sender = null), msg("theirs")), historic = listOf(msg("older"))),
        )
        assertEquals(listOf("theirs"), result.messages.map { it.text })
        val onlyMine = NotificationNormalizer.normalize(snap(messages = listOf(msg("mine", sender = null))))
        assertTrue(onlyMine.messages.isEmpty())
        assertEquals(WithheldReason.NO_TEXT, onlyMine.withheld)
    }

    @Test
    fun `a title and text notification gives one candidate with a weak sender`() {
        val result = NotificationNormalizer.normalize(snap(title = "Synthetic Title", text = "hello there", whenMs = T0 + 5))
        val only = result.messages.single()
        assertEquals("hello there", only.text)
        assertEquals("Synthetic Title", only.senderLabel)
        assertEquals(SenderLabelSource.NOTIFICATION_TITLE, only.senderLabelSource)
        assertEquals(ObservedDirection.UNKNOWN, only.direction)
        assertEquals(SourceClaimTime(T0 + 5, SourceClaimTimeBasis.NOTIFICATION_WHEN), only.sourceClaimTime)
    }

    @Test
    fun `big text wins over text and a missing when is not invented`() {
        val only = NotificationNormalizer.normalize(snap(title = "T", text = "short", bigText = "the long form")).messages.single()
        assertEquals("the long form", only.text)
        assertEquals(SourceClaimTime(null, SourceClaimTimeBasis.NONE), only.sourceClaimTime)
    }

    @Test
    fun `count notices are summaries and never messages`() {
        listOf("3 new messages", "2 messages", "New messages", "5 new messages from 2 chats", "1 new message").forEach {
            val result = NotificationNormalizer.normalize(snap(title = "App", text = it))
            assertTrue(result.messages.isEmpty(), it)
            assertEquals(WithheldReason.SUMMARY_COUNT_NOTICE, result.withheld, it)
        }
    }

    @Test
    fun `a real sentence that mentions messages is still a message`() {
        val result = NotificationNormalizer.normalize(snap(title = "A", text = "I sent 3 messages to you yesterday"))
        assertEquals(1, result.messages.size)
    }

    @Test
    fun `media labels are type hints and never content`() {
        listOf("Photo", "Voice message", "video").forEach {
            val result = NotificationNormalizer.normalize(snap(title = "A", text = it))
            assertEquals(WithheldReason.MEDIA_LABEL_ONLY, result.withheld, it)
        }
    }

    @Test
    fun `a group summary excerpt is summary only`() {
        val result = NotificationNormalizer.normalize(snap(title = "A", text = "Alice: are you there", summary = true))
        assertEquals(ObservedTextStatus.SUMMARY_ONLY, result.messages.single().textStatus)
    }

    @Test
    fun `cut text is truncated`() {
        val viaMessage = NotificationNormalizer.normalize(snap(messages = listOf(msg("cut", truncated = true))))
        assertEquals(ObservedTextStatus.TRUNCATED, viaMessage.messages.single().textStatus)
        val viaText = NotificationNormalizer.normalize(snap(title = "A", text = "cut", truncatedFields = setOf(SnapshotField.TEXT)))
        assertEquals(ObservedTextStatus.TRUNCATED, viaText.messages.single().textStatus)
    }

    @Test
    fun `secret notifications and removals give nothing`() {
        val secret = NotificationNormalizer.normalize(snap(title = "A", text = "hidden", visibility = NotificationVisibility.SECRET))
        assertEquals(WithheldReason.SECRET_VISIBILITY, secret.withheld)
        val removal = NotificationNormalizer.normalize(snap(origin = SnapshotOrigin.REMOVAL, removalReason = 8))
        assertEquals(WithheldReason.REMOVAL_ONLY, removal.withheld)
        assertTrue(removal.messages.isEmpty())
    }

    @Test
    fun `no sender gives an unknown identity basis and direction`() {
        val result = NotificationNormalizer.normalize(snap(text = "just text"))
        assertEquals(IdentityBasis.UNKNOWN, result.messages.single().identityBasis)
        assertEquals(ObservedDirection.UNKNOWN, result.messages.single().direction)
    }

    @Test
    fun `non-Latin scripts and emoji pass through unchanged`() {
        val texts = listOf("മലയാളം synthetic", "हिन्दी synthetic", "ok 😀")
        val result = NotificationNormalizer.normalize(snap(messages = texts.map { msg(it) }))
        assertEquals(texts, result.messages.map { it.text })
    }

    @Test
    fun `equal titles in different notifications never share a scope`() {
        val a = NotificationNormalizer.normalize(snap(key = "k1", shortcut = null, title = "Same", text = "x")).messages.single()
        val b = NotificationNormalizer.normalize(snap(key = "k2", shortcut = null, title = "Same", text = "x")).messages.single()
        assertTrue(a.conversation.scopeId != b.conversation.scopeId)
    }
}
