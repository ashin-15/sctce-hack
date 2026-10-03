package org.sakshi.app.deletion

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.sakshi.app.R
import org.sakshi.app.support.ForbiddenWords

@RunWith(RobolectricTestRunner::class)
class DeleteEverythingTextTest {
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    private val shown = listOf(
        R.string.delete_all_menu, R.string.delete_all_title, R.string.delete_all_removed_heading, R.string.delete_all_removed_cases,
        R.string.delete_all_removed_files, R.string.delete_all_removed_text, R.string.delete_all_removed_answers,
        R.string.delete_all_removed_activity, R.string.delete_all_removed_keys, R.string.delete_all_not_affected,
        R.string.delete_all_permanent, R.string.delete_all_limit, R.string.delete_all_more_label, R.string.delete_all_confirm_word,
        R.string.delete_all_field_label, R.string.delete_all_button, R.string.delete_all_running_title,
        R.string.delete_all_running_body, R.string.delete_all_incomplete_title, R.string.delete_all_incomplete_body,
        R.string.delete_all_failed_title, R.string.delete_all_failed_body, R.string.delete_all_retry, R.string.delete_all_done_title,
        R.string.delete_all_done_body, R.string.delete_all_done_continue, R.string.cases_menu_options,
    )

    @Test
    fun noStringHasADashForbiddenWordOrJargon() {
        val jargon = listOf("vault", "database", "key store", "keystore", "wipe", "purge")
        shown.forEach { id ->
            val text = resources.getString(id)
            assertFalse(ForbiddenWords.hasDash(text), text)
            assertEquals(emptyList(), ForbiddenWords.found(text), text)
            assertFalse(jargon.any { it in text.lowercase() }, text)
            assertFalse("..." in text, text)
        }
        assertFalse(ForbiddenWords.hasDash(resources.getString(R.string.delete_all_type_prompt, "DELETE")))
    }

    @Test
    fun theScreenSaysWhatIsRemovedWhatIsNotAndTheHonestLimit() {
        assertEquals("Physical erasure from the phone's storage cannot be promised.", resources.getString(R.string.delete_all_limit))
        assertEquals("This cannot be undone.", resources.getString(R.string.delete_all_permanent))
        assertTrue("not affected" in resources.getString(R.string.delete_all_not_affected))
        assertEquals("Every case", resources.getString(R.string.delete_all_removed_cases))
        assertEquals("The keys that protect them", resources.getString(R.string.delete_all_removed_keys))
    }

    @Test
    fun theOutcomesUseTheAgreedWords() {
        assertEquals("Everything Sakshi had saved on this phone was deleted.", resources.getString(R.string.delete_all_done_body))
        assertEquals(
            "Most of what Sakshi had saved was deleted, but some files could not be removed. Try again.",
            resources.getString(R.string.delete_all_incomplete_body),
        )
        assertEquals("Try again", resources.getString(R.string.delete_all_retry))
    }

    @Test
    fun theButtonOnlyWorksForTheShownWordIgnoringCaseAndEdgeSpaces() {
        val word = resources.getString(R.string.delete_all_confirm_word)
        listOf(word, word.lowercase(), "  $word ", "Delete").forEach { assertTrue(confirmWordTyped(it, word), it) }
        listOf("", " ", "DELET", "DELETE it", "DEL ETE", "ELETE").forEach { assertFalse(confirmWordTyped(it, word), it) }
    }
}
