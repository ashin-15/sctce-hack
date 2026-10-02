package org.sakshi.app.support

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.sakshi.acquisition.importer.EvidenceImporter
import org.sakshi.acquisition.importer.ItemKind
import org.sakshi.acquisition.importer.PendingItem
import org.sakshi.core.crypto.SoftwareKeyWrapper
import org.sakshi.core.vault.Vault

val FIXED_INSTANT: Instant = Instant.parse("2026-10-02T10:00:00Z")
private const val AUTHORITY = "synthetic.authority"
private const val AWAIT_MILLIS = 10_000L

/** An in-memory vault with synthetic ids, a fixed clock, and helpers to serve bytes through the content resolver. */
@RunWith(RobolectricTestRunner::class)
abstract class VaultTestBase {
    protected val context: Context = ApplicationProvider.getApplicationContext()
    protected val resolver get() = context.contentResolver
    protected lateinit var vault: Vault
    protected lateinit var importer: EvidenceImporter
    protected val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
    private val counter = AtomicInteger()

    @BeforeTest
    fun openVault() {
        vault = Vault.openForTests(
            context,
            SoftwareKeyWrapper(ByteArray(32) { it.toByte() }),
            { FIXED_INSTANT },
            { "synthetic-${counter.incrementAndGet()}" },
        )
        importer = EvidenceImporter(vault.evidence, resolver, clock = { FIXED_INSTANT })
    }

    @AfterTest
    fun closeVault() {
        scope.cancel()
        vault.close()
        File(context.noBackupFilesDir, "vault").deleteRecursively()
    }

    protected fun newCase(title: String = "synthetic case"): String = runBlocking { vault.cases.create(title).id }

    protected fun evidenceCount(caseId: String): Int = runBlocking { vault.evidence.observeForCase(caseId).first().size }

    protected fun uriFor(name: String): Uri = Uri.parse("content://$AUTHORITY/$name")

    /** Registers [supplier] as the content of a new synthetic URI. */
    protected fun serve(name: String, supplier: () -> InputStream): Uri =
        uriFor(name).also { shadowOf(resolver).registerInputStreamSupplier(it, supplier) }

    protected fun serve(name: String, bytes: ByteArray): Uri = serve(name) { ByteArrayInputStream(bytes) }

    protected fun streamItem(index: Int, uri: Uri, mime: String? = "application/octet-stream"): PendingItem.Stream =
        PendingItem.Stream(index, uri, mime, "synthetic-name-$index", null, ItemKind.OTHER, AUTHORITY)

    protected fun <T> await(flow: Flow<T>, predicate: (T) -> Boolean): T =
        runBlocking { withTimeout(AWAIT_MILLIS) { flow.first(predicate) } }
}

/** Reports the first read to [onFirstRead] before returning bytes, so a test can act while a copy is running. */
class HookedStream(bytes: ByteArray, private val onFirstRead: () -> Unit) : ByteArrayInputStream(bytes) {
    private var fired = false

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (!fired) {
            fired = true
            onFirstRead()
        }
        return super.read(b, off, len)
    }
}
