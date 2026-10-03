package org.sakshi.export.report

import java.time.Instant
import org.sakshi.core.integrity.Sha256

/**
 * A digest of everything a person reads in the report: every model field except when it was built and the
 * integrity values that only exist once the bundle is signed (manifest hash, Merkle root, key id, audit head).
 * Two builds with the same digest render the same content, so an export can be checked against its preview.
 */
public object ReportFingerprint {
    public fun of(model: ReportModel): String {
        val reviewed = model.copy(
            generatedAt = Instant.EPOCH,
            integrity = model.integrity.copy(manifestHash = null, merkleRoot = null, signerKeyId = null, auditChainHead = ""),
        )
        return Sha256.hex(Sha256.digest(reviewed.toString().toByteArray(Charsets.UTF_8)))
    }
}
