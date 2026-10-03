package org.sakshi.acquisition.projection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.sakshi.core.crypto.SoftwareKeyWrapper
import org.sakshi.core.vault.Vault
import org.sakshi.processing.ocr.OcrBox
import org.sakshi.processing.ocr.OcrEngine
import org.sakshi.processing.ocr.OcrFrame
import org.sakshi.processing.ocr.OcrLine
import org.sakshi.processing.ocr.OcrOutcome
import org.sakshi.processing.ocr.OcrPoint
import org.sakshi.processing.ocr.OcrProcessor
import org.sakshi.processing.ocr.OcrResult

@RunWith(RobolectricTestRunner::class)
class ProjectionEvidenceCoordinatorTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var vault: Vault
    private lateinit var caseId: String
    private var counter = 0

    @Before
    fun setUp() {
        vault = Vault.openForTests(
            context,
            SoftwareKeyWrapper(ByteArray(32) { it.toByte() }),
            { Instant.parse("2026-10-03T10:00:00Z") },
            { "synthetic-${++counter}" },
        )
        caseId = runBlocking { vault.cases.create("Harassment Case").id }
    }

    @After
    fun tearDown() {
        vault.close()
        File(context.noBackupFilesDir, "vault").deleteRecursively()
    }

    private class FakeOcrProcessor(private val outcome: OcrOutcome) : OcrProcessor {
        override val engine: OcrEngine = OcrEngine("test-engine", "1.0", setOf("Latn"))

        override suspend fun process(imageBytes: ByteArray): OcrOutcome = outcome
    }

    private fun createSyntheticFrame(isBlack: Boolean): CapturedFrame {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        if (isBlack) {
            bitmap.eraseColor(Color.BLACK)
        } else {
            bitmap.eraseColor(Color.WHITE)
            bitmap.setPixel(10, 10, Color.BLUE)
        }
        val bytes = FrameSampler.bitmapToPngBytes(bitmap)
        val hash = FrameSampler.sha256Hex(bytes)
        val kind = FrameSampler.detectFrameKind(bitmap)
        bitmap.recycle()

        return CapturedFrame(
            frameIndex = 1,
            timestampMs = 123456789L,
            width = 100,
            height = 100,
            imageBytes = bytes,
            sha256Hex = hash,
            kind = kind,
        )
    }

    @Test
    fun `stores blank output without claiming why Android returned a blank frame`() = runBlocking {
        val frame = createSyntheticFrame(isBlack = true)
        val coordinator = ProjectionEvidenceCoordinator(
            evidenceRepository = vault.evidence,
            ocrProcessor = FakeOcrProcessor(OcrOutcome.NoText(OcrFrame(100, 100, 1, 1, 0))),
        )

        val outcome = coordinator.processAndStore(caseId, frame)
        val blankOutcome = assertIs<ProjectionEvidenceOutcome.NoTextDetected>(outcome)

        assertEquals(frame.sha256Hex, blankOutcome.sha256)

        val stored = vault.evidence.observeForCase(caseId).first()
        assertEquals(1, stored.size)
        val details = vault.evidence.details(blankOutcome.evidenceId)
        assertEquals(frame.sha256Hex, details?.sha256)
    }

    @Test
    fun `stores normal frame and parses extracted chat messages via OCR`() = runBlocking {
        val frame = createSyntheticFrame(isBlack = false)
        val ocrResult = OcrResult(
            lines = listOf(
                OcrLine(
                    blockIndex = 0,
                    text = "Contact Name",
                    polygon = listOf(OcrPoint(10, 5), OcrPoint(80, 5), OcrPoint(80, 10), OcrPoint(10, 10)),
                    box = OcrBox(10, 5, 80, 10),
                    confidence = 0.99f,
                    language = "en",
                ),
                OcrLine(
                    blockIndex = 1,
                    text = "Give me the password 11:30 AM",
                    polygon = listOf(OcrPoint(10, 40), OcrPoint(90, 40), OcrPoint(90, 50), OcrPoint(10, 50)),
                    box = OcrBox(10, 40, 90, 50),
                    confidence = 0.95f,
                    language = "en",
                ),
            ),
            frame = OcrFrame(100, 100, 1, 1, 0),
        )

        val coordinator = ProjectionEvidenceCoordinator(
            evidenceRepository = vault.evidence,
            ocrProcessor = FakeOcrProcessor(OcrOutcome.Success(ocrResult)),
        )

        val outcome = coordinator.processAndStore(caseId, frame)
        val successOutcome = assertIs<ProjectionEvidenceOutcome.Success>(outcome)

        assertEquals(frame.sha256Hex, successOutcome.sha256)
        assertEquals("Contact Name", successOutcome.parsedChat.contactName)
        assertEquals(1, successOutcome.parsedChat.messages.size)
        assertEquals("Give me the password", successOutcome.parsedChat.messages[0].text)
        assertEquals("11:30 AM", successOutcome.parsedChat.messages[0].timestampText)
    }
}
