package org.sakshi.processing.analysis

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SchemaValidatorsConfig
import com.networknt.schema.SpecVersion
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.sakshi.core.crypto.SoftwareKeyWrapper
import org.sakshi.core.model.CaseId
import org.sakshi.core.model.Event
import org.sakshi.core.model.EventSchemaAdapter
import org.sakshi.core.vault.AccessClass
import org.sakshi.core.vault.AcquisitionKind
import org.sakshi.core.vault.ImportRequest
import org.sakshi.core.vault.Vault

/** All data in these tests is synthetic. */
internal val FIXED_NOW: Instant = Instant.parse("2026-10-02T10:00:00Z")

@RunWith(RobolectricTestRunner::class)
abstract class AnalysisTestBase {
    protected val context: Context = ApplicationProvider.getApplicationContext()
    protected val clock: () -> Instant = { FIXED_NOW }
    private var counter = 0
    protected val ids: () -> String = { "synthetic-${++counter}" }

    protected lateinit var vault: Vault
    protected lateinit var caseId: String
    protected lateinit var analysis: TextAnalysis

    @Before
    fun openVault() {
        vault = Vault.openForTests(context, SoftwareKeyWrapper(ByteArray(32) { it.toByte() }), clock, ids)
        caseId = runBlocking { vault.cases.create("synthetic-case").id }
        analysis = TextAnalysis(vault, RulesEngineFactory.default(), clock, ids)
    }

    @After
    fun closeVault() {
        vault.close()
        File(context.noBackupFilesDir, "vault").deleteRecursively()
    }

    protected fun importBytes(
        bytes: ByteArray,
        declaredMime: String? = "text/plain",
        kind: String = AcquisitionKind.SHARED_TEXT,
        maxBytes: Long = 20_000_000L,
    ): String = runBlocking {
        val request = ImportRequest(
            caseId, kind, AccessClass.USER_MEDIATED, "synthetic-test", declaredMime,
            "synthetic-origin", "synthetic-name.txt", null, maxBytes,
        )
        vault.evidence.import(request, ByteArrayInputStream(bytes)).id
    }

    protected fun importText(text: String): String = importBytes(text.toByteArray(Charsets.UTF_8))

    protected fun events(): List<Event> = runBlocking {
        vault.events.loadLatest(CaseId(caseId), Instant.ofEpochMilli(Long.MAX_VALUE))
    }

    protected fun assertSchemaValid(events: List<Event>) {
        val problems = events.flatMap { event ->
            schema.validate(mapper.readTree(EventSchemaAdapter.toJson(event))).map { it.message }
        }
        assertEquals(emptyList(), problems.take(5))
        assertTrue(events.isNotEmpty())
    }

    private companion object {
        val mapper = ObjectMapper()
        val schema: JsonSchema by lazy {
            val path = checkNotNull(System.getProperty("sakshi.eventSchema")) { "sakshi.eventSchema not set" }
            val config = SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build()
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema(mapper.readTree(File(path).readText()), config)
        }
    }
}
