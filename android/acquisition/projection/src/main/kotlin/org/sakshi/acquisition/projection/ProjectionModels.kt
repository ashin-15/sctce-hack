package org.sakshi.acquisition.projection

/**
 * Kind of visual frame acquired from MediaProjection.
 */
public enum class FrameKind {
    /** Standard unblocked screen content containing readable visual UI. */
    NORMAL,

    /**
     * Screen content protected by Android OS policy (FLAG_SECURE or private space).
     * The OS blanks the virtual display buffer, resulting in uniform zero luminosity.
     */
    SECURE_CONTENT_DETECTED,

    /** Image buffer was empty or could not be decoded. */
    BLANK,
}

/**
 * Spatial bounding box for on-screen chat bubbles and UI elements.
 */
public data class BubbleBox(
    public val left: Int,
    public val top: Int,
    public val right: Int,
    public val bottom: Int,
) {
    public val centerX: Int get() = (left + right) / 2
    public val centerY: Int get() = (top + bottom) / 2
    public val width: Int get() = right - left
    public val height: Int get() = bottom - top
}

/**
 * Inferred direction of a message bubble based on spatial alignment.
 */
public enum class MessageDirection {
    /** Contact message, typically left-aligned on screen. */
    INCOMING,

    /** User message, typically right-aligned on screen. */
    OUTGOING,

    /** Centered system banner (date separator, encryption notice, call log). */
    SYSTEM_BANNER,
}

/**
 * Structured chat bubble extracted from a captured screen frame.
 */
public data class VisualMessageBubble(
    public val text: String,
    public val direction: MessageDirection,
    public val boundingBox: BubbleBox,
    public val timestampText: String? = null,
    public val confidence: Float? = null,
)

/**
 * High-level chat screen model parsed from OCR bounding boxes and layout geometry.
 */
public data class ParsedChatScreen(
    public val contactName: String?,
    public val headerText: String?,
    public val messages: List<VisualMessageBubble>,
    public val rawLineCount: Int,
    public val isGroupChatEstimate: Boolean,
)

/**
 * Raw visual frame acquired from a MediaProjection virtual display.
 */
public data class CapturedFrame(
    public val frameIndex: Int,
    public val timestampMs: Long,
    public val width: Int,
    public val height: Int,
    public val imageBytes: ByteArray,
    public val sha256Hex: String,
    public val kind: FrameKind,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CapturedFrame) return false
        if (frameIndex != other.frameIndex) return false
        if (timestampMs != other.timestampMs) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (!imageBytes.contentEquals(other.imageBytes)) return false
        if (sha256Hex != other.sha256Hex) return false
        if (kind != other.kind) return false
        return true
    }

    override fun hashCode(): Int {
        var result = frameIndex
        result = 31 * result + timestampMs.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + imageBytes.contentHashCode()
        result = 31 * result + sha256Hex.hashCode()
        result = 31 * result + kind.hashCode()
        return result
    }
}
