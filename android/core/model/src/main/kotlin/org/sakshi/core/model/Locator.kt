package org.sakshi.core.model

import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val CODE_POINT_UNIT: String = "unicode_code_points"

/** Position of evidence inside an artifact; discriminated by the JSON property `kind`. */
@Serializable
public sealed interface Locator {
    @Serializable
    @SerialName("whole_artifact")
    public data object WholeArtifact : Locator

    /** Half-open `[start, end)` range in Unicode code points. */
    @Serializable
    @SerialName("text")
    public data class Text(
        val start: Int,
        val end: Int,
        @Required val unit: String = CODE_POINT_UNIT,
    ) : Locator {
        init {
            require(start >= 0 && end >= 0) { "Offsets must be non-negative" }
            require(unit == CODE_POINT_UNIT) { "Unsupported unit: $unit" }
        }

        public fun toCodePointSpan(): CodePointSpan = CodePointSpan(start, end)
    }

    @Serializable
    @SerialName("audio_time")
    public data class AudioTime(
        @SerialName("start_ms") val startMs: Long,
        @SerialName("end_ms") val endMs: Long,
    ) : Locator {
        init {
            require(startMs >= 0 && endMs >= 0) { "Times must be non-negative" }
        }
    }

    @Serializable
    @SerialName("image_or_page_region")
    public data class ImageOrPageRegion(
        @SerialName("page_index") val pageIndex: Int?,
        @SerialName("region_id") val regionId: ScopeId,
    ) : Locator {
        init {
            require(pageIndex == null || pageIndex >= 0) { "page_index must be non-negative" }
        }
    }
}
