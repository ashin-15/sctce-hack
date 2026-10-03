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
 *
 * For text read from an image, [regionsOf] names the image regions a cue span came from. Each such region becomes
 * one more reference with an image-region locator, listed by the categories of the cues inside it, so a suggestion
 * can be traced to the place in the image as well as to the recognised text.
 */
internal object CueReferences {
    /** The schema allows 64 references per event: the body plus at most this many cue and region references. */
    private const val MAX_EXTRA_REFERENCES: Int = 63

    fun assess(
        signals: RuleSignals,
        bodyOffset: Int,
        bodyReference: ReferenceId,
        artifact: ArtifactId,
        sha256: String,
        representation: Representation = Representation.PRESERVED_IMPORT,
        regionsOf: (CodePointSpan) -> List<String> = { emptyList() },
    ): CueAssessment {
        val spans = signals.matches.map { it.span }.distinct().take(MAX_EXTRA_REFERENCES)
        val ids = spans.withIndex().associate { (index, span) -> span to ReferenceId("cue-${index + 1}") }
        val cueReferences = spans.map { span ->
            EvidenceReference(
                referenceId = ids.getValue(span),
                artifactId = artifact,
                sha256 = sha256,
                representation = representation,
                locator = Locator.Text(bodyOffset + span.start, bodyOffset + span.end),
            )
        }
        val regionIds = spans.flatMap(regionsOf).distinct().take(MAX_EXTRA_REFERENCES - spans.size)
        val regionReferenceIds = regionIds.withIndex().associate { (index, region) -> region to ReferenceId("region-${index + 1}") }
        val regionReferences = regionIds.map { region ->
            EvidenceReference(
                referenceId = regionReferenceIds.getValue(region),
                artifactId = artifact,
                sha256 = sha256,
                representation = representation,
                locator = Locator.ImageOrPageRegion(0, ScopeId(region)),
            )
        }
        val byLabel = linkedMapOf<CategoryLabel, MutableList<CodePointSpan>>()
        for (match in signals.matches.sortedBy { it.span.start }) {
            val labelSpans = byLabel.getOrPut(match.schemaLabel) { mutableListOf() }
            if (match.span in ids) labelSpans.add(match.span)
        }
        val categories = byLabel.map { (label, labelSpans) ->
            val cueRefs = labelSpans.distinct().map { ids.getValue(it) }
            val regionRefs = labelSpans.flatMap(regionsOf).distinct().mapNotNull { regionReferenceIds[it] }
            CategoryAssessment(
                label = label,
                basis = CategoryBasis.RULE_SUGGESTION,
                confidence = Confidence(null, ConfidenceSemantics.NOT_APPLICABLE, null),
                producerVersion = ScopeId(signals.producerVersion),
                evidenceReferenceIds = (cueRefs + regionRefs).ifEmpty { listOf(bodyReference) },
                reviewStatus = CategoryReviewStatus.UNREVIEWED,
            )
        }
        return CueAssessment(categories, severity(categories), cueReferences + regionReferences)
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
