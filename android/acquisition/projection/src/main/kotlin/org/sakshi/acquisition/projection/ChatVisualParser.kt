package org.sakshi.acquisition.projection

import org.sakshi.processing.ocr.OcrLine
import org.sakshi.processing.ocr.OcrResult

/**
 * Parses OCR lines into structured conversation messages using spatial layout heuristics
 * inspired by AgentHita's messaging analysis, but operating directly on visual bounding boxes
 * rather than brittle view hierarchy node IDs.
 */
public object ChatVisualParser {

    private val TIMESTAMP_REGEX: Regex = Regex(
        """(?:\b|(?<=\s))([0-1]?[0-9]|2[0-3]):([0-5][0-9])\s*(?:AM|PM|am|pm)?\b"""
    )

    private val DATE_SEPARATOR_REGEX: Regex = Regex(
        """^(?:today|yesterday|monday|tuesday|wednesday|thursday|friday|saturday|sunday|\d{1,2}\s+(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*|\w+\s+\d{1,2}(?:,\s*\d{4})?)$""",
        RegexOption.IGNORE_CASE
    )

    private val UI_CHROME_EXACT: Set<String> = setOf(
        "type a message",
        "message",
        "send",
        "search",
        "chats",
        "status",
        "calls",
        "updates",
        "online",
        "typing...",
        "last seen recently",
    )

    private val UI_CHROME_PREFIXES: List<String> = listOf(
        "last seen ",
        "tap here for ",
        "missed voice call",
        "missed video call",
        "ongoing call",
    )

    /**
     * Parses the recognized text lines of an [OcrResult] into a structured [ParsedChatScreen].
     */
    public fun parse(result: OcrResult, frameWidth: Int, frameHeight: Int): ParsedChatScreen {
        val lines = result.lines
        if (lines.isEmpty()) {
            return ParsedChatScreen(
                contactName = null,
                headerText = null,
                messages = emptyList(),
                rawLineCount = 0,
                isGroupChatEstimate = false,
            )
        }

        val headerThresholdY = (frameHeight * 0.12).toInt()
        val footerThresholdY = (frameHeight * 0.90).toInt()

        val headerLines = mutableListOf<OcrLine>()
        val conversationLines = mutableListOf<OcrLine>()

        for (line in lines) {
            val trimmed = line.text.trim()
            if (trimmed.isEmpty()) continue

            if (line.box.bottom <= headerThresholdY) {
                headerLines.add(line)
            } else if (line.box.top >= footerThresholdY && isFooterChrome(trimmed)) {
                // Ignore compose box, camera button, mic button in footer zone
                continue
            } else if (isUIChrome(trimmed)) {
                // Ignore transient UI chrome
                continue
            } else {
                conversationLines.add(line)
            }
        }

        val (contactName, isGroup) = extractHeaderMetadata(headerLines)
        val fullHeaderText = headerLines.joinToString(" ") { it.text.trim() }.takeIf { it.isNotBlank() }

        val bubbles = clusterAndClassifyBubbles(conversationLines, frameWidth)

        return ParsedChatScreen(
            contactName = contactName,
            headerText = fullHeaderText,
            messages = bubbles,
            rawLineCount = lines.size,
            isGroupChatEstimate = isGroup,
        )
    }

    /**
     * Determines whether a given text is generic UI chrome rather than conversation content.
     */
    public fun isUIChrome(text: String): Boolean {
        val lower = text.lowercase().trim()
        if (lower in UI_CHROME_EXACT) return true
        if (UI_CHROME_PREFIXES.any { lower.startsWith(it) }) return true
        return false
    }

    private fun isFooterChrome(text: String): Boolean {
        val lower = text.lowercase().trim()
        return lower == "type a message" || lower == "message" || lower == "send" || lower == "chat"
    }

    private fun extractHeaderMetadata(headerLines: List<OcrLine>): Pair<String?, Boolean> {
        if (headerLines.isEmpty()) return Pair(null, false)

        var candidateName: String? = null
        var isGroup = false

        for (line in headerLines) {
            val text = line.text.trim()
            val lower = text.lowercase()

            if (lower.contains("member") || lower.contains("participants") || lower.contains("group")) {
                isGroup = true
            }

            // Exclude common status bar text like system clock ("12:30", "100%")
            if (candidateName == null && !text.matches(Regex("""\d{1,2}:\d{2}""")) && !lower.contains("%") && text.length >= 2) {
                candidateName = text
            }
        }

        return Pair(candidateName, isGroup)
    }

    private fun clusterAndClassifyBubbles(
        lines: List<OcrLine>,
        frameWidth: Int,
    ): List<VisualMessageBubble> {
        if (lines.isEmpty()) return emptyList()

        val bubbles = mutableListOf<VisualMessageBubble>()

        // Group lines by blockIndex or spatial proximity
        val blocks = lines.groupBy { it.blockIndex }

        for ((_, blockLines) in blocks) {
            if (blockLines.isEmpty()) continue

            val minLeft = blockLines.minOf { it.box.left }
            val minTop = blockLines.minOf { it.box.top }
            val maxRight = blockLines.maxOf { it.box.right }
            val maxBottom = blockLines.maxOf { it.box.bottom }

            val bubbleBox = BubbleBox(minLeft, minTop, maxRight, maxBottom)
            val combinedText = blockLines.joinToString("\n") { it.text.trim() }

            // Extract trailing timestamp if present
            var timestamp: String? = null
            var cleanText = combinedText

            val tsMatch = TIMESTAMP_REGEX.findAll(combinedText).lastOrNull()
            if (tsMatch != null && tsMatch.range.last >= combinedText.length - 15) {
                timestamp = tsMatch.value.trim()
                cleanText = (combinedText.substring(0, tsMatch.range.first) +
                    combinedText.substring(tsMatch.range.last + 1)).trim()
            }

            // Determine direction by horizontal center relative to screen width
            val centerX = bubbleBox.centerX
            val leftMarginRatio = centerX.toFloat() / frameWidth.toFloat()

            val direction = when {
                DATE_SEPARATOR_REGEX.matches(combinedText.trim()) -> MessageDirection.SYSTEM_BANNER
                combinedText.contains("end-to-end encrypted", ignoreCase = true) -> MessageDirection.SYSTEM_BANNER
                leftMarginRatio > 0.55f -> MessageDirection.OUTGOING
                leftMarginRatio < 0.45f -> MessageDirection.INCOMING
                else -> MessageDirection.SYSTEM_BANNER
            }

            // Calculate mean confidence
            val confidences = blockLines.mapNotNull { it.confidence }
            val avgConfidence = if (confidences.isNotEmpty()) confidences.average().toFloat() else null

            bubbles.add(
                VisualMessageBubble(
                    text = cleanText.ifEmpty { combinedText },
                    direction = direction,
                    boundingBox = bubbleBox,
                    timestampText = timestamp,
                    confidence = avgConfidence,
                )
            )
        }

        return bubbles.sortedBy { it.boundingBox.top }
    }
}
