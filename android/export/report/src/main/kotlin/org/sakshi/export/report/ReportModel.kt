package org.sakshi.export.report

import java.time.Instant
import org.sakshi.core.model.EpistemicStatus
import org.sakshi.export.bundle.GeneratorInfo

/** Everything the PDF prints, as plain data. No Android types. */
public data class ReportModel(
    val title: String,
    val reportVersion: Int,
    val generatedAt: Instant,
    val generator: GeneratorInfo,
    val scope: ScopeStatement,
    val timeline: List<TimelineRow>,
    val events: List<EventBlock>,
    val patterns: List<PatternBlock>,
    val unknowns: List<String>,
    val integrity: IntegrityAppendix,
)

/** What was selected and what was left out, one sentence per line. */
public data class ScopeStatement(val lines: List<String>)

/** One line of the timeline. Basis and precision are stated in words, never implied. */
public data class TimelineRow(
    val eventId: String,
    val timeText: String,
    /** For example "as written in the export, to the minute". */
    val timeBasis: String,
    val sender: String,
    /** For example "name as it appears in the export, not confirmed". */
    val identityBasis: String,
    val direction: String,
    val sourceKind: String,
)

/** A statement in the report together with how it is known. The status is fixed by the part's type. */
public sealed interface ReportPart {
    public val status: EpistemicStatus
}

/** Text present in a preserved artefact, quoted verbatim. */
public data class ObservedPart(
    /** Null when the text could not be resolved. */
    val quote: String?,
    val artifactId: String,
    val sha256: String?,
    val locator: String,
    val representation: String,
    /** Number of passages removed from [quote] by the person who made the report; zero when the quote is whole. */
    val redactedPassages: Int = 0,
) : ReportPart {
    override val status: EpistemicStatus get() = EpistemicStatus.OBSERVED
}

/** The user's own statement, as written. */
public data class UserStatementPart(
    val text: String?,
    val artifactId: String,
    val writtenAt: String,
    val locator: String,
    /** Number of passages removed from [text]; zero when the text is whole. */
    val redactedPassages: Int = 0,
) : ReportPart {
    override val status: EpistemicStatus get() = EpistemicStatus.USER_REPORTED
}

/**
 * A tag on an event. [status] is inferred for rule or model suggestions and user-reported for tags the user
 * made themselves.
 */
public data class TagPart(
    override val status: EpistemicStatus,
    val label: String,
    val basis: String,
    val producerVersion: String,
    val confidence: String,
    val reviewStatus: String,
) : ReportPart

/** One included event with observed, stated and inferred material kept apart. */
public data class EventBlock(
    val eventId: String,
    val revision: Int,
    val heading: String,
    /** Record id, time and sender lines. */
    val details: List<String>,
    val observed: List<ObservedPart>,
    val userStatements: List<UserStatementPart>,
    /** Tags the user accepted. */
    val inferred: List<TagPart>,
    /** Suggestions the user has not accepted; empty unless the option to list them is on. */
    val unreviewed: List<TagPart>,
)

/** A described pattern over the selected events only. */
public data class PatternBlock(
    val id: String,
    val heading: String,
    /** The assessment in words, for example "candidate, needs review". */
    val assessment: String,
    val observed: String,
    val interpretation: String?,
    val limitations: List<String>,
    val supportingEventIds: List<String>,
    val ruleVersion: String,
) : ReportPart {
    override val status: EpistemicStatus get() = EpistemicStatus.PATTERN
}

/**
 * Integrity facts. The manifest hash and Merkle root are null because the report is listed in the manifest, so
 * its own bytes cannot contain them; they are shown by the app and in manifest.json.
 */
public data class IntegrityAppendix(
    val manifestHash: String?,
    val merkleRoot: String?,
    val signerKeyId: String?,
    val auditChainHead: String,
    val limits: List<String>,
    val derivativeNote: String,
)
