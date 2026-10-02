package org.sakshi.app.ui.components

import org.sakshi.app.R
import org.sakshi.core.model.EpistemicStatus

/** Line style of the rule drawn down the left edge of an epistemic block. */
enum class RuleStyle { NONE, SOLID, DASHED, DOUBLE }

/** Line style of the box drawn around an epistemic block. */
enum class BoxStyle { NONE, DOTTED, DASHED }

/** Which theme colour tints the rule and label. Colour is a secondary cue; the shape and the label carry the meaning. */
enum class Accent { INK, GOLD, GREEN, MUTED }

/**
 * How one [EpistemicStatus] is drawn. Two statuses never share the same ([rule], [box], [filled], [italic]) shape.
 */
data class Treatment(
    val rule: RuleStyle,
    val box: BoxStyle,
    val filled: Boolean,
    val italic: Boolean,
    val accent: Accent,
) {
    /** Everything that is not colour, for checking that statuses stay distinguishable in greyscale. */
    val shape: List<Any> get() = listOf(rule, box, filled, italic)
}

/**
 * The mapping from status to treatment. The label is always the first line of the block, so reading order is
 * the same for sighted and TalkBack users.
 *
 * - OBSERVED: text with one solid thick rule on the left, no box.
 * - USER_REPORTED: text with a dashed thick rule on the left, no box.
 * - INFERRED: dotted rounded box on all sides with a quiet fill.
 * - PATTERN: a double (two thin) rule on the left, no box.
 * - UNKNOWN: dashed rounded box on all sides, no fill, italic text.
 */
fun epistemicTreatment(status: EpistemicStatus): Treatment = when (status) {
    EpistemicStatus.OBSERVED -> Treatment(RuleStyle.SOLID, BoxStyle.NONE, filled = false, italic = false, accent = Accent.INK)
    EpistemicStatus.USER_REPORTED -> Treatment(RuleStyle.DASHED, BoxStyle.NONE, filled = false, italic = false, accent = Accent.GOLD)
    EpistemicStatus.INFERRED -> Treatment(RuleStyle.NONE, BoxStyle.DOTTED, filled = true, italic = false, accent = Accent.MUTED)
    EpistemicStatus.PATTERN -> Treatment(RuleStyle.DOUBLE, BoxStyle.NONE, filled = false, italic = false, accent = Accent.GREEN)
    EpistemicStatus.UNKNOWN -> Treatment(RuleStyle.NONE, BoxStyle.DASHED, filled = false, italic = true, accent = Accent.MUTED)
}

/** The string resource holding the visible label of [status]. */
fun epistemicLabelRes(status: EpistemicStatus): Int = when (status) {
    EpistemicStatus.OBSERVED -> R.string.epistemic_observed
    EpistemicStatus.USER_REPORTED -> R.string.epistemic_user_reported
    EpistemicStatus.INFERRED -> R.string.epistemic_inferred
    EpistemicStatus.PATTERN -> R.string.epistemic_pattern
    EpistemicStatus.UNKNOWN -> R.string.epistemic_unknown
}
