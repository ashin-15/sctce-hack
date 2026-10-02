package org.sakshi.core.vault

import androidx.room.withTransaction
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.sakshi.core.database.DerivativeEntity
import org.sakshi.core.database.SakshiDatabase
import org.sakshi.core.integrity.Sha256

/** Values of `derivative.kind`. */
public object DerivativeKind {
    public const val PARSED_TEXT: String = "parsed_text"
    public const val OCR: String = "ocr"
    public const val TRANSCRIPT: String = "transcript"
    public const val NORMALISED_VIEW: String = "normalised_view"
    public const val USER_EDIT: String = "user_edit"

    internal val all: Set<String> = setOf(PARSED_TEXT, OCR, TRANSCRIPT, NORMALISED_VIEW, USER_EDIT)
}

/** One immutable revision of text derived from evidence. The original evidence is never changed by it. */
public data class StoredDerivative(
    val id: String,
    val evidenceId: String,
    val kind: String,
    val revision: Int,
    val parentDerivativeId: String?,
    val text: String,
    val toolId: String,
    val toolVersion: String,
    val sourceMapJson: String?,
    val qualityJson: String?,
    val createdAt: String,
)

/**
 * Insert-only store of parsed text, OCR, transcripts, normalised views and user edits. Text is stored exactly
 * as given (no normalisation of line endings or Unicode). Derivatives are removed with their evidence.
 */
public class DerivativeStore(
    private val database: SakshiDatabase,
    private val audit: AuditLog,
    private val clock: () -> Instant,
    private val ids: () -> String,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /**
     * Inserts a new derivative; its revision is the latest revision for this evidence and kind plus one,
     * starting at 1. One transaction with one `derivative.saved` audit row (ids, kind, revision, text hash).
     *
     * @throws IllegalArgumentException for unknown evidence, an unknown kind, or a parent that is not a
     * derivative of the same evidence.
     */
    public suspend fun save(
        evidenceId: String,
        kind: String,
        text: String,
        toolId: String,
        toolVersion: String,
        parentDerivativeId: String? = null,
        sourceMapJson: String? = null,
        qualityJson: String? = null,
    ): StoredDerivative {
        require(kind in DerivativeKind.all) { "Unknown derivative kind" }
        return withContext(dispatcher) {
            database.withTransaction {
                val dao = database.derivativeDao()
                requireNotNull(database.evidenceDao().get(evidenceId)) { "Unknown evidence" }
                if (parentDerivativeId != null) {
                    require(dao.get(parentDerivativeId)?.evidenceId == evidenceId) {
                        "Parent is not a derivative of this evidence"
                    }
                }
                val entity = DerivativeEntity(
                    id = ids(),
                    evidenceId = evidenceId,
                    parentDerivativeId = parentDerivativeId,
                    revision = (dao.getLatest(evidenceId, kind)?.revision ?: 0) + 1,
                    kind = kind,
                    text = text,
                    sourceMapJson = sourceMapJson,
                    qualityJson = qualityJson,
                    toolId = toolId,
                    toolVersion = toolVersion,
                    modelVersionId = null,
                    createdAt = clock().toString(),
                )
                dao.insert(entity)
                audit.append(
                    AuditActions.DERIVATIVE_SAVED,
                    SUBJECT_TYPE,
                    entity.id,
                    jsonObjectOf(
                        "derivative_id" to entity.id,
                        "evidence_id" to evidenceId,
                        "kind" to kind,
                        "revision" to entity.revision,
                        "sha256" to Sha256.hex(Sha256.digest(text.toByteArray(Charsets.UTF_8))),
                    ),
                )
                entity.toStored()
            }
        }
    }

    /** The newest revision of the kind for the evidence, or null if there is none. */
    public suspend fun latest(evidenceId: String, kind: String): StoredDerivative? =
        withContext(dispatcher) { database.derivativeDao().getLatest(evidenceId, kind)?.toStored() }

    public suspend fun get(id: String): StoredDerivative? =
        withContext(dispatcher) { database.derivativeDao().get(id)?.toStored() }

    /** Every derivative of the evidence, by kind and then oldest revision first. */
    public suspend fun listForEvidence(evidenceId: String): List<StoredDerivative> =
        withContext(dispatcher) { database.derivativeDao().getAllForEvidence(evidenceId).map { it.toStored() } }

    /**
     * Code point length of a derivative's text, or null if unknown; suitable as the `artifactLengths` lookup of
     * [EventStore] when anchors use the derivative id as artifact id.
     */
    public suspend fun codePointLength(id: String): Int? = withContext(dispatcher) {
        database.derivativeDao().get(id)?.text?.let { it.codePointCount(0, it.length) }
    }

    private fun DerivativeEntity.toStored(): StoredDerivative = StoredDerivative(
        id, evidenceId, kind, revision, parentDerivativeId, text, toolId, toolVersion, sourceMapJson, qualityJson, createdAt,
    )

    private companion object {
        const val SUBJECT_TYPE = "derivative"
    }
}
