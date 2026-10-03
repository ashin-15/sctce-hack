package org.sakshi.acquisition.projection

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FrameSamplerTest {

    @Test
    fun `detects secure content when frame is solid black`() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLACK)

        val kind = FrameSampler.detectFrameKind(bitmap)
        assertEquals(FrameKind.SECURE_CONTENT_DETECTED, kind)
    }

    @Test
    fun `detects normal frame when visible content exists`() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        bitmap.setPixel(50, 50, Color.BLUE)

        val kind = FrameSampler.detectFrameKind(bitmap)
        assertEquals(FrameKind.NORMAL, kind)
    }

    @Test
    fun `computes perceptual hash and hamming distance`() {
        val bitmap1 = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap1.eraseColor(Color.WHITE)

        val bitmap2 = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap2.eraseColor(Color.WHITE)

        val hash1 = FrameSampler.computePerceptualHash(bitmap1)
        val hash2 = FrameSampler.computePerceptualHash(bitmap2)

        assertEquals(0, FrameSampler.hammingDistance(hash1, hash2))
        assertFalse(FrameSampler.shouldProcessFrame(hash2, hash1, threshold = 3))

        // Create a substantially different bitmap
        val bitmap3 = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        for (y in 0 until 64) {
            for (x in 0 until 64) {
                bitmap3.setPixel(x, y, if (x < 32) Color.BLACK else Color.WHITE)
            }
        }
        val hash3 = FrameSampler.computePerceptualHash(bitmap3)
        val distance = FrameSampler.hammingDistance(hash1, hash3)
        assertTrue(distance > 0)
        assertTrue(FrameSampler.shouldProcessFrame(hash3, hash1, threshold = 3))
    }

    @Test
    fun `computes sha256 digest accurately`() {
        val emptyBytes = ByteArray(0)
        val hash = FrameSampler.sha256Hex(emptyBytes)
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hash)
    }
}
