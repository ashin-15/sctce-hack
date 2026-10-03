package org.sakshi.app.analysis

import org.sakshi.app.R
import org.sakshi.app.ui.UiText
import org.sakshi.app.ui.plural
import org.sakshi.app.ui.res
import org.sakshi.processing.analysis.AnalysisOutcome
import org.sakshi.processing.analysis.AnalysisWarning
import org.sakshi.processing.analysis.NotAnalysableReason

/** One plain sentence per reason an item was not analysed. Never an error name or a stack. */
fun refusalText(reason: NotAnalysableReason): UiText = res(
    when (reason) {
        NotAnalysableReason.NOT_TEXT -> R.string.analysis_refused_not_text
        NotAnalysableReason.TOO_LARGE -> R.string.analysis_refused_too_large
        NotAnalysableReason.PRESERVE_ONLY_TYPE -> R.string.analysis_refused_preserve_only
        NotAnalysableReason.MANUAL_NOTE -> R.string.analysis_refused_note
        NotAnalysableReason.EVIDENCE_MISSING -> R.string.analysis_refused_missing
        NotAnalysableReason.UNREADABLE -> R.string.analysis_refused_unreadable
        NotAnalysableReason.ALREADY_ANALYSED -> R.string.analysis_refused_already
        NotAnalysableReason.NO_TEXT_RECOGNISED -> R.string.analysis_refused_no_text_recognised
        NotAnalysableReason.IMAGE_NOT_DECODABLE -> R.string.analysis_refused_image_not_decodable
        NotAnalysableReason.RECOGNITION_FAILED -> R.string.analysis_refused_recognition_failed
        NotAnalysableReason.NO_SPEECH -> R.string.analysis_refused_no_speech
        NotAnalysableReason.AUDIO_TOO_LONG -> R.string.analysis_refused_audio_too_long
        NotAnalysableReason.AUDIO_NOT_DECODABLE -> R.string.analysis_refused_audio_not_decodable
        NotAnalysableReason.SPEECH_MODEL_UNAVAILABLE -> R.string.analysis_refused_speech_model_unavailable
        NotAnalysableReason.TRANSCRIPTION_FAILED -> R.string.analysis_refused_transcription_failed
    },
)

/** Each warning in words; [count] is how many records it concerns, where a count helps. */
fun warningText(warning: AnalysisWarning, count: Int): UiText = when (warning) {
    AnalysisWarning.DATE_ORDER_OVERRIDDEN -> res(R.string.analysis_warn_date_order)
    AnalysisWarning.SYSTEM_LINES_SKIPPED -> plural(R.plurals.analysis_warn_system_lines, count, count)
    AnalysisWarning.UNRESOLVED_TIMES -> plural(R.plurals.analysis_warn_times, count, count)
    AnalysisWarning.RECORD_LIMIT_REACHED -> res(R.string.analysis_warn_limit)
    AnalysisWarning.UNPARSED_PREFIX_SKIPPED -> res(R.string.analysis_warn_prefix)
    AnalysisWarning.UNSUPPORTED_LANGUAGE_PRESENT -> res(R.string.analysis_warn_language)
    AnalysisWarning.CUE_LIST_NOT_REVIEWED -> res(R.string.analysis_warn_cue_list)
    AnalysisWarning.OCR_LATIN_SCRIPT_ONLY -> res(R.string.analysis_warn_ocr_latin_only)
    AnalysisWarning.OCR_LOW_CONFIDENCE_LINES -> plural(R.plurals.analysis_warn_ocr_low_confidence, count, count)
    AnalysisWarning.AUDIO_TRANSCRIPT_MAY_CONTAIN_ERRORS -> res(R.string.analysis_warn_audio_may_contain_errors)
    AnalysisWarning.AUDIO_LOW_CONFIDENCE_SEGMENTS -> plural(R.plurals.analysis_warn_audio_low_confidence, count, count)
}

fun warningTexts(result: AnalysisOutcome.Analysed): List<UiText> =
    AnalysisWarning.entries.filter { it in result.warnings }.map { warningText(it, result.warningCounts[it] ?: 1) }
