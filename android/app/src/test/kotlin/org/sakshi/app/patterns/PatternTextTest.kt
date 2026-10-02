package org.sakshi.app.patterns

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.sakshi.app.support.ForbiddenWords
import org.sakshi.app.support.VaultTestBase
import org.sakshi.app.ui.resolve
import org.sakshi.core.temporal.AssessmentStatus
import org.sakshi.core.temporal.PatternType

class PatternTextTest : VaultTestBase() {
    @Test
    fun everyPatternTypeHasItsNeutralTitle() {
        val expected = mapOf(
            PatternType.REPEATED_CONTACT to "Repeated contact",
            PatternType.RECURRENCE_AFTER_BOUNDARY to "Contact after a boundary",
            PatternType.WORDING_TRANSITION to "Change in wording",
            PatternType.DENSITY_CHANGE to "Change in how often",
        )
        assertEquals(PatternType.entries.toSet(), expected.keys)
        expected.forEach { (type, title) -> assertEquals(title, patternTitle(type).resolve(context.resources)) }
    }

    @Test
    fun everyStatusHasItsSentence() {
        val expected = mapOf(
            AssessmentStatus.SUPPORTED_DESCRIPTION to "Description supported by the selected records",
            AssessmentStatus.CANDIDATE to "Possible, needs review",
            AssessmentStatus.INSUFFICIENT_CONTEXT to "Not enough context to say",
            AssessmentStatus.NOT_OBSERVED to "Not observed in the selected records. This does not mean nothing happened.",
        )
        assertEquals(AssessmentStatus.entries.toSet(), expected.keys)
        expected.forEach { (status, sentence) -> assertEquals(sentence, patternStatusText(status).resolve(context.resources)) }
    }

    @Test
    fun nothingMappedUsesAForbiddenWordOrADash() {
        val texts = PatternType.entries.map { patternTitle(it) } + AssessmentStatus.entries.map { patternStatusText(it) } +
            listOf(interpretationText("This may be a change in wording."), supportCountText(1), supportCountText(7))
        texts.map { it.resolve(context.resources) }.forEach { text ->
            assertEquals(emptyList(), ForbiddenWords.found(text), text)
            assertTrue(!ForbiddenWords.hasDash(text), text)
        }
    }

    @Test
    fun theSupportCountIsPluralAware() {
        assertEquals("Based on 1 item", supportCountText(1).resolve(context.resources))
        assertEquals("Based on 7 items", supportCountText(7).resolve(context.resources))
    }

    @Test
    fun theInterpretationIsIntroducedByOneWayToReadThis() {
        assertEquals("One way to read this: This may be a change in wording.", interpretationText("This may be a change in wording.").resolve(context.resources))
    }
}
