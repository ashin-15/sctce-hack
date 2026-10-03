package org.sakshi.processing.llm.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

public class LlamaCppEngine public constructor(
    private val modelPath: String? = null,
    public val modelInfo: ModelInfo? = null,
    private val bridge: NativeLlmBridge = NativeLlmBridge,
    private val inferenceLock: InferenceLock = InferenceLock(),
) : LlmEngine {

    private var nativeHandle: Long = 0L
    private var _status: LlmStatus = LlmStatus.Unloaded

    override val status: LlmStatus
        get() = _status

    init {
        if (!bridge.isAvailable || modelPath.isNullOrBlank()) {
            _status = LlmStatus.Unloaded
        } else {
            val contextSize = modelInfo?.contextWindowTokens ?: 2048
            nativeHandle = bridge.initModel(modelPath, contextSize)
            _status = if (nativeHandle != 0L) {
                LlmStatus.Ready
            } else {
                LlmStatus.Error
            }
        }
    }

    override suspend fun generate(request: GenerationRequest): GenerationOutcome {
        if (!bridge.isAvailable || _status != LlmStatus.Ready || nativeHandle == 0L) {
            return GenerationOutcome.Failed(
                reason = GenerationFailureReason.MODEL_NOT_READY,
                message = "Native LLM engine is not ready or library is not available",
            )
        }

        return try {
            currentCoroutineContext().ensureActive()
            inferenceLock.withLock {
                currentCoroutineContext().ensureActive()

                val contextLimit = modelInfo?.contextWindowTokens ?: 2048
                val combinedPrompt = "${request.systemPrompt}\n${request.userPrompt}"
                if (combinedPrompt.length / 3 > contextLimit) {
                    return@withLock GenerationOutcome.Failed(
                        reason = GenerationFailureReason.CONTEXT_LIMIT_EXCEEDED,
                        message = "Prompt exceeds context window limit",
                    )
                }

                val stopArray = request.stopSequences.toTypedArray()
                val result = bridge.generate(
                    handle = nativeHandle,
                    prompt = combinedPrompt,
                    maxTokens = request.maxTokens,
                    temperature = request.temperature,
                    grammar = request.grammar,
                    stopSequences = stopArray,
                )

                if (result != null) {
                    GenerationOutcome.Success(text = result)
                } else {
                    GenerationOutcome.Failed(
                        reason = GenerationFailureReason.ENGINE_ERROR,
                        message = "Native generation returned null",
                    )
                }
            }
        } catch (_: CancellationException) {
            GenerationOutcome.Failed(
                reason = GenerationFailureReason.CANCELLED,
                message = "Inference was cancelled",
            )
        } catch (_: OutOfMemoryError) {
            GenerationOutcome.Failed(
                reason = GenerationFailureReason.OUT_OF_MEMORY,
                message = "Native generation ran out of memory",
            )
        } catch (e: Exception) {
            GenerationOutcome.Failed(
                reason = GenerationFailureReason.ENGINE_ERROR,
                message = e.message ?: "Unknown engine error",
            )
        }
    }

    override fun close(): Unit {
        if (nativeHandle != 0L) {
            bridge.freeModel(nativeHandle)
            nativeHandle = 0L
        }
        _status = LlmStatus.Unloaded
    }
}
