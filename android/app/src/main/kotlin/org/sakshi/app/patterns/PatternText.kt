package org.sakshi.app.patterns

import org.sakshi.app.R
import org.sakshi.app.review.reasonText
import org.sakshi.app.ui.UiText
import org.sakshi.app.ui.plural
import org.sakshi.app.ui.res
import org.sakshi.core.temporal.AssessmentStatus
import org.sakshi.core.temporal.PatternType
import org.sakshi.core.vault.PatternReview
import org.sakshi.core.vault.ReviewReason

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

/** Only a description that says something happened in the records can match or not match them; absence and doubt are not answered. */
fun isReviewableStatus(status: AssessmentStatus): Boolean =
    status == AssessmentStatus.SUPPORTED_DESCRIPTION || status == AssessmentStatus.CANDIDATE

/** The reasons offered when a description does not match. The words-are-not-there reason fits tags, not descriptions. */
val PATTERN_REJECT_REASONS: List<String> = listOf(
    ReviewReason.WRONG_SENDER_OR_QUOTE,
    ReviewReason.EXTRACTION_ERROR,
    ReviewReason.DUPLICATE,
    ReviewReason.INSUFFICIENT_CONTEXT,
)

/** What the person has said about a description, in words. Agreeing means only that it matches what they saved. */
fun patternReviewText(review: PatternReview, reason: String?): UiText = when (review) {
    PatternReview.NOT_REVIEWED -> res(R.string.patterns_review_none)
    PatternReview.ACCEPTED -> res(R.string.patterns_review_accepted)
    PatternReview.REJECTED ->
        if (reason == null) res(R.string.patterns_review_rejected) else res(R.string.patterns_review_rejected_reason, reasonText(reason))
    PatternReview.MARKED_UNKNOWN -> res(R.string.patterns_review_unknown)
}

fun patternNoticeText(notice: PatternNotice): UiText = res(
    when (notice) {
        PatternNotice.CHANGED_BEFORE_SAVE -> R.string.patterns_notice_changed
        PatternNotice.NOT_SAVED -> R.string.patterns_notice_not_saved
    },
)
