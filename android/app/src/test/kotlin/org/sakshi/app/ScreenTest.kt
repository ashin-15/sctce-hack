package org.sakshi.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import org.sakshi.acquisition.importer.ImportMechanism
import org.sakshi.acquisition.importer.ImportReport
import org.sakshi.acquisition.importer.PendingBatch
import org.sakshi.app.importing.CaseChoice
import org.sakshi.app.importing.ImportUiState
import org.sakshi.app.session.SessionState
import org.sakshi.app.support.VaultTestBase

class ScreenTest : VaultTestBase() {
    private val batch = PendingBatch(ImportMechanism.SHARE_SEND, emptyList(), referrerClaim = null)

    @Test
    fun onboardingComesBeforeAnythingElse() {
        assertEquals(Screen.Onboarding, screenFor(acknowledged = false, SessionState.Locked))
        assertEquals(Screen.Onboarding, screenFor(acknowledged = false, SessionState.NoDeviceLock))
        assertEquals(Screen.Onboarding, screenFor(acknowledged = false, SessionState.Unlocked(vault)))
    }

    @Test
    fun acknowledgedUsersSeeTheLockScreenForEveryLockedState() {
        listOf(SessionState.Locked, SessionState.Unlocking, SessionState.NoDeviceLock, SessionState.KeyInvalidated).forEach {
            assertEquals(Screen.Lock(it), screenFor(acknowledged = true, it))
        }
        assertIs<Screen.Lock>(screenFor(acknowledged = true, SessionState.Locked))
    }

    @Test
    fun anUnlockedSessionShowsTheSessionScreens() {
        assertSame(vault, assertIs<Screen.Session>(screenFor(acknowledged = true, SessionState.Unlocked(vault))).vault)
    }

    @Test
    fun anIdleImportLeavesTheCurrentSessionScreen() {
        listOf(SessionScreen.CaseList, SessionScreen.CaseDetail("synthetic-id"), SessionScreen.ManualNote("synthetic-id")).forEach {
            assertEquals(it, sessionScreenFor(it, ImportUiState.Idle))
        }
    }

    @Test
    fun anyActiveImportStateTakesOverTheScreen() {
        val report = ImportReport(emptyList())
        val states = listOf(
            ImportUiState.Previewing(batch, emptySet(), fixedCaseId = null, choice = CaseChoice.None),
            ImportUiState.Saving(0, 1),
            ImportUiState.Finished("synthetic-id", report, emptyMap()),
            ImportUiState.Cancelled("synthetic-id", report, emptyMap()),
        )
        states.forEach { assertEquals(SessionScreen.Import, sessionScreenFor(SessionScreen.CaseDetail("synthetic-id"), it)) }
    }

    @Test
    fun theNavigatorMovesBetweenCaseScreens() {
        val navigator = SessionNavigator()
        assertEquals(SessionScreen.CaseList, navigator.current.value)
        navigator.openCase("synthetic-id")
        assertEquals(SessionScreen.CaseDetail("synthetic-id"), navigator.current.value)
        navigator.openNote("synthetic-id")
        assertEquals(SessionScreen.ManualNote("synthetic-id"), navigator.current.value)
        navigator.showCaseList()
        assertEquals(SessionScreen.CaseList, navigator.current.value)
    }
}
