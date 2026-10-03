package org.sakshi.export.report

import java.time.ZoneId
import java.util.Locale
import org.sakshi.core.model.CategoryAssessment
import org.sakshi.core.model.CategoryBasis
import org.sakshi.core.model.CategoryReviewStatus
import org.sakshi.core.model.EpistemicStatus
import org.sakshi.core.model.Event
import org.sakshi.core.model.Representation
import org.sakshi.core.vault.DecisionTargets
import org.sakshi.export.bundle.Finding
import org.sakshi.export.bundle.FindingConfidence

/** Tags of one event, sorted by how the report may show them. */
internal class TagSplit(
    val accepted: List<Pair<Int, CategoryAssessment>>,
    val unreviewed: List<Pair<Int, CategoryAssessment>>,
    /** Suggestions left out because they were not accepted and the option to list them is off. */
    val withheld: Int,
)

/** Builds the per-event parts of the report and the bundle findings that go with them. */
internal object EventBlocks {
    fun split(event: Event, options: ReportOptions): TagSplit {
        val indexed = event.categories.withIndex().map { it.index to it.value }
        val accepted = indexed.filter { it.second.reviewStatus == CategoryReviewStatus.ACCEPTED }
        val others = indexed.filter {
            it.second.reviewStatus == CategoryReviewStatus.UNREVIEWED || it.second.reviewStatus == CategoryReviewStatus.UNCERTAIN
        }
        return if (options.includeUnreviewedSuggestions) {
            TagSplit(accepted, others, 0)
        } else {
            TagSplit(accepted, emptyList(), others.size)
        }
    }

    fun timelineRow(event: Event, zone: ZoneId): TimelineRow {
        val time = ReportFormat.time(event, zone)
        val (sender, identity) = ReportFormat.sender(event)
        return TimelineRow(
            eventId = event.eventId.value,
            timeText = time.text,
            timeBasis = time.basis,
            sender = sender,
            identityBasis = identity,
            direction = ReportText.DIRECTION.getValue(event.direction),
            sourceKind = ReportText.SOURCE_KIND.getValue(event.source.kind),
        )
    }

    suspend fun block(
        number: Int,
        event: Event,
        zone: ZoneId,
        tags: TagSplit,
        quotes: QuoteSource,
        redacted: RedactedReference? = null,
        hashOf: suspend (String) -> String?,
    ): EventBlock {
        val time = ReportFormat.time(event, zone)
        val (sender, identity) = ReportFormat.sender(event)
        val observed = mutableListOf<ObservedPart>()
        val statements = mutableListOf<UserStatementPart>()
        for (reference in event.evidenceReferences) {
            val hidden = redacted?.takeIf { it.referenceId == reference.referenceId.value }
            val quote = hidden?.text ?: quotes.quote(event, reference)
            val artifact = reference.artifactId.value
            val locator = if (hidden != null) ReportText.LOCATOR_WITHHELD else ReportFormat.locator(reference.locator)
            val removed = hidden?.passages ?: 0
            if (reference.representation == Representation.MANUAL_STATEMENT) {
                statements += UserStatementPart(quote, artifact, time.text, locator, removed)
            } else {
                observed += ObservedPart(
                    quote = quote,
                    artifactId = artifact,
                    // A hash of the whole text would let a reader test guesses at the removed part.
                    sha256 = if (hidden != null) null else reference.sha256 ?: hashOf(artifact),
                    locator = locator,
                    representation = ReportText.REPRESENTATION.getValue(reference.representation),
                    redactedPassages = removed,
                )
            }
        }
        return EventBlock(
            eventId = event.eventId.value,
            revision = event.revision,
            heading = ReportText.fill(ReportText.RECORD_HEADING, number, ReportText.EVENT_KIND.getValue(event.eventKind)),
            details = listOf(
                ReportText.fill(ReportText.RECORD_ID, event.eventId.value, event.revision),
                ReportText.fill(ReportText.RECORD_TIME, time.text, time.basis),
                ReportText.fill(ReportText.RECORD_SENDER, sender, identity),
            ),
            observed = observed,
            userStatements = statements,
            inferred = tags.accepted.map { tag(it.second) },
            unreviewed = tags.unreviewed.map { tag(it.second) },
        )
    }

    fun findings(event: Event, tags: TagSplit): List<Finding> {
        val referenceIds = event.evidenceReferences.map { it.referenceId.value }
        return (tags.accepted + tags.unreviewed).mapNotNull { (index, category) ->
            val anchors = category.evidenceReferenceIds.map { it.value }.ifEmpty { referenceIds }
            if (anchors.isEmpty()) return@mapNotNull null
            Finding(
                id = DecisionTargets.category(event.eventId, event.revision, index),
                eventId = event.eventId.value,
                eventRevision = event.revision,
                label = category.label.name.lowercase(),
                sourceLabel = null,
                basis = category.basis.name.lowercase(),
                epistemicStatus = statusOf(category),
                confidence = FindingConfidence(category.confidence.value, category.confidence.semantics.name.lowercase()),
                producerVersion = category.producerVersion.value,
                anchorReferenceIds = anchors,
                reviewStatus = category.reviewStatus.name.lowercase(),
            )
        }
    }

    private fun tag(category: CategoryAssessment): TagPart = TagPart(
        status = statusOf(category),
        label = ReportText.CATEGORY.getValue(category.label),
        basis = ReportText.CATEGORY_BASIS.getValue(category.basis),
        producerVersion = category.producerVersion.value,
        confidence = confidence(category),
        reviewStatus = ReportText.REVIEW_STATUS.getValue(category.reviewStatus),
    )

    private fun statusOf(category: CategoryAssessment): EpistemicStatus =
        if (category.basis == CategoryBasis.USER_TAG) EpistemicStatus.USER_REPORTED else EpistemicStatus.INFERRED

    private fun confidence(category: CategoryAssessment): String {
        val score = category.confidence.value?.let { "%.2f".format(Locale.ENGLISH, it) } ?: ReportText.SCORE_NOT_RECORDED
        return ReportText.fill(ReportText.CONFIDENCE.getValue(category.confidence.semantics), score)
    }
}
