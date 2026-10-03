package org.sakshi.processing.llm.engine

public object NativeLlmBridge {
    private const val LIBRARY_NAME: String = "sakshi_llm"

    public val isLoaded: Boolean = try {
        System.loadLibrary(LIBRARY_NAME)
        true
    } catch (_: UnsatisfiedLinkError) {
        false
    } catch (_: SecurityException) {
        false
    }

    public val isAvailable: Boolean
        get() = isLoaded

    public fun initModel(modelPath: String, contextSize: Int): Long {
        if (!isLoaded) return 0L
        return try {
            nativeInitModel(modelPath, contextSize)
        } catch (_: UnsatisfiedLinkError) {
            0L
        }
    }

    public fun freeModel(handle: Long): Unit {
        if (!isLoaded || handle == 0L) return
        try {
            nativeFreeModel(handle)
        } catch (_: UnsatisfiedLinkError) {
            // Safe ignore if native library unlinked
        }
    }

    public fun generate(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        grammar: String?,
        stopSequences: Array<String>,
    ): String? {
        if (!isLoaded || handle == 0L) return null
        return try {
            nativeGenerate(handle, prompt, maxTokens, temperature, grammar, stopSequences)
        } catch (_: UnsatisfiedLinkError) {
            null
        }
    }

    @JvmStatic
    private external fun nativeInitModel(modelPath: String, contextSize: Int): Long

    @JvmStatic
    private external fun nativeFreeModel(handle: Long): Unit

    @JvmStatic
    private external fun nativeGenerate(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        grammar: String?,
        stopSequences: Array<String>,
    ): String?
}
