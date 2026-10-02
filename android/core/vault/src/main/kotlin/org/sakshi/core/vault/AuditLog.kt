package org.sakshi.core.vault

import java.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.sakshi.core.database.AuditRecordEntity
import org.sakshi.core.database.SakshiDatabase
import org.sakshi.core.integrity.CanonicalJson
import org.sakshi.core.integrity.HashChain
import org.sakshi.core.integrity.Sha256

/** Names of audited actions. */
public object AuditActions {
    public const val CASE_CREATED: String = "case.created"
    public const val CASE_RENAMED: String = "case.renamed"
    public const val CASE_ARCHIVED: String = "case.archived"
    public const val CASE_DELETED: String = "case.deleted"
    public const val EVIDENCE_IMPORTED: String = "evidence.imported"
    public const val EVIDENCE_DELETED: String = "evidence.deleted"
    public const val EVIDENCE_VERIFIED: String = "evidence.verified"
    public const val VAULT_OPENED: String = "vault.opened"
}

/** Outcome of walking the audit chain. */
public sealed interface AuditVerification {
    /** Every record links to its predecessor. [head] is the final chain value. */
    public class Valid(public val count: Int, head: ByteArray) : AuditVerification {
        private val headBytes: ByteArray = head.copyOf()
        public val head: ByteArray get() = headBytes.copyOf()

        override fun equals(other: Any?): Boolean =
            other is Valid && count == other.count && headBytes.contentEquals(other.headBytes)

        override fun hashCode(): Int = 31 * count + headBytes.contentHashCode()
    }

    /** The record with sequence number [atSeq] does not match the chain. */
    public data class Broken(public val atSeq: Long) : AuditVerification
}

/** Pure chain arithmetic, kept separate so it can be tested without a database. */
internal object AuditChain {
    fun verify(rows: List<AuditRecordEntity>): AuditVerification {
        var previous = HashChain.genesis()
        for (row in rows) {
            val digest = try {
                Sha256.fromHex(row.payloadSha256)
            } catch (_: IllegalArgumentException) {
                return AuditVerification.Broken(row.seq)
            }
            val expected = HashChain.next(previous, digest)
            if (!row.prevHash.contentEquals(previous) || !row.thisHash.contentEquals(expected)) {
                return AuditVerification.Broken(row.seq)
            }
            previous = row.thisHash
        }
        return AuditVerification.Valid(rows.size, previous)
    }
}

/**
 * Tamper-evident log of vault actions.
 *
 * The canonical payload is `{"action","at","details","subject_id","subject_type"}`. Only its SHA-256 is stored,
 * so the chain is defined over that stored digest and can be re-verified from the table alone:
 * `this_hash = HashChain.next(prev_hash, payload_sha256 bytes)`, starting from [HashChain.genesis].
 *
 * The chain detects edits, reordering and removal of records that have a successor. It cannot detect removal
 * of the newest records unless the head has been recorded elsewhere.
 */
public open class AuditLog(private val database: SakshiDatabase, private val clock: () -> Instant) {
    /**
     * Appends a record and returns its sequence number. [details] must contain only ids, counts, enum strings
     * and hashes of stored bytes - never evidence text, titles or user-supplied names, which would be
     * unrecoverable plaintext metadata in a log that can never be edited.
     */
    public open suspend fun append(
        action: String,
        subjectType: String,
        subjectId: String,
        details: JsonObject = JsonObject(emptyMap()),
    ): Long {
        val now = clock()
        val at = now.toString()
        val payload = JsonObject(
            mapOf(
                "action" to JsonPrimitive(action),
                "at" to JsonPrimitive(at),
                "details" to details,
                "subject_id" to JsonPrimitive(subjectId),
                "subject_type" to JsonPrimitive(subjectType),
            ),
        )
        val digest = Sha256.digest(CanonicalJson.encode(payload))
        return database.auditDao().appendNext { previous ->
            val previousHash = previous?.thisHash ?: HashChain.genesis()
            AuditRecordEntity(
                at = at,
                atEpochMs = now.toEpochMilli(),
                action = action,
                subjectType = subjectType,
                subjectId = subjectId,
                payloadSha256 = Sha256.hex(digest),
                prevHash = previousHash,
                thisHash = HashChain.next(previousHash, digest),
            )
        }
    }

    /** Re-walks every record. */
    public suspend fun verify(): AuditVerification = AuditChain.verify(database.auditDao().all())

    /** The current chain head, or the genesis value when the log is empty. */
    public suspend fun head(): ByteArray = database.auditDao().last()?.thisHash ?: HashChain.genesis()
}
