package org.sakshi.export.report

import java.io.ByteArrayInputStream
import java.io.OutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.sakshi.core.integrity.CanonicalJson
import org.sakshi.core.integrity.Sha256
import org.sakshi.core.model.ArtifactId
import org.sakshi.core.model.Event
import org.sakshi.core.temporal.TemporalInput
import org.sakshi.core.temporal.fixtures.SyntheticTimelines
import org.sakshi.core.vault.AccessClass
import org.sakshi.core.vault.AcquisitionKind
import org.sakshi.core.vault.AuditActions
import org.sakshi.core.vault.BatchSaveResult
import org.sakshi.core.vault.ImportRequest
import org.sakshi.export.bundle.SoftwareP256Signer

class ExportAuditTest : ReportTestBase() {
    private val signer = SoftwareP256Signer.generate()

    private fun service(renderer: ReportRenderer = FakeRenderer()) =
        ExportService(context, vault, builder(), renderer, signer, { FIXED_NOW }, ids)

    private suspend fun exportRows(): List<String> = vault.audit.records().filter { it.action == AuditActions.EXPORT_CREATED }.map { it.subjectId }

    private fun withEvidence(input: TemporalInput, bytes: ByteArray): Pair<List<Event>, String> = runBlocking {
        store(input, emptyList())
        val imported = vault.evidence.import(
            ImportRequest(
                input.caseId.value, AcquisitionKind.SHARED_STREAM, AccessClass.USER_MEDIATED, "synthetic-test", null, null, null, null, 1_000_000L,
            ),
            ByteArrayInputStream(bytes),
        )
        val events = input.events.map { event ->
            if (event.eventId.value != "synthetic-a1") event else event.copy(
                evidenceReferences = event.evidenceReferences.map { it.copy(artifactId = ArtifactId(imported.id), sha256 = imported.sha256) },
            )
        }
        assertEquals(BatchSaveResult.Saved(events.size), vault.events.saveAll(events))
        events to imported.id
    }

    @Test
    fun aSuccessfulExportWritesOneAuditRowWithIdsAndCountsOnly() = runBlocking<Unit> {
        val input = SyntheticTimelines.a()
        store(input)
        val before = vault.audit.count()

        val result = service().export(selectAll(input)) as ExportResult.Exported

        val rows = vault.audit.records().drop(before)
        assertEquals(listOf(AuditActions.EXPORT_CREATED), rows.map { it.action })
        assertEquals(result.snapshotId, rows.single().subjectId)
        assertEquals("export", rows.single().subjectType)
        val details = JsonObject(
            mapOf(
                "snapshot_id" to JsonPrimitive(result.snapshotId),
                "case_id" to JsonPrimitive(input.caseId.value),
                "event_count" to JsonPrimitive(7),
                "original_count" to JsonPrimitive(0),
                "signer_key_id" to JsonPrimitive(result.signerKeyId),
            ),
        )
        val payload = JsonObject(
            mapOf(
                "action" to JsonPrimitive(AuditActions.EXPORT_CREATED),
                "at" to JsonPrimitive(FIXED_NOW.toString()),
                "details" to details,
                "subject_id" to JsonPrimitive(result.snapshotId),
                "subject_type" to JsonPrimitive("export"),
            ),
        )
        assertEquals(Sha256.hex(Sha256.digest(CanonicalJson.encode(payload))), rows.single().payloadSha256Hex)
    }

    @Test
    fun refusedAndFailedExportsWriteNoAuditRow() = runBlocking<Unit> {
        val input = SyntheticTimelines.a()
        store(input)
        val before = vault.audit.count()
        val broken = object : ReportRenderer {
            override fun render(model: ReportModel, output: OutputStream): RenderSummary = throw IllegalArgumentException("synthetic")
        }

        assertEquals(ExportResult.Refused(RefusalReason.EMPTY_SELECTION), service().export(select(input)))
        assertEquals(ExportResult.Failed(ExportFailure.RENDER_FAILED), service(broken).export(selectAll(input)))

        assertEquals(before, vault.audit.count())
        assertEquals(emptyList(), exportRows())
    }

    @Test
    fun anOriginalThatNoLongerMatchesItsHashIsItsOwnFailure() = runBlocking<Unit> {
        val original = ByteArray(2_000) { (it * 7).toByte() }
        val input = SyntheticTimelines.a()
        val (_, evidenceId) = withEvidence(input, original)
        val tampered = ExportService(context, vault, builder(), FakeRenderer(), signer, { FIXED_NOW }, ids)
        tampered.originalOpener = { ByteArrayInputStream(original.copyOf().also { it[10] = (it[10].toInt() xor 1).toByte() }) }
        val before = vault.audit.count()

        val result = tampered.export(selectAll(input).copy(includeOriginalsFor = setOf(evidenceId)))

        assertEquals(ExportResult.Failed(ExportFailure.ORIGINAL_HASH_MISMATCH), result)
        assertEquals(before, vault.audit.count())
        assertEquals(0L, tampered.pendingExportBytes())

        tampered.originalOpener = { ByteArrayInputStream(original) }
        val ok = tampered.export(selectAll(input).copy(includeOriginalsFor = setOf(evidenceId)))
        assertTrue(ok is ExportResult.Exported, ok.toString())
        assertEquals(listOf(ok.snapshotId), exportRows())
        assertEquals(1, ok.summary.originalCount)
    }

    @Test
    fun pendingExportBytesCountsLeftoverFilesUntilCleared() = runBlocking<Unit> {
        val input = SyntheticTimelines.a()
        store(input)
        val service = service()
        assertEquals(0L, service.pendingExportBytes())

        val result = service.export(selectAll(input)) as ExportResult.Exported

        assertTrue(result.zipFile.length() > 0)
        assertEquals(result.zipFile.length(), service.pendingExportBytes())
        service.clearExports()
        assertEquals(0L, service.pendingExportBytes())
    }
}
