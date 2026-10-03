package org.sakshi.processing.llm.engine

public interface LlmEngine : AutoCloseable {
    public val status: LlmStatus
    public suspend fun generate(request: GenerationRequest): GenerationOutcome
}
