package org.sakshi.acquisition.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SnapshotBoundsTest {
    private fun raw(text: String, sender: String? = "Synthetic Sender One") = RawMessage(sender, text, T0, sender == null)

    @Test
    fun `a field is cut to the field bound and flagged`() {
        val clipped = checkNotNull(TextBudget().clip("x".repeat(NotificationBounds.MAX_FIELD_CHARS + 10)))
        assertEquals(NotificationBounds.MAX_FIELD_CHARS, clipped.value.length)
        assertTrue(clipped.truncated)
    }

    @Test
    fun `text within the bound is untouched`() {
        val clipped = checkNotNull(TextBudget().clip("short"))
        assertEquals("short", clipped.value)
        assertFalse(clipped.truncated)
    }

    @Test
    fun `a cut never splits a surrogate pair`() {
        val emoji = "😀"
        val text = "a".repeat(NotificationBounds.MAX_FIELD_CHARS - 1) + emoji
        val clipped = checkNotNull(TextBudget().clip(text))
        assertTrue(clipped.truncated)
        assertEquals(NotificationBounds.MAX_FIELD_CHARS - 1, clipped.value.length)
        assertFalse(Character.isHighSurrogate(clipped.value.last()))
    }

    @Test
    fun `null and empty input give null`() {
        val budget = TextBudget()
        assertEquals(null, budget.clip(null))
        assertEquals(null, budget.clip(""))
    }

    @Test
    fun `the total allowance is shared by all fields`() {
        val budget = TextBudget()
        repeat(4) { assertFalse(checkNotNull(budget.clip("y".repeat(NotificationBounds.MAX_FIELD_CHARS))).truncated) }
        assertTrue(budget.isExhausted)
        val exhausted = checkNotNull(budget.clip("more"))
        assertEquals("", exhausted.value)
        assertTrue(exhausted.truncated)
    }

    @Test
    fun `only the newest messages are kept and the omission is flagged`() {
        val all = (1..40).map { raw("m$it") }
        val (kept, omitted) = SnapshotBounds.boundMessages(all, NotificationBounds.MAX_MESSAGES, TextBudget())
        assertEquals(NotificationBounds.MAX_MESSAGES, kept.size)
        assertEquals("m16", kept.first().text)
        assertEquals("m40", kept.last().text)
        assertTrue(omitted)
    }

    @Test
    fun `a long message is truncated and flagged on the message`() {
        val (kept, _) = SnapshotBounds.boundMessages(
            listOf(raw("z".repeat(NotificationBounds.MAX_FIELD_CHARS * 2))), 25, TextBudget(),
        )
        assertEquals(NotificationBounds.MAX_FIELD_CHARS, kept.single().text.length)
        assertTrue(kept.single().textTruncated)
    }

    @Test
    fun `when the total allowance runs out older messages are left out and flagged`() {
        val all = (1..10).map { raw("n".repeat(NotificationBounds.MAX_FIELD_CHARS)) }
        val (kept, omitted) = SnapshotBounds.boundMessages(all, 25, TextBudget())
        assertEquals(4, kept.size)
        assertTrue(omitted)
    }

    @Test
    fun `a message without text is left out`() {
        val (kept, omitted) = SnapshotBounds.boundMessages(listOf(RawMessage("A", null, T0, false)), 25, TextBudget())
        assertTrue(kept.isEmpty())
        assertTrue(omitted)
    }

    @Test
    fun `a missing sender marks the current user hint`() {
        val (kept, _) = SnapshotBounds.boundMessages(listOf(raw("mine", sender = null)), 25, TextBudget())
        assertTrue(kept.single().fromCurrentUserHint)
        assertEquals(null, kept.single().senderLabel)
    }

    @Test
    fun `a snapshot refuses unbounded input`() {
        val tooMany = List(NotificationBounds.MAX_MESSAGES + 1) { msg("m$it") }
        assertFailsWith<IllegalArgumentException> { snap(messages = tooMany) }
        assertFailsWith<IllegalArgumentException> { snap(text = "x".repeat(NotificationBounds.MAX_FIELD_CHARS + 1)) }
        assertFailsWith<IllegalArgumentException> { snap(origin = SnapshotOrigin.REMOVAL) }
    }

    @Test
    fun `the documented bounds match the megaplan and report numbers`() {
        assertEquals(64, NotificationBounds.QUEUE_CAPACITY)
        assertEquals(100, NotificationBounds.INBOX_MAX_RECORDS)
        assertEquals(2 * 1024 * 1024, NotificationBounds.INBOX_MAX_BYTES)
        assertEquals(25, NotificationBounds.MAX_MESSAGES)
        assertEquals(25, NotificationBounds.MAX_HISTORIC_MESSAGES)
        assertEquals(2_048, NotificationBounds.MAX_FIELD_CHARS)
        assertEquals(8_192, NotificationBounds.MAX_TOTAL_TEXT_CHARS)
        assertEquals(256, NotificationBounds.DEDUP_MAX_SCOPES)
        assertEquals(30 * 60 * 1000L, NotificationBounds.DEDUP_TTL_MS)
        assertEquals(30, NotificationBounds.MIN_API)
    }
}
