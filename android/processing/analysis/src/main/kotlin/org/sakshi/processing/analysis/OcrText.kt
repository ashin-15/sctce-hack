package org.sakshi.processing.analysis

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.sakshi.core.vault.RegionDraft
import org.sakshi.processing.ocr.OcrDocument
import org.sakshi.processing.ocr.OcrEngine
import org.sakshi.processing.ocr.OcrResult
import org.sakshi.processing.ocr.polygonToJson

/** A stored OCR derivative: its id, exact text and the source map that ties text ranges to image regions. */
public class OcrDerivative(public val id: String, public val text: String, public val sourceMapJson: String?)

/** Everything one OCR derivative row and its region rows hold, ready to store. */
public class OcrDraft(
    public val text: String,
    public val toolId: String,
    public val toolVersion: String,
    public val regions: List<RegionDraft>,
    public val sourceMapJson: String,
    public val qualityJson: String,
)

/**
 * One recognised line as stored: region id and its code-point range `[start, end)` in the derivative text.
 * [confidence] is the engine's uncalibrated line score, or null when it gave none.
 */
internal class PlacedRegion(val id: String, val start: Int, val end: Int, val confidence: Float?)

/** Text read from one image, with the regions its lines came from. */
internal class ImageText(val derivative: DerivativeText, val regions: List<PlacedRegion>)

/**
 * Builds and reads the OCR derivative format. Text is kept exactly as recognised; the source map lists, in the
 * engine's order, which region each line came from and where it sits in the text. Region coordinates are pixels of
 * the frame described in each region's transform.
 */
internal object OcrRecord {
    private const val VERSION: Int = 1
    private val json = Json { prettyPrint = false }

    fun draft(result: OcrResult, engine: OcrEngine, ids: () -> String, minLineConfidence: Float): OcrDraft {
        val document = OcrDocument.of(result)
        val frame = result.frame
        val transform = buildJsonObject {
            put("frame", "decoded_for_recognition")
            put("original_width", frame.originalWidth)
            put("original_height", frame.originalHeight)
            put("sample_size", frame.sampleSize)
            put("exif_orientation", frame.exifOrientation)
            put("rotation_degrees", frame.rotationDegrees)
            put("mirroring_undone", false)
        }.toString()
        val placed = document.lines.map { it to ids() }
        val regions = placed.map { (line, id) -> RegionDraft(id, 0, polygonToJson(line.line.polygon), transform) }
        val sourceMap = buildJsonObject {
            put("version", VERSION)
            put("unit", "code_point")
            put("order", "engine")
            put(
                "regions",
                buildJsonArray {
                    placed.forEach { (line, id) ->
                        add(
                            buildJsonObject {
                                put("region_id", id)
                                put("start", line.start)
                                put("end", line.end)
                                put("block", line.line.blockIndex)
                                put("confidence", line.line.confidence?.let(::JsonPrimitive) ?: JsonNull)
                                put("language", line.line.language?.let(::JsonPrimitive) ?: JsonNull)
                            },
                        )
                    }
                },
            )
        }
        val confidences = document.lines.map { it.line.confidence }
        val quality = buildJsonObject {
            put("engine", engine.id)
            put("scripts_read", JsonArray(engine.scripts.sorted().map(::JsonPrimitive)))
            put("line_count", confidences.size)
            put("low_confidence_lines", confidences.count { isLow(it, minLineConfidence) })
            put("min_line_confidence", minLineConfidence)
            put("confidence_basis", "engine_line_score_uncalibrated")
        }
        return OcrDraft(document.text, engine.id, engine.version, regions, json.encodeToString(JsonObject.serializer(), sourceMap), quality.toString())
    }

    /** The regions of a stored derivative, or null when its source map is missing, foreign or out of range. */
    fun read(stored: OcrDerivative): ImageText? {
        val map = stored.sourceMapJson ?: return null
        return try {
            val root = json.parseToJsonElement(map).jsonObject
            if (root["version"]?.jsonPrimitive?.intOrNull != VERSION) return null
            val length = stored.text.codePointCount(0, stored.text.length)
            val regions = root.getValue("regions").jsonArray.map { element ->
                val entry = element.jsonObject
                PlacedRegion(
                    id = entry.getValue("region_id").jsonPrimitive.content,
                    start = checkNotNull(entry.getValue("start").jsonPrimitive.intOrNull),
                    end = checkNotNull(entry.getValue("end").jsonPrimitive.intOrNull),
                    confidence = (entry["confidence"] as? JsonPrimitive)?.floatOrNull,
                )
            }
            if (regions.any { it.start < 0 || it.start > it.end || it.end > length }) return null
            ImageText(DerivativeText(stored.id, stored.text), regions)
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: IllegalStateException) {
            null
        } catch (_: NoSuchElementException) {
            null
        }
    }

    fun isLow(confidence: Float?, minLineConfidence: Float): Boolean = confidence == null || confidence < minLineConfidence
}
