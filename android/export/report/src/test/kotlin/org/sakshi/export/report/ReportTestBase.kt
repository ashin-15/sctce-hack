package org.sakshi.export.report

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.time.Instant
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.sakshi.core.crypto.SoftwareKeyWrapper
import org.sakshi.core.model.AssociationReview
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.Event
import org.sakshi.core.model.EventId
import org.sakshi.core.model.IdentityBasis
import org.sakshi.core.model.Timestamp
import org.sakshi.core.temporal.EvidenceView
import org.sakshi.core.temporal.TemporalInput
import org.sakshi.core.vault.BatchSaveResult
import org.sakshi.core.vault.Vault
import org.sakshi.export.bundle.GeneratorInfo
import java.time.ZoneOffset

val FIXED_NOW: Instant = Instant.parse("2026-10-04T00:00:00Z")
val GENERATOR: GeneratorInfo = GeneratorInfo("synthetic-app-1", listOf("synthetic-rules-1"), listOf("synthetic-model-1"))
val ZONE: ZoneOffset = ZoneOffset.ofHoursMinutes(5, 30)

/** Quotes every reference as a fixed synthetic sentence unless a test overrides it. */
class FakeQuotes(private val override: Map<String, String> = emptyMap()) : QuoteSource {
    override suspend fun quote(event: Event, reference: org.sakshi.core.model.EvidenceReference): String =
        override[event.eventId.value] ?: "synthetic quote of ${event.eventId.value}"
}

/** A vault over an in-memory database with a fixed clock and counter ids. */
@RunWith(RobolectricTestRunner::class)
abstract class ReportTestBase {
    protected val context: Context = ApplicationProvider.getApplicationContext()
    private val queuedIds = ArrayDeque<String>()
    private var counter = 0
    protected val ids: () -> String = { queuedIds.removeFirstOrNull() ?: "synthetic-id-${++counter}" }
    protected lateinit var vault: Vault

    @Before
    fun openVault() {
        vault = Vault.openForTests(context, SoftwareKeyWrapper(ByteArray(32) { it.toByte() }), { FIXED_NOW }, ids)
    }

    @After
    fun closeVault() {
        vault.close()
        File(context.noBackupFilesDir, "vault").deleteRecursively()
        File(context.cacheDir, "exports").deleteRecursively()
    }

    protected fun builder(quotes: QuoteSource = FakeQuotes()): ReportBuilder =
        ReportBuilder(vault, quotes, { FIXED_NOW }, GENERATOR)

    /** Creates the input's case under its own id, registers its actors and stores [events]. */
    protected fun store(input: TemporalInput, events: List<Event> = input.events, title: String = "synthetic case title") =
        runBlocking {
            queuedIds.addLast(input.caseId.value)
            vault.cases.create(title)
            input.events.flatMap { listOfNotNull(it.sender.actorId, it.boundary.actorId) }.toSet().forEach {
                vault.actors.create(input.caseId, "synthetic-actor-label", IdentityBasis.USER_ASSERTED, AssociationReview.CONFIRMED, it)
            }
            assertEquals(BatchSaveResult.Saved(events.size), vault.events.saveAll(events))
        }

    protected fun gap(caseId: CaseId, start: String?, end: String?) = runBlocking {
        vault.events.addCoverageGap(caseId, start?.let(::Timestamp), end?.let(::Timestamp), "listener_disconnected")
    }

    protected fun select(input: TemporalInput, vararg ids: String, originals: Set<String> = emptySet()) = ReportSelection(
        caseId = input.caseId,
        eventIds = ids.map { EventId(it) }.toSet(),
        includeOriginalsFor = originals,
        view = EvidenceView.CONFIRMED_ONLY,
        zone = ZONE,
    )

    protected fun selectAll(input: TemporalInput) = select(input, *input.events.map { it.eventId.value }.toTypedArray())

    protected fun built(result: ReportBuildResult): ReportBuildResult.Built =
        result as? ReportBuildResult.Built ?: error("Expected a built report but got $result")
}
