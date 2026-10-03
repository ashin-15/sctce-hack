package org.sakshi.app.review

import org.sakshi.core.database.RegionEntity
import org.sakshi.core.model.Event
import org.sakshi.core.model.Locator

/** The place on the upright picture that one recognised line was read from. [referenceId] is the event's reference to it. */
data class RegionOutline(val referenceId: String, val points: List<UnitPoint>)

/** Chooses which recognised lines to outline: those this event's own references point at. */
object RegionOutlines {
    /**
     * Outlines for the image-region references of [event] that belong to the recognised text [derivativeId], on a picture
     * whose original is [uprightWidth] by [uprightHeight] pixels once upright. A reference to a region that is not stored,
     * belongs to another derivative or cannot be placed exactly is left out.
     */
    fun of(event: Event, derivativeId: String, regions: List<RegionEntity>, uprightWidth: Int, uprightHeight: Int): List<RegionOutline> {
        val own = regions.filter { it.derivativeId == derivativeId }.associateBy { it.id }
        val seen = mutableSetOf<String>()
        return event.evidenceReferences.mapNotNull { reference ->
            val locator = reference.locator as? Locator.ImageOrPageRegion ?: return@mapNotNull null
            if (reference.artifactId.value != derivativeId) return@mapNotNull null
            val region = own[locator.regionId.value] ?: return@mapNotNull null
            if (!seen.add(region.id)) return@mapNotNull null
            val points = RegionGeometry.outline(region.polygonJson, region.transformJson, uprightWidth, uprightHeight)
                ?: return@mapNotNull null
            RegionOutline(reference.referenceId.value, points)
        }
    }
}
