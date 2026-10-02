package org.sakshi.core.vault

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking

class ExportAuditRecordTest : EventStoreTestBase() {
    @Test
    fun recordExportAppendsIdsAndCountsOnly() = runBlocking<Unit> {
        recording.recordExport("synthetic-snap", "synthetic-case-1", 7, 2, "synthetic-key")

        assertEquals(
            "export.created|export|synthetic-snap|{\"snapshot_id\":\"synthetic-snap\",\"case_id\":\"synthetic-case-1\"," +
                "\"event_count\":7,\"original_count\":2,\"signer_key_id\":\"synthetic-key\"}",
            recording.calls.single(),
        )
        assertEquals("export.created", AuditActions.EXPORT_CREATED)
        assertEquals(1, assertIs<AuditVerification.Valid>(recording.verify()).count)
    }
}
