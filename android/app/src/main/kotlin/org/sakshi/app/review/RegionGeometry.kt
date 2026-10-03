package org.sakshi.app.review

import kotlin.math.min
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A point as a fraction of the upright picture: 0 is the left or top edge and 1 the right or bottom edge. */
data class UnitPoint(val x: Float, val y: Float)

/** A pixel position in the view that shows the picture. */
data class ViewPoint(val x: Float, val y: Float)

/**
 * The frame an OCR line polygon was recorded in, read from the region's `transform_json`: the picture as decoded for
 * recognition, [sampleSize] times smaller than the original on each side and turned upright by [rotationDegrees].
 */
data class RegionFrame(
    val originalWidth: Int,
    val originalHeight: Int,
    val sampleSize: Int,
    val rotationDegrees: Int,
    val exifOrientation: Int,
) {
    private val turned: Boolean get() = rotationDegrees == QUARTER || rotationDegrees == THREE_QUARTERS

    /** Width of the original once upright, in pixels. */
    val uprightWidth: Int get() = if (turned) originalHeight else originalWidth

    /** Height of the original once upright, in pixels. */
    val uprightHeight: Int get() = if (turned) originalWidth else originalHeight

    /** Width of the frame the polygon was recorded in. Decoders round a reduced size up. */
    val frameWidth: Int get() = ceilDiv(uprightWidth, sampleSize)

    /** Height of the frame the polygon was recorded in. */
    val frameHeight: Int get() = ceilDiv(uprightHeight, sampleSize)

    /** The recognition frame turns the picture but never mirrors it, so a mirrored orientation cannot be matched. */
    val mirrored: Boolean get() = exifOrientation in MIRRORED_ORIENTATIONS

    private fun ceilDiv(value: Int, by: Int): Int = (value + by - 1) / by

    companion object {
        private const val QUARTER = 90
        private const val THREE_QUARTERS = 270
        private val ROTATIONS = setOf(0, QUARTER, 180, THREE_QUARTERS)
        private val MIRRORED_ORIENTATIONS = setOf(2, 4, 5, 7)

        internal fun of(originalWidth: Int, originalHeight: Int, sampleSize: Int, rotationDegrees: Int, exifOrientation: Int): RegionFrame? =
            if (originalWidth > 0 && originalHeight > 0 && sampleSize > 0 && rotationDegrees in ROTATIONS) {
                RegionFrame(originalWidth, originalHeight, sampleSize, rotationDegrees, exifOrientation)
            } else {
                null
            }
    }
}

/**
 * Moves OCR line polygons onto the picture as shown. Pure arithmetic: no Android types and nothing is drawn here.
 * A polygon that cannot be placed exactly gives null; a location is never guessed.
 */
object RegionGeometry {
    private val json = Json

    /** More than this fraction of the frame outside its edge means the polygon is not from this frame. */
    private const val EDGE_TOLERANCE = 0.1f
    private const val MIN_POINTS = 3
    private const val MAX_POINTS = 16

    /** The frame in a region's `transform_json`, or null when it is missing, foreign or out of range. */
    fun frameOf(transformJson: String?): RegionFrame? {
        if (transformJson == null) return null
        return try {
            val root = json.parseToJsonElement(transformJson).jsonObject
            if (root["frame"]?.jsonPrimitive?.content != "decoded_for_recognition") return null
            RegionFrame.of(
                root.intOf("original_width") ?: return null,
                root.intOf("original_height") ?: return null,
                root.intOf("sample_size") ?: return null,
                root.intOf("rotation_degrees") ?: return null,
                root.intOf("exif_orientation") ?: return null,
            )
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: IllegalStateException) {
            null
        }
    }

    /** The corners of a region polygon as fractions of the upright picture, or null when they cannot be placed. */
    fun outline(polygonJson: String, transformJson: String?, uprightWidth: Int, uprightHeight: Int): List<UnitPoint>? {
        val frame = frameOf(transformJson) ?: return null
        if (frame.mirrored || frame.uprightWidth != uprightWidth || frame.uprightHeight != uprightHeight) return null
        val corners = cornersOf(polygonJson) ?: return null
        val width = frame.frameWidth.toFloat()
        val height = frame.frameHeight.toFloat()
        val points = corners.map { (x, y) -> UnitPoint(x / width, y / height) }
        val inside = points.all { it.x in -EDGE_TOLERANCE..1f + EDGE_TOLERANCE && it.y in -EDGE_TOLERANCE..1f + EDGE_TOLERANCE }
        return if (inside) points.map { UnitPoint(it.x.coerceIn(0f, 1f), it.y.coerceIn(0f, 1f)) } else null
    }

    /**
     * Places [points] on a picture of [pictureWidth] by [pictureHeight] pixels drawn to fit inside a view of
     * [viewWidth] by [viewHeight] pixels and centred, as `ContentScale.Fit` does, so empty bands beside or above the
     * picture are accounted for.
     */
    fun toView(points: List<UnitPoint>, pictureWidth: Int, pictureHeight: Int, viewWidth: Float, viewHeight: Float): List<ViewPoint> {
        if (pictureWidth <= 0 || pictureHeight <= 0 || viewWidth <= 0f || viewHeight <= 0f) return emptyList()
        val scale = min(viewWidth / pictureWidth, viewHeight / pictureHeight)
        val drawnWidth = pictureWidth * scale
        val drawnHeight = pictureHeight * scale
        val left = (viewWidth - drawnWidth) / 2f
        val top = (viewHeight - drawnHeight) / 2f
        return points.map { ViewPoint(left + it.x * drawnWidth, top + it.y * drawnHeight) }
    }

    private fun JsonObject.intOf(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull

    /** `[[x,y],...]` as stored in `region.polygon_json`, or null for anything else. */
    private fun cornersOf(polygonJson: String): List<Pair<Int, Int>>? = try {
        val array: JsonArray = json.parseToJsonElement(polygonJson).jsonArray
        val corners = array.map { corner ->
            val pair = corner.jsonArray
            if (pair.size != 2) return null
            (pair[0].jsonPrimitive.intOrNull ?: return null) to (pair[1].jsonPrimitive.intOrNull ?: return null)
        }
        corners.takeIf { it.size in MIN_POINTS..MAX_POINTS }
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: IllegalStateException) {
        null
    }
}
