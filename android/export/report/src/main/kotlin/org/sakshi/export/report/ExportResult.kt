package org.sakshi.export.report

import java.io.File
import org.sakshi.export.bundle.OmittedCounts

/** Why an export that passed the selection checks still failed. Nothing is left in the cache in these cases. */
public enum class ExportFailure {
    /** The finished bundle did not verify as consistent, so it was deleted. */
    VERIFICATION_FAILED,

    /** The bundle writer rejected the content. */
    CONTENT_REJECTED,

    /** A selected original is missing or cannot be decrypted. */
    ORIGINAL_UNAVAILABLE,

    /** A selected original decrypts but its bytes no longer match the hash stored when it was imported. */
    ORIGINAL_HASH_MISMATCH,

    /** The PDF could not be produced. */
    RENDER_FAILED,

    /** The signing key could not be used. */
    SIGNING_FAILED,

    /** A file could not be written. */
    IO_ERROR,
}

/** Counts that describe an exported bundle. No text from the case. */
public data class ExportSummary(
    val merkleRoot: String,
    val fileCount: Int,
    val pageCount: Int,
    val eventCount: Int,
    val findingCount: Int,
    val patternCount: Int,
    val originalCount: Int,
    val omitted: OmittedCounts,
    val zipBytes: Long,
    /** Records with text removed, and the passages removed from them. */
    val redactedEventCount: Int = 0,
    val redactedPassageCount: Int = 0,
    /** True when an included original still holds text that was removed elsewhere in the bundle. */
    val includedOriginalHoldsRemovedText: Boolean = false,
)

/** Outcome of [ExportService.export]. */
public sealed interface ExportResult {
    public data class Exported(
        val zipFile: File,
        val snapshotId: String,
        /** The report version this export is stored as, printed in the PDF footer. */
        val reportVersion: Int,
        val signerKeyId: String,
        val summary: ExportSummary,
    ) : ExportResult

    public data class Refused(val reason: RefusalReason) : ExportResult

    public data class Failed(val reason: ExportFailure) : ExportResult
}
