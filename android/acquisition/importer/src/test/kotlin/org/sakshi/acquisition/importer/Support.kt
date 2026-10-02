package org.sakshi.acquisition.importer

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.sakshi.core.crypto.KeyWrapper
import org.sakshi.core.crypto.SoftwareKeyWrapper
import org.sakshi.core.vault.Vault

const val AUTHORITY: String = "synthetic.test"
val FIXED_INSTANT: Instant = Instant.parse("2026-10-02T10:00:00Z")

/** What the synthetic provider answers for one path. */
class Entry(
    val mime: String? = null,
    val name: String? = null,
    val size: Long? = null,
    val throwOnMetadata: Boolean = false,
)

/** Answers `getType` and `query` for registered paths. Bytes are served through Robolectric's resolver shadow. */
class SyntheticProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? {
        val entry = entries[uri.path] ?: return null
        if (entry.throwOnMetadata) throw SecurityException("synthetic")
        return entry.mime
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, args: Array<String>?, order: String?): Cursor? {
        val entry = entries[uri.path] ?: return null
        if (entry.throwOnMetadata) throw SecurityException("synthetic")
        val columns = projection ?: arrayOf("_display_name", "_size")
        val cursor = MatrixCursor(columns)
        cursor.addRow(columns.map { column ->
            when (column) {
                "_display_name" -> entry.name
                "_size" -> entry.size
                else -> null
            }
        })
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, args: Array<String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<String>?): Int = 0

    companion object {
        val entries: MutableMap<String, Entry> = HashMap()
    }
}

/** Remembers whether it was closed. */
class TrackingStream(delegate: InputStream) : FilterInputStream(delegate) {
    var closed: Boolean = false
        private set

    override fun close() {
        closed = true
        super.close()
    }
}

/** Delivers [good] bytes and then fails. */
class FailingStream(private val good: Int) : InputStream() {
    private var served = 0

    override fun read(): Int {
        if (served >= good) throw IOException("synthetic")
        served++
        return 'a'.code
    }
}

/** Fails to wrap, like a Keystore that lost its key. */
class BrokenWrapper : KeyWrapper {
    override fun wrap(secret: ByteArray): ByteArray = throw org.sakshi.core.vault.VaultKeyException.Unavailable(null)

    override fun unwrap(wrapped: ByteArray): ByteArray = throw org.sakshi.core.vault.VaultKeyException.Unavailable(null)
}

fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

fun contentUri(path: String): Uri = Uri.parse("content://$AUTHORITY/$path")

/** Robolectric base with an open in-memory vault, a case and the synthetic provider. */
@RunWith(RobolectricTestRunner::class)
abstract class ImporterTestBase {
    protected val context: Context = ApplicationProvider.getApplicationContext()
    protected val resolver get() = context.contentResolver
    private var counter = 0
    protected lateinit var vault: Vault
    protected lateinit var caseId: String
    protected val blobDirectory: File get() = File(context.noBackupFilesDir, "vault/blobs")

    @Before
    fun openVault() {
        Robolectric.setupContentProvider(SyntheticProvider::class.java, AUTHORITY)
        SyntheticProvider.entries.clear()
        vault = newVault(SoftwareKeyWrapper(ByteArray(32) { it.toByte() }))
        caseId = runBlocking { vault.cases.create("Synthetic case").id }
    }

    @After
    fun closeVault() {
        vault.close()
        File(context.noBackupFilesDir, "vault").deleteRecursively()
    }

    protected fun newVault(wrapper: KeyWrapper): Vault =
        Vault.openForTests(context, wrapper, { FIXED_INSTANT }, { "synthetic-${++counter}" })

    protected fun importer(limits: ImportLimits = ImportLimits(), vault: Vault = this.vault): EvidenceImporter =
        EvidenceImporter(vault.evidence, resolver, limits, clock = { FIXED_INSTANT })

    /** Registers [bytes] as the content of [path] and describes it in the provider. */
    protected fun serve(path: String, bytes: ByteArray, entry: Entry = Entry()): Uri =
        serveStream(path, entry) { ByteArrayInputStream(bytes) }

    protected fun serveStream(path: String, entry: Entry = Entry(), supplier: () -> InputStream): Uri {
        val uri = contentUri(path)
        SyntheticProvider.entries[uri.path!!] = entry
        shadowOf(resolver).registerInputStreamSupplier(uri, supplier)
        return uri
    }

    protected fun rowCount(): Int = runBlocking { vault.evidence.observeForCase(caseId).first().size }

    protected fun blobNames(): List<String> = blobDirectory.list()?.sorted().orEmpty()

    protected fun assertNoBlobs() = assertEquals(emptyList(), blobNames())

    protected fun assertOnlyBlobFilesInVault() {
        val vaultRoot = File(context.noBackupFilesDir, "vault")
        val pattern = Regex("[0-9a-f]{32}\\.skb")
        vaultRoot.walkTopDown().filter { it.isFile }.forEach { assertTrue(pattern.matches(it.name), "Unexpected file in vault") }
    }

    protected fun original(evidenceId: String): ByteArray =
        runBlocking { vault.evidence.openOriginal(evidenceId).use { it.inputStream().readBytes() } }

    protected fun pendingStream(uri: Uri, kind: ItemKind, index: Int = 0, mime: String? = null): PendingItem.Stream =
        PendingItem.Stream(index, uri, mime, "synthetic-name", null, kind, AUTHORITY)

    protected fun batch(vararg items: PendingItem, mechanism: ImportMechanism = ImportMechanism.SHARE_SEND_MULTIPLE) =
        PendingBatch(mechanism, items.toList(), "synthetic.referrer")
}
