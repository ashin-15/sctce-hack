package org.sakshi.app.report

import org.sakshi.core.model.EpistemicStatus
import org.sakshi.export.report.EventBlock
import org.sakshi.export.report.IntegrityAppendix
import org.sakshi.export.report.ObservedPart
import org.sakshi.export.report.PatternBlock
import org.sakshi.export.report.ReportModel
import org.sakshi.export.report.ReportPart
import org.sakshi.export.report.ReportText
import org.sakshi.export.report.TagPart
import org.sakshi.export.report.TimelineRow
import org.sakshi.export.report.UserStatementPart

/** Which part of the report model a [PreviewRow.Block] shows. */
enum class BlockKind { OBSERVED, USER_STATEMENT, ACCEPTED_TAG, USER_TAG, UNREVIEWED_SUGGESTION, PATTERN, UNKNOWNS }

/**
 * One row of the on-screen preview. The words come from the report model and from [ReportText], the same sources
 * the PDF uses, so the preview and the file say the same things.
 */
sealed interface PreviewRow {
    val key: String

    data class Heading(override val key: String, val text: String, val large: Boolean) : PreviewRow

    data class Lines(override val key: String, val lines: List<String>, val small: Boolean = false) : PreviewRow

    /**
     * A statement framed by [status]. [quote] is text from the saved records, shown exactly as saved; [lines] are the
     * report's own sentences about it.
     */
    data class Block(
        override val key: String,
        val kind: BlockKind,
        val status: EpistemicStatus,
        val title: String?,
        val quote: String?,
        val lines: List<String>,
    ) : PreviewRow
}

/** Pure mapping from the report model to preview rows. Every field of the model is mapped; none is dropped. */
object ReportPreviewMapping {
    fun rows(model: ReportModel): List<PreviewRow> = buildList {
        add(PreviewRow.Heading("title", model.title, large = true))
        add(PreviewRow.Lines("cover", cover(model)))
        add(PreviewRow.Heading("scope-heading", ReportText.SECTION_SCOPE, large = true))
        add(PreviewRow.Lines("scope", model.scope.lines))
        add(PreviewRow.Heading("timeline-heading", ReportText.SECTION_TIMELINE, large = true))
        model.timeline.forEachIndexed { index, row -> add(PreviewRow.Lines("timeline-$index", listOf(timelineLine(row)))) }
        add(PreviewRow.Heading("records-heading", ReportText.SECTION_RECORDS, large = true))
        model.events.forEach { addAll(event(it)) }
        add(PreviewRow.Heading("patterns-heading", ReportText.SECTION_PATTERNS, large = true))
        if (model.patterns.isEmpty()) add(PreviewRow.Lines("patterns-none", listOf(ReportText.PATTERNS_NONE)))
        model.patterns.forEach { add(pattern(it)) }
        add(PreviewRow.Heading("unknown-heading", ReportText.SECTION_UNKNOWN, large = true))
        add(unknowns(model.unknowns))
        addAll(integrity(model.integrity))
    }

    /** The status a part is shown with. Exhaustive over [ReportPart], so a new kind of part cannot be forgotten silently. */
    fun statusOf(part: ReportPart): EpistemicStatus = when (part) {
        is ObservedPart -> EpistemicStatus.OBSERVED
        is UserStatementPart -> EpistemicStatus.USER_REPORTED
        is TagPart -> part.status
        is PatternBlock -> EpistemicStatus.PATTERN
    }

    private fun cover(model: ReportModel): List<String> = listOf(
        ReportText.TITLE_SUFFIX,
        ReportText.fill(ReportText.GENERATED, model.generatedAt),
        ReportText.fill(
            ReportText.VERSIONS,
            model.reportVersion,
            model.generator.appVersion,
            model.generator.ruleVersions.joinToString(", ").ifEmpty { ReportText.NONE_LISTED },
            model.generator.modelVersions.joinToString(", ").ifEmpty { ReportText.NONE_LISTED },
        ),
        ReportText.NOT_EVIDENCE,
    )

    private fun timelineLine(row: TimelineRow): String = ReportText.fill(
        ReportText.TIMELINE_ROW, row.timeText, row.timeBasis, row.sender, row.identityBasis, row.direction, row.sourceKind,
    )

    private fun event(block: EventBlock): List<PreviewRow> = buildList {
        val key = "event-${block.eventId}"
        add(PreviewRow.Heading("$key-heading", block.heading, large = false))
        add(PreviewRow.Lines("$key-details", block.details, small = true))
        block.observed.forEachIndexed { index, part -> add(observed("$key-observed-$index", part)) }
        block.userStatements.forEachIndexed { index, part -> add(statement("$key-statement-$index", part)) }
        block.inferred.forEachIndexed { index, part ->
            add(tag("$key-tag-$index", part, if (part.status == EpistemicStatus.USER_REPORTED) BlockKind.USER_TAG else BlockKind.ACCEPTED_TAG, ReportText.ACCEPTED_TAGS_HEADING))
        }
        block.unreviewed.forEachIndexed { index, part ->
            add(tag("$key-unreviewed-$index", part, BlockKind.UNREVIEWED_SUGGESTION, ReportText.UNREVIEWED_HEADING))
        }
    }

    private fun observed(key: String, part: ObservedPart): PreviewRow.Block = PreviewRow.Block(
        key = key,
        kind = BlockKind.OBSERVED,
        status = statusOf(part),
        title = null,
        quote = part.quote,
        lines = listOfNotNull(
            ReportText.QUOTE_UNAVAILABLE.takeIf { part.quote == null },
            ReportText.fill(
                ReportText.ARTIFACT_LINE,
                part.representation, part.artifactId, part.sha256 ?: ReportText.HASH_NOT_RECORDED, part.locator,
            ),
        ),
    )

    private fun statement(key: String, part: UserStatementPart): PreviewRow.Block = PreviewRow.Block(
        key = key,
        kind = BlockKind.USER_STATEMENT,
        status = statusOf(part),
        title = null,
        quote = part.text,
        lines = listOfNotNull(
            ReportText.QUOTE_UNAVAILABLE.takeIf { part.text == null },
            ReportText.fill(ReportText.STATEMENT_LINE, part.writtenAt, part.artifactId, part.locator),
        ),
    )

    private fun tag(key: String, part: TagPart, kind: BlockKind, title: String): PreviewRow.Block = PreviewRow.Block(
        key = key,
        kind = kind,
        status = statusOf(part),
        title = title,
        quote = null,
        lines = listOf(
            ReportText.fill(ReportText.TAG_LINE, part.label, part.reviewStatus, part.basis, part.producerVersion, part.confidence),
            if (part.status == EpistemicStatus.USER_REPORTED) ReportText.TAG_NOTE_USER else ReportText.TAG_NOTE_SUGGESTION,
        ),
    )

    private fun pattern(block: PatternBlock): PreviewRow.Block = PreviewRow.Block(
        key = "pattern-${block.id}",
        kind = BlockKind.PATTERN,
        status = statusOf(block),
        title = block.heading,
        quote = null,
        lines = buildList {
            add(ReportText.fill(ReportText.PATTERN_STATUS, block.assessment))
            add(block.observed)
            block.interpretation?.let { add(it) }
            if (block.limitations.isNotEmpty()) {
                add(ReportText.PATTERN_LIMITS_HEADING)
                block.limitations.forEach { add(ReportText.fill(ReportText.BULLET, it)) }
            }
            add(ReportText.fill(ReportText.PATTERN_EVENTS, block.supportingEventIds.joinToString(", ")))
            add(ReportText.fill(ReportText.PATTERN_RULES, block.ruleVersion))
        },
    )

    private fun unknowns(items: List<String>): PreviewRow.Block = PreviewRow.Block(
        key = "unknowns",
        kind = BlockKind.UNKNOWNS,
        status = EpistemicStatus.UNKNOWN,
        title = null,
        quote = null,
        lines = items.map { ReportText.fill(ReportText.BULLET, it) },
    )

    private fun integrity(appendix: IntegrityAppendix): List<PreviewRow> = listOf(
        PreviewRow.Heading("integrity-heading", ReportText.SECTION_INTEGRITY, large = true),
        PreviewRow.Lines(
            "integrity",
            listOf(
                appendix.derivativeNote,
                ReportText.INTEGRITY_MANIFEST,
                appendix.signerKeyId?.let { ReportText.fill(ReportText.INTEGRITY_SIGNER, it) } ?: ReportText.INTEGRITY_SIGNER_UNKNOWN,
                ReportText.fill(ReportText.INTEGRITY_AUDIT, appendix.auditChainHead),
                ReportText.INTEGRITY_VERIFY,
                ReportText.LIMITS_HEADING,
            ) + appendix.limits,
        ),
    )
}
