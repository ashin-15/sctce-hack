package org.sakshi.core.vault

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.sakshi.core.database.BoundaryEntity
import org.sakshi.core.database.EpistemicStatus
import org.sakshi.core.database.EventLinkEntity
import org.sakshi.core.database.EventRevisionEntity
import org.sakshi.core.database.EvidenceAnchorEntity
import org.sakshi.core.database.FindingEntity
import org.sakshi.core.model.BoundaryMarker
import org.sakshi.core.model.BoundaryReviewStatus
import org.sakshi.core.model.CategoryAssessment
import org.sakshi.core.model.CategoryBasis
import org.sakshi.core.model.CommunicationStatus
import org.sakshi.core.model.Event
import org.sakshi.core.model.EvidenceReference
import org.sakshi.core.model.Locator
import org.sakshi.core.model.ReferenceId

internal val LOCATOR_WHOLE_ARTIFACT: String = Locator.WholeArtifact.serializer().descriptor.serialName
internal val LOCATOR_TEXT: String = Locator.Text.serializer().descriptor.serialName
internal val LOCATOR_AUDIO_TIME: String = Locator.AudioTime.serializer().descriptor.serialName
internal val LOCATOR_REGION: String = Locator.ImageOrPageRegion.serializer().descriptor.serialName

/** Turns an [Event] into table rows. Reading them back is [EventRowReader]. */
internal object EventRowWriter {
    /**
     * Fields the current tables cannot hold without loss. The caller must refuse the event rather than drop them.
     * - list sizes beyond [RowKeys.MAX_LIST_SIZE], and duplicate ids inside one category's reference list
     *   (the schema forbids both; `finding_anchor` is keyed on the pair).
     */
    fun unsupportedFields(event: Event): List<String> = buildList {
        if (event.categories.any { it.evidenceReferenceIds.toSet().size != it.evidenceReferenceIds.size }) {
            add("categories.evidence_reference_ids")
        }
        if (listOf(event.evidenceReferences.size, event.categories.size, event.relationshipToPreviousEvents.size)
                .any { it > RowKeys.MAX_LIST_SIZE }
        ) {
            add("list_size")
        }
    }

    /** @param evidenceIds artifact id to the stored evidence id, for artifacts held in the same case. */
    fun rows(event: Event, evidenceIds: Map<String, String>, createdAt: String): RevisionRows {
        val eventId = event.eventId.value
        val anchors = event.evidenceReferences.mapIndexed { index, reference ->
            anchor(event, reference, RowKeys.anchor(eventId, event.revision, index), evidenceIds[reference.artifactId.value])
        }
        val anchorByReference = anchors.associate { it.referenceId to it.id }
        return RevisionRows(
            revision = revision(event),
            anchors = anchors,
            findings = event.categories.mapIndexed { index, category ->
                FindingRows(
                    finding(event, category, RowKeys.finding(eventId, event.revision, index), createdAt),
                    category.evidenceReferenceIds.map { anchorByReference.getValue(it.value) },
                )
            },
            links = event.relationshipToPreviousEvents.mapIndexed { index, relationship ->
                EventLinkEntity(
                    id = RowKeys.link(eventId, event.revision, index),
                    fromEvent = eventId,
                    toEvent = relationship.targetEventId.value,
                    type = Codecs.relationshipType.name(relationship.type),
                    basis = Codecs.relationshipBasis.name(relationship.basis),
                    confidenceValue = relationship.confidence.value,
                    confidenceSemantics = Codecs.confidenceSemantics.name(relationship.confidence.semantics),
                    calibrationVersion = relationship.confidence.calibrationVersion?.value,
                    reviewStatus = Codecs.relationshipReviewStatus.name(relationship.reviewStatus),
                )
            },
            boundary = boundary(event),
        )
    }

    private fun revision(event: Event): EventRevisionEntity {
        val bounds = event.timestamp
        return EventRevisionEntity(
            eventId = event.eventId.value,
            revision = event.revision,
            kind = Codecs.eventKind.name(event.eventKind),
            observedAt = event.observedAt.iso,
            availableAt = event.availableAt.iso,
            availableAtEpochMs = event.availableAt.instant.toEpochMilli(),
            tsEarliest = bounds.earliest?.iso,
            tsEarliestEpochMs = bounds.earliest?.instant?.toEpochMilli(),
            tsLatest = bounds.latest?.iso,
            tsLatestEpochMs = bounds.latest?.instant?.toEpochMilli(),
            tsBasis = Codecs.timeBasis.name(bounds.basis),
            tsPrecision = Codecs.timePrecision.name(bounds.precision),
            tsTimezone = bounds.sourceTimezone,
            collectorSessionId = bounds.collectorSessionId?.value,
            monotonicMs = bounds.monotonicMs,
            actorId = event.sender.actorId?.value,
            sourceScopeId = null,
            senderDisplayLabel = event.sender.displayLabel,
            senderIdentityBasis = Codecs.identityBasis.name(event.sender.identityBasis),
            senderAssociationReview = Codecs.associationReview.name(event.sender.associationReview),
            sourceKind = Codecs.sourceKind.name(event.source.kind),
            sourceApp = event.source.sourceApp,
            profileScopeId = event.source.profileScopeId?.value,
            conversationScopeId = event.source.conversationScopeId?.value,
            sourceRecordId = event.source.sourceRecordId?.value,
            parserVersion = event.source.parserVersion.value,
            direction = Codecs.direction.name(event.direction),
            reviewPriority = Codecs.reviewPriority.name(event.severity.reviewPriority),
            severityBasis = Codecs.severityBasis.name(event.severity.basis),
            severityReferenceIdsJson = encodeIds(event.severity.evidenceReferenceIds),
            confirmationStatus = Codecs.confirmationStatus.name(event.userConfirmation.status),
            confirmationScope = Codecs.confirmationScope.name(event.userConfirmation.scope),
            reviewedAt = event.userConfirmation.reviewedAt?.iso,
            dedupStatus = Codecs.dedupStatus.name(event.deduplication.status),
            canonicalEventId = event.deduplication.canonicalEventId?.value,
            dedupMethod = event.deduplication.methodVersion.value,
            coverageContext = Codecs.coverageContext.name(event.coverage.context),
            textStatus = Codecs.textStatus.name(event.coverage.textStatus),
            outgoingCoverage = Codecs.outgoingCoverage.name(event.coverage.outgoingCoverage),
            gapReferenceIdsJson = encodeIds(event.coverage.gapReferenceIds),
            unwantedContact = Codecs.unwantedContact.name(event.boundary.unwantedContact),
            retentionMode = Codecs.retentionMode.name(event.retention.mode),
            expiresAt = event.retention.expiresAt?.iso,
            consentGeneration = event.retention.consentGeneration,
        )
    }

    private fun anchor(event: Event, reference: EvidenceReference, id: String, evidenceId: String?): EvidenceAnchorEntity {
        val locator = reference.locator
        return EvidenceAnchorEntity(
            id = id,
            eventId = event.eventId.value,
            eventRevision = event.revision,
            referenceId = reference.referenceId.value,
            artifactId = reference.artifactId.value,
            evidenceId = evidenceId,
            derivativeId = null,
            sha256 = reference.sha256,
            representation = Codecs.representation.name(reference.representation),
            locatorKind = when (locator) {
                Locator.WholeArtifact -> LOCATOR_WHOLE_ARTIFACT
                is Locator.Text -> LOCATOR_TEXT
                is Locator.AudioTime -> LOCATOR_AUDIO_TIME
                is Locator.ImageOrPageRegion -> LOCATOR_REGION
            },
            startCp = (locator as? Locator.Text)?.start,
            endCp = (locator as? Locator.Text)?.end,
            startMs = (locator as? Locator.AudioTime)?.startMs,
            endMs = (locator as? Locator.AudioTime)?.endMs,
            pageIndex = (locator as? Locator.ImageOrPageRegion)?.pageIndex,
            regionId = (locator as? Locator.ImageOrPageRegion)?.regionId?.value,
        )
    }

    private fun finding(event: Event, category: CategoryAssessment, id: String, createdAt: String): FindingEntity =
        FindingEntity(
            id = id,
            caseId = event.caseId.value,
            eventId = event.eventId.value,
            eventRevision = event.revision,
            label = Codecs.categoryLabel.name(category.label),
            sourceLabel = null,
            basis = Codecs.categoryBasis.name(category.basis),
            confidenceValue = category.confidence.value,
            confidenceSemantics = Codecs.confidenceSemantics.name(category.confidence.semantics),
            calibrationVersion = category.confidence.calibrationVersion?.value,
            producerVersion = category.producerVersion.value,
            modelVersionId = null,
            epistemicStatus = if (category.basis == CategoryBasis.USER_TAG) {
                EpistemicStatus.USER_REPORTED
            } else {
                EpistemicStatus.INFERRED
            },
            createdAt = createdAt,
            reviewStatusAtImport = Codecs.categoryReviewStatus.name(category.reviewStatus),
        )

    /** A row exists whenever the boundary carries anything beyond the plain "no boundary" values. */
    private fun boundary(event: Event): BoundaryEntity? {
        val boundary = event.boundary
        val plain = boundary.marker == BoundaryMarker.NONE && boundary.actorId == null &&
            boundary.reviewStatus == BoundaryReviewStatus.NOT_APPLICABLE &&
            boundary.communicationStatus == CommunicationStatus.NOT_APPLICABLE
        if (plain) return null
        return BoundaryEntity(
            id = RowKeys.boundary(event.eventId.value, event.revision),
            caseId = event.caseId.value,
            eventId = event.eventId.value,
            marker = Codecs.boundaryMarker.name(boundary.marker),
            actorId = boundary.actorId?.value,
            reviewStatus = Codecs.boundaryReviewStatus.name(boundary.reviewStatus),
            communicationStatus = Codecs.communicationStatus.name(boundary.communicationStatus),
            scopeNote = null,
        )
    }

    private fun encodeIds(ids: List<ReferenceId>): String = JsonArray(ids.map { JsonPrimitive(it.value) }).toString()
}
