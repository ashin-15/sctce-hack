package org.sakshi.processing.llm.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

public class DeterministicFallbackEngine public constructor(
    private val customHandler: ((GenerationRequest) -> GenerationOutcome)? = null,
) : LlmEngine {

    private var _status: LlmStatus = LlmStatus.Ready

    override val status: LlmStatus
        get() = _status

    override suspend fun generate(request: GenerationRequest): GenerationOutcome {
        if (_status != LlmStatus.Ready) {
            return GenerationOutcome.Failed(
                reason = GenerationFailureReason.MODEL_NOT_READY,
                message = "Deterministic engine is unloaded",
            )
        }

        try {
            currentCoroutineContext().ensureActive()
        } catch (_: CancellationException) {
            return GenerationOutcome.Failed(
                reason = GenerationFailureReason.CANCELLED,
                message = "Inference was cancelled",
            )
        }

        return InferenceLock.withLock {
            if (customHandler != null) {
                return@withLock customHandler.invoke(request)
            }

            val userPrompt = request.userPrompt
            when {
                userPrompt.contains("Explain the predicted label", ignoreCase = true) -> {
                    generateWhyFlaggedFallback(userPrompt)
                }
                userPrompt.contains("Write exactly three sentences", ignoreCase = true) -> {
                    generateSummaryFallback(userPrompt)
                }
                userPrompt.contains("Extract date, platform, sender", ignoreCase = true) -> {
                    generateExtractionFallback(userPrompt)
                }
                else -> {
                    GenerationOutcome.Success(text = "Processed request deterministically.")
                }
            }
        }
    }

    private fun generateWhyFlaggedFallback(prompt: String): GenerationOutcome {
        val evidenceMarker = "Evidence:"
        val labelsMarker = "; classifier labels:"
        val evidenceIdx = prompt.indexOf(evidenceMarker)
        val labelsIdx = prompt.indexOf(labelsMarker)

        val source = if (evidenceIdx != -1 && labelsIdx != -1 && labelsIdx > evidenceIdx) {
            prompt.substring(evidenceIdx + evidenceMarker.length, labelsIdx).trim()
        } else if (evidenceIdx != -1) {
            prompt.substring(evidenceIdx + evidenceMarker.length).trim()
        } else {
            prompt
        }

        val labels = if (labelsIdx != -1) {
            prompt.substring(labelsIdx + labelsMarker.length).trim()
        } else {
            "unspecified"
        }

        val sourceIdMatch = Regex("""\[([a-zA-Z0-9_\-]+)\]""").find(source)
        val sourceId = sourceIdMatch?.value ?: "[src-1]"

        val cleanSource = source.replace(Regex("""\[[a-zA-Z0-9_\-]+\]"""), "").trim()
        val quote = if (cleanSource.length > 50) cleanSource.take(50).trim() else cleanSource

        val response = "Classifier suggested $labels based on exact quote \"$quote\" from source $sourceId."
        return GenerationOutcome.Success(text = response)
    }

    private fun generateSummaryFallback(prompt: String): GenerationOutcome {
        val evidenceMarker = "Evidence:"
        val evidenceIdx = prompt.indexOf(evidenceMarker)
        val source = if (evidenceIdx != -1) {
            prompt.substring(evidenceIdx + evidenceMarker.length).trim()
        } else {
            prompt
        }

        val sourceIdMatch = Regex("""\[([a-zA-Z0-9_\-]+)\]""").find(source)
        val sourceId = sourceIdMatch?.value ?: "[src-1]"

        val cleanSource = source.replace(Regex("""\[[a-zA-Z0-9_\-]+\]"""), "").trim()
        val quote = if (cleanSource.length > 40) cleanSource.take(40).trim() else cleanSource

        val s1 = "Source $sourceId records an incoming message from the sender."
        val s2 = "The message contained the statement \"$quote\" per $sourceId."
        val s3 = "The context remains preserved for user review in $sourceId."

        return GenerationOutcome.Success(text = "$s1 $s2 $s3")
    }

    private fun generateExtractionFallback(prompt: String): GenerationOutcome {
        val evidenceMarker = "Evidence:"
        val evidenceIdx = prompt.indexOf(evidenceMarker)
        val source = if (evidenceIdx != -1) {
            prompt.substring(evidenceIdx + evidenceMarker.length).trim()
        } else {
            prompt
        }

        val sourceIdMatch = Regex("""\[([a-zA-Z0-9_\-]+)\]""").find(source)
        val rawSourceId = sourceIdMatch?.groupValues?.get(1) ?: "src-1"

        val cleanSource = source.replace(Regex("""\[[a-zA-Z0-9_\-]+\]"""), "").trim()
        val quote = if (cleanSource.length > 30) cleanSource.take(30).trim() else cleanSource

        val json = """
        {
          "date": null,
          "platform": null,
          "sender": null,
          "threat_type": null,
          "quote": "$quote",
          "source_ids": ["$rawSourceId"]
        }
        """.trimIndent()
        return GenerationOutcome.Success(text = json)
    }

    override fun close(): Unit {
        _status = LlmStatus.Unloaded
    }
}
