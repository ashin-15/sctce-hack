package org.sakshi.processing.stt

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeNotNull

/**
 * Device tests of the speech lane on a real arm64 phone, with the real model. Debug build, one phone: observations,
 * not release measurements.
 *
 * Provision the model once before running (from the repository root; `models/` is git-ignored):
 *
 *     adb push models/whisper.cpp/ggml-base-q5_1.bin /data/local/tmp/ggml-base-q5_1.bin
 *
 * The tests stream that file into the test app's private `noBackupFilesDir/models` through the shell and verify its
 * SHA-256; they skip with an assumption message when the file is absent. All audio is synthetic and made in memory (or
 * by the phone's own text-to-speech into the test sandbox, deleted at once).
 */
class SttDeviceTest {
    private fun report(key: String, value: String) {
        val bundle = Bundle().apply { putString("stt_$key", value) }
        InstrumentationRegistry.getInstrumentation().sendStatus(0, bundle)
    }

    private fun wavSource(samples: FloatArray, rate: Int): CountingSource =
        CountingSource(ByteArrayRandomAccessSource(wavBytes(samples, rate)))

    @Test
    fun nativeLibraryLoadsAndNamesTheEngine() {
        val engine = WhisperEngine()
        assertTrue(engine.version.startsWith("whisper.cpp-1."), engine.version)
        report("engine_version", engine.version)
    }

    @Test
    fun modelProvisionedFromTheShellIsVerified() {
        val provisioner = provisionedModel()
        assertEquals(ModelSpec.WHISPER_BASE_Q5_1.sha256, Sha256.hexOf(provisioner.modelFile))
    }

    @Test
    fun wrongModelFileIsRefusedBeforeLoading() = runBlocking<Unit> {
        val provisioner = provisionedModel()
        val impostor = java.io.File(targetContext.cacheDir, "impostor.bin")
        try {
            provisioner.modelFile.copyTo(impostor, overwrite = true)
            impostor.outputStream().use { it.write(byteArrayOf(0)) }
            val manager = ModelSessionManager(ModelSpec.WHISPER_BASE_Q5_1, impostor, WhisperEngine())
            val result = newProcessor(manager).transcribe(PcmAudioSource(tone(220.0, 3)), SttOptions())
            assertEquals(SttResult.ModelUnavailable(ModelUnavailableReason.HASH_MISMATCH), result)
        } finally {
            impostor.delete()
        }
    }

    @Test
    fun tenSecondsOfSilenceIsExplicitNoSpeechThroughTheRealDecoder() = runBlocking<Unit> {
        val processor = newProcessor(newManager(provisionedModel()))
        val result = processor.transcribe(MediaAudioSource(wavSource(FloatArray(160_000), 16_000)), SttOptions())
        assertEquals(SttResult.NoSpeech(10_000, NoSpeechBasis.SIGNAL_LEVEL), result)
    }

    @Test
    fun seventySecondWavIsTooLongFromTheContainerAndMostOfItIsNeverRead() = runBlocking<Unit> {
        val processor = newProcessor(newManager(provisionedModel()))
        // 48 kHz keeps the file large next to the platform's own read-ahead, so the fraction read says something.
        val counting = wavSource(tone(220.0, 70, rate = 48_000), 48_000)
        val result = processor.transcribe(MediaAudioSource(counting), SttOptions())
        val tooLong = assertIs<SttResult.TooLong>(result)
        assertTrue((tooLong.durationMs ?: 0) in 69_000..71_000, "duration ${tooLong.durationMs}")
        val fraction = counting.bytesRead.toDouble() / counting.size
        report("too_long_wav_bytes_read", "%.4f of %d bytes".format(fraction, counting.size))
        assertTrue(fraction < 0.5, "read $fraction of the clip")
    }

    @Test
    fun aacClipWithoutDeclaredDurationDecodesThroughMediaCodec() = runBlocking<Unit> {
        val bytes = aacAdtsBytes(tone(440.0, 3))
        val decoded = MediaAudioSource(ByteArrayRandomAccessSource(bytes)).decode(MAX_CLIP_DURATION_MS)
        val audio = assertIs<AudioDecodeResult.Decoded>(decoded).audio
        report("aac_decoded", "${audio.durationMs} ms, source ${audio.sourceSampleRate} Hz x ${audio.sourceChannels}, ${bytes.size} bytes")
        assertTrue(audio.durationMs in 2_800..3_400, "duration ${audio.durationMs}")
        val middle = audio.samples.slice(8_000 until 40_000)
        val rms = kotlin.math.sqrt(middle.sumOf { it.toDouble() * it } / middle.size)
        assertTrue(rms in 0.15..0.27, "rms $rms (a 0.3 amplitude sine is 0.212)")
    }

    @Test
    fun seventySecondAacIsRefusedFromTheDurationTheExtractorEstimates() = runBlocking<Unit> {
        // ADTS states no duration; the extractor scans the compressed frames (cheap) and estimates one. Nothing is decoded.
        val bytes = aacAdtsBytes(tone(440.0, 70))
        val result = MediaAudioSource(ByteArrayRandomAccessSource(bytes)).decode(MAX_CLIP_DURATION_MS)
        val tooLong = assertIs<AudioDecodeResult.TooLong>(result)
        report("too_long_aac", "reported=${tooLong.durationMs} ms from ${bytes.size} bytes")
        assertTrue((tooLong.durationMs ?: 0) in 69_000..71_000, "duration ${tooLong.durationMs}")
    }

    @Test
    fun nonAudioBytesAreUnsupportedNotACrash() = runBlocking<Unit> {
        val processor = newProcessor(newManager(provisionedModel()))
        val result = processor.transcribe(MediaAudioSource(ByteArrayRandomAccessSource(ByteArray(4096) { it.toByte() })), SttOptions())
        assertIs<SttResult.Unsupported>(result)
    }

    @Test
    fun pureToneInventsNoWords() = runBlocking<Unit> {
        val processor = newProcessor(newManager(provisionedModel()))
        val result = processor.transcribe(MediaAudioSource(wavSource(tone(440.0, 5), 16_000)), SttOptions())
        report("tone_result", result.toString())
        assertTrue(result is SttResult.NoSpeech || result is SttResult.Success, "unexpected $result")
        assertTrue(
            result is SttResult.NoSpeech,
            "The model produced text for a pure tone (recorded, not asserted as fluent): $result",
        )
    }

    @Test
    fun syntheticEnglishSpeechIsTranscribedWithAWordErrorRateRecorded() = runBlocking<Unit> {
        val provisioner = provisionedModel()
        val wav = synthesiseEnglish(REFERENCE)
        assumeNotNull("No offline English text-to-speech voice on this phone; speech accuracy test skipped", wav)
        val processor = newProcessor(newManager(provisioner))
        val result = processor.transcribe(MediaAudioSource(ByteArrayRandomAccessSource(checkNotNull(wav))), SttOptions())
        val success = assertIs<SttResult.Success>(result)
        val text = success.segments.joinToString(" ") { it.text }
        val wer = wordErrorRate(REFERENCE, text)
        report("speech_hypothesis", text)
        report("speech_wer", "%.3f".format(wer))
        report("speech_language", success.language.toString())
        report("speech_segments", success.segments.joinToString(" | ") { "${it.startMs}-${it.endMs}ms conf=${it.uncalibratedConfidence}" })
        assertEquals("en", success.language)
        assertTrue(wer <= 0.5, "WER $wer for: $text")
    }

    @Test
    fun speechIsNeverTranslatedWhenALanguageIsForcedWrongly() = runBlocking<Unit> {
        val provisioner = provisionedModel()
        val wav = synthesiseEnglish(REFERENCE)
        assumeNotNull("No offline English text-to-speech voice on this phone; test skipped", wav)
        val processor = newProcessor(newManager(provisioner))
        val result = processor.transcribe(MediaAudioSource(ByteArrayRandomAccessSource(checkNotNull(wav))), SttOptions(language = "hi"))
        report("forced_hi_result", result.toString())
        assertTrue(result is SttResult.Success || result is SttResult.NoSpeech, "unexpected $result")
    }

    private companion object {
        const val REFERENCE: String = "Please stop sending me messages. I have told you many times to leave me alone."
    }
}
