package org.sakshi.core.vault

import androidx.room.withTransaction
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.sakshi.core.database.ActorEntity
import org.sakshi.core.database.SakshiDatabase
import org.sakshi.core.database.SakshiSchema
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.AssociationReview
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.IdentityBasis

/** A case-scoped actor. The label is what the user typed; it is an alias, not an authenticated identity. */
public data class StoredActor(
    val id: ActorId,
    val caseId: CaseId,
    val displayLabel: String,
    val identityBasis: IdentityBasis,
    val associationReview: AssociationReview,
)

/** Creates and lists the actors that events refer to. Labels are never written to the audit log. */
public class ActorRegistry(
    private val database: SakshiDatabase,
    private val audit: AuditLog,
    private val ids: () -> String = { UUID.randomUUID().toString() },
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /**
     * Creates an actor in the case. A fresh id is generated unless [actorId] is given.
     *
     * @throws IllegalArgumentException for an unknown case or a blank or over-long label.
     */
    public suspend fun create(
        caseId: CaseId,
        displayLabel: String,
        identityBasis: IdentityBasis,
        associationReview: AssociationReview,
        actorId: ActorId = ActorId(ids()),
    ): ActorId {
        require(displayLabel.isNotBlank() && displayLabel.length <= MAX_LABEL_LENGTH) {
            "Label must be 1..$MAX_LABEL_LENGTH characters"
        }
        val entity = ActorEntity(
            actorId.value,
            caseId.value,
            displayLabel,
            Codecs.identityBasis.name(identityBasis),
            Codecs.associationReview.name(associationReview),
        )
        withContext(dispatcher) {
            database.withTransaction {
                requireNotNull(database.caseDao().get(caseId.value)) { "Unknown case" }
                database.eventDao().insertActor(entity)
                audit.append(
                    AuditActions.ACTOR_CREATED,
                    SUBJECT_TYPE,
                    entity.id,
                    jsonObjectOf(
                        "actor_id" to entity.id,
                        "case_id" to entity.caseId,
                        "identity_basis" to entity.identityBasis,
                        "association_review" to entity.associationReview,
                    ),
                )
            }
        }
        return actorId
    }

    /**
     * Changes the label of an actor, marks the case's stored patterns stale (their wording names the person) and audits
     * `actor.renamed` with ids only. Deleting an actor is not offered:
     * events and boundaries refer to actors by foreign key, so the database refuses it while any do.
     *
     * @throws IllegalArgumentException for an unknown actor or a blank or over-long label.
     */
    public suspend fun rename(actorId: ActorId, displayLabel: String) {
        require(displayLabel.isNotBlank() && displayLabel.length <= MAX_LABEL_LENGTH) {
            "Label must be 1..$MAX_LABEL_LENGTH characters"
        }
        withContext(dispatcher) {
            database.withTransaction {
                val actor = requireNotNull(database.eventDao().getActor(actorId.value)) { "Unknown actor" }
                if (actor.displayLabel == displayLabel) return@withTransaction
                database.eventDao().updateActor(actor.copy(displayLabel = displayLabel))
                database.patternDao().markStaleForCase(actor.caseId)
                audit.append(
                    AuditActions.ACTOR_RENAMED,
                    SUBJECT_TYPE,
                    actor.id,
                    jsonObjectOf("actor_id" to actor.id, "case_id" to actor.caseId),
                )
            }
        }
    }

    /** Emits the actors of the case, by id, now and after every change. */
    public fun observe(caseId: CaseId): Flow<List<StoredActor>> =
        database.invalidationTracker.createFlow(SakshiSchema.ACTOR).map { list(caseId) }.distinctUntilChanged()

    /** Every actor of the case, by id. */
    public suspend fun list(caseId: CaseId): List<StoredActor> = withContext(dispatcher) {
        database.eventDao().getActors(caseId.value).map {
            StoredActor(
                ActorId(it.id),
                CaseId(it.caseId),
                it.displayLabel,
                Codecs.identityBasis.parse(it.identityBasis),
                Codecs.associationReview.parse(it.associationReview),
            )
        }
    }

    internal companion object {
        private const val SUBJECT_TYPE = "actor"
        const val MAX_LABEL_LENGTH: Int = 256
    }
}
