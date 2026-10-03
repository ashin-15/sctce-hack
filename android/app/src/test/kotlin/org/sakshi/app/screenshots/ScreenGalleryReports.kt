package org.sakshi.app.screenshots

import java.io.File
import java.time.Instant
import kotlin.test.Test
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.sakshi.app.evidence.EvidenceKind
import org.sakshi.app.report.BlockReason
import org.sakshi.app.report.ExportDone
import org.sakshi.app.report.ExportResultActions
import org.sakshi.app.report.ExportResultScreen
import org.sakshi.app.report.ExportState
import org.sakshi.app.report.OriginalChoice
import org.sakshi.app.report.PreviewState
import org.sakshi.app.report.PreviewSummary
import org.sakshi.app.report.ReportEventRow
import org.sakshi.app.report.ReportPreviewActions
import org.sakshi.app.report.ReportPreviewMapping
import org.sakshi.app.report.ReportPreviewScreen
import org.sakshi.app.report.ReportSelectionActions
import org.sakshi.app.report.ReportSelectionScreen
import org.sakshi.app.report.ReportUiState
import org.sakshi.app.timeline.BodyView
import org.sakshi.app.timeline.SenderStatus
import org.sakshi.app.timeline.SenderView
import org.sakshi.app.timeline.TimeLabel
import org.sakshi.app.timeline.TimeReading
import org.sakshi.app.timeline.TimelineEventRow
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.Direction
import org.sakshi.core.model.EpistemicStatus
import org.sakshi.core.model.TimeBasis
import org.sakshi.core.temporal.EvidenceView
import org.sakshi.export.bundle.GeneratorInfo
import org.sakshi.export.bundle.OmittedCounts
import org.sakshi.export.report.EventBlock
import org.sakshi.export.report.ExportSummary
import org.sakshi.export.report.IntegrityAppendix
import org.sakshi.export.report.ObservedPart
import org.sakshi.export.report.PatternBlock
import org.sakshi.export.report.ReportModel
import org.sakshi.export.report.ReportOptions
import org.sakshi.export.report.ReportSelection
import org.sakshi.export.report.ReportText
import org.sakshi.export.report.ScopeStatement
import org.sakshi.export.report.TagPart
import org.sakshi.export.report.TimelineRow

/**
 * Report selection, report preview and export result, drawn from invented state. The notification observation and
 * visible capture screens read live services (the notification listener, the accessibility service, a vault), which a
 * state-only render cannot build, so they have no entry here.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class ScreenGalleryReports {
    private val harness = GalleryHarness()

    @get:Rule
    val rules = harness.rules

    private val monday: Instant = Instant.parse("2026-09-28T15:30:00Z")

    private fun eventRow(id: String, minutes: Long, text: String, tagged: Boolean, reason: BlockReason?) = ReportEventRow(
        TimelineEventRow(
            eventId = id,
            time = TimeReading(TimeLabel.At(monday.plusSeconds(minutes * 60)), TimeBasis.SOURCE_CLAIM),
            orderNote = false,
            sender = SenderView("synthetic-sam", SenderStatus.NOT_CONFIRMED, null),
            direction = Direction.INCOMING,
            body = BodyView.Message(text, text),
            tags = emptyList(),
            needsReview = false,
            tagged = tagged,
        ),
        reason,
        setOf("e1"),
    )

    private val rows = listOf(
        eventRow("ev1", 0, "why do you ignore me", tagged = true, reason = null),
        eventRow("ev2", 2, "answer me right now, I know you can see this and you are not replying to anyone", tagged = false, reason = null),
        eventRow("ev3", 5, "reply now", tagged = true, reason = BlockReason.NOT_CONFIRMED_YET),
        eventRow("ev4", 9, "synthetic rejected message", tagged = false, reason = BlockReason.REJECTED),
    )

    private val selectionState = ReportUiState(
        loaded = true,
        rows = rows,
        selected = setOf("ev1", "ev2"),
        includeUnreviewed = false,
        originals = listOf(OriginalChoice("e1", EvidenceKind.TEXT_FILE, 48_210L, included = true)),
        zone = SampleState.zone,
    )

    private val selectionActions = ReportSelectionActions({}, {}, {}, {}, {}, {}, {}, {}, {})

    @Test
    fun reportSelection() = harness.shoot("report-selection") { ReportSelectionScreen(selectionState, selectionActions) }

    @Test
    fun reportSelectionEmpty() = harness.shoot("report-selection-empty") {
        ReportSelectionScreen(ReportUiState(loaded = true, zone = SampleState.zone), selectionActions)
    }

    private val model = ReportModel(
        title = "synthetic case",
        reportVersion = 1,
        generatedAt = Instant.parse("2026-10-02T10:00:00Z"),
        generator = GeneratorInfo("synthetic-app-1", listOf("synthetic-rules-1"), emptyList()),
        scope = ScopeStatement(listOf("2 of 4 records selected.", "1 record could not be included.")),
        timeline = listOf(
            TimelineRow("ev1", "28 Sep 2026, 21:00", "as written in the export", "synthetic-sam", "name as it appears in the export, not confirmed", "incoming, received by you", "export you selected"),
        ),
        events = listOf(
            EventBlock(
                eventId = "ev1",
                revision = 1,
                heading = "Record 1: Message",
                details = listOf("Record id ev1, revision 1."),
                observed = listOf(ObservedPart("why do you ignore me", "synthetic-artifact", "a".repeat(64), "characters 0 to 20 of the saved text", "saved copy of what you imported")),
                userStatements = emptyList(),
                inferred = listOf(TagPart(EpistemicStatus.INFERRED, "Pressure to reply", "suggested by a rule", "synthetic-rules-1", "no score applies", "accepted by you")),
                unreviewed = emptyList(),
            ),
        ),
        patterns = listOf(
            PatternBlock(
                id = "p1",
                heading = "Repeated contact",
                assessment = "candidate, needs review",
                observed = "Six records came in half an hour.",
                interpretation = "The records continue after a request to stop. This describes the records only.",
                limitations = listOf("Only imported messages are counted."),
                supportingEventIds = listOf("ev1"),
                ruleVersion = "synthetic-rules-1",
            ),
        ),
        unknowns = listOf("Who really sent the messages is not known."),
        integrity = IntegrityAppendix(null, null, "b".repeat(64), "c".repeat(64), ReportText.LIMITS, "synthetic derivative note"),
    )

    private fun ready(summary: PreviewSummary) = PreviewState.Ready(
        model = model,
        rows = ReportPreviewMapping.rows(model),
        summary = summary,
        selection = ReportSelection(CaseId("c1"), emptySet(), emptySet(), EvidenceView.CONFIRMED_ONLY, SampleState.zone),
        options = ReportOptions(),
        contentSha256 = "d".repeat(64),
    )

    private val previewActions = ReportPreviewActions({}, {}, {}, {})

    @Test
    fun reportPreview() = harness.shoot("report-preview") {
        ReportPreviewScreen(
            ReportUiState(loaded = true, preview = ready(PreviewSummary(2, 1, 48_210L, 2, includesUnreviewed = false))),
            previewActions,
        )
    }

    @Test
    fun reportPreviewUnreviewed() = harness.shoot("report-preview-unreviewed") {
        ReportPreviewScreen(
            ReportUiState(loaded = true, preview = ready(PreviewSummary(2, 0, 0L, 0, includesUnreviewed = true)), export = ExportState.Cancelled),
            previewActions,
        )
    }

    @Test
    fun exportResult() = harness.shoot("export-result") {
        val summary = ExportSummary(
            merkleRoot = "e".repeat(64),
            fileCount = 6,
            pageCount = 5,
            eventCount = 2,
            findingCount = 3,
            patternCount = 1,
            originalCount = 1,
            omitted = OmittedCounts(0, 0, 2),
            zipBytes = 214_000L,
        )
        ExportResultScreen(
            ExportDone(File("sakshi-report-1.zip"), "snapshot-1", "0123456789abcdef".repeat(4), summary, 1),
            ExportResultActions({}, {}),
        )
    }
}
