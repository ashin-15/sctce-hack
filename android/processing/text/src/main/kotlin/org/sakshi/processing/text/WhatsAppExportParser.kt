package org.sakshi.processing.text

import org.sakshi.core.model.CodePointSpan

/** Day-month-year versus month-day-year, decided from the whole file and never guessed. */
public enum class DateOrder { DAY_MONTH, MONTH_DAY, AMBIGUOUS }

/** A recognised record is either a sender message or a line without a sender part. */
public enum class LineKind { MESSAGE, SYSTEM }

/** Conditions the caller should surface to the user. */
public enum class ParseWarning { NO_RECORDS, AMBIGUOUS_DATE_ORDER, MIXED_DATE_SHAPES, LIMIT_REACHED, UNPARSED_PREFIX }

/**
 * One export record. Date and time are kept exactly as written. Spans are half-open code point
 * offsets over the original export text. [text] joins continuation lines with `\n` and excludes line
 * terminators; for a single-line record it equals the slice at [bodySpan].
 */
public data class ParsedRecord(
    val index: Int,
    val kind: LineKind,
    val dateRaw: String,
    val timeRaw: String,
    val senderClaim: String?,
    val text: String,
    val bodySpan: CodePointSpan,
    val recordSpan: CodePointSpan,
    val mediaOmitted: Boolean,
)

/**
 * Parse result. [dialect] is `android`, `bracket` or `mixed` (null without records).
 * [twelveHourClock] is true when any record carries an AM/PM marker.
 */
public data class ExportParse(
    val dialect: String?,
    val dateOrder: DateOrder,
    val twelveHourClock: Boolean,
    val records: List<ParsedRecord>,
    val unparsedPrefix: CodePointSpan?,
    val warnings: Set<ParseWarning>,
)

/**
 * Parser for WhatsApp "export chat" text files in the Android shape `d/m/yy, h:mm[ AM|PM] - Sender: body`
 * and the bracketed shape `[d/m/yy, h:mm:ss] Sender: body`. Date separators may be `/`, `.` or `-`;
 * seconds are optional; a space, no-break space or narrow no-break space may precede AM/PM; digits are ASCII;
 * a left-to-right mark or byte order mark may precede a record. Lines are split on `\n`, with a
 * preceding `\r` dropped. A line that is not a record continues the previous record, blank lines included.
 *
 * The sender split is a heuristic: the sender claim ends at the FIRST `": "` after the date-time prefix (or
 * a `:` that ends the line), so a sender name that contains a colon followed by a space cannot be recovered.
 * It stays user-correctable. Content is not interpreted: only exact media placeholders set `mediaOmitted`.
 * The parser produces no timestamps; see [ExportTime].
 */
public object WhatsAppExportParser {
    public const val VERSION: String = "whatsapp-txt-v1"

    private const val LRM = '‎'
    private const val BOM = '﻿'
    private val MEDIA_PLACEHOLDERS = listOf(
        "<Media omitted>", "image omitted", "video omitted", "audio omitted",
        "sticker omitted", "GIF omitted", "document omitted",
    )

    private class Head(
        val dateRaw: String,
        val timeRaw: String,
        val restStart: Int,
        val bracket: Boolean,
        val meridiem: Boolean,
        val first: Int,
        val second: Int,
    )

    private class Open(
        val head: Head,
        val kind: LineKind,
        val sender: String?,
        val bodyStartCp: Int,
        val recordStartCp: Int,
        val text: StringBuilder,
        var endCp: Int,
    )

    /** Parses [text], stopping after [maxRecords] records with [ParseWarning.LIMIT_REACHED]. */
    public fun parse(text: String, maxRecords: Int = 10_000): ExportParse {
        require(maxRecords >= 0) { "maxRecords must be >= 0" }
        val records = mutableListOf<ParsedRecord>()
        val heads = mutableListOf<Head>()
        var open: Open? = null
        var firstRecordCp = -1
        var limitReached = false
        var lineStart = 0
        var lineCp = 0

        fun close() {
            val current = open ?: return
            val body = current.text.toString()
            records += ParsedRecord(
                index = records.size,
                kind = current.kind,
                dateRaw = current.head.dateRaw,
                timeRaw = current.head.timeRaw,
                senderClaim = current.sender,
                text = body,
                bodySpan = CodePointSpan(current.bodyStartCp, current.endCp),
                recordSpan = CodePointSpan(current.recordStartCp, current.endCp),
                mediaOmitted = current.kind == LineKind.MESSAGE && isMediaPlaceholder(body),
            )
            heads += current.head
            open = null
        }

        while (lineStart < text.length) {
            val newline = text.indexOf('\n', lineStart)
            val next = if (newline < 0) text.length else newline + 1
            var contentEnd = if (newline < 0) text.length else newline
            if (contentEnd > lineStart && text[contentEnd - 1] == '\r') contentEnd--
            val contentCpLength = text.codePointCount(lineStart, contentEnd)
            val head = parseHead(text, lineStart, contentEnd)
            if (head != null) {
                close()
                if (records.size >= maxRecords) {
                    limitReached = true
                    break
                }
                if (firstRecordCp < 0) firstRecordCp = lineCp
                open = openRecord(text, head, lineStart, lineCp, contentEnd)
                open?.endCp = lineCp + contentCpLength
            } else {
                open?.let {
                    it.text.append('\n').append(text, lineStart, contentEnd)
                    it.endCp = lineCp + contentCpLength
                }
            }
            lineCp += text.codePointCount(lineStart, next)
            lineStart = next
        }
        close()
        return summarise(text, records, heads, firstRecordCp, limitReached)
    }

    private fun summarise(
        text: String,
        records: List<ParsedRecord>,
        heads: List<Head>,
        firstRecordCp: Int,
        limitReached: Boolean,
    ): ExportParse {
        val warnings = linkedSetOf<ParseWarning>()
        if (records.isEmpty()) warnings += ParseWarning.NO_RECORDS
        if (limitReached) warnings += ParseWarning.LIMIT_REACHED
        val prefixEnd = if (firstRecordCp >= 0) firstRecordCp else text.codePointCount(0, text.length)
        val prefixUtf16 = text.offsetByCodePoints(0, prefixEnd)
        val prefix = if (hasContent(text, prefixUtf16)) CodePointSpan(0, prefixEnd) else null
        if (prefix != null) warnings += ParseWarning.UNPARSED_PREFIX
        val firstOverTwelve = heads.any { it.first > MAX_MONTH }
        val secondOverTwelve = heads.any { it.second > MAX_MONTH }
        val order = when {
            firstOverTwelve && secondOverTwelve -> DateOrder.AMBIGUOUS.also { warnings += ParseWarning.MIXED_DATE_SHAPES }
            firstOverTwelve -> DateOrder.DAY_MONTH
            secondOverTwelve -> DateOrder.MONTH_DAY
            else -> DateOrder.AMBIGUOUS.also { if (heads.isNotEmpty()) warnings += ParseWarning.AMBIGUOUS_DATE_ORDER }
        }
        val dialect = when {
            heads.isEmpty() -> null
            heads.all { it.bracket } -> "bracket"
            heads.none { it.bracket } -> "android"
            else -> "mixed"
        }
        return ExportParse(dialect, order, heads.any { it.meridiem }, records, prefix, warnings)
    }

    private const val MAX_MONTH = 12

    private fun hasContent(text: String, endUtf16: Int): Boolean =
        (0 until endUtf16).any { !text[it].isWhitespace() && text[it] != BOM && text[it] != LRM }

    private fun isMediaPlaceholder(body: String): Boolean {
        val trimmed = if (body.startsWith(LRM)) body.substring(1) else body
        return MEDIA_PLACEHOLDERS.any { it.equals(trimmed, ignoreCase = true) }
    }

    private fun openRecord(text: String, head: Head, lineStart: Int, lineCp: Int, contentEnd: Int): Open {
        val split = findSplit(text, head.restStart, contentEnd)
        val sender = if (split < 0) null else text.substring(head.restStart, split).removePrefix(LRM.toString())
        val isMessage = sender != null && sender.isNotBlank()
        val bodyStart = when {
            !isMessage -> head.restStart
            split + 1 < contentEnd && text[split + 1] == ' ' -> split + 2
            else -> split + 1
        }
        val bodyStartCp = lineCp + text.codePointCount(lineStart, bodyStart)
        return Open(
            head = head,
            kind = if (isMessage) LineKind.MESSAGE else LineKind.SYSTEM,
            sender = sender.takeIf { isMessage },
            bodyStartCp = bodyStartCp,
            recordStartCp = lineCp,
            text = StringBuilder(text.substring(bodyStart, contentEnd)),
            endCp = bodyStartCp,
        )
    }

    /** Index of the first `:` followed by a space or by the end of the line, or -1. */
    private fun findSplit(text: String, from: Int, end: Int): Int {
        for (i in from until end) {
            if (text[i] == ':' && (i + 1 == end || text[i + 1] == ' ')) return i
        }
        return -1
    }

    private fun isGap(c: Char): Boolean = c == ' ' || c == '\t' || c == '\u00A0' || c == '\u202F'

    private fun skipGaps(text: String, from: Int, end: Int): Int {
        var i = from
        while (i < end && isGap(text[i])) i++
        return i
    }

    private fun digitRun(text: String, from: Int, end: Int): Int {
        var i = from
        while (i < end && text[i] in '0'..'9') i++
        return i
    }

    private fun parseHead(text: String, lineStart: Int, end: Int): Head? {
        var i = lineStart
        while (i < end && (text[i] == BOM || text[i] == LRM)) i++
        val bracket = i < end && text[i] == '['
        if (bracket) i++
        val dateStart = i
        val firstEnd = digitRun(text, i, end)
        if (firstEnd - i !in 1..2 || firstEnd >= end || text[firstEnd] !in DATE_SEPARATORS) return null
        val separator = text[firstEnd]
        val secondStart = firstEnd + 1
        val secondEnd = digitRun(text, secondStart, end)
        if (secondEnd - secondStart !in 1..2 || secondEnd >= end || text[secondEnd] != separator) return null
        val yearEnd = digitRun(text, secondEnd + 1, end)
        val yearLength = yearEnd - (secondEnd + 1)
        if (yearLength != 2 && yearLength != 4) return null
        var cursor = yearEnd
        if (cursor < end && text[cursor] == ',') cursor++
        val timeStart = skipGaps(text, cursor, end)
        if (timeStart == cursor) return null
        val hourEnd = digitRun(text, timeStart, end)
        if (hourEnd - timeStart !in 1..2 || hourEnd >= end || text[hourEnd] != ':') return null
        val minuteEnd = digitRun(text, hourEnd + 1, end)
        if (minuteEnd - (hourEnd + 1) != 2) return null
        var timeEnd = minuteEnd
        if (timeEnd < end && text[timeEnd] == ':') {
            val secondsEnd = digitRun(text, timeEnd + 1, end)
            if (secondsEnd - (timeEnd + 1) != 2) return null
            timeEnd = secondsEnd
        }
        val markerStart = skipGaps(text, timeEnd, end)
        val meridiem = markerStart + 1 < end && text[markerStart] in "AaPp" && text[markerStart + 1] in "Mm"
        if (meridiem) timeEnd = markerStart + 2
        val restStart = restStart(text, timeEnd, end, bracket) ?: return null
        return Head(
            dateRaw = text.substring(dateStart, yearEnd),
            timeRaw = text.substring(timeStart, timeEnd),
            restStart = restStart,
            bracket = bracket,
            meridiem = meridiem,
            first = text.substring(dateStart, firstEnd).toInt(),
            second = text.substring(secondStart, secondEnd).toInt(),
        )
    }

    /** Start of the text after the date-time prefix, or null when the prefix is not closed correctly. */
    private fun restStart(text: String, timeEnd: Int, end: Int, bracket: Boolean): Int? {
        if (bracket) {
            return if (timeEnd < end && text[timeEnd] == ']') skipGaps(text, timeEnd + 1, end) else null
        }
        val dash = skipGaps(text, timeEnd, end)
        if (dash == timeEnd || dash >= end || text[dash] != '-') return null
        if (dash + 1 == end) return end
        return if (isGap(text[dash + 1])) skipGaps(text, dash + 1, end) else null
    }

    private const val DATE_SEPARATORS = "/.-"
}
