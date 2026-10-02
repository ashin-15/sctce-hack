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

    /** True for a text item that has not been turned into events yet, so "Analyse text" is offered. */
    val canAnalyse: Boolean get() = kind.readsAsText && !analysed
}

/** The kinds the text analysis reads. Images wait for text recognition, which this version does not have. */
val EvidenceKind.readsAsText: Boolean get() = this == EvidenceKind.TEXT || this == EvidenceKind.TEXT_FILE

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
    val mime = (detectedMime ?: declaredMime)?.substringBefore(';')?.trim()?.lowercase()
    return when {
        mime == null -> EvidenceKind.FILE
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
    EvidenceKind.TEXT, EvidenceKind.TEXT_FILE, EvidenceKind.IMAGE, EvidenceKind.NOTE -> AnalysisLabel.WAITING_FOR_TEXT
    EvidenceKind.AUDIO, EvidenceKind.VIDEO, EvidenceKind.PDF, EvidenceKind.ARCHIVE, EvidenceKind.FILE ->
        AnalysisLabel.NOT_ANALYSED
}
