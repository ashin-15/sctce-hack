package org.sakshi.processing.analysis

import org.sakshi.core.model.ArtifactId
import org.sakshi.core.model.CategoryAssessment
import org.sakshi.core.model.CategoryBasis
import org.sakshi.core.model.CategoryLabel
import org.sakshi.core.model.CategoryReviewStatus
import org.sakshi.core.model.CodePointSpan
import org.sakshi.core.model.Confidence
import org.sakshi.core.model.ConfidenceSemantics
import org.sakshi.core.model.EvidenceReference
import org.sakshi.core.model.Locator
import org.sakshi.core.model.ReferenceId
import org.sakshi.core.model.Representation
import org.sakshi.core.model.ReviewPriority
import org.sakshi.core.model.ScopeId
import org.sakshi.core.model.SeverityAssessment
import org.sakshi.core.model.SeverityBasis
import org.sakshi.processing.text.RuleSignals

/** Categories, severity and the extra cue references derived from one rule result. */
internal class CueAssessment(
    val categories: List<CategoryAssessment>,
    val severity: SeverityAssessment,
    val cueReferences: List<EvidenceReference>,
)

/**
 * Turns rule matches into schema categories. Each distinct cue span becomes one extra evidence reference whose
 * text locator lies inside the message, and one category per schema label lists the spans of its matches.
 */
internal object CueReferences {
    /** The schema allows 64 references per event: the body plus at most this many cue spans. */
    private const val MAX_CUE_REFERENCES: Int = 63

    fun assess(
        signals: RuleSignals,
        bodyOffset: Int,
        bodyReference: ReferenceId,
        artifact: ArtifactId,
        sha256: String,
    ): CueAssessment {
        val spans = signals.matches.map { it.span }.distinct().take(MAX_CUE_REFERENCES)
        val ids = spans.withIndex().associate { (index, span) -> span to ReferenceId("cue-${index + 1}") }
        val cueReferences = spans.map { span ->
            EvidenceReference(
                referenceId = ids.getValue(span),
                artifactId = artifact,
                sha256 = sha256,
                representation = Representation.PRESERVED_IMPORT,
                locator = Locator.Text(bodyOffset + span.start, bodyOffset + span.end),
            )
        }
        val byLabel = linkedMapOf<CategoryLabel, MutableList<CodePointSpan>>()
        for (match in signals.matches.sortedBy { it.span.start }) {
            val labelSpans = byLabel.getOrPut(match.schemaLabel) { mutableListOf() }
            if (match.span in ids) labelSpans.add(match.span)
        }
        val categories = byLabel.map { (label, labelSpans) ->
            val refs = labelSpans.distinct().map { ids.getValue(it) }.ifEmpty { listOf(bodyReference) }
            CategoryAssessment(
                label = label,
                basis = CategoryBasis.RULE_SUGGESTION,
                confidence = Confidence(null, ConfidenceSemantics.NOT_APPLICABLE, null),
                producerVersion = ScopeId(signals.producerVersion),
                evidenceReferenceIds = refs,
                reviewStatus = CategoryReviewStatus.UNREVIEWED,
            )
        }
        return CueAssessment(categories, severity(categories), cueReferences)
    }

    fun severity(categories: List<CategoryAssessment>): SeverityAssessment =
        if (categories.isEmpty()) {
            SeverityAssessment(ReviewPriority.ORDINARY, SeverityBasis.UNKNOWN, emptyList())
        } else {
            SeverityAssessment(
                ReviewPriority.REVIEW,
                SeverityBasis.POLICY_SUGGESTION,
                categories.flatMap { it.evidenceReferenceIds }.distinct(),
            )
        }
}
