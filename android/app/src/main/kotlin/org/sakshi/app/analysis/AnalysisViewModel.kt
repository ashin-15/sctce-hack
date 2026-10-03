package org.sakshi.app.analysis

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.sakshi.app.SessionServices
import org.sakshi.processing.analysis.AnalysisOutcome
import org.sakshi.processing.analysis.ExportOptions
import org.sakshi.processing.analysis.NotAnalysableReason
import org.sakshi.processing.stt.ModelSpec
import org.sakshi.processing.stt.ProvisionResult
import org.sakshi.processing.text.DateOrder

/** Runs the text analysis of one evidence item. A function so that a test can stand in for a slow analysis. */
typealias Analyser = suspend (evidenceId: String, options: ExportOptions?) -> AnalysisOutcome

/** Copies a chosen speech model file in, checks it and installs it. The production one is `ModelProvisioner::importFrom`. */
typealias SpeechModelInstaller = (InputStream) -> ProvisionResult

/** Opens the file the person chose, or gives null when it cannot be opened. */
typealias SpeechFileOpener = (Uri) -> InputStream?

/** The answer to "Which of these names is you?". Nothing is preselected. */
sealed interface OwnerChoice {
    data object Unanswered : OwnerChoice

    data class Sender(val name: String) : OwnerChoice

    /** "None of these / I am not sure": the analysis is told there is no owner claim. */
    data object NoneOfThese : OwnerChoice
}

data class ExportAnswers(val dateOrder: DateOrder?, val zone: ZoneId, val owner: OwnerChoice)

/** What the app has to ask about a chat export, and what has been answered so far. */
data class ExportQuestions(val needs: AnalysisOutcome.NeedsExportOptions, val answers: ExportAnswers) {
    /** The order the file itself allows, or null when the file's dates fit both. */
    val fixedDateOrder: DateOrder? get() = needs.detectedDateOrder.takeIf { it != DateOrder.AMBIGUOUS }

    val effectiveDateOrder: DateOrder? get() = fixedDateOrder ?: answers.dateOrder.takeIf { it != DateOrder.AMBIGUOUS }

    val canContinue: Boolean get() = effectiveDateOrder != null && answers.owner != OwnerChoice.Unanswered

    internal fun toOptions(): ExportOptions? {
        val order = effectiveDateOrder ?: return null
        val claim = when (val owner = answers.owner) {
            OwnerChoice.Unanswered -> return null
            OwnerChoice.NoneOfThese -> null
            is OwnerChoice.Sender -> owner.name
        }
        return ExportOptions(order, answers.zone, claim)
    }
}

sealed interface AnalysisUiState {
    /** Nothing is running. Also where a cancel returns to. */
    data object Idle : AnalysisUiState

    data object Running : AnalysisUiState

    data class Questions(val questions: ExportQuestions) : AnalysisUiState

    data class Done(val result: AnalysisOutcome.Analysed) : AnalysisUiState

    data class Refused(val reason: NotAnalysableReason) : AnalysisUiState

    /** A recording could not be read because the speech model file is not on this phone yet. */
    data class SpeechSetupNeeded(val setup: SpeechSetup) : AnalysisUiState

    /** Something unexpected stopped the analysis; nothing from the evidence is kept in this state. */
    data object Failed : AnalysisUiState
}

/** Where the one-time speech model preparation stands. Nothing here is ever downloaded; the person picks a file. */
sealed interface SpeechSetup {
    /** Nothing chosen yet, or a choice that changed nothing was cancelled. */
    data object Needed : SpeechSetup

    /** The file is being copied and checked. [totalBytes] is the size of the file Sakshi expects. */
    data class Installing(val copiedBytes: Long, val totalBytes: Long) : SpeechSetup {
        /** Whole percent, from 0 to 100. */
        val percent: Int get() = if (totalBytes <= 0) 0 else (copiedBytes * PERCENT / totalBytes).coerceIn(0, PERCENT.toLong()).toInt()
    }

    data object Installed : SpeechSetup

    /** The file is not the pinned model. */
    data object WrongFile : SpeechSetup

    data object TooLarge : SpeechSetup

    /** The file could not be opened or copied. */
    data object Failed : SpeechSetup
}

private const val PERCENT = 100

/** Reports each whole step of the bytes read, and stops a cancelled copy by failing the read. */
private class ProgressInputStream(
    input: InputStream,
    private val isActive: () -> Boolean,
    private val onBytes: (Long) -> Unit,
) : FilterInputStream(input) {
    private var total = 0L
    private var reported = 0L

    override fun read(): Int {
        val value = super.read()
        if (value >= 0) advance(1)
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val count = super.read(buffer, offset, length)
        if (count > 0) advance(count.toLong())
        return count
    }

    private fun advance(count: Long) {
        if (!isActive()) throw IOException("The copy was cancelled")
        total += count
        if (total - reported >= REPORT_STEP_BYTES) {
            reported = total
            onBytes(total)
        }
    }

    private companion object {
        const val REPORT_STEP_BYTES: Long = 1L shl 20
    }
}

/**
 * State machine for analysing one saved text item: Running, then a result, a refusal or the export questions, which
 * lead to a second run. It lives in the activity's view model store, so a lock cancels a run and drops the answers.
 *
 * A recording whose speech model file is missing leads to [AnalysisUiState.SpeechSetupNeeded]: the person picks the file
 * once, it is copied and checked off the main thread, and the recording can then be analysed again.
 */
class AnalysisViewModel(
    private val evidenceId: String,
    private val analyser: Analyser,
    private val deviceZone: () -> ZoneId = ZoneId::systemDefault,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val installSpeechModel: SpeechModelInstaller = { ProvisionResult.IoFailed },
    private val openSpeechFile: SpeechFileOpener = { null },
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val speechModelBytes: Long = ModelSpec.WHISPER_BASE_Q5_1.sizeBytes,
) : ViewModel(scope) {
    private val mutableState = MutableStateFlow<AnalysisUiState>(AnalysisUiState.Idle)
    val state: StateFlow<AnalysisUiState> = mutableState.asStateFlow()

    private var started = false
    private var job: Job? = null

    /** Starts the first run when the screen opens; later calls do nothing, so a cancel is not undone by recomposition. */
    fun startOnce() {
        if (started) return
        started = true
        analyse()
    }

    /** Runs the analysis. From the questions it is a fresh run without answers; use [continueWithAnswers] for those. */
    fun analyse() = run(null)

    fun chooseDateOrder(order: DateOrder) = answer { it.copy(dateOrder = order) }

    fun chooseZone(zone: ZoneId) = answer { it.copy(zone = zone) }

    fun chooseOwner(choice: OwnerChoice) = answer { it.copy(owner = choice) }

    private inline fun answer(change: (ExportAnswers) -> ExportAnswers) {
        val current = mutableState.value as? AnalysisUiState.Questions ?: return
        mutableState.value = AnalysisUiState.Questions(current.questions.copy(answers = change(current.questions.answers)))
    }

    fun continueWithAnswers() {
        val current = mutableState.value as? AnalysisUiState.Questions ?: return
        val options = current.questions.toOptions() ?: return
        run(options)
    }

    /** Stops a run and returns to [AnalysisUiState.Idle]. The analysis writes its events in one step at the end. */
    fun cancel() {
        job?.cancel()
        job = null
        val current = mutableState.value
        if (current == AnalysisUiState.Running) mutableState.value = AnalysisUiState.Idle
        if (current is AnalysisUiState.SpeechSetupNeeded && current.setup is SpeechSetup.Installing) {
            mutableState.value = AnalysisUiState.SpeechSetupNeeded(SpeechSetup.Needed)
        }
    }

    /**
     * Copies the speech model file the person chose into the app's private storage and checks it. Does nothing unless
     * the speech preparation is showing and no copy is running. The copy runs off the main thread.
     */
    fun importSpeechModel(uri: Uri) {
        val current = mutableState.value as? AnalysisUiState.SpeechSetupNeeded ?: return
        if (current.setup is SpeechSetup.Installing) return
        mutableState.value = AnalysisUiState.SpeechSetupNeeded(SpeechSetup.Installing(0, speechModelBytes))
        job = viewModelScope.launch {
            val result = try {
                withContext(io) { copySpeechModel(uri) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                ProvisionResult.IoFailed
            } catch (_: SecurityException) {
                ProvisionResult.IoFailed
            }
            mutableState.value = AnalysisUiState.SpeechSetupNeeded(
                when (result) {
                    is ProvisionResult.Installed -> SpeechSetup.Installed
                    ProvisionResult.HashMismatch -> SpeechSetup.WrongFile
                    ProvisionResult.TooLarge -> SpeechSetup.TooLarge
                    ProvisionResult.IoFailed -> SpeechSetup.Failed
                },
            )
        }
    }

    private suspend fun copySpeechModel(uri: Uri): ProvisionResult {
        val context = currentCoroutineContext()
        val input = openSpeechFile(uri) ?: return ProvisionResult.IoFailed
        return input.use { stream ->
            installSpeechModel(
                ProgressInputStream(stream, { context.isActive }) { copied ->
                    mutableState.update { state ->
                        if (state is AnalysisUiState.SpeechSetupNeeded && state.setup is SpeechSetup.Installing) {
                            AnalysisUiState.SpeechSetupNeeded(SpeechSetup.Installing(copied, speechModelBytes))
                        } else {
                            state
                        }
                    }
                },
            )
        }
    }

    private fun run(options: ExportOptions?) {
        if (mutableState.value == AnalysisUiState.Running) return
        val previous = (mutableState.value as? AnalysisUiState.Questions)?.questions?.answers
        mutableState.value = AnalysisUiState.Running
        job = viewModelScope.launch {
            val next = try {
                when (val outcome = analyser(evidenceId, options)) {
                    is AnalysisOutcome.Analysed -> AnalysisUiState.Done(outcome)
                    is AnalysisOutcome.NeedsExportOptions -> AnalysisUiState.Questions(
                        ExportQuestions(outcome, previous ?: ExportAnswers(null, deviceZone(), OwnerChoice.Unanswered)),
                    )
                    is AnalysisOutcome.NotAnalysable -> when (outcome.reason) {
                        NotAnalysableReason.SPEECH_MODEL_UNAVAILABLE -> AnalysisUiState.SpeechSetupNeeded(SpeechSetup.Needed)
                        else -> AnalysisUiState.Refused(outcome.reason)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                AnalysisUiState.Failed
            }
            mutableState.value = next
        }
    }

    companion object {
        fun factory(evidenceId: String, services: SessionServices): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AnalysisViewModel(
                    evidenceId,
                    services.analyse,
                    installSpeechModel = services.speechProvisioner::importFrom,
                    openSpeechFile = { uri -> services.resolver.openInputStream(uri) },
                    io = services.io,
                )
            }
        }
    }
}
