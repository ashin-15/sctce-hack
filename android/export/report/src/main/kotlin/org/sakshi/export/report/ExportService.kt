package org.sakshi.export.report

import android.content.Context
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.GeneralSecurityException
import java.security.ProviderException
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.sakshi.core.crypto.BlobIntegrityException
import org.sakshi.core.crypto.BlobReader
import org.sakshi.core.integrity.Sha256
import org.sakshi.core.vault.Vault
import org.sakshi.export.bundle.BundleContent
import org.sakshi.export.bundle.BundleFile
import org.sakshi.export.bundle.BundleSummary
import org.sakshi.export.bundle.BundleVerifier
import org.sakshi.export.bundle.BundleWriter
import org.sakshi.export.bundle.ManifestSigner
import org.sakshi.export.bundle.Verdict

/**
 * Builds a report and a signed bundle for a selection and packs them into one zip in the app's cache.
 *
 * Plaintext exists in `<cacheDir>/exports` only because the user asked to export. Call [clearExports] after the
 * zip has been shared and at start-up. No file name contains a case title or any other user text.
 */
public class ExportService(
    private val context: Context,
    private val vault: Vault,
    private val builder: ReportBuilder,
    private val renderer: ReportRenderer,
    private val signer: ManifestSigner,
    private val clock: () -> Instant,
    private val ids: () -> String,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /**
     * Builds the report and bundle for [selection], verifies the bundle, zips it to
     * `<cacheDir>/exports/<snapshot-id>.zip` and returns it. On any refusal or failure nothing is left behind.
     */
    public suspend fun export(selection: ReportSelection, options: ReportOptions = ReportOptions()): ExportResult =
        withContext(dispatcher) {
            val snapshotId = ids()
            if (!SNAPSHOT_ID.matches(snapshotId)) return@withContext ExportResult.Failed(ExportFailure.CONTENT_REJECTED)
            val keyId = try {
                Sha256.hex(Sha256.digest(signer.publicKeySpkiDer))
            } catch (_: GeneralSecurityException) {
                return@withContext ExportResult.Failed(ExportFailure.SIGNING_FAILED)
            } catch (_: ProviderException) {
                return@withContext ExportResult.Failed(ExportFailure.SIGNING_FAILED)
            }
            when (val built = builder.build(selection, options, keyId)) {
                is ReportBuildResult.Refused -> ExportResult.Refused(built.reason)
                is ReportBuildResult.Built -> Run(snapshotId, keyId, built).execute()
            }
        }

    /** Deletes everything under `<cacheDir>/exports`. Returns the number of files removed. */
    public fun clearExports(): Int {
        val root = exportsDirectory()
        if (!root.exists()) return 0
        val removed = root.walkBottomUp().count { it.isFile && it.delete() }
        root.deleteRecursively()
        return removed
    }

    /** Total size in bytes of the files left under `<cacheDir>/exports`, so the app can report or clean them at start-up. */
    public fun pendingExportBytes(): Long {
        val root = exportsDirectory()
        return if (root.exists()) root.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L
    }

    /** Tests only: opens the original's bytes instead of the vault, to simulate a store that changed them. */
    internal var originalOpener: ((String) -> InputStream)? = null

    private fun exportsDirectory(): File = File(context.cacheDir, EXPORTS)

    private inner class Run(val snapshotId: String, val keyId: String, val built: ReportBuildResult.Built) {
        private val root = exportsDirectory()
        private val work = File(root, "$snapshotId.work")
        private val bundle = File(root, snapshotId)
        private val zip = File(root, "$snapshotId.zip")

        suspend fun execute(): ExportResult = try {
            produce()
        } catch (_: BlobIntegrityException) {
            ExportResult.Failed(ExportFailure.ORIGINAL_UNAVAILABLE)
        } catch (_: OriginalHashMismatchException) {
            ExportResult.Failed(ExportFailure.ORIGINAL_HASH_MISMATCH)
        } catch (_: IOException) {
            ExportResult.Failed(ExportFailure.IO_ERROR)
        } catch (_: GeneralSecurityException) {
            ExportResult.Failed(ExportFailure.SIGNING_FAILED)
        } catch (_: ProviderException) {
            ExportResult.Failed(ExportFailure.SIGNING_FAILED)
        } catch (_: IllegalArgumentException) {
            ExportResult.Failed(ExportFailure.CONTENT_REJECTED)
        } finally {
            withContext(NonCancellable) {
                work.deleteRecursively()
                bundle.deleteRecursively()
                File(root, "$snapshotId.zip.part").delete()
            }
        }

        private suspend fun produce(): ExportResult {
            check(work.mkdirs() && bundle.mkdirs()) { "Could not create export directories" }
            val pdf = File(work, "report.pdf")
            val pages = try {
                pdf.outputStream().buffered().use { renderer.render(built.model, it).pageCount }
            } catch (_: IllegalArgumentException) {
                return ExportResult.Failed(ExportFailure.RENDER_FAILED)
            }
            val originals = originals()
            val inputs = built.bundleInputs
            val reportSha = Sha256.hex(Sha256.digest(pdf.readBytes()))
            val content = BundleContent(
                snapshotId = snapshotId,
                caseId = inputs.caseId,
                createdAt = clock().toString(),
                generator = built.model.generator,
                auditChainHead = inputs.auditChainHead,
                omitted = inputs.omitted,
                events = inputs.events,
                findings = inputs.findings,
                corrections = inputs.corrections,
                patterns = inputs.patterns,
                provenance = ProvenanceAssembler.assemble(inputs.events, inputs.findings, inputs.patterns, originals.map { it.first }, reportSha),
                originals = originals.map { it.second },
                derivatives = emptyList(),
                reportPdf = BundleFile("report", pdf.length()) { pdf.inputStream() },
            )
            val summary = BundleWriter.write(content, bundle.toPath(), signer)
            if (BundleVerifier.verify(bundle.toPath()).verdict != Verdict.CONSISTENT) {
                return ExportResult.Failed(ExportFailure.VERIFICATION_FAILED)
            }
            return pack(summary, pages, originals.size)
        }

        private suspend fun pack(summary: BundleSummary, pages: Int, originalCount: Int): ExportResult {
            val part = File(root, "$snapshotId.zip.part")
            ZipPacker.pack(bundle.toPath(), part)
            val inputs = built.bundleInputs
            vault.audit.recordExport(snapshotId, inputs.caseId, inputs.events.size, originalCount, summary.signerKeyId)
            Files.move(part.toPath(), zip.toPath(), StandardCopyOption.ATOMIC_MOVE)
            return ExportResult.Exported(
                zipFile = zip,
                snapshotId = snapshotId,
                signerKeyId = summary.signerKeyId,
                summary = ExportSummary(
                    merkleRoot = summary.merkleRoot,
                    fileCount = summary.fileCount,
                    pageCount = pages,
                    eventCount = inputs.events.size,
                    findingCount = inputs.findings.size,
                    patternCount = inputs.patterns.size,
                    originalCount = originalCount,
                    omitted = inputs.omitted,
                    zipBytes = zip.length(),
                ),
            )
        }

        /** The selected originals with the id each gets in the bundle. Unselected evidence is never touched. */
        private suspend fun originals(): List<Pair<IncludedOriginal, BundleFile>> =
            built.bundleInputs.includeOriginalsFor.sorted().map { evidenceId ->
                val details = vault.evidence.details(evidenceId) ?: throw BlobIntegrityException("Original is missing")
                val opaque = if (OPAQUE_ID.matches(evidenceId) && evidenceId != "." && evidenceId != "..") evidenceId else ids()
                IncludedOriginal(evidenceId, opaque, details.sha256) to
                    BundleFile(opaque, details.byteSize) { openOriginal(evidenceId, details.sha256) }
            }

        /**
         * Called by the bundle writer on its own thread. The reader is closed with the stream, and reading to the end
         * checks the bytes against the hash stored at import.
         */
        private fun openOriginal(evidenceId: String, expectedSha256: String): InputStream {
            originalOpener?.let { return HashCheckedStream(it(evidenceId), expectedSha256) }
            val reader: BlobReader = runBlocking { vault.evidence.openOriginal(evidenceId) }
            val source = HashCheckedStream(reader.inputStream(), expectedSha256)
            return object : InputStream() {
                override fun read(): Int = source.read()

                override fun read(b: ByteArray, off: Int, len: Int): Int = source.read(b, off, len)

                override fun close() {
                    reader.close()
                }
            }
        }
    }

    private companion object {
        const val EXPORTS = "exports"
        val SNAPSHOT_ID: Regex = Regex("[A-Za-z0-9_-]{1,100}")
        val OPAQUE_ID: Regex = Regex("[A-Za-z0-9._-]{1,128}")
    }
}
