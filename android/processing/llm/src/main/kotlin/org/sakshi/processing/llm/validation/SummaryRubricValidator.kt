package org.sakshi.processing.llm.validation

public data class RubricPointResult(
    val pointName: String,
    val passed: Boolean,
    val explanation: String,
)

public data class RubricValidationResult(
    val isValid: Boolean,
    val points: List<RubricPointResult>,
    val sentenceCount: Int,
    val sentences: List<String>,
)

public object SummaryRubricValidator {

    public fun splitSentences(text: String): List<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        val sentences = mutableListOf<String>()
        val current = StringBuilder()
        var insideQuotes = false

        var i = 0
        while (i < trimmed.length) {
            val c = trimmed[i]
            if (c == '"' || c == '“' || c == '”') {
                insideQuotes = !insideQuotes
                current.append(c)
                i++
                continue
            }

            current.append(c)

            if (!insideQuotes && (c == '.' || c == '!' || c == '?')) {
                var j = i + 1
                while (j < trimmed.length && trimmed[j].isWhitespace()) {
                    j++
                }
                if (j > i + 1 && (j >= trimmed.length || trimmed[j].isUpperCase() || trimmed[j] == '[')) {
                    val sentenceStr = current.toString().trim()
                    if (sentenceStr.isNotEmpty()) {
                        sentences.add(sentenceStr)
                    }
                    current.clear()
                    i = j
                    continue
                }
            }
            i++
        }

        val remaining = current.toString().trim()
        if (remaining.isNotEmpty()) {
            sentences.add(remaining)
        }

        return sentences
    }

    public fun extractSourceCitations(text: String): Set<String> {
        val bracketMatches = Regex("""\[([a-zA-Z0-9_\-]+)\]""").findAll(text)
        return bracketMatches.map { it.groupValues[1] }.toSet()
    }

    public fun validate(
        summaryText: String,
        sourceTexts: Map<String, String>,
        knownSourceIds: Set<String> = sourceTexts.keys,
    ): RubricValidationResult {
        val sentences = splitSentences(summaryText)
        val points = mutableListOf<RubricPointResult>()

        // 1. Exactly three sentences
        val countPassed = sentences.size == 3
        points.add(
            RubricPointResult(
                pointName = "Exactly three sentences",
                passed = countPassed,
                explanation = if (countPassed) {
                    "Summary contains exactly 3 sentences."
                } else {
                    "Expected exactly 3 sentences but found ${sentences.size}."
                },
            ),
        )

        // 2. Source ID citations in each factual sentence
        val uncitedSentences = mutableListOf<Int>()
        val invalidCitations = mutableListOf<String>()

        for ((index, sentence) in sentences.withIndex()) {
            val citedIds = extractSourceCitations(sentence)
            if (citedIds.isEmpty()) {
                val foundDirect = knownSourceIds.any { id -> sentence.contains(id) }
                if (!foundDirect) {
                    uncitedSentences.add(index + 1)
                }
            } else if (knownSourceIds.isNotEmpty()) {
                for (cid in citedIds) {
                    if (cid !in knownSourceIds) {
                        invalidCitations.add(cid)
                    }
                }
            }
        }

        val citationPassed = uncitedSentences.isEmpty() && invalidCitations.isEmpty()
        points.add(
            RubricPointResult(
                pointName = "Valid source ID citations",
                passed = citationPassed,
                explanation = if (citationPassed) {
                    "All sentences cite valid source IDs."
                } else {
                    buildString {
                        if (uncitedSentences.isNotEmpty()) {
                            append("Sentences without citations: ${uncitedSentences.joinToString(", ")}. ")
                        }
                        if (invalidCitations.isNotEmpty()) {
                            append("Invalid cited IDs: ${invalidCitations.joinToString(", ")}.")
                        }
                    }.trim()
                },
            ),
        )

        // 3. Exact substring quotes
        val combinedSourceText = sourceTexts.values.joinToString(" ")
        val quotes = SubstringQuoteValidator.extractQuotes(summaryText)
        val quoteCheck = SubstringQuoteValidator.validate(quotes, combinedSourceText)
        val quotesPassed = quoteCheck.isValid
        points.add(
            RubricPointResult(
                pointName = "Exact substring quotes",
                passed = quotesPassed,
                explanation = if (quotesPassed) {
                    "All quotes are exact substrings of source evidence."
                } else {
                    "Hallucinated quotes detected: ${quoteCheck.hallucinatedQuotes.joinToString(", ")}."
                },
            ),
        )

        // 4. No impermissible legal claims or guilt assertions
        val legalCheck = LegalClaimFilter.filter(summaryText)
        val legalPassed = !legalCheck.containsImpermissibleClaims
        points.add(
            RubricPointResult(
                pointName = "No legal claims",
                passed = legalPassed,
                explanation = if (legalPassed) {
                    "No impermissible legal claims detected."
                } else {
                    "Impermissible legal claims found: ${legalCheck.flaggedPhrases.joinToString(", ")}."
                },
            ),
        )

        // 5. Preserved uncertainty and source tracing
        val tracedPassed = citationPassed && quotesPassed
        points.add(
            RubricPointResult(
                pointName = "Source-supported facts",
                passed = tracedPassed,
                explanation = if (tracedPassed) {
                    "Factual claims are traced to source evidence."
                } else {
                    "Some claims or quotes cannot be traced to source evidence."
                },
            ),
        )

        val allPassed = points.all { it.passed }

        return RubricValidationResult(
            isValid = allPassed,
            points = points,
            sentenceCount = sentences.size,
            sentences = sentences,
        )
    }

    public fun validate(
        summaryText: String,
        sourceText: String,
        sourceId: String = "src-1",
    ): RubricValidationResult {
        return validate(
            summaryText = summaryText,
            sourceTexts = mapOf(sourceId to sourceText),
            knownSourceIds = setOf(sourceId),
        )
    }
}
