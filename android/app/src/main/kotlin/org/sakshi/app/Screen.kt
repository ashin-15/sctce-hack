package org.sakshi.app

import org.sakshi.app.importing.ImportUiState
import org.sakshi.app.session.DeletionState
import org.sakshi.app.session.SessionState
import org.sakshi.core.vault.SenderSelector
import org.sakshi.core.vault.Vault

/** What the activity shows. Derived from state, never stored. */
sealed interface Screen {
    data object Onboarding : Screen

    data class Lock(val state: SessionState) : Screen

    /** "Delete everything" is running or has an outcome to show. It comes before every other screen. */
    data class Deletion(val state: DeletionState) : Screen

    /** The unlocked session; [SessionScreen] says which part of it. */
    class Session(val vault: Vault) : Screen
}

fun screenFor(acknowledged: Boolean, session: SessionState, deletion: DeletionState = DeletionState.Idle): Screen = when {
    deletion != DeletionState.Idle -> Screen.Deletion(deletion)
    !acknowledged -> Screen.Onboarding
    session is SessionState.Unlocked -> Screen.Session(session.vault)
    else -> Screen.Lock(session)
}

/** Screens inside the unlocked session. Case ids are kept in memory only and vanish when the session locks. */
sealed interface SessionScreen {
    data object CaseList : SessionScreen

    data class CaseDetail(val caseId: String) : SessionScreen

    /** Preview, progress and result of an import; which one is decided by [ImportUiState]. */
    data object Import : SessionScreen

    data class ManualNote(val caseId: String) : SessionScreen

    /** Reads one saved text item and, for a chat export, asks the three questions it needs. */
    data class Analysis(val caseId: String, val evidenceId: String) : SessionScreen

    data class Timeline(val caseId: String) : SessionScreen

    data class EventReview(val caseId: String, val eventId: String) : SessionScreen

    /** [focusClaim] is the sender claim the person was looking at, or null to show them all. */
    data class WhoIsWho(val caseId: String, val focusClaim: SenderSelector? = null) : SessionScreen

    data class Patterns(val caseId: String) : SessionScreen

    data class Search(val caseId: String) : SessionScreen

    /** Explains what "Delete everything" removes and asks for the typed word. Once confirmed the whole app takes over. */
    data object DeleteEverything : SessionScreen

    /** Chooses what goes into a report. What was chosen is held in the case's report view model, not in the route. */
    data class ReportSelection(val caseId: String) : SessionScreen

    data class ReportPreview(val caseId: String) : SessionScreen

    /** The finished export file, with the way to share it. */
    data class ExportResult(val caseId: String) : SessionScreen

    /** Download, import and manage on-device AI models. */
    data object AiModel : SessionScreen
}

/** An import in any state other than idle takes over the screen; leaving it returns to where it started. */
fun sessionScreenFor(current: SessionScreen, importState: ImportUiState): SessionScreen =
    if (importState is ImportUiState.Idle) current else SessionScreen.Import
