package org.sakshi.app

import kotlin.test.Test
import kotlin.test.assertEquals
import org.sakshi.core.vault.SenderSelector

class SessionNavigatorTest {
    private val claim = SenderSelector("synthetic-sender", null, null)

    @Test
    fun backPopsOneLevelAtATime() {
        val navigator = SessionNavigator()
        navigator.openCase("synthetic-case")
        navigator.openTimeline("synthetic-case")
        navigator.openEventReview("synthetic-case", "synthetic-event")
        navigator.openWhoIsWho("synthetic-case", claim)
        assertEquals(SessionScreen.WhoIsWho("synthetic-case", claim), navigator.current.value)
        navigator.back()
        assertEquals(SessionScreen.EventReview("synthetic-case", "synthetic-event"), navigator.current.value)
        navigator.back()
        assertEquals(SessionScreen.Timeline("synthetic-case"), navigator.current.value)
        navigator.back()
        assertEquals(SessionScreen.CaseDetail("synthetic-case"), navigator.current.value)
        navigator.back()
        assertEquals(SessionScreen.CaseList, navigator.current.value)
        navigator.back()
        assertEquals(SessionScreen.CaseList, navigator.current.value)
    }

    @Test
    fun theAnalysisResultReplacesItselfWithTheTimeline() {
        val navigator = SessionNavigator()
        navigator.openCase("synthetic-case")
        navigator.openAnalysis("synthetic-case", "synthetic-evidence")
        assertEquals(SessionScreen.Analysis("synthetic-case", "synthetic-evidence"), navigator.current.value)
        navigator.replaceTopWithTimeline("synthetic-case")
        assertEquals(SessionScreen.Timeline("synthetic-case"), navigator.current.value)
        navigator.back()
        assertEquals(SessionScreen.CaseDetail("synthetic-case"), navigator.current.value)
    }

    @Test
    fun patternsOpenFromTheCaseAndBackReturnsThere() {
        val navigator = SessionNavigator()
        navigator.openCase("synthetic-case")
        navigator.openPatterns("synthetic-case")
        assertEquals(SessionScreen.Patterns("synthetic-case"), navigator.current.value)
        navigator.back()
        assertEquals(SessionScreen.CaseDetail("synthetic-case"), navigator.current.value)
    }

    @Test
    fun openingACaseStartsAFreshStack() {
        val navigator = SessionNavigator()
        navigator.openCase("synthetic-case")
        navigator.openTimeline("synthetic-case")
        navigator.openCase("synthetic-other")
        navigator.back()
        assertEquals(SessionScreen.CaseList, navigator.current.value)
    }

    @Test
    fun theReportFlowStepsBackThroughPreviewAndSelectionToTheCase() {
        val navigator = SessionNavigator()
        navigator.openCase("synthetic-case")
        navigator.openReport("synthetic-case")
        navigator.openReportPreview("synthetic-case")
        navigator.openExportResult("synthetic-case")
        assertEquals(SessionScreen.ExportResult("synthetic-case"), navigator.current.value)
        navigator.openCase("synthetic-case")
        assertEquals(SessionScreen.CaseDetail("synthetic-case"), navigator.current.value)
        navigator.openReport("synthetic-case")
        navigator.openReportPreview("synthetic-case")
        navigator.back()
        assertEquals(SessionScreen.ReportSelection("synthetic-case"), navigator.current.value)
        navigator.back()
        assertEquals(SessionScreen.CaseDetail("synthetic-case"), navigator.current.value)
    }
}
