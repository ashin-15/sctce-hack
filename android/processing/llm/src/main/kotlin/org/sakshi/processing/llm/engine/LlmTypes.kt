package org.sakshi.processing.llm.engine

public enum class LlmStatus {
    Unloaded,
    Ready,
    Error;

    public companion object {
        public val UNLOADED: LlmStatus = Unloaded
        public val READY: LlmStatus = Ready
        public val ERROR: LlmStatus = Error
    }
}

public data class ModelInfo(
    val modelId: String,
    val parameterCount: Long,
    val quantization: String,
    val contextWindowTokens: Int,
    val sha256Checksum: String,
)

public data class GenerationRequest(
    val systemPrompt: String,
    val userPrompt: String,
    val maxTokens: Int = 256,
    val temperature: Float = 0.0f,
    val grammar: String? = null,
    val stopSequences: List<String> = emptyList(),
)

public enum class GenerationFailureReason {
    MODEL_NOT_READY,
    CONTEXT_LIMIT_EXCEEDED,
    OUT_OF_MEMORY,
    ENGINE_ERROR,
    CANCELLED,
}

public sealed interface GenerationOutcome {
    public data class Success(val text: String) : GenerationOutcome

    public data class Failed(
        val reason: GenerationFailureReason,
        val message: String? = null,
    ) : GenerationOutcome
}
