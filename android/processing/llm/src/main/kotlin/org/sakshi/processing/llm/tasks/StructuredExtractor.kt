package org.sakshi.processing.llm.tasks

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.sakshi.processing.llm.engine.GenerationOutcome
import org.sakshi.processing.llm.engine.GenerationRequest
import org.sakshi.processing.llm.engine.LlmEngine
import org.sakshi.processing.llm.prompt.GbnfGrammars
import org.sakshi.processing.llm.prompt.PromptBuilder
import org.sakshi.processing.llm.validation.SubstringQuoteValidator

@Serializable
internal data class RawExtractionJson(
    val date: String? = null,
    val platform: String? = null,
    val sender: String? = null,
    val threat_type: String? = null,
    val quote: String? = null,
    val source_ids: List<String> = emptyList(),
)

public class StructuredExtractor public constructor(
    private val engine: LlmEngine,
) {
    private val jsonParser: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    public suspend fun extract(
        sourceId: String,
        sourceText: String,
    ): StructuredExtractionResult {
        val evidence = "[$sourceId] $sourceText"
        val prompt = PromptBuilder.buildExtractionPrompt(evidence)
        val request = GenerationRequest(
            systemPrompt = PromptBuilder.SYSTEM_PROMPT,
            userPrompt = prompt,
            grammar = GbnfGrammars.extractionGrammar(),
        )

        return when (val outcome = engine.generate(request)) {
            is GenerationOutcome.Failed -> {
                StructuredExtractionResult(
                    isSuccess = false,
                    failureReason = outcome.reason.name,
                )
            }
            is GenerationOutcome.Success -> {
                parseAndValidate(outcome.text, sourceText, listOf(sourceId))
            }
        }
    }

    public suspend fun extract(
        sourceTexts: Map<String, String>,
    ): StructuredExtractionResult {
        val evidence = sourceTexts.entries.joinToString("\n") { (id, text) -> "[$id] $text" }
        val prompt = PromptBuilder.buildExtractionPrompt(evidence)
        val request = GenerationRequest(
            systemPrompt = PromptBuilder.SYSTEM_PROMPT,
            userPrompt = prompt,
            grammar = GbnfGrammars.extractionGrammar(),
        )

        return when (val outcome = engine.generate(request)) {
            is GenerationOutcome.Failed -> {
                StructuredExtractionResult(
                    isSuccess = false,
                    failureReason = outcome.reason.name,
                )
            }
            is GenerationOutcome.Success -> {
                val combinedText = sourceTexts.values.joinToString(" ")
                parseAndValidate(outcome.text, combinedText, sourceTexts.keys.toList())
            }
        }
    }

    private fun parseAndValidate(
        rawText: String,
        sourceText: String,
        fallbackSourceIds: List<String>,
    ): StructuredExtractionResult {
        return try {
            val jsonStartIndex = rawText.indexOf('{')
            val jsonEndIndex = rawText.lastIndexOf('}')
            val jsonString = if (jsonStartIndex != -1 && jsonEndIndex != -1 && jsonEndIndex > jsonStartIndex) {
                rawText.substring(jsonStartIndex, jsonEndIndex + 1)
            } else {
                rawText
            }

            val parsed = jsonParser.decodeFromString<RawExtractionJson>(jsonString)
            val quote = parsed.quote

            val quoteValid = if (!quote.isNullOrBlank()) {
                SubstringQuoteValidator.isValidQuote(quote, sourceText)
            } else {
                true
            }

            val sourceIds = if (parsed.source_ids.isNotEmpty()) {
                parsed.source_ids
            } else {
                fallbackSourceIds
            }

            StructuredExtractionResult(
                isSuccess = true,
                date = parsed.date,
                platform = parsed.platform,
                sender = parsed.sender,
                threatType = parsed.threat_type,
                quote = if (quoteValid) quote else null,
                sourceIds = sourceIds,
                isQuoteExactSubstring = quoteValid && !quote.isNullOrBlank(),
                failureReason = if (!quoteValid) "Extracted quote is not an exact substring of source" else null,
            )
        } catch (e: Exception) {
            StructuredExtractionResult(
                isSuccess = false,
                failureReason = "Failed to parse JSON extraction: ${e.message}",
            )
        }
    }
}
