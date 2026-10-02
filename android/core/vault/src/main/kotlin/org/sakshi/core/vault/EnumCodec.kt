package org.sakshi.core.vault

import kotlinx.serialization.KSerializer
import org.sakshi.core.model.AssociationReview
import org.sakshi.core.model.BoundaryMarker
import org.sakshi.core.model.BoundaryReviewStatus
import org.sakshi.core.model.CategoryBasis
import org.sakshi.core.model.CategoryLabel
import org.sakshi.core.model.CategoryReviewStatus
import org.sakshi.core.model.CommunicationStatus
import org.sakshi.core.model.ConfidenceSemantics
import org.sakshi.core.model.ConfirmationScope
import org.sakshi.core.model.ConfirmationStatus
import org.sakshi.core.model.CoverageContext
import org.sakshi.core.model.DedupStatus
import org.sakshi.core.model.Direction
import org.sakshi.core.model.EventKind
import org.sakshi.core.model.IdentityBasis
import org.sakshi.core.model.OutgoingCoverage
import org.sakshi.core.model.RelationshipBasis
import org.sakshi.core.model.RelationshipReviewStatus
import org.sakshi.core.model.RelationshipType
import org.sakshi.core.model.Representation
import org.sakshi.core.model.RetentionMode
import org.sakshi.core.model.ReviewPriority
import org.sakshi.core.model.SeverityBasis
import org.sakshi.core.model.SourceKind
import org.sakshi.core.model.TextStatus
import org.sakshi.core.model.TimeBasis
import org.sakshi.core.model.TimePrecision
import org.sakshi.core.model.UnwantedContact

/**
 * Maps an enum to the schema's snake_case strings and back. The strings come from the enum's serializer
 * descriptor (the `@SerialName` values), so there is no second table to keep in step.
 */
internal class EnumCodec<E : Enum<E>>(serializer: KSerializer<E>, entries: List<E>) {
    private val typeName: String = serializer.descriptor.serialName
    private val names: List<String> = entries.map { serializer.descriptor.getElementName(it.ordinal) }
    private val byName: Map<String, E> = entries.associateBy { names[it.ordinal] }

    fun name(value: E): String = names[value.ordinal]

    /** @throws IllegalStateException if [text] is not a value of the enum, i.e. the stored row is damaged. */
    fun parse(text: String): E = checkNotNull(byName[text]) { "Stored value is not a known $typeName" }
}

/** One [EnumCodec] per schema enum. */
internal object Codecs {
    val eventKind: EnumCodec<EventKind> = EnumCodec(EventKind.serializer(), EventKind.entries)
    val identityBasis: EnumCodec<IdentityBasis> = EnumCodec(IdentityBasis.serializer(), IdentityBasis.entries)
    val associationReview: EnumCodec<AssociationReview> = EnumCodec(AssociationReview.serializer(), AssociationReview.entries)
    val sourceKind: EnumCodec<SourceKind> = EnumCodec(SourceKind.serializer(), SourceKind.entries)
    val direction: EnumCodec<Direction> = EnumCodec(Direction.serializer(), Direction.entries)
    val categoryLabel: EnumCodec<CategoryLabel> = EnumCodec(CategoryLabel.serializer(), CategoryLabel.entries)
    val categoryBasis: EnumCodec<CategoryBasis> = EnumCodec(CategoryBasis.serializer(), CategoryBasis.entries)
    val categoryReviewStatus: EnumCodec<CategoryReviewStatus> = EnumCodec(CategoryReviewStatus.serializer(), CategoryReviewStatus.entries)
    val confidenceSemantics: EnumCodec<ConfidenceSemantics> = EnumCodec(ConfidenceSemantics.serializer(), ConfidenceSemantics.entries)
    val reviewPriority: EnumCodec<ReviewPriority> = EnumCodec(ReviewPriority.serializer(), ReviewPriority.entries)
    val severityBasis: EnumCodec<SeverityBasis> = EnumCodec(SeverityBasis.serializer(), SeverityBasis.entries)
    val representation: EnumCodec<Representation> = EnumCodec(Representation.serializer(), Representation.entries)
    val confirmationStatus: EnumCodec<ConfirmationStatus> = EnumCodec(ConfirmationStatus.serializer(), ConfirmationStatus.entries)
    val confirmationScope: EnumCodec<ConfirmationScope> = EnumCodec(ConfirmationScope.serializer(), ConfirmationScope.entries)
    val dedupStatus: EnumCodec<DedupStatus> = EnumCodec(DedupStatus.serializer(), DedupStatus.entries)
    val coverageContext: EnumCodec<CoverageContext> = EnumCodec(CoverageContext.serializer(), CoverageContext.entries)
    val textStatus: EnumCodec<TextStatus> = EnumCodec(TextStatus.serializer(), TextStatus.entries)
    val outgoingCoverage: EnumCodec<OutgoingCoverage> = EnumCodec(OutgoingCoverage.serializer(), OutgoingCoverage.entries)
    val boundaryMarker: EnumCodec<BoundaryMarker> = EnumCodec(BoundaryMarker.serializer(), BoundaryMarker.entries)
    val boundaryReviewStatus: EnumCodec<BoundaryReviewStatus> = EnumCodec(BoundaryReviewStatus.serializer(), BoundaryReviewStatus.entries)
    val communicationStatus: EnumCodec<CommunicationStatus> = EnumCodec(CommunicationStatus.serializer(), CommunicationStatus.entries)
    val unwantedContact: EnumCodec<UnwantedContact> = EnumCodec(UnwantedContact.serializer(), UnwantedContact.entries)
    val relationshipType: EnumCodec<RelationshipType> = EnumCodec(RelationshipType.serializer(), RelationshipType.entries)
    val relationshipBasis: EnumCodec<RelationshipBasis> = EnumCodec(RelationshipBasis.serializer(), RelationshipBasis.entries)
    val relationshipReviewStatus: EnumCodec<RelationshipReviewStatus> = EnumCodec(RelationshipReviewStatus.serializer(), RelationshipReviewStatus.entries)
    val retentionMode: EnumCodec<RetentionMode> = EnumCodec(RetentionMode.serializer(), RetentionMode.entries)
    val timeBasis: EnumCodec<TimeBasis> = EnumCodec(TimeBasis.serializer(), TimeBasis.entries)
    val timePrecision: EnumCodec<TimePrecision> = EnumCodec(TimePrecision.serializer(), TimePrecision.entries)
}
