package org.sakshi.app

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.sakshi.core.vault.SenderSelector

/**
 * Where the user is inside the unlocked session, as a stack so that back pops one level. Lives in the view model
 * store, so a lock discards it. Opening the case list or a case starts a fresh stack; the screens that belong to a
 * case are pushed on top of it.
 */
class SessionNavigator : ViewModel() {
    private val stack = ArrayDeque<SessionScreen>().apply { addLast(SessionScreen.CaseList) }
    private val mutableCurrent = MutableStateFlow<SessionScreen>(SessionScreen.CaseList)
    val current: StateFlow<SessionScreen> = mutableCurrent.asStateFlow()
    private val mutableCanGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = mutableCanGoBack.asStateFlow()

    private fun publish() {
        mutableCurrent.value = stack.last()
        mutableCanGoBack.value = stack.size > 1
    }

    private fun reset(vararg screens: SessionScreen) {
        stack.clear()
        stack.addAll(screens)
        publish()
    }

    private fun push(screen: SessionScreen) {
        stack.addLast(screen)
        publish()
    }

    fun showCaseList() = reset(SessionScreen.CaseList)

    fun openCase(caseId: String) = reset(SessionScreen.CaseList, SessionScreen.CaseDetail(caseId))

    fun openNote(caseId: String) = reset(SessionScreen.CaseList, SessionScreen.CaseDetail(caseId), SessionScreen.ManualNote(caseId))

    fun openAnalysis(caseId: String, evidenceId: String) = push(SessionScreen.Analysis(caseId, evidenceId))

    fun openTimeline(caseId: String) = push(SessionScreen.Timeline(caseId))

    fun openEventReview(caseId: String, eventId: String) = push(SessionScreen.EventReview(caseId, eventId))

    fun openWhoIsWho(caseId: String, focusClaim: SenderSelector? = null) = push(SessionScreen.WhoIsWho(caseId, focusClaim))

    fun openPatterns(caseId: String) = push(SessionScreen.Patterns(caseId))

    fun openSearch(caseId: String) = push(SessionScreen.Search(caseId))

    fun openDeleteEverything() = push(SessionScreen.DeleteEverything)

    fun openReport(caseId: String) = push(SessionScreen.ReportSelection(caseId))

    fun openReportPreview(caseId: String) = push(SessionScreen.ReportPreview(caseId))

    fun openExportResult(caseId: String) = push(SessionScreen.ExportResult(caseId))
 
    fun openAiModel() = push(SessionScreen.AiModel)

    fun openObservation() = push(SessionScreen.Observation)

    fun openVisibleCapture() = push(SessionScreen.VisibleCapture)

    /** Swaps the screen on top, for a step that should not be returned to (the analysis result becomes the timeline). */
    fun replaceTopWithTimeline(caseId: String) {
        if (stack.size > 1) stack.removeLast()
        push(SessionScreen.Timeline(caseId))
    }

    /** Pops one level; the case list is the floor. */
    fun back() {
        if (stack.size > 1) stack.removeLast()
        publish()
    }
}
