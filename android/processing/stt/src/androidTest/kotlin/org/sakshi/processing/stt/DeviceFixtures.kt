package org.sakshi.processing.stt

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assume.assumeTrue

internal const val DEVICE_MODEL_PATH: String = "/data/local/tmp/ggml-base-q5_1.bin"

internal const val MODEL_PUSH_HINT: String =
    "Model not on the device. From the repository root run: " +
        "adb push models/whisper.cpp/ggml-base-q5_1.bin /data/local/tmp/ggml-base-q5_1.bin"

internal val targetContext: Context
    get() = InstrumentationRegistry.getInstrumentation().targetContext

internal fun shellOutput(command: String): String {
    val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    return ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes().toString(Charsets.UTF_8) }
}

/**
 * Streams the pushed model into the test app's private storage through the shell, so the test APK needs no storage
 * permission, and verifies it on the way. Skips (assumption) when the file was not pushed.
 */
internal fun provisionedModel(): ModelProvisioner {
    val provisioner = ModelProvisioner.forContext(targetContext)
    if (provisioner.isProvisioned()) return provisioner
    assumeTrue(MODEL_PUSH_HINT, shellOutput("ls $DEVICE_MODEL_PATH").trim() == DEVICE_MODEL_PATH)
    val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cat $DEVICE_MODEL_PATH")
    val result = ParcelFileDescriptor.AutoCloseInputStream(descriptor).use(provisioner::importFrom)
    check(result is ProvisionResult.Installed) { "Provisioning the pushed model failed: $result" }
    return provisioner
}

internal fun newManager(provisioner: ModelProvisioner, engine: SpeechEngine = WhisperEngine(), keepLoaded: Boolean = false) =
    ModelSessionManager(ModelSpec.WHISPER_BASE_Q5_1, provisioner.modelFile, engine, keepLoaded = keepLoaded)

internal fun newProcessor(manager: ModelSessionManager) = SttProcessor(manager, ThermalStatusProvider.system(targetContext))

/** A 16-bit mono PCM WAV file in memory. */
internal fun wavBytes(samples: FloatArray, sampleRate: Int): ByteArray {
    val data = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
    samples.forEach { data.putShort((it.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()) }
    val header = ByteBuffer.allocate(WAV_HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
    header.put("RIFF".toByteArray()).putInt(36 + data.capacity()).put("WAVE".toByteArray())
    header.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(sampleRate).putInt(sampleRate * 2)
    header.putShort(2).putShort(16).put("data".toByteArray()).putInt(data.capacity())
    return header.array() + data.array()
}

private const val WAV_HEADER_BYTES: Int = 44

internal fun tone(frequencyHz: Double, seconds: Int, rate: Int = 16_000, amplitude: Double = 0.3): FloatArray =
    FloatArray(seconds * rate) { (amplitude * sin(2.0 * PI * frequencyHz * it / rate)).toFloat() }

/** Counts the bytes a decoder asks for, to show how much of a clip was really read. */
internal class CountingSource(private val inner: RandomAccessSource) : RandomAccessSource {
    var bytesRead: Long = 0
        private set

    override val size: Long
        get() = inner.size

    override fun read(position: Long, destination: ByteArray, offset: Int, length: Int): Int {
        val count = inner.read(position, destination, offset, length)
        if (count > 0) bytesRead += count
        return count
    }
}

/** Word error rate of [hypothesis] against [reference]: edit distance over words, lower-cased, punctuation removed. */
internal fun wordErrorRate(reference: String, hypothesis: String): Double {
    fun words(text: String) = text.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}\\s]"), " ").split(Regex("\\s+")).filter { it.isNotEmpty() }
    val ref = words(reference)
    val hyp = words(hypothesis)
    val previous = IntArray(hyp.size + 1) { it }
    for (i in 1..ref.size) {
        var diagonal = previous[0]
        previous[0] = i
        for (j in 1..hyp.size) {
            val above = previous[j]
            previous[j] = minOf(previous[j] + 1, previous[j - 1] + 1, diagonal + if (ref[i - 1] == hyp[j - 1]) 0 else 1)
            diagonal = above
        }
    }
    return previous[hyp.size].toDouble() / ref.size
}

/**
 * Speech made by the phone's own text-to-speech engine, synthesised into the test app's cache directory (the test
 * sandbox), read back into memory and deleted at once. Returns null when no engine, no offline voice or no English voice
 * is available, in which case the calling test skips. Synthetic speech: not real-world evidence.
 */
internal fun synthesiseEnglish(text: String): ByteArray? {
    val ready = CountDownLatch(1)
    var initStatus = TextToSpeech.ERROR
    val engine = TextToSpeech(targetContext) { status ->
        initStatus = status
        ready.countDown()
    }
    try {
        if (!ready.await(INIT_TIMEOUT_SECONDS, TimeUnit.SECONDS) || initStatus != TextToSpeech.SUCCESS) return null
        if (engine.setLanguage(Locale.US) < TextToSpeech.LANG_AVAILABLE) return null
        if (engine.voice?.isNetworkConnectionRequired == true) return null
        val file = File(targetContext.cacheDir, "tts-fixture.wav")
        val finished = CountDownLatch(1)
        var succeeded = false
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                succeeded = true
                finished.countDown()
            }

            @Deprecated("Required override on API levels before 21")
            override fun onError(utteranceId: String?) {
                finished.countDown()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                finished.countDown()
            }
        })
        val queued = engine.synthesizeToFile(text, Bundle(), file, "fixture")
        if (queued != TextToSpeech.SUCCESS || !finished.await(SYNTH_TIMEOUT_SECONDS, TimeUnit.SECONDS) || !succeeded) {
            file.delete()
            return null
        }
        return file.readBytes().also { file.delete() }.takeIf { it.size > WAV_HEADER_BYTES }
    } finally {
        engine.shutdown()
    }
}

private const val INIT_TIMEOUT_SECONDS: Long = 15
private const val SYNTH_TIMEOUT_SECONDS: Long = 60

/**
 * AAC-LC in ADTS framing, made in memory with the phone's own encoder from mono 16 kHz PCM. ADTS carries no duration, so
 * decoding it exercises the `MediaCodec` path and the in-decode length cap rather than the container-duration refusal.
 */
internal fun aacAdtsBytes(samples: FloatArray, sampleRate: Int = 16_000): ByteArray {
    val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
        setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        setInteger(MediaFormat.KEY_BIT_RATE, 64_000)
    }
    val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    val out = java.io.ByteArrayOutputStream()
    try {
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val info = MediaCodec.BufferInfo()
        var position = 0
        var inputDone = false
        var outputDone = false
        while (!outputDone) {
            if (!inputDone) {
                val index = codec.dequeueInputBuffer(10_000)
                if (index >= 0) {
                    val buffer = checkNotNull(codec.getInputBuffer(index))
                    buffer.clear()
                    val count = minOf(buffer.capacity() / 2, samples.size - position)
                    if (count <= 0) {
                        codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        buffer.order(ByteOrder.nativeOrder())
                        repeat(count) { buffer.putShort((samples[position + it].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()) }
                        codec.queueInputBuffer(index, 0, count * 2, position * 1_000_000L / sampleRate, 0)
                        position += count
                    }
                }
            }
            val outIndex = codec.dequeueOutputBuffer(info, 10_000)
            if (outIndex >= 0) {
                val buffer = checkNotNull(codec.getOutputBuffer(outIndex))
                val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                if (info.size > 0 && !isConfig) {
                    out.write(adtsHeader(info.size, sampleRate))
                    val frame = ByteArray(info.size)
                    buffer.position(info.offset)
                    buffer.get(frame)
                    out.write(frame)
                }
                codec.releaseOutputBuffer(outIndex, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
            }
        }
    } finally {
        codec.release()
    }
    return out.toByteArray()
}

private fun adtsHeader(payloadBytes: Int, sampleRate: Int): ByteArray {
    val frameLength = payloadBytes + ADTS_HEADER_BYTES
    val rateIndex = ADTS_RATES.indexOf(sampleRate)
    require(rateIndex >= 0) { "Unsupported ADTS sample rate $sampleRate" }
    val profile = 1 // AAC-LC minus one
    val channels = 1
    return byteArrayOf(
        0xFF.toByte(),
        0xF1.toByte(),
        ((profile shl 6) or (rateIndex shl 2) or (channels shr 2)).toByte(),
        (((channels and 3) shl 6) or (frameLength shr 11)).toByte(),
        ((frameLength shr 3) and 0xFF).toByte(),
        (((frameLength and 7) shl 5) or 0x1F).toByte(),
        0xFC.toByte(),
    )
}

private const val ADTS_HEADER_BYTES: Int = 7
private val ADTS_RATES: List<Int> = listOf(96_000, 88_200, 64_000, 48_000, 44_100, 32_000, 24_000, 22_050, 16_000, 12_000, 11_025, 8_000)
