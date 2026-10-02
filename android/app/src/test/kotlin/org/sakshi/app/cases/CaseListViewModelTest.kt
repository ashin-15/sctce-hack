package org.sakshi.app.cases

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.sakshi.core.crypto.SoftwareKeyWrapper
import org.sakshi.core.vault.CaseRepository
import org.sakshi.core.vault.Vault

@RunWith(RobolectricTestRunner::class)
class CaseListViewModelTest {
    private lateinit var vault: Vault
    private lateinit var model: CaseListViewModel
    private val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
    private var nextId = 0

    @BeforeTest
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        vault = Vault.openForTests(
            context,
            SoftwareKeyWrapper(ByteArray(32) { it.toByte() }),
            { Instant.parse("2026-10-02T10:00:00Z") },
            { "id-${nextId++}" },
        )
        model = CaseListViewModel(vault.cases, scope)
    }

    @AfterTest
    fun tearDown() {
        vault.close()
        scope.cancel()
    }

    private fun awaitState(predicate: (CaseListUiState) -> Boolean): CaseListUiState =
        runBlocking { withTimeout(AWAIT_MILLIS) { model.uiState.first(predicate) } }

    @Test
    fun createRenameArchiveUnarchiveAndDeleteShowInState() {
        model.create("  synthetic one  ")
        val created = awaitState { it.active.size == 1 }
        assertEquals("synthetic one", created.active.single().title)
        val id = created.active.single().id

        model.rename(id, "synthetic renamed")
        assertEquals("synthetic renamed", awaitState { it.active.singleOrNull()?.title == "synthetic renamed" }.active.single().title)

        model.archive(id)
        val archived = awaitState { it.active.isEmpty() && it.archived.size == 1 }
        assertEquals(id, archived.archived.single().id)

        model.unarchive(id)
        awaitState { it.active.size == 1 && it.archived.isEmpty() }

        model.delete(id)
        awaitState { it.active.isEmpty() && it.archived.isEmpty() }
    }

    @Test
    fun blankTitleIsReportedAndNothingIsCreated() {
        model.create("   ")
        assertEquals(CaseMessage.TITLE_BLANK, awaitState { it.message != null }.message)
        model.messageShown()
        assertNull(awaitState { it.message == null }.message)
        assertTrue(model.uiState.value.active.isEmpty())
        assertTrue(runBlocking { vault.cases.observe().first() }.isEmpty())
    }

    @Test
    fun overLongTitleIsReportedAndNothingIsCreated() {
        model.create("synthetic ".repeat(CaseRepository.MAX_TITLE_LENGTH))
        assertEquals(CaseMessage.TITLE_TOO_LONG, awaitState { it.message != null }.message)
        assertTrue(runBlocking { vault.cases.observe().first() }.isEmpty())
    }

    @Test
    fun renameWithBlankTitleKeepsTheOldTitle() {
        model.create("synthetic keep")
        val id = awaitState { it.active.size == 1 }.active.single().id
        model.rename(id, "")
        assertEquals(CaseMessage.TITLE_BLANK, awaitState { it.message != null }.message)
        assertEquals("synthetic keep", runBlocking { vault.cases.observe().first() }.single().title)
    }

    @Test
    fun unknownCaseBecomesSaveFailedNotACrash() {
        model.archive("missing")
        assertEquals(CaseMessage.SAVE_FAILED, awaitState { it.message != null }.message)
    }

    private companion object {
        const val AWAIT_MILLIS = 10_000L
    }
}
