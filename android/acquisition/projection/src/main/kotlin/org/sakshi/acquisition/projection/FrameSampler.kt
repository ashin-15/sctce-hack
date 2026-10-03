package org.sakshi.acquisition.projection

import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * Encodes captured frames and provides optional, non-authoritative visual similarity hints.
 */
public object FrameSampler {

    private const val HASH_GRID_SIZE: Int = 8
    private const val SAMPLE_GRID_SIZE: Int = 16
    /** Uniformly dark frames have an unknown cause and are never treated as proof of protected content. */
    public fun detectFrameKind(bitmap: Bitmap): FrameKind {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return FrameKind.BLANK

        val stepX = (width / SAMPLE_GRID_SIZE).coerceAtLeast(1)
        val stepY = (height / SAMPLE_GRID_SIZE).coerceAtLeast(1)
        for (y in 0 until height step stepY) {
            for (x in 0 until width step stepX) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.alpha(pixel) > 10 && (Color.red(pixel) > 5 || Color.green(pixel) > 5 || Color.blue(pixel) > 5)) {
                    return FrameKind.NORMAL
                }
            }
        }
        return FrameKind.BLANK
    }

    /**
     * Computes a 64-bit perceptual average hash (aHash) for screen change detection.
     */
    public fun computePerceptualHash(bitmap: Bitmap): Long {
        if (bitmap.width <= 0 || bitmap.height <= 0) return 0L

        val scaled = if (bitmap.width == HASH_GRID_SIZE && bitmap.height == HASH_GRID_SIZE) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, HASH_GRID_SIZE, HASH_GRID_SIZE, false)
        }

        val luminances = DoubleArray(64)
        var sum = 0.0

        var idx = 0
        for (y in 0 until HASH_GRID_SIZE) {
            for (x in 0 until HASH_GRID_SIZE) {
                val pixel = scaled.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val lum = 0.299 * r + 0.587 * g + 0.114 * b
                luminances[idx++] = lum
                sum += lum
            }
        }

        if (scaled !== bitmap) {
            scaled.recycle()
        }

        val avg = sum / 64.0
        var hash = 0L
        for (i in 0 until 64) {
            if (luminances[i] >= avg) {
                hash = hash or (1L shl i)
            }
        }
        return hash
    }

    /**
     * Calculates the Hamming distance between two perceptual hashes.
     */
    public fun hammingDistance(hash1: Long, hash2: Long): Int {
        return java.lang.Long.bitCount(hash1 xor hash2)
    }

    /**
     * Returns true if the frame has changed sufficiently to justify running an OCR pass.
     * Prevents battery and CPU exhaustion when the device screen is static.
     */
    public fun shouldProcessFrame(
        currentHash: Long,
        previousHash: Long?,
        threshold: Int = 3,
    ): Boolean {
        if (previousHash == null) return true
        val distance = hammingDistance(currentHash, previousHash)
        return distance >= threshold
    }

    /**
     * Compresses [bitmap] losslessly into PNG bytes.
     */
    public fun bitmapToPngBytes(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    /**
     * Calculates the lowercase hex SHA-256 digest of [bytes].
     */
    public fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
