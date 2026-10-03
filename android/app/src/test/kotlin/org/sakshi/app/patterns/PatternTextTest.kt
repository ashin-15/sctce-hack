package org.sakshi.app.patterns

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.sakshi.app.R
import org.sakshi.app.support.ForbiddenWords
import org.sakshi.app.support.VaultTestBase
import org.sakshi.app.ui.resolve
import org.sakshi.core.temporal.AssessmentStatus
import org.sakshi.core.temporal.PatternType
import org.sakshi.core.vault.PatternReview
import org.sakshi.core.vault.ReviewReason

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
            AssessmentStatus.SUPPORTED_DESCRIPTION to "Supported by the records",
            AssessmentStatus.CANDIDATE to "Possible, needs review",
            AssessmentStatus.INSUFFICIENT_CONTEXT to "Not enough context",
            AssessmentStatus.NOT_OBSERVED to "Not observed in the records",
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

    @Test
    fun everyReviewStateHasPlainWordsAndTheReasonIsAddedToARejection() {
        val resources = context.resources
        assertEquals("Not reviewed yet", patternReviewText(PatternReview.NOT_REVIEWED, null).resolve(resources))
        assertEquals("You said this matches", patternReviewText(PatternReview.ACCEPTED, null).resolve(resources))
        assertEquals("You said this does not match", patternReviewText(PatternReview.REJECTED, null).resolve(resources))
        assertEquals("You marked this as not sure", patternReviewText(PatternReview.MARKED_UNKNOWN, null).resolve(resources))
        val reasons = mapOf(
            ReviewReason.WRONG_SENDER_OR_QUOTE to "Wrong sender or quote",
            ReviewReason.EXTRACTION_ERROR to "The text was read wrongly",
            ReviewReason.DUPLICATE to "Duplicate",
            ReviewReason.INSUFFICIENT_CONTEXT to "Not enough context",
        )
        assertEquals(reasons.keys.toList(), PATTERN_REJECT_REASONS)
        reasons.forEach { (code, words) ->
            assertEquals("You said this does not match: $words", patternReviewText(PatternReview.REJECTED, code).resolve(resources))
        }
    }

    @Test
    fun theReviewWordsUseNoForbiddenWordAndNoDash() {
        val resources = context.resources
        val texts = PatternReview.entries.map { patternReviewText(it, null).resolve(resources) } +
            PATTERN_REJECT_REASONS.map { patternReviewText(PatternReview.REJECTED, it).resolve(resources) } +
            PatternNotice.entries.map { patternNoticeText(it).resolve(resources) } +
            listOf(
                R.string.patterns_accept, R.string.patterns_reject, R.string.patterns_unsure, R.string.patterns_withdraw,
                R.string.patterns_review_explain, R.string.patterns_review_preview_only, R.string.patterns_refreshing,
                R.string.patterns_reject_title, R.string.patterns_reject_body, R.string.patterns_reject_no_reason,
                R.string.patterns_not_observed_limit, R.string.patterns_more, R.string.patterns_footer,
            ).map { resources.getString(it) }
        texts.forEach { text ->
            assertEquals(emptyList(), ForbiddenWords.found(text), text)
            assertTrue(!ForbiddenWords.hasDash(text), text)
            val lower = text.lowercase()
            listOf("confirm", "verif", "prove", "valid").forEach { assertTrue(it !in lower, "$it in $text") }
        }
    }
}
