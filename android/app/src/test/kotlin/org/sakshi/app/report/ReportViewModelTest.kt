package org.sakshi.app.report

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.io.File
import java.nio.file.Files
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.sakshi.app.review.EventReviewViewModel
import org.sakshi.app.support.FakeReportRenderer
import org.sakshi.app.support.ReportTestBase
import org.sakshi.app.support.SyntheticChats
import org.sakshi.app.ui.resolve
import org.sakshi.core.model.CategoryLabel
import org.sakshi.core.model.CategoryReviewStatus
import org.sakshi.core.model.ConfirmationStatus
import org.sakshi.core.model.EventId
import org.sakshi.core.vault.BatchSaveResult
import org.sakshi.export.bundle.BundleVerifier
import org.sakshi.export.bundle.Verdict
import org.sakshi.export.report.ExportFailure
import org.sakshi.export.report.RefusalReason
import org.sakshi.export.report.ReportText

class ReportViewModelTest : ReportTestBase() {
    private lateinit var caseId: String

    private fun loaded(model: ReportViewModel): ReportUiState = await(model.state) { it.loaded }

    private fun ready(model: ReportViewModel): PreviewState.Ready =
        assertIs<PreviewState.Ready>(await(model.state) { it.preview is PreviewState.Ready }.preview)

    private fun done(model: ReportViewModel): ExportDone =
        assertIs<ExportState.Done>(await(model.state) { it.export is ExportState.Done }.export).export

    /** Previews what is selected, then exports exactly that preview. */
    private fun exportPreviewed(model: ReportViewModel) {
        model.buildPreview()
        ready(model)
        model.startExport()
    }

    /** Marks the first message that has [label] as agreed, the way the person does on the review screen. */
    private fun agreeWith(label: CategoryLabel) {
        val event = events(caseId).first { hasLabel(it, label) }
        val review = EventReviewViewModel(caseId, event.eventId.value, vault, scope)
        await(review.state) { it.loaded }
        review.agree(event.categories.indexOfFirst { it.label == label })
        await(review.state) { s -> s.event?.categories?.any { it.label == label && it.reviewStatus == CategoryReviewStatus.ACCEPTED } == true }
    }

    @Test
    fun nothingIsSelectedUntilThePersonSelectsIt() {
        caseId = newCase()
        addChat(caseId)
        val state = loaded(reportModel(services(), caseId))
        assertEquals(events(caseId).size, state.rows.size)
        assertEquals(emptySet(), state.selected)
        assertFalse(state.includeUnreviewed)
        assertEquals(emptyList(), state.originals)
        assertFalse(state.canPreview)
    }

    @Test
    fun theHelperActionsSelectWhatTheyName() {
        caseId = newCase()
        addChat(caseId)
        agreeWith(CategoryLabel.VERBAL_ABUSE)
        val model = reportModel(services(), caseId)
        val rows = loaded(model).rows
        model.selectAll()
        assertEquals(rows.size, await(model.state) { it.selected.size == rows.size }.selected.size)
        assertTrue(model.state.value.canPreview)
        model.selectNone()
        assertEquals(emptySet(), await(model.state) { it.selected.isEmpty() }.selected)
        model.selectTagged()
        val tagged = await(model.state) { it.selected.isNotEmpty() }.selected
        val expected = events(caseId).filter { hasLabel(it, CategoryLabel.VERBAL_ABUSE) }.map { it.eventId.value }.toSet()
        assertEquals(expected, tagged)
        model.toggle(tagged.first())
        assertEquals(emptySet(), await(model.state) { it.selected.isEmpty() }.selected)
    }

    @Test
    fun previewDoesNothingWhileNothingIsSelected() {
        caseId = newCase()
        addChat(caseId)
        val model = reportModel(services(), caseId)
        loaded(model)
        model.buildPreview()
        assertEquals(PreviewState.Idle, model.state.value.preview)
        model.startExport()
        assertEquals(ExportState.Idle, model.state.value.export)
    }

    @Test
    fun anEventThatIsNotConfirmedCannotBeSelected() {
        caseId = newCase()
        addChat(caseId)
        val pending = events(caseId).first()
        val changed = pending.copy(
            revision = pending.revision + 1,
            userConfirmation = pending.userConfirmation.copy(status = ConfirmationStatus.PENDING),
        )
        assertEquals(BatchSaveResult.Saved(1), runBlocking { vault.events.saveAll(listOf(changed)) })
        val model = reportModel(services(), caseId)
        val row = await(model.state) { s -> s.loaded && s.rows.any { it.reason != null } }.rows.single { it.reason != null }
        assertEquals(pending.eventId.value, row.eventId)
        assertEquals(BlockReason.NOT_CONFIRMED_YET, row.reason)
        assertEquals("Cannot be included: you have not confirmed this item yet.", ReportRows.blockText(row.reason!!).resolve(context.resources))
        model.toggle(row.eventId)
        model.selectAll()
        val state = await(model.state) { it.selected.isNotEmpty() }
        assertFalse(row.eventId in state.selected)
        assertEquals(state.rows.size - 1, state.selected.size)
        assertEquals(state.rows.size - 1, state.selectableCount)
    }

    @Test
    fun theUnreviewedSwitchReachesTheReportOptions() {
        caseId = newCase()
        addChat(caseId)
        val model = reportModel(services(), caseId)
        loaded(model)
        model.selectAll()
        await(model.state) { it.canPreview }
        model.buildPreview()
        assertTrue(ready(model).model.events.all { it.unreviewed.isEmpty() })
        assertFalse(ready(model).summary.includesUnreviewed)
        model.backToSelection()
        model.setIncludeUnreviewed(true)
        await(model.state) { it.includeUnreviewed && it.preview == PreviewState.Idle }
        model.buildPreview()
        val on = ready(model)
        assertTrue(on.model.events.any { it.unreviewed.isNotEmpty() })
        assertTrue(on.summary.includesUnreviewed)
    }

    @Test
    fun originalsAreOffByDefaultAndOnlyTheCheckedOnesAreIncluded() {
        caseId = newCase()
        val first = addChat(caseId)
        val second = addChat(caseId, SyntheticChats.STOP_THEN_SIX)
        val model = reportModel(services(), caseId)
        loaded(model)
        model.selectAll()
        val state = await(model.state) { it.originals.size == 2 }
        assertTrue(state.originals.none { it.included })
        assertEquals(setOf(first, second), state.originals.map { it.evidenceId }.toSet())
        model.buildPreview()
        assertEquals(0, ready(model).summary.originals)
        model.backToSelection()
        model.toggleOriginal(second)
        await(model.state) { s -> s.originals.any { it.included } }
        model.buildPreview()
        val summary = ready(model).summary
        assertEquals(1, summary.originals)
        assertEquals(SyntheticChats.STOP_THEN_SIX.toByteArray().size.toLong(), summary.originalBytes)
        assertEquals(setOf(second), model.state.value.originals.filter { it.included }.map { it.evidenceId }.toSet())
    }

    @Test
    fun deselectingTheEventsDropsTheirOriginals() {
        caseId = newCase()
        val evidenceId = addChat(caseId)
        val model = reportModel(services(), caseId)
        loaded(model)
        model.selectAll()
        await(model.state) { it.originals.size == 1 }
        model.toggleOriginal(evidenceId)
        model.selectNone()
        assertEquals(emptyList(), await(model.state) { it.selected.isEmpty() }.originals)
    }

    @Test
    fun theChosenZoneReachesTheReport() {
        caseId = newCase()
        addChat(caseId)
        val model = reportModel(services(), caseId)
        loaded(model)
        model.selectAll()
        model.setZone(ZoneId.of("Pacific/Auckland"))
        await(model.state) { it.canPreview && it.zone.id == "Pacific/Auckland" }
        model.buildPreview()
        assertTrue(ready(model).model.scope.lines.contains(ReportText.fill(ReportText.SCOPE_ZONE, "Pacific/Auckland")))
    }

    @Test
    fun theSelectionSummaryCountsWhatWasLeftOut() {
        caseId = newCase()
        addChat(caseId)
        val model = reportModel(services(), caseId)
        val rows = loaded(model).rows
        model.toggle(rows.first().eventId)
        model.toggle(rows.last().eventId)
        await(model.state) { it.selected.size == 2 }
        model.buildPreview()
        val summary = ready(model).summary
        assertEquals(2, summary.messages)
        assertEquals(rows.size - 2, summary.leftOut)
    }

    @Test
    fun everyRefusalAndFailureHasItsOwnSentence() {
        val refusals = RefusalReason.entries.map { ReportMessages.refusal(it).resolve(context.resources) }
        val failures = ExportFailure.entries.map { ReportMessages.failure(it).resolve(context.resources) }
        val others = listOf(ReportMessages.cancelled, ReportMessages.unexpected).map { it.resolve(context.resources) }
        val all = refusals + failures + others
        assertTrue(all.all { it.isNotBlank() })
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun anExportIsAZipThatVerifiesAndHasNoOriginalUnlessChosen() {
        caseId = newCase()
        addChat(caseId)
        val model = reportModel(services(), caseId)
        loaded(model)
        model.selectAll()
        await(model.state) { it.canPreview }
        exportPreviewed(model)
        val plain = done(model)
        assertEquals(setOf(plain.file.name), exportDirectory.list()!!.toSet())
        assertEquals("${plain.snapshotId}.zip", plain.file.name)
        val bare = unzip(plain.file)
        val report = BundleVerifier.verify(bare)
        assertEquals(Verdict.CONSISTENT, report.verdict, report.checks.toString())
        assertEquals(plain.keyId, report.signerKeyId)
        assertEquals(events(caseId).size, plain.summary.eventCount)
        assertFalse(Files.exists(bare.resolve("evidence")))
        model.leaveResult()

        val evidenceId = model.state.value.originals.single().evidenceId
        model.toggleOriginal(evidenceId)
        await(model.state) { s -> s.originals.any { it.included } }
        exportPreviewed(model)
        val withOriginal = done(model)
        val dir = unzip(withOriginal.file)
        assertEquals(Verdict.CONSISTENT, BundleVerifier.verify(dir).verdict)
        val originals = Files.list(dir.resolve("evidence")).use { stream -> stream.toList() }
        assertEquals(1, originals.size)
        assertEquals(SyntheticChats.EIGHT_MESSAGES.toByteArray().size.toLong(), Files.size(originals.single()))
        assertEquals(1, withOriginal.summary.originalCount)
    }

    @Test
    fun theFirstPreviewIsVersionOneAndTheNextExportIsVersionTwo() {
        caseId = newCase()
        addChat(caseId)
        val model = reportModel(services(), caseId)
        loaded(model)
        model.selectAll()
        await(model.state) { it.canPreview }
        model.buildPreview()
        assertEquals(1, ready(model).options.reportVersion)
        model.startExport()
        assertEquals(1, done(model).reportVersion)
        model.leaveResult()
        model.buildPreview()
        assertEquals(2, ready(model).options.reportVersion)
        model.startExport()
        assertEquals(2, done(model).reportVersion)
    }

    @Test
    fun theExportPassesTheFingerprintOfTheReportThatWasPreviewed() {
        caseId = newCase()
        addChat(caseId)
        val model = reportModel(services(), caseId)
        loaded(model)
        model.selectAll()
        await(model.state) { it.canPreview }
        model.buildPreview()
        val previewed = ready(model)
        assertTrue(previewed.contentSha256.isNotBlank())
        assertEquals(previewed.selection.eventIds, model.state.value.selected.map { EventId(it) }.toSet())
        model.startExport()
        val export = done(model)
        assertEquals(previewed.options.reportVersion, export.reportVersion)
        assertEquals(previewed.summary.messages, export.summary.eventCount)
    }

    @Test
    fun aCaseThatChangedAfterThePreviewIsNotExported() {
        caseId = newCase()
        addChat(caseId)
        val model = reportModel(services(), caseId)
        loaded(model)
        model.selectAll()
        await(model.state) { it.canPreview }
        model.buildPreview()
        ready(model)
        agreeWith(CategoryLabel.VERBAL_ABUSE)
        model.startExport()
        val failed = assertIs<ExportState.Failed>(await(model.state) { it.export is ExportState.Failed }.export)
        assertEquals(ReportMessages.refusal(RefusalReason.CHANGED_SINCE_PREVIEW), failed.message)
        waitForEmptyExports()
        model.backToSelection()
        model.exportNoticeShown()
        model.buildPreview()
        ready(model)
        model.startExport()
        assertEquals(1, done(model).reportVersion)
    }

    @Test
    fun exportDoesNothingWithoutAReadyPreview() {
        caseId = newCase()
        addChat(caseId)
        val model = reportModel(services(), caseId)
        loaded(model)
        model.selectAll()
        await(model.state) { it.canPreview }
        model.startExport()
        assertEquals(ExportState.Idle, model.state.value.export)
        assertEquals(PreviewState.Idle, model.state.value.preview)
        assertEquals(emptyList(), exportDirectory.list().orEmpty().toList())
    }

    /** The preview is not reset by changing a choice, so what is exported is what was previewed. */
    @Test
    fun changingAChoiceAfterThePreviewDoesNotChangeWhatIsExported() {
        caseId = newCase()
        addChat(caseId)
        val model = reportModel(services(), caseId)
        val rows = loaded(model).rows
        model.selectAll()
        await(model.state) { it.selected.size == rows.size }
        model.buildPreview()
        ready(model)
        model.selectNone()
        model.toggle(rows.first().eventId)
        await(model.state) { it.selected.size == 1 }
        model.setIncludeUnreviewed(true)
        model.startExport()
        val export = done(model)
        assertEquals(rows.size, export.summary.eventCount)
        assertEquals(1, model.state.value.selected.size)
    }

    @Test
    fun anEarlierExportIsShownWithItsDrift() {
        caseId = newCase()
        addChat(caseId)
        val model = reportModel(services(), caseId)
        loaded(model)
        assertEquals(null, model.state.value.earlierExport)
        model.selectAll()
        await(model.state) { it.canPreview }
        exportPreviewed(model)
        done(model)
        val fresh = await(model.state) { it.earlierExport != null }.earlierExport!!
        assertEquals(1, fresh.version)
        assertTrue(fresh.upToDate)
        agreeWith(CategoryLabel.VERBAL_ABUSE)
        val drifted = await(model.state) { it.earlierExport?.upToDate == false }.earlierExport!!
        assertEquals(1, drifted.changed)
        assertEquals(0, drifted.removed)
    }

    @Test
    fun cancellingAnExportLeavesNothingInTheExportFolder() {
        caseId = newCase()
        addChat(caseId)
        val gate = CountDownLatch(1)
        val started = CountDownLatch(1)
        val model = reportModel(services(FakeReportRenderer(gate, started)), caseId)
        loaded(model)
        model.selectAll()
        await(model.state) { it.canPreview }
        exportPreviewed(model)
        assertTrue(started.await(20, TimeUnit.SECONDS))
        model.cancelExport()
        assertEquals(ExportState.Running(cancelling = true), model.state.value.export)
        gate.countDown()
        await(model.state) { it.export == ExportState.Cancelled }
        waitForEmptyExports()
        model.exportNoticeShown()
        assertEquals(ExportState.Idle, model.state.value.export)
    }

    @Test
    fun discardingTheResultClearsTheExportFolder() {
        caseId = newCase()
        addChat(caseId)
        val model = reportModel(services(), caseId)
        loaded(model)
        model.selectAll()
        await(model.state) { it.canPreview }
        exportPreviewed(model)
        val export = done(model)
        assertTrue(export.file.isFile)
        model.leaveResult()
        assertEquals(emptyList(), exportDirectory.list().orEmpty().toList())
        assertEquals(ExportState.Idle, model.state.value.export)
        assertEquals(PreviewState.Idle, model.state.value.preview)
    }

    @Test
    fun lockingTheSessionClearsTheExportFolder() {
        caseId = newCase()
        addChat(caseId)
        val services = services()
        val store = ViewModelStore()
        val factory = viewModelFactory { initializer { reportModel(services, caseId) } }
        val model = ViewModelProvider(store, factory)["report", ReportViewModel::class.java]
        loaded(model)
        model.selectAll()
        await(model.state) { it.canPreview }
        exportPreviewed(model)
        assertTrue(done(model).file.isFile)
        store.clear()
        assertEquals(emptyList(), exportDirectory.list().orEmpty().toList())
    }

    @Test
    fun startingTheServicesRemovesAFileLeftBehind() {
        val leftover = File(exportDirectory, "synthetic-leftover.zip")
        exportDirectory.mkdirs()
        leftover.writeText("synthetic")
        assertTrue(leftover.exists())
        services()
        assertFalse(leftover.exists())
    }
}
