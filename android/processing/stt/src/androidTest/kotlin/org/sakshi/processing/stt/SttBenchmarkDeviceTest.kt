package org.sakshi.processing.stt

import android.os.Bundle
import android.os.Debug
import android.os.PowerManager
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeNotNull

/**
 * One 60 s clip, one warm-up, one measured run, on the phone the tests run on. DEBUG-BUILD OBSERVATION, not a V-12
 * release measurement: debug app, one device, whatever thermal state it was in (recorded), speech made by text-to-speech
 * and repeated to fill 60 s. The numbers go to the instrumentation status stream (`INSTRUMENTATION_STATUS: stt_...`),
 * not to logcat. Provisioning the model: see [SttDeviceTest].
 */
class SttBenchmarkDeviceTest {
    private class ProbingEngine(private val inner: SpeechEngine, private val samples: MutableMap<String, Long>) : SpeechEngine {
        override val version: String
            get() = inner.version

        override fun load(modelPath: String): EngineLoad {
            val outcome = inner.load(modelPath)
            samples["pss_after_load_kb"] = Debug.getPss()
            return if (outcome is EngineLoad.Loaded) EngineLoad.Loaded(Probing(outcome.model)) else outcome
        }

        private inner class Probing(private val model: SpeechModel) : SpeechModel {
            override fun transcribe(pcm: FloatArray, language: String?, threads: Int): EngineRun {
                val run = model.transcribe(pcm, language, threads)
                samples["pss_after_transcribe_kb"] = Debug.getPss()
                return run
            }

            override fun cancel() = model.cancel()

            override fun close() = model.close()
        }
    }

    private fun report(values: Map<String, String>) {
        val bundle = Bundle()
        values.forEach { (key, value) -> bundle.putString("stt_bench_$key", value) }
        InstrumentationRegistry.getInstrumentation().sendStatus(0, bundle)
    }

    @Test
    fun sixtySecondClipLoadTranscribeUnload() = runBlocking<Unit> {
        val provisioner = provisionedModel()
        val wav = synthesiseEnglish(SENTENCES)
        assumeNotNull("No offline English text-to-speech voice on this phone; benchmark skipped", wav)
        val one = (MediaAudioSource(ByteArrayRandomAccessSource(checkNotNull(wav))).decode(MAX_CLIP_DURATION_MS) as AudioDecodeResult.Decoded).audio
        val target = MODEL_SAMPLE_RATE_HZ * 60
        val clip = FloatArray(target) { one.samples[it % one.samples.size] }
        val source = PcmAudioSource(clip)

        val samples = mutableMapOf<String, Long>()
        val manager = newManager(provisioner, ProbingEngine(WhisperEngine(), samples), keepLoaded = true)
        val processor = newProcessor(manager)
        val power = targetContext.getSystemService(PowerManager::class.java)
        val thermalBefore = power.currentThermalStatus

        val pssBaseline = Debug.getPss()
        val warmUp = processor.transcribe(source, SttOptions())
        assertTrue(warmUp is SttResult.Success || warmUp is SttResult.NoSpeech, "warm-up gave $warmUp")
        manager.release()
        Runtime.getRuntime().gc()
        Thread.sleep(SETTLE_MS)

        samples.clear()
        val pssBeforeLoad = Debug.getPss()
        val result = processor.transcribe(source, SttOptions())
        val success = assertIs<SttResult.Success>(result)
        val loadedWhileHeld = manager.isLoaded
        manager.release()
        Thread.sleep(SETTLE_MS)
        val pssAfterUnload = Debug.getPss()
        val timings = success.timings

        report(
            linkedMapOf(
                "build" to "debug (observation, not a V-12 release measurement)",
                "clip" to "synthetic TTS sentence repeated to 60 s, 16 kHz mono",
                "engine" to success.engineVersion,
                "model" to success.modelId,
                "threads" to SttOptions().threads.toString(),
                "audio_ms" to success.audioDurationMs.toString(),
                "model_load_ms_including_sha256" to timings.modelLoadMs.toString(),
                "decode_ms" to timings.decodeMs.toString(),
                "transcribe_ms" to timings.inferenceMs.toString(),
                "total_ms" to timings.totalMs.toString(),
                "real_time_factor" to "%.3f".format(timings.inferenceMs.toDouble() / success.audioDurationMs),
                "pss_baseline_before_warm_up_kb" to pssBaseline.toString(),
                "pss_before_load_kb" to pssBeforeLoad.toString(),
                "pss_after_load_kb" to samples["pss_after_load_kb"].toString(),
                "pss_after_transcribe_kb" to samples["pss_after_transcribe_kb"].toString(),
                "pss_after_unload_kb" to pssAfterUnload.toString(),
                "model_held_between_runs_when_keepLoaded" to loadedWhileHeld.toString(),
                "model_loaded_after_release" to manager.isLoaded.toString(),
                "segments" to success.segments.size.toString(),
                "language" to success.language.toString(),
                "thermal_status_before" to thermalBefore.toString(),
                "thermal_status_after" to power.currentThermalStatus.toString(),
            ),
        )
        assertTrue(!manager.isLoaded)
    }

    private companion object {
        const val SETTLE_MS: Long = 500
        const val SENTENCES: String = "I told you to stop. Please stop sending me messages. I have told you many times to leave me alone."
    }
}
