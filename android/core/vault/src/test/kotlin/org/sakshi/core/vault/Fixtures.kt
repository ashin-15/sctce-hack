package org.sakshi.core.vault

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.GeneralSecurityException
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.sakshi.core.crypto.KeyWrapper
import org.sakshi.core.crypto.SoftwareKeyWrapper
import org.sakshi.core.database.SakshiDatabase
import org.sakshi.core.database.SakshiDatabaseFactory

/** Synthetic plaintext that must never appear in any stored file. */
const val MARKER: String = "SYNTHETIC-PLAINTEXT-MARKER-7f3a"

val FIXED_INSTANT: Instant = Instant.parse("2026-10-02T10:00:00Z")

fun testWrapper(): KeyWrapper = SoftwareKeyWrapper(ByteArray(32) { it.toByte() })

fun contains(haystack: ByteArray, needle: ByteArray): Boolean =
    (0..haystack.size - needle.size).any { start -> needle.indices.all { haystack[start + it] == needle[it] } }

/** Delivers [good] bytes and then fails, to simulate a source that dies mid-read. */
class FailingInputStream(private val good: Int) : InputStream() {
    private var served = 0

    override fun read(): Int {
        if (served >= good) throw IOException("synthetic failure")
        served++
        return MARKER[served % MARKER.length].code
    }
}

/** Wraps like [testWrapper] but can never unwrap, like a Keystore that lost its key. */
class UnwrapFailsWrapper(private val delegate: KeyWrapper) : KeyWrapper {
    override fun wrap(secret: ByteArray): ByteArray = delegate.wrap(secret)

    override fun unwrap(wrapped: ByteArray): ByteArray = throw GeneralSecurityException("synthetic key loss")
}

/** Records what callers pass to the audit log, then appends it for real. */
class RecordingAuditLog(database: SakshiDatabase, clock: () -> Instant) : AuditLog(database, clock) {
    val calls = mutableListOf<String>()

    override suspend fun append(
        action: String,
        subjectType: String,
        subjectId: String,
        details: kotlinx.serialization.json.JsonObject,
    ): Long {
        calls += "$action|$subjectType|$subjectId|$details"
        return super.append(action, subjectType, subjectId, details)
    }
}

/** Wires every component directly over an in-memory database so tests can also inspect the tables. */
@RunWith(RobolectricTestRunner::class)
abstract class VaultTestBase {
    protected val context: Context = ApplicationProvider.getApplicationContext()
    protected val clock: () -> Instant = { FIXED_INSTANT }
    private var counter = 0
    protected val ids: () -> String = { "synthetic-${++counter}" }

    protected lateinit var db: SakshiDatabase
    protected lateinit var blobs: BlobStore
    protected lateinit var audit: AuditLog
    protected lateinit var cases: CaseRepository
    protected lateinit var evidence: EvidenceRepository
    protected lateinit var jobs: JobQueue
    protected lateinit var sweeper: OrphanSweeper

    protected val blobDirectory: File get() = File(context.noBackupFilesDir, "vault/blobs")

    @Before
    fun openComponents() {
        db = SakshiDatabaseFactory.openInMemoryForTests(context)
        blobs = BlobStore(blobDirectory, testWrapper(), chunkSize = 4096)
        audit = AuditLog(db, clock)
        cases = CaseRepository(db, blobs, audit, clock, ids, Dispatchers.IO)
        evidence = EvidenceRepository(db, blobs, audit, clock, ids, Dispatchers.IO)
        jobs = JobQueue(db, clock)
        sweeper = OrphanSweeper(db, blobs, Dispatchers.IO)
    }

    @After
    fun closeComponents() {
        db.close()
        File(context.noBackupFilesDir, "vault").deleteRecursively()
    }

    protected fun request(caseId: String, maxBytes: Long = 1_000_000L, declaredMime: String? = "text/plain") =
        ImportRequest(
            caseId = caseId,
            acquisitionKind = AcquisitionKind.SHARED_STREAM,
            accessClass = AccessClass.USER_MEDIATED,
            importerMechanism = "synthetic-test",
            declaredMime = declaredMime,
            claimedOrigin = "synthetic-origin",
            displayNameClaim = "synthetic-name.bin",
            uriAuthorityClaim = "synthetic.authority",
            maxPlaintextBytes = maxBytes,
        )

    protected fun blobFiles(): List<String> = blobDirectory.list()?.sorted().orEmpty()
}
