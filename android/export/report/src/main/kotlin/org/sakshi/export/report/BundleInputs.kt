package org.sakshi.export.report

import org.sakshi.core.model.Event
import org.sakshi.export.bundle.Correction
import org.sakshi.export.bundle.Finding
import org.sakshi.export.bundle.OmittedCounts
import org.sakshi.export.bundle.Pattern
import org.sakshi.export.bundle.RedactionSummary

/** The parts of a bundle that the report builder already knows. The export service adds files and provenance. */
public data class BundleInputs(
    val caseId: String,
    /** Latest revisions of the selected events. */
    val events: List<Event>,
    val findings: List<Finding>,
    val corrections: List<Correction>,
    /** Patterns with at least one supporting event; others appear in the report only. */
    val patterns: List<Pattern>,
    val omitted: OmittedCounts,
    /** Hex SHA-256 chain head read once, so the report and the manifest agree. */
    val auditChainHead: String,
    val includeOriginalsFor: Set<String>,
    /** Counts of removed text, and whether an included original still holds it. */
    val redactions: RedactionSummary = RedactionSummary.NONE,
    /** One copy per redacted quote, with the removed parts replaced. Goes into `derivatives/`. */
    val redactedCopies: List<RedactedCopy> = emptyList(),
)

/** The text of one quote after removal, as it is stored in the bundle. It never holds the removed text. */
public data class RedactedCopy(
    /** File name in `derivatives/`. */
    val opaqueId: String,
    /** The artefact the quote was read from. */
    val artifactId: String,
    val text: String,
)

/** Outcome of [ReportBuilder.build]. */
public sealed interface ReportBuildResult {
    public data class Built(val model: ReportModel, val bundleInputs: BundleInputs) : ReportBuildResult {
        /** [ReportFingerprint] of [model]: what the person reads, without build time and signing values. */
        val contentSha256: String by lazy { ReportFingerprint.of(model) }

        /**
         * True when an original file that goes into the bundle is the source of text the person removed, so the
         * original still contains it. The app shows this before export; the report and bundle carry it too.
         */
        val includedOriginalHoldsRemovedText: Boolean get() = bundleInputs.redactions.originalMayHoldRemovedContent
    }

    public data class Refused(val reason: RefusalReason) : ReportBuildResult
}
