package org.sakshi.acquisition.projection

import java.io.ByteArrayInputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.sakshi.core.vault.AccessClass
import org.sakshi.core.vault.AcquisitionKind
import org.sakshi.core.vault.EvidenceRepository
import org.sakshi.core.vault.ImportRequest
import org.sakshi.processing.ocr.OcrFailure
import org.sakshi.processing.ocr.OcrOutcome
import org.sakshi.processing.ocr.OcrProcessor

/**
 * Outcome of processing a captured frame after a user explicitly saves it to the vault.
 */
public sealed interface ProjectionEvidenceOutcome {
    /** Frame stored; OCR layout output is an unreviewed proposal with unverified attribution. */
    public data class Success(
        public val evidenceId: String,
        public val sha256: String,
        public val parsedChat: ParsedChatScreen,
    ) : ProjectionEvidenceOutcome

    /** Frame stored, but no readable text was recognized. */
    public data class NoTextDetected(
        public val evidenceId: String,
        public val sha256: String,
    ) : ProjectionEvidenceOutcome

    /** Frame stored, but OCR processing encountered an error. */
    public data class OcrFailed(
        public val evidenceId: String,
        public val sha256: String,
        public val failure: OcrFailure,
    ) : ProjectionEvidenceOutcome
}

/**
 * Coordinates frame ingestion into the encrypted vault and on-device OCR transcript extraction.
 */
public class ProjectionEvidenceCoordinator(
    private val evidenceRepository: EvidenceRepository,
    private val ocrProcessor: OcrProcessor,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /**
     * Stores the original frame bytes into the vault with cryptographic SHA-256 verification,
     * then runs local OCR. Layout guesses are uncertain derivatives.
     */
    public suspend fun processAndStore(
        caseId: String,
        frame: CapturedFrame,
    ): ProjectionEvidenceOutcome = withContext(dispatcher) {
        val request = ImportRequest(
            caseId = caseId,
            acquisitionKind = AcquisitionKind.SELECTED_VISUAL_MEDIA,
            accessClass = AccessClass.USER_MEDIATED,
            importerMechanism = "media_projection",
            declaredMime = "image/png",
            claimedOrigin = if (frame.kind == FrameKind.BLANK) "BLANK_OR_UNAVAILABLE_SCREEN_OBSERVATION" else "SCREEN_OBSERVATION",
            displayNameClaim = "screen_capture_" + frame.frameIndex + ".png",
            uriAuthorityClaim = null,
            maxPlaintextBytes = frame.imageBytes.size.toLong(),
        )
        val imported = ByteArrayInputStream(frame.imageBytes).use { evidenceRepository.import(request, it) }

        when (val ocrOutcome = ocrProcessor.process(frame.imageBytes)) {
            is OcrOutcome.Success -> {
                val parsed = ChatVisualParser.parse(
                    result = ocrOutcome.result,
                    frameWidth = frame.width,
                    frameHeight = frame.height,
                )
                ProjectionEvidenceOutcome.Success(imported.id, imported.sha256, parsed)
            }
            is OcrOutcome.NoText -> ProjectionEvidenceOutcome.NoTextDetected(imported.id, imported.sha256)
            is OcrOutcome.Failed -> ProjectionEvidenceOutcome.OcrFailed(imported.id, imported.sha256, ocrOutcome.failure)
        }
    }
}
