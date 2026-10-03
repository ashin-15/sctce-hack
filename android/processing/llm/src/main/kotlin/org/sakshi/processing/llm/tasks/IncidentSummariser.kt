package org.sakshi.processing.llm.tasks

import org.sakshi.processing.llm.engine.GenerationOutcome
import org.sakshi.processing.llm.engine.GenerationRequest
import org.sakshi.processing.llm.engine.LlmEngine
import org.sakshi.processing.llm.prompt.PromptBuilder
import org.sakshi.processing.llm.validation.SummaryRubricValidator

public class IncidentSummariser public constructor(
    private val engine: LlmEngine,
) {
    public suspend fun summarise(
        sourceTexts: Map<String, String>,
    ): IncidentSummaryResult {
        val evidenceFormatted = sourceTexts.entries.joinToString("\n") { (id, text) ->
            "[$id] $text"
        }
        val prompt = PromptBuilder.buildSummaryPrompt(evidenceFormatted)
        val request = GenerationRequest(
            systemPrompt = PromptBuilder.SYSTEM_PROMPT,
            userPrompt = prompt,
        )

        return when (val outcome = engine.generate(request)) {
            is GenerationOutcome.Failed -> {
                IncidentSummaryResult(
                    isSuccess = false,
                    summary = "",
                    failureReason = outcome.reason.name,
                )
            }
            is GenerationOutcome.Success -> {
                val rubricResult = SummaryRubricValidator.validate(
                    summaryText = outcome.text,
                    sourceTexts = sourceTexts,
                )
                IncidentSummaryResult(
                    isSuccess = rubricResult.isValid,
                    summary = outcome.text,
                    sentences = rubricResult.sentences,
                    rubricResult = rubricResult,
                    failureReason = if (!rubricResult.isValid) {
                        rubricResult.points.filter { !it.passed }.joinToString("; ") { it.explanation }
                    } else null,
                )
            }
        }
    }

    public suspend fun summarise(
        sourceId: String,
        sourceText: String,
    ): IncidentSummaryResult {
        return summarise(mapOf(sourceId to sourceText))
    }
}
