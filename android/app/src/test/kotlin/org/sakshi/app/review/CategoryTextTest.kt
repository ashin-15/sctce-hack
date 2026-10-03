package org.sakshi.app.review

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.sakshi.app.support.ForbiddenWords
import org.sakshi.app.support.VaultTestBase
import org.sakshi.app.ui.UiText
import org.sakshi.app.ui.resolve
import org.sakshi.core.model.CategoryBasis
import org.sakshi.core.model.CategoryLabel
import org.sakshi.core.model.CategoryReviewStatus
import org.sakshi.core.model.Direction

class CategoryTextTest : VaultTestBase() {
    private fun words(texts: List<UiText>): List<String> = texts.map { it.resolve(context.resources) }

    private fun assertClean(texts: List<String>) {
        assertTrue(texts.all { it.isNotBlank() })
        texts.forEach { text ->
            assertEquals(emptyList(), ForbiddenWords.found(text), text)
            assertTrue(!ForbiddenWords.hasDash(text), text)
        }
    }

    @Test
    fun everyLabelHasADistinctNeutralPhrase() {
        val all = words(CategoryLabel.entries.map { categoryLabelText(it) })
        assertEquals(CategoryLabel.entries.size, all.size)
        assertEquals(all.size, all.toSet().size)
        assertClean(all)
    }

    @Test
    fun theLabelsMatchTheBrief() {
        val expected = mapOf(
            CategoryLabel.VERBAL_ABUSE to "Insulting or degrading wording",
            CategoryLabel.EXPLICIT_THREAT to "Wording that states harm",
            CategoryLabel.IMPLIED_THREAT to "Wording that hints at harm",
            CategoryLabel.INTIMIDATION to "Intimidating wording",
            CategoryLabel.CONTROLLING_REQUEST to "Controlling demand",
            CategoryLabel.SEXUAL_PRESSURE to "Sexual pressure",
            CategoryLabel.PRIVACY_EXPOSURE_INDICATOR to "Mentions exposing private information",
            CategoryLabel.CONTACT_REQUEST to "Asks for contact",
            CategoryLabel.ORDINARY to "Ordinary",
            CategoryLabel.UNKNOWN to "Not sure",
        )
        assertEquals(CategoryLabel.entries.toSet(), expected.keys)
        expected.forEach { (label, phrase) -> assertEquals(phrase, categoryLabelText(label).resolve(context.resources)) }
    }

    @Test
    fun everyBasisAndReviewStatusHasWords() {
        assertClean(words(CategoryBasis.entries.map { categoryBasisText(it, listOf("idiot")) }))
        assertClean(words(CategoryReviewStatus.entries.map { reviewStatusText(it) }))
        assertClean(words(Direction.entries.map { directionText(it) }))
        assertClean(words(DISAGREE_REASONS.map { reasonText(it) }) + words(listOf(reasonText("synthetic-unknown-code"))))
    }

    @Test
    fun theRuleSentenceNamesTheWordsAndTheLimitsAreOneTapAway() {
        val sentence = categoryBasisText(CategoryBasis.RULE_SUGGESTION, listOf("idiot", "worthless")).resolve(context.resources)
        assertEquals("Words matched a list: 'idiot', 'worthless'", sentence)
        assertEquals("Words matched a list.", categoryBasisText(CategoryBasis.RULE_SUGGESTION, emptyList()).resolve(context.resources))
        assertEquals(
            "The word list has not been reviewed by a native speaker. It cannot read context, jokes or quotes.",
            context.getString(org.sakshi.app.R.string.review_list_limits),
        )
    }

    @Test
    fun theModelSuggestionsSayTheyCanBeWrong() {
        listOf(CategoryBasis.CLASSIFIER_SUGGESTION, CategoryBasis.LLM_SUGGESTION).forEach {
            assertTrue(categoryBasisText(it, emptyList()).resolve(context.resources).contains("can be wrong"))
        }
        assertTrue(categoryBasisText(CategoryBasis.CLASSIFIER_SUGGESTION, emptyList()).resolve(context.resources).contains("Uncalibrated"))
    }

    @Test
    fun theOwnTagLabelsOfferEveryLabel() {
        assertEquals(CategoryLabel.entries.toList(), OWN_TAG_LABELS)
    }
}
