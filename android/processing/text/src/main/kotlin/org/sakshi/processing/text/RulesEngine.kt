package org.sakshi.processing.text

import org.sakshi.core.model.CategoryLabel
import org.sakshi.core.model.CodePointSpan
import org.sakshi.core.model.ConfidenceSemantics

/** Why [RuleSignals] holds, or does not hold, matches. */
public enum class SignalStatus { SUGGESTION, NO_CUE_MATCHED, UNSUPPORTED_LANGUAGE, CUE_LIST_NOT_REVIEWED, EMPTY_TEXT }

/** One cue occurrence; [span] is half-open, in code points of the original (unfolded) text. */
public data class CueMatch(
    val label: ProducerLabel,
    val schemaLabel: CategoryLabel,
    val phrase: String,
    val span: CodePointSpan,
)

/** Rule output for one text. A match is a suggestion for human review, never a conclusion. */
public data class RuleSignals(
    val status: SignalStatus,
    val matches: List<CueMatch>,
    val language: LanguageAssessment,
    val producerVersion: String,
    /** Always `NOT_APPLICABLE`: a cue match is not a probability. */
    val confidenceSemantics: ConfidenceSemantics,
)

/**
 * Phrase-cue matching over case-folded text. Cues have no context: negated or quoted phrases still
 * match, and every phrase of every language is tried against every text, as in the benchmark.
 */
public class RulesEngine(private val cueList: CueList, private val requireReviewedCues: Boolean) {
    private val foldedPhrases: List<String> = cueList.cues.map { CaseFolding.fold(it.phrase).text }

    /** Applies language and review gating, then returns every cue occurrence sorted by position. */
    public fun analyse(text: String): RuleSignals {
        val language = ScriptHints.assess(text)
        val all = if (text.isBlank() || language.hint == LanguageHint.UNSUPPORTED) emptyList() else occurrences(text)
        val kept = if (requireReviewedCues) all.filter { it.second in cueList.reviewed } else all
        val status = when {
            text.isBlank() -> SignalStatus.EMPTY_TEXT
            language.hint == LanguageHint.UNSUPPORTED -> SignalStatus.UNSUPPORTED_LANGUAGE
            kept.isNotEmpty() -> SignalStatus.SUGGESTION
            all.isNotEmpty() -> SignalStatus.CUE_LIST_NOT_REVIEWED
            else -> SignalStatus.NO_CUE_MATCHED
        }
        return RuleSignals(status, kept.map { it.first }, language, cueList.version, ConfidenceSemantics.NOT_APPLICABLE)
    }

    /** Labels with at least one cue occurrence, ignoring language and review gating (benchmark parity). */
    public fun labels(text: String): Set<ProducerLabel> = occurrences(text).mapTo(mutableSetOf()) { it.first.label }

    private fun occurrences(text: String): List<Pair<CueMatch, LanguageHint>> {
        val folded = CaseFolding.fold(text)
        val found = mutableListOf<Pair<CueMatch, LanguageHint>>()
        cueList.cues.forEachIndexed { cueIndex, cue ->
            val needle = foldedPhrases[cueIndex]
            var at = folded.text.indexOf(needle)
            while (at >= 0) {
                val (start, end) = folded.originalRange(at, at + needle.length)
                val match = CueMatch(cue.label, LabelMapping.toSchema(cue.label), cue.phrase, CodePointSpan(start, end))
                found += match to cue.language
                at = folded.text.indexOf(needle, at + 1)
            }
        }
        return found.sortedWith(
            compareBy<Pair<CueMatch, LanguageHint>>({ it.first.span.start }, { it.first.span.end }, { it.first.label }, { it.first.phrase }),
        )
    }
}
