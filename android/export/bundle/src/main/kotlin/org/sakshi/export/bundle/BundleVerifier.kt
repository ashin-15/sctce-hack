package org.sakshi.export.bundle

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.JsonElement

/** Offline consistency check of a bundle directory. The directory is treated as untrusted input. */
public object BundleVerifier {
    public fun verify(directory: Path, limits: VerifierLimits = VerifierLimits()): VerificationReport {
        if (!Files.isDirectory(directory)) return unreadable("layout", "directory does not exist or is not a directory")
        val root = directory.toRealPath()
        val manifestBytes = try {
            FileAccess.readBounded(root.resolve(BundleFormat.MANIFEST), limits.maxManifestBytes)
        } catch (e: BundleReadException) {
            return unreadable("manifest_parse", "manifest.json: ${e.message}")
        }
        val parsed: JsonElement
        val manifest: Manifest
        try {
            parsed = JsonInput.parse(String(manifestBytes, Charsets.UTF_8))
            manifest = Manifest.parse(parsed)
        } catch (e: IllegalArgumentException) {
            return unreadable("manifest_parse", "manifest.json cannot be parsed: ${safe(e.message.orEmpty())}")
        }
        val signer = try {
            SignerFile.parse(FileAccess.readBounded(root.resolve(BundleFormat.SIGNER), SIGNER_MAX_BYTES))
        } catch (e: BundleReadException) {
            return unreadable("signer_parse", "signer.json: ${e.message}")
        } catch (e: IllegalArgumentException) {
            return unreadable("signer_parse", "signer.json cannot be parsed: ${safe(e.message.orEmpty())}")
        }
        return Run(BundleView(root, manifest, limits), manifestBytes, parsed, signer).execute()
    }

    private const val SIGNER_MAX_BYTES: Long = 64L * 1024
    private const val SIGNATURE_MAX_BYTES: Long = 1024

    private fun unreadable(name: String, detail: String): VerificationReport = VerificationReport(
        Verdict.UNREADABLE,
        null,
        listOf(Check(name, CheckStatus.FAILED, detail)),
        emptyMap(),
        BundleFormat.LIMITS,
    )

    private class Run(
        val view: BundleView,
        val manifestBytes: ByteArray,
        val parsed: JsonElement,
        val signer: SignerFile,
    ) {
        fun execute(): VerificationReport {
            val signature = try {
                FileAccess.readBounded(view.root.resolve(BundleFormat.SIGNATURE), SIGNATURE_MAX_BYTES)
            } catch (e: BundleReadException) {
                null
            }
            val signatureOutcome = SignatureVerifier.verify(manifestBytes, signature, signer)
            val events = ContentChecks.events(view)
            val checks = listOf(
                StructureChecks.layout(view),
                StructureChecks.canonical(manifestBytes, parsed),
                signatureOutcome.check,
                StructureChecks.fileHashes(view),
                StructureChecks.merkle(view.manifest),
                events.check,
                ReferenceChecks.check(view, events.events),
                ContentChecks.noCrossCase(view, events.events),
                StructureChecks.omitted(view.manifest),
            )
            val verdict = if (checks.any { it.status == CheckStatus.FAILED }) Verdict.INCONSISTENT else Verdict.CONSISTENT
            val omitted = view.manifest.omitted
            return VerificationReport(
                verdict,
                signatureOutcome.keyId,
                checks,
                mapOf(
                    "evidence_count" to omitted.evidenceCount,
                    "derivative_count" to omitted.derivativeCount,
                    "event_count" to omitted.eventCount,
                ),
                BundleFormat.LIMITS,
            )
        }
    }
}
