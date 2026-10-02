package org.sakshi.processing.analysis

import org.sakshi.core.model.CodePointSpan
import org.sakshi.core.model.Direction
import org.sakshi.core.model.Event
import org.sakshi.core.model.EventId
import org.sakshi.core.model.EventSource
import org.sakshi.core.model.OutgoingCoverage
import org.sakshi.core.model.ReferenceId
import org.sakshi.core.model.ScopeId
import org.sakshi.core.model.SourceKind
import org.sakshi.core.model.TextStatus
import org.sakshi.core.model.TimeBasis
import org.sakshi.core.model.TimeBounds
import org.sakshi.core.model.Timestamp
import org.sakshi.processing.text.DateOrder
import org.sakshi.processing.text.ExportParse
import org.sakshi.processing.text.ExportTime
import org.sakshi.processing.text.LanguageHint
import org.sakshi.processing.text.LineKind
import org.sakshi.processing.text.ParseWarning
import org.sakshi.processing.text.ParsedRecord
import org.sakshi.processing.text.RuleSignals
import org.sakshi.processing.text.RulesEngine
import org.sakshi.processing.text.SignalStatus
import org.sakshi.processing.text.WhatsAppExportParser

/** What [EventBuilder] decided. */
internal sealed interface BuildResult {
    class NeedsOptions(
        val senders: List<String>,
        val detectedDateOrder: DateOrder,
        val recordCount: Int,
        val sampleDates: List<String>,
    ) : BuildResult

    class Built(val events: List<Event>, val kind: InputKind, val warnings: Map<AnalysisWarning, Int>) : BuildResult
}

/** Decides plain text versus export and builds the events, rule signals included. Pure CPU work. */
internal class EventBuilder(
    private val rules: RulesEngine,
    private val ids: () -> String,
    private val context: EventContext,
    private val evidenceId: String,
    private val limits: AnalysisLimits,
) {
    private val warnings = linkedMapOf<AnalysisWarning, Int>()

    fun build(text: String, options: ExportOptions?): BuildResult {
        val index = CodePointIndex(text)
        val parse = WhatsAppExportParser.parse(text, limits.maxRecords)
        val messages = parse.records.count { it.kind == LineKind.MESSAGE }
        val prefix = parse.unparsedPrefix
        val isExport = messages >= MIN_EXPORT_MESSAGES && (prefix == null || prefix.end - prefix.start <= MAX_PREFIX_CODE_POINTS)
        if (!isExport) return built(listOf(plainEvent(text, index)), InputKind.PLAIN_TEXT)
        if (options == null) return needsOptions(parse, messages)
        return built(exportEvents(index, parse, options), InputKind.WHATSAPP_EXPORT)
    }

    private fun built(events: List<Event>, kind: InputKind): BuildResult.Built =
        BuildResult.Built(events, kind, warnings.toMap())

    private fun needsOptions(parse: ExportParse, messages: Int): BuildResult.NeedsOptions = BuildResult.NeedsOptions(
        senders = parse.records.mapNotNull { it.senderClaim }.distinct(),
        detectedDateOrder = parse.dateOrder,
        recordCount = messages,
        sampleDates = parse.records.map { it.dateRaw }.distinct().take(MAX_SAMPLE_DATES),
    )

    private fun plainEvent(text: String, index: CodePointIndex): Event {
        val signals = rules.analyse(text)
        noteLanguage(signals)
        val source = EventSource(SourceKind.SELECTED_TEXT, null, null, null, null, ScopeId(PLAIN_PARSER_VERSION))
        val draft = EventDraft(
            eventId = EventId(ids()),
            timestamp = EventFactory.unknownTime(),
            sender = EventFactory.claimedSender(null),
            direction = Direction.UNKNOWN,
            source = source,
            bodySpan = CodePointSpan(0, index.length),
            textStatus = TextStatus.AVAILABLE,
            outgoingCoverage = OutgoingCoverage.UNKNOWN,
            assessment = assess(signals, 0),
        )
        return EventFactory.build(context, draft)
    }

    private fun exportEvents(index: CodePointIndex, parse: ExportParse, options: ExportOptions): List<Event> {
        val order = effectiveOrder(parse.dateOrder, options)
        if (parse.unparsedPrefix != null) note(AnalysisWarning.UNPARSED_PREFIX_SKIPPED)
        if (ParseWarning.LIMIT_REACHED in parse.warnings) note(AnalysisWarning.RECORD_LIMIT_REACHED)
        val scope = ScopeId(("export-$evidenceId").take(MAX_ID_LENGTH))
        val events = ArrayList<Event>(parse.records.size)
        for (record in parse.records) {
            if (record.kind == LineKind.SYSTEM) {
                note(AnalysisWarning.SYSTEM_LINES_SKIPPED)
                continue
            }
            events += EventFactory.build(context, exportDraft(record, index, order, options, scope))
        }
        return events
    }

    private fun effectiveOrder(detected: DateOrder, options: ExportOptions): DateOrder {
        if (detected == DateOrder.AMBIGUOUS) return options.dateOrder
        if (detected != options.dateOrder) note(AnalysisWarning.DATE_ORDER_OVERRIDDEN)
        return detected
    }

    private fun exportDraft(
        record: ParsedRecord,
        index: CodePointIndex,
        order: DateOrder,
        options: ExportOptions,
        scope: ScopeId,
    ): EventDraft {
        val claim = checkNotNull(record.senderClaim) { "Message record without sender" }
        val empty = record.bodySpan.start >= record.bodySpan.end
        val hasText = !record.mediaOmitted && !empty
        val assessment = if (hasText) {
            val body = index.slice(record.bodySpan.start, record.bodySpan.end)
            val signals = rules.analyse(body)
            noteLanguage(signals)
            assess(signals, record.bodySpan.start)
        } else {
            null
        }
        val source = EventSource(
            SourceKind.SELECTED_EXPORT,
            SOURCE_APP,
            null,
            scope,
            ScopeId(record.index.toString()),
            ScopeId(WhatsAppExportParser.VERSION),
        )
        return EventDraft(
            eventId = EventId(ids()),
            timestamp = timeOf(record, order, options),
            sender = EventFactory.claimedSender(truncate(claim)),
            direction = directionOf(claim, options.ownerSenderClaim),
            source = source,
            bodySpan = if (empty) record.recordSpan else record.bodySpan,
            textStatus = if (hasText) TextStatus.AVAILABLE else TextStatus.ABSENT,
            outgoingCoverage = OutgoingCoverage.INCLUDED_FOR_SELECTED_RANGE,
            assessment = assessment,
        )
    }

    /** At most [MAX_LABEL_LENGTH] UTF-16 units, never cutting a surrogate pair. */
    private fun truncate(label: String): String {
        if (label.length <= MAX_LABEL_LENGTH) return label
        val cut = if (Character.isHighSurrogate(label[MAX_LABEL_LENGTH - 1])) MAX_LABEL_LENGTH - 1 else MAX_LABEL_LENGTH
        return label.substring(0, cut)
    }

    private fun directionOf(claim: String, owner: String?): Direction = when {
        owner == null -> Direction.UNKNOWN
        claim == owner -> Direction.OUTGOING
        else -> Direction.INCOMING
    }

    private fun timeOf(record: ParsedRecord, order: DateOrder, options: ExportOptions): TimeBounds {
        val resolved = ExportTime.resolve(record, order, options.zone)
        if (resolved == null) {
            note(AnalysisWarning.UNRESOLVED_TIMES)
            return EventFactory.unknownTime()
        }
        return TimeBounds(
            Timestamp(resolved.earliest.toString()),
            Timestamp(resolved.latest.toString()),
            TimeBasis.SOURCE_CLAIM,
            resolved.precision,
            options.zone.id.take(MAX_ZONE_LENGTH),
            null,
            null,
        )
    }

    private fun assess(signals: RuleSignals, bodyOffset: Int): CueAssessment? {
        if (signals.matches.isEmpty()) return null
        return CueReferences.assess(signals, bodyOffset, EventFactory.BODY_REFERENCE, context.derivative, context.evidenceSha256)
    }

    private fun noteLanguage(signals: RuleSignals) {
        when (signals.status) {
            SignalStatus.UNSUPPORTED_LANGUAGE ->
                if (signals.language.hint == LanguageHint.UNSUPPORTED && signals.language.basis != NO_LETTERS) {
                    note(AnalysisWarning.UNSUPPORTED_LANGUAGE_PRESENT)
                }
            SignalStatus.CUE_LIST_NOT_REVIEWED -> note(AnalysisWarning.CUE_LIST_NOT_REVIEWED)
            SignalStatus.SUGGESTION, SignalStatus.NO_CUE_MATCHED, SignalStatus.EMPTY_TEXT -> Unit
        }
    }

    private fun note(warning: AnalysisWarning) {
        warnings[warning] = (warnings[warning] ?: 0) + 1
    }

    private companion object {
        const val MIN_EXPORT_MESSAGES: Int = 2
        const val MAX_PREFIX_CODE_POINTS: Int = 256
        const val MAX_SAMPLE_DATES: Int = 5
        const val MAX_LABEL_LENGTH: Int = 256
        const val MAX_ID_LENGTH: Int = 128
        const val MAX_ZONE_LENGTH: Int = 64
        const val PLAIN_PARSER_VERSION: String = "plain-text-v1"
        const val SOURCE_APP: String = "whatsapp-export-claim"
        const val NO_LETTERS: String = "no_letters"
    }
}
