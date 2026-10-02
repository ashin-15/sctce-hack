package org.sakshi.processing.text

// All fixture content used by this test is synthetic. No real conversation data is involved.

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.sakshi.core.model.CodePointSpan
import org.sakshi.core.model.slice

class WhatsAppExportParserTest {
    private fun parse(text: String, max: Int = 10_000): ExportParse = WhatsAppExportParser.parse(text, max)

    private fun single(text: String): ParsedRecord = parse(text).records.single()

    @Test
    fun benchFixtureGivesBenchSendersAndTexts() {
        val text = "01/01/2025, 10:30 - Fake Sender: synthetic message\ncontinued line\n" +
            "01/01/2025, 10:31 - Fake Other: synthetic reply"
        val result = parse(text)
        assertEquals(listOf("Fake Sender", "Fake Other"), result.records.map { it.senderClaim })
        assertEquals(listOf("synthetic message\ncontinued line", "synthetic reply"), result.records.map { it.text })
        assertEquals(listOf("01/01/2025", "01/01/2025"), result.records.map { it.dateRaw })
        assertEquals(listOf("10:30", "10:31"), result.records.map { it.timeRaw })
        assertEquals("android", result.dialect)
        assertEquals(listOf(0, 1), result.records.map { it.index })
    }

    @Test
    fun twelveHourAndTwentyFourHourForms() {
        val pm = single("1/2/25, 9:05 PM - A: hi")
        assertEquals("9:05 PM", pm.timeRaw)
        assertEquals("1/2/25", pm.dateRaw)
        assertTrue(parse("1/2/25, 9:05 pm - A: hi").twelveHourClock)
        assertEquals(false, parse("1/2/25, 21:05 - A: hi").twelveHourClock)
    }

    @Test
    fun secondsAreKept() {
        assertEquals("10:30:15", single("01/02/2025, 10:30:15 - A: hi").timeRaw)
    }

    @Test
    fun bracketedShape() {
        val result = parse("[01/02/25, 10:30:15] Alice: hello there")
        val record = result.records.single()
        assertEquals("bracket", result.dialect)
        assertEquals("Alice", record.senderClaim)
        assertEquals("hello there", record.text)
        assertEquals("10:30:15", record.timeRaw)
    }

    @Test
    fun dotAndDashSeparators() {
        assertEquals("13.02.2025", single("13.02.2025, 10:30 - A: hi").dateRaw)
        assertEquals("13-02-25", single("13-02-25, 10:30 - A: hi").dateRaw)
        assertTrue(parse("13.02/2025, 10:30 - A: hi").records.isEmpty())
    }

    @Test
    fun noBreakSpacesBeforeMeridiem() {
        for (gap in listOf(" ", " ")) {
            val record = single("01/02/2025, 9:05${gap}PM - A: hi")
            assertEquals("9:05${gap}PM", record.timeRaw)
            assertEquals("hi", record.text)
        }
    }

    @Test
    fun crlfInputExcludesCarriageReturns() {
        val text = "01/01/2025, 10:30 - A: hi\r\n01/01/2025, 10:31 - B: yo\r\nmore\r\n"
        val result = parse(text)
        assertEquals(listOf("hi", "yo\nmore"), result.records.map { it.text })
        val second = result.records[1]
        assertEquals(second.text, second.bodySpan.slice(text).replace("\r", ""))
    }

    @Test
    fun leftToRightMarkAndByteOrderMarkBeforeRecord() {
        val text = "﻿‎[01/01/25, 10:30:00] A: hi\n‎01/01/2025, 10:31 - B: yo"
        assertEquals(listOf("A", "B"), parse(text).records.map { it.senderClaim })
    }

    @Test
    fun documentsFirstColonSplitForSenderContainingColon() {
        // The sender claim ends at the first ": ", so "Dr: Who" cannot be recovered. It stays user-correctable.
        val record = single("01/01/2025, 10:30 - Dr: Who: hello")
        assertEquals("Dr", record.senderClaim)
        assertEquals("Who: hello", record.text)
        assertEquals("Bob:Smith", single("01/01/2025, 10:30 - Bob:Smith: hi").senderClaim)
    }

    @Test
    fun linesWithoutSenderAreSystemRecords() {
        val text = "01/01/2025, 10:30 - Messages and calls are end-to-end encrypted.\n[01/01/25, 10:31:00] Group created"
        val result = parse(text)
        assertEquals(listOf(LineKind.SYSTEM, LineKind.SYSTEM), result.records.map { it.kind })
        assertNull(result.records[0].senderClaim)
        assertEquals("Messages and calls are end-to-end encrypted.", result.records[0].text)
        assertEquals("mixed", result.dialect)
    }

    @Test
    fun mediaPlaceholdersOnlyWhenExact() {
        for (body in listOf("<Media omitted>", "image omitted", "VIDEO OMITTED", "audio omitted", "sticker omitted", "GIF omitted", "document omitted", "‎image omitted")) {
            assertTrue(single("01/01/2025, 10:30 - A: $body").mediaOmitted, body)
        }
        assertEquals(false, single("01/01/2025, 10:30 - A: image omitted today").mediaOmitted)
        assertEquals(false, single("01/01/2025, 10:30 - A: This message was deleted").mediaOmitted)
        assertEquals("This message was deleted", single("01/01/2025, 10:30 - A: This message was deleted").text)
    }

    @Test
    fun textBeforeFirstRecordIsReportedAsUnparsedPrefix() {
        val text = "stray header\n01/01/2025, 10:30 - A: hi"
        val result = parse(text)
        assertEquals(CodePointSpan(0, 13), result.unparsedPrefix)
        assertTrue(ParseWarning.UNPARSED_PREFIX in result.warnings)
        assertEquals(1, result.records.size)
        assertNull(parse("\n01/01/2025, 10:30 - A: hi").unparsedPrefix)
    }

    @Test
    fun emptyAndUnrecognisedInput() {
        val empty = parse("")
        assertEquals(setOf(ParseWarning.NO_RECORDS), empty.warnings)
        assertNull(empty.dialect)
        assertNull(empty.unparsedPrefix)
        val junk = parse("just some text")
        assertTrue(ParseWarning.NO_RECORDS in junk.warnings)
        assertEquals(CodePointSpan(0, 14), junk.unparsedPrefix)
    }

    @Test
    fun dateOrderDecisionTable() {
        val dayFirst = parse("13/01/2025, 10:30 - A: x")
        assertEquals(DateOrder.DAY_MONTH, dayFirst.dateOrder)
        assertTrue(dayFirst.warnings.isEmpty())
        val monthFirst = parse("01/13/2025, 10:30 - A: x")
        assertEquals(DateOrder.MONTH_DAY, monthFirst.dateOrder)
        assertTrue(monthFirst.warnings.isEmpty())
        val mixed = parse("13/01/2025, 10:30 - A: x\n01/13/2025, 10:31 - A: y")
        assertEquals(DateOrder.AMBIGUOUS, mixed.dateOrder)
        assertEquals(setOf(ParseWarning.MIXED_DATE_SHAPES), mixed.warnings)
        val neither = parse("01/02/2025, 10:30 - A: x")
        assertEquals(DateOrder.AMBIGUOUS, neither.dateOrder)
        assertEquals(setOf(ParseWarning.AMBIGUOUS_DATE_ORDER), neither.warnings)
    }

    @Test
    fun maxRecordsStopsWithWarning() {
        val text = (1..5).joinToString("\n") { "01/01/2025, 10:3$it - A: m$it\nmore$it" }
        val result = parse(text, max = 3)
        assertEquals(3, result.records.size)
        assertTrue(ParseWarning.LIMIT_REACHED in result.warnings)
        assertEquals("m3\nmore3", result.records.last().text)
        assertTrue(ParseWarning.LIMIT_REACHED !in parse(text).warnings)
        assertTrue(parse(text, max = 0).records.isEmpty())
    }

    @Test
    fun spansSliceBackWithEmojiAndIndicTextBeforeTheRecord() {
        val text = "😀 नമസ്കാരം\n" +
            "01/01/2025, 10:30 - Ann: hi 😀 नमस्ते\n" +
            "01/01/2025, 10:31 - Bob: first\nsecond 😀\r\n" +
            "[01/01/25, 10:32:00] Cy: പാസ്‌വേഡ്"
        val result = parse(text)
        val prefix = requireNotNull(result.unparsedPrefix)
        assertEquals("😀 नമസ്കാരം\n", prefix.slice(text))
        val first = result.records[0]
        assertEquals("hi 😀 नमस्ते", first.bodySpan.slice(text))
        assertEquals(first.text, first.bodySpan.slice(text))
        assertEquals("01/01/2025, 10:30 - Ann: hi 😀 नमस्ते", first.recordSpan.slice(text))
        val second = result.records[1]
        assertEquals("first\nsecond 😀", second.text)
        assertEquals(second.text, second.bodySpan.slice(text).replace("\r", ""))
        assertEquals("01/01/2025, 10:31 - Bob: first\nsecond 😀", second.recordSpan.slice(text).replace("\r", "").trimEnd('\n'))
        val third = result.records[2]
        assertEquals("പാസ്‌വേഡ്", third.bodySpan.slice(text))
        assertEquals("[01/01/25, 10:32:00] Cy: പാസ്‌വേഡ്", third.recordSpan.slice(text))
    }

    @Test(timeout = 20_000)
    fun pathologicalInputFinishesQuickly() {
        val budget = 1 shl 20
        val pieces = listOf(
            ":".repeat(budget),
            "1/".repeat(budget / 2),
            "[".repeat(budget),
            "12/12/2025, 10:30 - " + ":".repeat(budget),
            "1".repeat(budget),
            "‎".repeat(budget),
            " ".repeat(budget),
        )
        for (piece in pieces) {
            parse(piece)
            parse((piece.take(64) + "\n").repeat(budget / 65))
        }
        val manyRecords = "01/01/2025, 10:30 - A: x\n".repeat(budget / 25)
        assertEquals(budget / 25, parse(manyRecords, max = Int.MAX_VALUE).records.size)
    }
}
