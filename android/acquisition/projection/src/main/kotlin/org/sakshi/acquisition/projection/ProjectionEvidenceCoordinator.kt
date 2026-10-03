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
 * Outcome of processing and storing a captured visual frame into the encrypted evidence vault.
 */
public sealed interface ProjectionEvidenceOutcome {
    /** Frame stored and chat messages successfully extracted via OCR and spatial heuristics. */
    public data class Success(
        public val evidenceId: String,
        public val sha256: String,
        public val parsedChat: ParsedChatScreen,
    ) : ProjectionEvidenceOutcome

    /**
     * Frame stored as proof of secure content interception (FLAG_SECURE encountered).
     * Raw bytes preserved as tamper-evident audit record.
     */
    public data class SecureContentRecorded(
        public val evidenceId: String,
        public val sha256: String,
        public val reason: String,
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
     * Stores the raw frame bytes into the vault with cryptographic SHA-256 verification,
     * then extracts structured chat conversation bubbles using local OCR.
     */
    public suspend fun processAndStore(
        caseId: String,
        frame: CapturedFrame,
    ): ProjectionEvidenceOutcome = withContext(dispatcher) {
        val stream = ByteArrayInputStream(frame.imageBytes)

        when (frame.kind) {
            FrameKind.SECURE_CONTENT_DETECTED -> {
                val request = ImportRequest(
                    caseId = caseId,
                    acquisitionKind = AcquisitionKind.SELECTED_VISUAL_MEDIA,
                    accessClass = AccessClass.USER_MEDIATED,
                    importerMechanism = "media_projection",
                    declaredMime = "image/png",
                    claimedOrigin = "FLAG_SECURE_ENCOUNTERED",
                    displayNameClaim = "screen_capture_secure_blocked_${frame.frameIndex}.png",
                    uriAuthorityClaim = null,
                    maxPlaintextBytes = frame.imageBytes.size.toLong(),
                )
                val imported = evidenceRepository.import(request, stream)
                ProjectionEvidenceOutcome.SecureContentRecorded(
                    evidenceId = imported.id,
                    sha256 = imported.sha256,
                    reason = "Android OS FLAG_SECURE policy active; buffer contained zero luminosity",
                )
            }
            FrameKind.NORMAL, FrameKind.BLANK -> {
                val request = ImportRequest(
                    caseId = caseId,
                    acquisitionKind = AcquisitionKind.SELECTED_VISUAL_MEDIA,
                    accessClass = AccessClass.USER_MEDIATED,
                    importerMechanism = "media_projection",
                    declaredMime = "image/png",
                    claimedOrigin = "MEDIA_PROJECTION",
                    displayNameClaim = "screen_capture_${frame.frameIndex}.png",
                    uriAuthorityClaim = null,
                    maxPlaintextBytes = frame.imageBytes.size.toLong(),
                )
                val imported = evidenceRepository.import(request, stream)

                when (val ocrOutcome = ocrProcessor.process(frame.imageBytes)) {
                    is OcrOutcome.Success -> {
                        val parsed = ChatVisualParser.parse(
                            result = ocrOutcome.result,
                            frameWidth = frame.width,
                            frameHeight = frame.height,
                        )
                        ProjectionEvidenceOutcome.Success(
                            evidenceId = imported.id,
                            sha256 = imported.sha256,
                            parsedChat = parsed,
                        )
                    }
                    is OcrOutcome.NoText -> {
                        ProjectionEvidenceOutcome.NoTextDetected(
                            evidenceId = imported.id,
                            sha256 = imported.sha256,
                        )
                    }
                    is OcrOutcome.Failed -> {
                        ProjectionEvidenceOutcome.OcrFailed(
                            evidenceId = imported.id,
                            sha256 = imported.sha256,
                            failure = ocrOutcome.failure,
                        )
                    }
                }
            }
        }
    }
}
