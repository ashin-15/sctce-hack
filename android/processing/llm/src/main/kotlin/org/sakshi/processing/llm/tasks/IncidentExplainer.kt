package org.sakshi.processing.llm.tasks

import org.sakshi.processing.llm.engine.GenerationOutcome
import org.sakshi.processing.llm.engine.GenerationRequest
import org.sakshi.processing.llm.engine.LlmEngine
import org.sakshi.processing.llm.prompt.PromptBuilder
import org.sakshi.processing.llm.validation.LegalClaimFilter
import org.sakshi.processing.llm.validation.SubstringQuoteValidator

public class IncidentExplainer public constructor(
    private val engine: LlmEngine,
) {
    public suspend fun explain(
        sourceId: String,
        sourceText: String,
        labels: List<String>,
    ): WhyFlaggedResult {
        val prompt = PromptBuilder.buildWhyFlaggedPrompt(
            source = "[$sourceId] $sourceText",
            labels = labels,
        )

        val request = GenerationRequest(
            systemPrompt = PromptBuilder.SYSTEM_PROMPT,
            userPrompt = prompt,
        )

        return when (val outcome = engine.generate(request)) {
            is GenerationOutcome.Failed -> {
                WhyFlaggedResult(
                    isSuccess = false,
                    explanation = "",
                    sourceId = sourceId,
                    failureReason = outcome.reason.name,
                )
            }
            is GenerationOutcome.Success -> {
                processExplanation(sourceId, sourceText, outcome.text)
            }
        }
    }

    private fun processExplanation(
        sourceId: String,
        sourceText: String,
        rawText: String,
    ): WhyFlaggedResult {
        val legalFilterResult = LegalClaimFilter.filter(rawText)
        val legalClaimsDetected = legalFilterResult.containsImpermissibleClaims
        val cleanedText = legalFilterResult.filteredText

        val extractedQuotes = SubstringQuoteValidator.extractQuotes(cleanedText)
        var quoteValidated = false
        var primaryQuote: String? = null

        if (extractedQuotes.isNotEmpty()) {
            val validation = SubstringQuoteValidator.validate(extractedQuotes, sourceText)
            quoteValidated = validation.isValid
            primaryQuote = validation.validQuotes.firstOrNull()
        }

        return WhyFlaggedResult(
            isSuccess = true,
            explanation = cleanedText,
            supportingQuote = primaryQuote,
            sourceId = sourceId,
            isClassifierSuggestion = true,
            legalClaimsDetected = legalClaimsDetected,
            quoteValidated = quoteValidated,
            failureReason = null,
        )
    }
}
