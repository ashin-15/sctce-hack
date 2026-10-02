package org.sakshi.app.ui

import androidx.test.core.app.ApplicationProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.sakshi.app.ui.components.epistemicLabelRes
import org.sakshi.app.ui.components.epistemicTreatment
import org.sakshi.core.model.EpistemicStatus

@RunWith(RobolectricTestRunner::class)
class EpistemicBlockContractTest {
    private val context: android.content.Context = ApplicationProvider.getApplicationContext()

    @Test
    fun fiveStatusesHaveFiveDistinctLabels() {
        val labels = EpistemicStatus.entries.map { context.getString(epistemicLabelRes(it)) }
        assertEquals(5, labels.distinct().size)
        assertEquals(listOf("Observed", "Your statement", "Suggestion", "Pattern", "Not known"), labels)
    }

    @Test
    fun fiveStatusesHaveFiveDistinctTreatments() {
        assertEquals(5, EpistemicStatus.entries.map { epistemicTreatment(it) }.distinct().size)
    }

    @Test
    fun theTreatmentsStayDistinctWithoutColour() {
        assertEquals(5, EpistemicStatus.entries.map { epistemicTreatment(it).shape }.distinct().size)
    }
}
