package org.sakshi.core.vault

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith

/** Measurements, not performance assertions: each test only checks that the work completed correctly. */
@RunWith(AndroidJUnit4::class)
class VaultBenchmarkDeviceTest : DeviceTestBase() {
    private val streamBytes = 32L * 1024 * 1024
    private val bytesPerMebibyte = 1024.0 * 1024.0

    private fun seconds(startNanos: Long): Double = (System.nanoTime() - startNanos) / 1e9

    @Test
    fun importThroughputAndFullVerify() = runBlocking<Unit> {
        val vault = openVault(newWrapper())
        vault.startUp()
        val case = vault.cases.create("synthetic-case-throughput")
        val request = importRequest(case.id, streamBytes + 1)

        vault.evidence.import(request, SyntheticStream(streamBytes, seed = 1L))
        var lastId = ""
        val rates = (1..3).map { run ->
            val start = System.nanoTime()
            lastId = vault.evidence.import(request, SyntheticStream(streamBytes, seed = run + 1L)).id
            streamBytes / bytesPerMebibyte / seconds(start)
        }

        val verifyStart = System.nanoTime()
        val result = vault.evidence.verify(lastId)
        val verifySeconds = seconds(verifyStart)

        assertEquals(VerificationResult.Intact, result)
        record(
            "import_throughput",
            *deviceState(context),
            "payload_mib" to 32,
            "runs" to rates.size,
            "import_mb_per_s_run1" to rates[0],
            "import_mb_per_s_run2" to rates[1],
            "import_mb_per_s_run3" to rates[2],
            "import_mb_per_s_median" to rates.sorted()[1],
            "verify_seconds" to verifySeconds,
            "verify_mb_per_s" to streamBytes / bytesPerMebibyte / verifySeconds,
        )
    }

    @Test
    fun randomAccessReads() = runBlocking<Unit> {
        val vault = openVault(newWrapper())
        vault.startUp()
        val case = vault.cases.create("synthetic-case-random-access")
        val imported = vault.evidence.import(importRequest(case.id, streamBytes + 1), SyntheticStream(streamBytes))
        val random = Random(7L)
        val buffer = ByteArray(4096)
        val reads = 200

        vault.evidence.openOriginal(imported.id).use { reader ->
            val positions = LongArray(reads) { (random.nextDouble() * (reader.size - buffer.size)).toLong() }
            val start = System.nanoTime()
            for (position in positions) {
                assertEquals(buffer.size, reader.read(position, buffer, 0, buffer.size))
            }
            val elapsedMicros = (System.nanoTime() - start) / 1e3
            record(
                "random_access",
                *deviceState(context),
                "reads" to reads,
                "read_bytes" to buffer.size,
                "mean_us_per_read" to elapsedMicros / reads,
            )
        }
    }

    @Test
    fun databaseOpenAndAuditInsertTimings() = runBlocking<Unit> {
        val wrapper = newWrapper()
        val rows = 1000

        val openStart = System.nanoTime()
        val vault = openVault(wrapper)
        vault.audit.head()
        val openMillis = seconds(openStart) * 1e3

        val insertStart = System.nanoTime()
        repeat(rows) { i -> vault.audit.append("synthetic.bench", "synthetic", "synthetic-$i") }
        val insertMillis = seconds(insertStart) * 1e3

        val verification = assertIs<AuditVerification.Valid>(vault.audit.verify())
        assertEquals(rows, verification.count)
        assertTrue(insertMillis > 0.0)
        record(
            "database_timings",
            *deviceState(context),
            "open_ms_including_keystore_and_first_query" to openMillis,
            "audit_rows" to rows,
            "insert_total_ms" to insertMillis,
            "insert_ms_per_row" to insertMillis / rows,
        )
    }
}
