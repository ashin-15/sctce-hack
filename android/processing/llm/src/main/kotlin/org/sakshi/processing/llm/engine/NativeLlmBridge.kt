package org.sakshi.processing.llm.engine

public enum class NativeRuntimeState { AVAILABLE, MISSING }

public data class NativeLoadResult(
    public val handle: Long,
    public val statusCode: Int,
    public val parameterCount: Long? = null,
    public val fileType: Long? = null,
    public val qwenArchitecture: Boolean? = null,
) {
    public val isReady: Boolean get() = handle != 0L && statusCode == STATUS_OK

    public companion object {
        public const val STATUS_OK: Int = 0
        public const val STATUS_ERROR: Int = 1
        public const val STATUS_MODEL_LOAD_FAILED: Int = 2
        public const val STATUS_MODEL_IDENTITY_MISMATCH: Int = 3
        public const val STATUS_CONTEXT_INIT_FAILED: Int = 4
    }
}

public data class NativeGenerationResult(public val statusCode: Int, public val text: String) {
    public companion object {
        public const val STATUS_OK: Int = 0
        public const val STATUS_ERROR: Int = 1
        public const val STATUS_TRUNCATED: Int = 2
        public const val STATUS_CANCELLED: Int = 3
    }
}

public interface NativeLlmRuntime {
    public val isAvailable: Boolean
    public fun initModel(modelPath: String, contextSize: Int): NativeLoadResult
    public fun freeModel(handle: Long)
    public fun cancel(handle: Long, requestId: String)
    public fun generate(
        handle: Long,
        systemPrompt: String,
        userPrompt: String,
        requestId: String,
        maxTokens: Int,
        grammar: String?,
        stopSequences: Array<String>,
    ): NativeGenerationResult
}

/** Thin typed boundary around the pinned llama.cpp JNI library. */
public object NativeLlmBridge : NativeLlmRuntime {
    public const val RUNTIME_COMMIT: String = "a7a98e0fffed794396b3fbad4dcdbbc184963645"
    public const val RUNTIME_VERSION: String = "llama.cpp-b6500"

    public val runtimeState: NativeRuntimeState = try {
        System.loadLibrary("sakshi_llm")
        NativeRuntimeState.AVAILABLE
    } catch (_: UnsatisfiedLinkError) {
        NativeRuntimeState.MISSING
    } catch (_: SecurityException) {
        NativeRuntimeState.MISSING
    }

    override val isAvailable: Boolean get() = runtimeState == NativeRuntimeState.AVAILABLE

    override fun initModel(modelPath: String, contextSize: Int): NativeLoadResult {
        if (!isAvailable) return NativeLoadResult(0L, NativeLoadResult.STATUS_ERROR)
        val result = try {
            nativeInitModel(modelPath, contextSize)
        } catch (_: UnsatisfiedLinkError) {
            null
        } catch (_: RuntimeException) {
            null
        }
        return if (result == null || result.size < 2) {
            NativeLoadResult(0L, NativeLoadResult.STATUS_ERROR)
        } else {
            NativeLoadResult(result[0], result[1].toInt(), result.getOrNull(2)?.takeIf { it > 0 },
                result.getOrNull(3)?.takeIf { it >= 0 }, result.getOrNull(4)?.let { it == 1L })
        }
    }

    override fun freeModel(handle: Long): Unit {
        if (isAvailable && handle != 0L) runCatching { nativeFreeModel(handle) }
    }

    override fun cancel(handle: Long, requestId: String): Unit {
        if (isAvailable && handle != 0L) runCatching { nativeCancel(handle, requestId) }
    }

    override fun generate(
        handle: Long,
        systemPrompt: String,
        userPrompt: String,
        requestId: String,
        maxTokens: Int,
        grammar: String?,
        stopSequences: Array<String>,
    ): NativeGenerationResult {
        if (!isAvailable || handle == 0L) return NativeGenerationResult(NativeGenerationResult.STATUS_ERROR, "")
        val result = try {
            nativeGenerate(handle, systemPrompt, userPrompt, requestId, maxTokens, grammar, stopSequences)
        } catch (_: UnsatisfiedLinkError) {
            null
        } catch (_: RuntimeException) {
            null
        }
        if (result == null || result.size < 2) return NativeGenerationResult(NativeGenerationResult.STATUS_ERROR, "")
        val status = result[0].toIntOrNull() ?: NativeGenerationResult.STATUS_ERROR
        return NativeGenerationResult(status, result[1].orEmpty())
    }

    @JvmStatic
    private external fun nativeInitModel(modelPath: String, contextSize: Int): LongArray?

    @JvmStatic
    private external fun nativeFreeModel(handle: Long)

    @JvmStatic
    private external fun nativeCancel(handle: Long, requestId: String)

    @JvmStatic
    private external fun nativeGenerate(
        handle: Long,
        systemPrompt: String,
        userPrompt: String,
        requestId: String,
        maxTokens: Int,
        grammar: String?,
        stopSequences: Array<String>,
    ): Array<String>?
}
