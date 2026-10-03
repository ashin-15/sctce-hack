package org.sakshi.acquisition.projection

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay

/**
 * Configuration for a MediaProjection virtual display.
 */
public data class ProjectionConfig(
    public val width: Int,
    public val height: Int,
    public val densityDpi: Int,
)

/**
 * Interface abstracting frame capture so components can be tested without hardware virtual displays.
 */
public interface FrameSource : AutoCloseable {
    public suspend fun captureNextFrame(): CapturedFrame?
}

/**
 * Live [FrameSource] backed by an Android [MediaProjection], [VirtualDisplay], and [ImageReader].
 */
public class MediaProjectionFrameSource(
    private val mediaProjection: MediaProjection,
    private val config: ProjectionConfig,
    private val token: ProjectionToken,
    private val onOsRevoked: () -> Unit = {},
) : FrameSource {

    private val frameCounter: AtomicInteger = AtomicInteger(0)
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null

    init {
        token.consume()

        mediaProjection.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                    token.markRevoked()
                    close()
                    onOsRevoked()
                }
            },
            Handler(Looper.getMainLooper()),
        )

        imageReader = ImageReader.newInstance(
            config.width,
            config.height,
            PixelFormat.RGBA_8888,
            2,
        )

        virtualDisplay = mediaProjection.createVirtualDisplay(
            "SakshiEvidenceCapture",
            config.width,
            config.height,
            config.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null,
            null,
        )
    }

    override suspend fun captureNextFrame(): CapturedFrame? {
        val reader = imageReader ?: return null
        val image = reader.acquireLatestImage() ?: return null

        return try {
            convertImageToCapturedFrame(image)
        } finally {
            image.close()
        }
    }

    private fun convertImageToCapturedFrame(image: Image): CapturedFrame {
        val width = image.width
        val height = image.height
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width

        val rawBitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            Bitmap.Config.ARGB_8888,
        )
        rawBitmap.copyPixelsFromBuffer(buffer)

        val cleanBitmap = if (rowPadding == 0) {
            rawBitmap
        } else {
            Bitmap.createBitmap(rawBitmap, 0, 0, width, height).also {
                rawBitmap.recycle()
            }
        }

        val frameKind = FrameSampler.detectFrameKind(cleanBitmap)
        val pngBytes = FrameSampler.bitmapToPngBytes(cleanBitmap)
        val sha256 = FrameSampler.sha256Hex(pngBytes)
        cleanBitmap.recycle()

        return CapturedFrame(
            frameIndex = frameCounter.incrementAndGet(),
            timestampMs = System.currentTimeMillis(),
            width = width,
            height = height,
            imageBytes = pngBytes,
            sha256Hex = sha256,
            kind = frameKind,
        )
    }

    override fun close() {
        token.markStopped()
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection.stop()
    }
}

/**
 * Manages an active capture session, supporting both single snapshots and bounded bursts.
 */
public class ProjectionSession(
    private val frameSource: FrameSource,
) : AutoCloseable {

    private var previousHash: Long? = null

    /**
     * Captures a single on-screen snapshot.
     */
    public suspend fun captureSnapshot(): CapturedFrame? {
        return frameSource.captureNextFrame()
    }

    /**
     * Bounded burst capture (e.g. while the user scrolls through a conversation).
     * Automatically deduplicates static frames using perceptual hashing.
     * Stops if [onFrame] returns false, or when [maxFrames] is reached.
     */
    public suspend fun captureBurst(
        maxFrames: Int = 10,
        intervalMs: Long = 500L,
        maxAttempts: Int = maxFrames * 3,
        onFrame: suspend (CapturedFrame) -> Boolean,
    ): Int {
        var processed = 0
        var attempts = 0
        while (processed < maxFrames && attempts < maxAttempts) {
            attempts++
            val frame = frameSource.captureNextFrame() ?: break

            // If it's a normal frame, check perceptual hash to skip static redundant frames
            var shouldProcess = true
            if (frame.kind == FrameKind.NORMAL) {
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(
                    frame.imageBytes, 0, frame.imageBytes.size
                )
                if (bitmap != null) {
                    val hash = FrameSampler.computePerceptualHash(bitmap)
                    shouldProcess = FrameSampler.shouldProcessFrame(hash, previousHash)
                    if (shouldProcess) {
                        previousHash = hash
                    }
                    bitmap.recycle()
                }
            }

            if (shouldProcess) {
                processed++
                val continueCapture = onFrame(frame)
                if (!continueCapture) break
            }
            if (intervalMs > 0L) {
                delay(intervalMs)
            }
        }
        return processed
    }

    override fun close() {
        frameSource.close()
    }
}
