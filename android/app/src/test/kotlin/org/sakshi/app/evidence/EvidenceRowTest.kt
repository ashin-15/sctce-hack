package org.sakshi.app.evidence

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.sakshi.core.database.SupportState
import org.sakshi.core.vault.AcquisitionKind

class EvidenceRowTest {
    private fun kind(acquisition: String, detected: String?, declared: String? = null) =
        evidenceKindOf(acquisition, detected, declared)

    @Test
    fun textAndNotesComeFromHowTheyArrived() {
        assertEquals(EvidenceKind.TEXT, kind(AcquisitionKind.SHARED_TEXT, null, "image/png"))
        assertEquals(EvidenceKind.TEXT, kind(AcquisitionKind.PASTED_TEXT, null))
        assertEquals(EvidenceKind.NOTE, kind(AcquisitionKind.MANUAL_NOTE, null))
    }

    @Test
    fun filesUseTheDetectedTypeThenTheDeclaredOne() {
        assertEquals(EvidenceKind.IMAGE, kind(AcquisitionKind.SHARED_STREAM, "image/png", "application/pdf"))
        assertEquals(EvidenceKind.AUDIO, kind(AcquisitionKind.SHARED_STREAM, null, "audio/ogg"))
        assertEquals(EvidenceKind.VIDEO, kind(AcquisitionKind.SELECTED_VISUAL_MEDIA, "video/mp4"))
        assertEquals(EvidenceKind.VIDEO, kind(AcquisitionKind.SELECTED_VISUAL_MEDIA, "video/mp4", "video/mp4"))
        assertEquals(EvidenceKind.AUDIO, kind(AcquisitionKind.SELECTED_DOCUMENT, "video/mp4", "Audio/MP4; codecs=mp4a"))
        assertEquals(EvidenceKind.PDF, kind(AcquisitionKind.SELECTED_DOCUMENT, "application/pdf", "audio/mpeg"))
        assertEquals(EvidenceKind.PDF, kind(AcquisitionKind.SELECTED_DOCUMENT, "application/pdf"))
        assertEquals(EvidenceKind.ARCHIVE, kind(AcquisitionKind.SELECTED_DOCUMENT, "application/zip"))
        assertEquals(EvidenceKind.TEXT_FILE, kind(AcquisitionKind.SELECTED_DOCUMENT, null, "text/csv; charset=utf-8"))
        assertEquals(EvidenceKind.FILE, kind(AcquisitionKind.SELECTED_DOCUMENT, null, "application/octet-stream"))
        assertEquals(EvidenceKind.FILE, kind(AcquisitionKind.SELECTED_DOCUMENT, null, null))
    }

    @Test
    fun analysisLabelsFollowTheBrief() {
        val waiting = listOf(EvidenceKind.TEXT, EvidenceKind.TEXT_FILE, EvidenceKind.IMAGE, EvidenceKind.AUDIO, EvidenceKind.NOTE)
        EvidenceKind.entries.forEach {
            val expected = if (it in waiting) AnalysisLabel.WAITING_FOR_TEXT else AnalysisLabel.NOT_ANALYSED
            assertEquals(expected, analysisOf(it), it.name)
        }
    }

    private fun row(kind: EvidenceKind, state: String) = EvidenceRow("synthetic-id", "2026-10-02T10:00:00Z", 10, kind, state)

    @Test
    fun onlyUnanalysedTextImagesAndRecordingsOfferAnalysis() {
        assertTrue(row(EvidenceKind.TEXT, SupportState.SAVED).canAnalyse)
        assertTrue(row(EvidenceKind.TEXT_FILE, SupportState.SAVED).canAnalyse)
        assertTrue(row(EvidenceKind.IMAGE, SupportState.SAVED).canAnalyse)
        assertFalse(row(EvidenceKind.TEXT, SupportState.ANALYZED).canAnalyse)
        assertFalse(row(EvidenceKind.TEXT, SupportState.PARTIAL).canAnalyse)
        assertFalse(row(EvidenceKind.IMAGE, SupportState.ANALYZED).canAnalyse)
        assertTrue(row(EvidenceKind.AUDIO, SupportState.SAVED).canAnalyse)
        assertFalse(row(EvidenceKind.AUDIO, SupportState.ANALYZED).canAnalyse)
        assertFalse(row(EvidenceKind.AUDIO, SupportState.PARTIAL).canAnalyse)
        listOf(EvidenceKind.NOTE, EvidenceKind.PDF, EvidenceKind.VIDEO, EvidenceKind.FILE).forEach {
            assertFalse(row(it, SupportState.SAVED).canAnalyse, it.name)
        }
    }

    @Test
    fun analysedTextSaysSoInsteadOfWaiting() {
        assertEquals(AnalysisLabel.WAITING_FOR_TEXT, row(EvidenceKind.TEXT, SupportState.SAVED).analysis)
        assertEquals(AnalysisLabel.ANALYSED, row(EvidenceKind.TEXT, SupportState.ANALYZED).analysis)
        assertEquals(AnalysisLabel.ANALYSED_IN_PART, row(EvidenceKind.TEXT_FILE, SupportState.PARTIAL).analysis)
        assertEquals(AnalysisLabel.ANALYSED, row(EvidenceKind.IMAGE, SupportState.ANALYZED).analysis)
        assertEquals(AnalysisLabel.ANALYSED_IN_PART, row(EvidenceKind.IMAGE, SupportState.PARTIAL).analysis)
        assertEquals(AnalysisLabel.WAITING_FOR_TEXT, row(EvidenceKind.AUDIO, SupportState.SAVED).analysis)
        assertEquals(AnalysisLabel.ANALYSED, row(EvidenceKind.AUDIO, SupportState.ANALYZED).analysis)
        assertEquals(AnalysisLabel.ANALYSED_IN_PART, row(EvidenceKind.AUDIO, SupportState.PARTIAL).analysis)
        assertEquals(AnalysisLabel.WAITING_FOR_TEXT, row(EvidenceKind.NOTE, SupportState.ANALYZED).analysis)
    }
}
