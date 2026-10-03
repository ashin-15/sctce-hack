package org.sakshi.processing.ocr

/** A pixel position in the frame described by [OcrFrame]. */
public data class OcrPoint(
    public val x: Int,
    public val y: Int,
)

/** Axis-aligned bounds in the frame described by [OcrFrame]. */
public data class OcrBox(
    public val left: Int,
    public val top: Int,
    public val right: Int,
    public val bottom: Int,
)

/**
 * One line of text as the engine returned it. [confidence] is the engine's own score for the line, not a probability
 * that the text is right and not calibrated by Sakshi; it is null when the engine gave none. [language] is the engine's
 * language tag, or null when it gave none or said undetermined. [blockIndex] groups lines the engine put in one block.
 */
public data class OcrLine(
    public val blockIndex: Int,
    public val text: String,
    public val polygon: List<OcrPoint>,
    public val box: OcrBox,
    public val confidence: Float?,
    public val language: String?,
)

/**
 * The frame that line coordinates refer to: the image as decoded for recognition, [sampleSize] times smaller than the
 * original on each side, and turned by [rotationDegrees] from the EXIF orientation. Mirroring in [exifOrientation]
 * (values 2, 4, 5 and 7) is recorded but not undone.
 */
public data class OcrFrame(
    public val originalWidth: Int,
    public val originalHeight: Int,
    public val sampleSize: Int,
    public val exifOrientation: Int,
    public val rotationDegrees: Int,
)

/** Lines in the engine's reading order. The engine decides the order; Sakshi does not change it. */
public data class OcrResult(
    public val lines: List<OcrLine>,
    public val frame: OcrFrame,
)

/** Why recognition did not produce a result. */
public enum class OcrFailure {
    /** The bytes are not an image this phone can decode. */
    UNDECODABLE,

    /** The decoded image did not fit in memory. */
    OUT_OF_MEMORY,

    /** The recognition engine reported an error. */
    ENGINE_FAILED,
}

public sealed interface OcrOutcome {
    public data class Success(public val result: OcrResult) : OcrOutcome

    /**
     * The engine found no text it can read. This does not mean the image has no text: text in a script the engine
     * does not read (for the Latin engine, Malayalam and Devanagari among others) also ends here.
     */
    public data class NoText(public val frame: OcrFrame) : OcrOutcome

    public data class Failed(public val failure: OcrFailure) : OcrOutcome
}

/** Identity of the recognition engine, recorded on every derivative it produces. */
public data class OcrEngine(public val id: String, public val version: String, public val scripts: Set<String>)

/** `[[x,y],...]` with no spaces, the form stored in `region.polygon_json`. */
public fun polygonToJson(points: List<OcrPoint>): String =
    "[${points.joinToString(",") { "[${it.x},${it.y}]" }}]"
