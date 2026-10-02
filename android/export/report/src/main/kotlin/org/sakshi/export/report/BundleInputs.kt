package org.sakshi.export.report

import org.sakshi.core.model.Event
import org.sakshi.export.bundle.Correction
import org.sakshi.export.bundle.Finding
import org.sakshi.export.bundle.OmittedCounts
import org.sakshi.export.bundle.Pattern

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
)

/** Outcome of [ReportBuilder.build]. */
public sealed interface ReportBuildResult {
    public data class Built(val model: ReportModel, val bundleInputs: BundleInputs) : ReportBuildResult

    public data class Refused(val reason: RefusalReason) : ReportBuildResult
}
