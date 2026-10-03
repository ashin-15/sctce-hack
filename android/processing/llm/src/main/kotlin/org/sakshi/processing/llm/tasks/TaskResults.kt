package org.sakshi.processing.llm.tasks

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.sakshi.processing.llm.validation.RubricValidationResult

@Serializable
public data class WhyFlaggedResult(
    val isSuccess: Boolean,
    val explanation: String,
    val supportingQuote: String? = null,
    val sourceId: String? = null,
    val isClassifierSuggestion: Boolean = true,
    val legalClaimsDetected: Boolean = false,
    val quoteValidated: Boolean = false,
    val failureReason: String? = null,
)

public data class IncidentSummaryResult(
    val isSuccess: Boolean,
    val summary: String,
    val sentences: List<String> = emptyList(),
    val rubricResult: RubricValidationResult? = null,
    val failureReason: String? = null,
)

@Serializable
public data class StructuredExtractionResult(
    val isSuccess: Boolean,
    val date: String? = null,
    val platform: String? = null,
    val sender: String? = null,
    @SerialName("threat_type") val threatType: String? = null,
    val quote: String? = null,
    @SerialName("source_ids") val sourceIds: List<String> = emptyList(),
    val isQuoteExactSubstring: Boolean = false,
    val failureReason: String? = null,
)
