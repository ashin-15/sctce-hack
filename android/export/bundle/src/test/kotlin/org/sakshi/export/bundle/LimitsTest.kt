package org.sakshi.export.bundle

import java.nio.file.Files
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LimitsTest {
    @get:Rule
    val tmp: TemporaryFolder = TemporaryFolder()

    @Test
    fun manifestLargerThanLimitIsUnreadable() {
        val dir = tmp.newFolder().toPath()
        BundleWriter.write(sampleContent(), dir, SoftwareP256Signer.generate())
        val report = BundleVerifier.verify(dir, VerifierLimits(maxManifestBytes = 100))
        assertEquals(Verdict.UNREADABLE, report.verdict)
    }

    @Test
    fun moreFilesThanLimitFailsLayout() {
        val dir = tmp.newFolder().toPath()
        BundleWriter.write(sampleContent(), dir, SoftwareP256Signer.generate())
        val report = BundleVerifier.verify(dir, VerifierLimits(maxFiles = 3))
        assertEquals(Verdict.INCONSISTENT, report.verdict)
        assertEquals(CheckStatus.FAILED, check(report, "layout").status)
        assertEquals(CheckStatus.FAILED, check(report, "file_hashes").status)
    }

    @Test
    fun missingDirectoryIsUnreadable() {
        val report = BundleVerifier.verify(tmp.root.toPath().resolve("does-not-exist"))
        assertEquals(Verdict.UNREADABLE, report.verdict)
    }

    @Test
    fun garbledManifestOrSignerIsUnreadable() {
        val dir = tmp.newFolder().toPath()
        BundleWriter.write(sampleContent(), dir, SoftwareP256Signer.generate())
        Files.writeString(dir.resolve("signer.json"), "not json")
        assertEquals(Verdict.UNREADABLE, BundleVerifier.verify(dir).verdict)
        Files.writeString(dir.resolve("manifest.json"), "[[[[")
        assertEquals(Verdict.UNREADABLE, BundleVerifier.verify(dir).verdict)
    }

    @Test
    fun deeplyNestedManifestIsUnreadableNotACrash() {
        val dir = tmp.newFolder().toPath()
        BundleWriter.write(sampleContent(), dir, SoftwareP256Signer.generate())
        Files.writeString(dir.resolve("manifest.json"), "[".repeat(100_000))
        assertEquals(Verdict.UNREADABLE, BundleVerifier.verify(dir).verdict)
    }
}
