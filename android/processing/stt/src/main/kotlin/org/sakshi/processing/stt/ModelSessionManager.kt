package org.sakshi.processing.stt

import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A model that is loaded for one block of work, and what loading it cost (0 when it was already loaded). */
public class LoadedModel(public val model: SpeechModel, public val loadMs: Long)

public sealed interface SessionResult<out T> {
    public class Ran<out T>(public val value: T) : SessionResult<T>

    public data class Unavailable(public val reason: ModelUnavailableReason) : SessionResult<Nothing>

    public data object OutOfMemory : SessionResult<Nothing>
}

/**
 * Owns the one speech model that may be in memory.
 *
 * - The model file is hashed and compared with [ModelSpec.sha256] before every load; on a mismatch nothing is loaded.
 *   The check and the load read the file separately, which is acceptable only because the file lives in app-private
 *   storage that other apps cannot write.
 * - Work runs under [lock], so only one heavy model is resident at a time across everything that shares the lock.
 * - The model is released when the work ends, unless [keepLoaded] is set; then it stays until [onTrimMemory] reports
 *   pressure or [release] is called.
 * - Everything blocking runs on [dispatcher], never on the caller's thread.
 */
public class ModelSessionManager(
    public val spec: ModelSpec,
    private val modelFile: File,
    private val engine: SpeechEngine,
    private val lock: HeavyModelLock = HeavyModelLock.ProcessWide,
    private val keepLoaded: Boolean = false,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
    private val hasher: (File) -> String = Sha256::hexOf,
) {
    private val state = Any()
    private var loaded: SpeechModel? = null
    private var busy = false
    private var releaseWhenIdle = false

    public val engineVersion: String
        get() = engine.version

    /** True while a model is held in memory. */
    public val isLoaded: Boolean
        get() = synchronized(state) { loaded != null }

    /**
     * Loads the model (after verifying it), runs [block] with it, and releases it unless [keepLoaded]. [block] runs on
     * the manager's background dispatcher.
     */
    public suspend fun <T> withModel(block: suspend (LoadedModel) -> T): SessionResult<T> =
        lock.withExclusive {
            withContext(dispatcher) {
                when (val acquired = acquire()) {
                    is Acquired.Refused -> acquired.result
                    is Acquired.Ready -> try {
                        SessionResult.Ran(block(acquired.model))
                    } finally {
                        finish()
                    }
                }
            }
        }

    /** Asks the running transcription, if any, to stop. */
    public fun cancelActive() {
        val model = synchronized(state) { if (busy) loaded else null }
        model?.cancel()
    }

    /** Frees a model that is held between uses. A model in use is freed as soon as its work ends. */
    public fun release() {
        synchronized(state) {
            if (busy) releaseWhenIdle = true else unloadLocked()
        }
    }

    /**
     * Call from `ComponentCallbacks2.onTrimMemory`. At [TRIM_MEMORY_RUNNING_CRITICAL] or worse the model is released
     * (immediately if idle, otherwise when its work ends).
     */
    public fun onTrimMemory(level: Int) {
        if (level >= TRIM_MEMORY_RUNNING_CRITICAL) release()
    }

    private fun acquire(): Acquired {
        synchronized(state) {
            loaded?.let {
                busy = true
                return Acquired.Ready(LoadedModel(it, 0))
            }
        }
        val started = System.nanoTime()
        if (!modelFile.isFile) return Acquired.Refused(SessionResult.Unavailable(ModelUnavailableReason.MISSING))
        val matches = try {
            modelFile.length() == spec.sizeBytes && hasher(modelFile) == spec.sha256
        } catch (e: IOException) {
            return Acquired.Refused(SessionResult.Unavailable(ModelUnavailableReason.LOAD_FAILED))
        }
        if (!matches) return Acquired.Refused(SessionResult.Unavailable(ModelUnavailableReason.HASH_MISMATCH))
        return when (val outcome = engine.load(modelFile.path)) {
            is EngineLoad.Loaded -> {
                synchronized(state) {
                    loaded = outcome.model
                    busy = true
                }
                Acquired.Ready(LoadedModel(outcome.model, (System.nanoTime() - started) / NANOS_PER_MILLI))
            }
            EngineLoad.LibraryMissing ->
                Acquired.Refused(SessionResult.Unavailable(ModelUnavailableReason.NATIVE_LIBRARY_MISSING))
            EngineLoad.OutOfMemory -> Acquired.Refused(SessionResult.OutOfMemory)
            EngineLoad.Failed -> Acquired.Refused(SessionResult.Unavailable(ModelUnavailableReason.LOAD_FAILED))
        }
    }

    private fun finish() {
        synchronized(state) {
            busy = false
            if (!keepLoaded || releaseWhenIdle) unloadLocked()
            releaseWhenIdle = false
        }
    }

    private fun unloadLocked() {
        loaded?.close()
        loaded = null
    }

    private sealed interface Acquired {
        class Ready(val model: LoadedModel) : Acquired

        class Refused(val result: SessionResult<Nothing>) : Acquired
    }

    public companion object {
        /** Value of `ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL`. */
        public const val TRIM_MEMORY_RUNNING_CRITICAL: Int = 15
        private const val NANOS_PER_MILLI: Long = 1_000_000L
    }
}
