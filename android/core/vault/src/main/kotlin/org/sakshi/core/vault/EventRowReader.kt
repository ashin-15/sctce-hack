package org.sakshi.core.vault

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.sakshi.core.database.EvidenceAnchorEntity
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.ArtifactId
import org.sakshi.core.model.Boundary
import org.sakshi.core.model.BoundaryMarker
import org.sakshi.core.model.BoundaryReviewStatus
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.CategoryAssessment
import org.sakshi.core.model.CommunicationStatus
import org.sakshi.core.model.Confidence
import org.sakshi.core.model.Coverage
import org.sakshi.core.model.Deduplication
import org.sakshi.core.model.Event
import org.sakshi.core.model.EventId
import org.sakshi.core.model.EventRelationship
import org.sakshi.core.model.EventSender
import org.sakshi.core.model.EventSource
import org.sakshi.core.model.EvidenceReference
import org.sakshi.core.model.Locator
import org.sakshi.core.model.ReferenceId
import org.sakshi.core.model.Retention
import org.sakshi.core.model.ScopeId
import org.sakshi.core.model.SeverityAssessment
import org.sakshi.core.model.TimeBounds
import org.sakshi.core.model.Timestamp
import org.sakshi.core.model.UnwantedContact
import org.sakshi.core.model.UserConfirmation

/** Rebuilds an [Event] from the rows [EventRowWriter] produced. Damaged rows fail closed with an exception. */
internal object EventRowReader {
    fun assemble(caseId: String, rows: RevisionRows): Event {
        val row = rows.revision
        val anchors = rows.anchors.sortedBy { it.id }
        val referenceByAnchor = anchors.associate { it.id to ReferenceId(it.referenceId) }
        return Event(
            eventId = EventId(row.eventId),
            caseId = CaseId(caseId),
            revision = row.revision,
            eventKind = Codecs.eventKind.parse(row.kind),
            observedAt = Timestamp(row.observedAt),
            availableAt = Timestamp(row.availableAt),
            timestamp = TimeBounds(
                earliest = row.tsEarliest?.let(::Timestamp),
                latest = row.tsLatest?.let(::Timestamp),
                basis = Codecs.timeBasis.parse(row.tsBasis),
                precision = Codecs.timePrecision.parse(row.tsPrecision),
                sourceTimezone = row.tsTimezone,
                collectorSessionId = row.collectorSessionId?.let(::ScopeId),
                monotonicMs = row.monotonicMs,
            ),
            sender = EventSender(
                actorId = row.actorId?.let(::ActorId),
                displayLabel = row.senderDisplayLabel,
                identityBasis = Codecs.identityBasis.parse(row.senderIdentityBasis),
                associationReview = Codecs.associationReview.parse(row.senderAssociationReview),
            ),
            source = EventSource(
                kind = Codecs.sourceKind.parse(row.sourceKind),
                sourceApp = row.sourceApp,
                profileScopeId = row.profileScopeId?.let(::ScopeId),
                conversationScopeId = row.conversationScopeId?.let(::ScopeId),
                sourceRecordId = row.sourceRecordId?.let(::ScopeId),
                parserVersion = ScopeId(checkNotNull(row.parserVersion) { "Stored parser version is missing" }),
            ),
            direction = Codecs.direction.parse(row.direction),
            categories = rows.findings.sortedBy { it.finding.id }.map { category(it, referenceByAnchor) },
            severity = SeverityAssessment(
                reviewPriority = Codecs.reviewPriority.parse(row.reviewPriority),
                basis = Codecs.severityBasis.parse(row.severityBasis),
                evidenceReferenceIds = decodeIds(row.severityReferenceIdsJson),
            ),
            evidenceReferences = anchors.map(::reference),
            userConfirmation = UserConfirmation(
                status = Codecs.confirmationStatus.parse(row.confirmationStatus),
                reviewedAt = row.reviewedAt?.let(::Timestamp),
                scope = Codecs.confirmationScope.parse(row.confirmationScope),
            ),
            deduplication = Deduplication(
                status = Codecs.dedupStatus.parse(row.dedupStatus),
                canonicalEventId = row.canonicalEventId?.let(::EventId),
                methodVersion = ScopeId(checkNotNull(row.dedupMethod) { "Stored deduplication method is missing" }),
            ),
            coverage = Coverage(
                context = Codecs.coverageContext.parse(row.coverageContext),
                textStatus = Codecs.textStatus.parse(row.textStatus),
                outgoingCoverage = Codecs.outgoingCoverage.parse(row.outgoingCoverage),
                gapReferenceIds = decodeIds(row.gapReferenceIdsJson),
            ),
            boundary = boundary(rows, Codecs.unwantedContact.parse(row.unwantedContact)),
            relationshipToPreviousEvents = rows.links.sortedBy { it.id }.map { link ->
                EventRelationship(
                    targetEventId = EventId(link.toEvent),
                    type = Codecs.relationshipType.parse(link.type),
                    basis = Codecs.relationshipBasis.parse(link.basis),
                    confidence = Confidence(
                        link.confidenceValue,
                        Codecs.confidenceSemantics.parse(link.confidenceSemantics),
                        link.calibrationVersion?.let(::ScopeId),
                    ),
                    reviewStatus = Codecs.relationshipReviewStatus.parse(link.reviewStatus),
                )
            },
            retention = Retention(
                mode = Codecs.retentionMode.parse(row.retentionMode),
                expiresAt = row.expiresAt?.let(::Timestamp),
                consentGeneration = row.consentGeneration,
            ),
        )
    }

    private fun category(rows: FindingRows, referenceByAnchor: Map<String, ReferenceId>): CategoryAssessment {
        val finding = rows.finding
        return CategoryAssessment(
            label = Codecs.categoryLabel.parse(finding.label),
            basis = Codecs.categoryBasis.parse(finding.basis),
            confidence = Confidence(
                finding.confidenceValue,
                Codecs.confidenceSemantics.parse(finding.confidenceSemantics),
                finding.calibrationVersion?.let(::ScopeId),
            ),
            producerVersion = ScopeId(finding.producerVersion),
            evidenceReferenceIds = rows.anchorIds.mapNotNull { referenceByAnchor[it] },
            reviewStatus = Codecs.categoryReviewStatus.parse(finding.reviewStatusAtImport),
        )
    }

    private fun reference(anchor: EvidenceAnchorEntity): EvidenceReference = EvidenceReference(
        referenceId = ReferenceId(anchor.referenceId),
        artifactId = ArtifactId(anchor.artifactId),
        sha256 = anchor.sha256,
        representation = Codecs.representation.parse(anchor.representation),
        locator = locator(anchor),
    )

    private fun locator(anchor: EvidenceAnchorEntity): Locator = when (anchor.locatorKind) {
        LOCATOR_WHOLE_ARTIFACT -> Locator.WholeArtifact
        LOCATOR_TEXT -> Locator.Text(
            checkNotNull(anchor.startCp) { "Stored text locator has no start" },
            checkNotNull(anchor.endCp) { "Stored text locator has no end" },
        )
        LOCATOR_AUDIO_TIME -> Locator.AudioTime(
            checkNotNull(anchor.startMs) { "Stored audio locator has no start" },
            checkNotNull(anchor.endMs) { "Stored audio locator has no end" },
        )
        LOCATOR_REGION -> Locator.ImageOrPageRegion(
            anchor.pageIndex,
            ScopeId(checkNotNull(anchor.regionId) { "Stored region locator has no region id" }),
        )
        else -> error("Stored locator kind is not supported")
    }

    private fun boundary(rows: RevisionRows, unwanted: UnwantedContact): Boundary {
        val stored = rows.boundary
        return if (stored == null) {
            Boundary(
                BoundaryMarker.NONE,
                null,
                BoundaryReviewStatus.NOT_APPLICABLE,
                CommunicationStatus.NOT_APPLICABLE,
                unwanted,
            )
        } else {
            Boundary(
                Codecs.boundaryMarker.parse(stored.marker),
                stored.actorId?.let(::ActorId),
                Codecs.boundaryReviewStatus.parse(stored.reviewStatus),
                Codecs.communicationStatus.parse(stored.communicationStatus),
                unwanted,
            )
        }
    }

    private fun decodeIds(json: String): List<ReferenceId> =
        Json.parseToJsonElement(json).jsonArray.map { ReferenceId(it.jsonPrimitive.content) }
}
