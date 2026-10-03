package org.sakshi.acquisition.projection

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.sakshi.processing.ocr.OcrBox
import org.sakshi.processing.ocr.OcrFrame
import org.sakshi.processing.ocr.OcrLine
import org.sakshi.processing.ocr.OcrPoint
import org.sakshi.processing.ocr.OcrResult

class ChatVisualParserTest {

    private fun line(
        text: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        blockIndex: Int = 0,
        confidence: Float? = 0.95f,
    ): OcrLine = OcrLine(
        blockIndex = blockIndex,
        text = text,
        polygon = listOf(OcrPoint(left, top), OcrPoint(right, top), OcrPoint(right, bottom), OcrPoint(left, bottom)),
        box = OcrBox(left, top, right, bottom),
        confidence = confidence,
        language = "en",
    )

    @Test
    fun `extracts header contact name and identifies chat direction`() {
        val screenWidth = 1080
        val screenHeight = 2400

        val lines = listOf(
            // Top header bar (top < 12% = 288px)
            line(text = "Alice Smith", left = 120, top = 80, right = 450, bottom = 140, blockIndex = 0),
            line(text = "Online", left = 120, top = 145, right = 250, bottom = 180, blockIndex = 1),

            // Date banner in middle
            line(text = "Today", left = 480, top = 350, right = 600, bottom = 400, blockIndex = 2),

            // Incoming message (left aligned: centerX = (50 + 450)/2 = 250 < 45% = 486)
            line(text = "Where is the money? 10:45 AM", left = 50, top = 500, right = 450, bottom = 580, blockIndex = 3),

            // Outgoing message (right aligned: centerX = (650 + 1030)/2 = 840 > 55% = 594)
            line(text = "I do not have it yet 10:46 AM", left = 650, top = 650, right = 1030, bottom = 730, blockIndex = 4),

            // Footer compose chrome (bottom > 90% = 2160)
            line(text = "Type a message", left = 100, top = 2200, right = 500, bottom = 2280, blockIndex = 5),
        )

        val frame = OcrFrame(
            originalWidth = screenWidth,
            originalHeight = screenHeight,
            sampleSize = 1,
            exifOrientation = 1,
            rotationDegrees = 0,
        )
        val result = OcrResult(lines = lines, frame = frame)

        val parsed = ChatVisualParser.parse(result, screenWidth, screenHeight)

        assertEquals("Alice Smith", parsed.contactName)
        assertFalse(parsed.isGroupChatEstimate)
        assertEquals(3, parsed.messages.size)

        // Verify Date Banner
        val banner = parsed.messages[0]
        assertEquals("Today", banner.text)
        assertEquals(MessageDirection.SYSTEM_BANNER, banner.direction)

        // Verify Incoming Bubble
        val incoming = parsed.messages[1]
        assertEquals("Where is the money?", incoming.text)
        assertEquals("10:45 AM", incoming.timestampText)
        assertEquals(MessageDirection.INCOMING, incoming.direction)

        // Verify Outgoing Bubble
        val outgoing = parsed.messages[2]
        assertEquals("I do not have it yet", outgoing.text)
        assertEquals("10:46 AM", outgoing.timestampText)
        assertEquals(MessageDirection.OUTGOING, outgoing.direction)
    }

    @Test
    fun `detects group chat from subtitle markers`() {
        val screenWidth = 1080
        val screenHeight = 2400

        val lines = listOf(
            line(text = "Project Team", left = 120, top = 80, right = 450, bottom = 140, blockIndex = 0),
            line(text = "Alice, Bob, You (8 members)", left = 120, top = 145, right = 600, bottom = 180, blockIndex = 1),
            line(text = "Please submit the report 09:15 AM", left = 50, top = 500, right = 550, bottom = 580, blockIndex = 2),
        )

        val frame = OcrFrame(screenWidth, screenHeight, 1, 1, 0)
        val result = OcrResult(lines, frame)

        val parsed = ChatVisualParser.parse(result, screenWidth, screenHeight)

        assertEquals("Project Team", parsed.contactName)
        assertTrue(parsed.isGroupChatEstimate)
        assertEquals(1, parsed.messages.size)
        assertEquals("Please submit the report", parsed.messages[0].text)
        assertEquals("09:15 AM", parsed.messages[0].timestampText)
    }

    @Test
    fun `identifies UI chrome`() {
        assertTrue(ChatVisualParser.isUIChrome("Type a message"))
        assertTrue(ChatVisualParser.isUIChrome("Send"))
        assertTrue(ChatVisualParser.isUIChrome("Online"))
        assertTrue(ChatVisualParser.isUIChrome("last seen recently"))
        assertTrue(ChatVisualParser.isUIChrome("Missed voice call at 11:00"))
        assertFalse(ChatVisualParser.isUIChrome("You must pay me right now"))
    }
}
