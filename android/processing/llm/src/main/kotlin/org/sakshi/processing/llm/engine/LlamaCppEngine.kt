package org.sakshi.processing.llm.engine

import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One model/context lease. Callers hold [InferenceLock]'s process-wide lock for its full lifetime. */
public class LlamaCppEngine public constructor(
    private val modelPath: String? = null,
    public val modelInfo: ModelInfo? = null,
    private val bridge: NativeLlmRuntime = NativeLlmBridge,
) : LlmEngine {

    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "sakshi-llama-inference").apply { isDaemon = true }
    }
    private var nativeHandle: Long = 0L
    private var _status: LlmStatus = LlmStatus.Unloaded
    @Volatile private var activeRequestId: String? = null
    @Volatile private var activeCompletion: CompletableDeferred<Unit>? = null

    override val status: LlmStatus get() = _status
    public val loadResult: NativeLoadResult

    init {
        if (bridge.isAvailable && !modelPath.isNullOrBlank()) {
            val load = bridge.initModel(modelPath, modelInfo?.contextWindowTokens ?: 2048)
            loadResult = load
            nativeHandle = load.handle
            _status = if (load.isReady) LlmStatus.Ready else LlmStatus.Error
        } else {
            loadResult = NativeLoadResult(0L, NativeLoadResult.STATUS_ERROR)
            _status = LlmStatus.Error
        }
    }

    override suspend fun generate(request: GenerationRequest): GenerationOutcome {
        if (_status != LlmStatus.Ready || nativeHandle == 0L) {
            return GenerationOutcome.Failed(GenerationFailureReason.MODEL_NOT_READY, "Native Qwen runtime is unavailable")
        }
        if (request.maxTokens !in 1..96 || request.systemPrompt.toByteArray(Charsets.UTF_8).size +
            request.userPrompt.toByteArray(Charsets.UTF_8).size > 64 * 1024
        ) {
            return GenerationOutcome.Failed(GenerationFailureReason.CONTEXT_LIMIT_EXCEEDED, "Request exceeds the local task budget")
        }
        val handle = nativeHandle
        val requestId = UUID.randomUUID().toString()
        val nativeCompletion = CompletableDeferred<Unit>()
        activeRequestId = requestId
        activeCompletion = nativeCompletion
        return try {
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation {
                    bridge.cancel(handle, requestId)
                }
                try {
                    worker.execute {
                        var outcome: GenerationOutcome? = null
                        var failure: Throwable? = null
                        try {
                            val result = bridge.generate(
                                handle,
                                request.systemPrompt,
                                request.userPrompt,
                                requestId,
                                request.maxTokens,
                                request.grammar,
                                request.stopSequences.toTypedArray(),
                            )
                            outcome = when (result.statusCode) {
                                NativeGenerationResult.STATUS_OK -> GenerationOutcome.Success(result.text)
                                NativeGenerationResult.STATUS_TRUNCATED -> GenerationOutcome.Failed(
                                    GenerationFailureReason.TRUNCATED,
                                    "Native generation did not complete within the token budget",
                                )
                                NativeGenerationResult.STATUS_CANCELLED -> GenerationOutcome.Failed(
                                    GenerationFailureReason.CANCELLED,
                                    "Native generation was cancelled",
                                )
                                else -> GenerationOutcome.Failed(GenerationFailureReason.ENGINE_ERROR, "Native generation failed")
                            }
                        } catch (thrown: Throwable) {
                            failure = thrown
                        } finally {
                            activeRequestId = null
                            activeCompletion = null
                            nativeCompletion.complete(Unit)
                        }
                        if (continuation.isActive) {
                            val thrown = failure
                            if (thrown == null) continuation.resume(checkNotNull(outcome))
                            else continuation.resumeWithException(thrown)
                        }
                    }
                } catch (_: RejectedExecutionException) {
                    activeRequestId = null
                    activeCompletion = null
                    nativeCompletion.complete(Unit)
                    if (continuation.isActive) {
                        continuation.resume(GenerationOutcome.Failed(GenerationFailureReason.ENGINE_ERROR, "Inference worker is closed"))
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            bridge.cancel(handle, requestId)
            withContext(NonCancellable) { nativeCompletion.await() }
            throw cancelled
        }
    }

    override fun close(): Unit {
        val handle = nativeHandle
        if (handle != 0L) {
            activeRequestId?.let { bridge.cancel(handle, it) }
            awaitActiveNativeCall()
            bridge.freeModel(handle)
            nativeHandle = 0L
        }
        _status = LlmStatus.Unloaded
        worker.shutdown()
    }

    private fun awaitActiveNativeCall() {
        val completion = activeCompletion ?: return
        if (completion.isCompleted) return
        val latch = CountDownLatch(1)
        completion.invokeOnCompletion { latch.countDown() }
        var interrupted = false
        while (true) {
            try {
                latch.await(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
                break
            } catch (_: InterruptedException) {
                interrupted = true
            }
        }
        if (interrupted) Thread.currentThread().interrupt()
    }
}
