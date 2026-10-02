package org.sakshi.export.report

import org.sakshi.core.model.EpistemicStatus

/** How a paragraph is set. The renderer maps each style to a font, size and spacing. */
internal enum class ParagraphStyle { TITLE, HEADING, SUBHEADING, STATUS, BODY, QUOTE, MONO, SMALL }

/**
 * One block of text for the page. [verbatim] marks the user's own words (evidence quotes, statements and the
 * case title), which are printed exactly and are exempt from the wording guard.
 */
internal data class Paragraph(
    val text: String,
    val style: ParagraphStyle,
    val verbatim: Boolean = false,
    val keepWithNext: Boolean = false,
)

/** Flattens a [ReportModel] into the paragraphs of the PDF, in reading order. Pure Kotlin. */
internal object ReportParagraphs {
    fun of(model: ReportModel): List<Paragraph> {
        val out = mutableListOf<Paragraph>()
        cover(model, out)
        section(ReportText.SECTION_SCOPE, out)
        model.scope.lines.forEach { out += body(it) }
        section(ReportText.SECTION_TIMELINE, out)
        model.timeline.forEach { out += body(timelineText(it)) }
        section(ReportText.SECTION_RECORDS, out)
        model.events.forEach { event(it, out) }
        section(ReportText.SECTION_PATTERNS, out)
        if (model.patterns.isEmpty()) out += body(ReportText.PATTERNS_NONE)
        model.patterns.forEach { pattern(it, out) }
        section(ReportText.SECTION_UNKNOWN, out)
        out += status(ReportText.WORD_UNKNOWN)
        model.unknowns.forEach { out += body(ReportText.fill(ReportText.BULLET, it)) }
        integrity(model.integrity, out)
        return out
    }

    /** The word printed in capitals above a part. */
    fun word(status: EpistemicStatus): String = when (status) {
        EpistemicStatus.OBSERVED -> ReportText.WORD_OBSERVED
        EpistemicStatus.USER_REPORTED -> ReportText.WORD_USER
        EpistemicStatus.INFERRED -> ReportText.WORD_INFERRED
        EpistemicStatus.PATTERN -> ReportText.WORD_PATTERN
        EpistemicStatus.UNKNOWN -> ReportText.WORD_UNKNOWN
    }

    private fun cover(model: ReportModel, out: MutableList<Paragraph>) {
        out += Paragraph(model.title, ParagraphStyle.TITLE, verbatim = true)
        out += Paragraph(ReportText.TITLE_SUFFIX, ParagraphStyle.SUBHEADING)
        out += body(ReportText.fill(ReportText.GENERATED, model.generatedAt))
        val generator = model.generator
        out += body(
            ReportText.fill(
                ReportText.VERSIONS,
                model.reportVersion,
                generator.appVersion,
                generator.ruleVersions.joinToString(", ").ifEmpty { ReportText.NONE_LISTED },
                generator.modelVersions.joinToString(", ").ifEmpty { ReportText.NONE_LISTED },
            ),
        )
        out += body(ReportText.NOT_EVIDENCE)
    }

    private fun timelineText(row: TimelineRow): String = ReportText.fill(
        ReportText.TIMELINE_ROW, row.timeText, row.timeBasis, row.sender, row.identityBasis, row.direction, row.sourceKind,
    )

    private fun event(block: EventBlock, out: MutableList<Paragraph>) {
        out += Paragraph(block.heading, ParagraphStyle.SUBHEADING, keepWithNext = true)
        block.details.forEach { out += Paragraph(it, ParagraphStyle.SMALL) }
        for (part in block.observed) {
            out += status(word(part.status))
            out += quote(part.quote)
            out += mono(
                ReportText.fill(
                    ReportText.ARTIFACT_LINE,
                    part.representation, part.artifactId, part.sha256 ?: ReportText.HASH_NOT_RECORDED, part.locator,
                ),
            )
        }
        for (part in block.userStatements) {
            out += status(word(part.status))
            out += quote(part.text)
            out += mono(ReportText.fill(ReportText.STATEMENT_LINE, part.writtenAt, part.artifactId, part.locator))
        }
        if (block.inferred.isNotEmpty()) out += Paragraph(ReportText.ACCEPTED_TAGS_HEADING, ParagraphStyle.SMALL, keepWithNext = true)
        block.inferred.forEach { tag(it, out) }
        if (block.unreviewed.isNotEmpty()) out += Paragraph(ReportText.UNREVIEWED_HEADING, ParagraphStyle.SMALL, keepWithNext = true)
        block.unreviewed.forEach { tag(it, out) }
    }

    private fun tag(part: TagPart, out: MutableList<Paragraph>) {
        out += status(word(part.status))
        out += body(ReportText.fill(ReportText.TAG_LINE, part.label, part.reviewStatus, part.basis, part.producerVersion, part.confidence))
        out += Paragraph(
            if (part.status == EpistemicStatus.USER_REPORTED) ReportText.TAG_NOTE_USER else ReportText.TAG_NOTE_SUGGESTION,
            ParagraphStyle.SMALL,
        )
    }

    private fun pattern(block: PatternBlock, out: MutableList<Paragraph>) {
        out += Paragraph(block.heading, ParagraphStyle.SUBHEADING, keepWithNext = true)
        out += status(word(block.status))
        out += body(ReportText.fill(ReportText.PATTERN_STATUS, block.assessment))
        out += body(block.observed)
        block.interpretation?.let { out += body(it) }
        if (block.limitations.isNotEmpty()) out += Paragraph(ReportText.PATTERN_LIMITS_HEADING, ParagraphStyle.SMALL, keepWithNext = true)
        block.limitations.forEach { out += body(ReportText.fill(ReportText.BULLET, it)) }
        out += mono(ReportText.fill(ReportText.PATTERN_EVENTS, block.supportingEventIds.joinToString(", ")))
        out += mono(ReportText.fill(ReportText.PATTERN_RULES, block.ruleVersion))
    }

    private fun integrity(appendix: IntegrityAppendix, out: MutableList<Paragraph>) {
        section(ReportText.SECTION_INTEGRITY, out)
        out += body(appendix.derivativeNote)
        out += body(ReportText.INTEGRITY_MANIFEST)
        out += mono(
            appendix.signerKeyId?.let { ReportText.fill(ReportText.INTEGRITY_SIGNER, it) } ?: ReportText.INTEGRITY_SIGNER_UNKNOWN,
        )
        out += mono(ReportText.fill(ReportText.INTEGRITY_AUDIT, appendix.auditChainHead))
        out += body(ReportText.INTEGRITY_VERIFY)
        out += Paragraph(ReportText.LIMITS_HEADING, ParagraphStyle.SMALL, keepWithNext = true)
        appendix.limits.forEach { out += mono(it) }
    }

    private fun quote(text: String?): Paragraph =
        if (text == null) {
            Paragraph(ReportText.QUOTE_UNAVAILABLE, ParagraphStyle.BODY)
        } else {
            Paragraph(text, ParagraphStyle.QUOTE, verbatim = true)
        }

    private fun section(title: String, out: MutableList<Paragraph>) {
        out += Paragraph(title, ParagraphStyle.HEADING, keepWithNext = true)
    }

    private fun status(word: String): Paragraph = Paragraph(word, ParagraphStyle.STATUS, keepWithNext = true)

    private fun body(text: String): Paragraph = Paragraph(text, ParagraphStyle.BODY)

    private fun mono(text: String): Paragraph = Paragraph(text, ParagraphStyle.MONO)
}
