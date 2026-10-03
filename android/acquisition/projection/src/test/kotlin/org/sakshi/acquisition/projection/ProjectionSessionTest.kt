package org.sakshi.acquisition.projection

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProjectionSessionTest {

    private fun createSyntheticFrame(index: Int, isBlack: Boolean = false): CapturedFrame {
        val bitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        if (isBlack) {
            bitmap.eraseColor(Color.BLACK)
        } else {
            bitmap.eraseColor(Color.WHITE)
            val quadrant = (index - 1) % 4
            val startX = if (quadrant % 2 == 1) 25 else 0
            val startY = if (quadrant >= 2) 25 else 0
            for (y in startY until startY + 25) {
                for (x in startX until startX + 25) {
                    bitmap.setPixel(x, y, Color.BLACK)
                }
            }
        }
        val bytes = FrameSampler.bitmapToPngBytes(bitmap)
        val hash = FrameSampler.sha256Hex(bytes)
        val kind = FrameSampler.detectFrameKind(bitmap)
        bitmap.recycle()

        return CapturedFrame(
            frameIndex = index,
            timestampMs = 1000L * index,
            width = 50,
            height = 50,
            imageBytes = bytes,
            sha256Hex = hash,
            kind = kind,
        )
    }

    private class FakeFrameSource(private val frames: List<CapturedFrame?>) : FrameSource {
        private var idx = 0
        var closed = false

        override suspend fun captureNextFrame(): CapturedFrame? {
            if (idx >= frames.size) return null
            return frames[idx++]
        }

        override fun close() {
            closed = true
        }
    }

    @Test
    fun `captureSnapshot returns next available frame`() = runTest {
        val frame1 = createSyntheticFrame(1)
        val source = FakeFrameSource(listOf(frame1))
        val session = ProjectionSession(source)

        val snapshot = session.captureSnapshot()
        assertNotNull(snapshot)
        assertEquals(1, snapshot.frameIndex)

        session.close()
        assert(source.closed)
    }

    @Test
    fun `captureBurst obeys maxFrames and early exit`() = runTest {
        val frames = (1..10).map { createSyntheticFrame(it) }
        val source = FakeFrameSource(frames)
        val session = ProjectionSession(source)

        val collected = mutableListOf<CapturedFrame>()
        val count = session.captureBurst(maxFrames = 3, intervalMs = 1_000L) { frame ->
            collected.add(frame)
            true
        }

        assertEquals(3, count)
        assertEquals(3, collected.size)
        assertEquals(1, collected[0].frameIndex)
        assertEquals(2, collected[1].frameIndex)
        assertEquals(3, collected[2].frameIndex)
    }

    @Test
    fun `captureBurst exits gracefully when frame source is exhausted`() = runTest {
        val frames = listOf(createSyntheticFrame(1), createSyntheticFrame(2))
        val source = FakeFrameSource(frames)
        val session = ProjectionSession(source)

        val collected = mutableListOf<CapturedFrame>()
        val count = session.captureBurst(maxFrames = 10, intervalMs = 1_000L) { frame ->
            collected.add(frame)
            true
        }

        assertEquals(2, count)
        assertEquals(2, collected.size)
    }
}
