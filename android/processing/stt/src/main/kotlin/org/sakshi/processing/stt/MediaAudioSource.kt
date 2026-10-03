package org.sakshi.processing.stt

import android.media.MediaCodec
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Decodes audio evidence with [MediaExtractor] and [MediaCodec] reading through a [RandomAccessSource], so decrypted
 * bytes stay in memory. The decoded PCM is bounded by the clip cap: it is mixed to mono as it is produced and decoding
 * stops as soon as the cap is passed.
 */
public class MediaAudioSource(private val source: RandomAccessSource) : AudioSource {
    override suspend fun containerDurationMs(): Long? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(RandomAccessMediaDataSource(source))
            val format = firstAudioTrack(extractor)?.let(extractor::getTrackFormat) ?: return null
            return if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION) / MICROS_PER_MILLI
            } else {
                null
            }
        } catch (e: IOException) {
            return null
        } catch (e: RuntimeException) {
            return null
        } finally {
            extractor.release()
        }
    }

    override suspend fun decode(maxDurationMs: Long): AudioDecodeResult {
        val extractor = MediaExtractor()
        val dataSource: MediaDataSource = RandomAccessMediaDataSource(source)
        try {
            try {
                extractor.setDataSource(dataSource)
            } catch (e: IOException) {
                return AudioDecodeResult.Unsupported(UnsupportedReason.UNREADABLE)
            } catch (e: RuntimeException) {
                return AudioDecodeResult.Unsupported(UnsupportedReason.UNREADABLE)
            }
            val track = firstAudioTrack(extractor) ?: return AudioDecodeResult.Unsupported(UnsupportedReason.NO_AUDIO_TRACK)
            val format = extractor.getTrackFormat(track)
            if (format.containsKey(MediaFormat.KEY_DURATION)) {
                val declaredMs = format.getLong(MediaFormat.KEY_DURATION) / MICROS_PER_MILLI
                if (declaredMs > maxDurationMs + AudioSource.DURATION_TOLERANCE_MS) return AudioDecodeResult.TooLong(declaredMs)
            }
            extractor.selectTrack(track)
            return Pipeline(extractor, format, maxDurationMs).run()
        } catch (e: OutOfMemoryError) {
            return AudioDecodeResult.OutOfMemory
        } finally {
            extractor.release()
            dataSource.close()
        }
    }

    private fun firstAudioTrack(extractor: MediaExtractor): Int? =
        (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith(AUDIO_MIME_PREFIX) == true
        }

    /** One decode run: extractor samples in, mono floats out at the container's own sample rate, then resampled once. */
    private class Pipeline(
        private val extractor: MediaExtractor,
        private val inputFormat: MediaFormat,
        private val maxDurationMs: Long,
    ) {
        private var sampleRate = inputFormat.intOrDefault(MediaFormat.KEY_SAMPLE_RATE, 0)
        private var channels = inputFormat.intOrDefault(MediaFormat.KEY_CHANNEL_COUNT, 0)
        private var encoding = inputFormat.intOrDefault(MediaFormat.KEY_PCM_ENCODING, PCM_16BIT)
        private var mono = FloatArray(INITIAL_CAPACITY)
        private var length = 0
        private var sourceChannels = channels
        private var tooLong = false

        suspend fun run(): AudioDecodeResult {
            val mime = inputFormat.getString(MediaFormat.KEY_MIME).orEmpty()
            val outcome = if (mime == MIME_RAW) readRaw() else decodeWithCodec(mime)
            if (outcome != null) return outcome
            if (tooLong) return AudioDecodeResult.TooLong(durationSoFarMs())
            if (sampleRate <= 0 || sourceChannels <= 0) return AudioDecodeResult.Failed
            val samples = Resampler.resample(mono.copyOf(length), sampleRate, MODEL_SAMPLE_RATE_HZ)
            return AudioDecodeResult.Decoded(DecodedAudio(samples, sampleRate, sourceChannels))
        }

        private suspend fun readRaw(): AudioDecodeResult? {
            if (encoding != PCM_16BIT && encoding != PCM_FLOAT) return AudioDecodeResult.Unsupported(UnsupportedReason.PCM_FORMAT)
            val buffer = ByteBuffer.allocate(RAW_READ_BYTES).order(ByteOrder.nativeOrder())
            while (!tooLong) {
                coroutineContext.ensureActive()
                buffer.clear()
                val read = extractor.readSampleData(buffer, 0)
                if (read < 0) break
                buffer.limit(read)
                append(buffer)
                extractor.advance()
            }
            return null
        }

        private suspend fun decodeWithCodec(mime: String): AudioDecodeResult? {
            val codec = try {
                MediaCodec.createDecoderByType(mime).also { it.configure(inputFormat, null, null, 0) }
            } catch (e: IOException) {
                return AudioDecodeResult.Unsupported(UnsupportedReason.CODEC_UNAVAILABLE)
            } catch (e: IllegalArgumentException) {
                return AudioDecodeResult.Unsupported(UnsupportedReason.CODEC_UNAVAILABLE)
            } catch (e: MediaCodec.CodecException) {
                return AudioDecodeResult.Unsupported(UnsupportedReason.CODEC_UNAVAILABLE)
            }
            try {
                codec.start()
                return pump(codec)
            } catch (e: MediaCodec.CodecException) {
                return AudioDecodeResult.Failed
            } catch (e: IllegalStateException) {
                return AudioDecodeResult.Failed
            } finally {
                codec.release()
            }
        }

        private suspend fun pump(codec: MediaCodec): AudioDecodeResult? {
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var idleRounds = 0
            while (!outputDone && !tooLong) {
                coroutineContext.ensureActive()
                var progressed = false
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (index >= 0) {
                        progressed = true
                        val input = checkNotNull(codec.getInputBuffer(index))
                        val read = extractor.readSampleData(input, 0)
                        if (read < 0) {
                            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(index, 0, read, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val out = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    out >= 0 -> {
                        progressed = true
                        val buffer = codec.getOutputBuffer(out)
                        if (buffer != null && info.size > 0) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            append(buffer.order(ByteOrder.nativeOrder()))
                        }
                        codec.releaseOutputBuffer(out, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                    out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        progressed = true
                        adoptOutputFormat(codec.outputFormat)
                    }
                    else -> Unit
                }
                idleRounds = if (progressed) 0 else idleRounds + 1
                if (idleRounds > MAX_IDLE_ROUNDS) return AudioDecodeResult.Failed
            }
            return if (encoding == PCM_16BIT || encoding == PCM_FLOAT) null else AudioDecodeResult.Unsupported(UnsupportedReason.PCM_FORMAT)
        }

        private fun adoptOutputFormat(format: MediaFormat) {
            sampleRate = format.intOrDefault(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
            channels = format.intOrDefault(MediaFormat.KEY_CHANNEL_COUNT, channels)
            encoding = format.intOrDefault(MediaFormat.KEY_PCM_ENCODING, PCM_16BIT)
            sourceChannels = channels
        }

        /** Converts interleaved PCM in [buffer] to mono floats and appends it, flagging [tooLong] once past the cap. */
        private fun append(buffer: ByteBuffer) {
            if (sampleRate <= 0 || channels <= 0) return
            val allowedFrames = (maxDurationMs + AudioSource.DURATION_TOLERANCE_MS) * sampleRate / MILLIS_PER_SECOND
            if (encoding == PCM_FLOAT) {
                val floats = buffer.asFloatBuffer()
                while (floats.remaining() >= channels && !tooLong) {
                    var sum = 0f
                    repeat(channels) { sum += floats.get() }
                    push(sum / channels, allowedFrames)
                }
            } else {
                val shorts = buffer.asShortBuffer()
                while (shorts.remaining() >= channels && !tooLong) {
                    var sum = 0f
                    repeat(channels) { sum += shorts.get() / SHORT_SCALE }
                    push(sum / channels, allowedFrames)
                }
            }
        }

        private fun push(value: Float, allowedFrames: Long) {
            if (length >= allowedFrames) {
                tooLong = true
                return
            }
            if (length == mono.size) mono = mono.copyOf(minOf(mono.size.toLong() * 2, allowedFrames + 1).toInt())
            mono[length++] = value
        }

        private fun durationSoFarMs(): Long? = if (sampleRate > 0) length * MILLIS_PER_SECOND / sampleRate else null
    }

    private companion object {
        const val AUDIO_MIME_PREFIX: String = "audio/"
        const val MIME_RAW: String = "audio/raw"
        const val MICROS_PER_MILLI: Long = 1000L
        const val MILLIS_PER_SECOND: Long = 1000L
        const val TIMEOUT_US: Long = 10_000L
        const val MAX_IDLE_ROUNDS: Int = 500
        const val INITIAL_CAPACITY: Int = 1 shl 16
        const val RAW_READ_BYTES: Int = 1 shl 16
        const val SHORT_SCALE: Float = 32768f
        const val PCM_16BIT: Int = android.media.AudioFormat.ENCODING_PCM_16BIT
        const val PCM_FLOAT: Int = android.media.AudioFormat.ENCODING_PCM_FLOAT
    }
}

private fun MediaFormat.intOrDefault(key: String, default: Int): Int = if (containsKey(key)) getInteger(key) else default
