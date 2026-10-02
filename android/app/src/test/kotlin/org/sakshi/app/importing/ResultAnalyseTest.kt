package org.sakshi.app.importing

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.sakshi.acquisition.importer.AnalysisState
import org.sakshi.acquisition.importer.ItemOutcome

class ResultAnalyseTest {
    private fun saved(declared: String? = null, detected: String? = null, state: AnalysisState = AnalysisState.READY_FOR_TEXT_ANALYSIS) =
        ItemOutcome.Saved(0, "synthetic-id", "0".repeat(64), 10, declared, detected, state, emptyList())

    @Test
    fun sharedAndPastedTextOfferAnalyseNow() {
        assertTrue(isAnalysableText(saved(), ItemLabel.SharedText))
        assertTrue(isAnalysableText(saved(), ItemLabel.PastedText))
    }

    @Test
    fun aFileOffersItOnlyWhenDeclaredAsTextAndNotDetectedAsAnotherType() {
        assertTrue(isAnalysableText(saved(declared = "text/plain"), ItemLabel.File("synthetic.txt")))
        assertFalse(isAnalysableText(saved(declared = "text/plain", detected = "application/pdf"), ItemLabel.File("synthetic.txt")))
        assertFalse(isAnalysableText(saved(declared = "image/png"), ItemLabel.File("synthetic.png")))
        assertFalse(isAnalysableText(saved(), ItemLabel.File(null)))
    }

    @Test
    fun keptOnlyItemsAndUnknownLabelsDoNot() {
        assertFalse(isAnalysableText(saved(state = AnalysisState.PRESERVED_NOT_ANALYSED), ItemLabel.SharedText))
        assertFalse(isAnalysableText(saved(), ItemLabel.Unknown))
    }
}
