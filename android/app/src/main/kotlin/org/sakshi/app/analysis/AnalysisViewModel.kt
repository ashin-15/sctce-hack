package org.sakshi.app.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.sakshi.app.SessionServices
import org.sakshi.processing.analysis.AnalysisOutcome
import org.sakshi.processing.analysis.ExportOptions
import org.sakshi.processing.analysis.NotAnalysableReason
import org.sakshi.processing.text.DateOrder

/** Runs the text analysis of one evidence item. A function so that a test can stand in for a slow analysis. */
typealias Analyser = suspend (evidenceId: String, options: ExportOptions?) -> AnalysisOutcome

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

    /** Something unexpected stopped the analysis; nothing from the evidence is kept in this state. */
    data object Failed : AnalysisUiState
}

/**
 * State machine for analysing one saved text item: Running, then a result, a refusal or the export questions, which
 * lead to a second run. It lives in the activity's view model store, so a lock cancels a run and drops the answers.
 */
class AnalysisViewModel(
    private val evidenceId: String,
    private val analyser: Analyser,
    private val deviceZone: () -> ZoneId = ZoneId::systemDefault,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
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
        if (mutableState.value == AnalysisUiState.Running) mutableState.value = AnalysisUiState.Idle
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
                    is AnalysisOutcome.NotAnalysable -> AnalysisUiState.Refused(outcome.reason)
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
            initializer { AnalysisViewModel(evidenceId, services.textAnalysis::analyse) }
        }
    }
}
