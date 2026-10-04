package org.sakshi.app.evidence

import org.sakshi.core.database.SupportState
import org.sakshi.core.vault.AcquisitionKind

/** Coarse type shown in the list, derived from how the item arrived and from its MIME type. */
enum class EvidenceKind { TEXT, TEXT_FILE, IMAGE, AUDIO, VIDEO, PDF, ARCHIVE, NOTE, FILE }

/** What this version does with a saved item. */
enum class AnalysisLabel { WAITING_FOR_TEXT, NOT_ANALYSED, ANALYSED, ANALYSED_IN_PART }

data class EvidenceRow(
    val id: String,
    val receivedAt: String,
    val byteSize: Long,
    val kind: EvidenceKind,
    val supportState: String = SupportState.SAVED,
) {
    private val analysed: Boolean get() = supportState == SupportState.ANALYZED || supportState == SupportState.PARTIAL

    val analysis: AnalysisLabel
        get() = when {
            kind.readsAsText && supportState == SupportState.ANALYZED -> AnalysisLabel.ANALYSED
            kind.readsAsText && supportState == SupportState.PARTIAL -> AnalysisLabel.ANALYSED_IN_PART
            else -> analysisOf(kind)
        }

    /** True for a text item, image or recording that has not been turned into events yet, so analysis is offered. */
    val canAnalyse: Boolean get() = kind.readsAsText && !analysed
}

/**
 * The kinds the text analysis reads. Images are read by on-device text recognition (Latin script only) and recordings
 * by on-device speech recognition; a file the recogniser cannot open is refused with a reason when the user asks.
 */
val EvidenceKind.readsAsText: Boolean
    get() = this == EvidenceKind.TEXT || this == EvidenceKind.TEXT_FILE || this == EvidenceKind.IMAGE || this == EvidenceKind.AUDIO

private val ARCHIVE_TYPES = setOf(
    "application/zip",
    "application/x-zip-compressed",
    "application/x-7z-compressed",
    "application/x-rar-compressed",
    "application/vnd.rar",
    "application/gzip",
    "application/x-gzip",
    "application/x-tar",
)

/** Prefers the type detected from the bytes, then the type the sender declared. */
fun evidenceKindOf(acquisitionKind: String, detectedMime: String?, declaredMime: String?): EvidenceKind {
    when (acquisitionKind) {
        AcquisitionKind.SHARED_TEXT, AcquisitionKind.PASTED_TEXT -> return EvidenceKind.TEXT
        AcquisitionKind.MANUAL_NOTE -> return EvidenceKind.NOTE
    }
    val declared = declaredMime?.substringBefore(';')?.trim()?.lowercase()
    val mime = detectedMime?.substringBefore(';')?.trim()?.lowercase() ?: declared
    return when {
        mime == null -> EvidenceKind.FILE
        // The leading bytes of an MP4 file do not say whether it holds sound only, so the provider's claim decides.
        mime == "video/mp4" && declared?.startsWith("audio/") == true -> EvidenceKind.AUDIO
        mime.startsWith("image/") -> EvidenceKind.IMAGE
        mime.startsWith("audio/") -> EvidenceKind.AUDIO
        mime.startsWith("video/") -> EvidenceKind.VIDEO
        mime == "application/pdf" -> EvidenceKind.PDF
        mime in ARCHIVE_TYPES -> EvidenceKind.ARCHIVE
        mime.startsWith("text/") -> EvidenceKind.TEXT_FILE
        else -> EvidenceKind.FILE
    }
}

fun analysisOf(kind: EvidenceKind): AnalysisLabel = when (kind) {
    EvidenceKind.TEXT, EvidenceKind.TEXT_FILE, EvidenceKind.IMAGE, EvidenceKind.AUDIO, EvidenceKind.NOTE ->
        AnalysisLabel.WAITING_FOR_TEXT
    EvidenceKind.VIDEO, EvidenceKind.PDF, EvidenceKind.ARCHIVE, EvidenceKind.FILE ->
        AnalysisLabel.NOT_ANALYSED
}
