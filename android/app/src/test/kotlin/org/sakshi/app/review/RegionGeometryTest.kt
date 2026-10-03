package org.sakshi.app.review

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RegionGeometryTest {
    private fun transform(width: Int, height: Int, sample: Int = 1, rotation: Int = 0, orientation: Int = 1, frame: String = "decoded_for_recognition") =
        """{"frame":"$frame","original_width":$width,"original_height":$height,"sample_size":$sample,"exif_orientation":$orientation,"rotation_degrees":$rotation,"mirroring_undone":false}"""

    private val box = "[[20,40],[120,40],[120,60],[20,60]]"

    private fun assertPoints(expected: List<Pair<Float, Float>>, actual: List<UnitPoint>?) {
        val points = assertNotNull(actual)
        assertEquals(expected.size, points.size)
        expected.zip(points).forEach { (want, got) ->
            assertEquals(want.first, got.x, 0.0001f)
            assertEquals(want.second, got.y, 0.0001f)
        }
    }

    @Test
    fun anUnturnedFullSizeFrameDividesByTheOriginalSize() {
        val points = RegionGeometry.outline(box, transform(200, 100), 200, 100)
        assertPoints(listOf(0.1f to 0.4f, 0.6f to 0.4f, 0.6f to 0.6f, 0.1f to 0.6f), points)
    }

    @Test
    fun aReducedFrameIsScaledBackByItsSampleSize() {
        val points = RegionGeometry.outline(box, transform(400, 200, sample = 2), 400, 200)
        assertPoints(listOf(0.1f to 0.4f, 0.6f to 0.4f, 0.6f to 0.6f, 0.1f to 0.6f), points)
    }

    @Test
    fun aReducedFrameRoundsItsSizeUpLikeTheDecoder() {
        val frame = assertNotNull(RegionGeometry.frameOf(transform(201, 101, sample = 2)))
        assertEquals(101, frame.frameWidth)
        assertEquals(51, frame.frameHeight)
    }

    @Test
    fun aQuarterTurnPutsTheFrameOnTheSwappedSides() {
        val frame = assertNotNull(RegionGeometry.frameOf(transform(400, 200, rotation = 90, orientation = 6)))
        assertEquals(200, frame.uprightWidth)
        assertEquals(400, frame.uprightHeight)
        val points = RegionGeometry.outline(box, transform(400, 200, rotation = 90, orientation = 6), 200, 400)
        assertPoints(listOf(0.1f to 0.1f, 0.6f to 0.1f, 0.6f to 0.15f, 0.1f to 0.15f), points)
    }

    @Test
    fun aHalfTurnKeepsTheSidesAndAThreeQuarterTurnSwapsThem() {
        assertPoints(
            listOf(0.1f to 0.4f, 0.6f to 0.4f, 0.6f to 0.6f, 0.1f to 0.6f),
            RegionGeometry.outline(box, transform(200, 100, rotation = 180, orientation = 3), 200, 100),
        )
        assertPoints(
            listOf(0.2f to 0.2f, 1f to 0.2f, 1f to 0.3f, 0.2f to 0.3f),
            RegionGeometry.outline("[[20,40],[100,40],[100,60],[20,60]]", transform(200, 100, rotation = 270, orientation = 8), 100, 200),
        )
        val frame = assertNotNull(RegionGeometry.frameOf(transform(200, 100, rotation = 270, orientation = 8)))
        assertEquals(100, frame.uprightWidth)
        assertEquals(200, frame.uprightHeight)
    }

    @Test
    fun aMirroredOrientationIsNeverPlaced() {
        listOf(2, 4, 5, 7).forEach { orientation ->
            assertNull(RegionGeometry.outline(box, transform(200, 100, orientation = orientation), 200, 100), "orientation $orientation")
        }
    }

    @Test
    fun aPictureOfAnotherSizeThanTheRecordedOriginalIsNeverPlaced() {
        assertNull(RegionGeometry.outline(box, transform(200, 100), 100, 200))
        assertNull(RegionGeometry.outline(box, transform(200, 100), 201, 100))
    }

    @Test
    fun missingOrMalformedRecordsAreNeverPlaced() {
        assertNull(RegionGeometry.outline(box, null, 200, 100))
        assertNull(RegionGeometry.outline(box, "not json", 200, 100))
        assertNull(RegionGeometry.outline(box, transform(200, 100, frame = "other"), 200, 100))
        assertNull(RegionGeometry.outline(box, transform(200, 100, sample = 0), 200, 100))
        assertNull(RegionGeometry.outline(box, transform(200, 100, rotation = 45), 200, 100))
        assertNull(RegionGeometry.outline("", transform(200, 100), 200, 100))
        assertNull(RegionGeometry.outline("[[1,2],[3,4]]", transform(200, 100), 200, 100))
        assertNull(RegionGeometry.outline("[[1,2],[3,4],[5]]", transform(200, 100), 200, 100))
        assertNull(RegionGeometry.outline("[[1,2],[3,4],[5,\"x\"]]", transform(200, 100), 200, 100))
        assertNull(RegionGeometry.outline("{\"a\":1}", transform(200, 100), 200, 100))
    }

    @Test
    fun aPolygonFarOutsideTheFrameIsNeverPlacedButAThinOverhangIsPulledToTheEdge() {
        assertNull(RegionGeometry.outline("[[0,0],[500,0],[500,50]]", transform(200, 100), 200, 100))
        val points = assertNotNull(RegionGeometry.outline("[[-5,0],[205,0],[205,50]]", transform(200, 100), 200, 100))
        assertEquals(listOf(0f, 1f, 1f), points.map { it.x })
    }

    private val corners = listOf(UnitPoint(0f, 0f), UnitPoint(1f, 1f), UnitPoint(0.5f, 0.25f))

    @Test
    fun aViewOfTheSameShapeMapsStraightThrough() {
        val view = RegionGeometry.toView(corners, 200, 100, 200f, 100f)
        assertEquals(listOf(ViewPoint(0f, 0f), ViewPoint(200f, 100f), ViewPoint(100f, 25f)), view)
    }

    @Test
    fun aLargerViewScalesTheOutline() {
        val view = RegionGeometry.toView(corners, 200, 100, 400f, 200f)
        assertEquals(listOf(ViewPoint(0f, 0f), ViewPoint(400f, 200f), ViewPoint(200f, 50f)), view)
    }

    @Test
    fun aTallViewLeavesBandsAboveAndBelowThePicture() {
        val view = RegionGeometry.toView(corners, 200, 100, 200f, 300f)
        assertEquals(listOf(ViewPoint(0f, 100f), ViewPoint(200f, 200f), ViewPoint(100f, 125f)), view)
    }

    @Test
    fun aWideViewLeavesBandsBesideThePicture() {
        val view = RegionGeometry.toView(corners, 200, 100, 400f, 100f)
        assertEquals(listOf(ViewPoint(100f, 0f), ViewPoint(300f, 100f), ViewPoint(200f, 25f)), view)
    }

    @Test
    fun anEmptyViewOrPictureGivesNothing() {
        assertEquals(emptyList(), RegionGeometry.toView(corners, 0, 100, 200f, 100f))
        assertEquals(emptyList(), RegionGeometry.toView(corners, 200, 100, 0f, 100f))
    }
}
