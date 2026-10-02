package org.sakshi.core.vault

import androidx.room.withTransaction
import java.io.InputStream
import java.nio.file.NoSuchFileException
import java.security.GeneralSecurityException
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.sakshi.core.crypto.BlobIntegrityException
import org.sakshi.core.crypto.BlobReader
import org.sakshi.core.database.CaptureMetadataEntity
import org.sakshi.core.database.CaseStatus
import org.sakshi.core.database.EvidenceBlobEntity
import org.sakshi.core.database.EvidenceEntity
import org.sakshi.core.database.EvidenceListItem
import org.sakshi.core.database.EvidenceStateEntity
import org.sakshi.core.database.ProviderTransform
import org.sakshi.core.database.SakshiDatabase
import org.sakshi.core.database.SupportState
import org.sakshi.core.integrity.Sha256

/**
 * What is being imported and where it came from. Claims such as [displayNameClaim] and [uriAuthorityClaim]
 * are recorded as provided by the source and are not trusted.
 */
public class ImportRequest(
    public val caseId: String,
    public val acquisitionKind: String,
    public val accessClass: String,
    public val importerMechanism: String,
    public val declaredMime: String?,
    public val claimedOrigin: String?,
    public val displayNameClaim: String?,
    public val uriAuthorityClaim: String?,
    public val maxPlaintextBytes: Long,
)

/** Identity of a stored original. [sha256] is lower-case hex of the received bytes. */
public data class ImportedEvidence(
    val id: String,
    val sha256: String,
    val byteSize: Long,
    val detectedMime: String?,
)

/** Why an original could not be checked. */
public enum class UnreadableReason { MISSING_FILE, AUTHENTICATION_FAILED, KEY_UNAVAILABLE }

/** Result of [EvidenceRepository.verify]. */
public sealed interface VerificationResult {
    /** Every chunk authenticated and the digest equals the stored one. */
    public data object Intact : VerificationResult

    /** The bytes authenticated but their digest differs from the stored one. */
    public data object HashMismatch : VerificationResult

    public data class Unreadable(val reason: UnreadableReason) : VerificationResult
}

/** Imports, verifies and deletes evidence. Originals are written once and never modified. */
public class EvidenceRepository(
    private val database: SakshiDatabase,
    private val blobs: BlobStore,
    private val audit: AuditLog,
    private val clock: () -> Instant,
    private val ids: () -> String = { UUID.randomUUID().toString() },
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /**
     * Encrypts [input] into the vault and records it. Does not close [input]. If the database transaction
     * fails the blob file is removed again, so a failed import leaves neither file nor rows.
     *
     * @throws IllegalArgumentException for an unknown case or an unknown acquisition kind or access class.
     * @throws IllegalStateException if the case is archived.
     * @throws org.sakshi.core.crypto.BlobTooLargeException if the input exceeds the limit.
     */
    public suspend fun import(request: ImportRequest, input: InputStream): ImportedEvidence {
        require(request.acquisitionKind in AcquisitionKind.all) { "Unknown acquisition kind" }
        require(request.accessClass in AccessClass.all) { "Unknown access class" }
        requireActiveCase(request.caseId)
        val sniffing = HeadCapturingInputStream(input)
        val stored = withContext(dispatcher) { blobs.write(sniffing, request.maxPlaintextBytes) }
        var committed = false
        try {
            val imported = record(request, stored, MimeSniffer.detect(sniffing.head()))
            committed = true
            return imported
        } finally {
            if (!committed) withContext(dispatcher) { blobs.delete(stored.relativePath) }
        }
    }

    /** Ids of evidence in the case with identical bytes, so the UI can warn about a possible duplicate. */
    public suspend fun findSameBytes(caseId: String, sha256: String): List<String> =
        database.evidenceDao().findBySha256(caseId, sha256).map { it.id }

    /** Opens the decrypted original for reading. The caller closes the reader. */
    public suspend fun openOriginal(evidenceId: String): BlobReader {
        val blob = requireNotNull(database.evidenceDao().getBlob(evidenceId)) { "Unknown evidence" }
        return withContext(dispatcher) {
            blobs.open(blob.path, BlobStore.blobIdOf(blob.path), blob.wrappedKey)
        }
    }

    /**
     * Decrypts the whole original and compares its digest with the stored one. Records the outcome in the audit
     * log. Never deletes or replaces anything, whatever the outcome.
     */
    public suspend fun verify(evidenceId: String): VerificationResult {
        val evidence = requireNotNull(database.evidenceDao().get(evidenceId)) { "Unknown evidence" }
        val result = withContext(dispatcher) { check(evidence) }
        audit.append(
            AuditActions.EVIDENCE_VERIFIED,
            SUBJECT_TYPE,
            evidenceId,
            jsonObjectOf("outcome" to outcomeName(result)),
        )
        return result
    }

    /** Removes the evidence and all that depends on it, then the blob file. */
    public suspend fun delete(evidenceId: String) {
        val path = database.withTransaction {
            val evidence = requireNotNull(database.evidenceDao().get(evidenceId)) { "Unknown evidence" }
            val blobPath = database.evidenceDao().getBlob(evidenceId)?.path
            database.evidenceDao().deleteWithDependants(evidenceId)
            audit.append(
                AuditActions.EVIDENCE_DELETED,
                SUBJECT_TYPE,
                evidenceId,
                jsonObjectOf("evidence_id" to evidenceId, "case_id" to evidence.caseId),
            )
            blobPath
        }
        if (path != null) withContext(dispatcher) { blobs.delete(path) }
    }

    public fun observeForCase(caseId: String): Flow<List<EvidenceListItem>> =
        database.evidenceDao().observeForCase(caseId)

    private suspend fun requireActiveCase(caseId: String) {
        val case = requireNotNull(database.caseDao().get(caseId)) { "Unknown case" }
        check(case.status == CaseStatus.ACTIVE) { "Case is archived" }
    }

    private suspend fun record(request: ImportRequest, stored: StoredBlob, detectedMime: String?): ImportedEvidence {
        val evidenceId = ids()
        val sha256 = Sha256.hex(stored.result.plaintextSha256)
        val size = stored.result.plaintextLength
        val now = clock()
        val evidence = EvidenceEntity(
            id = evidenceId,
            caseId = request.caseId,
            acquisitionKind = request.acquisitionKind,
            accessClass = request.accessClass,
            receivedAt = now.toString(),
            receivedAtEpochMs = now.toEpochMilli(),
            claimedOrigin = request.claimedOrigin,
            declaredMime = request.declaredMime,
            detectedMime = detectedMime,
            byteSize = size,
            sha256 = sha256,
            retentionMode = RETENTION_CONFIRMED_VAULT,
        )
        val blob = EvidenceBlobEntity(
            evidenceId = evidenceId,
            path = stored.relativePath,
            envelopeVersion = ENVELOPE_VERSION,
            wrappedKey = stored.wrappedKey,
            chunkSize = stored.chunkSize,
            chunkCount = stored.result.chunkCount,
            plaintextLength = size,
        )
        val metadata = CaptureMetadataEntity(
            evidenceId = evidenceId,
            importerMechanism = request.importerMechanism,
            uriAuthorityClaim = request.uriAuthorityClaim,
            displayNameClaim = request.displayNameClaim,
            exifJson = null,
            providerTransform = ProviderTransform.UNKNOWN,
            collectorSessionId = null,
            elapsedRealtimeMs = null,
        )
        database.withTransaction {
            requireActiveCase(request.caseId)
            database.evidenceDao().insertFull(
                evidence,
                blob,
                metadata,
                EvidenceStateEntity(evidenceId, SupportState.SAVED, now.toEpochMilli()),
            )
            audit.append(
                AuditActions.EVIDENCE_IMPORTED,
                SUBJECT_TYPE,
                evidenceId,
                jsonObjectOf(
                    "evidence_id" to evidenceId,
                    "case_id" to request.caseId,
                    "sha256" to sha256,
                    "byte_size" to size,
                    "acquisition_kind" to request.acquisitionKind,
                ),
            )
        }
        return ImportedEvidence(evidenceId, sha256, size, detectedMime)
    }

    private suspend fun check(evidence: EvidenceEntity): VerificationResult {
        val blob = database.evidenceDao().getBlob(evidence.id)
            ?: return VerificationResult.Unreadable(UnreadableReason.MISSING_FILE)
        return try {
            blobs.open(blob.path, BlobStore.blobIdOf(blob.path), blob.wrappedKey).use { reader ->
                if (Sha256.hex(reader.verifyAll()) == evidence.sha256) {
                    VerificationResult.Intact
                } else {
                    VerificationResult.HashMismatch
                }
            }
        } catch (_: NoSuchFileException) {
            VerificationResult.Unreadable(UnreadableReason.MISSING_FILE)
        } catch (_: BlobIntegrityException) {
            VerificationResult.Unreadable(UnreadableReason.AUTHENTICATION_FAILED)
        } catch (_: GeneralSecurityException) {
            VerificationResult.Unreadable(UnreadableReason.KEY_UNAVAILABLE)
        }
    }

    private fun outcomeName(result: VerificationResult): String = when (result) {
        VerificationResult.Intact -> "intact"
        VerificationResult.HashMismatch -> "hash_mismatch"
        is VerificationResult.Unreadable -> result.reason.name.lowercase()
    }

    private companion object {
        const val SUBJECT_TYPE = "evidence"
        const val ENVELOPE_VERSION = 1
    }
}
