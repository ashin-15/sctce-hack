package org.sakshi.app.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.io.File
import java.io.IOException
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.sakshi.app.SessionServices
import org.sakshi.app.evidence.EvidenceKind
import org.sakshi.app.evidence.EvidenceRow
import org.sakshi.app.evidence.evidenceKindOf
import org.sakshi.app.ui.UiText
import org.sakshi.core.database.EvidenceListItem
import org.sakshi.processing.analysis.EventText
import org.sakshi.core.model.ActorId
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.Event
import org.sakshi.core.model.EventId
import org.sakshi.core.temporal.EvidenceView
import org.sakshi.core.vault.Vault
import org.sakshi.export.report.ExportResult
import org.sakshi.export.report.ExportService
import org.sakshi.export.report.ExportSummary
import org.sakshi.export.report.ReportBuildResult
import org.sakshi.export.report.ReportBuilder
import org.sakshi.export.report.ReportModel
import org.sakshi.export.report.ReportOptions
import org.sakshi.export.report.ReportSelection

/** A saved file the selected events come from, and whether its original goes into the export. */
data class OriginalChoice(val evidenceId: String, val kind: EvidenceKind, val byteSize: Long, val included: Boolean)

/** Counts for the card at the top of the preview. No text from the case. */
data class PreviewSummary(
    val messages: Int,
    val originals: Int,
    val originalBytes: Long,
    val leftOut: Int,
    val includesUnreviewed: Boolean,
)

sealed interface PreviewState {
    data object Idle : PreviewState

    data object Building : PreviewState

    /** A built preview and exactly what it was built from, so the export can make the same report. */
    class Ready(
        val model: ReportModel,
        val rows: List<PreviewRow>,
        val summary: PreviewSummary,
        val selection: ReportSelection,
        val options: ReportOptions,
        val contentSha256: String,
    ) : PreviewState

    data class Refused(val message: UiText) : PreviewState
}

/** A finished export file. [keyId] is the full hex id of the signing key. [reportVersion] is the case's report number. */
class ExportDone(val file: File, val snapshotId: String, val keyId: String, val summary: ExportSummary, val reportVersion: Int)

sealed interface ExportState {
    data object Idle : ExportState

    data class Running(val cancelling: Boolean = false) : ExportState

    class Done(val export: ExportDone) : ExportState

    data class Failed(val message: UiText) : ExportState

    data object Cancelled : ExportState
}

data class ReportUiState(
    val loaded: Boolean = false,
    val rows: List<ReportEventRow> = emptyList(),
    val selected: Set<String> = emptySet(),
    val includeUnreviewed: Boolean = false,
    val originals: List<OriginalChoice> = emptyList(),
    val zone: ZoneId = ZoneId.systemDefault(),
    val preview: PreviewState = PreviewState.Idle,
    val export: ExportState = ExportState.Idle,
    val earlierExport: EarlierExport? = null,
) {
    val selectableCount: Int get() = rows.count { it.selectable }
    val canPreview: Boolean get() = selected.isNotEmpty()
}

private class Loaded(val rows: List<ReportEventRow>, val evidence: Map<String, EvidenceRow>)

private data class Choices(
    val selected: Set<String> = emptySet(),
    val includeUnreviewed: Boolean = false,
    val originals: Set<String> = emptySet(),
    val zone: ZoneId,
)

/**
 * Holds what the person chose to put into one report, the preview built from it and the export that follows. Nothing is
 * selected until the person selects it. The activity clears this view model when the session locks, which also wipes
 * the export folder.
 */
class ReportViewModel(
    private val caseId: String,
    private val vault: Vault,
    private val builder: ReportBuilder,
    private val exports: ExportService,
    zone: ZoneId = ZoneId.systemDefault(),
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : ViewModel(scope) {
    private val choices = MutableStateFlow(Choices(zone = zone))
    private val preview = MutableStateFlow<PreviewState>(PreviewState.Idle)
    private val export = MutableStateFlow<ExportState>(ExportState.Idle)
    private var previewJob: Job? = null
    private var exportJob: Job? = null
    private val bodyCache = HashMap<String, String>()

    private val loaded: StateFlow<Loaded?> =
        combine(vault.events.observeLatest(CaseId(caseId)), vault.evidence.observeForCase(caseId)) { events, evidence -> events to evidence }
            .map { (events, evidence) -> load(events, evidence) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val earlier: StateFlow<EarlierExport?> = vault.reports.observeDrift(CaseId(caseId))
        .map { ExportHistoryMapping.earlierExport(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val state: StateFlow<ReportUiState> = combine(loaded, choices, preview, export, earlier) { data, chosen, previewing, exporting, exported ->
        if (data == null) {
            ReportUiState(zone = chosen.zone, preview = previewing, export = exporting, earlierExport = exported)
        } else {
            val selected = chosen.selected intersect ReportRows.selectableIds(data.rows)
            ReportUiState(
                loaded = true,
                rows = data.rows,
                selected = selected,
                includeUnreviewed = chosen.includeUnreviewed,
                originals = originalsFor(data, selected, chosen.originals),
                zone = chosen.zone,
                preview = previewing,
                export = exporting,
                earlierExport = exported,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ReportUiState(zone = zone))

    private suspend fun load(events: List<Event>, items: List<EvidenceListItem>): Loaded {
        val missing = events.filter { it.eventId.value !in bodyCache }
        EventText(vault).bodiesOf(missing).forEach { (id, body) -> if (body != null) bodyCache[id.value] = body }
        val actorLabels: Map<ActorId, String> = vault.actors.list(CaseId(caseId)).associate { it.id to it.displayLabel }
        val evidence = items.associate { item ->
            item.id to EvidenceRow(
                id = item.id,
                receivedAt = item.receivedAt,
                byteSize = item.byteSize,
                kind = evidenceKindOf(item.acquisitionKind, item.detectedMime, vault.evidence.details(item.id)?.declaredMime),
                supportState = item.supportState,
            )
        }
        val sources = evidenceBehind(events, evidence.keys)
        val rows = ReportRows.build(events, bodyCache.toMap(), actorLabels) { sources[it.eventId.value].orEmpty() }
        return Loaded(rows, evidence)
    }

    /** The saved files each event comes from. A reference may name a derivative text of a file instead of the file. */
    private suspend fun evidenceBehind(events: List<Event>, known: Set<String>): Map<String, Set<String>> {
        val resolved = HashMap<String, String?>()
        return events.associate { event ->
            val ids = event.evidenceReferences.mapNotNull { reference ->
                val artifact = reference.artifactId.value
                if (artifact !in resolved) {
                    resolved[artifact] = if (artifact in known) artifact else vault.derivatives.get(artifact)?.evidenceId?.takeIf { it in known }
                }
                resolved[artifact]
            }
            event.eventId.value to ids.toSet()
        }
    }

    private fun originalsFor(data: Loaded, selected: Set<String>, included: Set<String>): List<OriginalChoice> =
        data.rows.filter { it.eventId in selected }.flatMap { it.evidenceIds }.distinct().mapNotNull { id ->
            data.evidence[id]?.let { OriginalChoice(id, it.kind, it.byteSize, included = id in included) }
        }

    fun toggle(eventId: String) = changeChoices { chosen ->
        if (eventId in chosen.selected) chosen.copy(selected = chosen.selected - eventId) else chosen.copy(selected = chosen.selected + eventId)
    }

    fun selectAll() {
        val data = loaded.value ?: return
        changeChoices { it.copy(selected = ReportRows.selectableIds(data.rows)) }
    }

    fun selectNone() = changeChoices { it.copy(selected = emptySet()) }

    /** Selects the events the person tagged themselves or agreed with. */
    fun selectTagged() {
        val data = loaded.value ?: return
        changeChoices { it.copy(selected = ReportRows.taggedIds(data.rows)) }
    }

    fun setIncludeUnreviewed(value: Boolean) = changeChoices { it.copy(includeUnreviewed = value) }

    fun toggleOriginal(evidenceId: String) = changeChoices { chosen ->
        if (evidenceId in chosen.originals) chosen.copy(originals = chosen.originals - evidenceId) else chosen.copy(originals = chosen.originals + evidenceId)
    }

    fun setZone(zone: ZoneId) = changeChoices { it.copy(zone = zone) }

    private fun changeChoices(change: (Choices) -> Choices) {
        choices.update(change)
    }

    /** What the screen currently shows as chosen, or null when nothing can be reported yet. */
    private fun currentSelection(): ReportSelection? {
        val current = state.value
        if (!current.loaded || current.selected.isEmpty()) return null
        return ReportSelection(
            caseId = CaseId(caseId),
            eventIds = current.selected.map { EventId(it) }.toSet(),
            includeOriginalsFor = current.originals.filter { it.included }.map { it.evidenceId }.toSet(),
            view = EvidenceView.CONFIRMED_ONLY,
            zone = current.zone,
        )
    }

    /** Builds the report model for the current selection and shows it as a preview. */
    fun buildPreview() {
        val selection = currentSelection() ?: return
        val includeUnreviewed = choices.value.includeUnreviewed
        val sizes = state.value.originals.filter { it.included }.sumOf { it.byteSize }
        previewJob?.cancel()
        preview.value = PreviewState.Building
        previewJob = viewModelScope.launch {
            preview.value = try {
                val options = ReportOptions(
                    includeUnreviewedSuggestions = includeUnreviewed,
                    reportVersion = vault.reports.nextVersion(selection.caseId),
                )
                when (val built = builder.build(selection, options)) {
                    is ReportBuildResult.Refused -> PreviewState.Refused(ReportMessages.refusal(built.reason))
                    is ReportBuildResult.Built -> PreviewState.Ready(
                        built.model,
                        ReportPreviewMapping.rows(built.model),
                        PreviewSummary(
                            messages = built.bundleInputs.events.size,
                            originals = built.bundleInputs.includeOriginalsFor.size,
                            originalBytes = sizes,
                            leftOut = built.bundleInputs.omitted.eventCount,
                            includesUnreviewed = options.includeUnreviewedSuggestions,
                        ),
                        selection,
                        options,
                        built.contentSha256,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IllegalStateException) {
                PreviewState.Refused(ReportMessages.unexpected)
            } catch (_: IllegalArgumentException) {
                PreviewState.Refused(ReportMessages.unexpected)
            }
        }
    }

    /** Leaves the preview. A build that is still running is stopped. */
    fun backToSelection() {
        previewJob?.cancel()
        preview.value = PreviewState.Idle
        if (export.value !is ExportState.Running && export.value !is ExportState.Done) export.value = ExportState.Idle
    }

    /**
     * Makes the export file for the report that was previewed, with the choices it was built from and not the ones on
     * screen now. Does nothing without a ready preview. The outcome is in [state].
     */
    fun startExport() {
        if (export.value is ExportState.Running || export.value is ExportState.Done) return
        val previewed = preview.value as? PreviewState.Ready ?: return
        export.value = ExportState.Running()
        exportJob = viewModelScope.launch {
            try {
                export.value = when (val result = exports.export(previewed.selection, previewed.options, previewed.contentSha256)) {
                    is ExportResult.Exported -> ExportState.Done(ExportDone(result.zipFile, result.snapshotId, result.signerKeyId, result.summary, result.reportVersion))
                    is ExportResult.Refused -> ExportState.Failed(ReportMessages.refusal(result.reason))
                    is ExportResult.Failed -> ExportState.Failed(ReportMessages.failure(result.reason))
                }
            } catch (cancelled: CancellationException) {
                exports.clearExports()
                export.value = ExportState.Cancelled
                throw cancelled
            } catch (_: IOException) {
                export.value = ExportState.Failed(ReportMessages.unexpected)
            } catch (_: RuntimeException) {
                export.value = ExportState.Failed(ReportMessages.unexpected)
            }
        }
    }

    /**
     * Asks a running export to stop. The export service finishes the step it is in and removes what it made, so the
     * state changes to cancelled once nothing is left in the export folder.
     */
    fun cancelExport() {
        if (export.value !is ExportState.Running) return
        export.value = ExportState.Running(cancelling = true)
        exportJob?.cancel()
    }

    /** Dismisses a failure or cancellation notice. */
    fun exportNoticeShown() {
        if (export.value is ExportState.Failed || export.value == ExportState.Cancelled) export.value = ExportState.Idle
    }

    /** Leaves the result screen, whichever way: removes the export file and forgets the preview. */
    fun leaveResult() {
        exports.clearExports()
        export.value = ExportState.Idle
        preview.value = PreviewState.Idle
    }

    override fun onCleared() {
        exports.clearExports()
    }

    companion object {
        fun factory(caseId: String, services: SessionServices): ViewModelProvider.Factory =
            viewModelFactory { initializer { ReportViewModel(caseId, services.vault, services.reportBuilder, services.exports) } }
    }
}
