package org.sakshi.app.screenshots

import android.net.Uri
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.sakshi.acquisition.importer.ContentAvailability
import org.sakshi.app.R
import org.sakshi.app.aimodel.AiModelScreen
import org.sakshi.app.aimodel.AiModelUiState
import org.sakshi.app.aimodel.InstalledModel
import org.sakshi.app.analysis.AnalysisActions
import org.sakshi.app.analysis.AnalysisScreen
import org.sakshi.app.analysis.AnalysisUiState
import org.sakshi.app.analysis.ExportAnswers
import org.sakshi.app.analysis.ExportQuestions
import org.sakshi.app.analysis.OwnerChoice
import org.sakshi.app.cases.CaseListScreen
import org.sakshi.app.cases.CaseListUiState
import org.sakshi.app.cases.CaseMessage
import org.sakshi.app.cases.DeleteDialog
import org.sakshi.app.cases.TitleDialog
import org.sakshi.app.deletion.DeleteEverythingScreen
import org.sakshi.app.deletion.DeletionProgressScreen
import org.sakshi.app.evidence.AddEvidenceActions
import org.sakshi.app.evidence.AddEvidenceCallbacks
import org.sakshi.app.evidence.CaseDetailActions
import org.sakshi.app.evidence.CaseDetailScreen
import org.sakshi.app.evidence.CaseDetailUiState
import org.sakshi.app.evidence.IntegrityStatus
import org.sakshi.app.evidence.PasteDialog
import org.sakshi.app.importing.CaseChoice
import org.sakshi.app.importing.ImportActions
import org.sakshi.app.importing.ImportScreen
import org.sakshi.app.importing.ImportUiState
import org.sakshi.app.importing.ItemLabel
import org.sakshi.app.lock.LockScreen
import org.sakshi.app.note.ManualNoteScreen
import org.sakshi.app.note.NoteActions
import org.sakshi.app.note.NoteFormState
import org.sakshi.app.note.NoteProblem
import org.sakshi.app.onboarding.OnboardingScreen
import org.sakshi.app.patterns.PatternNotice
import org.sakshi.app.patterns.PatternsActions
import org.sakshi.app.patterns.PatternsScreen
import org.sakshi.app.patterns.PatternsUiState
import org.sakshi.app.search.PersonChoice
import org.sakshi.app.search.SearchActions
import org.sakshi.app.search.SearchFilterActions
import org.sakshi.app.search.SearchFilterState
import org.sakshi.app.search.SearchScreen
import org.sakshi.app.search.SearchUiState
import org.sakshi.app.search.SourceChoice
import org.sakshi.app.session.DeletionState
import org.sakshi.app.session.FailureReason
import org.sakshi.app.session.SessionState
import org.sakshi.app.ui.catalog.DesignCatalog
import org.sakshi.core.model.ActorId
import org.sakshi.core.temporal.EvidenceView
import org.sakshi.core.vault.SearchHit
import org.sakshi.core.vault.SearchMatch
import org.sakshi.core.vault.SearchScope
import org.sakshi.core.vault.DerivativeKind
import org.sakshi.processing.analysis.AnalysisOutcome
import org.sakshi.processing.analysis.AnalysisWarning
import org.sakshi.processing.analysis.InputKind
import org.sakshi.processing.analysis.NotAnalysableReason
import org.sakshi.processing.llm.model.ModelManager
import org.sakshi.processing.text.DateOrder

/**
 * Renders every screen that can be drawn from state alone to `app/build/screenshots`, in light and dark theme at font
 * scales 1.0 and 1.5, on a 411 x 891 dp phone (2.625 density). A screen taller than the phone is also saved scrolled
 * to its end (`-end`). The state is invented; no vault or view model is started. Screens that need a real vault
 * (event review, timeline, who is who, report screens) are in [VaultScreenGallery].
 *
 * Opt-in, so the normal test run skips it. Run from the `android` folder:
 *
 *     SAKSHI_SCREENSHOTS=true ./gradlew :app:testDebugUnitTest --tests 'org.sakshi.app.screenshots.*'
 *
 * then open `app/build/screenshots/index.html`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class ScreenGallery {
    private val harness = GalleryHarness()

    @get:Rule
    val rules = harness.rules

    private val noImport = ImportActions({}, {}, {}, {}, {}, {}, {}, { _, _ -> })
    private val noAnalysis = AnalysisActions({}, {}, {}, {}, {}, {}, {}, {}, {})
    private val noPickers = AddEvidenceCallbacks({}, {}, {}, {}, {})

    private fun caseDetailActions() = CaseDetailActions({}, {}, {}, {}, {}, {}, noPickers, {}, {}, {}, {}, {})

    @Test
    fun onboarding() = harness.shoot("onboarding") { OnboardingScreen(onAcknowledge = {}) }

    private fun lock(name: String, state: SessionState, notCompleted: Boolean = false, sharePending: Boolean = false) =
        harness.shoot(name) { LockScreen(state, notCompleted, sharePending, onUnlock = {}, onRetry = {}) }

    @Test
    fun lockLocked() = lock("lock-locked", SessionState.Locked)

    @Test
    fun lockLockedWithNotes() = lock("lock-locked-notes", SessionState.Locked, notCompleted = true, sharePending = true)

    @Test
    fun lockUnlocking() = lock("lock-unlocking", SessionState.Unlocking)

    @Test
    fun lockNoDeviceLock() = lock("lock-no-device-lock", SessionState.NoDeviceLock)

    @Test
    fun lockKeyInvalidated() = lock("lock-key-invalidated", SessionState.KeyInvalidated)

    @Test
    fun lockFailed() = lock("lock-failed", SessionState.Failed(FailureReason.STORAGE_ERROR))

    private fun caseList(name: String, state: CaseListUiState) = harness.shoot(
        name,
        before = { rule -> if (state.archived.isNotEmpty()) rule.onNodeWithText("Archived (${state.archived.size})").performClick() },
    ) {
        CaseListScreen(state, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {})
    }

    @Test
    fun caseListEmpty() = caseList("cases-empty", CaseListUiState())

    @Test
    fun caseListFew() = caseList("cases-few", CaseListUiState(active = SampleState.activeCases))

    @Test
    fun workspaceNavigationAndCollection() {
        val opened = mutableListOf<String>()
        val case = SampleState.activeCases.first()
        harness.shoot("workspace-home", before = { rule ->
            listOf("Evidence" to "evidence", "Incidents" to "timeline", "Reports" to "report", "Vault" to "case").forEach { (label, route) ->
                rule.onNode(hasText(label) and hasClickAction()).performClick()
                rule.onNodeWithText(case.title).performScrollTo().performClick()
                assertEquals("$route:${case.id}", opened.last())
            }
            rule.onNode(hasText("Home") and hasClickAction()).performClick()
            rule.onNodeWithText("Notification collection").performScrollTo().performClick()
            rule.onNodeWithText("Visible message capture").performScrollTo().performClick()
            assertEquals(listOf("evidence:c1", "timeline:c1", "report:c1", "case:c1", "notifications", "capture"), opened)
            rule.onNodeWithText("Your private workspace").performScrollTo()
        }) {
            CaseListScreen(
                CaseListUiState(active = SampleState.activeCases), {}, { _, _ -> }, {}, {}, {}, {}, {},
                { opened += "case:$it" }, {}, {},
                onOpenEvidence = { opened += "evidence:$it" },
                onOpenTimeline = { opened += "timeline:$it" },
                onOpenReports = { opened += "report:$it" },
                onObservationSettings = { opened += "notifications" },
                onCaptureSettings = { opened += "capture" },
            )
        }
    }

    @Test
    fun caseListWithArchived() = caseList(
        "cases-archived-open",
        CaseListUiState(active = SampleState.activeCases, archived = SampleState.archivedCases),
    )

    @Test
    fun caseListWithMessage() = caseList(
        "cases-message",
        CaseListUiState(active = SampleState.activeCases, message = CaseMessage.TITLE_BLANK),
    )

    @Test
    fun newCaseDialog() = harness.shoot("dialog-new-case") {
        TitleDialog(R.string.dialog_new_title, R.string.dialog_create, "synthetic case", {}, {})
    }

    @Test
    fun deleteCaseDialog() = harness.shoot("dialog-delete-case") { DeleteDialog(evidenceCount = 12, onConfirm = {}, onDismiss = {}) }

    private fun caseDetail(name: String, state: CaseDetailUiState) =
        harness.shoot(name) { CaseDetailScreen(state, caseDetailActions()) }

    @Test
    fun caseDetailEmpty() = caseDetail("case-detail-empty", CaseDetailUiState(loaded = true, title = "synthetic case one"))

    @Test
    fun caseDetailManyKinds() = caseDetail(
        "case-detail-evidence",
        CaseDetailUiState(
            loaded = true,
            title = "synthetic case one",
            items = SampleState.evidenceRows,
            integrity = mapOf(
                "e1" to IntegrityStatus.INTACT,
                "e2" to IntegrityStatus.CHECKING,
                "e3" to IntegrityStatus.CHANGED,
                "e4" to IntegrityStatus.FILE_MISSING,
                "e5" to IntegrityStatus.KEY_UNAVAILABLE,
            ),
        ),
    )

    @Test
    fun caseDetailArchived() = caseDetail(
        "case-detail-archived",
        CaseDetailUiState(loaded = true, title = "synthetic old case", archived = true, items = SampleState.evidenceRows.take(3)),
    )

    @Test
    fun addEvidenceChoices() = harness.shoot("add-evidence-choices") {
        androidx.compose.foundation.layout.Column { AddEvidenceActions(enabled = true, callbacks = noPickers, onPaste = {}) }
    }

    @Test
    fun pasteDialog() = harness.shoot("dialog-paste") { PasteDialog(onReview = {}, onDismiss = {}) }

    private fun note(name: String, state: NoteFormState) = harness.shoot(name) {
        ManualNoteScreen(state, NoteActions({}, {}, {}, {}, {}, {}, {}, {}, {}))
    }

    @Test
    fun manualNoteEmpty() = note("note-empty", NoteFormState())

    @Test
    fun manualNoteFilled() = note(
        "note-filled",
        NoteFormState(
            text = "synthetic-sam kept sending messages in the evening. synthetic note.",
            incidentTime = "late evening, 24 September",
            sender = "synthetic-sam",
            app = "synthetic chat app",
            viewOnce = true,
            availability = ContentAvailability.NOT_ACQUIRED,
        ),
    )

    @Test
    fun manualNoteProblem() = note("note-problem", NoteFormState(text = "", problem = NoteProblem.TEXT_REQUIRED))

    private fun importScreen(name: String, state: ImportUiState) = harness.shoot(name) {
        ImportScreen(state, SampleState.activeCases + SampleState.archivedCases, noImport)
    }

    @Test
    fun importPreviewChooseCase() = importScreen(
        "import-preview-choose-case",
        ImportUiState.Previewing(SampleState.pendingBatch, setOf(0, 1), fixedCaseId = null, choice = CaseChoice.None),
    )

    @Test
    fun importPreviewNewCase() = importScreen(
        "import-preview-new-case",
        ImportUiState.Previewing(SampleState.pendingBatch, setOf(0, 1, 2), fixedCaseId = null, choice = CaseChoice.New("synthetic new case")),
    )

    @Test
    fun importPreviewFixedCase() = importScreen(
        "import-preview-fixed-case",
        ImportUiState.Previewing(SampleState.pendingBatch, setOf(0), fixedCaseId = "c1", choice = CaseChoice.None),
    )

    @Test
    fun importProgress() = importScreen("import-progress", ImportUiState.Saving(2, 5))

    private val labels = mapOf<Int, ItemLabel>(
        0 to ItemLabel.PastedText,
        1 to ItemLabel.File("synthetic-chat.txt"),
        2 to ItemLabel.File("synthetic-screenshot.png"),
        3 to ItemLabel.File(null),
        4 to ItemLabel.Unknown,
    )

    @Test
    fun importResult() = importScreen("import-result", ImportUiState.Finished("c1", SampleState.report, labels))

    @Test
    fun importCancelled() = importScreen("import-cancelled", ImportUiState.Cancelled("c1", SampleState.report, labels))

    private fun analysis(name: String, state: AnalysisUiState) = harness.shoot(name) { AnalysisScreen(state, noAnalysis) }

    @Test
    fun analysisIdle() = analysis("analysis-idle", AnalysisUiState.Idle)

    @Test
    fun analysisRunning() = analysis("analysis-running", AnalysisUiState.Running)

    @Test
    fun analysisQuestions() = analysis(
        "analysis-questions",
        AnalysisUiState.Questions(
            ExportQuestions(
                AnalysisOutcome.NeedsExportOptions(
                    "d1",
                    listOf("synthetic-alex", "synthetic-sam"),
                    DateOrder.AMBIGUOUS,
                    120,
                    listOf("01/02/2026", "02/03/2026", "04/05/2026"),
                ),
                ExportAnswers(DateOrder.DAY_MONTH, ZoneId.of("Asia/Kolkata"), OwnerChoice.Sender("synthetic-alex")),
            ),
        ),
    )

    @Test
    fun analysisDone() = analysis(
        "analysis-done",
        AnalysisUiState.Done(
            AnalysisOutcome.Analysed(
                "d1",
                8,
                3,
                InputKind.WHATSAPP_EXPORT,
                setOf(AnalysisWarning.SYSTEM_LINES_SKIPPED, AnalysisWarning.UNSUPPORTED_LANGUAGE_PRESENT),
            ),
        ),
    )

    @Test
    fun analysisRefused() = analysis("analysis-refused", AnalysisUiState.Refused(NotAnalysableReason.PRESERVE_ONLY_TYPE))

    @Test
    fun analysisFailed() = analysis("analysis-failed", AnalysisUiState.Failed)

    private fun patterns(name: String, state: PatternsUiState) = harness.shoot(name) {
        PatternsScreen(state, PatternsActions({}, {}, {}, {}, { _, _, _ -> }))
    }

    @Test
    fun patternsEveryReviewState() = patterns(
        "patterns-cards",
        PatternsUiState(loading = false, cards = SampleState.patternCards, zone = SampleState.zone),
    )

    @Test
    fun patternsRefreshing() = patterns(
        "patterns-refreshing",
        PatternsUiState(
            loading = false,
            cards = SampleState.patternCards.take(2),
            refreshing = true,
            notice = PatternNotice.CHANGED_BEFORE_SAVE,
            zone = SampleState.zone,
        ),
    )

    @Test
    fun patternsPreview() = patterns(
        "patterns-preview",
        PatternsUiState(
            loading = false,
            view = EvidenceView.CANDIDATE_PREVIEW,
            cards = SampleState.patternCards.take(3),
            zone = SampleState.zone,
        ),
    )

    @Test
    fun patternsEmpty() = patterns("patterns-empty", PatternsUiState(loading = false, zone = SampleState.zone))

    @Test
    fun patternsFailed() = patterns("patterns-failed", PatternsUiState(loading = false, failed = true, zone = SampleState.zone))

    private val noFilters = SearchFilterActions({}, {}, {}, {}, {}, {}, {})

    private fun search(name: String, state: SearchUiState) =
        harness.shoot(name) { SearchScreen(state, SearchActions({}, {}, {}, noFilters)) }

    private val hits = listOf(
        SearchHit(
            "e1",
            "d1",
            DerivativeKind.PARSED_TEXT,
            "ev1",
            "synthetic-sam",
            "2026-09-24T15:30:00Z",
            listOf(SearchMatch("synthetic-sam: why do you ignore me", 22, 28), SearchMatch("answer me, why do you reply late", 11, 17)),
        ),
        SearchHit("e3", "d3", DerivativeKind.OCR, "ev2", "synthetic-sam", "2026-09-26T09:00:00Z", listOf(SearchMatch("you ignore me again", 4, 10))),
        SearchHit(
            "e7",
            "e7",
            SearchHit.NOTE_DERIVATIVE_KIND,
            null,
            null,
            null,
            listOf(SearchMatch("my own note: they ignore me at work", 14, 20)),
        ),
    )

    @Test
    fun searchInitial() = search("search-initial", SearchUiState())

    @Test
    fun searchResults() = search("search-results", SearchUiState(query = "ignore", hits = hits))

    @Test
    fun searchLimitReached() = search("search-limit", SearchUiState(query = "ignore", hits = hits, limitReached = true))

    @Test
    fun searchFiltersOpen() = search(
        "search-filters-open",
        SearchUiState(
            query = "ignore",
            hits = hits,
            filtersOpen = true,
            people = listOf(PersonChoice(ActorId("p1"), "synthetic-sam"), PersonChoice(ActorId("p2"), "synthetic-alex")),
            filters = SearchFilterState(
                scope = SearchScope.ACCEPTED_FINDINGS_ONLY,
                personId = ActorId("p1"),
                sources = setOf(SourceChoice.EXPORT),
                fromText = "01/09/2026",
            ),
        ),
    )

    @Test
    fun searchNoResults() = search("search-no-results", SearchUiState(query = "synthetic-unmatched"))

    @Test
    fun searchNoResultsFiltered() = search(
        "search-no-results-filtered",
        SearchUiState(query = "synthetic-unmatched", filters = SearchFilterState(sources = setOf(SourceChoice.NOTE))),
    )

    @Test
    fun searchFailed() = search("search-failed", SearchUiState(query = "ignore", failed = true))

    @Test
    fun deleteEverything() = harness.shoot("delete-everything") { DeleteEverythingScreen(onConfirm = {}, onBack = {}) }

    private fun deletion(name: String, state: DeletionState) =
        harness.shoot(name) { DeletionProgressScreen(state, onRetry = {}, onFinish = {}) }

    @Test
    fun deletionRunning() = deletion("delete-running", DeletionState.Running)

    @Test
    fun deletionIncomplete() = deletion("delete-incomplete", DeletionState.Incomplete)

    @Test
    fun deletionFailed() = deletion("delete-failed", DeletionState.Failed)

    @Test
    fun deletionDone() = deletion("delete-done", DeletionState.Done)

    @Test
    fun designCatalogue() = harness.shoot("design-catalogue") { DesignCatalog(onClose = {}) }

    private fun aiModel(name: String, state: AiModelUiState) = harness.shoot(name) {
        AiModelScreen(state, {}, {}, {}, {}, { _: Uri -> }, {}, {}, {}, {})
    }

    private val installed = InstalledModel("synthetic-model.gguf", "Synthetic model 1.5B", 1_100_000_000L, "1.1 GB", "ab12cd34", isRunning = true)

    @Test
    fun aiModelNone() = aiModel("ai-model-none", AiModelUiState(presets = ModelManager.PRESETS, detectedInDownloads = ModelManager.PRESETS.first()))

    @Test
    fun aiModelInstalled() = aiModel("ai-model-installed", AiModelUiState(installedModel = installed, testOutput = "synthetic test answer"))

    @Test
    fun aiModelImporting() = aiModel(
        "ai-model-importing",
        AiModelUiState(isImporting = true, importedBytes = 400_000_000L, totalBytesToImport = 1_100_000_000L),
    )

    @Test
    fun aiModelTesting() = aiModel("ai-model-testing", AiModelUiState(installedModel = installed, isTestingInference = true))
}
