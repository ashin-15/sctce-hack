package org.sakshi.app.support

import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipFile
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.sakshi.app.SessionServices
import org.sakshi.app.report.ReportViewModel
import org.sakshi.core.model.CategoryLabel
import org.sakshi.core.model.Event
import org.sakshi.export.bundle.SoftwareP256Signer
import org.sakshi.export.report.RenderSummary
import org.sakshi.export.report.ReportModel
import org.sakshi.export.report.ReportRenderer

/** Stands in for the platform PDF writer, which does not run on the JVM. */
class FakeReportRenderer(private val gate: CountDownLatch? = null, private val started: CountDownLatch? = null) : ReportRenderer {
    override fun render(model: ReportModel, output: OutputStream): RenderSummary {
        started?.countDown()
        if (gate != null) check(gate.await(WAIT_SECONDS, TimeUnit.SECONDS)) { "Test gate was never opened" }
        output.write("%PDF-1.4 synthetic ${model.events.size} events".toByteArray())
        return RenderSummary(PAGES)
    }

    companion object {
        const val PAGES: Int = 2
        private const val WAIT_SECONDS = 20L
    }
}

/** Synthetic case with a chat that has been read into events, plus services with a software signer and a fake renderer. */
abstract class ReportTestBase : AnalysisTestBase() {
    private val snapshotCounter = AtomicInteger()
    protected val signer = SoftwareP256Signer.generate()
    protected val exportDirectory: File get() = File(context.cacheDir, "exports")

    @AfterTest
    fun wipeExports() {
        exportDirectory.deleteRecursively()
    }

    protected fun services(renderer: ReportRenderer = FakeReportRenderer()): SessionServices = SessionServices(
        vault,
        context,
        Dispatchers.IO,
        { FIXED_INSTANT },
        { "synthetic-snapshot-${snapshotCounter.incrementAndGet()}" },
        signer,
        renderer,
        "synthetic-app-1",
    )

    protected fun reportModel(services: SessionServices, caseId: String) =
        ReportViewModel(caseId, vault, services.reportBuilder, services.exports, TEST_ZONE, scope)

    /** Imports [text] as a chat export and reads it into events. Returns the evidence id. */
    protected fun addChat(caseId: String, text: String = SyntheticChats.EIGHT_MESSAGES): String {
        val evidenceId = importText(caseId, text)
        analyseExport(evidenceId)
        return evidenceId
    }

    protected fun hasLabel(event: Event, label: CategoryLabel): Boolean = event.categories.any { it.label == label }

    protected fun unzip(zip: File): Path {
        val target = Files.createTempDirectory("synthetic-unzip")
        ZipFile(zip).use { archive ->
            archive.entries().asSequence().forEach { entry ->
                val out = target.resolve(entry.name)
                Files.createDirectories(out.parent)
                archive.getInputStream(entry).use { Files.copy(it, out) }
            }
        }
        return target
    }

    protected fun waitForEmptyExports() {
        runBlocking {
            repeat(STEPS) {
                if (exportDirectory.list().orEmpty().isEmpty()) return@runBlocking
                kotlinx.coroutines.delay(STEP_MILLIS)
            }
        }
        assertEquals(emptyList(), exportDirectory.list().orEmpty().toList())
    }

    private companion object {
        const val STEPS = 100
        const val STEP_MILLIS = 50L
    }
}
