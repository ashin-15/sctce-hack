package org.sakshi.app.patterns

import org.sakshi.app.R
import org.sakshi.app.ui.UiText
import org.sakshi.app.ui.plural
import org.sakshi.app.ui.res
import org.sakshi.core.temporal.AssessmentStatus
import org.sakshi.core.temporal.PatternType

/** Neutral titles. They name what is described, never a person's conduct or an offence. */
fun patternTitle(type: PatternType): UiText = res(
    when (type) {
        PatternType.REPEATED_CONTACT -> R.string.pattern_repeated_contact
        PatternType.RECURRENCE_AFTER_BOUNDARY -> R.string.pattern_after_boundary
        PatternType.WORDING_TRANSITION -> R.string.pattern_wording_change
        PatternType.DENSITY_CHANGE -> R.string.pattern_frequency_change
    },
)

fun patternStatusText(status: AssessmentStatus): UiText = res(
    when (status) {
        AssessmentStatus.SUPPORTED_DESCRIPTION -> R.string.pattern_status_supported
        AssessmentStatus.CANDIDATE -> R.string.pattern_status_candidate
        AssessmentStatus.INSUFFICIENT_CONTEXT -> R.string.pattern_status_insufficient
        AssessmentStatus.NOT_OBSERVED -> R.string.pattern_status_not_observed
    },
)

fun interpretationText(sentence: String): UiText = res(R.string.pattern_interpretation, sentence)

fun supportCountText(count: Int): UiText = plural(R.plurals.pattern_based_on, count, count)
