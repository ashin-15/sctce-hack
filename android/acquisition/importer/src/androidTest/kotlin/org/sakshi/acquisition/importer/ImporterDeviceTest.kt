package org.sakshi.acquisition.importer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.sakshi.core.vault.KeystoreKeyWrapper
import org.sakshi.core.vault.Vault

class ImporterDeviceTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val authority: String = context.packageName + ".synthetic"
    private val wrapper = KeystoreKeyWrapper(alias = "synthetic-importer-test-${UUID.randomUUID()}", requireUserAuthentication = false)
    private lateinit var vault: Vault
    private lateinit var caseId: String
    private val vaultDirectory: File get() = File(context.noBackupFilesDir, "vault")

    private fun uri(name: String): Uri = Uri.parse("content://$authority/$name")

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun importer(): EvidenceImporter = EvidenceImporter(vault.evidence, context.contentResolver)

    private fun rows(): Int = runBlocking { vault.evidence.observeForCase(caseId).first().size }

    private fun blobFiles(): List<File> =
        File(vaultDirectory, "blobs").listFiles()?.toList().orEmpty()

    @Before
    fun setUp() {
        vaultDirectory.deleteRecursively()
        vault = Vault.open(context, wrapper)
        caseId = runBlocking { vault.cases.create("Synthetic device case").id }
    }

    @After
    fun tearDown() {
        runCatching { vault.close() }
        runCatching { wrapper.delete() }
        vaultDirectory.deleteRecursively()
    }

    private fun send(vararg names: String): Intent =
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(names.map(::uri)))

    @Test
    fun imageAndTextFileRoundTripThroughRealProviderAndVault() {
        val batch = IntentReader.read(send(SyntheticDeviceProvider.IMAGE, SyntheticDeviceProvider.TEXT), context.contentResolver, "synthetic.referrer")!!
        val image = assertIs<PendingItem.Stream>(batch.items[0])
        val text = assertIs<PendingItem.Stream>(batch.items[1])
        assertEquals(ItemKind.IMAGE, image.kind)
        assertEquals(ItemKind.TEXT_FILE, text.kind)
        assertEquals("synthetic-image.jpg", image.displayNameClaim)
        assertEquals(SyntheticDeviceProvider.IMAGE_SIZE.toLong(), image.sizeClaim)

        val report = runBlocking { importer().commit(caseId, batch, setOf(0, 1)) }

        val expected = listOf(SyntheticDeviceProvider.imageBytes(), SyntheticDeviceProvider.textBytes())
        report.outcomes.forEachIndexed { i, outcome ->
            val saved = assertIs<ItemOutcome.Saved>(outcome)
            assertEquals(sha256(expected[i]), saved.sha256)
            assertEquals(expected[i].size.toLong(), saved.byteSize)
            assertEquals(AnalysisState.READY_FOR_TEXT_ANALYSIS, saved.analysisState)
            val back = runBlocking { vault.evidence.openOriginal(saved.evidenceId).use { it.inputStream().readBytes() } }
            assertContentEquals(expected[i], back)
        }
        assertEquals(2, rows())
        assertEquals(2, blobFiles().size)
    }

    @Test
    fun providerThrowingSecurityExceptionGivesAccessDenied() {
        val batch = IntentReader.read(send(SyntheticDeviceProvider.DENIED), context.contentResolver, null)!!
        val report = runBlocking { importer().commit(caseId, batch, setOf(0)) }
        assertEquals(ItemOutcome.Failed(0, ImportFailure.ACCESS_DENIED), report.outcomes.single())
        assertEquals(0, rows())
        assertEquals(0, blobFiles().size)
    }

    @Test
    fun streamFailingMidwayLeavesNoRowAndNoFile() {
        val batch = IntentReader.read(send(SyntheticDeviceProvider.MIDWAY), context.contentResolver, null)!!
        val report = runBlocking { importer().commit(caseId, batch, setOf(0)) }
        assertEquals(ItemOutcome.Failed(0, ImportFailure.STORAGE_ERROR), report.outcomes.single())
        assertEquals(0, rows())
        assertEquals(0, blobFiles().size)
        assertTrue(vaultDirectory.walkTopDown().none { it.name.endsWith(".tmp") })
    }

    @Test
    fun sendIntentParsesToExpectedBatch() {
        val target = uri(SyntheticDeviceProvider.IMAGE)
        val intent = Intent(Intent.ACTION_SEND).setType("image/*").putExtra(Intent.EXTRA_STREAM, target)
        val batch = IntentReader.read(intent, context.contentResolver, "synthetic.referrer")
        val expected = PendingBatch(
            mechanism = ImportMechanism.SHARE_SEND,
            items = listOf(
                PendingItem.Stream(
                    index = 0,
                    uri = target,
                    declaredMime = "image/jpeg",
                    displayNameClaim = "synthetic-image.jpg",
                    sizeClaim = SyntheticDeviceProvider.IMAGE_SIZE.toLong(),
                    kind = ItemKind.IMAGE,
                    uriAuthorityClaim = authority,
                ),
            ),
            referrerClaim = "synthetic.referrer",
        )
        assertEquals(expected, batch)
    }
}
