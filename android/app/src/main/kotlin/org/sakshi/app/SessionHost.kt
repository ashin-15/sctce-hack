package org.sakshi.app

import android.content.ActivityNotFoundException
import android.app.Activity
import android.widget.Toast
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import org.sakshi.acquisition.importer.ImportMechanism
import org.sakshi.acquisition.projection.ProjectionCaptureMode
import java.time.ZoneId
import org.sakshi.app.aimodel.AiModelScreen
import org.sakshi.app.aimodel.AiModelViewModel
import org.sakshi.app.analysis.AnalysisActions
import org.sakshi.app.analysis.AnalysisScreen
import org.sakshi.app.analysis.AnalysisViewModel
import org.sakshi.processing.llm.model.ModelManager
import org.sakshi.app.cases.CaseListScreen
import org.sakshi.app.cases.CaseListViewModel
import org.sakshi.app.observation.NotificationObservationScreen
import org.sakshi.app.capture.VisibleCaptureScreen
import org.sakshi.app.deletion.DeleteEverythingScreen
import org.sakshi.app.evidence.AddEvidenceCallbacks
import org.sakshi.app.evidence.CaseDetailActions
import org.sakshi.app.evidence.CaseDetailScreen
import org.sakshi.app.evidence.CaseDetailViewModel
import org.sakshi.app.importing.ImportActions
import org.sakshi.app.importing.ImportScreen
import org.sakshi.app.importing.ImportViewModel
import org.sakshi.app.note.ManualNoteScreen
import org.sakshi.app.note.ManualNoteViewModel
import org.sakshi.app.note.NoteActions
import org.sakshi.app.patterns.PatternsActions
import org.sakshi.app.patterns.PatternsScreen
import org.sakshi.app.patterns.PatternsViewModel
import org.sakshi.app.people.WhoIsWhoActions
import org.sakshi.app.people.WhoIsWhoScreen
import org.sakshi.app.people.WhoIsWhoViewModel
import org.sakshi.app.search.SearchActions
import org.sakshi.app.search.SearchFilterActions
import org.sakshi.app.search.SearchScreen
import org.sakshi.app.search.SearchViewModel
import org.sakshi.app.report.ExportResultActions
import org.sakshi.app.report.ExportResultScreen
import org.sakshi.app.report.ExportShare
import org.sakshi.app.report.ExportState
import org.sakshi.app.report.ReportPreviewActions
import org.sakshi.app.report.ReportPreviewScreen
import org.sakshi.app.report.ReportSelectionActions
import org.sakshi.app.report.ReportSelectionScreen
import org.sakshi.app.report.ReportViewModel
import org.sakshi.app.review.EventReviewActions
import org.sakshi.app.review.EventReviewScreen
import org.sakshi.app.review.EventReviewViewModel
import org.sakshi.app.timeline.TimelineActions
import org.sakshi.app.timeline.TimelineScreen
import org.sakshi.app.timeline.TimelineViewModel

/**
 * The unlocked session. Every view model here comes from [owner]'s store, which the activity clears on lock, so
 * case titles, evidence rows, pending previews and typed notes do not outlive the session.
 */
@Composable
fun SessionHost(services: SessionServices, container: AppContainer, owner: ViewModelStoreOwner) {
    val context = LocalContext.current
    val settingsUnavailable = stringResource(R.string.capture_settings_unavailable)
    val projectionUnavailable = stringResource(R.string.projection_capture_failed)
    val projectionNotificationsDisabled = stringResource(R.string.projection_notifications_disabled)
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        container.pickerGrace.end()
        org.sakshi.acquisition.notifications.NotificationObservation.from(context).refreshAccess()
    }
    val openSystemSettings: (android.content.Intent) -> Unit = { intent ->
        container.pickerGrace.begin()
        try {
            settingsLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            container.pickerGrace.end()
            Toast.makeText(context, settingsUnavailable, Toast.LENGTH_LONG).show()
        }
    }
    var projectionMode by remember { mutableStateOf(ProjectionCaptureMode.SNAPSHOT) }
    val projectionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        container.pickerGrace.end()
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            runCatching { container.projectionCapture.start(context, result.resultCode, data, projectionMode) }
                .onFailure { Toast.makeText(context, projectionUnavailable, Toast.LENGTH_LONG).show() }
        }
    }
    val requestProjection: (ProjectionCaptureMode) -> Unit = { mode ->
        projectionMode = mode
        if (!container.projectionCapture.notificationsEnabled()) {
            Toast.makeText(context, projectionNotificationsDisabled, Toast.LENGTH_LONG).show()
            openSystemSettings(
                android.content.Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        } else {
            container.pickerGrace.begin()
            try {
                projectionLauncher.launch(container.projectionCapture.consentIntent())
            } catch (_: Exception) {
                container.pickerGrace.end()
                Toast.makeText(context, projectionUnavailable, Toast.LENGTH_LONG).show()
            }
        }
    }
    val navigator = remember(services) { ViewModelProvider(owner)[SessionNavigator::class.java] }
    LaunchedEffect(navigator) {
        val activity = context as? android.app.Activity
        if (activity?.intent?.getBooleanExtra("open_observation", false) == true) {
            activity.intent.removeExtra("open_observation")
            navigator.openObservation()
        }
    }
    val importModel = remember(services) { ViewModelProvider(owner, ImportViewModel.factory(services))[ImportViewModel::class.java] }
    val current by navigator.current.collectAsState()
    val canGoBack by navigator.canGoBack.collectAsState()
    val importState by importModel.state.collectAsState()
    val sharePending by container.importCoordinator.hasPending.collectAsState()

    BackHandler(enabled = canGoBack) {
        navigator.back()
    }

    LaunchedEffect(sharePending) {
        if (sharePending) container.importCoordinator.take()?.let(importModel::startShare)
    }

    when (val screen = sessionScreenFor(current, importState)) {
        SessionScreen.CaseList -> CaseListRoute(services, owner, navigator, container)
        is SessionScreen.CaseDetail -> CaseDetailRoute(screen.caseId, services, owner, navigator, importModel, container)
        SessionScreen.Import -> {
            val cases by importModel.cases.collectAsState()
            val actions = remember(importModel, navigator) {
                ImportActions(
                    toggle = importModel::toggle,
                    chooseCase = importModel::chooseCase,
                    chooseNewCase = importModel::chooseNewCase,
                    save = importModel::save,
                    dismiss = importModel::dismiss,
                    cancelSaving = importModel::cancelSaving,
                    done = { caseId ->
                        if (caseId != null) navigator.openCase(caseId)
                        importModel.dismiss()
                    },
                    analyse = { caseId, evidenceId ->
                        navigator.openCase(caseId)
                        navigator.openAnalysis(caseId, evidenceId)
                        importModel.dismiss()
                    },
                )
            }
            ImportScreen(importState, cases, actions)
        }
        is SessionScreen.ManualNote -> NoteRoute(screen.caseId, services, owner, navigator)
        is SessionScreen.Analysis -> AnalysisRoute(screen, services, owner, navigator, container)
        is SessionScreen.Timeline -> TimelineRoute(screen.caseId, services, owner, navigator)
        is SessionScreen.EventReview -> EventReviewRoute(screen, services, owner, navigator)
        is SessionScreen.WhoIsWho -> WhoIsWhoRoute(screen, services, owner, navigator)
        is SessionScreen.Patterns -> PatternsRoute(screen.caseId, services, owner, navigator)
        is SessionScreen.Search -> SearchRoute(screen.caseId, services, owner, navigator)
        is SessionScreen.ReportSelection -> ReportSelectionRoute(screen.caseId, services, owner, navigator)
        is SessionScreen.ReportPreview -> ReportPreviewRoute(screen.caseId, services, owner, navigator)
        is SessionScreen.ExportResult -> ExportResultRoute(screen.caseId, services, owner, navigator, container)
        SessionScreen.AiModel -> AiModelRoute(owner, navigator, container)
        SessionScreen.Observation -> NotificationObservationScreen(
            services, navigator::back,
            onAnalyse = { caseId, evidenceId ->
                navigator.openCase(caseId)
                navigator.openAnalysis(caseId, evidenceId)
            },
            onOpenAccessSettings = openSystemSettings,
        )
        SessionScreen.VisibleCapture -> VisibleCaptureScreen(
            services, navigator::back,
            onAnalyse = { caseId, evidenceId ->
                navigator.openCase(caseId)
                navigator.openAnalysis(caseId, evidenceId)
            },
            onOpenAccessSettings = openSystemSettings,
            projection = container.projectionCapture,
            onRequestProjection = requestProjection,
        )
        SessionScreen.DeleteEverything -> DeleteEverythingScreen(
            onConfirm = {
                org.sakshi.acquisition.notifications.NotificationObservation.from(context).disable()
                org.sakshi.acquisition.accessibility.AccessibleCapture.from(context).revoke()
                // Everything that holds saved data is released first; the deletion then outlives this screen and session.
                container.deletion.start {
                    services.exports.clearExports()
                    services.close()
                }
            },
            onBack = navigator::back,
        )
    }
}

@Composable
private fun CaseListRoute(
    services: SessionServices,
    owner: ViewModelStoreOwner,
    navigator: SessionNavigator,
    container: AppContainer,
) {
    val model = remember(services) {
        ViewModelProvider(owner, CaseListViewModel.factory(services.vault.cases))[CaseListViewModel::class.java]
    }
    val state by model.uiState.collectAsState()
    CaseListScreen(
        state = state,
        onCreate = model::create,
        onRename = model::rename,
        onArchive = model::archive,
        onUnarchive = model::unarchive,
        onDelete = model::delete,
        onMessageShown = model::messageShown,
        onLock = {
            org.sakshi.acquisition.notifications.NotificationObservation.from(services.appContext).stopAndClear()
            org.sakshi.acquisition.accessibility.AccessibleCapture.from(services.appContext).stopAndClear()
            container.session.lock()
        },
        onOpen = navigator::openCase,
        onDeleteEverything = navigator::openDeleteEverything,
        onOpenAiModel = navigator::openAiModel,
        onOpenEvidence = navigator::openCase,
        onOpenTimeline = { caseId -> navigator.openCase(caseId); navigator.openTimeline(caseId) },
        onOpenReports = { caseId -> navigator.openCase(caseId); navigator.openReport(caseId) },
        onObservationSettings = navigator::openObservation,
        onCaptureSettings = navigator::openVisibleCapture,
    )
}

@Composable
private fun AiModelRoute(
    owner: ViewModelStoreOwner,
    navigator: SessionNavigator,
    container: AppContainer,
) {
    val context = LocalContext.current
    val modelManager = remember { ModelManager(context) }
    val model = remember(modelManager) {
        ViewModelProvider(owner, AiModelViewModel.factory(modelManager))[AiModelViewModel::class.java]
    }
    val state by model.uiState.collectAsState()
    AiModelScreen(
        state = state,
        onBack = navigator::back,
        onDownloadPreset = { preset ->
            container.pickerGrace.begin()
            model.downloadPreset(preset, context)
        },
        onPickerOpening = container.pickerGrace::begin,
        onPickerClosed = container.pickerGrace::end,
        onImportUri = { uri -> model.importFromUri(uri, context) },
        onImportDetected = model::importDetectedDownload,
        onDeleteModel = model::deleteModel,
        onClearNotice = model::clearNotice,
        onTestInference = model::testInference,
    )
}

@Composable
private fun CaseDetailRoute(
    caseId: String,
    services: SessionServices,
    owner: ViewModelStoreOwner,
    navigator: SessionNavigator,
    importModel: ImportViewModel,
    container: AppContainer,
) {
    val model = remember(services, caseId) {
        val factory = CaseDetailViewModel.factory(caseId, services.vault.cases, services.vault.evidence)
        ViewModelProvider(owner, factory)["case-$caseId", CaseDetailViewModel::class.java]
    }
    val state by model.uiState.collectAsState()
    val actions = remember(model, importModel, navigator) {
        CaseDetailActions(
            back = navigator::showCaseList,
            lock = container.session::lock,
            check = model::checkIntegrity,
            delete = model::delete,
            messageShown = model::messageShown,
            pasted = { importModel.startPasted(caseId, it) },
            add = AddEvidenceCallbacks(
                onDocuments = { uris: List<Uri> -> importModel.startPicked(caseId, uris, ImportMechanism.DOCUMENT_PICKER) },
                onMedia = { uris: List<Uri> -> importModel.startPicked(caseId, uris, ImportMechanism.PHOTO_PICKER) },
                onNote = { navigator.openNote(caseId) },
                onPickerOpening = container.pickerGrace::begin,
                onPickerClosed = container.pickerGrace::end,
            ),
            analyse = { evidenceId -> navigator.openAnalysis(caseId, evidenceId) },
            openTimeline = { navigator.openTimeline(caseId) },
            openPatterns = { navigator.openPatterns(caseId) },
            openReport = { navigator.openReport(caseId) },
            openSearch = { navigator.openSearch(caseId) },
        )
    }
    CaseDetailScreen(state, actions)
}

@Composable
private fun NoteRoute(
    caseId: String,
    services: SessionServices,
    owner: ViewModelStoreOwner,
    navigator: SessionNavigator,
) {
    val model = remember(services, caseId) {
        ViewModelProvider(owner, ManualNoteViewModel.factory(caseId, services.importer))["note-$caseId", ManualNoteViewModel::class.java]
    }
    val state by model.state.collectAsState()
    val actions = remember(model, navigator) {
        NoteActions(
            setText = model::setText,
            setIncidentTime = model::setIncidentTime,
            setSender = model::setSender,
            setApp = model::setApp,
            setViewOnce = model::setViewOnce,
            setAvailability = model::setAvailability,
            save = model::save,
            cancel = { navigator.openCase(caseId) },
            saved = {
                model.savedAcknowledged()
                navigator.openCase(caseId)
            },
        )
    }
    ManualNoteScreen(state, actions)
}

@Composable
private fun AnalysisRoute(
    screen: SessionScreen.Analysis,
    services: SessionServices,
    owner: ViewModelStoreOwner,
    navigator: SessionNavigator,
    container: AppContainer,
) {
    val model = remember(services, screen) {
        ViewModelProvider(owner, AnalysisViewModel.factory(screen.evidenceId, services))["analysis-${screen.evidenceId}", AnalysisViewModel::class.java]
    }
    val state by model.state.collectAsState()
    val actions = remember(model, navigator, screen, container) {
        AnalysisActions(
            back = {
                model.cancel()
                navigator.back()
            },
            analyse = model::analyse,
            cancel = model::cancel,
            chooseDateOrder = model::chooseDateOrder,
            chooseZone = model::chooseZone,
            chooseOwner = model::chooseOwner,
            continueWithAnswers = model::continueWithAnswers,
            openTimeline = { navigator.replaceTopWithTimeline(screen.caseId) },
            startOnce = model::startOnce,
            importSpeechModel = model::importSpeechModel,
            onPickerOpening = container.pickerGrace::begin,
            onPickerClosed = container.pickerGrace::end,
        )
    }
    AnalysisScreen(state, actions)
}

@Composable
private fun TimelineRoute(caseId: String, services: SessionServices, owner: ViewModelStoreOwner, navigator: SessionNavigator) {
    val model = remember(services, caseId) {
        ViewModelProvider(owner, TimelineViewModel.factory(caseId, services))["timeline-$caseId", TimelineViewModel::class.java]
    }
    val state by model.uiState.collectAsState()
    val gapOutcome by model.gapOutcome.collectAsState()
    val actions = remember(model, navigator, caseId) {
        TimelineActions(
            back = navigator::back,
            openEvent = { navigator.openEventReview(caseId, it) },
            openPatterns = { navigator.openPatterns(caseId) },
            openWhoIsWho = { navigator.openWhoIsWho(caseId) },
            setFilter = model::setFilter,
            addGap = model::addGap,
            gapOutcomeShown = model::gapOutcomeShown,
            openSearch = { navigator.openSearch(caseId) },
        )
    }
    TimelineScreen(state, gapOutcome, actions)
}

@Composable
private fun EventReviewRoute(
    screen: SessionScreen.EventReview,
    services: SessionServices,
    owner: ViewModelStoreOwner,
    navigator: SessionNavigator,
) {
    val model = remember(services, screen) {
        ViewModelProvider(owner, EventReviewViewModel.factory(screen.caseId, screen.eventId, services))["review-${screen.eventId}", EventReviewViewModel::class.java]
    }
    val state by model.state.collectAsState()
    val actions = remember(model, navigator, screen) {
        EventReviewActions(
            back = navigator::back,
            agree = model::agree,
            disagree = model::disagree,
            notSure = model::notSure,
            addOwnTag = model::addOwnTag,
            setDirection = model::setDirection,
            markWantedness = model::markWantedness,
            markBoundary = model::markBoundary,
            openWhoIsWho = { claim -> navigator.openWhoIsWho(screen.caseId, claim) },
            noticeShown = model::noticeShown,
        )
    }
    EventReviewScreen(state, ZoneId.systemDefault(), actions)
}

@Composable
private fun WhoIsWhoRoute(
    screen: SessionScreen.WhoIsWho,
    services: SessionServices,
    owner: ViewModelStoreOwner,
    navigator: SessionNavigator,
) {
    val model = remember(services, screen) {
        val factory = WhoIsWhoViewModel.factory(screen.caseId, screen.focusClaim, services)
        ViewModelProvider(owner, factory)["people-${screen.caseId}-${screen.focusClaim.hashCode()}", WhoIsWhoViewModel::class.java]
    }
    val state by model.state.collectAsState()
    val actions = remember(model, navigator) {
        WhoIsWhoActions(
            back = navigator::back,
            markOwn = model::markOwn,
            assignToPerson = model::assignToPerson,
            assignToNewPerson = model::assignToNewPerson,
            undo = model::undo,
            noticeShown = model::noticeShown,
        )
    }
    WhoIsWhoScreen(state, ZoneId.systemDefault(), actions)
}

@Composable
private fun PatternsRoute(caseId: String, services: SessionServices, owner: ViewModelStoreOwner, navigator: SessionNavigator) {
    val model = remember(services, caseId) {
        ViewModelProvider(owner, PatternsViewModel.factory(caseId, services))["patterns-$caseId", PatternsViewModel::class.java]
    }
    val state by model.state.collectAsState()
    val actions = remember(model, navigator, caseId) {
        PatternsActions(
            back = navigator::back,
            setView = model::setView,
            openEvent = { navigator.openEventReview(caseId, it) },
            enter = model::recompute,
            answer = model::answer,
        )
    }
    PatternsScreen(state, actions)
}

@Composable
private fun SearchRoute(caseId: String, services: SessionServices, owner: ViewModelStoreOwner, navigator: SessionNavigator) {
    val model = remember(services, caseId) {
        ViewModelProvider(owner, SearchViewModel.factory(caseId, services))["search-$caseId", SearchViewModel::class.java]
    }
    val state by model.state.collectAsState()
    val actions = remember(model, navigator, caseId) {
        SearchActions(
            back = navigator::back,
            openEvent = { navigator.openEventReview(caseId, it) },
            onQueryChanged = model::onQueryChanged,
            filters = SearchFilterActions(
                setOpen = model::setFiltersOpen,
                setScope = model::setScope,
                setPerson = model::setPerson,
                toggleSource = model::toggleSource,
                setFromText = model::setFromText,
                setUntilText = model::setUntilText,
                clear = model::clearFilters,
            ),
        )
    }
    SearchScreen(state, actions)
}

@Composable
private fun reportModel(caseId: String, services: SessionServices, owner: ViewModelStoreOwner): ReportViewModel =
    remember(services, caseId) {
        ViewModelProvider(owner, ReportViewModel.factory(caseId, services))["report-$caseId", ReportViewModel::class.java]
    }

@Composable
private fun ReportSelectionRoute(caseId: String, services: SessionServices, owner: ViewModelStoreOwner, navigator: SessionNavigator) {
    val model = reportModel(caseId, services, owner)
    val state by model.state.collectAsState()
    val actions = remember(model, navigator, caseId) {
        ReportSelectionActions(
            back = navigator::back,
            toggle = model::toggle,
            selectAll = model::selectAll,
            selectNone = model::selectNone,
            selectTagged = model::selectTagged,
            setUnreviewed = model::setIncludeUnreviewed,
            toggleOriginal = model::toggleOriginal,
            setZone = model::setZone,
            preview = {
                model.buildPreview()
                navigator.openReportPreview(caseId)
            },
        )
    }
    ReportSelectionScreen(state, actions)
}

@Composable
private fun ReportPreviewRoute(caseId: String, services: SessionServices, owner: ViewModelStoreOwner, navigator: SessionNavigator) {
    val model = reportModel(caseId, services, owner)
    val state by model.state.collectAsState()
    LaunchedEffect(state.export is ExportState.Done) {
        if (state.export is ExportState.Done) navigator.openExportResult(caseId)
    }
    val actions = remember(model, navigator) {
        ReportPreviewActions(
            back = {
                model.backToSelection()
                navigator.back()
            },
            create = model::startExport,
            cancelExport = model::cancelExport,
            noticeShown = model::exportNoticeShown,
        )
    }
    ReportPreviewScreen(state, actions)
}

@Composable
private fun ExportResultRoute(
    caseId: String,
    services: SessionServices,
    owner: ViewModelStoreOwner,
    navigator: SessionNavigator,
    container: AppContainer,
) {
    val model = reportModel(caseId, services, owner)
    val state by model.state.collectAsState()
    val context = LocalContext.current
    val title = stringResource(R.string.export_share_title)
    val unavailable = stringResource(R.string.export_share_unavailable)
    // The share sheet covers the app, so the session must not lock while it is open. The result callback fires when the
    // person comes back, with or without a share.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { container.pickerGrace.end() }
    val done = (state.export as? ExportState.Done)?.export
    val actions = remember(model, navigator, caseId, done) {
        ExportResultActions(
            leave = {
                model.leaveResult()
                navigator.openCase(caseId)
            },
            share = {
                if (done != null) {
                    container.pickerGrace.begin()
                    try {
                        launcher.launch(ExportShare.chooser(context, done.file, title))
                    } catch (_: ActivityNotFoundException) {
                        container.pickerGrace.end()
                        Toast.makeText(context, unavailable, Toast.LENGTH_LONG).show()
                    }
                }
            },
        )
    }
    if (done != null) ExportResultScreen(done, actions) else LaunchedEffect(Unit) { actions.leave() }
}
