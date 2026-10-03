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
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

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
    private val closed: AtomicBoolean = AtomicBoolean(false)
    private val availableFrames: Channel<Unit> = Channel(Channel.CONFLATED)
    private val stateLock: Any = Any()
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null

    init {
        require(config.width > 0 && config.height > 0 && config.densityDpi > 0) { "Invalid projection dimensions" }
        require(config.width.toLong() * config.height <= 4_000_000L) { "Projection exceeds pixel limit" }
        token.consume()

        mediaProjection.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                    token.markRevoked()
                    if (releaseResources(stopProjection = false)) onOsRevoked()
                }

                override fun onCapturedContentResize(width: Int, height: Int) {
                    if (runCatching { resizeCapture(width, height) }.isFailure) onOsRevoked()
                }
            },
            Handler(Looper.getMainLooper()),
        )

        try {
            imageReader = newImageReader(config.width, config.height)
            virtualDisplay = mediaProjection.createVirtualDisplay(
                "SakshiEvidenceCapture", config.width, config.height, config.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, checkNotNull(imageReader).surface, null, null,
            )
        } catch (failure: Throwable) {
            releaseResources(stopProjection = true)
            throw failure
        }
    }

    override suspend fun captureNextFrame(): CapturedFrame? {
        if (closed.get()) return null
        val signalled = withTimeoutOrNull(FIRST_FRAME_TIMEOUT_MS) { availableFrames.receiveCatching().isSuccess } ?: false
        if (!signalled || closed.get()) return null
        val image = synchronized(stateLock) { imageReader?.acquireLatestImage() } ?: return null

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
        require(pixelStride >= 4 && rowStride >= pixelStride * width) { "Unsupported ImageReader pixel layout" }
        require(width.toLong() * height <= 4_000_000L) { "Captured frame exceeds pixel limit" }
        val rowBytes = Math.multiplyExact(pixelStride, width)
        val requiredBytes = rowStride.toLong() * (height - 1) + rowBytes
        require(requiredBytes <= Int.MAX_VALUE && buffer.remaining().toLong() >= requiredBytes) { "Truncated ImageReader buffer" }
        val row = ByteArray(rowBytes)
        val pixels = IntArray(width)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            for (y in 0 until height) {
                buffer.position(y * rowStride)
                buffer.get(row)
                for (x in 0 until width) {
                    val offset = x * pixelStride
                    val red = row[offset].toInt() and 0xff
                    val green = row[offset + 1].toInt() and 0xff
                    val blue = row[offset + 2].toInt() and 0xff
                    val alpha = row[offset + 3].toInt() and 0xff
                    pixels[x] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
                }
                bitmap.setPixels(pixels, 0, width, 0, y, width, 1)
            }
            val frameKind = FrameSampler.detectFrameKind(bitmap)
            val pngBytes = FrameSampler.bitmapToPngBytes(bitmap)
            require(pngBytes.size <= MAX_FRAME_BYTES) { "Captured frame exceeds encoded byte limit" }
            val sha256 = FrameSampler.sha256Hex(pngBytes)
            return CapturedFrame(
                frameIndex = frameCounter.incrementAndGet(), timestampMs = System.currentTimeMillis(),
                width = width, height = height, imageBytes = pngBytes, sha256Hex = sha256, kind = frameKind,
                elapsedRealtimeMs = SystemClock.elapsedRealtime(),
            )
        } finally {
            bitmap.recycle()
        }
    }

    private fun newImageReader(width: Int, height: Int): ImageReader =
        ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).also { reader ->
            reader.setOnImageAvailableListener({ availableFrames.trySend(Unit) }, Handler(Looper.getMainLooper()))
        }

    private fun resizeCapture(width: Int, height: Int) {
        if (width <= 0 || height <= 0 || width.toLong() * height > 4_000_000L) {
            if (!closed.get()) onOsRevoked()
            return
        }
        if (closed.get()) return
        synchronized(stateLock) {
            if (closed.get()) return
            val replacement = runCatching { newImageReader(width, height) }.getOrNull() ?: return
            val old = imageReader
            try {
                virtualDisplay?.resize(width, height, config.densityDpi)
                virtualDisplay?.setSurface(replacement.surface)
                imageReader = replacement
                old?.setOnImageAvailableListener(null, null)
                old?.close()
            } catch (failure: Exception) {
                replacement.close()
                throw failure
            }
        }
    }

    override fun close() {
        releaseResources(stopProjection = true)
    }

    private fun releaseResources(stopProjection: Boolean): Boolean {
        if (!closed.compareAndSet(false, true)) return false
        token.markStopped()
        availableFrames.close()
        synchronized(stateLock) {
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.setOnImageAvailableListener(null, null)
            imageReader?.close()
            imageReader = null
        }
        if (stopProjection) runCatching { mediaProjection.stop() }
        return true
    }

    private companion object {
        const val FIRST_FRAME_TIMEOUT_MS = 5_000L
        const val MAX_FRAME_BYTES = 16 * 1024 * 1024
    }
}

/**
 * Manages an active capture session, supporting both single snapshots and bounded bursts.
 */
public class ProjectionSession(
    private val frameSource: FrameSource,
) : AutoCloseable {

    /**
     * Captures a single on-screen snapshot.
     */
    public suspend fun captureSnapshot(): CapturedFrame? {
        return frameSource.captureNextFrame()
    }

    /**
     * Bounded burst capture (e.g. while the user scrolls through a conversation).
 * Preserves each sampled frame, including visually similar frames that may contain changed message text.
     * Stops if [onFrame] returns false, or when [maxFrames] is reached.
     */
    public suspend fun captureBurst(
        maxFrames: Int = 10,
        intervalMs: Long = 1_000L,
        maxAttempts: Int = maxFrames * 6,
        onFrame: suspend (CapturedFrame) -> Boolean,
    ): Int {
        require(maxFrames in 1..20) { "Burst frame limit must be between 1 and 20" }
        require(intervalMs >= 1_000L) { "Burst sampling must be at most one frame per second" }
        require(maxAttempts >= maxFrames) { "Attempt budget must cover requested frames" }
        var processed = 0
        var attempts = 0
        while (processed < maxFrames && attempts < maxAttempts) {
            attempts++
            val frame = frameSource.captureNextFrame()
            if (frame == null) {
                if (intervalMs > 0L) delay(intervalMs)
                continue
            }
            processed++
            val continueCapture = onFrame(frame)
            if (!continueCapture) break
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
